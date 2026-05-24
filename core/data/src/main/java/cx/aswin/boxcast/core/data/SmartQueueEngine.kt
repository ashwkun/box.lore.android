package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.data.database.ListeningHistoryDao
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.network.model.EpisodeItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Represents an episode in the queue along with its podcast context.
 * This is needed because fallback episodes may come from different podcasts.
 */
data class QueueEntry(
    val episode: EpisodeItem,
    val podcast: Podcast,
    val source: String
)

interface SmartQueueEngine {
    suspend fun getNextEpisodes(currentEpisode: EpisodeItem, podcast: Podcast?, preferredSort: String? = null): List<QueueEntry>
}

@Singleton
class DefaultSmartQueueEngine @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val listeningHistoryDao: ListeningHistoryDao,
    private val subscriptionRepository: SubscriptionRepository
) : SmartQueueEngine {

    override suspend fun getNextEpisodes(currentEpisode: EpisodeItem, podcast: Podcast?, preferredSort: String?): List<QueueEntry> {
        android.util.Log.d("SmartQueue", "getNextEpisodes called for epId=${currentEpisode.id}, podcast=${podcast?.title}, sort=$preferredSort")
        
        if (podcast == null) {
            android.util.Log.e("SmartQueue", "Podcast is null, returning empty")
            return emptyList()
        }

        // 1. Fetch all episodes for context
        val rawEpisodes = podcastRepository.getEpisodes(podcast.id)
        android.util.Log.d("SmartQueue", "Fetched ${rawEpisodes.size} episodes from repo")
        
        if (rawEpisodes.isEmpty()) {
             android.util.Log.w("SmartQueue", "Repo returned NO episodes")
             return emptyList()
        }

        // Sort Chronologically (Oldest -> Newest)
        val allEpisodes = rawEpisodes.sortedBy { it.publishedDate }

        // 2. Find current episode index
        val searchId = currentEpisode.id.toString()
        val currentIndex = allEpisodes.indexOfFirst { it.id == searchId }
        
        android.util.Log.d("SmartQueue", "Searching for ID $searchId. Found at index: $currentIndex")

        if (currentIndex == -1) {
            android.util.Log.e("SmartQueue", "Current episode NOT found in list of ${allEpisodes.size}. IDs: ${allEpisodes.take(5).map { it.id }}")
            return emptyList()
        }

        // 3. Take next episodes from the same podcast (excluding current)
        val currentIdStr = searchId
        val candidates = mutableListOf<Episode>()
        val remainingCount = allEpisodes.size - (currentIndex + 1)
        
        android.util.Log.d("SmartQueue", "Current=$currentIndex, Total=${allEpisodes.size}, Remaining=$remainingCount")

        if (remainingCount > 0) {
            val limit = minOf(remainingCount, 20)
            for (i in 1..limit) {
                val candidate = allEpisodes[currentIndex + i]
                // Skip duplicates (by ID only — title dedup is too fragile)
                if (candidate.id == currentIdStr) {
                    android.util.Log.d("SmartQueue", "Skipping duplicate: '${candidate.title}' (id=${candidate.id})")
                    continue
                }
                // Skip trailers — don't auto-suggest promotional content
                if (candidate.episodeType == "trailer") {
                    android.util.Log.d("SmartQueue", "Skipping trailer: '${candidate.title}' (id=${candidate.id})")
                    continue
                }
                candidates.add(candidate)
            }
        }
        
        // If no valid candidates remain (all were trailers or end of podcast), use fallback
        if (candidates.isEmpty()) {
            android.util.Log.d("SmartQueue", "No non-trailer candidates remain. Triggering Smart Fallback")
            val fallbackEpisodes = getSmartFallbackEpisodes(podcast)
            android.util.Log.d("SmartQueue", "Fallback returned ${fallbackEpisodes.size} episodes")
            return fallbackEpisodes
        }
        
        android.util.Log.d("SmartQueue", "Returning ${candidates.size} candidates from same podcast (trailers excluded)")

        return candidates.map { domainEp ->
            val episodeItem = EpisodeItem(
                id = domainEp.id.toLongOrNull() ?: 0L,
                title = domainEp.title,
                description = domainEp.description,
                enclosureUrl = domainEp.audioUrl,
                duration = domainEp.duration.toInt(),
                datePublished = domainEp.publishedDate,
                image = domainEp.imageUrl,
                feedImage = domainEp.podcastImageUrl,
                episodeType = domainEp.episodeType
            )
            QueueEntry(episode = episodeItem, podcast = podcast, source = "same_podcast")
        }
    }

    /**
     * Smart Fallback: When the current podcast has no more episodes.
     * Priority:
     * 1. User's subscriptions (ANY sub with unplayed episodes, no genre matching)
     * 2. Trending podcasts in the same genre -> latest episode
     * Returns QueueEntry with the CORRECT podcast for each episode.
     */
    private suspend fun getSmartFallbackEpisodes(currentPodcast: Podcast): List<QueueEntry> {
        val completedEpisodeIds = listeningHistoryDao.getCompletedEpisodeIds().toSet()
        
        // Loop prevention: exclude recently played podcasts (12h)
        val limitTimestamp = System.currentTimeMillis() - (12 * 60 * 60 * 1000)
        val recentPodcasts = listeningHistoryDao.getRecentlyPlayedPodcasts(limitTimestamp).toSet()
        
        android.util.Log.d("SmartQueue", "Fallback: ${completedEpisodeIds.size} completed, ${recentPodcasts.size} recently played")

        // --- Priority 1: Any subscription with unplayed episodes (NO genre matching) ---
        val subscribedPodcasts = subscriptionRepository.subscribedPodcasts.first()
        
        val eligibleSubs = subscribedPodcasts.filter { sub ->
            sub.id != currentPodcast.id && 
            !sub.title.equals(currentPodcast.title, ignoreCase = true) &&
            !recentPodcasts.contains(sub.id)
        }.shuffled() // Rotate which subscription gets priority
        android.util.Log.d("SmartQueue", "Fallback: ${eligibleSubs.size} eligible subscriptions (shuffled, no genre filter)")

        for (sub in eligibleSubs) {
            val subEpisodes = podcastRepository.getEpisodes(sub.id)
                .sortedByDescending { it.publishedDate }
                .filter { it.id !in completedEpisodeIds && it.episodeType != "trailer" }
            
            if (subEpisodes.isNotEmpty()) {
                android.util.Log.d("SmartQueue", "Fallback: Found unplayed episode in '${sub.title}'")
                val nextEp = subEpisodes.first()
                return listOf(nextEp.toQueueEntry(sub, "subscription"))
            }
        }

        // --- Priority 2: Trending (genre-matched, last resort) ---
        // Fetch full podcast for accurate genre
        val fullPodcast = try {
            val cached = subscribedPodcasts.find { it.id == currentPodcast.id }
            if (cached != null && !cached.genre.isNullOrEmpty()) cached
            else podcastRepository.getPodcastDetails(currentPodcast.id) ?: currentPodcast
        } catch (e: Exception) { currentPodcast }
        
        val currentGenre = fullPodcast.genre
        android.util.Log.d("SmartQueue", "Fallback: Trying Trending for genre='$currentGenre'")
        
        val trendingPodcasts = podcastRepository.getTrendingPodcasts(category = currentGenre)

        for (trendingPod in trendingPodcasts) {
            if (trendingPod.id == currentPodcast.id) continue
            if (trendingPod.title.equals(currentPodcast.title, ignoreCase = true)) continue
            
            val cleanId = trendingPod.id.trim()
            if (recentPodcasts.any { it.trim() == cleanId }) {
                android.util.Log.d("SmartQueue", "Fallback: Skipping trending '${trendingPod.title}' (recently played)")
                continue
            }
            
            val trendingEpisodes = podcastRepository.getEpisodes(trendingPod.id)
                .sortedByDescending { it.publishedDate }
                .filter { it.id !in completedEpisodeIds && it.episodeType != "trailer" }

            if (trendingEpisodes.isNotEmpty()) {
                android.util.Log.d("SmartQueue", "Fallback: Found trending '${trendingPod.title}' with unplayed")
                val nextEp = trendingEpisodes.first()
                return listOf(nextEp.toQueueEntry(trendingPod, "trending"))
            }
        }

        android.util.Log.w("SmartQueue", "Fallback: No suitable episodes found. Queue will end.")
        return emptyList()
    }

    /**
     * Convert Episode to QueueEntry with its associated podcast.
     */
    private fun Episode.toQueueEntry(podcast: Podcast, source: String): QueueEntry {
        val episodeItem = EpisodeItem(
            id = this.id.toLongOrNull() ?: 0L,
            title = this.title,
            description = this.description,
            enclosureUrl = this.audioUrl,
            duration = this.duration.toInt(),
            datePublished = this.publishedDate,
            image = this.imageUrl,
            feedImage = this.podcastImageUrl ?: podcast.imageUrl
        )
        return QueueEntry(episode = episodeItem, podcast = podcast, source = source)
    }
}
