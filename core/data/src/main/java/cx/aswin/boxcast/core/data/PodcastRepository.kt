package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Person
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.Transcript
import cx.aswin.boxcast.core.model.Briefing
import cx.aswin.boxcast.core.network.BoxCastApi
import cx.aswin.boxcast.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import okhttp3.ResponseBody
import java.io.InputStreamReader

import cx.aswin.boxcast.core.network.model.TrendingFeed

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

data class SearchResult(
    val podcasts: List<cx.aswin.boxcast.core.model.Podcast>,
    val correctedQuery: String? = null
)

/**
 * Repository for podcast data via BoxCast API (Cloudflare Worker → Podcast Index)
 */
class PodcastRepository(
    private val baseUrl: String,
    val publicKey: String,
    context: android.content.Context
) {
    val api: BoxCastApi = NetworkModule.createBoxCastApi(baseUrl, context)

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
                        medium = feed.medium
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEpisodes(feedId: String): List<Episode> = withContext(Dispatchers.IO) {
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEpisode(episodeId: String): Episode? = withContext(Dispatchers.IO) {
        try {
            val response = api.getEpisode(publicKey, episodeId).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.episode?.let { mapToEpisode(it) }
            } else {
                null
            }
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
            return@withContext cached.first
        }
        android.util.Log.d("PodcastRepository", "Cache MISS for getEpisodesPaginated: $cacheKey. Fetching from network.")
        try {
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
        } catch (e: Exception) {
            EpisodePage(emptyList(), false)
        }
    }

    suspend fun getPodcastDetails(feedId: String): Podcast? = withContext(Dispatchers.IO) {
        try {
            val response = if (feedId.startsWith("url:")) {
                val encodedUrl = feedId.substringAfter("url:")
                val decodedUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                api.getPodcast(publicKey = publicKey, feedUrl = decodedUrl).execute()
            } else if (feedId.startsWith("guid:")) {
                val guid = feedId.substringAfter("guid:")
                api.getPodcast(publicKey = publicKey, feedGuid = guid).execute()
            } else if (feedId.startsWith("itunes:")) {
                val itunesId = feedId.substringAfter("itunes:")
                api.getPodcast(publicKey = publicKey, itunesId = itunesId).execute()
            } else {
                api.getPodcast(publicKey = publicKey, feedId = feedId).execute()
            }
            if (response.isSuccessful && response.body() != null) {
                val feed = response.body()!!.feed ?: return@withContext null
                Podcast(
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
                    isLocked = feed.locked == 1
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncSubscriptions(feedIds: List<String>): Map<String, Episode> = withContext(Dispatchers.IO) {
        try {
            if (feedIds.isEmpty()) return@withContext emptyMap()
            
            val request = cx.aswin.boxcast.core.network.model.SyncRequest(feedIds)
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

    suspend fun getPersonalizedRecommendations(
        history: List<cx.aswin.boxcast.core.network.model.HistoryItem>,
        interests: List<String> = emptyList(),
        country: String? = null,
        subscribedPodcastIds: List<String> = emptyList(),
        subscribedGenres: List<String> = emptyList()
    ): List<Episode> = withContext(Dispatchers.IO) {
        val cacheKey = buildString {
            append(country ?: "")
            append("|")
            append(interests.sorted().joinToString(","))
            append("|")
            append(subscribedPodcastIds.sorted().joinToString(","))
            append("|")
            append(subscribedGenres.sorted().joinToString(","))
            append("|")
            append(history.joinToString(",") { "${it.episodeId}:${it.progressMs}" })
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
                history = history,
                interests = interests,
                country = country,
                subscribedPodcastIds = subscribedPodcastIds,
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
                id = episodeId,
                podcastId = podcastId,
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
    
    suspend fun submitFeedback(category: String, message: String, appVersion: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = cx.aswin.boxcast.core.network.model.FeedbackRequest(category, message, appVersion)
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

    // --- RADIO METHODS ---

    suspend fun getRadioLocate(): cx.aswin.boxcast.core.network.model.RadioLocateResponse? = withContext(Dispatchers.IO) {
        try {
            val response = api.getRadioLocate(publicKey).execute()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPopularRadioStations(country: String? = null, limit: Int = 50, offset: Int = 0): List<cx.aswin.boxcast.core.network.model.RadioStationItem> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPopularStations(publicKey, country, limit, offset).execute()
            if (response.isSuccessful) response.body()?.stations ?: emptyList() else emptyList()
        } catch (e: Exception) {
            android.util.Log.e("RadioDebug", "getPopularRadioStations error", e)
            emptyList()
        }
    }

    suspend fun getTrendingRadioStations(country: String? = null, limit: Int = 10): List<cx.aswin.boxcast.core.network.model.RadioStationItem> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTrendingStations(publicKey, country, limit).execute()
            if (response.isSuccessful) response.body()?.stations ?: emptyList() else emptyList()
        } catch (e: Exception) {
            android.util.Log.e("RadioDebug", "getTrendingRadioStations error", e)
            emptyList()
        }
    }

    suspend fun getRadioStationsByGenre(tag: String, limit: Int = 50): List<cx.aswin.boxcast.core.network.model.RadioStationItem> = withContext(Dispatchers.IO) {
        try {
            val response = api.getStationsByGenre(publicKey, tag, limit).execute()
            if (response.isSuccessful) response.body()?.stations ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchRadioStations(query: String? = null, tag: String? = null, country: String? = null, language: String? = null, limit: Int = 50): List<cx.aswin.boxcast.core.network.model.RadioStationItem> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchStations(publicKey, query, tag, country, language, limit).execute()
            if (response.isSuccessful) response.body()?.stations ?: emptyList() else emptyList()
        } catch (e: Exception) {
            android.util.Log.e("RadioDebug", "searchRadioStations error", e)
            emptyList()
        }
    }

    suspend fun getBriefingMetadata(region: String): Briefing? = withContext(Dispatchers.IO) {
        try {
            val response = api.getBriefingMetadata(region).execute()
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PodcastRepository", "Failed to fetch briefing for $region", e)
            null
        }
    }

    companion object {
        private val episodesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<EpisodePage, Long>>()
        private val recommendationsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<Episode>, Long>>()
    }
}
