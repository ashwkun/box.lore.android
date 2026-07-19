package cx.aswin.boxlore.core.data

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.model.Podcast

/**
 * Room-backed [LocalCatalogPort] for feature ViewModels and nav helpers.
 */
class RoomLocalCatalog(
    private val database: BoxLoreDatabase,
) : LocalCatalogPort {
    private val podcastDao get() = database.podcastDao()

    override suspend fun getLocalPodcast(id: String): Podcast? =
        podcastDao.getPodcast(id)?.toPodcast()

    override suspend fun getSubscribedRssLinkedTo(podcastIndexId: String): Podcast? =
        podcastDao.getRssPodcastLinkedTo(podcastIndexId)
            ?.takeIf { it.isSubscribed }
            ?.toPodcast()

    override suspend fun upsertSubscribedPodcast(podcast: Podcast) {
        val existing = podcastDao.getPodcast(podcast.id)
        val preferredSort = podcast.preferredSort ?: existing?.preferredSort ?: "newest"
        val typeVal = if (preferredSort == "oldest") "serial" else (podcast.type.ifBlank { "episodic" })
        podcastDao.upsert(
            PodcastEntity(
                podcastId = podcast.id,
                title = podcast.title,
                author = podcast.artist,
                imageUrl = podcast.imageUrl.ifEmpty { existing?.imageUrl.orEmpty() },
                description = podcast.description,
                genre = podcast.genre,
                type = typeVal,
                isSubscribed = true,
                subscribedAt = podcast.subscribedAt.takeIf { it > 0L }
                    ?: existing?.subscribedAt
                    ?: 0L,
                lastRefreshed = System.currentTimeMillis(),
                latestEpisode = podcast.latestEpisode ?: existing?.latestEpisode,
                podcastGuid = podcast.podcastGuid ?: existing?.podcastGuid,
                fundingUrl = podcast.fundingUrl ?: existing?.fundingUrl,
                fundingMessage = podcast.fundingMessage ?: existing?.fundingMessage,
                medium = podcast.medium ?: existing?.medium,
                hasValue = podcast.hasValue || (existing?.hasValue == true),
                updateFrequency = podcast.updateFrequency ?: existing?.updateFrequency,
                location = podcast.location ?: existing?.location,
                license = podcast.license ?: existing?.license,
                isLocked = podcast.isLocked || (existing?.isLocked == true),
                preferredSort = preferredSort,
                notificationsEnabled = podcast.notificationsEnabled,
                autoDownloadEnabled = podcast.autoDownloadEnabled,
                skipBeginningOverrideMs = podcast.skipBeginningOverrideMs
                    ?: existing?.skipBeginningOverrideMs,
                skipEndingOverrideMs = podcast.skipEndingOverrideMs
                    ?: existing?.skipEndingOverrideMs,
                // Preserve RSS / catalog identity fields from the existing row.
                sourceType = existing?.sourceType ?: podcast.sourceType,
                feedUrl = existing?.feedUrl ?: podcast.feedUrl,
                feedEtag = existing?.feedEtag,
                feedLastModified = existing?.feedLastModified,
                feedDeclaredUpdatedAt = existing?.feedDeclaredUpdatedAt,
                rssRefreshCapability = existing?.rssRefreshCapability
                    ?: podcast.rssRefreshCapability,
                lastRssSyncAt = existing?.lastRssSyncAt ?: 0L,
                rssCatalogStale = existing?.rssCatalogStale ?: podcast.rssCatalogStale,
                rssHasNewEpisodes = existing?.rssHasNewEpisodes ?: podcast.rssHasNewEpisodes,
                linkedPodcastIndexId = existing?.linkedPodcastIndexId
                    ?: podcast.linkedPodcastIndexId,
            ),
        )
    }
}
