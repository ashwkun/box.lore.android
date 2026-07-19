package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.Transcript

import cx.aswin.boxlore.core.network.model.TrendingFeed
import cx.aswin.boxlore.core.catalog.BuildConfig

/**
 * Upgrade HTTP URLs to HTTPS to fix Android cleartext traffic restrictions.
 * Most CDNs (including BBC's ichef) support HTTPS, so this is safe.
 */
internal fun String?.toHttps(): String {
    if (this.isNullOrEmpty()) return ""
    return if (this.startsWith("http://")) {
        this.replaceFirst("http://", "https://")
    } else {
        this
    }
}

// Sort from most specific/descriptive to most generic to prevent "News" overriding "Sports"
private val GENRE_PRIORITY = listOf(
    "True Crime", "Fiction", "Comedy", "Sports", "History", "Science",
    "Technology", "Music", "TV & Film", "Arts", "Health & Fitness", "Health",
    "Religion & Spirituality", "Kids & Family", "Education", "Government", "Business",
    "News", "Leisure", "Society & Culture", "Society", "Culture"
)

internal fun resolvePrimaryGenre(categories: Map<String, String>?): String {
    if (categories.isNullOrEmpty()) return "Podcast"

    // 1. Check for exact matches in our priority list (from most specific to generic)
    for (priority in GENRE_PRIORITY) {
        val match = categories.values.find { it.equals(priority, ignoreCase = true) }
        if (match != null) return match
    }

    // 2. Fallback check for partial matches in our priority list
    for (priority in GENRE_PRIORITY) {
        val match = categories.values.find { it.contains(priority, ignoreCase = true) }
        if (match != null) return match
    }

    // 3. Last resort: Return the first value that isn't completely useless
    val ignored = listOf("podcasts", "podcast")
    return categories.values.firstOrNull { it.lowercase() !in ignored } ?: "Podcast"
}

internal fun mapFeedsToPodcasts(feeds: List<cx.aswin.boxlore.core.network.model.TrendingFeed>): List<Podcast> {
    return feeds.map { feed ->
        Podcast(
            id = feed.id.toString(),
            title = feed.title,
            artist = feed.author ?: "Unknown",
            imageUrl = (feed.artwork ?: feed.image).toHttps(),
            description = feed.description,
            genre = resolvePrimaryGenre(feed.categories),
            latestEpisode = feed.latestEpisode?.let { epItem ->
                mapToEpisode(epItem)?.copy(
                    podcastId = epItem.feedId?.toString() ?: feed.id.toString(),
                    podcastTitle = epItem.feedTitle?.takeIf { it.isNotBlank() } ?: feed.title
                )
            },
            medium = feed.medium
        )
    }
}


internal fun mapToEpisode(item: cx.aswin.boxlore.core.network.model.EpisodeItem): Episode? {
    val audioUrl = item.enclosureUrl ?: return null
    android.util.Log.d("BoxCastRepo", "mapToEpisode: ${item.title} | persons=${item.persons?.size} | chaptersUrl=${item.chaptersUrl != null} | transcripts=${item.transcripts?.size}")
    return item.toEpisode(
        audioUrl = audioUrl,
        resolvedTranscriptUrl = item.resolvedTranscriptUrl()
    )
}

private fun cx.aswin.boxlore.core.network.model.EpisodeItem.resolvedTranscriptUrl(): String? {
    val preferredTranscript = transcripts?.firstOrNull {
        it.isPreferredSubtitleTranscript()
    }
    if (preferredTranscript != null) return preferredTranscript.url

    val directTranscriptUrl = transcriptUrl
    if (directTranscriptUrl.isSubtitleTranscriptUrl()) return directTranscriptUrl

    return directTranscriptUrl ?: transcripts?.firstOrNull()?.url
}

private fun cx.aswin.boxlore.core.network.model.TranscriptItem.isPreferredSubtitleTranscript(): Boolean =
    type.isSubtitleTranscriptType() || url.isSubtitleTranscriptUrl()

private fun String?.isSubtitleTranscriptType(): Boolean =
    this == "application/srt" ||
        this == "text/vtt" ||
        this == "application/x-subrip"

private fun String?.isSubtitleTranscriptUrl(): Boolean =
    this?.contains(".srt", ignoreCase = true) == true ||
        this?.contains(".vtt", ignoreCase = true) == true

private fun cx.aswin.boxlore.core.network.model.EpisodeItem.toEpisode(
    audioUrl: String,
    resolvedTranscriptUrl: String?
): Episode {
    return Episode(
        id = id.toString(),
        title = title,
        description = description ?: "",
        audioUrl = audioUrl,
        imageUrl = (image?.takeIf { it.isNotBlank() } ?: feedImage?.takeIf { it.isNotBlank() }).toHttps(),
        podcastImageUrl = feedImage?.takeIf { it.isNotBlank() }?.let { it.toHttps() },
        podcastTitle = feedTitle,
        podcastId = feedId?.toString(),
        duration = duration ?: 0,
        publishedDate = datePublished ?: 0L,
        // Podcast 2.0
        chaptersUrl = chaptersUrl,
        transcriptUrl = resolvedTranscriptUrl,
        transcripts = transcripts?.map { Transcript(url = it.url, type = it.type) },
        persons = persons?.map { Person(name = it.name, role = it.role, group = it.group, img = it.img, href = it.href) },
        seasonNumber = season,
        episodeNumber = episodeNumber,
        episodeType = episodeType,
        enclosureType = enclosureType,
        retrievalScore = retrievalScore,
        semanticScore = semanticScore,
        recommendationSource = recommendationSource,
        recommendationReason = recommendationReason,
        serverRank = serverRank,
        recommendationAlgorithmVersion = algorithmVersion,
        language = language,
        podcastGenre = genre,
    )
}

internal fun mapToPodcast(feed: cx.aswin.boxlore.core.network.model.TrendingFeed): Podcast {
    return Podcast(
        id = feed.id.toString(),
        title = feed.title,
        artist = feed.author ?: "Unknown",
        imageUrl = (feed.artwork ?: feed.image).toHttps(),
        description = feed.description,
        genre = resolvePrimaryGenre(feed.categories),
        latestEpisode = feed.latestEpisode?.let { epItem ->
            mapToEpisode(epItem)?.copy(
                podcastId = epItem.feedId?.toString() ?: feed.id.toString(),
                podcastTitle = epItem.feedTitle?.takeIf { it.isNotBlank() } ?: feed.title
            )
        },
        medium = feed.medium
    )
}
