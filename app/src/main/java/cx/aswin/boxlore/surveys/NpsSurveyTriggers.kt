package cx.aswin.boxlore.surveys

import android.util.Log
import com.posthog.PostHog
import cx.aswin.boxlore.core.data.EngagementPromptCoordinator
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fires NPS survey trigger events on app open.
 *
 * 1. Console remote trigger: if the `survey-nps-remote-trigger` flag is enabled for this
 *    user, fire the manual trigger and reload flags so the one-shot trigger doesn't repeat.
 * 2. Deferred auto trigger: if the survey was marked pending (ep 3 reached, possibly during
 *    background playback) and hasn't fired yet, fire it now and mark it fired.
 *
 * PostHog owns the survey's display conditions (onboarding status, audience flag, wait
 * period), so this only emits the trigger events.
 */
object NpsSurveyTriggers {
    private const val TAG = "NpsSurvey"

    fun check(
        surveyPrefs: UserPreferencesRepository,
        engagementCoordinator: EngagementPromptCoordinator,
        isCurrentlyPlaying: () -> Boolean,
        scope: CoroutineScope,
    ) {
        try {
            if (
                PostHog.isFeatureEnabled("survey-nps-remote-trigger") &&
                engagementCoordinator.canShowProactivePrompt(isCurrentlyPlaying())
            ) {
                AnalyticsHelper.trackSurveyNpsManualTrigger(source = "remote_flag")
                PostHog.reloadFeatureFlags()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote trigger check failed", e)
        }

        scope.launch {
            try {
                if (
                    surveyPrefs.isNpsSurveyPending() &&
                    !surveyPrefs.hasNpsSurveyFired() &&
                    engagementCoordinator.canShowProactivePrompt(isCurrentlyPlaying()) &&
                    surveyPrefs.isEngagementCooldownElapsed()
                ) {
                    AnalyticsHelper.trackSurveyNpsEligible(
                        completedEpisodes = surveyPrefs.npsSurveyCompletedCount(),
                        triggerContext = "app_open",
                    )
                    surveyPrefs.markNpsSurveyFired()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Deferred trigger check failed", e)
            }
        }
    }
}
