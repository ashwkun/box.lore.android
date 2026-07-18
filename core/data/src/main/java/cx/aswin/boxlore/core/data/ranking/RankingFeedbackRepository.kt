package cx.aswin.boxlore.core.data.ranking

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

data class FeedbackTarget(
    val episodeId: String,
    val podcastId: String,
    val genre: String? = null,
    val source: CandidateSource? = null,
)

class RankingFeedbackRepository private constructor(
    private val adaptiveRankingRepository: AdaptiveRankingRepository?,
) : cx.aswin.boxlore.core.domain.ports.RankingResetPort {
    private val recentActions = ConcurrentHashMap<String, Long>()

    suspend fun recordExposure(exposure: RankingExposure): String {
        return safely("record exposure", "") {
            adaptiveRankingRepository?.recordExposure(exposure).orEmpty()
        }
    }

    suspend fun recordAction(
        target: FeedbackTarget,
        action: RankingAction,
        listenSeconds: Long = 0,
        durationSeconds: Long = 0,
    ) {
        safely("record action", Unit) {
            if (isRecentDuplicate(target.episodeId, action)) {
                LearningEventLog.record { id, ts ->
                    LearningEvent.DuplicateIgnored(
                        id = id,
                        timestamp = ts,
                        action = action,
                        episodeId = target.episodeId,
                    )
                }
                return@safely
            }
            val reward = RankingReward.calculate(
                RankingOutcome(
                    actions = setOf(action),
                    listenSeconds = listenSeconds,
                    durationSeconds = durationSeconds,
                ),
            )
            LearningEventLog.record { id, ts ->
                LearningEvent.ActionReceived(
                    id = id,
                    timestamp = ts,
                    action = action,
                    reward = reward,
                    podcastId = target.podcastId,
                    genre = target.genre,
                    source = target.source?.name,
                    listenSeconds = listenSeconds,
                )
            }
            updateTasteFacets(target, reward)
            if (action in terminalExposureActions) {
                adaptiveRankingRepository?.resolveLatestExposure(
                    episodeId = target.episodeId,
                    reward = reward,
                    listenSeconds = listenSeconds,
                )
            }
        }
    }

    suspend fun recordPlayback(
        target: FeedbackTarget,
        listenSeconds: Long,
        durationSeconds: Long,
        completed: Boolean,
        earlySkip: Boolean,
    ) {
        safely("record playback", Unit) {
            val actions = buildSet {
                if (listenSeconds >= MEANINGFUL_PLAY_SECONDS ||
                    progressRatio(listenSeconds, durationSeconds) >= MEANINGFUL_PROGRESS_RATIO
                ) {
                    add(RankingAction.MEANINGFUL_PLAY)
                }
                if (completed) add(RankingAction.COMPLETE)
                if (earlySkip) add(RankingAction.EARLY_SKIP)
            }
            if (actions.isEmpty()) return@safely
            val reward = RankingReward.calculate(
                RankingOutcome(
                    actions = actions,
                    listenSeconds = listenSeconds,
                    durationSeconds = durationSeconds,
                ),
            )
            LearningEventLog.record { id, ts ->
                val primary = when {
                    RankingAction.COMPLETE in actions -> RankingAction.COMPLETE
                    RankingAction.EARLY_SKIP in actions -> RankingAction.EARLY_SKIP
                    else -> RankingAction.MEANINGFUL_PLAY
                }
                LearningEvent.ActionReceived(
                    id = id,
                    timestamp = ts,
                    action = primary,
                    reward = reward,
                    podcastId = target.podcastId,
                    genre = target.genre,
                    source = target.source?.name,
                    listenSeconds = listenSeconds,
                )
            }
            updateTasteFacets(target, reward)
            adaptiveRankingRepository?.resolveLatestExposure(
                episodeId = target.episodeId,
                reward = reward,
                listenSeconds = listenSeconds,
            )
        }
    }

    override suspend fun reset(): Boolean {
        return safely("reset recommendations", false) {
            adaptiveRankingRepository?.reset()
            recentActions.clear()
            RankingShadowDiagnostics.clear()
            LearningEventLog.clear()
            adaptiveRankingRepository != null
        }
    }

    private suspend fun updateTasteFacets(
        target: FeedbackTarget,
        reward: Double,
    ) {
        val repository = adaptiveRankingRepository ?: return
        repository.updateFacet(PreferenceFacetType.SHOW, target.podcastId, reward)
        // Skip placeholder "Podcast" / unknown labels — only canonical genres learn.
        target.genre?.let { genre ->
            repository.updateFacet(PreferenceFacetType.GENRE, genre, reward)
        }
        target.source?.let { source ->
            repository.updateFacet(
                PreferenceFacetType.SOURCE,
                source.name,
                reward,
            )
        }
    }

    private fun isRecentDuplicate(
        episodeId: String,
        action: RankingAction,
    ): Boolean {
        val now = System.currentTimeMillis()
        val key = "$episodeId:${action.name}"
        val previous = recentActions.put(key, now)
        if (recentActions.size > MAX_RECENT_ACTIONS) {
            recentActions.entries.removeIf { now - it.value > ACTION_DEDUP_WINDOW_MILLIS }
        }
        return previous != null && now - previous < ACTION_DEDUP_WINDOW_MILLIS
    }

    private suspend fun <T> safely(
        operation: String,
        fallback: T,
        block: suspend () -> T,
    ): T {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Failed to $operation", error)
            fallback
        }
    }

    companion object {
        private const val MEANINGFUL_PLAY_SECONDS = 60L
        private const val MEANINGFUL_PROGRESS_RATIO = 0.2
        private const val ACTION_DEDUP_WINDOW_MILLIS = 5_000L
        private const val MAX_RECENT_ACTIONS = 500
        private const val TAG = "RankingFeedback"

        private val terminalExposureActions = setOf(
            RankingAction.MEANINGFUL_PLAY,
            RankingAction.COMPLETE,
            RankingAction.LIKE,
            RankingAction.UNLIKE,
            RankingAction.SUBSCRIBE,
            RankingAction.UNSUBSCRIBE,
            RankingAction.EXPLICIT_QUEUE,
            RankingAction.MANUAL_DOWNLOAD,
            RankingAction.EARLY_SKIP,
            RankingAction.REMOVE_AUTOFILLED,
            RankingAction.MOVE_UP,
            RankingAction.MOVE_DOWN,
            RankingAction.DISMISS,
        )

        @Volatile
        private var instance: RankingFeedbackRepository? = null

        fun getInstance(context: Context): RankingFeedbackRepository {
            return instance ?: synchronized(this) {
                instance ?: RankingFeedbackRepository(
                    runCatching {
                        AdaptiveRankingRepository.getInstance(context.applicationContext)
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to initialize adaptive ranking", error)
                    }.getOrNull(),
                ).also { instance = it }
            }
        }

        fun getIfInitialized(): RankingFeedbackRepository? = instance

        private fun progressRatio(listenSeconds: Long, durationSeconds: Long): Double {
            if (durationSeconds <= 0L) return 0.0
            return listenSeconds.toDouble() / durationSeconds.toDouble()
        }
    }
}
