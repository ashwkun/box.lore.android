package cx.aswin.boxlore.core.data.service

import android.app.Notification
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import cx.aswin.boxlore.core.data.DownloadRepository
import cx.aswin.boxlore.core.data.R

class MediaDownloadService :
    DownloadService(
        FOREGROUND_NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.download_notification_channel_name,
        R.string.download_notification_channel_description,
    ) {
    override fun getDownloadManager(): DownloadManager {
        // This will be initialized by our Hilt entry point or Application class
        // For now, we assume DownloadRepository holds the singleton
        return DownloadRepository.getDownloadManager(this)
    }

    override fun getScheduler(): Scheduler? = if (Util.SDK_INT >= 21) PlatformScheduler(this, JOB_ID) else null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification {
        // Find active download
        val activeDownload =
            downloads.find {
                it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_RESTARTING
            }

        // Count total vs remaining downloads in manager list
        val totalCount = downloads.size
        val completedCount = downloads.count { it.state == Download.STATE_COMPLETED }
        val remainingCount = totalCount - completedCount

        // Extract metadata for the active episode if present
        val activeTitle =
            activeDownload?.let { dl ->
                try {
                    val metaString = String(dl.request.data)
                    val parts = metaString.split("|")
                    if (parts.size >= 3) parts[2] else null
                } catch (e: Exception) {
                    null
                }
            } ?: "Mixtape episode"

        val activePercent =
            if (activeDownload != null && activeDownload.percentDownloaded >= 0f) {
                activeDownload.percentDownloaded.toInt()
            } else {
                0
            }

        // Custom notification details
        val titleText =
            if (remainingCount > 0) {
                "Syncing Mixtape ($remainingCount left)"
            } else {
                "Syncing Mixtape"
            }

        val contentText =
            if (activeDownload != null) {
                "Downloading: $activeTitle ($activePercent%)"
            } else {
                "Preparing next episodes..."
            }

        val builder =
            androidx.core.app.NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(titleText)
                .setContentText(contentText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, activePercent, activeDownload == null || activePercent == 0)

        return builder.build()
    }

    companion object {
        const val JOB_ID = 1
        const val FOREGROUND_NOTIFICATION_ID = 1
        const val CHANNEL_ID = "download_channel"
    }
}
