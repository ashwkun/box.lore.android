package cx.aswin.boxcast.core.data.content

import cx.aswin.boxcast.core.data.ranking.CandidateSource
import cx.aswin.boxcast.core.data.ranking.RankingObjective
import cx.aswin.boxcast.core.data.ranking.RankingSurface
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.network.model.ContentSectionEpisodeDto
import cx.aswin.boxcast.core.network.model.ContentSectionsV1Response

class ServerGroupedSectionProvider(
    private val loader: suspend (ContentContext) -> GroupedContentSections?,
) : GroupedCandidateProvider {
    override val source: CandidateSource = CandidateSource.SERVER_RECOMMENDATION

    override suspend fun sections(context: ContentContext): GroupedContentSections? = loader(context)
}

internal fun ContentSectionsV1Response.toGroupedContentSections(
    catalog: ContentCatalogSnapshot?,
    seenPodcastIds: Set<String>,
): GroupedContentSections? {
    val responseCatalogVersion = catalogVersion ?: return null
    val responseDaypart = resolvedDaypart?.takeIf(String::isNotBlank) ?: return null
    val responseAlgorithmVersion = algorithmVersion?.takeIf(String::isNotBlank) ?: return null
    if (status != "true" || contractVersion != 1) return null
    if (catalog != null && catalog.catalogVersion != responseCatalogVersion.toString()) return null

    val catalogIntents = catalog?.intents.orEmpty().associateBy(ContentIntent::id)
    val mappedSections = sections.mapNotNull { section ->
        val layout = section.layout.toContentLayout() ?: return@mapNotNull null
        val baseIntent = catalogIntents[section.intent.id]
        val intent = if (baseIntent != null) {
            baseIntent.copy(
                title = section.intent.titleFallback,
                subtitle = section.intent.subtitleFallback,
                titleKey = section.intent.titleKey,
                subtitleKey = section.intent.subtitleKey,
                icon = section.intent.icon,
                daypartIds = section.intent.dayparts,
                layout = layout,
                refreshPolicy = section.intent.refreshPolicy.toContentRefreshPolicy()
                    ?: baseIntent.refreshPolicy,
                protected = layout == ContentLayout.PROTECTED_CARD,
            )
        } else {
            ContentIntent(
                id = section.intent.id,
                objective = RankingObjective.DISCOVERY,
                eligibleSurfaces = setOf(
                    RankingSurface.HOME,
                    RankingSurface.EXPLORE,
                    RankingSurface.ANDROID_AUTO,
                ),
                eligibleDayparts = section.intent.dayparts
                    .mapNotNull(String::toBroadContentDaypart)
                    .toSet()
                    .ifEmpty { ContentDaypart.entries.toSet() },
                title = section.intent.titleFallback,
                subtitle = section.intent.subtitleFallback,
                titleKey = section.intent.titleKey,
                subtitleKey = section.intent.subtitleKey,
                icon = section.intent.icon,
                daypartIds = section.intent.dayparts,
                layout = layout,
                refreshPolicy = section.intent.refreshPolicy.toContentRefreshPolicy()
                    ?: ContentRefreshPolicy.DAYPART,
                minimumItems = 1,
                maximumItems = section.items.size.coerceAtLeast(1),
                protected = layout == ContentLayout.PROTECTED_CARD,
            )
        }
        val candidates = section.items.map { item ->
            item.toContentCandidate(intent.id, seenPodcastIds)
        }
        GroupedContentSection(intent = intent, items = candidates)
    }
    return GroupedContentSections(
        contractVersion = 1,
        catalogVersion = responseCatalogVersion.toString(),
        resolvedDaypart = responseDaypart,
        algorithmVersion = responseAlgorithmVersion,
        isFallback = isFallback,
        generatedAt = generatedAt,
        sections = mappedSections,
    )
}

private fun ContentSectionEpisodeDto.toContentCandidate(
    intentId: String,
    seenPodcastIds: Set<String>,
): ContentCandidate {
    val podcastId = feedId.toString()
    val episode = Episode(
        id = id.toString(),
        title = title,
        description = description,
        audioUrl = enclosureUrl,
        imageUrl = image.ifBlank { feedImage }.toHttps(),
        podcastImageUrl = feedImage.toHttps(),
        podcastTitle = feedTitle,
        podcastId = podcastId,
        podcastGenre = genre,
        duration = duration,
        publishedDate = datePublished,
        retrievalScore = retrievalScore,
        semanticScore = semanticScore,
        recommendationSource = source,
        recommendationReason = reason,
        serverRank = serverRank,
        recommendationAlgorithmVersion = algorithmVersion,
        language = language,
    )
    val podcast = Podcast(
        id = podcastId,
        title = feedTitle,
        artist = "",
        imageUrl = feedImage.toHttps().orEmpty(),
        description = null,
        genre = genre.ifBlank { "Podcast" },
        latestEpisode = episode,
        subscribedAt = if (podcastId in seenPodcastIds) 1L else 0L,
    )
    return ContentCandidate(
        id = episode.id,
        episode = episode,
        podcast = podcast,
        source = CandidateSource.SERVER_RECOMMENDATION,
        intentId = intentId,
        retrievalScore = retrievalScore,
        isNovel = source == "unseen_exploration" || podcastId !in seenPodcastIds,
        semanticScore = semanticScore,
        serverRank = serverRank,
        explanationTokens = setOf(reason, source, algorithmVersion),
    )
}

private fun String?.toHttps(): String? {
    if (this.isNullOrBlank()) return null
    return if (startsWith("http://")) replaceFirst("http://", "https://") else this
}

private fun String.toContentLayout(): ContentLayout? = when (lowercase()) {
    "episode_rail" -> ContentLayout.EPISODE_RAIL
    "podcast_rail" -> ContentLayout.PODCAST_RAIL
    "compact_list" -> ContentLayout.COMPACT_LIST
    "protected_card" -> ContentLayout.PROTECTED_CARD
    else -> null
}

private fun String.toBroadContentDaypart(): ContentDaypart? = when (lowercase()) {
    "early_morning", "morning", "commute" -> ContentDaypart.MORNING
    "afternoon" -> ContentDaypart.AFTERNOON
    "evening" -> ContentDaypart.EVENING
    "late_night" -> ContentDaypart.LATE_NIGHT
    else -> null
}

private fun String?.toContentRefreshPolicy(): ContentRefreshPolicy? = when (this?.lowercase()) {
    "session" -> ContentRefreshPolicy.SESSION
    "manual" -> ContentRefreshPolicy.MANUAL
    "daypart" -> ContentRefreshPolicy.DAYPART
    "daily" -> ContentRefreshPolicy.DAILY
    else -> null
}
