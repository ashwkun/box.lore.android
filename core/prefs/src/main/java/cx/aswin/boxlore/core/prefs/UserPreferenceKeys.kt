package cx.aswin.boxlore.core.prefs

import androidx.datastore.preferences.core.stringPreferencesKey

internal object Keys {
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
    val PLAYBACK_SPEED = androidx.datastore.preferences.core.floatPreferencesKey("playback_speed")
    val SKIP_BEGINNING_MS = androidx.datastore.preferences.core.longPreferencesKey("skip_beginning_ms")
    val SKIP_ENDING_MS = androidx.datastore.preferences.core.longPreferencesKey("skip_ending_ms")
    val SEEK_BACKWARD_MS = androidx.datastore.preferences.core.longPreferencesKey("seek_backward_ms")
    val SEEK_FORWARD_MS = androidx.datastore.preferences.core.longPreferencesKey("seek_forward_ms")
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

    // HOME PERSONALIZATION REBUILD
    val STICKY_MISSION_ID = stringPreferencesKey("home_sticky_mission_id")
    val STICKY_MISSION_SLOT_KEY = stringPreferencesKey("home_sticky_mission_slot_key")
    val FIRST_LAUNCH_PERSONALIZATION_RESET_DONE =
        androidx.datastore.preferences.core
            .booleanPreferencesKey("first_launch_personalization_reset_done")
}
