package cx.aswin.boxlore.core.playback

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.playback.QueueMath
import cx.aswin.boxlore.core.playback.QueueSkipMemory
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.analytics.PendingEntryPoint
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Playback analytics session: start / enrich / end, heartbeats, and consumed-audio accounting.
 * Extracted from [cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService].
 */
internal class PlaybackTelemetrySession(
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val rankingFeedbackRepository: RankingFeedbackRepository,
    private val queueSkipMemory: QueueSkipMemory,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val findPodcastIdForEpisode: suspend (String) -> String?,
    private val effectiveSkipEndingMs: (Long) -> Long,
    private val markCompletionTelemetryDispatched: () -> Boolean,
    private val playerProvider: () -> Player?,
    private val removeCompletedDownload: (String) -> Unit,
) {
    private val firedHeartbeats = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    var startTimeMs: Long = 0L
        private set
    var episodeId: String? = null
        private set
    var episodeTitle: String? = null
        private set
    var podcastId: String? = null
        private set
    var podcastName: String? = null
        private set
    var podcastGenre: String? = null
        private set
    var totalDurationMs: Long = 0L
    var isRepeating: Boolean = false
        private set
    var entryPoint: String? = null
        private set
    var entryPointContext: Map<String, Any>? = null
        private set
    var contextType: String? = null
        private set
    var contextSourceId: String? = null
        private set

    private var bufferingStartTimeMs: Long = 0L
    private var totalBufferedTimeMs: Long = 0L
    private var consumedAudioMs: Long = 0L
    private var lastPositionMs: Long? = null
    private var lastPositionSampleMs: Long = 0L

    /** Episode paused so a bare remote play() can be attributed as resume. */
    var lastPausedEpisodeId: String? = null

    fun onBufferingStarted() {
        bufferingStartTimeMs = System.currentTimeMillis()
    }

    fun onBufferingEnded() {
        if (bufferingStartTimeMs > 0) {
            val bufferMs = System.currentTimeMillis() - bufferingStartTimeMs
            totalBufferedTimeMs += bufferMs
            bufferingStartTimeMs = 0L
            AnalyticsHelper.trackPlaybackBuffering(
                episodeId = episodeId,
                podcastId = podcastId,
                entryPoint = entryPoint,
                bufferDurationMs = bufferMs,
            )
        }
    }

    fun noteSeekPosition(positionMs: Long) {
        lastPositionMs = positionMs
        lastPositionSampleMs = SystemClock.elapsedRealtime()
    }

    fun start(
        episodeId: String,
        currentItem: MediaItem?,
        fallbackEntryPoint: String? = null,
    ) {
        if (startTimeMs > 0 && this.episodeId == episodeId) return

        end(forceCompleted = false)

        if (this.episodeId != episodeId) {
            firedHeartbeats.clear()
        }

        startTimeMs = System.currentTimeMillis()
        bufferingStartTimeMs = 0L
        totalBufferedTimeMs = 0L
        consumedAudioMs = 0L
        lastPositionMs = playerProvider()?.currentPosition
        lastPositionSampleMs = SystemClock.elapsedRealtime()
        this.episodeId = episodeId

        val title = currentItem?.mediaMetadata?.title?.toString()
        val artist =
            currentItem?.mediaMetadata?.artist?.toString()
                ?: currentItem?.mediaMetadata?.subtitle?.toString()
        val genre = currentItem?.mediaMetadata?.genre?.toString()
        episodeTitle = title
        podcastName = artist
        podcastGenre = genre

        val extras = currentItem?.mediaMetadata?.extras
        val bundleMap = mutableMapOf<String, Any>()

        val pendingEntryPoint = PendingEntryPoint.consume()
        if (pendingEntryPoint != null) {
            entryPoint = pendingEntryPoint["entry_point"] as? String
            val contextMap = pendingEntryPoint.filterKeys { it != "entry_point" }
            entryPointContext = contextMap.ifEmpty { null }
        } else {
            extras?.keySet()?.forEach { key ->
                @Suppress("DEPRECATION")
                val value = extras.get(key)
                if (value != null && key != "entry_point") {
                    bundleMap[key] = value
                }
            }
            entryPoint = extras?.getString("entry_point")
            entryPointContext = if (bundleMap.isNotEmpty()) bundleMap else null
        }

        if (entryPoint == null) {
            entryPoint =
                if (episodeId == lastPausedEpisodeId) {
                    "resume_notification"
                } else {
                    fallbackEntryPoint
                }
        }
        lastPausedEpisodeId = null

        scope.launch {
            enrich(episodeId, currentItem, genre)
        }
    }

    private suspend fun resolvePodcastFromDb(podcastId: String): Pair<String?, String?> =
        try {
            val podcast = database.podcastDao().getPodcast(podcastId)
            if (podcast != null) {
                val genre =
                    if (!podcast.genre.isNullOrBlank() && podcast.genre != "Podcast") {
                        podcast.genre
                    } else {
                        null
                    }
                Pair(podcast.title, genre)
            } else {
                Pair(null, null)
            }
        } catch (_: Exception) {
            Pair(null, null)
        }

    private suspend fun resolvePodcastFromHistory(episodeId: String): String? =
        try {
            val historyItem = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (historyItem != null && !historyItem.podcastName.isNullOrBlank()) {
                historyItem.podcastName
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    private suspend fun resolvePodcastFromNetwork(podcastId: String): String? =
        try {
            podcastRepository.getPodcastDetails(podcastId)?.title
        } catch (_: Exception) {
            null
        }

    private suspend fun resolvePodcastMetadata(
        podcastId: String,
        episodeId: String,
        currentItem: MediaItem?,
        genre: String?,
    ): Pair<String?, String?> {
        val (dbName, dbGenre) = resolvePodcastFromDb(podcastId)
        var resolvedPodcastName = dbName
        var actualGenre = dbGenre ?: genre

        if (resolvedPodcastName.isNullOrBlank()) {
            resolvedPodcastName = resolvePodcastFromHistory(episodeId)
        }
        if (resolvedPodcastName.isNullOrBlank()) {
            resolvedPodcastName = resolvePodcastFromNetwork(podcastId)
        }

        val finalPodcastName =
            resolvedPodcastName
                ?: currentItem?.mediaMetadata?.subtitle?.toString()
                ?: currentItem?.mediaMetadata?.artist?.toString()

        return Pair(finalPodcastName, actualGenre)
    }

    private suspend fun enrich(
        episodeId: String,
        currentItem: MediaItem?,
        genre: String?,
    ) {
        try {
            val queueItem = database.queueDao().getQueueItemByEpisodeId(episodeId)
            if (queueItem != null) {
                contextType = queueItem.contextType
                contextSourceId = queueItem.contextSourceId
            } else {
                contextType = null
                contextSourceId = null
            }
        } catch (_: Exception) {
            contextType = null
            contextSourceId = null
        }

        val resolvedPodcastId = findPodcastIdForEpisode(episodeId)
        podcastId = resolvedPodcastId

        val (resolvedName, resolvedGenre) =
            if (resolvedPodcastId != null) {
                resolvePodcastMetadata(resolvedPodcastId, episodeId, currentItem, genre)
            } else {
                val finalName =
                    currentItem?.mediaMetadata?.subtitle?.toString()
                        ?: currentItem?.mediaMetadata?.artist?.toString()
                Pair(finalName, genre)
            }
        podcastName = resolvedName
        podcastGenre = resolvedGenre

        val history = database.listeningHistoryDao().getHistoryItem(episodeId)
        isRepeating = history?.isCompleted == true

        var durationMs = currentItem?.mediaMetadata?.extras?.getLong("durationMs", 0L) ?: 0L
        val exoDuration =
            withContext(mainDispatcher) {
                playerProvider()?.duration ?: 0L
            }
        if (exoDuration > 0) durationMs = exoDuration
        totalDurationMs = durationMs

        val startPositionMs =
            withContext(mainDispatcher) {
                playerProvider()?.currentPosition ?: 0L
            }
        withContext(mainDispatcher) {
            updateHeartbeatsForPosition(startPositionMs, durationMs)
        }

        val isSubscribed = resolvedPodcastId?.let { subscriptionRepository.isSubscribed(it) } ?: false

        AnalyticsHelper.trackPlaybackStarted(
            podcastId = resolvedPodcastId,
            podcastName = resolvedName,
            podcastGenre = resolvedGenre,
            episodeId = episodeId,
            episodeTitle = currentItem?.mediaMetadata?.title?.toString(),
            startPositionSeconds = startPositionMs / 1000f,
            totalDurationSeconds = durationMs / 1000f,
            isRepeating = isRepeating,
            isSubscribed = isSubscribed,
            entryPoint = entryPoint,
            entryPointContext = entryPointContext,
        )
    }

    fun end(
        forceCompleted: Boolean = false,
        isTransition: Boolean = false,
    ) {
        val currentEpisodeId = episodeId
        if (startTimeMs <= 0 || currentEpisodeId == null) return

        playerProvider()?.let(::updateConsumedAudio)
        val durationPlayedMs = System.currentTimeMillis() - startTimeMs
        val durationPlayedSeconds = durationPlayedMs / 1000f
        val consumedAudioSeconds = consumedAudioMs / 1000f
        val currentPodcastId = podcastId
        val currentPodcastName = podcastName
        val currentPodcastGenre = podcastGenre
        val currentEpisodeTitle = episodeTitle
        val sessionTotalDurationMs = totalDurationMs
        val sessionEntryPoint = entryPoint
        val sessionEntryPointContext = entryPointContext

        var isCompleted = forceCompleted
        if (!isCompleted) {
            try {
                val pos = playerProvider()?.currentPosition ?: 0L
                isCompleted =
                    PlaybackSkipPolicy.shouldCompleteFromProgress(
                        positionMs = pos,
                        durationMs = sessionTotalDurationMs,
                        effectiveSkipEndingMs = effectiveSkipEndingMs(sessionTotalDurationMs),
                    )
            } catch (e: Exception) {
                Log.w(
                    "BoxLorePlaybackService",
                    "Failed to evaluate playback completion; using fallback",
                    e,
                )
            }
        }

        val currentQueueSize =
            try {
                playerProvider()?.mediaItemCount ?: 0
            } catch (_: Exception) {
                0
            }

        if (isCompleted && markCompletionTelemetryDispatched()) {
            if (!firedHeartbeats.contains("percent_100")) {
                firedHeartbeats.add("percent_100")
                AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = currentPodcastId,
                    podcastName = currentPodcastName,
                    episodeId = currentEpisodeId,
                    episodeTitle = currentEpisodeTitle,
                    currentPositionSeconds = sessionTotalDurationMs / 1000f,
                    totalDurationSeconds = sessionTotalDurationMs / 1000f,
                    heartbeatPercentage = 100,
                    heartbeatType = "percent",
                    entryPoint = sessionEntryPoint,
                )
            }

            AnalyticsHelper.trackPlaybackCompleted(
                podcastId = currentPodcastId,
                podcastName = currentPodcastName,
                podcastGenre = currentPodcastGenre,
                episodeId = currentEpisodeId,
                episodeTitle = currentEpisodeTitle,
                totalDurationSeconds = sessionTotalDurationMs / 1000f,
                entryPoint = sessionEntryPoint,
                entryPointContext = sessionEntryPointContext,
            )

            if (currentEpisodeId.isNotEmpty()) {
                scope.launch {
                    try {
                        val shouldDelete = userPreferencesRepository.autoDownloadDeleteCompletedStream.first()
                        if (shouldDelete) {
                            removeCompletedDownload(currentEpisodeId)
                            Log.d("BoxLorePlaybackService", "Auto-deleted completed downloaded episode: $currentEpisodeId")
                        }
                    } catch (e: Exception) {
                        Log.e("BoxLorePlaybackService", "Failed to auto-delete completed download", e)
                    }
                }
            }
        } else if (!isCompleted) {
            val pauseReason = AnalyticsHelper.consumePauseReason()
            AnalyticsHelper.trackPlaybackPaused(
                podcastId = currentPodcastId,
                podcastName = currentPodcastName,
                podcastGenre = currentPodcastGenre,
                episodeId = currentEpisodeId,
                episodeTitle = currentEpisodeTitle,
                durationPlayedSeconds = durationPlayedSeconds,
                totalBufferedTimeSeconds = totalBufferedTimeMs / 1000f,
                totalDurationSeconds = sessionTotalDurationMs / 1000f,
                isCompleted = false,
                entryPoint = sessionEntryPoint,
                entryPointContext = sessionEntryPointContext,
                queueSize = currentQueueSize,
                pauseReason = pauseReason,
            )

            if (isTransition && consumedAudioSeconds <= 30f && contextType == "AUTO_FILL") {
                AnalyticsHelper.trackSmartQueueEpisodeSkipped(
                    episodeId = currentEpisodeId,
                    recommendationSource = contextSourceId ?: "unknown",
                    positionInQueue = 0,
                )
                try {
                    queueSkipMemory.recordSkip(
                        episodeId = currentEpisodeId,
                        podcastId = currentPodcastId,
                        source = contextSourceId,
                    )
                } catch (e: Exception) {
                    Log.e("AutoQueue", "Failed to record skip memory", e)
                }
            }
        }

        val adaptiveSource =
            when (contextType) {
                "AUTO_FILL" -> CandidateSource.SERVER_RECOMMENDATION
                QueueMath.CONTEXT_TYPE_LORE -> CandidateSource.CURATED_INTENT
                else -> null
            }
        val isAdaptiveEarlySkip =
            isTransition &&
                consumedAudioSeconds <= 30f &&
                adaptiveSource != null
        scope.launch {
            rankingFeedbackRepository.recordPlayback(
                target =
                    FeedbackTarget(
                        episodeId = currentEpisodeId,
                        podcastId = currentPodcastId.orEmpty(),
                        genre = currentPodcastGenre,
                        source = adaptiveSource,
                    ),
                listenSeconds = consumedAudioSeconds.toLong().coerceAtLeast(0L),
                durationSeconds = (sessionTotalDurationMs / 1_000L).coerceAtLeast(0L),
                completed = isCompleted,
                earlySkip = isAdaptiveEarlySkip,
            )
        }

        AnalyticsHelper.flush()
        reset()
    }

    private fun reset() {
        startTimeMs = 0L
        bufferingStartTimeMs = 0L
        totalBufferedTimeMs = 0L
        consumedAudioMs = 0L
        lastPositionMs = null
        lastPositionSampleMs = 0L
        episodeId = null
        episodeTitle = null
        podcastId = null
        podcastName = null
        totalDurationMs = 0L
        isRepeating = false
        entryPoint = null
        entryPointContext = null
    }

    fun updateConsumedAudio(player: Player) {
        val now = SystemClock.elapsedRealtime()
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val previousPosition = lastPositionMs
        val previousSample = lastPositionSampleMs
        if (player.isPlaying && previousPosition != null && previousSample > 0L) {
            val positionAdvance = currentPosition - previousPosition
            val elapsed = (now - previousSample).coerceAtLeast(0L)
            val maximumNaturalAdvance =
                (elapsed * player.playbackParameters.speed * 1.5f).toLong() + 1_000L
            if (positionAdvance in 0..maximumNaturalAdvance) {
                consumedAudioMs += positionAdvance
            }
        }
        lastPositionMs = currentPosition
        lastPositionSampleMs = now
    }

    fun dispatchHeartbeatTelemetry(player: ExoPlayer) {
        val episodeId = episodeId ?: return
        if (!player.isPlaying) return

        val currentPosMs = player.currentPosition
        val durationMs = player.duration
        if (durationMs <= 0) return

        val currentPosSec = currentPosMs / 1000f
        val durationSec = durationMs / 1000f
        val percent = (currentPosMs.toFloat() / durationMs.toFloat()) * 100f

        checkPercentHeartbeats(episodeId, currentPosSec, durationSec, percent)
        checkIntervalHeartbeats(episodeId, currentPosSec, durationSec)
    }

    private fun checkPercentHeartbeats(
        episodeId: String,
        currentPosSec: Float,
        durationSec: Float,
        percent: Float,
    ) {
        val percentMilestones = listOf(10, 25, 50, 75, 90)
        for (milestone in percentMilestones) {
            if (percent >= milestone && !firedHeartbeats.contains("percent_$milestone")) {
                firedHeartbeats.add("percent_$milestone")
                AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = podcastId,
                    podcastName = podcastName,
                    episodeId = episodeId,
                    episodeTitle = episodeTitle,
                    currentPositionSeconds = currentPosSec,
                    totalDurationSeconds = durationSec,
                    heartbeatPercentage = milestone,
                    heartbeatType = "percent",
                    entryPoint = entryPoint,
                )
            }
        }
    }

    private fun checkIntervalHeartbeats(
        episodeId: String,
        currentPosSec: Float,
        durationSec: Float,
    ) {
        val fiveMinuteIntervals = (currentPosSec / 300f).toInt()
        if (fiveMinuteIntervals > 0) {
            val milestoneKey = "time_${fiveMinuteIntervals * 5}m"
            if (!firedHeartbeats.contains(milestoneKey)) {
                firedHeartbeats.add(milestoneKey)
                AnalyticsHelper.trackPlaybackHeartbeat(
                    podcastId = podcastId,
                    podcastName = podcastName,
                    episodeId = episodeId,
                    episodeTitle = episodeTitle,
                    currentPositionSeconds = currentPosSec,
                    totalDurationSeconds = durationSec,
                    heartbeatPercentage = 0,
                    heartbeatType = "interval",
                    entryPoint = entryPoint,
                )
            }
        }
    }

    fun updateHeartbeatsForPosition(
        positionMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0) return
        val percent = (positionMs.toFloat() / durationMs.toFloat()) * 100f

        val percentMilestones = listOf(10, 25, 50, 75, 90)
        for (milestone in percentMilestones) {
            if (percent >= milestone) {
                firedHeartbeats.add("percent_$milestone")
            }
        }

        val positionSec = positionMs / 1000f
        val fiveMinuteIntervals = (positionSec / 300f).toInt()
        for (i in 1..fiveMinuteIntervals) {
            firedHeartbeats.add("time_${i * 5}m")
        }
    }

    fun trackManualCompletion(
        episodeId: String,
        totalDurationSeconds: Float,
    ) {
        AnalyticsHelper.trackPlaybackCompleted(
            podcastId = podcastId,
            podcastName = podcastName,
            podcastGenre = podcastGenre,
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            totalDurationSeconds = totalDurationSeconds,
            entryPoint = entryPoint,
            entryPointContext = entryPointContext,
        )
    }

    fun trackPlayerError(error: androidx.media3.common.PlaybackException) {
        AnalyticsHelper.trackPlaybackError(
            errorCode = error.errorCodeName,
            errorMessage = error.message ?: "Unknown",
            podcastId = podcastId,
            episodeId = episodeId,
            podcastName = podcastName,
            episodeTitle = episodeTitle,
        )
    }
}
