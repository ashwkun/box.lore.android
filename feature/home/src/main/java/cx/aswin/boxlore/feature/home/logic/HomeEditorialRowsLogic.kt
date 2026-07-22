package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeEditorialIcon
import cx.aswin.boxlore.feature.home.HomeEditorialRow

internal data class HomeEditorialRowDefinition(
    val providerId: String,
    val title: String,
    val subtitle: String,
    val icon: HomeEditorialIcon,
)

internal fun editorialRowDefinitionsFor(daypart: ContentDaypart): List<HomeEditorialRowDefinition> =
    when (daypart) {
        ContentDaypart.MORNING ->
            listOf(
                HomeEditorialRowDefinition(
                    providerId = "morning_news",
                    title = "What's happening",
                    subtitle = "The stories shaping today",
                    icon = HomeEditorialIcon.HEADLINES,
                ),
                HomeEditorialRowDefinition(
                    providerId = "morning_motivation",
                    title = "A brighter start",
                    subtitle = "Ideas and stories with a little lift",
                    icon = HomeEditorialIcon.UPLIFTING,
                ),
                HomeEditorialRowDefinition(
                    providerId = "business_insider",
                    title = "Business in focus",
                    subtitle = "Markets, technology, and the people moving them",
                    icon = HomeEditorialIcon.BUSINESS,
                ),
            )
        ContentDaypart.AFTERNOON ->
            listOf(
                HomeEditorialRowDefinition(
                    providerId = "science_explainer",
                    title = "Worth knowing",
                    subtitle = "Clear answers to curious questions",
                    icon = HomeEditorialIcon.SCIENCE,
                ),
                HomeEditorialRowDefinition(
                    providerId = "tech_culture",
                    title = "Tech right now",
                    subtitle = "The ideas changing how we live and work",
                    icon = HomeEditorialIcon.TECHNOLOGY,
                ),
                HomeEditorialRowDefinition(
                    providerId = "creative_focus",
                    title = "Creative spark",
                    subtitle = "Fresh perspectives from art and design",
                    icon = HomeEditorialIcon.CREATIVITY,
                ),
            )
        ContentDaypart.EVENING ->
            listOf(
                HomeEditorialRowDefinition(
                    providerId = "comedy_gold",
                    title = "A good laugh",
                    subtitle = "Comedy and conversation for winding down",
                    icon = HomeEditorialIcon.COMEDY,
                ),
                HomeEditorialRowDefinition(
                    providerId = "tv_film_buff",
                    title = "On screen",
                    subtitle = "Film, television, and culture worth talking about",
                    icon = HomeEditorialIcon.SCREEN,
                ),
                HomeEditorialRowDefinition(
                    providerId = "sports_fan",
                    title = "Game time",
                    subtitle = "Stories and analysis from across sport",
                    icon = HomeEditorialIcon.SPORTS,
                ),
            )
        ContentDaypart.LATE_NIGHT ->
            listOf(
                HomeEditorialRowDefinition(
                    providerId = "true_crime_sleep",
                    title = "True crime after dark",
                    subtitle = "Investigations that keep you listening",
                    icon = HomeEditorialIcon.TRUE_CRIME,
                ),
                HomeEditorialRowDefinition(
                    providerId = "history_buff",
                    title = "Stories from history",
                    subtitle = "The past, told like it happened yesterday",
                    icon = HomeEditorialIcon.HISTORY,
                ),
                HomeEditorialRowDefinition(
                    providerId = "mystery_thriller",
                    title = "Mystery & suspense",
                    subtitle = "Twists, tension, and stories for the night",
                    icon = HomeEditorialIcon.MYSTERY,
                ),
            )
    }

internal fun buildHomeEditorialRows(
    daypart: ContentDaypart,
    podcastsByProvider: Map<String, List<Podcast>>,
    maximumItemsPerRow: Int = 8,
): List<HomeEditorialRow> {
    if (maximumItemsPerRow <= 0) return emptyList()
    val seenPodcastIds = mutableSetOf<String>()
    val seenEpisodeIds = mutableSetOf<String>()
    return editorialRowDefinitionsFor(daypart).mapNotNull { definition ->
        val podcasts =
            podcastsByProvider[definition.providerId]
                .orEmpty()
                .asSequence()
                .filter { podcast ->
                    val episode = podcast.latestEpisode
                    episode != null &&
                        episode.id.isNotBlank() &&
                        episode.audioUrl.isNotBlank() &&
                        podcast.id.isNotBlank() &&
                        podcast.id !in seenPodcastIds &&
                        episode.id !in seenEpisodeIds
                }.onEach { podcast ->
                    seenPodcastIds += podcast.id
                    podcast.latestEpisode?.id?.let(seenEpisodeIds::add)
                }.take(maximumItemsPerRow)
                .toList()
        if (podcasts.isEmpty()) {
            null
        } else {
            HomeEditorialRow(
                providerId = definition.providerId,
                title = definition.title,
                subtitle = definition.subtitle,
                icon = definition.icon,
                podcasts = podcasts,
            )
        }
    }
}
