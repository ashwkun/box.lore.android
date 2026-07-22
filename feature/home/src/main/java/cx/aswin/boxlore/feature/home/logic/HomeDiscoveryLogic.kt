package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HomeEditorialRow
import cx.aswin.boxlore.feature.home.SmartHeroItem

internal fun discoverPodcastsExcluding(
    trending: List<Podcast>,
    heroItems: List<SmartHeroItem>,
    editorialRows: List<HomeEditorialRow>,
): List<Podcast>? {
    if (trending.isEmpty()) return null
    return trending.filter { podcast ->
        heroItems.none { it.podcast.id == podcast.id } &&
            editorialRows.none { row ->
                row.podcasts.any { candidate -> candidate.id == podcast.id }
            }
    }
}
