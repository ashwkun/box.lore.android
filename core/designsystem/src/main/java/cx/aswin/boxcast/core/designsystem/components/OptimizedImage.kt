package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/**
 * Global cache of URLs for which the proxy has failed, to avoid repeatedly
 * attempting proxy load for failed items when scrolling lazy lists.
 */
private val proxyFailedUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

/**
 * A reusable image composable that implements a proxy-first loading strategy
 * with automatic fallback to the raw URL if the proxy fails.
 *
 * Loading Order:
 *   1. Try the wsrv.nl proxy (context-sized WebP) — fast, small, sharp.
 *   2. If the proxy errors (e.g. origin blocks wsrv.nl) → fall back to the raw URL.
 *   3. If the raw URL also errors → show [AnimatedShapesFallback].
 *
 * @param url The original, unproxied image URL.
 * @param proxyWidth The desired width in pixels for the wsrv.nl proxy.
 *        Use 2x the dp display size for retina sharpness (e.g. 180dp → 400px).
 * @param contentDescription Accessibility description.
 * @param modifier Modifier for the image.
 * @param contentScale How the image should be scaled inside its bounds.
 * @param colorFilter Optional color filter (e.g., grayscale).
 */
@Composable
fun OptimizedImage(
    url: String?,
    proxyWidth: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null
) {
    LogRecomposition(name = "OptimizedImage: ${url?.takeLast(20)}")
    if (url.isNullOrBlank()) {
        Box(modifier = modifier) {
            AnimatedShapesFallback()
        }
        return
    }

    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val pxWidth = remember(proxyWidth, density) { (proxyWidth * density).toInt() }
    val proxyUrl = remember(url, proxyWidth, density) { url.optimizedImageUrl(proxyWidth) }

    // Check if this url has already failed on the proxy in this app session
    val isProxyKnownFailed = remember(url) { proxyFailedUrls.contains(url) }

    // Track which URL we're currently trying
    var currentUrl by remember(url) { mutableStateOf(if (isProxyKnownFailed) url else proxyUrl) }
    var hasTriedFallback by remember(url) { mutableStateOf(isProxyKnownFailed) }

    val painter = rememberAsyncImagePainter(
        model = remember(currentUrl, pxWidth) {
            ImageRequest.Builder(context)
                .data(currentUrl)
                .size(coil.size.Size(pxWidth, pxWidth))
                .memoryCacheKey("$currentUrl-w$pxWidth")
                .allowHardware(true)
                .crossfade(150)
                .build()
        }
    )
    val state = painter.state

    if (state is AsyncImagePainter.State.Error) {
        // Trigger fallback to raw URL outside of composition via LaunchedEffect
        if (!hasTriedFallback && currentUrl == proxyUrl) {
            LaunchedEffect(url) {
                proxyFailedUrls.add(url)
                hasTriedFallback = true
                currentUrl = url
                trackProxyFallback(url, proxyWidth)
            }
        }
    }

    Box(modifier = modifier) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            modifier = Modifier.fillMaxSize()
        )
        when {
            state is AsyncImagePainter.State.Success -> {
                // Rendered by Image, do nothing
            }
            state is AsyncImagePainter.State.Error && hasTriedFallback -> {
                // Both proxy and raw URL failed. Render final fallback.
                AnimatedShapesFallback()
            }
            else -> {
                // Loading, Empty, or proxy failed but we are falling back: Render lightweight solid placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Podcasts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
    }
}

private fun trackProxyFallback(url: String, proxyWidth: Int) {
    if (kotlin.random.Random.nextInt(10) == 0) {
        val imageHost = try {
            android.net.Uri.parse(url).host ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        com.posthog.PostHog.capture(
            "proxy_fallback_triggered",
            properties = mapOf(
                "image_host" to imageHost,
                "proxy_width" to proxyWidth,
                "sample_multiplier" to 10
            )
        )
    }
}
