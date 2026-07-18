package cx.aswin.boxlore.core.data.backup

import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.RssPodcastRepository
import cx.aswin.boxlore.core.data.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.data.SubscriptionRepository
import cx.aswin.boxlore.core.data.database.PodcastEntity
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.data.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.data.ranking.AdaptiveRankingBackup
import cx.aswin.boxlore.core.data.ranking.AdaptiveRankingRepository
import kotlinx.coroutines.flow.first
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import android.util.Log
import cx.aswin.boxlore.core.data.BuildConfig

data class GlobalPreferencesBackup(
    val region: String? = null,
    val themeConfig: String? = null,
    val themeBrand: String? = null,
    val surfaceStyle: String? = null,
    val useDynamicColor: Boolean? = null,
    val subscriptionSort: String? = null,
    val latestEpisodesSortUseSmart: Boolean? = null,
    val skipBehavior: String? = null,
    val skipBeginningMs: Long? = null,
    val skipEndingMs: Long? = null,
    val seekBackwardMs: Long? = null,
    val seekForwardMs: Long? = null,
    val hideCompletedInFeeds: Boolean? = null,
    val hideCompletedInShowDetails: Boolean? = null,
    val hideCompletedInHome: Boolean? = null,
    val hideCompletedInSubs: Boolean? = null,
    val smartDownloadsEnabled: Boolean? = null,
    val smartDownloadsMaxEpisodes: Int? = null,
    val smartDownloadsStorageBudget: Long? = null,
    val smartDownloadsWifiOnly: Boolean? = null,
    val smartDownloadsChargingOnly: Boolean? = null,
    val smartDownloadsCleanupRule: String? = null,
    val autoDownloadWifiOnly: Boolean? = null,
    val autoDownloadMaxEpisodes: Int? = null,
    val autoDownloadDeleteCompleted: Boolean? = null
)

data class BoxCastBackup(
    val version: Int = 5,
    val subscriptions: List<PodcastEntity>,
    val history: List<ListeningHistoryEntity>,
    val globalPreferences: GlobalPreferencesBackup? = null,
    val adaptiveRanking: AdaptiveRankingBackup? = null,
)

data class OpmlFeed(
    val title: String,
    val xmlUrl: String
)

class LibraryBackupManager(
    private val subscriptionRepository: SubscriptionRepository,
    private val listeningHistory: ListeningHistoryBackupPort,
    private val podcastRepository: PodcastRepository,
    private val userPrefs: cx.aswin.boxlore.core.data.UserPreferencesRepository? = null,
    context: android.content.Context,
    private val adaptiveRankingRepository: AdaptiveRankingRepository =
        SharedAppDependenciesHolder.require().adaptiveRankingRepository,
    private val rssPodcastRepository: RssPodcastRepository =
        SharedAppDependenciesHolder.require().rssPodcastRepository,
) {
    private val context = context.applicationContext
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    suspend fun exportLibraryAsJson(): String {
        val subscriptions = subscriptionRepository.getAllSubscribedPodcasts().first()
        val allHistory = listeningHistory.getAllHistory().first()
        
        val globalPrefs = if (userPrefs != null) {
            GlobalPreferencesBackup(
                region = userPrefs.regionStream.first(),
                themeConfig = userPrefs.themeConfigStream.first(),
                themeBrand = userPrefs.themeBrandStream.first(),
                surfaceStyle = userPrefs.surfaceStyleStream.first(),
                useDynamicColor = userPrefs.useDynamicColorStream.first(),
                subscriptionSort = userPrefs.subscriptionSortStream.first(),
                latestEpisodesSortUseSmart = userPrefs.latestEpisodesSortUseSmartStream.first(),
                skipBehavior = userPrefs.skipBehaviorStream.first(),
                skipBeginningMs = userPrefs.skipBeginningMsStream.first(),
                skipEndingMs = userPrefs.skipEndingMsStream.first(),
                seekBackwardMs = userPrefs.seekBackwardMsStream.first(),
                seekForwardMs = userPrefs.seekForwardMsStream.first(),
                hideCompletedInFeeds = userPrefs.hideCompletedInFeedsStream.first(),
                hideCompletedInShowDetails = userPrefs.hideCompletedInShowDetailsStream.first(),
                hideCompletedInHome = userPrefs.hideCompletedInHomeStream.first(),
                hideCompletedInSubs = userPrefs.hideCompletedInSubsStream.first(),
                smartDownloadsEnabled = userPrefs.smartDownloadsEnabledStream.first(),
                smartDownloadsMaxEpisodes = userPrefs.smartDownloadsMaxEpisodesStream.first(),
                smartDownloadsStorageBudget = userPrefs.smartDownloadsStorageBudgetStream.first(),
                smartDownloadsWifiOnly = userPrefs.smartDownloadsWifiOnlyStream.first(),
                smartDownloadsChargingOnly = userPrefs.smartDownloadsChargingOnlyStream.first(),
                smartDownloadsCleanupRule = userPrefs.smartDownloadsCleanupRuleStream.first(),
                autoDownloadWifiOnly = userPrefs.autoDownloadWifiOnlyStream.first(),
                autoDownloadMaxEpisodes = userPrefs.autoDownloadMaxEpisodesStream.first(),
                autoDownloadDeleteCompleted = userPrefs.autoDownloadDeleteCompletedStream.first()
            )
        } else null

        val rankingBackup = adaptiveRankingRepository.exportBackup()
        val backup = BoxCastBackup(
            version = 5,
            subscriptions = subscriptions,
            history = allHistory,
            globalPreferences = globalPrefs,
            adaptiveRanking = rankingBackup,
        )
        return gson.toJson(backup)
    }

    suspend fun exportLibraryAsOpml(): String {
        val subscriptions = subscriptionRepository.getAllSubscribedPodcasts().first()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>Boxlore Subscriptions</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        sb.append("    <outline text=\"Subscriptions\" title=\"Subscriptions\">\n")
        for (entity in subscriptions) {
            val title = escapeXml(entity.title)
            val feedUrl = escapeXml(
                entity.feedUrl ?: "${BuildConfig.BOXCAST_API_BASE_URL}/episodes?id=${entity.podcastId}"
            )
            sb.append("      <outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"$feedUrl\" />\n")
        }
        sb.append("    </outline>\n")
        sb.append("  </body>\n")
        sb.append("</opml>")
        return sb.toString()
    }

    private fun escapeXml(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    suspend fun importLibraryFromJson(jsonString: String): Pair<Int, Boolean> {
        return try {
            val backup = gson.fromJson(jsonString, BoxCastBackup::class.java)
            
            // 0. Restore global preferences
            backup.globalPreferences?.let { prefs ->
                userPrefs?.let { up ->
                    prefs.region?.let { up.setRegion(it) }
                    prefs.themeConfig?.let { up.setThemeConfig(it) }
                    prefs.themeBrand?.let { up.setThemeBrand(it) }
                    prefs.surfaceStyle?.let { up.setSurfaceStyle(it) }
                    prefs.useDynamicColor?.let { up.setUseDynamicColor(it) }
                    prefs.subscriptionSort?.let { up.setSubscriptionSort(it) }
                    prefs.latestEpisodesSortUseSmart?.let { up.setLatestEpisodesSortUseSmart(it) }
                    prefs.skipBehavior?.let { up.setSkipBehavior(it) }
                    prefs.skipBeginningMs?.let { up.setSkipBeginningMs(it) }
                    prefs.skipEndingMs?.let { up.setSkipEndingMs(it) }
                    prefs.seekBackwardMs?.let { up.setSeekBackwardMs(it) }
                    prefs.seekForwardMs?.let { up.setSeekForwardMs(it) }
                    prefs.hideCompletedInFeeds?.let { up.setHideCompletedInFeeds(it) }
                    prefs.hideCompletedInShowDetails?.let { up.setHideCompletedInShowDetails(it) }
                    prefs.hideCompletedInHome?.let { up.setHideCompletedInHome(it) }
                    prefs.hideCompletedInSubs?.let { up.setHideCompletedInSubs(it) }
                    prefs.smartDownloadsEnabled?.let { enabled ->
                        up.setSmartDownloadsEnabled(enabled)
                        if (enabled) {
                            val wifiOnly = prefs.smartDownloadsWifiOnly ?: true
                            val chargingOnly = prefs.smartDownloadsChargingOnly ?: false
                            cx.aswin.boxlore.core.data.SmartDownloadManager.schedulePeriodicSync(context, wifiOnly, chargingOnly)
                        } else {
                            cx.aswin.boxlore.core.data.SmartDownloadManager.cancelPeriodicSync(context)
                        }
                    }
                    prefs.smartDownloadsMaxEpisodes?.let { up.setSmartDownloadsMaxEpisodes(it) }
                    prefs.smartDownloadsStorageBudget?.let { up.setSmartDownloadsStorageBudget(it) }
                    prefs.smartDownloadsWifiOnly?.let { up.setSmartDownloadsWifiOnly(it) }
                    prefs.smartDownloadsChargingOnly?.let { up.setSmartDownloadsChargingOnly(it) }
                    prefs.smartDownloadsCleanupRule?.let { up.setSmartDownloadsCleanupRule(it) }
                    prefs.autoDownloadWifiOnly?.let { up.setAutoDownloadWifiOnly(it) }
                    prefs.autoDownloadMaxEpisodes?.let { up.setAutoDownloadMaxEpisodes(it) }
                    prefs.autoDownloadDeleteCompleted?.let { up.setAutoDownloadDeleteCompleted(it) }
                }
            }

            val importedIds = mutableListOf<String>()
            
            // 1. Restore subscriptions
            for (entity in backup.subscriptions) {
                if (entity.sourceType == PodcastEntity.SOURCE_RSS) {
                    val feedUrl = entity.feedUrl
                    val rssPodcast = if (!feedUrl.isNullOrBlank()) {
                        runCatching {
                            rssPodcastRepository
                                .addSubscription(feedUrl)
                                .podcast
                        }.onFailure { error ->
                            Log.e(
                                "JSON_IMPORT",
                                "RSS restore failed for ${entity.title}; feed must be re-added",
                                error,
                            )
                        }.getOrNull()
                    } else {
                        null
                    }
                    if (rssPodcast == null) continue
                    val subscribedRssPodcast = rssPodcast.copy(
                        preferredSort = entity.preferredSort,
                        linkedPodcastIndexId = entity.linkedPodcastIndexId,
                        skipBeginningOverrideMs = entity.skipBeginningOverrideMs,
                        skipEndingOverrideMs = entity.skipEndingOverrideMs,
                    )
                    subscriptionRepository.subscribe(subscribedRssPodcast)

                    // Restore per-podcast settings & FCM registrations, same as the
                    // Podcast Index branch below — RSS subscriptions must not skip this.
                    if (entity.notificationsEnabled) {
                        subscriptionRepository.setNotificationsEnabled(subscribedRssPodcast, true)
                    }
                    if (entity.autoDownloadEnabled) {
                        subscriptionRepository.setAutoDownloadEnabled(subscribedRssPodcast.id, true)
                    }
                    if (backup.version >= 4) {
                        subscriptionRepository.setPlaybackSkipOverrides(
                            subscribedRssPodcast.id,
                            entity.skipBeginningOverrideMs,
                            entity.skipEndingOverrideMs,
                        )
                    }
                    importedIds.add(subscribedRssPodcast.id)
                    continue
                }
                // Assuming we have a way to save entity directly or we re-map to model
                // Since SubscriptionRepository expects a Podcast model to subscribe,
                // let's construct a domain Podcast model and pass it.
                val podcast = cx.aswin.boxlore.core.model.Podcast(
                    id = (entity.podcastId as String?) ?: "", // Cast to prevent Kotlin from optimizing out the null-check
                    title = (entity.title as String?) ?: "Unknown",
                    artist = (entity.author as String?) ?: "Unknown",
                    imageUrl = (entity.imageUrl as String?) ?: "",
                    description = entity.description,
                    genre = entity.genre ?: "Podcast",
                    type = (entity.type as String?) ?: "episodic",
                    latestEpisode = entity.latestEpisode,
                    subscribedAt = entity.subscribedAt,
                    podcastGuid = entity.podcastGuid,
                    fundingUrl = entity.fundingUrl,
                    fundingMessage = entity.fundingMessage,
                    medium = entity.medium,
                    hasValue = entity.hasValue,
                    updateFrequency = entity.updateFrequency,
                    location = entity.location,
                    license = entity.license,
                    isLocked = entity.isLocked,
                    preferredSort = entity.preferredSort,
                    skipBeginningOverrideMs = entity.skipBeginningOverrideMs,
                    skipEndingOverrideMs = entity.skipEndingOverrideMs,
                    sourceType = (entity.sourceType as String?)
                        ?: PodcastEntity.SOURCE_PODCAST_INDEX,
                    feedUrl = entity.feedUrl,
                    rssRefreshCapability = (entity.rssRefreshCapability as String?)
                        ?: PodcastEntity.RSS_REFRESH_MANUAL,
                    rssCatalogStale = entity.rssCatalogStale,
                    rssHasNewEpisodes = entity.rssHasNewEpisodes,
                    linkedPodcastIndexId = entity.linkedPodcastIndexId,
                )
                subscriptionRepository.subscribe(podcast)
                
                // Restore per-podcast settings & FCM registrations
                if (entity.notificationsEnabled) {
                    subscriptionRepository.setNotificationsEnabled(podcast, true)
                }
                if (entity.autoDownloadEnabled) {
                    subscriptionRepository.setAutoDownloadEnabled(podcast.id, true)
                }
                if (backup.version >= 4) {
                    subscriptionRepository.setPlaybackSkipOverrides(
                        podcast.id,
                        entity.skipBeginningOverrideMs,
                        entity.skipEndingOverrideMs,
                    )
                }
                importedIds.add(podcast.id)
            }
            
            // 2. Restore playback histories (liked episodes)
            for (entity in backup.history) {
                if (entity.podcastId.startsWith("rss:") && entity.podcastId !in importedIds) {
                    continue
                }
                // Reconstruct safe entity to prevent null crashes on non-nullable String fields
                // Primitives (Long, Boolean) are safe as Gson initializes them to 0/false if missing
                val safeEntity = cx.aswin.boxlore.core.data.database.ListeningHistoryEntity(
                    episodeId = (entity.episodeId as String?) ?: "",
                    podcastId = (entity.podcastId as String?) ?: "",
                    episodeTitle = (entity.episodeTitle as String?) ?: "Unknown",
                    episodeImageUrl = entity.episodeImageUrl,
                    podcastImageUrl = entity.podcastImageUrl,
                    episodeAudioUrl = entity.episodeAudioUrl,
                    podcastName = (entity.podcastName as String?) ?: "Unknown",
                    progressMs = entity.progressMs,
                    durationMs = entity.durationMs,
                    isCompleted = entity.isCompleted,
                    isLiked = entity.isLiked,
                    lastPlayedAt = entity.lastPlayedAt,
                    isDirty = entity.isDirty,
                    syncedAt = entity.syncedAt,
                    enclosureType = entity.enclosureType,
                    episodeDescription = entity.episodeDescription
                )
                listeningHistory.upsertHistoryEntity(safeEntity)
            }

            // 3. Restore the complete on-device learning state when present. Older backups
            // remain compatible because this versioned field is optional.
            backup.adaptiveRanking?.let { rankingBackup ->
                adaptiveRankingRepository.restoreBackup(rankingBackup)
            }
            
            // 4. Trigger check for new episodes
            if (importedIds.isNotEmpty()) {
                try {
                    val syncedMap = podcastRepository.syncSubscriptions(importedIds)
                    for ((id, ep) in syncedMap) {
                        subscriptionRepository.updateLatestEpisode(id, ep)
                    }
                } catch (e: Exception) {
                    Log.e("JSON_IMPORT", "Failed to sync episodes", e)
                }
            }
            
            val hasNotificationsEnabled = backup.subscriptions.any { it.notificationsEnabled || it.autoDownloadEnabled }
            Pair(importedIds.size, hasNotificationsEnabled)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(-1, false)
        }
    }

    suspend fun importFromOpml(inputStream: InputStream): Int {
        return try {
            parseOpmlFeeds(inputStream).count { feed ->
                importSingleOpmlFeed(feed) != null
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to import OPML", e)
            -1
        }
    }

    fun parseOpmlFeeds(inputStream: InputStream): List<OpmlFeed> {
        val feeds = mutableListOf<OpmlFeed>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                    val title = parser.getAttributeValue(null, "text") ?: parser.getAttributeValue(null, "title")
                    
                    if (xmlUrl != null && title != null) {
                        feeds.add(OpmlFeed(title, xmlUrl))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to parse OPML feeds", e)
        }
        return feeds
    }

    suspend fun importSingleOpmlFeed(feed: OpmlFeed): cx.aswin.boxlore.core.model.Podcast? {
        try {
            runCatching {
                rssPodcastRepository
                    .addSubscription(feed.xmlUrl)
                    .podcast
            }.onFailure { error ->
                Log.w(
                    "OPML_IMPORT",
                    "Direct RSS import failed for ${feed.title}; trying Podcast Index",
                    error,
                )
            }.getOrNull()?.let { return it }

            var matchedPodcast: cx.aswin.boxlore.core.model.Podcast? = null
            
            // 1. Try URL lookup since it is exact and fast
            try {
                val encodedUrl = java.net.URLEncoder.encode(feed.xmlUrl, "UTF-8")
                matchedPodcast = podcastRepository.getPodcastDetails("url:$encodedUrl")
            } catch (e: Exception) {
                Log.e("OPML_IMPORT", "Failed URL lookup for: ${feed.xmlUrl}", e)
            }
            
            // 2. Fallback to searching by title if URL lookup returned null
            if (matchedPodcast == null) {
                val results = podcastRepository.searchPodcasts(feed.title)
                if (results.isNotEmpty()) {
                    matchedPodcast = results.first()
                }
            }
            
            if (matchedPodcast != null) {
                subscriptionRepository.subscribe(matchedPodcast)
                // Sync latest episode so it shows up correctly in the home list
                try {
                    val syncedMap = podcastRepository.syncSubscriptions(listOf(matchedPodcast.id))
                    for ((id, ep) in syncedMap) {
                        subscriptionRepository.updateLatestEpisode(id, ep)
                    }
                } catch (e: Exception) {
                    Log.e("OPML_IMPORT", "Failed to sync episodes for: ${matchedPodcast.title}", e)
                }
                return matchedPodcast
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to import single feed: ${feed.title}", e)
        }
        return null
    }

    suspend fun markAllEpisodesCompleted(podcast: cx.aswin.boxlore.core.model.Podcast) {
        try {
            // Get complete backlog of episodes (bypassing initial limit)
            val episodes = podcastRepository.getEpisodes(podcast.id)
            if (episodes.isNotEmpty()) {
                listeningHistory.markAllEpisodesCompleted(
                    episodes = episodes,
                    podcastId = podcast.id,
                    podcastTitle = podcast.title,
                    podcastImageUrl = podcast.imageUrl
                )
            }
        } catch (e: Exception) {
            Log.e("OPML_IMPORT", "Failed to mark all episodes completed for: ${podcast.title}", e)
        }
    }
}
