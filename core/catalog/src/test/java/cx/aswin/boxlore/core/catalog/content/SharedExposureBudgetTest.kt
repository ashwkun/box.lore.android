package cx.aswin.boxlore.core.catalog.content

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SharedExposureBudgetTest {
    @Test
    fun `per-show cap blocks a third episode from the same show`() {
        val budget = SharedExposureBudget(maximumItemsPerShow = 2)
        val first = candidate("ep-1", "show-a")
        val second = candidate("ep-2", "show-a")
        val third = candidate("ep-3", "show-a")
        val otherShow = candidate("ep-4", "show-b")

        assertTrue(budget.allows(first))
        budget.record(listOf(first, second))

        assertFalse(budget.allows(third))
        assertTrue(budget.allows(otherShow))
    }

    @Test
    fun `fifo eviction frees a show slot after memory overflows`() {
        val budget = SharedExposureBudget(maximumRememberedItems = 2, maximumItemsPerShow = 1)
        budget.record(
            listOf(
                candidate("ep-1", "show-a"),
                candidate("ep-2", "show-b"),
            ),
        )

        assertFalse(budget.allows(candidate("ep-3", "show-a")))
        budget.record(listOf(candidate("ep-3", "show-c")))

        assertTrue(budget.allows(candidate("ep-4", "show-a")))
    }

    @Test
    fun `compose without reset drops previously recorded ids`() {
        val budget = SharedExposureBudget()
        val intent = intent("discover")
        val candidates =
            listOf(
                candidate("ep-1", "show-a", score = 1.0),
                candidate("ep-2", "show-b", score = 0.9),
            )

        val first =
            SlateComposer().compose(
                context = context(),
                catalogVersion = "v1",
                rankedByIntent = listOf(intent to candidates),
                exposureBudget = budget,
                now = 1L,
            )
        val second =
            SlateComposer().compose(
                context = context(),
                catalogVersion = "v1",
                rankedByIntent = listOf(intent to candidates),
                exposureBudget = budget,
                now = 2L,
            )

        assertEquals(listOf("ep-1", "ep-2"), first.sections.single().items.map(ContentCandidate::id))
        assertTrue(second.sections.isEmpty())
    }

    @Test
    fun `reset allows previously recorded ids again`() {
        val budget = SharedExposureBudget()
        val intent = intent("discover")
        val candidates = listOf(candidate("ep-1", "show-a", score = 1.0))

        SlateComposer().compose(
            context = context(),
            catalogVersion = "v1",
            rankedByIntent = listOf(intent to candidates),
            exposureBudget = budget,
            now = 1L,
        )
        budget.reset()
        val afterReset =
            SlateComposer().compose(
                context = context(),
                catalogVersion = "v1",
                rankedByIntent = listOf(intent to candidates),
                exposureBudget = budget,
                now = 2L,
            )

        assertEquals(listOf("ep-1"), afterReset.sections.single().items.map(ContentCandidate::id))
    }

    @Test
    fun `section drops when budget leaves too few candidates for minimumItems`() {
        val budget = SharedExposureBudget(maximumItemsPerShow = 2)
        budget.record(
            listOf(
                candidate("prior-1", "show-x"),
                candidate("prior-2", "show-x"),
            ),
        )
        val intent =
            intent("starved").copy(
                minimumItems = 2,
                maximumItems = 2,
            )
        val candidates =
            listOf(
                candidate("b1", "show-x", score = 0.8),
                candidate("b2", "show-y", score = 0.7),
            )

        val slate =
            SlateComposer().compose(
                context = context(),
                catalogVersion = "v1",
                rankedByIntent = listOf(intent to candidates),
                exposureBudget = budget,
                now = 1L,
            )

        assertTrue(slate.sections.isEmpty())
    }

    private fun intent(id: String): ContentIntent =
        ContentIntent(
            id = id,
            objective = RankingObjective.DISCOVERY,
            eligibleSurfaces = setOf(RankingSurface.HOME),
            title = id,
            layout = ContentLayout.PODCAST_RAIL,
        )

    private fun context(): ContentContext =
        ContentContext(
            surface = RankingSurface.HOME,
            localMinuteOfDay = 600,
            weekday = 3,
            daypart = ContentDaypart.MORNING,
            region = "us",
            isDriving = false,
            isOnline = true,
            availableMinutes = null,
            currentEpisodeId = null,
            currentPodcastId = null,
            historyMaturity = 10,
            subscriptionCount = 2,
            sessionId = "session",
        )

    private fun candidate(
        id: String,
        showId: String,
        score: Double = 1.0,
    ): ContentCandidate =
        ContentCandidate(
            id = id,
            episode = null,
            podcast = Podcast(showId, showId, "", ""),
            source = CandidateSource.SERVER_RECOMMENDATION,
            intentId = "discover",
            retrievalScore = score,
            rankingScore = score,
        )
}
