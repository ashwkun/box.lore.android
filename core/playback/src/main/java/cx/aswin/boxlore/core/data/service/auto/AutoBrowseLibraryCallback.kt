package cx.aswin.boxlore.core.data.service.auto

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
import cx.aswin.boxlore.core.data.ranking.RankingObjective
import cx.aswin.boxlore.core.data.toScorable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AutoBrowseLibraryCallback(
    private val host: AutoBrowseLibraryHost,
) : MediaLibrarySession.Callback {
    private val rootChildLimits =
        java.util.concurrent.ConcurrentHashMap<MediaSession.ControllerInfo, Int>()
    private val searchCache =
        object : LinkedHashMap<String, List<MediaItem>>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MediaItem>>): Boolean = size > 8
        }

    @Volatile
    private var lastDrivePicks: List<MediaItem> = emptyList()

    @Volatile
    private var lastMixtape: List<MediaItem> = emptyList()

    @Volatile
    private var lastMixtapeUpdatedAt: Long = 0L

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
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                    .setSeekSource("seek_backward")
                session.player.seekBack()
                android.util.Log.d("AutoBrowse", "Seek backward")
            }
            "SEEK_FORWARD" -> {
                cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
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

    private suspend fun getOrCreateHistoryItem(episodeId: String): cx.aswin.boxlore.core.data.database.ListeningHistoryEntity? {
        host.database
            .listeningHistoryDao()
            .getHistoryItem(episodeId)
            ?.let { return it }
        val episode = resolveDomainEpisode(episodeId) ?: return null
        return cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
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
        val episode = queuedEpisode ?: resolveDomainEpisode(episodeId) ?: return false
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
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
                        .setSeekSource("seek_forward")
                    session.player.seekForward()
                    android.util.Log.d("BoxLorePlaybackService", "onMediaButtonEvent: KEYCODE_MEDIA_NEXT intercepted, seeking forward")
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper
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

        return host.serviceScope.future {
            try {
                val items =
                    when {
                        parentId == ROOT_ID -> getRootChildren().take(rootChildLimits[browser] ?: 4)
                        parentId == HOME_ID -> getHomeChildren()
                        parentId == HOME_CONTINUE_LISTENING_ID -> getContinueListeningChildren()
                        parentId == HOME_QUEUE_ID -> getQueueChildren()
                        parentId == AutoBrowseContract.HOME_DRIVE_MIX_ID -> getMixtapeChildren()
                        parentId == HOME_NEW_EPISODES_ID -> getNewEpisodesChildren()
                        parentId == HOME_SUBSCRIPTIONS_ID || parentId == SUBSCRIPTIONS_ID -> getSubscriptionsChildren()
                        parentId == LIBRARY_ID -> getLibraryChildren()
                        parentId == AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID ->
                            getSubscriptionsChildren()
                        parentId == AutoBrowseContract.LIBRARY_LIKED_ID -> getLikedChildren()
                        parentId == AutoBrowseContract.LIBRARY_HISTORY_ID -> getHistoryChildren()
                        parentId == DOWNLOADS_ID -> getDownloadsChildren()
                        parentId == DISCOVER_ID || parentId == EXPLORE_ID -> getDiscoverChildren()
                        parentId == AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID ->
                            getDrivePicksChildren()
                        parentId == AutoBrowseContract.DISCOVER_TIME_PICKS_ID ||
                            parentId == "explore_picks" -> getExplorePicksChildren()
                        parentId == AutoBrowseContract.DISCOVER_GENRES_ID ||
                            parentId == "explore_genres" -> getGenresChildren()
                        parentId.startsWith(AutoBrowseContract.GENRE_PREFIX) -> {
                            getGenreChildren(
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
                            getCuratedChildren(vibeId)
                        }
                        parentId.startsWith(SUBSCRIPTION_PREFIX) -> {
                            val podcastId = parentId.removePrefix(SUBSCRIPTION_PREFIX)
                            getPodcastEpisodes(podcastId)
                        }
                        else -> emptyList()
                    }
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(slicePage(items, page, pageSize)),
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
            val results = buildSearchResults(query)
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
                        ?: buildSearchResults(query).also {
                            synchronized(searchCache) { searchCache[query] = it }
                        }
                android.util.Log.d("AutoBrowse", "Search results for '$query': ${results.size} items")
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(slicePage(results, page, pageSize)),
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

    private suspend fun buildSearchResults(query: String): List<MediaItem> {
        val normalized = normalizeVoiceQuery(query)
        if (normalized.isBlank()) return emptyList()
        val results = mutableListOf<Pair<Int, MediaItem>>()

        host.database
            .listeningHistoryDao()
            .getRecentHistoryList(100)
            .mapNotNull { history ->
                val score = searchScore(history.episodeTitle, history.podcastName, normalized)
                if (score == 0) return@mapNotNull null
                score to
                    AutoMediaItemFactory.fromHistory(
                        history = history,
                        source = AutoBrowseContract.SOURCE_SEARCH,
                        artworkUri =
                            AutoArtworkRepository.remoteUri(
                                host.asContext(),
                                history.episodeImageUrl ?: history.podcastImageUrl,
                            ),
                        subtitle =
                            AutoMediaItemFactory.buildDurationSubtitle(
                                history.podcastName,
                                history.durationMs,
                            ),
                        groupTitle =
                            host.getString(
                                cx.aswin.boxlore.core.data.R.string.auto_group_search,
                            ),
                    )
            }.sortedByDescending { it.first }
            .take(8)
            .let(results::addAll)

        host.database
            .podcastDao()
            .getSubscribedPodcastsList()
            .mapNotNull { podcast ->
                val score = searchScore(podcast.title, podcast.author, normalized)
                if (score == 0) return@mapNotNull null
                score to
                    AutoMediaItemFactory.browsable(
                        id = "$SUBSCRIPTION_PREFIX${podcast.podcastId}",
                        title = podcast.title,
                        subtitle = podcast.author,
                        artworkUri =
                            AutoArtworkRepository.remoteUri(
                                host.asContext(),
                                podcast.imageUrl,
                            ),
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    )
            }.sortedByDescending { it.first }
            .take(8)
            .let(results::addAll)

        if (results.size < 12) {
            try {
                kotlinx.coroutines
                    .withTimeout(5_000L) {
                        host.podcastRepository.searchPodcasts(normalized)
                    }.take(10)
                    .forEach { podcast ->
                        results += searchScore(podcast.title, podcast.artist, normalized) to
                            AutoMediaItemFactory.browsable(
                                id = "$SUBSCRIPTION_PREFIX${podcast.id}",
                                title = podcast.title,
                                subtitle = podcast.artist,
                                artworkUri =
                                    AutoArtworkRepository.remoteUri(
                                        host.asContext(),
                                        podcast.imageUrl,
                                    ),
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                            )
                    }
            } catch (error: Exception) {
                android.util.Log.w("AutoBrowse", "Remote Auto search unavailable", error)
            }
        }

        return results
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.mediaId }
            .take(30)
    }

    private fun searchScore(
        primary: String,
        secondary: String?,
        query: String,
    ): Int =
        cx.aswin.boxlore.core.data.playback.AutoVoiceSearchLogic
            .searchScore(primary, secondary, query)

    private fun normalizeVoiceQuery(query: String): String =
        cx.aswin.boxlore.core.data.playback.AutoVoiceSearchLogic
            .normalizeVoiceQuery(query)

    private fun voiceMatchScore(
        title: String,
        author: String?,
        query: String,
    ): Int =
        cx.aswin.boxlore.core.data.playback.AutoVoiceSearchLogic
            .voiceMatchScore(title, author, query)

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
                    resolveMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
                } else {
                    (
                        getRootChildren() +
                            getHomeChildren() +
                            getLibraryChildren() +
                            getDiscoverChildren()
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
     * Called when Android Auto wants to play a MediaItem from the browse tree.
     * Resolves items into playable MediaItems AND builds a queue from the
     * same podcast, mirroring the phone's QueueManager/PlayerViewModel behavior.
     */
    private suspend fun handleVoiceSearchQuery(searchQuery: String): MutableList<MediaItem> {
        val rawQuery = searchQuery.lowercase()
        val normalizedQuery = normalizeVoiceQuery(searchQuery)
        android.util.Log.d(
            "AutoBrowse",
            "Normalized voice query '$searchQuery' → '$normalizedQuery'",
        )

        handleVoiceQueryQuickFallbacks(rawQuery, normalizedQuery)?.let { return it }
        handleVoiceQueryHistoryResume(rawQuery)?.let { return it }
        handleVoiceQuerySubscriptionMatch(normalizedQuery)?.let { return it }
        handleVoiceQueryRemoteSearch(normalizedQuery)?.let { return it }

        val fallback = host.database.listeningHistoryDao().getLastPlayedSession()
        if (fallback != null) {
            android.util.Log.d("AutoBrowse", "Voice fallback: ${fallback.episodeTitle}")
            return mutableListOf(voiceHistoryItem(fallback))
        }

        return handlePlayAllMixtape().ifEmpty {
            getDownloadEpisodeItems().take(1).toMutableList()
        }
    }

    private suspend fun handleVoiceQueryQuickFallbacks(
        rawQuery: String,
        normalizedQuery: String,
    ): MutableList<MediaItem>? {
        if (rawQuery.contains("download") || rawQuery.contains("offline")) {
            return getDownloadEpisodeItems().toMutableList()
        }
        if (rawQuery.contains("drive mix") || rawQuery.contains("mixtape")) {
            return handlePlayAllMixtape()
        }
        if (
            normalizedQuery in
            listOf(
                "",
                "something",
                "anything",
                "surprise me",
                "podcast",
                "podcasts",
                "my shows",
                "my mix",
            )
        ) {
            return handlePlayAllMixtape()
        }
        return null
    }

    private suspend fun handleVoiceQueryHistoryResume(rawQuery: String): MutableList<MediaItem>? {
        if (rawQuery.contains("subscription") || rawQuery.contains("resume")) {
            val lastSession = host.database.listeningHistoryDao().getLastPlayedSession()
            if (lastSession != null) {
                android.util.Log.d(
                    "AutoBrowse",
                    "Voice resume matched: ${lastSession.episodeTitle}",
                )
                return mutableListOf(voiceHistoryItem(lastSession))
            }
        }
        return null
    }

    private suspend fun handleVoiceQuerySubscriptionMatch(normalizedQuery: String): MutableList<MediaItem>? {
        val subs = host.database.podcastDao().getSubscribedPodcastsList()
        val matchedPod =
            subs
                .map { podcast ->
                    podcast to
                        voiceMatchScore(
                            podcast.title,
                            podcast.author,
                            normalizedQuery,
                        )
                }.filter { (_, score) -> score > 0 }
                .maxByOrNull { (_, score) -> score }
                ?.first

        if (matchedPod != null) {
            android.util.Log.d("AutoBrowse", "Voice matched subscription: ${matchedPod.title}")
            val episode =
                matchedPod.latestEpisode
                    ?: kotlinx.coroutines.withTimeoutOrNull(2_500L) {
                        host.podcastRepository.getEpisodes(matchedPod.podcastId).firstOrNull()
                    }
            if (episode != null) {
                return mutableListOf(
                    voiceEpisodeItem(
                        episode = episode,
                        podcastTitle = matchedPod.title,
                        podcastImageUrl = matchedPod.imageUrl,
                    ),
                )
            }
        }
        return null
    }

    private suspend fun searchPodcastMatch(normalizedQuery: String): MediaItem? =
        try {
            val podcast =
                host.podcastRepository
                    .searchPodcasts(normalizedQuery)
                    .maxByOrNull {
                        voiceMatchScore(it.title, it.artist, normalizedQuery)
                    }
            podcast?.let {
                val episode =
                    it.latestEpisode
                        ?: host.podcastRepository.getEpisodes(it.id).firstOrNull()
                episode?.let { match ->
                    voiceEpisodeItem(
                        episode = match,
                        podcastTitle = it.title,
                        podcastImageUrl = it.imageUrl,
                    )
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w(
                "AutoBrowse",
                "Voice podcast search unavailable",
                error,
            )
            null
        }

    private suspend fun searchEpisodeMatch(normalizedQuery: String): MediaItem? =
        try {
            val region = host.smartQueueSources.getRegion()
            host.podcastRepository
                .searchEpisodesSemantic(normalizedQuery, region)
                .firstOrNull()
                ?.let {
                    voiceEpisodeItem(
                        episode = it,
                        podcastTitle = it.podcastTitle,
                        podcastImageUrl = it.podcastImageUrl,
                    )
                }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w(
                "AutoBrowse",
                "Voice episode search unavailable",
                error,
            )
            null
        }

    private suspend fun handleVoiceQueryRemoteSearch(normalizedQuery: String): MutableList<MediaItem>? {
        val remoteItem =
            kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                kotlinx.coroutines.coroutineScope {
                    val podcastMatch = async { searchPodcastMatch(normalizedQuery) }
                    val episodeMatch = async { searchEpisodeMatch(normalizedQuery) }
                    select<MediaItem?> {
                        podcastMatch.onAwait { result ->
                            if (result != null) {
                                episodeMatch.cancel()
                                result
                            } else {
                                episodeMatch.await()
                            }
                        }
                        episodeMatch.onAwait { result ->
                            if (result != null) {
                                podcastMatch.cancel()
                                result
                            } else {
                                podcastMatch.await()
                            }
                        }
                    }
                }
            }
        if (remoteItem != null) {
            android.util.Log.d("AutoBrowse", "Voice matched remote result")
            return mutableListOf(remoteItem)
        }
        return null
    }

    private fun voiceEpisodeItem(
        episode: cx.aswin.boxlore.core.model.Episode,
        podcastTitle: String?,
        podcastImageUrl: String?,
    ): MediaItem =
        AutoMediaItemFactory.fromEpisode(
            episode = episode,
            source = AutoBrowseContract.SOURCE_SEARCH,
            artworkUri =
                AutoArtworkRepository.remoteUri(
                    host.asContext(),
                    episode.imageUrl ?: episode.podcastImageUrl ?: podcastImageUrl,
                ),
            podcastTitle = podcastTitle,
            groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_search),
        )

    private fun voiceHistoryItem(history: cx.aswin.boxlore.core.data.database.ListeningHistoryEntity): MediaItem =
        AutoMediaItemFactory.fromHistory(
            history = history,
            source = AutoBrowseContract.SOURCE_CONTINUE,
            artworkUri =
                AutoArtworkRepository.remoteUri(
                    host.asContext(),
                    history.episodeImageUrl ?: history.podcastImageUrl,
                ),
            subtitle =
                buildProgressSubtitle(
                    history.podcastName,
                    history.progressMs,
                    history.durationMs,
                ),
            groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_continue),
        )

    private suspend fun handlePlayAllNewEpisodes(): MutableList<MediaItem> {
        android.util.Log.d("AutoBrowse", "Play All New Episodes triggered")
        val subscriptions = host.database.podcastDao().getSubscribedPodcastsList()

        val newEpisodes =
            subscriptions
                .mapNotNull { entity -> entity.latestEpisode?.let { ep -> ep to entity } }
                .sortedByDescending { (ep, _) -> ep.publishedDate }
                .take(20)

        cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_new_episodes"),
        )
        return newEpisodes
            .map { (episode, podcast) ->
                AutoMediaItemFactory.fromEpisode(
                    episode = episode,
                    source = AutoBrowseContract.SOURCE_NEW,
                    artworkUri =
                        AutoArtworkRepository.remoteUri(
                            host.asContext(),
                            episode.imageUrl ?: podcast.imageUrl,
                        ),
                    podcastTitle = podcast.title,
                )
            }.toMutableList()
    }

    private suspend fun handlePlayAllLiked(): MutableList<MediaItem> {
        cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_liked"),
        )
        return host.database
            .listeningHistoryDao()
            .getLikedEpisodesList(50)
            .map { history ->
                AutoMediaItemFactory.fromHistory(
                    history = history,
                    source = AutoBrowseContract.SOURCE_LIKED,
                    artworkUri =
                        AutoArtworkRepository.remoteUri(
                            host.asContext(),
                            history.episodeImageUrl ?: history.podcastImageUrl,
                        ),
                    subtitle =
                        AutoMediaItemFactory.buildDurationSubtitle(
                            history.podcastName,
                            history.durationMs,
                        ),
                    groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_liked),
                )
            }.toMutableList()
    }

    private suspend fun handlePlayAllDownloads(): MutableList<MediaItem> {
        cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_downloads"),
        )
        return getDownloadEpisodeItems().toMutableList()
    }

    private suspend fun handlePlayAllDrivePicks(): MutableList<MediaItem> {
        cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_drive_picks"),
        )
        return getDrivePicksChildren()
            .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_DRIVE_ID }
            .toMutableList()
    }

    private suspend fun handlePlayAllMixtape(): MutableList<MediaItem> {
        cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_mixtape"),
        )
        return getMixtapeChildren()
            .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_MIXTAPE_ID }
            .toMutableList()
    }

    private suspend fun handlePlayFromMixtape(episodeId: String): MutableList<MediaItem> {
        val mixtape =
            getMixtapeChildren()
                .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_MIXTAPE_ID }
        val selectedIndex =
            mixtape.indexOfFirst {
                it.mediaId.stripEpisodePrefix() == episodeId
            }
        if (selectedIndex < 0) return mutableListOf()
        cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_mixtape"),
        )
        return mixtape.drop(selectedIndex).toMutableList()
    }

    private suspend fun handlePlayFromQueue(episodeId: String): MutableList<MediaItem> {
        val queue = host.queueRepository.getQueueSnapshot()
        val selectedIndex = queue.indexOfFirst { it.id == episodeId }
        if (selectedIndex < 0) {
            android.util.Log.w("AutoBrowse", "Ignoring stale queue selection: $episodeId")
            return mutableListOf()
        }
        cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_queue"),
        )
        return queue
            .drop(selectedIndex)
            .map { episode ->
                AutoMediaItemFactory.fromEpisode(
                    episode = episode,
                    source = AutoBrowseContract.SOURCE_QUEUE,
                    artworkUri =
                        AutoArtworkRepository.remoteUri(
                            host.asContext(),
                            episode.imageUrl ?: episode.podcastImageUrl,
                        ),
                    mediaIdPrefix = AutoBrowseContract.QUEUE_PREFIX,
                    groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_queue),
                )
            }.toMutableList()
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
                cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
                    mapOf("entry_point" to "android_auto_play_all"),
                )
                return@future mediaItems.map { resolveMediaItem(it) }.toMutableList()
            }

            val selectedItem = mediaItems.first()
            val searchQuery = selectedItem.requestMetadata.searchQuery

            if (!searchQuery.isNullOrBlank()) {
                android.util.Log.d("AutoBrowse", "Voice play request: '$searchQuery'")
                cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
                    mapOf("entry_point" to "android_auto_voice"),
                )
                return@future handleVoiceSearchQuery(searchQuery)
            }

            when (selectedItem.mediaId) {
                PLAY_ALL_NEW_EPISODES_ID -> return@future handlePlayAllNewEpisodes()
                AutoBrowseContract.PLAY_ALL_LIKED_ID -> return@future handlePlayAllLiked()
                AutoBrowseContract.PLAY_ALL_DOWNLOADS_ID -> return@future handlePlayAllDownloads()
                AutoBrowseContract.PLAY_ALL_DRIVE_ID -> return@future handlePlayAllDrivePicks()
                AutoBrowseContract.PLAY_ALL_MIXTAPE_ID -> return@future handlePlayAllMixtape()
            }

            val source =
                selectedItem.mediaMetadata.extras
                    ?.getString(AutoBrowseContract.EXTRA_SOURCE)
                    ?: AutoBrowseContract.SOURCE_DISCOVER
            if (selectedItem.mediaId.startsWith(AutoBrowseContract.QUEUE_PREFIX)) {
                return@future handlePlayFromQueue(selectedItem.mediaId.stripEpisodePrefix())
            }
            if (source == AutoBrowseContract.SOURCE_MIXTAPE) {
                return@future handlePlayFromMixtape(selectedItem.mediaId.stripEpisodePrefix())
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
        cx.aswin.boxlore.core.data.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_$source"),
        )
        val resolvedItem = resolveMediaItem(selectedItem)
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

    /**
     * Resolve a single MediaItem into a playable one with a proper URI.
     */
    private suspend fun resolveMediaItem(item: MediaItem): MediaItem {
        android.util.Log.d("BoxCastPlayer", "resolveMediaItem: mediaId=${item.mediaId}, initialArtworkUri=${item.mediaMetadata.artworkUri}")
        val episodeId = item.mediaId.stripEpisodePrefix()
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri

        if (uri != null) {
            return item
                .buildUpon()
                .setUri(uri)
                .setCustomCacheKey(episodeId)
                .build()
        }

        val download = host.database.downloadedEpisodeDao().getDownload(episodeId)
        val historyItem = host.database.listeningHistoryDao().getHistoryItem(episodeId)
        val queueItem = host.queueRepository.getQueueItemByEpisodeId(episodeId)
        val resolvedAudioUrl =
            download
                ?.takeIf {
                    it.status ==
                        cx.aswin.boxlore.core.data.database.DownloadedEpisodeEntity.STATUS_COMPLETED
                }?.let { resolveDownloadRequestUri(episodeId) }
                ?: historyItem
                    ?.episodeAudioUrl
                    ?.takeIf { it.isNotBlank() }
                ?: queueItem?.audioUrl?.takeIf { it.isNotBlank() }
        if (resolvedAudioUrl != null) {
            val histArtworkUriStr = historyItem?.episodeImageUrl ?: historyItem?.podcastImageUrl
            android.util.Log.d("BoxCastPlayer", "resolveMediaItem: resolved from history: '$histArtworkUriStr'")
            return MediaItem
                .Builder()
                .setMediaId(item.mediaId)
                .setUri(resolvedAudioUrl)
                .setCustomCacheKey(episodeId)
                .setMediaMetadata(
                    item.mediaMetadata
                        .buildUpon()
                        .setTitle(historyItem?.episodeTitle ?: queueItem?.title)
                        .setArtist(historyItem?.podcastName ?: queueItem?.podcastTitle)
                        .setArtworkUri(
                            AutoArtworkRepository.remoteUri(
                                host.asContext(),
                                histArtworkUriStr ?: queueItem?.imageUrl ?: queueItem?.podcastImageUrl,
                            ),
                        ).setExtras(
                            AutoBrowseContract.mergeExtras(
                                item.mediaMetadata.extras,
                                AutoBrowseContract.itemExtras(
                                    source =
                                        item.mediaMetadata.extras
                                            ?.getString(AutoBrowseContract.EXTRA_SOURCE)
                                            ?: AutoBrowseContract.SOURCE_DISCOVER,
                                    downloadStatus =
                                        if (
                                            download?.status ==
                                            cx.aswin.boxlore.core.data.database.DownloadedEpisodeEntity.STATUS_COMPLETED
                                        ) {
                                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
                                        } else {
                                            null
                                        },
                                ),
                            ),
                        ).build(),
                ).build()
        }

        // Try API
        val episode = host.podcastRepository.getEpisode(episodeId)
        if (episode != null) {
            android.util.Log.d("BoxCastPlayer", "resolveMediaItem: resolved from API: '${episode.imageUrl}'")
            return MediaItem
                .Builder()
                .setMediaId(item.mediaId)
                .setUri(episode.audioUrl)
                .setCustomCacheKey(episodeId)
                .setMediaMetadata(
                    item.mediaMetadata
                        .buildUpon()
                        .setTitle(episode.title)
                        .setArtist(episode.podcastArtist ?: "")
                        .setArtworkUri(
                            AutoArtworkRepository.remoteUri(
                                host.asContext(),
                                episode.imageUrl ?: episode.podcastImageUrl,
                            ),
                        ).build(),
                ).build()
        }

        android.util.Log.e("AutoBrowse", "Could not resolve episode: $episodeId")
        return item
    }

    private suspend fun resolveDomainEpisode(episodeId: String): cx.aswin.boxlore.core.model.Episode? {
        host.queueRepository
            .getQueueSnapshot()
            .firstOrNull { it.id == episodeId }
            ?.let { return it }
        val history = host.database.listeningHistoryDao().getHistoryItem(episodeId)
        val historyAudioUrl = history?.episodeAudioUrl
        if (history != null && historyAudioUrl != null) {
            return cx.aswin.boxlore.core.model.Episode(
                id = history.episodeId,
                title = history.episodeTitle,
                description = history.episodeDescription.orEmpty(),
                audioUrl = historyAudioUrl,
                imageUrl = history.episodeImageUrl,
                podcastImageUrl = history.podcastImageUrl,
                podcastTitle = history.podcastName,
                podcastId = history.podcastId,
                duration = (history.durationMs / 1_000L).toInt(),
                enclosureType = history.enclosureType,
            )
        }
        val download = host.database.downloadedEpisodeDao().getDownload(episodeId)
        if (
            download?.status ==
            cx.aswin.boxlore.core.data.database.DownloadedEpisodeEntity.STATUS_COMPLETED
        ) {
            val audioUrl =
                resolveDownloadRequestUri(episodeId)
                    ?: download.localFilePath
                        .takeIf {
                            it.isNotBlank() && it != "CACHED" && java.io.File(it).isFile
                        }?.let {
                            android.net.Uri
                                .fromFile(java.io.File(it))
                                .toString()
                        }
            if (audioUrl != null) {
                return cx.aswin.boxlore.core.model.Episode(
                    id = download.episodeId,
                    title = download.episodeTitle,
                    description = download.episodeDescription.orEmpty(),
                    audioUrl = audioUrl,
                    imageUrl = download.episodeImageUrl,
                    podcastImageUrl = download.podcastImageUrl,
                    podcastTitle = download.podcastName,
                    podcastId = download.podcastId,
                    duration = (download.durationMs / 1_000L).toInt(),
                    publishedDate = download.publishedDate,
                )
            }
        }
        return host.podcastRepository.getEpisode(episodeId)
    }

    private fun String.stripEpisodePrefix(): String =
        removePrefix(
            AutoBrowseContract.LEARN_PREFIX,
        ).removePrefix(AutoBrowseContract.EPISODE_PREFIX).removePrefix(AutoBrowseContract.QUEUE_PREFIX)

    // ============= Browse Tree Builders =============

    private fun getRootChildren(): List<MediaItem> =
        listOf(
            AutoMediaItemFactory.browsable(
                id = HOME_ID,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_home),
                subtitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_home_subtitle),
                artworkUri = folderArtwork(HOME_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
            AutoMediaItemFactory.browsable(
                id = LIBRARY_ID,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_library),
                subtitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_library_subtitle),
                artworkUri = folderArtwork(LIBRARY_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
            AutoMediaItemFactory.browsable(
                id = DISCOVER_ID,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_discover),
                subtitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_discover_subtitle),
                artworkUri = folderArtwork(DISCOVER_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
            AutoMediaItemFactory.browsable(
                id = DOWNLOADS_ID,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_downloads),
                subtitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_downloads_subtitle),
                artworkUri = folderArtwork(DOWNLOADS_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
        )

    private suspend fun getContinueListeningChildren(): List<MediaItem> {
        val resumeItems = host.database.listeningHistoryDao().getResumeItemsList()
        android.util.Log.d("AutoBrowse", "Continue Listening: ${resumeItems.size} items")

        return resumeItems.map { entity ->
            val subtitle = buildProgressSubtitle(entity.podcastName, entity.progressMs, entity.durationMs)
            AutoMediaItemFactory.fromHistory(
                history = entity,
                source = AutoBrowseContract.SOURCE_CONTINUE,
                artworkUri =
                    AutoArtworkRepository.remoteUri(
                        host.asContext(),
                        entity.episodeImageUrl ?: entity.podcastImageUrl,
                    ),
                subtitle = subtitle,
                groupTitle =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_group_continue,
                    ),
            )
        }
    }

    private suspend fun getSubscriptionsChildren(): List<MediaItem> {
        val subscriptions = host.database.podcastDao().getSubscribedPodcastsList()
        android.util.Log.d("AutoBrowse", "Subscriptions: ${subscriptions.size} podcasts")
        val history = host.database.listeningHistoryDao().getRecentHistoryList(300)
        val scores =
            host.adaptiveCandidateScorer.scorePodcasts(
                podcasts = subscriptions.map { it.toScorable() },
                history = history,
                objective = RankingObjective.YOUR_SHOWS,
                surface = cx.aswin.boxlore.core.data.ranking.RankingSurface.ANDROID_AUTO,
            )
        val rankedSubscriptions =
            subscriptions.sortedByDescending {
                scores[it.podcastId] ?: 0.0
            }

        return rankedSubscriptions.map { entity ->
            AutoMediaItemFactory.browsable(
                id = "$SUBSCRIPTION_PREFIX${entity.podcastId}",
                title = entity.title,
                subtitle = entity.author,
                artworkUri =
                    AutoArtworkRepository.remoteUri(
                        host.asContext(),
                        entity.imageUrl,
                    ),
                mediaType = MediaMetadata.MEDIA_TYPE_PODCAST,
                childStyleExtras =
                    AutoBrowseContract.mergeExtras(
                        AutoBrowseContract.listChildrenExtras(),
                        android.os.Bundle().apply {
                            putString(
                                androidx.media3.session.MediaConstants
                                    .EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE,
                                host.getString(
                                    cx.aswin.boxlore.core.data.R.string.auto_group_subscriptions,
                                ),
                            )
                        },
                    ),
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            )
        }
    }

    private suspend fun getHomeChildren(): List<MediaItem> {
        val newEpCount =
            try {
                host.database
                    .podcastDao()
                    .getSubscribedPodcastsList()
                    .count { it.latestEpisode != null }
            } catch (e: Exception) {
                0
            }
        val newEpSubtitle =
            when {
                newEpCount == 0 -> host.getString(cx.aswin.boxlore.core.data.R.string.auto_new_none)
                newEpCount == 1 -> host.getString(cx.aswin.boxlore.core.data.R.string.auto_new_one)
                else ->
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_new_many,
                        newEpCount,
                    )
            }

        return listOf(
            AutoMediaItemFactory.browsable(
                id = HOME_CONTINUE_LISTENING_ID,
                title =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_continue_listening,
                    ),
                artworkUri = folderArtwork(HOME_CONTINUE_LISTENING_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
            AutoMediaItemFactory.browsable(
                id = AutoBrowseContract.HOME_DRIVE_MIX_ID,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_drive_mix),
                subtitle =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_drive_mix_subtitle,
                    ),
                artworkUri = folderArtwork(AutoBrowseContract.HOME_DRIVE_MIX_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
            AutoMediaItemFactory.browsable(
                id = HOME_NEW_EPISODES_ID,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_whats_new),
                subtitle = newEpSubtitle,
                artworkUri = folderArtwork(HOME_NEW_EPISODES_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
        )
    }

    private fun getDiscoverChildren(): List<MediaItem> {
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val timeLabel =
            when (hour) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..22 -> "Evening"
                else -> "Late Night"
            }

        return listOf(
            AutoMediaItemFactory.browsable(
                id = AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_drive_picks),
                subtitle =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_drive_picks_subtitle,
                    ),
                artworkUri = folderArtwork(AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
            AutoMediaItemFactory.browsable(
                id = AutoBrowseContract.DISCOVER_TIME_PICKS_ID,
                title =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_time_picks,
                        timeLabel,
                    ),
                subtitle =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_time_picks_subtitle,
                    ),
                artworkUri = folderArtwork(AutoBrowseContract.DISCOVER_TIME_PICKS_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
            AutoMediaItemFactory.browsable(
                id = AutoBrowseContract.DISCOVER_GENRES_ID,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_browse_genre),
                subtitle =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_browse_genre_subtitle,
                    ),
                artworkUri = folderArtwork(AutoBrowseContract.DISCOVER_GENRES_ID),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            ),
        )
    }

    private fun getExplorePicksChildren(): List<MediaItem> {
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        return host.getTimeBasedGenres(hour).map { (vibeId, title) ->
            AutoMediaItemFactory.browsable(
                id = "${AutoBrowseContract.CURATED_PREFIX}$vibeId",
                title = title,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                childStyleExtras = AutoBrowseContract.gridChildrenExtras(),
            )
        }
    }

    private fun getGenresChildren(): List<MediaItem> =
        listOf(
            "News" to "News",
            "Technology" to "Tech",
            "Business" to "Business",
            "Comedy" to "Comedy",
            AutoBrowseContract.GENRE_TRUE_CRIME to AutoBrowseContract.GENRE_TRUE_CRIME,
            "Sports" to "Sports",
            "Health" to "Health",
            "History" to "History",
            "Arts" to "Arts",
            "Society & Culture" to "Society",
            "Education" to "Education",
            "Science" to "Science",
            AutoBrowseContract.GENRE_TV_FILM to AutoBrowseContract.GENRE_TV_FILM,
            "Fiction" to "Fiction",
            "Music" to "Music",
            "Religion & Spirituality" to "Religion",
            "Kids & Family" to "Family",
            "Leisure" to "Leisure",
            "Government" to "Government",
        ).map { (category, title) ->
            AutoMediaItemFactory.browsable(
                id = "${AutoBrowseContract.GENRE_PREFIX}$category",
                title = title,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                childStyleExtras = AutoBrowseContract.gridChildrenExtras(),
            )
        }

    private suspend fun getLibraryChildren(): List<MediaItem> =
        getSubscriptionsChildren() +
            listOf(
                AutoMediaItemFactory.browsable(
                    id = AutoBrowseContract.LIBRARY_LIKED_ID,
                    title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_liked_episodes),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                ),
                AutoMediaItemFactory.browsable(
                    id = AutoBrowseContract.LIBRARY_HISTORY_ID,
                    title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_listening_history),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
                    childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                    singleItemStyle =
                        androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                ),
            )

    private suspend fun getLikedChildren(): List<MediaItem> {
        val history = host.database.listeningHistoryDao().getLikedEpisodesList(50)
        if (history.isEmpty()) return emptyList()
        val items =
            history.map {
                AutoMediaItemFactory.fromHistory(
                    history = it,
                    source = AutoBrowseContract.SOURCE_LIKED,
                    artworkUri =
                        AutoArtworkRepository.remoteUri(
                            host.asContext(),
                            it.episodeImageUrl ?: it.podcastImageUrl,
                        ),
                    subtitle =
                        AutoMediaItemFactory.buildDurationSubtitle(
                            it.podcastName,
                            it.durationMs,
                        ),
                    groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_liked),
                )
            }
        return if (items.size > 1) {
            listOf(
                buildPlayAllItem(
                    AutoBrowseContract.PLAY_ALL_LIKED_ID,
                    items.size,
                    AutoBrowseContract.SOURCE_LIKED,
                ),
            ) + items
        } else {
            items
        }
    }

    private suspend fun getHistoryChildren(): List<MediaItem> =
        host.database.listeningHistoryDao().getRecentHistoryList(50).map {
            AutoMediaItemFactory.fromHistory(
                history = it,
                source = AutoBrowseContract.SOURCE_HISTORY,
                artworkUri =
                    AutoArtworkRepository.remoteUri(
                        host.asContext(),
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    ),
                subtitle =
                    AutoMediaItemFactory.buildDurationSubtitle(
                        it.podcastName,
                        it.durationMs,
                    ),
                groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_history),
            )
        }

    private suspend fun getMixtapeChildren(): List<MediaItem> {
        val now = System.currentTimeMillis()
        if (lastMixtape.isNotEmpty() && now - lastMixtapeUpdatedAt < 15 * 60_000L) {
            return lastMixtape
        }
        val subscriptionEntities = host.database.podcastDao().getSubscribedPodcastsList()
        val subscriptions = subscriptionEntities.map { host.toAutoPodcast(it) }
        val history = host.database.listeningHistoryDao().getRecentHistoryList(300)
        var result =
            cx.aswin.boxlore.core.data.MixtapeEngine.build(
                subscriptions = subscriptions,
                history = history,
                adaptiveRanking =
                    cx.aswin.boxlore.core.data.MixtapeEngine.AdaptiveRanking(
                        scorer = host.adaptiveCandidateScorer,
                        surface = cx.aswin.boxlore.core.data.ranking.RankingSurface.ANDROID_AUTO,
                    ),
            )
        if (result.episodes.size < 3) {
            val recommendations =
                runCatching {
                    kotlinx.coroutines.withTimeout(6_000L) {
                        host.smartQueueSources.getPersonalizedRecommendations(
                            history = host.smartQueueSources.getHistoryForRecommendations(25),
                            interests = host.smartQueueSources.getInterests(),
                            country = host.smartQueueSources.getRegion(),
                            subscribedPodcastIds = subscriptions.map { it.id },
                            subscribedGenres = subscriptionEntities.mapNotNull { it.genre }.distinct(),
                        )
                    }
                }.onFailure {
                    android.util.Log.w("AutoBrowse", "Mixtape fallback unavailable", it)
                }.getOrDefault(emptyList())
            result =
                cx.aswin.boxlore.core.data.MixtapeEngine.build(
                    subscriptions = subscriptions,
                    history = history,
                    recommendations = recommendations,
                    adaptiveRanking =
                        cx.aswin.boxlore.core.data.MixtapeEngine.AdaptiveRanking(
                            scorer = host.adaptiveCandidateScorer,
                            surface = cx.aswin.boxlore.core.data.ranking.RankingSurface.ANDROID_AUTO,
                        ),
                )
        }
        val episodes =
            result.podcasts.mapNotNull { podcast ->
                podcast.latestEpisode?.let { episode ->
                    AutoMediaItemFactory.fromEpisode(
                        episode = episode,
                        source = AutoBrowseContract.SOURCE_MIXTAPE,
                        artworkUri =
                            AutoArtworkRepository.remoteUri(
                                host.asContext(),
                                episode.imageUrl ?: episode.podcastImageUrl ?: podcast.imageUrl,
                            ),
                        podcastTitle = podcast.title,
                        groupTitle =
                            host.getString(
                                cx.aswin.boxlore.core.data.R.string.auto_group_mixtape,
                            ),
                    )
                }
            }
        val items =
            if (episodes.size > 1) {
                listOf(
                    buildPlayAllItem(
                        AutoBrowseContract.PLAY_ALL_MIXTAPE_ID,
                        episodes.size,
                        AutoBrowseContract.SOURCE_MIXTAPE,
                    ),
                ) + episodes
            } else {
                episodes
            }
        lastMixtape = items
        lastMixtapeUpdatedAt = now
        return items
    }

    private suspend fun getQueueChildren(): List<MediaItem> =
        host.queueRepository.getQueueSnapshot().take(50).map { episode ->
            AutoMediaItemFactory.fromEpisode(
                episode = episode,
                source = AutoBrowseContract.SOURCE_QUEUE,
                artworkUri =
                    AutoArtworkRepository.remoteUri(
                        host.asContext(),
                        episode.imageUrl ?: episode.podcastImageUrl,
                    ),
                podcastTitle = episode.podcastTitle,
                mediaIdPrefix = AutoBrowseContract.QUEUE_PREFIX,
                groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_queue),
            )
        }

    private suspend fun getDownloadsChildren(): List<MediaItem> {
        val items = getDownloadEpisodeItems()
        return if (items.size > 1) {
            listOf(
                buildPlayAllItem(
                    AutoBrowseContract.PLAY_ALL_DOWNLOADS_ID,
                    items.size,
                    AutoBrowseContract.SOURCE_DOWNLOADS,
                ),
            ) + items
        } else {
            items
        }
    }

    private suspend fun getDownloadEpisodeItems(): List<MediaItem> =
        host.database.downloadedEpisodeDao().getCompletedDownloads(50).map { download ->
            val sourceUri =
                download.localFilePath
                    .takeIf {
                        it.isNotBlank() && it != "CACHED" && java.io.File(it).exists()
                    }?.let {
                        android.net.Uri
                            .fromFile(java.io.File(it))
                            .toString()
                    }
                    ?: resolveDownloadRequestUri(download.episodeId)
                    ?: host.database
                        .listeningHistoryDao()
                        .getHistoryItem(download.episodeId)
                        ?.episodeAudioUrl
                    ?: host.queueRepository.getQueueItemByEpisodeId(download.episodeId)?.audioUrl
            AutoMediaItemFactory.fromDownload(
                download = download,
                artworkUri =
                    AutoArtworkRepository.remoteUri(
                        host.asContext(),
                        download.episodeImageUrl ?: download.podcastImageUrl,
                    ),
                uri = sourceUri,
                groupTitle =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_group_downloads,
                    ),
            )
        }

    private fun resolveDownloadRequestUri(episodeId: String): String? =
        runCatching {
            cx.aswin.boxlore.core.data.DownloadRepository
                .getDownloadManager(host.asContext())
                .downloadIndex
                .getDownload(episodeId)
                ?.request
                ?.uri
                ?.toString()
        }.onFailure {
            android.util.Log.w(
                "AutoBrowse",
                "Unable to resolve cached download URI for $episodeId",
                it,
            )
        }.getOrNull()

    private suspend fun getDrivePicksChildren(): List<MediaItem> {
        val calendar = java.util.Calendar.getInstance()
        val driveVibes = AutoBrowseContract.driveVibes(calendar)
        val region =
            kotlinx.coroutines.withTimeoutOrNull(1_000L) {
                host.userPreferencesRepository.regionStream.first()
            } ?: "us"
        val driveFeeds =
            kotlinx.coroutines
                .withTimeoutOrNull(6_000L) {
                    host.podcastRepository.getCuratedVibes(driveVibes, region)
                }.orEmpty()
        val fallbackFeeds =
            if (driveFeeds.values.all { it.isEmpty() }) {
                val fallbackIds =
                    host
                        .getTimeBasedGenres(
                            calendar.get(java.util.Calendar.HOUR_OF_DAY),
                        ).map { it.first }
                kotlinx.coroutines
                    .withTimeoutOrNull(6_000L) {
                        host.podcastRepository.getCuratedVibes(fallbackIds, region)
                    }.orEmpty()
            } else {
                emptyMap()
            }
        val completedIds =
            host.database
                .listeningHistoryDao()
                .getCompletedEpisodeIds()
                .toSet()
        val recentIds =
            host.database
                .listeningHistoryDao()
                .getRecentHistoryList(30)
                .mapTo(mutableSetOf()) { it.episodeId }
        val feedMap =
            if (driveFeeds.values.any { it.isNotEmpty() }) {
                driveFeeds
            } else {
                fallbackFeeds
            }
        val episodes =
            (driveVibes + feedMap.keys.sorted())
                .distinct()
                .flatMap { feedMap[it].orEmpty() }
                .distinctBy { it.id }
                .mapNotNull { podcast ->
                    podcast.latestEpisode?.let { episode -> episode to podcast }
                }.filter { (episode, _) ->
                    episode.id !in completedIds && episode.id !in recentIds
                }.take(20)
        if (episodes.isEmpty()) {
            return lastDrivePicks.ifEmpty {
                val downloads = getDownloadEpisodeItems()
                if (downloads.isNotEmpty()) downloads.take(20) else getQueueChildren().take(20)
            }
        }

        val items =
            episodes.map { (episode, podcast) ->
                AutoMediaItemFactory.fromEpisode(
                    episode =
                        episode.copy(
                            podcastId = podcast.id,
                            podcastTitle = podcast.title,
                            podcastArtist = podcast.artist,
                            podcastImageUrl = podcast.imageUrl,
                        ),
                    source = AutoBrowseContract.SOURCE_DRIVE,
                    artworkUri =
                        AutoArtworkRepository.remoteUri(
                            host.asContext(),
                            episode.imageUrl ?: podcast.imageUrl,
                        ),
                    podcastTitle = podcast.title,
                    groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_drive),
                )
            }
        val result =
            if (items.size > 1) {
                listOf(
                    buildPlayAllItem(
                        AutoBrowseContract.PLAY_ALL_DRIVE_ID,
                        items.size,
                        AutoBrowseContract.SOURCE_DRIVE,
                    ),
                ) + items
            } else {
                items
            }
        lastDrivePicks = result
        return result
    }

    private suspend fun getNewEpisodesChildren(): List<MediaItem> {
        // Use direct DAO query instead of Flow to avoid hanging
        val subscriptions =
            try {
                host.database.podcastDao().getSubscribedPodcastsList()
            } catch (e: Exception) {
                return emptyList()
            }

        // Get completed episode IDs to exclude (matches phone app behavior)
        val completedIds =
            try {
                host.database
                    .listeningHistoryDao()
                    .getCompletedEpisodeIds()
                    .toSet()
            } catch (e: Exception) {
                emptySet()
            }

        // Extract the newest episode from each subscription, excluding completed ones
        val newEpisodes =
            subscriptions
                .mapNotNull { entity ->
                    entity.latestEpisode?.let { ep ->
                        if (ep.id !in completedIds) ep to entity else null
                    }
                }.sortedByDescending { (ep, _) -> ep.publishedDate }
                .take(20)

        if (newEpisodes.isEmpty()) return emptyList()

        val items = mutableListOf<MediaItem>()

        items.add(
            buildPlayAllItem(
                PLAY_ALL_NEW_EPISODES_ID,
                newEpisodes.size,
                AutoBrowseContract.SOURCE_NEW,
            ),
        )

        items.addAll(
            newEpisodes.map { (ep, pod) ->
                AutoMediaItemFactory.fromEpisode(
                    episode = ep,
                    source = AutoBrowseContract.SOURCE_NEW,
                    artworkUri =
                        AutoArtworkRepository.remoteUri(
                            host.asContext(),
                            ep.imageUrl ?: pod.imageUrl,
                        ),
                    podcastTitle = pod.title,
                    groupTitle =
                        host.getString(
                            cx.aswin.boxlore.core.data.R.string.auto_group_new,
                        ),
                )
            },
        )

        return items
    }

    private suspend fun getCuratedChildren(vibeId: String): List<MediaItem> {
        legacyAutoGenreCategory(vibeId)?.let { return getGenreChildren(it) }
        val region =
            kotlinx.coroutines.withTimeoutOrNull(1_000L) {
                host.userPreferencesRepository.regionStream.first()
            } ?: "us"
        val curatedPodcasts =
            host.podcastRepository.getCuratedPodcasts(
                vibeId,
                region,
            )
        android.util.Log.d(
            "AutoBrowse",
            "Curated $vibeId: ${curatedPodcasts.size} podcasts",
        )
        return buildPodcastFolderItems(curatedPodcasts)
    }

    private suspend fun getGenreChildren(category: String): List<MediaItem> {
        val region =
            kotlinx.coroutines.withTimeoutOrNull(1_000L) {
                host.userPreferencesRepository.regionStream.first()
            } ?: "us"
        val podcasts =
            kotlinx.coroutines
                .withTimeoutOrNull(6_000L) {
                    host.podcastRepository.getTrendingPodcasts(
                        country = region,
                        limit = 50,
                        category = category.lowercase(),
                    )
                }.orEmpty()
        android.util.Log.d(
            "AutoBrowse",
            "Genre chart category=$category country=$region: ${podcasts.size} podcasts",
        )
        return buildPodcastFolderItems(podcasts)
    }

    private fun buildPodcastFolderItems(podcasts: List<cx.aswin.boxlore.core.model.Podcast>): List<MediaItem> =
        podcasts.map { podcast ->
            AutoMediaItemFactory.browsable(
                id = "$SUBSCRIPTION_PREFIX${podcast.id}",
                title = podcast.title,
                subtitle = podcast.artist,
                artworkUri =
                    AutoArtworkRepository.remoteUri(
                        host.asContext(),
                        podcast.imageUrl,
                    ),
                mediaType = MediaMetadata.MEDIA_TYPE_PODCAST,
                childStyleExtras = AutoBrowseContract.listChildrenExtras(),
                singleItemStyle =
                    androidx.media3.session.MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
            )
        }

    private fun legacyAutoGenreCategory(genreId: String): String? =
        when (genreId) {
            "true_crime" -> AutoBrowseContract.GENRE_TRUE_CRIME
            "comedy" -> "Comedy"
            "news" -> "News"
            "technology" -> "Technology"
            "science" -> "Science"
            "health" -> "Health"
            "business" -> "Business"
            "sports" -> "Sports"
            "history" -> "History"
            "society" -> "Society & Culture"
            "education" -> "Education"
            "arts" -> "Arts"
            "music" -> "Music"
            "fiction" -> "Fiction"
            "kids" -> "Kids & Family"
            "self_improvement" -> "Health"
            else -> null
        }

    private suspend fun getPodcastEpisodes(podcastId: String): List<MediaItem> {
        android.util.Log.d("AutoBrowse", "Fetching episodes for podcast: $podcastId")

        // Get podcast details for artwork fallback
        val podcastEntity = host.database.podcastDao().getPodcast(podcastId)
        val podcastArtwork = podcastEntity?.imageUrl

        // Fetch latest episodes (limit to 50 for Auto performance)
        val episodes = host.podcastRepository.getEpisodesPaginated(podcastId, limit = 50, sort = "newest")
        android.util.Log.d("AutoBrowse", "Got ${episodes.episodes.size} episodes for $podcastId")
        val historyById =
            host.database
                .listeningHistoryDao()
                .getRecentHistoryList(300)
                .associateBy { it.episodeId }

        return episodes.episodes.map { episode ->
            val history = historyById[episode.id]
            AutoMediaItemFactory.playable(
                AutoPlayableSpec(
                    mediaId = "episode:${episode.id}",
                    title = episode.title,
                    podcastTitle = podcastEntity?.title ?: episode.podcastTitle,
                    subtitle =
                        AutoMediaItemFactory.buildDurationSubtitle(
                            podcastEntity?.title ?: episode.podcastTitle,
                            episode.duration.toLong() * 1_000L,
                        ),
                    artworkUri =
                        AutoArtworkRepository.remoteUri(
                            host.asContext(),
                            episode.imageUrl ?: podcastArtwork,
                        ),
                    uri = episode.audioUrl,
                    durationMs = episode.duration.toLong() * 1_000L,
                    source = AutoBrowseContract.SOURCE_DISCOVER,
                    progress =
                        history?.let {
                            if (it.durationMs > 0) {
                                it.progressMs.toDouble() / it.durationMs.toDouble()
                            } else {
                                0.0
                            }
                        },
                    isCompleted = history?.isCompleted == true,
                    customCacheKey = episode.id,
                ),
            )
        }
    }

    // ============= Helpers =============

    private fun buildPlayAllItem(
        id: String,
        count: Int,
        source: String,
    ): MediaItem =
        AutoMediaItemFactory.playable(
            AutoPlayableSpec(
                mediaId = id,
                title = host.getString(cx.aswin.boxlore.core.data.R.string.auto_play_all, count),
                podcastTitle =
                    host.getString(
                        cx.aswin.boxlore.core.data.R.string.auto_play_all_subtitle,
                    ),
                source = source,
                supportedCommands = emptyList(),
            ),
        )

    private fun folderArtwork(folderId: String): android.net.Uri? =
        host.autoCollageUris[folderId]
            ?: AutoArtworkRepository.collageUri(host.asContext(), folderId)

    private fun slicePage(
        items: List<MediaItem>,
        page: Int,
        pageSize: Int,
    ): List<MediaItem> {
        val safePageSize = pageSize.takeIf { it > 0 }?.coerceAtMost(50) ?: 50
        val start = page.coerceAtLeast(0) * safePageSize
        if (start >= items.size) return emptyList()
        return items.subList(start, minOf(start + safePageSize, items.size))
    }

    /**
     * Build a subtitle showing remaining time, e.g. "Podcast Name · 35 min left"
     */
    private fun buildProgressSubtitle(
        podcastName: String,
        progressMs: Long,
        durationMs: Long,
    ): String {
        if (durationMs <= 0) return podcastName
        val remainingMs = (durationMs - progressMs).coerceAtLeast(0)
        val remainingMin = remainingMs / 60000
        return when {
            remainingMin > 60 -> {
                val hours = remainingMin / 60
                val mins = remainingMin % 60
                "$podcastName · ${hours}h ${mins}m left"
            }
            remainingMin > 0 -> "$podcastName · $remainingMin min left"
            else -> "$podcastName · Almost done"
        }
    }
}
