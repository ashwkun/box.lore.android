package cx.aswin.boxlore.core.playback.service.auto

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.database.toScorable
import kotlinx.coroutines.flow.first

/**
 * Builds Android Auto browse-tree MediaItem lists.
 * Extracted from [AutoBrowseLibraryCallback].
 */
internal class AutoBrowseTreeBuilder(
    private val host: AutoBrowseLibraryHost,
    private val mediaResolver: AutoMediaResolver,
) {
    @Volatile
    private var lastDrivePicks: List<MediaItem> = emptyList()

    @Volatile
    private var lastMixtape: List<MediaItem> = emptyList()

    @Volatile
    private var lastMixtapeUpdatedAt: Long = 0L

    private val HOME_ID = AutoBrowseContract.HOME_ID
    private val LIBRARY_ID = AutoBrowseContract.LIBRARY_ID
    private val DOWNLOADS_ID = AutoBrowseContract.DOWNLOADS_ID
    private val DISCOVER_ID = AutoBrowseContract.DISCOVER_ID
    private val HOME_CONTINUE_LISTENING_ID = AutoBrowseContract.HOME_CONTINUE_ID
    private val HOME_NEW_EPISODES_ID = AutoBrowseContract.HOME_NEW_EPISODES_ID
    private val PLAY_ALL_NEW_EPISODES_ID = AutoBrowseContract.PLAY_ALL_NEW_ID
    private val SUBSCRIPTION_PREFIX = AutoBrowseContract.SUBSCRIPTION_PREFIX

    fun getRootChildren(): List<MediaItem> =
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

    suspend fun getContinueListeningChildren(): List<MediaItem> {
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

    suspend fun getSubscriptionsChildren(): List<MediaItem> {
        val subscriptions = host.database.podcastDao().getSubscribedPodcastsList()
        android.util.Log.d("AutoBrowse", "Subscriptions: ${subscriptions.size} podcasts")
        val history = host.database.listeningHistoryDao().getRecentHistoryList(300)
        val scores =
            host.adaptiveCandidateScorer.scorePodcasts(
                podcasts = subscriptions.map { it.toScorable() },
                history = history,
                objective = RankingObjective.YOUR_SHOWS,
                surface = cx.aswin.boxlore.core.ranking.RankingSurface.ANDROID_AUTO,
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

    suspend fun getHomeChildren(): List<MediaItem> {
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

    fun getDiscoverChildren(): List<MediaItem> {
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

    fun getExplorePicksChildren(): List<MediaItem> {
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

    fun getGenresChildren(): List<MediaItem> =
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

    suspend fun getLibraryChildren(): List<MediaItem> =
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

    suspend fun getLikedChildren(): List<MediaItem> {
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

    suspend fun getHistoryChildren(): List<MediaItem> =
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

    suspend fun getMixtapeChildren(): List<MediaItem> {
        val now = System.currentTimeMillis()
        if (lastMixtape.isNotEmpty() && now - lastMixtapeUpdatedAt < 15 * 60_000L) {
            return lastMixtape
        }
        val subscriptionEntities = host.database.podcastDao().getSubscribedPodcastsList()
        val subscriptions = subscriptionEntities.map { host.toAutoPodcast(it) }
        val history = host.database.listeningHistoryDao().getRecentHistoryList(300)
        var result =
            cx.aswin.boxlore.core.playback.MixtapeEngine.build(
                subscriptions = subscriptions,
                history = history,
                adaptiveRanking =
                    cx.aswin.boxlore.core.playback.MixtapeEngine.AdaptiveRanking(
                        scorer = host.adaptiveCandidateScorer,
                        surface = cx.aswin.boxlore.core.ranking.RankingSurface.ANDROID_AUTO,
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
                cx.aswin.boxlore.core.playback.MixtapeEngine.build(
                    subscriptions = subscriptions,
                    history = history,
                    recommendations = recommendations,
                    adaptiveRanking =
                        cx.aswin.boxlore.core.playback.MixtapeEngine.AdaptiveRanking(
                            scorer = host.adaptiveCandidateScorer,
                            surface = cx.aswin.boxlore.core.ranking.RankingSurface.ANDROID_AUTO,
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

    suspend fun getQueueChildren(): List<MediaItem> =
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

    suspend fun getDownloadsChildren(): List<MediaItem> {
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

    suspend fun getDownloadEpisodeItems(): List<MediaItem> =
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
                    ?: mediaResolver.resolveDownloadRequestUri(download.episodeId)
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

    suspend fun getDrivePicksChildren(): List<MediaItem> {
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

    suspend fun getNewEpisodesChildren(): List<MediaItem> {
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

    suspend fun getCuratedChildren(vibeId: String): List<MediaItem> {
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

    suspend fun getGenreChildren(category: String): List<MediaItem> {
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

    suspend fun getPodcastEpisodes(podcastId: String): List<MediaItem> {
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

    fun slicePage(
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
    fun buildProgressSubtitle(
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
