package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.core.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose UI smoke for an onboarding suggested-podcast row and its subscribe toggle.
 * Hosted in `:feature:onboarding` with a fake [Podcast] so it needs no ViewModel or DI.
 */
@RunWith(AndroidJUnit4::class)
class SuggestedPodcastRowItemUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val podcast =
        Podcast(
            id = "pod-42",
            title = "Fake Onboarding Show",
            artist = "Fake Host",
            imageUrl = "https://example.com/cover.jpg",
        )

    @Test
    fun unsubscribedRow_showsMetadata_andToggleInvokesSubscription() {
        var toggledId: String? = null

        composeRule.setContent {
            SuggestedPodcastRowItem(
                podcast = podcast,
                isSubscribed = false,
                onToggleSubscription = { toggledId = it },
            )
        }

        composeRule.onNodeWithText("Fake Onboarding Show").assertIsDisplayed()
        composeRule.onNodeWithText("Fake Host").assertIsDisplayed()

        composeRule.onNodeWithTag(SuggestedPodcastTestTags.TOGGLE).performClick()
        assertEquals("pod-42", toggledId)
    }

    @Test
    fun subscribedRow_showsSelectedIndicator() {
        composeRule.setContent {
            SuggestedPodcastRowItem(
                podcast = podcast,
                isSubscribed = true,
                onToggleSubscription = {},
            )
        }

        composeRule.onNodeWithContentDescription("Selected").assertIsDisplayed()
    }
}
