package cx.aswin.boxlore.feature.home.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.feature.home.settings.dialogs.ResetAnalyticsDialog
import cx.aswin.boxlore.feature.home.settings.dialogs.SettingsResetAnalyticsTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose smoke for Settings → Privacy → Reset analytics ID dialog.
 */
@RunWith(AndroidJUnit4::class)
class ResetAnalyticsDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmAndCancel_invokeCallbacks() {
        var confirmed = false
        var dismissed = false

        composeRule.setContent {
            ResetAnalyticsDialog(
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithText("Start a new analytics ID?").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsResetAnalyticsTestTags.CANCEL).performClick()
        assertTrue(dismissed)

        composeRule.onNodeWithTag(SettingsResetAnalyticsTestTags.CONFIRM).performClick()
        assertTrue(confirmed)
    }
}
