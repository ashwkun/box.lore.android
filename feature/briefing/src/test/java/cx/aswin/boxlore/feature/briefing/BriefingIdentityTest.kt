package cx.aswin.boxlore.feature.briefing

import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.Briefing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BriefingIdentityTest {
    @Test
    fun `episode id encodes region and date`() {
        val briefing =
            Briefing(
                region = "us",
                date = "2026-07-18",
                title = "Daily Brief",
                audioUrl = "https://example.com/audio.mp3",
                coverUrl = "https://example.com/cover.jpg",
                script = "",
                sources = emptyList(),
            )

        assertEquals("briefing_us_2026-07-18", BriefingIdentity.episodeId(briefing))
    }

    @Test
    fun `cover drawable resolves region aliases`() {
        assertEquals(R.drawable.daily_briefing_usa, BriefingIdentity.coverDrawableRes("us"))
        assertEquals(R.drawable.daily_briefing_usa, BriefingIdentity.coverDrawableRes("USA"))
        assertEquals(R.drawable.daily_briefing_india, BriefingIdentity.coverDrawableRes("in"))
        assertEquals(R.drawable.daily_briefing_uk, BriefingIdentity.coverDrawableRes("gb"))
        assertEquals(R.drawable.daily_briefing_global, BriefingIdentity.coverDrawableRes("eu"))
    }
}
