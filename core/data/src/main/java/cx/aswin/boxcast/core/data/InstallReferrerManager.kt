package cx.aswin.boxcast.core.data

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

private const val TAG = "InstallReferrerManager"
private const val PREFS_NAME = "boxcast_referrer_prefs"
private const val KEY_REFERRER_PROCESSED = "referrer_processed"

data class ReferralIntent(
    val type: String, // "podcast" or "episode"
    val id: String,
    val timestamp: Long? = null,
    val start: Long? = null,
    val end: Long? = null
)

class InstallReferrerManager(private val context: Context) {

    private val _referralFlow = MutableSharedFlow<ReferralIntent>(replay = 1)
    val referralFlow: SharedFlow<ReferralIntent> = _referralFlow.asSharedFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun checkInstallReferrer() {
        if (prefs.getBoolean(KEY_REFERRER_PROCESSED, false)) {
            Log.d(TAG, "Install referrer already processed previously.")
            return
        }

        Log.d(TAG, "Starting Google Play Install Referrer check...")
        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        try {
                            val response = referrerClient.installReferrer
                            val referrerUrl = response.installReferrer
                            Log.d(TAG, "Retrieved referrer: $referrerUrl")

                            if (!referrerUrl.isNullOrEmpty()) {
                                handleReferrer(referrerUrl)
                            }
                            
                            // Mark as processed so we don't handle it on subsequent launches
                            prefs.edit().putBoolean(KEY_REFERRER_PROCESSED, true).apply()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to get referrer details", e)
                        } finally {
                            try {
                                referrerClient.endConnection()
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                        Log.w(TAG, "Install Referrer API not supported on this device.")
                    }
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        Log.w(TAG, "Install Referrer service is currently unavailable.")
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                Log.d(TAG, "Install Referrer service disconnected.")
            }
        })
    }

    /**
     * Parses the referrer string.
     * Supported formats:
     * 1. Underscore-separated: type_episode_id_67890_t_150 or type_podcast_id_12345
     * 2. Query string parameters: type=episode&id=67890&t=150
     */
    fun handleReferrer(referrerUrl: String) {
        scope.launch {
            try {
                val parsed = parseReferrer(referrerUrl)
                if (parsed != null) {
                    Log.d(TAG, "Successfully parsed referral intent: $parsed")
                    _referralFlow.emit(parsed)
                } else {
                    Log.w(TAG, "Referrer URL could not be parsed: $referrerUrl")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling referrer: $referrerUrl", e)
            }
        }
    }

    private fun parseQueryReferrer(decoded: String): ReferralIntent? {
        if (decoded.contains("type=") && (decoded.contains("id=") || decoded.contains("podcastId="))) {
            val uri = android.net.Uri.parse("boxlore://share?$decoded")
            val type = uri.getQueryParameter("type")
            val id = uri.getQueryParameter("id") ?: uri.getQueryParameter("podcastId")
            if (type != null && id != null) {
                val t = uri.getQueryParameter("t")?.toLongOrNull()
                val start = uri.getQueryParameter("start")?.toLongOrNull()
                val end = uri.getQueryParameter("end")?.toLongOrNull()
                return ReferralIntent(type, id, t, start, end)
            }
        }
        return null
    }

    private fun parseUnderscoreReferrer(decoded: String): ReferralIntent? {
        if (!decoded.contains("type_") || (!decoded.contains("_id_") && !decoded.contains("_podcast_id_"))) {
            return null
        }
        val parts = decoded.split("_")
        val map = mutableMapOf<String, String>()
        for (i in 0 until parts.size - 1 step 2) {
            map[parts[i]] = parts[i + 1]
        }
        val type = map["type"]
        val id = map["id"] ?: map["podcast_id"]
        if (type != null && id != null) {
            val t = map["t"]?.toLongOrNull()
            val start = map["start"]?.toLongOrNull()
            val end = map["end"]?.toLongOrNull()
            return ReferralIntent(type, id, t, start, end)
        }
        return null
    }

    private fun parseSimpleReferrer(decoded: String): ReferralIntent? {
        if (decoded.startsWith("episode_")) {
            val parts = decoded.split("_")
            val id = parts.getOrNull(1)
            val t = parts.getOrNull(3)?.toLongOrNull() ?: parts.getOrNull(2)?.toLongOrNull() // handles episode_123_t_45
            if (id != null) {
                return ReferralIntent("episode", id, t)
            }
        } else if (decoded.startsWith("podcast_")) {
            val id = decoded.removePrefix("podcast_")
            if (id.isNotEmpty()) {
                return ReferralIntent("podcast", id)
            }
        }
        return null
    }

    private fun parseReferrer(referrer: String): ReferralIntent? {
        val decoded = android.net.Uri.decode(referrer)
        Log.d(TAG, "Parsing decoded referrer: $decoded")

        return parseQueryReferrer(decoded)
            ?: parseUnderscoreReferrer(decoded)
            ?: parseSimpleReferrer(decoded)
    }
}
