package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.network.model.EpisodeItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val queueRepository: QueueRepository,
    private val smartQueueEngine: SmartQueueEngine,
    private val playbackRepository: PlaybackRepository,
    private val podcastRepository: PodcastRepository
) {
    private val TAG = "QueueManager"
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isRefilling = false
    
    init {
        android.util.Log.d(TAG, "QueueManager initialized")
        // Set up auto-refill callback
        playbackRepository.queueRefillCallback = { currentEpisode, podcast ->
            android.util.Log.d(TAG, "queueRefillCallback triggered for: ${currentEpisode.title}")
            refillQueue(currentEpisode, podcast)
        }
    }
    
    /**
     * Auto-refill queue when running low on episodes
     */
    private fun refillQueue(currentEpisode: cx.aswin.boxcast.core.model.Episode, podcast: cx.aswin.boxcast.core.model.Podcast) {
        if (isRefilling) return
        isRefilling = true
        
        scope.launch {
            android.util.Log.d(TAG, "Auto-refill triggered for: ${currentEpisode.title}")
            
            val currentItem = EpisodeItem(
                id = currentEpisode.id.toLongOrNull() ?: 0L,
                title = currentEpisode.title,
                description = currentEpisode.description,
                enclosureUrl = currentEpisode.audioUrl,
                duration = currentEpisode.duration,
                datePublished = currentEpisode.publishedDate,
                image = currentEpisode.imageUrl,
                feedImage = currentEpisode.podcastImageUrl
            )
            
            val nextEntries = smartQueueEngine.getNextEpisodes(currentItem, podcast, null)
            android.util.Log.d(TAG, "Auto-refill got ${nextEntries.size} more episodes")
            
            // Defensive dedup: skip any entry matching currently playing episode
            val currentEpisodeId = currentEpisode.id
            val currentEpisodeTitle = currentEpisode.title
            
            nextEntries.forEach { entry ->
                val domainNext = entry.episode.toDomain(entry.podcast)
                
                // Skip if same ID or exact same title as currently playing
                if (domainNext.id == currentEpisodeId || domainNext.title == currentEpisodeTitle) {
                    android.util.Log.d(TAG, "Refill: Skipping duplicate '${domainNext.title}' (id=${domainNext.id})")
                    return@forEach
                }
                
                // Add to Persistence
                queueRepository.addToQueue(entry.episode, entry.podcast)
                
                // Add to Active Player Queue
                playbackRepository.addToQueue(domainNext, entry.podcast)
            }
            
            isRefilling = false
        }
    }

    fun playEpisode(episode: EpisodeItem, podcast: cx.aswin.boxcast.core.model.Podcast?, preferredSort: String? = null) {
        scope.launch {
            android.util.Log.d(TAG, "playEpisode called: ${episode.title}, sort=$preferredSort")
            
            if (podcast != null) {
                // 1. Clear current queue for a fresh start
                queueRepository.clearQueue()
                
                // 2. Add selected episode & Persist
                queueRepository.addToQueue(episode, podcast)
                
                // 3. Start playback IMMEDIATELY with just the current episode
                // The queueRefillCallback will auto-fill more episodes when queue runs low
                val domainEpisode = episode.toDomain(podcast)
                android.util.Log.d(TAG, "Starting playback immediately for: ${domainEpisode.title}")
                playbackRepository.playQueue(listOf(domainEpisode), podcast, 0)
            } else {
                 android.util.Log.e(TAG, "Podcast is null!")
            }
        }
    }

    fun addToQueue(episode: EpisodeItem, podcast: cx.aswin.boxcast.core.model.Podcast?) {
        android.util.Log.d(TAG, "addToQueue called: episodeId=${episode.id}, title=${episode.title}, podcast=${podcast?.title}")
        scope.launch {
            if (podcast != null) {
                // Persist
                queueRepository.addToQueue(episode, podcast)
                
                // Add to Player
                val domainEpisode = episode.toDomain(podcast)
                playbackRepository.addToQueue(domainEpisode, podcast)
                android.util.Log.d(TAG, "addToQueue: Complete. Current queue size: ${playbackRepository.playerState.value.queue.size}")
            } else {
                android.util.Log.e(TAG, "addToQueue: Podcast is null, ignoring!")
            }
        }
    }
    
    // Overload for Domain Objects (UI)
    fun playEpisode(episode: cx.aswin.boxcast.core.model.Episode, podcast: cx.aswin.boxcast.core.model.Podcast?, preferredSort: String? = null) {
        val currentQueue = playbackRepository.playerState.value.queue
        val currentPodcast = playbackRepository.playerState.value.currentPodcast

        // Check if we can just skip to the episode in the existing queue
        if (podcast != null && currentPodcast?.id == podcast.id) {
             val index = currentQueue.indexOfFirst { it.id == episode.id }
             if (index != -1) {
                 android.util.Log.d(TAG, "Episode found in existing queue at index $index. Skipping to it.")
                 playbackRepository.skipToEpisode(index)
                 return
             }
        }

        val item = episode.toEpisodeItem(podcast)
        playEpisode(item, podcast, preferredSort)
    }

    private fun cx.aswin.boxcast.core.model.Episode.toEpisodeItem(podcast: cx.aswin.boxcast.core.model.Podcast?): EpisodeItem {
        return EpisodeItem(
            id = this.id.toLongOrNull() ?: 0L,
            title = this.title,
            description = this.description,
            enclosureUrl = this.audioUrl,
            duration = this.duration,
            datePublished = this.publishedDate,
            image = this.imageUrl,
            feedImage = this.podcastImageUrl ?: podcast?.imageUrl,
            // Podcast 2.0
            chaptersUrl = this.chaptersUrl,
            transcriptUrl = this.transcriptUrl,
            persons = this.persons?.map { cx.aswin.boxcast.core.network.model.PersonItem(name = it.name, role = it.role, img = it.img, href = it.href) },
            transcripts = this.transcripts?.map { cx.aswin.boxcast.core.network.model.TranscriptItem(url = it.url, type = it.type) }
        )
    }

    /**
     * Canonical conversion: EpisodeItem + Podcast -> Domain Episode
     * Ensures ALL podcast metadata fields are populated.
     */
    private fun EpisodeItem.toDomain(podcast: cx.aswin.boxcast.core.model.Podcast): cx.aswin.boxcast.core.model.Episode {
        return cx.aswin.boxcast.core.model.Episode(
            id = this.id.toString(),
            title = this.title,
            description = this.description ?: "",
            audioUrl = this.enclosureUrl ?: "",
            imageUrl = (this.image?.takeIf { it.isNotBlank() } ?: this.feedImage?.takeIf { it.isNotBlank() }) ?: podcast.imageUrl,
            podcastImageUrl = this.feedImage?.takeIf { it.isNotBlank() } ?: podcast.imageUrl,
            podcastTitle = podcast.title,
            podcastId = podcast.id,
            podcastGenre = podcast.genre,
            podcastArtist = podcast.artist,
            duration = this.duration ?: 0,
            publishedDate = this.datePublished ?: 0L,
            // Podcast 2.0
            chaptersUrl = this.chaptersUrl,
            transcriptUrl = this.transcriptUrl,
            persons = this.persons?.map { cx.aswin.boxcast.core.model.Person(name = it.name, role = it.role, img = it.img, href = it.href) },
            transcripts = this.transcripts?.map { cx.aswin.boxcast.core.model.Transcript(url = it.url, type = it.type) }
        )
    }
}
