package cx.aswin.boxlore.core.data.crosspromo

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.CrossPromotionConfidence
import cx.aswin.boxlore.core.model.CrossPromotionIndicator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrossPromotionDetectorTest {

    private val detector = CrossPromotionDetector()

    private fun createEpisode(
        title: String,
        duration: Int = 0,
        episodeType: String? = null,
        episodeNumber: Int? = null,
        description: String = "Test description"
    ): Episode {
        return Episode(
            id = "test-id",
            title = title,
            description = description,
            audioUrl = "http://example.com/audio.mp3",
            duration = duration,
            episodeType = episodeType,
            episodeNumber = episodeNumber
        )
    }

    @Test
    fun testStrictDelimiterPattern() {
        val cases = listOf(
            "Feed Drop: Serial" to "Serial",
            "Trailer Swap - Crime Junkie" to "Crime Junkie",
            "promo drop || planet money" to "planet money",
            "Bonus Drop: Radiolab" to "Radiolab",
            "Listen Now: Lawless Planet" to "Lawless Planet",
            "Sneak Peek: Normal Show" to "Normal Show", // Delimiter matches now run regardless of duration
            "Feed Swap: Serial" to "Serial",
            "Feed Share: Crime Junkie" to "Crime Junkie",
            "Promo Swap: Planet Money" to "Planet Money",
            "Special Preview: Radiolab" to "Radiolab",
            "Listen To: Lawless Planet" to "Lawless Planet"
        )

        for ((title, expectedShow) in cases) {
            val episode = createEpisode(title = title, duration = 300) // long duration
            val result = detector.detect(episode, "Host Podcast")
            println("StrictDelimiter - TITLE: '$title', RESULT isCrossPromo: ${result.isCrossPromotion}, extractedShowName: '${result.extractedShowName}'")
            assertTrue(result.isCrossPromotion, "Should detect cross promo for: $title")
            assertEquals(CrossPromotionConfidence.HIGH, result.confidence)
            assertEquals(expectedShow, result.extractedShowName)
            assertTrue(result.matchedIndicators.contains(CrossPromotionIndicator.TITLE_DELIMITER_PATTERN))
        }
    }

    @Test
    fun testStrictPresentsPattern() {
        val cases = listOf(
            "NPR Presents: Planet Money" to "Planet Money",
            "Presented by NPR: Planet Money" to "Planet Money",
            "From the creators of Suspect: Origin Stories" to "Origin Stories",
            "Brought to you by Wondery: Dr. Death" to "Dr. Death"
        )

        for ((title, expectedShow) in cases) {
            val episode = createEpisode(title = title, duration = 400)
            val result = detector.detect(episode, "Host Podcast")
            println("StrictPresents - TITLE: '$title', RESULT isCrossPromo: ${result.isCrossPromotion}, extractedShowName: '${result.extractedShowName}'")
            assertTrue(result.isCrossPromotion)
            assertEquals(expectedShow, result.extractedShowName)
            assertEquals(CrossPromotionConfidence.HIGH, result.confidence)
            assertTrue(result.matchedIndicators.contains(CrossPromotionIndicator.TITLE_PRESENTS_PATTERN))
        }
    }

    @Test
    fun testConditionalStrictPattern() {
        val cases = listOf(
            "Introducing: The Daily" to "The Daily",
            "Discover: The Daily" to "The Daily",
            "Meet: The Daily" to "The Daily",
            "Check Out: The Daily" to "The Daily",
            "We Recommend: The Daily" to "The Daily",
            "Announcing: The Daily" to "The Daily",
            "New Season: The Daily" to "The Daily",
            "Brand New Season: The Daily" to "The Daily",
            "Next Season: The Daily" to "The Daily",
            "New Sesson: The Daily" to "The Daily",
            "Next Sesson: The Daily" to "The Daily",
            "Sesson: The Daily" to "The Daily",
            "Next Seaton: The Daily" to "The Daily"
        )
        
        for ((title, expectedShow) in cases) {
            val episodeLong = createEpisode(title = title, duration = 600)
            val resultLong = detector.detect(episodeLong, "Host Podcast")
            println("ConditionalStrict - TITLE: '$title' (Long) - isCrossPromo: ${resultLong.isCrossPromotion}, extractedShowName: '${resultLong.extractedShowName}'")
            assertTrue(resultLong.isCrossPromotion)
            assertEquals(expectedShow, resultLong.extractedShowName)

            val episodeShort = createEpisode(title = title, duration = 90)
            val resultShort = detector.detect(episodeShort, "Host Podcast")
            println("ConditionalStrict - TITLE: '$title' (Short) - isCrossPromo: ${resultShort.isCrossPromotion}, extractedShowName: '${resultShort.extractedShowName}'")
            assertTrue(resultShort.isCrossPromotion)
            assertEquals(CrossPromotionConfidence.HIGH, resultShort.confidence)
            assertEquals(expectedShow, resultShort.extractedShowName)
            assertTrue(resultShort.matchedIndicators.contains(CrossPromotionIndicator.TITLE_DELIMITER_PATTERN))
        }
    }

    @Test
    fun testOptionalIndicators_SeamlessIntroducingWithShortDuration() {
        // "Introducing" seamless (no colon) + duration = 90s (2 optional indicators)
        val episode = createEpisode(
            title = "Introducing The Daily Show",
            duration = 90,
            episodeType = "full",
            episodeNumber = 12
        )
        val result = detector.detect(episode, "Host Podcast")
        assertTrue(result.isCrossPromotion)
        assertEquals(CrossPromotionConfidence.MEDIUM, result.confidence)
        assertEquals("The Daily Show", result.extractedShowName)
        assertTrue(result.matchedIndicators.contains(CrossPromotionIndicator.TITLE_SEAMLESS_INTRODUCING))
        assertTrue(result.matchedIndicators.contains(CrossPromotionIndicator.SHORT_DURATION))
    }

    @Test
    fun testOptionalIndicators_TrailerTypeAndShortDurationNoName() {
        // duration = 90s + type = "trailer" + missing number = 3 optional indicators
        // BUT no seamless introducing regex matches, so no name is extracted.
        // Should NOT be cross-promotional because we don't have a name.
        val episode = createEpisode(
            title = "My Own Season Trailer",
            duration = 90,
            episodeType = "trailer",
            episodeNumber = null
        )
        val result = detector.detect(episode, "Host Podcast")
        assertFalse(result.isCrossPromotion)
        assertNull(result.extractedShowName)
    }

    @Test
    fun testSamePodcastGuard() {
        // Matches delimiter but same show name -> ignore
        val episode1 = createEpisode(title = "Feed Drop: Serial", duration = 300)
        val result1 = detector.detect(episode1, "Serial")
        assertFalse(result1.isCrossPromotion)

        // Matches delimiter but host title contains extracted name -> ignore
        val episode2 = createEpisode(title = "Introducing: The Daily", duration = 90)
        val result2 = detector.detect(episode2, "The Daily Podcast")
        assertFalse(result2.isCrossPromotion)

        // Matches optional seamless but host title contains extracted name -> ignore
        val episode3 = createEpisode(title = "Introducing Serial", duration = 90)
        val result3 = detector.detect(episode3, "Serial")
        assertFalse(result3.isCrossPromotion)
    }

    @Test
    fun testNegativeLookaheadForSeason() {
        // "Introducing Season 3" matches seamless but negative lookahead should block it
        val episode = createEpisode(title = "Introducing Season 3", duration = 90)
        val result = detector.detect(episode, "Host Podcast")
        assertFalse(result.isCrossPromotion)
    }

    @Test
    fun testNormalEpisode() {
        val episode = createEpisode(
            title = "Episode 47: Introducing the New iPhone",
            duration = 3000,
            episodeType = "full",
            episodeNumber = 47
        )
        val result = detector.detect(episode, "Host Podcast")
        assertFalse(result.isCrossPromotion)
    }

    @Test
    fun testGuestFeedAndCompanionPatterns() {
        val guest = detector.detect(
            createEpisode(title = "Guest Feed: Serial", duration = 120),
            "Host Podcast"
        )
        assertTrue(guest.isCrossPromotion)
        assertEquals("Serial", guest.extractedShowName)

        val companion = detector.detect(
            createEpisode(title = "Companion Show: Crime Junkie", duration = 90),
            "Host Podcast"
        )
        assertTrue(companion.isCrossPromotion)
        assertEquals("Crime Junkie", companion.extractedShowName)
    }

    @Test
    fun testDescriptionSubscribeLanguage() {
        val episode = createEpisode(
            title = "A Special Preview",
            duration = 95,
            episodeType = "trailer",
            episodeNumber = null,
            description = "Subscribe to \"Lawless Planet\" wherever you get your podcasts."
        )
        val result = detector.detect(episode, "Host Podcast")
        assertTrue(result.isCrossPromotion)
        assertEquals("Lawless Planet", result.extractedShowName)
        assertTrue(result.matchedIndicators.contains(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE))
    }

    @Test
    fun testDescriptionDoesNotPromoteHostShow() {
        val episode = createEpisode(
            title = "Season Trailer",
            duration = 90,
            episodeType = "trailer",
            episodeNumber = null,
            description = "Subscribe to Host Podcast for more episodes every week."
        )
        val result = detector.detect(episode, "Host Podcast")
        assertFalse(result.isCrossPromotion)
    }

    @Test
    fun testSeasonOnlyNameRejected() {
        val episode = createEpisode(title = "New Season: Season 4", duration = 120)
        val result = detector.detect(episode, "Host Podcast")
        assertFalse(result.isCrossPromotion)
    }
}
