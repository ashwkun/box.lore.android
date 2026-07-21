package cx.aswin.boxlore.core.ranking

import android.content.Context
import android.util.Log
import cx.aswin.boxlore.core.ranking.database.RankingOutcomeEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

data class FeedbackTarget(
    val episodeId: String,
    val podcastId: String,
    val genre: String? = null,
    val source: CandidateSource? = null,
    val exposureId: String? = null,
)

class RankingFeedbackRepository private constructor(
    private val adaptiveRankingRepository: AdaptiveRankingRepository?,
) : cx.aswin.boxlore.core.domain.ports.RankingResetPort {
    private val recentActions = ConcurrentHashMap<String, Long>()
    private val manualAnchors = ConcurrentHashMap<String, Long>()

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
                appendOutcomeLedger(
                    target = target,
                    action = action,
                    partialReward = reward,
                    listenSeconds = listenSeconds,
                    durationSeconds = durationSeconds,
                )
                settleOrApplyDelta(target, reward, listenSeconds)
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
            val primary = when {
                RankingAction.COMPLETE in actions -> RankingAction.COMPLETE
                RankingAction.EARLY_SKIP in actions -> RankingAction.EARLY_SKIP
                else -> RankingAction.MEANINGFUL_PLAY
            }
            LearningEventLog.record { id, ts ->
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
            appendOutcomeLedger(
                target = target,
                action = primary,
                partialReward = reward,
                listenSeconds = listenSeconds,
                durationSeconds = durationSeconds,
            )
            settleOrApplyDelta(target, reward, listenSeconds)
        }
    }

    /**
     * Hard-hide a show from personalization surfaces and record a strong negative signal
     * against SHOW / SOURCE / GENRE facets. The optional [exposureId] links the exclusion
     * back to the impression that triggered it so telemetry can trace attribution.
     */
    suspend fun hideShow(
        podcastId: String,
        exposureId: String? = null,
        episodeId: String = "",
        genre: String? = null,
        source: CandidateSource? = null,
    ) {
        safely("hide show", Unit) {
            val repo = adaptiveRankingRepository ?: return@safely
            repo.excludeShow(podcastId, reason = "hide_show", sourceExposureId = exposureId)
            val target = FeedbackTarget(
                episodeId = episodeId,
                podcastId = podcastId,
                genre = genre,
                source = source,
                exposureId = exposureId,
            )
            recordExplicitFeedback(target, RankingAction.HIDE_SHOW)
        }
    }

    /**
     * Record an explicit "more like this" boost against the podcast, source, and genre facets.
     */
    suspend fun moreLikeThis(target: FeedbackTarget) {
        safely("more like this", Unit) {
            recordExplicitFeedback(target, RankingAction.MORE_LIKE_THIS)
        }
    }

    /**
     * Record an explicit "not for me" penalty against the podcast, source, and genre facets.
     */
    suspend fun notForMe(target: FeedbackTarget) {
        safely("not for me", Unit) {
            recordExplicitFeedback(target, RankingAction.NOT_FOR_ME)
        }
    }

    /**
     * Anchor a show as an explicit personalization pin. Bounded once per podcast to prevent
     * runaway boosts if the user opens the same anchor sheet repeatedly.
     */
    suspend fun recordManualAnchor(
        podcastId: String,
        genre: String? = null,
    ) {
        safely("record manual anchor", Unit) {
            val normalized = podcastId.trim()
            if (normalized.isEmpty()) return@safely
            val now = System.currentTimeMillis()
            val previous = manualAnchors.put(normalized, now)
            if (previous != null && now - previous < MANUAL_ANCHOR_WINDOW_MILLIS) {
                LearningEventLog.record { id, ts ->
                    LearningEvent.DuplicateIgnored(
                        id = id,
                        timestamp = ts,
                        action = RankingAction.MANUAL_ANCHOR,
                        episodeId = normalized,
                    )
                }
                return@safely
            }
            val repo = adaptiveRankingRepository ?: return@safely
            val reward = RankingReward.partialForAction(RankingAction.MANUAL_ANCHOR)
            repo.updateFacet(PreferenceFacetType.SHOW, normalized, reward, now)
            genre?.let { repo.updateFacet(PreferenceFacetType.GENRE, it, GENRE_CREDIT * reward, now) }
        }
    }

    suspend fun hardExcludedPodcastIds(): Set<String> {
        return safely("hard exclusions", emptySet()) {
            adaptiveRankingRepository?.hardExcludedPodcastIds() ?: emptySet()
        }
    }

    override suspend fun reset(): Boolean {
        return safely("reset recommendations", false) {
            adaptiveRankingRepository?.reset()
            recentActions.clear()
            manualAnchors.clear()
            RankingShadowDiagnostics.clear()
            LearningEventLog.clear()
            adaptiveRankingRepository != null
        }
    }

    private suspend fun recordExplicitFeedback(
        target: FeedbackTarget,
        action: RankingAction,
    ) {
        val reward = RankingReward.partialForAction(action)
        LearningEventLog.record { id, ts ->
            LearningEvent.ActionReceived(
                id = id,
                timestamp = ts,
                action = action,
                reward = reward,
                podcastId = target.podcastId,
                genre = target.genre,
                source = target.source?.name,
                listenSeconds = 0,
            )
        }
        updateTasteFacets(target, reward)
        appendOutcomeLedger(
            target = target,
            action = action,
            partialReward = reward,
            listenSeconds = 0,
            durationSeconds = 0,
        )
        if (target.exposureId != null) {
            settleOrApplyDelta(target, reward, listenSeconds = 0)
        }
    }

    /**
     * Resolve the pending exposure with the given reward when possible; if the exposure
     * has already been settled by an earlier outcome, apply the reward delta on top so
     * later signals nudge the model without double-counting the impression.
     */
    private suspend fun settleOrApplyDelta(
        target: FeedbackTarget,
        reward: Double,
        listenSeconds: Long,
    ) {
        val repo = adaptiveRankingRepository ?: return
        val exposureId = target.exposureId
        if (exposureId != null) {
            val resolved = repo.resolveExposure(exposureId, reward, listenSeconds)
            if (!resolved) {
                repo.applyRewardDelta(exposureId, reward, listenSeconds)
            }
            return
        }
        repo.resolveLatestExposure(
            episodeId = target.episodeId,
            reward = reward,
            listenSeconds = listenSeconds,
        )
    }

    private suspend fun appendOutcomeLedger(
        target: FeedbackTarget,
        action: RankingAction,
        partialReward: Double,
        listenSeconds: Long,
        durationSeconds: Long,
    ) {
        val repo = adaptiveRankingRepository ?: return
        repo.appendOutcome(
            RankingOutcomeEntity(
                outcomeId = UUID.randomUUID().toString(),
                exposureId = target.exposureId,
                episodeId = target.episodeId,
                podcastId = target.podcastId,
                action = action.name,
                partialReward = partialReward.coerceIn(-1.0, 1.0),
                listenSeconds = listenSeconds.coerceAtLeast(0L),
                durationSeconds = durationSeconds.coerceAtLeast(0L),
                recordedAt = System.currentTimeMillis(),
                appliedToModel = target.exposureId != null || target.episodeId.isNotEmpty(),
            ),
        )
    }

    private suspend fun updateTasteFacets(
        target: FeedbackTarget,
        reward: Double,
    ) {
        val repository = adaptiveRankingRepository ?: return
        repository.updateFacet(PreferenceFacetType.SHOW, target.podcastId, SHOW_CREDIT * reward)
        target.genre?.let { genre ->
            repository.updateFacet(PreferenceFacetType.GENRE, genre, GENRE_CREDIT * reward)
        }
        target.source?.let { source ->
            repository.updateFacet(
                PreferenceFacetType.SOURCE,
                source.name,
                SOURCE_CREDIT * reward,
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
        private const val MANUAL_ANCHOR_WINDOW_MILLIS = 24L * 60L * 60L * 1_000L
        private const val MAX_RECENT_ACTIONS = 500
        private const val TAG = "RankingFeedback"

        /** Facet credit shares — SHOW carries full reward; SOURCE / GENRE dampened. */
        private const val SHOW_CREDIT = 1.0
        private const val SOURCE_CREDIT = 0.5
        private const val GENRE_CREDIT = 0.25

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
            RankingAction.MORE_LIKE_THIS,
            RankingAction.NOT_FOR_ME,
            RankingAction.HIDE_SHOW,
            RankingAction.MANUAL_ANCHOR,
        )

        @Volatile
        private var instance: RankingFeedbackRepository? = null

        fun create(adaptiveRankingRepository: AdaptiveRankingRepository?): RankingFeedbackRepository =
            RankingFeedbackRepository(adaptiveRankingRepository)

        fun install(value: RankingFeedbackRepository) {
            instance = value
        }

        /** Prefer AppContainer / SharedAppDependenciesHolder in production. */
        fun getInstance(context: Context): RankingFeedbackRepository {
            return instance ?: synchronized(this) {
                instance ?: create(
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
