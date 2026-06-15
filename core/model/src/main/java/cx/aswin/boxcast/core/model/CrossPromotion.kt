package cx.aswin.boxcast.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CrossPromotionConfidence {
    HIGH,
    MEDIUM,
    NONE
}

@Serializable
enum class CrossPromotionIndicator {
    TITLE_DELIMITER_PATTERN,
    TITLE_PRESENTS_PATTERN,
    TITLE_SEAMLESS_INTRODUCING,
    SHORT_DURATION,
    TRAILER_OR_BONUS_TYPE,
    MISSING_EPISODE_NUMBER
}

@Serializable
data class CrossPromotionResult(
    val isCrossPromotion: Boolean,
    val confidence: CrossPromotionConfidence,
    val extractedShowName: String?,
    val matchedIndicators: List<CrossPromotionIndicator>
)

@Serializable
data class ResolvedCrossPromotion(
    val extractedShowName: String,
    val confidence: CrossPromotionConfidence,
    val targetPodcast: Podcast?,
    val matchedIndicators: List<CrossPromotionIndicator>
)
