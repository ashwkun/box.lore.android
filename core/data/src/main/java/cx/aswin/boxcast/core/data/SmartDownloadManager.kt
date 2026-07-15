package cx.aswin.boxcast.core.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import cx.aswin.boxcast.core.data.database.BoxLoreDatabase
import cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.data.database.PodcastEntity
import cx.aswin.boxcast.core.data.ranking.AdaptiveCandidateScorer
import cx.aswin.boxcast.core.data.ranking.CandidateSource
import cx.aswin.boxcast.core.data.ranking.EpisodeRankingInput
import cx.aswin.boxcast.core.data.ranking.RankingObjective
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun PodcastEntity.toDownloadManagerPodcast(): Podcast {
    return Podcast(
        id = this.podcastId,
        title = this.title,
        artist = this.author ?: "",
        imageUrl = this.imageUrl ?: "",
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

class SmartDownloadManager(
    private val context: Context,
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPrefs: UserPreferencesRepository
) {
    private val adaptiveScorer = AdaptiveCandidateScorer.getInstance(context)

    private data class MixtapeCandidate(
        val episodeId: String,
        val score: Double,
        val isProgress: Boolean,
        val podcast: Podcast,
        val episode: Episode,
        val progressMs: Long = 0L,
        val durationMs: Long = 0L
    )

    private suspend fun checkSyncConstraints(isManual: Boolean, isForeground: Boolean): Boolean {
        val isEnabled = userPrefs.smartDownloadsEnabledStream.first()
        if (!isEnabled && !isManual && !isForeground) {
            Log.d("SmartDownloadManager", "Smart downloads disabled. Sync skipped.")
            writeLogToFile(context, "Sync skipped: Smart downloads is disabled.")
            return false
        }

        val wifiOnly = userPrefs.smartDownloadsWifiOnlyStream.first()
        if (wifiOnly && !isManual) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true || 
                         capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            if (!isWifi) {
                Log.d("SmartDownloadManager", "Wi-Fi constraint failed. Sync skipped.")
                writeLogToFile(context, "Sync skipped: Wi-Fi constraint failed (Wi-Fi Only enabled, but on mobile data/disconnected).")
                return false
            }
        }

        val chargingOnly = userPrefs.smartDownloadsChargingOnlyStream.first()
        if (chargingOnly && !isManual && !isForeground) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (!isCharging) {
                Log.d("SmartDownloadManager", "Charging constraint failed. Sync skipped.")
                writeLogToFile(context, "Sync skipped: Charging constraint failed (Charging Only enabled, but on battery).")
                return false
            }
        }
        return true
    }

    private suspend fun getAndSyncSubscriptions(): List<PodcastEntity> {
        var subs = database.podcastDao().getSubscribedPodcastsList()
        if (subs.isEmpty()) {
            return emptyList()
        }

        try {
            val feedIds = subs.map { it.podcastId }
            val freshLatestEpisodes = podcastRepository.syncSubscriptions(feedIds)
            for ((podId, ep) in freshLatestEpisodes) {
                subscriptionRepository.updateLatestEpisode(podId, ep)
            }
            subs = database.podcastDao().getSubscribedPodcastsList()
        } catch (e: Exception) {
            Log.e("SmartDownloadManager", "Failed to bulk sync subscription metadata", e)
        }
        return subs
    }

    private suspend fun resolveOldestSerialNextEpisode(
        pod: PodcastEntity,
        allHistory: List<ListeningHistoryEntity>,
        completedEpIdsForResolve: Set<String>,
        inProgressEpIdsForResolve: Set<String>
    ): Episode? {
        try {
            val ongoingId = allHistory.filter { h -> h.podcastId == pod.podcastId && !h.isCompleted && h.progressMs > 0L }.maxByOrNull { it.lastPlayedAt }?.episodeId
            val lastCompletedId = allHistory.filter { h -> h.podcastId == pod.podcastId && h.isCompleted }.maxByOrNull { it.lastPlayedAt }?.episodeId
            
            val page = podcastRepository.getEpisodesPaginated(pod.podcastId, limit = 200, offset = 0, sort = "oldest")
            val allEpisodes = page.episodes
            
            return when {
                ongoingId != null -> {
                    val ongoingIndex = allEpisodes.indexOfFirst { it.id == ongoingId }
                    if (ongoingIndex != -1 && ongoingIndex < allEpisodes.lastIndex) {
                        allEpisodes[ongoingIndex + 1]
                    } else null
                }
                lastCompletedId != null -> {
                    val completedIndex = allEpisodes.indexOfFirst { it.id == lastCompletedId }
                    if (completedIndex != -1 && completedIndex < allEpisodes.lastIndex) {
                        allEpisodes[completedIndex + 1]
                    } else null
                }
                else -> allEpisodes.firstOrNull()
            } ?: allEpisodes.firstOrNull { ep ->
                ep.id !in completedEpIdsForResolve && ep.id !in inProgressEpIdsForResolve
            }
        } catch (e: Exception) {
            Log.e("SmartDownloadManager", "Failed to resolve oldest serial next episode for pod ${pod.podcastId}", e)
        }
        return null
    }

    private suspend fun resolveOldestSerialNextEpisodes(
        subs: List<PodcastEntity>,
        allHistory: List<ListeningHistoryEntity>,
        completedEpisodeIds: List<String>
    ): Map<String, Episode> {
        val completedEpIdsForResolve = allHistory.filter { it.isCompleted }.map { it.episodeId }.toSet() + completedEpisodeIds
        val inProgressEpIdsForResolve = allHistory.filter { !it.isCompleted && it.progressMs > 0L }.map { it.episodeId }.toSet()
        val resolvedSerial = mutableMapOf<String, Episode>()
        
        for (pod in subs) {
            if ((pod.preferredSort ?: "newest") == "oldest") {
                val nextEp = resolveOldestSerialNextEpisode(pod, allHistory, completedEpIdsForResolve, inProgressEpIdsForResolve)
                if (nextEp != null) {
                    resolvedSerial[pod.podcastId] = nextEp
                }
            }
        }
        return resolvedSerial
    }

    private fun buildInProgressMixtapeCandidates(
        subsMap: Map<String, PodcastEntity>,
        allHistory: List<ListeningHistoryEntity>,
        subIds: Set<String>
    ): List<MixtapeCandidate> {
        val inProgressCandidates = allHistory.filter { history ->
            history.podcastId in subIds && !history.isCompleted && history.progressMs > 0L
        }.groupBy { it.podcastId }
         .mapValues { (_, eps) -> eps.maxByOrNull { it.lastPlayedAt } }
         .values.filterNotNull()

        return inProgressCandidates.mapNotNull { history ->
            val parentPod = subsMap[history.podcastId] ?: return@mapNotNull null
            val hoursSinceLastPlay = (System.currentTimeMillis() - history.lastPlayedAt).toDouble() / (1000.0 * 3600.0)
            val score = 1000.0 + 500.0 / (1.0 + hoursSinceLastPlay.coerceAtLeast(0.0) / 24.0)

            val inProgressEpisode = Episode(
                id = history.episodeId,
                title = history.episodeTitle,
                description = "",
                audioUrl = history.episodeAudioUrl ?: "",
                imageUrl = history.episodeImageUrl ?: "",
                podcastImageUrl = history.podcastImageUrl ?: parentPod.imageUrl ?: "",
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

    private fun resolveUnplayedDropCandidate(
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
        if (resolvedEpisode == null) return null
        val ep = resolvedEpisode

        val history = historyByEpisode[ep.id]
        val isUnplayed = history == null || (history.progressMs == 0L && !history.isCompleted)
        return if (isUnplayed) {
            pod to ep.copy(podcastTitle = pod.title, podcastId = pod.podcastId)
        } else null
    }

    private fun buildUnplayedDropsMixtapeCandidates(
        subs: List<PodcastEntity>,
        resolvedSerial: Map<String, Episode>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        podScoresMap: Map<String, Double>
    ): List<MixtapeCandidate> {
        val unplayedDropsCandidates = subs.mapNotNull { pod ->
            resolveUnplayedDropCandidate(pod, resolvedSerial, historyByEpisode)
        }

        return unplayedDropsCandidates.map { (pod, ep) ->
            val isRecent = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0 <= 168.0
            val releasedAfterSub = ep.publishedDate > (pod.subscribedAt / 1000L) || isRecent
            val freshnessBoost = if (releasedAfterSub) {
                val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0
                300.0 / (1.0 + hoursSinceRelease.coerceAtLeast(0.0) / 24.0)
            } else 0.0
            val newTagBoost = if (releasedAfterSub) 200.0 else 0.0
            val sort = pod.preferredSort ?: "newest"
            val serialBoost = if (sort == "oldest") 150.0 else 0.0

            val parentPodScore = podScoresMap[pod.podcastId] ?: 0.0
            val score = 500.0 + freshnessBoost + newTagBoost + serialBoost + 0.8 * parentPodScore

            MixtapeCandidate(
                episodeId = ep.id,
                score = score,
                isProgress = false,
                podcast = pod.toDownloadManagerPodcast(),
                episode = ep
            )
        }
    }

    private fun shouldIncludeCandidate(cand: MixtapeCandidate, slots: Set<Boolean>): Boolean {
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

    private fun deduplicateAndOrderMixtapeCandidates(
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

    private fun generateMixtapeCandidates(
        subs: List<PodcastEntity>,
        allHistory: List<ListeningHistoryEntity>,
        historyByEpisode: Map<String, ListeningHistoryEntity>,
        resolvedSerial: Map<String, Episode>,
        podScoresMap: Map<String, Double>
    ): List<MixtapeCandidate> {
        val subsMap = subs.associateBy { it.podcastId }
        val subIds = subs.map { it.podcastId }.toSet()
        
        val inProgress = buildInProgressMixtapeCandidates(subsMap, allHistory, subIds)
        val unplayed = buildUnplayedDropsMixtapeCandidates(subs, resolvedSerial, historyByEpisode, podScoresMap)
        
        return deduplicateAndOrderMixtapeCandidates(inProgress, unplayed)
    }

    private suspend fun fetchPersonalizedRecommendations(
        subs: List<PodcastEntity>,
        chosenSubsIds: Set<String>,
        targetSize: Int
    ): List<Episode> {
        val chosenRecs = mutableListOf<Episode>()
        if (targetSize > 0) {
            try {
                val historyItems = playbackRepository.getHistoryForRecommendations(15)
                val subscribedIds = subs.map { it.podcastId }
                val subscribedGenres = subs.mapNotNull { it.genre }.distinct()
                val region = userPrefs.regionStream.first().takeIf { it.isNotBlank() } ?: "us"
                
                val recs = podcastRepository.getPersonalizedRecommendations(
                    history = historyItems,
                    country = region,
                    subscribedPodcastIds = subscribedIds,
                    subscribedGenres = subscribedGenres
                )
                
                val completedEpisodeIds = database.listeningHistoryDao().getCompletedEpisodeIds().toSet()
                
                val filteredRecs = recs.filter { ep ->
                    ep.id !in chosenSubsIds &&
                    ep.id !in completedEpisodeIds
                }.distinctBy { it.id }
                
                chosenRecs.addAll(filteredRecs.take(targetSize))
                writeLogToFile(context, "Fetched ${recs.size} recommendations from endpoint. Selected ${chosenRecs.size} after filtering.")
            } catch (e: Exception) {
                Log.e("SmartDownloadManager", "Failed to fetch personalized recommendations for sync", e)
                writeLogToFile(context, "Failed to fetch personalized recommendations: ${e.message}")
            }
        }
        return chosenRecs
    }

    private suspend fun fetchTrendingEpisodes(
        chosenSubsIds: Set<String>,
        chosenRecsIds: Set<String>,
        targetSize: Int
    ): List<Episode> {
        val chosenTrends = mutableListOf<Episode>()
        if (targetSize > 0) {
            try {
                val region = userPrefs.regionStream.first().takeIf { it.isNotBlank() } ?: "us"
                val trendingPods = podcastRepository.getTrendingPodcasts(country = region, limit = 30)
                
                val trendingEpisodes = trendingPods.mapNotNull { it.latestEpisode }
                
                val completedEpisodeIds = database.listeningHistoryDao().getCompletedEpisodeIds().toSet()
                
                val filteredTrends = trendingEpisodes.filter { ep ->
                    ep.id !in chosenSubsIds &&
                    ep.id !in chosenRecsIds &&
                    ep.id !in completedEpisodeIds
                }.distinctBy { it.id }
                
                chosenTrends.addAll(filteredTrends.take(targetSize))
                writeLogToFile(context, "Fetched ${trendingPods.size} trending podcasts from endpoint. Selected ${chosenTrends.size} episodes after filtering.")
            } catch (e: Exception) {
                Log.e("SmartDownloadManager", "Failed to fetch trending podcasts for sync", e)
                writeLogToFile(context, "Failed to fetch trending: ${e.message}")
            }
        }
        return chosenTrends
    }

    private fun estimateDownloadSize(download: DownloadedEpisodeEntity): Long {
        return if (download.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
            download.sizeBytes
        } else if (download.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) {
            val durSec = download.durationMs / 1000L
            if (durSec > 0) durSec * 12000L else 50L * 1024 * 1024
        } else {
            0L
        }
    }

    private fun estimateEpisodeSize(episode: Episode): Long {
        return if (episode.duration > 0) {
            episode.duration.toLong() * 12000L
        } else {
            50L * 1024 * 1024
        }
    }

    private fun checkIsAlreadyDownloadedOrDownloading(episode: Episode, existingDownloads: List<DownloadedEpisodeEntity>): Boolean {
        val isAlreadyDownloaded = existingDownloads.any { it.episodeId == episode.id && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
        val isDownloading = existingDownloads.any { it.episodeId == episode.id && it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING }
        return isAlreadyDownloaded || isDownloading
    }

    private suspend fun recycleOldDownloads(candidateEpisodeIds: Set<String>, existingDownloads: List<DownloadedEpisodeEntity>): Long {
        var currentDownloadedBytes = 0L
        for (download in existingDownloads) {
            if (download.isSmartDownloaded) {
                val estSize = estimateDownloadSize(download)
                currentDownloadedBytes += estSize
                
                if (download.episodeId !in candidateEpisodeIds) {
                    Log.d("SmartDownloadManager", "Recycling/cleaning up old smart-downloaded episode: ${download.episodeId}")
                    writeLogToFile(context, "Recycling/deleting old smart-downloaded episode: '${download.episodeTitle}' (ID: ${download.episodeId})")
                    downloadRepository.removeDownload(download.episodeId)
                    currentDownloadedBytes -= estSize
                }
            }
        }
        return currentDownloadedBytes
    }

    private suspend fun triggerDownloads(
        combinedEpisodes: List<Episode>,
        existingDownloads: List<DownloadedEpisodeEntity>,
        subs: List<PodcastEntity>,
        maxCount: Int,
        storageBudgetMb: Long,
        startingDownloadedBytes: Long,
        startingCount: Int
    ) {
        var countDownloaded = startingCount
        var currentDownloadedBytes = startingDownloadedBytes

        for (episode in combinedEpisodes) {
            if (checkIsAlreadyDownloadedOrDownloading(episode, existingDownloads)) {
                Log.d("SmartDownloadManager", "Episode ${episode.title} already downloaded or downloading. Skipping.")
                continue
            }

            if (countDownloaded >= maxCount) {
                Log.d("SmartDownloadManager", "Hit max count limit ($maxCount episodes). Halting downloads.")
                writeLogToFile(context, "Hit max count limit ($maxCount episodes). Halting downloads.")
                break
            }

            val estimatedSize = estimateEpisodeSize(episode)

            if (storageBudgetMb > 0 && currentDownloadedBytes + estimatedSize > storageBudgetMb * 1024 * 1024L) {
                val estMb = estimatedSize / (1024 * 1024)
                val currMb = currentDownloadedBytes / (1024 * 1024)
                Log.d("SmartDownloadManager", "Hit storage budget limit ($storageBudgetMb MB). Adding '${episode.title}' (Est: $estMb MB) would exceed budget (Current: $currMb MB). Halting downloads.")
                writeLogToFile(context, "Adding '${episode.title}' (Est: $estMb MB) would exceed storage budget ($storageBudgetMb MB). Halting downloads.")
                break
            }

            val parentPod = subs.find { it.podcastId == episode.podcastId }?.toDownloadManagerPodcast() ?: Podcast(
                id = episode.podcastId ?: "0",
                title = episode.podcastTitle?.takeIf { it.isNotBlank() } ?: "Unknown Podcast",
                artist = episode.podcastArtist ?: "Unknown",
                imageUrl = episode.podcastImageUrl?.takeIf { it.isNotBlank() } ?: episode.imageUrl ?: ""
            )

            Log.d("SmartDownloadManager", "Auto-downloading smart mixtape candidate: ${episode.title} (pod=${parentPod.title}, estSize=${estimatedSize / (1024 * 1024)} MB)")
            writeLogToFile(context, "Triggered download for episode: '${episode.title}' (Show: '${parentPod.title}', Est: ${estimatedSize / (1024 * 1024)} MB)")
            downloadRepository.addDownload(episode, parentPod, isSmartDownloaded = true)
            countDownloaded++
            currentDownloadedBytes += estimatedSize
        }
    }

    suspend fun performSync(isManual: Boolean = false, isForeground: Boolean = false): Boolean {
        Log.d("SmartDownloadManager", "Starting smart downloads sync. isManual=$isManual, isForeground=$isForeground")
        writeLogToFile(context, "Starting sync. isManual=$isManual, isForeground=$isForeground")
        
        if (!checkSyncConstraints(isManual, isForeground)) {
            return false
        }

        try {
            val subs = getAndSyncSubscriptions()
            if (subs.isEmpty()) {
                Log.d("SmartDownloadManager", "No subscribed podcasts found. Sync skipped.")
                return false
            }

            val allHistory = database.listeningHistoryDao().getRecentHistoryList(limit = 200)
            val completedEpisodeIds = database.listeningHistoryDao().getCompletedEpisodeIds()

            val resolvedSerial = resolveOldestSerialNextEpisodes(subs, allHistory, completedEpisodeIds)

            val historyByEpisode = allHistory.associateBy { it.episodeId }
            val podScoresMap = adaptiveScorer.scorePodcasts(
                podcasts = subs.map { it.toScorable() },
                history = allHistory,
                objective = RankingObjective.OFFLINE,
                includeAutoDownloadBoost = false,
            )

            val initialCandidates = generateMixtapeCandidates(
                subs,
                allHistory,
                historyByEpisode,
                resolvedSerial,
                podScoresMap,
            )
            val adaptiveScores = adaptiveScorer.scoreEpisodes(
                inputs = initialCandidates.map { candidate ->
                    EpisodeRankingInput(
                        episode = candidate.episode,
                        podcast = candidate.podcast,
                        priorScore = candidate.score,
                        source = if (candidate.isProgress) {
                            CandidateSource.LOCAL_HISTORY
                        } else {
                            CandidateSource.SUBSCRIPTION
                        },
                        online = false,
                    )
                },
                history = allHistory,
                objective = RankingObjective.OFFLINE,
            )
            val orderedCandidates = initialCandidates
                .map { candidate ->
                    candidate.copy(
                        score = adaptiveScores[candidate.episodeId] ?: candidate.score,
                    )
                }
                .sortedByDescending(MixtapeCandidate::score)

            val maxCount = userPrefs.smartDownloadsMaxEpisodesStream.first()
            val storageBudgetMb = userPrefs.smartDownloadsStorageBudgetStream.first()

            val subQuota = (maxCount * 0.7).toInt().coerceAtLeast(1)
            val recQuota = (maxCount * 0.2).toInt().coerceAtLeast(1)
            val trendQuota = (maxCount - subQuota - recQuota).coerceAtLeast(1)
            
            writeLogToFile(context, "Calculated quotas - Subscriptions: $subQuota, Recommendations: $recQuota, Trending: $trendQuota")

            val inProgressSubs = orderedCandidates.filter { it.isProgress }
            val unplayedSubs = orderedCandidates.filter { !it.isProgress }
            
            val chosenSubsCandidates = (inProgressSubs + unplayedSubs).take(subQuota)
            val chosenSubs = chosenSubsCandidates.map { it.episode }
            val subOverflow = (subQuota - chosenSubs.size).coerceAtLeast(0)
            
            writeLogToFile(context, "Selected ${chosenSubs.size} subscription episodes. Overflow: $subOverflow")

            val chosenSubsIds = chosenSubs.map { it.id }.toSet()
            val chosenRecs = fetchPersonalizedRecommendations(subs, chosenSubsIds, recQuota + subOverflow)
            
            val recOverflow = (recQuota + subOverflow - chosenRecs.size).coerceAtLeast(0)
            val chosenRecsIds = chosenRecs.map { it.id }.toSet()
            val chosenTrends = fetchTrendingEpisodes(chosenSubsIds, chosenRecsIds, trendQuota + recOverflow)

            val inProgressEpisodes = chosenSubsCandidates.filter { it.isProgress }.map { it.episode }
            val otherSubsEpisodes = chosenSubsCandidates.filter { !it.isProgress }.map { it.episode }
            
            val combinedEpisodes = (inProgressEpisodes + otherSubsEpisodes + chosenRecs + chosenTrends).distinctBy { it.id }
            val candidateEpisodeIds = combinedEpisodes.map { it.id }.toSet()
            
            writeLogToFile(context, "Combined download candidates list size: ${combinedEpisodes.size}. Target episode IDs: $candidateEpisodeIds")

            val existingDownloads = database.downloadedEpisodeDao().getAllDownloadsSync()
            val currentDownloadedBytes = recycleOldDownloads(candidateEpisodeIds, existingDownloads)

            val countDownloaded = existingDownloads.count { 
                (it.status == DownloadedEpisodeEntity.STATUS_COMPLETED || it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) && 
                it.isSmartDownloaded && 
                it.episodeId in candidateEpisodeIds 
            }
            
            writeLogToFile(context, "Starting download loop. Current active/queued smart downloads count: $countDownloaded / $maxCount. Current size tally: ${currentDownloadedBytes / (1024 * 1024)} MB / $storageBudgetMb MB limit.")

            triggerDownloads(combinedEpisodes, existingDownloads, subs, maxCount, storageBudgetMb, currentDownloadedBytes, countDownloaded)

            userPrefs.setSmartDownloadsLastSyncTime(System.currentTimeMillis())
            Log.d("SmartDownloadManager", "Smart downloads sync completed successfully.")
            writeLogToFile(context, "Sync completed successfully.")
            return true
        } catch (e: Exception) {
            Log.e("SmartDownloadManager", "Error running smart downloads sync", e)
            writeLogToFile(context, "Sync failed with error: ${e.message}")
            return false
        }
    }

    companion object {
        fun schedulePeriodicSync(context: Context, wifiOnly: Boolean, chargingOnly: Boolean) {
            try {
                val constraints = androidx.work.Constraints.Builder().apply {
                    if (wifiOnly) {
                        setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                    } else {
                        setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    }
                    setRequiresCharging(chargingOnly)
                }.build()

                val workRequest = androidx.work.PeriodicWorkRequestBuilder<SmartDownloadWorker>(
                    24, java.util.concurrent.TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "SmartDownloadSync",
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
                Log.d("SmartDownloadManager", "Enqueued periodic SmartDownloadWorker daily task with WorkManager constraints: wifiOnly=$wifiOnly, chargingOnly=$chargingOnly")
                writeLogToFile(context, "WorkManager periodic task scheduled/updated. Constraints: wifiOnly=$wifiOnly, chargingOnly=$chargingOnly")
            } catch (e: Exception) {
                Log.e("SmartDownloadManager", "Failed to schedule periodic sync work", e)
            }
        }

        fun cancelPeriodicSync(context: Context) {
            try {
                androidx.work.WorkManager.getInstance(context).cancelUniqueWork("SmartDownloadSync")
                Log.d("SmartDownloadManager", "Cancelled periodic SmartDownloadWorker task.")
                writeLogToFile(context, "WorkManager periodic task cancelled.")
            } catch (e: Exception) {
                Log.e("SmartDownloadManager", "Failed to cancel periodic sync work", e)
            }
        }

        fun purgeAllSmartDownloads(context: Context) {
            try {
                val request = androidx.work.OneTimeWorkRequestBuilder<PurgeSmartDownloadsWorker>()
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "PurgeSmartDownloads",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.d("SmartDownloadManager", "Enqueued PurgeSmartDownloadsWorker.")
                writeLogToFile(context, "Enqueued background smart downloads purge worker.")
            } catch (e: Exception) {
                Log.e("SmartDownloadManager", "Failed to enqueue purge worker", e)
            }
        }

        fun writeLogToFile(context: Context, message: String) {
            try {
                val logFile = java.io.File(context.filesDir, "smart_downloads_log.txt")
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val logLine = "[$timestamp] $message\n"
                
                if (logFile.exists() && logFile.length() > 512 * 1024) { // 512 KB limit
                    val lines = logFile.readLines()
                    val keepLines = lines.takeLast(1000)
                    logFile.writeText(keepLines.joinToString("\n") + "\n")
                }
                
                logFile.appendText(logLine)
                Log.d("SmartDownloadManager", "FILE_LOG: $message")
            } catch (e: Exception) {
                Log.e("SmartDownloadManager", "Failed to write log to file", e)
            }
        }
    }
}
