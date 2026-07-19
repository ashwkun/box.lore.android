package cx.aswin.boxlore.core.ranking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ScorablePodcast
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.database.AdaptiveRankingDatabase
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveCandidateScorerTest {

    private lateinit var context: Context
    private lateinit var database: AdaptiveRankingDatabase
    private lateinit var repository: AdaptiveRankingRepository
    private lateinit var controls: RankingRuntimeControls
    private lateinit var scorer: AdaptiveCandidateScorer

    private val nowMs = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("adaptive_ranking_runtime", Context.MODE_PRIVATE)
            .edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AdaptiveRankingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AdaptiveRankingRepository.create(context, database)
        controls = RankingRuntimeControls.create(context)
        scorer = AdaptiveCandidateScorer.create(repository, controls)
    }

    @After
    fun tearDown() {
        database.close()
        LearningEventLog.configure(false)
        RankingShadowDiagnostics.clear()
    }

    private fun episode(
        id: String = "ep-1",
        podcastId: String = "pod-1",
        publishedSeconds: Long = nowMs / 1_000 - 3_600,
        duration: Int = 45 * 60,
    ): Episode = TestFixtures.episode(
        id = id,
        podcastId = podcastId,
        publishedDate = publishedSeconds,
        duration = duration,
    )

    private fun podcast(
        id: String = "pod-1",
        genre: String = "Technology",
        subscribedAt: Long = nowMs - 3_600_000L,
        autoDownload: Boolean = false,
        notifications: Boolean = false,
        preferredSort: String? = null,
        latest: Episode? = null,
    ): Podcast = TestFixtures.podcast(id = id, genre = genre, subscribedAt = subscribedAt).copy(
        autoDownloadEnabled = autoDownload,
        notificationsEnabled = notifications,
        preferredSort = preferredSort,
        latestEpisode = latest,
    )

    private fun scorable(
        id: String = "pod-1",
        subscribedAt: Long = nowMs - 3_600_000L,
        latest: Episode? = episode(podcastId = id),
        notifications: Boolean = false,
        autoDownload: Boolean = false,
    ): ScorablePodcast = ScorablePodcast(
        id = id,
        subscribedAt = subscribedAt,
        latestEpisode = latest,
        notificationsEnabled = notifications,
        autoDownloadEnabled = autoDownload,
    )

    private fun history(
        episodeId: String = "ep-1",
        podcastId: String = "pod-1",
        progressMs: Long = 0L,
        durationMs: Long = 45 * 60 * 1000L,
        isCompleted: Boolean = false,
        isLiked: Boolean = false,
        lastPlayedAt: Long = nowMs - 60_000L,
    ): ListeningHistoryEntity = ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode $episodeId",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Podcast $podcastId",
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = isCompleted,
        isLiked = isLiked,
        lastPlayedAt = lastPlayedAt,
    )

    @Test
    fun scorePodcastsEmptyReturnsEmpty() = runTest {
        val result = scorer.scorePodcasts(
            podcasts = emptyList(),
            history = emptyList(),
            objective = RankingObjective.YOUR_SHOWS,
            surface = RankingSurface.HOME,
            nowMs = nowMs,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun scorePodcastsAdaptiveDisabledReturnsLegacyScores() = runTest {
        // EXPLORE surface defaults to adaptive-disabled, so legacy scoring is returned verbatim.
        val podcasts = listOf(
            scorable(id = "pod-1", autoDownload = true),
            scorable(id = "pod-2"),
        )
        val legacy = scorer.scorePodcasts(
            podcasts = podcasts,
            history = listOf(history(podcastId = "pod-1", isLiked = true)),
            objective = RankingObjective.YOUR_SHOWS,
            surface = RankingSurface.EXPLORE,
            nowMs = nowMs,
        )
        assertEquals(setOf("pod-1", "pod-2"), legacy.keys)
        assertTrue(legacy.values.all { it.isFinite() })
    }

    @Test
    fun scorePodcastsAdaptiveEnabledProducesBoundedScores() = runTest {
        controls.setShadowDiagnosticsEnabled(true)
        val podcasts = listOf(
            scorable(id = "pod-1", autoDownload = true, notifications = true),
            scorable(id = "pod-2", latest = null),
            scorable(id = "pod-3", subscribedAt = 0L),
        )
        val history = listOf(
            history(podcastId = "pod-1", progressMs = 30 * 60 * 1000L, isLiked = true),
        )
        repository.updateFacet(PreferenceFacetType.SHOW, "pod-1", reward = 1.0, now = nowMs)

        val adaptive = scorer.scorePodcasts(
            podcasts = podcasts,
            history = history,
            objective = RankingObjective.YOUR_SHOWS,
            surface = RankingSurface.HOME,
            includeAutoDownloadBoost = true,
            nowMs = nowMs,
        )

        assertEquals(setOf("pod-1", "pod-2", "pod-3"), adaptive.keys)
        assertTrue(adaptive.values.all { it.isFinite() })
        assertTrue(RankingShadowDiagnostics.snapshots().isNotEmpty())
    }

    @Test
    fun scoreEpisodesEmptyReturnsEmpty() = runTest {
        val result = scorer.scoreEpisodes(
            inputs = emptyList(),
            history = emptyList(),
            objective = RankingObjective.DISCOVERY,
            surface = RankingSurface.HOME,
            nowMs = nowMs,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun scoreEpisodesAdaptiveDisabledReturnsNormalizedPriors() = runTest {
        val inputs = listOf(
            EpisodeRankingInput(
                episode = episode(id = "ep-1"),
                podcast = podcast(),
                priorScore = 10.0,
                source = CandidateSource.SERVER_RECOMMENDATION,
            ),
            EpisodeRankingInput(
                episode = episode(id = "ep-2"),
                podcast = podcast(),
                priorScore = 0.0,
                source = CandidateSource.TRENDING,
            ),
        )
        val result = scorer.scoreEpisodes(
            inputs = inputs,
            history = emptyList(),
            objective = RankingObjective.DISCOVERY,
            surface = RankingSurface.LIBRARY,
            nowMs = nowMs,
        )
        assertEquals(setOf("ep-1", "ep-2"), result.keys)
        assertTrue(result.getValue("ep-1") in 0.0..1.0)
        assertEquals(0.0, result.getValue("ep-2"), 1e-9)
    }

    @Test
    fun scoreEpisodesAdaptiveEnabledUsesFacetsAndHistory() = runTest {
        repository.updateFacet(PreferenceFacetType.GENRE, "Technology", reward = 1.0, now = nowMs)
        repository.updateFacet(PreferenceFacetType.SOURCE, CandidateSource.LIKED.name, reward = 1.0, now = nowMs)
        val inputs = listOf(
            EpisodeRankingInput(
                episode = episode(id = "ep-1", duration = 30 * 60),
                podcast = podcast(preferredSort = "oldest"),
                priorScore = 5.0,
                source = CandidateSource.LIKED,
                isNovel = true,
                online = false,
            ),
            EpisodeRankingInput(
                episode = episode(id = "ep-2"),
                podcast = podcast(id = "pod-2", genre = "News"),
                priorScore = 2.0,
                source = CandidateSource.TRENDING,
            ),
        )
        val result = scorer.scoreEpisodes(
            inputs = inputs,
            history = listOf(history(episodeId = "ep-1", progressMs = 15 * 60 * 1000L)),
            objective = RankingObjective.DISCOVERY,
            surface = RankingSurface.HOME,
            nowMs = nowMs,
        )
        assertEquals(setOf("ep-1", "ep-2"), result.keys)
        assertTrue(result.values.all { it.isFinite() })
    }

    @Test
    fun rankEpisodesReturnsDiversifiedOrder() = runTest {
        controls.setShadowDiagnosticsEnabled(true)
        val inputs = (1..5).map { i ->
            EpisodeRankingInput(
                episode = episode(id = "ep-$i", podcastId = if (i <= 3) "pod-a" else "pod-b"),
                podcast = podcast(id = if (i <= 3) "pod-a" else "pod-b"),
                priorScore = i.toDouble(),
                source = CandidateSource.SUBSCRIPTION,
                isNovel = i == 5,
            )
        }
        val ranked = scorer.rankEpisodes(
            inputs = inputs,
            history = emptyList(),
            objective = RankingObjective.DISCOVERY,
            surface = RankingSurface.HOME,
            diversityPolicy = DiversityPolicy(limit = 3, maxPerShow = 2, reserveNovelSlot = true),
            nowMs = nowMs,
        )
        assertTrue(ranked.size <= 3)
        assertEquals(ranked.map(Episode::id).distinct(), ranked.map(Episode::id))
    }

    @Test
    fun rankPodcastsEmptyReturnsEmpty() = runTest {
        val ranked = scorer.rankPodcasts(
            inputs = emptyList(),
            history = emptyList(),
            objective = RankingObjective.YOUR_SHOWS,
            surface = RankingSurface.HOME,
            nowMs = nowMs,
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun rankPodcastsWithoutDiversityPolicySortsByScore() = runTest {
        val inputs = listOf(
            PodcastRankingInput(
                podcast = podcast(id = "pod-1", latest = episode(id = "ep-1", podcastId = "pod-1")),
                priorScore = 1.0,
                source = CandidateSource.SUBSCRIPTION,
            ),
            PodcastRankingInput(
                podcast = podcast(id = "pod-2", latest = episode(id = "ep-2", podcastId = "pod-2")),
                priorScore = 9.0,
                source = CandidateSource.SUBSCRIPTION,
                isNovel = true,
            ),
        )
        // Disable adaptive so ordering follows normalized priors deterministically.
        controls.setSurfaceEnabled(RankingSurface.HOME, false)
        val ranked = scorer.rankPodcasts(
            inputs = inputs,
            history = listOf(history(episodeId = "ep-1", podcastId = "pod-1")),
            objective = RankingObjective.YOUR_SHOWS,
            surface = RankingSurface.HOME,
            nowMs = nowMs,
        )
        assertEquals(listOf("pod-2", "pod-1"), ranked.map(Podcast::id))
    }

    @Test
    fun rankPodcastsWithDiversityPolicyAppliesReranker() = runTest {
        controls.setShadowDiagnosticsEnabled(true)
        val inputs = (1..4).map { i ->
            PodcastRankingInput(
                podcast = podcast(
                    id = "pod-$i",
                    genre = if (i % 2 == 0) "News" else "Technology",
                    latest = episode(id = "ep-$i", podcastId = "pod-$i"),
                ),
                priorScore = i.toDouble(),
                source = CandidateSource.SUBSCRIPTION,
                timeContextMatch = 0.9,
            )
        }
        val ranked = scorer.rankPodcasts(
            inputs = inputs,
            history = emptyList(),
            objective = RankingObjective.YOUR_SHOWS,
            surface = RankingSurface.HOME,
            diversityPolicy = DiversityPolicy(limit = 2),
            nowMs = nowMs,
        )
        assertTrue(ranked.size <= 2)
    }
}
