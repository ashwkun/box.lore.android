package cx.aswin.boxlore.core.analytics

/** AnalyticsHelper track façades part F (extensions; keeps helper under LargeClass). */

fun AnalyticsHelper.trackPlayMixClicked(count: Int) = LibraryAnalyticsTracks.trackPlayMixClicked(count)

fun AnalyticsHelper.trackHomePodcastFiltered(
    podcastId: String,
    title: String,
) = LibraryAnalyticsTracks.trackHomePodcastFiltered(podcastId, title)

fun AnalyticsHelper.trackLibrarySubscriptionsLayoutToggled(isGridView: Boolean) =
    LibraryAnalyticsTracks.trackLibrarySubscriptionsLayoutToggled(isGridView)

fun AnalyticsHelper.trackLibrarySubscriptionsSortChanged(
    sortMethod: String,
    tab: String,
) = LibraryAnalyticsTracks.trackLibrarySubscriptionsSortChanged(sortMethod, tab)

fun AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(
    genreName: String,
    tab: String,
) = LibraryAnalyticsTracks.trackLibrarySubscriptionsGenreFiltered(genreName, tab)

fun AnalyticsHelper.trackSmartQueueRefilled(event: AnalyticsHelper.SmartQueueRefillEvent) =
    QueueContentAnalyticsTracks.trackSmartQueueRefilled(event)

fun AnalyticsHelper.trackQueueReordered(
    episodeId: String,
    fromPosition: Int,
    toPosition: Int,
    contextType: String?,
) = QueueContentAnalyticsTracks.trackQueueReordered(
    episodeId,
    fromPosition,
    toPosition,
    contextType,
)

fun AnalyticsHelper.trackLoreQueueConflictShown(
    episodeId: String,
    normalQueueSize: Int,
) = QueueContentAnalyticsTracks.trackLoreQueueConflictShown(episodeId, normalQueueSize)

fun AnalyticsHelper.trackLoreQueueConflictResult(
    episodeId: String,
    result: String,
) = QueueContentAnalyticsTracks.trackLoreQueueConflictResult(episodeId, result)

fun AnalyticsHelper.trackSmartQueueEpisodeSkipped(
    episodeId: String,
    recommendationSource: String,
    positionInQueue: Int,
) = QueueContentAnalyticsTracks.trackSmartQueueEpisodeSkipped(
    episodeId,
    recommendationSource,
    positionInQueue,
)

fun AnalyticsHelper.trackOfflineModeEntered() = QueueContentAnalyticsTracks.trackOfflineModeEntered()

fun AnalyticsHelper.trackDiscoverCategoryFiltered(categoryName: String) =
    QueueContentAnalyticsTracks.trackDiscoverCategoryFiltered(categoryName)

fun AnalyticsHelper.trackAutoChaptersRequested(
    episodeId: String,
    podcastId: String?,
    audioUrl: String,
) = QueueContentAnalyticsTracks.trackAutoChaptersRequested(
    episodeId,
    podcastId,
    audioUrl,
)

fun AnalyticsHelper.trackAutoChaptersCompleted(
    episodeId: String,
    podcastId: String?,
    durationSeconds: Float,
    chaptersCount: Int,
) = QueueContentAnalyticsTracks.trackAutoChaptersCompleted(
    episodeId,
    podcastId,
    durationSeconds,
    chaptersCount,
)
