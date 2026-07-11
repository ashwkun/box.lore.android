package cx.aswin.boxcast.surveys.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks the foreground [Activity] so survey UI can attach to the visible window.
 * Uses [WeakReference] to avoid leaking destroyed activities.
 */
internal class ActivityProvider : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var foreground: WeakReference<Activity>? = null

    /** Invoked when a tracked activity is destroyed (used to dismiss survey dialogs). */
    var onActivityDestroyedListener: ((Activity) -> Unit)? = null

    /** Invoked on resume so surveys can reattach after config changes or backgrounding. */
    var onActivityResumedListener: ((Activity) -> Unit)? = null

    /** The activity currently in the foreground, if any. */
    val foregroundActivity: Activity?
        get() = foreground?.get()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        foreground = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        foreground = WeakReference(activity)
        onActivityResumedListener?.invoke(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        if (foreground?.get() === activity) {
            foreground = null
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (foreground?.get() === activity) {
            foreground = null
        }
        onActivityDestroyedListener?.invoke(activity)
    }
}
