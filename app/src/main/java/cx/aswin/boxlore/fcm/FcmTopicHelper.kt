package cx.aswin.boxlore.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.core.data.SubscriptionRepository
import java.io.File

/** FCM topic subscribe helpers and post-restore reconciliation. */
object FcmTopicHelper {
    private const val TAG = "Firebase"
    private const val SENTINEL_NAME = "fcm_topics_synced"

    /** Subscribe to broadcast topics (all_users + debug/prod). */
    fun subscribeDefaultTopics() {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            if (BuildConfig.DEBUG) {
                FirebaseMessaging.getInstance().subscribeToTopic("debug_users")
                FirebaseMessaging.getInstance().unsubscribeFromTopic("prod_users")
            } else {
                FirebaseMessaging.getInstance().subscribeToTopic("prod_users")
                FirebaseMessaging.getInstance().unsubscribeFromTopic("debug_users")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed FCM init", e)
        }
    }

    /**
     * After a backup restore, re-subscribe per-podcast topics once (sentinel in noBackupFilesDir).
     */
    suspend fun reconcileAfterRestoreIfNeeded(
        context: Context,
        subscriptionRepository: SubscriptionRepository,
    ) {
        val sentinel = File(context.noBackupFilesDir, SENTINEL_NAME)
        if (!sentinel.exists()) {
            subscriptionRepository.reconcileFcmTopicSubscriptions()
            try {
                sentinel.createNewFile()
            } catch (e: Exception) {
                Log.e("FCM_Topic", "Failed to write sentinel", e)
            }
        }
    }
}
