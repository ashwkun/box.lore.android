package cx.aswin.boxlore.core.domain.ports

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectivityStatusPortTest {
    @Test
    fun `always online default reports online`() {
        assertTrue(AlwaysOnlineConnectivity.isOnline())
    }

    @Test
    fun `fixed double reports configured status`() {
        val offline = ConnectivityStatusPort { false }
        val online = ConnectivityStatusPort { true }
        assertFalse(offline.isOnline())
        assertTrue(online.isOnline())
    }
}
