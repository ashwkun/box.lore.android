package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import cx.aswin.boxlore.core.catalog.BuildConfig

internal suspend fun PodcastRepository.searchRssEpisodes(feedId: String, query: String): List<Episode> = try {
    rssRepository.searchEpisodes(feedId, query)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "RSS searchEpisodes failed for $feedId", e)
    emptyList()
}

internal suspend fun PodcastRepository.searchNetworkEpisodes(feedId: String, query: String): List<Episode> = try {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    val response = api.searchEpisodes(publicKey, resolvedId, query).execute()
    if (response.isSuccessful && response.body() != null) {
        response.body()!!.items.mapNotNull { mapToEpisode(it) }
    } else {
        emptyList()
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}

internal suspend fun PodcastRepository.getEpisodesImpl(feedId: String): List<Episode> = withContext(Dispatchers.IO) {
    if (feedId.startsWith("rss:")) {
        return@withContext getAllRssEpisodes(feedId)
    }
    getAllNetworkEpisodes(feedId)
}

internal suspend fun PodcastRepository.getAllRssEpisodes(feedId: String): List<Episode> = try {
    rssRepository.getAllEpisodes(feedId)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "RSS getAllEpisodes failed for $feedId", e)
    emptyList()
}

internal suspend fun PodcastRepository.getAllNetworkEpisodes(feedId: String): List<Episode> = try {
    val resolvedId = resolvePodcastIndexFeedId(feedId)
    // Use paginated endpoint with high limit to get "all" (max 1000 per proxy)
    // This avoids the parsing issue with EpisodesResponse vs EpisodesPaginatedResponse
    val response = api.getEpisodesPaginated(publicKey, resolvedId, limit = 1000).execute()
    if (response.isSuccessful && response.body() != null) {
        response.body()!!.items.mapNotNull { mapToEpisode(it) }
    } else {
        emptyList()
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    emptyList()
}

internal suspend fun PodcastRepository.getEpisodeImpl(episodeId: String): Episode? = withContext(Dispatchers.IO) {
    if (episodeId.toLongOrNull()?.let { it < 0L } == true) {
        return@withContext getRssEpisode(episodeId)
    }
    getNetworkEpisode(episodeId)
}

internal suspend fun PodcastRepository.getRssEpisode(episodeId: String): Episode? = try {
    rssRepository.getEpisode(episodeId)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.e("PodcastRepository", "RSS getEpisode failed for $episodeId", e)
    null
}

internal suspend fun PodcastRepository.getNetworkEpisode(episodeId: String): Episode? = try {
    val response = api.getEpisode(publicKey, episodeId).execute()
    if (response.isSuccessful && response.body() != null) {
        response.body()!!.episode?.let { mapToEpisode(it) }
    } else {
        null
    }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/** Resolves url:/guid:/itunes: identifiers to a Podcast Index feed id when needed. */
internal suspend fun PodcastRepository.resolvePodcastIndexFeedId(feedId: String): String {
    return if (
        feedId.startsWith(PodcastRepository.FEED_PREFIX_URL) ||
        feedId.startsWith(PodcastRepository.FEED_PREFIX_GUID) ||
        feedId.startsWith(PodcastRepository.FEED_PREFIX_ITUNES)
    ) {
        getPodcastDetails(feedId)?.id ?: feedId
    } else {
        feedId
    }
}
