package cx.aswin.boxlore.feature.home.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.feature.home.settings.pages.DownloadsSettingsPage
import cx.aswin.boxlore.feature.home.settings.pages.SettingsDownloadsTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose smoke for Settings → Downloads navigation rows (no network / DI).
 */
@RunWith(AndroidJUnit4::class)
class DownloadsSettingsPageUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smartAndAutoRows_areDisplayed_andSmartRowInvokesCallback() {
        var smartClicked = false
        var autoClicked = false

        composeRule.setContent {
            DownloadsSettingsPage(
                onSmartDownloadsClick = { smartClicked = true },
                onAutoDownloadsClick = { autoClicked = true },
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsDownloadsTestTags.SMART_DOWNLOADS_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsDownloadsTestTags.AUTO_DOWNLOADS_ROW).assertIsDisplayed()

        composeRule.onNodeWithTag(SettingsDownloadsTestTags.SMART_DOWNLOADS_ROW).performClick()
        assertTrue(smartClicked)

        composeRule.onNodeWithTag(SettingsDownloadsTestTags.AUTO_DOWNLOADS_ROW).performClick()
        assertTrue(autoClicked)
    }
}
