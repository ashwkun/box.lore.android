package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose UI smoke for the home top control bar (settings + feedback CTAs).
 * Hosted in `:feature:home` with plain callbacks so it needs no ViewModel or DI.
 */
@RunWith(AndroidJUnit4::class)
class TopControlBarUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsAndFeedback_areDisplayed_andInvokeCallbacks() {
        var settingsClicks = 0
        var feedbackClicks = 0

        composeRule.setContent {
            TopControlBar(
                onFeedbackClick = { feedbackClicks++ },
                onAvatarClick = { settingsClicks++ },
            )
        }

        composeRule.onNodeWithContentDescription("Send Feedback").assertIsDisplayed()
        composeRule.onNodeWithTag("home_settings_button").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Send Feedback").performClick()
        composeRule.onNodeWithTag("home_settings_button").performClick()

        assertTrue(feedbackClicks == 1)
        assertTrue(settingsClicks == 1)
    }
}
