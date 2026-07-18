package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShareLinkBuilderTest {
    @Test
    fun `podcast link encodes id`() {
        assertEquals(
            "https://aswin.cx/boxlore/share?type=podcast&id=42",
            ShareLinkBuilder.podcast("42"),
        )
    }

    @Test
    fun `episode link without timestamp omits query param`() {
        assertEquals(
            "https://aswin.cx/boxlore/share?type=episode&id=ep-1",
            ShareLinkBuilder.episode("ep-1"),
        )
    }

    @Test
    fun `episode link converts timestamp to seconds`() {
        assertEquals(
            "https://aswin.cx/boxlore/share?type=episode&id=ep-1&t=90",
            ShareLinkBuilder.episode("ep-1", timestampMs = 90_500),
        )
    }

    @Test
    fun `episode link prefers clip range over timestamp`() {
        assertEquals(
            "https://aswin.cx/boxlore/share?type=episode&id=ep-1&start=10&end=20",
            ShareLinkBuilder.episode("ep-1", timestampMs = 5_000, startMs = 10_000, endMs = 20_000),
        )
    }

    @Test
    fun `build dispatches by type`() {
        assertEquals(ShareLinkBuilder.podcast("p"), ShareLinkBuilder.build("podcast", "p"))
        assertEquals(
            ShareLinkBuilder.episode("e", timestampMs = 1_000),
            ShareLinkBuilder.build("episode", "e", timestampMs = 1_000),
        )
    }
}
