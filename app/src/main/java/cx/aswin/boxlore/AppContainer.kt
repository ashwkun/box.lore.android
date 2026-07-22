package cx.aswin.boxlore

import android.content.Context
import cx.aswin.boxlore.connectivity.AndroidConnectivityObserver
import cx.aswin.boxlore.core.catalog.InstallReferrerManager
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.RoomEpisodeOfflineLookup
import cx.aswin.boxlore.core.catalog.RoomLocalCatalog
import cx.aswin.boxlore.core.catalog.SharedAppDependencies
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.SmartDownloadSyncPort
import cx.aswin.boxlore.core.catalog.privacy.ConsentManager
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import cx.aswin.boxlore.core.domain.ports.EpisodeOfflineLookupPort
import cx.aswin.boxlore.core.domain.ports.HistoryRecommendationSource
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.downloads.DownloadsDependencies
import cx.aswin.boxlore.core.downloads.SmartDownloadManager
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncher
import cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncherHolder
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.QueueManager
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.service.MediaDownloadService
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.ranking.RankingRuntimeControls
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker

/**
 * Application-scoped composition root for shared DB / repositories / managers.
 *
 * Construction order (invariant):
 * DB → RSS/ranking peers → PodcastRepository → QueueRepository → PlaybackRepository
 * → QueueManager → SmartDownloadManager.
 *
 * Ranking/RSS are [create]+[install]ed here (not via production getInstance call sites).
 * Workers/services consume the same instances via
 * [cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder] and
 * [cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder].
 */
class AppContainer(
    context: Context,
    apiBaseUrl: String,
    publicKey: String,
    /**
     * Optional pre-built prefs instance so [BoxLoreApplication] can keep a single
     * [UserPreferencesRepository] (theme cache / engagement) without a second DataStore client.
     */
    sharedUserPreferences: UserPreferencesRepository? = null,
) : SharedAppDependencies,
    DownloadsDependencies {
    private val appContext = context.applicationContext

    /** Process-scoped online/offline for NavHost offline UX. */
    val connectivityObserver: AndroidConnectivityObserver =
        AndroidConnectivityObserver(appContext).also { it.start() }

    val connectivityStatus: ConnectivityStatusPort get() = connectivityObserver

    override val database: BoxLoreDatabase by lazy {
        BoxLoreDatabase.getDatabase(appContext)
    }

    /** Feature / nav local podcast access — prefer this over injecting [database]. */
    val localCatalogPort: LocalCatalogPort by lazy {
        RoomLocalCatalog(database)
    }

    /** Episode Info offline hydration — prefer this over injecting [database]. */
    val episodeOfflineLookupPort: EpisodeOfflineLookupPort by lazy {
        RoomEpisodeOfflineLookup(database)
    }

    /** Single install path for RSS; production callers must not call getInstance. */
    override val rssPodcastRepository: RssPodcastRepository by lazy {
        RssPodcastRepository.create(appContext, database).also(RssPodcastRepository::install)
    }

    /** Single install path for adaptive ranking; production callers must not call getInstance. */
    override val adaptiveRankingRepository: AdaptiveRankingRepository by lazy {
        AdaptiveRankingRepository.create(appContext).also(AdaptiveRankingRepository::install)
    }

    override val rankingRuntimeControls: RankingRuntimeControls by lazy {
        RankingRuntimeControls.create(appContext).also(RankingRuntimeControls::install)
    }

    override val rankingFeedbackRepository: RankingFeedbackRepository by lazy {
        RankingFeedbackRepository.create(adaptiveRankingRepository).also(RankingFeedbackRepository::install)
    }

    override val adaptiveCandidateScorer: AdaptiveCandidateScorer by lazy {
        AdaptiveCandidateScorer
            .create(adaptiveRankingRepository, rankingRuntimeControls)
            .also(AdaptiveCandidateScorer::install)
    }

    init {
        // Playback owns MediaDownloadService; downloads starts it via this launcher (no Class.forName).
        DownloadServiceLauncherHolder.instance =
            DownloadServiceLauncher { MediaDownloadService::class.java }
    }

    override val podcastRepository: PodcastRepository by lazy {
        PodcastRepository(
            baseUrl = apiBaseUrl,
            publicKey = publicKey,
            context = appContext,
            rssRepository = rssPodcastRepository,
        )
    }

    val queueRepository: QueueRepository by lazy {
        QueueRepository(database, podcastRepository)
    }

    val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(
            context = appContext,
            listeningHistoryDao = database.listeningHistoryDao(),
            listeningSessionDao = database.listeningSessionDao(),
            listeningRollupDao = database.listeningRollupDao(),
            listeningInsightsMaintenance = database.listeningInsightsMaintenance(),
            queueRepository = queueRepository,
            podcastRepository = podcastRepository,
            rankingFeedbackRepository = rankingFeedbackRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    override val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(
            context = appContext,
            database = database,
            rankingFeedbackRepository = rankingFeedbackRepository,
        ).also { _ ->
            // Wire the DownloadCacheRelinker into RssPodcastRepository so the RSS module
            // does not need a compile-time dependency on :core:downloads.
            rssPodcastRepository.setDownloadCacheRelinker(
                DownloadCacheRelinker { oldId, newId ->
                    DownloadRepository.relinkDownloadCache(appContext, oldId, newId)
                },
            )
        }
    }

    override val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(database.podcastDao())
    }

    override val userPreferencesRepository: UserPreferencesRepository =
        sharedUserPreferences ?: UserPreferencesRepository(appContext)

    val consentManager: ConsentManager by lazy {
        ConsentManager(appContext)
    }

    val queueManager: QueueManager by lazy {
        QueueManager(queueRepository, playbackRepository)
    }

    override val historyRecommendationSource: HistoryRecommendationSource by lazy {
        cx.aswin.boxlore.core.playback.DefaultSmartQueueSources(
            context = appContext,
            database = database,
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    override val smartDownloadManager: SmartDownloadManager by lazy {
        SmartDownloadManager(
            context = appContext,
            database = database,
            podcastRepository = podcastRepository,
            historyRecommendationSource = historyRecommendationSource,
            downloadRepository = downloadRepository,
            subscriptionRepository = subscriptionRepository,
            userPrefs = userPreferencesRepository,
            adaptiveScorer = adaptiveCandidateScorer,
        ).also {
            // Wire SmartDownloadSyncPort so LibraryBackupManager (in :core:data) can schedule
            // WorkManager periodic work without a data→downloads compile edge.
            SmartDownloadSyncPort.schedulePeriodicSync = { wifiOnly, chargingOnly ->
                SmartDownloadManager.schedulePeriodicSync(appContext, wifiOnly, chargingOnly)
            }
            SmartDownloadSyncPort.cancelPeriodicSync = {
                SmartDownloadManager.cancelPeriodicSync(appContext)
            }
        }
    }

    val installReferrerManager: InstallReferrerManager by lazy {
        InstallReferrerManager(appContext).also { manager ->
            manager.onInstallReferrerResolved = { channel, raw ->
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackInstallChannelAttributed(
                    installChannel = channel,
                    referrerRaw = raw,
                )
            }
        }
    }
}
