package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode

import cx.aswin.boxlore.core.catalog.BuildConfig

internal fun PodcastRepository.fetchRecommendationV2(
    history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
    interests: List<String>,
    country: String?,
    subscribedPodcastIds: List<String>,
    languages: List<String> = recommendationLanguagesForCountry(country),
): List<Episode>? {
    val seeds = buildRecommendationSeeds(history, subscribedPodcastIds)
    val boundedInterests = interests.map(String::trim).filter(String::isNotEmpty).distinct().take(12)
    if (seeds.isEmpty() && boundedInterests.isEmpty()) return null
    val request = cx.aswin.boxlore.core.network.model.RecommendationsV2Request(
        country = country?.lowercase()?.takeIf { it.length in 2..3 } ?: "us",
        languages = languages.map(String::trim).filter(String::isNotEmpty).distinct().take(8)
            .ifEmpty { recommendationLanguagesForCountry(country) },
        seeds = seeds,
        interests = boundedInterests,
        subscribedPodcastIds = subscribedPodcastIds.mapNotNull(String::toLongOrNull)
            .filter { it > 0L }
            .distinct()
            .take(250),
        excludedEpisodeIds = history.mapNotNull { it.episodeId?.toLongOrNull() }
            .filter { it > 0L }
            .distinct()
            .take(250),
    )
    val response = api.getPersonalizedRecommendationsV2(
        publicKey,
        getOrCreateDeviceUuid(),
        request,
    ).execute()
    val body = response.body()
    val payload = body ?: return null
    if (!payload.isValidRecommendationV2Response(response.isSuccessful)) {
        return null
    }
    return payload.items.mapNotNull { mapToEpisode(it) }.takeIf { it.isNotEmpty() }
}

private fun cx.aswin.boxlore.core.network.model.RecommendationsV2Response.isValidRecommendationV2Response(
    isHttpSuccessful: Boolean,
): Boolean {
    if (!isHttpSuccessful) return false
    return status == "true" &&
        contractVersion == 2 &&
        !algorithmVersion.isNullOrBlank()
}

internal fun buildRecommendationSeeds(
    history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
    subscribedPodcastIds: List<String>,
    maximumSeeds: Int = 12,
): List<cx.aswin.boxlore.core.network.model.RecommendationSeedV2> {
    require(maximumSeeds > 0)
    val episodeSeeds = history.mapNotNull { item ->
        val id = item.episodeId?.toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
        val durationMs = item.durationMs ?: 0L
        val progressRatio = if (durationMs > 0L) {
            (item.progressMs ?: 0L).toDouble() / durationMs
        } else {
            0.0
        }
        val weight = when {
            item.isLiked == true -> 1.0
            item.isCompleted == true -> 0.9
            progressRatio >= 0.5 -> 0.75
            progressRatio >= 0.2 -> 0.55
            else -> 0.35
        }
        cx.aswin.boxlore.core.network.model.RecommendationSeedV2(
            kind = "episode",
            id = id,
            weight = weight,
            fallback = cx.aswin.boxlore.core.network.model.RecommendationSemanticFallback(
                episodeTitle = item.episodeTitle.take(180),
                podcastTitle = item.podcastTitle.take(180),
                genre = item.genre?.take(120),
                description = item.episodeDescription.toSemanticFallback(),
            ),
        )
    }.distinctBy { it.id }.sortedByDescending { it.weight }
    if (episodeSeeds.size >= maximumSeeds) return episodeSeeds.take(maximumSeeds)
    val podcastSeeds = subscribedPodcastIds.asSequence()
        .mapNotNull(String::toLongOrNull)
        .filter { it > 0L }
        .distinct()
        .map { id ->
            cx.aswin.boxlore.core.network.model.RecommendationSeedV2(
                kind = "podcast",
                id = id,
                weight = 0.35,
            )
        }
        .take(maximumSeeds - episodeSeeds.size)
        .toList()
    return episodeSeeds.take(maximumSeeds) + podcastSeeds
}

/**
 * Maps region codes to language tags for recommendation retrieval.
 * English remains included as a fallback so sparse non-EN inventory still works.
 */
fun recommendationLanguagesForCountry(country: String?): List<String> {
    return when (country?.lowercase()) {
        "fr" -> listOf("fr", "en")
        "in" -> listOf("en", "hi")
        "gb", "uk", "us", "au", "ca", "ie", "nz" -> listOf("en")
        else -> listOf("en")
    }
}

internal fun PodcastRepository.fetchLegacyRecommendations(
    history: List<cx.aswin.boxlore.core.network.model.HistoryItem>,
    interests: List<String>,
    country: String?,
    subscribedPodcastIds: List<String>,
    subscribedGenres: List<String>,
): List<Episode> {
    val request = cx.aswin.boxlore.core.network.model.RecommendationsRequest(
        history = history,
        interests = interests,
        country = country,
        subscribedPodcastIds = subscribedPodcastIds,
        subscribedGenres = subscribedGenres,
    )
    val response = api.getPersonalizedRecommendations(
        publicKey,
        getOrCreateDeviceUuid(),
        request,
    ).execute()
    return if (response.isSuccessful) {
        response.body()?.items.orEmpty().mapNotNull { mapToEpisode(it) }
    } else {
        emptyList()
    }
}
