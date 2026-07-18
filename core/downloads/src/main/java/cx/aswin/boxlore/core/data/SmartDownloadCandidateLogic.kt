package cx.aswin.boxlore.core.data

import cx.aswin.boxlore.core.data.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.data.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

internal fun PodcastEntity.toDownloadManagerPodcast(): Podcast {
    return Podcast(
        id = this.podcastId,
        title = this.title,
        artist = this.author,
        imageUrl = this.imageUrl,
        fallbackImageUrl = this.latestEpisode?.imageUrl ?: "",
        description = this.description,
        genre = this.genre ?: "Podcast",
        type = this.type,
        latestEpisode = this.latestEpisode,
        subscribedAt = this.subscribedAt,
        podcastGuid = this.podcastGuid,
        fundingUrl = this.fundingUrl,
        fundingMessage = this.fundingMessage,
        medium = this.medium,
        hasValue = this.hasValue,
        updateFrequency = this.updateFrequency,
        location = this.location,
        license = this.license,
        isLocked = this.isLocked,
        preferredSort = this.preferredSort,
        notificationsEnabled = this.notificationsEnabled,
        autoDownloadEnabled = this.autoDownloadEnabled,
        sourceType = this.sourceType,
        feedUrl = this.feedUrl,
        rssRefreshCapability = this.rssRefreshCapability,
        rssCatalogStale = this.rssCatalogStale,
        rssHasNewEpisodes = this.rssHasNewEpisodes,
    )
}

internal object SmartDownloadCandidateLogic {
    private const val ESTIMATED_BYTES_PER_SECOND = 12_000L
    private const val DEFAULT_EPISODE_SIZE_BYTES = 50L * 1024 * 1024

    internal data class MixtapeCandidate(
        val episodeId: String,
        val score: Double,
        val isProgress: Boolean,
        val podcast: Podcast,
        val episode: Episode,
        val progressMs: Long = 0L,
        val durationMs: Long = 0L
    )

    internal data class DownloadQuotas(
        val subscriptionQuota: Int,
        val recommendationQuota: Int,
        val trendingQuota: Int,
    )

    internal fun scoreInProgressCandidate(lastPlayedAt: Long, nowMs: Long): Double {
        val hoursSinceLastPlay = (nowMs - lastPlayedAt).toDouble() / (1000.0 * 3600.0)
        return 1000.0 + 500.0 / (1.0 + hoursSinceLastPlay.coerceAtLeast(0.0) / 24.0)
    }

    internal fun buildInProgressMixtapeCandidates(
        subsMap: Map<String, PodcastEntity>,
        allHistory: List<ListeningHistoryEntity>,
        subIds: Set<String>,
        nowMs: Long,
    ): List<MixtapeCandidate> {
        val inProgressCandidates = allHistory.filter { history ->
            history.podcastId in subIds && !history.isCompleted && history.progressMs > 0L
        }.groupBy { it.podcastId }
            .mapValues { (_, eps) -> eps.maxByOrNull { it.lastPlayedAt } }
            .values.filterNotNull()

        return inProgressCandidates.mapNotNull { history ->
            val parentPod = subsMap[history.podcastId] ?: return@mapNotNull null
            val score = scoreInProgressCandidate(history.lastPlayedAt, nowMs)

            val inProgressEpisode = Episode(
                id = history.episodeId,
                title = history.episodeTitle,
                description = "",
                audioUrl = history.episodeAudioUrl ?: "",
                imageUrl = history.episodeImageUrl ?: "",
                podcastImageUrl = history.podcastImageUrl ?: parentPod.imageUrl,
                podcastTitle = history.podcastName.takeIf { it.isNotBlank() && it != "Unknown Podcast" } ?: parentPod.title,
                podcastId = history.podcastId,
                duration = (history.durationMs / 1000).toInt(),
                publishedDate = 0L
            )

            MixtapeCandidate(
                episodeId = history.episodeId,
                score = score,
                isProgress = true,
                podcast = parentPod.toDownloadManagerPodcast(),
                episode = inProgressEpisode,
                progressMs = history.progressMs,
                durationMs = history.durationMs
            )
        }
    }

    internal fun resolveUnplayedDropCandidate(
        pod: PodcastEntity,
        resolvedSerial: Map<String, Episode>,
        historyByEpisode: Map<String, ListeningHistoryEntity>
    ): Pair<PodcastEntity, Episode>? {
        val sort = pod.preferredSort ?: "newest"
        val resolvedEpisode = if (sort == "oldest") {
            resolvedSerial[pod.podcastId] ?: pod.latestEpisode
        } else {
            pod.latestEpisode
        }
        return buildUnplayedDropCandidate(pod, resolvedEpisode, historyByEpisode)
    }

    internal fun buildUnplayedDropCandidate(
        pod: PodcastEntity,
        episode: Episode?,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
    ): Pair<PodcastEntity, Episode>? {
        val ep = episode ?: return null
        val history = historyByEpisode[ep.id]
        val isUnplayed = history == null || (history.progressMs == 0L && !history.isCompleted)
        return if (isUnplayed) {
            pod to ep.copy(podcastTitle = pod.title, podcastId = pod.podcastId)
        } else {
            null
        }
    }

    internal fun scoreUnplayedDropCandidate(
        pod: PodcastEntity,
        episode: Episode,
        podScoresMap: Map<String, Double>,
        nowMs: Long,
    ): Double {
        val nowSeconds = nowMs / 1000.0
        val isRecent = (nowSeconds - episode.publishedDate) / 3600.0 <= 168.0
        val releasedAfterSub = episode.publishedDate > (pod.subscribedAt / 1000L) || isRecent
        val freshnessBoost = if (releasedAfterSub) {
            val hoursSinceRelease = (nowSeconds - episode.publishedDate) / 3600.0
            300.0 / (1.0 + hoursSinceRelease.coerceAtLeast(0.0) / 24.0)
        } else {
            0.0
        }
        val newTagBoost = if (releasedAfterSub) 200.0 else 0.0
        val sort = pod.preferredSort ?: "newest"
        val serialBoost = if (sort == "oldest") 150.0 else 0.0
        val parentPodScore = podScoresMap[pod.podcastId] ?: 0.0

        return 500.0 + freshnessBoost + newTagBoost + serialBoost + 0.8 * parentPodScore
    }

    internal fun buildUnplayedDropsMixtapeCandidates(
        subs: List<PodcastEntity>,
        resolvedSerial: Map<String, Episode>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        podScoresMap: Map<String, Double>,
        nowMs: Long,
    ): List<MixtapeCandidate> {
        val unplayedDropsCandidates = subs.mapNotNull { pod ->
            resolveUnplayedDropCandidate(pod, resolvedSerial, historyByEpisode)
        }

        return unplayedDropsCandidates.map { (pod, ep) ->
            MixtapeCandidate(
                episodeId = ep.id,
                score = scoreUnplayedDropCandidate(pod, ep, podScoresMap, nowMs),
                isProgress = false,
                podcast = pod.toDownloadManagerPodcast(),
                episode = ep
            )
        }
    }

    internal fun shouldIncludeCandidate(cand: MixtapeCandidate, slots: Set<Boolean>): Boolean {
        if (cand.isProgress !in slots) {
            return true
        }
        if (slots.size < 2) {
            val isNewTagged = !cand.isProgress && cand.episode.publishedDate > (cand.podcast.subscribedAt / 1000L)
            if (isNewTagged && slots.contains(true)) {
                return true
            }
            if (cand.isProgress && slots.contains(false)) {
                return true
            }
        }
        return false
    }

    internal fun deduplicateAndOrderMixtapeCandidates(
        inProgressMixtapeCandidates: List<MixtapeCandidate>,
        unplayedDropsMixtapeCandidates: List<MixtapeCandidate>
    ): List<MixtapeCandidate> {
        val allMixtapeCandidates = (inProgressMixtapeCandidates + unplayedDropsMixtapeCandidates)
            .sortedByDescending { it.score }

        val deduplicatedCandidates = mutableListOf<MixtapeCandidate>()
        val seenEpisodeIds = mutableSetOf<String>()
        val podcastSlots = mutableMapOf<String, MutableSet<Boolean>>()

        for (cand in allMixtapeCandidates) {
            if (cand.episodeId in seenEpisodeIds) continue
            val podId = cand.podcast.id
            val slots = podcastSlots.getOrPut(podId) { mutableSetOf() }
            if (shouldIncludeCandidate(cand, slots)) {
                slots.add(cand.isProgress)
                seenEpisodeIds.add(cand.episodeId)
                deduplicatedCandidates.add(cand)
            }
        }

        val orderedCandidates = mutableListOf<MixtapeCandidate>()
        val inProgressList = deduplicatedCandidates.filter { it.isProgress }
        val unplayedList = deduplicatedCandidates.filter { !it.isProgress }.toMutableList()

        for (ipCand in inProgressList) {
            orderedCandidates.add(ipCand)
            val nextEpCand = unplayedList.find { it.podcast.id == ipCand.podcast.id }
            if (nextEpCand != null) {
                orderedCandidates.add(nextEpCand)
                unplayedList.remove(nextEpCand)
            }
        }
        orderedCandidates.addAll(unplayedList)
        return orderedCandidates
    }

    internal fun generateMixtapeCandidates(
        subs: List<PodcastEntity>,
        allHistory: List<ListeningHistoryEntity>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        resolvedSerial: Map<String, Episode>,
        podScoresMap: Map<String, Double>,
        nowMs: Long,
    ): List<MixtapeCandidate> {
        val subsMap = subs.associateBy { it.podcastId }
        val subIds = subs.map { it.podcastId }.toSet()

        val inProgress = buildInProgressMixtapeCandidates(subsMap, allHistory, subIds, nowMs)
        val unplayed = buildUnplayedDropsMixtapeCandidates(subs, resolvedSerial, historyByEpisode, podScoresMap, nowMs)

        return deduplicateAndOrderMixtapeCandidates(inProgress, unplayed)
    }

    internal fun estimateDownloadSize(download: DownloadedEpisodeEntity): Long {
        return if (download.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
            download.sizeBytes
        } else if (download.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) {
            val durSec = download.durationMs / 1000L
            if (durSec > 0) durSec * ESTIMATED_BYTES_PER_SECOND else DEFAULT_EPISODE_SIZE_BYTES
        } else {
            0L
        }
    }

    internal fun estimateEpisodeSize(episode: Episode): Long {
        return if (episode.duration > 0) {
            episode.duration.toLong() * ESTIMATED_BYTES_PER_SECOND
        } else {
            DEFAULT_EPISODE_SIZE_BYTES
        }
    }

    internal fun computeDownloadQuotas(maxCount: Int): DownloadQuotas {
        val subQuota = (maxCount * 0.7).toInt().coerceAtLeast(1)
        val recQuota = (maxCount * 0.2).toInt().coerceAtLeast(1)
        val trendQuota = (maxCount - subQuota - recQuota).coerceAtLeast(1)
        return DownloadQuotas(
            subscriptionQuota = subQuota,
            recommendationQuota = recQuota,
            trendingQuota = trendQuota,
        )
    }
}
