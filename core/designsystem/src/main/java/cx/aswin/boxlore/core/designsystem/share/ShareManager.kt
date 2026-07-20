package cx.aswin.boxlore.core.designsystem.share

import android.content.Context
import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.ShareLinkBuilder
import cx.aswin.boxlore.core.model.ShareTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShareManager {

    private val shareScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun sharePodcast(
        context: Context,
        podcast: Podcast,
        target: ShareTarget = ShareTarget.MESSAGE
    ) {
        trackShare(contentType = "podcast", podcastId = podcast.id, target = target)
        val shareUrl = ShareLinkBuilder.podcast(podcast.id)
        val creator = podcast.artist.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
        val shareText = "I think you'll like this podcast:\n\n" +
            "${podcast.title}$creator\n\nListen on boxlore:\n$shareUrl"
        
        shareWithCompositeImage(
            context = context,
            text = shareText,
            shareUrl = shareUrl,
            title = "Share Podcast",
            cardTitle = podcast.title,
            cardSubtitle = podcast.artist,
            imageUrl = podcast.imageUrl,
            target = target
        )
    }

    fun shareEpisode(
        context: Context,
        episode: Episode,
        podcastTitle: String,
        timestampMs: Long? = null,
        startMs: Long? = null,
        endMs: Long? = null,
        target: ShareTarget = ShareTarget.MESSAGE
    ) {
        trackShare(
            contentType = "episode",
            podcastId = episode.podcastId,
            episodeId = episode.id,
            target = target,
        )
        val shareUrl = ShareLinkBuilder.episode(
            id = episode.id,
            timestampMs = timestampMs,
            startMs = startMs,
            endMs = endMs
        )

        val timeContext = when {
            startMs != null && endMs != null -> {
                "Clip ${formatTime(startMs)}–${formatTime(endMs)}"
            }
            timestampMs != null && timestampMs > 0 -> {
                "Start at ${formatTime(timestampMs)}"
            }
            else -> null
        }

        val contextLine = listOfNotNull(
            podcastTitle.takeIf { it.isNotBlank() },
            timeContext
        ).joinToString(" • ")
        val shareText = "This episode is worth a listen:\n\n" +
            "“${episode.title}”\n$contextLine\n\nListen on boxlore:\n$shareUrl"
        
        shareWithCompositeImage(
            context = context,
            text = shareText,
            shareUrl = shareUrl,
            title = "Share Episode",
            cardTitle = episode.title,
            cardSubtitle = podcastTitle,
            imageUrl = episode.imageUrl,
            fallbackImageUrl = episode.podcastImageUrl,
            target = target
        )
    }

    private fun trackShare(
        contentType: String,
        podcastId: String?,
        episodeId: String? = null,
        target: ShareTarget,
    ) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackShareContent(
            contentType = contentType,
            podcastId = podcastId,
            episodeId = episodeId,
            channel =
                when (target) {
                    ShareTarget.MESSAGE -> "message"
                    ShareTarget.INSTAGRAM_STORY -> "instagram_story"
                },
        )
    }

    private fun shareWithCompositeImage(
        context: Context,
        text: String,
        shareUrl: String,
        title: String,
        cardTitle: String,
        cardSubtitle: String,
        imageUrl: String?,
        fallbackImageUrl: String? = null,
        target: ShareTarget
    ) {
        shareScope.launch {
            var sharedUri: android.net.Uri? = null
            val targetUrl = imageUrl?.takeIf { it.isNotEmpty() } ?: fallbackImageUrl?.takeIf { it.isNotEmpty() }
            
            if (targetUrl != null) {
                try {
                    val loader = coil.ImageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(targetUrl)
                        .allowHardware(false) // Required to draw onto Canvas
                        .build()
                    val result = loader.execute(request)
                    val originalBitmap = (result as? coil.request.SuccessResult)?.drawable?.toBitmap()
                    
                    if (originalBitmap != null) {
                        val shareCard = ShareCardRenderer.createShareCard(
                            context = context,
                            artwork = originalBitmap,
                            title = cardTitle,
                            subtitle = cardSubtitle,
                            target = target
                        )
                        clearExpiredShareCards(context)
                        val formatName = if (target == ShareTarget.INSTAGRAM_STORY) {
                            "story"
                        } else {
                            "message"
                        }
                        val cacheFile = java.io.File(
                            context.cacheDir,
                            "boxlore_share_${formatName}_${System.currentTimeMillis()}.png"
                        )
                        java.io.FileOutputStream(cacheFile).use { out ->
                            shareCard.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        }
                        
                        sharedUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            cacheFile
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShareManager", "Error generating composite image", e)
                }
            }
            
            withContext(Dispatchers.Main) {
                val openedInstagram = if (
                    target == ShareTarget.INSTAGRAM_STORY &&
                    sharedUri != null
                ) {
                    openInstagramStory(
                        context = context,
                        imageUri = sharedUri,
                        shareUrl = shareUrl
                    )
                } else {
                    false
                }

                if (!openedInstagram) {
                    if (target == ShareTarget.INSTAGRAM_STORY) {
                        android.widget.Toast.makeText(
                            context,
                            "Instagram isn't available — choose another app",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    openShareSheet(
                        context = context,
                        imageUri = sharedUri,
                        text = text,
                        chooserTitle = title
                    )
                }
            }
        }
    }

    private fun openShareSheet(
        context: Context,
        imageUri: android.net.Uri?,
        text: String,
        chooserTitle: String
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (imageUri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                clipData = android.content.ClipData.newRawUri("boxlore share card", imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, chooserTitle)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun openInstagramStory(
        context: Context,
        imageUri: android.net.Uri,
        shareUrl: String
    ): Boolean {
        val instagramPackage = "com.instagram.android"
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(imageUri, "image/png")
            setPackage(instagramPackage)
            clipData = android.content.ClipData.newRawUri("boxlore Story", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) return false

        context.grantUriPermission(
            instagramPackage,
            imageUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("boxlore link", shareUrl)
        )
        android.widget.Toast.makeText(
            context,
            "Link copied — add it with Instagram's Link sticker",
            android.widget.Toast.LENGTH_LONG
        ).show()
        context.startActivity(intent)
        return true
    }

    private fun clearExpiredShareCards(context: Context) {
        val expiry = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("boxlore_share_") && it.lastModified() < expiry }
            ?.forEach { it.delete() }
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }



}
