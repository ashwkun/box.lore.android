package cx.aswin.boxlore.core.analytics

internal object OnboardingAnalyticsTracks {
    fun trackHomeImportBannerImpression() {
        AnalyticsEmit.event(
            "home_import_banner_action",
            mapOf("action" to "impression"),
        )
    }

    fun trackHomeImportBannerClicked(action: String) {
        AnalyticsEmit.event(
            "home_import_banner_action",
            mapOf("action" to "click", "click_target" to action),
        )
        AnalyticsEmit.event(
            "home_surface_tapped",
            mapOf(
                "surface_component" to "import_banner",
                "content_id" to action,
            ),
        )
    }

    fun trackHomeImportBannerDismissed() {
        AnalyticsEmit.event(
            "home_import_banner_action",
            mapOf("action" to "dismiss"),
        )
    }

    fun trackOnboardingStarted(entryPoint: String = "welcome_screen") {
        AnalyticsEmit.event(
            "onboarding_started",
            mapOf("entry_point" to entryPoint),
        )
    }

    fun trackOnboardingFlowSelected(flowType: String, entryPoint: String = "welcome_screen") {
        AnalyticsEmit.event(
            "onboarding_flow_selected",
            mapOf(
                "flow_type" to flowType,
                "entry_point" to entryPoint,
            ),
        )
    }

    fun trackOnboardingSkipped(screen: String, totalOnboardingTimeSeconds: Float) {
        AnalyticsEmit.event(
            "onboarding_completed",
            mapOf(
                "method" to "skip_welcome",
                "screen" to screen,
                "total_subscribed_count" to 0,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
            ),
        )
        AnalyticsEmit.personSet(
            mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "skip",
                "user_intent" to "casual_browser",
            ),
        )
    }

    fun trackOnboardingAiTurnSubmitted(
        turnNumber: Int,
        selectedOptions: Set<String>,
        customInputText: String,
        timeSpentSeconds: Float,
    ) {
        val userInputText = customInputText
        AnalyticsEmit.event(
            "onboarding_ai_turn_submitted",
            mapOf(
                "turn_number" to turnNumber,
                "selected_options" to selectedOptions.toList(),
                "has_custom_input" to userInputText.isNotBlank(),
                "user_input_text" to userInputText,
                "time_spent_seconds" to timeSpentSeconds,
            ),
        )
    }

    fun trackOnboardingAiResponseReceived(
        turnNumber: Int,
        assistantMessage: String,
        optionsCount: Int,
        optionsList: List<String>,
        durationSeconds: Float,
        detectedIntent: String? = null,
    ) {
        AnalyticsEmit.event(
            "onboarding_ai_response_received",
            buildMap {
                put("turn_number", turnNumber)
                put("assistant_message", assistantMessage.take(500))
                put("options_count", optionsCount)
                put("options_list", optionsList)
                put("duration_seconds", durationSeconds)
                detectedIntent?.let { put("detected_intent", it) }
            },
        )
    }

    fun trackOnboardingAiSearchRedirect(turnNumber: Int, suggestedQuery: String?) {
        AnalyticsEmit.event(
            "onboarding_ai_search_redirect",
            buildMap {
                put("turn_number", turnNumber)
                suggestedQuery?.let { put("suggested_query", it) }
            },
        )
    }

    fun trackOnboardingAiSynthesisCompleted(rowsCount: Int, podcastsCount: Int, durationSeconds: Float) {
        AnalyticsEmit.event(
            "onboarding_ai_synthesis_completed",
            mapOf(
                "rows_count" to rowsCount,
                "podcasts_count" to podcastsCount,
                "duration_seconds" to durationSeconds,
            ),
        )
    }

    fun trackOnboardingAiSynthesisFailed(errorMessage: String) {
        AnalyticsEmit.event(
            "onboarding_ai_synthesis_failed",
            mapOf("error_message" to errorMessage),
        )
    }

    fun trackOnboardingAiDone(
        totalSubscribedCount: Int,
        subscribedPodcastsList: List<String>,
        didScrollSuggestions: Boolean,
        totalOnboardingTimeSeconds: Float,
        favoriteGenres: List<String>,
        entryPoint: String = "welcome_screen",
    ) {
        AnalyticsEmit.event(
            "onboarding_completed",
            mapOf(
                "method" to "ai_suggestions_done",
                "screen" to "ai_suggestions_screen",
                "total_subscribed_count" to totalSubscribedCount,
                "subscribed_podcasts_list" to subscribedPodcastsList,
                "did_scroll_suggestions" to didScrollSuggestions,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
                "entry_point" to entryPoint,
                "favorite_genres" to favoriteGenres,
            ),
        )
        AnalyticsEmit.personSet(
            mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "ai_chat",
                "user_intent" to "ai_guided_listener",
                "initial_podcasts_subscribed" to totalSubscribedCount,
                "favorite_genres" to favoriteGenres,
            ),
        )
    }

    fun trackSearchPerformed(query: String, resultsCount: Int) {
        val trimmed = query.trim()
        AnalyticsEmit.event(
            "onboarding_search_performed",
            mapOf(
                "search_query" to trimmed,
                "results_count" to resultsCount,
                "query_length" to trimmed.length,
            ),
        )
        // Also emit unified search_performed (glossary Phase A/B).
        AnalyticsEmit.event(
            "search_performed",
            mapOf(
                "surface" to "onboarding",
                "search_mode" to "show_keyword",
                "search_query" to trimmed,
                "results_count" to resultsCount,
                "query_length" to trimmed.length,
            ),
        )
    }

    fun trackSearchPodcastSubscribed(podcastName: String, podcastId: String, totalSubscribedCount: Int) {
        AnalyticsEmit.event(
            "onboarding_search_podcast_subscribed",
            mapOf(
                "podcast_name" to podcastName,
                "podcast_id" to podcastId,
                "total_subscribed_count" to totalSubscribedCount,
            ),
        )
    }

    fun trackOnboardingSearchDone(
        entryPoint: String,
        totalSubscribedCount: Int,
        subscribedPodcastsList: List<String>,
        searchesPerformed: Int,
        timeSpentOnSearchSeconds: Float,
        totalOnboardingTimeSeconds: Float,
    ) {
        AnalyticsEmit.event(
            "onboarding_completed",
            mapOf(
                "method" to "search_done",
                "screen" to "search_screen",
                "entry_point" to entryPoint,
                "total_subscribed_count" to totalSubscribedCount,
                "subscribed_podcasts_list" to subscribedPodcastsList,
                "searches_performed" to searchesPerformed,
                "time_spent_on_search_seconds" to timeSpentOnSearchSeconds,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
            ),
        )
        AnalyticsEmit.personSet(
            mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "search",
                "user_intent" to "targeted_listener",
                "initial_podcasts_subscribed" to totalSubscribedCount,
            ),
        )
    }

    fun trackImportSheetOpened() {
        AnalyticsEmit.event("onboarding_import_sheet_opened")
    }

    fun trackOnboardingImportCompleted(
        importType: String,
        importedPodcastCount: Int,
        importedPodcastsList: List<String>,
        totalOnboardingTimeSeconds: Float,
        entryPoint: String = "welcome_screen",
    ) {
        AnalyticsEmit.event(
            "onboarding_completed",
            mapOf(
                "method" to "import",
                "import_type" to importType,
                "screen" to (if (entryPoint == "home_import_banner") "home_screen" else "welcome_screen"),
                "total_subscribed_count" to importedPodcastCount,
                "imported_podcast_count" to importedPodcastCount,
                "imported_podcasts_list" to importedPodcastsList,
                "subscribed_podcasts_list" to importedPodcastsList,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
                "entry_point" to entryPoint,
            ),
        )
        AnalyticsEmit.personSet(
            mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "import",
                "user_intent" to "migrating_power_user",
                "initial_podcasts_subscribed" to importedPodcastCount,
            ),
        )
    }

    fun trackOnboardingImportFailed(importType: String, errorMessage: String?) {
        AnalyticsEmit.event(
            "onboarding_import_failed",
            mapOf(
                "import_type" to importType,
                "error_message" to (errorMessage ?: "Unknown error"),
            ),
        )
    }

    fun trackOnboardingManualStepCompleted(
        stepName: String,
        selectionsCount: Int,
        selectionsList: List<String>,
        timeSpentSeconds: Float,
    ) {
        AnalyticsEmit.event(
            "onboarding_manual_step_completed",
            mapOf(
                "step_name" to stepName,
                "selections_count" to selectionsCount,
                "selections_list" to selectionsList,
                "time_spent_seconds" to timeSpentSeconds,
            ),
        )
        AnalyticsEmit.event(
            "onboarding_step_viewed",
            mapOf(
                "step_name" to stepName,
                "flow_type" to "manual_genre",
            ),
        )
    }

    fun trackOnboardingManualDone(
        totalSubscribedCount: Int,
        subscribedPodcastsList: List<String>,
        totalOnboardingTimeSeconds: Float,
        didSwitchFromAi: Boolean,
        favoriteGenres: Set<String>,
    ) {
        AnalyticsEmit.event(
            "onboarding_completed",
            mapOf(
                "method" to "manual_genre_flow",
                "screen" to "ai_suggestions_screen",
                "total_subscribed_count" to totalSubscribedCount,
                "subscribed_podcasts_list" to subscribedPodcastsList,
                "total_onboarding_time_seconds" to totalOnboardingTimeSeconds,
                "did_switch_from_ai" to didSwitchFromAi,
                "favorite_genres" to favoriteGenres.toList(),
            ),
        )

        val personaMap = GenrePersonaLogic.deriveGenrePersona(favoriteGenres)
        val finalProps = mutableMapOf<String, Any>(
            "onboarding_status" to "completed",
            "onboarding_method" to "manual_genre",
            "user_intent" to "selective_curator",
            "initial_podcasts_subscribed" to totalSubscribedCount,
            "favorite_genres" to favoriteGenres.toList(),
        )
        finalProps.putAll(personaMap)
        AnalyticsEmit.personSet(finalProps)
    }

    fun setOnboardingImportCompletedUserProperties(initialPodcastsSubscribed: Int) {
        AnalyticsEmit.personSet(
            mapOf(
                "onboarding_status" to "completed",
                "onboarding_method" to "import",
                "user_intent" to "migrating_power_user",
                "initial_podcasts_subscribed" to initialPodcastsSubscribed,
            ),
        )
    }
}
