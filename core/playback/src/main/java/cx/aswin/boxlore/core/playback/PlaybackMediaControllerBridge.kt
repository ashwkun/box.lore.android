package cx.aswin.boxlore.core.playback

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.media3.common.Player
import cx.aswin.boxlore.core.data.BuildConfig
import cx.aswin.boxlore.core.playback.PlaybackLifecycleSignals
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.data.mapRegionForBriefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Media3 [Player.Listener] implementation extracted from PlaybackRepository.initializeMediaController.
 */
internal class PlaybackMediaControllerBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val mediaHandle: PlaybackMediaControllerHandle,
    private val queueRepository: QueueRepository,
    private val currentSkipBehavior: () -> String,
    private val activePlaybackStartTimeMs: () -> Long,
    private val setActivePlaybackStartTimeMs: (Long) -> Unit,
    private val onPlaybackStarted: () -> Unit,
    private val startProgressTicker: () -> Unit,
    private val stopProgressTicker: () -> Unit,
    private val saveCurrentState: suspend (updateLastPlayedAt: Boolean) -> Unit,
    private val cancelSleepTimer: () -> Unit,
    private val syncQueueToDb: suspend () -> Unit,
    private val reconcileQueueWithController: () -> Unit,
    private val markEpisodeAsCompleted: suspend (Episode, Podcast?) -> Unit,
    private val findPodcastIdForEpisode: suspend (String) -> String?,
) : Player.Listener {
    private var pendingSaveJob: Job? = null

                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d("PlaybackRepo", "onIsPlayingChanged: isPlaying=$isPlaying, currentPos=${mediaHandle.controller?.currentPosition}")
                    val oldIsPlaying = playerStateFlow.value.isPlaying
                    playerStateFlow.value = playerStateFlow.value.copy(isPlaying = isPlaying)
                    if (isPlaying) {
                        pendingSaveJob?.cancel() // Cancel pending save if we resume
                        if (!oldIsPlaying) {
                            setActivePlaybackStartTimeMs(System.currentTimeMillis())
                            onPlaybackStarted()
                        }
                        startProgressTicker()
                    } else {
                        stopProgressTicker()
                        val hasBeenPlayingFor10s =
                            activePlaybackStartTimeMs() > 0 &&
                                (System.currentTimeMillis() - activePlaybackStartTimeMs() >= 10_000)
                        pendingSaveJob?.cancel()
                        pendingSaveJob =
                            scope.launch {
                                saveCurrentState(hasBeenPlayingFor10s)
                            }
                        setActivePlaybackStartTimeMs(0L)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val isLoading = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                    playerStateFlow.value = playerStateFlow.value.copy(isLoading = isLoading)

                    if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        Log.d("PlaybackRepo", "Playback ENDED. Cancelling pending saves.")
                        pendingSaveJob?.cancel() // CRITICAL: Prevent "Pause" save from overwriting completion

                        val controller = mediaHandle.controller
                        val hasMedia = controller != null && controller.mediaItemCount > 0
                        val reachedEnd =
                            controller != null &&
                                controller.duration > 0 &&
                                (
                                    controller.currentPosition >= controller.duration - 10000 ||
                                        controller.currentPosition >= controller.duration * 0.95
                                )

                        if (hasMedia && reachedEnd) {
                            // Sleep Timer: End of Episode — stop everything if EOE is active
                            if (playerStateFlow.value.sleepAtEndOfEpisode) {
                                Log.d("PlaybackRepo", "Sleep Timer (EOE): Episode ended, stopping playback.")
                                mediaHandle.controller?.stop()
                                mediaHandle.controller?.clearMediaItems()
                                cancelSleepTimer()
                                stopProgressTicker()
                                playerStateFlow.value =
                                    playerStateFlow.value.copy(
                                        isPlaying = false,
                                        position = 0,
                                        sleepTimerEnd = null,
                                        sleepAtEndOfEpisode = false,
                                    )
                                return
                            }

                            playerStateFlow.value = playerStateFlow.value.copy(isPlaying = false, position = 0)
                            stopProgressTicker()
                            // Natural completion persistence is service-owned. The history
                            // observer mirrors the resulting DB state back into PlayerState.
                        } else {
                            Log.d(
                                "PlaybackRepo",
                                "Playback ended but not naturally completed (hasMedia=$hasMedia, reachedEnd=$reachedEnd). Skipping completion marking.",
                            )
                            playerStateFlow.value = playerStateFlow.value.copy(isPlaying = false)
                            stopProgressTicker()
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("PlaybackRepo", "Player Error: ${error.message}", error)
                    val controller = mediaHandle.controller ?: return
                    val queue = playerStateFlow.value.queue
                    val failedEpisode = playerStateFlow.value.currentEpisode

                    // Try to skip the bad item instead of nuking the entire queue
                    val currentIndex = controller.currentMediaItemIndex
                    val hasNext = currentIndex < controller.mediaItemCount - 1

                    if (hasNext) {
                        // Remove the failed item and advance to the next one
                        Log.d("PlaybackRepo", "onPlayerError: Skipping failed item '${failedEpisode?.title}', advancing to next")
                        controller.removeMediaItem(currentIndex)
                        val newQueue = queue.filterNot { it.id == failedEpisode?.id }
                        playerStateFlow.value =
                            playerStateFlow.value.copy(
                                queue = newQueue,
                                isLoading = true,
                            )
                        controller.prepare()
                        controller.play()
                    } else {
                        // No more items — clear everything
                        Log.d("PlaybackRepo", "onPlayerError: No more items in queue, clearing.")
                        controller.stop()
                        controller.clearMediaItems()
                        playerStateFlow.value =
                            playerStateFlow.value.copy(
                                isPlaying = false,
                                isLoading = false,
                                currentEpisode = null,
                                queue = emptyList(),
                            )
                    }

                    scope.launch {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast
                                .makeText(
                                    context,
                                    "Stream unavailable, skipping...",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    if (mediaHandle.controller?.isPlaying == true) {
                        setActivePlaybackStartTimeMs(System.currentTimeMillis())
                    } else {
                        setActivePlaybackStartTimeMs(0L)
                    }
                    // Use mediaId to find episode — more reliable than index
                    val episodeId = mediaItem?.mediaId?.let(PlaybackMediaIdPolicy::stripMediaIdPrefixes) ?: return
                    val queue = playerStateFlow.value.queue
                    val oldState = playerStateFlow.value

                    val slotIndex = queue.indexOfFirst { it.id == episodeId }
                    android.util.Log.d(
                        "PlaybackRepo",
                        "onMediaItemTransition: mediaId=${mediaItem.mediaId}, stripped=$episodeId, slotIndex=$slotIndex, queueSize=${queue.size}, reason=$reason",
                    )

                    // Sleep Timer: End of Episode — intercept auto-advance
                    if (oldState.sleepAtEndOfEpisode && reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        Log.d("PlaybackRepo", "Sleep Timer (EOE): Auto-advance intercepted. Pausing playback.")
                        mediaHandle.controller?.pause()
                        cancelSleepTimer()
                        stopProgressTicker()
                        playerStateFlow.value =
                            playerStateFlow.value.copy(
                                isPlaying = false,
                                sleepTimerEnd = null,
                                sleepAtEndOfEpisode = false,
                            )
                        // Completion persistence and timer-holder clearing are service-owned.
                        // The history observer mirrors completion into this repository.
                        return
                    }

                    // Launch a single coroutine to handle the transition logic sequentially
                    // This ensures DB updates finish BEFORE we trigger SmartQueue refill
                    scope.launch {
                        val finalQueue: List<Episode>
                        val finalSlotIndex: Int

                        if (slotIndex == -1) {
                            android.util.Log.w(
                                "PlaybackRepo",
                                "onMediaItemTransition: Episode $episodeId NOT found in local queue. Attempting recovery from DB...",
                            )
                            val dbQueue = queueRepository.getQueueSnapshot()
                            val dbSlotIndex = dbQueue.indexOfFirst { it.id == episodeId }
                            android.util.Log.d(
                                "PlaybackRepo",
                                "onMediaItemTransition (Recovery): dbSlotIndex=$dbSlotIndex, dbQueueSize=${dbQueue.size}",
                            )

                            val resolvedEpisode =
                                if (dbSlotIndex != -1) {
                                    dbQueue[dbSlotIndex]
                                } else {
                                    // Fallback: construct Episode directly from mediaItem's MediaMetadata
                                    val metadata = mediaItem.mediaMetadata
                                    val resolvedPodcastId = findPodcastIdForEpisode(episodeId) ?: ""
                                    Episode(
                                        id = episodeId,
                                        title = metadata.title?.toString() ?: "Unknown Episode",
                                        description = "",
                                        audioUrl = mediaItem.localConfiguration?.uri?.toString() ?: "",
                                        imageUrl = metadata.artworkUri?.toString(),
                                        podcastImageUrl = metadata.artworkUri?.toString(),
                                        podcastTitle = metadata.subtitle?.toString() ?: metadata.artist?.toString() ?: "Unknown Podcast",
                                        podcastId = resolvedPodcastId,
                                        podcastGenre = metadata.genre?.toString() ?: "Podcast",
                                        podcastArtist = metadata.artist?.toString() ?: "",
                                        duration = 0,
                                        publishedDate = 0L,
                                    )
                                }

                            if (dbSlotIndex != -1) {
                                finalQueue = dbQueue
                                finalSlotIndex = dbSlotIndex
                            } else {
                                val mutable = queue.toMutableList()
                                if (mutable.none { it.id == episodeId }) {
                                    mutable.add(resolvedEpisode)
                                }
                                finalQueue = mutable.toList()
                                finalSlotIndex = finalQueue.indexOfFirst { it.id == episodeId }.coerceAtLeast(0)
                            }
                        } else {
                            finalQueue = queue
                            finalSlotIndex = slotIndex
                        }

                        var newEpisode = finalQueue[finalSlotIndex]
                        // Enrich only when URLs are missing; present ones may carry server signatures
                        if (newEpisode.id.startsWith("briefing_") &&
                            (newEpisode.chaptersUrl == null || newEpisode.transcriptUrl == null)
                        ) {
                            try {
                                val parts = newEpisode.id.split("_")
                                if (parts.size >= 3) {
                                    val region = parts[1]
                                    val date = parts[2]
                                    val audioUri = android.net.Uri.parse(newEpisode.audioUrl)
                                    val version = audioUri.getQueryParameter("v")
                                    val versionParam = if (version != null) "&v=$version" else ""
                                    val mappedRegion = mapRegionForBriefing(region)
                                    newEpisode =
                                        newEpisode.copy(
                                            chaptersUrl = "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/$mappedRegion?d=$date$versionParam",
                                            transcriptUrl = "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/transcript/$mappedRegion?d=$date$versionParam",
                                        )
                                    android.util.Log.d(
                                        "PlaybackRepo",
                                        "onMediaItemTransition: Enriched briefing episode ${newEpisode.id} with chaptersUrl=${newEpisode.chaptersUrl}",
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PlaybackRepo", "Failed to enrich transition briefing episode ${newEpisode.id}", e)
                            }
                        }
                        android.util.Log.d("PlaybackRepo", "Media transition to: ${newEpisode.title}")

                        // 1. Mark PREVIOUS episode as completed (if distinct and transition reason permits)
                        val previousEpisode = oldState.currentEpisode
                        val previousPodcast = oldState.currentPodcast
                        val isServiceOwnedNaturalAdvance =
                            previousEpisode?.id ==
                                PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId
                        if (isServiceOwnedNaturalAdvance) {
                            PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId = null
                        }

                        val shouldMarkCompleted =
                            reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
                                currentSkipBehavior() == "mark_completed_skip" &&
                                !isServiceOwnedNaturalAdvance

                        if (previousEpisode != null && previousEpisode.id != newEpisode.id && shouldMarkCompleted) {
                            android.util.Log.d(
                                "PlaybackRepo",
                                "Transition (ShouldMarkCompleted): Marking previous episode as COMPLETED: ${previousEpisode.title} (ID: ${previousEpisode.id}), reason: $reason, skipBehavior: $currentSkipBehavior()",
                            )
                            markEpisodeAsCompleted(previousEpisode, previousPodcast)
                        }

                        // 2. Derive podcast context from episode metadata & local DB
                        val newPodcast: Podcast? =
                            if (newEpisode.podcastId != null) {
                                val existingPod = oldState.currentPodcast
                                val database =
                                    cx.aswin.boxlore.core.database.BoxLoreDatabase
                                        .getDatabase(context)
                                val dbPodEntity = database.podcastDao().getPodcast(newEpisode.podcastId!!)
                                val dbPodcast =
                                    dbPodEntity?.let { entity ->
                                        cx.aswin.boxlore.core.model.Podcast(
                                            id = entity.podcastId,
                                            title = entity.title,
                                            artist = entity.author,
                                            imageUrl = entity.imageUrl,
                                            fallbackImageUrl = entity.latestEpisode?.imageUrl ?: "",
                                            description = entity.description,
                                            genre = entity.genre ?: "Podcast",
                                            type = entity.type,
                                            latestEpisode = entity.latestEpisode,
                                            subscribedAt = entity.subscribedAt,
                                            podcastGuid = entity.podcastGuid,
                                            fundingUrl = entity.fundingUrl,
                                            fundingMessage = entity.fundingMessage,
                                            medium = entity.medium,
                                            hasValue = entity.hasValue,
                                            updateFrequency = entity.updateFrequency,
                                            location = entity.location,
                                            license = entity.license,
                                            isLocked = entity.isLocked,
                                            preferredSort = entity.preferredSort,
                                        )
                                    }

                                if (dbPodcast != null && dbPodcast.title != "Unknown Podcast") {
                                    dbPodcast
                                } else if (existingPod != null &&
                                    existingPod.id == newEpisode.podcastId &&
                                    existingPod.title != "Unknown Podcast"
                                ) {
                                    // Preserve the fully-populated existing podcast object
                                    existingPod
                                } else {
                                    cx.aswin.boxlore.core.model.Podcast(
                                        id = newEpisode.podcastId!!,
                                        title =
                                            newEpisode.podcastTitle?.takeIf { !it.isNullOrBlank() && it != "Unknown Podcast" }
                                                ?: "Unknown Podcast",
                                        artist = newEpisode.podcastArtist?.takeIf { it.isNotEmpty() } ?: existingPod?.artist ?: "",
                                        imageUrl =
                                            newEpisode.podcastImageUrl?.takeIf {
                                                it.isNotEmpty()
                                            } ?: existingPod?.imageUrl ?: "",
                                        description = null,
                                        genre = newEpisode.podcastGenre ?: existingPod?.genre ?: "Podcast",
                                    )
                                }
                            } else {
                                oldState.currentPodcast
                            }

                        // 3. QUEUE CONSUMPTION: Drop items before current
                        val newQueue = finalQueue.drop(finalSlotIndex)
                        android.util.Log.d("PlaybackRepo", "Consuming queue: Dropped $finalSlotIndex items. New size: ${newQueue.size}")
                        playerStateFlow.value =
                            playerStateFlow.value.copy(
                                currentEpisode = newEpisode,
                                currentPodcast = newPodcast,
                                queue = newQueue,
                            )

                        // 4. Sync queue to DB for restart recovery
                        syncQueueToDb()

                        // Resume/intro seeking is owned exclusively by BoxLorePlaybackService.
                        // A controller transition must never apply a second seek.

                        // NOTE: auto-refill is owned exclusively by BoxLorePlaybackService's
                        // transition listener (single guarded path, works with UI closed).
                        // Items it appends are picked up by onTimelineChanged below.
                    }
                }

                override fun onTimelineChanged(
                    timeline: androidx.media3.common.Timeline,
                    reason: Int,
                ) {
                    // The playback service (auto-refill, Android Auto) can append items
                    // directly to the player. Reconcile the in-memory queue whenever the
                    // playlist changes so the UI stays in sync.
                    if (reason == androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                        reconcileQueueWithController()
                    }
                }
}
