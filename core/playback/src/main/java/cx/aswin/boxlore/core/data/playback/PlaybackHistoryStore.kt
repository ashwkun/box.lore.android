package cx.aswin.boxlore.core.data.playback

import android.content.Context
import cx.aswin.boxlore.core.data.PlaybackSession
import cx.aswin.boxlore.core.data.PlayerState
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.database.ListeningHistoryDao
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Listening history / like / completion store for [cx.aswin.boxlore.core.data.PlaybackRepository].
 */
internal class PlaybackHistoryStore(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playerState: StateFlow<PlayerState>,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val listeningHistoryDao: ListeningHistoryDao,
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

    fun getAllHistory(): Flow<List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity>> =
        listeningHistoryDao.getAllHistory()

    val likedEpisodes: Flow<List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity>> = listeningHistoryDao.getLikedEpisodes()

    val completedEpisodeIds: Flow<Set<String>> =
        listeningHistoryDao
            .getCompletedEpisodeIdsFlow()
            .map { it.toSet() }

    suspend fun upsertHistoryEntity(entity: cx.aswin.boxlore.core.data.database.ListeningHistoryEntity) {
        listeningHistoryDao.upsert(entity)
    }

    suspend fun removeHistoryItem(episodeId: String) {
        listeningHistoryDao.delete(episodeId)
    }

    suspend fun clearHistory() {
        listeningHistoryDao.deleteAll()
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

        // If current player is playing this episode, update state immediately
        if (playerStateFlow.value.currentEpisode?.id == episode.id) {
            playerStateFlow.value = playerStateFlow.value.copy(isLiked = newStatus)
        }

        if (existing != null) {
            listeningHistoryDao.setLikeStatus(episode.id, newStatus)
        } else {
            // Create new entry if liking something not in history
            val entity =
                cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
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
            // Create new entry
            val entity =
                cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
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

        // Update DB
        listeningHistoryDao.setCompletionStatus(episode.id, true)

        // Update History Entity to ensure consistency
        val existing = listeningHistoryDao.getHistoryItem(episode.id)

        if (existing == null) {
            if (podcast == null && episode.podcastId == null) {
                android.util.Log.e("PlaybackRepo", "markEpisodeAsCompleted: Cannot create history item, podcast is null")
                return
            }
            android.util.Log.d("PlaybackRepo", "Creating new history item for completed episode")
            val entity =
                cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
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
                    progressMs = 0L, // Reset progress on completion
                    durationMs = episode.duration * 1000L,
                    isCompleted = true,
                    isLiked = false, // We don't know
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    enclosureType = episode.enclosureType,
                    episodeDescription = episode.description,
                )
            listeningHistoryDao.upsert(entity)
        } else {
            // UPDATE timestamp too so it appears in recently played (loop prevention)
            android.util.Log.d("PlaybackRepo", "Updating existing history item as completed")
            val updated =
                existing.copy(
                    isCompleted = true,
                    progressMs = 0L, // Reset progress!
                    lastPlayedAt = System.currentTimeMillis(),
                    isDirty = true,
                    episodeDescription = existing.episodeDescription ?: episode.description,
                )
            listeningHistoryDao.upsert(updated)
        }
        android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: DONE for ${episode.title}")
    }

    // ... toggleLike ...

    suspend fun savePlaybackState(
        podcastId: String,
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        // New params for cache
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
            cx.aswin.boxlore.core.data.playback.ListeningHistoryUpsertLogic.buildProgressSaveEntity(
                cx.aswin.boxlore.core.data.playback.ListeningHistoryUpsertLogic.ProgressSaveInput(
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

    // Legacy parameterless generic toggle (for player controls)
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

    /** Recent history rows for adaptive scoring (Home Because You Like, etc.). */
    suspend fun getRecentHistoryList(limit: Int): List<cx.aswin.boxlore.core.data.database.ListeningHistoryEntity> =
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
                cx.aswin.boxlore.core.data.playback.ListeningHistoryUpsertLogic.buildBulkCompleteEntity(
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
                cx.aswin.boxlore.core.data.playback.ListeningHistoryUpsertLogic.buildBulkUncompleteEntity(
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
        val database =
            cx.aswin.boxlore.core.data.database.BoxLoreDatabase
                .getDatabase(context)
        val podcastDao = database.podcastDao()

        // Fetch up to limit * 3 recent items to have room for filtering out accidental skips/taps
        val rawHistory = listeningHistoryDao.getRecentHistoryList(limit * 3)
        return cx.aswin.boxlore.core.data.playback.HistoryRecommendationLogic
            .selectEligible(
                raw = rawHistory,
                limit = limit,
            ) { entity ->
                cx.aswin.boxlore.core.data.playback.HistoryRecommendationLogic.isEligible(
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
}
