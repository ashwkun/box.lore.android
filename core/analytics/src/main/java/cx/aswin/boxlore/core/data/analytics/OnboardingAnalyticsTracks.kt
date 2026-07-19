package cx.aswin.boxlore.core.data.analytics

import com.posthog.PostHog

// ── Home Import Banner (Empty State) ───────────────────────────

internal object OnboardingAnalyticsTracks {
    fun trackHomeImportBannerImpression() {
        PostHog.capture(event = "home_import_banner_impression")
    }

    fun trackHomeImportBannerClicked(action: String) {
        PostHog.capture(
            event = "home_import_banner_clicked",
            properties = mapOf("action" to action)
        )
    }

    fun trackHomeImportBannerDismissed() {
        PostHog.capture(event = "home_import_banner_dismissed")
    }

    // ── 2. Onboarding Started & Flow Selection ──────────────────────

    fun trackOnboardingStarted(entryPoint: String = "welcome_screen") {
        PostHog.capture(
            event = "onboarding_started",
            properties = mapOf("entry_point" to entryPoint)
        )
    }

    fun trackOnboardingFlowSelected(flowType: String, entryPoint: String = "welcome_screen") {
        PostHog.capture(
            event = "onboarding_flow_selected",
            properties = mapOf(
                "flow_type" to flowType,
                "entry_point" to entryPoint
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

    fun trackOnboardingAiTurnSubmitted(
        turnNumber: Int,
        selectedOptions: Set<String>,
        customInputText: String,
        timeSpentSeconds: Float
    ) {
        PostHog.capture(
            event = "onboarding_ai_turn_submitted",
            properties = mapOf(
                "turn_number" to turnNumber,
                "selected_options" to selectedOptions.toList(),
                "has_custom_input" to customInputText.isNotBlank(),
                "custom_input_text" to customInputText,
                "time_spent_seconds" to timeSpentSeconds
            )
        )
    }

    fun trackOnboardingAiResponseReceived(
        turnNumber: Int,
        assistantMessage: String,
        optionsCount: Int,
        optionsList: List<String>,
        durationSeconds: Float,
        detectedIntent: String? = null
    ) {
        PostHog.capture(
            event = "onboarding_ai_response_received",
            properties = buildMap {
                put("turn_number", turnNumber)
                put("assistant_message", assistantMessage.take(500))
                put("options_count", optionsCount)
                put("options_list", optionsList)
                put("duration_seconds", durationSeconds)
                detectedIntent?.let { put("detected_intent", it) }
            }
        )
    }

    fun trackOnboardingAiSearchRedirect(turnNumber: Int, suggestedQuery: String?) {
        PostHog.capture(
            event = "onboarding_ai_search_redirect",
            properties = buildMap {
                put("turn_number", turnNumber)
                suggestedQuery?.let { put("suggested_query", it) }
            }
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
        subscribedPodcastsList: List<String>,
        didScrollSuggestions: Boolean,
        totalOnboardingTimeSeconds: Float,
        favoriteGenres: List<String>,
        entryPoint: String = "welcome_screen"
    ) {
        PostHog.capture(
            event = "onboarding_completed",
            properties = mapOf(
                "method" to "ai_suggestions_done",
                "screen" to "ai_suggestions_screen",
                "total_subscribed_count" to totalSubscribedCount,
                "subscribed_podcasts_list" to subscribedPodcastsList,
                "did_scroll_suggestions" to didScrollSuggestions,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
                "entry_point" to entryPoint
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
        subscribedPodcastsList: List<String>,
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
                "subscribed_podcasts_list" to subscribedPodcastsList,
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
        importedPodcastsList: List<String>,
        totalOnboardingTimeSeconds: Float,
        entryPoint: String = "welcome_screen"
    ) {
        PostHog.capture(
            event = "onboarding_completed",
            properties = mapOf(
                "method" to "import",
                "import_type" to importType,
                "screen" to (if (entryPoint == "home_import_banner") "home_screen" else "welcome_screen"),
                "imported_podcast_count" to importedPodcastCount,
                "imported_podcasts_list" to importedPodcastsList,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
                "entry_point" to entryPoint
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

    fun trackOnboardingManualStepCompleted(
        stepName: String,
        selectionsCount: Int,
        selectionsList: List<String>,
        timeSpentSeconds: Float
    ) {
        PostHog.capture(
            event = "onboarding_manual_step_completed",
            properties = mapOf(
                "step_name" to stepName,
                "selections_count" to selectionsCount,
                "selections_list" to selectionsList,
                "time_spent_seconds" to timeSpentSeconds
            )
        )
    }

    fun trackOnboardingManualDone(
        totalSubscribedCount: Int,
        subscribedPodcastsList: List<String>,
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
                "subscribed_podcasts_list" to subscribedPodcastsList,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
                "did_switch_from_ai" to didSwitchFromAi
            )
        )

        val personaMap = GenrePersonaLogic.deriveGenrePersona(favoriteGenres)
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

    /**
     * Person-property refresh when import onboarding already emitted `onboarding_completed`
     * elsewhere (e.g. MainActivity OPML path) but AI suggestions finish still needs `$set`.
     */
    fun setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed: Int) {
        PostHog.capture(
            event = "\$set",
            userProperties =
                mapOf(
                    "onboarding_status" to "completed",
                    "onboarding_method" to "import",
                    "user_intent" to "migrating_power_user",
                    "initial_podcasts_subscribed" to initialPodcastsSubscribed,
                ),
        )
    }
}
