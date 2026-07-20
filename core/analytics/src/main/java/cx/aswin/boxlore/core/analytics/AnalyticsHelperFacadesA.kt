package cx.aswin.boxlore.core.analytics

/** AnalyticsHelper track façades part A (extensions; keeps helper under LargeClass). */

fun AnalyticsHelper.trackHomeImportBannerImpression() = OnboardingAnalyticsTracks.trackHomeImportBannerImpression()

fun AnalyticsHelper.trackHomeImportBannerClicked(action: String) = OnboardingAnalyticsTracks.trackHomeImportBannerClicked(action)

fun AnalyticsHelper.trackHomeImportBannerDismissed() = OnboardingAnalyticsTracks.trackHomeImportBannerDismissed()

fun AnalyticsHelper.trackOnboardingStarted(entryPoint: String = "welcome_screen") =
    OnboardingAnalyticsTracks.trackOnboardingStarted(entryPoint)

fun AnalyticsHelper.trackOnboardingFlowSelected(
    flowType: String,
    entryPoint: String = "welcome_screen",
) = OnboardingAnalyticsTracks.trackOnboardingFlowSelected(flowType, entryPoint)

fun AnalyticsHelper.trackOnboardingSkipped(
    screen: String,
    totalOnboardingTimeSeconds: Float,
) = OnboardingAnalyticsTracks.trackOnboardingSkipped(screen, totalOnboardingTimeSeconds)

fun AnalyticsHelper.trackOnboardingAiTurnSubmitted(
    turnNumber: Int,
    selectedOptions: Set<String>,
    customInputText: String,
    timeSpentSeconds: Float,
) = OnboardingAnalyticsTracks.trackOnboardingAiTurnSubmitted(
    turnNumber,
    selectedOptions,
    customInputText,
    timeSpentSeconds,
)

fun AnalyticsHelper.trackOnboardingAiResponseReceived(
    turnNumber: Int,
    assistantMessage: String,
    optionsCount: Int,
    optionsList: List<String>,
    durationSeconds: Float,
    detectedIntent: String? = null,
) = OnboardingAnalyticsTracks.trackOnboardingAiResponseReceived(
    turnNumber,
    assistantMessage,
    optionsCount,
    optionsList,
    durationSeconds,
    detectedIntent,
)

fun AnalyticsHelper.trackOnboardingAiSearchRedirect(
    turnNumber: Int,
    suggestedQuery: String?,
) = OnboardingAnalyticsTracks.trackOnboardingAiSearchRedirect(turnNumber, suggestedQuery)

fun AnalyticsHelper.trackOnboardingAiSynthesisCompleted(
    rowsCount: Int,
    podcastsCount: Int,
    durationSeconds: Float,
) = OnboardingAnalyticsTracks.trackOnboardingAiSynthesisCompleted(
    rowsCount,
    podcastsCount,
    durationSeconds,
)

fun AnalyticsHelper.trackOnboardingAiSynthesisFailed(errorMessage: String) =
    OnboardingAnalyticsTracks.trackOnboardingAiSynthesisFailed(errorMessage)

fun AnalyticsHelper.trackOnboardingAiDone(
    totalSubscribedCount: Int,
    subscribedPodcastsList: List<String>,
    didScrollSuggestions: Boolean,
    totalOnboardingTimeSeconds: Float,
    favoriteGenres: List<String>,
    entryPoint: String = "welcome_screen",
) = OnboardingAnalyticsTracks.trackOnboardingAiDone(
    totalSubscribedCount,
    subscribedPodcastsList,
    didScrollSuggestions,
    totalOnboardingTimeSeconds,
    favoriteGenres,
    entryPoint,
)

fun AnalyticsHelper.trackSearchPerformed(
    query: String,
    resultsCount: Int,
) = OnboardingAnalyticsTracks.trackSearchPerformed(query, resultsCount)

fun AnalyticsHelper.trackSearchPodcastSubscribed(
    podcastName: String,
    podcastId: String,
    totalSubscribedCount: Int,
) = OnboardingAnalyticsTracks.trackSearchPodcastSubscribed(
    podcastName,
    podcastId,
    totalSubscribedCount,
)
