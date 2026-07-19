package cx.aswin.boxlore.core.catalog.privacy

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsentManagerTest {
    private lateinit var context: Context
    private lateinit var manager: ConsentManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.dataStore.edit { it.clear() } }
        manager = ConsentManager(context)
    }

    @After
    fun tearDown() {
        runBlocking { context.dataStore.edit { it.clear() } }
    }

    @Test
    fun defaultsOptInForBothConsentFlags() =
        runTest {
            assertTrue(manager.isCrashReportingConsented.first())
            assertTrue(manager.isUsageAnalyticsConsented.first())
        }

    @Test
    fun hasUserSetConsentAlwaysTrue() =
        runTest {
            assertTrue(manager.hasUserSetConsent.first())
        }

    @Test
    fun setConsentPersistsBothFlags() =
        runTest {
            manager.setConsent(crashReporting = false, usageAnalytics = true)

            assertFalse(manager.isCrashReportingConsented.first())
            assertTrue(manager.isUsageAnalyticsConsented.first())
        }

    @Test
    fun setConsentAllOffPersists() =
        runTest {
            manager.setConsent(crashReporting = false, usageAnalytics = false)

            assertFalse(manager.isCrashReportingConsented.first())
            assertFalse(manager.isUsageAnalyticsConsented.first())
        }

    @Test
    fun clearConsentRestoresOptOutDefaults() =
        runTest {
            manager.setConsent(crashReporting = false, usageAnalytics = false)
            manager.clearConsent()

            assertTrue(manager.isCrashReportingConsented.first())
            assertTrue(manager.isUsageAnalyticsConsented.first())
        }
}
