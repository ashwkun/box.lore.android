package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.data.SharedAppDependenciesHolder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
import kotlinx.coroutines.flow.first

open class SmartDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d("SmartDownloadWorker", "WorkManager daily check triggered.")

        val sharedDeps = SharedAppDependenciesHolder.require()
        val userPrefs = sharedDeps.userPreferencesRepository

        // Check if enabled first
        val isEnabled = userPrefs.smartDownloadsEnabledStream.first()
        if (!isEnabled) {
            Log.d("SmartDownloadWorker", "Smart downloads is disabled. Skipping worker execution.")
            return Result.success()
        }

        try {
            val downloadDeps = DownloadsDependenciesHolder.require()
            val success = downloadDeps.smartDownloadManager.performSync(isForeground = false)
            return if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e("SmartDownloadWorker", "Worker execution failed", e)
            return Result.failure()
        }
    }
}
