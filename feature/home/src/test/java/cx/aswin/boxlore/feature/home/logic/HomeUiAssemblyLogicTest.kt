package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeUiAssemblyLogicTest {
    private val briefing =
        Briefing(
            date = "2026-01-01",
            region = "us",
            title = "Daily Brief",
            script = "script",
            audioUrl = "https://audio/brief.mp3",
            coverUrl = "https://img/brief.jpg",
        )

    private suspend fun assemble(
        trending: List<cx.aswin.boxlore.core.model.Podcast> = listOf(TestFixtures.podcast(id = "trend-1")),
        subs: List<cx.aswin.boxlore.core.model.Podcast> = emptyList(),
        previousMixtape: HomeMixtapeCache? = null,
        isTrendingLoaded: Boolean = true,
        hasDismissedImportBanner: Boolean = false,
        rawBriefing: Briefing? = null,
        completedEpisodeIds: Set<String> = emptySet(),
        briefingDismissedDate: String = "",
        briefingDismissedForever: Boolean = false,
        mixtapeResult: MixtapeEngine.Result =
            MixtapeEngine.Result(
                podcasts = listOf(TestFixtures.podcast(id = "mix-1")),
                episodes = listOf(TestFixtures.episode(id = "mix-ep-1")),
                unplayedCount = 3,
            ),
        onBuildMixtape: () -> Unit = {},
    ): HomeUiAssemblyResult =
        HomeUiAssemblyLogic.assemble(
            trendingList = trending,
            rankedRecommendations = listOf(TestFixtures.episode(id = "rec-1")),
            resumeList = emptyList(),
            subs = subs,
            allHistory = emptyList(),
            resolvedSerial = emptyMap(),
            completedEpisodeIds = completedEpisodeIds,
            region = "us",
            editorialRows = emptyList(),
            previousStableOrder = null,
            podcastScores = emptyMap(),
            previousMixtape = previousMixtape,
            buildMixtape = { _, _ ->
                onBuildMixtape()
                mixtapeResult
            },
            isTrendingLoaded = isTrendingLoaded,
            hasDismissedImportBanner = hasDismissedImportBanner,
            rawBriefing = rawBriefing,
            rawBriefingChapters = emptyList(),
            briefingDismissedDate = briefingDismissedDate,
            briefingDismissedForever = briefingDismissedForever,
        )

    @Test
    fun `builds mixtape when no cache and passes rankings through`() =
        runTest {
            var built = 0
            val result = assemble(onBuildMixtape = { built++ })

            assertEquals(1, built)
            assertEquals(listOf("mix-1"), result.latestEpisodes.map { it.id })
            assertEquals(3, result.unplayedEpisodeCount)
            assertEquals(listOf("rec-1"), result.recommendations.map { it.id })
            assertEquals(emptySet<String>(), result.mixtapeCache?.subSignature)
        }

    @Test
    fun `reuses mixtape cache when subscription signature unchanged`() =
        runTest {
            val subs = listOf(TestFixtures.podcast(id = "sub-1"))
            val cache =
                HomeMixtapeCache(
                    podcasts = listOf(TestFixtures.podcast(id = "cached")),
                    unplayedCount = 7,
                    episodes = listOf(TestFixtures.episode(id = "cached-ep")),
                    subSignature = setOf("sub-1"),
                )
            var built = 0

            val result = assemble(subs = subs, previousMixtape = cache, onBuildMixtape = { built++ })

            assertEquals(0, built)
            assertEquals(listOf("cached"), result.latestEpisodes.map { it.id })
            assertEquals(7, result.unplayedEpisodeCount)
        }

    @Test
    fun `rebuilds mixtape when subscription signature changed`() =
        runTest {
            val cache =
                HomeMixtapeCache(
                    podcasts = listOf(TestFixtures.podcast(id = "cached")),
                    unplayedCount = 7,
                    episodes = emptyList(),
                    subSignature = setOf("old-sub"),
                )
            var built = 0

            val result =
                assemble(
                    subs = listOf(TestFixtures.podcast(id = "new-sub")),
                    previousMixtape = cache,
                    onBuildMixtape = { built++ },
                )

            assertEquals(1, built)
            assertEquals(listOf("mix-1"), result.latestEpisodes.map { it.id })
            assertEquals(setOf("new-sub"), result.mixtapeCache?.subSignature)
        }

    @Test
    fun `shows import banner only when subs empty and not dismissed`() =
        runTest {
            assertTrue(assemble(subs = emptyList(), hasDismissedImportBanner = false).showImportBanner)
            assertFalse(assemble(subs = emptyList(), hasDismissedImportBanner = true).showImportBanner)
            assertFalse(
                assemble(subs = listOf(TestFixtures.podcast(id = "s")), hasDismissedImportBanner = false)
                    .showImportBanner,
            )
        }

    @Test
    fun `loading flags follow trending state`() =
        runTest {
            val loading = assemble(trending = emptyList(), isTrendingLoaded = false)
            assertTrue(loading.isLoading)
            assertTrue(loading.isFilterLoading)

            val loaded = assemble(trending = listOf(TestFixtures.podcast(id = "t")), isTrendingLoaded = true)
            assertFalse(loaded.isLoading)
            assertFalse(loaded.isFilterLoading)
            assertTrue(loaded.shouldUpdateForYouCache)
        }

    @Test
    fun `briefing surfaces when present and not dismissed or completed`() =
        runTest {
            val shown = assemble(rawBriefing = briefing)
            assertEquals(briefing, shown.briefing)

            val dismissed = assemble(rawBriefing = briefing, briefingDismissedDate = briefing.date)
            assertNull(dismissed.briefing)

            val forever = assemble(rawBriefing = briefing, briefingDismissedForever = true)
            assertNull(forever.briefing)

            val completed =
                assemble(
                    rawBriefing = briefing,
                    completedEpisodeIds = setOf("briefing_${briefing.region}_${briefing.date}"),
                )
            assertNull(completed.briefing)

            assertNull(assemble(rawBriefing = null).briefing)
        }
}
