package cx.aswin.boxlore.core.data.service.auto

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select

/**
 * Android Auto voice search and play-all / play-from handlers.
 * Extracted from [AutoBrowseLibraryCallback].
 */
internal class AutoVoiceSearchHandler(
    private val host: AutoBrowseLibraryHost,
    private val treeBuilder: AutoBrowseTreeBuilder,
) {
    private val SUBSCRIPTION_PREFIX = AutoBrowseContract.SUBSCRIPTION_PREFIX

    suspend fun buildSearchResults(query: String): List<MediaItem> {
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

    suspend fun handleVoiceSearchQuery(searchQuery: String): MutableList<MediaItem> {
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
            treeBuilder.getDownloadEpisodeItems().take(1).toMutableList()
        }
    }

    private suspend fun handleVoiceQueryQuickFallbacks(
        rawQuery: String,
        normalizedQuery: String,
    ): MutableList<MediaItem>? {
        if (rawQuery.contains("download") || rawQuery.contains("offline")) {
            return treeBuilder.getDownloadEpisodeItems().toMutableList()
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
                treeBuilder.buildProgressSubtitle(
                    history.podcastName,
                    history.progressMs,
                    history.durationMs,
                ),
            groupTitle = host.getString(cx.aswin.boxlore.core.data.R.string.auto_group_continue),
        )

    suspend fun handlePlayAllNewEpisodes(): MutableList<MediaItem> {
        android.util.Log.d("AutoBrowse", "Play All New Episodes triggered")
        val subscriptions = host.database.podcastDao().getSubscribedPodcastsList()

        val newEpisodes =
            subscriptions
                .mapNotNull { entity -> entity.latestEpisode?.let { ep -> ep to entity } }
                .sortedByDescending { (ep, _) -> ep.publishedDate }
                .take(20)

        cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
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

    suspend fun handlePlayAllLiked(): MutableList<MediaItem> {
        cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
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

    suspend fun handlePlayAllDownloads(): MutableList<MediaItem> {
        cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_downloads"),
        )
        return treeBuilder.getDownloadEpisodeItems().toMutableList()
    }

    suspend fun handlePlayAllDrivePicks(): MutableList<MediaItem> {
        cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_drive_picks"),
        )
        return treeBuilder.getDrivePicksChildren()
            .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_DRIVE_ID }
            .toMutableList()
    }

    suspend fun handlePlayAllMixtape(): MutableList<MediaItem> {
        cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_mixtape"),
        )
        return treeBuilder.getMixtapeChildren()
            .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_MIXTAPE_ID }
            .toMutableList()
    }

    suspend fun handlePlayFromMixtape(episodeId: String): MutableList<MediaItem> {
        val mixtape =
            treeBuilder.getMixtapeChildren()
                .filterNot { it.mediaId == AutoBrowseContract.PLAY_ALL_MIXTAPE_ID }
        val selectedIndex =
            mixtape.indexOfFirst {
                it.mediaId.stripEpisodePrefix() == episodeId
            }
        if (selectedIndex < 0) return mutableListOf()
        cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
            mapOf("entry_point" to "android_auto_mixtape"),
        )
        return mixtape.drop(selectedIndex).toMutableList()
    }

    suspend fun handlePlayFromQueue(episodeId: String): MutableList<MediaItem> {
        val queue = host.queueRepository.getQueueSnapshot()
        val selectedIndex = queue.indexOfFirst { it.id == episodeId }
        if (selectedIndex < 0) {
            android.util.Log.w("AutoBrowse", "Ignoring stale queue selection: $episodeId")
            return mutableListOf()
        }
        cx.aswin.boxlore.core.analytics.PendingEntryPoint.set(
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
}
