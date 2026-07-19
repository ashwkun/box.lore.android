package cx.aswin.boxlore.core.analytics

import android.content.Context
import com.posthog.PostHog
import cx.aswin.boxlore.core.model.RankingAggregateTelemetry
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import java.time.Instant

object AnalyticsHelper : Analytics {

    @Volatile private var activeSeekSource: String = "scrubber"
    @Volatile private var activePauseReason: String = "user_voluntary"

    fun setSeekSource(source: String) {
        activeSeekSource = source
    }

    fun consumeSeekSource(): String {
        val src = activeSeekSource
        activeSeekSource = "scrubber" // Reset to default
        return src
    }

    fun setPauseReason(reason: String) {
        activePauseReason = reason
    }

    fun consumePauseReason(): String {
        val reason = activePauseReason
        activePauseReason = "user_voluntary" // Reset to default
        return reason
    }

    private const val PREFS_NAME = PrefsFileMigrator.Files.ANALYTICS
    private const val KEY_FIRST_LAUNCH = "is_first_launch"

    fun deriveGenrePersona(selectedGenres: Set<String>): Map<String, String> =
        GenrePersonaLogic.deriveGenrePersona(selectedGenres)

    // ── 1. App Launch ──────────────────────────────────────────────

    override fun trackFirstLaunchIfNecessary(context: Context) {
        val prefs = PrefsFileMigrator.open(
            context,
            newName = PREFS_NAME,
            oldName = PrefsFileMigrator.LegacyFiles.ANALYTICS,
        )
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            AnalyticsEmit.personSet(
                mapOf(
                    "first_seen_at" to Instant.now().toString(),
                    "onboarding_status" to "pending",
                ),
            )
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
    }

    /**
     * App Check health/adoption. Captured once per launch on builds that ship
     * App Check. `token_obtained` = whether the SDK produced an attestation
     * token; `provider` distinguishes debug vs Play Integrity. App version is
     * attached automatically by PostHog, so adoption can be sliced by build.
     */
    override fun trackAppCheckStatus(tokenObtained: Boolean, provider: String) {
        AnalyticsEmit.event(
            "app_check_status",
            mapOf(
                "token_obtained" to tokenObtained,
                "provider" to provider,
            ),
        )
    }

    override fun trackFirstEpisodePlayed() {
        AnalyticsEmit.event(
            "first_episode_played",
            mapOf(
                "\$set_once" to mapOf(
                    "first_episode_played_logged" to true,
                    "first_play_at" to Instant.now().toString(),
                ),
            ),
        )
    }

    // ── Feedback / NPS (glossary: feedback_submitted) ───────────────
    // PostHog survey display conditions should key off feedback_submitted
    // after the PR7 dashboard rebuild (see glossary checklist).

    /** Deferred automatic trigger, fired on app open once the user hits the eligibility milestone. */
    override fun trackSurveyNpsEligible(completedEpisodes: Int?, triggerContext: String) {
        AnalyticsEmit.event(
            "feedback_submitted",
            buildMap {
                put("feedback_type", "nps_eligible")
                put("source", triggerContext)
                put("trigger_type", "automatic")
                put("trigger_context", triggerContext)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
        deliverSurveyTriggerEvent()
    }

    /** Manual trigger from a long-press or a remote console feature flag. */
    override fun trackSurveyNpsManualTrigger(source: String) {
        AnalyticsEmit.event(
            "feedback_submitted",
            mapOf(
                "feedback_type" to "nps_manual",
                "source" to source,
                "trigger_source" to source,
                "trigger_type" to "manual",
                "trigger_context" to if (source == "remote_flag") "console" else "manual",
            ),
        )
        deliverSurveyTriggerEvent()
    }

    /** Flush and refresh flags so the SDK evaluates survey display conditions immediately. */
    private fun deliverSurveyTriggerEvent() {
        try {
            PostHog.flush()
            PostHog.reloadFeatureFlags()
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsHelper", "Failed to deliver survey trigger", e)
        }
    }

    /** Tracks when a proactive engagement modal (NPS, review, etc.) is shown. */
    override fun trackEngagementPromptShown(promptType: String, source: String, completedEpisodes: Int?) {
        AnalyticsEmit.event(
            "feedback_submitted",
            buildMap {
                put("feedback_type", promptType)
                put("source", source)
                completedEpisodes?.let { put("completed_episodes", it) }
            },
        )
    }

    /** Fired when a promoter (NPS 8+) is routed to the Play Store review sheet on a later open. */
    override fun trackPromoterReviewHandoff(npsScore: Int?) {
        AnalyticsEmit.event(
            "feedback_submitted",
            buildMap {
                put("feedback_type", "promoter_review_handoff")
                npsScore?.let {
                    put("score", it)
                    put("nps_score", it)
                }
            },
        )
    }

    fun resetIdentity() {
        AnalyticsEmit.event("identity_reset", mapOf("reason" to "user_reset"))
        PostHog.reset()
    }

    fun getDistinctId(): String {
        return PostHog.distinctId()
    }

    /** Phase C — stopped in PR7; no-op until PR9. */
    @Suppress("UNUSED_PARAMETER")
    fun trackLateNightSafeguardDecision(decision: String, durationMinutes: Int? = null) = Unit

    data class SmartQueueRefillEvent(
        val triggeringEpisodeId: String,
        val triggeringPodcastGenre: String,
        val refilledCount: Int,
        val recommendationSources: List<String>,
        val refilledEpisodeIds: List<String>,
        val region: String? = null,
        val sourceCounts: Map<String, Int> = emptyMap(),
        val usedServerRecommendations: Boolean = false
    )



    // Domain track façades (bodies in *AnalyticsTracks)
    fun trackHomeImportBannerImpression() = OnboardingAnalyticsTracks.trackHomeImportBannerImpression()
    fun trackHomeImportBannerClicked(action: String) = OnboardingAnalyticsTracks.trackHomeImportBannerClicked(action)
    fun trackHomeImportBannerDismissed() = OnboardingAnalyticsTracks.trackHomeImportBannerDismissed()
    fun trackOnboardingStarted(entryPoint: String = "welcome_screen") = OnboardingAnalyticsTracks.trackOnboardingStarted(entryPoint)
    fun trackOnboardingFlowSelected(flowType: String, entryPoint: String = "welcome_screen") = OnboardingAnalyticsTracks.trackOnboardingFlowSelected(flowType, entryPoint)
    fun trackOnboardingSkipped(screen: String, totalOnboardingTimeSeconds: Float) = OnboardingAnalyticsTracks.trackOnboardingSkipped(screen, totalOnboardingTimeSeconds)
    fun trackOnboardingAiTurnSubmitted(
    turnNumber: Int,
    selectedOptions: Set<String>,
    customInputText: String,
    timeSpentSeconds: Float
) = OnboardingAnalyticsTracks.trackOnboardingAiTurnSubmitted(turnNumber, selectedOptions, customInputText, timeSpentSeconds)
    fun trackOnboardingAiResponseReceived(
    turnNumber: Int,
    assistantMessage: String,
    optionsCount: Int,
    optionsList: List<String>,
    durationSeconds: Float,
    detectedIntent: String? = null
) = OnboardingAnalyticsTracks.trackOnboardingAiResponseReceived(turnNumber, assistantMessage, optionsCount, optionsList, durationSeconds, detectedIntent)
    fun trackOnboardingAiSearchRedirect(turnNumber: Int, suggestedQuery: String?) = OnboardingAnalyticsTracks.trackOnboardingAiSearchRedirect(turnNumber, suggestedQuery)
    fun trackOnboardingAiSynthesisCompleted(rowsCount: Int, podcastsCount: Int, durationSeconds: Float) = OnboardingAnalyticsTracks.trackOnboardingAiSynthesisCompleted(rowsCount, podcastsCount, durationSeconds)
    fun trackOnboardingAiSynthesisFailed(errorMessage: String) = OnboardingAnalyticsTracks.trackOnboardingAiSynthesisFailed(errorMessage)
    fun trackOnboardingAiDone(
    totalSubscribedCount: Int,
    subscribedPodcastsList: List<String>,
    didScrollSuggestions: Boolean,
    totalOnboardingTimeSeconds: Float,
    favoriteGenres: List<String>,
    entryPoint: String = "welcome_screen"
) = OnboardingAnalyticsTracks.trackOnboardingAiDone(totalSubscribedCount, subscribedPodcastsList, didScrollSuggestions, totalOnboardingTimeSeconds, favoriteGenres, entryPoint)
    fun trackSearchPerformed(query: String, resultsCount: Int) = OnboardingAnalyticsTracks.trackSearchPerformed(query, resultsCount)
    fun trackSearchPodcastSubscribed(podcastName: String, podcastId: String, totalSubscribedCount: Int) = OnboardingAnalyticsTracks.trackSearchPodcastSubscribed(podcastName, podcastId, totalSubscribedCount)
    fun trackOnboardingSearchDone(
    entryPoint: String,
    totalSubscribedCount: Int,
    subscribedPodcastsList: List<String>,
    searchesPerformed: Int,
    timeSpentOnSearchSeconds: Float,
    totalOnboardingTimeSeconds: Float
) = OnboardingAnalyticsTracks.trackOnboardingSearchDone(entryPoint, totalSubscribedCount, subscribedPodcastsList, searchesPerformed, timeSpentOnSearchSeconds, totalOnboardingTimeSeconds)
    fun trackImportSheetOpened() = OnboardingAnalyticsTracks.trackImportSheetOpened()
    fun trackOnboardingImportCompleted(
    importType: String,
    importedPodcastCount: Int,
    importedPodcastsList: List<String>,
    totalOnboardingTimeSeconds: Float,
    entryPoint: String = "welcome_screen"
) = OnboardingAnalyticsTracks.trackOnboardingImportCompleted(importType, importedPodcastCount, importedPodcastsList, totalOnboardingTimeSeconds, entryPoint)
    fun trackOnboardingImportFailed(importType: String, errorMessage: String?) = OnboardingAnalyticsTracks.trackOnboardingImportFailed(importType, errorMessage)
    fun trackOnboardingManualStepCompleted(
    stepName: String,
    selectionsCount: Int,
    selectionsList: List<String>,
    timeSpentSeconds: Float
) = OnboardingAnalyticsTracks.trackOnboardingManualStepCompleted(stepName, selectionsCount, selectionsList, timeSpentSeconds)
    fun trackOnboardingManualDone(
    totalSubscribedCount: Int,
    subscribedPodcastsList: List<String>,
    totalOnboardingTimeSeconds: Float,
    didSwitchFromAi: Boolean,
    favoriteGenres: Set<String>
) = OnboardingAnalyticsTracks.trackOnboardingManualDone(totalSubscribedCount, subscribedPodcastsList, totalOnboardingTimeSeconds, didSwitchFromAi, favoriteGenres)
    fun trackFeatureAnnouncementViewed(featureId: String) = DiscoveryAnalyticsTracks.trackFeatureAnnouncementViewed(featureId)
    fun trackFeatureAnnouncementDismissed(featureId: String) = DiscoveryAnalyticsTracks.trackFeatureAnnouncementDismissed(featureId)
    fun trackInAppAnnouncementViewed(
    category: String,
    hasImage: Boolean,
    hasAction: Boolean,
) = DiscoveryAnalyticsTracks.trackInAppAnnouncementViewed(category, hasImage, hasAction)
    fun trackInAppAnnouncementDismissed(
    category: String,
    hasImage: Boolean,
    hasAction: Boolean,
) = DiscoveryAnalyticsTracks.trackInAppAnnouncementDismissed(category, hasImage, hasAction)
    fun trackInAppAnnouncementAction(
    category: String,
    hasImage: Boolean,
    actionLabel: String,
) = DiscoveryAnalyticsTracks.trackInAppAnnouncementAction(category, hasImage, actionLabel)
    fun trackNotificationPermissionRequested() = DiscoveryAnalyticsTracks.trackNotificationPermissionRequested()
    fun trackNotificationPermissionDecided(isGranted: Boolean) = DiscoveryAnalyticsTracks.trackNotificationPermissionDecided(isGranted)
    fun trackHomeHeroCarouselSwiped(maxCardIndexViewed: Int, totalCardsAvailable: Int) = DiscoveryAnalyticsTracks.trackHomeHeroCarouselSwiped(maxCardIndexViewed, totalCardsAvailable)
    fun trackCuratedBlockImpression(blockTitle: String, vibeIds: List<String>) = DiscoveryAnalyticsTracks.trackCuratedBlockImpression(blockTitle, vibeIds)
    fun trackHomeRecommendationsImpression(
    recommendationsCount: Int,
    episodeIds: List<String>,
    timeBlockTitle: String?
) = DiscoveryAnalyticsTracks.trackHomeRecommendationsImpression(recommendationsCount, episodeIds, timeBlockTitle)
    fun trackHomeRecommendationCardTapped(
    episodeId: String,
    episodeTitle: String?,
    podcastId: String,
    podcastName: String?,
    positionIndex: Int,
    timeBlockTitle: String?
) = DiscoveryAnalyticsTracks.trackHomeRecommendationCardTapped(episodeId, episodeTitle, podcastId, podcastName, positionIndex, timeBlockTitle)
    fun trackExploreRecommendationsImpression(
    recommendationsCount: Int,
    episodeIds: List<String>
) = DiscoveryAnalyticsTracks.trackExploreRecommendationsImpression(recommendationsCount, episodeIds)
    fun trackExploreRecommendationCardTapped(
    episodeId: String,
    episodeTitle: String?,
    podcastId: String,
    podcastName: String?,
    positionIndex: Int
) = DiscoveryAnalyticsTracks.trackExploreRecommendationCardTapped(episodeId, episodeTitle, podcastId, podcastName, positionIndex)
    fun trackPodcastInfoScreenViewed(
    podcastId: String,
    podcastName: String? = null,
    entryPoint: String? = null,
    genreFilter: String? = null,
    scrollDepth: Int? = null,
    searchQuery: String? = null
) = DiscoveryAnalyticsTracks.trackPodcastInfoScreenViewed(podcastId, podcastName, entryPoint, genreFilter, scrollDepth, searchQuery)
    fun trackPodcastSubscriptionToggled(
    podcastId: String,
    podcastName: String?,
    isSubscribed: Boolean,
    entryPoint: String
) = DiscoveryAnalyticsTracks.trackPodcastSubscriptionToggled(podcastId, podcastName, isSubscribed, entryPoint)
    fun trackPodcastInfoScreenSession(
    podcastId: String,
    podcastName: String,
    timeSpentSeconds: Float,
    wasSubscribed: Boolean,
    didSubscribe: Boolean,
    didUnsubscribe: Boolean,
    didSearch: Boolean,
    didSortEpisodes: Boolean,
    episodesPlayedCount: Int,
    episodesClickedCount: Int
) = DiscoveryAnalyticsTracks.trackPodcastInfoScreenSession(podcastId, podcastName, timeSpentSeconds, wasSubscribed, didSubscribe, didUnsubscribe, didSearch, didSortEpisodes, episodesPlayedCount, episodesClickedCount)
    fun trackCuratedCardTapped(
    podcastId: String,
    podcastName: String?,
    vibeId: String,
    positionIndex: Int
) = DiscoveryAnalyticsTracks.trackCuratedCardTapped(podcastId, podcastName, vibeId, positionIndex)

    fun trackEpisodeInfoScreenViewed(properties: Map<String, Any>) =
        DiscoveryAnalyticsTracks.trackEpisodeInfoScreenViewed(properties)

    fun trackEpisodeInfoScreenSession(properties: Map<String, Any>) =
        DiscoveryAnalyticsTracks.trackEpisodeInfoScreenSession(properties)

    fun trackProxyFallbackTriggered(
        imageHost: String,
        proxyWidth: Int,
        sampleMultiplier: Int = 10,
    ) = DiscoveryAnalyticsTracks.trackProxyFallbackTriggered(imageHost, proxyWidth, sampleMultiplier)

    fun setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed: Int) =
        OnboardingAnalyticsTracks.setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed)

    fun trackPlaybackStarted(
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
    entryPointContext: Map<String, Any>? = null
) = PlaybackAnalyticsTracks.trackPlaybackStarted(podcastId, podcastName, podcastGenre, episodeId, episodeTitle, startPositionSeconds, totalDurationSeconds, isRepeating, isSubscribed, entryPoint, entryPointContext)
    fun trackPlaybackPaused(
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
    pauseReason: String = "user_voluntary"
) = PlaybackAnalyticsTracks.trackPlaybackPaused(podcastId, podcastName, podcastGenre, episodeId, episodeTitle, durationPlayedSeconds, totalBufferedTimeSeconds, totalDurationSeconds, isCompleted, entryPoint, entryPointContext, queueSize, pauseReason)
    fun trackPlaybackCompleted(
    podcastId: String?,
    podcastName: String?,
    podcastGenre: String?,
    episodeId: String,
    episodeTitle: String?,
    totalDurationSeconds: Float,
    entryPoint: String? = null,
    entryPointContext: Map<String, Any>? = null
) = PlaybackAnalyticsTracks.trackPlaybackCompleted(podcastId, podcastName, podcastGenre, episodeId, episodeTitle, totalDurationSeconds, entryPoint, entryPointContext)
    fun trackPlaybackHeartbeat(
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
        podcastId, podcastName, episodeId, episodeTitle,
        currentPositionSeconds, totalDurationSeconds, heartbeatPercentage, heartbeatType, entryPoint,
    )
    fun trackPlaybackSeeked(
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
        podcastId, podcastName, episodeId, episodeTitle,
        fromPositionSeconds, toPositionSeconds, totalDurationSeconds, seekSource, entryPoint,
    )
    fun trackPlaybackBuffering(
        episodeId: String? = null,
        podcastId: String? = null,
        entryPoint: String? = null,
        bufferDurationMs: Long? = null,
    ) = PlaybackAnalyticsTracks.trackPlaybackBuffering(episodeId, podcastId, entryPoint, bufferDurationMs)
    fun trackPlaybackError(
    errorCode: String,
    errorMessage: String,
    podcastId: String?,
    episodeId: String?,
    podcastName: String? = null,
    episodeTitle: String? = null
) = PlaybackAnalyticsTracks.trackPlaybackError(errorCode, errorMessage, podcastId, episodeId, podcastName, episodeTitle)
    fun trackExploreScreenViewed(sourceEntryPoint: String? = null) = PlaybackAnalyticsTracks.trackExploreScreenViewed(sourceEntryPoint)
    fun trackExploreSearchPerformed(query: String, resultsCount: Int) = PlaybackAnalyticsTracks.trackExploreSearchPerformed(query, resultsCount)
    fun trackExploreScreenSession(
    timeSpentSeconds: Float,
    categoriesClickedCount: Int,
    vibesClickedCount: Int,
    searchesPerformedCount: Int,
    podcastsClickedCount: Int,
    maxScrollDepth: Int,
    finalCategoryState: String,
    finalVibeState: String?,
    finalSearchQuery: String?
) = PlaybackAnalyticsTracks.trackExploreScreenSession(timeSpentSeconds, categoriesClickedCount, vibesClickedCount, searchesPerformedCount, podcastsClickedCount, maxScrollDepth, finalCategoryState, finalVibeState, finalSearchQuery)
    fun trackLibraryHubViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryHubViewed(sourceEntryPoint)
    fun trackLibraryHubSession(timeSpentSeconds: Float, navigatedTo: String?) = LibraryAnalyticsTracks.trackLibraryHubSession(timeSpentSeconds, navigatedTo)
    fun trackLibrarySubscriptionsViewed(sourceEntryPoint: String, initialTab: String) = LibraryAnalyticsTracks.trackLibrarySubscriptionsViewed(sourceEntryPoint, initialTab)
    fun trackLibrarySubscriptionsSession(
    timeSpentSeconds: Float,
    tabSwitchesCount: Int,
    didSearch: Boolean,
    finalSearchQuery: String?,
    podcastsClickedCount: Int,
    episodesClickedCount: Int
) = LibraryAnalyticsTracks.trackLibrarySubscriptionsSession(timeSpentSeconds, tabSwitchesCount, didSearch, finalSearchQuery, podcastsClickedCount, episodesClickedCount)
    fun trackLibraryLikedViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryLikedViewed(sourceEntryPoint)
    fun trackLibraryLikedSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesUnlikedCount: Int) = LibraryAnalyticsTracks.trackLibraryLikedSession(timeSpentSeconds, episodesClickedCount, episodesUnlikedCount)
    fun trackLibraryDownloadsViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryDownloadsViewed(sourceEntryPoint)
    fun trackLibraryDownloadsSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesDeletedCount: Int) = LibraryAnalyticsTracks.trackLibraryDownloadsSession(timeSpentSeconds, episodesClickedCount, episodesDeletedCount)
    fun trackLibraryHistoryViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackLibraryHistoryViewed(sourceEntryPoint)
    fun trackLibraryHistorySession(timeSpentSeconds: Float, episodesClickedCount: Int, itemsDeletedCount: Int) = LibraryAnalyticsTracks.trackLibraryHistorySession(timeSpentSeconds, episodesClickedCount, itemsDeletedCount)
    fun trackTopControlbarInteraction(action: String, screen: String) = LibraryAnalyticsTracks.trackTopControlbarInteraction(action, screen)
    fun trackSettingsScreenViewed(sourceEntryPoint: String) = LibraryAnalyticsTracks.trackSettingsScreenViewed(sourceEntryPoint)
    fun trackSettingsInteraction(action: String, value: String? = null) = LibraryAnalyticsTracks.trackSettingsInteraction(action, value)
    fun trackMiniPlayerInteraction(
    action: String,
    podcastId: String?,
    episodeId: String?,
    podcastName: String? = null,
    episodeTitle: String? = null
) = LibraryAnalyticsTracks.trackMiniPlayerInteraction(action, podcastId, episodeId, podcastName, episodeTitle)
    fun trackFullPlayerScreenSession(
    podcastId: String?,
    episodeId: String?,
    metrics: Map<String, Any>,
    podcastName: String? = null,
    episodeTitle: String? = null
) = LibraryAnalyticsTracks.trackFullPlayerScreenSession(podcastId, episodeId, metrics, podcastName, episodeTitle)
    fun trackNotificationTapped() = LibraryAnalyticsTracks.trackNotificationTapped()
    fun trackDownloadCompleted(fileSizeMb: Float) = LibraryAnalyticsTracks.trackDownloadCompleted(fileSizeMb)
    fun trackDownloadFailed(errorReason: String) = LibraryAnalyticsTracks.trackDownloadFailed(errorReason)
    fun trackPlayMixClicked(count: Int) = LibraryAnalyticsTracks.trackPlayMixClicked(count)
    fun trackHomePodcastFiltered(podcastId: String, title: String) = LibraryAnalyticsTracks.trackHomePodcastFiltered(podcastId, title)
    fun trackLibrarySubscriptionsLayoutToggled(isGridView: Boolean) = LibraryAnalyticsTracks.trackLibrarySubscriptionsLayoutToggled(isGridView)
    fun trackLibrarySubscriptionsSortChanged(sortMethod: String, tab: String) = LibraryAnalyticsTracks.trackLibrarySubscriptionsSortChanged(sortMethod, tab)
    fun trackLibrarySubscriptionsGenreFiltered(genreName: String, tab: String) = LibraryAnalyticsTracks.trackLibrarySubscriptionsGenreFiltered(genreName, tab)
    fun trackSmartQueueRefilled(event: AnalyticsHelper.SmartQueueRefillEvent) = QueueContentAnalyticsTracks.trackSmartQueueRefilled(event)
    fun trackQueueReordered(
    episodeId: String,
    fromPosition: Int,
    toPosition: Int,
    contextType: String?
) = QueueContentAnalyticsTracks.trackQueueReordered(episodeId, fromPosition, toPosition, contextType)
    fun trackLoreQueueConflictShown(episodeId: String, normalQueueSize: Int) = QueueContentAnalyticsTracks.trackLoreQueueConflictShown(episodeId, normalQueueSize)
    fun trackLoreQueueConflictResult(episodeId: String, result: String) = QueueContentAnalyticsTracks.trackLoreQueueConflictResult(episodeId, result)
    fun trackSmartQueueEpisodeSkipped(
    episodeId: String,
    recommendationSource: String,
    positionInQueue: Int
) = QueueContentAnalyticsTracks.trackSmartQueueEpisodeSkipped(episodeId, recommendationSource, positionInQueue)
    fun trackOfflineModeEntered() = QueueContentAnalyticsTracks.trackOfflineModeEntered()
    fun trackDiscoverCategoryFiltered(categoryName: String) = QueueContentAnalyticsTracks.trackDiscoverCategoryFiltered(categoryName)
    fun trackAutoChaptersRequested(episodeId: String, podcastId: String?, audioUrl: String) = QueueContentAnalyticsTracks.trackAutoChaptersRequested(episodeId, podcastId, audioUrl)
    fun trackAutoChaptersCompleted(episodeId: String, podcastId: String?, durationSeconds: Float, chaptersCount: Int) = QueueContentAnalyticsTracks.trackAutoChaptersCompleted(episodeId, podcastId, durationSeconds, chaptersCount)
    fun trackAutoChaptersFailed(episodeId: String, podcastId: String?, errorMessage: String) = QueueContentAnalyticsTracks.trackAutoChaptersFailed(episodeId, podcastId, errorMessage)
    fun trackAutoTranscriptRequested(episodeId: String, podcastId: String?, audioUrl: String) = QueueContentAnalyticsTracks.trackAutoTranscriptRequested(episodeId, podcastId, audioUrl)
    fun trackAutoTranscriptCompleted(episodeId: String, podcastId: String?, durationSeconds: Float, linesCount: Int) = QueueContentAnalyticsTracks.trackAutoTranscriptCompleted(episodeId, podcastId, durationSeconds, linesCount)
    fun trackAutoTranscriptFailed(episodeId: String, podcastId: String?, errorMessage: String) = QueueContentAnalyticsTracks.trackAutoTranscriptFailed(episodeId, podcastId, errorMessage)
    fun trackDailyBriefingBannerTapped(region: String, date: String) = QueueContentAnalyticsTracks.trackDailyBriefingBannerTapped(region, date)
    fun trackDailyBriefingPlayClicked(region: String, date: String, source: String) = QueueContentAnalyticsTracks.trackDailyBriefingPlayClicked(region, date, source)
    fun trackDailyBriefingPauseClicked(region: String, date: String, source: String) = QueueContentAnalyticsTracks.trackDailyBriefingPauseClicked(region, date, source)
    fun trackDailyBriefingInteraction(
    action: String,
    region: String,
    date: String,
    extraProps: Map<String, Any> = emptyMap()
) = QueueContentAnalyticsTracks.trackDailyBriefingInteraction(action, region, date, extraProps)
    fun trackDailyBriefingRegionChanged(previousRegion: String, newRegion: String, date: String) = QueueContentAnalyticsTracks.trackDailyBriefingRegionChanged(previousRegion, newRegion, date)
    fun trackDailyBriefingRelatedEpisodeClicked(
    region: String,
    date: String,
    chapterIndex: Int,
    episodeId: String,
    episodeTitle: String,
    podcastId: String,
    podcastTitle: String
) = QueueContentAnalyticsTracks.trackDailyBriefingRelatedEpisodeClicked(region, date, chapterIndex, episodeId, episodeTitle, podcastId, podcastTitle)
    fun trackDailyBriefingCardImpression(region: String, date: String, playbackStatus: String) = QueueContentAnalyticsTracks.trackDailyBriefingCardImpression(region, date, playbackStatus)
    fun trackDailyBriefingScreenViewed(region: String, date: String, source: String? = null) = QueueContentAnalyticsTracks.trackDailyBriefingScreenViewed(region, date, source)
    fun trackNavTabClicked(tabName: String) = QueueContentAnalyticsTracks.trackNavTabClicked(tabName)
    fun trackLearnScreenViewed() = QueueContentAnalyticsTracks.trackLearnScreenViewed()
    fun trackLearnCardDismissed(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) = QueueContentAnalyticsTracks.trackLearnCardDismissed(episodeId, episodeTitle, podcastId, podcastTitle)
    fun trackLearnCardQueued(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) = QueueContentAnalyticsTracks.trackLearnCardQueued(episodeId, episodeTitle, podcastId, podcastTitle)
    fun trackLearnCardInfoClicked(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) = QueueContentAnalyticsTracks.trackLearnCardInfoClicked(episodeId, episodeTitle, podcastId, podcastTitle)
    fun trackLearnCardPodcastClicked(podcastId: String?, podcastTitle: String?) = QueueContentAnalyticsTracks.trackLearnCardPodcastClicked(podcastId, podcastTitle)
    fun trackLearnCardPlayClicked(episodeId: String, episodeTitle: String?, podcastId: String?, podcastTitle: String?) = QueueContentAnalyticsTracks.trackLearnCardPlayClicked(episodeId, episodeTitle, podcastId, podcastTitle)
    fun trackLearnScreenSession(
    timeSpentSeconds: Float,
    cardsDismissedCount: Int,
    cardsQueuedCount: Int,
    playsCount: Int,
    podcastsClickedCount: Int,
    infosClickedCount: Int
) = QueueContentAnalyticsTracks.trackLearnScreenSession(timeSpentSeconds, cardsDismissedCount, cardsQueuedCount, playsCount, podcastsClickedCount, infosClickedCount)

    /** Phase C — stopped in PR7; no-op until PR9 `adaptive_ranking_status`. */
    override fun trackAdaptiveRankingStatus(statuses: List<RankingAggregateTelemetry>) {
        // Intentionally no-op (Phase C).
    }

    override fun flush() {
        try {
            PostHog.flush()
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsHelper", "Failed to flush PostHog", e)
        }
    }

    override fun capture(
        event: String,
        properties: Map<String, Any>,
    ) {
        AnalyticsEmit.event(event, properties)
    }
}
