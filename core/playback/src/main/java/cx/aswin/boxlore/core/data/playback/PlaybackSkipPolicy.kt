package cx.aswin.boxlore.core.data.playback

import cx.aswin.boxlore.core.prefs.PlaybackSkipBounds

object PlaybackSkipPolicy {
    const val DEFAULT_SKIP_BEGINNING_MS = PlaybackSkipBounds.DEFAULT_SKIP_BEGINNING_MS
    const val DEFAULT_SKIP_ENDING_MS = PlaybackSkipBounds.DEFAULT_SKIP_ENDING_MS
    const val DEFAULT_SEEK_BACKWARD_MS = PlaybackSkipBounds.DEFAULT_SEEK_BACKWARD_MS
    const val DEFAULT_SEEK_FORWARD_MS = PlaybackSkipBounds.DEFAULT_SEEK_FORWARD_MS

    const val MAX_TRIM_MS = PlaybackSkipBounds.MAX_TRIM_MS
    const val MIN_SEEK_MS = PlaybackSkipBounds.MIN_SEEK_MS
    const val MAX_SEEK_MS = PlaybackSkipBounds.MAX_SEEK_MS
    const val MEANINGFUL_RESUME_MS = 2_000L
    const val MIN_PLAYABLE_CONTENT_MS = 30_000L

    data class EffectiveTrim(
        val skipBeginningMs: Long,
        val skipEndingMs: Long,
    )

    enum class InitialPositionReason {
        EXPLICIT,
        RESUME,
        SKIP_BEGINNING,
        START,
    }

    data class InitialPosition(
        val positionMs: Long,
        val reason: InitialPositionReason,
    )

    fun sanitizeTrim(valueMs: Long): Long = PlaybackSkipBounds.sanitizeTrim(valueMs)

    fun sanitizeSeekBackward(valueMs: Long): Long = PlaybackSkipBounds.sanitizeSeekBackward(valueMs)

    fun sanitizeSeekForward(valueMs: Long): Long = PlaybackSkipBounds.sanitizeSeekForward(valueMs)

    fun resolveEffectiveTrim(
        globalSkipBeginningMs: Long,
        globalSkipEndingMs: Long,
        podcastSkipBeginningOverrideMs: Long?,
        podcastSkipEndingOverrideMs: Long?,
    ): EffectiveTrim =
        EffectiveTrim(
            skipBeginningMs =
                sanitizeTrim(
                    podcastSkipBeginningOverrideMs ?: globalSkipBeginningMs,
                ),
            skipEndingMs =
                sanitizeTrim(
                    podcastSkipEndingOverrideMs ?: globalSkipEndingMs,
                ),
        )

    fun resolveInitialPosition(
        explicitPositionMs: Long?,
        savedProgressMs: Long,
        isCompleted: Boolean,
        skipBeginningMs: Long,
        resetRequested: Boolean = false,
    ): InitialPosition {
        if (explicitPositionMs != null) {
            return InitialPosition(explicitPositionMs.coerceAtLeast(0L), InitialPositionReason.EXPLICIT)
        }
        if (!resetRequested && !isCompleted && savedProgressMs > MEANINGFUL_RESUME_MS) {
            return InitialPosition(savedProgressMs, InitialPositionReason.RESUME)
        }
        val sanitizedBeginning = sanitizeTrim(skipBeginningMs)
        return if (sanitizedBeginning > 0L) {
            InitialPosition(sanitizedBeginning, InitialPositionReason.SKIP_BEGINNING)
        } else {
            InitialPosition(0L, InitialPositionReason.START)
        }
    }

    fun hasSafePlayableWindow(
        durationMs: Long,
        skipBeginningMs: Long,
        skipEndingMs: Long,
    ): Boolean {
        if (durationMs <= 0L) return false
        val beginning = sanitizeTrim(skipBeginningMs)
        val ending = sanitizeTrim(skipEndingMs)
        return beginning < durationMs &&
            ending < durationMs &&
            durationMs - beginning - ending >= MIN_PLAYABLE_CONTENT_MS
    }

    fun outroBoundaryMs(
        durationMs: Long,
        skipEndingMs: Long,
    ): Long? {
        val ending = sanitizeTrim(skipEndingMs)
        if (durationMs <= 0L || ending <= 0L) return null
        return (durationMs - ending).takeIf { it >= 0L }
    }

    fun effectiveEndingTrimForCompletion(
        durationMs: Long,
        skipBeginningMs: Long,
        skipEndingMs: Long,
    ): Long {
        val ending = sanitizeTrim(skipEndingMs)
        return ending.takeIf {
            it > 0L &&
                hasSafePlayableWindow(
                    durationMs = durationMs,
                    skipBeginningMs = skipBeginningMs,
                    skipEndingMs = it,
                )
        } ?: 0L
    }

    fun isNaturalOutroCrossing(
        previousPositionMs: Long,
        currentPositionMs: Long,
        durationMs: Long,
        skipBeginningMs: Long,
        skipEndingMs: Long,
        armed: Boolean,
        isPlaying: Boolean,
    ): Boolean {
        if (!armed || !isPlaying) return false
        if (!hasSafePlayableWindow(durationMs, skipBeginningMs, skipEndingMs)) return false
        val boundary = outroBoundaryMs(durationMs, skipEndingMs) ?: return false
        return previousPositionMs < boundary && currentPositionMs >= boundary
    }

    /**
     * Existing early-completion heuristics remain unchanged when trimming is off. When an
     * effective ending trim is active, only the service's terminal effective-end path completes
     * the episode, preventing progress writers from completing it minutes before the boundary.
     */
    fun shouldCompleteFromProgress(
        positionMs: Long,
        durationMs: Long,
        effectiveSkipEndingMs: Long,
    ): Boolean {
        if (durationMs <= 0L || sanitizeTrim(effectiveSkipEndingMs) > 0L) return false
        return positionMs >= durationMs - 5_000L ||
            positionMs >= durationMs * 0.95 ||
            (durationMs >= 900_000L && durationMs - positionMs <= 300_000L)
    }
}
