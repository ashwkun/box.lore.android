package cx.aswin.boxlore.feature.home.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import cx.aswin.boxlore.feature.home.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxlore.feature.home.settings.dialogs.ResetAnalyticsDialog
import cx.aswin.boxlore.feature.home.settings.dialogs.SettingsResetAnalyticsTestTags
import cx.aswin.boxlore.feature.home.settings.dialogs.SettingsRssTestTags
import cx.aswin.boxlore.feature.home.settings.pages.DownloadsSettingsPage
import cx.aswin.boxlore.feature.home.settings.pages.SettingsDownloadsTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi golden coverage for Settings composables. Goldens are committed under
 * `screenshots/baselines/` (see feature/home roborazzi config); a fixed light color
 * scheme keeps captures deterministic across machines. Record with
 * `./gradlew :feature:home:recordRoborazziDebug`, verify with `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SettingsGoldenRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addRssFeedDialog_golden() {
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                var url by remember { mutableStateOf("https://example.com/feed.xml") }
                AddRssFeedDialog(
                    url = url,
                    error = null,
                    isAdding = false,
                    onUrlChange = { url = it },
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsRssTestTags.URL_FIELD).assertExists()
        composeRule.onRoot().captureRoboImage("add_rss_feed_dialog.png")
    }

    @Test
    fun resetAnalyticsDialog_golden() {
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                ResetAnalyticsDialog(
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsResetAnalyticsTestTags.CONFIRM).assertExists()
        composeRule.onRoot().captureRoboImage("reset_analytics_dialog.png")
    }

    @Test
    fun downloadsSettingsPage_golden() {
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                DownloadsSettingsPage(
                    onSmartDownloadsClick = {},
                    onAutoDownloadsClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsDownloadsTestTags.SMART_DOWNLOADS_ROW).assertExists()
        composeRule.onRoot().captureRoboImage("downloads_settings_page.png")
    }
}
