package cx.aswin.boxcast.util

import android.content.Context
import android.util.Log

private const val PLAY_STORE_PACKAGE = "com.android.vending"
private const val TAG = "InstallSource"

/** True when this app install was delivered by the Google Play Store. */
fun Context.isInstalledFromPlayStore(): Boolean {
    return try {
        packageManager.getInstallSourceInfo(packageName).installingPackageName == PLAY_STORE_PACKAGE
    } catch (e: Exception) {
        Log.w(TAG, "Failed to resolve install source; treating as non-Play", e)
        false
    }
}
