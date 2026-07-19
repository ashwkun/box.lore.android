package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.PlaybackEntryPoint

/**
 * Pure mixtape resume / restart decision.
 * Extracted from [cx.aswin.boxlore.core.playback.PlaybackRepository].
 */
object MixtapeResumePolicy {
    fun shouldResetPlayback(
        savedProgressMs: Long,
        durationMs: Long,
        entryPoint: PlaybackEntryPoint,
    ): Boolean {
        if (entryPoint != PlaybackEntryPoint.HOME_MIXTAPE) return false
        val ratio = if (durationMs > 0L) savedProgressMs.toDouble() / durationMs.toDouble() else 0.0
        val remainingMs = durationMs - savedProgressMs
        return ratio < 0.10 || ratio > 0.90 || (durationMs > 0L && remainingMs < 120_000L)
    }
}
