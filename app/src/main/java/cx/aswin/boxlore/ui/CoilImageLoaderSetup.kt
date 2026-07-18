package cx.aswin.boxlore.ui

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Configures the process-wide Coil [ImageLoader] (memory + disk cache, OkHttp timeouts). */
object CoilImageLoaderSetup {
    fun install(context: Context) {
        val appContext = context.applicationContext
        val imageLoader = ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder().apply {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(20, TimeUnit.SECONDS)
                }.build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
