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
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackShareContent(
            contentType = "podcast",
            podcastId = podcast.id,
            channel = when (target) {
                ShareTarget.MESSAGE -> "message"
                ShareTarget.INSTAGRAM_STORY -> "instagram_story"
            },
        )
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
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackShareContent(
            contentType = "episode",
            podcastId = episode.podcastId,
            episodeId = episode.id,
            channel = when (target) {
                ShareTarget.MESSAGE -> "message"
                ShareTarget.INSTAGRAM_STORY -> "instagram_story"
            },
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
                        val shareCard = createShareCard(
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

    private fun createShareCard(
        context: Context,
        artwork: android.graphics.Bitmap,
        title: String,
        subtitle: String,
        target: ShareTarget
    ): android.graphics.Bitmap {
        val isStory = target == ShareTarget.INSTAGRAM_STORY
        val width = if (isStory) 1080 else 1200
        val height = if (isStory) 1920 else 1200
        val output = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.parseColor("#2E2DD0"))
        drawShareCardShapes(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            isStory = isStory
        )

        val artworkSize = if (isStory) 760f else 680f
        val artworkTop = if (isStory) 290f else 55f
        val artworkRect = android.graphics.RectF(
            (width - artworkSize) / 2f,
            artworkTop,
            (width + artworkSize) / 2f,
            artworkTop + artworkSize
        )
        drawRoundedArtwork(
            canvas = canvas,
            artwork = artwork,
            destination = artworkRect,
            cornerRadius = 72f
        )

        if (isStory) {
            val textBottom = drawShareText(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                title = title,
                subtitle = subtitle,
                titleTop = 1_110f,
                titleWidth = 860,
                titleSize = 60f,
                subtitleSize = 44f,
                blockSpacing = 13f
            )
            drawShareBranding(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                brandingTop = textBottom + 44f,
                brandingLabelSize = 34f,
                logoWidth = 480,
                listenNowGap = 22f,
                listenNowSize = 32f
            )
        } else {
            val textBottom = drawShareText(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                title = title,
                subtitle = subtitle,
                titleTop = 785f,
                titleWidth = 960,
                titleSize = 48f,
                subtitleSize = 36f,
                blockSpacing = 8f
            )
            drawShareBranding(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                brandingTop = textBottom + 30f,
                brandingLabelSize = 28f,
                logoWidth = 400,
                listenNowGap = 17f,
                listenNowSize = 28f
            )
        }

        return output
    }

    private fun drawShareCardShapes(
        canvas: android.graphics.Canvas,
        width: Float,
        height: Float,
        isStory: Boolean
    ) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#5B5BDF")
            alpha = 185
            style = android.graphics.Paint.Style.FILL
        }

        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.Puffy,
            left = width - if (isStory) 345f else 315f,
            top = if (isStory) -145f else -165f,
            size = if (isStory) 500f else 455f,
            rotation = 14f,
            paint = paint
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.PuffyDiamond,
            left = if (isStory) -195f else -155f,
            top = if (isStory) 710f else height * 0.35f,
            size = if (isStory) 390f else 320f,
            rotation = -18f,
            paint = paint.apply { alpha = 155 }
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.Cookie4,
            left = if (isStory) -120f else -100f,
            top = if (isStory) 35f else -20f,
            size = if (isStory) 390f else 325f,
            rotation = -24f,
            paint = paint.apply { alpha = 130 }
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.SoftBurst,
            left = width - if (isStory) 230f else 195f,
            top = if (isStory) 710f else 530f,
            size = if (isStory) 430f else 370f,
            rotation = 20f,
            paint = paint.apply { alpha = 180 }
        )
    }

    private fun drawExpressiveShape(
        canvas: android.graphics.Canvas,
        type: cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType,
        left: Float,
        top: Float,
        size: Float,
        rotation: Float,
        paint: android.graphics.Paint
    ) {
        val path = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.androidPath(
            type = type,
            width = size,
            height = size
        )
        canvas.save()
        canvas.translate(left, top)
        canvas.rotate(rotation, size / 2f, size / 2f)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawShareBranding(
        context: Context,
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        brandingTop: Float,
        brandingLabelSize: Float,
        logoWidth: Int,
        listenNowGap: Float,
        listenNowSize: Float
    ) {
        val brandingLeft = (canvasWidth - logoWidth) / 2f
        val brandingRight = brandingLeft + logoWidth
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = brandingLabelSize
            typeface = googleSansTypeface(context, android.graphics.Typeface.BOLD)
        }
        val label = "is better on"
        val labelBaseline = brandingTop - textPaint.fontMetrics.ascent
        canvas.drawText(label, brandingLeft, labelBaseline, textPaint)

        val waveStart = brandingLeft + textPaint.measureText(label) + 20f
        val waveCenter = brandingTop + (
            textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
            ) / 2f
        if (brandingRight > waveStart) {
            val path = android.graphics.Path()
            val amplitude = 9f
            val wavelength = 72f
            var x = waveStart
            path.moveTo(x, waveCenter)
            while (x < brandingRight) {
                x = (x + 3f).coerceAtMost(brandingRight)
                val y = waveCenter + amplitude * kotlin.math.sin(
                    ((x - waveStart) / wavelength) * 2f * kotlin.math.PI.toFloat()
                )
                path.lineTo(x, y)
            }
            canvas.drawPath(
                path,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 4f
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
            )
        }

        val labelHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
        val logoTop = (brandingTop + labelHeight + 18f).toInt()
        val logoHeight = (logoWidth * 110f / 805f).toInt()
        val logoLeft = (canvasWidth - logoWidth) / 2
        androidx.core.content.ContextCompat.getDrawable(
            context,
            cx.aswin.boxlore.core.designsystem.R.drawable.ic_boxlore_logo
        )?.mutate()?.apply {
            setTint(android.graphics.Color.WHITE)
            setBounds(
                logoLeft,
                logoTop,
                logoLeft + logoWidth,
                logoTop + logoHeight
            )
            draw(canvas)
        }
        val listenNowTop = logoTop + logoHeight + listenNowGap
        drawCenteredTextBlock(
            canvas = canvas,
            canvasWidth = canvasWidth,
            text = "listen now",
            top = listenNowTop,
            width = logoWidth,
            textSize = listenNowSize,
            maxLines = 1,
            color = android.graphics.Color.parseColor("#DCDCF8"),
            typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL)
        )
    }

    private fun drawShareText(
        context: Context,
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        title: String,
        subtitle: String,
        titleTop: Float,
        titleWidth: Int,
        titleSize: Float,
        subtitleSize: Float,
        blockSpacing: Float
    ): Float {
        val titleHeight = drawCenteredTextBlock(
            canvas = canvas,
            canvasWidth = canvasWidth,
            text = title,
            top = titleTop,
            width = titleWidth,
            textSize = titleSize,
            maxLines = 2,
            color = android.graphics.Color.WHITE,
            typeface = googleSansTypeface(context, android.graphics.Typeface.BOLD)
        )
        var nextTop = titleTop + titleHeight
        if (subtitle.isNotBlank()) {
            val bySize = subtitleSize * 0.78f
            val byTop = nextTop + blockSpacing
            val byHeight = drawCenteredTextBlock(
                canvas = canvas,
                canvasWidth = canvasWidth,
                text = "by",
                top = byTop,
                width = titleWidth,
                textSize = bySize,
                maxLines = 1,
                color = android.graphics.Color.parseColor("#BEBEE8"),
                typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL)
            )
            val subtitleTop = byTop + byHeight + blockSpacing * 0.5f
            val subtitleHeight = drawCenteredTextBlock(
                canvas = canvas,
                canvasWidth = canvasWidth,
                text = subtitle,
                top = subtitleTop,
                width = titleWidth,
                textSize = subtitleSize,
                maxLines = 1,
                color = android.graphics.Color.parseColor("#DCDCF8"),
                typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL)
            )
            nextTop = subtitleTop + subtitleHeight
        }
        return nextTop
    }

    private fun drawCenteredTextBlock(
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        text: String,
        top: Float,
        width: Int,
        textSize: Float,
        maxLines: Int,
        color: Int,
        typeface: android.graphics.Typeface
    ): Int {
        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = typeface
        }
        val layout = android.text.StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            textPaint,
            width
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.02f)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate((canvasWidth - width) / 2f, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun googleSansTypeface(
        context: Context,
        style: Int
    ): android.graphics.Typeface {
        val font = androidx.core.content.res.ResourcesCompat.getFont(
            context,
            cx.aswin.boxlore.core.designsystem.R.font.google_sans_variable
        )
        return android.graphics.Typeface.create(font, style)
    }

    private fun drawRoundedArtwork(
        canvas: android.graphics.Canvas,
        artwork: android.graphics.Bitmap,
        destination: android.graphics.RectF,
        cornerRadius: Float
    ) {
        val sourceSide = minOf(artwork.width, artwork.height)
        val sourceLeft = (artwork.width - sourceSide) / 2
        val sourceTop = (artwork.height - sourceSide) / 2
        val source = android.graphics.Rect(
            sourceLeft,
            sourceTop,
            sourceLeft + sourceSide,
            sourceTop + sourceSide
        )
        val clipPath = android.graphics.Path().apply {
            addRoundRect(
                destination,
                cornerRadius,
                cornerRadius,
                android.graphics.Path.Direction.CW
            )
        }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(
            artwork,
            source,
            destination,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }
        )
        canvas.restore()

        canvas.drawRoundRect(
            destination,
            cornerRadius,
            cornerRadius,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(52, 255, 255, 255)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            }
        )
    }

}
