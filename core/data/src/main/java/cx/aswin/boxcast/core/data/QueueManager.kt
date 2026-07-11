package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.network.model.EpisodeItem
import cx.aswin.boxcast.core.model.PlaybackEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val queueRepository: QueueRepository,
    private val playbackRepository: PlaybackRepository
) {
    private val TAG = "QueueManager"
    private val scope = CoroutineScope(Dispatchers.Main)

    // NOTE: auto-refill is intentionally NOT handled here. BoxLorePlaybackService owns the
    // single guarded refill path (it works with the UI closed and can't race a second
    // trigger); this class only orchestrates explicit user actions.

    /**
     * Maps the rich "entry_point" string carried in the source bundle back to the coarse
     * [PlaybackEntryPoint] enum, which drives mixtape reset logic. The full string is still
     * forwarded separately as sourceContext so analytics keeps the precise origin (e.g.
     * "episode_info_screen", "home_hero_new_episodes_grid") instead of collapsing to GENERIC.
     */
    private fun resolveEntryPoint(entryPointContext: android.os.Bundle?): PlaybackEntryPoint {
        return when (entryPointContext?.getString("entry_point")) {
            "home_mixtape" -> PlaybackEntryPoint.HOME_MIXTAPE
            "learn" -> PlaybackEntryPoint.LEARN
            else -> PlaybackEntryPoint.GENERIC
        }
    }

    fun playEpisode(episode: EpisodeItem, podcast: cx.aswin.boxcast.core.model.Podcast?, preferredSort: String? = null, entryPointContext: android.os.Bundle? = null) {
        scope.launch {
            android.util.Log.d(TAG, "playEpisode called: ${episode.title}, sort=$preferredSort")
            
            if (podcast != null) {
                // 1. Clear current queue for a fresh start
                queueRepository.clearQueue()
                
                // 2. Add selected episode & Persist
                queueRepository.addToQueue(episode, podcast)
                
                // 3. Start playback IMMEDIATELY with just the current episode
                // The playback service auto-fills more episodes when the queue runs low
                val domainEpisode = episode.toDomain(podcast)
                val entryPoint = resolveEntryPoint(entryPointContext)
                playbackRepository.playQueue(listOf(domainEpisode), podcast, 0, entryPoint, sourceContext = entryPointContext)
            } else {
                 android.util.Log.e(TAG, "Podcast is null!")
            }
        }
    }

    fun addToQueue(
        episode: EpisodeItem,
        podcast: cx.aswin.boxcast.core.model.Podcast?,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC
    ) {
        android.util.Log.d(TAG, "addToQueue called: episodeId=${episode.id}, title=${episode.title}, podcast=${podcast?.title}, entryPoint=$entryPoint")
        scope.launch {
            if (podcast != null) {
                // Lore items carry their own contextType so queue-type detection is
                // robust across restarts (see Lore queue independence).
                val contextType = if (entryPoint == PlaybackEntryPoint.LEARN) QueueMath.CONTEXT_TYPE_LORE else "MANUAL"

                // Persist
                queueRepository.addToQueue(episode, podcast, contextType = contextType)
                
                // Add to Player (carry provenance so the queue sheet can label the row)
                val domainEpisode = episode.toDomain(podcast).copy(contextType = contextType)
                playbackRepository.addToQueue(domainEpisode, podcast, entryPoint)
                android.util.Log.d(TAG, "addToQueue: Complete. Current queue size: ${playbackRepository.playerState.value.queue.size}")
            } else {
                android.util.Log.e(TAG, "addToQueue: Podcast is null, ignoring!")
            }
        }
    }
    
    fun addToQueue(
        episode: cx.aswin.boxcast.core.model.Episode,
        podcast: cx.aswin.boxcast.core.model.Podcast?,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC
    ) {
        val item = episode.toEpisodeItem(podcast)
        addToQueue(item, podcast, entryPoint)
    }

    // Overload for Domain Objects (UI)
    fun playEpisode(
        episode: cx.aswin.boxcast.core.model.Episode,
        podcast: cx.aswin.boxcast.core.model.Podcast?,
        preferredSort: String? = null,
        entryPointContext: android.os.Bundle? = null,
        initialPositionMs: Long? = null
    ) {
        val currentQueue = playbackRepository.playerState.value.queue
        val currentPodcast = playbackRepository.playerState.value.currentPodcast
        val entryPoint = resolveEntryPoint(entryPointContext)

        if (episode.id.startsWith("briefing_") && podcast != null) {
            scope.launch {
                android.util.Log.d(TAG, "playEpisode briefing intercepted: ${episode.id}")
                queueRepository.clearQueue()
                queueRepository.replaceQueue(listOf(episode))
                playbackRepository.playQueue(listOf(episode), podcast, 0, entryPoint, initialPositionMs, sourceContext = entryPointContext)
            }
            return
        }

        // Check if we can just skip to the episode in the existing queue
        if (podcast != null && currentPodcast?.id == podcast.id) {
             val index = currentQueue.indexOfFirst { it.id == episode.id }
             if (index != -1) {
                 playbackRepository.skipToEpisode(index, entryPoint, sourceContext = entryPointContext)
                 return
             }
        }

        val item = episode.toEpisodeItem(podcast)
        playEpisode(item, podcast, preferredSort, entryPointContext)
    }

    fun playEpisodes(episodes: List<cx.aswin.boxcast.core.model.Episode>, fallbackPodcast: cx.aswin.boxcast.core.model.Podcast, startIndex: Int = 0) {
        scope.launch {
            android.util.Log.d(TAG, "playEpisodes called: count=${episodes.size}, startIndex=$startIndex")
            if (episodes.isNotEmpty()) {
                queueRepository.clearQueue()
                queueRepository.replaceQueue(episodes)
                playbackRepository.playQueue(episodes, fallbackPodcast, startIndex)
            }
        }
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
            transcripts = this.transcripts?.map { cx.aswin.boxcast.core.network.model.TranscriptItem(url = it.url, type = it.type) },
            episodeType = this.episodeType,
            enclosureType = this.enclosureType
        )
    }

    /**
     * Canonical conversion: EpisodeItem + Podcast -> Domain Episode
     * Ensures ALL podcast metadata fields are populated.
     */
    private fun EpisodeItem.toDomain(podcast: cx.aswin.boxcast.core.model.Podcast): cx.aswin.boxcast.core.model.Episode {
        val resolvedTranscriptUrl = this.transcripts?.firstOrNull { 
            it.type == "application/srt" || 
            it.type == "text/vtt" || 
            it.type == "application/x-subrip" ||
            it.url.contains(".srt", ignoreCase = true) ||
            it.url.contains(".vtt", ignoreCase = true)
        }?.url
        ?: this.transcriptUrl?.takeIf { 
            it.contains(".srt", ignoreCase = true) || 
            it.contains(".vtt", ignoreCase = true) 
        }
        ?: this.transcriptUrl
        ?: this.transcripts?.firstOrNull()?.url
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
            transcriptUrl = resolvedTranscriptUrl,
            persons = this.persons?.map { cx.aswin.boxcast.core.model.Person(name = it.name, role = it.role, img = it.img, href = it.href) },
            transcripts = this.transcripts?.map { cx.aswin.boxcast.core.model.Transcript(url = it.url, type = it.type) },
            episodeType = this.episodeType,
            enclosureType = this.enclosureType
        )
    }
}
