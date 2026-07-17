package cx.aswin.boxlore.core.data.content

import com.google.gson.Gson
import cx.aswin.boxlore.core.data.toContentCatalogSnapshot
import cx.aswin.boxlore.core.data.ranking.CandidateSource
import cx.aswin.boxlore.core.data.ranking.RankingObjective
import cx.aswin.boxlore.core.data.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.ContentCatalogResponse
import cx.aswin.boxlore.core.network.model.ContentDiversityDto
import cx.aswin.boxlore.core.network.model.ContentDiscoverySectionDto
import cx.aswin.boxlore.core.network.model.ContentDurationRangeDto
import cx.aswin.boxlore.core.network.model.ContentIntentDto
import cx.aswin.boxlore.core.network.model.ContentQualityDto
import cx.aswin.boxlore.core.network.model.ContentSectionEpisodeDto
import cx.aswin.boxlore.core.network.model.ContentSectionIntentMetadataDto
import cx.aswin.boxlore.core.network.model.ContentSectionsV1Request
import cx.aswin.boxlore.core.network.model.ContentSectionsV1Response
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroupedContentSectionsTest {
    @Test
    fun `sections request sends local minute without broad Android daypart`() {
        val request = ContentSectionsV1Request(
            contractVersion = 1,
            surface = "home",
            localMinuteOfDay = 500,
            country = "us",
            candidateBudget = 120,
        )

        val json = Gson().toJson(request)

        assertTrue("\"contractVersion\":1" in json)
        assertTrue("\"localMinuteOfDay\":500" in json)
        assertTrue("\"candidateBudget\":120" in json)
        assertFalse("\"daypart\"" in json)
    }

    @Test
    fun `catalog mapping retains v3 selection constraints`() {
        val snapshot = ContentCatalogResponse(
            schemaVersion = 1,
            catalogVersion = 3,
            validForSeconds = 86_400,
            intents = listOf(
                ContentIntentDto(
                    id = "focused",
                    titleKey = "focused_title",
                    titleFallback = "Focused",
                    subtitleKey = "focused_subtitle",
                    subtitleFallback = "Learn something",
                    icon = "neurology",
                    surfaces = listOf("home"),
                    dayparts = listOf("afternoon"),
                    providerQueryRef = "creative_focus",
                    layout = "episode_rail",
                    minCandidates = 4,
                    maxCandidates = 12,
                    freshnessDays = 30,
                    durationMinutes = ContentDurationRangeDto(12, 60),
                    diversity = ContentDiversityDto(2, 4),
                    quality = ContentQualityDto(0.23, 0.4),
                ),
            ),
        ).toContentCatalogSnapshot(fetchedAt = 1_000L)

        val intent = snapshot.intents.single()
        assertEquals(30, intent.freshnessDays)
        assertEquals(ContentDurationRange(12, 60), intent.durationRange)
        assertEquals(ContentDiversityConstraints(2, 4), intent.diversity)
        assertEquals(ContentQualityConstraints(0.23, 0.4), intent.quality)
        assertEquals(listOf("afternoon"), intent.daypartIds)
    }

    @Test
    fun `response mapping preserves group metadata order and second units`() {
        val response = response(
            sections = listOf(
                section("first", "compact_list", listOf(item(11, 101, rank = 1))),
                section(
                    "second",
                    "protected_card",
                    listOf(item(22, 202, rank = 1), item(23, 203, rank = 2)),
                ),
            ),
        )

        val mapped = requireNotNull(response.toGroupedContentSections(catalog(), emptySet()))

        assertEquals(listOf("first", "second"), mapped.sections.map { it.intent.id })
        assertEquals(ContentLayout.COMPACT_LIST, mapped.sections[0].intent.layout)
        assertEquals("icon-first", mapped.sections[0].intent.icon)
        assertEquals("title-first", mapped.sections[0].intent.titleKey)
        assertEquals(listOf("commute"), mapped.sections[0].intent.daypartIds)
        assertEquals(14, mapped.sections[0].intent.freshnessDays)
        assertEquals(0.6, mapped.sections[0].intent.quality.minimumSemanticScore, 0.0)
        assertTrue(mapped.sections[1].intent.protected)
        assertEquals(listOf("22", "23"), mapped.sections[1].items.map(ContentCandidate::id))
        assertEquals(1_800, mapped.sections[1].items.first().episode?.duration)
        assertEquals(1_700_000_000L, mapped.sections[1].items.first().episode?.publishedDate)
        assertEquals(2, mapped.sections[1].items.last().serverRank)
        assertEquals(CandidateSource.SERVER_RECOMMENDATION, mapped.sections[1].items.last().source)
    }

    @Test
    fun `daypart cache lookup matches backend overlap priority`() {
        assertEquals("early_morning", ContentSectionsDaypartResolver.resolve(419))
        assertEquals("commute", ContentSectionsDaypartResolver.resolve(420))
        assertEquals("commute", ContentSectionsDaypartResolver.resolve(599))
        assertEquals("morning", ContentSectionsDaypartResolver.resolve(600))
        assertEquals("afternoon", ContentSectionsDaypartResolver.resolve(660))
        assertEquals("evening", ContentSectionsDaypartResolver.resolve(1_020))
        assertEquals("late_night", ContentSectionsDaypartResolver.resolve(1_320))
        assertEquals("late_night", ContentSectionsDaypartResolver.resolve(0))
        assertEquals(
            "content_sections_v1e:3:2026-07-17:us:home:commute:000000000000000000000000",
            contentSectionsCacheKey(
                catalogVersion = 3,
                country = "US",
                surface = "home",
                localMinuteOfDay = 500,
                localDate = "2026-07-17",
                profileFingerprint = "0".repeat(24),
            ),
        )
    }

    @Test
    fun `cache fingerprint separates profile rotation and local day`() {
        val base = ContentSectionsV1Request(
            contractVersion = 1,
            surface = "home",
            localMinuteOfDay = 500,
            country = "us",
            interests = listOf("Technology"),
            recentSectionIds = listOf("technology-morning-brief"),
            noveltyPreference = 0.5,
            localDate = "2026-07-17",
            timezoneOffsetMinutes = 330,
        )
        val baseFingerprint = contentSectionsProfileFingerprint(base)
        val changedProfile = contentSectionsProfileFingerprint(
            base.copy(noveltyPreference = 0.8),
        )
        val changedRotation = contentSectionsProfileFingerprint(
            base.copy(recentSectionIds = listOf("science-morning-deep-dive")),
        )
        val nextDayRequest = base.copy(localDate = "2026-07-18")
        val nextDayFingerprint = contentSectionsProfileFingerprint(nextDayRequest)

        assertTrue(baseFingerprint != changedProfile)
        assertTrue(baseFingerprint != changedRotation)
        assertTrue(baseFingerprint != nextDayFingerprint)
        assertTrue(
            contentSectionsCacheKey(3, "us", "home", 500, "2026-07-17", baseFingerprint) !=
                contentSectionsCacheKey(3, "us", "home", 500, "2026-07-18", nextDayFingerprint),
        )
    }

    @Test
    fun `grouped provider is preferred and keeps fallback providers idle`() = runTest {
        var groupedCalls = 0
        var fallbackCalls = 0
        val grouped = requireNotNull(
            response(
                sections = listOf(section("first", "episode_rail", listOf(item(1, 10, rank = 1)))),
            ).toGroupedContentSections(catalog(), emptySet()),
        )
        val orchestrator = ContentOrchestrator(
            providers = listOf(
                object : CandidateProvider {
                    override val source = CandidateSource.CURATED_INTENT

                    override suspend fun candidates(
                        intent: ContentIntent,
                        context: ContentContext,
                    ): List<ContentCandidate> {
                        fallbackCalls++
                        return listOf(candidate("fallback", "fallback-show"))
                    }
                },
            ),
            groupedProviders = listOf(
                ServerGroupedSectionProvider {
                    groupedCalls++
                    grouped
                },
            ),
            ranker = ContentCandidateRanker { candidates, _, _ -> candidates },
        )

        val slate = orchestrator.compose(
            context = context(),
            catalog = catalog(),
            now = 1_700_000_000_000L,
        )

        assertEquals(listOf("first"), slate.sections.map { it.intent.id })
        assertEquals(listOf("1"), slate.sections.single().items.map(ContentCandidate::id))
        assertEquals(1, groupedCalls)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `composer enforces catalog quality freshness duration diversity and unseen reserve`() {
        val now = 1_800_000_000_000L
        val constrainedIntent = intent("constrained").copy(
            minimumItems = 2,
            maximumItems = 3,
            freshnessDays = 10,
            durationRange = ContentDurationRange(10, 30),
            diversity = ContentDiversityConstraints(
                maximumItemsPerShow = 1,
                minimumDistinctShows = 2,
            ),
            quality = ContentQualityConstraints(
                minimumSemanticScore = 0.5,
                unseenShowReserve = 0.34,
            ),
        )
        val currentSeconds = now / 1_000L
        val seen = episodeCandidate("seen", "show-a", currentSeconds, semantic = 0.9)
        val sameShow = episodeCandidate("same-show", "show-a", currentSeconds, semantic = 0.9)
        val unseen = episodeCandidate(
            "unseen",
            "show-b",
            currentSeconds,
            semantic = 0.8,
            novel = true,
        )
        val stale = episodeCandidate(
            "stale",
            "show-c",
            currentSeconds - 11L * 86_400L,
            semantic = 0.9,
        )
        val lowQuality = episodeCandidate("low", "show-d", currentSeconds, semantic = 0.4)
        val tooShort = episodeCandidate(
            "short",
            "show-e",
            currentSeconds,
            semantic = 0.9,
            durationSeconds = 300,
        )

        val slate = SlateComposer().compose(
            context = context(),
            catalogVersion = "3",
            rankedByIntent = listOf(
                constrainedIntent to listOf(seen, sameShow, unseen, stale, lowQuality, tooShort),
            ),
            exposureBudget = SharedExposureBudget(),
            now = now,
        )

        assertEquals(listOf("seen", "unseen"), slate.sections.single().items.map(ContentCandidate::id))
        assertFalse(slate.sections.single().items.any { it.id in setOf("stale", "low", "short") })
        assertEquals(2, slate.sections.single().items.map { it.podcast.id }.distinct().size)
    }

    @Test
    fun `malformed response contract is not mapped`() {
        val malformed = response(emptyList()).copy(contractVersion = 2)
        assertEquals(null, malformed.toGroupedContentSections(catalog(), emptySet()))
    }

    private fun response(
        sections: List<ContentDiscoverySectionDto>,
    ) = ContentSectionsV1Response(
        status = "true",
        contractVersion = 1,
        catalogVersion = 3,
        resolvedDaypart = "commute",
        algorithmVersion = "multi-lane-rrf-v1.0",
        generatedAt = "2026-07-17T00:00:00.000Z",
        sections = sections,
    )

    private fun section(
        id: String,
        layout: String,
        items: List<ContentSectionEpisodeDto>,
    ) = ContentDiscoverySectionDto(
        intent = ContentSectionIntentMetadataDto(
            id = id,
            titleKey = "title-$id",
            titleFallback = "Title $id",
            subtitleKey = "subtitle-$id",
            subtitleFallback = "Subtitle $id",
            icon = "icon-$id",
            dayparts = listOf("commute"),
            refreshPolicy = "daypart",
        ),
        layout = layout,
        items = items,
    )

    private fun item(
        id: Long,
        feedId: Long,
        rank: Int,
    ) = ContentSectionEpisodeDto(
        id = id,
        title = "Episode $id",
        description = "Description",
        enclosureUrl = "https://example.com/$id.mp3",
        duration = 1_800,
        datePublished = 1_700_000_000L,
        image = "https://example.com/$id.jpg",
        feedImage = "https://example.com/$feedId.jpg",
        feedId = feedId,
        feedTitle = "Show $feedId",
        genre = "Technology",
        retrievalScore = 1.0 / rank,
        semanticScore = 0.8,
        source = "intent",
        reason = "fits_this_moment",
        serverRank = rank,
        algorithmVersion = "multi-lane-rrf-v1.0",
    )

    private fun catalog(): ContentCatalogSnapshot = ContentCatalogSnapshot(
        schemaVersion = 1,
        catalogVersion = "3",
        validUntil = Long.MAX_VALUE,
        intents = listOf(
            intent("first").copy(
                freshnessDays = 14,
                diversity = ContentDiversityConstraints(1, 1),
                quality = ContentQualityConstraints(0.6, 0.25),
            ),
            intent("second"),
        ),
    )

    private fun intent(id: String): ContentIntent = ContentIntent(
        id = id,
        objective = RankingObjective.DISCOVERY,
        eligibleSurfaces = setOf(RankingSurface.HOME),
        eligibleDayparts = setOf(ContentDaypart.MORNING),
        title = "Catalog $id",
        layout = ContentLayout.EPISODE_RAIL,
        minimumItems = 1,
        maximumItems = 10,
    )

    private fun context(): ContentContext = ContentContext(
        surface = RankingSurface.HOME,
        localMinuteOfDay = 500,
        weekday = 5,
        daypart = ContentDaypart.MORNING,
        region = "us",
        isDriving = false,
        isOnline = true,
        availableMinutes = null,
        currentEpisodeId = null,
        currentPodcastId = null,
        historyMaturity = 4,
        subscriptionCount = 1,
        sessionId = "grouped-test",
    )

    private fun candidate(id: String, showId: String): ContentCandidate = ContentCandidate(
        id = id,
        episode = null,
        podcast = Podcast(showId, showId, "", ""),
        source = CandidateSource.CURATED_INTENT,
        intentId = "first",
        retrievalScore = 1.0,
    )

    private fun episodeCandidate(
        id: String,
        showId: String,
        publishedAtSeconds: Long,
        semantic: Double,
        novel: Boolean = false,
        durationSeconds: Int = 1_200,
    ): ContentCandidate {
        val episode = Episode(
            id = id,
            title = id,
            description = "",
            audioUrl = "https://example.com/$id.mp3",
            podcastTitle = showId,
            podcastId = showId,
            duration = durationSeconds,
            publishedDate = publishedAtSeconds,
            retrievalScore = 0.8,
            semanticScore = semantic,
        )
        return ContentCandidate(
            id = id,
            episode = episode,
            podcast = Podcast(showId, showId, "", "", latestEpisode = episode),
            source = CandidateSource.SERVER_RECOMMENDATION,
            intentId = "constrained",
            retrievalScore = 0.8,
            isNovel = novel,
            semanticScore = semantic,
        )
    }
}
