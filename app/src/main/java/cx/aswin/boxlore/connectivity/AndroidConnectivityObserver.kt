package cx.aswin.boxlore.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single process-scoped connectivity observer (INTERNET capability).
 * Shared by NavHost offline UX.
 */
class AndroidConnectivityObserver(
    context: Context,
) : ConnectivityStatusPort {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val onlineState = MutableStateFlow(readIsOnline())
    val isOnlineFlow: StateFlow<Boolean> = onlineState.asStateFlow()

    @Volatile
    private var started = false

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onlineState.value = true
            }

            override fun onLost(network: Network) {
                onlineState.value = readIsOnline()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                onlineState.value =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }

    override fun isOnline(): Boolean = onlineState.value

    fun start() {
        if (started) return
        started = true
        onlineState.value = readIsOnline()
        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // Emulators / restricted contexts — keep last known value.
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun readIsOnline(): Boolean =
        try {
            val caps = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            true
        }
}
