package cx.aswin.boxlore.core.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_episodes")
data class DownloadedEpisodeEntity(
    @PrimaryKey
    val episodeId: String,
    val podcastId: String,
    // Store critical metadata to display in Library without network
    val episodeTitle: String,
    val episodeDescription: String?,
    val episodeImageUrl: String?,
    val podcastName: String,
    val podcastImageUrl: String?,
    val durationMs: Long,
    val publishedDate: Long,
    
    // Download Specifics
    val localFilePath: String, // Path to file on disk
    val downloadId: Long,      // ID from DownloadManager (if using system manager)
    val downloadedAt: Long,
    val sizeBytes: Long,
    val status: Int = STATUS_QUEUED,
    val isSmartDownloaded: Boolean = false
) {
    companion object {
        const val STATUS_QUEUED = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_COMPLETED = 2
        const val STATUS_FAILED = 3
    }
}
