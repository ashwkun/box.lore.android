package cx.aswin.boxlore.core.analytics

/** AnalyticsHelper track façades part E (extensions; keeps helper under LargeClass). */

fun AnalyticsHelper.trackLibraryLikedSession(
    timeSpentSeconds: Float,
    episodesClickedCount: Int,
    episodesUnlikedCount: Int,
) = LibraryAnalyticsTracks.trackLibraryLikedSession(
    timeSpentSeconds,
    episodesClickedCount,
    episodesUnlikedCount,
)

fun AnalyticsHelper.trackLibraryDownloadsViewed(sourceEntryPoint: String) =
    LibraryAnalyticsTracks.trackLibraryDownloadsViewed(sourceEntryPoint)

fun AnalyticsHelper.trackLibraryDownloadsSession(
    timeSpentSeconds: Float,
    episodesClickedCount: Int,
    episodesDeletedCount: Int,
) = LibraryAnalyticsTracks.trackLibraryDownloadsSession(
    timeSpentSeconds,
    episodesClickedCount,
    episodesDeletedCount,
)

fun AnalyticsHelper.trackLibraryHistoryViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryHistoryViewed(sourceEntryPoint)

fun AnalyticsHelper.trackLibraryHistorySession(
    timeSpentSeconds: Float,
    episodesClickedCount: Int,
    itemsDeletedCount: Int,
) = LibraryAnalyticsTracks.trackLibraryHistorySession(
    timeSpentSeconds,
    episodesClickedCount,
    itemsDeletedCount,
)

fun AnalyticsHelper.trackLibraryHistoryTrackingNotice(action: String) = LibraryAnalyticsTracks.trackLibraryHistoryTrackingNotice(action)

fun AnalyticsHelper.trackTopControlbarInteraction(
    action: String,
    screen: String,
) = LibraryAnalyticsTracks.trackTopControlbarInteraction(action, screen)

fun AnalyticsHelper.trackSettingsScreenViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackSettingsScreenViewed(sourceEntryPoint)

fun AnalyticsHelper.trackSettingsInteraction(
    action: String,
    value: String? = null,
) = LibraryAnalyticsTracks.trackSettingsInteraction(action, value)

fun AnalyticsHelper.trackMiniPlayerInteraction(
    action: String,
    podcastId: String?,
    episodeId: String?,
    podcastName: String? = null,
    episodeTitle: String? = null,
) = LibraryAnalyticsTracks.trackMiniPlayerInteraction(
    action,
    podcastId,
    episodeId,
    podcastName,
    episodeTitle,
)

fun AnalyticsHelper.trackFullPlayerScreenSession(
    podcastId: String?,
    episodeId: String?,
    metrics: Map<String, Any>,
    podcastName: String? = null,
    episodeTitle: String? = null,
) = LibraryAnalyticsTracks.trackFullPlayerScreenSession(
    podcastId,
    episodeId,
    metrics,
    podcastName,
    episodeTitle,
)

fun AnalyticsHelper.trackNotificationTapped() = LibraryAnalyticsTracks.trackNotificationTapped()

fun AnalyticsHelper.trackDownloadCompleted(fileSizeMb: Float) = LibraryAnalyticsTracks.trackDownloadCompleted(fileSizeMb)

fun AnalyticsHelper.trackDownloadFailed(errorReason: String) = LibraryAnalyticsTracks.trackDownloadFailed(errorReason)
