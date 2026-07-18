package cx.aswin.boxlore.core.data.service.auto

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import java.util.Calendar

@OptIn(UnstableApi::class)
internal object AutoBrowseContract {
    const val ROOT_ID = "root"
    const val HOME_ID = "home"
    const val LIBRARY_ID = "library"
    const val DOWNLOADS_ID = "downloads"
    const val DISCOVER_ID = "discover"
    const val LEGACY_EXPLORE_ID = "explore"

    const val HOME_CONTINUE_ID = "home_continue_listening"
    const val HOME_QUEUE_ID = "home_queue"
    const val HOME_NEW_EPISODES_ID = "home_new_episodes"
    const val HOME_DRIVE_MIX_ID = "home_drive_mix"

    const val LIBRARY_SUBSCRIPTIONS_ID = "library_subscriptions"
    const val LIBRARY_LIKED_ID = "library_liked"
    const val LIBRARY_HISTORY_ID = "library_history"

    const val DISCOVER_DRIVE_PICKS_ID = "discover_drive_picks"
    const val DISCOVER_TIME_PICKS_ID = "discover_time_picks"
    const val DISCOVER_GENRES_ID = "discover_genres"

    const val PLAY_ALL_NEW_ID = "play_all_new_episodes"
    const val PLAY_ALL_LIKED_ID = "play_all_liked_episodes"
    const val PLAY_ALL_DOWNLOADS_ID = "play_all_downloads"
    const val PLAY_ALL_DRIVE_ID = "play_all_drive_picks"
    const val PLAY_ALL_MIXTAPE_ID = "play_all_mixtape"

    const val SUBSCRIPTION_PREFIX = "subscription:"
    const val CURATED_PREFIX = "discover_curated_"
    const val GENRE_PREFIX = "discover_genre:"

    const val EXTRA_SOURCE = "cx.aswin.boxlore.auto.SOURCE"
    const val EXTRA_ENTRY_POINT = "entry_point"
    const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"

    const val SOURCE_CONTINUE = "continue"
    const val SOURCE_QUEUE = "queue"
    const val SOURCE_NEW = "new_episodes"
    const val SOURCE_LIKED = "liked"
    const val SOURCE_HISTORY = "history"
    const val SOURCE_DOWNLOADS = "downloads"
    const val SOURCE_DRIVE = "drive_picks"
    const val SOURCE_MIXTAPE = "mixtape"
    const val SOURCE_DISCOVER = "discover"
    const val SOURCE_SEARCH = "search"

    const val COMMAND_TOGGLE_LIKE = "AUTO_TOGGLE_LIKE"
    const val COMMAND_ADD_TO_QUEUE = "AUTO_ADD_TO_QUEUE"
    const val COMMAND_MARK_COMPLETE = "AUTO_MARK_COMPLETE"

    fun listChildrenExtras(): Bundle = contentStyleExtras(
        browsable = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
        playable = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
    )

    fun gridChildrenExtras(): Bundle = contentStyleExtras(
        browsable = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
        playable = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
    )

    fun categoryGridChildrenExtras(): Bundle = contentStyleExtras(
        browsable = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM,
        playable = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
    )

    fun itemExtras(
        source: String,
        groupTitle: String? = null,
        progress: Double? = null,
        isCompleted: Boolean = false,
        downloadStatus: Long? = null,
        singleItemStyle: Int? = null,
    ): Bundle = Bundle().apply {
        putString(EXTRA_SOURCE, source)
        putString(EXTRA_ENTRY_POINT, "android_auto_$source")
        groupTitle?.let {
            putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it)
        }
        singleItemStyle?.let {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, it)
        }
        progress?.let {
            putDouble(
                MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                it.coerceIn(0.0, 1.0),
            )
            putInt(
                MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                when {
                    isCompleted -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                    it > 0.0 -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                    else -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                },
            )
        }
        downloadStatus?.let {
            putLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS, it)
        }
    }

    fun mergeExtras(vararg bundles: Bundle?): Bundle = Bundle().apply {
        bundles.filterNotNull().forEach(::putAll)
    }

    fun driveVibes(calendar: Calendar = Calendar.getInstance()): List<String> {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val weekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY
        return when {
            day == Calendar.FRIDAY && hour in 16..23 -> listOf("drive_friday_wind_down")
            day == Calendar.SUNDAY && hour in 14..23 -> listOf("drive_sunday_reset")
            weekend && hour in 5..13 -> listOf("drive_weekend_road_trip")
            hour in 5..11 -> listOf(
                "drive_weekday_morning_brief",
                "drive_weekday_morning_energy",
            )
            hour in 12..16 -> listOf("drive_weekend_road_trip")
            hour in 17..22 -> listOf("drive_weekday_evening_catchup")
            else -> listOf("drive_late_night_stories")
        }
    }

    private fun contentStyleExtras(browsable: Int, playable: Int): Bundle =
        Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsable)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, playable)
        }
}
