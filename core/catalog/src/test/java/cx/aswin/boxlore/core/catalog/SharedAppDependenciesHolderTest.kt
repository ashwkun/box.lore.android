package cx.aswin.boxlore.core.catalog

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SharedAppDependenciesHolderTest {
    @AfterEach
    fun tearDown() {
        SharedAppDependenciesHolder.instance = null
    }

    @Test
    fun `require throws when instance is unset`() {
        SharedAppDependenciesHolder.instance = null
        val error =
            assertThrows(IllegalStateException::class.java) {
                SharedAppDependenciesHolder.require()
            }
        assertTrue(error.message!!.contains("SharedAppDependencies not installed"))
    }
}
