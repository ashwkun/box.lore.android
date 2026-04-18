package cx.aswin.boxcast.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.userPreferencesDataStore

    private object Keys {
        val REGION = stringPreferencesKey("region")
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val USE_DYNAMIC_COLOR = androidx.datastore.preferences.core.booleanPreferencesKey("use_dynamic_color")
        val THEME_BRAND = stringPreferencesKey("theme_brand")
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
            preferences[Keys.REGION] ?: "us"
        }

    suspend fun setRegion(region: String) {
        dataStore.edit { preferences ->
            preferences[Keys.REGION] = region
        }
    }

    // THEME PREFERENCES
    val themeConfigStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.THEME_CONFIG] ?: "system"
        }

    suspend fun setThemeConfig(themeConfig: String) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_CONFIG] = themeConfig
        }
    }

    val useDynamicColorStream: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.USE_DYNAMIC_COLOR] ?: true
        }

    suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }

    val themeBrandStream: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[Keys.THEME_BRAND] ?: "violet"
        }

    suspend fun setThemeBrand(themeBrand: String) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_BRAND] = themeBrand
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

    val hasSeenTitleTapTip: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TooltipKeys.HAS_SEEN_TITLE_TAP_TIP] ?: false }

    val hasSeenSwipeMinimizeTip: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TooltipKeys.HAS_SEEN_SWIPE_MINIMIZE_TIP] ?: false }

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

    suspend fun markFirstPlayLogged() {
        dataStore.edit { it[AnalyticsKeys.HAS_LOGGED_FIRST_PLAY] = true }
    }
    
    // --- ANNOUNCEMENT PREFERENCES ---
    private object AnnouncementKeys {
        val TITLE = stringPreferencesKey("announcement_title")
        val BODY = stringPreferencesKey("announcement_body")
        val ROUTE = stringPreferencesKey("announcement_route")
        val TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("announcement_timestamp")
    }
    
    data class Announcement(val title: String, val body: String, val route: String?, val timestamp: Long)
    
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
                    timestamp = pref[AnnouncementKeys.TIMESTAMP] ?: 0L
                )
            } else {
                null
            }
        }
        
    suspend fun setAnnouncement(title: String, body: String, route: String?, timestamp: Long) {
        dataStore.edit { pref ->
            pref[AnnouncementKeys.TITLE] = title
            pref[AnnouncementKeys.BODY] = body
            if (route != null) {
                pref[AnnouncementKeys.ROUTE] = route
            } else {
                pref.remove(AnnouncementKeys.ROUTE)
            }
            pref[AnnouncementKeys.TIMESTAMP] = timestamp
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
}

