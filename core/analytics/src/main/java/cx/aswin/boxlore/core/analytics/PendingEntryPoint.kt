package cx.aswin.boxlore.core.analytics

/**
 * Static holder for entry-point context that needs to cross the
 * MediaController → MediaSession IPC boundary.
 *
 * Bundle extras set via MediaMetadata.Builder().setExtras() are not
 * reliably preserved across Binder serialization. This singleton
 * provides a same-process shortcut: PlaybackRepository writes the
 * context before calling controller.play(), and
 * BoxLorePlaybackService consumes it in startPlaybackSession().
 *
 * Also carries ranking attribution via [KEY_EXPOSURE_ID] so background
 * playback can resolve the exact Home/Learn impression that produced a play.
 *
 * Thread-safe via @Synchronized.
 */
object PendingEntryPoint {
    const val KEY_EXPOSURE_ID = "exposure_id"

    private var pending: Map<String, Any>? = null

    @Synchronized
    fun set(context: Map<String, Any>) {
        pending = context
    }

    /**
     * Sets the pending context only if nothing is already queued. Lets an explicit
     * source (e.g. "episode_info_screen") set by a screen win over a generic default
     * (e.g. "resume_player") applied later on the same play() call.
     */
    @Synchronized
    fun setIfAbsent(context: Map<String, Any>) {
        if (pending == null) pending = context
    }

    @Synchronized
    fun consume(): Map<String, Any>? {
        val result = pending
        pending = null
        return result
    }
}
