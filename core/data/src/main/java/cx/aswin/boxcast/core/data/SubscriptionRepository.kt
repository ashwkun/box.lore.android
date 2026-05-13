package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.data.database.PodcastDao
import cx.aswin.boxcast.core.data.database.PodcastEntity
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SubscriptionRepository(
    private val podcastDao: PodcastDao
) {

    val subscribedPodcastIds: Flow<Set<String>> = podcastDao.getSubscribedPodcasts()
        .map { list -> list.map { it.podcastId }.toSet() }

    fun getAllSubscribedPodcasts(): Flow<List<PodcastEntity>> {
        return podcastDao.getSubscribedPodcasts()
    }

    val subscribedPodcasts: Flow<List<Podcast>> = podcastDao.getSubscribedPodcasts()
        .map { list ->
            list.map { entity ->
                Podcast(
                    id = entity.podcastId,
                    title = entity.title,
                    artist = entity.author ?: "",
                    imageUrl = entity.imageUrl ?: "",
                    description = entity.description,
                    genre = entity.genre ?: "Podcast", // Use stored genre
                    type = entity.type,
                    latestEpisode = entity.latestEpisode,
                    podcastGuid = entity.podcastGuid,
                    fundingUrl = entity.fundingUrl,
                    fundingMessage = entity.fundingMessage,
                    medium = entity.medium,
                    hasValue = entity.hasValue,
                    updateFrequency = entity.updateFrequency
                )
            }
        }

    suspend fun toggleSubscription(podcast: Podcast) {
        val existing = podcastDao.getPodcast(podcast.id)
        val isCurrentlySubscribed = existing?.isSubscribed == true

        if (isCurrentlySubscribed) {
            // Unsubscribe
            podcastDao.setSubscribed(podcast.id, false)
        } else {
            // Subscribe (Upsert to ensure we have data for offline/Jump Back In)
            val entity = PodcastEntity(
                podcastId = podcast.id,
                title = podcast.title,
                author = podcast.artist,
                imageUrl = podcast.imageUrl,
                description = podcast.description,
                isSubscribed = true,
                genre = podcast.genre, // Persist genre for Smart Queue matching
                type = podcast.type,
                lastRefreshed = System.currentTimeMillis(),
                latestEpisode = podcast.latestEpisode,
                podcastGuid = podcast.podcastGuid,
                fundingUrl = podcast.fundingUrl,
                fundingMessage = podcast.fundingMessage,
                medium = podcast.medium,
                hasValue = podcast.hasValue
            )
            podcastDao.upsert(entity)
        }
    }

    suspend fun isSubscribed(podcastId: String): Boolean {
        return podcastDao.getPodcast(podcastId)?.isSubscribed == true
    }

    suspend fun subscribe(podcast: Podcast) {
        val existing = podcastDao.getPodcast(podcast.id)
        val entity = PodcastEntity(
            podcastId = podcast.id,
            title = podcast.title,
            author = podcast.artist,
            imageUrl = podcast.imageUrl,
            description = podcast.description,
            isSubscribed = true,
            genre = podcast.genre,
            type = podcast.type,
            lastRefreshed = existing?.lastRefreshed ?: System.currentTimeMillis(),
            latestEpisode = podcast.latestEpisode ?: existing?.latestEpisode,
            podcastGuid = existing?.podcastGuid ?: podcast.podcastGuid,
            fundingUrl = existing?.fundingUrl ?: podcast.fundingUrl,
            fundingMessage = existing?.fundingMessage ?: podcast.fundingMessage,
            medium = existing?.medium ?: podcast.medium,
            hasValue = existing?.hasValue ?: podcast.hasValue,
            updateFrequency = existing?.updateFrequency ?: podcast.updateFrequency
        )
        podcastDao.upsert(entity)
    }

    suspend fun updateLatestEpisode(podcastId: String, episode: cx.aswin.boxcast.core.model.Episode?) {
        podcastDao.updateLatestEpisode(podcastId, episode)
    }
}
