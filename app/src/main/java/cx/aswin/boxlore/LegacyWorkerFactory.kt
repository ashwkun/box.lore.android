package cx.aswin.boxlore

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Permanent upgrade bridge for WorkManager class-name stability.
 *
 * Maps pre-rename worker FQCNs (`cx.aswin.boxcast.*` and transitional
 * `cx.aswin.boxlore.core.data.*`) to current `cx.aswin.boxlore.core.downloads.*`
 * implementations so scheduled work enqueued before package align still resolves
 * after upgrade. Keep this factory — do **not** delete as a “cleanup”.
 *
 * Belt-and-suspenders: thin stubs also remain at the old `core.data` FQCNs.
 */
class LegacyWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val resolvedClassName = LEGACY_WORKER_ALIASES[workerClassName] ?: return null
        return try {
            val clazz = Class.forName(resolvedClassName).asSubclass(ListenableWorker::class.java)
            val constructor = clazz.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            constructor.newInstance(appContext, workerParameters)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /**
         * Public for [LegacyWorkerFactoryTest] / migration-map honesty checks.
         */
        val LEGACY_WORKER_ALIASES: Map<String, String> = mapOf(
            "cx.aswin.boxcast.core.data.SmartDownloadWorker" to
                "cx.aswin.boxlore.core.downloads.SmartDownloadWorker",
            "cx.aswin.boxcast.core.data.AutoDownloadWorker" to
                "cx.aswin.boxlore.core.downloads.AutoDownloadWorker",
            "cx.aswin.boxcast.core.data.PurgeSmartDownloadsWorker" to
                "cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker",
            "cx.aswin.boxlore.core.data.SmartDownloadWorker" to
                "cx.aswin.boxlore.core.downloads.SmartDownloadWorker",
            "cx.aswin.boxlore.core.data.AutoDownloadWorker" to
                "cx.aswin.boxlore.core.downloads.AutoDownloadWorker",
            "cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker" to
                "cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker",
        )
    }
}
