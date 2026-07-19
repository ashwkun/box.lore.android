package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import okhttp3.ResponseBody
import java.io.InputStreamReader

import cx.aswin.boxlore.core.network.model.TrendingFeed
import cx.aswin.boxlore.core.catalog.BuildConfig

internal fun PodcastRepository.trendingPodcastsStream(country: String = "us", limit: Int = 50, category: String? = null, offset: Int = 0): kotlinx.coroutines.flow.Flow<List<Podcast>> = kotlinx.coroutines.flow.flow {
    val podcasts = mutableListOf<Podcast>()
    try {
        android.util.Log.d("BoxCastRepo", "Stream: Requesting trending country=$country, limit=$limit, category=$category, offset=$offset")
        val call = api.getTrendingStream(publicKey, country, limit, category, offset)
        val response = call.execute()

        response.raw().use {
            android.util.Log.d("BoxCastRepo", "Stream: Response code=${response.code()}, isSuccessful=${response.isSuccessful}")

            val responseBody = response.body()
            if (!response.isSuccessful || responseBody == null) {
                android.util.Log.e("BoxCastRepo", "Stream: Failed! code=${response.code()}, body isNull=${responseBody == null}")
                return@use
            }

            readTrendingPodcasts(responseBody, podcasts) { emit(it) }

            android.util.Log.d("BoxCastRepo", "Stream: Parsed ${podcasts.size} podcasts for category=$category")

            // Final emission
            if (podcasts.isNotEmpty()) {
                emit(podcasts)
            }
        }

    } catch (e: Exception) {
        android.util.Log.e("BoxCastRepo", "Stream: Exception for category=$category", e)
        if (podcasts.isNotEmpty()) emit(podcasts)
    }
}.flowOn(Dispatchers.IO)

private suspend fun PodcastRepository.readTrendingPodcasts(
    responseBody: ResponseBody,
    podcasts: MutableList<Podcast>,
    emitSnapshot: suspend (List<Podcast>) -> Unit,
) {
    val reader = JsonReader(InputStreamReader(responseBody.byteStream(), "UTF-8"))
    reader.isLenient = true
    reader.beginObject()
    while (reader.hasNext()) {
        readTrendingProperty(reader, podcasts, emitSnapshot)
    }
    reader.endObject()
}

private suspend fun PodcastRepository.readTrendingProperty(
    reader: JsonReader,
    podcasts: MutableList<Podcast>,
    emitSnapshot: suspend (List<Podcast>) -> Unit,
) {
    if (reader.nextName() != "feeds") {
        reader.skipValue()
        return
    }

    reader.beginArray()
    readTrendingFeeds(reader, podcasts, emitSnapshot)
    reader.endArray()
}

private suspend fun PodcastRepository.readTrendingFeeds(
    reader: JsonReader,
    podcasts: MutableList<Podcast>,
    emitSnapshot: suspend (List<Podcast>) -> Unit,
) {
    val gson = Gson()
    while (reader.hasNext()) {
        val feed = readTrendingFeed(reader, gson) ?: continue
        podcasts.add(toPodcastModel(feed))
        if (shouldEmitTrendingSnapshot(podcasts.size)) {
            emitSnapshot(podcasts.toList())
        }
    }
}

private fun readTrendingFeed(
    reader: JsonReader,
    gson: Gson,
): TrendingFeed? =
    try {
        gson.fromJson(reader, TrendingFeed::class.java)
    } catch (e: Exception) {
        android.util.Log.e("BoxCastRepo", "Stream: Feed parse error", e)
        null
    }

private fun PodcastRepository.toPodcastModel(feed: TrendingFeed): Podcast =
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

private fun shouldEmitTrendingSnapshot(podcastCount: Int): Boolean =
    podcastCount == 1 || podcastCount % 4 == 0
