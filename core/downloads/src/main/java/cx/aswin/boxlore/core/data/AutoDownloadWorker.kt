package cx.aswin.boxlore.core.data

import android.content.Context
import androidx.work.WorkerParameters

/**
 * Permanent upgrade stub: old WorkManager FQCN resolves after package align to
 * [cx.aswin.boxlore.core.downloads.AutoDownloadWorker].
 */
@Deprecated("Permanent upgrade bridge; use cx.aswin.boxlore.core.downloads.AutoDownloadWorker")
class AutoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : cx.aswin.boxlore.core.downloads.AutoDownloadWorker(appContext, params)
