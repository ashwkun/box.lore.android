package cx.aswin.boxcast.core.data

import android.content.Context
import cx.aswin.boxcast.core.data.database.BoxLoreDatabase
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.network.model.HistoryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Data-access seam for [DefaultSmartQueueEngine].
 *
 * The engine only talks to this interface so the tier/ranking logic stays a pure
 * JVM component that can be unit-tested with hand-rolled fakes. Production code
 * uses [DefaultSmartQueueSources], which wraps the real repositories and DAOs.
 */
interface SmartQueueSources {
    suspend fun getEpisodes(podcastId: String): List<Episode>
    suspend fun getPodcastDetails(podcastId: String): Podcast?
    suspend fun getSubscribedPodcasts(): List<Podcast>
    suspend fun getCompletedEpisodeIds(): Set<String>
    suspend fun getRecentlyPlayedPodcastIds(sinceMs: Long): Set<String>
    suspend fun getResumeCandidates(): List<ListeningHistoryEntity>
    suspend fun getRecentHistory(limit: Int): List<ListeningHistoryEntity>

    /** Normalized user region code (e.g. "us", "in"), used for trending + recommendations. */
    suspend fun getRegion(): String

    /** Genres the user picked during onboarding. */
    suspend fun getInterests(): List<String>

    suspend fun getHistoryForRecommendations(limit: Int): List<HistoryItem>

    suspend fun getPersonalizedRecommendations(
        history: List<HistoryItem>,
        interests: List<String>,
        country: String?,
        subscribedPodcastIds: List<String>,
        subscribedGenres: List<String>
    ): List<Episode>

    suspend fun getSimilarEpisodes(
        episodeId: String,
        podcastId: String,
        title: String,
        description: String,
        podcastTitle: String,
        country: String?
    ): List<Episode>

    suspend fun getTrendingPodcasts(country: String, category: String?): List<Podcast>
}

class DefaultSmartQueueSources(
    private val context: Context,
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : SmartQueueSources {

    override suspend fun getEpisodes(podcastId: String): List<Episode> =
        podcastRepository.getEpisodes(podcastId)

    override suspend fun getPodcastDetails(podcastId: String): Podcast? =
        podcastRepository.getPodcastDetails(podcastId)

    override suspend fun getSubscribedPodcasts(): List<Podcast> =
        subscriptionRepository.subscribedPodcasts.first()

    override suspend fun getCompletedEpisodeIds(): Set<String> =
        database.listeningHistoryDao().getCompletedEpisodeIds().toSet()

    override suspend fun getRecentlyPlayedPodcastIds(sinceMs: Long): Set<String> =
        database.listeningHistoryDao().getRecentlyPlayedPodcasts(sinceMs).toSet()

    override suspend fun getResumeCandidates(): List<ListeningHistoryEntity> =
        database.listeningHistoryDao().getResumeItemsList()

    override suspend fun getRecentHistory(limit: Int): List<ListeningHistoryEntity> =
        database.listeningHistoryDao().getRecentHistoryList(limit)

    override suspend fun getRegion(): String = try {
        userPreferencesRepository.regionStream.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        "us"
    }

    override suspend fun getInterests(): List<String> = try {
        context.getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
            .getStringSet("user_genres", emptySet())?.toList() ?: emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Same shaping as PlaybackRepository.getHistoryForRecommendations: recent meaningful
     * listens (>= 60s progress or naturally completed), excluding manual/bulk completions.
     */
    override suspend fun getHistoryForRecommendations(limit: Int): List<HistoryItem> {
        val podcastDao = database.podcastDao()
        val rawHistory = database.listeningHistoryDao().getRecentHistoryList(limit * 3)
        val filtered = rawHistory
            .filter { entity ->
                !entity.isManualCompletion && !entity.isBulkCompletion &&
                    (entity.progressMs >= 60_000L || entity.isCompleted)
            }
            .take(limit)
        val genreByPodcastId = if (filtered.isEmpty()) {
            emptyMap()
        } else {
            podcastDao.getPodcastsByIds(filtered.map { it.podcastId }.distinct())
                .associate { it.podcastId to it.genre }
        }
        return filtered.map { entity ->
            HistoryItem(
                podcastTitle = entity.podcastName,
                episodeTitle = entity.episodeTitle,
                podcastId = entity.podcastId,
                episodeId = entity.episodeId,
                genre = genreByPodcastId[entity.podcastId],
                durationMs = entity.durationMs,
                progressMs = entity.progressMs,
                isCompleted = entity.isCompleted,
                isLiked = entity.isLiked,
                episodeDescription = entity.episodeDescription
            )
        }
    }

    override suspend fun getPersonalizedRecommendations(
        history: List<HistoryItem>,
        interests: List<String>,
        country: String?,
        subscribedPodcastIds: List<String>,
        subscribedGenres: List<String>
    ): List<Episode> = podcastRepository.getPersonalizedRecommendations(
        history = history,
        interests = interests,
        country = country,
        subscribedPodcastIds = subscribedPodcastIds,
        subscribedGenres = subscribedGenres
    )

    override suspend fun getSimilarEpisodes(
        episodeId: String,
        podcastId: String,
        title: String,
        description: String,
        podcastTitle: String,
        country: String?
    ): List<Episode> = podcastRepository.getSimilarEpisodes(
        episodeId = episodeId,
        podcastId = podcastId,
        title = title,
        description = description,
        podcastTitle = podcastTitle,
        country = country
    )

    override suspend fun getTrendingPodcasts(country: String, category: String?): List<Podcast> =
        podcastRepository.getTrendingPodcasts(country = country, category = category)
}
