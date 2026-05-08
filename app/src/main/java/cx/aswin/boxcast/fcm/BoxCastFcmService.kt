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
import com.google.firebase.messaging.FirebaseMessaging

class BoxCastFcmService : FirebaseMessagingService() {

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
            val title = data["title"] ?: "BoxCast Update"
            val body = data["body"] ?: "Check out what's new in BoxCast!"
            val type = data["type"] ?: "both" // push, in-app, both
            val route = data["route"]
            val imageUrl = data["image"]

            if (type == "in-app" || type == "both") {
                saveInAppAnnouncement(title, body, route, imageUrl)
            }

            if (type == "push" || type == "both") {
                showPushNotification(title, body, route, imageUrl)
            }
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
            .setSmallIcon(cx.aswin.boxcast.R.drawable.ic_notification) 
            .setColor(android.graphics.Color.parseColor("#000000")) // Must be black so the white mask is visible
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
