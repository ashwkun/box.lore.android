package cx.aswin.boxcast.core.network

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }
    private val contentType = "application/json".toMediaType()
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("BoxCastAPI", message)
    }.apply {
        level = if (cx.aswin.boxcast.core.network.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder().apply {
        addInterceptor(loggingInterceptor)
        connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    }.build()

    /**
     * BoxCast API via Cloudflare Worker proxy
     * Base URL injected from BuildConfig at runtime
     */
    fun createBoxLoreApi(baseUrl: String, context: android.content.Context): BoxLoreApi {
        val cacheSize = 50L * 1024L * 1024L // 50 MiB
        val cache = okhttp3.Cache(context.cacheDir, cacheSize)

        // Create a new client sharing the same connection pool/interceptors but with cache
        val cacheClient = okHttpClient.newBuilder()
            .cache(cache)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(cacheClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(BoxLoreApi::class.java)
    }
}
