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
    val autoDownloadEnabled: Boolean = false
)

fun Podcast.isLatestEpisodeNew(lastSeenId: String?): Boolean {
    if (episodeStatus != EpisodeStatus.UNPLAYED) return false
    val ep = latestEpisode ?: return false
    if (subscribedAt <= 0L) return false
    if (ep.publishedDate <= (subscribedAt / 1000L)) return false
    if (ep.id == lastSeenId) return false
    val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0
    return hoursSinceRelease <= 48.0
}
