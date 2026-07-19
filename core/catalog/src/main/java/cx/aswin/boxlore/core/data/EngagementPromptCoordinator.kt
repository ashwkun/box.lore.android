package cx.aswin.boxlore.core.data

import android.util.Log
import cx.aswin.boxlore.core.prefs.EngagementPromptConstants
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Coordinates proactive engagement prompts (NPS survey, Play review) so only one
 * surfaces per session and promoter handoffs follow the unified strategy.
 */
class EngagementPromptCoordinator(
    private val userPrefs: UserPreferencesRepository,
    private val scope: CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, "Engagement prompt side-effect failed", throwable)
                },
        ),
) {
    /** In-memory guard: at most one proactive modal per app process session. */
    @Volatile
    var sessionProactivePromptShown: Boolean = false
        private set

    /** Returns true when playback is idle and no proactive prompt has shown this session. */
    fun canShowProactivePrompt(isPlaying: Boolean): Boolean =
        !isPlaying && !sessionProactivePromptShown

    /** Marks the current session as having shown a proactive prompt and persists cooldown. */
    fun recordProactivePromptShown() {
        sessionProactivePromptShown = true
        scope.launch {
            try {
                userPrefs.recordEngagementPromptShown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record engagement prompt", e)
            }
        }
    }

    /** Called when the PostHog NPS sheet becomes visible. */
    fun onSurveyDisplayed() {
        recordProactivePromptShown()
    }

    /**
     * Persists the first NPS rating and queues promoter Play review when [score] is 8+.
     */
    fun onNpsRatingSubmitted(score: Int?) {
        if (score == null) return
        scope.launch {
            try {
                userPrefs.setNpsLastScore(score)
                if (score >= EngagementPromptConstants.PROMOTER_SCORE_THRESHOLD) {
                    userPrefs.setPromoterReviewPending(true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist NPS score", e)
            }
        }
    }

    /**
     * True when a deferred promoter handoff is pending and the user has not reviewed yet.
     * Does not apply the 14-day cooldown — that prompt completes the NPS journey.
     */
    suspend fun shouldShowPromoterReview(isPlaying: Boolean): Boolean {
        if (!canShowProactivePrompt(isPlaying)) return false
        if (userPrefs.hasReviewedSync()) return false
        if (!userPrefs.hasNpsSurveyFired()) return false
        return userPrefs.isPromoterReviewPending()
    }

    /** Clears the one-shot promoter handoff flag after the review sheet is shown or dismissed. */
    suspend fun clearPromoterReviewPending() {
        userPrefs.setPromoterReviewPending(false)
    }

    /** Whether enough time has passed since the last proactive engagement prompt. */
    suspend fun isEngagementCooldownElapsed(): Boolean = userPrefs.isEngagementCooldownElapsed()

    companion object {
        private const val TAG = "EngagementPromptCoordinator"
    }
}
