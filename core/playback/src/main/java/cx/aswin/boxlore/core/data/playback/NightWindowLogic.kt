package cx.aswin.boxlore.core.data.playback

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pure "night window" id for sleep-nudge gating (10:30 PM – 4:00 AM).
 * Extracted from [cx.aswin.boxlore.core.data.PlaybackRepository].
 */
object NightWindowLogic {
    private const val START_LATE_NIGHT_MINUTES = 22 * 60 + 30
    private const val END_LATE_NIGHT_MINUTES = 4 * 60

    fun currentNightWindowId(calendar: Calendar = Calendar.getInstance()): String? {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute

        val inWindow =
            timeInMinutes >= START_LATE_NIGHT_MINUTES || timeInMinutes < END_LATE_NIGHT_MINUTES
        if (!inWindow) return null

        val day = calendar.clone() as Calendar
        if (timeInMinutes < END_LATE_NIGHT_MINUTES) {
            day.add(Calendar.DAY_OF_YEAR, -1)
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(day.time)
    }
}
