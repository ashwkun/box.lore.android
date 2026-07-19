package cx.aswin.boxlore.feature.briefing

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose UI smoke for a briefing related-episode chip.
 * Hosted in `:feature:briefing` with a fake [Episode] so it needs no ViewModel or DI.
 */
@RunWith(AndroidJUnit4::class)
class CompactEpisodeChipUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val episode =
        Episode(
            id = "ep-9",
            title = "Briefing Related Episode",
            description = "desc",
            audioUrl = "https://example.com/b.mp3",
            podcastTitle = "Briefing Source Show",
            duration = 600,
        )

    @Test
    fun inactiveChip_displaysMetadata_andInvokesClick() {
        var clicks = 0

        composeRule.setContent {
            CompactEpisodeChip(
                episode = episode,
                isActiveCard = false,
                accentColor = Color(0xFF6750A4),
                onClick = { clicks++ },
                modifier = Modifier.testTag("briefing_episode_chip"),
            )
        }

        composeRule.onNodeWithText("Briefing Related Episode").assertIsDisplayed()
        composeRule.onNodeWithText("Briefing Source Show").assertIsDisplayed()
        composeRule.onNodeWithTag("briefing_episode_chip").performClick()
        assertTrue(clicks == 1)
    }

    @Test
    fun activeChip_stillInvokesClick() {
        var clicks = 0

        composeRule.setContent {
            CompactEpisodeChip(
                episode = episode,
                isActiveCard = true,
                accentColor = Color(0xFF6750A4),
                onClick = { clicks++ },
                modifier = Modifier.testTag("briefing_episode_chip_active"),
            )
        }

        composeRule.onNodeWithTag("briefing_episode_chip_active").performClick()
        assertTrue(clicks == 1)
    }
}
