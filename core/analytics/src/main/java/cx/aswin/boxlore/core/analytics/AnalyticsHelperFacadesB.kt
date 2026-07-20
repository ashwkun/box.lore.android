package cx.aswin.boxlore.core.analytics

/** AnalyticsHelper track façades part B (extensions; keeps helper under LargeClass). */

fun AnalyticsHelper.trackOnboardingSearchDone(
    entryPoint: String,
    totalSubscribedCount: Int,
    subscribedPodcastsList: List<String>,
    searchesPerformed: Int,
    timeSpentOnSearchSeconds: Float,
    totalOnboardingTimeSeconds: Float,
) = OnboardingAnalyticsTracks.trackOnboardingSearchDone(
    entryPoint,
    totalSubscribedCount,
    subscribedPodcastsList,
    searchesPerformed,
    timeSpentOnSearchSeconds,
    totalOnboardingTimeSeconds,
)

fun AnalyticsHelper.trackImportSheetOpened() = OnboardingAnalyticsTracks.trackImportSheetOpened()

fun AnalyticsHelper.trackOnboardingImportCompleted(
    importType: String,
    importedPodcastCount: Int,
    importedPodcastsList: List<String>,
    totalOnboardingTimeSeconds: Float,
    entryPoint: String = "welcome_screen",
) = OnboardingAnalyticsTracks.trackOnboardingImportCompleted(
    importType,
    importedPodcastCount,
    importedPodcastsList,
    totalOnboardingTimeSeconds,
    entryPoint,
)

fun AnalyticsHelper.trackOnboardingImportFailed(
    importType: String,
    errorMessage: String?,
) = OnboardingAnalyticsTracks.trackOnboardingImportFailed(importType, errorMessage)

fun AnalyticsHelper.trackOnboardingManualStepCompleted(
    stepName: String,
    selectionsCount: Int,
    selectionsList: List<String>,
    timeSpentSeconds: Float,
) = OnboardingAnalyticsTracks.trackOnboardingManualStepCompleted(
    stepName,
    selectionsCount,
    selectionsList,
    timeSpentSeconds,
)

fun AnalyticsHelper.trackOnboardingManualDone(
    totalSubscribedCount: Int,
    subscribedPodcastsList: List<String>,
    totalOnboardingTimeSeconds: Float,
    didSwitchFromAi: Boolean,
    favoriteGenres: Set<String>,
) = OnboardingAnalyticsTracks.trackOnboardingManualDone(
    totalSubscribedCount,
    subscribedPodcastsList,
    totalOnboardingTimeSeconds,
    didSwitchFromAi,
    favoriteGenres,
)

fun AnalyticsHelper.trackFeatureAnnouncementViewed(featureId: String) = DiscoveryAnalyticsTracks.trackFeatureAnnouncementViewed(featureId)

fun AnalyticsHelper.trackFeatureAnnouncementDismissed(featureId: String) =
    DiscoveryAnalyticsTracks.trackFeatureAnnouncementDismissed(featureId)

fun AnalyticsHelper.trackInAppAnnouncementViewed(
    category: String,
    hasImage: Boolean,
    hasAction: Boolean,
) = DiscoveryAnalyticsTracks.trackInAppAnnouncementViewed(
    category,
    hasImage,
    hasAction,
)

fun AnalyticsHelper.trackInAppAnnouncementDismissed(
    category: String,
    hasImage: Boolean,
    hasAction: Boolean,
) = DiscoveryAnalyticsTracks.trackInAppAnnouncementDismissed(
    category,
    hasImage,
    hasAction,
)

fun AnalyticsHelper.trackInAppAnnouncementAction(
    category: String,
    hasImage: Boolean,
    actionLabel: String,
) = DiscoveryAnalyticsTracks.trackInAppAnnouncementAction(
    category,
    hasImage,
    actionLabel,
)

fun AnalyticsHelper.trackNotificationPermissionRequested() = DiscoveryAnalyticsTracks.trackNotificationPermissionRequested()

fun AnalyticsHelper.trackNotificationPermissionDecided(isGranted: Boolean) =
    DiscoveryAnalyticsTracks.trackNotificationPermissionDecided(isGranted)

fun AnalyticsHelper.trackHomeHeroCarouselSwiped(
    maxCardIndexViewed: Int,
    totalCardsAvailable: Int,
) = DiscoveryAnalyticsTracks.trackHomeHeroCarouselSwiped(
    maxCardIndexViewed,
    totalCardsAvailable,
)
