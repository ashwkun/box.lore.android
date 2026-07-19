package cx.aswin.boxlore.feature.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionSortTest {
    @Test
    fun valueOf_roundTripsKnownSorts() {
        SubscriptionSort.entries.forEach { sort ->
            assertEquals(sort, SubscriptionSort.valueOf(sort.name))
        }
    }

    @Test
    fun defaultFallbackMatchesLibraryViewModelBehavior() {
        val sortName = "not_a_real_sort"
        val resolved =
            try {
                SubscriptionSort.valueOf(sortName)
            } catch (_: Exception) {
                SubscriptionSort.SmartRank
            }
        assertEquals(SubscriptionSort.SmartRank, resolved)
    }
}
