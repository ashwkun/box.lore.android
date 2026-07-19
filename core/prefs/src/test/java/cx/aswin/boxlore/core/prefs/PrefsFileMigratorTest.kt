package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsFileMigratorTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clear(PrefsFileMigrator.LegacyFiles.PREFS)
        clear(PrefsFileMigrator.Files.PREFS)
    }

    private fun clear(name: String) {
        context
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun oldOnlyInstall_valuesReadableAfterOpen() {
        context
            .getSharedPreferences(PrefsFileMigrator.LegacyFiles.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .putString("user_note", "hello")
            .commit()

        val prefs =
            PrefsFileMigrator.open(
                context,
                newName = PrefsFileMigrator.Files.PREFS,
                oldName = PrefsFileMigrator.LegacyFiles.PREFS,
            )

        assertTrue(prefs.getBoolean("onboarding_completed", false))
        assertEquals("hello", prefs.getString("user_note", null))
        assertEquals(
            PrefsFileMigrator.LegacyFiles.PREFS,
            prefs.getString(PrefsFileMigrator.MARKER_KEY, null),
        )
    }

    @Test
    fun newOnlyInstall_noCrashAndWritesStick() {
        val prefs =
            PrefsFileMigrator.open(
                context,
                newName = PrefsFileMigrator.Files.PREFS,
                oldName = PrefsFileMigrator.LegacyFiles.PREFS,
            )
        prefs.edit().putInt("score", 7).commit()
        assertEquals(7, prefs.getInt("score", 0))
    }

    @Test
    fun bothPresent_preferNew() {
        context
            .getSharedPreferences(PrefsFileMigrator.LegacyFiles.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("channel", "old")
            .commit()
        context
            .getSharedPreferences(PrefsFileMigrator.Files.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("channel", "new")
            .putString(PrefsFileMigrator.MARKER_KEY, PrefsFileMigrator.LegacyFiles.PREFS)
            .commit()

        val prefs =
            PrefsFileMigrator.open(
                context,
                newName = PrefsFileMigrator.Files.PREFS,
                oldName = PrefsFileMigrator.LegacyFiles.PREFS,
            )
        assertEquals("new", prefs.getString("channel", null))
    }

    @Test
    fun emptyNewPlusFullOld_copiesEntries() {
        context
            .getSharedPreferences(PrefsFileMigrator.LegacyFiles.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("genres", setOf("news", "tech"))
            .putLong("ts", 42L)
            .commit()

        assertTrue(
            PrefsFileMigrator.migrateIfNeeded(
                context,
                oldName = PrefsFileMigrator.LegacyFiles.PREFS,
                newName = PrefsFileMigrator.Files.PREFS,
            ),
        )

        val neu = context.getSharedPreferences(PrefsFileMigrator.Files.PREFS, Context.MODE_PRIVATE)
        assertEquals(setOf("news", "tech"), neu.getStringSet("genres", emptySet()))
        assertEquals(42L, neu.getLong("ts", 0L))
        assertFalse(neu.getString(PrefsFileMigrator.MARKER_KEY, null).isNullOrBlank())
    }
}
