package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnboardingSearchBackStepTest {
    @Test
    fun `resolve returns welcome for welcome entry`() {
        assertEquals(
            OnboardingStep.WELCOME,
            OnboardingSearchBackStep.resolve("welcome_screen", emptySet()),
        )
    }

    @Test
    fun `resolve returns sub genres when genres were selected`() {
        assertEquals(
            OnboardingStep.SUB_GENRES,
            OnboardingSearchBackStep.resolve("genre_screen", setOf("tech")),
        )
    }

    @Test
    fun `resolve returns genres screen when search opened from genre flow without picks`() {
        assertEquals(
            OnboardingStep.GENRES,
            OnboardingSearchBackStep.resolve("genre_screen", emptySet()),
        )
    }

    @Test
    fun `resolve returns ai onboarding for ai side trip`() {
        assertEquals(
            OnboardingStep.AI_ONBOARDING,
            OnboardingSearchBackStep.resolve("ai_onboarding", emptySet()),
        )
    }
}
