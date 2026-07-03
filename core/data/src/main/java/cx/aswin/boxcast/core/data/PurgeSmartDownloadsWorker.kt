package cx.aswin.boxcast.core.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cx.aswin.boxcast.core.data.database.BoxLoreDatabase

class PurgeSmartDownloadsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i("BoxLore_BackgroundTrace", "[Worker] PurgeSmartDownloadsWorker started.")
        val context = applicationContext
        val database = BoxLoreDatabase.getDatabase(context)
        val downloadRepository = DownloadRepository(context, database)

        try {
            val existingDownloads = database.downloadedEpisodeDao().getAllDownloadsSync()
            var count = 0
            for (download in existingDownloads) {
                if (download.isSmartDownloaded) {
                    Log.d("PurgeWorker", "Purging smart-downloaded episode: '${download.episodeTitle}' (ID: ${download.episodeId})")
                    downloadRepository.removeDownload(download.episodeId)
                    count++
                }
            }
            Log.i("BoxLore_BackgroundTrace", "[Worker] PurgeSmartDownloadsWorker finished. Purged $count episodes.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("BoxLore_BackgroundTrace", "[Worker] PurgeSmartDownloadsWorker failed", e)
            return Result.failure()
        }
    }
}
