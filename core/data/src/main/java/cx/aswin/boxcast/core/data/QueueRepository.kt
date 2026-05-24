package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.data.database.BoxCastDatabase
import cx.aswin.boxcast.core.data.database.entities.QueueItem
import cx.aswin.boxcast.core.network.model.EpisodeItem
import cx.aswin.boxcast.core.model.Person
import cx.aswin.boxcast.core.model.Transcript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepository @Inject constructor(
    private val database: BoxCastDatabase,
    private val podcastRepository: PodcastRepository
) {
    private val TAG = "QueueRepository"
    private val queueDao = database.queueDao()

    val queue: Flow<List<EpisodeItem>> = queueDao.getAllQueueItems()
        .map { items ->
            items.map { it.toEpisodeItem() }
        }

    suspend fun addToQueue(
        episode: EpisodeItem,
        podcast: cx.aswin.boxcast.core.model.Podcast?,
        contextType: String? = null,
        contextSourceId: String? = null
    ) {
        android.util.Log.d(TAG, "addToQueue: episodeId=${episode.id}, title=${episode.title}, contextType=$contextType, contextSourceId=$contextSourceId")
        val maxPos = queueDao.getMaxPosition() ?: 0

        // Check for duplicates using String ID
        val episodeIdStr = episode.id.toString()
        val existingCount = queueDao.countEpisode(episodeIdStr)
        if (existingCount > 0) {
            android.util.Log.w(TAG, "addToQueue: Episode ${episode.title} ($episodeIdStr) already in queue. Skipping.")
            return
        }
        
        val podcastTitle = podcast?.title ?: "Unknown Podcast"
        val podcastId = podcast?.id ?: ""

        val resolvedTranscriptUrl = episode.transcripts?.firstOrNull { 
            it.type == "application/srt" || 
            it.type == "text/vtt" || 
            it.type == "application/x-subrip" ||
            it.url.contains(".srt", ignoreCase = true) ||
            it.url.contains(".vtt", ignoreCase = true)
        }?.url
        ?: episode.transcriptUrl?.takeIf { 
            it.contains(".srt", ignoreCase = true) || 
            it.contains(".vtt", ignoreCase = true) 
        }
        ?: episode.transcriptUrl
        ?: episode.transcripts?.firstOrNull()?.url

        val newItem = QueueItem(
            episodeId = episodeIdStr,
            title = episode.title,
            podcastId = podcastId,
            podcastTitle = podcastTitle,
            podcastGenre = podcast?.genre ?: "",
            podcastArtist = podcast?.artist ?: "",
            podcastImageUrl = podcast?.imageUrl,
            imageUrl = episode.image ?: podcast?.imageUrl,
            audioUrl = episode.enclosureUrl ?: "",
            duration = episode.duration ?: 0,
            pubDate = episode.datePublished ?: 0L,
            description = episode.description,
            position = maxPos + 1,
            contextType = contextType ?: "MANUAL",
            contextSourceId = contextSourceId,
            // Podcast 2.0
            chaptersUrl = episode.chaptersUrl,
            transcriptUrl = resolvedTranscriptUrl,
            personsJson = encodePersons(episode.persons?.map { Person(name = it.name, role = it.role, img = it.img, href = it.href) }),
            transcriptsJson = encodeTranscripts(episode.transcripts?.map { Transcript(url = it.url, type = it.type) }),
            episodeType = episode.episodeType,
            seasonNumber = episode.season,
            episodeNumber = episode.episodeNumber
        )
        android.util.Log.d(TAG, "addToQueue: Inserting newItem at position ${maxPos + 1}")
        queueDao.insertQueueItem(newItem)
    }

    suspend fun clearQueue() {
        android.util.Log.d(TAG, "clearQueue: Clearing all queue items")
        queueDao.clearQueue()
    }
    
    /**
     * Replace the entire queue with the provided items.
     * Used to sync in-memory queue state back to DB.
     */
    suspend fun replaceQueue(episodes: List<cx.aswin.boxcast.core.model.Episode>) {
        queueDao.clearQueue()
        episodes.forEachIndexed { index, ep ->
            val item = QueueItem(
                episodeId = ep.id,
                title = ep.title,
                podcastId = ep.podcastId ?: "",
                podcastTitle = ep.podcastTitle ?: "",
                podcastGenre = ep.podcastGenre ?: "",
                podcastArtist = ep.podcastArtist ?: "",
                podcastImageUrl = ep.podcastImageUrl,
                imageUrl = ep.imageUrl,
                audioUrl = ep.audioUrl,
                duration = ep.duration,
                pubDate = ep.publishedDate,
                description = ep.description,
                position = index,
                contextType = ep.contextType ?: "MANUAL",
                contextSourceId = ep.contextSourceId,
                // Podcast 2.0
                chaptersUrl = ep.chaptersUrl,
                transcriptUrl = ep.transcriptUrl,
                personsJson = encodePersons(ep.persons),
                transcriptsJson = encodeTranscripts(ep.transcripts),
                episodeType = ep.episodeType,
                seasonNumber = ep.seasonNumber,
                episodeNumber = ep.episodeNumber
            )
            queueDao.insertQueueItem(item)
        }
    }
    
    suspend fun getQueueSnapshot(): List<cx.aswin.boxcast.core.model.Episode> {
        android.util.Log.d(TAG, "getQueueSnapshot: Fetching sync")
        val items = queueDao.getAllQueueItemsSync()
        android.util.Log.d(TAG, "getQueueSnapshot: Got ${items.size} items")
        return items.map { it.toDomainEpisode() }
    }
    
    private fun cx.aswin.boxcast.core.data.database.entities.QueueItem.toDomainEpisode(): cx.aswin.boxcast.core.model.Episode {
        return cx.aswin.boxcast.core.model.Episode(
            id = this.episodeId,
            title = this.title,
            description = this.description ?: "",
            audioUrl = this.audioUrl,
            imageUrl = this.imageUrl,
            podcastImageUrl = this.podcastImageUrl ?: this.imageUrl,
            podcastTitle = this.podcastTitle,
            podcastId = this.podcastId,
            podcastGenre = this.podcastGenre,
            podcastArtist = this.podcastArtist,
            duration = this.duration ?: 0,
            publishedDate = this.pubDate,
            // Podcast 2.0
            chaptersUrl = this.chaptersUrl,
            transcriptUrl = this.transcriptUrl,
            persons = decodePersons(this.personsJson),
            transcripts = decodeTranscripts(this.transcriptsJson),
            episodeType = this.episodeType,
            seasonNumber = this.seasonNumber,
            episodeNumber = this.episodeNumber,
            contextType = this.contextType,
            contextSourceId = this.contextSourceId
        )
    }
    
    // --- P2.0 JSON helpers (using Android's org.json) ---
    
    private fun encodePersons(persons: List<Person>?): String? {
        if (persons.isNullOrEmpty()) return null
        val arr = JSONArray()
        persons.forEach { p ->
            val obj = JSONObject()
            obj.put("name", p.name)
            p.role?.let { obj.put("role", it) }
            p.img?.let { obj.put("img", it) }
            p.href?.let { obj.put("href", it) }
            arr.put(obj)
        }
        return arr.toString()
    }
    
    private fun decodePersons(json: String?): List<Person>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Person(
                    name = obj.getString("name"),
                    role = obj.optString("role", null),
                    img = obj.optString("img", null),
                    href = obj.optString("href", null)
                )
            }
        } catch (e: Exception) { null }
    }
    
    private fun encodeTranscripts(transcripts: List<Transcript>?): String? {
        if (transcripts.isNullOrEmpty()) return null
        val arr = JSONArray()
        transcripts.forEach { t ->
            val obj = JSONObject()
            obj.put("url", t.url)
            t.type?.let { obj.put("type", it) }
            arr.put(obj)
        }
        return arr.toString()
    }
    
    private fun decodeTranscripts(json: String?): List<Transcript>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Transcript(
                    url = obj.getString("url"),
                    type = obj.optString("type", null)
                )
            }
        } catch (e: Exception) { null }
    }

    suspend fun getQueueItemByEpisodeId(episodeId: String): cx.aswin.boxcast.core.data.database.entities.QueueItem? {
        return queueDao.getQueueItemByEpisodeId(episodeId)
    }

    suspend fun reorderQueue(items: List<EpisodeItem>) {
        // Deferred
    }
}
