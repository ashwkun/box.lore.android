package cx.aswin.boxlore.core.analytics

/**
 * Glossary Phase A∪B∪C contract (PR7 A+B hard cut; PR9 Phase C Auto + polish).
 * Source of truth: docs/ANALYTICS_EVENT_GLOSSARY.md + docs/analytics/event_glossary.csv
 */
object AnalyticsGlossary {

    /** Person-property `$set` is allowed alongside glossary event names. */
    const val PERSON_SET_EVENT = "\$set"

    /** Canonical Phase A ∪ Phase B event names (includes A/B `search_performed`). */
    val PHASE_A_UNION_B: Set<String> = setOf(
        // Phase A
        "app_open",
        "app_background",
        "install_attributed",
        "deep_link_opened",
        "onboarding_started",
        "onboarding_flow_selected",
        "onboarding_step_viewed",
        "onboarding_abandoned",
        "onboarding_completed",
        "onboarding_ai_turn_submitted",
        "onboarding_ai_response_received",
        "onboarding_ai_search_redirect",
        "onboarding_ai_synthesis_completed",
        "onboarding_ai_synthesis_failed",
        "onboarding_search_performed",
        "onboarding_search_podcast_subscribed",
        "onboarding_import_sheet_opened",
        "onboarding_import_failed",
        "onboarding_manual_step_completed",
        "playback_started",
        "playback_heartbeat",
        "playback_paused",
        "playback_completed",
        "playback_error",
        "playback_buffering",
        "playback_seeked",
        "session_restore_prompt",
        "podcast_subscription_toggled",
        "notification_permission_requested",
        "notification_permission_decided",
        "notification_tapped",
        "notification_received",
        "identity_reset",
        "app_check_status",
        "first_episode_played",
        // Phase A/B
        "search_performed",
        // Phase B
        "home_surface_tapped",
        "home_surface_impression",
        "search_result_tapped",
        "learn_card_action",
        "learn_screen_viewed",
        "explore_screen_viewed",
        "explore_recommendation_tapped",
        "queue_modified",
        "episode_liked_toggled",
        "episode_mark_played",
        "download_requested",
        "download_completed",
        "download_failed",
        "smart_download_sync",
        "show_notification_toggled",
        "share_content",
        "backup_restore_result",
        "feedback_submitted",
        "library_destination_viewed",
        "podcast_detail_viewed",
        "episode_detail_viewed",
        "nav_tab_clicked",
        "settings_interaction",
        "feature_announcement_action",
        "offline_mode_entered",
        "player_chrome_interaction",
        "daily_briefing_action",
        "home_import_banner_action",
        "library_history_tracking_notice",
    )

    /** Phase C (Auto + polish) — shipped PR9. */
    val PHASE_C: Set<String> = setOf(
        "android_auto_connected",
        "android_auto_disconnected",
        "android_auto_browse",
        "adaptive_ranking_status",
        "learn_caught_up",
        "catalog_miss",
        "rss_refresh_failed",
        "progress_sync_anomaly",
        "late_night_safeguard_decision",
        "auto_chapters_lifecycle",
        "auto_transcript_lifecycle",
        "proxy_fallback_triggered",
    )

    val PHASE_A_UNION_B_UNION_C: Set<String> = PHASE_A_UNION_B + PHASE_C

    fun isAllowedEvent(eventName: String): Boolean =
        eventName == PERSON_SET_EVENT || eventName in PHASE_A_UNION_B_UNION_C

    /**
     * Normalizes legacy / free-form playback entry_point strings to the glossary enum sheet.
     * Unknown values become `unknown` (never omit — required on playback_*).
     */
    fun normalizeEntryPoint(raw: String?): String {
        if (raw.isNullOrBlank()) return "unknown"
        val key = raw.trim().lowercase()
        LEGACY_ENTRY_POINT_ALIASES[key]?.let { return it }
        if (key in CANONICAL_ENTRY_POINTS) return key
        return normalizeEntryPointFamily(key)
    }

    private fun normalizeEntryPointFamily(key: String): String =
        when {
            key.startsWith("home_adaptive_") -> key
            key.startsWith("android_auto_") ->
                if (key == "android_auto_drive_picks") {
                    "android_auto_discover"
                } else if (key in CANONICAL_ENTRY_POINTS) {
                    key
                } else {
                    "android_auto"
                }
            key.startsWith("home_hero_") ->
                when {
                    "resume" in key -> "home_hero_resume"
                    "jump_back" in key -> "home_hero_jump_back_in"
                    "new_episode" in key -> "home_hero_new_episodes"
                    else -> "home_hero_spotlight"
                }
            else -> "unknown"
        }

    private val LEGACY_ENTRY_POINT_ALIASES: Map<String, String> = mapOf(
        "home_hero_resume_grid" to "home_hero_resume",
        "home_hero_new_episodes_grid" to "home_hero_new_episodes",
        "home_hero_card" to "home_hero_spotlight",
        "resume_mini_player" to "mini_player",
        "resume_player" to "mini_player",
        "resume_notification" to "notification",
        "episode_info_screen" to "episode_detail",
        "episode_info" to "episode_detail",
        "podcast_info_screen" to "podcast_detail",
        "podcast_info" to "podcast_detail",
        "generic" to "unknown",
        "home_mixtape" to "home_mixtape",
        "learn" to "learn",
        "session_restore" to "session_restore",
        "smart_queue" to "smart_queue",
        "player_up_next" to "player_up_next",
        "onboarding_suggestion" to "onboarding_suggestion",
    )

    private val CANONICAL_ENTRY_POINTS: Set<String> = setOf(
        "home_hero_resume",
        "home_hero_jump_back_in",
        "home_hero_new_episodes",
        "home_hero_spotlight",
        "home_mixtape",
        "home_because_you_like",
        "home_discover_grid",
        "home_recommendations",
        "briefing",
        "explore_for_you",
        "explore_trending",
        "explore_category",
        "explore_search_shows",
        "explore_search_episodes",
        "learn",
        "podcast_detail",
        "episode_detail",
        "queue",
        "downloads",
        "history",
        "liked",
        "notification",
        "deep_link",
        "share",
        "install_referrer",
        "android_auto",
        "android_auto_continue",
        "android_auto_queue",
        "android_auto_new_episodes",
        "android_auto_mixtape",
        "android_auto_downloads",
        "android_auto_liked",
        "android_auto_history",
        "android_auto_discover",
        "android_auto_voice",
        "android_auto_play_all",
        "mini_player",
        "session_restore",
        "player_up_next",
        "smart_queue",
        "onboarding_suggestion",
        "unknown",
    )
}
