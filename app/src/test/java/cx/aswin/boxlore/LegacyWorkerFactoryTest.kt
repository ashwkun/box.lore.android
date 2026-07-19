package cx.aswin.boxlore

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every mapped legacy worker FQCN must resolve to a ListenableWorker type,
 * and every transitional `core.data` stub must resolve with Class.forName.
 */
class LegacyWorkerFactoryTest {

    @Test
    fun factoryAliasTargetsAreWorkers() {
        assertEquals(6, LegacyWorkerFactory.LEGACY_WORKER_ALIASES.size)
        for ((legacy, modern) in LegacyWorkerFactory.LEGACY_WORKER_ALIASES) {
            val modernClass = Class.forName(modern)
            assertNotNull("missing modern class for $legacy", modernClass)
            assertTrue(
                "$modern must be a ListenableWorker",
                ListenableWorker::class.java.isAssignableFrom(modernClass),
            )
            val ctor = modernClass.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            assertNotNull("$legacy → $modern needs (Context, WorkerParameters) ctor", ctor)
        }
    }

    @Test
    fun oldFqcnStubsResolve() {
        val stubs = listOf(
            "cx.aswin.boxlore.core.data.SmartDownloadWorker",
            "cx.aswin.boxlore.core.data.AutoDownloadWorker",
            "cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker",
        )
        for (fqcn in stubs) {
            val clazz = Class.forName(fqcn)
            assertNotNull(fqcn, clazz)
            assertTrue(
                fqcn,
                ListenableWorker::class.java.isAssignableFrom(clazz),
            )
            val ctor = clazz.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            assertNotNull(ctor)
        }
    }
}
