package cx.aswin.boxcast.core.data

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
import cx.aswin.boxcast.core.data.database.BoxCastDatabase
import cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity
import cx.aswin.boxcast.core.data.service.MediaDownloadService
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class DownloadRepository(
    private val context: Context,
    private val database: BoxCastDatabase
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
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDownloadCompleted(fileSizeMb)
                    
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
                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDownloadFailed(errorReason)
                    
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

    fun addDownload(episode: Episode, podcast: Podcast) {
        val downloadRequest = DownloadRequest.Builder(episode.id, android.net.Uri.parse(episode.audioUrl))
            .setCustomCacheKey(episode.id)
            .setData(
                // Serialize needed metadata to restore if app killed
                // Ideally use Proto or JSON. For now, we trust DB has details.
                "${podcast.id}|${podcast.title}|${episode.title}".toByteArray()
            )
            .build()
            
        DownloadService.sendAddDownload(
            context,
            MediaDownloadService::class.java,
            downloadRequest,
            true
        )
        
        android.util.Log.d("DownloadRepo", "Optimistically adding download: ${episode.id}")
        // Optimistically insert into DB as "Downloading"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.d("DownloadRepo", "Inserting optimistic download entity for ${episode.id}")
                val entity = DownloadedEpisodeEntity(
                    episodeId = episode.id,
                    podcastId = podcast.id,
                    episodeTitle = episode.title,
                    episodeDescription = episode.description,
                    episodeImageUrl = episode.imageUrl,
                    podcastName = podcast.title,
                    podcastImageUrl = podcast.imageUrl,
                    durationMs = episode.duration * 1000L,
                    publishedDate = episode.publishedDate,
                    localFilePath = "", // Filled when done
                    downloadId = 0,
                    downloadedAt = System.currentTimeMillis(),
                    sizeBytes = 0,
                    status = DownloadedEpisodeEntity.STATUS_DOWNLOADING
                )
                database.downloadedEpisodeDao().insert(entity)
                android.util.Log.d("DownloadRepo", "Optimistic insert successful for ${episode.id}")
            } catch (e: Exception) {
                android.util.Log.e("DownloadRepo", "Optimistic insert failed for ${episode.id}", e)
            }
        }
    }
    
    fun removeDownload(episodeId: String) {
         DownloadService.sendRemoveDownload(
            context,
            MediaDownloadService::class.java,
            episodeId,
            false
        )
        CoroutineScope(Dispatchers.IO).launch {
            database.downloadedEpisodeDao().delete(episodeId)
        }
    }
    
    val downloads: Flow<List<DownloadedEpisodeEntity>> = database.downloadedEpisodeDao().getAllDownloads()
    
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

        fun getDownloadManager(context: Context): DownloadManager {
            return downloadManager ?: synchronized(this) {
                downloadManager ?: createDownloadManager(context).also { downloadManager = it }
            }
        }
        
        private fun createDownloadManager(context: Context): DownloadManager {
            val databaseProvider = getDatabaseProvider(context)
            val cache = getDownloadCache(context)
            val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(androidx.media3.common.util.Util.getUserAgent(context, "BoxCast"))
                .setAllowCrossProtocolRedirects(true)
            
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
    }
}
