package cx.aswin.boxcast.core.designsystem.components

import java.net.URLEncoder

/**
 * Optimizes an image URL by passing it through a resizing CDN (wsrv.nl).
 * This significantly improves loading times for lists and grids by preventing
 * the app from downloading 5MB uncompressed podcast cover arts.
 * 
 * @param width The desired maximum width in pixels.
 * @return The optimized URL, or the original if it's not an HTTP/HTTPS URL.
 */
fun String.optimizedImageUrl(width: Int = 400): String {
    val cleanedUrl = this.cleanImageUrl()
    if (cleanedUrl.isBlank() || (!cleanedUrl.startsWith("http://") && !cleanedUrl.startsWith("https://"))) {
        return cleanedUrl
    }
    
    // Dynamically scale width based on screen density and tablet/viewport configuration
    val scaledWidth = try {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val density = metrics.density
        val screenWidthPx = metrics.widthPixels
        val isLargeScreen = (screenWidthPx / density) >= 600f
        
        val scale = when {
            isLargeScreen -> 2.5f   // Table/large viewports need significantly higher resolution
            density >= 3.5f -> 1.8f // High-density QHD phones
            density >= 2.5f -> 1.4f // Medium-high density FHD phones
            else -> 1.1f
        }
        (width * scale).toInt().coerceIn(10, 2048)
    } catch (e: Exception) {
        width
    }
    
    // Some podcast servers block wsrv.nl, but for most standard URLs it works perfectly.
    return try {
        val encodedUrl = URLEncoder.encode(cleanedUrl, "UTF-8")
        "https://wsrv.nl/?url=$encodedUrl&w=$scaledWidth&q=85&output=webp"
    } catch (e: Exception) {
        cleanedUrl
    }
}

/**
 * Strips Automattic/WordPress/Jetpack Photon CDN prefixes and removes standard
 * sizing query parameters (fit, resize, w, h) to obtain original high-resolution artwork URLs.
 */
fun String.cleanImageUrl(): String {
    if (this.isBlank()) return ""
    var cleaned = this.trim()

    // Strip WordPress/Jetpack Photon CDN prefix
    val wpRegex = Regex("^https?://(i\\d+)\\.wp\\.com/", RegexOption.IGNORE_CASE)
    if (wpRegex.containsMatchIn(cleaned)) {
        cleaned = cleaned.replace(wpRegex, "https://")
        
        // Remove fit, resize, w, h query parameters safely
        val parts = cleaned.split("?")
        if (parts.size > 1) {
            val baseUrl = parts[0]
            val queryParams = parts[1].split("&")
            val filteredParams = queryParams.filter { param ->
                val name = param.substringBefore("=")
                name != "fit" && name != "resize" && name != "w" && name != "h"
            }
            cleaned = if (filteredParams.isNotEmpty()) {
                baseUrl + "?" + filteredParams.joinToString("&")
            } else {
                baseUrl
            }
        }
    }
    return cleaned
}

