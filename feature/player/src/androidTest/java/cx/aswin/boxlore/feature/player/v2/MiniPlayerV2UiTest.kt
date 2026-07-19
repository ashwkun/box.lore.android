package cx.aswin.boxlore.feature.player.v2

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose UI smoke for the v2 mini player transport controls.
 * Hosted in `:feature:player` with fake [MiniPlayerContent] so it needs no media session or DI.
 */
@RunWith(AndroidJUnit4::class)
class MiniPlayerV2UiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val episode =
        Episode(
            id = "ep-1",
            title = "Mini Player Episode",
            description = "desc",
            audioUrl = "https://example.com/a.mp3",
        )

    private fun content(isPlaying: Boolean) =
        MiniPlayerContent(
            episode = episode,
            podcastTitle = "Fake Podcast",
            podcastImageUrl = null,
            isPlaying = isPlaying,
            isLoading = false,
            position = 30_000L,
            duration = 120_000L,
        )

    private val colors =
        MiniPlayerColors(
            colorScheme = lightColorScheme(),
            backgroundColor = Color(0xFFEDE7F6),
        )

    @Test
    fun pausedState_showsPlay_metadata_andInvokesPlayPause() {
        var playPauseClicks = 0

        composeRule.setContent {
            MiniPlayerV2(
                content = content(isPlaying = false),
                colors = colors,
                actions =
                    MiniPlayerActions(
                        onPlayPause = { playPauseClicks++ },
                        onReplay = {},
                        onForward = {},
                        onDismiss = {},
                    ),
            )
        }

        composeRule.onNodeWithText("Mini Player Episode").assertIsDisplayed()
        composeRule.onNodeWithText("Fake Podcast").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
        assertTrue(playPauseClicks == 1)
    }

    @Test
    fun playingState_showsPauseControl() {
        composeRule.setContent {
            MiniPlayerV2(
                content = content(isPlaying = true),
                colors = colors,
                actions =
                    MiniPlayerActions(
                        onPlayPause = {},
                        onReplay = {},
                        onForward = {},
                        onDismiss = {},
                    ),
            )
        }

        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }
}
