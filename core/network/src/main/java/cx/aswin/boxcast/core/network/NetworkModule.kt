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
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder().apply {
        addInterceptor(loggingInterceptor)
        connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        
        if (cx.aswin.boxcast.core.network.BuildConfig.DEBUG) {
            try {
                val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                    object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    }
                )
                
                val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                
                sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Log.e("NetworkModule", "Failed to configure trust-all certificates for debug", e)
            }
        }
    }.build()

    /**
     * BoxCast API via Cloudflare Worker proxy
     * Base URL injected from BuildConfig at runtime
     */
    fun createBoxCastApi(baseUrl: String, context: android.content.Context): BoxCastApi {
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
            .create(BoxCastApi::class.java)
    }
}
