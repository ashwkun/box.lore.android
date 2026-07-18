package cx.aswin.boxlore.feature.home.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.feature.home.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxlore.feature.home.settings.dialogs.SettingsRssTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshot / visual-regression anchor for Add RSS dialog (B7).
 *
 * Runs as a composition smoke test (not ignored). Full Roborazzi JVM goldens are staged —
 * reserve PNGs under `screenshots/baselines/` and see `docs/screenshots/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class AddRssFeedDialogScreenshotStubTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addRssDialog_composesWithTaggedControls() {
        composeRule.setContent {
            AddRssFeedDialog(
                url = "https://example.com/feed.xml",
                error = null,
                isAdding = false,
                onUrlChange = {},
                onConfirm = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithTag(SettingsRssTestTags.URL_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsRssTestTags.CONFIRM).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsRssTestTags.CANCEL).assertIsDisplayed()
        composeRule.onRoot().assertIsDisplayed()
    }
}
