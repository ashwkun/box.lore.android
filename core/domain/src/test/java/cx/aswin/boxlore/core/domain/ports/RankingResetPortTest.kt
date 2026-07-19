package cx.aswin.boxlore.core.domain.ports

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RankingResetPortTest {
    @Test
    fun `fake port returns configured reset result`() =
        runTest {
            val ok: RankingResetPort = RankingResetPort { true }
            val fail: RankingResetPort = RankingResetPort { false }

            assertTrue(ok.reset())
            assertFalse(fail.reset())
        }
}
