package cx.aswin.boxcast.core.model

import kotlinx.serialization.Serializable

enum class EpisodeStatus { UNPLAYED, IN_PROGRESS, COMPLETED }

@Serializable
data class Podcast(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String,
    val type: String = "episodic", // "episodic" or "serial"
    val description: String? = null,
    val genre: String = "Podcast",
    val colorHex: String? = null, // For dynamic theming storage
    val fallbackImageUrl: String? = null, // Logic: Episode Art -> Fallback Podcast Art
    val latestEpisode: Episode? = null,
    val resumeProgress: Float? = null, // 0.0 - 1.0
    val episodeStatus: EpisodeStatus = EpisodeStatus.UNPLAYED,
    val subscribedAt: Long = 0L,
    // --- Podcast 2.0 ---
    val fundingUrl: String? = null,
    val fundingMessage: String? = null,
    val podcastGuid: String? = null,
    val medium: String? = null,   // "podcast", "music", "video"
    val ownerName: String? = null,
    val hasValue: Boolean = false,  // true if V4V value tag exists
    val updateFrequency: String? = null,
    val location: String? = null,
    val license: String? = null,
    val isLocked: Boolean = false,
    val podroll: List<PodrollItem>? = null,
    // User listening style: "newest" / "oldest" / null (default by type)
    val preferredSort: String? = null,
    val notificationsEnabled: Boolean = false,
    val autoDownloadEnabled: Boolean = false,
    val sourceType: String = SOURCE_PODCAST_INDEX,
    val feedUrl: String? = null,
    val rssRefreshCapability: String = RSS_REFRESH_MANUAL,
    val rssCatalogStale: Boolean = false,
    val rssHasNewEpisodes: Boolean = false,
    /** Podcast Index row represented by this local RSS subscription, when known. */
    val linkedPodcastIndexId: String? = null,
) {
    val isRss: Boolean
        get() = sourceType == SOURCE_RSS

    companion object {
        const val SOURCE_PODCAST_INDEX = "podcast_index"
        const val SOURCE_RSS = "rss"
        const val RSS_REFRESH_HEAD_VALIDATORS = "head_validators"
        const val RSS_REFRESH_MANUAL = "manual"
    }
}

fun Podcast.isLatestEpisodeNew(lastSeenId: String?): Boolean {
    if (isRss && rssHasNewEpisodes) return true
    if (episodeStatus != EpisodeStatus.UNPLAYED) return false
    val ep = latestEpisode ?: return false
    if (subscribedAt <= 0L) return false
    if (ep.publishedDate <= (subscribedAt / 1000L)) return false
    if (ep.id == lastSeenId) return false
    val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0
    return hoursSinceRelease <= 48.0
}
