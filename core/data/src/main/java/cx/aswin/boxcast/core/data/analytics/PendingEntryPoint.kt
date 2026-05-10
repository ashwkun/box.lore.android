package cx.aswin.boxcast.core.data.analytics

/**
 * Static holder for entry-point context that needs to cross the
 * MediaController → MediaSession IPC boundary.
 * 
 * Bundle extras set via MediaMetadata.Builder().setExtras() are not
 * reliably preserved across Binder serialization. This singleton
 * provides a same-process shortcut: PlaybackRepository writes the
 * context before calling controller.play(), and
 * BoxCastPlaybackService consumes it in startPlaybackSession().
 * 
 * Thread-safe via @Synchronized.
 */
object PendingEntryPoint {
    private var pending: Map<String, Any>? = null

    @Synchronized
    fun set(context: Map<String, Any>) {
        pending = context
    }

    @Synchronized
    fun consume(): Map<String, Any>? {
        val result = pending
        pending = null
        return result
    }
}
