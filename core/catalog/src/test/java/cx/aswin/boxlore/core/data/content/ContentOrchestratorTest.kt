package cx.aswin.boxlore.core.data.content

import com.google.gson.Gson
import cx.aswin.boxlore.core.data.ranking.CandidateSource
import cx.aswin.boxlore.core.data.ranking.RankingObjective
import cx.aswin.boxlore.core.data.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.RecommendationSeedV2
import cx.aswin.boxlore.core.network.model.RecommendationsV2Request
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class ContentOrchestratorTest {
    @Test
    fun `invalid catalog resolves to generic anytime fallback`() {
        val context = context()
        val expired = ContentCatalogSnapshot(
            schemaVersion = 1,
            catalogVersion = "expired",
            validUntil = 0,
            intents = emptyList(),
        )

        val (version, intents) = ContentIntentResolver().resolve(expired, context, now = 1)

        assertEquals("embedded-anytime-v1", version)
        assertEquals(listOf("anytime"), intents.map(ContentIntent::id))
    }

    @Test
    fun `provider failures are isolated and session slate is stable`() = runTest {
        var successfulCalls = 0
        val failing = provider(CandidateSource.TRENDING) { error("offline") }
        val successful = provider(CandidateSource.SERVER_RECOMMENDATION) {
            successfulCalls++
            listOf(candidate("episode-a", "show-a", score = 0.8))
        }
        val orchestrator = ContentOrchestrator(
            providers = listOf(failing, successful),
            ranker = ContentCandidateRanker { candidates, _, _ ->
                candidates.sortedByDescending(ContentCandidate::rankingScore)
            },
        )

        val first = orchestrator.compose(context(), catalog())
        val second = orchestrator.compose(context(), catalog())

        assertEquals(listOf("episode-a"), first.sections.single().items.map(ContentCandidate::id))
        assertSame(first, second)
        assertEquals(1, successfulCalls)
    }

    @Test
    fun `slate keeps protected sections and deduplicates across optional sections`() {
        val protectedIntent = intent("protected", protected = true)
        val optionalIntent = intent("optional")
        val duplicate = candidate("duplicate", "show-a", score = 0.9)
        val unique = candidate("unique", "show-b", score = 0.8)

        val slate = SlateComposer().compose(
            context = context(),
            catalogVersion = "test",
            rankedByIntent = listOf(
                protectedIntent to listOf(duplicate),
                optionalIntent to listOf(duplicate, unique),
            ),
            exposureBudget = SharedExposureBudget(),
            now = 1,
        )

        assertTrue(slate.sections.first().intent.protected)
        assertEquals(
            listOf("duplicate", "unique"),
            slate.sections.flatMap(ContentSection::items).map(ContentCandidate::id),
        )
    }

    @Test
    fun `content section rejects an empty item list`() {
        try {
            ContentSection(
                stableId = "empty",
                intent = intent("empty"),
                items = emptyList(),
                utility = 0.0,
            )
            fail("Expected empty section to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `ranker failure falls back to retrieval order`() = runTest {
        val orchestrator = ContentOrchestrator(
            providers = listOf(
                provider(CandidateSource.SERVER_RECOMMENDATION) {
                    listOf(
                        candidate("low", "show-a", score = 0.2),
                        candidate("high", "show-b", score = 0.9),
                    )
                },
            ),
            ranker = ContentCandidateRanker { _, _, _ -> error("ranking unavailable") },
        )

        val slate = orchestrator.compose(context(), catalog())

        assertEquals(listOf("high", "low"), slate.sections.single().items.map(ContentCandidate::id))
    }

    @Test
    fun `provider cancellation is never converted to an empty result`() = runTest {
        val orchestrator = ContentOrchestrator(
            providers = listOf(
                provider(CandidateSource.TRENDING) {
                    throw CancellationException("cancelled")
                },
            ),
            ranker = ContentCandidateRanker { candidates, _, _ -> candidates },
        )

        try {
            orchestrator.compose(context(), catalog())
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected: structured concurrency must remain cancellable.
        }
    }

    @Test
    fun `daily refresh policy invalidates slate on the next day`() = runTest {
        var calls = 0
        val dailyIntent = intent("daily", refreshPolicy = ContentRefreshPolicy.DAILY)
        val dailyCatalog = ContentCatalogSnapshot(
            schemaVersion = 1,
            catalogVersion = "daily-test",
            validUntil = Long.MAX_VALUE,
            intents = listOf(dailyIntent),
        )
        val orchestrator = ContentOrchestrator(
            providers = listOf(
                provider(CandidateSource.TRENDING) {
                    calls++
                    listOf(candidate("episode-$calls", "show-$calls", score = 1.0))
                },
            ),
            ranker = ContentCandidateRanker { candidates, _, _ -> candidates },
        )

        val first = orchestrator.compose(context(), dailyCatalog, now = 1)
        val cached = orchestrator.compose(context(), dailyCatalog, now = 2)
        val nextDay = orchestrator.compose(
            context(),
            dailyCatalog,
            now = 24L * 60L * 60L * 1_000L,
        )

        assertSame(first, cached)
        assertNotSame(first, nextDay)
        assertEquals(2, calls)
    }

    @Test
    fun `recommendation v2 request excludes raw behavioral history`() {
        val request = RecommendationsV2Request(
            country = "us",
            seeds = listOf(RecommendationSeedV2(kind = "episode", id = 42, weight = 0.8)),
            subscribedPodcastIds = listOf(7),
            excludedEpisodeIds = listOf(9),
        )

        val json = Gson().toJson(request)

        assertTrue("\"contractVersion\":2" in json)
        assertTrue("\"seeds\"" in json)
        assertTrue("\"history\"" !in json)
        assertTrue("\"progressMs\"" !in json)
        assertTrue("\"isLiked\"" !in json)
    }

    @Test
    fun `offline context is accepted by ContentContextEngine`() {
        val offline = ContentContextEngine().create(
            ContentContextInput(
                surface = RankingSurface.HOME,
                region = "us",
                isDriving = false,
                isOnline = false,
                availableMinutes = null,
                currentEpisodeId = null,
                currentPodcastId = null,
                historyMaturity = 0,
                subscriptionCount = 0,
                sessionId = "s",
            ),
        )
        assertEquals(false, offline.isOnline)
    }

    private fun catalog(): ContentCatalogSnapshot = ContentCatalogSnapshot(
        schemaVersion = 1,
        catalogVersion = "test",
        validUntil = Long.MAX_VALUE,
        intents = listOf(intent("discover")),
    )

    private fun intent(
        id: String,
        protected: Boolean = false,
        refreshPolicy: ContentRefreshPolicy = ContentRefreshPolicy.SESSION,
    ): ContentIntent = ContentIntent(
        id = id,
        objective = RankingObjective.DISCOVERY,
        eligibleSurfaces = setOf(RankingSurface.HOME),
        title = id,
        layout = ContentLayout.PODCAST_RAIL,
        refreshPolicy = refreshPolicy,
        protected = protected,
    )

    private fun context(): ContentContext = ContentContext(
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

    private fun candidate(id: String, showId: String, score: Double): ContentCandidate {
        return ContentCandidate(
            id = id,
            episode = null,
            podcast = Podcast(showId, showId, "", ""),
            source = CandidateSource.SERVER_RECOMMENDATION,
            intentId = "discover",
            retrievalScore = score,
            rankingScore = score,
        )
    }

    private fun provider(
        source: CandidateSource,
        block: suspend () -> List<ContentCandidate>,
    ): CandidateProvider = object : CandidateProvider {
        override val source: CandidateSource = source

        override suspend fun candidates(
            intent: ContentIntent,
            context: ContentContext,
        ): List<ContentCandidate> = block()
    }
}
