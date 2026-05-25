package cx.aswin.boxcast.core.data.backup

import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.data.PlaybackRepository
import cx.aswin.boxcast.core.data.database.PodcastEntity
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import kotlinx.coroutines.flow.first
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import android.util.Log

data class BoxCastBackup(
    val version: Int = 1,
    val subscriptions: List<PodcastEntity>,
    val history: List<ListeningHistoryEntity>
)

data class OpmlFeed(
    val title: String,
    val xmlUrl: String
)

class LibraryBackupManager(
    private val subscriptionRepository: SubscriptionRepository,
    private val playbackRepository: PlaybackRepository,
    private val podcastRepository: PodcastRepository
) {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    suspend fun exportLibraryAsJson(): String {
        val subscriptions = subscriptionRepository.getAllSubscribedPodcasts().first()
        val allHistory = playbackRepository.getAllHistory().first()
        
        val backup = BoxCastBackup(
            version = 1,
            subscriptions = subscriptions,
            history = allHistory
        )
        return gson.toJson(backup)
    }

    suspend fun importLibraryFromJson(jsonString: String): Int {
        return try {
            val backup = gson.fromJson(jsonString, BoxCastBackup::class.java)
            
            val importedIds = mutableListOf<String>()
            
            // 1. Restore subscriptions
            for (entity in backup.subscriptions) {
                // Assuming we have a way to save entity directly or we re-map to model
                // Since SubscriptionRepository expects a Podcast model to subscribe,
                // let's construct a domain Podcast model and pass it.
                val podcast = cx.aswin.boxcast.core.model.Podcast(
                    id = (entity.podcastId as String?) ?: "", // Cast to prevent Kotlin from optimizing out the null-check
                    title = (entity.title as String?) ?: "Unknown",
                    artist = (entity.author as String?) ?: "Unknown",
                    imageUrl = (entity.imageUrl as String?) ?: "",
                    description = entity.description,
                    genre = entity.genre ?: "Podcast",
                    type = (entity.type as String?) ?: "episodic",
                    updateFrequency = entity.updateFrequency
                )
                subscriptionRepository.subscribe(podcast)
                importedIds.add(podcast.id)
            }
            
            // 2. Restore playback histories (liked episodes)
            for (entity in backup.history) {
                // Reconstruct safe entity to prevent null crashes on non-nullable String fields
                // Primitives (Long, Boolean) are safe as Gson initializes them to 0/false if missing
                val safeEntity = cx.aswin.boxcast.core.data.database.ListeningHistoryEntity(
                    episodeId = (entity.episodeId as String?) ?: "",
                    podcastId = (entity.podcastId as String?) ?: "",
                    episodeTitle = (entity.episodeTitle as String?) ?: "Unknown",
                    episodeImageUrl = entity.episodeImageUrl,
                    podcastImageUrl = entity.podcastImageUrl,
                    episodeAudioUrl = entity.episodeAudioUrl,
                    podcastName = (entity.podcastName as String?) ?: "Unknown",
                    progressMs = entity.progressMs,
                    durationMs = entity.durationMs,
                    isCompleted = entity.isCompleted,
                    isLiked = entity.isLiked,
                    lastPlayedAt = entity.lastPlayedAt,
                    isDirty = entity.isDirty,
                    syncedAt = entity.syncedAt,
                    enclosureType = entity.enclosureType
                )
                playbackRepository.upsertHistoryEntity(safeEntity)
            }
            
            // 3. Trigger check for new episodes
            if (importedIds.isNotEmpty()) {
                try {
                    val syncedMap = podcastRepository.syncSubscriptions(importedIds)
                    for ((id, ep) in syncedMap) {
                        subscriptionRepository.updateLatestEpisode(id, ep)
                    }
                } catch (e: Exception) {
                    Log.e("JSON_IMPORT", "Failed to sync episodes", e)
                }
            }
            
            backup.subscriptions.size
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    suspend fun importFromOpml(inputStream: InputStream): Int {
        var importedCount = 0
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            val queries = mutableListOf<String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    val title = parser.getAttributeValue(null, "text") ?: parser.getAttributeValue(null, "title")
                    
                    if (xmlUrl != null && title != null) {
                        queries.add(title)
                    }
                }
                eventType = parser.next()
            }

            val importedIds = mutableListOf<String>()
            
            // Fallback: search BoxCast API for each query silently to resolve
            for (query in queries) {
                try {
                    // Try to search by title
                    val results = podcastRepository.searchPodcasts(query)
                    if (results.isNotEmpty()) {
                        val match = results.first()
                        subscriptionRepository.subscribe(match)
                        importedIds.add(match.id)
                        importedCount++
                    }
                } catch (e: Exception) {
                    Log.e("OPML_IMPORT", "Failed to resolve: $query", e)
                }
            }
            
            // Trigger check for new episodes
            if (importedIds.isNotEmpty()) {
                try {
                    // Break into chunks if OPML is huge to prevent request timeouts
                    importedIds.chunked(50).forEach { chunk ->
                        val syncedMap = podcastRepository.syncSubscriptions(chunk)
                        for ((id, ep) in syncedMap) {
                            subscriptionRepository.updateLatestEpisode(id, ep)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OPML_IMPORT", "Failed to sync episodes", e)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }
        return importedCount
    }

    fun parseOpmlFeeds(inputStream: InputStream): List<OpmlFeed> {
        val feeds = mutableListOf<OpmlFeed>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    val title = parser.getAttributeValue(null, "text") ?: parser.getAttributeValue(null, "title")
                    
                    if (xmlUrl != null && title != null) {
                        feeds.add(OpmlFeed(title, xmlUrl))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to parse OPML feeds", e)
        }
        return feeds
    }

    suspend fun importSingleOpmlFeed(feed: OpmlFeed): cx.aswin.boxcast.core.model.Podcast? {
        try {
            var matchedPodcast: cx.aswin.boxcast.core.model.Podcast? = null
            
            // 1. Try URL lookup since it is exact and fast
            try {
                val encodedUrl = java.net.URLEncoder.encode(feed.xmlUrl, "UTF-8")
                matchedPodcast = podcastRepository.getPodcastDetails("url:$encodedUrl")
            } catch (e: Exception) {
                Log.e("OPML_IMPORT", "Failed URL lookup for: ${feed.xmlUrl}", e)
            }
            
            // 2. Fallback to searching by title if URL lookup returned null
            if (matchedPodcast == null) {
                val results = podcastRepository.searchPodcasts(feed.title)
                if (results.isNotEmpty()) {
                    matchedPodcast = results.first()
                }
            }
            
            if (matchedPodcast != null) {
                subscriptionRepository.subscribe(matchedPodcast)
                // Sync latest episode so it shows up correctly in the home list
                try {
                    val syncedMap = podcastRepository.syncSubscriptions(listOf(matchedPodcast.id))
                    for ((id, ep) in syncedMap) {
                        subscriptionRepository.updateLatestEpisode(id, ep)
                    }
                } catch (e: Exception) {
                    Log.e("OPML_IMPORT", "Failed to sync episodes for: ${matchedPodcast.title}", e)
                }
                return matchedPodcast
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to import single feed: ${feed.title}", e)
        }
        return null
    }

    suspend fun markAllEpisodesCompleted(podcast: cx.aswin.boxcast.core.model.Podcast) {
        try {
            // Get complete backlog of episodes (bypassing initial limit)
            val episodes = podcastRepository.getEpisodes(podcast.id)
            if (episodes.isNotEmpty()) {
                playbackRepository.markAllEpisodesCompleted(
                    episodes = episodes,
                    podcastId = podcast.id,
                    podcastTitle = podcast.title,
                    podcastImageUrl = podcast.imageUrl
                )
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to mark all episodes completed for: ${podcast.title}", e)
        }
    }
}
