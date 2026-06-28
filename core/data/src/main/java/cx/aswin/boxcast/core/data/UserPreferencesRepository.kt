package cx.aswin.boxcast.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.userPreferencesDataStore
    private val syncPrefs = context.getSharedPreferences("boxcast_theme_fast_cache", Context.MODE_PRIVATE)

    val cachedThemeConfig: String
        get() = syncPrefs.getString("theme_config", null) ?: "system"

    val cachedSurfaceStyle: String
        get() = syncPrefs.getString("surface_style", null) ?: "classic_dynamic"

    val cachedThemeBrand: String
        get() = syncPrefs.getString("theme_brand", null) ?: "violet"

    val cachedUseDynamicColor: Boolean
        get() = syncPrefs.getBoolean("use_dynamic_color", false)

    private object Keys {
        val REGION = stringPreferencesKey("region")
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val USE_DYNAMIC_COLOR = androidx.datastore.preferences.core.booleanPreferencesKey("use_dynamic_color")
        val THEME_BRAND = stringPreferencesKey("theme_brand")
        val SURFACE_STYLE = stringPreferencesKey("surface_style")
        val HAS_DISMISSED_REGION_NUDGE = androidx.datastore.preferences.core.booleanPreferencesKey("has_dismissed_region_nudge")
        val HAS_DISMISSED_EXPLORE_REGION_NUDGE = androidx.datastore.preferences.core.booleanPreferencesKey("has_dismissed_explore_region_nudge")
        val WAS_INITIAL_REGION_MATCH = androidx.datastore.preferences.core.booleanPreferencesKey("was_initial_region_match")
        val SUBSCRIPTION_SORT = stringPreferencesKey("subscription_sort")
        val LATEST_EPISODES_SORT_USE_SMART = androidx.datastore.preferences.core.booleanPreferencesKey("latest_episodes_sort_use_smart")
        val SKIP_BEHAVIOR = stringPreferencesKey("skip_behavior")
        val HIDE_COMPLETED_IN_FEEDS = androidx.datastore.preferences.core.booleanPreferencesKey("hide_completed_in_feeds")
        val HIDE_COMPLETED_IN_SHOW_DETAILS = androidx.datastore.preferences.core.booleanPreferencesKey("hide_completed_in_show_details")
        val HIDE_COMPLETED_IN_HOME = androidx.datastore.preferences.core.booleanPreferencesKey("hide_completed_in_home")
        val HIDE_COMPLETED_IN_SUBS = androidx.datastore.preferences.core.booleanPreferencesKey("hide_completed_in_subs")
        val HAS_DISMISSED_HOME_IMPORT_BANNER = androidx.datastore.preferences.core.booleanPreferencesKey("has_dismissed_home_import_banner")
        val BRIEFING_DISMISSED_DATE = stringPreferencesKey("briefing_dismissed_date")
        val BRIEFING_DISMISSED_FOREVER = androidx.datastore.preferences.core.booleanPreferencesKey("briefing_dismissed_forever")
        val OVERRIDDEN_REC_PODCAST_ID = stringPreferencesKey("overridden_rec_podcast_id")
        val SMART_DOWNLOADS_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("smart_downloads_enabled")
        val SMART_DOWNLOADS_MAX_EPISODES = androidx.datastore.preferences.core.intPreferencesKey("smart_downloads_max_episodes")
        val SMART_DOWNLOADS_STORAGE_BUDGET = androidx.datastore.preferences.core.longPreferencesKey("smart_downloads_storage_budget")
        val SMART_DOWNLOADS_WIFI_ONLY = androidx.datastore.preferences.core.booleanPreferencesKey("smart_downloads_wifi_only")
        val SMART_DOWNLOADS_CHARGING_ONLY = androidx.datastore.preferences.core.booleanPreferencesKey("smart_downloads_charging_only")
        val SMART_DOWNLOADS_CLEANUP_RULE = stringPreferencesKey("smart_downloads_cleanup_rule")
        val SMART_DOWNLOADS_LAST_SYNC_TIME = androidx.datastore.preferences.core.longPreferencesKey("smart_downloads_last_sync_time")
        val AUTO_DOWNLOAD_WIFI_ONLY = androidx.datastore.preferences.core.booleanPreferencesKey("auto_download_wifi_only")
        val AUTO_DOWNLOAD_MAX_EPISODES = androidx.datastore.preferences.core.intPreferencesKey("auto_download_max_episodes")
        val AUTO_DOWNLOAD_DELETE_COMPLETED = androidx.datastore.preferences.core.booleanPreferencesKey("auto_download_delete_completed")
    }

    val regionStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.REGION] ?: run {
                val localeCountry = java.util.Locale.getDefault().country.lowercase()
                if (localeCountry == "in" || localeCountry == "gb" || localeCountry == "uk") {
                    localeCountry
                } else {
                    "us"
                }
            }
        }
        .distinctUntilChanged()

    suspend fun setRegion(region: String) {
        dataStore.edit { preferences ->
            preferences[Keys.REGION] = region
            preferences[Keys.HAS_DISMISSED_REGION_NUDGE] = true
        }
    }

    val hasDismissedRegionNudgeStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.HAS_DISMISSED_REGION_NUDGE] ?: false
        }
        .distinctUntilChanged()

    suspend fun dismissRegionNudge() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_REGION_NUDGE] = true
        }
    }

    val hasDismissedExploreRegionNudgeStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.HAS_DISMISSED_EXPLORE_REGION_NUDGE] ?: false
        }
        .distinctUntilChanged()

    suspend fun dismissExploreRegionNudge() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_EXPLORE_REGION_NUDGE] = true
        }
    }

    val hasDismissedHomeImportBannerStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.HAS_DISMISSED_HOME_IMPORT_BANNER] ?: false
        }
        .distinctUntilChanged()

    suspend fun dismissHomeImportBanner() {
        dataStore.edit { preferences ->
            preferences[Keys.HAS_DISMISSED_HOME_IMPORT_BANNER] = true
        }
    }

    val briefingDismissedDate: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.BRIEFING_DISMISSED_DATE] ?: ""
        }
        .distinctUntilChanged()

    suspend fun dismissBriefing(date: String) {
        dataStore.edit { preferences ->
            preferences[Keys.BRIEFING_DISMISSED_DATE] = date
        }
    }

    val briefingDismissedForever: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.BRIEFING_DISMISSED_FOREVER] ?: false
        }
        .distinctUntilChanged()

    suspend fun dismissBriefingForever() {
        dataStore.edit { preferences ->
            preferences[Keys.BRIEFING_DISMISSED_FOREVER] = true
        }
    }

    val wasInitialRegionMatchStream: Flow<Boolean?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.WAS_INITIAL_REGION_MATCH]
        }
        .distinctUntilChanged()

    suspend fun setWasInitialRegionMatch(match: Boolean) {
        dataStore.edit { preferences ->
            if (preferences[Keys.WAS_INITIAL_REGION_MATCH] == null) {
                preferences[Keys.WAS_INITIAL_REGION_MATCH] = match
            }
        }
    }


    // THEME PREFERENCES
    val themeConfigStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val config = preferences[Keys.THEME_CONFIG] ?: "system"
            syncPrefs.edit().putString("theme_config", config).apply()
            config
        }
        .distinctUntilChanged()

    suspend fun setThemeConfig(themeConfig: String) {
        syncPrefs.edit().putString("theme_config", themeConfig).apply()
        dataStore.edit { preferences ->
            preferences[Keys.THEME_CONFIG] = themeConfig
        }
    }

    val useDynamicColorStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val enabled = preferences[Keys.USE_DYNAMIC_COLOR] ?: false
            syncPrefs.edit().putBoolean("use_dynamic_color", enabled).apply()
            enabled
        }
        .distinctUntilChanged()

    suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        syncPrefs.edit().putBoolean("use_dynamic_color", useDynamicColor).apply()
        dataStore.edit { preferences ->
            preferences[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }

    val themeBrandStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val brand = preferences[Keys.THEME_BRAND] ?: "violet"
            syncPrefs.edit().putString("theme_brand", brand).apply()
            brand
        }
        .distinctUntilChanged()

    suspend fun setThemeBrand(themeBrand: String) {
        syncPrefs.edit().putString("theme_brand", themeBrand).apply()
        dataStore.edit { preferences ->
            preferences[Keys.THEME_BRAND] = themeBrand
        }
    }

    val surfaceStyleStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val style = preferences[Keys.SURFACE_STYLE] ?: "classic_dynamic"
            syncPrefs.edit().putString("surface_style", style).apply()
            style
        }
        .distinctUntilChanged()

    suspend fun setSurfaceStyle(surfaceStyle: String) {
        syncPrefs.edit().putString("surface_style", surfaceStyle).apply()
        dataStore.edit { preferences ->
            preferences[Keys.SURFACE_STYLE] = surfaceStyle
        }
    }
    // SORTING PREFERENCES
    val subscriptionSortStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SUBSCRIPTION_SORT] ?: "SmartRank"
        }
        .distinctUntilChanged()

    suspend fun setSubscriptionSort(sort: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SUBSCRIPTION_SORT] = sort
        }
    }

    val latestEpisodesSortUseSmartStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.LATEST_EPISODES_SORT_USE_SMART] ?: true
        }
        .distinctUntilChanged()

    suspend fun setLatestEpisodesSortUseSmart(useSmart: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.LATEST_EPISODES_SORT_USE_SMART] = useSmart
        }
    }

    val skipBehaviorStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SKIP_BEHAVIOR] ?: "just_skip"
        }
        .distinctUntilChanged()

    suspend fun setSkipBehavior(behavior: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SKIP_BEHAVIOR] = behavior
        }
    }

    // TOOLTIP PREFERENCES (one-time tips)
    private object TooltipKeys {
        val HAS_SEEN_SWIPE_DISMISS_TIP = androidx.datastore.preferences.core.booleanPreferencesKey("has_seen_swipe_dismiss_tip")
        val HAS_SEEN_TITLE_TAP_TIP = androidx.datastore.preferences.core.booleanPreferencesKey("has_seen_title_tap_tip")
        val HAS_SEEN_SWIPE_MINIMIZE_TIP = androidx.datastore.preferences.core.booleanPreferencesKey("has_seen_swipe_minimize_tip")
        val HAS_SEEN_MARK_PLAYED_TIP = androidx.datastore.preferences.core.booleanPreferencesKey("has_seen_mark_played_tip")
    }

    val hasSeenSwipeDismissTip: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TooltipKeys.HAS_SEEN_SWIPE_DISMISS_TIP] ?: false }
        .distinctUntilChanged()

    val hasSeenTitleTapTip: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TooltipKeys.HAS_SEEN_TITLE_TAP_TIP] ?: false }
        .distinctUntilChanged()

    val hasSeenSwipeMinimizeTip: Flow<Boolean> = dataStore.data
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

    val hasSeenMarkPlayedTip: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TooltipKeys.HAS_SEEN_MARK_PLAYED_TIP] ?: false }
        .distinctUntilChanged()

    suspend fun markMarkPlayedTipSeen() {
        dataStore.edit { it[TooltipKeys.HAS_SEEN_MARK_PLAYED_TIP] = true }
    }

    // ANALYTICS & REVIEW KEYS
    private object AnalyticsKeys {
        val HAS_LOGGED_FIRST_PLAY = androidx.datastore.preferences.core.booleanPreferencesKey("has_logged_first_play")
        val REVIEW_LAST_PROMPT_AT = androidx.datastore.preferences.core.longPreferencesKey("review_last_prompt_at")
        val REVIEW_PROMPT_COUNT = androidx.datastore.preferences.core.intPreferencesKey("review_prompt_count")
        val REVIEW_HAS_REVIEWED = androidx.datastore.preferences.core.booleanPreferencesKey("review_has_reviewed")
        val REVIEW_FIRST_LAUNCH_AT = androidx.datastore.preferences.core.longPreferencesKey("review_first_launch_at")
    }

    val hasLoggedFirstPlay: Flow<Boolean> = dataStore.data
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
    
    val dismissedFeatureVersion: Flow<String> = dataStore.data
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
        val TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("announcement_timestamp")
    }
    
    data class Announcement(val title: String, val body: String, val route: String?, val imageUrl: String?, val timestamp: Long)
    
    val activeAnnouncementStream: Flow<Announcement?> = dataStore.data
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
                    timestamp = pref[AnnouncementKeys.TIMESTAMP] ?: 0L
                )
            } else null
        }
        .distinctUntilChanged()
        
    suspend fun setAnnouncement(title: String, body: String, route: String?, imageUrl: String?, timestamp: Long) {
        dataStore.edit {
            it[AnnouncementKeys.TITLE] = title
            it[AnnouncementKeys.BODY] = body
            if (route != null) it[AnnouncementKeys.ROUTE] = route else it.remove(AnnouncementKeys.ROUTE)
            if (imageUrl != null) it[AnnouncementKeys.IMAGE_URL] = imageUrl else it.remove(AnnouncementKeys.IMAGE_URL)
            it[AnnouncementKeys.TIMESTAMP] = timestamp
        }
    }
    
    suspend fun clearAnnouncement() {
        dataStore.edit { pref ->
            pref.remove(AnnouncementKeys.TITLE)
            pref.remove(AnnouncementKeys.BODY)
            pref.remove(AnnouncementKeys.ROUTE)
            pref.remove(AnnouncementKeys.TIMESTAMP)
        }
    }
    
    // --- APP REVIEW LOGIC ---
    val reviewHasReviewed: Flow<Boolean> = dataStore.data
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
        }
    }

    /**
     * Rules to show:
     * - User has NOT reviewed yet
     * - App installed for at least 2 days
     * - Max 3 prompts lifetime
     * - Minimum 30 days between prompts
     * - They hit a milestone (5, 15, or 30 episodes completed)
     */
    suspend fun shouldShowReviewPrompt(completedCount: Int, isPlaying: Boolean): Boolean {
        if (isPlaying) return false // Never interrupt playback
        
        // Only trigger on exact milestones so it doesn't prompt continuously
        if (completedCount != 5 && completedCount != 15 && completedCount != 30) return false

        var shouldShow = false
        dataStore.edit { pref ->
            val hasReviewed = pref[AnalyticsKeys.REVIEW_HAS_REVIEWED] ?: false
            if (hasReviewed) return@edit // Early out

            val promptCount = pref[AnalyticsKeys.REVIEW_PROMPT_COUNT] ?: 0
            if (promptCount >= 3) return@edit // Lifetime cap

            // Initialize first launch time if empty
            val firstLaunchStr = pref[AnalyticsKeys.REVIEW_FIRST_LAUNCH_AT]
            if (firstLaunchStr == null) {
                 pref[AnalyticsKeys.REVIEW_FIRST_LAUNCH_AT] = System.currentTimeMillis()
                 return@edit // Don't show on very first day
            }
            
            val daysSinceInstall = (System.currentTimeMillis() - firstLaunchStr) / (1000 * 60 * 60 * 24)
            if (daysSinceInstall < 2) return@edit

            val lastPrompt = pref[AnalyticsKeys.REVIEW_LAST_PROMPT_AT] ?: 0L
            val daysSinceLastPrompt = (System.currentTimeMillis() - lastPrompt) / (1000 * 60 * 60 * 24)
            
            if (lastPrompt == 0L || daysSinceLastPrompt >= 30) {
                // We don't mark as shown here, we just check. The UI tier will mark it.
                shouldShow = true
            }
        }
        
        return shouldShow
    }

    val hideCompletedInFeedsStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_FEEDS] ?: true
        }
        .distinctUntilChanged()

    suspend fun setHideCompletedInFeeds(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_FEEDS] = hide
        }
    }

    val hideCompletedInShowDetailsStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_SHOW_DETAILS] ?: false
        }
        .distinctUntilChanged()

    suspend fun setHideCompletedInShowDetails(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_SHOW_DETAILS] = hide
        }
    }

    val hideCompletedInHomeStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_HOME] ?: true
        }
        .distinctUntilChanged()

    suspend fun setHideCompletedInHome(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_HOME] = hide
        }
    }

    val hideCompletedInSubsStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_SUBS] ?: true
        }
        .distinctUntilChanged()

    suspend fun setHideCompletedInSubs(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_COMPLETED_IN_SUBS] = hide
        }
    }

    val overriddenRecPodcastIdStream: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.OVERRIDDEN_REC_PODCAST_ID]
        }
        .distinctUntilChanged()

    suspend fun setOverriddenRecPodcastId(podcastId: String?) {
        dataStore.edit { preferences ->
            if (podcastId == null) {
                preferences.remove(Keys.OVERRIDDEN_REC_PODCAST_ID)
            } else {
                preferences[Keys.OVERRIDDEN_REC_PODCAST_ID] = podcastId
            }
        }
    }

    val smartDownloadsEnabledStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SMART_DOWNLOADS_ENABLED] ?: false
        }
        .distinctUntilChanged()

    suspend fun setSmartDownloadsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_ENABLED] = enabled
        }
    }

    val smartDownloadsMaxEpisodesStream: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SMART_DOWNLOADS_MAX_EPISODES] ?: 10
        }
        .distinctUntilChanged()

    suspend fun setSmartDownloadsMaxEpisodes(maxEpisodes: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_MAX_EPISODES] = maxEpisodes
        }
    }

    val smartDownloadsStorageBudgetStream: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SMART_DOWNLOADS_STORAGE_BUDGET] ?: 1000L
        }
        .distinctUntilChanged()

    suspend fun setSmartDownloadsStorageBudget(budgetMb: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_STORAGE_BUDGET] = budgetMb
        }
    }

    val smartDownloadsWifiOnlyStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SMART_DOWNLOADS_WIFI_ONLY] ?: true
        }
        .distinctUntilChanged()

    suspend fun setSmartDownloadsWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_WIFI_ONLY] = wifiOnly
        }
    }

    val smartDownloadsChargingOnlyStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SMART_DOWNLOADS_CHARGING_ONLY] ?: false
        }
        .distinctUntilChanged()

    suspend fun setSmartDownloadsChargingOnly(chargingOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_CHARGING_ONLY] = chargingOnly
        }
    }

    val smartDownloadsCleanupRuleStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SMART_DOWNLOADS_CLEANUP_RULE] ?: "after_24h"
        }
        .distinctUntilChanged()

    suspend fun setSmartDownloadsCleanupRule(rule: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_CLEANUP_RULE] = rule
        }
    }

    val smartDownloadsLastSyncTimeStream: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.SMART_DOWNLOADS_LAST_SYNC_TIME] ?: 0L
        }
        .distinctUntilChanged()

    suspend fun setSmartDownloadsLastSyncTime(lastSyncTime: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.SMART_DOWNLOADS_LAST_SYNC_TIME] = lastSyncTime
        }
    }

    val autoDownloadWifiOnlyStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_WIFI_ONLY] ?: true
        }
        .distinctUntilChanged()

    suspend fun setAutoDownloadWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_WIFI_ONLY] = wifiOnly
        }
    }

    val autoDownloadMaxEpisodesStream: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_MAX_EPISODES] ?: 2
        }
        .distinctUntilChanged()

    suspend fun setAutoDownloadMaxEpisodes(maxEpisodes: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_MAX_EPISODES] = maxEpisodes
        }
    }

    val autoDownloadDeleteCompletedStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_DELETE_COMPLETED] ?: true
        }
        .distinctUntilChanged()

    suspend fun setAutoDownloadDeleteCompleted(deleteCompleted: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_DOWNLOAD_DELETE_COMPLETED] = deleteCompleted
        }
    }
}

