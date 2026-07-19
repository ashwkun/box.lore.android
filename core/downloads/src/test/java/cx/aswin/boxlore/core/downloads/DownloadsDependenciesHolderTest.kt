package cx.aswin.boxlore.core.downloads

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadsDependenciesHolderTest {
    @AfterEach
    fun tearDown() {
        DownloadsDependenciesHolder.instance = null
    }

    @Test
    fun `require throws when instance is unset`() {
        DownloadsDependenciesHolder.instance = null
        val error =
            assertThrows(IllegalStateException::class.java) {
                DownloadsDependenciesHolder.require()
            }
        assertTrue(error.message!!.contains("DownloadsDependencies not installed"))
    }
}
