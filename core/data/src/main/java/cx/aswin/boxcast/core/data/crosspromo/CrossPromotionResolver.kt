package cx.aswin.boxcast.core.data.crosspromo

import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.model.Podcast
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrossPromotionResolver @Inject constructor(
    private val podcastRepository: PodcastRepository
) {
    private val resolutionCache = mutableMapOf<String, Podcast?>()
    private val cacheLock = Any()

    suspend fun resolve(extractedName: String): Podcast? {
        if (extractedName.isBlank()) return null
        val cleanExtracted = extractedName.trim().lowercase()

        synchronized(cacheLock) {
            if (resolutionCache.containsKey(cleanExtracted)) {
                return resolutionCache[cleanExtracted]
            }
        }

        // 1. Try to extract quoted text if present (e.g. 'History Daily')
        val quotedMatch = quotedTextRegex.find(extractedName)
        var cleanedName = if (quotedMatch != null) {
            quotedMatch.groupValues[1].trim()
        } else {
            extractedName.trim()
        }

        // 2. Remove noise suffixes (e.g. "from host Lindsay Graham", "from Wondery")
        cleanedName = noiseSuffixRegex.replace(cleanedName, "").trim()

        // 3. Remove subtitle/season suffixes after colons (e.g. "Dr. Death: The Cowboy" -> "Dr. Death")
        if (cleanedName.contains(":")) {
            cleanedName = cleanedName.substringBefore(":").trim()
        }

        // 4. Clean query by removing season/series/part suffixes for better search indexing
        val searchQuery = seasonSuffixRegex.replace(cleanedName, "").trim()
        if (searchQuery.isBlank()) {
            synchronized(cacheLock) {
                resolutionCache[cleanExtracted] = null
            }
            return null
        }

        val resolved = try {
            val results = podcastRepository.searchPodcasts(searchQuery)
            pickBestMatch(results, cleanedName, searchQuery)
        } catch (_: Exception) {
            null
        }

        synchronized(cacheLock) {
            resolutionCache[cleanExtracted] = resolved
        }
        return resolved
    }

    private fun pickBestMatch(
        results: List<Podcast>,
        cleanedName: String,
        searchQuery: String
    ): Podcast? {
        if (results.isEmpty()) return null

        val normalizedCleaned = normalizeForComparison(cleanedName)
        val normalizedQuery = normalizeForComparison(searchQuery)

        data class Scored(val podcast: Podcast, val score: Int)

        val scored = results.take(8).mapNotNull { podcast ->
            val title = normalizeForComparison(podcast.title)
            val score = when {
                title == normalizedCleaned || title == normalizedQuery -> 100
                title.startsWith(normalizedCleaned) || normalizedCleaned.startsWith(title) -> 85
                title.contains(normalizedCleaned) || normalizedCleaned.contains(title) -> 70
                title.startsWith(normalizedQuery) || normalizedQuery.startsWith(title) -> 60
                title.contains(normalizedQuery) || normalizedQuery.contains(title) -> 50
                // Token overlap for multi-word shows
                else -> {
                    val titleTokens = title.split(' ').filter { it.length > 2 }.toSet()
                    val queryTokens = normalizedCleaned.split(' ').filter { it.length > 2 }.toSet()
                    if (queryTokens.isEmpty() || titleTokens.isEmpty()) return@mapNotNull null
                    val overlap = queryTokens.intersect(titleTokens).size.toFloat() / queryTokens.size
                    if (overlap >= 0.75f) (40 + (overlap * 20).toInt()) else return@mapNotNull null
                }
            }
            Scored(podcast, score)
        }

        return scored.maxByOrNull { it.score }?.takeIf { it.score >= 50 }?.podcast
    }

    private val quotedTextRegex = Regex(
        """['"‘“]([^'"’”]+)[''’”]"""
    )

    private val noiseSuffixRegex = Regex(
        """\s+(?:from\s+host|hosted\s+by|from|with|by)\b.+""",
        RegexOption.IGNORE_CASE
    )

    private val seasonSuffixRegex = Regex(
        """\s+(?:brand\s+new\s+season|brand\s+new\s+sesson|brand\s+new|new\s+season|new\s+sesson|next\s+seaton|next\s+sesson|next\s+season|season|sesson|seaton|series|s|part)\b(?:\s*\d+.*)?$""",
        RegexOption.IGNORE_CASE
    )

    private fun normalizeForComparison(text: String): String {
        return text.lowercase()
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace(":", "")
            .replace("-", "")
            .replace(",", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
