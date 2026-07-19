package cx.aswin.boxlore.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import cx.aswin.boxlore.core.data.PlaybackLifecycleSignals
import cx.aswin.boxlore.core.data.SleepTimerHolder
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.data.database.BoxLoreDatabase
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OUTRO_REARM_HYSTERESIS_MS = 1_000L
private const val EFFECTIVE_END_WATCHDOG_MS = 1_250L
private const val TRUE_END_SEEK_MARGIN_MS = 250L

internal class PlaybackIntroOutroController(
    private val scope: CoroutineScope,
    private val database: BoxLoreDatabase,
    private val globalSkipBeginningMs: () -> Long,
    private val globalSkipEndingMs: () -> Long,
    private val lifecycleEpisodeId: (MediaItem?) -> String?,
    private val findPodcastIdForEpisode: suspend (String) -> String?,
    private val onActiveDurationResolved: (episodeId: String, durationMs: Long) -> Unit,
    private val onNaturalCompletion: (episodeId: String, durationMs: Long) -> Job?,
    private val onClearEndOfEpisodeSleep: () -> Unit,
) {
    data class SeekDiscontinuityResult(
        val isLifecycleSeek: Boolean,
    )

    private var playbackActivationGeneration = 0L
    private var activeLifecycleEpisodeId: String? = null
    private var activeLifecycleMediaItem: MediaItem? = null
    private var activeLifecycleDurationMs = 0L
    private var activationInitialPositionMs = 0L
    private var effectiveSkipBeginningMs = 0L
    private var effectiveSkipEndingMs = 0L
    private var introTargetResolved = false
    private var pendingIntroTargetMs: Long? = null
    private var pendingIntroSeekSource: String? = null
    private var introApplied = false
    private var introCancelledByUser = false
    private var automaticSeekSource: String? = null
    private var outroArmed = false
    private var lastOutroPositionMs = 0L
    private var lastOutroBoundaryMs: Long? = null
    private var effectiveEndLatch = false
    private var claimedCompletionGeneration = -1L
    private var completionTelemetryGeneration = -1L
    private var outroMonitorJob: Job? = null
    private var effectiveEndWatchdogJob: Job? = null
    private var completionPersistenceJob: Job? = null

    val activeEpisodeId: String?
        get() = activeLifecycleEpisodeId

    val activeDurationMs: Long
        get() = activeLifecycleDurationMs

    val isEffectiveEndLatched: Boolean
        get() = effectiveEndLatch

    fun isActiveMediaItem(mediaItem: MediaItem): Boolean =
        lifecycleEpisodeId(mediaItem) == activeLifecycleEpisodeId &&
            mediaItem === activeLifecycleMediaItem

    fun reset(
        mediaItem: MediaItem?,
        initialPositionMs: Long,
    ) {
        playbackActivationGeneration++
        activeLifecycleEpisodeId = lifecycleEpisodeId(mediaItem)
        activeLifecycleMediaItem = mediaItem
        activeLifecycleDurationMs = 0L
        activationInitialPositionMs = initialPositionMs.coerceAtLeast(0L)
        effectiveSkipBeginningMs = 0L
        effectiveSkipEndingMs = 0L
        PlaybackLifecycleSignals.effectiveSkipEndingMs = null
        introTargetResolved = false
        pendingIntroTargetMs = null
        pendingIntroSeekSource = null
        introApplied = false
        introCancelledByUser = false
        automaticSeekSource = null
        outroArmed = false
        lastOutroPositionMs = initialPositionMs.coerceAtLeast(0L)
        lastOutroBoundaryMs = null
        effectiveEndLatch = false
        stopOutroMonitor()
        effectiveEndWatchdogJob?.cancel()
        effectiveEndWatchdogJob = null
    }

    fun onMediaActivated(
        player: ExoPlayer,
        mediaItem: MediaItem?,
    ) {
        reset(mediaItem, player.currentPosition)
        refreshActiveSkipConfiguration(player, preferenceChanged = false)
    }

    fun onSkipPreferencesChanged(player: ExoPlayer) {
        refreshActiveSkipConfiguration(player, preferenceChanged = true)
    }

    fun onReadyOrPlaying(player: ExoPlayer) {
        maybeApplyPendingIntro(player)
        refreshOutroBoundary(player, preferenceChanged = false)
    }

    fun onSeekDiscontinuity(
        newPositionMs: Long,
        durationMs: Long,
        source: String,
    ): SeekDiscontinuityResult {
        val isLifecycleSeek =
            source == automaticSeekSource ||
                source == "resume" ||
                source == "transition" ||
                source == "skip_beginning_auto" ||
                source == "skip_ending_auto"
        automaticSeekSource = null
        if (
            isLifecycleSeek &&
            !introTargetResolved &&
            newPositionMs > 0L
        ) {
            activationInitialPositionMs = newPositionMs
        }
        if (!isLifecycleSeek) {
            introCancelledByUser = true
            pendingIntroTargetMs = null
            pendingIntroSeekSource = null
            updateOutroArmingAfterManualSeek(newPositionMs, durationMs)
        }
        return SeekDiscontinuityResult(isLifecycleSeek = isLifecycleSeek)
    }

    fun markAutomaticSeekSource(source: String) {
        automaticSeekSource = source
    }

    fun maybeApplyPendingIntro(player: ExoPlayer) {
        if (!canApplyPendingIntro(player)) return
        val durationMs = player.duration
        if (durationMs <= 0L || durationMs == C.TIME_UNSET) return
        val targetMs = pendingIntroTargetMs
        val source = pendingIntroSeekSource
        introApplied = true
        pendingIntroTargetMs = null
        pendingIntroSeekSource = null
        if (targetMs == null || targetMs <= 0L) return
        if (
            source == "skip_beginning_auto" &&
            !PlaybackSkipPolicy.hasSafePlayableWindow(
                durationMs = durationMs,
                skipBeginningMs = effectiveSkipBeginningMs,
                skipEndingMs = effectiveSkipEndingMs,
            )
        ) {
            return
        }
        val clampedTargetMs = targetMs.coerceAtMost((durationMs - 1L).coerceAtLeast(0L))
        if (clampedTargetMs <= 0L || player.currentPosition == clampedTargetMs) return
        performAutomaticSeek(player, clampedTargetMs, source ?: "resume")
    }

    private fun canApplyPendingIntro(player: ExoPlayer): Boolean {
        if (introApplied || introCancelledByUser || !introTargetResolved) return false
        return player.playbackState == Player.STATE_READY
    }

    fun startOutroMonitor(player: ExoPlayer) {
        if (outroMonitorJob?.isActive == true) return
        outroMonitorJob =
            scope.launch {
                while (player.isPlaying) {
                    checkOutroCrossing(player)
                    delay(200L)
                }
            }
    }

    fun stopOutroMonitor() {
        outroMonitorJob?.cancel()
        outroMonitorJob = null
    }

    fun onNaturalStateEnded(player: ExoPlayer) {
        val episodeId = activeLifecycleEpisodeId ?: return
        claimNaturalCompletion(episodeId, player.duration)
        if (SleepTimerHolder.sleepAtEndOfEpisode) {
            onClearEndOfEpisodeSleep()
            player.pause()
        } else if (!player.hasNextMediaItem()) {
            player.stop()
            reset(null, 0L)
        }
    }

    fun claimNaturalCompletion(
        episodeId: String,
        durationMs: Long,
    ) {
        if (claimedCompletionGeneration == playbackActivationGeneration) return
        claimedCompletionGeneration = playbackActivationGeneration
        effectiveEndLatch = true
        stopOutroMonitor()
        completionPersistenceJob = onNaturalCompletion(episodeId, durationMs)
    }

    fun observeManualCompletion(episodeId: String) {
        if (episodeId != activeLifecycleEpisodeId) return
        claimedCompletionGeneration = playbackActivationGeneration
        completionTelemetryGeneration = playbackActivationGeneration
        effectiveEndLatch = true
        introCancelledByUser = true
        pendingIntroTargetMs = null
        pendingIntroSeekSource = null
        stopOutroMonitor()
        effectiveEndWatchdogJob?.cancel()
        effectiveEndWatchdogJob = null
    }

    fun effectiveEndingTrimForCompletion(durationMs: Long): Long =
        PlaybackSkipPolicy.effectiveEndingTrimForCompletion(
            durationMs = durationMs,
            skipBeginningMs = effectiveSkipBeginningMs,
            skipEndingMs = effectiveSkipEndingMs,
        )

    fun markCompletionTelemetryDispatched(): Boolean {
        if (completionTelemetryGeneration == playbackActivationGeneration) return false
        completionTelemetryGeneration = playbackActivationGeneration
        return true
    }

    fun trueEndSeekTarget(durationMs: Long): Long = (durationMs - TRUE_END_SEEK_MARGIN_MS).coerceAtLeast(0L)

    private fun refreshActiveSkipConfiguration(
        player: ExoPlayer,
        preferenceChanged: Boolean,
    ) {
        val episodeId = activeLifecycleEpisodeId ?: return
        val generation = playbackActivationGeneration
        scope.launch {
            val (history, effectiveTrim) = resolveActiveSkipConfiguration(episodeId)

            if (
                generation != playbackActivationGeneration ||
                episodeId != activeLifecycleEpisodeId
            ) {
                return@launch
            }

            effectiveSkipBeginningMs = effectiveTrim.skipBeginningMs
            effectiveSkipEndingMs = effectiveTrim.skipEndingMs
            // Duration is required to decide whether trims leave a safe playable window.
            PlaybackLifecycleSignals.effectiveSkipEndingMs = 0L

            if (!introTargetResolved) {
                resolveActiveIntroTarget(history)
            }

            refreshOutroBoundary(player, preferenceChanged)
            maybeApplyPendingIntro(player)
        }
    }

    private suspend fun resolveActiveSkipConfiguration(
        episodeId: String,
    ): Pair<ListeningHistoryEntity?, PlaybackSkipPolicy.EffectiveTrim> {
        val history =
            runCatching {
                database.listeningHistoryDao().getHistoryItem(episodeId)
            }.getOrNull()
        val isBriefing = episodeId.startsWith("briefing_")
        val podcastId =
            history?.podcastId?.takeIf { it.isNotBlank() }
                ?: if (isBriefing) {
                    null
                } else {
                    runCatching { findPodcastIdForEpisode(episodeId) }.getOrNull()
                }
        val podcast =
            podcastId?.let { id ->
                runCatching { database.podcastDao().getPodcast(id) }.getOrNull()
            }
        val effectiveTrim =
            if (isBriefing) {
                PlaybackSkipPolicy.EffectiveTrim(
                    skipBeginningMs = PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS,
                    skipEndingMs = PlaybackSkipPolicy.DEFAULT_SKIP_ENDING_MS,
                )
            } else {
                PlaybackSkipPolicy.resolveEffectiveTrim(
                    globalSkipBeginningMs = globalSkipBeginningMs(),
                    globalSkipEndingMs = globalSkipEndingMs(),
                    podcastSkipBeginningOverrideMs = podcast?.skipBeginningOverrideMs,
                    podcastSkipEndingOverrideMs = podcast?.skipEndingOverrideMs,
                )
            }
        return history to effectiveTrim
    }

    private fun resolveActiveIntroTarget(history: ListeningHistoryEntity?) {
        val explicitStartMs = activationInitialPositionMs.takeIf { it > 0L }
        val initialPosition =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = explicitStartMs,
                savedProgressMs = history?.progressMs ?: 0L,
                isCompleted = history?.isCompleted == true,
                skipBeginningMs = effectiveSkipBeginningMs,
            )
        pendingIntroTargetMs =
            when (initialPosition.reason) {
                PlaybackSkipPolicy.InitialPositionReason.RESUME,
                PlaybackSkipPolicy.InitialPositionReason.SKIP_BEGINNING,
                -> initialPosition.positionMs
                PlaybackSkipPolicy.InitialPositionReason.EXPLICIT,
                PlaybackSkipPolicy.InitialPositionReason.START,
                -> null
            }
        pendingIntroSeekSource =
            when (initialPosition.reason) {
                PlaybackSkipPolicy.InitialPositionReason.RESUME -> "resume"
                PlaybackSkipPolicy.InitialPositionReason.SKIP_BEGINNING -> "skip_beginning_auto"
                PlaybackSkipPolicy.InitialPositionReason.EXPLICIT,
                PlaybackSkipPolicy.InitialPositionReason.START,
                -> null
            }
        introApplied =
            initialPosition.reason == PlaybackSkipPolicy.InitialPositionReason.EXPLICIT
        introTargetResolved = true
    }

    private fun performAutomaticSeek(
        player: ExoPlayer,
        targetMs: Long,
        source: String,
    ) {
        automaticSeekSource = source
        AnalyticsHelper.setSeekSource(source)
        player.seekTo(targetMs)
    }

    private fun refreshOutroBoundary(
        player: ExoPlayer,
        preferenceChanged: Boolean,
    ) {
        val durationMs = player.duration
        activeLifecycleDurationMs = durationMs.takeIf {
            it > 0L && it != C.TIME_UNSET
        } ?: 0L
        val episodeId = activeLifecycleEpisodeId
        if (episodeId != null && activeLifecycleDurationMs > 0L) {
            onActiveDurationResolved(episodeId, activeLifecycleDurationMs)
        }
        PlaybackLifecycleSignals.effectiveSkipEndingMs =
            effectiveEndingTrimForCompletion(activeLifecycleDurationMs)
        val oldBoundaryMs = lastOutroBoundaryMs
        val newBoundaryMs = calculateOutroBoundary(activeLifecycleDurationMs)
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        lastOutroBoundaryMs = newBoundaryMs

        if (newBoundaryMs == null) {
            outroArmed = false
            lastOutroPositionMs = positionMs
            return
        }

        val boundaryMoved = oldBoundaryMs != null && oldBoundaryMs != newBoundaryMs
        if ((preferenceChanged || boundaryMoved) && positionMs >= newBoundaryMs) {
            // A preference/duration update must not manufacture a crossing behind the playhead.
            outroArmed = false
        } else if (oldBoundaryMs == null) {
            outroArmed = positionMs < newBoundaryMs
        }
        lastOutroPositionMs = positionMs
    }

    private fun calculateOutroBoundary(durationMs: Long): Long? {
        if (
            !PlaybackSkipPolicy.hasSafePlayableWindow(
                durationMs = durationMs,
                skipBeginningMs = effectiveSkipBeginningMs,
                skipEndingMs = effectiveSkipEndingMs,
            )
        ) {
            return null
        }
        return PlaybackSkipPolicy.outroBoundaryMs(durationMs, effectiveSkipEndingMs)
    }

    private fun updateOutroArmingAfterManualSeek(
        positionMs: Long,
        durationMs: Long,
    ) {
        activeLifecycleDurationMs = durationMs.takeIf {
            it > 0L && it != C.TIME_UNSET
        } ?: 0L
        val boundaryMs = calculateOutroBoundary(activeLifecycleDurationMs)
        lastOutroBoundaryMs = boundaryMs
        outroArmed = boundaryMs != null &&
            positionMs < boundaryMs - OUTRO_REARM_HYSTERESIS_MS
        lastOutroPositionMs = positionMs
    }

    private fun checkOutroCrossing(player: ExoPlayer) {
        if (effectiveEndLatch) return
        val durationMs = player.duration
        if (durationMs != activeLifecycleDurationMs) {
            refreshOutroBoundary(player, preferenceChanged = false)
            return
        }
        val boundaryMs = calculateOutroBoundary(durationMs)
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        if (PlaybackSkipPolicy.isNaturalOutroCrossing(
                previousPositionMs = lastOutroPositionMs,
                currentPositionMs = positionMs,
                durationMs = durationMs,
                skipBeginningMs = effectiveSkipBeginningMs,
                skipEndingMs = effectiveSkipEndingMs,
                armed = outroArmed && boundaryMs != null,
                isPlaying = player.isPlaying,
            )
        ) {
            finishAtEffectiveEnd(player)
            return
        }
        lastOutroPositionMs = positionMs
    }

    private fun finishAtEffectiveEnd(player: ExoPlayer) {
        if (effectiveEndLatch) return
        effectiveEndLatch = true
        stopOutroMonitor()
        val episodeId = activeLifecycleEpisodeId ?: return
        claimNaturalCompletion(episodeId, player.duration)

        if (SleepTimerHolder.sleepAtEndOfEpisode) {
            onClearEndOfEpisodeSleep()
            player.pause()
            return
        }

        player.playWhenReady = true
        performAutomaticSeek(player, trueEndSeekTarget(player.duration), "skip_ending_auto")
        val generation = playbackActivationGeneration
        effectiveEndWatchdogJob?.cancel()
        effectiveEndWatchdogJob =
            scope.launch {
                delay(EFFECTIVE_END_WATCHDOG_MS)
                if (
                    generation != playbackActivationGeneration ||
                    episodeId != lifecycleEpisodeId(player.currentMediaItem)
                ) {
                    return@launch
                }
                completionPersistenceJob?.join()
                if (player.hasNextMediaItem()) {
                    PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId = episodeId
                    player.seekToNextMediaItem()
                    scope.launch {
                        delay(2_000L)
                        if (PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId == episodeId) {
                            PlaybackLifecycleSignals.serviceOwnedNaturalAdvanceEpisodeId = null
                        }
                    }
                } else {
                    player.stop()
                    reset(null, 0L)
                }
            }
    }
}
