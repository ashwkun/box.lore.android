package cx.aswin.boxlore.surveys.internal

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toDrawable
import com.posthog.PostHog
import com.posthog.surveys.OnPostHogSurveyClosed
import com.posthog.surveys.OnPostHogSurveyResponse
import com.posthog.surveys.OnPostHogSurveyShown
import com.posthog.surveys.PostHogDisplaySurvey
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.surveys.internal.ui.SurveySheet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Presents PostHog surveys in a Compose [ComponentDialog] attached to the foreground activity.
 * Survives configuration changes by preserving saveable state and reattaching on resume.
 */
internal class BoxcastSurveyHost(
    private val activityProvider: ActivityProvider,
    private val userPrefs: UserPreferencesRepository,
    private val onSurveyDisplayed: () -> Unit,
    private val onFirstRatingSubmitted: (Int?) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable ->
                    Log.e(TAG, "Survey host side-effect failed", throwable)
                },
        )

    private var dialog: ComponentDialog? = null
    private var composeView: ComposeView? = null
    private var hostActivity: Activity? = null
    private var currentSurvey: PostHogDisplaySurvey? = null
    private var onShownCallback: OnPostHogSurveyShown? = null
    private var onResponseCallback: OnPostHogSurveyResponse? = null
    private var onClosedCallback: OnPostHogSurveyClosed? = null
    private var shownReported = false
    private var awaitingForeground = false
    private var saveableRegistry: SaveableStateRegistry? = null
    private var savedSurveyState: Map<String, List<Any?>>? = null

    init {
        activityProvider.onActivityDestroyedListener = { destroyed ->
            if (destroyed === hostActivity) {
                if (destroyed.isChangingConfigurations) {
                    preserveForConfigChange()
                } else {
                    dismissInternal(notifyClosed = true)
                }
            }
        }
        activityProvider.onActivityResumedListener = { resumed ->
            if (currentSurvey != null && (savedSurveyState != null || awaitingForeground)) {
                present(resumed)
            }
        }
    }

    /** Queues the survey and presents it on the current foreground activity. */
    fun show(
        survey: PostHogDisplaySurvey,
        onSurveyShown: OnPostHogSurveyShown,
        onSurveyResponse: OnPostHogSurveyResponse,
        onSurveyClosed: OnPostHogSurveyClosed,
    ) {
        runOnMain {
            dismissInternal(notifyClosed = true)

            currentSurvey = survey
            onShownCallback = onSurveyShown
            onResponseCallback = onSurveyResponse
            onClosedCallback = onSurveyClosed
            present(activityProvider.foregroundActivity)
        }
    }

    /** Dismisses any visible survey without notifying PostHog of a close event. */
    fun cleanup() {
        runOnMain { dismissInternal(notifyClosed = false) }
    }

    private fun present(activity: Activity?) {
        val survey = currentSurvey ?: return

        if (activity == null || activity.isFinishing) {
            awaitingForeground = true
            return
        }

        val presented =
            guard("presenting the survey") {
                awaitingForeground = false
                hostActivity = activity

                val registry =
                    SaveableStateRegistry(
                        restoredValues = savedSurveyState,
                        canBeSaved = { true },
                    )
                saveableRegistry = registry
                savedSurveyState = null

                val themeConfig = userPrefs.cachedThemeConfig

                val view =
                    ComposeView(activity).apply {
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            val darkTheme =
                                when (themeConfig) {
                                    "light" -> false
                                    "dark" -> true
                                    else -> isSystemInDarkTheme()
                                }
                            BoxLoreTheme(
                                darkTheme = darkTheme,
                                dynamicColor = userPrefs.cachedUseDynamicColor,
                                themeBrand = userPrefs.cachedThemeBrand,
                                surfaceStyle = userPrefs.cachedSurfaceStyle,
                            ) {
                                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                                    SurveySheet(
                                        survey = survey,
                                        onSurveyShown = { reportShownOnce() },
                                        onSubmit = { questionIndex, response ->
                                            onResponseCallback?.invoke(survey, questionIndex, response)
                                        },
                                        onClose = { dismissInternal(notifyClosed = true) },
                                        onFirstRatingSubmitted = onFirstRatingSubmitted,
                                    )
                                }
                            }
                        }
                    }

                val componentDialog =
                    ComponentDialog(activity).apply {
                        setContentView(view)
                        setCancelable(false)
                        setCanceledOnTouchOutside(false)
                        configureWindow(window)
                    }

                dialog = componentDialog
                composeView = view
                componentDialog.show()
            }

        if (!presented) {
            dismissInternal(notifyClosed = false)
        }
    }

    private fun reportShownOnce() {
        if (shownReported) return
        val survey = currentSurvey ?: return
        shownReported = true
        onSurveyDisplayed()
        scope.launch {
            runCatching {
                if (!userPrefs.hasNpsSurveyFired()) {
                    userPrefs.markNpsSurveyFired()
                }
            }.onFailure { Log.e(TAG, "Failed to mark NPS survey fired", it) }
        }
        onShownCallback?.invoke(survey)
    }

    private fun configureWindow(window: Window?) {
        window ?: return
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    private fun preserveForConfigChange() {
        guard("preserving the survey across a configuration change") {
            savedSurveyState = saveableRegistry?.performSave()
            saveableRegistry = null
            composeView?.disposeComposition()
            dialog?.let { if (it.isShowing) it.dismiss() }
        }

        dialog = null
        composeView = null
        hostActivity = null
    }

    private fun dismissInternal(notifyClosed: Boolean) {
        val activeDialog = dialog
        val activeView = composeView
        val survey = currentSurvey
        val onClosed = onClosedCallback
        val wasShown = shownReported

        dialog = null
        composeView = null
        hostActivity = null
        currentSurvey = null
        onShownCallback = null
        onResponseCallback = null
        onClosedCallback = null
        shownReported = false
        awaitingForeground = false
        saveableRegistry = null
        savedSurveyState = null

        guard("tearing down the survey") {
            activeView?.disposeComposition()
            activeDialog?.let { d ->
                if (d.isShowing) {
                    d.dismiss()
                }
            }
        }

        if (notifyClosed && wasShown && survey != null && onClosed != null) {
            onClosed(survey)
        }
    }

    private inline fun guard(action: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            PostHog.getConfig<com.posthog.PostHogConfig>()?.logger?.log(
                "Surveys: $action failed, skipping the survey. $e",
            )
            false
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        private const val TAG = "BoxcastSurveyHost"
    }
}
