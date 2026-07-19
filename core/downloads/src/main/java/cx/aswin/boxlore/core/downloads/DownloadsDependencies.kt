package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.data.SharedAppDependenciesHolder

import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.downloads.SmartDownloadManager

/**
 * Process-wide façade for the Application-scoped download instances.
 *
 * Installed once from [AppContainer] via [DownloadsDependenciesHolder] in Application.onCreate,
 * alongside [cx.aswin.boxlore.core.data.SharedAppDependenciesHolder].
 *
 * Workers and services that need download/smart-download types call
 * [DownloadsDependenciesHolder.require] instead of constructing new instances.
 */
interface DownloadsDependencies {
    val downloadRepository: DownloadRepository
    val smartDownloadManager: SmartDownloadManager
}

/**
 * Holder for the single [DownloadsDependencies] installed by the Application composition root.
 *
 * Workers call [require] for download-owned types; catalog/prefs/ranking types come from
 * [cx.aswin.boxlore.core.data.SharedAppDependenciesHolder].
 */
object DownloadsDependenciesHolder {
    @Volatile
    var instance: DownloadsDependencies? = null

    fun require(): DownloadsDependencies =
        instance
            ?: error(
                "DownloadsDependencies not installed. " +
                    "Set DownloadsDependenciesHolder.instance from Application after creating AppContainer.",
            )
}
