package cx.aswin.boxlore.feature.home.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.feature.home.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxlore.feature.home.settings.dialogs.SettingsRssTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Composition smoke for Add RSS dialog controls (not a screenshot golden).
 *
 * AlertDialog creates multiple Compose roots — assert via [testTag]s only.
 * PNG baselines / Roborazzi remain deferred; see `docs/screenshots/README.md`
 * (P26 is not complete).
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
    }
}
