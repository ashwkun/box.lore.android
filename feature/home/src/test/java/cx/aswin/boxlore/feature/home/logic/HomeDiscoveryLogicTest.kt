package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.data.content.ContentCandidate
import cx.aswin.boxlore.core.data.content.ContentIntent
import cx.aswin.boxlore.core.data.content.ContentLayout
import cx.aswin.boxlore.core.data.content.ContentSection
import cx.aswin.boxlore.core.data.ranking.CandidateSource
import cx.aswin.boxlore.core.data.ranking.RankingObjective
import cx.aswin.boxlore.core.data.ranking.RankingSurface
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeDiscoveryLogicTest {
    @Test
    fun `adaptive history maturity bucket maps count thresholds`() {
        assertEquals(0, adaptiveHistoryMaturityBucket(0))
        assertEquals(1, adaptiveHistoryMaturityBucket(4))
        assertEquals(2, adaptiveHistoryMaturityBucket(14))
        assertEquals(3, adaptiveHistoryMaturityBucket(29))
        assertEquals(4, adaptiveHistoryMaturityBucket(30))
    }

    @Test
    fun `discover podcasts excluding returns null when trending is empty`() {
        assertNull(
            discoverPodcastsExcluding(
                trending = emptyList(),
                heroItems = emptyList(),
                adaptiveSections = emptyList(),
            ),
        )
    }

    @Test
    fun `discover podcasts excluding filters hero and adaptive section podcasts`() {
        val trending =
            listOf(
                TestFixtures.podcast(id = "hero", title = "Hero"),
                TestFixtures.podcast(id = "adaptive", title = "Adaptive"),
                TestFixtures.podcast(id = "free", title = "Free"),
            )
        val heroItems =
            listOf(
                SmartHeroItem(
                    type = HeroType.SPOTLIGHT,
                    podcast = trending[0],
                    label = "Hero",
                ),
            )
        val adaptiveSections =
            listOf(
                contentSection(
                    podcastId = "adaptive",
                    sectionId = "section-1",
                ),
            )

        val result =
            discoverPodcastsExcluding(
                trending = trending,
                heroItems = heroItems,
                adaptiveSections = adaptiveSections,
            )

        assertEquals(listOf("free"), result?.map { it.id })
    }

    @Test
    fun `discover podcasts excluding returns empty list when all trending shows are filtered`() {
        val trending =
            listOf(
                TestFixtures.podcast(id = "hero", title = "Hero"),
                TestFixtures.podcast(id = "adaptive", title = "Adaptive"),
            )
        val heroItems =
            listOf(
                SmartHeroItem(
                    type = HeroType.SPOTLIGHT,
                    podcast = trending[0],
                    label = "Hero",
                ),
            )
        val adaptiveSections =
            listOf(
                contentSection(
                    podcastId = "adaptive",
                    sectionId = "section-1",
                ),
            )

        val result =
            discoverPodcastsExcluding(
                trending = trending,
                heroItems = heroItems,
                adaptiveSections = adaptiveSections,
            )

        assertEquals(emptyList<String>(), result?.map { it.id })
    }

    private fun contentSection(
        podcastId: String,
        sectionId: String,
    ): ContentSection {
        val podcast = TestFixtures.podcast(id = podcastId)
        return ContentSection(
            stableId = sectionId,
            intent =
                ContentIntent(
                    id = sectionId,
                    objective = RankingObjective.DISCOVERY,
                    eligibleSurfaces = setOf(RankingSurface.HOME),
                    title = "Section",
                    layout = ContentLayout.PODCAST_RAIL,
                ),
            items =
                listOf(
                    ContentCandidate(
                        id = "candidate-$podcastId",
                        episode = null,
                        podcast = podcast,
                        source = CandidateSource.TRENDING,
                        intentId = sectionId,
                        retrievalScore = 1.0,
                    ),
                ),
            utility = 1.0,
        )
    }
}
