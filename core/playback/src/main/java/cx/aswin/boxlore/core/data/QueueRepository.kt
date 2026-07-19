package cx.aswin.boxlore.core.data

import androidx.room.withTransaction
import cx.aswin.boxlore.core.data.database.BoxLoreDatabase
import cx.aswin.boxlore.core.data.database.entities.QueueItem
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Transcript
import cx.aswin.boxlore.core.network.model.EpisodeItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class QueueRepository(
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
) {
    private val TAG = "QueueRepository"
    private val queueDao = database.queueDao()

    val queue: Flow<List<EpisodeItem>> =
        queueDao
            .getAllQueueItems()
            .map { items ->
                items.distinctBy { it.episodeId }.map { it.toEpisodeItem() }
            }

    suspend fun addToQueue(
        episode: EpisodeItem,
        podcast: cx.aswin.boxlore.core.model.Podcast?,
        contextType: String? = null,
        contextSourceId: String? = null,
    ) {
        android.util.Log.d(
            TAG,
            "addToQueue: episodeId=${episode.id}, title=${episode.title}, contextType=$contextType, contextSourceId=$contextSourceId",
        )
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

        val resolvedTranscriptUrl =
            episode.transcripts
                ?.firstOrNull {
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

        val newItem =
            QueueItem(
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
                episodeNumber = episode.episodeNumber,
                enclosureType = episode.enclosureType,
            )
        android.util.Log.d(TAG, "addToQueue: Inserting newItem at position ${maxPos + 1}")
        queueDao.insertQueueItem(newItem)
        if (newItem.contextType == "MANUAL" || newItem.contextType == QueueMath.CONTEXT_TYPE_LORE) {
            RankingFeedbackRepository.getIfInitialized()?.recordAction(
                target =
                    FeedbackTarget(
                        episodeId = newItem.episodeId,
                        podcastId = newItem.podcastId,
                        genre = newItem.podcastGenre,
                        source =
                            if (newItem.contextType == QueueMath.CONTEXT_TYPE_LORE) {
                                CandidateSource.CURATED_INTENT
                            } else {
                                null
                            },
                    ),
                action = RankingAction.EXPLICIT_QUEUE,
            )
        }
    }

    suspend fun clearQueue() {
        android.util.Log.d(TAG, "clearQueue: Clearing all queue items")
        queueDao.clearQueue()
    }

    /**
     * Replace the entire queue with the provided items.
     * Used to sync in-memory queue state back to DB.
     */
    suspend fun replaceQueue(episodes: List<cx.aswin.boxlore.core.model.Episode>) {
        val uniqueEpisodes = episodes.distinctBy { it.id }
        if (uniqueEpisodes.size != episodes.size) {
            android.util.Log.w(
                TAG,
                "replaceQueue: Removed ${episodes.size - uniqueEpisodes.size} duplicate episode IDs",
            )
        }
        database.withTransaction {
            replaceQueueItems(uniqueEpisodes)
        }
    }

    private suspend fun replaceQueueItems(episodes: List<cx.aswin.boxlore.core.model.Episode>) {
        queueDao.clearQueue()
        episodes.forEachIndexed { index, ep ->
            val item =
                QueueItem(
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
                    episodeNumber = ep.episodeNumber,
                    enclosureType = ep.enclosureType,
                )
            queueDao.insertQueueItem(item)
        }
    }

    suspend fun getQueueSnapshot(): List<cx.aswin.boxlore.core.model.Episode> =
        database.withTransaction {
            android.util.Log.d(TAG, "getQueueSnapshot: Fetching sync")
            val items = queueDao.getAllQueueItemsSync()
            android.util.Log.d(TAG, "getQueueSnapshot: Got ${items.size} items")
            val episodes = items.map { it.toDomainEpisode() }
            val uniqueEpisodes = episodes.distinctBy { it.id }
            if (uniqueEpisodes.size != episodes.size) {
                android.util.Log.w(
                    TAG,
                    "getQueueSnapshot: Repairing ${episodes.size - uniqueEpisodes.size} duplicate queue rows",
                )
                replaceQueueItems(uniqueEpisodes)
            }
            uniqueEpisodes
        }

    private fun cx.aswin.boxlore.core.data.database.entities.QueueItem.toDomainEpisode(): cx.aswin.boxlore.core.model.Episode =
        cx.aswin.boxlore.core.model.Episode(
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
            duration = this.duration,
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
            contextSourceId = this.contextSourceId,
            enclosureType = this.enclosureType,
        )

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
                    role = if (obj.isNull("role")) null else obj.optString("role"),
                    img = if (obj.isNull("img")) null else obj.optString("img"),
                    href = if (obj.isNull("href")) null else obj.optString("href"),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun encodeTranscripts(transcripts: List<Transcript>?): String? {
        if (transcripts.isNullOrEmpty()) return null
        val arr = JSONArray()
        transcripts.forEach { t ->
            val obj = JSONObject()
            obj.put("url", t.url)
            obj.put("type", t.type)
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
                    type = obj.optString("type"),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getQueueItemByEpisodeId(episodeId: String): cx.aswin.boxlore.core.data.database.entities.QueueItem? =
        queueDao.getQueueItemByEpisodeId(episodeId)

    /**
     * Rewrites row positions to match the given episode-id order (a Room transaction via
     * the DAO), preserving each row's contextType/contextSourceId provenance.
     * Ids not present in the DB are ignored; rows not present in the list keep their
     * position but are pushed after the reordered block.
     */
    suspend fun reorderQueue(orderedEpisodeIds: List<String>) {
        val items = queueDao.getAllQueueItemsSync()
        if (items.isEmpty() || orderedEpisodeIds.isEmpty()) return

        val byEpisodeId = items.associateBy { it.episodeId }
        val reordered = mutableListOf<QueueItem>()
        orderedEpisodeIds.distinct().forEach { episodeId ->
            byEpisodeId[episodeId]?.let { reordered.add(it) }
        }
        // Keep any rows that weren't part of the provided order (defensive) at the tail.
        val coveredIds = reordered.map { it.episodeId }.toSet()
        items.filter { it.episodeId !in coveredIds }.forEach { reordered.add(it) }

        val updated = reordered.mapIndexed { index, item -> item.copy(position = index) }
        android.util.Log.d(TAG, "reorderQueue: Rewriting positions for ${updated.size} items")
        queueDao.updateQueuePositions(updated)
    }
}
