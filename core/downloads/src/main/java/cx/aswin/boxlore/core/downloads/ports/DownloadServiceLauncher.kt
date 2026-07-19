package cx.aswin.boxlore.core.downloads.ports

import androidx.media3.exoplayer.offline.DownloadService

/**
 * App/playback-owned bridge so [:core:downloads] can start [DownloadService] without
 * [Class.forName] or a compile edge onto [:core:playback].
 *
 * Installed once from [cx.aswin.boxlore.AppContainer] (or Application) before downloads run.
 */
fun interface DownloadServiceLauncher {
    fun mediaDownloadServiceClass(): Class<out DownloadService>
}

object DownloadServiceLauncherHolder {
    @Volatile
    var instance: DownloadServiceLauncher? = null

    fun require(): DownloadServiceLauncher =
        instance
            ?: error(
                "DownloadServiceLauncher not installed. " +
                    "Set DownloadServiceLauncherHolder.instance from AppContainer.",
            )
}
