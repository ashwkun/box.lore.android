package cx.aswin.boxlore.feature.home.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import cx.aswin.boxlore.feature.home.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxlore.feature.home.settings.dialogs.SettingsRssTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * PR5 Roborazzi spike — JVM screenshot capture for Add RSS dialog (no golden committed).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class AddRssFeedDialogRoborazziSpikeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addRssDialog_rendersTaggedControls() {
        composeRule.setContent {
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

        composeRule.onNodeWithTag(SettingsRssTestTags.URL_FIELD).assertExists()
        composeRule.onRoot().captureRoboImage()
    }
}
