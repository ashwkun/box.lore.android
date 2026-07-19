package cx.aswin.boxlore.lifecycle

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.downloads.DownloadSpeedLimiter

/**
 * Bandwidth-adaptive download throttling: throttle more when playing on a slow link.
 */
@Composable
fun DownloadBandwidthEffect(isPlaying: Boolean) {
    val context = LocalContext.current
    var isConnectionFast by remember { mutableStateOf(true) }
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                isConnectionFast = networkCapabilities.linkDownstreamBandwidthKbps > 15000
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to register network callback", e)
        }
        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
                /* ignore */
            }
        }
    }
    LaunchedEffect(isPlaying, isConnectionFast) {
        if (isConnectionFast) {
            DownloadSpeedLimiter.speedLimitBps = 0L
        } else if (isPlaying) {
            DownloadSpeedLimiter.speedLimitBps = 250 * 1024L
        } else {
            DownloadSpeedLimiter.speedLimitBps = 750 * 1024L
        }
    }
}
