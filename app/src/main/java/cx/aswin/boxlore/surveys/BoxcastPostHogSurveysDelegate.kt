package cx.aswin.boxlore.surveys

import android.app.Application
import android.content.Context
import com.posthog.surveys.OnPostHogSurveyClosed
import com.posthog.surveys.OnPostHogSurveyResponse
import com.posthog.surveys.OnPostHogSurveyShown
import com.posthog.surveys.PostHogDisplaySurvey
import com.posthog.surveys.PostHogSurveysDelegate
import cx.aswin.boxlore.core.data.EngagementPromptCoordinator
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.surveys.internal.ActivityProvider
import cx.aswin.boxlore.surveys.internal.BoxcastSurveyHost

/**
 * Material3 1.5-compatible survey UI delegate.
 *
 * Replaces `posthog-android-surveys-compose:0.1.0`, which crashes at runtime
 * against Material3 1.5 because it calls a removed `ModalBottomSheetProperties`
 * constructor.
 */
class BoxcastPostHogSurveysDelegate(
    context: Context,
    private val userPrefs: UserPreferencesRepository,
    private val engagementCoordinator: EngagementPromptCoordinator,
) : PostHogSurveysDelegate {
    private val application: Application = context.applicationContext as Application
    private val activityProvider = ActivityProvider()
    private val host =
        BoxcastSurveyHost(
            activityProvider = activityProvider,
            userPrefs = userPrefs,
            onSurveyDisplayed = {
                engagementCoordinator.onSurveyDisplayed()
                AnalyticsHelper.trackEngagementPromptShown(
                    promptType = "nps_survey",
                    source = "posthog",
                )
            },
            onFirstRatingSubmitted = { score ->
                engagementCoordinator.onNpsRatingSubmitted(score)
            },
        )

    init {
        application.registerActivityLifecycleCallbacks(activityProvider)
    }

    override fun renderSurvey(
        survey: PostHogDisplaySurvey,
        onSurveyShown: OnPostHogSurveyShown,
        onSurveyResponse: OnPostHogSurveyResponse,
        onSurveyClosed: OnPostHogSurveyClosed,
    ) {
        host.show(
            survey = survey,
            onSurveyShown = onSurveyShown,
            onSurveyResponse = onSurveyResponse,
            onSurveyClosed = onSurveyClosed,
        )
    }

    override fun cleanupSurveys() {
        host.cleanup()
    }
}
