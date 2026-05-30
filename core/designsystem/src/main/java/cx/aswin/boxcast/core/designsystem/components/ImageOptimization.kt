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

    // Upgrade to HTTPS for all requests to satisfy Android cleartext security rules
    val httpsUrl = if (cleanedUrl.startsWith("http://")) {
        cleanedUrl.replaceFirst("http://", "https://")
    } else {
        cleanedUrl
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
    
    // NATIVE CDN OPTIMIZATIONS (Completely bypasses third-party proxy lag/failure!)
    try {
        // 1. BBC / ichef
        if (httpsUrl.contains("ichef.bbci.co.uk")) {
            val ichefRegex = Regex("/images/ic/\\d+x\\d+/")
            if (ichefRegex.containsMatchIn(httpsUrl)) {
                return httpsUrl.replace(ichefRegex, "/images/ic/${scaledWidth}x${scaledWidth}/")
            }
        }

        // 2. Simplecast CDN
        if (httpsUrl.contains("simplecastcdn.com")) {
            val simplecastRegex = Regex("/\\d+x\\d+/")
            if (simplecastRegex.containsMatchIn(httpsUrl)) {
                return httpsUrl.replace(simplecastRegex, "/${scaledWidth}x${scaledWidth}/")
            }
        }

        // 3. Captivate FM
        if (httpsUrl.contains("captivate.fm")) {
            // Replaces patterns like /3000x3000-CN-Pod-Logo.png with /400x400-CN-Pod-Logo.png
            val captivateRegex1 = Regex("/\\d+x\\d+-")
            if (captivateRegex1.containsMatchIn(httpsUrl)) {
                return httpsUrl.replace(captivateRegex1, "/${scaledWidth}x${scaledWidth}-")
            }
            // Or -3000x3000.png
            val captivateRegex2 = Regex("-\\d+x\\d+\\.")
            if (captivateRegex2.containsMatchIn(httpsUrl)) {
                return httpsUrl.replace(captivateRegex2, "-${scaledWidth}x${scaledWidth}.")
            }
        }

        // 4. Imgix / Megaphone / Vox Media (and other Imgix-powered CDNs)
        if (httpsUrl.contains("imgix.net") || httpsUrl.contains("megaphone.fm") || httpsUrl.contains("voxmedia.com")) {
            val uri = android.net.Uri.parse(httpsUrl)
            val builder = uri.buildUpon()
            builder.clearQuery()
            
            // Rebuild query, keeping original parameters except sizing/format
            for (key in uri.queryParameterNames) {
                if (key != "w" && key != "h" && key != "max-w" && key != "max-h" && key != "width" && key != "height" && key != "fit" && key != "auto" && key != "q" && key != "format") {
                    for (value in uri.getQueryParameters(key)) {
                        builder.appendQueryParameter(key, value)
                    }
                }
            }
            // Append optimal sizing parameters
            builder.appendQueryParameter("w", scaledWidth.toString())
            builder.appendQueryParameter("h", scaledWidth.toString())
            builder.appendQueryParameter("fit", "crop")
            builder.appendQueryParameter("auto", "format,compress")
            builder.appendQueryParameter("q", "80")
            return builder.build().toString()
        }
        
        // 5. Generic size pattern in path (e.g. /3000x3000/ or /1400x1400/)
        val genericPathRegex = Regex("/\\d{3,4}x\\d{3,4}/")
        if (genericPathRegex.containsMatchIn(httpsUrl)) {
            return httpsUrl.replace(genericPathRegex, "/${scaledWidth}x${scaledWidth}/")
        }
    } catch (e: Exception) {
        // Fallback to default wsrv.nl proxy on parsing/formatting exception
    }

    // Default fallback to wsrv.nl proxy
    return try {
        val encodedUrl = URLEncoder.encode(httpsUrl, "UTF-8")
        "https://wsrv.nl/?url=$encodedUrl&w=$scaledWidth&q=85&output=webp"
    } catch (e: Exception) {
        httpsUrl
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
