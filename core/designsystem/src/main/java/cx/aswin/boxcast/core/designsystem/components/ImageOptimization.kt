package cx.aswin.boxcast.core.designsystem.components

import java.net.URLEncoder

private const val SCHEME_HTTP = "http://"
private const val SCHEME_HTTPS = "https://"

private fun calculateScaledWidth(originalWidth: Int): Int {
    return try {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        val density = metrics.density
        val screenWidthPx = metrics.widthPixels
        val isLargeScreen = (screenWidthPx / density) >= 600f
        
        // originalWidth is already in pixels (callers pass dp * ~2), so only apply
        // a quality multiplier — do NOT multiply by density again.
        val qualityMultiplier = when {
            isLargeScreen -> 2.0f
            density >= 3.5f -> 1.5f
            else -> 1.3f
        }
        (originalWidth * qualityMultiplier).toInt().coerceIn(10, 2048)
    } catch (e: Exception) {
        originalWidth
    }
}

private fun optimizeBbcCdn(httpsUrl: String, scaledWidth: Int): String? {
    if (httpsUrl.contains("ichef.bbci.co.uk")) {
        val ichefRegex = Regex("/images/ic/\\d+x\\d+/")
        if (ichefRegex.containsMatchIn(httpsUrl)) {
            return httpsUrl.replace(ichefRegex, "/images/ic/${scaledWidth}x${scaledWidth}/")
        }
    }
    return null
}

private fun optimizeSimplecastCdn(httpsUrl: String, scaledWidth: Int): String? {
    if (httpsUrl.contains("simplecastcdn.com")) {
        val simplecastRegex = Regex("/\\d+x\\d+/")
        if (simplecastRegex.containsMatchIn(httpsUrl)) {
            return httpsUrl.replace(simplecastRegex, "/${scaledWidth}x${scaledWidth}/")
        }
    }
    return null
}

private fun optimizeCaptivateCdn(httpsUrl: String, scaledWidth: Int): String? {
    if (httpsUrl.contains("captivate.fm")) {
        val captivateRegex1 = Regex("/\\d+x\\d+-")
        if (captivateRegex1.containsMatchIn(httpsUrl)) {
            return httpsUrl.replace(captivateRegex1, "/${scaledWidth}x${scaledWidth}-")
        }
        val captivateRegex2 = Regex("-\\d+x\\d+\\.")
        if (captivateRegex2.containsMatchIn(httpsUrl)) {
            return httpsUrl.replace(captivateRegex2, "-${scaledWidth}x${scaledWidth}.")
        }
    }
    return null
}

private fun optimizeImgixCdn(httpsUrl: String, scaledWidth: Int): String? {
    try {
        val uri = android.net.Uri.parse(httpsUrl)
        val builder = uri.buildUpon()
        builder.clearQuery()
        
        for (key in uri.queryParameterNames) {
            if (key != "w" && key != "h" && key != "max-w" && key != "max-h" && key != "width" && key != "height" && key != "fit" && key != "auto" && key != "q" && key != "format") {
                for (value in uri.getQueryParameters(key)) {
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        builder.appendQueryParameter("w", scaledWidth.toString())
        builder.appendQueryParameter("h", scaledWidth.toString())
        builder.appendQueryParameter("fit", "crop")
        builder.appendQueryParameter("auto", "format,compress")
        builder.appendQueryParameter("q", "80")
        return builder.build().toString()
    } catch (e: Exception) {
        return null
    }
}

private fun optimizeKnownCdns(httpsUrl: String, scaledWidth: Int): String? {
    try {
        optimizeBbcCdn(httpsUrl, scaledWidth)?.let { return it }
        optimizeSimplecastCdn(httpsUrl, scaledWidth)?.let { return it }
        optimizeCaptivateCdn(httpsUrl, scaledWidth)?.let { return it }
        
        if (httpsUrl.contains("imgix.net") || httpsUrl.contains("megaphone.fm") || httpsUrl.contains("voxmedia.com")) {
            return optimizeImgixCdn(httpsUrl, scaledWidth)
        }
        
        val genericPathRegex = Regex("/\\d{3,4}x\\d{3,4}/")
        if (genericPathRegex.containsMatchIn(httpsUrl)) {
            return httpsUrl.replace(genericPathRegex, "/${scaledWidth}x${scaledWidth}/")
        }
    } catch (e: Exception) {
        // Fallback to default wsrv.nl proxy
    }
    return null
}

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
    if (cleanedUrl.isBlank() || (!cleanedUrl.startsWith(SCHEME_HTTP) && !cleanedUrl.startsWith(SCHEME_HTTPS))) {
        return cleanedUrl
    }

    // Bypass proxy for first-party dynamic briefing images to ensure high resolution and avoid caching issues
    if (cleanedUrl.contains("aswin.cx", ignoreCase = true)) {
        return cleanedUrl
    }

    // Upgrade to HTTPS for all requests to satisfy Android cleartext security rules
    val httpsUrl = if (cleanedUrl.startsWith(SCHEME_HTTP)) {
        cleanedUrl.replaceFirst(SCHEME_HTTP, SCHEME_HTTPS)
    } else {
        cleanedUrl
    }
    
    // Dynamically scale width based on screen density and tablet/viewport configuration
    val scaledWidth = calculateScaledWidth(width)
    
    // NATIVE CDN OPTIMIZATIONS (Completely bypasses third-party proxy lag/failure!)
    val optimizedCdnUrl = optimizeKnownCdns(httpsUrl, scaledWidth)
    if (optimizedCdnUrl != null) {
        return optimizedCdnUrl
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

    // Hardcoded override for The Vergecast low-res feed artwork
    if (cleaned.contains("dc65bd8c06aac32075dc6102fa4b7057")) {
        return "https://is1-ssl.mzstatic.com/image/thumb/Podcasts221/v4/3b/f7/db/3bf7dbc4-b8cc-3b1f-aab3-d5efdc64b1f1/mza_12861866806353468883.jpeg/600x600bb.jpg"
    }

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
