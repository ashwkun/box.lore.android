package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.AutoCollageFolderLogic
import cx.aswin.boxlore.core.playback.AutoCollagePrewarmPolicy
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueSources
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.RankingSurface
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/**
 * Prewarms Android Auto folder collage artwork for [BoxLorePlaybackService].
 */
internal class AutoCollagePrewarmer(
    private val context: Context,
    private val database: BoxLoreDatabase,
    private val queueRepository: QueueRepository,
    private val smartQueueSources: SmartQueueSources,
    private val adaptiveCandidateScorer: AdaptiveCandidateScorer,
    private val toAutoPodcast: (PodcastEntity) -> Podcast,
    private val mediaSessionProvider: () -> MediaLibrarySession?,
    private val onCollagesReady: (Map<String, Uri>) -> Unit,
) {
    private val mutex = Mutex()
    private val lastPrewarmAtMs = AtomicLong(0L)

    suspend fun prewarm(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!AutoCollagePrewarmPolicy.shouldRun(force, lastPrewarmAtMs.get(), now)) {
            return
        }
        mutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!AutoCollagePrewarmPolicy.shouldRun(force, lastPrewarmAtMs.get(), lockedNow)) {
                return
            }
            runPrewarm()
            lastPrewarmAtMs.set(System.currentTimeMillis())
        }
    }

    private suspend fun runPrewarm() {
        try {
            val snapshot = loadSnapshot()
            val mixtape = resolveMixtape(snapshot)
            val folders = snapshot.buildFolderInputs(mixtape)
            val uris =
                AutoCollageGenerator.generateAllCollages(
                    context = context,
                    folderImages = folders.mapValues { AutoCollageFolderLogic.imagesOf(it.value) },
                    folderContentKeys = folders.mapValues { AutoCollageFolderLogic.keysOf(it.value) },
                )
            onCollagesReady(uris)
            notifyBrowseTree(snapshot.subscriptions.size, snapshot.resumeItems.size)
        } catch (error: Exception) {
            Log.w("AutoBrowse", "Unable to prewarm Android Auto artwork", error)
        }
    }

    private suspend fun loadSnapshot(): PrewarmSnapshot {
        val history = database.listeningHistoryDao().getRecentHistoryList(300)
        val resumeItems = database.listeningHistoryDao().getResumeItemsList()
        val subscriptions = database.podcastDao().getSubscribedPodcastsList()
        val downloads = database.downloadedEpisodeDao().getCompletedDownloads(8)
        val queue = queueRepository.getQueueSnapshot()
        return PrewarmSnapshot(
            history = history,
            resumeItems = resumeItems,
            subscriptions = subscriptions,
            downloads = downloads,
            queue = queue,
        )
    }

    private suspend fun resolveMixtape(snapshot: PrewarmSnapshot): MixtapeEngine.Result {
        var mixtape =
            MixtapeEngine.build(
                subscriptions = snapshot.subscriptions.map(toAutoPodcast),
                history = snapshot.history,
                adaptiveRanking =
                    MixtapeEngine.AdaptiveRanking(
                        scorer = adaptiveCandidateScorer,
                        surface = RankingSurface.ANDROID_AUTO,
                    ),
            )
        if (mixtape.episodes.size >= 3) return mixtape
        val recommendations =
            runCatching {
                withTimeout(6_000L) {
                    smartQueueSources.getPersonalizedRecommendations(
                        history = smartQueueSources.getHistoryForRecommendations(25),
                        interests = smartQueueSources.getInterests(),
                        country = smartQueueSources.getRegion(),
                        subscribedPodcastIds = snapshot.subscriptions.map { it.podcastId },
                        subscribedGenres =
                            snapshot.subscriptions.mapNotNull { it.genre }.distinct(),
                    )
                }
            }.getOrDefault(emptyList())
        return MixtapeEngine.build(
            subscriptions = snapshot.subscriptions.map(toAutoPodcast),
            history = snapshot.history,
            recommendations = recommendations,
            adaptiveRanking =
                MixtapeEngine.AdaptiveRanking(
                    scorer = adaptiveCandidateScorer,
                    surface = RankingSurface.ANDROID_AUTO,
                ),
        )
    }

    private fun notifyBrowseTree(
        subscriptionCount: Int,
        resumeCount: Int,
    ) {
        val session = mediaSessionProvider()
        session?.notifyChildrenChanged(AutoBrowseContract.ROOT_ID, 4, null)
        session?.notifyChildrenChanged(AutoBrowseContract.HOME_ID, 3, null)
        session?.notifyChildrenChanged(
            AutoBrowseContract.LIBRARY_ID,
            subscriptionCount + 2,
            null,
        )
        session?.notifyChildrenChanged(AutoBrowseContract.DISCOVER_ID, 3, null)
        session?.notifyChildrenChanged(AutoBrowseContract.HOME_CONTINUE_ID, resumeCount, null)
    }

    private data class PrewarmSnapshot(
        val history: List<ListeningHistoryEntity>,
        val resumeItems: List<ListeningHistoryEntity>,
        val subscriptions: List<PodcastEntity>,
        val downloads: List<DownloadedEpisodeEntity>,
        val queue: List<Episode>,
    ) {
        fun buildFolderInputs(mixtape: MixtapeEngine.Result): Map<String, List<AutoCollageFolderLogic.ArtPair>> {
            val historyPairs =
                history.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(
                        it.episodeId,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    )
                }
            val resumePairs =
                resumeItems.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(
                        it.episodeId,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    )
                }
            val subscriptionPairs =
                subscriptions.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(it.podcastId, it.imageUrl)
                }
            val downloadPairs =
                downloads.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(
                        it.episodeId,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    )
                }
            val queuePairs =
                queue.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(it.id, it.imageUrl ?: it.podcastImageUrl)
                }
            val newEpisodePairs =
                subscriptions.mapNotNull { podcast ->
                    val episode = podcast.latestEpisode
                    AutoCollageFolderLogic.pairOrNull(
                        episode?.id ?: podcast.podcastId,
                        episode?.imageUrl ?: podcast.imageUrl,
                    )
                }
            val mixtapePairs =
                mixtape.podcasts.mapNotNull { podcast ->
                    val episode = podcast.latestEpisode ?: return@mapNotNull null
                    AutoCollageFolderLogic.pairOrNull(
                        episode.id,
                        episode.imageUrl ?: episode.podcastImageUrl ?: podcast.imageUrl,
                    )
                }
            val drivePairs =
                AutoCollageFolderLogic.takeAligned(queuePairs + subscriptionPairs)
            return mapOf(
                AutoBrowseContract.HOME_ID to
                    AutoCollageFolderLogic.takeAligned(historyPairs + newEpisodePairs),
                AutoBrowseContract.LIBRARY_ID to AutoCollageFolderLogic.takeAligned(subscriptionPairs),
                AutoBrowseContract.DOWNLOADS_ID to AutoCollageFolderLogic.takeAligned(downloadPairs),
                AutoBrowseContract.DISCOVER_ID to
                    AutoCollageFolderLogic.takeAligned(subscriptionPairs.asReversed()),
                AutoBrowseContract.HOME_CONTINUE_ID to AutoCollageFolderLogic.takeAligned(resumePairs),
                AutoBrowseContract.HOME_QUEUE_ID to AutoCollageFolderLogic.takeAligned(queuePairs),
                AutoBrowseContract.HOME_NEW_EPISODES_ID to
                    AutoCollageFolderLogic.takeAligned(newEpisodePairs),
                AutoBrowseContract.HOME_DRIVE_MIX_ID to AutoCollageFolderLogic.takeAligned(mixtapePairs),
                AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID to drivePairs,
                AutoBrowseContract.DISCOVER_TIME_PICKS_ID to emptyList(),
                AutoBrowseContract.DISCOVER_GENRES_ID to emptyList(),
            )
        }
    }
}
