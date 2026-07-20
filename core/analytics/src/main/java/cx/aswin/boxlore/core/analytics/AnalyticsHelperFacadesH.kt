package cx.aswin.boxlore.core.analytics

/** AnalyticsHelper track façades part H (extensions; keeps helper under LargeClass). */

fun AnalyticsHelper.trackLearnCardDismissed(
    episodeId: String,
    episodeTitle: String?,
    podcastId: String?,
    podcastTitle: String?,
) = QueueContentAnalyticsTracks.trackLearnCardDismissed(
    episodeId,
    episodeTitle,
    podcastId,
    podcastTitle,
)

fun AnalyticsHelper.trackLearnCardQueued(
    episodeId: String,
    episodeTitle: String?,
    podcastId: String?,
    podcastTitle: String?,
) = QueueContentAnalyticsTracks.trackLearnCardQueued(
    episodeId,
    episodeTitle,
    podcastId,
    podcastTitle,
)

fun AnalyticsHelper.trackLearnCardInfoClicked(
    episodeId: String,
    episodeTitle: String?,
    podcastId: String?,
    podcastTitle: String?,
) = QueueContentAnalyticsTracks.trackLearnCardInfoClicked(
    episodeId,
    episodeTitle,
    podcastId,
    podcastTitle,
)

fun AnalyticsHelper.trackLearnCardPodcastClicked(
    podcastId: String?,
    podcastTitle: String?,
) = QueueContentAnalyticsTracks.trackLearnCardPodcastClicked(podcastId, podcastTitle)

fun AnalyticsHelper.trackLearnCardPlayClicked(
    episodeId: String,
    episodeTitle: String?,
    podcastId: String?,
    podcastTitle: String?,
) = QueueContentAnalyticsTracks.trackLearnCardPlayClicked(
    episodeId,
    episodeTitle,
    podcastId,
    podcastTitle,
)

fun AnalyticsHelper.trackLearnScreenSession(
    timeSpentSeconds: Float,
    cardsDismissedCount: Int,
    cardsQueuedCount: Int,
    playsCount: Int,
    podcastsClickedCount: Int,
    infosClickedCount: Int,
) = QueueContentAnalyticsTracks.trackLearnScreenSession(
    timeSpentSeconds,
    cardsDismissedCount,
    cardsQueuedCount,
    playsCount,
    podcastsClickedCount,
    infosClickedCount,
)
