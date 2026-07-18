package cx.aswin.boxlore.core.data.playback

import android.util.Log
import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.data.PlayerState
import cx.aswin.boxlore.core.data.database.ListeningHistoryDao
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resume / skip transport helpers for [cx.aswin.boxlore.core.data.PlaybackRepository].
 */
internal class PlaybackTransportHelper(
    private val scope: CoroutineScope,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val mediaHandle: PlaybackMediaControllerHandle,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val storePendingEntryPoint: (android.os.Bundle?) -> Unit,
    private val playQueue: suspend (
        episodes: List<Episode>,
        podcast: Podcast,
        startIndex: Int,
        entryPoint: PlaybackEntryPoint,
        initialPositionMs: Long?,
        sourceContext: android.os.Bundle?,
    ) -> Unit,
) {
    fun resume(entryPointContext: android.os.Bundle? = null) {
        val controller = mediaHandle.controller ?: return

        Log.d("PlaybackRepo", "resume() called: mediaItemCount=${controller.mediaItemCount}, statePos=${playerStateFlow.value.position}")

        // Attribute the resume so playback_started isn't logged as "not set". An explicit
        // source (e.g. a screen that set PendingEntryPoint just before) always wins via
        // setIfAbsent; otherwise we tag the surface this resume came from.
        val hasExplicitSource = entryPointContext?.getString("entry_point") != null

        fun applyResumeSource(default: String) {
            if (hasExplicitSource) {
                storePendingEntryPoint(entryPointContext)
            } else {
                cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.setIfAbsent(
                    mapOf("entry_point" to default),
                )
            }
        }

        // If controller has no media but we have state, reload the FULL queue
        if (controller.mediaItemCount == 0 && playerStateFlow.value.currentEpisode != null) {
            val queue = playerStateFlow.value.queue
            val currentEpisode = playerStateFlow.value.currentEpisode!!
            val podcast = playerStateFlow.value.currentPodcast
            val savedPosition = playerStateFlow.value.position

            Log.d("PlaybackRepo", "resume(): Controller empty, reloading full queue (${queue.size} items)")

            scope.launch {
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                    .setSeekSource("resume")
                if (queue.isNotEmpty() && podcast != null) {
                    // Find current episode in queue and reload from that point
                    val startIndex = queue.indexOfFirst { it.id == currentEpisode.id }.coerceAtLeast(0)
                    Log.d("PlaybackRepo", "resume(): Reloading queue from index $startIndex with position=$savedPosition")

                    val mediaItems =
                        queue.map { episode ->
                            val metadata =
                                androidx.media3.common.MediaMetadata
                                    .Builder()
                                    .setTitle(episode.title)
                                    .setArtist(episode.podcastTitle ?: podcast.title)
                                    .setArtworkUri(
                                        android.net.Uri.parse(
                                            episode.imageUrl?.takeIf { it.isNotBlank() }
                                                ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() }
                                                ?: podcast.imageUrl,
                                        ),
                                    ).setDisplayTitle(episode.title)
                                    .setSubtitle(episode.podcastTitle ?: podcast.title)
                                    .setGenre(episode.podcastGenre ?: podcast.genre)
                                    .setExtras(entryPointContext)
                                    .build()

                            MediaItem
                                .Builder()
                                .setUri(episode.audioUrl)
                                .setMediaMetadata(metadata)
                                .setMediaId(episode.id)
                                .setCustomCacheKey(episode.id)
                                .build()
                        }

                    controller.setMediaItems(mediaItems, startIndex, savedPosition.coerceAtLeast(0L))
                    controller.prepare()
                    applyResumeSource("resume_restore")
                    controller.play()
                } else {
                    // Fallback: single episode resume (no queue available)
                    Log.d("PlaybackRepo", "resume(): No queue, loading single episode")
                    val metadata =
                        androidx.media3.common.MediaMetadata
                            .Builder()
                            .setTitle(currentEpisode.title)
                            .setArtist(podcast?.title ?: "")
                            .setArtworkUri(
                                android.net.Uri.parse(currentEpisode.imageUrl?.takeIf { it.isNotBlank() } ?: podcast?.imageUrl ?: ""),
                            ).setDisplayTitle(currentEpisode.title)
                            .setSubtitle(podcast?.title ?: "")
                            .setGenre(currentEpisode.podcastGenre ?: podcast?.genre ?: "Podcast")
                            .setExtras(entryPointContext)
                            .build()

                    val mediaItem =
                        MediaItem
                            .Builder()
                            .setUri(currentEpisode.audioUrl)
                            .setMediaMetadata(metadata)
                            .setMediaId(currentEpisode.id)
                            .setCustomCacheKey(currentEpisode.id)
                            .build()

                    controller.setMediaItem(mediaItem, savedPosition.coerceAtLeast(0L))
                    controller.prepare()
                    applyResumeSource("resume_restore")
                    controller.play()
                }
            }
        } else {
            Log.d("PlaybackRepo", "resume(): Media exists, just calling play()")
            applyResumeSource("resume_player")
            controller.play()
        }
    }

    fun reinitializePlaybackIfEmpty(
        controller: androidx.media3.session.MediaController,
        index: Int,
        entryPoint: PlaybackEntryPoint,
        sourceContext: android.os.Bundle? = null,
    ): Boolean {
        if (controller.mediaItemCount == 0 && playerStateFlow.value.queue.isNotEmpty()) {
            android.util.Log.d("PlaybackRepo", "skipToEpisode: Controller empty but local queue exists. Re-initializing playback.")
            val queue = playerStateFlow.value.queue
            val podcast = playerStateFlow.value.currentPodcast

            if (index in queue.indices && podcast != null) {
                scope.launch {
                    playQueue(queue, podcast, index, entryPoint, null, sourceContext)
                }
                return true
            }
        }
        return false
    }

    fun restorePositionAndSeek(
        controller: androidx.media3.session.MediaController,
        targetEpisodeId: String,
        mediaIndex: Int,
    ) {
        scope.launch {
            val saved = listeningHistoryDao.getHistoryItem(targetEpisodeId)
            val savedPosMs =
                if (saved != null && !saved.isCompleted && saved.progressMs > 2000) {
                    android.util.Log.d("PlaybackRepo", "skipToEpisode: Restoring saved position ${saved.progressMs}ms for $targetEpisodeId")
                    saved.progressMs
                } else {
                    0L
                }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                controller.seekTo(mediaIndex, savedPosMs)
                controller.play()
            }
        }
    }

    fun skipToEpisode(
        index: Int,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        sourceContext: android.os.Bundle? = null,
    ) {
        val controller = mediaHandle.controller
        android.util.Log.d(
            "PlaybackRepo",
            "skipToEpisode: index=$index, controller=${controller != null}, mediaItemCount=${controller?.mediaItemCount ?: -1}",
        )

        if (controller == null) {
            android.util.Log.e("PlaybackRepo", "skipToEpisode: mediaHandle.controller is NULL!")
            return
        }

        val entryPointContext =
            sourceContext?.takeIf { it.getString("entry_point") != null }
                ?: if (entryPoint != PlaybackEntryPoint.GENERIC) {
                    android.os.Bundle().apply {
                        putString("entry_point", entryPoint.name.lowercase())
                    }
                } else {
                    null
                }

        if (reinitializePlaybackIfEmpty(controller, index, entryPoint, entryPointContext)) {
            return
        }

        val targetEpisode = playerStateFlow.value.queue.getOrNull(index)
        if (targetEpisode != null) {
            for (i in 0 until controller.mediaItemCount) {
                if (PlaybackMediaIdPolicy.stripMediaIdPrefixes(controller.getMediaItemAt(i).mediaId) == targetEpisode.id) {
                    android.util.Log.d("PlaybackRepo", "skipToEpisode: Found mediaId=${targetEpisode.id} at Media3 index $i")

                    storePendingEntryPoint(entryPointContext)

                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                        .setSeekSource("transition")
                    restorePositionAndSeek(controller, targetEpisode.id, i)
                    return
                }
            }
            android.util.Log.e("PlaybackRepo", "skipToEpisode: mediaId=${targetEpisode.id} NOT found in Media3!")
        } else {
            android.util.Log.e("PlaybackRepo", "skipToEpisode: index $index out of bounds for queue size ${playerStateFlow.value.queue.size}!")
        }
    }

    fun skipToNextEpisode() {
        val currentEpisodeId = playerStateFlow.value.currentEpisode?.id ?: return
        val currentIndex = playerStateFlow.value.queue.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex != -1 && currentIndex < playerStateFlow.value.queue.size - 1) {
            skipToEpisode(currentIndex + 1)
        }
    }

    fun skipToPreviousEpisode() {
        val currentEpisodeId = playerStateFlow.value.currentEpisode?.id ?: return
        val currentIndex = playerStateFlow.value.queue.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex > 0) {
            skipToEpisode(currentIndex - 1)
        }
    }
}
