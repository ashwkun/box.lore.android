package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * Pure Explore browse helpers extracted from [cx.aswin.boxlore.feature.explore.ExploreViewModel].
 */
object ExploreBrowseLogic {
    fun vibesForHour(hourOfDay: Int): List<Pair<String, String>> {
        val morning = listOf(
            "morning_news" to "Top News",
            "morning_motivation" to "Daily Motivation",
            "business_insider" to "Business & Tech",
        )
        val afternoon = listOf(
            "science_explainer" to "Science & Discovery",
            "tech_culture" to "Tech & Gadgets",
            "creative_focus" to "Creative Focus",
        )
        val evening = listOf(
            "comedy_gold" to "Comedy Gold",
            "tv_film_buff" to "TV & Film",
            "sports_fan" to "Sports Highlights",
        )
        val lateNight = listOf(
            "true_crime_sleep" to "True Crime & Chill",
            "history_buff" to "History",
            "mystery_thriller" to "Mystery & Thrillers",
        )
        return when (hourOfDay) {
            in 5..11 -> morning + afternoon + evening + lateNight
            in 12..16 -> afternoon + evening + lateNight + morning
            in 17..22 -> evening + lateNight + morning + afternoon
            else -> lateNight + morning + afternoon + evening
        }
    }

    fun filterPodcastsBySubstring(query: String, podcasts: Collection<Podcast>): List<Podcast> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return podcasts.filter { podcast ->
            podcast.title.contains(trimmed, ignoreCase = true) ||
                podcast.artist.contains(trimmed, ignoreCase = true)
        }.sortedBy { it.title }
    }

    fun <T> mergeUniqueById(existing: List<T>, incoming: List<T>, idOf: (T) -> String): List<T> {
        val existingIds = existing.map(idOf).toSet()
        return existing + incoming.filter { idOf(it) !in existingIds }
    }

    fun episodeToSearchPodcast(episode: Episode): Podcast = Podcast(
        id = episode.podcastId.orEmpty(),
        title = episode.podcastTitle.orEmpty(),
        artist = episode.podcastArtist.orEmpty(),
        imageUrl = episode.podcastImageUrl ?: episode.imageUrl.orEmpty(),
        genre = episode.podcastGenre.orEmpty(),
        latestEpisode = episode,
    )
}
