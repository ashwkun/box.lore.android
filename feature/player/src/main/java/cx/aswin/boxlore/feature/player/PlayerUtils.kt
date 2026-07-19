package cx.aswin.boxlore.feature.player

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Extracts a seed color from a bitmap using Palette API.
 */
suspend fun extractSeedColor(bitmap: Bitmap): Color = withContext(Dispatchers.Default) {
    val scaledBitmap = if (bitmap.width > 100 || bitmap.height > 100) {
        val scale = 100f / max(bitmap.width, bitmap.height)
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    } else {
        bitmap
    }
    
    val palette = Palette.Builder(scaledBitmap)
        .maximumColorCount(16)
        .generate()
    
    val color = palette.vibrantSwatch?.rgb?.let { Color(it) }
        ?: palette.mutedSwatch?.rgb?.let { Color(it) }
        ?: palette.dominantSwatch?.rgb?.let { Color(it) }
        ?: Color(0xFF6750A4) // Default purple
    
    if (scaledBitmap != bitmap) {
        scaledBitmap.recycle()
    }
    
    color
}



/**
 * Formats milliseconds to time string.
 * Shows HH:MM:SS if hours > 0, otherwise MM:SS.
 */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
