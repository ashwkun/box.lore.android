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
                    fallbackImageUrl = entity.latestEpisode?.imageUrl ?: "",
                    description = entity.description,
                    genre = entity.genre ?: "Podcast", // Use stored genre
                    type = entity.type,
                    latestEpisode = entity.latestEpisode,
                    subscribedAt = entity.subscribedAt,
                    podcastGuid = entity.podcastGuid,
                    fundingUrl = entity.fundingUrl,
                    fundingMessage = entity.fundingMessage,
                    medium = entity.medium,
                    hasValue = entity.hasValue,
                    updateFrequency = entity.updateFrequency,
                    location = entity.location,
                    license = entity.license,
                    isLocked = entity.isLocked,
                    preferredSort = entity.preferredSort
                )
            }
        }

    suspend fun toggleSubscription(podcast: Podcast) {
        val existing = podcastDao.getPodcast(podcast.id)
        val isCurrentlySubscribed = existing?.isSubscribed == true

        if (isCurrentlySubscribed) {
            // Unsubscribe
            existing?.let {
                val updated = it.copy(isSubscribed = false, subscribedAt = 0L)
                podcastDao.upsert(updated)
            } ?: podcastDao.setSubscribed(podcast.id, false)
        } else {
            // Subscribe (Upsert to ensure we have data for offline/Jump Back In)
            val entity = PodcastEntity(
                podcastId = podcast.id,
                title = podcast.title,
                author = podcast.artist,
                imageUrl = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: existing?.imageUrl ?: "",
                description = podcast.description,
                isSubscribed = true,
                subscribedAt = System.currentTimeMillis(),
                genre = podcast.genre, // Persist genre for Smart Queue matching
                type = podcast.type,
                lastRefreshed = System.currentTimeMillis(),
                latestEpisode = podcast.latestEpisode?.let { ep ->
                    ep.copy(
                        podcastId = ep.podcastId.takeIf { !it.isNullOrBlank() } ?: podcast.id,
                        podcastTitle = ep.podcastTitle.takeIf { !it.isNullOrBlank() } ?: podcast.title
                    )
                },
                podcastGuid = podcast.podcastGuid,
                fundingUrl = podcast.fundingUrl,
                fundingMessage = podcast.fundingMessage,
                medium = podcast.medium,
                hasValue = podcast.hasValue,
                updateFrequency = podcast.updateFrequency,
                location = podcast.location,
                license = podcast.license,
                isLocked = podcast.isLocked,
                preferredSort = existing?.preferredSort // Preserve existing sort preference
            )
            podcastDao.upsert(entity)
        }
    }

    suspend fun isSubscribed(podcastId: String): Boolean {
        return podcastDao.getPodcast(podcastId)?.isSubscribed == true
    }

    suspend fun subscribe(podcast: Podcast) {
        val existing = podcastDao.getPodcast(podcast.id)
        val preferredSortVal = existing?.preferredSort ?: if (podcast.type == "serial") "oldest" else "newest"
        val typeVal = if (preferredSortVal == "oldest" || podcast.type == "serial") "serial" else "episodic"
        val entity = PodcastEntity(
            podcastId = podcast.id,
            title = podcast.title,
            author = podcast.artist,
            imageUrl = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: existing?.imageUrl ?: "",
            description = podcast.description,
            isSubscribed = true,
            subscribedAt = if (existing?.isSubscribed == true) existing.subscribedAt else System.currentTimeMillis(),
            genre = podcast.genre,
            type = typeVal,
            lastRefreshed = existing?.lastRefreshed ?: System.currentTimeMillis(),
            latestEpisode = (podcast.latestEpisode ?: existing?.latestEpisode)?.let { ep ->
                ep.copy(
                    podcastId = ep.podcastId.takeIf { !it.isNullOrBlank() } ?: podcast.id,
                    podcastTitle = ep.podcastTitle.takeIf { !it.isNullOrBlank() } ?: podcast.title
                )
            },
            podcastGuid = existing?.podcastGuid ?: podcast.podcastGuid,
            fundingUrl = existing?.fundingUrl ?: podcast.fundingUrl,
            fundingMessage = existing?.fundingMessage ?: podcast.fundingMessage,
            medium = existing?.medium ?: podcast.medium,
            hasValue = existing?.hasValue ?: podcast.hasValue,
            updateFrequency = existing?.updateFrequency ?: podcast.updateFrequency,
            location = existing?.location ?: podcast.location,
            license = existing?.license ?: podcast.license,
            isLocked = existing?.isLocked ?: podcast.isLocked,
            preferredSort = preferredSortVal
        )
        podcastDao.upsert(entity)
    }

    suspend fun updateLatestEpisode(podcastId: String, episode: cx.aswin.boxcast.core.model.Episode?) {
        val enrichedEpisode = episode?.let { ep ->
            val resolvedTitle = if (ep.podcastTitle.isNullOrBlank()) {
                val podcast = podcastDao.getPodcast(podcastId)
                podcast?.title
            } else {
                ep.podcastTitle
            }
            ep.copy(
                podcastId = ep.podcastId.takeIf { !it.isNullOrBlank() } ?: podcastId,
                podcastTitle = resolvedTitle
            )
        }
        podcastDao.updateLatestEpisode(podcastId, enrichedEpisode)
    }

    suspend fun updatePreferredSort(podcastId: String, sort: String?) {
        val type = if (sort == "oldest") "serial" else "episodic"
        podcastDao.updatePreferredSortAndType(podcastId, sort, type)
    }
}
