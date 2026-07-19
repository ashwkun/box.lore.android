package cx.aswin.boxlore.feature.info.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose UI smoke for the episode action rail (play / like / download / queue).
 * Hosted in `:feature:info` with fake state so it needs no ViewModel or app-level DI.
 */
@RunWith(AndroidJUnit4::class)
class EpisodeActionRailUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun state(
        isPlaying: Boolean = false,
        isLiked: Boolean = false,
        isDownloaded: Boolean = false,
        isQueued: Boolean = false,
        isCompleted: Boolean = false,
    ) = EpisodeActionRailState(
        title = "Deep Dive Episode",
        imageUrl = null,
        isPlaying = isPlaying,
        isPlaybackLoading = false,
        isResume = false,
        isLiked = isLiked,
        isDownloaded = isDownloaded,
        isDownloading = false,
        isQueued = isQueued,
        isCompleted = isCompleted,
        progress = 0f,
        remainingTimeText = null,
    )

    @Test
    fun idleState_showsPlay_andInvokesCallbacksForEachControl() {
        var mainClicks = 0
        var likeClicks = 0
        var downloadClicks = 0
        var queueClicks = 0
        var markPlayedClicks = 0

        composeRule.setContent {
            EpisodeActionRail(
                state = state(),
                callbacks =
                    EpisodeActionRailCallbacks(
                        onMainActionClick = { mainClicks++ },
                        onLikeClick = { likeClicks++ },
                        onDownloadClick = { downloadClicks++ },
                        onQueueClick = { queueClicks++ },
                        onMarkPlayedClick = { markPlayedClicks++ },
                    ),
                accentColor = Color(0xFF6750A4),
                showMarkPlayedTip = false,
                onMarkPlayedTipDismissed = {},
            )
        }

        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Mark played").performClick()
        composeRule.onNodeWithContentDescription("Like").performClick()
        composeRule.onNodeWithContentDescription("Download").performClick()
        composeRule.onNodeWithContentDescription("Add to queue").performClick()

        assertTrue(mainClicks == 1)
        assertTrue(likeClicks == 1)
        assertTrue(downloadClicks == 1)
        assertTrue(queueClicks == 1)
        assertTrue(markPlayedClicks == 1)
    }

    @Test
    fun activeState_reflectsPlayingAndToggledControls() {
        composeRule.setContent {
            EpisodeActionRail(
                state =
                    state(
                        isPlaying = true,
                        isLiked = true,
                        isDownloaded = true,
                        isQueued = true,
                        isCompleted = true,
                    ),
                callbacks =
                    EpisodeActionRailCallbacks(
                        onMainActionClick = {},
                        onLikeClick = {},
                        onDownloadClick = {},
                        onQueueClick = {},
                        onMarkPlayedClick = {},
                    ),
                accentColor = Color(0xFF6750A4),
                showMarkPlayedTip = false,
                onMarkPlayedTipDismissed = {},
            )
        }

        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Unlike").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove download").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove from queue").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mark unplayed").assertIsDisplayed()
    }
}
