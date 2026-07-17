package cx.aswin.boxcast.core.data.crosspromo

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.CrossPromotionConfidence
import cx.aswin.boxcast.core.model.CrossPromotionIndicator
import cx.aswin.boxcast.core.model.CrossPromotionResult

class CrossPromotionDetector {

    private val strictDelimiterRegex = Regex(
        """^(?:\[|\(|\*)?(?:feed drop|trailer swap|promo drop|bonus drop|listen now|feed swap|feed share|promo swap|special preview|listen to|guest feed|companion show|network premiere|crossover|cross.?promo|promo episode)(?:\]|\)|\*)?\s*(?::|-|\|\||\|)\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val conditionalDelimiterRegex = Regex(
        """^(?:\[|\(|\*)?(?:introducing|sneak peek|discover|meet|check out|we recommend|announcing|new season|brand new season|next season|next seaton|new sesson|brand new sesson|next sesson|sesson|try|sample|preview)(?:\]|\)|\*)?\s*(?::|-|\|\||\|)\s*(.+)""",
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

    /** "Subscribe to X", "Listen to our new show X", "Check out the podcast X" in description. */
    private val descriptionSubscribeRegex = Regex(
        """(?:subscribe\s+to|listen\s+to(?:\s+our)?(?:\s+new)?(?:\s+show|podcast)?|check\s+out(?:\s+our)?(?:\s+new)?(?:\s+show|podcast)?|find\s+us\s+on|search\s+for|follow)\s+["'“‘]?([^"'”’.!?\n,]{3,80})["'”’]?""",
        RegexOption.IGNORE_CASE
    )

    /** Quoted show titles near promo language. */
    private val descriptionQuotedShowRegex = Regex(
        """(?:podcast|show|series|feed)\s+["'“‘]([^"'”’]{3,80})["'”’]""",
        RegexOption.IGNORE_CASE
    )

    /** Apple Podcasts / Spotify / podcast: deep links with a nearby title hint. */
    private val descriptionLinkTitleRegex = Regex(
        """(?:podcasts\.apple\.com|open\.spotify\.com/show|podcasts?:)[^\s<"']+""",
        RegexOption.IGNORE_CASE
    )

    private val seasonOnlyNameRegex = Regex(
        """^(?:brand\s+)?(?:new|next)\s+season(?:\s+\d+)?$|^season\s+\d+$|^s\d+$""",
        RegexOption.IGNORE_CASE
    )

    fun detect(episode: Episode, hostPodcastTitle: String): CrossPromotionResult {
        val title = episode.title.trim()
        val duration = episode.duration
        val episodeType = episode.episodeType?.lowercase()
        val episodeNumber = episode.episodeNumber
        val plainDescription = stripHtml(episode.description)

        val matchedIndicators = mutableListOf<CrossPromotionIndicator>()
        var extractedShowName: String? = null

        // 1. Strict indicator: Feed Drop / Trailer Swap / Promo Drop / Bonus Drop / Listen Now + delimiter
        val strictMatch = strictDelimiterRegex.find(title)
        if (strictMatch != null) {
            val name = cleanExtractedName(strictMatch.groupValues[1])
            if (isPromotableShowName(name, hostPodcastTitle)) {
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
            val name = cleanExtractedName(delimiterMatch.groupValues[1])
            if (isPromotableShowName(name, hostPodcastTitle)) {
                matchedIndicators.add(
                    if (presentsRegex.containsMatchIn(title)) {
                        CrossPromotionIndicator.TITLE_PRESENTS_PATTERN
                    } else {
                        CrossPromotionIndicator.TITLE_DELIMITER_PATTERN
                    }
                )
                return CrossPromotionResult(
                    isCrossPromotion = true,
                    confidence = CrossPromotionConfidence.HIGH,
                    extractedShowName = name,
                    matchedIndicators = matchedIndicators
                )
            }
        }

        // 3. Optional indicators
        val seamlessMatch = seamlessIntroducingRegex.find(title)
        if (seamlessMatch != null) {
            val name = cleanExtractedName(seamlessMatch.groupValues[1])
            if (isPromotableShowName(name, hostPodcastTitle)) {
                extractedShowName = name
                matchedIndicators.add(CrossPromotionIndicator.TITLE_SEAMLESS_INTRODUCING)
            }
        }

        // Description-based name extraction (supports trailer/promo episodes without title cues)
        val descriptionName = extractShowNameFromDescription(plainDescription, hostPodcastTitle)
        if (descriptionName != null) {
            if (extractedShowName == null) {
                extractedShowName = descriptionName
            }
            matchedIndicators.add(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE)
        } else if (descriptionLinkTitleRegex.containsMatchIn(plainDescription) &&
            (duration in 30..180 || episodeType == "trailer" || episodeType == "bonus")
        ) {
            // Promo-shaped episode with platform links but no parseable name — still score the signal.
            matchedIndicators.add(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE)
        }

        if (duration in 30..180) {
            matchedIndicators.add(CrossPromotionIndicator.SHORT_DURATION)
        }

        if (episodeType == "trailer" || episodeType == "bonus") {
            matchedIndicators.add(CrossPromotionIndicator.TRAILER_OR_BONUS_TYPE)
        }

        if (episodeNumber == null) {
            matchedIndicators.add(CrossPromotionIndicator.MISSING_EPISODE_NUMBER)
        }

        // Optional path: ≥2 signals + a resolvable show name
        if (matchedIndicators.size >= 2 && extractedShowName != null) {
            val confidence = when {
                matchedIndicators.contains(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE) &&
                    matchedIndicators.contains(CrossPromotionIndicator.TRAILER_OR_BONUS_TYPE) ->
                    CrossPromotionConfidence.HIGH
                matchedIndicators.size >= 3 -> CrossPromotionConfidence.HIGH
                else -> CrossPromotionConfidence.MEDIUM
            }
            return CrossPromotionResult(
                isCrossPromotion = true,
                confidence = confidence,
                extractedShowName = extractedShowName,
                matchedIndicators = matchedIndicators
            )
        }

        // High-confidence description alone (explicit subscribe/check-out with a clear name)
        if (descriptionName != null &&
            matchedIndicators.contains(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE) &&
            (duration in 30..300 || episodeType == "trailer" || episodeType == "bonus" || episodeNumber == null)
        ) {
            return CrossPromotionResult(
                isCrossPromotion = true,
                confidence = CrossPromotionConfidence.MEDIUM,
                extractedShowName = descriptionName,
                matchedIndicators = matchedIndicators.ifEmpty {
                    listOf(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE)
                }
            )
        }

        return CrossPromotionResult(
            isCrossPromotion = false,
            confidence = CrossPromotionConfidence.NONE,
            extractedShowName = null,
            matchedIndicators = emptyList()
        )
    }

    private fun extractShowNameFromDescription(
        description: String,
        hostPodcastTitle: String
    ): String? {
        if (description.isBlank()) return null

        descriptionSubscribeRegex.findAll(description).forEach { match ->
            val name = cleanExtractedName(match.groupValues[1])
            if (isPromotableShowName(name, hostPodcastTitle)) return name
        }

        descriptionQuotedShowRegex.findAll(description).forEach { match ->
            val name = cleanExtractedName(match.groupValues[1])
            if (isPromotableShowName(name, hostPodcastTitle)) return name
        }

        return null
    }

    private fun cleanExtractedName(raw: String): String {
        return raw
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’', '.', ',', '!', '?')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isPromotableShowName(extractedName: String, hostPodcastTitle: String): Boolean {
        if (extractedName.length < 3) return false
        if (seasonOnlyNameRegex.matches(extractedName)) return false
        if (isSamePodcast(extractedName, hostPodcastTitle)) return false
        // Reject generic filler that isn't a show title
        val lower = extractedName.lowercase()
        if (lower in GENERIC_SHOW_NAMES) return false
        return true
    }

    private fun isSamePodcast(extractedName: String, hostPodcastTitle: String): Boolean {
        val cleanExtracted = extractedName.trim().lowercase()
        val cleanHost = hostPodcastTitle.trim().lowercase()
        if (cleanExtracted.isEmpty() || cleanHost.isEmpty()) return false
        return cleanHost.contains(cleanExtracted) || cleanExtracted.contains(cleanHost)
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("""<(?i)br\s*/?>"""), "\n")
            .replace(Regex("""</(?i)p>"""), "\n")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private companion object {
        val GENERIC_SHOW_NAMES = setOf(
            "this podcast",
            "our podcast",
            "the podcast",
            "this show",
            "our show",
            "the show",
            "us",
            "me",
            "more",
            "apple podcasts",
            "spotify",
            "youtube"
        )
    }
}
