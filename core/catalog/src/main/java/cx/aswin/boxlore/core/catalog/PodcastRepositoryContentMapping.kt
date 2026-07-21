package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.catalog.BuildConfig

fun mapRegionForBriefing(region: String): String {
    return when (region.lowercase().trim()) {
        "us" -> "us"
        "in", "ind" -> "in"
        "uk", "gb" -> "uk"
        else -> "global"
    }
}

data class SearchResult(
    val podcasts: List<cx.aswin.boxlore.core.model.Podcast>,
    val correctedQuery: String? = null
)

internal data class PodcastIndexScopedInputs(
    val history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
    val subscriptionIds: List<String>,
)

data class PersonalizedContentSectionInputs(
    val history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
    val interests: List<String> = emptyList(),
    val searchTopics: List<String> = emptyList(),
    val subscribedPodcastIds: List<String> = emptyList(),
    val subscribedGenres: List<String> = emptyList(),
    val learnedGenreAffinities: Map<String, Double> = emptyMap(),
    val recentSectionIds: List<String> = emptyList(),
    val excludedPodcastIds: List<String> = emptyList(),
    val excludedEpisodeIds: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

internal val semanticMarkupPattern = Regex("<[^>]+>")
internal val semanticUrlPattern = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
internal val semanticWhitespacePattern = Regex("\\s+")

internal fun String?.toSemanticFallback(): String? {
    return this
        ?.replace(semanticMarkupPattern, " ")
        ?.replace(semanticUrlPattern, " ")
        ?.replace(semanticWhitespacePattern, " ")
        ?.trim()
        ?.take(800)
        ?.takeIf(String::isNotEmpty)
}

internal fun List<String>.toBoundedPositiveIds(
    maximum: Int = 250,
): List<Long> {
    return asSequence()
        .mapNotNull(String::toLongOrNull)
        .filter { it > 0L }
        .distinct()
        .take(maximum)
        .toList()
}

internal fun List<String>.toBoundedLanguageCodes(): List<String> {
    return asSequence()
        .map { it.trim().lowercase() }
        .filter { it.matches(Regex("^[a-z]{2,3}(?:-[a-z]{2})?$")) }
        .distinct()
        .take(4)
        .toList()
        .ifEmpty { listOf("en") }
}

internal fun RankingSurface.toContentSectionsSurface(): String? = when (this) {
    RankingSurface.HOME -> "home"
    RankingSurface.EXPLORE -> "explore"
    RankingSurface.ANDROID_AUTO -> "auto"
    else -> null
}

internal fun cx.aswin.boxlore.core.network.model.ContentCatalogResponse.toContentCatalogSnapshot(
    fetchedAt: Long,
): cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot {
    val validDurationMillis = validForSeconds
        .coerceIn(60L, 7L * 24L * 60L * 60L)
        .times(1_000L)
    val mappedIntents = intents.mapNotNull { intent ->
        val surfaces = intent.surfaces.mapNotNull(String::toRankingSurface).toSet()
        val dayparts = intent.dayparts.mapNotNull(String::toContentDaypart).toSet()
        val layout = intent.layout.toContentLayout() ?: return@mapNotNull null
        if (surfaces.isEmpty() || dayparts.isEmpty()) return@mapNotNull null
        runCatching {
            cx.aswin.boxlore.core.catalog.content.ContentIntent(
                id = intent.id,
                objective = cx.aswin.boxlore.core.ranking.RankingObjective.DISCOVERY,
                eligibleSurfaces = surfaces,
                eligibleDayparts = dayparts,
                title = intent.titleFallback,
                subtitle = intent.subtitleFallback,
                titleKey = intent.titleKey,
                subtitleKey = intent.subtitleKey,
                icon = intent.icon,
                daypartIds = intent.dayparts,
                providerQueryRef = intent.providerQueryRef,
                layout = layout,
                refreshPolicy = intent.refreshPolicy.toContentRefreshPolicy()
                    ?: cx.aswin.boxlore.core.catalog.content.ContentRefreshPolicy.SESSION,
                minimumItems = intent.minCandidates,
                maximumItems = intent.maxCandidates,
                freshnessDays = intent.freshnessDays,
                durationRange = intent.durationMinutes?.let {
                    cx.aswin.boxlore.core.catalog.content.ContentDurationRange(
                        minimumMinutes = it.min,
                        maximumMinutes = it.max,
                    )
                },
                diversity = cx.aswin.boxlore.core.catalog.content.ContentDiversityConstraints(
                    maximumItemsPerShow = intent.diversity.maxPerShow,
                    minimumDistinctShows = intent.diversity.minDistinctShows,
                ),
                quality = cx.aswin.boxlore.core.catalog.content.ContentQualityConstraints(
                    minimumSemanticScore = intent.quality.minSemanticScore,
                    unseenShowReserve = intent.quality.unseenShowReserve,
                ),
                protected = intent.layout == "protected_card",
            )
        }.getOrNull()
    }
    return cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot(
        schemaVersion = schemaVersion,
        catalogVersion = catalogVersion.toString(),
        validUntil = fetchedAt + validDurationMillis,
        intents = mappedIntents,
    )
}

internal fun String.toRankingSurface(): cx.aswin.boxlore.core.ranking.RankingSurface? {
    return when (lowercase()) {
        "home" -> cx.aswin.boxlore.core.ranking.RankingSurface.HOME
        "explore" -> cx.aswin.boxlore.core.ranking.RankingSurface.EXPLORE
        "auto" -> cx.aswin.boxlore.core.ranking.RankingSurface.ANDROID_AUTO
        else -> null
    }
}

internal fun String.toContentDaypart(): cx.aswin.boxlore.core.catalog.content.ContentDaypart? {
    return when (lowercase()) {
        "early_morning", "morning", "commute" ->
            cx.aswin.boxlore.core.catalog.content.ContentDaypart.MORNING
        "afternoon" -> cx.aswin.boxlore.core.catalog.content.ContentDaypart.AFTERNOON
        "evening" -> cx.aswin.boxlore.core.catalog.content.ContentDaypart.EVENING
        "late_night" -> cx.aswin.boxlore.core.catalog.content.ContentDaypart.LATE_NIGHT
        else -> null
    }
}

internal fun String.toContentLayout(): cx.aswin.boxlore.core.catalog.content.ContentLayout? {
    return when (lowercase()) {
        "episode_rail" -> cx.aswin.boxlore.core.catalog.content.ContentLayout.EPISODE_RAIL
        "podcast_rail" -> cx.aswin.boxlore.core.catalog.content.ContentLayout.PODCAST_RAIL
        "compact_list" -> cx.aswin.boxlore.core.catalog.content.ContentLayout.COMPACT_LIST
        "protected_card" -> cx.aswin.boxlore.core.catalog.content.ContentLayout.PROTECTED_CARD
        else -> null
    }
}

internal fun String?.toContentRefreshPolicy():
    cx.aswin.boxlore.core.catalog.content.ContentRefreshPolicy? {
    return when (this?.lowercase()) {
        "session" -> cx.aswin.boxlore.core.catalog.content.ContentRefreshPolicy.SESSION
        "manual" -> cx.aswin.boxlore.core.catalog.content.ContentRefreshPolicy.MANUAL
        "daypart" -> cx.aswin.boxlore.core.catalog.content.ContentRefreshPolicy.DAYPART
        "daily" -> cx.aswin.boxlore.core.catalog.content.ContentRefreshPolicy.DAILY
        else -> null
    }
}

data class HomeBootstrapData(
    val briefing: cx.aswin.boxlore.core.model.Briefing?,
    val briefingChapters: List<cx.aswin.boxlore.core.model.Chapter>,
    val trending: List<Podcast>,
    val curatedVibes: Map<String, List<Podcast>>,
    val recommendations: List<Episode>,
    val isRecommendationsFallback: Boolean = true
)

data class BecauseYouLikeData(
    val podcasts: List<Podcast> = emptyList(),
    val episodes: List<Episode> = emptyList()
)
