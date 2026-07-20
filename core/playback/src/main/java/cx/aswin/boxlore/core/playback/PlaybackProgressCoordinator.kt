package cx.aswin.boxlore.core.playback

import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import cx.aswin.boxlore.core.playback.SleepTimerHolder
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.playback.service.auto.stripEpisodePrefix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Playback progress ticker: periodic save, sleep-timer enforcement, and heartbeat dispatch hooks.
 * Extracted from [cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService].
 */
internal class PlaybackProgressCoordinator(
    private val mainDispatcher: CoroutineDispatcher,
    private val database: BoxLoreDatabase,
    private val mediaSessionProvider: () -> MediaLibrarySession?,
    private val isEffectiveEndLatched: () -> Boolean,
    private val effectiveSkipEndingMs: (Long) -> Long,
    private val updateConsumedAudio: (androidx.media3.common.Player) -> Unit,
    private val dispatchHeartbeatTelemetry: (ExoPlayer) -> Unit,
) {
    var activePlaybackStartTimeMs: Long = 0L
    private var lastProgressAnomalyEpisodeId: String? = null

    /**
     * Periodically saves playback position and dispatches heartbeat telemetry (runs on Dispatchers.Main).
     * Also checks and enforces sleep timer expiration continuously while the foreground service is active.
     */
    suspend fun startPlaybackTicker(player: ExoPlayer) {
        var tickCount = 0
        while (true) {
            delay(1_000)
            updateConsumedAudio(player)

            val sleepEnd = SleepTimerHolder.activeSleepTimerEndMs
            if (sleepEnd != null && System.currentTimeMillis() >= sleepEnd) {
                SleepTimerHolder.activeSleepTimerEndMs = null
                Log.d("BoxCastPlayer", "Foreground Service Sleep Timer: Expired! Pausing player.")
                withContext(mainDispatcher) {
                    if (player.isPlaying) player.pause()
                }
            }

            tickCount++
            if (tickCount % 10 == 0) {
                saveProgressOnce(player)
                dispatchHeartbeatTelemetry(player)
            }
        }
    }

    /** Saves the current playback position to DB once. */
    suspend fun saveProgressOnce(player: ExoPlayer) {
        if (isEffectiveEndLatched()) return
        try {
            val currentItem = withContext(mainDispatcher) { player.currentMediaItem }
            val positionMs = withContext(mainDispatcher) { player.currentPosition }
            val durationMs = withContext(mainDispatcher) { player.duration }
            val episodeId = currentItem?.mediaId?.stripEpisodePrefix() ?: return

            if (durationMs > 0 && positionMs > durationMs && lastProgressAnomalyEpisodeId != episodeId) {
                lastProgressAnomalyEpisodeId = episodeId
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackProgressSyncAnomaly(
                    anomalyType = "position_exceeds_duration",
                    episodeId = episodeId,
                )
            }

            val existing = database.listeningHistoryDao().getHistoryItem(episodeId)
            if (existing != null && positionMs > 0) {
                val hasBeenPlayingFor10s =
                    activePlaybackStartTimeMs > 0 &&
                        (System.currentTimeMillis() - activePlaybackStartTimeMs >= 10_000)
                val lastPlayed = if (hasBeenPlayingFor10s) System.currentTimeMillis() else existing.lastPlayedAt

                val isCompleted = checkIsPlaybackCompleted(positionMs, durationMs)

                if (isCompleted) {
                    val updated =
                        existing.copy(
                            isCompleted = true,
                            progressMs = 0L,
                            durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                            lastPlayedAt = lastPlayed,
                            isDirty = true,
                        )
                    database.listeningHistoryDao().upsert(updated)
                    Log.d("AutoProgress", "Saved completed: $episodeId")
                } else {
                    database.listeningHistoryDao().updateProgress(
                        episodeId = episodeId,
                        progressMs = positionMs,
                        durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                        lastPlayedAt = lastPlayed,
                    )
                    Log.d("AutoProgress", "Saved progress: $episodeId @ ${positionMs / 1000}s / ${durationMs / 1000}s")
                }

                withContext(mainDispatcher) {
                    try {
                        mediaSessionProvider()?.notifyChildrenChanged("home_continue_listening", 0, null)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AutoProgress", "Error saving progress once", e)
        }
    }

    private fun checkIsPlaybackCompleted(
        positionMs: Long,
        durationMs: Long,
    ): Boolean =
        PlaybackSkipPolicy.shouldCompleteFromProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            effectiveSkipEndingMs = effectiveSkipEndingMs(durationMs),
        )
}
