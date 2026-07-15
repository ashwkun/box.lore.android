package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.data.database.PodcastEntity
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Person
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.Transcript
import cx.aswin.boxcast.core.model.Briefing
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.core.network.BoxLoreApi
import cx.aswin.boxcast.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import okhttp3.ResponseBody
import java.io.InputStreamReader

import cx.aswin.boxcast.core.network.model.TrendingFeed
import cx.aswin.boxcast.core.network.model.CuratedCuriosityResponseDto

/**
 * Upgrade HTTP URLs to HTTPS to fix Android cleartext traffic restrictions.
 * Most CDNs (including BBC's ichef) support HTTPS, so this is safe.
 */
private fun String?.toHttps(): String {
    if (this.isNullOrEmpty()) return ""
    return if (this.startsWith("http://")) {
        this.replaceFirst("http://", "https://")
    } else {
        this
    }
}

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
    sourceType = sourceType,
    feedUrl = feedUrl,
    rssRefreshCapability = rssRefreshCapability,
    rssCatalogStale = rssCatalogStale,
    rssHasNewEpisodes = rssHasNewEpisodes,
    linkedPodcastIndexId = linkedPodcastIndexId,
)

fun mapRegionForBriefing(region: String): String {
    return when (region.lowercase().trim()) {
        "us" -> "us"
        "in", "ind" -> "in"
        "uk", "gb" -> "uk"
        else -> "global"
    }
}

data class SearchResult(
    val podcasts: List<cx.aswin.boxcast.core.model.Podcast>,
    val correctedQuery: String? = null
)

private data class PodcastIndexScopedInputs(
    val history: List<cx.aswin.boxcast.core.network.model.HistoryItem>,
    val subscriptionIds: List<String>,
)

/**
 * Repository for podcast data via BoxCast API (Cloudflare Worker → Podcast Index)
 */
class PodcastRepository(
    private val baseUrl: String,
    val publicKey: String,
    context: android.content.Context,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) {
    val api: BoxLoreApi = NetworkModule.createBoxLoreApi(baseUrl, context)
    private val rssRepository = RssPodcastRepository.getInstance(context)

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

    fun getTrendingPodcastsStream(country: String = "us", limit: Int = 50, category: String? = null, offset: Int = 0): kotlinx.coroutines.flow.Flow<List<Podcast>> = kotlinx.coroutines.flow.flow {
        val podcasts = mutableListOf<Podcast>()
        try {
            android.util.Log.d("BoxCastRepo", "Stream: Requesting trending country=$country, limit=$limit, category=$category, offset=$offset")
            val call = api.getTrendingStream(publicKey, country, limit, category, offset)
            val response = call.execute()
            
            android.util.Log.d("BoxCastRepo", "Stream: Response code=${response.code()}, isSuccessful=${response.isSuccessful}")
            
            if (!response.isSuccessful || response.body() == null) {
                android.util.Log.e("BoxCastRepo", "Stream: Failed! code=${response.code()}, body isNull=${response.body() == null}")
                return@flow
            }
            
            val responseBody = response.body()!!
            val stream = responseBody.byteStream()
            val reader = com.google.gson.stream.JsonReader(java.io.InputStreamReader(stream, "UTF-8"))
            
            reader.isLenient = true
            
            reader.beginObject() 
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == "feeds") {
                    reader.beginArray() // [
                    while (reader.hasNext()) {
                        try {
                            val feed = com.google.gson.Gson().fromJson<cx.aswin.boxcast.core.network.model.TrendingFeed>(
                                reader, 
                                cx.aswin.boxcast.core.network.model.TrendingFeed::class.java
                            )
                            
                            if (feed != null) {
                                val podcast = Podcast(
                                    id = feed.id.toString(),
                                    title = feed.title,
                                    artist = feed.author ?: "Unknown",
                                    imageUrl = (feed.artwork ?: feed.image).toHttps(),
                                    description = feed.description,
                                    genre = resolvePrimaryGenre(feed.categories),
                                    latestEpisode = feed.latestEpisode?.let { epItem ->
                                        mapToEpisode(epItem)?.copy(
                                            podcastId = epItem.feedId?.toString() ?: feed.id.toString(),
                                            podcastTitle = epItem.feedTitle?.takeIf { it.isNotBlank() } ?: feed.title
                                        )
                                    },
                                    medium = feed.medium
                                )
                                podcasts.add(podcast)
                                
                                if (podcasts.size % 4 == 0 || podcasts.size == 1) {
                                     emit(podcasts.toList())
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BoxCastRepo", "Stream: Feed parse error", e)
                        }
                    }
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            
            android.util.Log.d("BoxCastRepo", "Stream: Parsed ${podcasts.size} podcasts for category=$category")
            
            // Final emission
            if (podcasts.isNotEmpty()) {
                emit(podcasts)
            }
            
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Stream: Exception for category=$category", e)
            if (podcasts.isNotEmpty()) emit(podcasts)
        }
    }.flowOn(Dispatchers.IO)

    // Sort from most specific/descriptive to most generic to prevent "News" overriding "Sports"
    private val GENRE_PRIORITY = listOf(
        "True Crime", "Fiction", "Comedy", "Sports", "History", "Science", 
        "Technology", "Music", "TV & Film", "Arts", "Health & Fitness", "Health", 
        "Religion & Spirituality", "Kids & Family", "Education", "Government", "Business",
        "News", "Leisure", "Society & Culture", "Society", "Culture"
    )

    private fun resolvePrimaryGenre(categories: Map<String, String>?): String {
        if (categories.isNullOrEmpty()) return "Podcast"
        
        // 1. Check for exact matches in our priority list (from most specific to generic)
        for (priority in GENRE_PRIORITY) {
            val match = categories.values.find { it.equals(priority, ignoreCase = true) }
            if (match != null) return match
        }

        // 2. Fallback check for partial matches in our priority list
        for (priority in GENRE_PRIORITY) {
            val match = categories.values.find { it.contains(priority, ignoreCase = true) }
            if (match != null) return match
        }
        
        // 3. Last resort: Return the first value that isn't completely useless
        val ignored = listOf("podcasts", "podcast")
        return categories.values.firstOrNull { it.lowercase() !in ignored } ?: "Podcast"
    }

    private fun mapFeedsToPodcasts(feeds: List<cx.aswin.boxcast.core.network.model.TrendingFeed>): List<Podcast> {
        return feeds.map { feed ->
            Podcast(
                id = feed.id.toString(),
                title = feed.title,
                artist = feed.author ?: "Unknown",
                imageUrl = (feed.artwork ?: feed.image).toHttps(),
                description = feed.description,
                genre = resolvePrimaryGenre(feed.categories),
                latestEpisode = feed.latestEpisode?.let { epItem ->
                    mapToEpisode(epItem)?.copy(
                        podcastId = epItem.feedId?.toString() ?: feed.id.toString(),
                        podcastTitle = epItem.feedTitle?.takeIf { it.isNotBlank() } ?: feed.title
                    )
                },
                medium = feed.medium
            )
        }
    }

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
            return@withContext try {
                rssRepository.searchEpisodes(feedId, query)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PodcastRepository", "RSS searchEpisodes failed for $feedId", e)
                emptyList()
            }
        }
        try {
            val resolvedId = if (feedId.startsWith("url:") || feedId.startsWith("guid:") || feedId.startsWith("itunes:")) {
                getPodcastDetails(feedId)?.id ?: feedId
            } else {
                feedId
            }
            // Use Proxy-side search (Server fetches 1000 items and filters)
            val response = api.searchEpisodes(publicKey, resolvedId, query).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.items.mapNotNull { mapToEpisode(it) }
            } else {
                emptyList()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEpisodes(feedId: String): List<Episode> = withContext(Dispatchers.IO) {
        if (feedId.startsWith("rss:")) {
            return@withContext try {
                rssRepository.getAllEpisodes(feedId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PodcastRepository", "RSS getAllEpisodes failed for $feedId", e)
                emptyList()
            }
        }
        try {
            val resolvedId = if (feedId.startsWith("url:") || feedId.startsWith("guid:") || feedId.startsWith("itunes:")) {
                getPodcastDetails(feedId)?.id ?: feedId
            } else {
                feedId
            }
            // Use paginated endpoint with high limit to get "all" (max 1000 per proxy)
            // This avoids the parsing issue with EpisodesResponse vs EpisodesPaginatedResponse
            val response = api.getEpisodesPaginated(publicKey, resolvedId, limit = 1000).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.items.mapNotNull { mapToEpisode(it) }
            } else {
                emptyList()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEpisode(episodeId: String): Episode? = withContext(Dispatchers.IO) {
        if (episodeId.toLongOrNull()?.let { it < 0L } == true) {
            return@withContext try {
                rssRepository.getEpisode(episodeId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("PodcastRepository", "RSS getEpisode failed for $episodeId", e)
                null
            }
        }
        try {
            val response = api.getEpisode(publicKey, episodeId).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.episode?.let { mapToEpisode(it) }
            } else {
                null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private val playerPrefs = context.getSharedPreferences("boxcast_player", android.content.Context.MODE_PRIVATE)

    private fun getOrCreateDeviceUuid(): String {
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
        val resolvedId = if (feedId.startsWith("url:") || feedId.startsWith("guid:") || feedId.startsWith("itunes:")) {
            getPodcastDetails(feedId)?.id ?: feedId
        } else {
            feedId
        }
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

    suspend fun getPodcastDetails(feedId: String): Podcast? = withContext(Dispatchers.IO) {
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
    private fun executeGetPodcastRequest(feedId: String): retrofit2.Response<cx.aswin.boxcast.core.network.model.PodcastResponse> {
        return when {
            feedId.startsWith("url:") -> {
                val decodedUrl = java.net.URLDecoder.decode(feedId.substringAfter("url:"), "UTF-8")
                api.getPodcast(publicKey = publicKey, feedUrl = decodedUrl).execute()
            }
            feedId.startsWith("guid:") -> {
                api.getPodcast(publicKey = publicKey, feedGuid = feedId.substringAfter("guid:")).execute()
            }
            feedId.startsWith("itunes:") -> {
                api.getPodcast(publicKey = publicKey, itunesId = feedId.substringAfter("itunes:")).execute()
            }
            else -> api.getPodcast(publicKey = publicKey, feedId = feedId).execute()
        }
    }

    private fun mapPodcastResponseFeed(feed: cx.aswin.boxcast.core.network.model.PodcastFeed): Podcast = Podcast(
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
            
            val request = cx.aswin.boxcast.core.network.model.SyncRequest(podcastIndexIds)
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
        history: List<cx.aswin.boxcast.core.network.model.HistoryItem>,
        subscribedPodcastIds: List<String>,
    ): PodcastIndexScopedInputs {
        val podcastIndexHistory = history.filter { item ->
            item.podcastId?.startsWith("rss:") != true &&
                item.episodeId?.toLongOrNull()?.let { it > 0L } != false
        }
        val podcastIndexSubscriptionIds = subscribedPodcastIds.filterNot { it.startsWith("rss:") }
        return PodcastIndexScopedInputs(podcastIndexHistory, podcastIndexSubscriptionIds)
    }

    suspend fun getPersonalizedRecommendations(
        history: List<cx.aswin.boxcast.core.network.model.HistoryItem>,
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
        android.util.Log.d("PodcastRepository", "Cache MISS for getPersonalizedRecommendations. Fetching from network. Key: $cacheKey")

        try {
            val request = cx.aswin.boxcast.core.network.model.RecommendationsRequest(
                history = podcastIndexHistory,
                interests = interests,
                country = country,
                subscribedPodcastIds = podcastIndexSubscriptionIds,
                subscribedGenres = subscribedGenres
            )
            val response = api.getPersonalizedRecommendations(publicKey, getOrCreateDeviceUuid(), request).execute()
            if (response.isSuccessful && response.body() != null) {
                val results = response.body()!!.items.mapNotNull { mapToEpisode(it) }
                recommendationsCache[cacheKey] = Pair(results, now)
                results
            } else {
                emptyList()
            }
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
            val request = cx.aswin.boxcast.core.network.model.BecauseYouLikeRequest(
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
            val request = cx.aswin.boxcast.core.network.model.SimilarEpisodesRequest(
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
            val request = cx.aswin.boxcast.core.network.model.FeedbackRequest(category, message, appVersion, email)
            val response = api.submitFeedback(publicKey, request).execute()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            android.util.Log.e("BoxCastRepo", "Failed to submit feedback", e)
            false
        }
    }
    
    private fun mapToEpisode(item: cx.aswin.boxcast.core.network.model.EpisodeItem): Episode? {
        val audioUrl = item.enclosureUrl ?: return null
        android.util.Log.d("BoxCastRepo", "mapToEpisode: ${item.title} | persons=${item.persons?.size} | chaptersUrl=${item.chaptersUrl != null} | transcripts=${item.transcripts?.size}")
        val resolvedTranscriptUrl = item.transcripts?.firstOrNull { 
            it.type == "application/srt" || 
            it.type == "text/vtt" || 
            it.type == "application/x-subrip" ||
            it.url.contains(".srt", ignoreCase = true) ||
            it.url.contains(".vtt", ignoreCase = true)
        }?.url
        ?: item.transcriptUrl?.takeIf { 
            it.contains(".srt", ignoreCase = true) || 
            it.contains(".vtt", ignoreCase = true) 
        }
        ?: item.transcriptUrl
        ?: item.transcripts?.firstOrNull()?.url
        return Episode(
            id = item.id.toString(),
            title = item.title,
            description = item.description ?: "",
            audioUrl = audioUrl,
            imageUrl = (item.image?.takeIf { it.isNotBlank() } ?: item.feedImage?.takeIf { it.isNotBlank() }).toHttps(),
            podcastImageUrl = item.feedImage?.takeIf { it.isNotBlank() }?.let { it.toHttps() },
            podcastTitle = item.feedTitle,
            podcastId = item.feedId?.toString(),
            duration = item.duration ?: 0,
            publishedDate = item.datePublished ?: 0L,
            // Podcast 2.0
            chaptersUrl = item.chaptersUrl,
            transcriptUrl = resolvedTranscriptUrl,
            transcripts = item.transcripts?.map { Transcript(url = it.url, type = it.type) },
            persons = item.persons?.map { Person(name = it.name, role = it.role, group = it.group, img = it.img, href = it.href) },
            seasonNumber = item.season,
            episodeNumber = item.episodeNumber,
            episodeType = item.episodeType,
            enclosureType = item.enclosureType
        )
    }
    suspend fun getPodcastType(feedId: String): String = withContext(Dispatchers.IO) {
        try {
            val response = getPodcastMeta(feedId)
            response?.type ?: "episodic"
        } catch (e: Exception) {
            "episodic"
        }
    }

    suspend fun getPodcastMeta(feedId: String): cx.aswin.boxcast.core.network.model.PodcastMetaResponse? = withContext(Dispatchers.IO) {
        try {
            val response = if (feedId.startsWith("url:")) {
                val encodedUrl = feedId.substringAfter("url:")
                val decodedUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                api.getPodcastMeta(publicKey = publicKey, feedUrl = decodedUrl).execute()
            } else if (feedId.startsWith("guid:")) {
                val guid = feedId.substringAfter("guid:")
                api.getPodcastMeta(publicKey = publicKey, feedGuid = guid).execute()
            } else if (feedId.startsWith("itunes:")) {
                val itunesId = feedId.substringAfter("itunes:")
                api.getPodcastMeta(publicKey = publicKey, itunesId = itunesId).execute()
            } else {
                api.getPodcastMeta(publicKey = publicKey, feedId = feedId).execute()
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

    private fun mapToPodcast(feed: cx.aswin.boxcast.core.network.model.TrendingFeed): Podcast {
        return Podcast(
            id = feed.id.toString(),
            title = feed.title,
            artist = feed.author ?: "Unknown",
            imageUrl = (feed.artwork ?: feed.image).toHttps(),
            description = feed.description,
            genre = resolvePrimaryGenre(feed.categories),
            latestEpisode = feed.latestEpisode?.let { epItem ->
                mapToEpisode(epItem)?.copy(
                    podcastId = epItem.feedId?.toString() ?: feed.id.toString(),
                    podcastTitle = epItem.feedTitle?.takeIf { it.isNotBlank() } ?: feed.title
                )
            },
            medium = feed.medium
        )
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
        history: List<cx.aswin.boxcast.core.network.model.HistoryItem> = emptyList(),
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
                cx.aswin.boxcast.core.network.model.RecommendationsRequest(
                    history = podcastIndexHistory,
                    interests = interests,
                    country = country,
                    subscribedPodcastIds = podcastIndexSubscriptionIds,
                    subscribedGenres = subscribedGenres
                )
            } else {
                null
            }

            val request = cx.aswin.boxcast.core.network.model.BootstrapRequest(
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
                            ?: "https://api.aswin.cx/briefings/chapters/${fallbackBriefing.region}?d=${fallbackBriefing.date}$versionParam"
                        try {
                            briefingChapters = cx.aswin.boxcast.core.data.ChapterRepository.getChapters(chaptersUrl)
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

    companion object {
        private val episodesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<EpisodePage, Long>>()
        private val recommendationsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<Episode>, Long>>()
        private val becauseYouLikeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<BecauseYouLikeData, Long>>()
    }
}

data class HomeBootstrapData(
    val briefing: cx.aswin.boxcast.core.model.Briefing?,
    val briefingChapters: List<cx.aswin.boxcast.core.model.Chapter>,
    val trending: List<Podcast>,
    val curatedVibes: Map<String, List<Podcast>>,
    val recommendations: List<Episode>,
    val isRecommendationsFallback: Boolean = true
)

data class BecauseYouLikeData(
    val podcasts: List<Podcast> = emptyList(),
    val episodes: List<Episode> = emptyList()
)
