package cx.aswin.boxlore.updates

import android.app.Activity
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play In-App Updates wiring owned by the Activity shell (not [AppContainer]).
 */
class PlayAppUpdateHelper(
    private val activity: Activity,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
) {
    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(activity)
    }

    fun checkForUpdates() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                val isUpdateAvailable =
                    appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                val updatePriority = appUpdateInfo.updatePriority()
                val daysStale = appUpdateInfo.clientVersionStalenessDays() ?: 0
                val isHighPriority = updatePriority >= 4 || daysStale >= 7

                val updateType = when {
                    isHighPriority && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ->
                        AppUpdateType.IMMEDIATE
                    appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                        AppUpdateType.FLEXIBLE
                    else -> null
                }

                if (isUpdateAvailable && updateType != null) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            updateLauncher,
                            AppUpdateOptions.newBuilder(updateType).build(),
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start update flow", e)
                    }
                }
            }.addOnFailureListener {
                Log.e(TAG, "Failed to check for updates", it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "App update initialization failed", e)
        }
    }

    /** Resume an in-progress immediate update after the Activity returns to foreground. */
    fun resumeInProgressUpdate() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    // Optional: appUpdateManager.completeUpdate() for background downloads
                }
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update status", e)
        }
    }

    companion object {
        private const val TAG = "AppUpdate"
    }
}
