package cx.aswin.boxlore.feature.onboarding

/**
 * Caps trending fetches per genre during onboarding so chart loads stay bounded.
 */
internal object OnboardingGenreLimits {
    fun perGenreTrendingLimit(genreCount: Int): Int =
        when {
            genreCount <= 0 -> 0
            genreCount <= 2 -> 5
            genreCount <= 4 -> 3
            else -> 2
        }
}
