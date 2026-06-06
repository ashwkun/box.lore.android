package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Fetches and parses Podcast 2.0 JSON Chapters from a chaptersUrl.
 * Format: https://github.com/Podcastindex-org/podcast-namespace/blob/main/chapters/jsonChapters.md
 */
object ChapterRepository {
    
    private val cache = mutableMapOf<String, List<Chapter>>()
    
    private fun normalizeUrl(url: String): String {
        try {
            var decoded = url
            if (decoded.contains("%3A") || decoded.contains("%2F") || decoded.contains("%3a") || decoded.contains("%2f")) {
                decoded = java.net.URLDecoder.decode(decoded, "UTF-8")
            }
            if (decoded.startsWith("http:") && !decoded.startsWith("http://")) {
                decoded = decoded.replaceFirst("http:", "http://")
            } else if (decoded.startsWith("https:") && !decoded.startsWith("https://")) {
                decoded = decoded.replaceFirst("https:", "https://")
            }
            if (decoded.startsWith("http://")) {
                decoded = decoded.replaceFirst("http://", "https://")
            }
            return decoded
        } catch (e: Exception) {
            return url
        }
    }

    suspend fun getChapters(chaptersUrl: String): List<Chapter> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(chaptersUrl)
        // Return cached if available
        cache[normalizedUrl]?.let { return@withContext it }
        
        try {
            val json = URL(normalizedUrl).readText()
            val root = JSONObject(json)
            val chaptersArray = root.optJSONArray("chapters") ?: return@withContext emptyList()
            
            val chapters = (0 until chaptersArray.length()).map { i ->
                val obj = chaptersArray.getJSONObject(i)
                Chapter(
                    startTime = obj.optDouble("startTime", 0.0),
                    title = obj.optString("title", "Chapter ${i + 1}"),
                    img = obj.optString("img").takeIf { it.isNotEmpty() },
                    url = obj.optString("url").takeIf { it.isNotEmpty() }
                )
            }.sortedBy { it.startTime }
            
            cache[normalizedUrl] = chapters
            chapters
        } catch (e: Exception) {
            android.util.Log.w("ChapterRepo", "Failed to fetch chapters: $normalizedUrl", e)
            emptyList()
        }
    }
    
    fun setCachedChapters(key: String, chapters: List<Chapter>) {
        cache[key] = chapters
    }

    fun getCachedChapters(key: String): List<Chapter>? {
        return cache[key]
    }
    
    fun clearCache() {
        cache.clear()
    }

    /**
     * Parses chapter timestamps from the episode description.
     * Supports both hh:mm:ss and mm:ss formats, and detects timestamps at either start or end of lines.
     */
    fun parseChaptersFromDescription(htmlDescription: String?): List<Chapter> {
        if (htmlDescription.isNullOrEmpty()) return emptyList()

        try {
            // Replace HTML paragraph, line break, and list tags with newlines
            val cleanText = htmlDescription
                .replace("(?i)<p>".toRegex(), "\n")
                .replace("(?i)</p>".toRegex(), "\n")
                .replace("(?i)<br\\s*/?>".toRegex(), "\n")
                .replace("(?i)<li>".toRegex(), "\n")
                .replace("<[^>]+>".toRegex(), "") // Strip all other HTML tags
            
            val lines = cleanText.split("\n")
            val chapters = mutableListOf<Chapter>()
            
            // Regex to find timestamps (e.g. 12:34 or 1:12:34)
            val timestampRegex = """\b(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\b""".toRegex()
            
            for (line in lines) {
                val trimmed = line.trim()
                val match = timestampRegex.find(trimmed) ?: continue
                
                val hours = match.groups[1]?.value?.toIntOrNull() ?: 0
                val minutes = match.groups[2]?.value?.toIntOrNull() ?: 0
                val seconds = match.groups[3]?.value?.toIntOrNull() ?: 0
                
                if (minutes >= 60 || seconds >= 60) continue
                
                // Determine whether the title is before or after the timestamp
                val matchStart = match.range.first
                val matchEnd = match.range.last + 1
                
                val rawTitle = if (matchStart > trimmed.length - matchEnd) {
                    trimmed.substring(0, matchStart)
                } else {
                    trimmed.substring(matchEnd)
                }
                
                // Clean up any remaining braces, hyphens, colons, or whitespace around the title
                val cleanTitle = rawTitle.trim()
                    .replace("^[^\\p{L}\\p{N}]+".toRegex(), "") // Strip leading non-alphanumeric chars
                    .replace("[^\\p{L}\\p{N}]+$".toRegex(), "") // Strip trailing non-alphanumeric chars
                    .trim()
                
                if (cleanTitle.isNotEmpty()) {
                    val startTime = hours * 3600.0 + minutes * 60.0 + seconds.toDouble()
                    chapters.add(
                        Chapter(
                            startTime = startTime,
                            title = cleanTitle
                        )
                    )
                }
            }
            
            // Safeguard: only return if we find at least 2 valid chapters (avoid random standalone timestamp false positives)
            return if (chapters.size >= 2) {
                chapters.sortedBy { it.startTime }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.w("ChapterRepo", "Failed to parse chapters from description", e)
            return emptyList()
        }
    }
}
