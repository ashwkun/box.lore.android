package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ColorFilter
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

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

    val proxyUrl = remember(url, proxyWidth) { url.optimizedImageUrl(proxyWidth) }

    // Track which URL we're currently trying
    var currentUrl by remember(url) { mutableStateOf(proxyUrl) }
    var hasTriedFallback by remember(url) { mutableStateOf(false) }

    val painter = rememberAsyncImagePainter(model = currentUrl)
    val state = painter.state

    if (state is AsyncImagePainter.State.Error) {
        // Trigger fallback to raw URL outside of composition via LaunchedEffect
        if (!hasTriedFallback && currentUrl == proxyUrl) {
            LaunchedEffect(url) {
                hasTriedFallback = true
                currentUrl = url
                
                // Track the fallback so we can monitor proxy reliability
                com.posthog.PostHog.capture(
                    "proxy_fallback_triggered",
                    properties = mapOf(
                        "original_url" to url,
                        "proxy_width" to proxyWidth
                    )
                )
            }
        }
    }

    Box(modifier = modifier) {
        if (state is AsyncImagePainter.State.Success) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AnimatedShapesFallback()
        }
    }
}
