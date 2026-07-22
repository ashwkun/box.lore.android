package cx.aswin.boxlore.core.catalog.home

/**
 * Durable Home personalization mode derived from meaningful-listen evidence and
 * learner eligibility. Recomputed from persisted signals — not one-shot UI flags.
 */
enum class HomePersonalizationMode {
    /** Honest regional charts; Because You Like hidden. */
    REGIONAL,

    /** Second+ meaningful play landed; personalized retrieval in flight. */
    PERSONALIZING,

    /** Taste and Because You Like eligible after successful candidate load. */
    PERSONALIZED,
}

/**
 * Pure mode derivation for Home cold-start → personalized transition.
 *
 * Thresholds (plan):
 * - 0–1 meaningful plays → [HomePersonalizationMode.REGIONAL]
 * - ≥2 meaningful plays → attempt personalization ([PERSONALIZING] until loaded)
 * - By the third meaningful play, transition to [PERSONALIZED] when an eligible
 *   positively evidenced show exists and candidates loaded successfully.
 */
object HomePersonalizationModeLogic {
    const val MEANINGFUL_PLAYS_TO_ATTEMPT = 2
    const val MEANINGFUL_PLAYS_TO_REQUIRE_ANCHOR = 3

    fun derive(
        meaningfulPlayCount: Int,
        hasEligiblePositiveShow: Boolean,
        personalizedCandidatesLoaded: Boolean,
        personalizedRequestFailed: Boolean,
    ): HomePersonalizationMode {
        if (meaningfulPlayCount < MEANINGFUL_PLAYS_TO_ATTEMPT) {
            return HomePersonalizationMode.REGIONAL
        }
        val anchorReady =
            meaningfulPlayCount < MEANINGFUL_PLAYS_TO_REQUIRE_ANCHOR ||
                hasEligiblePositiveShow
        if (!anchorReady) {
            return HomePersonalizationMode.REGIONAL
        }
        if (personalizedCandidatesLoaded) {
            return HomePersonalizationMode.PERSONALIZED
        }
        // Transient failure keeps last regional slate visible but stays PERSONALIZING
        // so retries continue (plan: do not get stuck).
        if (personalizedRequestFailed) {
            return HomePersonalizationMode.PERSONALIZING
        }
        return HomePersonalizationMode.PERSONALIZING
    }

    fun showBecauseYouLike(mode: HomePersonalizationMode): Boolean = mode == HomePersonalizationMode.PERSONALIZED

    fun tasteSectionTitle(mode: HomePersonalizationMode): String =
        when (mode) {
            HomePersonalizationMode.REGIONAL,
            HomePersonalizationMode.PERSONALIZING,
            -> "Popular in your Region"
            HomePersonalizationMode.PERSONALIZED -> "Based on Your Taste"
        }

    fun tasteSectionSubtitle(mode: HomePersonalizationMode): String =
        when (mode) {
            HomePersonalizationMode.REGIONAL,
            HomePersonalizationMode.PERSONALIZING,
            -> "Popular picks from listeners near you"
            HomePersonalizationMode.PERSONALIZED -> "Picked from your listening patterns"
        }
}
