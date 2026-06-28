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
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.flow.first
import java.io.File

private fun PodcastEntity.toPodcast(): Podcast {
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
        autoDownloadEnabled = this.autoDownloadEnabled
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

    private data class MixtapeCandidate(
        val episodeId: String,
        val score: Double,
        val isProgress: Boolean,
        val podcast: Podcast,
        val episode: Episode,
        val progressMs: Long = 0L,
        val durationMs: Long = 0L
    )

    suspend fun performSync(isManual: Boolean = false, isForeground: Boolean = false): Boolean {
        Log.d("SmartDownloadManager", "Starting smart downloads sync. isManual=$isManual, isForeground=$isForeground")
        writeLogToFile(context, "Starting sync. isManual=$isManual, isForeground=$isForeground")
        
        // 1. Verify enabled status
        val isEnabled = userPrefs.smartDownloadsEnabledStream.first()
        if (!isEnabled && !isManual && !isForeground) {
            Log.d("SmartDownloadManager", "Smart downloads disabled. Sync skipped.")
            writeLogToFile(context, "Sync skipped: Smart downloads is disabled.")
            return false
        }

        // 2. Network Constraints check
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

        // 3. Battery Constraints check
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

        try {
            // 4. Gather Subscriptions and listening history
            var subs = database.podcastDao().getSubscribedPodcastsList()
            
            if (subs.isEmpty()) {
                Log.d("SmartDownloadManager", "No subscribed podcasts found. Sync skipped.")
                return false
            }

            // Bulk sync latest episodes from the server to get fresh metadata
            try {
                val feedIds = subs.map { it.podcastId }
                val freshLatestEpisodes = podcastRepository.syncSubscriptions(feedIds)
                for ((podId, ep) in freshLatestEpisodes) {
                    subscriptionRepository.updateLatestEpisode(podId, ep)
                }
                // Reload subs from database to use updated latestEpisode references
                subs = database.podcastDao().getSubscribedPodcastsList()
            } catch (e: Exception) {
                Log.e("SmartDownloadManager", "Failed to bulk sync subscription metadata", e)
            }

            val allHistory = database.listeningHistoryDao().getRecentHistoryList(limit = 200)
            val completedEpisodeIds = database.listeningHistoryDao().getCompletedEpisodeIds()

            // Resolve serial podcasts (Preferred sort oldest)
            val completedEpIdsForResolve = allHistory.filter { it.isCompleted }.map { it.episodeId }.toSet() + completedEpisodeIds
            val inProgressEpIdsForResolve = allHistory.filter { !it.isCompleted && it.progressMs > 0L }.map { it.episodeId }.toSet()
            val resolvedSerial = mutableMapOf<String, Episode>()
            
            for (pod in subs) {
                if ((pod.preferredSort ?: "newest") == "oldest") {
                    try {
                        val ongoingId = allHistory.filter { h -> h.podcastId == pod.podcastId && !h.isCompleted && h.progressMs > 0L }.maxByOrNull { it.lastPlayedAt }?.episodeId
                        val lastCompletedId = allHistory.filter { h -> h.podcastId == pod.podcastId && h.isCompleted }.maxByOrNull { it.lastPlayedAt }?.episodeId
                        
                        val page = podcastRepository.getEpisodesPaginated(pod.podcastId, limit = 200, offset = 0, sort = "oldest")
                        val allEpisodes = page.episodes
                        
                        val nextEp = when {
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
                        
                        if (nextEp != null) {
                            resolvedSerial[pod.podcastId] = nextEp
                        }
                    } catch (e: Exception) {
                        Log.e("SmartDownloadManager", "Failed to resolve oldest serial next episode for pod ${pod.podcastId}", e)
                    }
                }
            }

            // 5. Run the Mixtape scoring algorithm
            // Group/Index listening history for O(N + M) efficiency
            val historyByPodcast = allHistory.groupBy { it.podcastId }
            val historyByEpisode = allHistory.associateBy { it.episodeId }
            val subsMap = subs.associateBy { it.podcastId }

            val podScoresMap = PodcastScoring.calculateScores(
                podcasts = subs.map { it.toScorable() },
                allHistory = allHistory,
                includeAutoDownloadBoost = false // Disable autoDownloadBoost as per original logic
            )

            val subIds = subs.map { it.podcastId }.toSet()
            
            // A. In-Progress candidates
            val inProgressCandidates = allHistory.filter { history ->
                history.podcastId in subIds && !history.isCompleted && history.progressMs > 0L
            }.groupBy { it.podcastId }
             .mapValues { (_, eps) -> eps.maxByOrNull { it.lastPlayedAt } }
             .values.filterNotNull()

            val inProgressMixtapeCandidates = inProgressCandidates.mapNotNull { history ->
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
                    podcast = parentPod.toPodcast(),
                    episode = inProgressEpisode,
                    progressMs = history.progressMs,
                    durationMs = history.durationMs
                )
            }

            // B. Unplayed Drops Candidates
            val unplayedDropsCandidates = subs.mapNotNull { pod ->
                val sort = pod.preferredSort ?: "newest"
                if (sort == "oldest") {
                    val resolved = resolvedSerial[pod.podcastId]
                    if (resolved != null) {
                        pod to resolved.copy(podcastTitle = pod.title, podcastId = pod.podcastId)
                    } else {
                        val latestEp = pod.latestEpisode ?: return@mapNotNull null
                        val history = historyByEpisode[latestEp.id]
                        val isUnplayed = history == null || (history.progressMs == 0L && !history.isCompleted)
                        if (isUnplayed) pod to latestEp.copy(podcastTitle = pod.title, podcastId = pod.podcastId) else null
                    }
                } else {
                    val latestEp = pod.latestEpisode ?: return@mapNotNull null
                    val history = historyByEpisode[latestEp.id]
                    val isUnplayed = history == null || (history.progressMs == 0L && !history.isCompleted)
                    if (isUnplayed) {
                        pod to latestEp.copy(podcastTitle = pod.title, podcastId = pod.podcastId)
                    } else null
                }
            }

            val unplayedDropsMixtapeCandidates = unplayedDropsCandidates.map { (pod, ep) ->
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
                    podcast = pod.toPodcast(),
                    episode = ep
                )
            }

            // C. Combine & Deduplicate
            val allMixtapeCandidates = (inProgressMixtapeCandidates + unplayedDropsMixtapeCandidates)
                .sortedByDescending { it.score }

            val deduplicatedCandidates = mutableListOf<MixtapeCandidate>()
            val seenEpisodeIds = mutableSetOf<String>()
            val podcastSlots = mutableMapOf<String, MutableSet<Boolean>>()
            
            for (cand in allMixtapeCandidates) {
                if (cand.episodeId in seenEpisodeIds) continue
                val podId = cand.podcast.id
                val slots = podcastSlots.getOrPut(podId) { mutableSetOf() }
                if (cand.isProgress !in slots) {
                    slots.add(cand.isProgress)
                    seenEpisodeIds.add(cand.episodeId)
                    deduplicatedCandidates.add(cand)
                } else if (slots.size < 2) {
                    val isNewTagged = !cand.isProgress && cand.episode.publishedDate > (cand.podcast.subscribedAt / 1000L)
                    if (isNewTagged && slots.contains(true)) {
                        slots.add(cand.isProgress)
                        seenEpisodeIds.add(cand.episodeId)
                        deduplicatedCandidates.add(cand)
                    } else if (cand.isProgress && slots.contains(false)) {
                        slots.add(cand.isProgress)
                        seenEpisodeIds.add(cand.episodeId)
                        deduplicatedCandidates.add(cand)
                    }
                }
            }

            // Re-order
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

            // 6. Calculate Quotas
            val maxCount = userPrefs.smartDownloadsMaxEpisodesStream.first()
            val storageBudgetMb = userPrefs.smartDownloadsStorageBudgetStream.first()

            val subQuota = (maxCount * 0.7).toInt().coerceAtLeast(1)
            val recQuota = (maxCount * 0.2).toInt().coerceAtLeast(1)
            val trendQuota = (maxCount - subQuota - recQuota).coerceAtLeast(1)
            
            writeLogToFile(context, "Calculated quotas - Subscriptions: $subQuota, Recommendations: $recQuota, Trending: $trendQuota")

            // Partition subscriptions: in-progress first, then unplayed
            val inProgressSubs = orderedCandidates.filter { it.isProgress }
            val unplayedSubs = orderedCandidates.filter { !it.isProgress }
            
            val chosenSubsCandidates = (inProgressSubs + unplayedSubs).take(subQuota)
            val chosenSubs = chosenSubsCandidates.map { it.episode }
            val subOverflow = (subQuota - chosenSubs.size).coerceAtLeast(0)
            
            writeLogToFile(context, "Selected ${chosenSubs.size} subscription episodes. Overflow: $subOverflow")

            // Fetch personalized recommendations
            val targetRecSize = recQuota + subOverflow
            val chosenRecs = mutableListOf<Episode>()
            if (targetRecSize > 0) {
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
                    
                    // Filter recommendations
                    val existingDownloads = database.downloadedEpisodeDao().getAllDownloadsSync()
                    val completedEpisodeIds = database.listeningHistoryDao().getCompletedEpisodeIds().toSet()
                    val chosenSubsIds = chosenSubs.map { it.id }.toSet()
                    
                    val filteredRecs = recs.filter { ep ->
                        ep.id !in chosenSubsIds &&
                        ep.id !in completedEpisodeIds &&
                        existingDownloads.none { it.episodeId == ep.id }
                    }.distinctBy { it.id }
                    
                    chosenRecs.addAll(filteredRecs.take(targetRecSize))
                    writeLogToFile(context, "Fetched ${recs.size} recommendations from endpoint. Selected ${chosenRecs.size} after filtering.")
                } catch (e: Exception) {
                    Log.e("SmartDownloadManager", "Failed to fetch personalized recommendations for sync", e)
                    writeLogToFile(context, "Failed to fetch personalized recommendations: ${e.message}")
                }
            }
            
            val recOverflow = (targetRecSize - chosenRecs.size).coerceAtLeast(0)

            // Fetch trending podcasts
            val targetTrendSize = trendQuota + recOverflow
            val chosenTrends = mutableListOf<Episode>()
            if (targetTrendSize > 0) {
                try {
                    val region = userPrefs.regionStream.first().takeIf { it.isNotBlank() } ?: "us"
                    val trendingPods = podcastRepository.getTrendingPodcasts(country = region, limit = 30)
                    
                    val trendingEpisodes = trendingPods.mapNotNull { it.latestEpisode }
                    
                    // Filter trending
                    val existingDownloads = database.downloadedEpisodeDao().getAllDownloadsSync()
                    val completedEpisodeIds = database.listeningHistoryDao().getCompletedEpisodeIds().toSet()
                    val chosenSubsIds = chosenSubs.map { it.id }.toSet()
                    val chosenRecsIds = chosenRecs.map { it.id }.toSet()
                    
                    val filteredTrends = trendingEpisodes.filter { ep ->
                        ep.id !in chosenSubsIds &&
                        ep.id !in chosenRecsIds &&
                        ep.id !in completedEpisodeIds &&
                        existingDownloads.none { it.episodeId == ep.id }
                    }.distinctBy { it.id }
                    
                    chosenTrends.addAll(filteredTrends.take(targetTrendSize))
                    writeLogToFile(context, "Fetched ${trendingPods.size} trending podcasts from endpoint. Selected ${chosenTrends.size} episodes after filtering.")
                } catch (e: Exception) {
                    Log.e("SmartDownloadManager", "Failed to fetch trending podcasts for sync", e)
                    writeLogToFile(context, "Failed to fetch trending: ${e.message}")
                }
            }
            
            // Combine them in priority order
            val inProgressEpisodes = chosenSubsCandidates.filter { it.isProgress }.map { it.episode }
            val otherSubsEpisodes = chosenSubsCandidates.filter { !it.isProgress }.map { it.episode }
            
            val combinedEpisodes = (inProgressEpisodes + otherSubsEpisodes + chosenRecs + chosenTrends).distinctBy { it.id }
            val candidateEpisodeIds = combinedEpisodes.map { it.id }.toSet()
            
            writeLogToFile(context, "Combined download candidates list size: ${combinedEpisodes.size}. Target episode IDs: $candidateEpisodeIds")

            // 7. Cleanup/Recycle smart-downloaded episodes no longer in the candidates list
            val existingDownloads = database.downloadedEpisodeDao().getAllDownloadsSync()
            var currentDownloadedBytes = 0L

            for (download in existingDownloads) {
                if (download.isSmartDownloaded) {
                    if (download.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
                        currentDownloadedBytes += download.sizeBytes
                    } else if (download.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) {
                        // Estimate active in-progress size from duration or fallback to 50MB
                        val durSec = download.durationMs / 1000L
                        val estSize = if (durSec > 0) durSec * 12000L else 50L * 1024 * 1024
                        currentDownloadedBytes += estSize
                    }
                }
                
                // If it is marked as smart-downloaded and NOT in the active top candidates list, delete it!
                if (download.isSmartDownloaded && download.episodeId !in candidateEpisodeIds) {
                    Log.d("SmartDownloadManager", "Recycling/cleaning up old smart-downloaded episode: ${download.episodeId}")
                    writeLogToFile(context, "Recycling/deleting old smart-downloaded episode: '${download.episodeTitle}' (ID: ${download.episodeId})")
                    downloadRepository.removeDownload(download.episodeId)
                    if (download.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
                        currentDownloadedBytes -= download.sizeBytes
                    } else if (download.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) {
                        val durSec = download.durationMs / 1000L
                        val estSize = if (durSec > 0) durSec * 12000L else 50L * 1024 * 1024
                        currentDownloadedBytes -= estSize
                    }
                }
            }

            // 8. Download the candidate list (First-Limit-Hit Rule)
            // Count any smart downloads that are completed or active
            var countDownloaded = existingDownloads.count { 
                (it.status == DownloadedEpisodeEntity.STATUS_COMPLETED || it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) && 
                it.isSmartDownloaded && 
                it.episodeId in candidateEpisodeIds 
            }
            
            writeLogToFile(context, "Starting download loop. Current active/queued smart downloads count: $countDownloaded / $maxCount. Current size tally: ${currentDownloadedBytes / (1024 * 1024)} MB / $storageBudgetMb MB limit.")

            for (episode in combinedEpisodes) {
                val isAlreadyDownloaded = existingDownloads.any { it.episodeId == episode.id && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
                val isDownloading = existingDownloads.any { it.episodeId == episode.id && it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING }
                
                if (isAlreadyDownloaded || isDownloading) {
                    Log.d("SmartDownloadManager", "Episode ${episode.title} already downloaded or downloading. Skipping.")
                    continue
                }

                // Check count constraint
                if (countDownloaded >= maxCount) {
                    Log.d("SmartDownloadManager", "Hit max count limit ($maxCount episodes). Halting downloads.")
                    writeLogToFile(context, "Hit max count limit ($maxCount episodes). Halting downloads.")
                    break
                }

                // Calculate duration-based estimated size (approx 96kbps -> 12KB/s -> 12000L bytes per second)
                val estimatedSize = if (episode.duration > 0) {
                    episode.duration.toLong() * 12000L
                } else {
                    50L * 1024 * 1024 // Fallback 50MB
                }

                // Check storage budget constraint BEFORE triggering the download (proactive check!)
                if (storageBudgetMb > 0) {
                    val budgetBytes = storageBudgetMb * 1024 * 1024L
                    if (currentDownloadedBytes + estimatedSize > budgetBytes) {
                        val estMb = estimatedSize / (1024 * 1024)
                        val currMb = currentDownloadedBytes / (1024 * 1024)
                        Log.d("SmartDownloadManager", "Hit storage budget limit ($storageBudgetMb MB). Adding '${episode.title}' (Est: $estMb MB) would exceed budget (Current: $currMb MB). Halting downloads.")
                        writeLogToFile(context, "Adding '${episode.title}' (Est: $estMb MB) would exceed storage budget ($storageBudgetMb MB). Halting downloads.")
                        break
                    }
                }

                val parentPod = subs.find { it.podcastId == episode.podcastId }?.toPodcast() ?: Podcast(
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

            // Update last sync time
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
