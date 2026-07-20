package cx.aswin.boxlore.core.analytics

/** AnalyticsHelper track façades part G (extensions; keeps helper under LargeClass). */

fun AnalyticsHelper.trackAutoChaptersFailed(
    episodeId: String,
    podcastId: String?,
    errorMessage: String,
) = QueueContentAnalyticsTracks.trackAutoChaptersFailed(
    episodeId,
    podcastId,
    errorMessage,
)

fun AnalyticsHelper.trackAutoTranscriptRequested(
    episodeId: String,
    podcastId: String?,
    audioUrl: String,
) = QueueContentAnalyticsTracks.trackAutoTranscriptRequested(
    episodeId,
    podcastId,
    audioUrl,
)

fun AnalyticsHelper.trackAutoTranscriptCompleted(
    episodeId: String,
    podcastId: String?,
    durationSeconds: Float,
    linesCount: Int,
) = QueueContentAnalyticsTracks.trackAutoTranscriptCompleted(
    episodeId,
    podcastId,
    durationSeconds,
    linesCount,
)

fun AnalyticsHelper.trackAutoTranscriptFailed(
    episodeId: String,
    podcastId: String?,
    errorMessage: String,
) = QueueContentAnalyticsTracks.trackAutoTranscriptFailed(
    episodeId,
    podcastId,
    errorMessage,
)

fun AnalyticsHelper.trackDailyBriefingBannerTapped(
    region: String,
    date: String,
) = QueueContentAnalyticsTracks.trackDailyBriefingBannerTapped(region, date)

fun AnalyticsHelper.trackDailyBriefingPlayClicked(
    region: String,
    date: String,
    source: String,
) = QueueContentAnalyticsTracks.trackDailyBriefingPlayClicked(
    region,
    date,
    source,
)

fun AnalyticsHelper.trackDailyBriefingPauseClicked(
    region: String,
    date: String,
    source: String,
) = QueueContentAnalyticsTracks.trackDailyBriefingPauseClicked(
    region,
    date,
    source,
)

fun AnalyticsHelper.trackDailyBriefingInteraction(
    action: String,
    region: String,
    date: String,
    extraProps: Map<String, Any> = emptyMap(),
) = QueueContentAnalyticsTracks.trackDailyBriefingInteraction(
    action,
    region,
    date,
    extraProps,
)

fun AnalyticsHelper.trackDailyBriefingRegionChanged(
    previousRegion: String,
    newRegion: String,
    date: String,
) = QueueContentAnalyticsTracks.trackDailyBriefingRegionChanged(
    previousRegion,
    newRegion,
    date,
)

fun AnalyticsHelper.trackDailyBriefingRelatedEpisodeClicked(
    region: String,
    date: String,
    chapterIndex: Int,
    episodeId: String,
    episodeTitle: String,
    podcastId: String,
    podcastTitle: String,
) = QueueContentAnalyticsTracks.trackDailyBriefingRelatedEpisodeClicked(
    region,
    date,
    chapterIndex,
    episodeId,
    episodeTitle,
    podcastId,
    podcastTitle,
)

fun AnalyticsHelper.trackDailyBriefingCardImpression(
    region: String,
    date: String,
    playbackStatus: String,
) = QueueContentAnalyticsTracks.trackDailyBriefingCardImpression(
    region,
    date,
    playbackStatus,
)

fun AnalyticsHelper.trackDailyBriefingScreenViewed(
    region: String,
    date: String,
    source: String? = null,
) = QueueContentAnalyticsTracks.trackDailyBriefingScreenViewed(
    region,
    date,
    source,
)

fun AnalyticsHelper.trackNavTabClicked(tabName: String) = QueueContentAnalyticsTracks.trackNavTabClicked(tabName)

fun AnalyticsHelper.trackLearnScreenViewed() = QueueContentAnalyticsTracks.trackLearnScreenViewed()
