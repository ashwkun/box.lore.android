package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.HistoryItem
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the tiered refill engine, driven through a hand-rolled fake of
 * [SmartQueueSources]. No Android framework, no network: each test shapes the fake's
 * canned data to force the engine down a specific tier path, then asserts on the
 * returned batch (sources, ordering, exclusions, and network usage).
 */
class SmartQueueEngineTest {

    private val now = 1_700_000_000_000L

    // ── fakes ───────────────────────────────────────────────────────────────

    private open class FakeSources : SmartQueueSources {
        val episodesByPodcast = mutableMapOf<String, List<Episode>>()
        val podcastDetails = mutableMapOf<String, Podcast>()
        var subscriptions: List<Podcast> = emptyList()
        var completedIds: Set<String> = emptySet()
        var recentlyPlayedPodcastIds: Set<String> = emptySet()
        var resumeCandidates: List<ListeningHistoryEntity> = emptyList()
        var recentHistory: List<ListeningHistoryEntity> = emptyList()
        var region: String = "us"
        var interests: List<String> = emptyList()
        var recommendationHistory: List<HistoryItem> = emptyList()
        var recommendations: List<Episode> = emptyList()
        var similarEpisodes: List<Episode> = emptyList()
        var trendingPodcasts: List<Podcast> = emptyList()
        val queueCandidateFailureIds = mutableSetOf<String>()

        // Spies
        var recommendationsCalls = 0
        var lastRecommendationCountry: String? = null
        var trendingCalls = 0
        var lastTrendingCountry: String? = null
        var lastTrendingCategory: String? = null
        var similarCalls = 0
        var lastSimilarCountry: String? = null
        var feedFetches = mutableListOf<String>()
        var queueCandidateRequests = mutableListOf<Pair<String, Int>>()

        override suspend fun getEpisodes(podcastId: String): List<Episode> {
            feedFetches.add(podcastId)
            return episodesByPodcast[podcastId] ?: emptyList()
        }

        override suspend fun getQueueCandidates(podcastId: String, limit: Int): List<Episode> {
            queueCandidateRequests += podcastId to limit
            if (podcastId in queueCandidateFailureIds) {
                error("Broken feed: $podcastId")
            }
            return episodesByPodcast[podcastId].orEmpty()
                .sortedByDescending { it.publishedDate }
                .take(limit)
        }

        override suspend fun getPodcastDetails(podcastId: String): Podcast? = podcastDetails[podcastId]
        override suspend fun getSubscribedPodcasts(): List<Podcast> = subscriptions
        override suspend fun getCompletedEpisodeIds(): Set<String> = completedIds
        override suspend fun getRecentlyPlayedPodcastIds(sinceMs: Long): Set<String> = recentlyPlayedPodcastIds
        override suspend fun getResumeCandidates(): List<ListeningHistoryEntity> = resumeCandidates
        override suspend fun getRecentHistory(limit: Int): List<ListeningHistoryEntity> = recentHistory.take(limit)
        override suspend fun getRegion(): String = region
        override suspend fun getInterests(): List<String> = interests

        override suspend fun getHistoryForRecommendations(limit: Int): List<HistoryItem> =
            recommendationHistory.take(limit)

        override suspend fun getPersonalizedRecommendations(
            history: List<HistoryItem>,
            interests: List<String>,
            country: String?,
            subscribedPodcastIds: List<String>,
            subscribedGenres: List<String>
        ): List<Episode> {
            recommendationsCalls++
            lastRecommendationCountry = country
            return recommendations
        }

        override suspend fun getSimilarEpisodes(
            episodeId: String,
            podcastId: String,
            title: String,
            description: String,
            podcastTitle: String,
            country: String?
        ): List<Episode> {
            similarCalls++
            lastSimilarCountry = country
            return similarEpisodes
        }

        override suspend fun getTrendingPodcasts(country: String, category: String?): List<Podcast> {
            trendingCalls++
            lastTrendingCountry = country
            lastTrendingCategory = category
            return trendingPodcasts
        }
    }

    private fun episode(
        id: Long,
        podcastId: String = "pod1",
        publishedDate: Long = id, // monotonic by default
        episodeType: String? = "full",
        duration: Int = 1800,
        podcastTitle: String = "Podcast $podcastId",
        podcastGenre: String? = "Comedy"
    ) = Episode(
        id = id.toString(),
        title = "Episode $id",
        description = "",
        audioUrl = "https://audio/$id.mp3",
        imageUrl = "https://img/$id.png",
        podcastImageUrl = "https://img/$podcastId.png",
        podcastTitle = podcastTitle,
        podcastId = podcastId,
        podcastGenre = podcastGenre,
        publishedDate = publishedDate,
        episodeType = episodeType,
        duration = duration
    )

    private fun podcast(
        id: String,
        type: String = "episodic",
        genre: String = "Comedy",
        preferredSort: String? = null,
        latestEpisode: Episode? = null
    ) = Podcast(
        id = id,
        title = "Podcast $id",
        artist = "Artist $id",
        imageUrl = "https://img/$id.png",
        type = type,
        genre = genre,
        preferredSort = preferredSort,
        latestEpisode = latestEpisode
    )

    private fun currentItem(id: Long) = EpisodeItem(id = id, title = "Episode $id")

    private fun resumeEntry(
        episodeId: String,
        podcastId: String,
        progressMs: Long,
        durationMs: Long = 100_000,
        lastPlayedAt: Long = now - 1000,
        isLiked: Boolean = false
    ) = ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode $episodeId",
        episodeImageUrl = null,
        podcastImageUrl = "https://img/$podcastId.png",
        episodeAudioUrl = "https://audio/$episodeId.mp3",
        podcastName = "Podcast $podcastId",
        progressMs = progressMs,
        durationMs = durationMs,
        isCompleted = false,
        isLiked = isLiked,
        lastPlayedAt = lastPlayedAt
    )

    private fun engine(sources: FakeSources, skipMemory: QueueSkipMemory? = null) =
        DefaultSmartQueueEngine(sources = sources, skipMemory = skipMemory, nowMs = { now })

    private fun skipMemory(): QueueSkipMemory {
        var raw: String? = null
        return QueueSkipMemory(readRaw = { raw }, writeRaw = { raw = it }, nowMs = { now })
    }

    // ── Tier 0: same-podcast continuation ───────────────────────────────────

    @Test
    fun `serial podcast continues chronologically after the current episode`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..6L).map { episode(it) }

        val batch = engine(sources).getNextEpisodes(currentItem(3), podcast("pod1", type = "serial"))

        assertEquals(listOf("4", "5", "6"), batch.map { it.episode.id.toString() })
        assertTrue(batch.all { it.source == SmartQueueEngine.SOURCE_SAME_PODCAST })
        // Binge fast path: no network tiers at all.
        assertEquals(0, sources.recommendationsCalls)
        assertEquals(0, sources.trendingCalls)
    }

    @Test
    fun `preferredSort oldest forces chronological continuation on an episodic show`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..6L).map { episode(it) }

        val batch = engine(sources).getNextEpisodes(
            currentItem(2), podcast("pod1", type = "episodic"), preferredSort = "oldest"
        )

        assertEquals(listOf("3", "4", "5", "6"), batch.map { it.episode.id.toString() })
    }

    @Test
    fun `news-style episodic show queues only newer episodes than current`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..5L).map { episode(it, podcastGenre = "News") }

        val batch = engine(sources).getNextEpisodes(
            currentItem(2), podcast("pod1", type = "episodic", genre = "News")
        )

        // Playing ep 2 → only fresher eps 3, 4, 5 (newest first), never rewind to ep 1.
        assertEquals(listOf("5", "4", "3"), batch.map { it.episode.id.toString() })
    }

    @Test
    fun `newest sort on latest episode does not rewind into older episodes`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..5L).map { episode(it) }
        sources.subscriptions = listOf(podcast("sub1"))
        sources.episodesByPodcast["sub1"] = listOf(episode(901, "sub1"))

        val batch = engine(sources).getNextEpisodes(
            currentItem(5), podcast("pod1", type = "episodic", preferredSort = "newest")
        )

        assertTrue(batch.none { it.source == SmartQueueEngine.SOURCE_SAME_PODCAST })
    }

    @Test
    fun `completed episodes and trailers are never suggested`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(
            episode(1), episode(2), episode(3),
            episode(4, episodeType = "trailer"),
            episode(5)
        )
        sources.completedIds = setOf("3")

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(listOf("2", "5"), batch.map { it.episode.id.toString() })
    }

    @Test
    fun `episodes already in the live queue are excluded`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..5L).map { episode(it) }

        val batch = engine(sources).getNextEpisodes(
            currentItem(1), podcast("pod1", type = "serial"),
            excludeEpisodeIds = setOf("2", "4")
        )

        assertEquals(listOf("3", "5"), batch.map { it.episode.id.toString() })
    }

    @Test
    fun `batch never contains duplicates`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..10L).map { episode(it) }

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(batch.size, batch.map { it.episode.id }.distinct().size)
    }

    @Test
    fun `discovery landing skips tier 0 so one suggested episode does not backfill the whole show`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..10L).map { episode(it) }
        sources.subscriptions = listOf(podcast("sub1"))
        sources.episodesByPodcast["sub1"] = listOf(episode(901, "sub1"))

        SmartQueueEngine.DISCOVERY_REFILL_SOURCES.forEach { discoverySource ->
            val batch = engine(sources).getNextEpisodes(
                currentItem(1),
                podcast("pod1", type = "serial"),
                currentContextSourceId = discoverySource
            )
            assertTrue(batch.none { it.source == SmartQueueEngine.SOURCE_SAME_PODCAST }, "Tier 0 must not run for discovery source $discoverySource")
        }
    }

    @Test
    fun `same-podcast auto-fill and manual play still allow tier 0 continuation`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..6L).map { episode(it) }

        val bingeBatch = engine(sources).getNextEpisodes(
            currentItem(3),
            podcast("pod1", type = "serial"),
            currentContextSourceId = SmartQueueEngine.SOURCE_SAME_PODCAST
        )
        assertTrue(bingeBatch.all { it.source == SmartQueueEngine.SOURCE_SAME_PODCAST })

        val manualBatch = engine(sources).getNextEpisodes(
            currentItem(3),
            podcast("pod1", type = "serial"),
            currentContextSourceId = null
        )
        assertTrue(manualBatch.all { it.source == SmartQueueEngine.SOURCE_SAME_PODCAST })
    }

    // ── Tier 1: resume ──────────────────────────────────────────────────────

    @Test
    fun `show exhausted falls back to one resume candidate`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1)) // nothing after current
        sources.resumeCandidates = listOf(
            resumeEntry("101", "pod2", progressMs = 50_000),
            resumeEntry("102", "pod3", progressMs = 40_000)
        )

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        val resumes = batch.filter { it.source == SmartQueueEngine.SOURCE_RESUME }
        assertEquals(1, resumes.size, "at most one resume nudge per batch")
        assertEquals("101", resumes.first().episode.id.toString())
    }

    @Test
    fun `resume ignores barely-started, nearly-done, stale and same-podcast episodes`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.resumeCandidates = listOf(
            resumeEntry("201", "pod2", progressMs = 5_000),                    // < 10%
            resumeEntry("202", "pod2", progressMs = 95_000),                   // > 90%
            resumeEntry("203", "pod1", progressMs = 50_000),                   // same podcast
            resumeEntry("204", "pod2", progressMs = 50_000,
                lastPlayedAt = now - 31L * 24 * 60 * 60 * 1000)                // stale
        )

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertTrue(batch.none { it.source == SmartQueueEngine.SOURCE_RESUME })
    }

    // ── Tier 2: scored subscriptions ────────────────────────────────────────

    @Test
    fun `fallback pulls newest unplayed episodes from subscriptions`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.subscriptions = listOf(podcast("sub1"), podcast("sub2"))
        sources.episodesByPodcast["sub1"] = listOf(episode(301, "sub1"), episode(302, "sub1"))
        sources.episodesByPodcast["sub2"] = listOf(episode(401, "sub2"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        val subs = batch.filter { it.source == SmartQueueEngine.SOURCE_SUBSCRIPTION }
        assertTrue(subs.isNotEmpty())
        // Round-robin: one episode per show, newest first within a show.
        assertEquals(setOf("sub1", "sub2"), subs.map { it.podcast.id }.toSet())
        assertTrue(subs.any { it.episode.id == 302L })
    }

    @Test
    fun `recently played subscriptions are skipped to avoid loops`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.subscriptions = listOf(podcast("sub1"), podcast("sub2"))
        sources.recentlyPlayedPodcastIds = setOf("sub1")
        sources.episodesByPodcast["sub1"] = listOf(episode(301, "sub1"))
        sources.episodesByPodcast["sub2"] = listOf(episode(401, "sub2"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        val subs = batch.filter { it.source == SmartQueueEngine.SOURCE_SUBSCRIPTION }
        assertTrue(subs.none { it.podcast.id == "sub1" })
    }

    @Test
    fun `RSS subscription candidates are bounded and negative IDs remain playable`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.subscriptions = listOf(podcast("rss:feed"))
        sources.episodesByPodcast["rss:feed"] = (1L..2_000L).map { index ->
            episode(
                id = -index,
                podcastId = "rss:feed",
                publishedDate = index,
            )
        }

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(
            listOf("rss:feed" to DefaultSmartQueueEngine.SUBSCRIPTION_CANDIDATE_LIMIT),
            sources.queueCandidateRequests,
        )
        assertTrue(batch.any { it.episode.id < 0L })
    }

    @Test
    fun `cached latest episode with blank audioUrl is skipped in favor of queue candidates`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        val malformedCached = episode(301, "sub1").copy(audioUrl = "")
        sources.subscriptions = listOf(podcast("sub1", latestEpisode = malformedCached))
        sources.episodesByPodcast["sub1"] = listOf(episode(302, "sub1"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        val subs = batch.filter { it.source == SmartQueueEngine.SOURCE_SUBSCRIPTION }
        assertTrue(subs.none { it.episode.id == 301L }, "malformed cached episode (blank audioUrl) must never be suggested")
        assertTrue(sources.queueCandidateRequests.any { it.first == "sub1" }, "getQueueCandidates must be invoked as a fallback when the cache is unusable")
        assertTrue(subs.any { it.episode.id == 302L })
    }

    @Test
    fun `cached latest episode with a non-numeric id is skipped in favor of queue candidates`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        val malformedCached = episode(401, "sub1").copy(id = "not-a-number")
        sources.subscriptions = listOf(podcast("sub1", latestEpisode = malformedCached))
        sources.episodesByPodcast["sub1"] = listOf(episode(402, "sub1"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        val subs = batch.filter { it.source == SmartQueueEngine.SOURCE_SUBSCRIPTION }
        assertTrue(subs.none { it.episode.id.toString() == "not-a-number" }, "malformed cached episode (non-numeric id) must never be suggested")
        assertTrue(sources.queueCandidateRequests.any { it.first == "sub1" }, "getQueueCandidates must be invoked as a fallback when the cache is unusable")
        assertTrue(subs.any { it.episode.id == 402L })
    }

    @Test
    fun `one failing RSS subscription does not abort other queue fallbacks`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.subscriptions = listOf(podcast("rss:broken"), podcast("subGood"))
        sources.queueCandidateFailureIds += "rss:broken"
        sources.episodesByPodcast["subGood"] = listOf(episode(601, "subGood"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertTrue(batch.any { it.podcast.id == "subGood" })
    }

    // ── Skip memory ─────────────────────────────────────────────────────────

    @Test
    fun `skipped episodes are never re-suggested`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..4L).map { episode(it) }
        val memory = skipMemory()
        memory.recordSkip("3", "pod1", "same_podcast")

        val batch = engine(sources, memory).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(listOf("2", "4"), batch.map { it.episode.id.toString() })
    }

    @Test
    fun `podcasts with repeated skips are down-ranked in subscription fallback`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.subscriptions = listOf(podcast("subBad"), podcast("subGood"))
        sources.episodesByPodcast["subBad"] = listOf(episode(501, "subBad"))
        sources.episodesByPodcast["subGood"] = listOf(episode(601, "subGood"))
        val memory = skipMemory()
        memory.recordSkip("801", "subBad", "subscription")
        memory.recordSkip("802", "subBad", "subscription")

        val batch = engine(sources, memory).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        val subs = batch.filter { it.source == SmartQueueEngine.SOURCE_SUBSCRIPTION }
        assertTrue(subs.isNotEmpty())
        assertEquals("subGood", subs.first().podcast.id)
    }

    // ── Tier 3 + 4: network tiers ───────────────────────────────────────────

    @Test
    fun `warm users get personalized recommendations when local tiers are thin`() = runTest {
        val sources = FakeSources()
        sources.region = "in"
        sources.recommendationHistory = listOf(
            HistoryItem(podcastTitle = "Past Pod", episodeTitle = "Past Ep")
        )
        sources.episodesByPodcast["pod1"] = listOf(episode(1)) // exhausted
        sources.recommendations = listOf(episode(701, "recPod"), episode(702, "recPod2"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(1, sources.recommendationsCalls)
        assertEquals(0, sources.similarCalls)
        assertEquals("in", sources.lastRecommendationCountry)
        assertTrue(batch.any { it.source == SmartQueueEngine.SOURCE_PERSONALIZED_REC })
    }

    @Test
    fun `cold users with episode metadata get similar episodes instead of recommendations`() = runTest {
        val sources = FakeSources()
        sources.region = "us"
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.similarEpisodes = listOf(episode(801, "simPod"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(0, sources.recommendationsCalls)
        assertEquals(1, sources.similarCalls)
        assertEquals("us", sources.lastSimilarCountry)
        assertTrue(batch.any { it.source == SmartQueueEngine.SOURCE_SIMILAR_EPISODE })
    }

    @Test
    fun `cold users without metadata skip tier 3 and fall through to trending`() = runTest {
        val sources = FakeSources()
        sources.region = "de"
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.recommendations = listOf(episode(701, "recPod"))
        sources.trendingPodcasts = listOf(podcast("trend1"))
        sources.episodesByPodcast["trend1"] = listOf(episode(901, "trend1"))

        val batch = engine(sources).getNextEpisodes(
            EpisodeItem(id = 0L, title = ""),
            podcast("pod1", genre = "Comedy", type = "serial")
        )

        assertEquals(0, sources.recommendationsCalls)
        assertEquals(0, sources.similarCalls)
        assertEquals(1, sources.trendingCalls)
        assertTrue(batch.any { it.source == SmartQueueEngine.SOURCE_TRENDING })
    }

    @Test
    fun `trending is region-aware and used when recommendations come back empty`() = runTest {
        val sources = FakeSources()
        sources.region = "de"
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.recommendations = emptyList()
        sources.trendingPodcasts = listOf(podcast("trend1"))
        sources.episodesByPodcast["trend1"] = listOf(episode(901, "trend1"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", genre = "Comedy", type = "serial"))

        assertEquals(1, sources.trendingCalls)
        assertEquals("de", sources.lastTrendingCountry)
        assertEquals("Comedy", sources.lastTrendingCategory)
        assertTrue(batch.any { it.source == SmartQueueEngine.SOURCE_TRENDING })
    }

    @Test
    fun `healthy same-show continuation makes no network calls at all`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = (1L..10L).map { episode(it) }

        engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(0, sources.recommendationsCalls)
        assertEquals(0, sources.trendingCalls)
        assertEquals(0, sources.similarCalls)
    }

    @Test
    fun `offline degradation returns local results without throwing`() = runTest {
        val sources = object : FakeSources() {
            override suspend fun getPersonalizedRecommendations(
                history: List<HistoryItem>, interests: List<String>, country: String?,
                subscribedPodcastIds: List<String>, subscribedGenres: List<String>
            ): List<Episode> = throw RuntimeException("offline")

            override suspend fun getTrendingPodcasts(country: String, category: String?): List<Podcast> =
                throw RuntimeException("offline")

            override suspend fun getSimilarEpisodes(
                episodeId: String, podcastId: String, title: String,
                description: String, podcastTitle: String, country: String?
            ): List<Episode> = throw RuntimeException("offline")
        }
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.resumeCandidates = listOf(resumeEntry("101", "pod2", progressMs = 50_000))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        // Whatever local tiers produced still comes back.
        assertEquals(listOf(SmartQueueEngine.SOURCE_RESUME), batch.map { it.source })
    }

    // ── Tier 3.5: liked similarity boost ────────────────────────────────────

    @Test
    fun `recent like triggers a region-aware similar-episode boost when tiers are thin`() = runTest {
        val sources = FakeSources()
        sources.region = "gb"
        sources.recommendationHistory = listOf(
            HistoryItem(podcastTitle = "Past Pod", episodeTitle = "Past Ep")
        )
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.recentHistory = listOf(
            resumeEntry("111", "pod9", progressMs = 60_000, isLiked = true)
        )
        sources.similarEpisodes = listOf(episode(951, "simPod"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(1, sources.recommendationsCalls)
        assertEquals(1, sources.similarCalls)
        assertEquals("gb", sources.lastSimilarCountry)
        assertTrue(batch.any { it.episode.id == 951L && it.source == SmartQueueEngine.SOURCE_SIMILAR_LIKED })
    }

    @Test
    fun `liked similar boost is skipped when tier 3 already used episode similar`() = runTest {
        val sources = FakeSources()
        sources.region = "gb"
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.recentHistory = listOf(
            resumeEntry("111", "pod9", progressMs = 60_000, isLiked = true)
        )
        sources.similarEpisodes = listOf(episode(801, "simPod"))

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertEquals(0, sources.recommendationsCalls)
        assertEquals(1, sources.similarCalls)
        assertTrue(batch.any { it.source == SmartQueueEngine.SOURCE_SIMILAR_EPISODE })
        assertEquals(1, batch.count { it.source == SmartQueueEngine.SOURCE_SIMILAR_EPISODE })
    }

    // ── Briefing handling ───────────────────────────────────────────────────

    @Test
    fun `briefing skips same-show tier and prefers short episodes in fallback`() = runTest {
        val sources = FakeSources()
        sources.region = "us"
        sources.subscriptions = listOf(podcast("subLong"), podcast("subShort"))
        sources.episodesByPodcast["subLong"] = listOf(episode(311, "subLong", duration = 3600))
        sources.episodesByPodcast["subShort"] = listOf(episode(312, "subShort", duration = 600))

        val briefingItem = EpisodeItem(id = 0L, title = "Morning Briefing")
        val briefingPodcast = podcast("briefing_daily", genre = "News")

        val batch = engine(sources).getNextEpisodes(briefingItem, briefingPodcast)

        assertTrue(batch.isNotEmpty())
        assertTrue(batch.none { it.source == SmartQueueEngine.SOURCE_SAME_PODCAST })
        // Short episode sorted ahead of the long one.
        val ids = batch.map { it.episode.id }
        assertTrue(ids.indexOf(312L) < ids.indexOf(311L))
    }

    @Test
    fun `briefing trending fallback uses the News genre`() = runTest {
        val sources = FakeSources()
        sources.region = "in"
        sources.trendingPodcasts = listOf(podcast("trendNews", genre = "News"))
        sources.episodesByPodcast["trendNews"] = listOf(episode(971, "trendNews"))

        val briefingItem = EpisodeItem(id = 0L, title = "Morning Briefing")
        val batch = engine(sources).getNextEpisodes(briefingItem, podcast("briefing_daily", genre = "Podcast"))

        assertEquals(1, sources.trendingCalls)
        assertEquals("News", sources.lastTrendingCategory)
        assertEquals("in", sources.lastTrendingCountry)
        assertTrue(batch.any { it.source == SmartQueueEngine.SOURCE_TRENDING })
    }

    // ── batch invariants ────────────────────────────────────────────────────

    @Test
    fun `null podcast returns an empty batch instead of throwing`() = runTest {
        val batch = engine(FakeSources()).getNextEpisodes(currentItem(1), null)
        assertTrue(batch.isEmpty())
    }

    @Test
    fun `fallback batch is capped at the target size`() = runTest {
        val sources = FakeSources()
        sources.episodesByPodcast["pod1"] = listOf(episode(1))
        sources.subscriptions = (1..10).map { podcast("sub$it") }
        (1..10).forEach { i ->
            sources.episodesByPodcast["sub$i"] = listOf(episode((1000 + i).toLong(), "sub$i"))
        }

        val batch = engine(sources).getNextEpisodes(currentItem(1), podcast("pod1", type = "serial"))

        assertTrue(batch.size <= DefaultSmartQueueEngine.FALLBACK_BATCH_TARGET, "fallback batch of ${batch.size} must be <= ${DefaultSmartQueueEngine.FALLBACK_BATCH_TARGET}")
        assertFalse(batch.isEmpty())
    }
}
