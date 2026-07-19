package cx.aswin.boxlore.core.data

import android.content.Context
import androidx.work.WorkerParameters

/**
 * Permanent upgrade stub: old WorkManager FQCN resolves after package align to
 * [cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker].
 */
@Deprecated("Permanent upgrade bridge; use cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker")
class PurgeSmartDownloadsWorker(
    appContext: Context,
    params: WorkerParameters,
) : cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker(appContext, params)
