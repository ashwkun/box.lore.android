package cx.aswin.boxcast.core.data

import android.content.Context
import androidx.room.withTransaction
import cx.aswin.boxcast.core.data.database.BoxLoreDatabase
import cx.aswin.boxcast.core.data.database.PodcastEntity
import cx.aswin.boxcast.core.data.database.RssEpisodeEntity
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class RssSubscriptionResult(
    val podcast: Podcast,
    val episodeCount: Int,
    val automaticUpdateChecksSupported: Boolean,
    val potentialPodcastIndexMatch: Podcast? = null,
    val linkedPodcastIndexId: String? = null,
)

class RssPodcastRepository private constructor(
    private val database: BoxLoreDatabase,
    private val feedClient: RssFeedClient,
) {
    private val podcastDao = database.podcastDao()
    private val episodeDao = database.rssEpisodeDao()
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()
    private val _refreshingPodcastIds = MutableStateFlow<Set<String>>(emptySet())

    val refreshingPodcastIds: StateFlow<Set<String>> = _refreshingPodcastIds.asStateFlow()

    suspend fun addSubscription(rawUrl: String): RssSubscriptionResult = withContext(Dispatchers.IO) {
        val normalizedUrl = RssIdGenerator.validateAndNormalizeFeedUrl(rawUrl)
        val fetched = feedClient.fetch(normalizedUrl)
        val podcastId = RssIdGenerator.podcastId(fetched.finalUrl)
        val parsed = feedClient.parse(
            feedUrl = fetched.finalUrl,
            bytes = fetched.body,
            podcastId = podcastId,
        )
        val supportsHeadChecks = feedClient.confirmHeadValidators(
            url = fetched.finalUrl,
            etag = fetched.etag,
            lastModified = fetched.lastModified,
        )
        val existing = podcastDao.getPodcast(podcastId)
        val podcastIndexSubscriptions = podcastDao.getSubscribedPodcastIndexPodcasts()
        val exactMatch = podcastIndexSubscriptions.firstOrNull { candidate ->
            RssSourceMatcher.feedIdentityMatches(
                rssFeedUrl = fetched.finalUrl,
                rssPodcastGuid = parsed.podcastGuid,
                candidate = candidate,
            )
        }
        val potentialMatch = if (exactMatch == null) {
            podcastIndexSubscriptions.firstOrNull { candidate ->
                RssSourceMatcher.likelySameShow(
                    rssTitle = parsed.title,
                    rssAuthor = parsed.author,
                    candidate = candidate,
                )
            }
        } else {
            null
        }
        val stateSource = exactMatch ?: existing
        val now = System.currentTimeMillis()
        val latestEpisode = parsed.episodes.first().toEpisode(
            podcastTitle = parsed.title,
            podcastImageUrl = parsed.imageUrl,
            podcastGenre = parsed.genre,
            podcastArtist = parsed.author,
        )
        val entity = PodcastEntity(
            podcastId = podcastId,
            title = parsed.title,
            author = parsed.author,
            imageUrl = parsed.imageUrl.orEmpty(),
            description = parsed.description,
            isSubscribed = true,
            subscribedAt = stateSource?.subscribedAt?.takeIf { stateSource.isSubscribed } ?: now,
            genre = parsed.genre,
            type = parsed.podcastType,
            lastRefreshed = now,
            latestEpisode = latestEpisode,
            preferredSort = stateSource?.preferredSort
                ?: if (parsed.podcastType == "serial") "oldest" else "newest",
            notificationsEnabled = false,
            autoDownloadEnabled = false,
            sourceType = PodcastEntity.SOURCE_RSS,
            feedUrl = fetched.finalUrl,
            feedEtag = fetched.etag,
            feedLastModified = fetched.lastModified,
            feedDeclaredUpdatedAt = parsed.declaredUpdatedAt,
            rssRefreshCapability = if (supportsHeadChecks) {
                PodcastEntity.RSS_REFRESH_HEAD_VALIDATORS
            } else {
                PodcastEntity.RSS_REFRESH_MANUAL
            },
            lastRssSyncAt = now,
            rssCatalogStale = false,
            rssHasNewEpisodes = false,
            podcastGuid = parsed.podcastGuid,
            linkedPodcastIndexId = exactMatch?.podcastId ?: existing?.linkedPodcastIndexId,
        )
        database.withTransaction {
            podcastDao.upsert(entity)
            episodeDao.upsertAll(parsed.episodes)
            exactMatch?.let { matched ->
                migrateLinkedState(
                    podcastIndexPodcast = matched,
                    rssPodcast = entity,
                    rssEpisodes = parsed.episodes,
                )
            }
        }
        exactMatch?.let { unsubscribeFromPodcastIndexNotifications(it.podcastId) }
        RssSubscriptionResult(
            podcast = entity.toPodcast(),
            episodeCount = parsed.episodes.size,
            automaticUpdateChecksSupported = supportsHeadChecks,
            potentialPodcastIndexMatch = potentialMatch?.toPodcast(),
            linkedPodcastIndexId = exactMatch?.podcastId,
        )
    }

    suspend fun confirmPodcastIndexLink(
        rssPodcastId: String,
        podcastIndexId: String,
    ): Podcast = withContext(Dispatchers.IO) {
        val linkedPodcast = database.withTransaction {
            val rssPodcast = podcastDao.getPodcast(rssPodcastId)
                ?: error("RSS subscription not found")
            require(rssPodcast.isRss) { "Podcast is not an RSS subscription" }
            val podcastIndexPodcast = podcastDao.getPodcast(podcastIndexId)
                ?: error("Podcast Index subscription not found")
            require(!podcastIndexPodcast.isRss) {
                "Linked source must be a Podcast Index subscription"
            }
            val linkedRssPodcast = rssPodcast.copy(
                subscribedAt = podcastIndexPodcast.subscribedAt
                    .takeIf { podcastIndexPodcast.isSubscribed }
                    ?: rssPodcast.subscribedAt,
                preferredSort = podcastIndexPodcast.preferredSort ?: rssPodcast.preferredSort,
                linkedPodcastIndexId = podcastIndexId,
            )
            podcastDao.upsert(linkedRssPodcast)
            migrateLinkedState(
                podcastIndexPodcast = podcastIndexPodcast,
                rssPodcast = linkedRssPodcast,
                rssEpisodes = episodeDao.getAllNewest(rssPodcastId),
            )
            linkedRssPodcast.toPodcast()
        }
        unsubscribeFromPodcastIndexNotifications(podcastIndexId)
        linkedPodcast
    }

    suspend fun refreshCatalog(podcastId: String): Result<Int> = withContext(Dispatchers.IO) {
        val lock = refreshLocks.getOrPut(podcastId) { Mutex() }
        lock.withLock {
            markRefreshing(podcastId, true)
            try {
                runCatching {
                    val existing = podcastDao.getPodcast(podcastId)
                        ?: error("RSS subscription not found")
                    require(existing.isRss) { "Podcast is not an RSS subscription" }
                    val feedUrl = existing.feedUrl ?: error("RSS feed URL is missing")
                    val fetched = feedClient.fetch(feedUrl)
                    val parsed = feedClient.parse(
                        feedUrl = fetched.finalUrl,
                        bytes = fetched.body,
                        podcastId = podcastId,
                    )
                    val supportsHeadChecks = feedClient.confirmHeadValidators(
                        url = fetched.finalUrl,
                        etag = fetched.etag,
                        lastModified = fetched.lastModified,
                    )
                    val latestEpisode = parsed.episodes.first().toEpisode(
                        podcastTitle = parsed.title,
                        podcastImageUrl = parsed.imageUrl ?: existing.imageUrl,
                        podcastGenre = parsed.genre ?: existing.genre,
                        podcastArtist = parsed.author.ifBlank { existing.author },
                    )
                    val updated = existing.copy(
                        title = parsed.title,
                        author = parsed.author.ifBlank { existing.author },
                        imageUrl = parsed.imageUrl ?: existing.imageUrl,
                        description = parsed.description ?: existing.description,
                        genre = parsed.genre ?: existing.genre,
                        type = parsed.podcastType,
                        latestEpisode = latestEpisode,
                        lastRefreshed = System.currentTimeMillis(),
                        feedUrl = fetched.finalUrl,
                        feedEtag = fetched.etag,
                        feedLastModified = fetched.lastModified,
                        feedDeclaredUpdatedAt = parsed.declaredUpdatedAt,
                        rssRefreshCapability = if (supportsHeadChecks) {
                            PodcastEntity.RSS_REFRESH_HEAD_VALIDATORS
                        } else {
                            PodcastEntity.RSS_REFRESH_MANUAL
                        },
                        lastRssSyncAt = System.currentTimeMillis(),
                        rssCatalogStale = false,
                        rssHasNewEpisodes = false,
                    )
                    database.withTransaction {
                        podcastDao.upsert(updated)
                        episodeDao.upsertAll(parsed.episodes)
                    }
                    parsed.episodes.size
                }
            } finally {
                markRefreshing(podcastId, false)
            }
        }
    }

    /**
     * Refreshes the catalog only when there is reason to believe it changed, so opening a podcast
     * does not waste a full feed download on every visit.
     *
     * - Feeds that support HTTP validators get a cheap conditional HEAD check first; the full feed
     *   is only downloaded when the server reports a change.
     * - Feeds without validators fall back to a full refresh, but only after a quiet interval so we
     *   don't re-download on every screen open.
     * - A forced [refreshCatalog] (e.g. pull-to-refresh) always bypasses this and downloads.
     *
     * Returns the number of episodes downloaded (0 when nothing was refreshed).
     */
    suspend fun refreshCatalogIfNeeded(podcastId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = podcastDao.getPodcast(podcastId)
                ?: error("RSS subscription not found")
            require(existing.isRss) { "Podcast is not an RSS subscription" }

            // A prior HEAD check already flagged the catalog as stale → download now.
            if (existing.rssCatalogStale) {
                return@runCatching refreshCatalog(podcastId).getOrThrow()
            }

            if (existing.rssRefreshCapability == PodcastEntity.RSS_REFRESH_HEAD_VALIDATORS) {
                when (val freshness = feedClient.checkFreshness(existing)) {
                    is RssFreshnessResult.Unchanged -> {
                        podcastDao.updateRssState(
                            id = existing.podcastId,
                            etag = freshness.etag ?: existing.feedEtag,
                            lastModified = freshness.lastModified ?: existing.feedLastModified,
                            declaredUpdatedAt = existing.feedDeclaredUpdatedAt,
                            refreshCapability = existing.rssRefreshCapability,
                            syncedAt = System.currentTimeMillis(),
                            catalogStale = false,
                            hasNewEpisodes = existing.rssHasNewEpisodes,
                        )
                        return@runCatching 0
                    }
                    is RssFreshnessResult.Changed -> return@runCatching refreshCatalog(podcastId).getOrThrow()
                    RssFreshnessResult.Unsupported -> return@runCatching refreshCatalog(podcastId).getOrThrow()
                    is RssFreshnessResult.Failed -> return@runCatching 0
                }
            }

            // No cheap validator available → gate a full refresh behind a quiet interval.
            val sinceLastSync = System.currentTimeMillis() - existing.lastRssSyncAt
            if (existing.lastRssSyncAt > 0L && sinceLastSync < HEAD_CHECK_INTERVAL_MS) {
                return@runCatching 0
            }
            refreshCatalog(podcastId).getOrThrow()
        }
    }

    suspend fun checkSubscribedFeedFreshness() = coroutineScope {
        val now = System.currentTimeMillis()
        val semaphore = Semaphore(MAX_CONCURRENT_HEAD_CHECKS)
        podcastDao.getSubscribedRssPodcasts()
            .filter { podcast ->
                podcast.rssRefreshCapability == PodcastEntity.RSS_REFRESH_HEAD_VALIDATORS &&
                    now - podcast.lastRssSyncAt >= HEAD_CHECK_INTERVAL_MS
            }
            .map { podcast ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        when (val freshness = feedClient.checkFreshness(podcast)) {
                            is RssFreshnessResult.Unchanged -> {
                                podcastDao.updateRssState(
                                    id = podcast.podcastId,
                                    etag = freshness.etag ?: podcast.feedEtag,
                                    lastModified = freshness.lastModified
                                        ?: podcast.feedLastModified,
                                    declaredUpdatedAt = podcast.feedDeclaredUpdatedAt,
                                    refreshCapability = podcast.rssRefreshCapability,
                                    syncedAt = now,
                                    catalogStale = podcast.rssCatalogStale,
                                    hasNewEpisodes = podcast.rssHasNewEpisodes,
                                )
                            }
                            is RssFreshnessResult.Changed -> {
                                podcastDao.updateRssState(
                                    id = podcast.podcastId,
                                    etag = freshness.etag ?: podcast.feedEtag,
                                    lastModified = freshness.lastModified
                                        ?: podcast.feedLastModified,
                                    declaredUpdatedAt = podcast.feedDeclaredUpdatedAt,
                                    refreshCapability = podcast.rssRefreshCapability,
                                    syncedAt = now,
                                    catalogStale = true,
                                    hasNewEpisodes = true,
                                )
                            }
                            RssFreshnessResult.Unsupported -> {
                                podcastDao.updateRssState(
                                    id = podcast.podcastId,
                                    etag = podcast.feedEtag,
                                    lastModified = podcast.feedLastModified,
                                    declaredUpdatedAt = podcast.feedDeclaredUpdatedAt,
                                    refreshCapability = PodcastEntity.RSS_REFRESH_MANUAL,
                                    syncedAt = now,
                                    catalogStale = podcast.rssCatalogStale,
                                    hasNewEpisodes = podcast.rssHasNewEpisodes,
                                )
                            }
                            is RssFreshnessResult.Failed -> Unit
                        }
                    }
                }
            }
            .awaitAll()
    }

    suspend fun getPodcast(podcastId: String): PodcastEntity? = podcastDao.getPodcast(podcastId)

    suspend fun getEpisode(episodeId: String): Episode? {
        val entity = episodeDao.getEpisode(episodeId) ?: return null
        return entity.toDomainEpisode()
    }

    suspend fun getEpisodes(
        podcastId: String,
        limit: Int,
        offset: Int,
        sort: String,
    ): List<Episode> {
        val podcast = podcastDao.getPodcast(podcastId) ?: return emptyList()
        val rows = if (sort == "oldest") {
            episodeDao.getOldestPage(podcastId, limit, offset)
        } else {
            episodeDao.getNewestPage(podcastId, limit, offset)
        }
        return rows.map { it.toDomainEpisode(podcast) }
    }

    suspend fun getAllEpisodes(podcastId: String): List<Episode> {
        val podcast = podcastDao.getPodcast(podcastId) ?: return emptyList()
        return episodeDao.getAllNewest(podcastId).map { it.toDomainEpisode(podcast) }
    }

    suspend fun searchEpisodes(podcastId: String, query: String): List<Episode> {
        val podcast = podcastDao.getPodcast(podcastId) ?: return emptyList()
        return episodeDao.search(podcastId, query.trim()).map { it.toDomainEpisode(podcast) }
    }

    suspend fun episodeCount(podcastId: String): Int = episodeDao.count(podcastId)

    suspend fun deleteCatalog(podcastId: String) = episodeDao.deleteForPodcast(podcastId)

    private fun RssEpisodeEntity.toDomainEpisode(podcast: PodcastEntity? = null): Episode =
        toEpisode(
            podcastTitle = podcast?.title,
            podcastImageUrl = podcast?.imageUrl,
            podcastGenre = podcast?.genre,
            podcastArtist = podcast?.author,
        )

    private fun PodcastEntity.toPodcast(): Podcast = Podcast(
        id = podcastId,
        title = title,
        artist = author,
        imageUrl = imageUrl,
        fallbackImageUrl = latestEpisode?.imageUrl,
        type = type,
        description = description,
        genre = genre ?: "Podcast",
        latestEpisode = latestEpisode,
        subscribedAt = subscribedAt,
        podcastGuid = podcastGuid,
        fundingUrl = fundingUrl,
        fundingMessage = fundingMessage,
        medium = medium,
        hasValue = hasValue,
        updateFrequency = updateFrequency,
        location = location,
        license = license,
        isLocked = isLocked,
        preferredSort = preferredSort,
        notificationsEnabled = false,
        autoDownloadEnabled = false,
        sourceType = sourceType,
        feedUrl = feedUrl,
        rssRefreshCapability = rssRefreshCapability,
        rssCatalogStale = rssCatalogStale,
        rssHasNewEpisodes = rssHasNewEpisodes,
        linkedPodcastIndexId = linkedPodcastIndexId,
    )

    private suspend fun migrateLinkedState(
        podcastIndexPodcast: PodcastEntity,
        rssPodcast: PodcastEntity,
        rssEpisodes: List<RssEpisodeEntity>,
    ) {
        val historyDao = database.listeningHistoryDao()
        historyDao.getHistoryForPodcast(podcastIndexPodcast.podcastId).forEach { old ->
            val rssEpisode = RssSourceMatcher.findMatchingEpisode(
                episodes = rssEpisodes,
                title = old.episodeTitle,
                audioUrl = old.episodeAudioUrl,
                publishedDate = null,
            ) ?: return@forEach
            val existing = historyDao.getHistoryItem(rssEpisode.episodeId)
            val remapped = old.copy(
                episodeId = rssEpisode.episodeId,
                podcastId = rssPodcast.podcastId,
                episodeTitle = rssEpisode.title,
                episodeImageUrl = rssEpisode.imageUrl ?: old.episodeImageUrl,
                podcastImageUrl = rssPodcast.imageUrl,
                episodeAudioUrl = rssEpisode.audioUrl,
                podcastName = rssPodcast.title,
                progressMs = maxOf(old.progressMs, existing?.progressMs ?: 0L),
                durationMs = maxOf(old.durationMs, existing?.durationMs ?: 0L),
                isCompleted = old.isCompleted || existing?.isCompleted == true,
                isLiked = old.isLiked || existing?.isLiked == true,
                lastPlayedAt = maxOf(old.lastPlayedAt, existing?.lastPlayedAt ?: 0L),
                enclosureType = rssEpisode.enclosureType ?: old.enclosureType,
            )
            historyDao.upsert(remapped)
            if (old.episodeId != remapped.episodeId) historyDao.delete(old.episodeId)
        }

        val downloadDao = database.downloadedEpisodeDao()
        downloadDao.getDownloadsForPodcast(podcastIndexPodcast.podcastId).forEach { old ->
            val rssEpisode = RssSourceMatcher.findMatchingEpisode(
                episodes = rssEpisodes,
                title = old.episodeTitle,
                audioUrl = null,
                publishedDate = old.publishedDate,
            ) ?: return@forEach
            if (downloadDao.getDownload(rssEpisode.episodeId) == null) {
                downloadDao.insert(
                    old.copy(
                        episodeId = rssEpisode.episodeId,
                        podcastId = rssPodcast.podcastId,
                        episodeTitle = rssEpisode.title,
                        episodeDescription = rssEpisode.description,
                        episodeImageUrl = rssEpisode.imageUrl ?: old.episodeImageUrl,
                        podcastName = rssPodcast.title,
                        podcastImageUrl = rssPodcast.imageUrl,
                        durationMs = rssEpisode.duration.toLong() * 1_000L,
                        publishedDate = rssEpisode.publishedDate,
                    ),
                )
            }
            if (old.episodeId != rssEpisode.episodeId) downloadDao.delete(old.episodeId)
        }

        val queueDao = database.queueDao()
        queueDao.getAllQueueItemsSync()
            .filter { it.podcastId == podcastIndexPodcast.podcastId }
            .forEach { old ->
                val rssEpisode = RssSourceMatcher.findMatchingEpisode(
                    episodes = rssEpisodes,
                    title = old.title,
                    audioUrl = old.audioUrl,
                    publishedDate = old.pubDate,
                ) ?: return@forEach
                queueDao.updateQueueItem(
                    old.copy(
                        episodeId = rssEpisode.episodeId,
                        title = rssEpisode.title,
                        podcastId = rssPodcast.podcastId,
                        podcastTitle = rssPodcast.title,
                        podcastGenre = rssPodcast.genre.orEmpty(),
                        podcastArtist = rssPodcast.author,
                        podcastImageUrl = rssPodcast.imageUrl,
                        imageUrl = rssEpisode.imageUrl ?: rssPodcast.imageUrl,
                        audioUrl = rssEpisode.audioUrl,
                        duration = rssEpisode.duration,
                        pubDate = rssEpisode.publishedDate,
                        description = rssEpisode.description,
                        chaptersUrl = rssEpisode.chaptersUrl,
                        transcriptUrl = rssEpisode.transcriptUrl,
                        episodeType = rssEpisode.episodeType,
                        seasonNumber = rssEpisode.seasonNumber,
                        episodeNumber = rssEpisode.episodeNumber,
                        enclosureType = rssEpisode.enclosureType,
                    ),
                )
            }
        podcastDao.retireLinkedPodcastIndexSubscription(podcastIndexPodcast.podcastId)
    }

    private fun unsubscribeFromPodcastIndexNotifications(podcastIndexId: String) {
        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .unsubscribeFromTopic("new_ep_$podcastIndexId")
        }.onFailure { error ->
            android.util.Log.w(
                "RssPodcastRepository",
                "Failed to retire Podcast Index notification topic",
                error,
            )
        }
    }

    private fun markRefreshing(podcastId: String, refreshing: Boolean) {
        _refreshingPodcastIds.value = if (refreshing) {
            _refreshingPodcastIds.value + podcastId
        } else {
            _refreshingPodcastIds.value - podcastId
        }
    }

    companion object {
        private const val MAX_CONCURRENT_HEAD_CHECKS = 4
        private const val HEAD_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
        @Volatile
        private var INSTANCE: RssPodcastRepository? = null

        fun getInstance(context: Context): RssPodcastRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RssPodcastRepository(
                    database = BoxLoreDatabase.getDatabase(context.applicationContext),
                    feedClient = RssFeedClient(),
                ).also { INSTANCE = it }
            }
    }
}

internal object RssSourceMatcher {
    private const val ONE_DAY_SECONDS = 24L * 60L * 60L

    fun findMatchingEpisode(
        episodes: List<RssEpisodeEntity>,
        title: String,
        audioUrl: String?,
        publishedDate: Long?,
    ): RssEpisodeEntity? {
        val normalizedAudioUrl = audioUrl?.trim()?.takeIf(String::isNotBlank)
        if (normalizedAudioUrl != null) {
            episodes.firstOrNull { it.audioUrl.trim() == normalizedAudioUrl }?.let { return it }
        }
        val titleMatches = episodes.filter { normalizeText(it.title) == normalizeText(title) }
        if (titleMatches.size == 1) return titleMatches.single()
        if (publishedDate != null && publishedDate > 0L) {
            return titleMatches.minByOrNull { kotlin.math.abs(it.publishedDate - publishedDate) }
                ?.takeIf {
                    kotlin.math.abs(it.publishedDate - publishedDate) <= ONE_DAY_SECONDS
                }
        }
        return null
    }

    fun feedIdentityMatches(
        rssFeedUrl: String,
        rssPodcastGuid: String?,
        candidate: PodcastEntity,
    ): Boolean {
        val canonicalRssUrl = canonicalFeedUrl(rssFeedUrl)
        val sameUrl = canonicalRssUrl != null &&
            canonicalRssUrl == canonicalFeedUrl(candidate.feedUrl)
        val sameGuid = !rssPodcastGuid.isNullOrBlank() &&
            rssPodcastGuid.equals(candidate.podcastGuid, ignoreCase = true)
        return sameUrl || sameGuid
    }

    fun likelySameShow(
        rssTitle: String,
        rssAuthor: String,
        candidate: PodcastEntity,
    ): Boolean {
        if (normalizeText(rssTitle) != normalizeText(candidate.title)) return false
        val rssAuthorKey = normalizeText(rssAuthor)
        val candidateAuthorKey = normalizeText(candidate.author)
        return rssAuthorKey.isBlank() || candidateAuthorKey.isBlank() ||
            rssAuthorKey == candidateAuthorKey
    }

    private fun canonicalFeedUrl(url: String?): String? =
        url?.trim()?.toHttpUrlOrNull()
            ?.newBuilder()
            ?.fragment(null)
            ?.build()
            ?.toString()
            ?.removeSuffix("/")

    private fun normalizeText(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
