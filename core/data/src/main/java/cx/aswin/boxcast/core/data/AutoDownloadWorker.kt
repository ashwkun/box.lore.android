package cx.aswin.boxcast.core.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cx.aswin.boxcast.core.data.database.BoxLoreDatabase
import cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity
import cx.aswin.boxcast.core.data.database.PodcastEntity
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.flow.first

class AutoDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private fun PodcastEntity.toPodcast(): Podcast {
        return Podcast(
            id = this.podcastId,
            title = this.title,
            artist = this.author ?: "",
            imageUrl = this.imageUrl ?: "",
            fallbackImageUrl = this.latestEpisode?.imageUrl ?: "",
            description = this.description,
            genre = this.genre ?: "Podcast",
            type = this.type,
            latestEpisode = this.latestEpisode,
            subscribedAt = this.subscribedAt,
            podcastGuid = this.podcastGuid,
            fundingUrl = this.fundingUrl,
            fundingMessage = this.fundingMessage,
            medium = this.medium,
            hasValue = this.hasValue,
            updateFrequency = this.updateFrequency,
            location = this.location,
            license = this.license,
            isLocked = this.isLocked,
            preferredSort = this.preferredSort,
            notificationsEnabled = this.notificationsEnabled,
            autoDownloadEnabled = this.autoDownloadEnabled
        )
    }

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
            return Result.success()
        }

        try {
            val apiBaseUrl = "https://api.aswin.cx"
            val publicKey = "wisby"
            val app = context.applicationContext as android.app.Application

            val podcastRepository = PodcastRepository(apiBaseUrl, publicKey, app)
            val downloadRepository = DownloadRepository(app, database)

            // Fetch full episode metadata
            Log.i("BoxLore_BackgroundTrace", "[Worker] Fetching episode metadata from repository for episodeId: $episodeId...")
            val episode = podcastRepository.getEpisode(episodeId)
            if (episode == null) {
                Log.e("BoxLore_BackgroundTrace", "[Worker] Failed to fetch episode metadata for episodeId: $episodeId. Retrying later.")
                return Result.retry()
            }

            Log.i("BoxLore_BackgroundTrace", "[Worker] Fetched episode metadata successfully: '${episode.title}' (${episode.audioUrl})")

            // Convert to domain Podcast model
            val podcast = podcastEntity.toPodcast()

            // Enforce max auto-downloaded episodes per podcast rule dynamically from user preferences
            val userPrefs = UserPreferencesRepository(context)
            val maxAllowed = userPrefs.autoDownloadMaxEpisodesStream.first()
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

            // Trigger the download request (isSmartDownloaded = false, since it is a deterministic auto-download)
            Log.i("BoxLore_BackgroundTrace", "[Worker] Enqueueing download request via DownloadRepository for '${episode.title}'...")
            downloadRepository.addDownload(episode, podcast, isSmartDownloaded = false)
            Log.i("BoxLore_BackgroundTrace", "[Worker] SUCCESS! Enqueued auto-download for episode: ${episode.title} ($episodeId)")
            return Result.success()
        } catch (e: Exception) {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Exception encountered during auto-download", e)
            return Result.retry()
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_PODCAST_ID = "podcast_id"
    }
}
