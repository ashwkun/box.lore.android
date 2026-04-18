package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Person
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.Transcript
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

/**
 * Repository for podcast data via BoxCast API (Cloudflare Worker → Podcast Index)
 */
class PodcastRepository(
    private val baseUrl: String,
    private val publicKey: String,
    context: android.content.Context
) {
    private val api: BoxCastApi = NetworkModule.createBoxCastApi(baseUrl, context)

    suspend fun getTrendingPodcasts(country: String = "us", limit: Int = 50, category: String? = null): List<Podcast> = withContext(Dispatchers.IO) {
        // Fallback or non-streaming implementation
        try {
            val response = api.getTrending(publicKey, country, limit, category).execute()
            if (response.isSuccessful && response.body() != null) {
                mapFeedsToPodcasts(response.body()!!.feeds)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCuratedPodcasts(vibeId: String): List<Podcast> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCuratedVibe(publicKey, vibeId).execute()
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

    fun getTrendingPodcastsStream(country: String = "us", limit: Int = 50, category: String? = null): kotlinx.coroutines.flow.Flow<List<Podcast>> = kotlinx.coroutines.flow.flow {
        val podcasts = mutableListOf<Podcast>()
        try {
            android.util.Log.d("BoxCastRepo", "Stream: Requesting trending country=$country, limit=$limit, category=$category")
            val call = api.getTrendingStream(publicKey, country, limit, category)
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
                                    genre = feed.categories.values.firstOrNull() ?: "Podcast",
                                    latestEpisode = feed.latestEpisode?.let { mapToEpisode(it) }
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

    private val GENRE_PRIORITY = listOf(
        "Technology", "News", "Business", "Science", "Sports", "True Crime",
        "History", "Comedy", "Arts", "Fiction", "Music", "Religion & Spirituality",
        "Kids & Family", "Government", "Health", "TV & Film", "Education"
    )

    private fun resolvePrimaryGenre(categories: Map<String, String>?): String {
        if (categories.isNullOrEmpty()) return "Podcast"
        
        // Check for high-priority genres first
        for (priority in GENRE_PRIORITY) {
            // Case-insensitive check
            val match = categories.values.find { it.equals(priority, ignoreCase = true) }
            if (match != null) return match
        }
        
        // Fallback: Return the first value
        return categories.values.firstOrNull() ?: "Podcast"
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
                latestEpisode = feed.latestEpisode?.let { mapToEpisode(it) }
            )
        }
    }

    suspend fun searchPodcasts(query: String): List<Podcast> = withContext(Dispatchers.IO) {
        try {
            val response = api.search(publicKey, query).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.feeds.map { feed ->
                    Podcast(
                        id = feed.id.toString(),
                        title = feed.title,
                        artist = feed.author ?: "Unknown",
                        imageUrl = (feed.artwork ?: feed.image).toHttps(),
                        description = feed.description,
                        genre = resolvePrimaryGenre(feed.categories)
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchEpisodes(feedId: String, query: String): List<Episode> = withContext(Dispatchers.IO) {
        try {
            // Use Proxy-side search (Server fetches 1000 items and filters)
            val response = api.searchEpisodes(publicKey, feedId, query).execute()
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
            // Use paginated endpoint with high limit to get "all" (max 1000 per proxy)
            // This avoids the parsing issue with EpisodesResponse vs EpisodesPaginatedResponse
            val response = api.getEpisodesPaginated(publicKey, feedId, limit = 1000).execute()
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
        try {
            val response = api.getEpisodesPaginated(publicKey, feedId, limit, offset, sort).execute()
            if (response.isSuccessful && response.body() != null) {
                EpisodePage(
                    episodes = response.body()!!.items.mapNotNull { mapToEpisode(it) },
                    hasMore = response.body()!!.hasMore
                )
            } else {
                EpisodePage(emptyList(), false)
            }
        } catch (e: Exception) {
            EpisodePage(emptyList(), false)
        }
    }

    suspend fun getPodcastDetails(feedId: String): Podcast? = withContext(Dispatchers.IO) {
        try {
            val response = api.getPodcast(publicKey, feedId).execute()
            if (response.isSuccessful && response.body() != null) {
                val feed = response.body()!!.feed ?: return@withContext null
                Podcast(
                    id = feed.id.toString(),
                    title = feed.title,
                    artist = feed.author ?: "Unknown",
                    imageUrl = (feed.artwork ?: feed.image).toHttps(),
                    description = feed.description,
                    genre = resolvePrimaryGenre(feed.categories),
                    // Podcast 2.0
                    fundingUrl = feed.funding?.url,
                    fundingMessage = feed.funding?.message,
                    podcastGuid = feed.podcastGuid,
                    medium = feed.medium,
                    ownerName = feed.ownerName,
                    hasValue = feed.value != null
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
                    val ep = item.latestEpisode?.let { mapToEpisode(it) }
                    if (ep != null) item.id to ep else null
                }.toMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
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
        return Episode(
            id = item.id.toString(),
            title = item.title,
            description = item.description ?: "",
            audioUrl = audioUrl,
            imageUrl = (item.image?.takeIf { it.isNotBlank() } ?: item.feedImage?.takeIf { it.isNotBlank() }).toHttps(),
            podcastImageUrl = item.feedImage?.takeIf { it.isNotBlank() }?.let { it.toHttps() },
            podcastId = item.feedId?.toString(),
            duration = item.duration ?: 0,
            publishedDate = item.datePublished ?: 0L,
            // Podcast 2.0
            chaptersUrl = item.chaptersUrl,
            transcriptUrl = item.transcriptUrl,
            transcripts = item.transcripts?.map { Transcript(url = it.url, type = it.type) },
            persons = item.persons?.map { Person(name = it.name, role = it.role, img = it.img, href = it.href) },
            seasonNumber = item.season,
            episodeNumber = item.episodeNumber,
            episodeType = item.episodeType
        )
    }
    suspend fun getPodcastType(feedId: String): String = withContext(Dispatchers.IO) {
        try {
            val response = api.getPodcastMeta(publicKey, feedId).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.type ?: "episodic"
            } else {
                "episodic"
            }
        } catch (e: Exception) {
            "episodic"
        }
    }
}
