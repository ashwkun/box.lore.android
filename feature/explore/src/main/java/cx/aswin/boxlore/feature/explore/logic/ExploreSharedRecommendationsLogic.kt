package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.model.Episode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shared Home ↔ Explore recommendation list helpers.
 *
 * Both surfaces read/write [cx.aswin.boxlore.core.prefs.BoxcastPrefs] recommendation
 * cache and should present the same bootstrap payload (taste or region fallback).
 */
object ExploreSharedRecommendationsLogic {
    private val json = Json { ignoreUnknownKeys = true }

    fun distinctRecommendations(episodes: List<Episode>): List<Episode> =
        episodes
            .distinctBy { it.id }
            .distinctBy { it.title.lowercase().trim() }

    fun decodeCachedRecommendationsJson(serialized: String?): List<Episode> {
        if (serialized.isNullOrBlank()) return emptyList()
        return runCatching {
            distinctRecommendations(json.decodeFromString<List<Episode>>(serialized))
        }.getOrDefault(emptyList())
    }

    fun encodeRecommendationsJson(episodes: List<Episode>): String =
        json.encodeToString(distinctRecommendations(episodes))
}
