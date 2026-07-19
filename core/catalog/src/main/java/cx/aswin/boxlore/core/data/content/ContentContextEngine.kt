package cx.aswin.boxlore.core.data.content

import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import java.time.Clock
import java.time.ZonedDateTime

data class ContentCatalogSnapshot(
    val schemaVersion: Int,
    val catalogVersion: String,
    val validUntil: Long,
    val intents: List<ContentIntent>,
) {
    fun isSupported(now: Long): Boolean {
        return schemaVersion == SUPPORTED_SCHEMA_VERSION &&
            catalogVersion.isNotBlank() &&
            validUntil > now &&
            intents.isNotEmpty() &&
            intents.map(ContentIntent::id).distinct().size == intents.size
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1

        fun anytimeFallback(now: Long): ContentCatalogSnapshot {
            return ContentCatalogSnapshot(
                schemaVersion = SUPPORTED_SCHEMA_VERSION,
                catalogVersion = "embedded-anytime-v1",
                validUntil = Long.MAX_VALUE,
                intents = listOf(
                    ContentIntent(
                        id = "anytime",
                        objective = RankingObjective.DISCOVERY,
                        eligibleSurfaces = setOf(
                            RankingSurface.HOME,
                            RankingSurface.EXPLORE,
                            RankingSurface.ANDROID_AUTO,
                        ),
                        title = "Anytime picks",
                        subtitle = "A balanced mix for whenever you’re listening.",
                        layout = ContentLayout.PODCAST_RAIL,
                        refreshPolicy = ContentRefreshPolicy.SESSION,
                        minimumItems = 1,
                        maximumItems = 10,
                    ),
                ),
            )
        }
    }
}

class ContentContextEngine(
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun currentDaypart(): ContentDaypart {
        val local = ZonedDateTime.now(clock)
        return (local.hour * 60 + local.minute).toDaypart()
    }

    fun create(input: ContentContextInput): ContentContext {
        val local = ZonedDateTime.now(clock)
        val minuteOfDay = local.hour * 60 + local.minute
        return ContentContext(
            surface = input.surface,
            localMinuteOfDay = minuteOfDay,
            weekday = local.dayOfWeek.value,
            daypart = minuteOfDay.toDaypart(),
            region = input.region.ifBlank { "us" },
            isDriving = input.isDriving,
            isOnline = input.isOnline,
            availableMinutes = input.availableMinutes?.coerceAtLeast(0),
            currentEpisodeId = input.currentEpisodeId,
            currentPodcastId = input.currentPodcastId,
            historyMaturity = input.historyMaturity,
            subscriptionCount = input.subscriptionCount,
            sessionId = input.sessionId,
        )
    }
}

data class ContentContextInput(
    val surface: RankingSurface,
    val region: String,
    val isDriving: Boolean,
    val isOnline: Boolean,
    val availableMinutes: Int?,
    val currentEpisodeId: String?,
    val currentPodcastId: String?,
    val historyMaturity: Int,
    val subscriptionCount: Int,
    val sessionId: String,
)

class ContentIntentResolver {
    fun resolve(
        catalog: ContentCatalogSnapshot?,
        context: ContentContext,
        now: Long = System.currentTimeMillis(),
    ): Pair<String, List<ContentIntent>> {
        val effectiveCatalog = catalog
            ?.takeIf { it.isSupported(now) }
            ?: ContentCatalogSnapshot.anytimeFallback(now)
        return effectiveCatalog.catalogVersion to effectiveCatalog.intents.filter {
            it.isEligible(context)
        }
    }
}

private fun Int.toDaypart(): ContentDaypart = when (this) {
    in 5 * 60 until 12 * 60 -> ContentDaypart.MORNING
    in 12 * 60 until 17 * 60 -> ContentDaypart.AFTERNOON
    in 17 * 60 until 23 * 60 -> ContentDaypart.EVENING
    else -> ContentDaypart.LATE_NIGHT
}
