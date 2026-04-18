package cx.aswin.boxcast.core.data.service

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
        folderImages: Map<String, List<String>> // folderId → list of image URLs (max 4)
    ): Map<String, Uri> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Uri>()
        val cacheDir = File(context.cacheDir, "auto_collages").apply { mkdirs() }

        for ((folderId, imageUrls) in folderImages) {
            try {
                val safeName = folderId.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val outFile = File(cacheDir, "${safeName}.png")

                val bitmap = createCollageBitmap(imageUrls, folderId, context)
                if (bitmap != null) {
                    FileOutputStream(outFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    bitmap.recycle()

                    // Use our custom exported ContentProvider URI
                    val uri = AutoCollageProvider.getUri("${safeName}.png")
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
        context: Context
    ): Bitmap? = coroutineScope {
        val size = COLLAGE_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Download images in parallel (with timeout protection)
        val urls = imageUrls.take(4).filter { it.isNotBlank() }
        val bitmaps = urls.map { url ->
            async(Dispatchers.IO) { downloadBitmap(url) }
        }.awaitAll().filterNotNull()

        when (bitmaps.size) {
            0 -> drawBrandedFallback(canvas, size, folderId)
            1 -> drawSingleCover(canvas, size, bitmaps[0])
            2 -> drawTwoSplit(canvas, size, bitmaps)
            3 -> drawThreeLayout(canvas, size, bitmaps)
            else -> drawFourGrid(canvas, size, bitmaps)
        }

        // Recycle source bitmaps
        bitmaps.forEach { it.recycle() }
        bitmap
    }

    // ============= Layout Renderers =============

    /** Full-bleed single image */
    private fun drawSingleCover(canvas: Canvas, size: Int, bmp: Bitmap) {
        val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        if (scaled != bmp) scaled.recycle()
    }

    /** Side-by-side vertical split */
    private fun drawTwoSplit(canvas: Canvas, size: Int, bitmaps: List<Bitmap>) {
        val halfW = size / 2
        val gap = 3 // thin gap between images

        for (i in 0..1) {
            val srcBmp = bitmaps[i]
            val scaled = Bitmap.createScaledBitmap(srcBmp, halfW, size, true)
            val x = if (i == 0) 0f else (halfW + gap).toFloat()
            canvas.drawBitmap(scaled, x, 0f, null)
            if (scaled != srcBmp) scaled.recycle()
        }
    }

    /** 1 large left + 2 stacked right */
    private fun drawThreeLayout(canvas: Canvas, size: Int, bitmaps: List<Bitmap>) {
        val halfW = size / 2
        val halfH = size / 2
        val gap = 3

        // Left: large
        val leftScaled = Bitmap.createScaledBitmap(bitmaps[0], halfW, size, true)
        canvas.drawBitmap(leftScaled, 0f, 0f, null)
        if (leftScaled != bitmaps[0]) leftScaled.recycle()

        // Top-right
        val tr = Bitmap.createScaledBitmap(bitmaps[1], halfW, halfH, true)
        canvas.drawBitmap(tr, (halfW + gap).toFloat(), 0f, null)
        if (tr != bitmaps[1]) tr.recycle()

        // Bottom-right
        val br = Bitmap.createScaledBitmap(bitmaps[2], halfW, halfH, true)
        canvas.drawBitmap(br, (halfW + gap).toFloat(), (halfH + gap).toFloat(), null)
        if (br != bitmaps[2]) br.recycle()
    }

    /** Classic 2×2 grid */
    private fun drawFourGrid(canvas: Canvas, size: Int, bitmaps: List<Bitmap>) {
        val halfW = size / 2
        val halfH = size / 2
        val gap = 3

        val positions = listOf(
            0f to 0f,
            (halfW + gap).toFloat() to 0f,
            0f to (halfH + gap).toFloat(),
            (halfW + gap).toFloat() to (halfH + gap).toFloat()
        )

        for (i in 0 until minOf(4, bitmaps.size)) {
            val scaled = Bitmap.createScaledBitmap(bitmaps[i], halfW, halfH, true)
            val (x, y) = positions[i]
            canvas.drawBitmap(scaled, x, y, null)
            if (scaled != bitmaps[i]) scaled.recycle()
        }
    }

    /** Branded gradient fallback when no images available */
    private fun drawBrandedFallback(canvas: Canvas, size: Int, folderId: String) {
        // Dark gradient background
        val gradient = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"),
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Category label
        val label = when {
            folderId.contains("resume") -> "▶"
            folderId.contains("new_episodes") -> "🆕"
            else -> "♫"
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = size * 0.3f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(label, size / 2f, size / 2f + textSize(textPaint) / 3f, textPaint)
    }

    private fun textSize(paint: Paint): Float = paint.textSize

    // ============= Image Downloading =============

    /**
     * Download a bitmap from URL with a 3-second timeout.
     * Returns null on failure (never throws).
     */
    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString.replace("http://", "https://"))
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.instanceFollowRedirects = true
            conn.doInput = true
            conn.connect()

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = conn.inputStream
                val opts = BitmapFactory.Options().apply {
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
                
                val decodeOpts = BitmapFactory.Options().apply {
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
