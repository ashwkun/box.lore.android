package cx.aswin.boxlore.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import cx.aswin.boxlore.core.data.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.BoxLoreApplication
import cx.aswin.boxlore.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.google.firebase.messaging.FirebaseMessaging
import cx.aswin.boxlore.core.designsystem.components.optimizedImageUrl
import cx.aswin.boxlore.navigation.PushTargetRouteAllowlist
import cx.aswin.boxlore.ui.announcement.shouldSuppressWhatsNewOnPlay

class BoxLoreFcmService : FirebaseMessagingService() {

    private val CHANNEL_ID = "boxlore_announcements_v2"

    private fun userPreferences(): UserPreferencesRepository =
        (application as? BoxLoreApplication)?.userPreferencesRepository
            ?: SharedAppDependenciesHolder.require().userPreferencesRepository

    // Firebase Messaging still delivers rotation callbacks here; TokenWatcher migration is follow-up.
    @Suppress("DEPRECATION")
    @Deprecated("Overrides deprecated FirebaseMessagingService.onNewToken")
    override fun onNewToken(token: String) {
        @Suppress("DEPRECATION")
        super.onNewToken(token)
        // Subscribe to the global announcements topic
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
        
        // Subscribe to environment-specific topic safely by clearing the antagonist topic
        if (cx.aswin.boxlore.BuildConfig.DEBUG) {
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

            val parsed = FcmPayloadParser.parse(data)

            if (parsed.type == "in-app" || parsed.type == "both") {
                saveInAppAnnouncement(
                    parsed.title,
                    parsed.body,
                    parsed.route,
                    parsed.imageUrl,
                    parsed.actionLabel,
                    parsed.showActionInApp,
                    parsed.category
                )
            }

            if (parsed.type == "push" || parsed.type == "both") {
                if (applicationContext.shouldSuppressWhatsNewOnPlay(parsed.category)) {
                    android.util.Log.d(
                        "BoxLoreFcmService",
                        "Skipping Whats New push on Play Store install (category=${parsed.category})",
                    )
                } else {
                    showPushNotification(
                        parsed.title,
                        parsed.body,
                        parsed.route,
                        parsed.imageUrl,
                        parsed.sound,
                        parsed.actionLabel,
                        parsed.showActionInPush
                    )
                }
            }
        }
    }

    private fun handleNewEpisodeMessage(data: Map<String, String>) {
        val podcastId = data["podcastId"] ?: return
        val episodeId = data["episodeId"] ?: return
        val podcastTitle = data["podcastTitle"] ?: "New Release"
        val episodeTitle = data["episodeTitle"] ?: "New Episode"
        val imageUrl = data["image"] ?: data["imageUrl"]
        val rawRoute = data["route"] ?: "boxlore://podcast/$podcastId"
        val route = if (rawRoute.startsWith("boxlore://episode/")) {
            "boxlore://episode/$episodeId?autoplay=false&podcastId=${Uri.encode(podcastId)}&podcastTitle=${Uri.encode(podcastTitle)}"
        } else {
            rawRoute
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "boxlore_new_episodes_v1"
        val soundUri = Uri.parse("android.resource://$packageName/raw/boxlore_chime")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val channel = NotificationChannel(
                channelId,
                "New Episodes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for new podcast episodes"
                setSound(soundUri, audioAttributes)
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
            .setSmallIcon(cx.aswin.boxlore.R.drawable.ic_notification_custom)
            .setColor(android.graphics.Color.parseColor("#5B5BD6"))
            .setContentTitle("New Episode • $podcastTitle")
            .setContentText(bodyText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)

        if (!imageUrl.isNullOrBlank()) {
            try {
                val optimizedUrl = imageUrl.optimizedImageUrl(500)
                val url = java.net.URL(optimizedUrl)
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
                val userPrefs = userPreferences()
                val wifiOnly = userPrefs.autoDownloadWifiOnlyStream.first()
                val requiredNetwork = if (wifiOnly) androidx.work.NetworkType.UNMETERED else androidx.work.NetworkType.CONNECTED
                
                android.util.Log.i("BoxLore_BackgroundTrace", "[FCM] Preparing AutoDownloadWorker. wifiOnly=$wifiOnly -> networkConstraint=$requiredNetwork")

                val inputData = androidx.work.Data.Builder()
                    .putString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_PODCAST_ID, podcastId)
                    .putString(cx.aswin.boxlore.core.downloads.AutoDownloadWorker.KEY_EPISODE_ID, episodeId)
                    .build()

                val workRequest = androidx.work.OneTimeWorkRequestBuilder<cx.aswin.boxlore.core.downloads.AutoDownloadWorker>()
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
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<cx.aswin.boxlore.core.downloads.SmartDownloadWorker>()
                .build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
            android.util.Log.d("BoxLoreFcmService", "Triggered immediate SmartDownloadWorker sync due to new episode push notification")
        } catch (e: Exception) {
            android.util.Log.e("BoxLoreFcmService", "Failed to trigger background sync", e)
        }
    }

    private fun saveInAppAnnouncement(
        title: String, 
        body: String, 
        route: String?, 
        imageUrl: String?, 
        actionLabel: String?, 
        showActionInApp: Boolean,
        category: String
    ) {
        // GitHub APK "What's New" / release download CTA is meaningless on Play installs.
        if (applicationContext.shouldSuppressWhatsNewOnPlay(category)) {
            android.util.Log.d(
                "BoxLoreFcmService",
                "Skipping Whats New in-app announcement on Play Store install (category=$category)",
            )
            return
        }

        val prefs = userPreferences()
        CoroutineScope(Dispatchers.IO).launch {
            val announcement = UserPreferencesRepository.Announcement(
                title = title,
                body = body,
                route = route,
                imageUrl = imageUrl,
                actionLabel = actionLabel,
                showActionInApp = showActionInApp,
                category = category,
                timestamp = System.currentTimeMillis()
            )
            prefs.setAnnouncement(announcement)
        }
    }

    private fun showPushNotification(
        title: String, 
        body: String, 
        route: String?, 
        imageUrl: String?, 
        sound: String?, 
        actionLabel: String?, 
        showActionInPush: Boolean
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val config = getPushChannelConfig(sound)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(config.id, config.name, config.importance).apply {
                description = "boxlore news and updates"
                if (config.soundUri != null) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    setSound(config.soundUri, audioAttributes)
                } else {
                    setSound(null, null)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = createPushIntent(route)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, config.id)
            .setSmallIcon(cx.aswin.boxlore.R.drawable.ic_notification_custom)
            .setColor(android.graphics.Color.parseColor("#5B5BD6")) // Brand purple color matching launcher icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            
        if (config.soundUri != null) {
            notificationBuilder.setSound(config.soundUri)
        }

        if (showActionInPush && !route.isNullOrBlank()) {
            val actionIntent = createPushIntent(route)
            val actionPendingIntent = PendingIntent.getActivity(
                this,
                route.hashCode(),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val btnLabel = actionLabel ?: "View"
            notificationBuilder.addAction(
                cx.aswin.boxlore.R.drawable.ic_notification_custom,
                btnLabel,
                actionPendingIntent
            )
        }

        loadPushImage(notificationBuilder, imageUrl)
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private data class PushChannelConfig(
        val id: String,
        val name: String,
        val soundUri: Uri?,
        val importance: Int
    )

    private fun getPushChannelConfig(sound: String?): PushChannelConfig {
        return when (sound) {
            "chime" -> PushChannelConfig(
                id = "boxlore_new_episodes_v1",
                name = "New Episode Alerts",
                soundUri = Uri.parse("android.resource://$packageName/raw/boxlore_chime"),
                importance = NotificationManager.IMPORTANCE_DEFAULT
            )
            "silent" -> PushChannelConfig(
                id = "boxlore_silent_v1",
                name = "Silent Notifications",
                soundUri = null,
                importance = NotificationManager.IMPORTANCE_LOW
            )
            else -> PushChannelConfig(
                id = "boxlore_announcements_v2",
                name = "Announcements",
                soundUri = Uri.parse("android.resource://$packageName/raw/boxlore_announcement_chime"),
                importance = NotificationManager.IMPORTANCE_DEFAULT
            )
        }
    }

    private fun createPushIntent(route: String?): Intent {
        val isUriRoute = route != null && PushTargetRouteAllowlist.isAppOrWebUri(route)
        return if (isUriRoute) {
            Intent(Intent.ACTION_VIEW, Uri.parse(route)).apply {
                setClass(this@BoxLoreFcmService, MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("from_push", true)
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("from_push", true)
                if (route != null && PushTargetRouteAllowlist.isAllowed(route)) {
                    putExtra("target_route", route)
                }
            }
        }
    }

    private fun loadPushImage(builder: NotificationCompat.Builder, imageUrl: String?) {
        if (imageUrl.isNullOrBlank()) return
        try {
            val optimizedUrl = imageUrl.optimizedImageUrl(500)
            val url = java.net.URL(optimizedUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.doInput = true
            connection.connect()
            val bitmap = android.graphics.BitmapFactory.decodeStream(connection.inputStream)
            if (bitmap != null) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as android.graphics.Bitmap?)
                )
                builder.setLargeIcon(bitmap)
            }
        } catch (e: Exception) {
            // Ignore image fetch failure
        }
    }
}
