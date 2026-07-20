package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

private const val LAST_SEEN_EPISODE_ID_PREFIX = "last_seen_episode_id_"

class UserPreferencesRepository(
    context: Context,
) {
    private val dataStore = context.userPreferencesDataStore
    private val syncPrefs =
        PrefsFileMigrator.open(
            context,
            newName = PrefsFileMigrator.Files.THEME_FAST_CACHE,
            oldName = PrefsFileMigrator.LegacyFiles.THEME_FAST_CACHE,
        )

    val cachedThemeConfig: String
        get() = syncPrefs.getString("theme_config", null) ?: "system"

    val cachedSurfaceStyle: String
        get() = syncPrefs.getString("surface_style", null) ?: "classic_dynamic"

    val cachedThemeBrand: String
        get() = syncPrefs.getString("theme_brand", null) ?: "violet"

    val cachedUseDynamicColor: Boolean
        get() = syncPrefs.getBoolean("use_dynamic_color", false)

    private fun normalizeRegionCode(region: String): String {
        val normalized = region.trim().lowercase()
        return when (normalized) {
            "ind" -> "in"
            "uk" -> "gb"
            else -> normalized
        }
    }

    val regionStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                val stored = preferences[Keys.REGION]
                if (stored != null) {
                    normalizeRegionCode(stored)
                } else {
                    val localeCountry =
                        java.util.Locale
                            .getDefault()
                            .country
                            .lowercase()
                    // "fr" is intentional here even though France isn't a supported region value —
                    // it still routes French locales into the region nudge/picker flow.
                    if (localeCountry in setOf("in", "gb", "uk", "fr")) {
                        normalizeRegionCode(localeCountry)
                    } else {
                        "us"
                    }
                }
            }.distinctUntilChanged()

    suspend fun setRegion(region: String) {
        val normalized = normalizeRegionCode(region)
        dataStore.edit { preferences ->
            preferences[Keys.REGION] = normalized
            preferences[Keys.HAS_DISMISSED_REGION_NUDGE] = true
        }
    }

    val hasDismissedRegionNudgeStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.HAS_DISMISSED_REGION_NUDGE] ?: false
            }.distinctUntilChanged()

    suspend fun dismissRegionNudge() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_REGION_NUDGE] = true
        }
    }

    val hasDismissedExploreRegionNudgeStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.HAS_DISMISSED_EXPLORE_REGION_NUDGE] ?: false
            }.distinctUntilChanged()

    suspend fun dismissExploreRegionNudge() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_EXPLORE_REGION_NUDGE] = true
        }
    }

    val hasDismissedHomeImportBannerStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.HAS_DISMISSED_HOME_IMPORT_BANNER] ?: false
            }.distinctUntilChanged()

    suspend fun dismissHomeImportBanner() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_HOME_IMPORT_BANNER] = true
        }
    }

    val briefingDismissedDate: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.BRIEFING_DISMISSED_DATE] ?: ""
            }.distinctUntilChanged()

    suspend fun dismissBriefing(date: String) {
        dataStore.edit { preferences ->
            preferences[Keys.BRIEFING_DISMISSED_DATE] = date
        }
    }

    val briefingDismissedForever: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.BRIEFING_DISMISSED_FOREVER] ?: false
            }.distinctUntilChanged()

    suspend fun dismissBriefingForever() {
        dataStore.edit { preferences ->
            preferences[Keys.BRIEFING_DISMISSED_FOREVER] = true
        }
    }

    val wasInitialRegionMatchStream: Flow<Boolean?> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                preferences[Keys.WAS_INITIAL_REGION_MATCH]
            }.distinctUntilChanged()

    suspend fun setWasInitialRegionMatch(match: Boolean) {
        dataStore.edit { preferences ->
            if (preferences[Keys.WAS_INITIAL_REGION_MATCH] == null) {
                preferences[Keys.WAS_INITIAL_REGION_MATCH] = match
            }
        }
    }

    // THEME PREFERENCES
    val themeConfigStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val config = preferences[Keys.THEME_CONFIG] ?: "system"
                syncPrefs.edit().putString("theme_config", config).apply()
                config
            }.distinctUntilChanged()

    suspend fun setThemeConfig(themeConfig: String) {
        syncPrefs.edit().putString("theme_config", themeConfig).apply()
        dataStore.edit { preferences ->
            preferences[Keys.THEME_CONFIG] = themeConfig
        }
    }

    val useDynamicColorStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val enabled = preferences[Keys.USE_DYNAMIC_COLOR] ?: false
                syncPrefs.edit().putBoolean("use_dynamic_color", enabled).apply()
                enabled
            }.distinctUntilChanged()

    suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        syncPrefs.edit().putBoolean("use_dynamic_color", useDynamicColor).apply()
        dataStore.edit { preferences ->
            preferences[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }

    val themeBrandStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val brand = preferences[Keys.THEME_BRAND] ?: "violet"
                syncPrefs.edit().putString("theme_brand", brand).apply()
                brand
            }.distinctUntilChanged()

    suspend fun setThemeBrand(themeBrand: String) {
        syncPrefs.edit().putString("theme_brand", themeBrand).apply()
        dataStore.edit { preferences ->
            preferences[Keys.THEME_BRAND] = themeBrand
        }
    }

    val surfaceStyleStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                val style = preferences[Keys.SURFACE_STYLE] ?: "classic_dynamic"
                syncPrefs.edit().putString("surface_style", style).apply()
                style
            }.distinctUntilChanged()

    suspend fun setSurfaceStyle(surfaceStyle: String) {
        syncPrefs.edit().putString("surface_style", surfaceStyle).apply()
        dataStore.edit { preferences ->
            preferences[Keys.SURFACE_STYLE] = surfaceStyle
        }
    }

    // SORTING PREFERENCES
    val subscriptionSortStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SUBSCRIPTION_SORT] ?: "SmartRank"
            }.distinctUntilChanged()

    suspend fun setSubscriptionSort(sort: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTION_SORT] = sort
        }
    }

    val latestEpisodesSortUseSmartStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.LATEST_EPISODES_SORT_USE_SMART] ?: true
            }.distinctUntilChanged()

    suspend fun setLatestEpisodesSortUseSmart(useSmart: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.LATEST_EPISODES_SORT_USE_SMART] = useSmart
        }
    }

    /** Persisted playback speed — restored across app restarts. */
    val playbackSpeedStream: Flow<Float> =
        dataStore.data
            .map { preferences ->
                preferences[Keys.PLAYBACK_SPEED] ?: 1.0f
            }.catch { exception ->
                if (exception is IOException) emit(1.0f) else throw exception
            }.distinctUntilChanged()

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.PLAYBACK_SPEED] = speed
        }
    }

    val skipBeginningMsStream: Flow<Long> =
        playbackDurationStream(
            Keys.SKIP_BEGINNING_MS,
            PlaybackSkipBounds.DEFAULT_SKIP_BEGINNING_MS,
        ) { PlaybackSkipBounds.sanitizeTrim(it) }

    val skipEndingMsStream: Flow<Long> =
        playbackDurationStream(
            Keys.SKIP_ENDING_MS,
            PlaybackSkipBounds.DEFAULT_SKIP_ENDING_MS,
        ) { PlaybackSkipBounds.sanitizeTrim(it) }

    val seekBackwardMsStream: Flow<Long> =
        playbackDurationStream(
            Keys.SEEK_BACKWARD_MS,
            PlaybackSkipBounds.DEFAULT_SEEK_BACKWARD_MS,
        ) { PlaybackSkipBounds.sanitizeSeekBackward(it) }

    val seekForwardMsStream: Flow<Long> =
        playbackDurationStream(
            Keys.SEEK_FORWARD_MS,
            PlaybackSkipBounds.DEFAULT_SEEK_FORWARD_MS,
        ) { PlaybackSkipBounds.sanitizeSeekForward(it) }

    suspend fun setSkipBeginningMs(valueMs: Long) {
        setPlaybackDuration(Keys.SKIP_BEGINNING_MS, valueMs) {
            PlaybackSkipBounds.sanitizeTrim(it)
        }
    }

    suspend fun setSkipEndingMs(valueMs: Long) {
        setPlaybackDuration(Keys.SKIP_ENDING_MS, valueMs) {
            PlaybackSkipBounds.sanitizeTrim(it)
        }
    }

    suspend fun setSeekBackwardMs(valueMs: Long) {
        setPlaybackDuration(Keys.SEEK_BACKWARD_MS, valueMs) {
            PlaybackSkipBounds.sanitizeSeekBackward(it)
        }
    }

    suspend fun setSeekForwardMs(valueMs: Long) {
        setPlaybackDuration(Keys.SEEK_FORWARD_MS, valueMs) {
            PlaybackSkipBounds.sanitizeSeekForward(it)
        }
    }

    private fun playbackDurationStream(
        key: Preferences.Key<Long>,
        defaultValue: Long,
        sanitize: (Long) -> Long,
    ): Flow<Long> =
        dataStore.data
            .map { preferences -> sanitize(preferences[key] ?: defaultValue) }
            .catch { exception ->
                if (exception is IOException) emit(defaultValue) else throw exception
            }.distinctUntilChanged()

    private suspend fun setPlaybackDuration(
        key: Preferences.Key<Long>,
        valueMs: Long,
        sanitize: (Long) -> Long,
    ) {
        dataStore.edit { preferences ->
            preferences[key] = sanitize(valueMs)
        }
    }

    val skipBehaviorStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SKIP_BEHAVIOR] ?: "just_skip"
            }.distinctUntilChanged()

    suspend fun setSkipBehavior(behavior: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SKIP_BEHAVIOR] = behavior
        }
    }

    // TOOLTIP PREFERENCES (one-time tips)
    private object TooltipKeys {
        val HAS_SEEN_SWIPE_DISMISS_TIP =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("has_seen_swipe_dismiss_tip")
        val HAS_SEEN_TITLE_TAP_TIP =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("has_seen_title_tap_tip")
        val HAS_SEEN_SWIPE_MINIMIZE_TIP =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("has_seen_swipe_minimize_tip")
        val HAS_SEEN_MARK_PLAYED_TIP =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("has_seen_mark_played_tip")
        val HAS_SEEN_LISTENING_HISTORY_TRACKING_NOTICE =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("has_seen_listening_history_tracking_notice")
    }

    val hasSeenSwipeDismissTip: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_SWIPE_DISMISS_TIP] ?: false }
            .distinctUntilChanged()

    val hasSeenTitleTapTip: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_TITLE_TAP_TIP] ?: false }
            .distinctUntilChanged()

    val hasSeenSwipeMinimizeTip: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_SWIPE_MINIMIZE_TIP] ?: false }
            .distinctUntilChanged()

    suspend fun markSwipeDismissTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_SWIPE_DISMISS_TIP] = true }
    }

    suspend fun markTitleTapTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_TITLE_TAP_TIP] = true }
    }

    suspend fun markSwipeMinimizeTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_SWIPE_MINIMIZE_TIP] = true }
    }

    val hasSeenMarkPlayedTip: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_MARK_PLAYED_TIP] ?: false }
            .distinctUntilChanged()

    suspend fun markMarkPlayedTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_MARK_PLAYED_TIP] = true }
    }

    val hasSeenListeningHistoryTrackingNotice: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[TooltipKeys.HAS_SEEN_LISTENING_HISTORY_TRACKING_NOTICE] ?: false }
            .distinctUntilChanged()

    suspend fun markListeningHistoryTrackingNoticeSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_LISTENING_HISTORY_TRACKING_NOTICE] = true }
    }

    // ANALYTICS & REVIEW KEYS
    private object AnalyticsKeys {
        val HAS_LOGGED_FIRST_PLAY =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("has_logged_first_play")
        val REVIEW_LAST_PROMPT_AT =
            androidx.datastore.preferences.core
                .longPreferencesKey("review_last_prompt_at")
        val REVIEW_PROMPT_COUNT =
            androidx.datastore.preferences.core
                .intPreferencesKey("review_prompt_count")
        val REVIEW_HAS_REVIEWED =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("review_has_reviewed")
        val REVIEW_FIRST_LAUNCH_AT =
            androidx.datastore.preferences.core
                .longPreferencesKey("review_first_launch_at")

        // NPS survey: milestone marks eligibility (pending); the event fires on
        // the next app open so it never surfaces during background playback.
        val NPS_SURVEY_PENDING =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("nps_survey_pending")
        val NPS_SURVEY_FIRED =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("nps_survey_fired")
        val NPS_SURVEY_COMPLETED_COUNT =
            androidx.datastore.preferences.core
                .intPreferencesKey("nps_survey_completed_count")
        val ENGAGEMENT_LAST_PROMPT_AT =
            androidx.datastore.preferences.core
                .longPreferencesKey("engagement_last_prompt_at")
        val NPS_LAST_SCORE =
            androidx.datastore.preferences.core
                .intPreferencesKey("nps_last_score")
        val PROMOTER_REVIEW_PENDING =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("promoter_review_pending")
        val REVIEW_MILESTONE_PENDING =
            androidx.datastore.preferences.core
                .intPreferencesKey("review_milestone_pending")
    }

    val hasLoggedFirstPlay: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[AnalyticsKeys.HAS_LOGGED_FIRST_PLAY] ?: false }
            .distinctUntilChanged()

    suspend fun markFirstPlayLogged() {
        dataStore.edit { it[AnalyticsKeys.HAS_LOGGED_FIRST_PLAY] = true }
    }

    // --- FEATURE ANNOUNCEMENT (version-specific one-time dialog) ---
    private object FeatureKeys {
        val DISMISSED_FEATURE_VERSION = stringPreferencesKey("dismissed_feature_version")
    }

    val dismissedFeatureVersion: Flow<String> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[FeatureKeys.DISMISSED_FEATURE_VERSION] ?: "" }
            .distinctUntilChanged()

    suspend fun dismissFeatureAnnouncement(version: String) {
        dataStore.edit { it[FeatureKeys.DISMISSED_FEATURE_VERSION] = version }
    }

    // --- ANNOUNCEMENT PREFERENCES ---
    private object AnnouncementKeys {
        val TITLE = stringPreferencesKey("announcement_title")
        val BODY = stringPreferencesKey("announcement_body")
        val ROUTE = stringPreferencesKey("announcement_route")
        val IMAGE_URL = stringPreferencesKey("announcement_image_url")
        val ACTION_LABEL = stringPreferencesKey("announcement_action_label")
        val SHOW_ACTION_IN_APP =
            androidx.datastore.preferences.core
                .booleanPreferencesKey("announcement_show_action_in_app")
        val TIMESTAMP =
            androidx.datastore.preferences.core
                .longPreferencesKey("announcement_timestamp")
        val CATEGORY = stringPreferencesKey("announcement_category")
    }

    data class Announcement(
        val title: String,
        val body: String,
        val route: String?,
        val imageUrl: String?,
        val actionLabel: String?,
        val showActionInApp: Boolean,
        val timestamp: Long,
        val category: String,
    )

    val activeAnnouncementStream: Flow<Announcement?> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { pref ->
                val title = pref[AnnouncementKeys.TITLE]
                val body = pref[AnnouncementKeys.BODY]
                if (!title.isNullOrBlank() && !body.isNullOrBlank()) {
                    Announcement(
                        title = title,
                        body = body,
                        route = pref[AnnouncementKeys.ROUTE],
                        imageUrl = pref[AnnouncementKeys.IMAGE_URL],
                        actionLabel = pref[AnnouncementKeys.ACTION_LABEL],
                        showActionInApp = pref[AnnouncementKeys.SHOW_ACTION_IN_APP] ?: true,
                        timestamp = pref[AnnouncementKeys.TIMESTAMP] ?: 0L,
                        category = pref[AnnouncementKeys.CATEGORY] ?: "WHAT'S NEW",
                    )
                } else {
                    null
                }
            }.distinctUntilChanged()

    suspend fun setAnnouncement(announcement: Announcement) {
        dataStore.edit {
            it[AnnouncementKeys.TITLE] = announcement.title
            it[AnnouncementKeys.BODY] = announcement.body
            if (announcement.route != null) it[AnnouncementKeys.ROUTE] = announcement.route else it.remove(AnnouncementKeys.ROUTE)
            if (announcement.imageUrl !=
                null
            ) {
                it[AnnouncementKeys.IMAGE_URL] = announcement.imageUrl
            } else {
                it.remove(AnnouncementKeys.IMAGE_URL)
            }
            if (announcement.actionLabel !=
                null
            ) {
                it[AnnouncementKeys.ACTION_LABEL] = announcement.actionLabel
            } else {
                it.remove(AnnouncementKeys.ACTION_LABEL)
            }
            it[AnnouncementKeys.SHOW_ACTION_IN_APP] = announcement.showActionInApp
            it[AnnouncementKeys.CATEGORY] = announcement.category
            it[AnnouncementKeys.TIMESTAMP] = announcement.timestamp
        }
    }

    suspend fun clearAnnouncement() {
        dataStore.edit { pref ->
            pref.remove(AnnouncementKeys.TITLE)
            pref.remove(AnnouncementKeys.BODY)
            pref.remove(AnnouncementKeys.ROUTE)
            pref.remove(AnnouncementKeys.IMAGE_URL)
            pref.remove(AnnouncementKeys.ACTION_LABEL)
            pref.remove(AnnouncementKeys.SHOW_ACTION_IN_APP)
            pref.remove(AnnouncementKeys.CATEGORY)
            pref.remove(AnnouncementKeys.TIMESTAMP)
        }
    }

    // --- APP REVIEW LOGIC ---
    val reviewHasReviewed: Flow<Boolean> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[AnalyticsKeys.REVIEW_HAS_REVIEWED] ?: false }
            .distinctUntilChanged()

    suspend fun markReviewed() {
        dataStore.edit { it[AnalyticsKeys.REVIEW_HAS_REVIEWED] = true }
    }

    suspend fun markReviewPromptShown() {
        dataStore.edit { pref ->
            val count = pref[AnalyticsKeys.REVIEW_PROMPT_COUNT] ?: 0
            pref[AnalyticsKeys.REVIEW_PROMPT_COUNT] = count + 1
            pref[AnalyticsKeys.REVIEW_LAST_PROMPT_AT] = System.currentTimeMillis()
            pref[AnalyticsKeys.ENGAGEMENT_LAST_PROMPT_AT] = System.currentTimeMillis()
            pref.remove(AnalyticsKeys.REVIEW_MILESTONE_PENDING)
        }
    }

    /**
     * Rules to show milestone Play review:
     * - A milestone (5/15/30) was reached and stored as pending (survives playback gaps)
     * - NPS survey already fired; skip detractors (score &lt;= 7)
     * - Shared 14-day engagement cooldown
     * - User has NOT reviewed yet; app installed 2+ days; max 3 lifetime; 30-day review gap
     * - Never during playback
     */
    suspend fun shouldShowReviewPrompt(isPlaying: Boolean): Boolean {
        if (isPlaying) return false

        val prefs = dataStore.data.first()
        val milestone = prefs[AnalyticsKeys.REVIEW_MILESTONE_PENDING] ?: return false
        if (milestone != 5 && milestone != 15 && milestone != 30) return false

        if (prefs[AnalyticsKeys.REVIEW_HAS_REVIEWED] == true) return false
        if (prefs[AnalyticsKeys.NPS_SURVEY_FIRED] != true) return false

        val npsScore = prefs[AnalyticsKeys.NPS_LAST_SCORE]
        if (npsScore != null && npsScore <= EngagementPromptConstants.DETRACTOR_SCORE_MAX) return false

        if (!isEngagementCooldownElapsed(prefs)) return false

        val promptCount = prefs[AnalyticsKeys.REVIEW_PROMPT_COUNT] ?: 0
        if (promptCount >= 3) return false

        val firstLaunch = prefs[AnalyticsKeys.REVIEW_FIRST_LAUNCH_AT]
        if (firstLaunch == null) {
            dataStore.edit { it[AnalyticsKeys.REVIEW_FIRST_LAUNCH_AT] = System.currentTimeMillis() }
            return false
        }

        val daysSinceInstall = (System.currentTimeMillis() - firstLaunch) / (1000 * 60 * 60 * 24)
        if (daysSinceInstall < 2) return false

        val lastPrompt = prefs[AnalyticsKeys.REVIEW_LAST_PROMPT_AT] ?: 0L
        val daysSinceLastPrompt = (System.currentTimeMillis() - lastPrompt) / (1000 * 60 * 60 * 24)
        return lastPrompt == 0L || daysSinceLastPrompt >= 30
    }

    /** Remember the highest unreached milestone so prompts survive playback gaps. */
    suspend fun syncReviewMilestonePending(completedCount: Int) {
        val milestone =
            when {
                completedCount >= 30 -> 30
                completedCount >= 15 -> 15
                completedCount >= 5 -> 5
                else -> return
            }
        dataStore.edit { pref ->
            if (pref[AnalyticsKeys.REVIEW_HAS_REVIEWED] == true) return@edit
            val current = pref[AnalyticsKeys.REVIEW_MILESTONE_PENDING]
            if (current == null || milestone > current) {
                pref[AnalyticsKeys.REVIEW_MILESTONE_PENDING] = milestone
            }
        }
    }

    suspend fun reviewMilestonePending(): Int? = dataStore.data.first()[AnalyticsKeys.REVIEW_MILESTONE_PENDING]

    /** Clears a stored milestone after the review prompt is shown or dismissed. */
    suspend fun clearReviewMilestonePending() {
        dataStore.edit { it.remove(AnalyticsKeys.REVIEW_MILESTONE_PENDING) }
    }

    /** Synchronous read of whether the user has completed the Play Store review flow. */
    suspend fun hasReviewedSync(): Boolean = dataStore.data.first()[AnalyticsKeys.REVIEW_HAS_REVIEWED] ?: false

    /** Updates the shared engagement cooldown timestamp after any proactive prompt. */
    suspend fun recordEngagementPromptShown() {
        dataStore.edit { pref ->
            pref[AnalyticsKeys.ENGAGEMENT_LAST_PROMPT_AT] = System.currentTimeMillis()
        }
    }

    /** True when at least [EngagementPromptConstants.ENGAGEMENT_COOLDOWN_DAYS] have passed since the last prompt. */
    suspend fun isEngagementCooldownElapsed(): Boolean = isEngagementCooldownElapsed(dataStore.data.first())

    private fun isEngagementCooldownElapsed(pref: Preferences): Boolean {
        val last = pref[AnalyticsKeys.ENGAGEMENT_LAST_PROMPT_AT] ?: 0L
        if (last == 0L) return true
        val days = (System.currentTimeMillis() - last) / (1000 * 60 * 60 * 24)
        return days >= EngagementPromptConstants.ENGAGEMENT_COOLDOWN_DAYS
    }

    /** Persists the most recent NPS score for milestone gating and promoter handoff. */
    suspend fun setNpsLastScore(score: Int) {
        dataStore.edit { it[AnalyticsKeys.NPS_LAST_SCORE] = score }
    }

    suspend fun npsLastScore(): Int? = dataStore.data.first()[AnalyticsKeys.NPS_LAST_SCORE]

    /** Sets whether a promoter Play review should show on the next eligible app open. */
    suspend fun setPromoterReviewPending(pending: Boolean) {
        dataStore.edit { it[AnalyticsKeys.PROMOTER_REVIEW_PENDING] = pending }
    }

    suspend fun isPromoterReviewPending(): Boolean = dataStore.data.first()[AnalyticsKeys.PROMOTER_REVIEW_PENDING] ?: false

    // --- NPS SURVEY (PostHog) TRIGGER STATE ---
    // The eligibility milestone (e.g. 3rd completed episode) can be reached
    // while playback runs in the background. Rather than fire immediately, we
    // mark the survey "pending" and let MainActivity fire the trigger event on
    // the next app open. Firing happens at most once (guarded by the fired flag).

    /** Mark the NPS survey pending (no-op if it has already fired). */
    suspend fun markNpsSurveyPending(completedCount: Int) {
        dataStore.edit { pref ->
            if (pref[AnalyticsKeys.NPS_SURVEY_FIRED] == true) return@edit
            pref[AnalyticsKeys.NPS_SURVEY_PENDING] = true
            pref[AnalyticsKeys.NPS_SURVEY_COMPLETED_COUNT] = completedCount
        }
    }

    suspend fun isNpsSurveyPending(): Boolean = dataStore.data.first()[AnalyticsKeys.NPS_SURVEY_PENDING] ?: false

    /** Whether the NPS trigger event has already fired for this install. */
    suspend fun hasNpsSurveyFired(): Boolean = dataStore.data.first()[AnalyticsKeys.NPS_SURVEY_FIRED] ?: false

    /** Completed-episode count captured when the survey became pending. */
    suspend fun npsSurveyCompletedCount(): Int? = dataStore.data.first()[AnalyticsKeys.NPS_SURVEY_COMPLETED_COUNT]

    /** Mark the NPS survey as fired and clear the pending flag. */
    suspend fun markNpsSurveyFired() {
        dataStore.edit { pref ->
            pref[AnalyticsKeys.NPS_SURVEY_FIRED] = true
            pref[AnalyticsKeys.NPS_SURVEY_PENDING] = false
        }
    }

    val hideCompletedInFeedsStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HIDE_COMPLETED_IN_FEEDS] ?: true
            }.distinctUntilChanged()

    suspend fun setHideCompletedInFeeds(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_FEEDS] = hide
        }
    }

    val hideCompletedInShowDetailsStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HIDE_COMPLETED_IN_SHOW_DETAILS] ?: false
            }.distinctUntilChanged()

    suspend fun setHideCompletedInShowDetails(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_SHOW_DETAILS] = hide
        }
    }

    val hideCompletedInHomeStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HIDE_COMPLETED_IN_HOME] ?: true
            }.distinctUntilChanged()

    suspend fun setHideCompletedInHome(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_HOME] = hide
        }
    }

    val hideCompletedInSubsStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.HIDE_COMPLETED_IN_SUBS] ?: true
            }.distinctUntilChanged()

    suspend fun setHideCompletedInSubs(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_SUBS] = hide
        }
    }

    val overriddenRecPodcastIdStream: Flow<String?> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.OVERRIDDEN_REC_PODCAST_ID]
            }.distinctUntilChanged()

    suspend fun setOverriddenRecPodcastId(podcastId: String?) {
        dataStore.edit { preferences ->
            if (podcastId == null) {
                preferences.remove(Keys.OVERRIDDEN_REC_PODCAST_ID)
            } else {
                preferences[Keys.OVERRIDDEN_REC_PODCAST_ID] = podcastId
            }
        }
    }

    val smartDownloadsEnabledStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_ENABLED] ?: false
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_ENABLED] = enabled
        }
    }

    val smartDownloadsMaxEpisodesStream: Flow<Int> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_MAX_EPISODES] ?: 10
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsMaxEpisodes(maxEpisodes: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_MAX_EPISODES] = maxEpisodes
        }
    }

    val smartDownloadsStorageBudgetStream: Flow<Long> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_STORAGE_BUDGET] ?: 1000L
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsStorageBudget(budgetMb: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_STORAGE_BUDGET] = budgetMb
        }
    }

    val smartDownloadsWifiOnlyStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_WIFI_ONLY] ?: true
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_WIFI_ONLY] = wifiOnly
        }
    }

    val smartDownloadsChargingOnlyStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_CHARGING_ONLY] ?: false
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsChargingOnly(chargingOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_CHARGING_ONLY] = chargingOnly
        }
    }

    val smartDownloadsCleanupRuleStream: Flow<String> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_CLEANUP_RULE] ?: "after_24h"
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsCleanupRule(rule: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_CLEANUP_RULE] = rule
        }
    }

    val smartDownloadsLastSyncTimeStream: Flow<Long> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.SMART_DOWNLOADS_LAST_SYNC_TIME] ?: 0L
            }.distinctUntilChanged()

    suspend fun setSmartDownloadsLastSyncTime(lastSyncTime: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_LAST_SYNC_TIME] = lastSyncTime
        }
    }

    val autoDownloadWifiOnlyStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.AUTO_DOWNLOAD_WIFI_ONLY] ?: true
            }.distinctUntilChanged()

    suspend fun setAutoDownloadWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_WIFI_ONLY] = wifiOnly
        }
    }

    val autoDownloadMaxEpisodesStream: Flow<Int> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.AUTO_DOWNLOAD_MAX_EPISODES] ?: 2
            }.distinctUntilChanged()

    suspend fun setAutoDownloadMaxEpisodes(maxEpisodes: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_MAX_EPISODES] = maxEpisodes
        }
    }

    val autoDownloadDeleteCompletedStream: Flow<Boolean> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences[Keys.AUTO_DOWNLOAD_DELETE_COMPLETED] ?: true
            }.distinctUntilChanged()

    suspend fun setAutoDownloadDeleteCompleted(deleteCompleted: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_DELETE_COMPLETED] = deleteCompleted
        }
    }

    val lastSeenEpisodesStream: Flow<Map<String, String>> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }.map { preferences ->
                preferences
                    .asMap()
                    .entries
                    .filter { it.key.name.startsWith(LAST_SEEN_EPISODE_ID_PREFIX) }
                    .mapNotNull { entry ->
                        val value = entry.value as? String
                        if (value != null) {
                            entry.key.name.removePrefix(LAST_SEEN_EPISODE_ID_PREFIX) to value
                        } else {
                            null
                        }
                    }.toMap()
            }.distinctUntilChanged()

    suspend fun setLastSeenEpisodeId(
        podcastId: String,
        episodeId: String,
    ) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("$LAST_SEEN_EPISODE_ID_PREFIX$podcastId")] = episodeId
        }
    }

    suspend fun removeLastSeenEpisodeId(podcastId: String) {
        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("$LAST_SEEN_EPISODE_ID_PREFIX$podcastId"))
        }
    }
}
