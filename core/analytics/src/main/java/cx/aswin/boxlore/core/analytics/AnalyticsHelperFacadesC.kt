package cx.aswin.boxlore.core.analytics

/** AnalyticsHelper track façades part C (extensions; keeps helper under LargeClass). */

fun AnalyticsHelper.trackCuratedBlockImpression(
    blockTitle: String,
    vibeIds: List<String>,
) = DiscoveryAnalyticsTracks.trackCuratedBlockImpression(blockTitle, vibeIds)

fun AnalyticsHelper.trackHomeRecommendationsImpression(
    recommendationsCount: Int,
    episodeIds: List<String>,
    timeBlockTitle: String?,
) = DiscoveryAnalyticsTracks.trackHomeRecommendationsImpression(
    recommendationsCount,
    episodeIds,
    timeBlockTitle,
)

fun AnalyticsHelper.trackHomeRecommendationCardTapped(
    episodeId: String,
    episodeTitle: String?,
    podcastId: String,
    podcastName: String?,
    positionIndex: Int,
    timeBlockTitle: String?,
) = DiscoveryAnalyticsTracks.trackHomeRecommendationCardTapped(
    episodeId,
    episodeTitle,
    podcastId,
    podcastName,
    positionIndex,
    timeBlockTitle,
)

fun AnalyticsHelper.trackExploreRecommendationsImpression(
    recommendationsCount: Int,
    episodeIds: List<String>,
) = DiscoveryAnalyticsTracks.trackExploreRecommendationsImpression(
    recommendationsCount,
    episodeIds,
)

fun AnalyticsHelper.trackExploreRecommendationCardTapped(
    episodeId: String,
    episodeTitle: String?,
    podcastId: String,
    podcastName: String?,
    positionIndex: Int,
) = DiscoveryAnalyticsTracks.trackExploreRecommendationCardTapped(
    episodeId,
    episodeTitle,
    podcastId,
    podcastName,
    positionIndex,
)

fun AnalyticsHelper.trackPodcastInfoScreenViewed(
    podcastId: String,
    podcastName: String? = null,
    entryPoint: String? = null,
    genreFilter: String? = null,
    scrollDepth: Int? = null,
    searchQuery: String? = null,
) = DiscoveryAnalyticsTracks.trackPodcastInfoScreenViewed(
    podcastId,
    podcastName,
    entryPoint,
    genreFilter,
    scrollDepth,
    searchQuery,
)

fun AnalyticsHelper.trackPodcastSubscriptionToggled(
    podcastId: String,
    podcastName: String?,
    isSubscribed: Boolean,
    entryPoint: String,
) = DiscoveryAnalyticsTracks.trackPodcastSubscriptionToggled(
    podcastId,
    podcastName,
    isSubscribed,
    entryPoint,
)

@Suppress("LongParameterList")
fun AnalyticsHelper.trackPodcastInfoScreenSession(
    podcastId: String,
    podcastName: String,
    timeSpentSeconds: Float,
    wasSubscribed: Boolean,
    didSubscribe: Boolean,
    didUnsubscribe: Boolean,
    didSearch: Boolean,
    didSortEpisodes: Boolean,
    episodesPlayedCount: Int,
    episodesClickedCount: Int,
) = DiscoveryAnalyticsTracks.trackPodcastInfoScreenSession(
    podcastId,
    podcastName,
    timeSpentSeconds,
    wasSubscribed,
    didSubscribe,
    didUnsubscribe,
    didSearch,
    didSortEpisodes,
    episodesPlayedCount,
    episodesClickedCount,
)

fun AnalyticsHelper.trackCuratedCardTapped(
    podcastId: String,
    podcastName: String?,
    vibeId: String,
    positionIndex: Int,
) = DiscoveryAnalyticsTracks.trackCuratedCardTapped(
    podcastId,
    podcastName,
    vibeId,
    positionIndex,
)

fun AnalyticsHelper.trackEpisodeInfoScreenViewed(properties: Map<String, Any>) =
    DiscoveryAnalyticsTracks.trackEpisodeInfoScreenViewed(properties)

fun AnalyticsHelper.trackEpisodeInfoScreenSession(properties: Map<String, Any>) =
    DiscoveryAnalyticsTracks.trackEpisodeInfoScreenSession(properties)

fun AnalyticsHelper.trackProxyFallbackTriggered(
    imageHost: String,
    proxyWidth: Int,
    sampleMultiplier: Int = 10,
) = DiscoveryAnalyticsTracks.trackProxyFallbackTriggered(
    imageHost,
    proxyWidth,
    sampleMultiplier,
)

fun AnalyticsHelper.setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed: Int) =
    OnboardingAnalyticsTracks.setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed)

@Suppress("LongParameterList")
fun AnalyticsHelper.trackPlaybackStarted(
    podcastId: String?,
    podcastName: String?,
    podcastGenre: String?,
    episodeId: String,
    episodeTitle: String?,
    startPositionSeconds: Float,
    totalDurationSeconds: Float,
    isRepeating: Boolean,
    isSubscribed: Boolean,
    entryPoint: String? = null,
    entryPointContext: Map<String, Any>? = null,
) = PlaybackAnalyticsTracks.trackPlaybackStarted(
    podcastId,
    podcastName,
    podcastGenre,
    episodeId,
    episodeTitle,
    startPositionSeconds,
    totalDurationSeconds,
    isRepeating,
    isSubscribed,
    entryPoint,
    entryPointContext,
)
