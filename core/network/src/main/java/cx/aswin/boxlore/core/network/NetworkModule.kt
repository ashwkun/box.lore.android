package cx.aswin.boxlore.core.network

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

    /**
     * Supplies a Firebase App Check token for outgoing API requests. Set by the
     * app module at startup (keeps this module free of Firebase dependencies).
     * Must return null quickly on any failure so requests proceed without the
     * header (fail-open): the Worker only observes tokens for now.
     */
    @Volatile
    var appCheckTokenProvider: (() -> String?)? = null

    /**
     * App version (BuildConfig.VERSION_NAME), set by the app module at startup.
     * Sent as X-App-Version so the proxy can slice App Check adoption by build.
     */
    @Volatile
    var appVersion: String? = null

    private val appCheckInterceptor =
        okhttp3.Interceptor { chain ->
            val token =
                try {
                    appCheckTokenProvider?.invoke()
                } catch (e: Exception) {
                    Log.w("BoxCastAPI", "App Check token fetch failed; proceeding without", e)
                    null
                }
            val builder = chain.request().newBuilder()
            if (!token.isNullOrEmpty()) {
                builder.header("X-Firebase-AppCheck", token)
            }
            appVersion?.let { builder.header("X-App-Version", it) }
            chain.proceed(builder.build())
        }

    private val loggingInterceptor =
        HttpLoggingInterceptor { message ->
            Log.d("BoxCastAPI", message)
        }.apply {
            level =
                if (cx.aswin.boxlore.core.network.BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.HEADERS
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            redactHeader("X-Firebase-AppCheck")
            redactHeader("X-App-Key")
        }

    private val okHttpClient =
        OkHttpClient
            .Builder()
            .apply {
                addInterceptor(appCheckInterceptor)
                addInterceptor(loggingInterceptor)
                connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            }.build()

    /**
     * BoxCast API via Cloudflare Worker proxy.
     * Base URL injected from BuildConfig at runtime.
     */
    fun createBoxLoreApi(
        baseUrl: String,
        context: android.content.Context,
    ): BoxLoreApi {
        val cacheSize = 50L * 1024L * 1024L // 50 MiB
        val cache = okhttp3.Cache(context.cacheDir, cacheSize)

        // Create a new client sharing the same connection pool/interceptors but with cache
        val cacheClient =
            okHttpClient
                .newBuilder()
                .cache(cache)
                .build()

        return createBoxLoreApi(baseUrl, cacheClient)
    }

    /**
     * Builds [BoxLoreApi] against an arbitrary [OkHttpClient].
     * Used by production (cached client) and JVM tests (MockWebServer, no disk cache).
     */
    fun createBoxLoreApi(
        baseUrl: String,
        client: OkHttpClient,
    ): BoxLoreApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(BoxLoreApi::class.java)
}
