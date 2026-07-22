package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.home.HomeCandidatesRequestBuilder
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Request
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Home candidates retrieval + ≥4h in-process cache.
 * Kept outside [PodcastRepository] to avoid growing that file past reviewability.
 */
private val homeCandidatesCache =
    ConcurrentHashMap<String, Pair<HomeCandidatesV1Response, Long>>()

/**
 * [cacheAgeMillis] is only populated when [fromCache] is true — it feeds the
 * `home_slate_quality_snapshot` diagnostics (see [cx.aswin.boxlore.core.catalog.home.HomeSlateQualityLogic])
 * without callers needing to reach into the cache map directly.
 */
internal data class HomeCandidatesFetchResult(
    val response: HomeCandidatesV1Response?,
    val fromCache: Boolean,
    val cacheAgeMillis: Long?,
)

internal suspend fun PodcastRepository.getHomeCandidatesV1(request: HomeCandidatesV1Request): HomeCandidatesFetchResult {
    return withContext(Dispatchers.IO) {
        val cacheKey = HomeCandidatesRequestBuilder.cacheKey(request)
        val now = System.currentTimeMillis()
        val cached = homeCandidatesCache[cacheKey]
        if (
            cached != null &&
            now - cached.second < HomeCandidatesRequestBuilder.CACHE_TTL_MILLIS
        ) {
            return@withContext HomeCandidatesFetchResult(cached.first, true, now - cached.second)
        }
        try {
            val response =
                api.getHomeCandidatesV1(
                    publicKey,
                    getOrCreateDeviceUuid(),
                    request,
                )
            if (HomeCandidatesRequestBuilder.isValid(response)) {
                homeCandidatesCache[cacheKey] = response to now
                HomeCandidatesFetchResult(response, false, null)
            } else {
                HomeCandidatesFetchResult(null, false, null)
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("PodcastRepository", "home/candidates/v1 unavailable", error)
            HomeCandidatesFetchResult(null, false, null)
        }
    }
}

internal fun clearHomeCandidatesCache() {
    homeCandidatesCache.clear()
}
