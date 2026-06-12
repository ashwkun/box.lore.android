package cx.aswin.boxcast.core.data.analytics

import android.content.Context
import com.posthog.PostHog
import java.util.Calendar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AnalyticsHelper {

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

    private const val PREFS_NAME = "boxcast_analytics_prefs"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"

    // ── Timestamp Helpers ──────────────────────────────────────────
    
    /** User's local timestamp in ISO format (e.g., 2026-05-09T14:30:00-04:00) */
    private fun userLocalTimestamp(): String {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
    }

    /** Global timestamp always in IST (Asia/Kolkata) */
    private fun globalIstTimestamp(): String {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.of("Asia/Kolkata"))
            .format(Instant.now())
    }

    // ── Genre Persona Logic ────────────────────────────────────────

    private val KNOWLEDGE_GENRES = setOf(
        "News", "Technology", "Business", "Education", "Science", "History", "Government"
    )
    private val ENTERTAINMENT_GENRES = setOf(
        "Comedy", "True Crime", "TV & Film", "Fiction", "Music", "Arts"
    )
    private val LIFESTYLE_GENRES = setOf(
        "Health", "Sports", "Society & Culture", "Religion & Spirituality", "Kids & Family", "Leisure"
    )

    fun deriveGenrePersona(selectedGenres: Set<String>): Map<String, String> {
        val knowledgeCount = selectedGenres.count { it in KNOWLEDGE_GENRES }
        val entertainmentCount = selectedGenres.count { it in ENTERTAINMENT_GENRES }
        val lifestyleCount = selectedGenres.count { it in LIFESTYLE_GENRES }

        // 1. Breadth
        val categoriesHit = listOf(knowledgeCount, entertainmentCount, lifestyleCount).count { it > 0 }
        val breadth = when (categoriesHit) {
            1 -> "highly_focused"
            2 -> "balanced"
            3 -> "broad_explorer"
            else -> "unknown"
        }

        // 2. Enthusiasm
        val totalCount = selectedGenres.size
        val enthusiasm = when {
            totalCount <= 2 -> "casual"
            totalCount <= 5 -> "engaged"
            else -> "obsessive"
        }

        // 3. Archetypes (listener_profile)
        val profile = when {
            selectedGenres.containsAll(listOf("True Crime", "Comedy")) -> "lighthearted_detective"
            selectedGenres.containsAll(listOf("Sports", "Leisure")) -> "sports_fanatic"
            listOf("News", "Government", "History").count { it in selectedGenres } >= 2 -> "civic_junkie"
            listOf("Technology", "Business", "Science").count { it in selectedGenres } >= 2 -> "tech_professional"
            listOf("Health", "Science", "Education").count { it in selectedGenres } >= 2 -> "wellness_intellectual"
            listOf("Society & Culture", "Religion & Spirituality", "Arts").count { it in selectedGenres } >= 2 -> "cultural_philosopher"
            else -> {
                // Fallback majority bucket
                val max = maxOf(knowledgeCount, entertainmentCount, lifestyleCount)
                if (max == 0) "eclectic_explorer"
                else {
                    val tiedCount = listOf(knowledgeCount, entertainmentCount, lifestyleCount).count { it == max }
                    if (tiedCount > 1) "eclectic_explorer"
                    else when (max) {
                        knowledgeCount -> "knowledge_seeker"
                        entertainmentCount -> "entertainment_fan"
                        else -> "lifestyle_enthusiast"
                    }
                }
            }
        }

        return mapOf(
            "listener_profile" to profile,
            "genre_breadth" to breadth,
            "genre_enthusiasm" to enthusiasm
        )
    }

    // ── 1. App Launch ──────────────────────────────────────────────

    fun trackFirstLaunchIfNecessary(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            PostHog.capture(
                event = "\$set",
                userProperties = mapOf(
                    "first_seen_date" to Instant.now().toString(),
                    "onboarding_status" to "pending"
                )
            )
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
    }

    fun trackFirstEpisodePlayed() {
        PostHog.capture(
            "first_episode_played",
            properties = mapOf(
                "\$set_once" to mapOf("first_episode_played_logged" to true)
            )
        )
    }

    // ── 2. Onboarding Started & Flow Selection ──────────────────────

    fun trackOnboardingStarted() {
        PostHog.capture(
            event = "onboarding_started",
            properties = mapOf(
                "user_local_timestamp" to userLocalTimestamp(),
                "global_ist_timestamp" to globalIstTimestamp()
            )
        )
    }

    fun trackOnboardingFlowSelected(flowType: String) {
        PostHog.capture(
            event = "onboarding_flow_selected",
            properties = mapOf(
                "flow_type" to flowType
            )
        )
    }

    fun trackOnboardingSkipped(screen: String, totalOnboardingTimeSeconds: Float) {
        PostHog.capture(
            event = "onboarding_completed",
            properties = mapOf(
                "method" to "skip_welcome",
                "screen" to screen,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds
            )
        )

        PostHog.capture(
            event = "\$set",
            userProperties = mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "skip",
                "user_intent" to "casual_browser"
            )
        )
    }

    // ── 3. AI Chat Onboarding Flow ──────────────────────────────────

    fun trackOnboardingAiTurnSubmitted(turnNumber: Int, selectedOptions: Set<String>, hasCustomInput: Boolean, timeSpentSeconds: Float) {
        PostHog.capture(
            event = "onboarding_ai_turn_submitted",
            properties = mapOf(
                "turn_number" to turnNumber,
                "selected_options" to selectedOptions.toList(),
                "has_custom_input" to hasCustomInput,
                "time_spent_seconds" to timeSpentSeconds
            )
        )
    }

    fun trackOnboardingAiSynthesisStarted(totalTurns: Int) {
        PostHog.capture(
            event = "onboarding_ai_synthesis_started",
            properties = mapOf(
                "total_turns" to totalTurns
            )
        )
    }

    fun trackOnboardingAiSynthesisCompleted(rowsCount: Int, podcastsCount: Int, durationSeconds: Float) {
        PostHog.capture(
            event = "onboarding_ai_synthesis_completed",
            properties = mapOf(
                "rows_count" to rowsCount,
                "podcasts_count" to podcastsCount,
                "duration_seconds" to durationSeconds
            )
        )
    }

    fun trackOnboardingAiSynthesisFailed(errorMessage: String) {
        PostHog.capture(
            event = "onboarding_ai_synthesis_failed",
            properties = mapOf(
                "error_message" to errorMessage
            )
        )
    }

    fun trackOnboardingAiDone(
        totalSubscribedCount: Int,
        didScrollSuggestions: Boolean,
        totalOnboardingTimeSeconds: Float,
        favoriteGenres: List<String>
    ) {
        PostHog.capture(
            event = "onboarding_completed",
            properties = mapOf(
                "method" to "ai_suggestions_done",
                "screen" to "ai_suggestions_screen",
                "total_subscribed_count" to totalSubscribedCount,
                "did_scroll_suggestions" to didScrollSuggestions,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds
            )
        )

        PostHog.capture(
            event = "\$set",
            userProperties = mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "ai_chat",
                "user_intent" to "ai_guided_listener",
                "initial_podcasts_subscribed" to totalSubscribedCount,
                "favorite_genres" to favoriteGenres
            )
        )
    }

    // ── 4. Search & Import Onboarding Flows ─────────────────────────

    fun trackSearchPerformed(query: String, resultsCount: Int) {
        PostHog.capture(
            event = "onboarding_search_performed",
            properties = mapOf(
                "search_query" to query,
                "results_count" to resultsCount
            )
        )
    }

    fun trackSearchPodcastSubscribed(podcastName: String, podcastId: String, totalSubscribedCount: Int) {
        PostHog.capture(
            event = "onboarding_search_podcast_subscribed",
            properties = mapOf(
                "podcast_name" to podcastName,
                "podcast_id" to podcastId,
                "total_subscribed_count" to totalSubscribedCount
            )
        )
    }

    fun trackOnboardingSearchDone(
        entryPoint: String,
        totalSubscribedCount: Int,
        searchesPerformed: Int,
        timeSpentOnSearchSeconds: Float,
        totalOnboardingTimeSeconds: Float
    ) {
        PostHog.capture(
            event = "onboarding_completed",
            properties = mapOf(
                "method" to "search_done",
                "screen" to "search_screen",
                "entry_point" to entryPoint,
                "total_subscribed_count" to totalSubscribedCount,
                "searches_performed" to searchesPerformed,
                "time_spent_on_search_seconds" to timeSpentOnSearchSeconds,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds
            )
        )

        PostHog.capture(
            event = "\$set",
            userProperties = mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "search",
                "user_intent" to "targeted_listener",
                "initial_podcasts_subscribed" to totalSubscribedCount
            )
        )
    }

    fun trackImportSheetOpened() {
        PostHog.capture(event = "onboarding_import_sheet_opened")
    }

    fun trackOnboardingImportCompleted(
        importType: String,
        importedPodcastCount: Int,
        totalOnboardingTimeSeconds: Float
    ) {
        PostHog.capture(
            event = "onboarding_completed",
            properties = mapOf(
                "method" to "import",
                "import_type" to importType,
                "screen" to "welcome_screen",
                "imported_podcast_count" to importedPodcastCount,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds
            )
        )

        PostHog.capture(
            event = "\$set",
            userProperties = mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "import",
                "user_intent" to "migrating_power_user",
                "initial_podcasts_subscribed" to importedPodcastCount
            )
        )
    }

    fun trackOnboardingImportFailed(importType: String, errorMessage: String?) {
        PostHog.capture(
            event = "onboarding_import_failed",
            properties = mapOf(
                "import_type" to importType,
                "error_message" to (errorMessage ?: "Unknown error")
            )
        )
    }

    // ── 5. Manual Genre Flow (Legacy / Switch) ──────────────────────

    fun trackOnboardingManualStepCompleted(stepName: String, selectionsCount: Int, timeSpentSeconds: Float) {
        PostHog.capture(
            event = "onboarding_manual_step_completed",
            properties = mapOf(
                "step_name" to stepName,
                "selections_count" to selectionsCount,
                "time_spent_seconds" to timeSpentSeconds
            )
        )
    }

    fun trackOnboardingManualDone(
        totalSubscribedCount: Int,
        totalOnboardingTimeSeconds: Float,
        didSwitchFromAi: Boolean,
        favoriteGenres: Set<String>
    ) {
        PostHog.capture(
            event = "onboarding_completed",
            properties = mapOf(
                "method" to "manual_genre_flow",
                "screen" to "ai_suggestions_screen",
                "total_subscribed_count" to totalSubscribedCount,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
                "did_switch_from_ai" to didSwitchFromAi
            )
        )

        val personaMap = deriveGenrePersona(favoriteGenres)
        val finalProps = mutableMapOf<String, Any>(
            "onboarding_status" to "completed",
            "onboarding_method" to "manual_genre",
            "user_intent" to "selective_curator",
            "initial_podcasts_subscribed" to totalSubscribedCount,
            "favorite_genres" to favoriteGenres.toList()
        )
        finalProps.putAll(personaMap)

        PostHog.capture(
            event = "\$set",
            userProperties = finalProps
        )
    }

    // ── 8. Feature Announcements ───────────────────────────────────

    fun trackFeatureAnnouncementViewed(featureId: String) {
        PostHog.capture(
            event = "feature_announcement_viewed",
            properties = mapOf("feature_id" to featureId)
        )
    }

    fun trackFeatureAnnouncementDismissed(featureId: String) {
        PostHog.capture(
            event = "feature_announcement_dismissed",
            properties = mapOf("feature_id" to featureId)
        )
    }

    // ── 9. Permissions ──────────────────────────────────────────────

    fun trackNotificationPermissionRequested() {
        PostHog.capture("notification_permission_requested")
    }

    fun trackNotificationPermissionDecided(isGranted: Boolean) {
        PostHog.capture(
            event = "notification_permission_decided",
            properties = mapOf("granted" to isGranted)
        )
        
        PostHog.capture(
            event = "\$set",
            userProperties = mapOf("notifications_enabled" to isGranted)
        )
    }

    // ── 10. Playback ───────────────────────────────────────────────

    fun trackHomeHeroCarouselSwiped(maxCardIndexViewed: Int, totalCardsAvailable: Int) {
        PostHog.capture(
            event = "home_hero_carousel_swiped",
            properties = mapOf(
                "\$screen_name" to "App Home",
                "max_card_index_viewed" to maxCardIndexViewed,
                "total_cards_available" to totalCardsAvailable
            )
        )
    }

    fun trackCuratedBlockImpression(blockTitle: String, vibeIds: List<String>) {
        PostHog.capture(
            event = "curated_block_impression",
            properties = mapOf(
                "block_title" to blockTitle,
                "vibes_shown_count" to vibeIds.size,
                "vibe_ids" to vibeIds
            )
        )
    }

    fun trackHomeRecommendationsImpression(
        recommendationsCount: Int,
        episodeIds: List<String>,
        timeBlockTitle: String?
    ) {
        val props = mutableMapOf<String, Any>(
            "recommendations_count" to recommendationsCount,
            "episode_ids" to episodeIds
        )
        timeBlockTitle?.let { props["time_block_title"] = it }
        PostHog.capture(event = "home_recommendations_impression", properties = props)
    }

    fun trackHomeRecommendationCardTapped(
        episodeId: String,
        episodeTitle: String?,
        podcastId: String,
        podcastName: String?,
        positionIndex: Int,
        timeBlockTitle: String?
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "podcast_id" to podcastId,
            "position_index" to positionIndex
        )
        episodeTitle?.let { props["episode_title"] = it }
        podcastName?.let { props["podcast_name"] = it }
        timeBlockTitle?.let { props["time_block_title"] = it }
        PostHog.capture(event = "home_recommendation_card_tapped", properties = props)
    }

    fun trackExploreRecommendationsImpression(
        recommendationsCount: Int,
        episodeIds: List<String>
    ) {
        PostHog.capture(
            event = "explore_recommendations_impression",
            properties = mapOf(
                "recommendations_count" to recommendationsCount,
                "episode_ids" to episodeIds
            )
        )
    }

    fun trackExploreRecommendationCardTapped(
        episodeId: String,
        episodeTitle: String?,
        podcastId: String,
        podcastName: String?,
        positionIndex: Int
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "podcast_id" to podcastId,
            "position_index" to positionIndex
        )
        episodeTitle?.let { props["episode_title"] = it }
        podcastName?.let { props["podcast_name"] = it }
        PostHog.capture(event = "explore_recommendation_card_tapped", properties = props)
    }


    // ── 11. Podcast Info Screen ────────────────────────────────────

    fun trackPodcastInfoScreenViewed(
        podcastId: String,
        podcastName: String? = null,
        entryPoint: String? = null,
        genreFilter: String? = null,
        scrollDepth: Int? = null,
        searchQuery: String? = null
    ) {
        val props = mutableMapOf<String, Any>("podcast_id" to podcastId)
        podcastName?.let { props["podcast_name"] = it }
        entryPoint?.let { props["source_entry_point"] = it }
        genreFilter?.let { props["genre_filter"] = it }
        scrollDepth?.let { props["scroll_depth"] = it }
        searchQuery?.let { props["search_query"] = it }

        PostHog.capture(event = "podcast_info_screen_viewed", properties = props)
    }

    fun trackPodcastSubscriptionToggled(
        podcastId: String,
        podcastName: String?,
        isSubscribed: Boolean,
        entryPoint: String
    ) {
        val props = mutableMapOf<String, Any>(
            "podcast_id" to podcastId,
            "is_subscribed" to isSubscribed,
            "entry_point" to entryPoint
        )
        podcastName?.let { props["podcast_name"] = it }
        PostHog.capture(event = "podcast_subscription_toggled", properties = props)
    }

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
    ) {
        PostHog.capture(
            event = "podcast_info_screen_session",
            properties = mapOf(
                "podcast_id" to podcastId,
                "podcast_name" to podcastName,
                "time_spent_seconds" to timeSpentSeconds,
                "was_subscribed" to wasSubscribed,
                "did_subscribe" to didSubscribe,
                "did_unsubscribe" to didUnsubscribe,
                "did_search" to didSearch,
                "did_sort_episodes" to didSortEpisodes,
                "episodes_played_count" to episodesPlayedCount,
                "episodes_clicked_count" to episodesClickedCount
            )
        )
    }

    fun trackCuratedCardTapped(
        podcastId: String,
        podcastName: String?,
        vibeId: String,
        positionIndex: Int
    ) {
        val props = mutableMapOf<String, Any>(
            "podcast_id" to podcastId,
            "vibe_id" to vibeId,
            "carousel_position" to positionIndex
        )
        if (podcastName != null) props["podcast_name"] = podcastName
        PostHog.capture(event = "curated_card_tapped", properties = props)
    }

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
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "start_position_seconds" to startPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "is_repeating" to isRepeating,
            "is_subscribed" to isSubscribed
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        entryPoint?.let { props["entry_point"] = it }
        entryPointContext?.let { props.putAll(it) }

        PostHog.capture(event = "playback_started", properties = props)
    }

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
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "duration_played_seconds" to durationPlayedSeconds,
            "total_buffered_time_seconds" to totalBufferedTimeSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "is_completed" to isCompleted,
            "pause_reason" to pauseReason
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        entryPoint?.let { props["entry_point"] = it }
        entryPointContext?.let { props.putAll(it) }
        queueSize?.let { props["queue_size"] = it }

        PostHog.capture(event = "playback_paused", properties = props)
    }

    fun trackPlaybackCompleted(
        podcastId: String?,
        podcastName: String?,
        podcastGenre: String?,
        episodeId: String,
        episodeTitle: String?,
        totalDurationSeconds: Float,
        entryPoint: String? = null,
        entryPointContext: Map<String, Any>? = null
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "total_duration_seconds" to totalDurationSeconds
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        podcastGenre?.let { props["podcast_genre"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        entryPoint?.let { props["entry_point"] = it }
        entryPointContext?.let { props.putAll(it) }

        PostHog.capture(event = "playback_completed", properties = props)
    }

    fun trackPlaybackHeartbeat(
        podcastId: String?,
        podcastName: String?,
        episodeId: String,
        episodeTitle: String?,
        currentPositionSeconds: Float,
        totalDurationSeconds: Float,
        heartbeatPercentage: Int,
        heartbeatType: String
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "current_position_seconds" to currentPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "heartbeat_percentage" to heartbeatPercentage,
            "heartbeat_type" to heartbeatType
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }

        PostHog.capture(event = "playback_heartbeat", properties = props)
    }

    fun trackPlaybackSeeked(
        podcastId: String?,
        podcastName: String?,
        episodeId: String,
        episodeTitle: String?,
        fromPositionSeconds: Float,
        toPositionSeconds: Float,
        totalDurationSeconds: Float,
        seekSource: String
    ) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "from_position_seconds" to fromPositionSeconds,
            "to_position_seconds" to toPositionSeconds,
            "total_duration_seconds" to totalDurationSeconds,
            "seek_source" to seekSource
        )
        podcastId?.let { props["podcast_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }

        PostHog.capture(event = "playback_seeked", properties = props)
    }

    fun trackPlaybackError(
        errorCode: String,
        errorMessage: String,
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null
    ) {
        val props = mutableMapOf<String, Any>(
            "error_code" to errorCode,
            "error_message" to errorMessage
        )
        podcastId?.let { props["podcast_id"] = it }
        episodeId?.let { props["episode_id"] = it }
        podcastName?.let { props["podcast_name"] = it }
        episodeTitle?.let { props["episode_title"] = it }
        PostHog.capture(event = "playback_error", properties = props)
    }

    fun trackExploreScreenViewed(sourceEntryPoint: String? = null) {
        val props = mutableMapOf<String, Any>()
        sourceEntryPoint?.let { props["source_entry_point"] = it }
        PostHog.capture(event = "explore_screen_viewed", properties = props)
    }

    fun trackExploreSearchPerformed(query: String, resultsCount: Int) {
        PostHog.capture(
            event = "explore_search_performed",
            properties = mapOf(
                "search_query" to query,
                "results_count" to resultsCount
            )
        )
    }

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
    ) {
        val props = mutableMapOf<String, Any>(
            "time_spent_seconds" to timeSpentSeconds,
            "categories_clicked_count" to categoriesClickedCount,
            "vibes_clicked_count" to vibesClickedCount,
            "searches_performed_count" to searchesPerformedCount,
            "podcasts_clicked_count" to podcastsClickedCount,
            "max_scroll_depth" to maxScrollDepth,
            "final_category_state" to finalCategoryState
        )
        finalVibeState?.let { props["final_vibe_state"] = it }
        finalSearchQuery?.let { props["final_search_query"] = it }

        PostHog.capture(event = "explore_screen_session", properties = props)
    }

    // ── 12. Library Screen ──────────────────────────────────────────

    fun trackLibraryHubViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "library_hub_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackLibraryHubSession(timeSpentSeconds: Float, navigatedTo: String?) {
        val props = mutableMapOf<String, Any>("time_spent_seconds" to timeSpentSeconds)
        navigatedTo?.let { props["navigated_to"] = it }
        PostHog.capture(event = "library_hub_session", properties = props)
    }

    fun trackLibrarySubscriptionsViewed(sourceEntryPoint: String, initialTab: String) {
        PostHog.capture(
            event = "library_subscriptions_viewed",
            properties = mapOf(
                "source_entry_point" to sourceEntryPoint,
                "initial_tab" to initialTab
            )
        )
    }

    fun trackLibrarySubscriptionsSession(
        timeSpentSeconds: Float,
        tabSwitchesCount: Int,
        didSearch: Boolean,
        finalSearchQuery: String?,
        podcastsClickedCount: Int,
        episodesClickedCount: Int
    ) {
        val props = mutableMapOf<String, Any>(
            "time_spent_seconds" to timeSpentSeconds,
            "tab_switches_count" to tabSwitchesCount,
            "did_search" to didSearch,
            "podcasts_clicked_count" to podcastsClickedCount,
            "episodes_clicked_count" to episodesClickedCount
        )
        finalSearchQuery?.let { props["final_search_query"] = it }
        PostHog.capture(event = "library_subscriptions_session", properties = props)
    }

    fun trackLibraryLikedViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "library_liked_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackLibraryLikedSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesUnlikedCount: Int) {
        PostHog.capture(
            event = "library_liked_session",
            properties = mapOf(
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "episodes_unliked_count" to episodesUnlikedCount
            )
        )
    }

    fun trackLibraryDownloadsViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "library_downloads_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackLibraryDownloadsSession(timeSpentSeconds: Float, episodesClickedCount: Int, episodesDeletedCount: Int) {
        PostHog.capture(
            event = "library_downloads_session",
            properties = mapOf(
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "episodes_deleted_count" to episodesDeletedCount
            )
        )
    }

    fun trackLibraryHistoryViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "library_history_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackLibraryHistorySession(timeSpentSeconds: Float, episodesClickedCount: Int, itemsDeletedCount: Int) {
        PostHog.capture(
            event = "library_history_session",
            properties = mapOf(
                "time_spent_seconds" to timeSpentSeconds,
                "episodes_clicked_count" to episodesClickedCount,
                "items_deleted_count" to itemsDeletedCount
            )
        )
    }

    fun trackTopControlbarInteraction(action: String, screen: String) {
        PostHog.capture(
            event = "top_controlbar_interaction",
            properties = mapOf(
                "action" to action,
                "screen" to screen
            )
        )
    }

    fun trackSettingsScreenViewed(sourceEntryPoint: String) {
        PostHog.capture(
            event = "settings_screen_viewed",
            properties = mapOf("source_entry_point" to sourceEntryPoint)
        )
    }

    fun trackSettingsInteraction(action: String, value: String? = null) {
        val props = mutableMapOf<String, Any>("action" to action)
        if (value != null) props["value"] = value
        PostHog.capture(event = "settings_interaction", properties = props)
    }

    fun trackMiniPlayerInteraction(
        action: String,
        podcastId: String?,
        episodeId: String?,
        podcastName: String? = null,
        episodeTitle: String? = null
    ) {
        val props = mutableMapOf<String, Any>("action" to action)
        if (podcastId != null) props["podcast_id"] = podcastId
        if (episodeId != null) props["episode_id"] = episodeId
        if (podcastName != null) props["podcast_name"] = podcastName
        if (episodeTitle != null) props["episode_title"] = episodeTitle
        PostHog.capture(event = "mini_player_interaction", properties = props)
    }

    fun trackFullPlayerScreenSession(
        podcastId: String?,
        episodeId: String?,
        metrics: Map<String, Any>,
        podcastName: String? = null,
        episodeTitle: String? = null
    ) {
        val props = mutableMapOf<String, Any>()
        if (podcastId != null) props["podcast_id"] = podcastId
        if (episodeId != null) props["episode_id"] = episodeId
        if (podcastName != null) props["podcast_name"] = podcastName
        if (episodeTitle != null) props["episode_title"] = episodeTitle
        props.putAll(metrics)
        PostHog.capture(event = "full_player_screen_session", properties = props)
    }

    // ── 13. System & Background ────────────────────────────────────

    fun trackNotificationTapped() {
        PostHog.capture("notification_tapped")
    }

    fun trackDownloadCompleted(fileSizeMb: Float) {
        PostHog.capture(
            event = "download_completed",
            properties = mapOf("file_size_mb" to fileSizeMb)
        )
    }

    fun trackDownloadFailed(errorReason: String) {
        PostHog.capture(
            event = "download_failed",
            properties = mapOf("error_reason" to errorReason)
        )
    }

    fun trackPlayMixClicked(count: Int) {
        PostHog.capture(
            event = "play_mix_clicked",
            properties = mapOf("episode_count" to count)
        )
    }

    fun trackHomePodcastFiltered(podcastId: String, title: String) {
        PostHog.capture(
            event = "home_podcast_filtered",
            properties = mapOf("podcast_id" to podcastId, "podcast_title" to title)
        )
    }

    fun trackLibrarySubscriptionsLayoutToggled(isGridView: Boolean) {
        PostHog.capture(
            event = "library_subscriptions_layout_toggled",
            properties = mapOf(
                "layout_type" to if (isGridView) "grid" else "list"
            )
        )
    }

    fun trackLibrarySubscriptionsSortChanged(sortMethod: String, tab: String) {
        PostHog.capture(
            event = "library_subscriptions_sort_changed",
            properties = mapOf(
                "sort_method" to sortMethod,
                "tab" to tab
            )
        )
    }

    fun trackLibrarySubscriptionsGenreFiltered(genreName: String, tab: String) {
        PostHog.capture(
            event = "library_subscriptions_genre_filtered",
            properties = mapOf(
                "genre_name" to genreName,
                "tab" to tab
            )
        )
    }

    fun resetIdentity() {
        PostHog.reset()
    }

    fun getDistinctId(): String {
        return PostHog.distinctId()
    }

    fun trackLateNightSafeguardDecision(decision: String, durationMinutes: Int? = null) {
        val props = mutableMapOf<String, Any>(
            "decision" to decision
        )
        if (durationMinutes != null) {
            props["duration_minutes"] = durationMinutes
        }
        PostHog.capture(
            event = "late_night_safeguard_decision",
            properties = props
        )
    }

    fun trackSmartQueueRefilled(
        triggeringEpisodeId: String,
        triggeringPodcastGenre: String,
        refilledCount: Int,
        recommendationSources: List<String>,
        refilledEpisodeIds: List<String>
    ) {
        PostHog.capture(
            event = "smart_queue_refilled",
            properties = mapOf(
                "triggering_episode_id" to triggeringEpisodeId,
                "triggering_podcast_genre" to triggeringPodcastGenre,
                "refilled_count" to refilledCount,
                "recommendation_sources" to recommendationSources,
                "refilled_episode_ids" to refilledEpisodeIds
            )
        )
    }

    fun trackSmartQueueEpisodeSkipped(
        episodeId: String,
        recommendationSource: String,
        positionInQueue: Int
    ) {
        PostHog.capture(
            event = "smart_queue_episode_skipped",
            properties = mapOf(
                "episode_id" to episodeId,
                "recommendation_source" to recommendationSource,
                "position_in_queue" to positionInQueue
            )
        )
    }

    fun trackOfflineModeEntered() {
        PostHog.capture(
            event = "offline_mode_entered"
        )
    }

    fun trackDiscoverCategoryFiltered(categoryName: String) {
        PostHog.capture(
            event = "discover_category_filtered",
            properties = mapOf("category_name" to categoryName)
        )
    }

    fun trackTimeBlockTapped(blockId: String, activeVibeIds: List<String>, userLocalHour: Int) {
        PostHog.capture(
            event = "time_block_tapped",
            properties = mapOf(
                "block_id" to blockId,
                "active_vibe_ids" to activeVibeIds,
                "user_local_hour" to userLocalHour
            )
        )
    }

    fun trackTimeBlockTapped() {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val blockId = when (hour) {
            in 5..11 -> "morning_news"
            in 12..16 -> "afternoon_break"
            in 17..22 -> "evening_unwind"
            else -> "late_night_listen"
        }
        val activeVibeIds = when (hour) {
            in 5..11 -> listOf("morning_news", "morning_motivation", "business_insider")
            in 12..16 -> listOf("science_explainer", "tech_culture", "creative_focus")
            in 17..22 -> listOf("comedy_gold", "tv_film_buff", "sports_fan")
            else -> listOf("true_crime_sleep", "history_buff", "mystery_thriller")
        }
        trackTimeBlockTapped(blockId, activeVibeIds, hour)
    }

    // ── 14. AI Chapters & Transcripts ──────────────────────────────

    fun trackAutoChaptersRequested(episodeId: String, podcastId: String?, audioUrl: String) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "audio_url" to audioUrl
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_chapters_requested", properties = props)
    }

    fun trackAutoChaptersCompleted(episodeId: String, podcastId: String?, durationSeconds: Float, chaptersCount: Int) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "duration_seconds" to durationSeconds,
            "chapters_count" to chaptersCount
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_chapters_completed", properties = props)
    }

    fun trackAutoChaptersFailed(episodeId: String, podcastId: String?, errorMessage: String) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "error_message" to errorMessage
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_chapters_failed", properties = props)
    }

    fun trackAutoTranscriptRequested(episodeId: String, podcastId: String?, audioUrl: String) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "audio_url" to audioUrl
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_transcript_requested", properties = props)
    }

    fun trackAutoTranscriptCompleted(episodeId: String, podcastId: String?, durationSeconds: Float, linesCount: Int) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "duration_seconds" to durationSeconds,
            "lines_count" to linesCount
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_transcript_completed", properties = props)
    }

    fun trackAutoTranscriptFailed(episodeId: String, podcastId: String?, errorMessage: String) {
        val props = mutableMapOf<String, Any>(
            "episode_id" to episodeId,
            "error_message" to errorMessage
        )
        podcastId?.let { props["podcast_id"] = it }
        PostHog.capture(event = "auto_transcript_failed", properties = props)
    }

    fun flush() {
        try {
            PostHog.flush()
        } catch (e: Exception) {
            android.util.Log.e("AnalyticsHelper", "Failed to flush PostHog", e)
        }
    }
}

