package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.data.SharedAppDependenciesHolder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hermetic [AutoDownloadWorker] input-validation paths (no network / Media3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoDownloadWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SharedAppDependenciesHolder.instance = null
        DownloadsDependenciesHolder.instance = null
    }

    @After
    fun tearDown() {
        SharedAppDependenciesHolder.instance = null
        DownloadsDependenciesHolder.instance = null
    }

    @Test
    fun `doWork fails when episode id missing`() {
        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(AutoDownloadWorker.KEY_PODCAST_ID, "pod-1")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork fails when podcast id missing`() {
        val worker =
            TestListenableWorkerBuilder<AutoDownloadWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(AutoDownloadWorker.KEY_EPISODE_ID, "ep-1")
                        .build(),
                ).build()

        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
