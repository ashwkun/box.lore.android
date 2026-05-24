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
}
