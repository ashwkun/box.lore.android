package cx.aswin.boxlore.core.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * Dual-read SharedPreferences file migration failsafe.
 *
 * Mirrors Room's `boxcast_database` → `boxlore_database` rename: copy (or fall back to
 * dual-read) instead of blind string swaps so upgrades keep listener state.
 *
 * Permanent upgrade bridge — keep for at least one release after migration ships.
 */
object PrefsFileMigrator {

    const val MARKER_KEY = "_migrated_from"

    private const val TAG = "PrefsFileMigrator"

    /**
     * Canonical brand-neutral SharedPreferences file names (writers use these).
     */
    object Files {
        const val PREFS = "boxlore_prefs"
        const val PLAYER = "boxlore_player"
        const val API_CONFIG = "boxlore_api_config"
        const val REFERRER = "boxlore_referrer_prefs"
        const val ANALYTICS = "boxlore_analytics_prefs"
        const val THEME_FAST_CACHE = "boxlore_theme_fast_cache"
    }

    /**
     * Pre-Phase-2 / pre-brand-rename SharedPreferences file names.
     */
    object LegacyFiles {
        const val PREFS = "boxcast_prefs"
        const val PLAYER = "boxcast_player"
        const val API_CONFIG = "boxcast_api_config"
        const val REFERRER = "boxcast_referrer_prefs"
        const val ANALYTICS = "boxcast_analytics_prefs"
        const val THEME_FAST_CACHE = "boxcast_theme_fast_cache"
    }

    /**
     * Open [newName], migrating from [oldName] when needed.
     * Writers always target the new file; readers dual-read when migration failed.
     */
    fun open(
        context: Context,
        newName: String,
        oldName: String,
    ): SharedPreferences {
        val app = context.applicationContext
        migrateIfNeeded(app, oldName, newName)
        val neu = app.getSharedPreferences(newName, Context.MODE_PRIVATE)
        if (neu.contains(MARKER_KEY) || hasUserKeys(neu)) {
            return neu
        }
        val old = app.getSharedPreferences(oldName, Context.MODE_PRIVATE)
        return if (hasUserKeys(old)) {
            DualReadSharedPreferences(primary = neu, fallback = old)
        } else {
            neu
        }
    }

    /**
     * @return true when new file is ready (already migrated, freshly copied, or no old data).
     */
    fun migrateIfNeeded(
        context: Context,
        oldName: String,
        newName: String,
    ): Boolean {
        val app = context.applicationContext
        val neu = app.getSharedPreferences(newName, Context.MODE_PRIVATE)
        if (neu.contains(MARKER_KEY) || hasUserKeys(neu)) {
            return true
        }

        val oldXml = sharedPrefsXml(app, oldName)
        val old = app.getSharedPreferences(oldName, Context.MODE_PRIVATE)
        if (!oldXml.exists() && !hasUserKeys(old)) {
            return true
        }

        return try {
            val editor = neu.edit()
            for ((key, value) in old.all) {
                if (key == null || value == null || key == MARKER_KEY) continue
                putAny(editor, key, value)
            }
            editor.putString(MARKER_KEY, oldName)
            val committed = editor.commit()
            if (!committed) {
                Log.e(TAG, "Failed to commit prefs migration $oldName → $newName")
                return false
            }
            Log.d(TAG, "Migrated SharedPreferences $oldName → $newName")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Prefs migration failed $oldName → $newName", t)
            false
        }
    }

    private fun sharedPrefsXml(context: Context, name: String): File =
        File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")

    private fun hasUserKeys(prefs: SharedPreferences): Boolean =
        prefs.all.keys.any { it != MARKER_KEY }

    @Suppress("UNCHECKED_CAST")
    private fun putAny(editor: SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value as Set<String>)
            else -> Log.w(TAG, "Skipping unsupported prefs type for key=$key (${value.javaClass.name})")
        }
    }
}

/**
 * Reads from [primary] first, then [fallback]. All writes go to [primary] only.
 */
internal class DualReadSharedPreferences(
    private val primary: SharedPreferences,
    private val fallback: SharedPreferences,
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> {
        val merged = LinkedHashMap<String, Any?>()
        merged.putAll(fallback.all)
        merged.putAll(primary.all)
        return merged
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        return if (primary.contains(key)) primary.getString(key, defValue)
        else fallback.getString(key, defValue)
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        if (key == null) return defValues
        return if (primary.contains(key)) primary.getStringSet(key, defValues)
        else fallback.getStringSet(key, defValues)
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (key == null) return defValue
        return if (primary.contains(key)) primary.getInt(key, defValue)
        else fallback.getInt(key, defValue)
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (key == null) return defValue
        return if (primary.contains(key)) primary.getLong(key, defValue)
        else fallback.getLong(key, defValue)
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        if (key == null) return defValue
        return if (primary.contains(key)) primary.getFloat(key, defValue)
        else fallback.getFloat(key, defValue)
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) return defValue
        return if (primary.contains(key)) primary.getBoolean(key, defValue)
        else fallback.getBoolean(key, defValue)
    }

    override fun contains(key: String?): Boolean {
        if (key == null) return false
        return primary.contains(key) || fallback.contains(key)
    }

    override fun edit(): SharedPreferences.Editor = primary.edit()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        primary.registerOnSharedPreferenceChangeListener(listener)
        fallback.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        primary.unregisterOnSharedPreferenceChangeListener(listener)
        fallback.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
