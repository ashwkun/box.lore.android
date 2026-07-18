package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnboardingGenreLimitsTest {
    @Test
    fun `per genre trending limit scales with selection count`() {
        assertEquals(0, OnboardingGenreLimits.perGenreTrendingLimit(0))
        assertEquals(5, OnboardingGenreLimits.perGenreTrendingLimit(1))
        assertEquals(5, OnboardingGenreLimits.perGenreTrendingLimit(2))
        assertEquals(3, OnboardingGenreLimits.perGenreTrendingLimit(4))
        assertEquals(2, OnboardingGenreLimits.perGenreTrendingLimit(5))
    }
}
