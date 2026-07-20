package cx.aswin.boxlore.core.analytics

/** AnalyticsHelper track façades part D (extensions; keeps helper under LargeClass). */

@Suppress("LongParameterList")
fun AnalyticsHelper.trackPlaybackPaused(
    podcastId: String?,
    podcastName: String?,
    podcastGenre: String?,
    episodeId: String,
    episodeTitle: String?,
    durationPlayedSeconds: Float,
    totalBufferedTimeSeconds: Float,
    totalDurationSeconds: Float,
    isCompleted: Boolean,
    entryPoint: String? = null,
    entryPointContext: Map<String, Any>? = null,
    queueSize: Int? = null,
    pauseReason: String = "user_voluntary",
) = PlaybackAnalyticsTracks.trackPlaybackPaused(
    podcastId,
    podcastName,
    podcastGenre,
    episodeId,
    episodeTitle,
    durationPlayedSeconds,
    totalBufferedTimeSeconds,
    totalDurationSeconds,
    isCompleted,
    entryPoint,
    entryPointContext,
    queueSize,
    pauseReason,
)

@Suppress("LongParameterList")
fun AnalyticsHelper.trackPlaybackCompleted(
    podcastId: String?,
    podcastName: String?,
    podcastGenre: String?,
    episodeId: String,
    episodeTitle: String?,
    totalDurationSeconds: Float,
    entryPoint: String? = null,
    entryPointContext: Map<String, Any>? = null,
) = PlaybackAnalyticsTracks.trackPlaybackCompleted(
    podcastId,
    podcastName,
    podcastGenre,
    episodeId,
    episodeTitle,
    totalDurationSeconds,
    entryPoint,
    entryPointContext,
)

@Suppress("LongParameterList")
fun AnalyticsHelper.trackPlaybackHeartbeat(
    podcastId: String?,
    podcastName: String?,
    episodeId: String,
    episodeTitle: String?,
    currentPositionSeconds: Float,
    totalDurationSeconds: Float,
    heartbeatPercentage: Int,
    heartbeatType: String,
    entryPoint: String? = null,
) = PlaybackAnalyticsTracks.trackPlaybackHeartbeat(
    podcastId,
    podcastName,
    episodeId,
    episodeTitle,
    currentPositionSeconds,
    totalDurationSeconds,
    heartbeatPercentage,
    heartbeatType,
    entryPoint,
)

@Suppress("LongParameterList")
fun AnalyticsHelper.trackPlaybackSeeked(
    podcastId: String?,
    podcastName: String?,
    episodeId: String,
    episodeTitle: String?,
    fromPositionSeconds: Float,
    toPositionSeconds: Float,
    totalDurationSeconds: Float,
    seekSource: String,
    entryPoint: String? = null,
) = PlaybackAnalyticsTracks.trackPlaybackSeeked(
    podcastId,
    podcastName,
    episodeId,
    episodeTitle,
    fromPositionSeconds,
    toPositionSeconds,
    totalDurationSeconds,
    seekSource,
    entryPoint,
)

fun AnalyticsHelper.trackPlaybackBuffering(
    episodeId: String? = null,
    podcastId: String? = null,
    entryPoint: String? = null,
    bufferDurationMs: Long? = null,
) = PlaybackAnalyticsTracks.trackPlaybackBuffering(
    episodeId,
    podcastId,
    entryPoint,
    bufferDurationMs,
)

fun AnalyticsHelper.trackPlaybackError(
    errorCode: String,
    errorMessage: String,
    podcastId: String?,
    episodeId: String?,
    podcastName: String? = null,
    episodeTitle: String? = null,
) = PlaybackAnalyticsTracks.trackPlaybackError(
    errorCode,
    errorMessage,
    podcastId,
    episodeId,
    podcastName,
    episodeTitle,
)

fun AnalyticsHelper.trackExploreScreenViewed(sourceEntryPoint: String? = null) =
    PlaybackAnalyticsTracks.trackExploreScreenViewed(sourceEntryPoint)

fun AnalyticsHelper.trackExploreSearchPerformed(
    query: String,
    resultsCount: Int,
) = PlaybackAnalyticsTracks.trackExploreSearchPerformed(query, resultsCount)

@Suppress("LongParameterList")
fun AnalyticsHelper.trackExploreScreenSession(
    timeSpentSeconds: Float,
    categoriesClickedCount: Int,
    vibesClickedCount: Int,
    searchesPerformedCount: Int,
    podcastsClickedCount: Int,
    maxScrollDepth: Int,
    finalCategoryState: String,
    finalVibeState: String?,
    finalSearchQuery: String?,
) = PlaybackAnalyticsTracks.trackExploreScreenSession(
    timeSpentSeconds,
    categoriesClickedCount,
    vibesClickedCount,
    searchesPerformedCount,
    podcastsClickedCount,
    maxScrollDepth,
    finalCategoryState,
    finalVibeState,
    finalSearchQuery,
)

fun AnalyticsHelper.trackLibraryHubViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryHubViewed(sourceEntryPoint)

fun AnalyticsHelper.trackLibraryHubSession(
    timeSpentSeconds: Float,
    navigatedTo: String?,
) = LibraryAnalyticsTracks.trackLibraryHubSession(timeSpentSeconds, navigatedTo)

fun AnalyticsHelper.trackLibrarySubscriptionsViewed(
    sourceEntryPoint: String,
    initialTab: String,
) = LibraryAnalyticsTracks.trackLibrarySubscriptionsViewed(sourceEntryPoint, initialTab)

fun AnalyticsHelper.trackLibrarySubscriptionsSession(
    timeSpentSeconds: Float,
    tabSwitchesCount: Int,
    didSearch: Boolean,
    finalSearchQuery: String?,
    podcastsClickedCount: Int,
    episodesClickedCount: Int,
) = LibraryAnalyticsTracks.trackLibrarySubscriptionsSession(
    timeSpentSeconds,
    tabSwitchesCount,
    didSearch,
    finalSearchQuery,
    podcastsClickedCount,
    episodesClickedCount,
)

fun AnalyticsHelper.trackLibraryLikedViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryLikedViewed(sourceEntryPoint)
