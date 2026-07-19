package cx.aswin.boxlore.feature.explore.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose UI smoke for the explore/learn clickable cards.
 * Hosted in `:feature:explore` with fake data so it needs no ViewModel or DI.
 */
@RunWith(AndroidJUnit4::class)
class ExploreCardsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun vibeCard_displaysLabel_andInvokesClick() {
        var clicks = 0

        composeRule.setContent {
            ExploreVibeCard(
                vibe = "science" to "Science & Wonder",
                onClick = { clicks++ },
                modifier = Modifier.testTag("explore_vibe_card"),
            )
        }

        composeRule.onNodeWithText("Science & Wonder").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_vibe_card").performClick()
        assertTrue(clicks == 1)
    }

    @Test
    fun vibeChip_displaysLabel_andInvokesClick() {
        var clicks = 0

        composeRule.setContent {
            ExploreVibeChip(
                vibe = "history" to "History",
                onClick = { clicks++ },
                modifier = Modifier.testTag("explore_vibe_chip"),
            )
        }

        composeRule.onNodeWithText("History").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_vibe_chip").performClick()
        assertTrue(clicks == 1)
    }
}
