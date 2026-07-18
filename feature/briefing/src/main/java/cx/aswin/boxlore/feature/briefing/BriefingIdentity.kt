package cx.aswin.boxlore.feature.briefing

import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.Briefing

/**
 * Stable briefing episode id and cover artwork mapping (no Android context).
 */
internal object BriefingIdentity {
    fun episodeId(briefing: Briefing): String = "briefing_${briefing.region}_${briefing.date}"

    fun coverDrawableRes(region: String): Int =
        when (region.lowercase()) {
            "in", "ind" -> R.drawable.daily_briefing_india
            "uk", "gb" -> R.drawable.daily_briefing_uk
            "us", "usa" -> R.drawable.daily_briefing_usa
            else -> R.drawable.daily_briefing_global
        }
}
