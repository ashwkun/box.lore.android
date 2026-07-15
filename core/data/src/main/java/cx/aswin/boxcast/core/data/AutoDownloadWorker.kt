package cx.aswin.boxcast.core.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cx.aswin.boxcast.core.data.database.BoxLoreDatabase
import cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity
import cx.aswin.boxcast.core.data.database.PodcastEntity
import cx.aswin.boxcast.core.model.Episode
import kotlinx.coroutines.flow.first

class AutoDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: run {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Failed: KEY_EPISODE_ID missing")
            return Result.failure()
        }
        val podcastId = inputData.getString(KEY_PODCAST_ID) ?: run {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Failed: KEY_PODCAST_ID missing")
            return Result.failure()
        }

        Log.i("BoxLore_BackgroundTrace", "[Worker] AutoDownloadWorker execution started for podcastId: $podcastId, episodeId: $episodeId")

        val context = applicationContext
        val database = BoxLoreDatabase.getDatabase(context)

        // 1. Verify that auto-download is enabled for this podcast
        val podcastEntity = database.podcastDao().getPodcast(podcastId)
        if (podcastEntity == null) {
            Log.w("BoxLore_BackgroundTrace", "[Worker] Podcast $podcastId not found in local database. Skipping auto-download.")
            return Result.success()
        }
        if (podcastEntity.isRss) {
            Log.i("BoxLore_BackgroundTrace", "[Worker] RSS podcasts do not support release-triggered auto-downloads.")
            return Result.success()
        }
        
        Log.i("BoxLore_BackgroundTrace", "[Worker] Local podcast check: autoDownloadEnabled=${podcastEntity.autoDownloadEnabled}, notificationsEnabled=${podcastEntity.notificationsEnabled}, title='${podcastEntity.title}'")
        
        if (!podcastEntity.autoDownloadEnabled) {
            Log.w("BoxLore_BackgroundTrace", "[Worker] Auto-download is NOT enabled for podcast '${podcastEntity.title}' ($podcastId). Skipping.")
            return Result.success()
        }

        // 2. Check if the episode is already downloaded or active
        val existingDownload = database.downloadedEpisodeDao().getDownload(episodeId)
        if (existingDownload != null && existingDownload.status in listOf(
                DownloadedEpisodeEntity.STATUS_QUEUED,
                DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                DownloadedEpisodeEntity.STATUS_COMPLETED
            )) {
            Log.i("BoxLore_BackgroundTrace", "[Worker] Episode $episodeId is already in status '${existingDownload.status}'. Skipping download.")
            if (existingDownload.isSmartDownloaded) {
                Log.i("BoxLore_BackgroundTrace", "[Worker] Promoting smart-downloaded episode $episodeId to standard auto-download.")
                database.downloadedEpisodeDao().insert(existingDownload.copy(isSmartDownloaded = false))
            }
            return Result.success()
        }

        try {
            val apiBaseUrl = BuildConfig.BOXCAST_API_BASE_URL
            val publicKey = BuildConfig.BOXCAST_PUBLIC_KEY
            val app = context.applicationContext as android.app.Application

            val podcastRepository = PodcastRepository(apiBaseUrl, publicKey, app)
            val downloadRepository = DownloadRepository(app, database)

            // Fetch full episode metadata with fallbacks (delegated to private helper)
            Log.i("BoxLore_BackgroundTrace", "[Worker] Fetching episode metadata from repository for episodeId: $episodeId...")
            val episode = fetchEpisodeMetadata(podcastRepository, podcastEntity, podcastId, episodeId)

            // Validate episode data before enqueuing — empty audioUrl means all metadata sources failed
            if (episode.audioUrl.isBlank()) {
                Log.e("BoxLore_BackgroundTrace", "[Worker] Cannot enqueue download for '$episodeId': audioUrl is blank. All metadata sources failed.")
                return Result.failure()
            }

            Log.i("BoxLore_BackgroundTrace", "[Worker] Fetched episode metadata successfully: '${episode.title}' (${episode.audioUrl})")

            // Convert to domain Podcast model
            val podcast = podcastEntity.toPodcast()

            // Trigger the download request (isSmartDownloaded = false, since it is a deterministic auto-download)
            Log.i("BoxLore_BackgroundTrace", "[Worker] Enqueueing download request via DownloadRepository for '${episode.title}'...")
            downloadRepository.addDownload(episode, podcast, isSmartDownloaded = false)
            Log.i("BoxLore_BackgroundTrace", "[Worker] SUCCESS! Enqueued auto-download for episode: ${episode.title} ($episodeId)")

            // Post-download quota trim: enforce max episodes AFTER adding, to prevent race conditions
            val userPrefs = UserPreferencesRepository(context)
            val maxAllowed = userPrefs.autoDownloadMaxEpisodesStream.first()
            enforceMaxDownloadsQuota(database, downloadRepository, podcastId, maxAllowed)

            return Result.success()
        } catch (e: java.io.IOException) {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Network error during auto-download (will retry)", e)
            return Result.retry()
        } catch (e: Exception) {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Non-retryable error during auto-download", e)
            return Result.failure()
        }
    }

    private suspend fun fetchEpisodeMetadata(
        podcastRepository: PodcastRepository,
        podcastEntity: PodcastEntity,
        podcastId: String,
        episodeId: String
    ): Episode {
        var episode = podcastRepository.getEpisode(episodeId)
        if (episode == null) {
            Log.w("BoxLore_BackgroundTrace", "[Worker] Direct getEpisode failed for $episodeId. Attempting paginated list fallback for podcast $podcastId...")
            val page = podcastRepository.getEpisodesPaginated(podcastId, limit = 50)
            episode = page.episodes.find { it.id == episodeId }
        }
        if (episode == null) {
            val latest = podcastEntity.latestEpisode
            if (latest != null) {
                Log.w("BoxLore_BackgroundTrace", "[Worker] Using local podcastEntity.latestEpisode fallback for $episodeId.")
                episode = latest.copy(id = episodeId)
            } else {
                Log.w("BoxLore_BackgroundTrace", "[Worker] Constructing emergency fallback episode for $episodeId.")
                episode = Episode(
                    id = episodeId,
                    title = "New Episode",
                    description = podcastEntity.description ?: "",
                    audioUrl = "",
                    imageUrl = podcastEntity.imageUrl ?: "",
                    podcastImageUrl = podcastEntity.imageUrl ?: "",
                    podcastTitle = podcastEntity.title,
                    podcastId = podcastId,
                    duration = 0,
                    publishedDate = System.currentTimeMillis() / 1000L
                )
            }
        }
        return episode
    }

    private suspend fun enforceMaxDownloadsQuota(
        database: BoxLoreDatabase,
        downloadRepository: DownloadRepository,
        podcastId: String,
        maxAllowed: Int
    ) {
        val allDownloads = database.downloadedEpisodeDao().getAllDownloadsSync()
        val podcastAutoDownloads = allDownloads.filter { 
            it.podcastId == podcastId && !it.isSmartDownloaded && it.status in listOf(
                DownloadedEpisodeEntity.STATUS_COMPLETED,
                DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                DownloadedEpisodeEntity.STATUS_QUEUED
            )
        }
        
        Log.i("BoxLore_BackgroundTrace", "[Worker] Quota Check: currently retain ${podcastAutoDownloads.size} auto-downloads (Max allowed: $maxAllowed)")
        
        if (podcastAutoDownloads.size >= maxAllowed) {
            val oldest = podcastAutoDownloads.minByOrNull { it.downloadedAt }
            if (oldest != null) {
                Log.i("BoxLore_BackgroundTrace", "[Worker] Quota reached ($maxAllowed). Deleting oldest download '${oldest.episodeTitle}' (${oldest.episodeId})")
                downloadRepository.removeDownload(oldest.episodeId)
            }
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_PODCAST_ID = "podcast_id"
    }
}
