package cx.aswin.boxcast.core.data.content

object ContentSectionsDaypartResolver {
    private data class DaypartRange(
        val id: String,
        val startMinute: Int,
        val endMinute: Int,
    ) {
        fun contains(minute: Int): Boolean {
            return if (startMinute < endMinute) {
                minute in startMinute until endMinute
            } else {
                minute >= startMinute || minute < endMinute
            }
        }
    }

    // Order intentionally matches the backend's overlap priority.
    private val prioritizedRanges = listOf(
        DaypartRange("early_morning", 300, 420),
        DaypartRange("commute", 420, 600),
        DaypartRange("morning", 420, 660),
        DaypartRange("afternoon", 660, 1_020),
        DaypartRange("evening", 1_020, 1_320),
        DaypartRange("late_night", 1_320, 300),
    )

    fun resolve(localMinuteOfDay: Int): String {
        require(localMinuteOfDay in 0 until MINUTES_PER_DAY)
        return prioritizedRanges.first { it.contains(localMinuteOfDay) }.id
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

internal fun contentSectionsCacheKey(
    catalogVersion: Int,
    country: String,
    localMinuteOfDay: Int,
): String {
    return contentSectionsCacheKey(
        catalogVersion = catalogVersion,
        country = country,
        resolvedDaypart = ContentSectionsDaypartResolver.resolve(localMinuteOfDay),
    )
}

internal fun contentSectionsCacheKey(
    catalogVersion: Int,
    country: String,
    resolvedDaypart: String,
): String {
    val normalizedCountry = country.lowercase().takeIf { it.length in 2..3 } ?: "us"
    return "content_sections_v1:$catalogVersion:$normalizedCountry:$resolvedDaypart"
}
