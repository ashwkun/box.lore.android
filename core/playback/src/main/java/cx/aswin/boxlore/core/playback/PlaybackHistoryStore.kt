package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.withTransaction
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningInsightsDao
import cx.aswin.boxlore.core.database.ListeningRollupEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.ListeningCompletionLogic
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.core.model.ListeningHistoryRemoval
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningPeriod
import cx.aswin.boxlore.core.model.ListeningRollupSnapshot
import cx.aswin.boxlore.core.model.ListeningSessionSnapshot
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Listening history / like / completion store for [cx.aswin.boxlore.core.playback.PlaybackRepository].
 */
internal class PlaybackHistoryStore(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playerState: StateFlow<PlayerState>,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val listeningInsightsDao: ListeningInsightsDao,
    private val podcastRepository: PodcastRepository,
    private val rankingFeedbackRepository: RankingFeedbackRepository,
) {
    private var likeStateObserverJob: Job? = null

    fun monitorLikeState() {
        scope.launch {
            playerState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collect { episodeId ->
                    likeStateObserverJob?.cancel()
                    if (episodeId != null) {
                        likeStateObserverJob =
                            launch {
                                listeningHistoryDao.getHistoryItemFlow(episodeId).collect { history ->
                                    if (history == null) {
                                        playerStateFlow.value = playerStateFlow.value.copy(isLiked = false, isCompleted = false)
                                    } else {
                                        if (playerStateFlow.value.isLiked != history.isLiked ||
                                            playerStateFlow.value.isCompleted != history.isCompleted
                                        ) {
                                            playerStateFlow.value =
                                                playerStateFlow.value.copy(
                                                    isLiked = history.isLiked,
                                                    isCompleted = history.isCompleted,
                                                )
                                        }
                                    }
                                }
                            }
                    }
                }
        }
    }

    val lastPlayedSession: Flow<PlaybackSession?> =
        listeningHistoryDao
            .getResumeItems()
            .map { historyList ->
                val latest = historyList.firstOrNull()
                if (latest != null) {
                    PlaybackSessionMapping.fromHistoryEntity(latest)
                } else {
                    null
                }
            }

    val resumeSessions: Flow<List<PlaybackSession>> =
        listeningHistoryDao
            .getResumeItems()
            .map { historyList ->
                historyList.map { entity ->
                    PlaybackSessionMapping.fromHistoryEntity(entity)
                }
            }

    fun getAllHistory(): Flow<List<ListeningHistoryEntity>> =
        listeningHistoryDao.getAllHistory()

    fun observeHistoryTimeline(): Flow<List<ListeningHistoryItem>> =
        listeningHistoryDao.getAllHistory().map { entities ->
            entities
                .filter { !it.isManualCompletion && !it.isBulkCompletion }
                .map { it.toHistoryItem() }
        }

    fun observeInsights(period: ListeningPeriod): Flow<ListeningInsightSummary> =
        combine(
            listeningInsightsDao.observeAllSessions(),
            listeningInsightsDao.observeAllRollups(),
            listeningHistoryDao.getAllHistory(),
        ) { sessions, rollups, history ->
            val timeline =
                history.filter { !it.isManualCompletion && !it.isBulkCompletion }
            val completedCount =
                timeline.count {
                    ListeningCompletionLogic.isCompleted(it.isCompleted, it.progressMs, it.durationMs)
                }
            val inProgressCount =
                timeline.count {
                    ListeningCompletionLogic.isInProgress(it.isCompleted, it.progressMs, it.durationMs)
                }
            val likedCount = timeline.count { it.isLiked }
            val podcastMeta =
                timeline
                    .groupBy { it.podcastId }
                    .mapValues { (_, rows) ->
                        val first = rows.first()
                        ListeningInsightsLogic.PodcastMeta(
                            name = first.podcastName,
                            imageUrl = first.podcastImageUrl ?: first.episodeImageUrl,
                        )
                    }
            val historyRows =
                timeline.map {
                    ListeningInsightsLogic.HistoryActivityRow(
                        podcastId = it.podcastId,
                        podcastName = it.podcastName,
                        podcastImageUrl = it.podcastImageUrl ?: it.episodeImageUrl,
                        progressMs = it.progressMs,
                        durationMs = it.durationMs,
                        isCompletedFlag = it.isCompleted,
                        lastPlayedAt = it.lastPlayedAt,
                    )
                }
            val trackingSince =
                listOfNotNull(
                    sessions.minOfOrNull { it.endedAt },
                    rollups.minOfOrNull { it.lastListenedAt },
                ).minOrNull()
            ListeningInsightsLogic.summarize(
                ListeningInsightsLogic.SummarizeInput(
                    period = period,
                    sessions = sessions,
                    rollups = rollups,
                    historyRows = historyRows,
                    historyCompleted = completedCount,
                    historyInProgress = inProgressCount,
                    historyLiked = likedCount,
                    podcastMetaById = podcastMeta,
                    today = LocalDate.now(),
                    trackingSinceEpochMs = trackingSince,
                ),
            )
        }

    val likedEpisodes: Flow<List<ListeningHistoryEntity>> = listeningHistoryDao.getLikedEpisodes()

    val completedEpisodeIds: Flow<Set<String>> =
        listeningHistoryDao
            .getCompletedEpisodeIdsFlow()
            .map { it.toSet() }

    suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) {
        listeningHistoryDao.upsert(entity)
    }

    suspend fun recordListeningSession(session: ListeningSessionEntity) {
        ListeningSessionRecordLogic.persistSessionAndRollUp(
            dao = listeningInsightsDao,
            session = session,
        )
    }

    suspend fun maintainListeningAnalytics() {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        listeningInsightsDao.rollUpEligibleSessions(
            cutoffEndedAtExclusive =
                ListeningSessionRecordLogic.retentionCutoffEndedAtExclusive(nowMs, zone),
            todayLocalDay = today.toEpochDay(),
        )
    }

    suspend fun removeHistoryItem(episodeId: String): ListeningHistoryRemoval? {
        val existing = listeningHistoryDao.getHistoryItem(episodeId) ?: return null
        val database = BoxLoreDatabase.getDatabase(context)
        return database.withTransaction {
            val sessions = listeningInsightsDao.getSessionsForEpisode(episodeId)
            val rollups = listeningInsightsDao.getRollupsForEpisode(episodeId)
            val removal =
                ListeningHistoryRemoval(
                    item = existing.toHistoryItem(),
                    sessions = sessions.map { it.toSnapshot() },
                    rollups = rollups.map { it.toSnapshot() },
                )
            listeningHistoryDao.delete(episodeId)
            listeningInsightsDao.deleteEpisodeAnalytics(episodeId)
            removal
        }
    }

    suspend fun restoreHistoryRemoval(removal: ListeningHistoryRemoval) {
        val item = removal.item
        val database = BoxLoreDatabase.getDatabase(context)
        database.withTransaction {
            listeningHistoryDao.upsert(item.toEntity())
            if (removal.sessions.isNotEmpty()) {
                listeningInsightsDao.upsertSessions(removal.sessions.map { it.toEntity() })
            }
            if (removal.rollups.isNotEmpty()) {
                listeningInsightsDao.upsertRollups(removal.rollups.map { it.toEntity() })
            }
        }
    }

    suspend fun clearHistory() {
        val database = BoxLoreDatabase.getDatabase(context)
        database.withTransaction {
            listeningHistoryDao.deleteAll()
            listeningInsightsDao.clearAllAnalytics()
        }
        rankingFeedbackRepository.reset()
    }

    suspend fun toggleLike(
        episode: Episode,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) {
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val newStatus = !(existing?.isLiked ?: false)

        if (playerStateFlow.value.currentEpisode?.id == episode.id) {
            playerStateFlow.value = playerStateFlow.value.copy(isLiked = newStatus)
        }

        if (existing != null) {
            listeningHistoryDao.setLikeStatus(episode.id, newStatus)
        } else {
            val entity =
                ListeningHistoryEntity(
                    episodeId = episode.id,
                    podcastId = podcastId,
                    episodeTitle = episode.title,
                    episodeImageUrl = episode.imageUrl,
                    podcastImageUrl = podcastImageUrl,
                    episodeAudioUrl = episode.audioUrl,
                    podcastName = podcastTitle,
                    progressMs = 0L,
                    durationMs = episode.duration * 1000L,
                    isCompleted = false,
                    isLiked = newStatus,
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    enclosureType = episode.enclosureType,
                    episodeDescription = episode.description,
                )
            listeningHistoryDao.upsert(entity)
        }
        rankingFeedbackRepository.recordAction(
            target =
                FeedbackTarget(
                    episodeId = episode.id,
                    podcastId = podcastId,
                    genre = episode.podcastGenre,
                ),
            action = if (newStatus) RankingAction.LIKE else RankingAction.UNLIKE,
        )
    }

    suspend fun toggleCompletion(
        episode: Episode,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) {
        val existing = listeningHistoryDao.getHistoryItem(episode.id)
        val newStatus = !(existing?.isCompleted ?: false)

        if (existing != null) {
            val updated =
                existing.copy(
                    isCompleted = newStatus,
                    isManualCompletion = newStatus,
                    progressMs = 0L,
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    episodeDescription = existing.episodeDescription ?: episode.description,
                )
            listeningHistoryDao.upsert(updated)
        } else {
            val entity =
                ListeningHistoryEntity(
                    episodeId = episode.id,
                    podcastId = podcastId,
                    episodeTitle = episode.title,
                    episodeImageUrl = episode.imageUrl,
                    podcastImageUrl = podcastImageUrl,
                    episodeAudioUrl = episode.audioUrl,
                    podcastName = podcastTitle,
                    progressMs = 0L,
                    durationMs = episode.duration * 1000L,
                    isCompleted = newStatus,
                    isLiked = false,
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    enclosureType = episode.enclosureType,
                    isManualCompletion = newStatus,
                    isBulkCompletion = false,
                    episodeDescription = episode.description,
                )
            listeningHistoryDao.upsert(entity)
        }
    }

    suspend fun markEpisodeAsCompleted(
        episode: Episode,
        podcast: Podcast?,
    ) {
        android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: START for ${episode.title}")

        listeningHistoryDao.setCompletionStatus(episode.id, true)

        val existing = listeningHistoryDao.getHistoryItem(episode.id)

        if (existing == null) {
            if (podcast == null && episode.podcastId == null) {
                android.util.Log.e("PlaybackRepo", "markEpisodeAsCompleted: Cannot create history item, podcast is null")
                return
            }
            android.util.Log.d("PlaybackRepo", "Creating new history item for completed episode")
            val entity =
                ListeningHistoryEntity(
                    episodeId = episode.id,
                    podcastId = episode.podcastId ?: podcast!!.id,
                    episodeTitle = episode.title,
                    episodeImageUrl = episode.imageUrl,
                    podcastImageUrl = episode.podcastImageUrl ?: podcast?.imageUrl,
                    episodeAudioUrl = episode.audioUrl,
                    podcastName =
                        episode.podcastTitle.orEmpty().ifBlank {
                            podcast?.title ?: "Unknown Podcast"
                        },
                    progressMs = 0L,
                    durationMs = episode.duration * 1000L,
                    isCompleted = true,
                    isLiked = false,
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    enclosureType = episode.enclosureType,
                    episodeDescription = episode.description,
                )
            listeningHistoryDao.upsert(entity)
        } else {
            android.util.Log.d("PlaybackRepo", "Updating existing history item as completed")
            val updated =
                existing.copy(
                    isCompleted = true,
                    progressMs = 0L,
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    episodeDescription = existing.episodeDescription ?: episode.description,
                )
            listeningHistoryDao.upsert(updated)
        }
        android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: DONE for ${episode.title}")
    }

    suspend fun savePlaybackState(
        podcastId: String,
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        episodeTitle: String,
        episodeImageUrl: String?,
        podcastImageUrl: String?,
        episodeAudioUrl: String,
        podcastName: String,
        isCompleted: Boolean,
        isLiked: Boolean,
        lastPlayedAt: Long = System.currentTimeMillis(),
        enclosureType: String? = null,
        episodeDescription: String? = null,
    ) {
        android.util.Log.v("PlaybackRepo", "Saving playback state: $episodeTitle, pos=$positionMs, completed=$isCompleted")
        val entity =
            ListeningHistoryUpsertLogic.buildProgressSaveEntity(
                ListeningHistoryUpsertLogic.ProgressSaveInput(
                    podcastId = podcastId,
                    episodeId = episodeId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    episodeTitle = episodeTitle,
                    episodeImageUrl = episodeImageUrl,
                    podcastImageUrl = podcastImageUrl,
                    episodeAudioUrl = episodeAudioUrl,
                    podcastName = podcastName,
                    isCompleted = isCompleted,
                    isLiked = isLiked,
                    lastPlayedAt = lastPlayedAt,
                    enclosureType = enclosureType,
                    episodeDescription = episodeDescription,
                ),
            )
        listeningHistoryDao.upsert(entity)
    }

    suspend fun toggleLike() {
        val state = playerStateFlow.value
        val episode = state.currentEpisode ?: return
        val podcast = state.currentPodcast ?: return
        toggleLike(episode, podcast.id, podcast.title, podcast.imageUrl)
    }

    suspend fun deleteSession(episodeId: String) {
        listeningHistoryDao.delete(episodeId)
    }

    suspend fun getSession(episodeId: String): PlaybackSession? {
        val entity = listeningHistoryDao.getHistoryItem(episodeId) ?: return null
        return PlaybackSessionMapping.fromHistoryEntity(entity)
    }

    suspend fun getRecentHistoryList(limit: Int): List<ListeningHistoryEntity> =
        listeningHistoryDao.getRecentHistoryList(limit)

    suspend fun markAllEpisodesCompleted(
        episodes: List<Episode>,
        podcastId: String,
        podcastTitle: String,
        podcastImageUrl: String?,
    ) {
        val currentTime = System.currentTimeMillis()
        val entitiesToUpsert =
            episodes.map { episode ->
                ListeningHistoryUpsertLogic.buildBulkCompleteEntity(
                    episode = episode,
                    podcastId = podcastId,
                    podcastTitle = podcastTitle,
                    podcastImageUrl = podcastImageUrl,
                    existing = listeningHistoryDao.getHistoryItem(episode.id),
                    nowMs = currentTime,
                )
            }
        listeningHistoryDao.upsertAll(entitiesToUpsert)
    }

    suspend fun markAllEpisodesUncompleted(episodes: List<Episode>) {
        val currentTime = System.currentTimeMillis()
        val entitiesToUpsert =
            episodes.mapNotNull { episode ->
                val existing = listeningHistoryDao.getHistoryItem(episode.id) ?: return@mapNotNull null
                ListeningHistoryUpsertLogic.buildBulkUncompleteEntity(
                    existing = existing,
                    nowMs = currentTime,
                )
            }
        if (entitiesToUpsert.isNotEmpty()) {
            listeningHistoryDao.upsertAll(entitiesToUpsert)
        }
    }

    suspend fun findPodcastIdForEpisode(episodeId: String): String? {
        val historyItem = listeningHistoryDao.getHistoryItem(episodeId)
        if (historyItem != null) return historyItem.podcastId

        val episode = podcastRepository.getEpisode(episodeId)
        return episode?.podcastId
    }

    suspend fun getHistoryForRecommendations(limit: Int = 15): List<cx.aswin.boxlore.core.network.model.HistoryItem> {
        val database = BoxLoreDatabase.getDatabase(context)
        val podcastDao = database.podcastDao()

        val rawHistory = listeningHistoryDao.getRecentHistoryList(limit * 3)
        return HistoryRecommendationLogic
            .selectEligible(
                raw = rawHistory,
                limit = limit,
            ) { entity ->
                HistoryRecommendationLogic.isEligible(
                    isManualCompletion = entity.isManualCompletion,
                    isBulkCompletion = entity.isBulkCompletion,
                    progressMs = entity.progressMs,
                    isCompleted = entity.isCompleted,
                )
            }.map { entity ->
                val podcast = podcastDao.getPodcast(entity.podcastId)
                android.util.Log.d(
                    "PlaybackRepo",
                    "Passing history: ${entity.episodeTitle} | Has Description: ${!entity.episodeDescription.isNullOrEmpty()} | Length: ${entity.episodeDescription?.length ?: 0}",
                )
                cx.aswin.boxlore.core.network.model.HistoryItem(
                    podcastTitle = entity.podcastName,
                    episodeTitle = entity.episodeTitle,
                    podcastId = entity.podcastId,
                    episodeId = entity.episodeId,
                    genre = podcast?.genre,
                    durationMs = entity.durationMs,
                    progressMs = entity.progressMs,
                    isCompleted = entity.isCompleted,
                    isLiked = entity.isLiked,
                    episodeDescription = entity.episodeDescription,
                )
            }
    }

    private fun ListeningHistoryEntity.toHistoryItem(): ListeningHistoryItem =
        ListeningHistoryItem(
            episodeId = episodeId,
            podcastId = podcastId,
            episodeTitle = episodeTitle,
            episodeImageUrl = episodeImageUrl,
            podcastImageUrl = podcastImageUrl,
            episodeAudioUrl = episodeAudioUrl,
            podcastName = podcastName,
            progressMs = progressMs,
            durationMs = durationMs,
            isCompleted = ListeningCompletionLogic.isCompleted(isCompleted, progressMs, durationMs),
            isLiked = isLiked,
            lastPlayedAt = lastPlayedAt,
            enclosureType = enclosureType,
            episodeDescription = episodeDescription,
        )

    private fun ListeningHistoryItem.toEntity(): ListeningHistoryEntity =
        ListeningHistoryEntity(
            episodeId = episodeId,
            podcastId = podcastId,
            episodeTitle = episodeTitle,
            episodeImageUrl = episodeImageUrl,
            podcastImageUrl = podcastImageUrl,
            episodeAudioUrl = episodeAudioUrl,
            podcastName = podcastName,
            progressMs = progressMs,
            durationMs = durationMs,
            isCompleted = isCompleted,
            isLiked = isLiked,
            lastPlayedAt = lastPlayedAt,
            isDirty = true,
            enclosureType = enclosureType,
            episodeDescription = episodeDescription,
        )

    private fun ListeningSessionEntity.toSnapshot(): ListeningSessionSnapshot =
        ListeningSessionSnapshot(
            sessionId = sessionId,
            episodeId = episodeId,
            podcastId = podcastId,
            startedAt = startedAt,
            endedAt = endedAt,
            consumedMs = consumedMs,
            completed = completed,
            localDay = localDay,
            timeBucket = timeBucket,
        )

    private fun ListeningSessionSnapshot.toEntity(): ListeningSessionEntity =
        ListeningSessionEntity(
            sessionId = sessionId,
            episodeId = episodeId,
            podcastId = podcastId,
            startedAt = startedAt,
            endedAt = endedAt,
            consumedMs = consumedMs,
            completed = completed,
            localDay = localDay,
            timeBucket = timeBucket,
        )

    private fun ListeningRollupEntity.toSnapshot(): ListeningRollupSnapshot =
        ListeningRollupSnapshot(
            localDay = localDay,
            episodeId = episodeId,
            podcastId = podcastId,
            consumedMs = consumedMs,
            sessionCount = sessionCount,
            completionCount = completionCount,
            lastListenedAt = lastListenedAt,
            morningMs = morningMs,
            afternoonMs = afternoonMs,
            eveningMs = eveningMs,
            nightMs = nightMs,
        )

    private fun ListeningRollupSnapshot.toEntity(): ListeningRollupEntity =
        ListeningRollupEntity(
            localDay = localDay,
            episodeId = episodeId,
            podcastId = podcastId,
            consumedMs = consumedMs,
            sessionCount = sessionCount,
            completionCount = completionCount,
            lastListenedAt = lastListenedAt,
            morningMs = morningMs,
            afternoonMs = afternoonMs,
            eveningMs = eveningMs,
            nightMs = nightMs,
        )
}
