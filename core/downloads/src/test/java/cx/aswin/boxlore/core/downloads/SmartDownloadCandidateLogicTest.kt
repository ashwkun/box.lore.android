package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.downloads.SmartDownloadCandidateLogic.MixtapeCandidate
import cx.aswin.boxlore.core.data.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.data.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the pure Smart Download candidate logic. Each test builds small
 * entities by hand so candidate scoring, filtering, and ordering stay deterministic.
 */
class SmartDownloadCandidateLogicTest {

    private val nowMs = 1_700_000_000_000L
    private val nowSeconds = nowMs / 1000L

    @Test
    fun `in-progress score decays with hours since last play`() {
        val score = SmartDownloadCandidateLogic.scoreInProgressCandidate(
            lastPlayedAt = nowMs - 48L * 60 * 60 * 1000,
            nowMs = nowMs,
        )

        assertEquals(1166.666, score, 0.001)
    }

    @Test
    fun `in-progress candidates use latest unfinished history for subscribed podcasts`() {
        val subscribed = podcastEntity("sub1")
        val history = listOf(
            history("old", "sub1", progressMs = 20_000, lastPlayedAt = nowMs - 2_000),
            history("latest", "sub1", progressMs = 30_000, lastPlayedAt = nowMs - 1_000),
            history("zero", "sub1", progressMs = 0, lastPlayedAt = nowMs),
            history("done", "sub1", progressMs = 40_000, isCompleted = true, lastPlayedAt = nowMs),
            history("other", "unsubscribed", progressMs = 50_000, lastPlayedAt = nowMs),
        )

        val candidates = SmartDownloadCandidateLogic.buildInProgressMixtapeCandidates(
            subsMap = mapOf("sub1" to subscribed),
            allHistory = history,
            subIds = setOf("sub1"),
            nowMs = nowMs,
        )

        assertEquals(listOf("latest"), candidates.map { it.episodeId })
        val candidate = candidates.single()
        assertTrue(candidate.isProgress)
        assertEquals("Episode latest", candidate.episode.title)
        assertEquals("Podcast sub1", candidate.episode.podcastTitle)
        assertEquals("sub1", candidate.episode.podcastId)
        assertEquals(30, candidate.episode.duration)
        assertEquals(30_000, candidate.progressMs)
    }

    @Test
    fun `unplayed drop resolution uses serial episode when preferred sort is oldest`() {
        val latest = episode("latest", "serial")
        val resolved = episode("next", "serial")
        val podcast = podcastEntity("serial", preferredSort = "oldest", latestEpisode = latest)

        val candidate = SmartDownloadCandidateLogic.resolveUnplayedDropCandidate(
            pod = podcast,
            resolvedSerial = mapOf("serial" to resolved),
            historyByEpisode = emptyMap(),
        )

        assertNotNull(candidate)
        assertEquals("next", candidate!!.second.id)
        assertEquals("Podcast serial", candidate.second.podcastTitle)
        assertEquals("serial", candidate.second.podcastId)
    }

    @Test
    fun `unplayed drop builder excludes completed and in-progress episodes`() {
        val podcast = podcastEntity("pod1")
        val unplayed = episode("unplayed", "pod1")
        val completed = episode("completed", "pod1")
        val inProgress = episode("progress", "pod1")
        val historyByEpisode = mapOf(
            "completed" to history("completed", "pod1", progressMs = 0, isCompleted = true),
            "progress" to history("progress", "pod1", progressMs = 10_000),
        )

        assertNotNull(SmartDownloadCandidateLogic.buildUnplayedDropCandidate(podcast, unplayed, historyByEpisode))
        assertNull(SmartDownloadCandidateLogic.buildUnplayedDropCandidate(podcast, completed, historyByEpisode))
        assertNull(SmartDownloadCandidateLogic.buildUnplayedDropCandidate(podcast, inProgress, historyByEpisode))
    }

    @Test
    fun `unplayed drop scoring combines freshness new tag serial and podcast score boosts`() {
        val podcast = podcastEntity(
            "pod1",
            subscribedAt = (nowSeconds - 3L * 24 * 60 * 60) * 1000L,
            preferredSort = "oldest",
        )
        val oneDayOld = episode("ep1", "pod1", publishedDate = nowSeconds - 24L * 60 * 60)

        val score = SmartDownloadCandidateLogic.scoreUnplayedDropCandidate(
            pod = podcast,
            episode = oneDayOld,
            podScoresMap = mapOf("pod1" to 100.0),
            nowMs = nowMs,
        )

        assertEquals(1080.0, score, 0.001)
    }

    @Test
    fun `deduplicate and order keeps one episode id and interleaves next episode after progress`() {
        val progressPod1 = candidate("progress-1", "pod1", score = 90.0, isProgress = true)
        val unplayedPod1 = candidate("unplayed-1", "pod1", score = 80.0, isProgress = false)
        val progressPod2 = candidate("progress-2", "pod2", score = 70.0, isProgress = true)
        val unplayedPod3 = candidate("unplayed-3", "pod3", score = 60.0, isProgress = false)
        val duplicateEpisode = candidate("progress-1", "pod4", score = 1.0, isProgress = false)

        val ordered = SmartDownloadCandidateLogic.deduplicateAndOrderMixtapeCandidates(
            inProgressMixtapeCandidates = listOf(progressPod1, progressPod2),
            unplayedDropsMixtapeCandidates = listOf(unplayedPod1, unplayedPod3, duplicateEpisode),
        )

        assertEquals(
            listOf("progress-1", "unplayed-1", "progress-2", "unplayed-3"),
            ordered.map { it.episodeId },
        )
    }

    @Test
    fun `candidate slot rules allow progress plus new tagged unplayed from one podcast`() {
        val progress = candidate("progress", "pod1", isProgress = true)
        val oldUnplayed = candidate("old", "pod1", isProgress = false, subscribedAt = nowMs, publishedDate = 1)
        val newUnplayed = candidate(
            "new",
            "pod1",
            isProgress = false,
            subscribedAt = nowMs - 10_000,
            publishedDate = nowSeconds,
        )

        assertTrue(SmartDownloadCandidateLogic.shouldIncludeCandidate(progress, emptySet()))
        assertFalse(SmartDownloadCandidateLogic.shouldIncludeCandidate(oldUnplayed, setOf(false)))
        assertTrue(SmartDownloadCandidateLogic.shouldIncludeCandidate(newUnplayed, setOf(true)))
    }

    @Test
    fun `generate mixtape candidates builds and orders progress plus unplayed drops`() {
        val pod1 = podcastEntity("pod1", latestEpisode = episode("next-1", "pod1", publishedDate = nowSeconds))
        val pod2 = podcastEntity("pod2", latestEpisode = episode("drop-2", "pod2", publishedDate = nowSeconds))
        val history = listOf(history("resume-1", "pod1", progressMs = 40_000, lastPlayedAt = nowMs - 1_000))

        val candidates = SmartDownloadCandidateLogic.generateMixtapeCandidates(
            subs = listOf(pod1, pod2),
            allHistory = history,
            historyByEpisode = history.associateBy { it.episodeId },
            resolvedSerial = emptyMap(),
            podScoresMap = mapOf("pod2" to 50.0),
            nowMs = nowMs,
        )

        assertEquals(listOf("resume-1", "next-1", "drop-2"), candidates.map { it.episodeId })
        assertEquals(listOf(true, false, false), candidates.map { it.isProgress })
    }

    @Test
    fun `size estimation handles completed downloading unknown and queued downloads`() {
        assertEquals(
            12_345L,
            SmartDownloadCandidateLogic.estimateDownloadSize(download("done", status = DownloadedEpisodeEntity.STATUS_COMPLETED, sizeBytes = 12_345L)),
        )
        assertEquals(
            720_000L,
            SmartDownloadCandidateLogic.estimateDownloadSize(download("downloading", status = DownloadedEpisodeEntity.STATUS_DOWNLOADING, durationMs = 60_000L)),
        )
        assertEquals(
            50L * 1024 * 1024,
            SmartDownloadCandidateLogic.estimateDownloadSize(download("unknown", status = DownloadedEpisodeEntity.STATUS_DOWNLOADING, durationMs = 0L)),
        )
        assertEquals(
            0L,
            SmartDownloadCandidateLogic.estimateDownloadSize(download("queued", status = DownloadedEpisodeEntity.STATUS_QUEUED)),
        )
    }

    @Test
    fun `episode size estimation uses duration and fallback`() {
        assertEquals(1_440_000L, SmartDownloadCandidateLogic.estimateEpisodeSize(episode("timed", duration = 120)))
        assertEquals(50L * 1024 * 1024, SmartDownloadCandidateLogic.estimateEpisodeSize(episode("unknown", duration = 0)))
    }

    @Test
    fun `download quotas preserve manager quota math`() {
        assertEquals(
            SmartDownloadCandidateLogic.DownloadQuotas(subscriptionQuota = 7, recommendationQuota = 2, trendingQuota = 1),
            SmartDownloadCandidateLogic.computeDownloadQuotas(10),
        )
        assertEquals(
            SmartDownloadCandidateLogic.DownloadQuotas(subscriptionQuota = 1, recommendationQuota = 1, trendingQuota = 1),
            SmartDownloadCandidateLogic.computeDownloadQuotas(1),
        )
    }

    private fun episode(
        id: String,
        podcastId: String = "pod1",
        publishedDate: Long = 0L,
        duration: Int = 1800,
    ) = Episode(
        id = id,
        title = "Episode $id",
        description = "",
        audioUrl = "https://audio/$id.mp3",
        imageUrl = "https://img/$id.png",
        podcastImageUrl = "https://img/$podcastId.png",
        podcastTitle = "Podcast $podcastId",
        podcastId = podcastId,
        duration = duration,
        publishedDate = publishedDate,
    )

    private fun podcastEntity(
        id: String,
        subscribedAt: Long = nowMs - 7L * 24 * 60 * 60 * 1000,
        preferredSort: String? = null,
        latestEpisode: Episode? = null,
    ) = PodcastEntity(
        podcastId = id,
        title = "Podcast $id",
        author = "Artist $id",
        imageUrl = "https://img/$id.png",
        description = "Description $id",
        isSubscribed = true,
        subscribedAt = subscribedAt,
        genre = "Comedy",
        preferredSort = preferredSort,
        latestEpisode = latestEpisode,
    )

    private fun history(
        episodeId: String,
        podcastId: String,
        progressMs: Long,
        durationMs: Long = 30_000L,
        isCompleted: Boolean = false,
        lastPlayedAt: Long = nowMs - 1_000,
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
        isCompleted = isCompleted,
        lastPlayedAt = lastPlayedAt,
    )

    private fun candidate(
        episodeId: String,
        podcastId: String,
        score: Double = 100.0,
        isProgress: Boolean,
        subscribedAt: Long = nowMs - 7L * 24 * 60 * 60 * 1000,
        publishedDate: Long = nowSeconds,
    ) = MixtapeCandidate(
        episodeId = episodeId,
        score = score,
        isProgress = isProgress,
        podcast = Podcast(
            id = podcastId,
            title = "Podcast $podcastId",
            artist = "Artist $podcastId",
            imageUrl = "https://img/$podcastId.png",
            subscribedAt = subscribedAt,
        ),
        episode = episode(episodeId, podcastId, publishedDate = publishedDate),
    )

    private fun download(
        episodeId: String,
        status: Int,
        durationMs: Long = 0L,
        sizeBytes: Long = 0L,
    ) = DownloadedEpisodeEntity(
        episodeId = episodeId,
        podcastId = "pod1",
        episodeTitle = "Episode $episodeId",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Podcast pod1",
        podcastImageUrl = null,
        durationMs = durationMs,
        publishedDate = 0L,
        localFilePath = "",
        downloadId = 0L,
        downloadedAt = 0L,
        sizeBytes = sizeBytes,
        status = status,
        isSmartDownloaded = true,
    )
}
