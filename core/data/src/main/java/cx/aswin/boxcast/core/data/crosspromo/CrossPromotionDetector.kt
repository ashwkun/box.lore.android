package cx.aswin.boxcast.core.data.crosspromo

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.CrossPromotionConfidence
import cx.aswin.boxcast.core.model.CrossPromotionIndicator
import cx.aswin.boxcast.core.model.CrossPromotionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrossPromotionDetector @Inject constructor() {

    private val strictDelimiterRegex = Regex(
        """^(?:\[|\(|\*)?(?:feed drop|trailer swap|promo drop|bonus drop|listen now|feed swap|feed share|promo swap|special preview|listen to)(?:\]|\)|\*)?\s*(?::|-|\|\||\|)\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val conditionalDelimiterRegex = Regex(
        """^(?:\[|\(|\*)?(?:introducing|sneak peek|discover|meet|check out|we recommend|announcing|new season|brand new season|next season|next seaton|new sesson|brand new sesson|next sesson|sesson)(?:\]|\)|\*)?\s*(?::|-|\|\||\|)\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val presentsRegex = Regex(
        """^.*(?:presents|presenting|presented by|from the creators of|from the makers of|from the team behind|brought to you by)(?:\s+[^:\-|]+)?\s*(?::|-|\|\||\|)\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val seamlessIntroducingRegex = Regex(
        """^(?:\[|\(|\*)?introducing(?:\]|\)|\*)?\s+(?!season\s)([^:\-|].+)""",
        RegexOption.IGNORE_CASE
    )

    fun detect(episode: Episode, hostPodcastTitle: String): CrossPromotionResult {
        val title = episode.title.trim()
        val duration = episode.duration
        val episodeType = episode.episodeType?.lowercase()
        val episodeNumber = episode.episodeNumber

        val matchedIndicators = mutableListOf<CrossPromotionIndicator>()
        var extractedShowName: String? = null

        // 1. Strict indicator: Feed Drop / Trailer Swap / Promo Drop / Bonus Drop / Listen Now + delimiter
        val strictMatch = strictDelimiterRegex.find(title)
        if (strictMatch != null) {
            val name = strictMatch.groupValues[1].trim()
            if (!isSamePodcast(name, hostPodcastTitle)) {
                matchedIndicators.add(CrossPromotionIndicator.TITLE_DELIMITER_PATTERN)
                return CrossPromotionResult(
                    isCrossPromotion = true,
                    confidence = CrossPromotionConfidence.HIGH,
                    extractedShowName = name,
                    matchedIndicators = matchedIndicators
                )
            }
        }

        // 2. Delimiter indicators: Introducing / Sneak Peek / Presents / Presenting + delimiter
        val delimiterMatch = conditionalDelimiterRegex.find(title) ?: presentsRegex.find(title)
        if (delimiterMatch != null) {
            val name = delimiterMatch.groupValues[1].trim()
            if (!isSamePodcast(name, hostPodcastTitle)) {
                matchedIndicators.add(if (presentsRegex.matches(title)) {
                    CrossPromotionIndicator.TITLE_PRESENTS_PATTERN
                } else {
                    CrossPromotionIndicator.TITLE_DELIMITER_PATTERN
                })
                return CrossPromotionResult(
                    isCrossPromotion = true,
                    confidence = CrossPromotionConfidence.HIGH,
                    extractedShowName = name,
                    matchedIndicators = matchedIndicators
                )
            }
        }

        // 3. Optional indicators check
        // We evaluate individual optional signals and calculate total matches.
        
        // Seamless "Introducing" (no delimiter)
        val seamlessMatch = seamlessIntroducingRegex.find(title)
        val hasSeamlessMatch = if (seamlessMatch != null) {
            val name = seamlessMatch.groupValues[1].trim()
            if (!isSamePodcast(name, hostPodcastTitle)) {
                extractedShowName = name
                matchedIndicators.add(CrossPromotionIndicator.TITLE_SEAMLESS_INTRODUCING)
                true
            } else false
        } else false

        // Short duration (30 - 180 seconds)
        if (duration in 30..180) {
            matchedIndicators.add(CrossPromotionIndicator.SHORT_DURATION)
        }

        // Trailer or Bonus type
        if (episodeType == "trailer" || episodeType == "bonus") {
            matchedIndicators.add(CrossPromotionIndicator.TRAILER_OR_BONUS_TYPE)
        }

        // Missing episode number
        if (episodeNumber == null) {
            matchedIndicators.add(CrossPromotionIndicator.MISSING_EPISODE_NUMBER)
        }

        // To flag via optional indicators, we need:
        // 1. 2 or more matched optional indicators.
        // 2. An extracted show name (which can only come from TITLE_SEAMLESS_INTRODUCING here).
        val optionalIndicatorsCount = matchedIndicators.size
        
        if (optionalIndicatorsCount >= 2 && extractedShowName != null) {
            return CrossPromotionResult(
                isCrossPromotion = true,
                confidence = CrossPromotionConfidence.MEDIUM,
                extractedShowName = extractedShowName,
                matchedIndicators = matchedIndicators
            )
        }

        return CrossPromotionResult(
            isCrossPromotion = false,
            confidence = CrossPromotionConfidence.NONE,
            extractedShowName = null,
            matchedIndicators = emptyList()
        )
    }

    private fun isSamePodcast(extractedName: String, hostPodcastTitle: String): Boolean {
        val cleanExtracted = extractedName.trim().lowercase()
        val cleanHost = hostPodcastTitle.trim().lowercase()
        if (cleanExtracted.isEmpty() || cleanHost.isEmpty()) return false
        return cleanHost.contains(cleanExtracted) || cleanExtracted.contains(cleanHost)
    }
}
