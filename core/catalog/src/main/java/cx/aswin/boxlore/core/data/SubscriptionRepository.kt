package cx.aswin.boxlore.core.data

import cx.aswin.boxlore.core.data.database.PodcastDao
import cx.aswin.boxlore.core.data.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
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
                    artist = entity.author,
                    imageUrl = entity.imageUrl,
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
                    preferredSort = entity.preferredSort,
                    notificationsEnabled = entity.notificationsEnabled,
                    autoDownloadEnabled = entity.autoDownloadEnabled,
                    skipBeginningOverrideMs = entity.skipBeginningOverrideMs,
                    skipEndingOverrideMs = entity.skipEndingOverrideMs,
                    sourceType = entity.sourceType,
                    feedUrl = entity.feedUrl,
                    rssRefreshCapability = entity.rssRefreshCapability,
                    rssCatalogStale = entity.rssCatalogStale,
                    rssHasNewEpisodes = entity.rssHasNewEpisodes,
                    linkedPodcastIndexId = entity.linkedPodcastIndexId,
                )
            }
        }

    suspend fun toggleSubscription(podcast: Podcast) {
        val existing = podcastDao.getPodcast(podcast.id)
        val linkedRss = if (!podcast.isRss) {
            podcastDao.getRssPodcastLinkedTo(podcast.id)
        } else {
            null
        }
        val activeEntity = linkedRss?.takeIf { it.isSubscribed } ?: existing
        val isCurrentlySubscribed = activeEntity?.isSubscribed == true

        if (isCurrentlySubscribed) {
            // Unsubscribe
            val target = checkNotNull(activeEntity)
            val updated = target.copy(
                isSubscribed = false,
                subscribedAt = 0L,
                notificationsEnabled = false,
                autoDownloadEnabled = false,
            )
            podcastDao.upsert(updated)
            if (target.isRss) podcastDao.deleteRssEpisodes(target.podcastId)
            if (!podcast.isRss) {
                updateFirebaseSubscription(podcast.id, podcast.title, podcast.imageUrl, false)
            }
            RankingFeedbackRepository.getIfInitialized()?.recordAction(
                target = FeedbackTarget(
                    episodeId = podcast.latestEpisode?.id ?: "podcast:${podcast.id}",
                    podcastId = podcast.id,
                    genre = podcast.genre,
                ),
                action = RankingAction.UNSUBSCRIBE,
            )
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
                preferredSort = existing?.preferredSort, // Preserve existing sort preference
                notificationsEnabled = false, // Off by default
                autoDownloadEnabled = false,
                skipBeginningOverrideMs = existing?.skipBeginningOverrideMs
                    ?: podcast.skipBeginningOverrideMs,
                skipEndingOverrideMs = existing?.skipEndingOverrideMs
                    ?: podcast.skipEndingOverrideMs,
                sourceType = podcast.sourceType,
                feedUrl = podcast.feedUrl,
                rssRefreshCapability = podcast.rssRefreshCapability,
                rssCatalogStale = podcast.rssCatalogStale,
                rssHasNewEpisodes = podcast.rssHasNewEpisodes,
                linkedPodcastIndexId = podcast.linkedPodcastIndexId,
            )
            podcastDao.upsert(entity)
            RankingFeedbackRepository.getIfInitialized()?.recordAction(
                target = FeedbackTarget(
                    episodeId = podcast.latestEpisode?.id ?: "podcast:${podcast.id}",
                    podcastId = podcast.id,
                    genre = podcast.genre,
                ),
                action = RankingAction.SUBSCRIBE,
            )
        }
    }

    suspend fun isSubscribed(podcastId: String): Boolean {
        if (podcastDao.getPodcast(podcastId)?.isSubscribed == true) return true
        return podcastDao.getRssPodcastLinkedTo(podcastId)?.isSubscribed == true
    }

    suspend fun subscribe(podcast: Podcast) {
        if (!podcast.isRss && podcastDao.getRssPodcastLinkedTo(podcast.id)?.isSubscribed == true) {
            return
        }
        val existing = podcastDao.getPodcast(podcast.id)
        val isNewSubscription = existing?.isSubscribed != true
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
            preferredSort = preferredSortVal,
            notificationsEnabled = false, // Off by default
            autoDownloadEnabled = false,
            skipBeginningOverrideMs = existing?.skipBeginningOverrideMs
                ?: podcast.skipBeginningOverrideMs,
            skipEndingOverrideMs = existing?.skipEndingOverrideMs
                ?: podcast.skipEndingOverrideMs,
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
        )
        podcastDao.upsert(entity)
        if (isNewSubscription) {
            RankingFeedbackRepository.getIfInitialized()?.recordAction(
                target = FeedbackTarget(
                    episodeId = podcast.latestEpisode?.id ?: "podcast:${podcast.id}",
                    podcastId = podcast.id,
                    genre = podcast.genre,
                ),
                action = RankingAction.SUBSCRIBE,
            )
        }
    }

    suspend fun setNotificationsEnabled(podcast: Podcast, enabled: Boolean) {
        if (podcast.isRss) {
            podcastDao.setNotificationsEnabled(podcast.id, false)
            podcastDao.setAutoDownloadEnabled(podcast.id, false)
            return
        }
        podcastDao.setNotificationsEnabled(podcast.id, enabled)
        updateFirebaseSubscription(podcast.id, podcast.title, podcast.imageUrl, enabled)
    }

    private fun updateFirebaseSubscription(podcastId: String, title: String, imageUrl: String, isSubscribed: Boolean) {
        try {
            if (isSubscribed) {
                val dbRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("tracked_podcasts")
                    .child(podcastId)
                
                val data = mapOf(
                    "title" to title,
                    "imageUrl" to imageUrl
                )
                dbRef.setValue(data)
                
                com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .subscribeToTopic("new_ep_$podcastId")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            android.util.Log.d("FCM_Topic", "Successfully subscribed to topic: new_ep_$podcastId")
                        } else {
                            android.util.Log.e("FCM_Topic", "Failed to subscribe to topic: new_ep_$podcastId", task.exception)
                        }
                    }
            } else {
                com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .unsubscribeFromTopic("new_ep_$podcastId")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            android.util.Log.d("FCM_Topic", "Successfully unsubscribed from topic: new_ep_$podcastId")
                        } else {
                            android.util.Log.e("FCM_Topic", "Failed to unsubscribe from topic: new_ep_$podcastId", task.exception)
                        }
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionRepository", "Firebase update failed for $podcastId", e)
        }
    }

    suspend fun updateLatestEpisode(podcastId: String, episode: cx.aswin.boxlore.core.model.Episode?) {
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

    /**
     * Marks a podcast's episodes as seen, clearing the RSS "new episodes" badge
     * ([PodcastEntity.rssHasNewEpisodes]). Safe to call for non-RSS podcasts (no-op).
     */
    suspend fun clearRssNewEpisodesFlag(podcastId: String) {
        podcastDao.clearRssNewEpisodesFlag(podcastId)
    }

    suspend fun setAutoDownloadEnabled(podcastId: String, enabled: Boolean) {
        if (podcastDao.getPodcast(podcastId)?.isRss == true) {
            podcastDao.setAutoDownloadEnabled(podcastId, false)
            return
        }
        podcastDao.setAutoDownloadEnabled(podcastId, enabled)
    }

    suspend fun setPlaybackSkipOverrides(
        podcastId: String,
        skipBeginningMs: Long?,
        skipEndingMs: Long?,
    ) {
        podcastDao.setPlaybackSkipOverrides(
            podcastId,
            skipBeginningMs,
            skipEndingMs,
        )
    }

    /**
     * Re-subscribes all notification-enabled podcasts to their FCM topics.
     *
     * After an uninstall/reinstall or device migration, the Room database may be
     * restored from backup while the FCM token is new and has no topic
     * subscriptions. This method reconciles the two by iterating every podcast
     * where [PodcastEntity.notificationsEnabled] is true and calling
     * [updateFirebaseSubscription] to re-register with Firebase.
     */
    suspend fun reconcileFcmTopicSubscriptions() {
        try {
            val podcasts = podcastDao.getNotificationEnabledPodcasts()
            if (podcasts.isEmpty()) return
            android.util.Log.i(
                "FCM_Topic",
                "Reconciling ${podcasts.size} FCM topic subscriptions after restore",
            )
            for (entity in podcasts) {
                updateFirebaseSubscription(
                    podcastId = entity.podcastId,
                    title = entity.title,
                    imageUrl = entity.imageUrl,
                    isSubscribed = true,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("FCM_Topic", "FCM topic reconciliation failed", e)
        }
    }
}
