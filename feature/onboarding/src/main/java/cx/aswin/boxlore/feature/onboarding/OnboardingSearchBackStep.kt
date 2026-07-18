package cx.aswin.boxlore.feature.onboarding

/**
 * Pure navigation target when leaving the onboarding search screen.
 */
internal object OnboardingSearchBackStep {
    fun resolve(
        searchEntryPoint: String,
        selectedGenres: Set<String>,
    ): OnboardingStep =
        when (searchEntryPoint) {
            "welcome_screen" -> OnboardingStep.WELCOME
            "genre_screen" ->
                if (selectedGenres.isNotEmpty()) {
                    OnboardingStep.SUB_GENRES
                } else {
                    OnboardingStep.GENRES
                }
            "ai_onboarding" -> OnboardingStep.AI_ONBOARDING
            else -> OnboardingStep.WELCOME
        }
}
