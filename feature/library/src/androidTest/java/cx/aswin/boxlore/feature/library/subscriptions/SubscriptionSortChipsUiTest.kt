package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.feature.library.SubscriptionSort
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hermetic Compose UI smoke for the library subscription sort filter chips.
 * Hosted in `:feature:library` with fake state so it needs no ViewModel or DI.
 *
 * Only asserts chips that fit a narrow emulator LazyRow viewport; the trailing
 * "Most Listened" chip is covered by JVM [SubscriptionSort] enum tests.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionSortChipsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chips_areDisplayed_andSelectingOneInvokesCallback() {
        var lastSort: SubscriptionSort? = null

        composeRule.setContent {
            SubscriptionSortChips(
                currentSort = SubscriptionSort.SmartRank,
                onSortChange = { lastSort = it },
            )
        }

        composeRule.onNodeWithText("Smart Rank").assertIsDisplayed()
        composeRule.onNodeWithText("Recently Updated").assertIsDisplayed()
        composeRule.onNodeWithText("A-Z").assertIsDisplayed()

        composeRule.onNodeWithText("A-Z").performClick()
        assertEquals(SubscriptionSort.Alphabetical, lastSort)
    }
}
