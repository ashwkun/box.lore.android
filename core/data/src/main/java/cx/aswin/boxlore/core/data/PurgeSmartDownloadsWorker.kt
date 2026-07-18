package cx.aswin.boxlore.core.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PurgeSmartDownloadsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i("BoxLore_BackgroundTrace", "[Worker] PurgeSmartDownloadsWorker started.")
        val deps = SharedAppDependenciesHolder.require()
        val database = deps.database
        val downloadRepository = deps.downloadRepository

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
