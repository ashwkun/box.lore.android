package cx.aswin.boxcast.core.data

import android.content.Context
import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShareManager {

    private val shareScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun sharePodcast(context: Context, podcast: Podcast) {
        val shareUrl = "https://aswin.cx/boxcast/share?type=podcast&id=${podcast.id}"
        val shareText = "*Listen to ${podcast.title} on boxcast*:\n$shareUrl"
        
        shareWithCompositeImage(
            context = context,
            text = shareText,
            title = "Share Podcast",
            imageUrl = podcast.imageUrl
        )
    }

    fun shareEpisode(
        context: Context,
        episode: Episode,
        podcastTitle: String,
        timestampMs: Long? = null,
        startMs: Long? = null,
        endMs: Long? = null
    ) {
        val baseUrl = "https://aswin.cx/boxcast/share?type=episode&id=${episode.id}"
        
        val shareUrl = when {
            startMs != null && endMs != null -> {
                "$baseUrl&start=${startMs / 1000}&end=${endMs / 1000}"
            }
            timestampMs != null && timestampMs > 0 -> {
                "$baseUrl&t=${timestampMs / 1000}"
            }
            else -> baseUrl
        }

        val timeSuffix = when {
            startMs != null && endMs != null -> {
                " (Clip: ${formatTime(startMs)} - ${formatTime(endMs)})"
            }
            timestampMs != null && timestampMs > 0 -> {
                " (at ${formatTime(timestampMs)})"
            }
            else -> ""
        }

        val shareText = "*Listen to \"${episode.title}\" from $podcastTitle$timeSuffix on boxcast*:\n$shareUrl"
        
        shareWithCompositeImage(
            context = context,
            text = shareText,
            title = "Share Episode",
            imageUrl = episode.imageUrl,
            fallbackImageUrl = episode.podcastImageUrl
        )
    }

    private fun shareWithCompositeImage(
        context: Context,
        text: String,
        title: String,
        imageUrl: String?,
        fallbackImageUrl: String? = null
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
                        val width = originalBitmap.width
                        val height = originalBitmap.height
                        val mutableBitmap = originalBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                        val canvas = android.graphics.Canvas(mutableBitmap)
                        
                        // Extract color palette dynamically from the artwork bitmap
                        val palette = androidx.palette.graphics.Palette.from(originalBitmap).generate()
                        val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
                        val bgColor = swatch?.rgb ?: android.graphics.Color.parseColor("#0F0C20")
                        
                        // Calculate relative luminance to guarantee maximum text and logo visibility
                        val bgLuminance = getRelativeLuminance(bgColor)
                        val darkColorVal = android.graphics.Color.parseColor("#0F0C20")
                        val darkLuminance = getRelativeLuminance(darkColorVal)
                        
                        val contrastWithWhite = (1.0 + 0.05) / (bgLuminance + 0.05)
                        val contrastWithDark = (bgLuminance + 0.05) / (darkLuminance + 0.05)
                        
                        var logoColor = android.graphics.Color.WHITE
                        var adjustedBgColor = bgColor
                        
                        if (contrastWithWhite >= contrastWithDark) {
                            logoColor = android.graphics.Color.WHITE
                            // If contrast with white is less than 4.5 (WCAG AA), darken the background slightly
                            if (contrastWithWhite < 4.5) {
                                val hsl = FloatArray(3)
                                androidx.core.graphics.ColorUtils.colorToHSL(bgColor, hsl)
                                hsl[2] = (hsl[2] - 0.15f).coerceAtLeast(0.12f) // Darken to safe range
                                adjustedBgColor = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                            }
                        } else {
                            logoColor = darkColorVal
                            // If contrast with dark is less than 4.5 (WCAG AA), lighten the background slightly
                            if (contrastWithDark < 4.5) {
                                val hsl = FloatArray(3)
                                androidx.core.graphics.ColorUtils.colorToHSL(bgColor, hsl)
                                hsl[2] = (hsl[2] + 0.15f).coerceAtMost(0.88f) // Lighten to safe range
                                adjustedBgColor = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                            }
                        }
                        
                        // Draw a custom corner badge at the bottom-right corner
                        // Load Google Sans typeface from resources and make it BOLD (Product Sans style)
                        val googleSansTypeface = try {
                            val baseFont = androidx.core.content.res.ResourcesCompat.getFont(
                                context,
                                cx.aswin.boxcast.core.designsystem.R.font.google_sans_variable
                            )
                            if (baseFont != null) {
                                android.graphics.Typeface.create(baseFont, android.graphics.Typeface.BOLD)
                            } else {
                                android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                            }
                        } catch (e: Exception) {
                            android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                        }
                        
                        val text = "listen on"
                        
                        // Keep text and logo size exactly identical to previous version (referenced to image height)
                        val targetTextSize = (height * 0.0396f).coerceAtLeast(20f)
                        val targetLogoHeight = (height * 0.077f).coerceAtLeast(38f)
                        
                        // Set up text paint for "listen on" (Google Sans Bold)
                        val textPaint = android.graphics.Paint().apply {
                            color = logoColor
                            textSize = targetTextSize
                            typeface = googleSansTypeface
                            isAntiAlias = true
                        }
                        
                        val fontMetrics = textPaint.fontMetrics
                        val textHeight = fontMetrics.descent - fontMetrics.ascent
                        
                        // Tight vertical spacing and padding to eliminate wasted space
                        var paddingTop = targetTextSize * 0.35f
                        var paddingBottom = targetLogoHeight * 0.20f
                        var spacing = targetLogoHeight * 0.12f
                        
                        var pillHeight = paddingTop + textHeight + spacing + targetLogoHeight + paddingBottom
                        var logoHeight = targetLogoHeight
                        
                        // Calculate logo width (aspect ratio is 778f / 112f)
                        var logoWidth = logoHeight * (778f / 112f)
                        
                        var radius = pillHeight * 0.40f
                        
                        // Tight horizontal padding to eliminate wasted horizontal space
                        var paddingLeft = radius + logoHeight * 0.10f
                        var paddingRight = logoHeight * 0.30f
                        var textWidth = textPaint.measureText(text)
                        var maxContentWidth = maxOf(textWidth, logoWidth)
                        var pillWidth = maxContentWidth + paddingLeft + paddingRight
                        
                        // Ensure badge doesn't overflow small images (scale down proportionally if needed)
                        if (pillWidth > width * 0.9f) {
                            val scale = (width * 0.9f) / pillWidth
                            val finalScale = scale.coerceAtLeast(0.5f)
                            
                            pillHeight *= finalScale
                            textPaint.textSize = textPaint.textSize * finalScale
                            logoHeight *= finalScale
                            logoWidth = logoHeight * (778f / 112f)
                            paddingLeft *= finalScale
                            paddingRight *= finalScale
                            paddingTop *= finalScale
                            paddingBottom *= finalScale
                            spacing *= finalScale
                            
                            // Recalculate text metrics after scaling
                            val scaledFontMetrics = textPaint.fontMetrics
                            val scaledTextHeight = scaledFontMetrics.descent - scaledFontMetrics.ascent
                            textWidth = textPaint.measureText(text)
                            maxContentWidth = maxOf(textWidth, logoWidth)
                            pillWidth = maxContentWidth + paddingLeft + paddingRight
                        }
                        
                        val pillLeft = width - pillWidth
                        
                        val paintBack = android.graphics.Paint().apply {
                            color = adjustedBgColor
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        }
                        
                        val path = android.graphics.Path().apply {
                            moveTo(pillLeft, height.toFloat()) // bottom-left of badge
                            lineTo(pillLeft, height - pillHeight + radius) // start of top-left curve
                            quadTo(pillLeft, height - pillHeight, pillLeft + radius, height - pillHeight) // top-left curve
                            lineTo(width.toFloat(), height - pillHeight) // top-right (sharp)
                            lineTo(width.toFloat(), height.toFloat()) // bottom-right (sharp)
                            close()
                        }
                        canvas.drawPath(path, paintBack)
                        
                        // Draw "listen on" text and logo right-aligned with each other
                        val contentEndX = width - paddingRight
                        val textX = contentEndX - textWidth
                        val textY = (height - pillHeight + paddingTop) - fontMetrics.ascent
                        canvas.drawText(text, textX, textY, textPaint)
                        
                        // Draw the logo inside the capsule (below "listen on", right-aligned)
                        val drawable = androidx.core.content.ContextCompat.getDrawable(
                            context,
                            cx.aswin.boxcast.core.designsystem.R.drawable.ic_boxlore_logo
                        )?.mutate()
                        if (drawable != null) {
                            val logoLeft = contentEndX - logoWidth
                            val logoTop = height - pillHeight + paddingTop + (fontMetrics.descent - fontMetrics.ascent) + spacing
                            val logoRight = contentEndX
                            val logoBottom = logoTop + logoHeight
                            
                            drawable.setBounds(logoLeft.toInt(), logoTop.toInt(), logoRight.toInt(), logoBottom.toInt())
                            drawable.setTint(logoColor)
                            drawable.draw(canvas)
                        }
                        
                        // Save bitmap to cache directory
                        val cacheFile = java.io.File(context.cacheDir, "shared_artwork.jpg")
                        java.io.FileOutputStream(cacheFile).use { out ->
                            mutableBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        
                        sharedUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "cx.aswin.boxcast.fileprovider",
                            cacheFile
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShareManager", "Error generating composite image", e)
                }
            }
            
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    if (sharedUri != null) {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, sharedUri)
                        putExtra(Intent.EXTRA_TEXT, text)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                }
                val chooser = Intent.createChooser(intent, title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
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

    private fun getRelativeLuminance(color: Int): Double {
        val r = android.graphics.Color.red(color) / 255.0
        val g = android.graphics.Color.green(color) / 255.0
        val b = android.graphics.Color.blue(color) / 255.0
        
        val rL = if (r <= 0.03928) r / 12.92 else Math.pow((r + 0.055) / 1.055, 2.4)
        val gL = if (g <= 0.03928) g / 12.92 else Math.pow((g + 0.055) / 1.055, 2.4)
        val bL = if (b <= 0.03928) b / 12.92 else Math.pow((b + 0.055) / 1.055, 2.4)
        
        return 0.2126 * rL + 0.7152 * gL + 0.0722 * bL
    }
}
