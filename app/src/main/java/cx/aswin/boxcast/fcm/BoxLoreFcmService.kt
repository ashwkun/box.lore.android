package cx.aswin.boxcast.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import cx.aswin.boxcast.core.data.UserPreferencesRepository
import cx.aswin.boxcast.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.google.firebase.messaging.FirebaseMessaging

class BoxLoreFcmService : FirebaseMessagingService() {

    private val CHANNEL_ID = "boxcast_announcements"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Subscribe to the global announcements topic
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
        
        // Subscribe to environment-specific topic safely by clearing the antagonist topic
        if (cx.aswin.boxcast.BuildConfig.DEBUG) {
            FirebaseMessaging.getInstance().subscribeToTopic("debug_users")
            FirebaseMessaging.getInstance().unsubscribeFromTopic("prod_users")
        } else {
            FirebaseMessaging.getInstance().subscribeToTopic("prod_users")
            FirebaseMessaging.getInstance().unsubscribeFromTopic("debug_users")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        if (data.isNotEmpty()) {
            val type = data["type"]
            if (type == "new_episode") {
                handleNewEpisodeMessage(data)
                return
            }

            val title = data["title"] ?: "BoxCast Update"
            val body = data["body"] ?: "Check out what's new in BoxCast!"
            val messageType = type ?: "both" // push, in-app, both
            val route = data["route"]
            val imageUrl = data["image"]

            if (messageType == "in-app" || messageType == "both") {
                saveInAppAnnouncement(title, body, route, imageUrl)
            }

            if (messageType == "push" || messageType == "both") {
                showPushNotification(title, body, route, imageUrl)
            }
        }
    }

    private fun handleNewEpisodeMessage(data: Map<String, String>) {
        val podcastId = data["podcastId"] ?: return
        val episodeId = data["episodeId"] ?: return
        val podcastTitle = data["podcastTitle"] ?: "New Release"
        val episodeTitle = data["episodeTitle"] ?: "New Episode"
        val imageUrl = data["image"] ?: data["imageUrl"]
        val route = data["route"] ?: "boxlore://podcast/$podcastId"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "boxcast_new_episodes"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "New Episodes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for new podcast episodes"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(route)).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("from_push", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            podcastId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val duration = data["duration"]?.toIntOrNull() ?: 0
        val bodyText = if (duration > 0) "\"$episodeTitle\" ($duration mins)" else "\"$episodeTitle\""

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(cx.aswin.boxcast.R.drawable.ic_notification_custom)
            .setColor(android.graphics.Color.parseColor("#5B5BD6"))
            .setContentTitle("New Episode • $podcastTitle")
            .setContentText(bodyText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (!imageUrl.isNullOrBlank()) {
            try {
                val url = java.net.URL(imageUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val bitmap = android.graphics.BitmapFactory.decodeStream(connection.inputStream)
                if (bitmap != null) {
                    notificationBuilder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as android.graphics.Bitmap?)
                    )
                    notificationBuilder.setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                // Ignore image fetch failure
            }
        }

        notificationManager.notify(podcastId.hashCode(), notificationBuilder.build())

        // Trigger the per-podcast Auto-Download check
        triggerAutoDownload(podcastId, episodeId)

        // Trigger Smart Download sync automatically to fetch new content in background
        triggerSmartDownloadSync()
    }

    private fun triggerAutoDownload(podcastId: String, episodeId: String) {
        try {
            android.util.Log.i("BoxLore_BackgroundTrace", "[FCM] Received new_episode trigger for podcastId: $podcastId, episodeId: $episodeId")
            
            CoroutineScope(Dispatchers.IO).launch {
                val userPrefs = UserPreferencesRepository(applicationContext)
                val wifiOnly = userPrefs.autoDownloadWifiOnlyStream.first()
                val requiredNetwork = if (wifiOnly) androidx.work.NetworkType.UNMETERED else androidx.work.NetworkType.CONNECTED
                
                android.util.Log.i("BoxLore_BackgroundTrace", "[FCM] Preparing AutoDownloadWorker. wifiOnly=$wifiOnly -> networkConstraint=$requiredNetwork")

                val inputData = androidx.work.Data.Builder()
                    .putString(cx.aswin.boxcast.core.data.AutoDownloadWorker.KEY_PODCAST_ID, podcastId)
                    .putString(cx.aswin.boxcast.core.data.AutoDownloadWorker.KEY_EPISODE_ID, episodeId)
                    .build()

                val workRequest = androidx.work.OneTimeWorkRequestBuilder<cx.aswin.boxcast.core.data.AutoDownloadWorker>()
                    .setInputData(inputData)
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(requiredNetwork)
                            .build()
                    )
                    .build()

                androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
                android.util.Log.i("BoxLore_BackgroundTrace", "[FCM] Successfully enqueued AutoDownloadWorker into WorkManager for podcast $podcastId")
            }
        } catch (e: Exception) {
            android.util.Log.e("BoxLore_BackgroundTrace", "[FCM] Error enqueuing AutoDownloadWorker", e)
        }
    }

    private fun triggerSmartDownloadSync() {
        try {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<cx.aswin.boxcast.core.data.SmartDownloadWorker>()
                .build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
            android.util.Log.d("BoxLoreFcmService", "Triggered immediate SmartDownloadWorker sync due to new episode push notification")
        } catch (e: Exception) {
            android.util.Log.e("BoxLoreFcmService", "Failed to trigger background sync", e)
        }
    }

    private fun saveInAppAnnouncement(title: String, body: String, route: String?, imageUrl: String?) {
        val prefs = UserPreferencesRepository(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            prefs.setAnnouncement(title, body, route, imageUrl, System.currentTimeMillis())
        }
    }

    private fun showPushNotification(title: String, body: String, route: String?, imageUrl: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Announcements",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "BoxCast news and updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Setup intent (open app, or deep link)
        val intent = if (route != null && route.startsWith("http")) {
            Intent(Intent.ACTION_VIEW, Uri.parse(route)).apply {
                putExtra("from_push", true)
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("from_push", true)
                // If we want internal routing via deep link, we pass an extra
                if (route != null) {
                    putExtra("target_route", route)
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )



        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(cx.aswin.boxcast.R.drawable.ic_notification_custom)
            .setColor(android.graphics.Color.parseColor("#5B5BD6")) // Brand purple color matching launcher icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (!imageUrl.isNullOrBlank()) {
            try {
                val url = java.net.URL(imageUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val bitmap = android.graphics.BitmapFactory.decodeStream(connection.inputStream)
                if (bitmap != null) {
                    notificationBuilder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as android.graphics.Bitmap?)
                    )
                    notificationBuilder.setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                // Ignore image fetch failure
            }
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
