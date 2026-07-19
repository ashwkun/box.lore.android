package cx.aswin.boxlore.core.testing.fakes

import cx.aswin.boxlore.core.domain.RssSubscriptionResult
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import cx.aswin.boxlore.core.domain.ports.EpisodeOfflineLookupPort
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.domain.ports.OfflineEpisodeSnapshot
import cx.aswin.boxlore.core.domain.ports.PodcastCatalogPort
import cx.aswin.boxlore.core.domain.ports.RankingResetPort
import cx.aswin.boxlore.core.domain.ports.RssSubscriptionPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.testing.TestFixtures

/** Controllable [LocalCatalogPort] for hermetic ViewModel / worker tests. */
class FakeLocalCatalogPort(
    private val byId: MutableMap<String, Podcast> = mutableMapOf(),
    private val rssLinks: MutableMap<String, Podcast> = mutableMapOf(),
) : LocalCatalogPort {
    var upsertCalls = 0
    val upserted = mutableListOf<Podcast>()

    fun put(podcast: Podcast) {
        byId[podcast.id] = podcast
    }

    fun linkRss(
        podcastIndexId: String,
        rssPodcast: Podcast,
    ) {
        rssLinks[podcastIndexId] = rssPodcast
    }

    override suspend fun getLocalPodcast(id: String): Podcast? = byId[id]

    override suspend fun getSubscribedRssLinkedTo(id: String): Podcast? = rssLinks[id]

    override suspend fun upsertSubscribedPodcast(podcast: Podcast) {
        upsertCalls++
        upserted += podcast
        byId[podcast.id] = podcast
    }
}

/** Controllable [PodcastCatalogPort] for Info / catalog ViewModel tests. */
class FakePodcastCatalogPort(
    var details: Podcast? = TestFixtures.podcast(),
    var episode: Episode? = TestFixtures.episode(),
    var episodes: List<Episode> = listOf(TestFixtures.episode()),
    var detailsError: Exception? = null,
    var episodeError: Exception? = null,
    var episodesError: Exception? = null,
) : PodcastCatalogPort {
    var detailsCalls = 0
    var episodeCalls = 0
    var episodesCalls = 0
    var lastDetailsId: String? = null
    var lastEpisodeId: String? = null
    var lastEpisodesFeedId: String? = null

    override suspend fun getPodcastDetails(feedId: String): Podcast? {
        detailsCalls++
        lastDetailsId = feedId
        detailsError?.let { throw it }
        return details
    }

    override suspend fun getEpisode(episodeId: String): Episode? {
        episodeCalls++
        lastEpisodeId = episodeId
        episodeError?.let { throw it }
        return episode
    }

    override suspend fun getEpisodes(feedId: String): List<Episode> {
        episodesCalls++
        lastEpisodesFeedId = feedId
        episodesError?.let { throw it }
        return episodes
    }
}

/** Controllable [EpisodeOfflineLookupPort]. */
class FakeEpisodeOfflineLookup(
    var fromDownload: OfflineEpisodeSnapshot? = null,
    var fromHistory: OfflineEpisodeSnapshot? = null,
) : EpisodeOfflineLookupPort {
    var downloadCalls = 0
    var historyCalls = 0

    override suspend fun fromDownload(episodeId: String): OfflineEpisodeSnapshot? {
        downloadCalls++
        return fromDownload
    }

    override suspend fun fromHistory(episodeId: String): OfflineEpisodeSnapshot? {
        historyCalls++
        return fromHistory
    }
}

/** Controllable [RssSubscriptionPort] for Settings / onboarding hermetic tests. */
class FakeRssSubscriptionPort(
    var result: RssSubscriptionResult =
        RssSubscriptionResult(
            podcast = TestFixtures.rssPodcast(),
            episodeCount = 3,
            automaticUpdateChecksSupported = true,
        ),
    var addError: Exception? = null,
    var confirmError: Exception? = null,
) : RssSubscriptionPort {
    var addCalls = 0
    var confirmCalls = 0
    var lastAddUrl: String? = null
    var lastConfirmRssId: String? = null
    var lastConfirmIndexId: String? = null

    override suspend fun addSubscription(rawUrl: String): RssSubscriptionResult {
        addCalls++
        lastAddUrl = rawUrl
        addError?.let { throw it }
        return result
    }

    override suspend fun confirmPodcastIndexLink(
        rssPodcastId: String,
        podcastIndexId: String,
    ): Podcast {
        confirmCalls++
        lastConfirmRssId = rssPodcastId
        lastConfirmIndexId = podcastIndexId
        confirmError?.let { throw it }
        return result.podcast
    }
}

/** Controllable [RankingResetPort]. */
class FakeRankingResetPort(
    var result: Boolean = true,
) : RankingResetPort {
    var resetCalls = 0

    override suspend fun reset(): Boolean {
        resetCalls++
        return result
    }
}

/** Controllable [HistoryRecommendationSource]. */
class FakeHistoryRecommendationSource(
    var items: List<HistoryItem> = listOf(TestFixtures.historyItem()),
) : HistoryRecommendationSource {
    var calls = 0
    var lastLimit: Int? = null

    override suspend fun getHistoryForRecommendations(limit: Int): List<HistoryItem> {
        calls++
        lastLimit = limit
        return items.take(limit)
    }
}

/** Controllable [ConnectivityStatusPort]. */
class FakeConnectivityStatusPort(
    var online: Boolean = true,
) : ConnectivityStatusPort {
    var calls = 0

    override fun isOnline(): Boolean {
        calls++
        return online
    }
}
