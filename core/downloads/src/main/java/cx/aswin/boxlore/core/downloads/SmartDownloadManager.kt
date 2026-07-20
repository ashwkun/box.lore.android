package cx.aswin.boxlore.core.downloads

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.PodcastScoring
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.toScorable
import cx.aswin.boxlore.core.downloads.SmartDownloadCandidateLogic.MixtapeCandidate
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SmartDownloadManager(
    private val context: Context,
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
    private val historyRecommendationSource: HistoryRecommendationSource,
    private val downloadRepository: DownloadRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPrefs: UserPreferencesRepository,
    private val adaptiveScorer: AdaptiveCandidateScorer,
) {

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

    private suspend fun fetchPersonalizedRecommendations(
        subs: List<PodcastEntity>,
        chosenSubsIds: Set<String>,
        targetSize: Int
    ): List<Episode> {
        val chosenRecs = mutableListOf<Episode>()
        if (targetSize > 0) {
            try {
                val historyItems = historyRecommendationSource.getHistoryForRecommendations(15)
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

    private fun checkIsAlreadyDownloadedOrDownloading(episode: Episode, existingDownloads: List<DownloadedEpisodeEntity>): Boolean {
        val isAlreadyDownloaded = existingDownloads.any { it.episodeId == episode.id && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
        val isDownloading = existingDownloads.any { it.episodeId == episode.id && it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING }
        return isAlreadyDownloaded || isDownloading
    }

    private suspend fun recycleOldDownloads(candidateEpisodeIds: Set<String>, existingDownloads: List<DownloadedEpisodeEntity>): Pair<Long, Int> {
        var currentDownloadedBytes = 0L
        var cleanedCount = 0
        for (download in existingDownloads) {
            if (download.isSmartDownloaded) {
                val estSize = SmartDownloadCandidateLogic.estimateDownloadSize(download)
                currentDownloadedBytes += estSize
                
                if (download.episodeId !in candidateEpisodeIds) {
                    Log.d("SmartDownloadManager", "Recycling/cleaning up old smart-downloaded episode: ${download.episodeId}")
                    writeLogToFile(context, "Recycling/deleting old smart-downloaded episode: '${download.episodeTitle}' (ID: ${download.episodeId})")
                    downloadRepository.removeDownload(download.episodeId)
                    currentDownloadedBytes -= estSize
                    cleanedCount++
                }
            }
        }
        return currentDownloadedBytes to cleanedCount
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

            val estimatedSize = SmartDownloadCandidateLogic.estimateEpisodeSize(episode)

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
            val nowMs = System.currentTimeMillis()

            val resolvedSerial = resolveOldestSerialNextEpisodes(subs, allHistory, completedEpisodeIds)

            val historyByEpisode = allHistory.associateBy { it.episodeId }
            val podScoresMap = try {
                adaptiveScorer.scorePodcasts(
                    podcasts = subs.map { it.toScorable() },
                    history = allHistory,
                    objective = RankingObjective.OFFLINE,
                    surface = RankingSurface.DOWNLOADS,
                    includeAutoDownloadBoost = false,
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("SmartDownloadManager", "Adaptive podcast scoring failed", error)
                PodcastScoring.calculateScores(
                    podcasts = subs.map { it.toScorable() },
                    allHistory = allHistory,
                    includeAutoDownloadBoost = false,
                )
            }

            val initialCandidates = SmartDownloadCandidateLogic.generateMixtapeCandidates(
                subs = subs,
                allHistory = allHistory,
                historyByEpisode = historyByEpisode,
                resolvedSerial = resolvedSerial,
                podScoresMap = podScoresMap,
                nowMs = nowMs,
            )
            val adaptiveScores = try {
                adaptiveScorer.scoreEpisodes(
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
                    surface = RankingSurface.DOWNLOADS,
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("SmartDownloadManager", "Adaptive episode scoring failed", error)
                initialCandidates.associate { it.episodeId to it.score }
            }
            val orderedCandidates = initialCandidates
                .map { candidate ->
                    candidate.copy(
                        score = adaptiveScores[candidate.episodeId] ?: candidate.score,
                    )
                }
                .sortedByDescending(MixtapeCandidate::score)

            val maxCount = userPrefs.smartDownloadsMaxEpisodesStream.first()
            val storageBudgetMb = userPrefs.smartDownloadsStorageBudgetStream.first()

            val quotas = SmartDownloadCandidateLogic.computeDownloadQuotas(maxCount)
            val subQuota = quotas.subscriptionQuota
            val recQuota = quotas.recommendationQuota
            val trendQuota = quotas.trendingQuota
            
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
            val (currentDownloadedBytes, cleanedCount) = recycleOldDownloads(candidateEpisodeIds, existingDownloads)

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
            emitSmartDownloadSyncTelemetry(
                requestedCount = combinedEpisodes.size,
                completedCount = countDownloaded,
                cleanedCount = cleanedCount,
                isManual = isManual,
                isForeground = isForeground,
            )
            return true
        } catch (e: Exception) {
            Log.e("SmartDownloadManager", "Error running smart downloads sync", e)
            writeLogToFile(context, "Sync failed with error: ${e.message}")
            return false
        }
    }

    private fun emitSmartDownloadSyncTelemetry(
        requestedCount: Int,
        completedCount: Int,
        cleanedCount: Int,
        isManual: Boolean,
        isForeground: Boolean,
    ) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackSmartDownloadSync(
            requestedCount = requestedCount,
            completedCount = completedCount,
            cleanedCount = cleanedCount,
            trigger = smartDownloadSyncTrigger(isManual, isForeground),
        )
    }

    private fun smartDownloadSyncTrigger(
        isManual: Boolean,
        isForeground: Boolean,
    ): String =
        when {
            isManual -> "manual"
            isForeground -> "foreground"
            else -> "background"
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
