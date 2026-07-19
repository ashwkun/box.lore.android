package cx.aswin.boxlore.core.data

import android.content.Context
import androidx.work.WorkerParameters

/**
 * Permanent upgrade stub: old WorkManager FQCN resolves after package align to
 * [cx.aswin.boxlore.core.downloads.SmartDownloadWorker].
 *
 * Do not delete — [cx.aswin.boxlore.LegacyWorkerFactory] + Class.forName(old) both rely on this.
 */
@Deprecated("Permanent upgrade bridge; use cx.aswin.boxlore.core.downloads.SmartDownloadWorker")
class SmartDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : cx.aswin.boxlore.core.downloads.SmartDownloadWorker(appContext, params)
