package cx.aswin.boxlore.core.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class SmartDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d("SmartDownloadWorker", "WorkManager daily check triggered.")

        val deps = SharedAppDependenciesHolder.require()
        val userPrefs = deps.userPreferencesRepository

        // Check if enabled first
        val isEnabled = userPrefs.smartDownloadsEnabledStream.first()
        if (!isEnabled) {
            Log.d("SmartDownloadWorker", "Smart downloads is disabled. Skipping worker execution.")
            return Result.success()
        }

        try {
            val success = deps.smartDownloadManager.performSync(isForeground = false)
            return if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e("SmartDownloadWorker", "Worker execution failed", e)
            return Result.failure()
        }
    }
}
