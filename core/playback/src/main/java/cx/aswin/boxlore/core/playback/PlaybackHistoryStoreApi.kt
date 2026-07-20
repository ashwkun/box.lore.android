package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Non-port [PlaybackHistoryStore] API kept off the class for TooManyFunctions. */
internal val PlaybackHistoryStore.lastPlayedSession: Flow<PlaybackSession?>
    get() =
        data.listeningHistoryDao
            .getResumeItems()
            .map { historyList ->
                val latest = historyList.firstOrNull()
                if (latest != null) {
                    PlaybackSessionMapping.fromHistoryEntity(latest)
                } else {
                    null
                }
            }

internal val PlaybackHistoryStore.resumeSessions: Flow<List<PlaybackSession>>
    get() =
        data.listeningHistoryDao
            .getResumeItems()
            .map { historyList ->
                historyList.map { entity ->
                    PlaybackSessionMapping.fromHistoryEntity(entity)
                }
            }

internal val PlaybackHistoryStore.likedEpisodes: Flow<List<ListeningHistoryEntity>>
    get() = data.listeningHistoryDao.getLikedEpisodes()

internal val PlaybackHistoryStore.completedEpisodeIds: Flow<Set<String>>
    get() =
        data.listeningHistoryDao
            .getCompletedEpisodeIdsFlow()
            .map { it.toSet() }

internal suspend fun PlaybackHistoryStore.recordListeningSession(session: ListeningSessionEntity) {
    ListeningSessionRecordLogic.persistSessionAndRollUp(
        sessions = data.listeningSessionDao,
        maintenance = data.listeningInsightsMaintenance,
        session = session,
    )
}

internal suspend fun PlaybackHistoryStore.toggleLike(
    episode: Episode,
    podcastId: String,
    podcastTitle: String,
    podcastImageUrl: String?,
) {
    val dao = data.listeningHistoryDao
    val existing = dao.getHistoryItem(episode.id)
    val newStatus = !(existing?.isLiked ?: false)
    val stateFlow = player.playerStateFlow

    if (stateFlow.value.currentEpisode?.id == episode.id) {
        stateFlow.value = stateFlow.value.copy(isLiked = newStatus)
    }

    if (existing != null) {
        dao.setLikeStatus(episode.id, newStatus)
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
        dao.upsert(entity)
    }
    data.rankingFeedbackRepository.recordAction(
        target =
            FeedbackTarget(
                episodeId = episode.id,
                podcastId = podcastId,
                genre = episode.podcastGenre,
            ),
        action = if (newStatus) RankingAction.LIKE else RankingAction.UNLIKE,
    )
    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackEpisodeLikedToggled(
        episodeId = episode.id,
        podcastId = podcastId,
        isLiked = newStatus,
    )
}

internal suspend fun PlaybackHistoryStore.toggleCompletion(
    episode: Episode,
    podcastId: String,
    podcastTitle: String,
    podcastImageUrl: String?,
) {
    val dao = data.listeningHistoryDao
    val existing = dao.getHistoryItem(episode.id)
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
        dao.upsert(updated)
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
        dao.upsert(entity)
    }
    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackEpisodeMarkPlayed(
        episodeId = episode.id,
        podcastId = podcastId,
        isPlayed = newStatus,
    )
}

internal suspend fun PlaybackHistoryStore.markEpisodeAsCompleted(
    episode: Episode,
    podcast: Podcast?,
) {
    android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: START for ${episode.title}")
    val dao = data.listeningHistoryDao
    dao.setCompletionStatus(episode.id, true)
    val existing = dao.getHistoryItem(episode.id)

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
        dao.upsert(entity)
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
        dao.upsert(updated)
    }
    android.util.Log.d("PlaybackRepo", "markEpisodeAsCompleted: DONE for ${episode.title}")
}

internal suspend fun PlaybackHistoryStore.savePlaybackState(input: ListeningHistoryUpsertLogic.ProgressSaveInput) {
    android.util.Log.v(
        "PlaybackRepo",
        "Saving playback state: ${input.episodeTitle}, pos=${input.positionMs}, completed=${input.isCompleted}",
    )
    data.listeningHistoryDao.upsert(ListeningHistoryUpsertLogic.buildProgressSaveEntity(input))
}

internal suspend fun PlaybackHistoryStore.toggleLike() {
    val state = player.playerStateFlow.value
    val episode = state.currentEpisode ?: return
    val podcast = state.currentPodcast ?: return
    toggleLike(episode, podcast.id, podcast.title, podcast.imageUrl)
}

internal suspend fun PlaybackHistoryStore.deleteSession(episodeId: String) {
    data.listeningHistoryDao.delete(episodeId)
}

internal suspend fun PlaybackHistoryStore.getSession(episodeId: String): PlaybackSession? {
    val entity = data.listeningHistoryDao.getHistoryItem(episodeId) ?: return null
    return PlaybackSessionMapping.fromHistoryEntity(entity)
}

internal suspend fun PlaybackHistoryStore.getRecentHistoryList(limit: Int): List<ListeningHistoryEntity> =
    data.listeningHistoryDao.getRecentHistoryList(limit)

internal suspend fun PlaybackHistoryStore.markAllEpisodesUncompleted(episodes: List<Episode>) {
    val currentTime = System.currentTimeMillis()
    val entitiesToUpsert =
        episodes.mapNotNull { episode ->
            val existing = data.listeningHistoryDao.getHistoryItem(episode.id) ?: return@mapNotNull null
            ListeningHistoryUpsertLogic.buildBulkUncompleteEntity(
                existing = existing,
                nowMs = currentTime,
            )
        }
    if (entitiesToUpsert.isNotEmpty()) {
        data.listeningHistoryDao.upsertAll(entitiesToUpsert)
    }
}

internal suspend fun PlaybackHistoryStore.findPodcastIdForEpisode(episodeId: String): String? {
    val historyItem = data.listeningHistoryDao.getHistoryItem(episodeId)
    if (historyItem != null) return historyItem.podcastId
    return data.podcastRepository.getEpisode(episodeId)?.podcastId
}

internal suspend fun PlaybackHistoryStore.getHistoryForRecommendations(limit: Int = 15): List<HistoryItem> {
    val database = BoxLoreDatabase.getDatabase(player.context)
    val podcastDao = database.podcastDao()
    val rawHistory = data.listeningHistoryDao.getRecentHistoryList(limit * 3)
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
            HistoryItem(
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
