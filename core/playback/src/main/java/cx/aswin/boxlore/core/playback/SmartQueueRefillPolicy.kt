package cx.aswin.boxlore.core.playback

/**
 * Pure gate for Smart Queue auto-refill after a media-item transition.
 * Extracted from [cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService].
 */
object SmartQueueRefillPolicy {
    const val DEFAULT_REMAINING_THRESHOLD = 2

    fun shouldRefill(
        remainingUpcoming: Int,
        isRefilling: Boolean,
        mediaItemCount: Int,
        isLearnEpisode: Boolean,
        sleepingAtEndOfEpisode: Boolean,
        remainingThreshold: Int = DEFAULT_REMAINING_THRESHOLD,
    ): Boolean {
        if (isRefilling) return false
        if (mediaItemCount <= 0) return false
        if (isLearnEpisode) return false
        if (sleepingAtEndOfEpisode) return false
        return remainingUpcoming <= remainingThreshold
    }

    fun stripQueuePrefixes(mediaId: String): String =
        mediaId
            .removePrefix("learn:")
            .removePrefix("episode:")
            .removePrefix("queue:")
}
