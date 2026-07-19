package cx.aswin.boxlore.core.data.service.auto

import androidx.media3.common.MediaItem

/**
 * Resolves Auto browse MediaItems and domain episodes into playable URIs.
 * Extracted from [AutoBrowseLibraryCallback].
 */
internal class AutoMediaResolver(
    private val host: AutoBrowseLibraryHost,
) {
    suspend fun resolveMediaItem(item: MediaItem): MediaItem {
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

    suspend fun resolveDomainEpisode(episodeId: String): cx.aswin.boxlore.core.model.Episode? {
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

    fun resolveDownloadRequestUri(episodeId: String): String? =
        runCatching {
            cx.aswin.boxlore.core.downloads.DownloadRepository
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
}
