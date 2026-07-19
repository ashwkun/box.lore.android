package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Generates composite collage bitmaps from multiple podcast cover art images
 * for Android Auto grid tiles.
 *
 * Layout adapts to the number of available images:
 * - 4 images → 2×2 grid
 * - 3 images → 1 large left + 2 stacked right
 * - 2 images → side-by-side split
 * - 1 image  → full-bleed single cover
 * - 0 images → branded gradient with category label
 */
object AutoCollageGenerator {
    private const val TAG = "AutoCollage"
    private const val COLLAGE_SIZE = 512 // px, square

    /**
     * Pre-generate all Home tab collages and return a map of folder ID → content:// URI.
     * This runs entirely on IO dispatcher and uses only local/cached data.
     */
    suspend fun generateAllCollages(
        context: Context,
        folderImages: Map<String, List<String>>, // folderId → list of image URLs (max 4)
        folderContentKeys: Map<String, List<String>> = emptyMap(),
    ): Map<String, Uri> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, Uri>()
            val cacheDir = File(context.cacheDir, "auto_collages").apply { mkdirs() }

            for ((folderId, imageUrls) in folderImages) {
                try {
                    val safeName = folderId.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val outFile = File(cacheDir, "$safeName.png")
                    val signatureFile = File(cacheDir, "$safeName.signature")
                    val uri = AutoCollageProvider.getUri(context, "$safeName.png")
                    val contentKeys = folderContentKeys[folderId].orEmpty()
                    val signatureValues = contentKeys.ifEmpty { imageUrls }
                    val signature =
                        buildString {
                            append("collage-v3\n")
                            append(
                                signatureValues
                                    .take(4)
                                    .filter(String::isNotBlank)
                                    .joinToString("\n"),
                            )
                        }.hashCode().toString()
                    val isFresh =
                        outFile.exists() &&
                            System.currentTimeMillis() - outFile.lastModified() < 6 * 60 * 60 * 1_000L &&
                            signatureFile.isFile &&
                            signatureFile.readText() == signature
                    if (isFresh) {
                        results[folderId] = uri
                        continue
                    }

                    val bitmap = createCollageBitmap(imageUrls, folderId, context)
                    if (bitmap != null) {
                        FileOutputStream(outFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                        }
                        runCatching { signatureFile.writeText(signature) }
                        bitmap.recycle()

                        // Use our custom exported ContentProvider URI
                        results[folderId] = uri
                        Log.d(TAG, "Generated collage for $folderId → $uri")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate collage for $folderId", e)
                }
            }

            results
        }

    /**
     * Create a single collage bitmap from a list of image URLs.
     * Adapts layout based on how many images are available.
     */
    private suspend fun createCollageBitmap(
        imageUrls: List<String>,
        folderId: String,
        context: Context,
    ): Bitmap? =
        coroutineScope {
            val size = COLLAGE_SIZE
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Download images in parallel (with timeout protection)
            val urls = imageUrls.take(4).filter { it.isNotBlank() }
            val bitmaps =
                urls
                    .map { url ->
                        async(Dispatchers.IO) { downloadBitmap(url) }
                    }.awaitAll()
                    .filterNotNull()

            when (bitmaps.size) {
                0 -> drawBrandedFallback(canvas, size, folderId)
                1 -> drawSingleCover(canvas, size, bitmaps[0])
                2 -> drawTwoSplit(canvas, size, bitmaps)
                3 -> drawThreeLayout(canvas, size, bitmaps)
                else -> drawFourGrid(canvas, size, bitmaps)
            }
            when {
                folderId.contains("drive_mix") -> drawLabelBadge(canvas, size, "MIX")
                folderId.contains("continue") -> drawLabelBadge(canvas, size, "RESUME")
            }

            // Recycle source bitmaps
            bitmaps.forEach { it.recycle() }
            bitmap
        }

    private fun drawLabelBadge(
        canvas: Canvas,
        size: Int,
        label: String,
    ) {
        val padding = size * 0.04f
        val badgeWidth = size * if (label.length > 3) 0.42f else 0.28f
        val badgeHeight = size * 0.14f
        val badge =
            RectF(
                size - badgeWidth - padding,
                size - badgeHeight - padding,
                size - padding,
                size - padding,
            )
        canvas.drawRoundRect(
            badge,
            size * 0.035f,
            size * 0.035f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D91A1A2E")
            },
        )
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = size * 0.065f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        val centerY = badge.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, badge.centerX(), centerY, textPaint)
    }

    // ============= Layout Renderers =============

    /** Full-bleed single image */
    private fun drawSingleCover(
        canvas: Canvas,
        size: Int,
        bmp: Bitmap,
    ) {
        drawCenterCrop(canvas, bmp, Rect(0, 0, size, size))
    }

    /** Side-by-side vertical split */
    private fun drawTwoSplit(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val gap = 3 // thin gap between images

        for (i in 0..1) {
            val srcBmp = bitmaps[i]
            val left = if (i == 0) 0 else halfW + gap
            drawCenterCrop(canvas, srcBmp, Rect(left, 0, size.takeIf { i == 1 } ?: halfW, size))
        }
    }

    /** 1 large left + 2 stacked right */
    private fun drawThreeLayout(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val halfH = size / 2
        val gap = 3

        // Left: large
        drawCenterCrop(canvas, bitmaps[0], Rect(0, 0, halfW, size))

        // Top-right
        drawCenterCrop(canvas, bitmaps[1], Rect(halfW + gap, 0, size, halfH))

        // Bottom-right
        drawCenterCrop(canvas, bitmaps[2], Rect(halfW + gap, halfH + gap, size, size))
    }

    /** Classic 2×2 grid */
    private fun drawFourGrid(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val halfH = size / 2
        val gap = 3

        val positions =
            listOf(
                0f to 0f,
                (halfW + gap).toFloat() to 0f,
                0f to (halfH + gap).toFloat(),
                (halfW + gap).toFloat() to (halfH + gap).toFloat(),
            )

        for (i in 0 until minOf(4, bitmaps.size)) {
            val (x, y) = positions[i]
            drawCenterCrop(
                canvas,
                bitmaps[i],
                Rect(x.toInt(), y.toInt(), (x + halfW).toInt(), (y + halfH).toInt()),
            )
        }
    }

    /** Branded gradient fallback when no images available */
    private fun drawBrandedFallback(
        canvas: Canvas,
        size: Int,
        folderId: String,
    ) {
        // Dark gradient background
        val gradient =
            LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                Color.parseColor("#1a1a2e"),
                Color.parseColor("#16213e"),
                Shader.TileMode.CLAMP,
            )
        val bgPaint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Stable text glyphs render consistently across head units.
        val label =
            when {
                folderId.contains("continue") -> "PLAY"
                folderId.contains("download") -> "OFFLINE"
                folderId.contains("drive_mix") -> "MIX"
                folderId.contains("time_picks") -> "NOW"
                folderId.contains("genres") -> "GENRES"
                folderId.contains("discover") -> "DISCOVER"
                folderId.contains("library") -> "LIBRARY"
                else -> "BOXLORE"
            }
        val textPaint =
            Paint().apply {
                color = Color.WHITE
                textSize = size * 0.1f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
        canvas.drawText(label, size / 2f, size / 2f + textSize(textPaint) / 3f, textPaint)
    }

    private fun textSize(paint: Paint): Float = paint.textSize

    private fun drawCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: Rect,
    ) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val destinationRatio = destination.width().toFloat() / destination.height().toFloat()
        val source =
            if (sourceRatio > destinationRatio) {
                val width = (bitmap.height * destinationRatio).toInt()
                val left = (bitmap.width - width) / 2
                Rect(left, 0, left + width, bitmap.height)
            } else {
                val height = (bitmap.width / destinationRatio).toInt()
                val top = (bitmap.height - height) / 2
                Rect(0, top, bitmap.width, top + height)
            }
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    // ============= Image Downloading =============

    /**
     * Download a bitmap from URL with a 3-second timeout.
     * Returns null on failure (never throws).
     */
    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            if (urlString.startsWith("/") || urlString.startsWith("file://")) {
                val path = urlString.removePrefix("file://")
                return BitmapFactory.decodeFile(path)
            }
            val url = URL(urlString.replace("http://", "https://"))
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.instanceFollowRedirects = true
            conn.doInput = true
            conn.connect()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = conn.inputStream
                val opts =
                    BitmapFactory.Options().apply {
                        // Downsample to save memory (max 256px per source)
                        inSampleSize = 1
                        inJustDecodeBounds = true
                    }
                // Two-pass decode: measure first, then downsample
                BitmapFactory.decodeStream(inputStream, null, opts)
                inputStream.close()

                // Calculate sample size
                val maxDim = maxOf(opts.outWidth, opts.outHeight)
                var sampleSize = 1
                while (maxDim / sampleSize > 300) sampleSize *= 2

                val conn2 = url.openConnection() as HttpURLConnection
                conn2.connectTimeout = 3000
                conn2.readTimeout = 3000
                conn2.instanceFollowRedirects = true
                conn2.doInput = true
                conn2.connect()

                val decodeOpts =
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                val bmp = BitmapFactory.decodeStream(conn2.inputStream, null, decodeOpts)
                conn2.disconnect()
                bmp
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download: $urlString", e)
            null
        }
    }
}
