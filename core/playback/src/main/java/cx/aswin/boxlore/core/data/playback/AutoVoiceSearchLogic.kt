package cx.aswin.boxlore.core.data.playback

/**
 * Pure Android Auto voice / browse search scoring.
 * Extracted from Android Auto browse voice-search paths in [cx.aswin.boxlore.core.data.service.auto.AutoBrowseLibraryCallback].
 */
object AutoVoiceSearchLogic {
    fun searchScore(
        primary: String,
        secondary: String?,
        query: String,
    ): Int {
        val title = primary.lowercase()
        val subtitle = secondary.orEmpty().lowercase()
        return when {
            title == query -> 100
            title.startsWith(query) -> 80
            subtitle == query -> 70
            subtitle.startsWith(query) -> 60
            title.contains(query) -> 50
            subtitle.contains(query) -> 40
            else -> 0
        }
    }

    fun normalizeVoiceQuery(query: String): String {
        var normalized =
            query
                .lowercase()
                .replace(Regex("[^a-z0-9&' ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        normalized =
            normalized.replace(
                Regex(
                    "\\b(on|using|from|in)\\s+(the\\s+)?box\\s*(lore|floor)(\\s+app)?\\b",
                ),
                " ",
            )
        normalized =
            normalized
                .replace(Regex("^(please\\s+)?(play|start|put on|listen to)\\s+"), "")
                .replace(
                    Regex(
                        "^(the\\s+)?(latest|newest|new)\\s+(podcast\\s+)?episode\\s+(of|from)\\s+",
                    ),
                    "",
                ).replace(Regex("^(a|an|the)\\s+episode\\s+(of|from)\\s+"), "")
                .replace(Regex("^(the|a|an)\\s+"), "")
                .replace(Regex("^podcast\\s+"), "")
                .replace(Regex("\\s+podcast$"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        return normalized
    }

    fun voiceMatchScore(
        title: String,
        author: String?,
        query: String,
    ): Int {
        val basicScore = searchScore(title, author, query)
        if (basicScore > 0) return basicScore
        val normalizedTitle = title.lowercase()
        if (query.contains(normalizedTitle)) return 75
        val queryTokens = query.split(" ").filter { it.length > 2 }.toSet()
        val titleTokens = normalizedTitle.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        if (queryTokens.isEmpty() || titleTokens.isEmpty()) return 0
        val overlap = queryTokens.intersect(titleTokens).size
        return if (overlap >= minOf(2, titleTokens.size)) 20 + overlap * 10 else 0
    }
}
