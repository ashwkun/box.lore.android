package cx.aswin.boxcast.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.URL
import androidx.annotation.VisibleForTesting

/**
 * Parsed transcript segment with timing.
 */
data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Fetches and parses podcast transcripts in SRT or VTT format.
 */
object TranscriptRepository {
    
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val cache = mutableMapOf<String, List<TranscriptSegment>>()
    
    private const val HTTPS_SCHEME = "https://"
    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_PREFIX = "https:"
    private const val HTTP_PREFIX = "http:"

    private fun normalizeUrl(url: String): String {
        try {
            var decoded = url
            if (decoded.contains("%3A") || decoded.contains("%2F") || decoded.contains("%3a") || decoded.contains("%2f")) {
                decoded = java.net.URLDecoder.decode(decoded, "UTF-8")
            }
            if (decoded.startsWith(HTTP_PREFIX) && !decoded.startsWith(HTTP_SCHEME)) {
                decoded = decoded.replaceFirst(HTTP_PREFIX, HTTP_SCHEME)
            } else if (decoded.startsWith(HTTPS_PREFIX) && !decoded.startsWith(HTTPS_SCHEME)) {
                decoded = decoded.replaceFirst(HTTPS_PREFIX, HTTPS_SCHEME)
            }
            if (decoded.startsWith(HTTP_SCHEME)) {
                decoded = decoded.replaceFirst(HTTP_SCHEME, HTTPS_SCHEME)
            }
            return decoded
        } catch (e: Exception) {
            return url
        }
    }

    suspend fun getTranscript(url: String, type: String? = null): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        cache[normalizedUrl]?.let { return@withContext it }
        
        try {
            val content = URL(normalizedUrl).readText()
            val segments = when {
                type?.contains("srt") == true || normalizedUrl.endsWith(".srt", ignoreCase = true) -> parseSrt(content)
                type?.contains("vtt") == true || normalizedUrl.endsWith(".vtt", ignoreCase = true) -> parseVtt(content)
                content.trimStart().startsWith("WEBVTT") -> parseVtt(content)
                else -> parseSrt(content) // Default to SRT
            }
            cache[normalizedUrl] = segments
            segments
        } catch (e: Exception) {
            android.util.Log.w("TranscriptRepo", "Failed to fetch transcript: $normalizedUrl", e)
            emptyList()
        }
    }

    suspend fun getAutoTranscript(
        api: cx.aswin.boxcast.core.network.BoxLoreApi,
        publicKey: String,
        deviceUuid: String,
        episodeId: String,
        audioUrl: String,
        transcriptUrl: String? = null
    ): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        val cacheKey = "auto_$episodeId"
        cache[cacheKey]?.let { return@withContext it }

        var retries = 0
        val maxRetries = 24 // 24 * 5s = 120s (2 minutes) max polling time

        while (retries < maxRetries) {
            try {
                val call = api.getAutoTranscript(publicKey, deviceUuid, episodeId, audioUrl, transcriptUrl)
                val response = call.execute()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        when (body.status) {
                            "completed" -> {
                                  val srtText = body.srt
                                  val chapters = body.chapters
                                  if (srtText != null) {
                                      val segments = parseSrt(srtText)
                                      cache[cacheKey] = segments
                                      if (chapters != null) {
                                          ChapterRepository.setCachedChapters("auto_$episodeId", chapters)
                                      }
                                      return@withContext segments
                                  } else {
                                      android.util.Log.w("TranscriptRepo", "Completed status but srt is null for $episodeId")
                                      return@withContext emptyList()
                                  }
                            }
                            "failed" -> {
                                android.util.Log.e("TranscriptRepo", "Auto-transcription failed on server for $episodeId: ${body.error}")
                                return@withContext emptyList()
                            }
                            "pending" -> {
                                android.util.Log.d("TranscriptRepo", "Auto-transcription pending for $episodeId, retrying...")
                            }
                        }
                    }
                } else {
                    android.util.Log.w("TranscriptRepo", "Failed to get auto-transcript: status code ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("TranscriptRepo", "Error calling getAutoTranscript for $episodeId", e)
            }

            retries++
            if (retries < maxRetries) {
                delay(5000)
            }
        }

        android.util.Log.w("TranscriptRepo", "Auto-transcription polling timed out for $episodeId")
        emptyList()
    }

    /**
     * Lightweight status check — calls the API with checkOnly=true.
     * Returns the full AutoTranscriptResponse containing status and limitLeft.
     * Returns null on network errors.
     */
    suspend fun checkAutoTranscriptStatus(
        api: cx.aswin.boxcast.core.network.BoxLoreApi,
        publicKey: String,
        deviceUuid: String,
        episodeId: String,
        audioUrl: String,
        transcriptUrl: String? = null
    ): cx.aswin.boxcast.core.network.model.AutoTranscriptResponse? = withContext(Dispatchers.IO) {
        try {
            val call = api.getAutoTranscript(publicKey, deviceUuid, episodeId, audioUrl, transcriptUrl, checkOnly = true)
            val response = call.execute()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.status == "completed") {
                    val chapters = body.chapters
                    if (chapters != null) {
                        ChapterRepository.setCachedChapters("auto_$episodeId", chapters)
                    }
                }
                body
            } else {
                android.util.Log.w("TranscriptRepo", "checkAutoTranscriptStatus failed: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("TranscriptRepo", "Error checking auto-transcript status for $episodeId", e)
            null
        }
    }
    
    /**
     * Parse SRT format:
     * 1
     * 00:00:01,000 --> 00:00:04,000
     * Hello and welcome
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun parseSrt(content: String): List<TranscriptSegment> {
        val segments = mutableListOf<TranscriptSegment>()
        val lines = content.lines().map { it.trim() }
        
        var currentId: Int? = null
        var currentTimeLine: String? = null
        val currentTextLines = mutableListOf<String>()
        
        for (line in lines) {
            if (line.isEmpty()) {
                // End of block
                if (currentTimeLine != null && currentTextLines.isNotEmpty()) {
                    val times = parseTimestampLine(currentTimeLine)
                    if (times != null) {
                        val text = currentTextLines.joinToString(" ").trim()
                            .replace(HTML_TAG_REGEX, "")
                        if (text.isNotBlank()) {
                            segments.add(TranscriptSegment(startMs = times.first, endMs = times.second, text = text))
                        }
                    }
                }
                currentId = null
                currentTimeLine = null
                currentTextLines.clear()
                continue
            }
            
            if (currentId == null) {
                // Try to parse block ID
                val id = line.toIntOrNull()
                if (id != null) {
                    currentId = id
                } else if (line.contains("-->")) {
                    // Fallback: if the line contains timestamps directly
                    currentTimeLine = line
                    currentId = -1
                }
            } else if (currentTimeLine == null) {
                if (line.contains("-->")) {
                    currentTimeLine = line
                }
            } else {
                currentTextLines.add(line)
            }
        }
        
        // Handle last block if file doesn't end with blank line
        if (currentTimeLine != null && currentTextLines.isNotEmpty()) {
            val times = parseTimestampLine(currentTimeLine)
            if (times != null) {
                val text = currentTextLines.joinToString(" ").trim()
                    .replace(HTML_TAG_REGEX, "")
                if (text.isNotBlank()) {
                    segments.add(TranscriptSegment(startMs = times.first, endMs = times.second, text = text))
                }
            }
        }
        
        return healSegments(segments)
    }
    
    /**
     * Parse WebVTT format:
     * WEBVTT
     *
     * 00:00:01.000 --> 00:00:04.000
     * Hello and welcome
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun parseVtt(content: String): List<TranscriptSegment> {
        val segments = mutableListOf<TranscriptSegment>()
        val blocks = content.trim().split(Regex("\\n\\n+"))
        
        for (block in blocks) {
            val lines = block.trim().lines()
            
            // Find the line with -->
            val timeLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIdx < 0) continue
            
            val times = parseTimestampLine(lines[timeLineIdx]) ?: continue
            val text = lines.drop(timeLineIdx + 1).joinToString(" ").trim()
                .replace(HTML_TAG_REGEX, "") // Strip VTT tags (<v>, <c>, etc.)
            
            if (text.isNotBlank()) {
                segments.add(TranscriptSegment(startMs = times.first, endMs = times.second, text = text))
            }
        }
        return healSegments(segments)
    }
    
    /**
     * Parse "00:01:23,456 --> 00:01:26,789" or "00:01:23.456 --> 00:01:26.789"
     */
    private fun parseTimestampLine(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size != 2) return null
        val start = parseTimestamp(parts[0].trim()) ?: return null
        val end = parseTimestamp(parts[1].trim().split(" ").first()) ?: return null
        return Pair(start, end)
    }
    
    /**
     * Parse timestamp to milliseconds with support for:
     * - HH:MM:SS,mmm
     * - HH:MM:SS.mmm
     * - MM:SS.mmm
     * - HH:MM:SS:mmm
     * - MM:SS:mmm
     */
    private fun parseTimestamp(ts: String): Long? {
        val parts = ts.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        
        return try {
            var h = 0L
            var m = 0L
            var s = 0L
            var ms = 0L
            
            when (parts.size) {
                4 -> {
                    h = parts[0].toLong()
                    m = parts[1].toLong()
                    s = parts[2].toLong()
                    ms = parseMillis(parts[3])
                }
                3 -> {
                    val lastPart = parts[2]
                    if (lastPart.length == 3 || lastPart.toInt() >= 60) {
                        // minutes:seconds:millis
                        m = parts[0].toLong()
                        s = parts[1].toLong()
                        ms = parseMillis(lastPart)
                    } else {
                        // hours:minutes:seconds
                        h = parts[0].toLong()
                        m = parts[1].toLong()
                        s = parts[2].toLong()
                    }
                }
                2 -> {
                    val lastPart = parts[1]
                    if (lastPart.length == 3 || lastPart.toInt() >= 60) {
                        // seconds:millis
                        s = parts[0].toLong()
                        ms = parseMillis(lastPart)
                    } else {
                        // minutes:seconds
                        m = parts[0].toLong()
                        s = parts[1].toLong()
                    }
                }
                1 -> {
                    s = parts[0].toLong()
                }
                else -> return null
            }
            
            h * 3600000L + m * 60000L + s * 1000L + ms
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseMillis(part: String): Long {
        return part.padEnd(3, '0').take(3).toLong()
    }
    
    /**
     * Reconstructs and self-heals shorthand or malformed transcript timelines
     * to ensure they are strictly chronological and monotonically increasing.
     */
    private fun healSegments(segments: List<TranscriptSegment>): List<TranscriptSegment> {
        if (segments.isEmpty()) return segments
        val correctedSegments = mutableListOf<TranscriptSegment>()
        var prevEnd = 0L
        
        for (i in segments.indices) {
            val segment = segments[i]
            var start = segment.startMs
            var end = segment.endMs
            
            // Failsafe for the very first block
            if (i == 0 && start > end) {
                start = (end - 3000L).coerceAtLeast(0L)
            }
            
            // 1. If start is backwards compared to previous end, try to correct it by adding minutes
            if (start < prevEnd) {
                var tempStart = start
                while (tempStart < prevEnd) {
                    tempStart += 60000L
                }
                start = tempStart
            }
            
            // 2. If end is backwards compared to start, correct it by adding minutes
            if (end < start) {
                var tempEnd = end
                while (tempEnd < start) {
                    tempEnd += 60000L
                }
                end = tempEnd
            }
            
            // 3. Absolute failsafe check
            if (start >= end) {
                start = prevEnd
                end = start + 3000L
            }
            
            correctedSegments.add(TranscriptSegment(startMs = start, endMs = end, text = segment.text))
            prevEnd = end
        }
        
        return correctedSegments
    }
    
    fun clearCache() {
        cache.clear()
    }
}
