package cx.aswin.boxlore.core.playback

/** Throttle policy for Android Auto collage prewarm / refresh. */
object AutoCollagePrewarmPolicy {
    const val MIN_REFRESH_INTERVAL_MS = 30_000L

    fun shouldRun(
        force: Boolean,
        lastPrewarmAtMs: Long,
        nowMs: Long,
    ): Boolean = force || nowMs - lastPrewarmAtMs >= MIN_REFRESH_INTERVAL_MS
}
