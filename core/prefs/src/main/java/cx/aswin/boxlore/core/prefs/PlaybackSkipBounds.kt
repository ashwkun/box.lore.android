package cx.aswin.boxlore.core.prefs

/**
 * Shared skip/seek duration bounds for preferences and playback policy.
 *
 * Lives in `:core:prefs` so DataStore sanitization and playback policy share one source of truth.
 * [cx.aswin.boxlore.core.prefs.playback.PlaybackSkipPolicy] delegates defaults/sanitize here.
 */
object PlaybackSkipBounds {
    const val DEFAULT_SKIP_BEGINNING_MS = 0L
    const val DEFAULT_SKIP_ENDING_MS = 0L
    const val DEFAULT_SEEK_BACKWARD_MS = 10_000L
    const val DEFAULT_SEEK_FORWARD_MS = 30_000L

    const val MAX_TRIM_MS = 300_000L
    const val MIN_SEEK_MS = 5_000L
    const val MAX_SEEK_MS = 120_000L

    fun sanitizeTrim(valueMs: Long): Long = valueMs.coerceIn(0L, MAX_TRIM_MS)

    fun sanitizeSeekBackward(valueMs: Long): Long =
        valueMs.coerceIn(MIN_SEEK_MS, MAX_SEEK_MS)

    fun sanitizeSeekForward(valueMs: Long): Long =
        valueMs.coerceIn(MIN_SEEK_MS, MAX_SEEK_MS)
}
