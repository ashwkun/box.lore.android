package cx.aswin.boxlore.core.rss

import android.util.Xml
import com.prof18.rssparser.RssParser
import cx.aswin.boxlore.core.data.database.PodcastEntity
import cx.aswin.boxlore.core.data.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Transcript
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ParsedRssFeed(
    val title: String,
    val author: String,
    val description: String?,
    val imageUrl: String?,
    val genre: String?,
    val podcastType: String,
    val podcastGuid: String?,
    val declaredUpdatedAt: Long?,
    val episodes: List<RssEpisodeEntity>,
)

data class RssFetchResult(
    val finalUrl: String,
    val etag: String?,
    val lastModified: String?,
    val body: ByteArray,
)

sealed interface RssFreshnessResult {
    data class Unchanged(
        val etag: String?,
        val lastModified: String?,
    ) : RssFreshnessResult

    data class Changed(
        val etag: String?,
        val lastModified: String?,
    ) : RssFreshnessResult

    data object Unsupported : RssFreshnessResult

    data class Failed(val cause: Throwable) : RssFreshnessResult
}

class RssFeedClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    suspend fun fetch(url: String): RssFetchResult {
        val normalizedUrl = RssIdGenerator.validateAndNormalizeFeedUrl(url)
        val request = Request.Builder()
            .url(normalizedUrl)
            .header("Accept", ACCEPT_HEADER)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return execute(request).use { response ->
            require(response.isSuccessful) {
                "Feed returned HTTP ${response.code}"
            }
            validateContentType(response)
            val body = response.body
            val declaredLength = body.contentLength()
            require(declaredLength < 0L || declaredLength <= MAX_FEED_BYTES) {
                "Feed is larger than ${MAX_FEED_BYTES / (1024 * 1024)} MB"
            }
            RssFetchResult(
                finalUrl = response.request.url.toString(),
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                body = readBounded(body.byteStream()),
            )
        }
    }

    suspend fun confirmHeadValidators(
        url: String,
        etag: String?,
        lastModified: String?,
    ): Boolean {
        if (etag.isNullOrBlank() && lastModified.isNullOrBlank()) return false
        val request = conditionalHeadRequest(url, etag, lastModified)
        return runCatching {
            execute(request).use { response -> response.code == HTTP_NOT_MODIFIED }
        }.getOrDefault(false)
    }

    suspend fun checkFreshness(podcast: PodcastEntity): RssFreshnessResult {
        val feedUrl = podcast.feedUrl
        if (!podcast.isRss ||
            podcast.rssRefreshCapability != PodcastEntity.RSS_REFRESH_HEAD_VALIDATORS ||
            feedUrl.isNullOrBlank()
        ) {
            return RssFreshnessResult.Unsupported
        }
        return try {
            execute(
                conditionalHeadRequest(
                    feedUrl,
                    podcast.feedEtag,
                    podcast.feedLastModified,
                ),
            ).use { response ->
                val currentEtag = response.header("ETag")
                val currentLastModified = response.header("Last-Modified")
                when {
                    response.code == HTTP_NOT_MODIFIED -> {
                        RssFreshnessResult.Unchanged(
                            etag = currentEtag ?: podcast.feedEtag,
                            lastModified = currentLastModified ?: podcast.feedLastModified,
                        )
                    }
                    response.isSuccessful &&
                        validatorsMatch(
                            podcast.feedEtag,
                            podcast.feedLastModified,
                            currentEtag,
                            currentLastModified,
                        ) -> {
                        RssFreshnessResult.Unchanged(currentEtag, currentLastModified)
                    }
                    response.isSuccessful &&
                        (!currentEtag.isNullOrBlank() || !currentLastModified.isNullOrBlank()) -> {
                        RssFreshnessResult.Changed(currentEtag, currentLastModified)
                    }
                    else -> RssFreshnessResult.Unsupported
                }
            }
        } catch (error: Exception) {
            RssFreshnessResult.Failed(error)
        }
    }

    suspend fun parse(
        feedUrl: String,
        bytes: ByteArray,
        podcastId: String = RssIdGenerator.podcastId(feedUrl),
    ): ParsedRssFeed {
        var libraryError: Exception? = null
        var customError: Exception? = null
        val libraryFeed = try {
            parseWithLibrary(bytes, podcastId)
        } catch (error: Exception) {
            libraryError = error
            null
        }
        val customFeed = try {
            parseCustom(bytes, podcastId)
        } catch (error: Exception) {
            customError = error
            null
        }
        return mergeParsedFeeds(libraryFeed, customFeed)
            ?: throw IllegalArgumentException(
                customError?.message
                    ?: libraryError?.message
                    ?: "Unable to parse this RSS feed",
                customError ?: libraryError,
            )
    }

    private suspend fun parseWithLibrary(
        bytes: ByteArray,
        podcastId: String,
    ): ParsedRssFeed {
        val channel = RssParser().parse(String(bytes, StandardCharsets.UTF_8))
        val title = channel.title?.trim().orEmpty()
        require(title.isNotBlank()) { "Feed has no podcast title" }
        val channelImage = channel.image?.url
            ?: channel.itunesChannelData?.image
        val episodes = channel.items.mapNotNull { item ->
            val itemTitle = item.title?.trim().orEmpty()
            val enclosureUrl = item.rawEnclosure?.url
            val mediaUrl = item.rawMediaContent?.url
            val audioUrl = item.audio?.takeIf(String::isNotBlank)
                ?: item.video?.takeIf(String::isNotBlank)
                ?: enclosureUrl?.takeIf {
                    isPlayableMedia(
                        url = it,
                        type = item.rawEnclosure?.type,
                        medium = null,
                    )
                }
                ?: mediaUrl?.takeIf {
                    isPlayableMedia(
                        url = it,
                        type = item.rawMediaContent?.type,
                        medium = item.rawMediaContent?.medium,
                    )
                }
            if (itemTitle.isBlank() || audioUrl.isNullOrBlank()) {
                null
            } else {
                val itunes = item.itunesItemData
                val description = listOfNotNull(
                    item.content,
                    item.description,
                    itunes?.summary,
                    itunes?.subtitle,
                ).filter(String::isNotBlank)
                    .maxByOrNull(String::length)
                    .orEmpty()
                    .cleanDescription()
                val publishedDate = parseDate(item.pubDate.orEmpty()) ?: 0L
                RssEpisodeEntity(
                    episodeId = RssIdGenerator.episodeIdForPodcast(
                        podcastId = podcastId,
                        guid = item.guid,
                        enclosureUrl = audioUrl,
                        publishedDate = publishedDate,
                        title = itemTitle,
                    ),
                    podcastId = podcastId,
                    guid = item.guid,
                    title = itemTitle,
                    description = description.take(MAX_DESCRIPTION_LENGTH),
                    audioUrl = audioUrl.trim(),
                    imageUrl = itunes?.image ?: item.image,
                    duration = parseDuration(itunes?.duration.orEmpty()),
                    publishedDate = publishedDate,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = itunes?.season?.toIntOrNull(),
                    episodeNumber = itunes?.episode?.toIntOrNull(),
                    episodeType = itunes?.episodeType,
                    enclosureType = item.rawEnclosure?.type
                        ?: item.rawMediaContent?.type,
                )
            }
        }
        require(episodes.isNotEmpty()) { "Feed has no playable episodes" }
        val itunes = channel.itunesChannelData
        return ParsedRssFeed(
            title = title,
            author = itunes?.author.orEmpty(),
            description = (channel.description ?: itunes?.summary)
                ?.cleanDescription(),
            imageUrl = channelImage,
            genre = itunes?.categories?.firstOrNull(),
            podcastType = itunes?.type
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it == "serial" || it == "episodic" }
                ?: "episodic",
            podcastGuid = null,
            declaredUpdatedAt = parseDate(channel.lastBuildDate.orEmpty()),
            episodes = episodes
                .distinctBy(RssEpisodeEntity::episodeId)
                .sortedByDescending(RssEpisodeEntity::publishedDate),
        )
    }

    private fun parseCustom(
        bytes: ByteArray,
        podcastId: String,
    ): ParsedRssFeed {
        val parser = Xml.newPullParser().apply {
            disableUnsafeXmlFeatures()
            runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
            setInput(ByteArrayInputStream(bytes), null)
        }
        val feed = MutableFeed()
        var currentEpisode: MutableEpisode? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val qualifiedName = parser.name.lowercase(Locale.ROOT)
                    val name = qualifiedName.substringAfter(':')
                    when {
                        name == "item" || name == "entry" -> {
                            currentEpisode = MutableEpisode()
                        }
                        currentEpisode != null -> {
                            parseEpisodeElement(
                                parser = parser,
                                name = name,
                                qualifiedName = qualifiedName,
                                episode = currentEpisode,
                            )
                        }
                        else -> handleChannelStartTag(parser, feed, name, qualifiedName)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.localName()
                    when (name) {
                        "item", "entry" -> {
                            currentEpisode?.toEntity(podcastId)?.let(feed.episodes::add)
                            currentEpisode = null
                        }
                        "image" -> feed.insideChannelImage = false
                        "author" -> feed.insideFeedAuthor = false
                    }
                }
            }
            parser.next()
        }

        require(feed.title.isNotBlank()) { "Feed has no podcast title" }
        require(feed.episodes.isNotEmpty()) { "Feed has no playable episodes" }
        return ParsedRssFeed(
            title = feed.title.trim(),
            author = feed.author.trim(),
            description = feed.description,
            imageUrl = feed.imageUrl,
            genre = feed.genre,
            podcastType = feed.podcastType,
            podcastGuid = feed.podcastGuid,
            declaredUpdatedAt = feed.declaredUpdatedAt,
            episodes = feed.episodes
                .distinctBy(RssEpisodeEntity::episodeId)
                .sortedByDescending(RssEpisodeEntity::publishedDate),
        )
    }

    /** Handles START_TAG events for channel-level (non-episode) feed metadata elements. */
    private fun handleChannelStartTag(
        parser: XmlPullParser,
        feed: MutableFeed,
        name: String,
        qualifiedName: String,
    ) {
        if (handleChannelImageOrAuthorTag(parser, feed, name)) return
        if (handleChannelTextTag(parser, feed, name)) return
        handleChannelMiscTag(parser, feed, name, qualifiedName)
    }

    /** Handles the channel `<image>`/`<author>` nesting, which share `feed`'s "inside X" flags. */
    private fun handleChannelImageOrAuthorTag(
        parser: XmlPullParser,
        feed: MutableFeed,
        name: String,
    ): Boolean {
        when {
            name == "image" && parser.depth > 2 -> {
                val href = parser.getAttributeValue(null, "href")
                    ?: parser.getAttributeValue(null, "url")
                if (!href.isNullOrBlank()) feed.imageUrl = href
                feed.insideChannelImage = href.isNullOrBlank()
            }
            name == "author" -> {
                feed.insideFeedAuthor = true
                val value = readSimpleText(parser)
                if (value.isNotBlank()) {
                    feed.author = value
                    feed.insideFeedAuthor = false
                }
            }
            feed.insideFeedAuthor && name == "name" -> {
                feed.author = readSimpleText(parser)
            }
            feed.insideChannelImage && name == "url" -> {
                feed.imageUrl = readSimpleText(parser)
            }
            else -> return false
        }
        return true
    }

    /** Handles simple text-content channel tags: title, description, managing editor. */
    private fun handleChannelTextTag(
        parser: XmlPullParser,
        feed: MutableFeed,
        name: String,
    ): Boolean {
        when {
            name == "title" && feed.title.isBlank() -> {
                feed.title = readSimpleText(parser)
            }
            name == "description" || name == "subtitle" -> {
                if (feed.description.isNullOrBlank()) {
                    feed.description = readSimpleText(parser).cleanDescription()
                }
            }
            name == "managingeditor" || name == "webmaster" -> {
                if (feed.author.isBlank()) feed.author = readSimpleText(parser)
            }
            else -> return false
        }
        return true
    }

    /** Handles the remaining channel tags: category, dates, podcast guid and type. */
    private fun handleChannelMiscTag(
        parser: XmlPullParser,
        feed: MutableFeed,
        name: String,
        qualifiedName: String,
    ) {
        when {
            name == "category" && feed.genre.isNullOrBlank() -> {
                feed.genre = parser.getAttributeValue(null, "text")
                    ?.takeIf(String::isNotBlank)
                    ?: readSimpleText(parser).takeIf(String::isNotBlank)
            }
            name == "lastbuilddate" || name == "updated" -> {
                feed.declaredUpdatedAt = parseDate(readSimpleText(parser))
            }
            qualifiedName == "podcast:guid" -> {
                feed.podcastGuid = readSimpleText(parser).takeIf(String::isNotBlank)
            }
            name == "type" -> {
                val type = readSimpleText(parser).lowercase(Locale.ROOT)
                if (type == "serial" || type == "episodic") feed.podcastType = type
            }
        }
    }

    private fun mergeParsedFeeds(
        libraryFeed: ParsedRssFeed?,
        customFeed: ParsedRssFeed?,
    ): ParsedRssFeed? {
        if (libraryFeed == null) return customFeed
        if (customFeed == null) return libraryFeed

        val customEpisodes = customFeed.episodes.associateBy(RssEpisodeEntity::episodeId)
        val libraryIds = libraryFeed.episodes.mapTo(mutableSetOf(), RssEpisodeEntity::episodeId)
        val mergedEpisodes = libraryFeed.episodes.map { libraryEpisode ->
            customEpisodes[libraryEpisode.episodeId]?.let { customEpisode ->
                libraryEpisode.copy(
                    guid = customEpisode.guid ?: libraryEpisode.guid,
                    title = customEpisode.title.takeIf(String::isNotBlank)
                        ?: libraryEpisode.title,
                    description = listOf(
                        libraryEpisode.description,
                        customEpisode.description,
                    ).maxByOrNull(String::length).orEmpty(),
                    imageUrl = customEpisode.imageUrl ?: libraryEpisode.imageUrl,
                    duration = customEpisode.duration.takeIf { it > 0 }
                        ?: libraryEpisode.duration,
                    publishedDate = customEpisode.publishedDate.takeIf { it > 0L }
                        ?: libraryEpisode.publishedDate,
                    chaptersUrl = customEpisode.chaptersUrl,
                    transcriptUrl = customEpisode.transcriptUrl,
                    transcripts = customEpisode.transcripts,
                    persons = customEpisode.persons,
                    seasonNumber = customEpisode.seasonNumber
                        ?: libraryEpisode.seasonNumber,
                    episodeNumber = customEpisode.episodeNumber
                        ?: libraryEpisode.episodeNumber,
                    episodeType = customEpisode.episodeType
                        ?: libraryEpisode.episodeType,
                    enclosureType = libraryEpisode.enclosureType
                        ?: customEpisode.enclosureType,
                )
            } ?: libraryEpisode
        } + customFeed.episodes.filterNot { it.episodeId in libraryIds }

        return ParsedRssFeed(
            title = customFeed.title.takeIf(String::isNotBlank) ?: libraryFeed.title,
            author = customFeed.author.takeIf(String::isNotBlank) ?: libraryFeed.author,
            description = customFeed.description ?: libraryFeed.description,
            imageUrl = customFeed.imageUrl ?: libraryFeed.imageUrl,
            genre = customFeed.genre ?: libraryFeed.genre,
            podcastType = customFeed.podcastType.takeIf { it == "serial" }
                ?: libraryFeed.podcastType,
            podcastGuid = customFeed.podcastGuid ?: libraryFeed.podcastGuid,
            declaredUpdatedAt = customFeed.declaredUpdatedAt
                ?: libraryFeed.declaredUpdatedAt,
            episodes = mergedEpisodes
                .distinctBy(RssEpisodeEntity::episodeId)
                .sortedByDescending(RssEpisodeEntity::publishedDate),
        )
    }

    private fun isPlayableMedia(
        url: String,
        type: String?,
        medium: String?,
    ): Boolean {
        val normalizedType = type?.lowercase(Locale.ROOT).orEmpty()
        val normalizedMedium = medium?.lowercase(Locale.ROOT).orEmpty()
        if (normalizedType.startsWith("audio/") || normalizedType.startsWith("video/")) {
            return true
        }
        if (normalizedMedium == "audio" || normalizedMedium == "video") return true
        val path = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return PLAYABLE_EXTENSIONS.any(path::endsWith)
    }

    private fun parseEpisodeElement(
        parser: XmlPullParser,
        name: String,
        qualifiedName: String,
        episode: MutableEpisode,
    ) {
        when {
            qualifiedName == "media:content" -> handleMediaContentTag(parser, episode)
            name == "title" -> episode.title = readSimpleText(parser)
            name == "description" ||
                name == "summary" ||
                name == "content" ||
                name == "encoded" -> handleEpisodeDescriptionTag(parser, episode)
            name == "guid" || name == "id" -> episode.guid = readSimpleText(parser)
            name == "pubdate" || name == "published" -> updateEpisodePublishedDate(parser, episode)
            name == "updated" && episode.publishedDate == 0L ->
                updateEpisodePublishedDate(parser, episode)
            name == "enclosure" -> handleEnclosureTag(parser, episode)
            name == "link" -> handleEpisodeLinkTag(parser, episode)
            name == "duration" -> episode.duration = parseDuration(readSimpleText(parser))
            name == "image" || name == "thumbnail" -> handleEpisodeImageTag(parser, episode)
            name == "season" -> episode.seasonNumber = readSimpleText(parser).toIntOrNull()
            name == "episode" -> episode.episodeNumber = readSimpleText(parser).toIntOrNull()
            name == "episodetype" -> episode.episodeType = readSimpleText(parser)
            name == "chapters" -> episode.chaptersUrl = parser.getAttributeValue(null, "url")
            name == "transcript" -> handleTranscriptTag(parser, episode)
            name == "person" -> handlePersonTag(parser, episode)
        }
    }

    private fun handleMediaContentTag(parser: XmlPullParser, episode: MutableEpisode) {
        val url = parser.getAttributeValue(null, "url")
        val type = parser.getAttributeValue(null, "type")
        val medium = parser.getAttributeValue(null, "medium")
        if (!url.isNullOrBlank() && isPlayableMedia(url, type, medium)) {
            episode.audioUrl = url
            episode.enclosureType = type
        }
    }

    private fun handleEpisodeDescriptionTag(parser: XmlPullParser, episode: MutableEpisode) {
        val text = readSimpleText(parser).cleanDescription()
        if (text.length > episode.description.length) episode.description = text
    }

    private fun updateEpisodePublishedDate(parser: XmlPullParser, episode: MutableEpisode) {
        parseDate(readSimpleText(parser))?.let { episode.publishedDate = it }
    }

    private fun handleEnclosureTag(parser: XmlPullParser, episode: MutableEpisode) {
        val url = parser.getAttributeValue(null, "url")
        val type = parser.getAttributeValue(null, "type")
        if (!url.isNullOrBlank() && isPlayableMedia(url, type, null)) {
            episode.audioUrl = url
            episode.enclosureType = type
        }
    }

    private fun handleEpisodeLinkTag(parser: XmlPullParser, episode: MutableEpisode) {
        val relation = parser.getAttributeValue(null, "rel")
        val href = parser.getAttributeValue(null, "href")
        val type = parser.getAttributeValue(null, "type")
        if (relation == "enclosure" && !href.isNullOrBlank() && isPlayableMedia(href, type, null)) {
            episode.audioUrl = href
            episode.enclosureType = type
        }
    }

    private fun handleEpisodeImageTag(parser: XmlPullParser, episode: MutableEpisode) {
        episode.imageUrl = parser.getAttributeValue(null, "href")
            ?: parser.getAttributeValue(null, "url")
            ?: readSimpleText(parser).takeIf(String::isNotBlank)
    }

    private fun handleTranscriptTag(parser: XmlPullParser, episode: MutableEpisode) {
        val url = parser.getAttributeValue(null, "url")
        if (!url.isNullOrBlank()) {
            val type = parser.getAttributeValue(null, "type").orEmpty()
            episode.transcripts += Transcript(url, type)
            if (episode.transcriptUrl.isNullOrBlank()) episode.transcriptUrl = url
        }
    }

    private fun handlePersonTag(parser: XmlPullParser, episode: MutableEpisode) {
        val role = parser.getAttributeValue(null, "role")
        val image = parser.getAttributeValue(null, "img")
        val href = parser.getAttributeValue(null, "href")
        val personName = readSimpleText(parser)
        if (personName.isNotBlank()) {
            episode.persons += Person(
                name = personName,
                role = role,
                img = image,
                href = href,
            )
        }
    }

    private fun conditionalHeadRequest(
        url: String,
        etag: String?,
        lastModified: String?,
    ): Request {
        val normalizedUrl = RssIdGenerator.validateAndNormalizeFeedUrl(url)
        return Request.Builder()
            .url(normalizedUrl)
            .header("Accept", ACCEPT_HEADER)
            .header("User-Agent", USER_AGENT)
            .apply {
                if (!etag.isNullOrBlank()) header("If-None-Match", etag)
                if (!lastModified.isNullOrBlank()) header("If-Modified-Since", lastModified)
            }
            .head()
            .build()
    }

    private fun execute(request: Request): Response {
        val response = httpClient.newCall(request).execute()
        if (!response.request.url.isHttps) {
            response.close()
            error("RSS feed redirects must stay on HTTPS")
        }
        return response
    }

    private fun XmlPullParser.disableUnsafeXmlFeatures() {
        runCatching { setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }

    private fun XmlPullParser.localName(): String =
        name.substringAfter(':').lowercase(Locale.ROOT)

    private fun readSimpleText(parser: XmlPullParser): String {
        val startDepth = parser.depth
        return runCatching {
            buildString {
                while (true) {
                    when (parser.nextToken()) {
                        XmlPullParser.TEXT,
                        XmlPullParser.CDSECT,
                        XmlPullParser.ENTITY_REF -> append(parser.text.orEmpty())
                        XmlPullParser.END_TAG -> if (parser.depth == startDepth) break
                        XmlPullParser.END_DOCUMENT -> break
                    }
                }
            }
                .trim()
        }.getOrDefault("")
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_FEED_BYTES) {
                "Feed is larger than ${MAX_FEED_BYTES / (1024 * 1024)} MB"
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun validateContentType(response: Response) {
        val contentType = response.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return
        require(
            contentType.contains("xml") ||
                contentType.contains("rss") ||
                contentType.contains("atom") ||
                contentType == "text/plain" ||
                contentType == "application/octet-stream",
        ) {
            "URL did not return an RSS or Atom feed"
        }
    }

    private fun validatorsMatch(
        previousEtag: String?,
        previousLastModified: String?,
        currentEtag: String?,
        currentLastModified: String?,
    ): Boolean {
        val etagMatches = !previousEtag.isNullOrBlank() && previousEtag == currentEtag
        val modifiedMatches = !previousLastModified.isNullOrBlank() &&
            previousLastModified == currentLastModified
        return etagMatches || modifiedMatches
    }

    private class MutableFeed {
        var title: String = ""
        var author: String = ""
        var description: String? = null
        var imageUrl: String? = null
        var genre: String? = null
        var podcastType: String = "episodic"
        var podcastGuid: String? = null
        var declaredUpdatedAt: Long? = null
        var insideChannelImage: Boolean = false
        var insideFeedAuthor: Boolean = false
        val episodes = mutableListOf<RssEpisodeEntity>()
    }

    private class MutableEpisode {
        var guid: String? = null
        var title: String = ""
        var description: String = ""
        var audioUrl: String = ""
        var imageUrl: String? = null
        var duration: Int = 0
        var publishedDate: Long = 0L
        var chaptersUrl: String? = null
        var transcriptUrl: String? = null
        var transcripts: List<Transcript> = emptyList()
        var persons: List<Person> = emptyList()
        var seasonNumber: Int? = null
        var episodeNumber: Int? = null
        var episodeType: String? = null
        var enclosureType: String? = null

        fun toEntity(podcastId: String): RssEpisodeEntity? {
            if (title.isBlank() || audioUrl.isBlank()) return null
            return RssEpisodeEntity(
                episodeId = RssIdGenerator.episodeIdForPodcast(
                    podcastId = podcastId,
                    guid = guid,
                    enclosureUrl = audioUrl,
                    publishedDate = publishedDate,
                    title = title,
                ),
                podcastId = podcastId,
                guid = guid,
                title = title.trim(),
                description = description.take(MAX_DESCRIPTION_LENGTH),
                audioUrl = audioUrl.trim(),
                imageUrl = imageUrl,
                duration = duration,
                publishedDate = publishedDate,
                chaptersUrl = chaptersUrl,
                transcriptUrl = transcriptUrl,
                transcripts = transcripts.takeIf(List<Transcript>::isNotEmpty),
                persons = persons.takeIf(List<Person>::isNotEmpty),
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeType = episodeType,
                enclosureType = enclosureType,
            )
        }
    }

    companion object {
        private const val MAX_FEED_BYTES = 25L * 1024L * 1024L
        private const val MAX_DESCRIPTION_LENGTH = 20_000
        private const val HTTP_NOT_MODIFIED = 304
        private const val USER_AGENT = "BoxLore/1.0 (Android; RSS reader)"
        private const val ACCEPT_HEADER =
            "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.5"
        private val PLAYABLE_EXTENSIONS = setOf(
            ".mp3",
            ".m4a",
            ".aac",
            ".ogg",
            ".opus",
            ".wav",
            ".mp4",
            ".m4v",
            ".webm",
            ".m3u8",
        )

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            // Never let OkHttp silently follow an HTTPS→HTTP downgrade redirect; execute()
            // re-validates the final scheme, but OkHttp would otherwise complete the request
            // over plaintext before that check ever runs.
            .followSslRedirects(false)
            .build()
    }
}

object RssIdGenerator {
    fun validateAndNormalizeFeedUrl(rawUrl: String): String {
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: error("Enter a valid RSS URL")
        require(parsed.scheme == "https") { "RSS feeds must use HTTPS" }
        return parsed.newBuilder().fragment(null).build().toString()
    }

    fun podcastId(feedUrl: String): String {
        val normalized = validateAndNormalizeFeedUrl(feedUrl)
        return "rss:${sha256(normalized).joinToString("") { "%02x".format(it) }}"
    }

    fun episodeId(
        feedUrl: String,
        guid: String?,
        enclosureUrl: String?,
        publishedDate: Long,
        title: String,
    ): String {
        return episodeIdForPodcast(
            podcastId = podcastId(feedUrl),
            guid = guid,
            enclosureUrl = enclosureUrl,
            publishedDate = publishedDate,
            title = title,
        )
    }

    fun episodeIdForPodcast(
        podcastId: String,
        guid: String?,
        enclosureUrl: String?,
        publishedDate: Long,
        title: String,
    ): String {
        require(podcastId.startsWith("rss:")) { "RSS podcast ID must use the rss: namespace" }
        val identity = guid?.trim()?.takeIf(String::isNotBlank)
            ?: enclosureUrl?.trim()?.takeIf(String::isNotBlank)
            ?: "$publishedDate\u0000${title.trim()}"
        val digest = sha256("$podcastId\u0000$identity")
        var positive = 0L
        repeat(Long.SIZE_BYTES) { index ->
            positive = (positive shl Byte.SIZE_BITS) or (digest[index].toLong() and 0xffL)
        }
        positive = positive and Long.MAX_VALUE
        if (positive == 0L) positive = 1L
        return (-positive).toString()
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
}

private fun String.cleanDescription(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun parseDuration(value: String): Int {
    val trimmed = value.trim()
    trimmed.toIntOrNull()?.let { return it.coerceAtLeast(0) }
    val parts = trimmed.split(':').mapNotNull(String::toIntOrNull)
    if (parts.isEmpty()) return 0
    return parts.reversed().foldIndexed(0) { index, total, part ->
        total + part * when (index) {
            0 -> 1
            1 -> 60
            else -> 3600
        }
    }.coerceAtLeast(0)
}

internal fun parseDate(value: String): Long? {
    if (value.isBlank()) return null
    value.toLongOrNull()?.let { numeric ->
        return if (numeric > 10_000_000_000L) numeric / 1000L else numeric
    }
    return sequenceOf(
        { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() },
        { OffsetDateTime.parse(value).toEpochSecond() },
        { Instant.parse(value).epochSecond },
    ).firstNotNullOfOrNull { parser -> runCatching(parser).getOrNull() }
}
