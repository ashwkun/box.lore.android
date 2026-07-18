package cx.aswin.boxlore.core.data

import android.content.Context
import android.util.Log
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import cx.aswin.boxlore.core.data.database.BoxLoreDatabase
import cx.aswin.boxlore.core.data.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.data.ranking.FeedbackTarget
import cx.aswin.boxlore.core.data.ranking.RankingAction
import cx.aswin.boxlore.core.data.ranking.RankingFeedbackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class DownloadRepository(
    private val context: Context,
    private val database: BoxLoreDatabase,
    private val rankingFeedbackRepository: RankingFeedbackRepository,
) {
    private val downloadManager: DownloadManager = getDownloadManager(context)
    
    init {
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: androidx.media3.exoplayer.offline.Download,
                finalException: Exception?
            ) {
                // Sync status with DB
                val state = download.state
                if (state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                    val fileSizeMb = if (download.contentLength > 0) download.contentLength / (1024f * 1024f) else 0f
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDownloadCompleted(fileSizeMb)
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        val existing = database.downloadedEpisodeDao().getDownload(download.request.id)
                        if (existing != null) {
                            val updated = existing.copy(
                                sizeBytes = download.contentLength,
                                localFilePath = "CACHED", // Marker that it is in Media3 Cache
                                status = DownloadedEpisodeEntity.STATUS_COMPLETED
                            )
                            database.downloadedEpisodeDao().insert(updated)
                        }
                    }
                } else if (state == androidx.media3.exoplayer.offline.Download.STATE_FAILED) {
                    val errorReason = finalException?.message ?: "Unknown Error"
                    cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDownloadFailed(errorReason)
                    
                     CoroutineScope(Dispatchers.IO).launch {
                        // Optional: Allow user to retry or just delete
                         database.downloadedEpisodeDao().delete(download.request.id)
                     }
                } else if (state == androidx.media3.exoplayer.offline.Download.STATE_REMOVING) {
                     CoroutineScope(Dispatchers.IO).launch {
                         database.downloadedEpisodeDao().delete(download.request.id)
                     }
                }
            }
        })
    }

    private fun downloadArtworkLocally(context: Context, imageUrl: String?, subDir: String, fileName: String): String? {
        if (imageUrl.isNullOrBlank()) return null
        try {
            val cleanUrlStr = if (imageUrl.startsWith("//")) "https:$imageUrl" else imageUrl
            val url = java.net.URI.create(cleanUrlStr).toURL()
            val dir = File(context.filesDir, subDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, fileName)
            url.openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("DownloadRepo", "Failed to download artwork: $imageUrl", e)
            return null
        }
    }

    private fun deleteLocalFileIfValid(path: String?) {
        if (path.isNullOrBlank()) return
        val prefix = "file://"
        if (path.startsWith("/") || path.startsWith(prefix)) {
            val cleanPath = path.removePrefix(prefix)
            try {
                val file = File(cleanPath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                android.util.Log.w("DownloadRepo", "Failed to delete file: $cleanPath", e)
            }
        }
    }

    fun addDownload(episode: Episode, podcast: Podcast, isSmartDownloaded: Boolean = false) {
        val downloadRequest = DownloadRequest.Builder(episode.id, android.net.Uri.parse(episode.audioUrl))
            .setCustomCacheKey(episode.id)
            .setData(
                // Serialize needed metadata to restore if app killed
                // Ideally use Proto or JSON. For now, we trust DB has details.
                "${podcast.id}|${podcast.title}|${episode.title}".toByteArray()
            )
            .build()
            
        try {
            DownloadService.sendAddDownload(
                context,
                mediaDownloadServiceClass(),
                downloadRequest,
                true
            )
        } catch (e: Exception) {
            android.util.Log.w("DownloadRepo", "Background service start blocked by OS. Adding directly to DownloadManager instance.", e)
            try {
                getDownloadManager(context).addDownload(downloadRequest)
            } catch (inner: Exception) {
                android.util.Log.e("DownloadRepo", "Failed to add download directly to DownloadManager", inner)
            }
        }
        
        android.util.Log.d("DownloadRepo", "Optimistically adding download: ${episode.id}")
        // Optimistically insert into DB as "Downloading"
        CoroutineScope(Dispatchers.IO).launch {
            val entity = try {
                android.util.Log.d("DownloadRepo", "Inserting optimistic download entity for ${episode.id}")
                
                val localEpisodeImg = downloadArtworkLocally(context, episode.imageUrl, "downloaded_artworks", "episode_${episode.id}.png") ?: episode.imageUrl
                val localPodcastImg = downloadArtworkLocally(context, podcast.imageUrl, "downloaded_artworks", "podcast_${podcast.id}.png") ?: podcast.imageUrl
                
                DownloadedEpisodeEntity(
                    episodeId = episode.id,
                    podcastId = podcast.id,
                    episodeTitle = episode.title,
                    episodeDescription = episode.description,
                    episodeImageUrl = localEpisodeImg,
                    podcastName = podcast.title,
                    podcastImageUrl = localPodcastImg,
                    durationMs = episode.duration * 1000L,
                    publishedDate = episode.publishedDate,
                    localFilePath = "", // Filled when done
                    downloadId = 0,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes = 0,
                    status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                    isSmartDownloaded = isSmartDownloaded
                )
            } catch (error: Exception) {
                android.util.Log.e("DownloadRepo", "Failed to prepare download ${episode.id}", error)
                return@launch
            }
            try {
                database.downloadedEpisodeDao().insert(entity)
            } catch (e: Exception) {
                android.util.Log.e("DownloadRepo", "Optimistic insert failed for ${episode.id}", e)
                return@launch
            }
            if (!isSmartDownloaded) {
                rankingFeedbackRepository.recordAction(
                    target = FeedbackTarget(
                        episodeId = episode.id,
                        podcastId = podcast.id,
                        genre = episode.podcastGenre ?: podcast.genre,
                    ),
                    action = RankingAction.MANUAL_DOWNLOAD,
                )
            }
            android.util.Log.d("DownloadRepo", "Optimistic insert successful for ${episode.id}")
        }
    }
    
    fun removeDownload(episodeId: String) {
        // Capture artwork paths BEFORE triggering removal to avoid a race with
        // the DownloadManager listener (which deletes the DB row on STATE_REMOVING).
        CoroutineScope(Dispatchers.IO).launch {
            var episodeImgPath: String? = null
            var podcastImgPath: String? = null
            var podcastId: String? = null
            try {
                val existing = database.downloadedEpisodeDao().getDownload(episodeId)
                if (existing != null) {
                    episodeImgPath = existing.episodeImageUrl
                    podcastImgPath = existing.podcastImageUrl
                    podcastId = existing.podcastId
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadRepo", "Failed to read artwork paths for $episodeId", e)
            }

            // Now send the remove request
            try {
                DownloadService.sendRemoveDownload(
                    context,
                    mediaDownloadServiceClass(),
                    episodeId,
                    false
                )
            } catch (e: Exception) {
                android.util.Log.w("DownloadRepo", "sendRemoveDownload failed for $episodeId", e)
            }

            // Clean up artwork files
            try {
                deleteLocalFileIfValid(episodeImgPath)

                // Only delete shared podcast artwork when no other episodes
                // from the same podcast still reference it.
                if (podcastId != null && podcastImgPath != null) {
                    val othersCount = database.downloadedEpisodeDao()
                        .countOthersByPodcastId(podcastId, episodeId)
                    if (othersCount == 0) {
                        deleteLocalFileIfValid(podcastImgPath)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadRepo", "Failed to clean up artwork files for $episodeId", e)
            }

            database.downloadedEpisodeDao().delete(episodeId)
        }
    }
    
    val downloads: Flow<List<DownloadedEpisodeEntity>> = database.downloadedEpisodeDao().getAllDownloads()

    /** Completed download episode ids — prefer over filtering [downloads] by Room status in features. */
    val completedDownloadIds: Flow<Set<String>> = downloads.map { list ->
        list.filter { it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
            .map { it.episodeId }
            .toSet()
    }

    /** Actively downloading episode ids — prefer over filtering [downloads] by Room status in features. */
    val downloadingEpisodeIds: Flow<Set<String>> = downloads.map { list ->
        list.filter { it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING }
            .map { it.episodeId }
            .toSet()
    }
    
    fun isDownloaded(episodeId: String): Flow<Boolean> = database.downloadedEpisodeDao().isDownloadedFlow(episodeId).map { it > 0 }
    
    fun isDownloading(episodeId: String): Flow<Boolean> = database.downloadedEpisodeDao().isDownloadingFlow(episodeId).map { it > 0 }

    companion object {
        @Volatile
        private var downloadManager: DownloadManager? = null
        private var cache: Cache? = null
        private var streamCache: Cache? = null
        private var databaseProvider: DatabaseProvider? = null
        private var streamDatabaseProvider: DatabaseProvider? = null

        private const val STREAM_CACHE_MAX_BYTES = 250L * 1024 * 1024 // 250 MB

        fun mediaDownloadServiceClass(): Class<out DownloadService> =
            cx.aswin.boxlore.core.data.ports.DownloadServiceLauncherHolder
                .require()
                .mediaDownloadServiceClass()

        fun getDownloadManager(context: Context): DownloadManager {
            return downloadManager ?: synchronized(this) {
                downloadManager ?: createDownloadManager(context).also { downloadManager = it }
            }
        }
        
        private fun createDownloadManager(context: Context): DownloadManager {
            val databaseProvider = getDatabaseProvider(context)
            val cache = getDownloadCache(context)
            val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(androidx.media3.common.util.Util.getUserAgent(context, "BoxLore"))
                .setAllowCrossProtocolRedirects(true)

            val dataSourceFactory = androidx.media3.datasource.DataSource.Factory {
                ThrottlingDataSource(httpDataSourceFactory.createDataSource())
            }
            
            return DownloadManager(
                context,
                databaseProvider,
                cache,
                dataSourceFactory,
                Executors.newFixedThreadPool(6)
            )
        }

        private fun getDatabaseProvider(context: Context): DatabaseProvider {
             return databaseProvider ?: StandaloneDatabaseProvider(context).also { databaseProvider = it }
        }

        /** Permanent cache for user-downloaded episodes. No eviction. */
        @Synchronized
        fun getDownloadCache(context: Context): Cache {
            return cache ?: run {
                val cacheDir = File(context.filesDir, "downloads")
                val evictor = NoOpCacheEvictor()
                val provider = getDatabaseProvider(context)
                SimpleCache(cacheDir, evictor, provider).also { cache = it }
            }
        }

        // Keep old name for backward compat
        @Synchronized
        fun getCache(context: Context): Cache = getDownloadCache(context)

        /** LRU-evicted cache for streaming playback. Auto-cleans when exceeding 250 MB. */
        @Synchronized
        fun getStreamCache(context: Context): Cache {
            return streamCache ?: run {
                val cacheDir = File(context.cacheDir, "stream_cache")
                val dbProvider = streamDatabaseProvider ?: StandaloneDatabaseProvider(context).also { streamDatabaseProvider = it }
                val evictor = androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(STREAM_CACHE_MAX_BYTES)
                SimpleCache(cacheDir, evictor, dbProvider).also { streamCache = it }
            }
        }

        /**
         * Media3's [Cache] has no key-rename API, so linking a Podcast Index download to its
         * RSS counterpart (a different episodeId) would otherwise orphan the already-downloaded
         * bytes under the old cache key. This copies the cached spans and content-length
         * metadata over to [newEpisodeId], frees the old resource, and best-effort re-registers
         * the download under the new id in Media3's own index so playback and the offline
         * library keep serving the cached asset instead of silently falling back to network.
         *
         * Safe to call for downloads that are not fully cached — it is then a no-op.
         */
        fun relinkDownloadCache(context: Context, oldEpisodeId: String, newEpisodeId: String) {
            if (oldEpisodeId == newEpisodeId) return
            val cache = getDownloadCache(context)
            // Tracked separately from content length: ContentMetadata.getContentLength() can
            // legitimately be LENGTH_UNSET even after the spans were copied successfully.
            val movedSuccessfully = runCatching {
                val spans = cache.getCachedSpans(oldEpisodeId)
                if (spans.isEmpty()) return@runCatching false
                spans.filter { it.isCached }.forEach { span ->
                    copyCachedSpanToNewKey(cache, span, newEpisodeId)
                }
                val contentLength = androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(cache.getContentMetadata(oldEpisodeId))
                if (contentLength > 0) {
                    cache.applyContentMetadataMutations(
                        newEpisodeId,
                        androidx.media3.datasource.cache.ContentMetadataMutations.setContentLength(
                            androidx.media3.datasource.cache.ContentMetadataMutations(),
                            contentLength,
                        ),
                    )
                }
                true
            }.onFailure {
                Log.w("DownloadRepo", "Failed to move cached bytes from $oldEpisodeId to $newEpisodeId", it)
            }.getOrDefault(false)

            if (!movedSuccessfully) return
            // Re-key the Media3 download index first, and only drop the old cache resource once
            // that succeeds, so a failure here never leaves the new key without an index entry.
            relinkDownloadIndexEntry(context, oldEpisodeId, newEpisodeId)
            runCatching { cache.removeResource(oldEpisodeId) }
                .onFailure { Log.w("DownloadRepo", "Failed to release old cache resource $oldEpisodeId", it) }
        }

        /**
         * Copies one cached [span] to [newEpisodeId], acquiring the write hole via
         * [Cache.startReadWrite] first as required by [Cache]'s locking contract — writing
         * through [Cache.startFile] without that lock can race with another active writer for
         * the same key/offset/length.
         */
        private fun copyCachedSpanToNewKey(
            cache: Cache,
            span: androidx.media3.datasource.cache.CacheSpan,
            newEpisodeId: String,
        ) {
            val destSpan = cache.startReadWrite(newEpisodeId, span.position, span.length)
            if (destSpan.isCached) return // already present at the destination key/offset
            try {
                val newFile = cache.startFile(newEpisodeId, span.position, span.length)
                span.file?.copyTo(newFile, overwrite = true)
                cache.commitFile(newFile, span.length)
            } finally {
                cache.releaseHoleSpan(destSpan)
            }
        }

        /** Best-effort: re-registers the Media3 download index entry under the new id. */
        private fun relinkDownloadIndexEntry(context: Context, oldEpisodeId: String, newEpisodeId: String) {
            runCatching {
                val manager = getDownloadManager(context)
                val existing = manager.downloadIndex.getDownload(oldEpisodeId) ?: return@runCatching
                val newRequest = DownloadRequest.Builder(newEpisodeId, existing.request.uri)
                    .setCustomCacheKey(newEpisodeId)
                    .apply { existing.request.mimeType?.let(::setMimeType) }
                    .setData(existing.request.data)
                    .build()
                manager.addDownload(newRequest)
                manager.removeDownload(oldEpisodeId)
            }.onFailure {
                Log.w(
                    "DownloadRepo",
                    "Failed to re-key Media3 download index from $oldEpisodeId to $newEpisodeId",
                    it,
                )
            }
        }
    }
}
