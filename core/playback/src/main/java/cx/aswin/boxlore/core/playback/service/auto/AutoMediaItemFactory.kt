package cx.aswin.boxlore.core.playback.service.auto

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode

internal data class AutoPlayableSpec(
    val mediaId: String,
    val title: CharSequence,
    val podcastTitle: CharSequence?,
    val source: String,
    val subtitle: CharSequence? = podcastTitle,
    val artworkUri: Uri? = null,
    val uri: String? = null,
    val durationMs: Long? = null,
    val groupTitle: String? = null,
    val progress: Double? = null,
    val isCompleted: Boolean = false,
    val isDownloaded: Boolean = false,
    val customCacheKey: String? = null,
    val supportedCommands: List<String>? = null,
)

@OptIn(UnstableApi::class)
internal object AutoMediaItemFactory {
    private val episodeCommands =
        listOf(
            AutoBrowseContract.COMMAND_TOGGLE_LIKE,
            AutoBrowseContract.COMMAND_ADD_TO_QUEUE,
            AutoBrowseContract.COMMAND_MARK_COMPLETE,
        )

    fun browsable(
        id: String,
        title: CharSequence,
        subtitle: CharSequence? = null,
        artworkUri: Uri? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        childStyleExtras: Bundle = AutoBrowseContract.listChildrenExtras(),
        singleItemStyle: Int? = null,
    ): MediaItem {
        val extras =
            if (singleItemStyle == null) {
                childStyleExtras
            } else {
                AutoBrowseContract.mergeExtras(
                    childStyleExtras,
                    Bundle().apply {
                        putInt(
                            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                            singleItemStyle,
                        )
                    },
                )
            }
        return MediaItem
            .Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .setExtras(extras)
                    .build(),
            ).build()
    }

    fun playable(spec: AutoPlayableSpec): MediaItem {
        val extras =
            AutoBrowseContract.itemExtras(
                source = spec.source,
                groupTitle = spec.groupTitle,
                progress = spec.progress,
                isCompleted = spec.isCompleted,
                downloadStatus =
                    if (spec.isDownloaded) {
                        MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
                    } else {
                        null
                    },
            )
        return MediaItem
            .Builder()
            .setMediaId(spec.mediaId)
            .apply {
                if (!spec.uri.isNullOrBlank() && spec.uri != "CACHED") setUri(spec.uri)
                if (!spec.customCacheKey.isNullOrBlank()) {
                    setCustomCacheKey(spec.customCacheKey)
                }
            }.setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(spec.title)
                    .setSubtitle(spec.subtitle)
                    .setArtist(spec.podcastTitle)
                    .setAlbumTitle(spec.podcastTitle)
                    .setArtworkUri(spec.artworkUri)
                    .apply { spec.durationMs?.takeIf { it > 0 }?.let(::setDurationMs) }
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setSupportedCommands(spec.supportedCommands ?: episodeCommands)
                    .setExtras(extras)
                    .build(),
            ).build()
    }

    fun fromHistory(
        history: ListeningHistoryEntity,
        source: String,
        artworkUri: Uri?,
        subtitle: String,
        groupTitle: String,
    ): MediaItem {
        val progress =
            if (history.durationMs > 0) {
                history.progressMs.toDouble() / history.durationMs.toDouble()
            } else {
                0.0
            }
        return playable(
            AutoPlayableSpec(
                mediaId = "episode:${history.episodeId}",
                title = history.episodeTitle,
                podcastTitle = history.podcastName,
                subtitle = subtitle,
                artworkUri = artworkUri,
                uri = history.episodeAudioUrl,
                durationMs = history.durationMs,
                source = source,
                groupTitle = groupTitle,
                progress = progress,
                isCompleted = history.isCompleted,
                customCacheKey = history.episodeId,
            ),
        )
    }

    fun fromEpisode(
        episode: Episode,
        source: String,
        artworkUri: Uri?,
        podcastTitle: String? = episode.podcastTitle,
        groupTitle: String? = null,
        mediaIdPrefix: String = "episode:",
        isDownloaded: Boolean = false,
    ): MediaItem =
        playable(
            AutoPlayableSpec(
                mediaId = "$mediaIdPrefix${episode.id}",
                title = episode.title,
                podcastTitle = podcastTitle,
                subtitle = buildDurationSubtitle(podcastTitle, episode.duration.toLong() * 1_000L),
                artworkUri = artworkUri,
                uri = episode.audioUrl,
                durationMs = episode.duration.toLong() * 1_000L,
                source = source,
                groupTitle = groupTitle,
                isDownloaded = isDownloaded,
                customCacheKey = episode.id,
            ),
        )

    fun fromDownload(
        download: DownloadedEpisodeEntity,
        artworkUri: Uri?,
        uri: String?,
        groupTitle: String,
    ): MediaItem =
        playable(
            AutoPlayableSpec(
                mediaId = "episode:${download.episodeId}",
                title = download.episodeTitle,
                podcastTitle = download.podcastName,
                subtitle = buildDurationSubtitle(download.podcastName, download.durationMs),
                artworkUri = artworkUri,
                uri = uri,
                durationMs = download.durationMs,
                source = AutoBrowseContract.SOURCE_DOWNLOADS,
                groupTitle = groupTitle,
                isDownloaded = true,
                customCacheKey = download.episodeId,
            ),
        )

    fun buildDurationSubtitle(
        podcastTitle: String?,
        durationMs: Long,
    ): String {
        val duration = formatDuration(durationMs)
        return listOfNotNull(
            podcastTitle?.takeIf(String::isNotBlank),
            duration.takeIf(String::isNotBlank),
        ).joinToString(" · ")
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return ""
        val totalMinutes = durationMs / 60_000L
        return when {
            totalMinutes >= 60 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
            totalMinutes > 0 -> "$totalMinutes min"
            else -> "< 1 min"
        }
    }
}
