package cx.aswin.boxlore.core.data.analytics

import android.util.Log

/**
 * Thin non-fatal error façade. Default sink is Logcat; `:app` may install a Crashlytics
 * sink at startup. Feature modules should report through this rather than open-coding
 * Crashlytics / PostHog error paths.
 */
object ErrorReporter {
    @Volatile
    private var sink: (Throwable, String?) -> Unit = { throwable, message ->
        Log.e(TAG, message ?: throwable.message ?: "non-fatal", throwable)
    }

    fun install(reporter: (Throwable, String?) -> Unit) {
        sink = reporter
    }

    fun report(
        throwable: Throwable,
        message: String? = null,
    ) {
        try {
            sink(throwable, message)
        } catch (e: Exception) {
            Log.e(TAG, "ErrorReporter sink failed", e)
            Log.e(TAG, message ?: throwable.message ?: "non-fatal", throwable)
        }
    }

    private const val TAG = "ErrorReporter"
}
