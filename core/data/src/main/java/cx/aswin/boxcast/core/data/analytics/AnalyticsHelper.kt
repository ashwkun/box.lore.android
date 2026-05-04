package cx.aswin.boxcast.core.data.analytics

import android.content.Context
import android.os.SystemClock
import cx.aswin.boxcast.core.data.privacy.ConsentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AnalyticsHelper — Privacy-first, first-party analytics powered by the Insight Engine.
 *
 * All events flow through SessionAggregator → batched HTTP → Cloudflare Worker → Turso.
 * Zero third-party analytics SDKs. No personal data collected.
 *
 * Event naming convention:
 *   funnel_*    → conversion funnel steps (onboarding, activation)
 *   discovery_* → how users find content
 *   play_*      → playback behavior and quality
 *   feature_*   → feature adoption signals
 *   action_*    → user actions (subscribe, like, share)
 *   screen_*    → navigation patterns
 *   friction_*  → pain points (errors, empty states, rage taps)
 */
class AnalyticsHelper(
    private val context: Context,
    private val consentManager: ConsentManager
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Cached state for synchronous checks (default false for safety)
    private var isUsageConsented = false

    // Timestamp when app was first opened (for time-to-first-play calculation)
    private val appOpenTimeElapsed = SystemClock.elapsedRealtime()

    // Milestone tracking for episode progress (scrub-safe)
    private var currentEpisodeMilestones = mutableSetOf<Int>()
    private var currentTrackingEpisodeKey: String? = null

    // Session tracking for listening_session_summary
    private var sessionStartElapsed: Long? = null

    init {
        scope.launch {
            consentManager.isUsageAnalyticsConsented.collectLatest { consented ->
                isUsageConsented = consented
            }
        }
    }

    // ── Core Logging ──────────────────────────────────────────

    private fun track(metricKey: String, amount: Int = 1) {
        // Insight Engine events are always tracked (privacy-safe by design)
        SessionAggregator.incrementAggregate(metricKey, amount)
    }

    /** Track regardless of consent — for onboarding funnel (pre-consent). */
    private fun trackUngated(metricKey: String, amount: Int = 1) {
        SessionAggregator.incrementAggregate(metricKey, amount)
    }

    // ══════════════════════════════════════════════════════════
    //  FUNNEL: Onboarding (always tracked, pre-activation)
    // ══════════════════════════════════════════════════════════

    /**
     * Tracks onboarding funnel steps with rich context.
     * Steps: genres_selected → search_shown → completed/skipped
     */
    fun logOnboardingStep(step: String, additionalParams: Map<String, String>? = null) {
        when (step) {
            "genres_selected" -> {
                trackUngated("funnel_onboarding_genres_picked")
                val count = additionalParams?.get("genre_count")?.toIntOrNull() ?: 0
                if (count > 0) trackUngated("funnel_onboarding_genres_total", count)
            }
            "search_shown" -> trackUngated("funnel_onboarding_search_used")
            "completed" -> {
                trackUngated("funnel_onboarding_completed")
                val podCount = additionalParams?.get("podcast_count")?.toIntOrNull() ?: 0
                if (podCount > 0) trackUngated("funnel_onboarding_subs_total", podCount)
            }
            "skipped" -> trackUngated("funnel_onboarding_skipped")
            else -> trackUngated("funnel_onboarding_$step")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  FUNNEL: Activation (first-ever play)
    // ══════════════════════════════════════════════════════════

    /** E1: First episode ever played. Call once per install (gate with DataStore). */
    fun logFirstEpisodePlayed(source: String) {
        val elapsedSinceOpen = SystemClock.elapsedRealtime() - appOpenTimeElapsed
        track("funnel_first_play")
        track("funnel_first_play_source_$source")
        // Time-to-first-play buckets for activation speed analysis
        when {
            elapsedSinceOpen < 60_000 -> track("funnel_first_play_under_1m")
            elapsedSinceOpen < 300_000 -> track("funnel_first_play_1_5m")
            else -> track("funnel_first_play_over_5m")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  DISCOVERY: How users find content
    // ══════════════════════════════════════════════════════════

    /** E7: Search performed. No query text logged (privacy-safe). */
    fun logSearchPerformed(hasResults: Boolean) {
        if (!isUsageConsented) return
        track("discovery_search")
        if (!hasResults) {
            track("friction_search_empty")
        }
    }

    /** E8: Explore vibe/category selected. */
    fun logExploreVibeSelected(vibeCategory: String) {
        if (!isUsageConsented) return
        track("discovery_explore_vibe")
        // Sanitize vibe name for metric key
        val safeVibe = vibeCategory.lowercase().replace(Regex("[^a-z0-9]"), "_").take(30)
        track("discovery_vibe_$safeVibe")
    }

    /** E9: Hero card tapped on home screen. */
    fun logHeroCardTapped(cardType: String, cardPosition: Int) {
        if (!isUsageConsented) return
        track("discovery_hero_tapped")
        val safeType = cardType.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        track("discovery_hero_type_$safeType")
    }

    // ══════════════════════════════════════════════════════════
    //  CURATED: Time-block section engagement
    // ══════════════════════════════════════════════════════════

    /** Curated time-block displayed on home screen (impression). */
    fun logCuratedBlockImpression(blockTitle: String, vibeCount: Int, totalPodcasts: Int) {
        if (!isUsageConsented) return
        track("curated_block_impression")
        val safeTitle = blockTitle.lowercase().replace(Regex("[^a-z0-9]"), "_").take(30)
        track("curated_block_$safeTitle")
        track("curated_vibes_shown", vibeCount)
        track("curated_pods_shown", totalPodcasts)
    }

    /** Curated vibe rail scrolled into view (per-vibe impression). */
    fun logCuratedVibeImpression(vibeId: String, podcastCount: Int) {
        if (!isUsageConsented) return
        val safeVibe = vibeId.lowercase().replace(Regex("[^a-z0-9]"), "_").take(30)
        track("curated_vibe_impression_$safeVibe")
        track("curated_vibe_pods_$safeVibe", podcastCount)
    }

    /** User tapped a podcast card in the curated section. */
    fun logCuratedCardTapped(vibeId: String, podcastId: String, position: Int) {
        if (!isUsageConsented) return
        track("curated_card_tapped")
        val safeVibe = vibeId.lowercase().replace(Regex("[^a-z0-9]"), "_").take(30)
        track("curated_tap_vibe_$safeVibe")
        // Track position buckets to understand if users scroll deep
        val posBucket = when {
            position < 3 -> "pos_0_2"
            position < 6 -> "pos_3_5"
            else -> "pos_6_plus"
        }
        track("curated_tap_$posBucket")
        // Track per-podcast interest via podcast intelligence
        SessionAggregator.incrementPodcastMetric(podcastId, "curated_taps")
    }

    /** Episode play initiated from the curated section. */
    fun logCuratedEpisodePlayed(vibeId: String, podcastId: String, position: Int) {
        if (!isUsageConsented) return
        track("curated_episode_played")
        val safeVibe = vibeId.lowercase().replace(Regex("[^a-z0-9]"), "_").take(30)
        track("curated_play_vibe_$safeVibe")
        val posBucket = when {
            position < 3 -> "pos_0_2"
            position < 6 -> "pos_3_5"
            else -> "pos_6_plus"
        }
        track("curated_play_$posBucket")
        // Track per-podcast curated plays
        SessionAggregator.incrementPodcastMetric(podcastId, "curated_plays")
    }

    // ══════════════════════════════════════════════════════════
    //  PLAYBACK: Engagement depth and quality
    // ══════════════════════════════════════════════════════════

    /** E2: Episode playback started. */
    fun logEpisodeStarted(source: String, isDownloaded: Boolean) {
        if (!isUsageConsented) return
        track("play_episode_started")
        val safeSource = source.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        track("play_source_$safeSource")
        if (isDownloaded) track("play_from_download")
    }

    /**
     * E3: Episode progress milestone reached. Scrub-safe.
     * Only fires if the milestone is crossed during normal playback (not seek).
     */
    fun logEpisodeProgress(episodeKey: String, currentPercent: Int) {
        if (!isUsageConsented) return
        // Reset tracking on episode change
        if (episodeKey != currentTrackingEpisodeKey) {
            currentTrackingEpisodeKey = episodeKey
            currentEpisodeMilestones.clear()
        }

        val milestones = listOf(25, 50, 75, 100)
        for (milestone in milestones) {
            if (currentPercent >= milestone && milestone !in currentEpisodeMilestones) {
                currentEpisodeMilestones.add(milestone)
                track("play_milestone_$milestone")
            }
        }
    }

    /**
     * Mark milestones as passed without firing events (on seek).
     * Prevents counting seek-past as genuine listening.
     */
    fun markMilestonesPassedOnSeek(episodeKey: String, seekToPercent: Int) {
        if (episodeKey != currentTrackingEpisodeKey) {
            currentTrackingEpisodeKey = episodeKey
            currentEpisodeMilestones.clear()
        }
        val milestones = listOf(25, 50, 75, 100)
        for (milestone in milestones) {
            if (seekToPercent >= milestone) {
                currentEpisodeMilestones.add(milestone) // Mark as passed, don't fire
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SESSION: Listening session quality
    // ══════════════════════════════════════════════════════════

    /** Call when playback starts to begin session timing. */
    fun startListeningSession() {
        if (sessionStartElapsed == null) {
            sessionStartElapsed = SystemClock.elapsedRealtime()
        }
    }

    /** E4: Listening session summary with duration bucket. */
    fun endListeningSession() {
        val start = sessionStartElapsed ?: return
        sessionStartElapsed = null

        val durationMs = SystemClock.elapsedRealtime() - start
        val durationSec = (durationMs / 1000).toInt()

        // Track total listening session time (aggregates across the day)
        if (durationSec > 0) track("play_session_total_sec", durationSec)

        // Duration buckets for session quality analysis
        val bucket = when {
            durationMs < 60_000 -> "under_1m"
            durationMs < 300_000 -> "1_5m"
            durationMs < 900_000 -> "5_15m"
            durationMs < 1_800_000 -> "15_30m"
            durationMs < 3_600_000 -> "30_60m"
            else -> "over_60m"
        }
        track("play_session_$bucket")
    }

    // ══════════════════════════════════════════════════════════
    //  SUBSCRIPTIONS: Growth and churn signals
    // ══════════════════════════════════════════════════════════

    /** E5: Subscribe or unsubscribe action. */
    fun logSubscribeAction(isSubscribe: Boolean, source: String = "unknown") {
        if (!isUsageConsented) return
        if (isSubscribe) {
            track("action_subscribe")
            val safeSource = source.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
            track("action_subscribe_from_$safeSource")
        } else {
            track("action_unsubscribe")
        }
    }

    // ══════════════════════════════════════════════════════════
    //  FEATURE ADOPTION: Power feature usage
    // ══════════════════════════════════════════════════════════

    /** E6: Power feature used (skip, speed, timer, download, queue, etc.) */
    fun logFeatureUsed(feature: String) {
        if (!isUsageConsented) return
        val safeFeature = feature.lowercase().replace(Regex("[^a-z0-9]"), "_").take(30)
        track("feature_$safeFeature")
    }

    // ══════════════════════════════════════════════════════════
    //  NAVIGATION: Screen-level behavior
    // ══════════════════════════════════════════════════════════

    /** E10: Screen view — tracks which screens users actually visit. */
    fun logScreenView(screenName: String) {
        if (!isUsageConsented) return
        val safeScreen = screenName.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        track("screen_$safeScreen")
    }

    /** Tracks when a user hits an empty state, useful for churn analysis. */
    fun logEmptyStateSeen(screenName: String) {
        if (!isUsageConsented) return
        val safeScreen = screenName.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        track("friction_empty_$safeScreen")
    }

    // ══════════════════════════════════════════════════════════
    //  FRICTION: Pain points and reliability
    // ══════════════════════════════════════════════════════════

    /** E11: Playback error with network context. */
    fun logPlaybackError(errorType: String) {
        if (!isUsageConsented) return
        track("friction_playback_error")
        val safeType = errorType.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        track("friction_error_$safeType")
        // Network context for debugging
        val networkState = getNetworkState()
        track("friction_error_net_$networkState")
    }

    private fun getNetworkState(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return "offline"
            val capabilities = cm.getNetworkCapabilities(network) ?: return "unknown"
            when {
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> "unknown"
            }
        } catch (_: Exception) { "unknown" }
    }

    // ══════════════════════════════════════════════════════════
    //  RETENTION: Engagement and feedback
    // ══════════════════════════════════════════════════════════

    /** E12: In-app feedback interaction. */
    fun logAppFeedback(action: String) {
        if (!isUsageConsented) return
        val safeAction = action.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        track("action_feedback_$safeAction")
    }

    /** E13: Notification opened or dismissed. */
    fun logNotificationInteraction(action: String) {
        if (!isUsageConsented) return
        val safeAction = action.lowercase().replace(Regex("[^a-z0-9]"), "_").take(20)
        track("action_notification_$safeAction")
    }

    /** Feature announcement banner seen/tapped. */
    fun logFeatureAnnouncementSeen(announcementId: String) {
        val safeId = announcementId.replace(".", "_").take(30)
        track("action_feature_banner_$safeId")
    }
}
