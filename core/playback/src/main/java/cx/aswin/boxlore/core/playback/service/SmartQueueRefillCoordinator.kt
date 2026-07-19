package cx.aswin.boxlore.core.playback.service

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueEngine
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.EpisodeItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SmartQueueRefillCoordinator(
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
    private val queueRepository: QueueRepository,
    private val smartQueueEngine: SmartQueueEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val findPodcastIdForEpisode: suspend (String) -> String?,
    private val queueMaxSize: Int,
    private val mediaIdPrefixStripper: (String) -> String,
) {
    suspend fun refillQueue(player: ExoPlayer) {
        val currentItem = player.currentMediaItem ?: return
        // Extract episode info — strip any prefix for consistent ID format
        val episodeId = mediaIdPrefixStripper(currentItem.mediaId)
        val metadata = currentItem.mediaMetadata

        Log.d("AutoQueue", "Refilling from: ${metadata.title}, episodeId=$episodeId")

        // Enforce queue cap
        if (player.mediaItemCount >= queueMaxSize) {
            Log.d("AutoQueue", "Queue at max capacity ($queueMaxSize). Skipping refill.")
            return
        }

        // Get podcast context (check DB first, then fallback to API).
        // Briefings have no feed entry; synthesize a minimal podcast so the engine can
        // run its fallback tiers (it detects the briefing_ prefix itself).
        val isBriefing = episodeId.startsWith("briefing_")
        val podcastId = if (isBriefing) episodeId else findPodcastIdForEpisode(episodeId) ?: return

        val podcastEntity = if (isBriefing) null else database.podcastDao().getPodcast(podcastId)
        val podcast =
            when {
                podcastEntity != null ->
                    Podcast(
                        id = podcastEntity.podcastId,
                        title = podcastEntity.title,
                        artist = podcastEntity.author,
                        imageUrl = podcastEntity.imageUrl,
                        description = podcastEntity.description,
                        genre = podcastEntity.genre ?: "Podcast",
                        type = podcastEntity.type,
                        preferredSort = podcastEntity.preferredSort,
                    )
                isBriefing ->
                    Podcast(
                        id = "briefing_daily",
                        title = metadata.subtitle?.toString() ?: "Daily Briefing",
                        artist = "",
                        imageUrl = metadata.artworkUri?.toString() ?: "",
                        genre = "News",
                    )
                // Fallback to API if not in local DB (e.g. unsubscribed podcast from history)
                else -> podcastRepository.getPodcastDetails(podcastId) ?: return
            }

        // Build the EpisodeItem for SmartQueueEngine
        val currentEpisodeItem =
            EpisodeItem(
                id = episodeId.toLongOrNull() ?: 0L,
                title = metadata.title?.toString() ?: "",
                description = "",
                enclosureUrl = currentItem.localConfiguration?.uri?.toString(),
                image = metadata.artworkUri?.toString(),
                feedImage = podcast.imageUrl,
            )

        // Everything already in the player is off-limits for the engine.
        val existingIds =
            withContext(mainDispatcher) {
                (0 until player.mediaItemCount)
                    .map {
                        mediaIdPrefixStripper(player.getMediaItemAt(it).mediaId)
                    }.toSet()
            }

        Log.d(
            "AutoQueue",
            "Refill context: podcastId=${podcast.id}, type=${podcast.type}, " +
                "preferredSort=${podcastEntity?.preferredSort ?: podcast.preferredSort}, genre=${podcast.genre}",
        )
        val currentContextSourceId = database.queueDao().getQueueItemByEpisodeId(episodeId)?.contextSourceId
        val nextEntries =
            withContext(ioDispatcher) {
                smartQueueEngine.getNextEpisodes(
                    currentEpisode = currentEpisodeItem,
                    podcast = podcast,
                    preferredSort = podcastEntity?.preferredSort,
                    excludeEpisodeIds = existingIds,
                    currentContextSourceId = currentContextSourceId,
                )
            }
        Log.d(
            "AutoQueue",
            "SmartQueue returned ${nextEntries.size} episodes: ${nextEntries.groupingBy { it.source }.eachCount()}",
        )
        if (nextEntries.isEmpty()) {
            Log.w(
                "AutoQueue",
                "SmartQueue returned no candidates for podcastId=${podcast.id}, episodeId=$episodeId",
            )
            return
        }

        // Respect the queue cap when appending the batch.
        val room = (queueMaxSize - player.mediaItemCount).coerceAtLeast(0)
        val entriesToAdd =
            nextEntries
                .filter { it.episode.id.toString() !in existingIds }
                .take(room)
        if (entriesToAdd.isEmpty()) return

        // Persist FIRST so PlaybackRepository's timeline reconciliation finds the rows
        // (with contextType/source for queue-sheet labels) when the player callback fires.
        entriesToAdd.forEach { entry ->
            try {
                queueRepository.addToQueue(
                    episode = entry.episode,
                    podcast = entry.podcast,
                    contextType = "AUTO_FILL",
                    contextSourceId = entry.source,
                )
            } catch (e: Exception) {
                Log.e("AutoQueue", "Failed to persist queue item: ${entry.episode.title}", e)
            }
        }

        val refilledEpisodeIds = mutableListOf<String>()
        val recommendationSources = mutableListOf<String>()

        // Add to player queue on main thread
        withContext(mainDispatcher) {
            entriesToAdd.forEach { entry ->
                val ep = entry.episode
                val pod = entry.podcast
                val epIdStr = ep.id.toString()

                val finalImageUrl = ep.image ?: ep.feedImage ?: pod.imageUrl
                val artworkUri = finalImageUrl.let { Uri.parse(it) }

                // Use raw ID — same format as PlaybackRepository (L1 fix)
                val mediaItem =
                    MediaItem
                        .Builder()
                        .setMediaId(epIdStr)
                        .setUri(ep.enclosureUrl ?: "")
                        .setMediaMetadata(
                            MediaMetadata
                                .Builder()
                                .setTitle(ep.title)
                                .setSubtitle(pod.title)
                                .setArtist(pod.artist)
                                .setArtworkUri(artworkUri)
                                .setDisplayTitle(ep.title)
                                .setGenre(pod.genre)
                                .setIsPlayable(true)
                                .setIsBrowsable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                .build(),
                        ).build()

                player.addMediaItem(mediaItem)
                refilledEpisodeIds.add(epIdStr)
                recommendationSources.add(entry.source)
            }
            Log.d("AutoQueue", "Added ${refilledEpisodeIds.size} items. Queue now: ${player.mediaItemCount}")
        }

        if (refilledEpisodeIds.isNotEmpty()) {
            val region =
                try {
                    userPreferencesRepository.regionStream.first()
                } catch (e: Exception) {
                    null
                }
            val sourceCounts = recommendationSources.groupingBy { it }.eachCount()
            AnalyticsHelper.trackSmartQueueRefilled(
                AnalyticsHelper.SmartQueueRefillEvent(
                    triggeringEpisodeId = episodeId,
                    triggeringPodcastGenre = podcast.genre,
                    refilledCount = refilledEpisodeIds.size,
                    recommendationSources = recommendationSources.distinct(),
                    refilledEpisodeIds = refilledEpisodeIds,
                    region = region,
                    sourceCounts = sourceCounts,
                    usedServerRecommendations =
                        SmartQueueEngine.SOURCE_PERSONALIZED_REC in sourceCounts ||
                            SmartQueueEngine.SOURCE_SERVER_REC in sourceCounts,
                ),
            )
        }
    }
}
