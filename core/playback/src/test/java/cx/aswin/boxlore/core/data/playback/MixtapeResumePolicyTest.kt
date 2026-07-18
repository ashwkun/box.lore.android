package cx.aswin.boxlore.core.data.playback

import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixtapeResumePolicyTest {
    @Test
    fun `non mixtape entry points never reset`() {
        assertFalse(
            MixtapeResumePolicy.shouldResetPlayback(
                savedProgressMs = 0L,
                durationMs = 3_600_000L,
                entryPoint = PlaybackEntryPoint.GENERIC,
            ),
        )
    }

    @Test
    fun `mixtape resets near start end or short remaining`() {
        assertTrue(
            MixtapeResumePolicy.shouldResetPlayback(
                savedProgressMs = 10_000L,
                durationMs = 3_600_000L,
                entryPoint = PlaybackEntryPoint.HOME_MIXTAPE,
            ),
        )
        assertTrue(
            MixtapeResumePolicy.shouldResetPlayback(
                savedProgressMs = 3_400_000L,
                durationMs = 3_600_000L,
                entryPoint = PlaybackEntryPoint.HOME_MIXTAPE,
            ),
        )
        assertTrue(
            MixtapeResumePolicy.shouldResetPlayback(
                savedProgressMs = 3_500_000L,
                durationMs = 3_600_000L,
                entryPoint = PlaybackEntryPoint.HOME_MIXTAPE,
            ),
        )
        assertFalse(
            MixtapeResumePolicy.shouldResetPlayback(
                savedProgressMs = 1_000_000L,
                durationMs = 3_600_000L,
                entryPoint = PlaybackEntryPoint.HOME_MIXTAPE,
            ),
        )
    }
}
