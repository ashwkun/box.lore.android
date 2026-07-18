package cx.aswin.boxlore.feature.home

import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.data.content.ContentDaypart
import java.time.DayOfWeek
import java.time.LocalDate

@Immutable
data class DiscoveryGreeting(
    val title: String,
    val subtitle: String,
    val daypart: ContentDaypart,
)

/** Pure daypart → home discovery greeting (weekend / Friday copy variants). */
internal fun discoveryGreetingFor(
    daypart: ContentDaypart,
    date: LocalDate = LocalDate.now(),
): DiscoveryGreeting =
    when (daypart) {
        ContentDaypart.MORNING ->
            DiscoveryGreeting(
                title = "Good Morning",
                subtitle =
                    if (date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                        "Catch up on the week."
                    } else {
                        "Start your day with these updates."
                    },
                daypart = daypart,
            )
        ContentDaypart.AFTERNOON ->
            DiscoveryGreeting(
                title = "Afternoon Break",
                subtitle = "Smart conversations to keep you going.",
                daypart = daypart,
            )
        ContentDaypart.EVENING ->
            DiscoveryGreeting(
                title = "Evening Unwind",
                subtitle =
                    if (date.dayOfWeek == DayOfWeek.FRIDAY) {
                        "Kick off the weekend."
                    } else {
                        "Relax, laugh, and catch up."
                    },
                daypart = daypart,
            )
        ContentDaypart.LATE_NIGHT ->
            DiscoveryGreeting(
                title = "Late Night Listen",
                subtitle = "Stories for the dark hours.",
                daypart = daypart,
            )
    }
