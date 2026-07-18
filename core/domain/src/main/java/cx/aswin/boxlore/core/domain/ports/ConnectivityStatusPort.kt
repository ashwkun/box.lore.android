package cx.aswin.boxlore.core.domain.ports

/**
 * Process-scoped online/offline signal for adaptive Home content and nav offline UX.
 *
 * Production: [cx.aswin.boxlore.connectivity.AndroidConnectivityObserver] on [AppContainer].
 * Tests: [AlwaysOnlineConnectivity] / fixed doubles.
 */
fun interface ConnectivityStatusPort {
    fun isOnline(): Boolean
}

/** Default for unit tests and pre-observer wiring. */
object AlwaysOnlineConnectivity : ConnectivityStatusPort {
    override fun isOnline(): Boolean = true
}
