package cx.aswin.boxlore.core.data.playback

import android.content.SharedPreferences
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cx.aswin.boxlore.core.data.PlayerState
import cx.aswin.boxlore.core.data.QueueMath
import cx.aswin.boxlore.core.data.QueueRepository
import cx.aswin.boxlore.core.data.QueueSkipMemory
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Snapshot of a removed queue item, returned so the UI can offer Undo and so the
 * skip signal (analytics + skip memory) can be deferred until the undo window lapses.
 */
data class RemovedQueueItem(
    val episode: Episode,
    val queueIndex: Int,
    val mediaIndex: Int,
    val contextType: String?,
    val contextSourceId: String?,
)

/**
 * Queue play / mutate / reconcile APIs for [cx.aswin.boxlore.core.data.PlaybackRepository].
 */
internal class PlaybackQueueCoordinator(
    private val scope: CoroutineScope,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val mediaHandle: PlaybackMediaControllerHandle,
    private val queueRepository: QueueRepository,
    private val rankingFeedbackRepository: RankingFeedbackRepository,
    private val queueSkipMemory: QueueSkipMemory,
    private val prefs: SharedPreferences,
    private val playerDismissedKey: String,
    private val queueMaxSize: Int,
    private val checkSavedProgress: suspend (String?, Long?, PlaybackEntryPoint) -> Pair<Long, Boolean>,
    private val onPlaybackStarted: () -> Unit,
    private val storePendingEntryPoint: (android.os.Bundle?) -> Unit,
    private val saveCurrentState: suspend (updateLastPlayedAt: Boolean) -> Unit,
    private val stopProgressTicker: () -> Unit,
) {
    suspend fun syncQueueToDb() {
        try {
            val currentQueue = playerStateFlow.value.queue
            queueRepository.replaceQueue(currentQueue)
            android.util.Log.d("PlaybackRepo", "syncQueueToDb: Synced ${currentQueue.size} items to DB")
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "syncQueueToDb: Failed", e)
        }
    }

    /**
     * Rebuilds PlayerState.queue from the controller's playlist when the two diverge —
     * e.g. after the playback service auto-refilled the queue or Android Auto appended
     * items directly to the player. Episode metadata is resolved from the in-memory
     * queue first, then the persisted queue rows (which carry contextType/source for
     * the queue-sheet labels), then the MediaItem itself as a last resort.
     */
    fun reconcileQueueWithController() {
        val controller = mediaHandle.controller ?: return
        if (controller.mediaItemCount == 0) return

        removeDuplicateUpcomingItems(controller)
        val orderedIds = controller.upcomingEpisodeIds()
        if (orderedIds == playerStateFlow.value.queue.map { it.id }) return

        scope.launch {
            reconcileQueueSnapshot(orderedIds)
        }
    }

    fun removeDuplicateUpcomingItems(controller: Player) {
        val seenIds = mutableSetOf<String>()
        val duplicateIndices = mutableListOf<Int>()
        for (index in controller.currentMediaItemIndex.coerceAtLeast(0) until controller.mediaItemCount) {
            val id = QueueMath.stripMediaIdPrefixes(controller.getMediaItemAt(index).mediaId)
            if (!seenIds.add(id)) duplicateIndices += index
        }
        if (duplicateIndices.isEmpty()) return

        android.util.Log.w(
            "PlaybackRepo",
            "reconcileQueueWithController: Removing ${duplicateIndices.size} duplicate media items",
        )
        duplicateIndices.asReversed().forEach(controller::removeMediaItem)
    }

    private fun Player.upcomingEpisodeIds(): List<String> {
        val start = currentMediaItemIndex.coerceAtLeast(0)
        return (start until mediaItemCount).map { index ->
            QueueMath.stripMediaIdPrefixes(getMediaItemAt(index).mediaId)
        }
    }

    suspend fun reconcileQueueSnapshot(orderedIds: List<String>) {
        var dbItems = loadPersistedQueueById()

        // The service persists refill rows just before appending to the player; give
        // a slow write one retry before falling back to bare MediaItem metadata.
        val knownNow = playerStateFlow.value.queue.associateBy { it.id }
        if (orderedIds.any { it !in knownNow && it !in dbItems }) {
            kotlinx.coroutines.delay(400)
            dbItems = loadPersistedQueueById(dbItems)
        }

        // Re-read the controller: the playlist may have changed again meanwhile.
        val controllerNow = mediaHandle.controller ?: return
        if (controllerNow.mediaItemCount == 0) return
        val startNow = controllerNow.currentMediaItemIndex.coerceAtLeast(0)
        val idsNow = controllerNow.upcomingEpisodeIds()
        val latestQueue = playerStateFlow.value.queue
        if (idsNow == latestQueue.map { it.id }) return

        val known = latestQueue.associateBy { it.id }
        val newQueue =
            idsNow
                .mapIndexed { offset, id ->
                    val currentEpisode = known[id]
                    val persistedEpisode = dbItems[id]
                    when {
                        currentEpisode != null -> currentEpisode
                        persistedEpisode != null -> persistedEpisode
                        else -> buildEpisodeFromMediaItem(controllerNow.getMediaItemAt(startNow + offset), id)
                    }
                }.distinctBy { it.id }
        android.util.Log.d("PlaybackRepo", "reconcileQueueWithController: ${latestQueue.size} -> ${newQueue.size} items")
        playerStateFlow.value = playerStateFlow.value.copy(queue = newQueue)
        syncQueueToDb()
    }

    suspend fun loadPersistedQueueById(fallback: Map<String, Episode> = emptyMap()): Map<String, Episode> =
        try {
            queueRepository.getQueueSnapshot().associateBy { it.id }
        } catch (exception: kotlinx.coroutines.CancellationException) {
            throw exception
        } catch (exception: Exception) {
            android.util.Log.w("PlaybackRepo", "Unable to read persisted queue snapshot", exception)
            fallback
        }

    fun buildEpisodeFromMediaItem(
        item: MediaItem,
        episodeId: String,
    ): Episode {
        val metadata = item.mediaMetadata
        return Episode(
            id = episodeId,
            title = metadata.title?.toString() ?: "Episode",
            description = "",
            audioUrl = item.localConfiguration?.uri?.toString() ?: "",
            imageUrl = metadata.artworkUri?.toString(),
            podcastImageUrl = metadata.artworkUri?.toString(),
            podcastTitle = metadata.subtitle?.toString() ?: metadata.artist?.toString(),
            podcastArtist = metadata.artist?.toString(),
            podcastGenre = metadata.genre?.toString(),
            duration = 0,
            publishedDate = 0L,
            // Items we didn't add locally were appended by the service refill path.
            contextType = "AUTO_FILL",
        )
    }

    fun buildMediaItems(
        episodes: List<Episode>,
        podcast: Podcast,
        entryPointContext: android.os.Bundle?,
    ): List<MediaItem> {
        val entryPoint = PlaybackMediaIdPolicy.parseEntryPointString(entryPointContext)
        val isLearn = PlaybackMediaIdPolicy.isLearnEntryPoint(entryPoint)
        return episodes.map { episode ->
            val resolvedUrl = PlaybackArtworkResolver.resolveEpisodeImageUrl(episode, podcast)
            Log.d(
                "PlaybackRepo",
                "playQueue: epId=${episode.id}, title='${episode.title}', resolvedImageUrl='$resolvedUrl', isLearn=$isLearn",
            )
            val metadata =
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(episode.title)
                    .setArtist(episode.podcastTitle ?: podcast.title)
                    .setArtworkUri(android.net.Uri.parse(resolvedUrl))
                    .setDisplayTitle(episode.title)
                    .setSubtitle(episode.podcastTitle ?: podcast.title)
                    .setGenre(episode.podcastGenre ?: podcast.genre)
                    .setExtras(entryPointContext)
                    .build()

            val mediaId = PlaybackMediaIdPolicy.encodeMediaId(episode.id, isLearn)
            MediaItem
                .Builder()
                .setUri(episode.audioUrl)
                .setMediaMetadata(metadata)
                .setMediaId(mediaId)
                .setCustomCacheKey(episode.id)
                .build()
        }
    }

    suspend fun playQueue(
        episodes: List<Episode>,
        podcast: Podcast,
        startIndex: Int = 0,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        initialPositionMs: Long? = null,
        sourceContext: android.os.Bundle? = null,
    ) {
        Log.d("PlaybackRepo", "playQueue() called: count=${episodes.size}, start=$startIndex, podcastGenre='${podcast.genre}'")
        val requestedStartId = episodes.getOrNull(startIndex)?.id
        val uniqueEpisodes = episodes.distinctBy { it.id }
        val uniqueStartIndex =
            requestedStartId
                ?.let { id -> uniqueEpisodes.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: 0
        if (uniqueEpisodes.size != episodes.size) {
            Log.w(
                "PlaybackRepo",
                "playQueue: Removed ${episodes.size - uniqueEpisodes.size} duplicate episode IDs",
            )
        }

        prefs.edit().putBoolean(playerDismissedKey, false).apply()

        if (mediaHandle.controller == null) {
            mediaHandle.controller = mediaHandle.future?.await()
        }

        mediaHandle.controller?.let { controller ->
            // A rich source bundle (e.g. "episode_info_screen", "home_hero_*") always wins.
            // Otherwise fall back to a bundle derived from the coarse enum. Note the key must
            // be "entry_point" — the playback service only reads that key.
            val entryPointContext =
                sourceContext?.takeIf { it.getString("entry_point") != null }
                    ?: if (entryPoint != PlaybackEntryPoint.GENERIC) {
                        android.os.Bundle().apply {
                            putString("entry_point", entryPoint.name.lowercase())
                        }
                    } else {
                        null
                    }
            val mediaItems = buildMediaItems(uniqueEpisodes, podcast, entryPointContext)

            val startEpisodeId = uniqueEpisodes.getOrNull(uniqueStartIndex)?.id
            val (startPosMs, initialLikeState) = checkSavedProgress(startEpisodeId, initialPositionMs, entryPoint)

            val currentEp = uniqueEpisodes.getOrNull(uniqueStartIndex)
            if (currentEp != null) {
                // playQueue optimistically flips isPlaying=true here, ahead of the real
                // MediaController callback, so the onIsPlayingChanged edge-trigger below
                // won't see a false->true transition for this path. Trigger explicitly.
                val wasPlaying = playerStateFlow.value.isPlaying
                playerStateFlow.value =
                    playerStateFlow.value.copy(
                        currentEpisode = currentEp,
                        currentPodcast = podcast,
                        isPlaying = true,
                        position = startPosMs,
                        duration = currentEp.duration.toLong() * 1000,
                        queue = uniqueEpisodes,
                        isLiked = initialLikeState,
                    )
                if (!wasPlaying) {
                    onPlaybackStarted()
                }
            }

            if (startPosMs > 0L) {
                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                    .setSeekSource("resume")
            }
            controller.setMediaItems(mediaItems, uniqueStartIndex, startPosMs)
            controller.prepare()

            storePendingEntryPoint(entryPointContext)

            controller.play()
            syncQueueToDb()
            saveCurrentState(false)
        }
    }

    suspend fun addToQueue(
        episode: Episode,
        podcast: Podcast,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    ) {
        Log.d("PlaybackRepo", "addToQueue called: episodeId=${episode.id}, title=${episode.title}, entryPoint=$entryPoint")

        // Prevent Duplicates in active queue (by ID)
        if (playerStateFlow.value.queue.any { it.id == episode.id }) {
            Log.w("PlaybackRepo", "addToQueue: Episode ${episode.title} already in active queue (ID match). Skipping.")
            return
        }

        // ID-based dedup is sufficient — title matching is too fragile
        // (podcasts reuse titles like "Bonus Episode", "Q&A", etc.)

        // Enforce queue size cap
        if (playerStateFlow.value.queue.size >= queueMaxSize) {
            Log.w("PlaybackRepo", "addToQueue: Queue at max capacity ($queueMaxSize). Skipping.")
            return
        }

        if (mediaHandle.controller == null) {
            Log.d("PlaybackRepo", "addToQueue: mediaHandle.controller null, awaiting...")
            mediaHandle.controller = mediaHandle.future?.await()
        }

        mediaHandle.controller?.let { controller ->
            Log.d("PlaybackRepo", "addToQueue: mediaHandle.controller ready, mediaItemCount=${controller.mediaItemCount}")
            val resolvedUrl = PlaybackArtworkResolver.resolveEpisodeImageUrl(episode, podcast)
            Log.d("PlaybackRepo", "addToQueue: epId=${episode.id}, resolvedImageUrl='$resolvedUrl'")
            val metadata =
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast.title)
                    .setArtworkUri(android.net.Uri.parse(resolvedUrl))
                    .setDisplayTitle(episode.title)
                    .setSubtitle(podcast.title)
                    .setGenre(episode.podcastGenre ?: podcast.genre)
                    .build()

            val isLearn = PlaybackMediaIdPolicy.isLearnEntryPoint(entryPoint)
            val mediaId = PlaybackMediaIdPolicy.encodeMediaId(episode.id, isLearn)
            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(episode.audioUrl)
                    .setMediaMetadata(metadata)
                    .setMediaId(mediaId)
                    .setCustomCacheKey(episode.id) // Match DownloadRequest custom key
                    .build()

            controller.addMediaItem(mediaItem)
            Log.d("PlaybackRepo", "addToQueue: Added to Media3, new mediaItemCount=${controller.mediaItemCount}")

            // Update local state
            val currentQueue = playerStateFlow.value.queue
            playerStateFlow.value = playerStateFlow.value.copy(queue = currentQueue + episode)
            Log.d("PlaybackRepo", "addToQueue: Updated local state, queue size=${playerStateFlow.value.queue.size}")
            syncQueueToDb()
            rankingFeedbackRepository.recordAction(
                target =
                    FeedbackTarget(
                        episodeId = episode.id,
                        podcastId = podcast.id,
                        genre = episode.podcastGenre ?: podcast.genre,
                        source =
                            if (entryPoint == PlaybackEntryPoint.LEARN) {
                                CandidateSource.CURATED_INTENT
                            } else {
                                null
                            },
                    ),
                action = RankingAction.EXPLICIT_QUEUE,
            )
        } ?: Log.e("PlaybackRepo", "addToQueue: mediaHandle.controller still NULL after await!")
    }

    suspend fun addToQueueNext(
        episode: Episode,
        podcast: Podcast,
    ) {
        if (mediaHandle.controller == null) {
            mediaHandle.controller = mediaHandle.future?.await()
        }

        mediaHandle.controller?.let { controller ->
            val resolvedUrl = PlaybackArtworkResolver.resolveEpisodeImageUrl(episode, podcast)
            Log.d("PlaybackRepo", "addToQueueNext: epId=${episode.id}, resolvedImageUrl='$resolvedUrl'")
            val metadata =
                androidx.media3.common.MediaMetadata
                    .Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast.title)
                    .setArtworkUri(android.net.Uri.parse(resolvedUrl))
                    .setDisplayTitle(episode.title)
                    .setSubtitle(podcast.title)
                    .setGenre(episode.podcastGenre ?: podcast.genre)
                    .build()

            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(episode.audioUrl)
                    .setMediaMetadata(metadata)
                    .setMediaId(episode.id)
                    .setCustomCacheKey(episode.id) // Match DownloadRequest custom key
                    .build()

            // Insert at index 1 (after current playing item)
            // If queue is empty or has 1 item, this adds to end (index 1)
            val insertIndex = if (controller.mediaItemCount > 0) controller.currentMediaItemIndex + 1 else 0
            controller.addMediaItem(insertIndex, mediaItem)

            // Update local state
            val currentQueue = playerStateFlow.value.queue

            val newQueue =
                if (currentQueue.isNotEmpty()) {
                    // Insert at index 1
                    val mutable = currentQueue.toMutableList()
                    // Find current episode index in local queue to be safe
                    val currentId = playerStateFlow.value.currentEpisode?.id
                    val currentIndex = if (currentId != null) mutable.indexOfFirst { it.id == currentId } else 0

                    val safeIndex = if (currentIndex != -1) currentIndex + 1 else 1

                    if (mutable.size >= safeIndex) {
                        mutable.add(safeIndex, episode)
                    } else {
                        mutable.add(episode)
                    }
                    mutable.toList()
                } else {
                    listOf(episode)
                }

            playerStateFlow.value = playerStateFlow.value.copy(queue = newQueue)
            syncQueueToDb()
        }
    }

    /**
     * Snapshot of a removed queue item, returned so the UI can offer Undo and so the
     * skip signal (analytics + skip memory) can be deferred until the undo window lapses.
     */
    
    /**
     * Removes an episode from the queue (Media3 + in-memory + DB).
     *
     * @param deferSkipSignal when true, the AUTO_FILL rejection signal is NOT recorded
     *   here — the caller must invoke [confirmQueueRemoval] once the undo window lapses
     *   (or [undoQueueRemoval] if the user undoes), so an undone remove doesn't count
     *   as a rejection.
     * @return removal info for undo, or null if the episode wasn't in the queue.
     */
    suspend fun removeFromQueue(
        episodeId: String,
        deferSkipSignal: Boolean = false,
    ): RemovedQueueItem? {
        if (mediaHandle.controller == null) {
            mediaHandle.controller = mediaHandle.future?.await()
        }

        val queueItem =
            try {
                queueRepository.getQueueItemByEpisodeId(episodeId)
            } catch (e: Exception) {
                null
            }

        val currentQueue = playerStateFlow.value.queue
        val queueIndex = currentQueue.indexOfFirst { it.id == episodeId }

        var mediaIndex = -1
        mediaHandle.controller?.let { controller ->
            val mediaIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
            mediaIndex = QueueMath.mediaIndexOfEpisode(mediaIds, episodeId)
        }

        val removedInfo =
            if (queueIndex != -1) {
                val episode = currentQueue[queueIndex]
                RemovedQueueItem(
                    episode =
                        episode.copy(
                            contextType = queueItem?.contextType ?: episode.contextType,
                            contextSourceId = queueItem?.contextSourceId ?: episode.contextSourceId,
                        ),
                    queueIndex = queueIndex,
                    mediaIndex = mediaIndex,
                    contextType = queueItem?.contextType ?: episode.contextType,
                    contextSourceId = queueItem?.contextSourceId ?: episode.contextSourceId,
                )
            } else {
                null
            }

        if (!deferSkipSignal && removedInfo != null) {
            confirmQueueRemoval(removedInfo)
        }

        var removedFromController = false
        mediaHandle.controller?.let { controller ->
            if (mediaIndex != -1) {
                controller.removeMediaItem(mediaIndex)
                removedFromController = true
            }
        }

        // Always update local state and sync to DB, even if the item wasn't in Media3 playlist
        val existsInLocalQueue = currentQueue.any { it.id == episodeId }

        if (existsInLocalQueue || !removedFromController) {
            val newQueue = currentQueue.filter { it.id != episodeId }
            playerStateFlow.value = playerStateFlow.value.copy(queue = newQueue)
            syncQueueToDb()
        }
        return removedInfo
    }

    /**
     * Records the rejection signal for a removed AUTO_FILL item: PostHog analytics plus
     * local skip memory (so the SmartQueueEngine stops re-suggesting it and can
     * down-rank the podcast). Called immediately on remove, or after the undo window.
     */
    fun confirmQueueRemoval(removed: RemovedQueueItem) {
        if (removed.contextType != "AUTO_FILL") return
        try {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackSmartQueueEpisodeSkipped(
                episodeId = removed.episode.id,
                recommendationSource = removed.contextSourceId ?: "unknown",
                positionInQueue = removed.mediaIndex,
            )
            queueSkipMemory.recordSkip(
                episodeId = removed.episode.id,
                podcastId = removed.episode.podcastId,
                source = removed.contextSourceId,
            )
            scope.launch {
                rankingFeedbackRepository.recordAction(
                    target =
                        FeedbackTarget(
                            episodeId = removed.episode.id,
                            podcastId = removed.episode.podcastId.orEmpty(),
                            genre = removed.episode.podcastGenre,
                            source = CandidateSource.SERVER_RECOMMENDATION,
                        ),
                    action = RankingAction.REMOVE_AUTOFILLED,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "Failed to record queue removal signal for ${removed.episode.id}", e)
        }
    }

    /** Re-inserts a removed episode at its original position (Media3 + state + DB). */
    suspend fun undoQueueRemoval(removed: RemovedQueueItem) {
        val episode = removed.episode
        if (playerStateFlow.value.queue.any { it.id == episode.id }) return
        if (mediaHandle.controller == null) {
            mediaHandle.controller = mediaHandle.future?.await()
        }
        val controller = mediaHandle.controller ?: return

        val isLore = removed.contextType == QueueMath.CONTEXT_TYPE_LORE
        val mediaId = PlaybackMediaIdPolicy.encodeMediaId(episode.id, isLore)
        val resolvedUrl =
            PlaybackArtworkResolver.resolveEpisodeImageUrl(
                episodeImageUrl = episode.imageUrl,
                episodePodcastImageUrl = episode.podcastImageUrl,
                podcastImageUrl = null,
            ).orEmpty()
        val metadata =
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(episode.title)
                .setArtist(episode.podcastTitle ?: "")
                .setArtworkUri(android.net.Uri.parse(resolvedUrl))
                .setDisplayTitle(episode.title)
                .setSubtitle(episode.podcastTitle ?: "")
                .setGenre(episode.podcastGenre ?: "Podcast")
                .build()
        val mediaItem =
            MediaItem
                .Builder()
                .setUri(episode.audioUrl)
                .setMediaMetadata(metadata)
                .setMediaId(mediaId)
                .setCustomCacheKey(episode.id)
                .build()

        val insertMediaIndex =
            removed.mediaIndex
                .takeIf { it in 0..controller.mediaItemCount }
                ?: controller.mediaItemCount
        controller.addMediaItem(insertMediaIndex, mediaItem)

        val currentQueue = playerStateFlow.value.queue.toMutableList()
        val insertQueueIndex = removed.queueIndex.coerceIn(0, currentQueue.size)
        currentQueue.add(insertQueueIndex, episode)
        playerStateFlow.value = playerStateFlow.value.copy(queue = currentQueue.toList())
        syncQueueToDb()
    }

    /**
     * Moves a queue item to a new position, updating all three layers in order:
     * Media3 playlist (no playback interruption), in-memory PlayerState.queue, and —
     * via [persistQueueOrder], typically debounced to drag end — the Room queue table.
     *
     * Indices are PlayerState.queue indices; index 0 (the playing item) is pinned.
     */
    fun moveQueueItem(
        fromQueueIndex: Int,
        toQueueIndex: Int,
    ) {
        val queue = playerStateFlow.value.queue
        if (fromQueueIndex == toQueueIndex) return
        if (fromQueueIndex !in queue.indices || toQueueIndex !in queue.indices) return
        if (fromQueueIndex == 0 || toQueueIndex == 0) return

        val controller = mediaHandle.controller ?: return
        val episode = queue[fromQueueIndex]

        // Resolve Media3 indices by mediaId (with learn-prefix stripped), never by raw
        // queue index: the playlist can retain already-played items before the current one.
        val mediaIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
        val fromMedia = QueueMath.mediaIndexOfEpisode(mediaIds, episode.id)
        if (fromMedia != -1) {
            val base =
                QueueMath
                    .mediaIndexOfEpisode(mediaIds, queue[0].id)
                    .takeIf { it != -1 } ?: controller.currentMediaItemIndex.coerceAtLeast(0)
            val toMedia = (base + toQueueIndex).coerceIn(0, controller.mediaItemCount - 1)
            controller.moveMediaItem(fromMedia, toMedia)
        }

        playerStateFlow.value =
            playerStateFlow.value.copy(
                queue = QueueMath.moveItem(queue, fromQueueIndex, toQueueIndex),
            )
    }

    /**
     * Persists the current queue order to Room (called once on drag end so rapid moves
     * don't thrash the DB) and emits the reorder analytics event.
     */
    suspend fun persistQueueOrder(
        movedEpisodeId: String? = null,
        fromQueueIndex: Int = -1,
        toQueueIndex: Int = -1,
    ) {
        val queue = playerStateFlow.value.queue
        try {
            queueRepository.reorderQueue(queue.map { it.id })
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "persistQueueOrder: Failed", e)
        }
        if (movedEpisodeId != null && fromQueueIndex != toQueueIndex && fromQueueIndex >= 0 && toQueueIndex >= 0) {
            val movedEpisode = queue.firstOrNull { it.id == movedEpisodeId }
            val contextType = movedEpisode?.contextType
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackQueueReordered(
                episodeId = movedEpisodeId,
                fromPosition = fromQueueIndex,
                toPosition = toQueueIndex,
                contextType = contextType,
            )
            movedEpisode?.let { episode ->
                rankingFeedbackRepository.recordAction(
                    target =
                        FeedbackTarget(
                            episodeId = episode.id,
                            podcastId = episode.podcastId.orEmpty(),
                            genre = episode.podcastGenre,
                        ),
                    action =
                        if (toQueueIndex < fromQueueIndex) {
                            RankingAction.MOVE_UP
                        } else {
                            RankingAction.MOVE_DOWN
                        },
                )
            }
        }
    }

    /**
     * True when the current queue contains any normal (non-Lore) item. Checks the live
     * player first, then the persisted queue rows (which survive process restarts).
     */
    suspend fun hasNonLoreQueue(): Boolean {
        if (mediaHandle.controller == null) {
            mediaHandle.controller = mediaHandle.future?.await()
        }
        val controller = mediaHandle.controller
        if (controller != null && controller.mediaItemCount > 0) {
            val mediaIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
            return QueueMath.hasNonLoreMediaIds(mediaIds)
        }
        val snapshot =
            try {
                queueRepository.getQueueSnapshot()
            } catch (e: Exception) {
                emptyList()
            }
        return snapshot.isNotEmpty() && QueueMath.hasNonLoreContextTypes(snapshot.map { it.contextType })
    }

    /**
     * Stops playback and clears the queue everywhere (player + state + DB). Used when
     * the user confirms starting a fresh Lore queue over an existing normal queue.
     */
    suspend fun stopAndClearQueue() {
        mediaHandle.controller?.stop()
        mediaHandle.controller?.clearMediaItems()
        stopProgressTicker()
        playerStateFlow.value = PlayerState()
        // A new queue is about to start; don't block session restore on next launch.
        prefs.edit().putBoolean(playerDismissedKey, false).apply()
        try {
            queueRepository.clearQueue()
        } catch (e: Exception) {
            android.util.Log.e("PlaybackRepo", "stopAndClearQueue: Failed to clear DB queue", e)
        }
    }

    suspend fun playEpisode(
        episode: Episode,
        podcast: Podcast,
        entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
        initialPositionMs: Long? = null,
    ) {
        playQueue(listOf(episode), podcast, 0, entryPoint, initialPositionMs)
    }

    /**
     * Play an episode from the provided queue list, reloading into Media3 from that point.
     * Pass the queue list directly to avoid stale state issues.
     */
    suspend fun playFromQueueIndex(
        episodeId: String,
        queueList: List<Episode>,
        podcast: Podcast,
    ) {
        val index = queueList.indexOfFirst { it.id == episodeId }

        if (index == -1) {
            android.util.Log.e("PlaybackRepo", "playFromQueueIndex: episode $episodeId not found in provided queue!")
            return
        }

        // Slice queue from this episode onwards
        val slicedQueue = queueList.drop(index)
        android.util.Log.d("PlaybackRepo", "playFromQueueIndex: slicing from index $index, newQueueSize=${slicedQueue.size}")

        // Reload into Media3 with the sliced queue
        playQueue(slicedQueue, podcast, 0)
    }
}
