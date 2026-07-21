package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot
import cx.aswin.boxlore.core.catalog.content.ContentContext
import cx.aswin.boxlore.core.catalog.content.GroupedContentSections
import cx.aswin.boxlore.core.catalog.content.buildContentSignalProfile
import cx.aswin.boxlore.core.catalog.content.contentSectionsCacheKey
import cx.aswin.boxlore.core.catalog.content.contentSectionsProfileFingerprint
import cx.aswin.boxlore.core.catalog.content.toGroupedContentSections
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import com.google.gson.Gson

import cx.aswin.boxlore.core.network.model.CuratedCuriosityResponseDto
import cx.aswin.boxlore.core.network.model.ContentSectionRecentSeedDto
import cx.aswin.boxlore.core.network.model.ContentSectionSeedFallbackDto
import cx.aswin.boxlore.core.network.model.ContentSectionsV1Request
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Request
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Response
import cx.aswin.boxlore.core.catalog.home.HomeCandidatesRequestBuilder
import cx.aswin.boxlore.core.catalog.home.HomePersonalizationCoordinator
import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import cx.aswin.boxlore.core.rss.RssPodcastRepository


/**
 * Canonical [PodcastEntity] → [Podcast] mapper shared across the data layer (and by feature
 * modules such as `feature/info`) so every caller maps the same fields the same way — see
 * [RssPodcastRepository]'s own variant for the one deliberate RSS-specific override
 * (notifications/auto-download are not surfaced from that entity snapshot).
 */
fun PodcastEntity.toPodcast(): Podcast = Podcast(
    id = podcastId,
    title = title,
    artist = author,
    imageUrl = imageUrl,
    type = type,
    description = description,
    genre = genre ?: "Podcast",
    fallbackImageUrl = latestEpisode?.imageUrl,
    latestEpisode = latestEpisode,
    subscribedAt = subscribedAt,
    fundingUrl = fundingUrl,
    fundingMessage = fundingMessage,
    podcastGuid = podcastGuid,
    medium = medium,
    hasValue = hasValue,
    updateFrequency = updateFrequency,
    location = location,
    license = license,
    isLocked = isLocked,
    preferredSort = preferredSort,
    notificationsEnabled = notificationsEnabled,
    autoDownloadEnabled = autoDownloadEnabled,
    skipBeginningOverrideMs = skipBeginningOverrideMs,
    skipEndingOverrideMs = skipEndingOverrideMs,
    sourceType = sourceType,
    feedUrl = feedUrl,
    rssRefreshCapability = rssRefreshCapability,
    rssCatalogStale = rssCatalogStale,
    rssHasNewEpisodes = rssHasNewEpisodes,
    linkedPodcastIndexId = linkedPodcastIndexId,
)

/**
 * Repository for podcast data via Boxlore API (Cloudflare Worker → Podcast Index).
 */
class PodcastRepository(
    private val baseUrl: String,
    val publicKey: String,
    private val context: android.content.Context,
    internal val rssRepository: RssPodcastRepository,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    /**
     * Optional override for hermetic JVM/MockWebServer tests. Production leaves this null so
     * [NetworkModule.createBoxLoreApi] builds the cached OkHttp client from [context].
     */
    boxLoreApi: BoxLoreApi? = null,
) : cx.aswin.boxlore.core.domain.ports.PodcastCatalogPort {
    val api: BoxLoreApi = boxLoreApi ?: NetworkModule.createBoxLoreApi(baseUrl, context)
    internal val contentCatalogPreferences = context.applicationContext.getSharedPreferences(
        "content_catalog_cache",
        android.content.Context.MODE_PRIVATE,
    )
    internal val contentSectionsPreferences = context.applicationContext.getSharedPreferences(
        "content_sections_cache",
        android.content.Context.MODE_PRIVATE,
    )

    suspend fun getTrendingPodcasts(country: String = "us", limit: Int = 50, category: String? = null, offset: Int = 0): List<Podcast> = withContext(Dispatchers.IO) {
        // Fallback or non-streaming implementation
        try {
            val response = api.getTrending(publicKey, country, limit, category, offset).execute()
            if (response.isSuccessful && response.body() != null) {
                mapFeedsToPodcasts(response.body()!!.feeds)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCuratedPodcasts(vibeId: String, country: String? = null): List<Podcast> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCuratedVibe(publicKey, vibeId, country).execute()
            if (response.isSuccessful && response.body() != null) {
                mapFeedsToPodcasts(response.body()!!.feeds)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Curated Vibe Error: $vibeId", e)
            emptyList()
        }
    }

    fun getTrendingPodcastsStream(country: String = "us", limit: Int = 50, category: String? = null, offset: Int = 0): kotlinx.coroutines.flow.Flow<List<Podcast>> =
        trendingPodcastsStream(country, limit, category, offset)

    suspend fun searchPodcastsWithCorrection(query: String): SearchResult = withContext(Dispatchers.IO) {
        try {
            val response = api.search(publicKey, query).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val podcasts = body.feeds.map { feed ->
                    val podcastId = if (feed.id != 0L) {
                        feed.id.toString()
                    } else if (feed.itunesId != null && feed.itunesId != 0L) {
                        "itunes:${feed.itunesId}"
                    } else if (!feed.url.isNullOrEmpty()) {
                        "url:${java.net.URLEncoder.encode(feed.url, "UTF-8")}"
                    } else {
                        "0"
                    }
                    Podcast(
                        id = podcastId,
                        title = feed.title,
                        artist = feed.author ?: "Unknown",
                        imageUrl = (feed.artwork ?: feed.image).toHttps(),
                        description = feed.description,
                        genre = resolvePrimaryGenre(feed.categories),
                        medium = feed.medium,
                        feedUrl = feed.url,
                    )
                }
                SearchResult(podcasts, null)
            } else {
                SearchResult(emptyList())
            }
        } catch (e: Exception) {
            SearchResult(emptyList())
        }
    }

    suspend fun searchPodcasts(query: String): List<Podcast> = withContext(Dispatchers.IO) {
        searchPodcastsWithCorrection(query).podcasts
    }

    suspend fun searchEpisodesSemantic(query: String, country: String): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchSemantic(publicKey, query, country).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.items.mapNotNull { mapToEpisode(it) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchEpisodes(feedId: String, query: String): List<Episode> = withContext(Dispatchers.IO) {
        if (feedId.startsWith("rss:")) {
            return@withContext searchRssEpisodes(feedId, query)
        }
        searchNetworkEpisodes(feedId, query)
    }

    private val playerPrefs = PrefsFileMigrator.open(
        context,
        newName = PrefsFileMigrator.Files.PLAYER,
        oldName = PrefsFileMigrator.LegacyFiles.PLAYER,
    )

    internal fun getOrCreateDeviceUuid(): String {
        val key = "device_uuid"
        var uuid = playerPrefs.getString(key, null)
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString()
            playerPrefs.edit().putString(key, uuid).apply()
        }
        return uuid
    }

    data class EpisodePage(
        val episodes: List<Episode>,
        val hasMore: Boolean
    )

    suspend fun getEpisodesPaginated(
        feedId: String,
        limit: Int = 20,
        offset: Int = 0,
        sort: String = "newest"
    ): EpisodePage = withContext(Dispatchers.IO) {
        if (feedId.startsWith("rss:")) {
            return@withContext getRssEpisodesPaginated(feedId, limit, offset, sort)
        }
        getNetworkEpisodesPaginated(feedId, limit, offset, sort)
    }

    private suspend fun getRssEpisodesPaginated(
        feedId: String,
        limit: Int,
        offset: Int,
        sort: String,
    ): EpisodePage = try {
        val episodes = rssRepository.getEpisodes(feedId, limit, offset, sort)
        val total = rssRepository.episodeCount(feedId)
        EpisodePage(
            episodes = episodes,
            hasMore = offset + episodes.size < total,
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "RSS getEpisodesPaginated failed for $feedId", e)
        EpisodePage(emptyList(), false)
    }

    private suspend fun getNetworkEpisodesPaginated(
        feedId: String,
        limit: Int,
        offset: Int,
        sort: String,
    ): EpisodePage {
        val resolvedId = resolvePodcastIndexFeedId(feedId)
        val cacheKey = "$resolvedId|$limit|$offset|$sort"
        val cached = episodesCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < 300_000L) { // 5-minute cache
            android.util.Log.d("PodcastRepository", "Cache HIT for getEpisodesPaginated: $cacheKey")
            return cached.first
        }
        android.util.Log.d("PodcastRepository", "Cache MISS for getEpisodesPaginated: $cacheKey. Fetching from network.")
        return try {
            val response = api.getEpisodesPaginated(publicKey, resolvedId, limit, offset, sort).execute()
            if (response.isSuccessful && response.body() != null) {
                val page = EpisodePage(
                    episodes = response.body()!!.items.mapNotNull { mapToEpisode(it) },
                    hasMore = response.body()!!.hasMore
                )
                episodesCache[cacheKey] = Pair(page, now)
                page
            } else {
                EpisodePage(emptyList(), false)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            EpisodePage(emptyList(), false)
        }
    }

    override suspend fun getPodcastDetails(feedId: String): Podcast? = withContext(Dispatchers.IO) {
        if (feedId.startsWith("rss:")) {
            return@withContext getRssPodcastDetails(feedId)
        }
        getNetworkPodcastDetails(feedId)
    }

    private suspend fun getRssPodcastDetails(feedId: String): Podcast? = try {
        rssRepository.getPodcast(feedId)?.toPodcast()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("PodcastRepository", "RSS getPodcastDetails failed for $feedId", e)
        null
    }

    /** Picks the right lookup parameter (feed URL/guid/iTunes id/id) and executes the request. */
    private fun executeGetPodcastRequest(feedId: String): retrofit2.Response<cx.aswin.boxlore.core.network.model.PodcastResponse> {
        return when {
            feedId.startsWith(FEED_PREFIX_URL) -> {
                val decodedUrl = java.net.URLDecoder.decode(feedId.substringAfter(FEED_PREFIX_URL), "UTF-8")
                api.getPodcast(publicKey = publicKey, feedUrl = decodedUrl).execute()
            }
            feedId.startsWith(FEED_PREFIX_GUID) -> {
                api.getPodcast(publicKey = publicKey, feedGuid = feedId.substringAfter(FEED_PREFIX_GUID)).execute()
            }
            feedId.startsWith(FEED_PREFIX_ITUNES) -> {
                api.getPodcast(publicKey = publicKey, itunesId = feedId.substringAfter(FEED_PREFIX_ITUNES)).execute()
            }
            else -> api.getPodcast(publicKey = publicKey, feedId = feedId).execute()
        }
    }

    private fun mapPodcastResponseFeed(feed: cx.aswin.boxlore.core.network.model.PodcastFeed): Podcast = Podcast(
        id = feed.id.toString(),
        title = feed.title,
        artist = feed.author ?: "Unknown",
        imageUrl = (feed.artwork ?: feed.image).toHttps(),
        type = feed.type ?: "episodic",
        description = feed.description,
        genre = resolvePrimaryGenre(feed.categories),
        // Podcast 2.0
        fundingUrl = feed.funding?.url,
        fundingMessage = feed.funding?.message,
        podcastGuid = feed.podcastGuid,
        medium = feed.medium,
        ownerName = feed.ownerName,
        hasValue = feed.value != null,
        updateFrequency = feed.updateFrequency,
        location = feed.location,
        license = feed.license,
        isLocked = feed.locked == 1,
        feedUrl = feed.url,
    )

    private suspend fun getNetworkPodcastDetails(feedId: String): Podcast? = try {
        val response = executeGetPodcastRequest(feedId)
        val feed = if (response.isSuccessful) response.body()?.feed else null
        feed?.let { mapPodcastResponseFeed(it) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun syncSubscriptions(feedIds: List<String>): Map<String, Episode> = withContext(Dispatchers.IO) {
        try {
            val podcastIndexIds = feedIds.filterNot { it.startsWith("rss:") }
            if (podcastIndexIds.isEmpty()) return@withContext emptyMap()

            val request = cx.aswin.boxlore.core.network.model.SyncRequest(podcastIndexIds)
            val response = api.syncSubscriptions(publicKey, request).execute()

            if (response.isSuccessful && response.body() != null) {
                response.body()!!.items.mapNotNull { item ->
                    val ep = item.latestEpisode?.let { mapToEpisode(it) }?.copy(
                        podcastId = item.id
                    )
                    if (ep != null) item.id to ep else null
                }.toMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * The recommendations/bootstrap endpoints only understand Podcast Index-sourced history
     * and subscriptions, so both callers need to strip RSS entries the same way before
     * building a request — shared here to avoid two copies of the same filter drifting apart.
     */
    private fun filterToPodcastIndexScope(
        history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
        subscribedPodcastIds: List<String>,
    ): PodcastIndexScopedInputs {
        val podcastIndexHistory = history.filter { item ->
            item.podcastId?.startsWith("rss:") != true &&
                item.episodeId?.toLongOrNull()?.let { it > 0L } != false
        }
        val podcastIndexSubscriptionIds = subscribedPodcastIds.filterNot { it.startsWith("rss:") }
        return PodcastIndexScopedInputs(podcastIndexHistory, podcastIndexSubscriptionIds)
    }

    suspend fun getContentCatalog(
        forceRefresh: Boolean = false,
    ): cx.aswin.boxlore.core.catalog.content.ContentCatalogSnapshot? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = readCachedContentCatalog(now)
        if (!forceRefresh && cached?.isSupported(now) == true) return@withContext cached
        try {
            val etag = contentCatalogPreferences.getString(CONTENT_CATALOG_ETAG, null)
            val response = api.getContentCatalog(publicKey, etag).execute()
            if (response.code() == 304) {
                contentCatalogPreferences.edit()
                    .putLong(CONTENT_CATALOG_FETCHED_AT, now)
                    .apply()
                return@withContext readCachedContentCatalog(now)
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) return@withContext cached
            val snapshot = body.toContentCatalogSnapshot(now)
            if (!snapshot.isSupported(now)) return@withContext cached
            contentCatalogPreferences.edit()
                .putString(CONTENT_CATALOG_JSON, Gson().toJson(body))
                .putString(CONTENT_CATALOG_ETAG, response.headers()["ETag"])
                .putLong(CONTENT_CATALOG_FETCHED_AT, now)
                .apply()
            snapshot
        } catch (error: Exception) {
            android.util.Log.w("PodcastRepository", "Content catalog refresh failed", error)
            cached
        }
    }


    suspend fun getPersonalizedContentSections(
        contentContext: ContentContext,
        catalog: ContentCatalogSnapshot,
        inputs: PersonalizedContentSectionInputs,
        preferCache: Boolean = false,
    ): GroupedContentSections? = withContext(ioDispatcher) {
        val expectedCatalogVersion = catalog.catalogVersion.toIntOrNull() ?: return@withContext null
        val country = contentContext.region.lowercase().takeIf { it.length in 2..3 } ?: "us"
        val surface = contentContext.surface.toContentSectionsSurface() ?: return@withContext null
        val (podcastIndexHistory, podcastIndexSubscriptionIds) =
            filterToPodcastIndexScope(inputs.history, inputs.subscribedPodcastIds)
        val subscribedIds = podcastIndexSubscriptionIds.toBoundedPositiveIds()
        val subscribedPodcastIdStrings = subscribedIds.map(Long::toString).toSet()
        val seenPodcastIdStrings = (
            subscribedPodcastIdStrings +
                podcastIndexHistory.mapNotNull { it.podcastId?.toLongOrNull()?.toString() }
            ).toSet()
        val historyByEpisodeId = podcastIndexHistory
            .mapNotNull { item -> item.episodeId?.toLongOrNull()?.let { it to item } }
            .toMap()
        val recentSeeds = buildRecommendationSeeds(
            history = podcastIndexHistory,
            subscribedPodcastIds = podcastIndexSubscriptionIds,
            maximumSeeds = MAX_CONTENT_SECTION_SEEDS,
        ).map { seed ->
            val historyItem = historyByEpisodeId[seed.id]
            ContentSectionRecentSeedDto(
                kind = seed.kind,
                id = seed.id,
                weight = seed.weight,
                fallback = seed.fallback?.let { fallback ->
                    ContentSectionSeedFallbackDto(
                        episodeTitle = fallback.episodeTitle,
                        podcastTitle = fallback.podcastTitle,
                        podcastId = historyItem?.podcastId?.toLongOrNull()?.takeIf { it > 0L },
                        genre = fallback.genre,
                        description = fallback.description,
                    )
                },
            )
        }
        val boundedInterests = (inputs.interests + inputs.searchTopics + inputs.subscribedGenres)
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.take(MAX_CONTENT_SECTION_INTEREST_LENGTH) }
            .distinctBy(String::lowercase)
            .take(MAX_CONTENT_SECTION_INTERESTS)
            .toList()
        val signalProfile = buildContentSignalProfile(
            explicitInterests = inputs.interests + inputs.searchTopics,
            subscribedGenres = inputs.subscribedGenres,
            recentHistory = podcastIndexHistory,
            subscribedPodcastIds = subscribedPodcastIdStrings,
            learnedGenreAffinities = inputs.learnedGenreAffinities,
        )
        val recentSectionIds = inputs.recentSectionIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filter { it.length <= MAX_CONTENT_SECTION_ID_LENGTH }
            .distinct()
            .take(MAX_RECENT_CONTENT_SECTION_IDS)
            .toList()
        val localClock = java.time.ZonedDateTime.now()
        val localDate = localClock.toLocalDate().toString()
        val timezoneOffsetMinutes = (localClock.offset.totalSeconds / 60)
            .coerceIn(MIN_TIMEZONE_OFFSET_MINUTES, MAX_TIMEZONE_OFFSET_MINUTES)
        val request = ContentSectionsV1Request(
            contractVersion = 1,
            surface = surface,
            localMinuteOfDay = contentContext.localMinuteOfDay,
            // The backend owns six overlapping dayparts; deliberately omit the broad Android hint.
            daypart = null,
            country = country,
            languages = inputs.languages.toBoundedLanguageCodes(),
            recentSeeds = recentSeeds,
            interests = boundedInterests,
            subscribedPodcastIds = subscribedIds,
            excludedPodcastIds = inputs.excludedPodcastIds.toBoundedPositiveIds(),
            excludedEpisodeIds = (
                inputs.excludedEpisodeIds.mapNotNull(String::toLongOrNull) +
                    podcastIndexHistory.mapNotNull { it.episodeId?.toLongOrNull() }
                ).asSequence()
                .filter { it > 0L }
                .distinct()
                .take(MAX_CONTENT_SECTION_EXCLUSIONS)
                .toList(),
            candidateBudget = CONTENT_SECTION_CANDIDATE_BUDGET,
            tasteSignals = signalProfile.tasteSignals,
            recentSectionIds = recentSectionIds,
            durationPreference = signalProfile.durationPreference,
            historyMaturity = signalProfile.historyMaturity,
            noveltyPreference = signalProfile.noveltyPreference,
            localDate = localDate,
            timezoneOffsetMinutes = timezoneOffsetMinutes,
        )
        val profileFingerprint = contentSectionsProfileFingerprint(request)
        val cacheKey = contentSectionsCacheKey(
            catalogVersion = expectedCatalogVersion,
            country = country,
            surface = surface,
            localMinuteOfDay = contentContext.localMinuteOfDay,
            localDate = localDate,
            profileFingerprint = profileFingerprint,
        )
        val cached = readCachedContentSections(
            cacheKey = cacheKey,
            catalog = catalog,
            seenPodcastIds = seenPodcastIdStrings,
            subscribedPodcastIds = subscribedPodcastIdStrings,
        ) ?: readStaleCachedContentSections(
            slot = ContentSectionsSlotKey(
                catalogVersion = expectedCatalogVersion,
                country = country,
                surface = surface,
                localMinuteOfDay = contentContext.localMinuteOfDay,
                localDate = localDate,
            ),
            catalog = catalog,
            seenPodcastIds = seenPodcastIdStrings,
            subscribedPodcastIds = subscribedPodcastIdStrings,
        )
        if (preferCache || !contentContext.isOnline) return@withContext cached
        try {
            val body = api.getContentSectionsV1(
                publicKey = publicKey,
                deviceUuid = getOrCreateDeviceUuid(),
                request = request,
            )
            val mapped = body.toGroupedContentSections(
                catalog = catalog,
                seenPodcastIds = seenPodcastIdStrings,
                subscribedPodcastIds = subscribedPodcastIdStrings,
            )
            if (mapped != null) {
                val payload = Gson().toJson(body)
                val responseKey = contentSectionsCacheKey(
                    catalogVersion = requireNotNull(body.catalogVersion),
                    country = country,
                    surface = surface,
                    resolvedDaypart = requireNotNull(body.resolvedDaypart),
                    localDate = localDate,
                    profileFingerprint = profileFingerprint,
                )
                persistContentSectionsCache(
                    activeKey = responseKey,
                    aliasKey = cacheKey.takeIf { it != responseKey },
                    payload = payload,
                )
            }
            mapped ?: cached
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            val validationDetails = (error as? retrofit2.HttpException)
                ?.response()
                ?.errorBody()
                ?.string()
            android.util.Log.w(
                "PodcastRepository",
                "Grouped content sections refresh failed" +
                    validationDetails?.let { ": $it" }.orEmpty(),
                error,
            )
            cached
        }
    }

    /**
     * Versioned Home candidate pools. Cached ≥4h keyed by module scope + seeds + revision.
     * Returns null body when the endpoint is unavailable so callers can fall back to legacy
     * `/recommendations` and `/recommendations/because-you-like`.
     */
    suspend fun getHomeCandidatesV1(
        request: HomeCandidatesV1Request,
    ): Pair<HomeCandidatesV1Response?, Boolean> = withContext(Dispatchers.IO) {
        val cacheKey = HomePersonalizationCoordinator.cacheKey(request)
        val now = System.currentTimeMillis()
        val cached = homeCandidatesCache[cacheKey]
        if (cached != null && now - cached.second < HomeCandidatesRequestBuilder.CACHE_TTL_MILLIS) {
            return@withContext cached.first to true
        }
        try {
            val response =
                api.getHomeCandidatesV1(
                    publicKey,
                    getOrCreateDeviceUuid(),
                    request,
                )
            if (HomeCandidatesRequestBuilder.isValid(response)) {
                homeCandidatesCache[cacheKey] = response to now
                response to false
            } else {
                null to false
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("PodcastRepository", "home/candidates/v1 unavailable", error)
            null to false
        }
    }

    suspend fun getPersonalizedRecommendations(
        history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
        interests: List<String> = emptyList(),
        country: String? = null,
        subscribedPodcastIds: List<String> = emptyList(),
        subscribedGenres: List<String> = emptyList()
    ): List<Episode> = withContext(Dispatchers.IO) {
        val (podcastIndexHistory, podcastIndexSubscriptionIds) =
            filterToPodcastIndexScope(history, subscribedPodcastIds)
        if (podcastIndexHistory.isEmpty() && interests.isEmpty() && podcastIndexSubscriptionIds.isEmpty()) {
            // Nothing Podcast Index-scoped to base recommendations on — skip the network call.
            return@withContext emptyList()
        }
        val cacheKey = buildString {
            append(country ?: "")
            append("|")
            append(interests.sorted().joinToString(","))
            append("|")
            append(podcastIndexSubscriptionIds.sorted().joinToString(","))
            append("|")
            append(subscribedGenres.sorted().joinToString(","))
            append("|")
            append(podcastIndexHistory.joinToString(",") { "${it.episodeId}:${it.progressMs}" })
        }

        val now = System.currentTimeMillis()
        val cached = recommendationsCache[cacheKey]
        if (cached != null && now - cached.second < 900_000L) { // 15-minute cache
            android.util.Log.d("PodcastRepository", "Cache HIT for getPersonalizedRecommendations: key=$cacheKey")
            return@withContext cached.first
        }
        android.util.Log.d("PodcastRepository", "Recommendation cache miss; fetching candidates")

        try {
            val v2Results = try {
                fetchRecommendationV2(
                    history = podcastIndexHistory,
                    interests = interests,
                    country = country,
                    subscribedPodcastIds = podcastIndexSubscriptionIds,
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("BoxCastRepo", "Recommendation v2 failed; using legacy", error)
                null
            }
            val results = v2Results ?: fetchLegacyRecommendations(
                history = podcastIndexHistory,
                interests = interests,
                country = country,
                subscribedPodcastIds = podcastIndexSubscriptionIds,
                subscribedGenres = subscribedGenres,
            )
            recommendationsCache[cacheKey] = Pair(results, now)
            results
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Personalized Recommendations Error", e)
            emptyList()
        }
    }

    suspend fun getBecauseYouLikeRecommendations(
        podcastTitle: String,
        podcastDescription: String,
        excludePodcastId: String? = null,
        country: String? = null
    ): BecauseYouLikeData = withContext(Dispatchers.IO) {
        val cacheKey = "byl|$podcastTitle|$excludePodcastId|$country"
        val now = System.currentTimeMillis()
        val cached = becauseYouLikeCache[cacheKey]
        if (cached != null && now - cached.second < 900_000L) { // 15-minute client-side memory cache
            android.util.Log.d("PodcastRepository", "Cache HIT for getBecauseYouLikeRecommendations: key=$cacheKey")
            return@withContext cached.first
        }

        try {
            val request = cx.aswin.boxlore.core.network.model.BecauseYouLikeRequest(
                podcastTitle = podcastTitle,
                podcastDescription = podcastDescription,
                excludePodcastId = excludePodcastId,
                country = country
            )
            val response = api.getBecauseYouLikeRecommendations(publicKey, request).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val mappedPodcasts = body.podcasts.map { mapToPodcast(it) }
                val mappedEpisodes = body.episodes.mapNotNull { mapToEpisode(it) }
                val data = BecauseYouLikeData(podcasts = mappedPodcasts, episodes = mappedEpisodes)
                becauseYouLikeCache[cacheKey] = Pair(data, now)
                data
            } else {
                BecauseYouLikeData()
            }
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Because You Like Recommendations Error", e)
            BecauseYouLikeData()
        }
    }

    suspend fun getSimilarEpisodes(
        episodeId: String,
        podcastId: String,
        title: String,
        description: String,
        podcastTitle: String,
        categories: String = "",
        author: String = "",
        limit: Int = 10,
        country: String? = null
    ): List<Episode> = withContext(Dispatchers.IO) {
        try {
            val request = cx.aswin.boxlore.core.network.model.SimilarEpisodesRequest(
                id = episodeId.takeUnless { it.toLongOrNull()?.let { value -> value < 0L } == true }
                    ?: "0",
                podcastId = podcastId.takeUnless { it.startsWith("rss:") } ?: "0",
                title = title,
                description = description,
                podcastTitle = podcastTitle,
                categories = categories,
                author = author,
                limit = limit,
                country = country
            )
            val response = api.getSimilarEpisodes(publicKey, request).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.items.mapNotNull { mapToEpisode(it) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Error getting similar episodes", e)
            emptyList()
        }
    }

    suspend fun submitFeedback(
        category: String,
        message: String,
        appVersion: String,
        email: String? = null
    ): Boolean = withContext(ioDispatcher) {
        try {
            val request = cx.aswin.boxlore.core.network.model.FeedbackRequest(category, message, appVersion, email)
            val response = api.submitFeedback(publicKey, request).execute()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Failed to submit feedback", e)
            false
        }
    }

    suspend fun getPodcastType(feedId: String): String = withContext(Dispatchers.IO) {
        try {
            val response = getPodcastMeta(feedId)
            response?.type ?: "episodic"
        } catch (e: Exception) {
            "episodic"
        }
    }

    suspend fun getPodcastMeta(feedId: String): cx.aswin.boxlore.core.network.model.PodcastMetaResponse? = withContext(Dispatchers.IO) {
        try {
            val response = when {
                feedId.startsWith(FEED_PREFIX_URL) -> {
                    val decodedUrl = java.net.URLDecoder.decode(
                        feedId.substringAfter(FEED_PREFIX_URL),
                        "UTF-8",
                    )
                    api.getPodcastMeta(publicKey = publicKey, feedUrl = decodedUrl).execute()
                }
                feedId.startsWith(FEED_PREFIX_GUID) -> {
                    api.getPodcastMeta(
                        publicKey = publicKey,
                        feedGuid = feedId.substringAfter(FEED_PREFIX_GUID),
                    ).execute()
                }
                feedId.startsWith(FEED_PREFIX_ITUNES) -> {
                    api.getPodcastMeta(
                        publicKey = publicKey,
                        itunesId = feedId.substringAfter(FEED_PREFIX_ITUNES),
                    ).execute()
                }
                else -> api.getPodcastMeta(publicKey = publicKey, feedId = feedId).execute()
            }
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getBriefingMetadata(region: String): Briefing? = withContext(Dispatchers.IO) {
        val mappedRegion = mapRegionForBriefing(region)
        try {
            val response = api.getBriefingMetadata(publicKey, mappedRegion).execute()
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Failed to fetch briefing for $region (mapped: $mappedRegion)", e)
            null
        }
    }

    suspend fun getHomeBootstrapDataFast(country: String): HomeBootstrapData = withContext(Dispatchers.IO) {
        try {
            val response = api.getHomeBootstrapGet(publicKey, country).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val trendingList = body.trending.map { mapToPodcast(it) }
                HomeBootstrapData(
                    briefing = body.briefing,
                    briefingChapters = body.briefingChapters,
                    trending = trendingList,
                    curatedVibes = emptyMap(),
                    recommendations = emptyList(),
                    isRecommendationsFallback = body.isRecommendationsFallback ?: true
                )
            } else {
                HomeBootstrapData(null, emptyList(), emptyList(), emptyMap(), emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "getHomeBootstrapDataFast failed", e)
            HomeBootstrapData(null, emptyList(), emptyList(), emptyMap(), emptyList())
        }
    }

    suspend fun getHomeBootstrapData(
        country: String,
        vibeIds: List<String>,
        history: List<cx.aswin.boxlore.core.network.model.HistoryItem> = emptyList(),
        interests: List<String> = emptyList(),
        subscribedPodcastIds: List<String> = emptyList(),
        subscribedGenres: List<String> = emptyList()
    ): HomeBootstrapData = withContext(Dispatchers.IO) {
        try {
            val (podcastIndexHistory, podcastIndexSubscriptionIds) =
                filterToPodcastIndexScope(history, subscribedPodcastIds)
            val recsReq = if (
                podcastIndexHistory.isNotEmpty() ||
                interests.isNotEmpty() ||
                podcastIndexSubscriptionIds.isNotEmpty()
            ) {
                cx.aswin.boxlore.core.network.model.RecommendationsRequest(
                    history = podcastIndexHistory,
                    interests = interests,
                    country = country,
                    subscribedPodcastIds = podcastIndexSubscriptionIds,
                    subscribedGenres = subscribedGenres
                )
            } else {
                null
            }

            val request = cx.aswin.boxlore.core.network.model.BootstrapRequest(
                country = country,
                vibeIds = vibeIds,
                deviceUuid = getOrCreateDeviceUuid(),
                recommendationsRequest = recsReq
            )

            val response = api.getHomeBootstrap(publicKey, getOrCreateDeviceUuid(), request).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                val trendingList = body.trending.map { mapToPodcast(it) }
                val recommendationsList = body.recommendations.mapNotNull { mapToEpisode(it) }

                val curatedVibesMap = body.curatedVibes.mapValues { entry ->
                    entry.value.map { mapToPodcast(it) }
                }

                var briefing = body.briefing
                var briefingChapters = body.briefingChapters
                if (briefing == null) {
                    val mappedRegion = mapRegionForBriefing(country)
                    android.util.Log.d("PodcastRepository", "HomeBootstrapData briefing was null for country $country, attempting fallback fetch with mapped region $mappedRegion")
                    val fallbackBriefing = getBriefingMetadata(mappedRegion)
                    if (fallbackBriefing != null) {
                        briefing = fallbackBriefing
                        val audioUri = android.net.Uri.parse(fallbackBriefing.audioUrl)
                        val version = audioUri.getQueryParameter("v")
                        val versionParam = if (version != null) "&v=$version" else ""
                        val chaptersUrl = fallbackBriefing.chaptersUrl
                            ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/${fallbackBriefing.region}?d=${fallbackBriefing.date}$versionParam"
                        try {
                            briefingChapters = cx.aswin.boxlore.core.catalog.ChapterRepository.getChapters(chaptersUrl)
                        } catch (e: Exception) {
                            android.util.Log.e("PodcastRepository", "Failed to fetch chapters for fallback briefing", e)
                        }
                    }
                }

                HomeBootstrapData(
                    briefing = briefing,
                    briefingChapters = briefingChapters,
                    trending = trendingList,
                    curatedVibes = curatedVibesMap,
                    recommendations = recommendationsList,
                    isRecommendationsFallback = body.isRecommendationsFallback ?: true
                )
            } else {
                HomeBootstrapData(null, emptyList(), emptyList(), emptyMap(), emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "getHomeBootstrapData failed", e)
            HomeBootstrapData(null, emptyList(), emptyList(), emptyMap(), emptyList())
        }
    }

    suspend fun getCuratedVibes(vibeIds: List<String>, country: String): Map<String, List<Podcast>> = withContext(Dispatchers.IO) {
        val result = java.util.concurrent.ConcurrentHashMap<String, List<Podcast>>()
        vibeIds.map { vibeId ->
            async {
                try {
                    val resp = api.getCuratedVibe(publicKey, vibeId, country).execute()
                    if (resp.isSuccessful && resp.body() != null) {
                        val pods = resp.body()!!.feeds.map { mapToPodcast(it) }
                        result[vibeId] = pods
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PodcastRepository", "Failed to fetch vibe $vibeId", e)
                }
            }
        }.awaitAll()
        result
    }

    suspend fun getCuratedCuriosity(page: Int = 1, bypassCache: Boolean = false): CuratedCuriosityResponseDto? = withContext(Dispatchers.IO) {
        try {
            val cbVal = if (bypassCache) System.currentTimeMillis().toString() else null
            val resp = api.getCuratedCuriosity(publicKey, page, cbVal).execute()
            if (resp.isSuccessful) {
                resp.body()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Failed to fetch curated curiosity: ${e.message}", e)
            null
        }
    }

    override suspend fun getEpisodes(feedId: String): List<Episode> = getEpisodesImpl(feedId)

    override suspend fun getEpisode(episodeId: String): Episode? = getEpisodeImpl(episodeId)

    companion object {
        internal const val FEED_PREFIX_URL = "url:"
        internal const val FEED_PREFIX_GUID = "guid:"
        internal const val FEED_PREFIX_ITUNES = "itunes:"
        // Catalog caches are endpoint-versioned so an otherwise-valid v1/v2 response cannot
        // suppress the v3 fetch and then invalidate grouped v3 section responses.

        private val episodesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<EpisodePage, Long>>()
        private val recommendationsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<Episode>, Long>>()
        private val becauseYouLikeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<BecauseYouLikeData, Long>>()
        private val homeCandidatesCache =
            java.util.concurrent.ConcurrentHashMap<String, Pair<HomeCandidatesV1Response, Long>>()
    }
}
