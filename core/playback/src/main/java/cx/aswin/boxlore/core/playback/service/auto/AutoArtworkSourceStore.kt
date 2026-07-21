package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Durable + immediately-visible registry of Android Auto artwork source URLs/paths.
 *
 * An in-process map makes sources visible to [cx.aswin.boxlore.core.playback.service.AutoCollageProvider]
 * as soon as [AutoArtworkRepository] returns a URI. Prefs are [commit]ted on a background thread so
 * browse-tree work on the main dispatcher is not blocked.
 */
internal object AutoArtworkSourceStore {
    const val SOURCE_PREFS = "android_auto_artwork_sources"

    private val memory = ConcurrentHashMap<String, String>()
    private val persistExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "auto-artwork-prefs").apply { isDaemon = true }
        }

    fun put(
        context: Context,
        key: String,
        value: String,
    ) {
        memory[key] = value
        val appContext = context.applicationContext
        persistExecutor.execute {
            appContext
                .getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(key, value)
                .commit()
        }
    }

    fun get(
        context: Context,
        key: String,
    ): String? {
        memory[key]?.let { return it }
        val persisted =
            context
                .getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
                .getString(key, null)
                ?: return null
        memory[key] = persisted
        return persisted
    }

    /** Test-only: clear in-memory overlay without wiping SharedPreferences. */
    internal fun clearMemoryForTests() {
        memory.clear()
    }

    /** Test-only: wait for queued prefs commits to finish. */
    internal fun flushPersistsForTests() {
        val done = java.util.concurrent.CountDownLatch(1)
        persistExecutor.execute { done.countDown() }
        done.await(2, java.util.concurrent.TimeUnit.SECONDS)
    }
}
