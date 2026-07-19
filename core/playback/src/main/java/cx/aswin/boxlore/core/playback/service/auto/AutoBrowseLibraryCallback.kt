package cx.aswin.boxlore.core.playback.service.auto

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AutoBrowseLibraryCallback(
    private val host: AutoBrowseLibraryHost,
) : MediaLibrarySession.Callback {
    private val mediaResolver = AutoMediaResolver(host)
    private val treeBuilder = AutoBrowseTreeBuilder(host, mediaResolver)
    private val voiceSearch = AutoVoiceSearchHandler(host, treeBuilder)

    private val connectedAtMs =
        java.util.concurrent.ConcurrentHashMap<MediaSession.ControllerInfo, Long>()
    private val rootChildLimits =
        java.util.concurrent.ConcurrentHashMap<MediaSession.ControllerInfo, Int>()
    private val searchCache =
        object : LinkedHashMap<String, List<MediaItem>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MediaItem>>): Boolean = size > 8
        }

    private val ROOT_ID = AutoBrowseContract.ROOT_ID
    private val HOME_ID = AutoBrowseContract.HOME_ID
    private val LIBRARY_ID = AutoBrowseContract.LIBRARY_ID
    private val DOWNLOADS_ID = AutoBrowseContract.DOWNLOADS_ID
    private val DISCOVER_ID = AutoBrowseContract.DISCOVER_ID
    private val EXPLORE_ID = AutoBrowseContract.LEGACY_EXPLORE_ID
    private val SUBSCRIPTIONS_ID = "subscriptions"
    private val HOME_CONTINUE_LISTENING_ID = AutoBrowseContract.HOME_CONTINUE_ID
    private val HOME_SUBSCRIPTIONS_ID = "home_subscriptions"
    private val HOME_QUEUE_ID = AutoBrowseContract.HOME_QUEUE_ID
    private val HOME_NEW_EPISODES_ID = AutoBrowseContract.HOME_NEW_EPISODES_ID
    private val PLAY_ALL_NEW_EPISODES_ID = AutoBrowseContract.PLAY_ALL_NEW_ID
    private val SUBSCRIPTION_PREFIX = AutoBrowseContract.SUBSCRIPTION_PREFIX

    private val SEEK_BACK_CMD = androidx.media3.session.SessionCommand("SEEK_BACK", Bundle.EMPTY)
    private val SEEK_FORWARD_CMD = androidx.media3.session.SessionCommand("SEEK_FORWARD", Bundle.EMPTY)
    private val MARK_COMPLETED_SKIP_CMD = androidx.media3.session.SessionCommand("MARK_COMPLETED_SKIP", Bundle.EMPTY)
    private val TOGGLE_LIKE_CMD =
        androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_TOGGLE_LIKE,
            Bundle.EMPTY,
        )
    private val ADD_TO_QUEUE_CMD =
        androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_ADD_TO_QUEUE,
            Bundle.EMPTY,
        )
    private val MARK_COMPLETE_CMD =
        androidx.media3.session.SessionCommand(
            AutoBrowseContract.COMMAND_MARK_COMPLETE,
            Bundle.EMPTY,
        )

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        connectedAtMs[controller] = System.currentTimeMillis()
        AnalyticsHelper.trackAndroidAutoConnected(sessionId = controller.packageName)
        val defaultResult = super.onConnect(session, controller)
        val sessionCommands =
            defaultResult.availableSessionCommands
                .buildUpon()
                .add(SEEK_BACK_CMD)
                .add(SEEK_FORWARD_CMD)
                .add(MARK_COMPLETED_SKIP_CMD)
                .add(TOGGLE_LIKE_CMD)
                .add(ADD_TO_QUEUE_CMD)
                .add(MARK_COMPLETE_CMD)
                .build()
        return MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(defaultResult.availablePlayerCommands)
            .setCustomLayout(listOf(host.seekBackAction, host.seekForwardAction, host.markCompleteAction))
            .build()
    }

    override fun onDisconnected(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ) {
        val started = connectedAtMs.remove(controller)
        val durationSeconds =
            started?.let { ((System.currentTimeMillis() - it) / 1000L).toInt().coerceAtLeast(0) }
        AnalyticsHelper.trackAndroidAutoDisconnected(
            sessionId = controller.packageName,
            durationSeconds = durationSeconds,
        )
        rootChildLimits.remove(controller)
        super.onDisconnected(session, controller)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: androidx.media3.session.SessionCommand,
        args: Bundle,
    ): ListenableFuture<androidx.media3.session.SessionResult> {
        if (
            customCommand.customAction == AutoBrowseContract.COMMAND_TOGGLE_LIKE ||
            customCommand.customAction == AutoBrowseContract.COMMAND_ADD_TO_QUEUE ||
            customCommand.customAction == AutoBrowseContract.COMMAND_MARK_COMPLETE
        ) {
            return host.serviceScope.future {
                val episodeId =
                    args
                        .getString(androidx.media3.session.MediaConstants.EXTRA_KEY_MEDIA_ID)
                        ?.stripEpisodePrefix()
                        ?: session.player.currentMediaItem
                            ?.mediaId
                            ?.stripEpisodePrefix()
                val handled =
                    if (episodeId != null) {
                        when (customCommand.customAction) {
                            AutoBrowseContract.COMMAND_TOGGLE_LIKE -> toggleEpisodeLike(episodeId)
                            AutoBrowseContract.COMMAND_ADD_TO_QUEUE ->
                                addEpisodeToQueue(episodeId, session.player)
                            AutoBrowseContract.COMMAND_MARK_COMPLETE -> markEpisodeComplete(episodeId)
                            else -> false
                        }
                    } else {
                        false
                    }
                androidx.media3.session.SessionResult(
                    if (!handled) {
                        androidx.media3.session.SessionResult.RESULT_ERROR_BAD_VALUE
                    } else {
                        androidx.media3.session.SessionResult.RESULT_SUCCESS
                    },
                )
            }
        }
        when (customCommand.customAction) {
            "SEEK_BACK" -> {
                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                    .setSeekSource("seek_backward")
                session.player.seekBack()
                android.util.Log.d("AutoBrowse", "Seek backward")
            }
            "SEEK_FORWARD" -> {
                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                    .setSeekSource("seek_forward")
                session.player.seekForward()
                android.util.Log.d("AutoBrowse", "Seek forward")
            }
            "MARK_COMPLETED_SKIP" -> {
                host.markCurrentEpisodeCompletedAndSkip(session)
            }
            else -> return super.onCustomCommand(session, controller, customCommand, args)
        }
        return Futures.immediateFuture(
            androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS),
        )
    }

    private suspend fun toggleEpisodeLike(episodeId: String): Boolean {
        val existing = getOrCreateHistoryItem(episodeId) ?: return false
        host.database.listeningHistoryDao().upsert(
            existing.copy(
                isLiked = !existing.isLiked,
                isDirty = true,
            ),
        )
        host.mediaSession?.notifyChildrenChanged(AutoBrowseContract.LIBRARY_LIKED_ID, 50, null)
        return true
    }

    private suspend fun markEpisodeComplete(episodeId: String): Boolean {
        val existing = getOrCreateHistoryItem(episodeId) ?: return false
        host.observeManualCompletion(episodeId)
        host.database.listeningHistoryDao().upsert(
            existing.copy(
                progressMs = 0L,
                isCompleted = true,
                isManualCompletion = true,
                isDirty = true,
                lastPlayedAt = System.currentTimeMillis(),
            ),
        )
        host.mediaSession?.notifyChildrenChanged(AutoBrowseContract.HOME_CONTINUE_ID, 20, null)
        host.mediaSession?.notifyChildrenChanged(AutoBrowseContract.LIBRARY_HISTORY_ID, 50, null)
        return true
    }

    private suspend fun getOrCreateHistoryItem(episodeId: String): cx.aswin.boxlore.core.database.ListeningHistoryEntity? {
        host.database
            .listeningHistoryDao()
            .getHistoryItem(episodeId)
            ?.let { return it }
        val episode = mediaResolver.resolveDomainEpisode(episodeId) ?: return null
        return cx.aswin.boxlore.core.database.ListeningHistoryEntity(
            episodeId = episode.id,
            podcastId = episode.podcastId.orEmpty(),
            episodeTitle = episode.title,
            episodeImageUrl = episode.imageUrl,
            podcastImageUrl = episode.podcastImageUrl,
            episodeAudioUrl = episode.audioUrl,
            podcastName = episode.podcastTitle.orEmpty(),
            progressMs = 0L,
            durationMs = episode.duration.toLong() * 1_000L,
            isCompleted = false,
            lastPlayedAt = System.currentTimeMillis(),
            enclosureType = episode.enclosureType,
            episodeDescription = episode.description,
        )
    }

    private suspend fun addEpisodeToQueue(
        episodeId: String,
        player: Player,
    ): Boolean {
        val existingQueue = host.queueRepository.getQueueSnapshot()
        val queuedEpisode = existingQueue.firstOrNull { it.id == episodeId }
        val episode = queuedEpisode ?: mediaResolver.resolveDomainEpisode(episodeId) ?: return false
        val playerHasEpisode =
            (0 until player.mediaItemCount).any { index ->
                player.getMediaItemAt(index).mediaId.stripEpisodePrefix() == episodeId
            }
        if (!playerHasEpisode) {
            player.addMediaItem(
                AutoMediaItemFactory.fromEpisode(
                    episode = episode,
                    source = AutoBrowseContract.SOURCE_QUEUE,
                    artworkUri =
                        AutoArtworkRepository.remoteUri(
                            host.asContext(),
                            episode.imageUrl ?: episode.podcastImageUrl,
                        ),
                    mediaIdPrefix = AutoBrowseContract.QUEUE_PREFIX,
                ),
            )
        }
        if (queuedEpisode == null) host.queueRepository.replaceQueue(existingQueue + episode)
        host.mediaSession?.notifyChildrenChanged(AutoBrowseContract.HOME_QUEUE_ID, 50, null)
        return true
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        val keyEvent =
            androidx.core.content.IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_KEY_EVENT,
                android.view.KeyEvent::class.java,
            )
        if (keyEvent != null && keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .setSeekSource("seek_forward")
                    session.player.seekForward()
                    android.util.Log.d("BoxLorePlaybackService", "onMediaButtonEvent: KEYCODE_MEDIA_NEXT intercepted, seeking forward")
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .setSeekSource("seek_backward")
                    session.player.seekBack()
                    android.util.Log.d("BoxLorePlaybackService", "onMediaButtonEvent: KEYCODE_MEDIA_PREVIOUS intercepted, seeking backward")
                    return true
                }
            }
        }
        return super.onMediaButtonEvent(session, controllerInfo, intent)
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        android.util.Log.d("AutoBrowse", "onGetLibraryRoot called")

        rootChildLimits[browser] = params
            ?.extras
            ?.getInt(androidx.media3.session.MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 4)
            ?.takeIf { it > 0 }
            ?.coerceAtMost(4)
            ?: 4
        val rootExtras = AutoBrowseContract.listChildrenExtras()

        val rootItem =
            MediaItem
                .Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(host.getString(cx.aswin.boxlore.core.data.R.string.auto_app_name))
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setExtras(rootExtras)
                        .build(),
                ).build()

        val resultParams = LibraryParams.Builder().setExtras(rootExtras).build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, resultParams))
    }


    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        android.util.Log.d("AutoBrowse", "onGetChildren: parentId=$parentId, page=$page")
        AnalyticsHelper.trackAndroidAutoBrowse(node = parentId, action = "get_children")

        return host.serviceScope.future {
            try {
                val items =
                    when {
                        parentId == ROOT_ID -> treeBuilder.getRootChildren().take(rootChildLimits[browser] ?: 4)
                        parentId == HOME_ID -> treeBuilder.getHomeChildren()
                        parentId == HOME_CONTINUE_LISTENING_ID -> treeBuilder.getContinueListeningChildren()
                        parentId == HOME_QUEUE_ID -> treeBuilder.getQueueChildren()
                        parentId == AutoBrowseContract.HOME_DRIVE_MIX_ID -> treeBuilder.getMixtapeChildren()
                        parentId == HOME_NEW_EPISODES_ID -> treeBuilder.getNewEpisodesChildren()
                        parentId == HOME_SUBSCRIPTIONS_ID || parentId == SUBSCRIPTIONS_ID ->
                            treeBuilder.getSubscriptionsChildren()
                        parentId == LIBRARY_ID -> treeBuilder.getLibraryChildren()
                        parentId == AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID ->
                            treeBuilder.getSubscriptionsChildren()
                        parentId == AutoBrowseContract.LIBRARY_LIKED_ID -> treeBuilder.getLikedChildren()
                        parentId == AutoBrowseContract.LIBRARY_HISTORY_ID -> treeBuilder.getHistoryChildren()
                        parentId == DOWNLOADS_ID -> treeBuilder.getDownloadsChildren()
                        parentId == DISCOVER_ID || parentId == EXPLORE_ID -> treeBuilder.getDiscoverChildren()
                        parentId == AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID ->
                            treeBuilder.getDrivePicksChildren()
                        parentId == AutoBrowseContract.DISCOVER_TIME_PICKS_ID ||
                            parentId == "explore_picks" -> treeBuilder.getExplorePicksChildren()
                        parentId == AutoBrowseContract.DISCOVER_GENRES_ID ||
                            parentId == "explore_genres" -> treeBuilder.getGenresChildren()
                        parentId.startsWith(AutoBrowseContract.GENRE_PREFIX) -> {
                            treeBuilder.getGenreChildren(
                                parentId.removePrefix(AutoBrowseContract.GENRE_PREFIX),
                            )
                        }
                        parentId.startsWith("home_curated_") ||
                            parentId.startsWith("explore_curated_") ||
                            parentId.startsWith(AutoBrowseContract.CURATED_PREFIX) -> {
                            val vibeId =
                                parentId
                                    .removePrefix("home_curated_")
                                    .removePrefix("explore_curated_")
                                    .removePrefix(AutoBrowseContract.CURATED_PREFIX)
                            treeBuilder.getCuratedChildren(vibeId)
                        }
                        parentId.startsWith(SUBSCRIPTION_PREFIX) -> {
                            val podcastId = parentId.removePrefix(SUBSCRIPTION_PREFIX)
                            treeBuilder.getPodcastEpisodes(podcastId)
                        }
                        else -> emptyList()
                    }
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(treeBuilder.slicePage(items, page, pageSize)),
                    params,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AutoBrowse", "onGetChildren error for $parentId", e)
                LibraryResult.ofItemList(ImmutableList.of(), params)
            }
        }
    }


    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        android.util.Log.d("AutoBrowse", "onSearch: query='$query'")
        return host.serviceScope.future {
            val results = voiceSearch.buildSearchResults(query)
            synchronized(searchCache) { searchCache[query] = results }
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        android.util.Log.d("AutoBrowse", "onGetSearchResult: query='$query'")

        return host.serviceScope.future {
            try {
                val results =
                    synchronized(searchCache) { searchCache[query] }
                        ?: voiceSearch.buildSearchResults(query).also {
                            synchronized(searchCache) { searchCache[query] = it }
                        }
                android.util.Log.d("AutoBrowse", "Search results for '$query': ${results.size} items")
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(treeBuilder.slicePage(results, page, pageSize)),
                    params,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("AutoBrowse", "Search failed for '$query'", e)
                LibraryResult.ofItemList(ImmutableList.of(), params)
            }
        }
    }


    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        android.util.Log.d("AutoBrowse", "onGetItem: mediaId=$mediaId")
        return host.serviceScope.future {
            val item =
                if (
                    mediaId.startsWith(AutoBrowseContract.EPISODE_PREFIX) ||
                    mediaId.startsWith(AutoBrowseContract.QUEUE_PREFIX) ||
                    mediaId.startsWith(AutoBrowseContract.LEARN_PREFIX)
                ) {
                    mediaResolver.resolveMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
                } else {
                    (
                        treeBuilder.getRootChildren() +
                            treeBuilder.getHomeChildren() +
                            treeBuilder.getLibraryChildren() +
                            treeBuilder.getDiscoverChildren()
                    ).firstOrNull { it.mediaId == mediaId }
                        ?: AutoMediaItemFactory.browsable(
                            id = mediaId,
                            title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_app_name),
                        )
                }
            LibraryResult.ofItem(item, null)
        }
    }

    /**
     * Android Auto browse plays a single episode; this routes the follow-up queue
     * build through the same guarded SmartQueueEngine refill path as the transition
     * listener (shared host.isRefilling flag), so Auto no longer bypasses dedup/ranking
     * or persists rows without provenance.
     */
    private fun buildAndAppendQueueAsync(
        episodeId: String,
        mediaSession: MediaSession,
    ) {
        host.serviceScope.launch {
            try {
                val player = mediaSession.player as? ExoPlayer ?: return@launch
                // Wait briefly for the selected episode to become the current item so
                // the engine refills relative to it (playback start is asynchronous).
                var attempts = 0
                while (attempts < 20) {
                    val currentId =
                        player.currentMediaItem
                            ?.mediaId
                            ?.removePrefix(
                                AutoBrowseContract.LEARN_PREFIX,
                            )?.removePrefix(AutoBrowseContract.EPISODE_PREFIX)
                            ?.removePrefix(AutoBrowseContract.QUEUE_PREFIX)
                    if (currentId == episodeId) break
                    kotlinx.coroutines.delay(250)
                    attempts++
                }
                if (host.isRefilling) {
                    android.util.Log.d("AutoBrowse", "Refill already in flight; skipping Auto queue build")
                    return@launch
                }
                host.isRefilling = true
                try {
                    host.refillQueue(player)
                } finally {
                    host.isRefilling = false
                }
            } catch (e: Exception) {
                android.util.Log.e("AutoBrowse", "Async queue build failed", e)
            }
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> {
        android.util.Log.d("AutoBrowse", "onAddMediaItems: ${mediaItems.size} items")

        return host.serviceScope.future {
            if (mediaItems.size > 1) {
                cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
                    mapOf("entry_point" to "android_auto_play_all"),
                )
                return@future mediaItems.map { mediaResolver.resolveMediaItem(it) }.toMutableList()
            }

            val selectedItem = mediaItems.first()
            val searchQuery = selectedItem.requestMetadata.searchQuery

            if (!searchQuery.isNullOrBlank()) {
                android.util.Log.d("AutoBrowse", "Voice play request: '$searchQuery'")
                cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
                    mapOf("entry_point" to "android_auto_voice"),
                )
                return@future voiceSearch.handleVoiceSearchQuery(searchQuery)
            }

            when (selectedItem.mediaId) {
                PLAY_ALL_NEW_EPISODES_ID -> return@future voiceSearch.handlePlayAllNewEpisodes()
                AutoBrowseContract.PLAY_ALL_LIKED_ID -> return@future voiceSearch.handlePlayAllLiked()
                AutoBrowseContract.PLAY_ALL_DOWNLOADS_ID -> return@future voiceSearch.handlePlayAllDownloads()
                AutoBrowseContract.PLAY_ALL_DRIVE_ID -> return@future voiceSearch.handlePlayAllDrivePicks()
                AutoBrowseContract.PLAY_ALL_MIXTAPE_ID -> return@future voiceSearch.handlePlayAllMixtape()
            }

            val source =
                selectedItem.mediaMetadata.extras
                    ?.getString(AutoBrowseContract.EXTRA_SOURCE)
                    ?: AutoBrowseContract.SOURCE_DISCOVER
            if (selectedItem.mediaId.startsWith(AutoBrowseContract.QUEUE_PREFIX)) {
                return@future voiceSearch.handlePlayFromQueue(selectedItem.mediaId.stripEpisodePrefix())
            }
            if (source == AutoBrowseContract.SOURCE_MIXTAPE) {
                return@future voiceSearch.handlePlayFromMixtape(selectedItem.mediaId.stripEpisodePrefix())
            }

            handleSingleMediaItemSelection(mediaSession, selectedItem, source)
        }
    }

    private suspend fun handleSingleMediaItemSelection(
        mediaSession: MediaSession,
        selectedItem: MediaItem,
        source: String,
    ): MutableList<MediaItem> {
        android.util.Log.d(
            "BoxCastPlayer",
            "onAddMediaItems: selectedItem.mediaId=${selectedItem.mediaId}, extrasKeys=${selectedItem.mediaMetadata.extras?.keySet()?.joinToString(
                ", ",
            )}",
        )
        cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_$source"),
        )
        val resolvedItem = mediaResolver.resolveMediaItem(selectedItem)
        val episodeId = selectedItem.mediaId.stripEpisodePrefix()
        android.util.Log.d(
            "AutoBrowse",
            "Returning episode instantly: $episodeId, startsWithLearn=${selectedItem.mediaId.startsWith(AutoBrowseContract.LEARN_PREFIX)}",
        )

        val skipSmartRefill =
            selectedItem.mediaId.startsWith(AutoBrowseContract.LEARN_PREFIX) ||
                source == AutoBrowseContract.SOURCE_DOWNLOADS ||
                source == AutoBrowseContract.SOURCE_QUEUE
        android.util.Log.d("AutoBrowse", "onAddMediaItems skipSmartRefill=$skipSmartRefill")
        if (!skipSmartRefill) {
            buildAndAppendQueueAsync(episodeId, mediaSession)
        } else {
            android.util.Log.d("AutoBrowse", "Explicit/offline source: skipping async queue append")
        }
        return mutableListOf(resolvedItem)
    }

}
