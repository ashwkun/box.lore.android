package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        clearThemeCache()
        repository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        clearThemeCache()
    }

    private fun clearThemeCache() {
        context
            .getSharedPreferences(PrefsFileMigrator.Files.THEME_FAST_CACHE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences(PrefsFileMigrator.LegacyFiles.THEME_FAST_CACHE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // ---- Region ----

    @Test
    fun regionDefaultsToUsForUnsupportedLocale() =
        runTest {
            assertEquals("us", repository.regionStream.first())
        }

    @Test
    fun setRegionNormalizesAliasesAndDismissesNudge() =
        runTest {
            repository.setRegion("IND")
            assertEquals("in", repository.regionStream.first())
            assertTrue(repository.hasDismissedRegionNudgeStream.first())

            repository.setRegion("UK")
            assertEquals("gb", repository.regionStream.first())
        }

    @Test
    fun dismissRegionNudgeSetsFlag() =
        runTest {
            assertFalse(repository.hasDismissedRegionNudgeStream.first())
            repository.dismissRegionNudge()
            assertTrue(repository.hasDismissedRegionNudgeStream.first())
        }

    @Test
    fun dismissExploreRegionNudgeSetsFlag() =
        runTest {
            assertFalse(repository.hasDismissedExploreRegionNudgeStream.first())
            repository.dismissExploreRegionNudge()
            assertTrue(repository.hasDismissedExploreRegionNudgeStream.first())
        }

    @Test
    fun dismissHomeImportBannerSetsFlag() =
        runTest {
            assertFalse(repository.hasDismissedHomeImportBannerStream.first())
            repository.dismissHomeImportBanner()
            assertTrue(repository.hasDismissedHomeImportBannerStream.first())
        }

    @Test
    fun wasInitialRegionMatchIsWriteOnce() =
        runTest {
            assertNull(repository.wasInitialRegionMatchStream.first())
            repository.setWasInitialRegionMatch(true)
            assertEquals(true, repository.wasInitialRegionMatchStream.first())
            // Second write is ignored because the value was already set.
            repository.setWasInitialRegionMatch(false)
            assertEquals(true, repository.wasInitialRegionMatchStream.first())
        }

    // ---- Briefing ----

    @Test
    fun briefingDismissDateRoundTrips() =
        runTest {
            assertEquals("", repository.briefingDismissedDate.first())
            repository.dismissBriefing("2026-07-19")
            assertEquals("2026-07-19", repository.briefingDismissedDate.first())
        }

    @Test
    fun briefingDismissForeverRoundTrips() =
        runTest {
            assertFalse(repository.briefingDismissedForever.first())
            repository.dismissBriefingForever()
            assertTrue(repository.briefingDismissedForever.first())
        }

    // ---- Theme ----

    @Test
    fun themeDefaultsMatchDocumentedValues() =
        runTest {
            assertEquals("system", repository.themeConfigStream.first())
            assertEquals(false, repository.useDynamicColorStream.first())
            assertEquals("violet", repository.themeBrandStream.first())
            assertEquals("classic_dynamic", repository.surfaceStyleStream.first())
        }

    @Test
    fun setThemeConfigUpdatesStreamAndSyncCache() =
        runTest {
            repository.setThemeConfig("dark")
            assertEquals("dark", repository.themeConfigStream.first())
            assertEquals("dark", repository.cachedThemeConfig)
        }

    @Test
    fun setUseDynamicColorUpdatesStreamAndCache() =
        runTest {
            repository.setUseDynamicColor(true)
            assertTrue(repository.useDynamicColorStream.first())
            assertTrue(repository.cachedUseDynamicColor)
        }

    @Test
    fun setThemeBrandAndSurfaceStyleUpdateCaches() =
        runTest {
            repository.setThemeBrand("emerald")
            repository.setSurfaceStyle("frosted")
            assertEquals("emerald", repository.themeBrandStream.first())
            assertEquals("emerald", repository.cachedThemeBrand)
            assertEquals("frosted", repository.surfaceStyleStream.first())
            assertEquals("frosted", repository.cachedSurfaceStyle)
        }

    @Test
    fun cachedGettersReturnDefaultsBeforeAnyWrite() {
        assertEquals("system", repository.cachedThemeConfig)
        assertEquals("classic_dynamic", repository.cachedSurfaceStyle)
        assertEquals("violet", repository.cachedThemeBrand)
        assertFalse(repository.cachedUseDynamicColor)
    }

    // ---- Sorting ----

    @Test
    fun subscriptionSortDefaultsToSmartRank() =
        runTest {
            assertEquals("SmartRank", repository.subscriptionSortStream.first())
            repository.setSubscriptionSort("Alphabetical")
            assertEquals("Alphabetical", repository.subscriptionSortStream.first())
        }

    @Test
    fun latestEpisodesSortDefaultsToSmart() =
        runTest {
            assertTrue(repository.latestEpisodesSortUseSmartStream.first())
            repository.setLatestEpisodesSortUseSmart(false)
            assertFalse(repository.latestEpisodesSortUseSmartStream.first())
        }

    // ---- Playback speed & skip bounds ----

    @Test
    fun playbackSpeedDefaultsToOne() =
        runTest {
            assertEquals(1.0f, repository.playbackSpeedStream.first(), 0.0f)
            repository.setPlaybackSpeed(1.75f)
            assertEquals(1.75f, repository.playbackSpeedStream.first(), 0.0f)
        }

    @Test
    fun skipBoundsUseDefaultsAndSanitizeWrites() =
        runTest {
            assertEquals(PlaybackSkipBounds.DEFAULT_SKIP_BEGINNING_MS, repository.skipBeginningMsStream.first())
            assertEquals(PlaybackSkipBounds.DEFAULT_SKIP_ENDING_MS, repository.skipEndingMsStream.first())
            assertEquals(PlaybackSkipBounds.DEFAULT_SEEK_BACKWARD_MS, repository.seekBackwardMsStream.first())
            assertEquals(PlaybackSkipBounds.DEFAULT_SEEK_FORWARD_MS, repository.seekForwardMsStream.first())

            repository.setSkipBeginningMs(-5_000L)
            assertEquals(0L, repository.skipBeginningMsStream.first())

            repository.setSkipEndingMs(999_999_999L)
            assertEquals(PlaybackSkipBounds.MAX_TRIM_MS, repository.skipEndingMsStream.first())

            repository.setSeekBackwardMs(1L)
            assertEquals(PlaybackSkipBounds.MIN_SEEK_MS, repository.seekBackwardMsStream.first())

            repository.setSeekForwardMs(999_999_999L)
            assertEquals(PlaybackSkipBounds.MAX_SEEK_MS, repository.seekForwardMsStream.first())
        }

    @Test
    fun skipBehaviorDefaultsToJustSkip() =
        runTest {
            assertEquals("just_skip", repository.skipBehaviorStream.first())
            repository.setSkipBehavior("skip_silence")
            assertEquals("skip_silence", repository.skipBehaviorStream.first())
        }

    // ---- Tooltips & first-play ----

    @Test
    fun tooltipFlagsDefaultFalseAndMarkSeen() =
        runTest {
            assertFalse(repository.hasSeenSwipeDismissTip.first())
            assertFalse(repository.hasSeenTitleTapTip.first())
            assertFalse(repository.hasSeenSwipeMinimizeTip.first())
            assertFalse(repository.hasSeenMarkPlayedTip.first())

            repository.markSwipeDismissTipSeen()
            repository.markTitleTapTipSeen()
            repository.markSwipeMinimizeTipSeen()
            repository.markMarkPlayedTipSeen()

            assertTrue(repository.hasSeenSwipeDismissTip.first())
            assertTrue(repository.hasSeenTitleTapTip.first())
            assertTrue(repository.hasSeenSwipeMinimizeTip.first())
            assertTrue(repository.hasSeenMarkPlayedTip.first())
        }

    @Test
    fun firstPlayLoggedFlag() =
        runTest {
            assertFalse(repository.hasLoggedFirstPlay.first())
            repository.markFirstPlayLogged()
            assertTrue(repository.hasLoggedFirstPlay.first())
        }

    @Test
    fun dismissedFeatureVersionRoundTrips() =
        runTest {
            assertEquals("", repository.dismissedFeatureVersion.first())
            repository.dismissFeatureAnnouncement("2.5.0")
            assertEquals("2.5.0", repository.dismissedFeatureVersion.first())
        }

    // ---- Announcement ----

    @Test
    fun announcementDefaultsNullAndRoundTrips() =
        runTest {
            assertNull(repository.activeAnnouncementStream.first())

            val announcement =
                UserPreferencesRepository.Announcement(
                    title = "Hello",
                    body = "World",
                    route = "boxcast://home",
                    imageUrl = "https://example.com/x.jpg",
                    actionLabel = "Open",
                    showActionInApp = true,
                    timestamp = 42L,
                    category = "WHAT'S NEW",
                )
            repository.setAnnouncement(announcement)

            val stored = repository.activeAnnouncementStream.first()!!
            assertEquals("Hello", stored.title)
            assertEquals("boxcast://home", stored.route)
            assertEquals(42L, stored.timestamp)

            repository.clearAnnouncement()
            assertNull(repository.activeAnnouncementStream.first())
        }

    @Test
    fun announcementWithBlankTitleIsNotSurfaced() =
        runTest {
            repository.setAnnouncement(
                UserPreferencesRepository.Announcement(
                    title = "   ",
                    body = "body",
                    route = null,
                    imageUrl = null,
                    actionLabel = null,
                    showActionInApp = false,
                    timestamp = 1L,
                    category = "X",
                ),
            )
            assertNull(repository.activeAnnouncementStream.first())
        }

    // ---- Review logic ----

    @Test
    fun shouldShowReviewPromptFalseWhilePlaying() =
        runTest {
            assertFalse(repository.shouldShowReviewPrompt(isPlaying = true))
        }

    @Test
    fun shouldShowReviewPromptFalseWithoutPendingMilestone() =
        runTest {
            assertFalse(repository.shouldShowReviewPrompt(isPlaying = false))
        }

    @Test
    fun markReviewedBlocksFuturePrompts() =
        runTest {
            assertFalse(repository.reviewHasReviewed.first())
            repository.markReviewed()
            assertTrue(repository.reviewHasReviewed.first())
            assertTrue(repository.hasReviewedSync())
        }

    @Test
    fun reviewMilestonePendingTracksHighestReachedTier() =
        runTest {
            assertNull(repository.reviewMilestonePending())

            repository.syncReviewMilestonePending(3)
            assertNull(repository.reviewMilestonePending())

            repository.syncReviewMilestonePending(5)
            assertEquals(5, repository.reviewMilestonePending())

            repository.syncReviewMilestonePending(30)
            assertEquals(30, repository.reviewMilestonePending())

            // Lower milestone does not downgrade.
            repository.syncReviewMilestonePending(15)
            assertEquals(30, repository.reviewMilestonePending())

            repository.clearReviewMilestonePending()
            assertNull(repository.reviewMilestonePending())
        }

    @Test
    fun reviewMilestoneNotStoredAfterReview() =
        runTest {
            repository.markReviewed()
            repository.syncReviewMilestonePending(30)
            assertNull(repository.reviewMilestonePending())
        }

    @Test
    fun engagementCooldownElapsedByDefaultThenResets() =
        runTest {
            assertTrue(repository.isEngagementCooldownElapsed())
            repository.recordEngagementPromptShown()
            assertFalse(repository.isEngagementCooldownElapsed())
        }

    @Test
    fun npsScoreRoundTrips() =
        runTest {
            assertNull(repository.npsLastScore())
            repository.setNpsLastScore(9)
            assertEquals(9, repository.npsLastScore())
        }

    @Test
    fun promoterReviewPendingRoundTrips() =
        runTest {
            assertFalse(repository.isPromoterReviewPending())
            repository.setPromoterReviewPending(true)
            assertTrue(repository.isPromoterReviewPending())
        }

    // ---- NPS survey lifecycle ----

    @Test
    fun npsSurveyPendingThenFiredLifecycle() =
        runTest {
            assertFalse(repository.isNpsSurveyPending())
            assertFalse(repository.hasNpsSurveyFired())
            assertNull(repository.npsSurveyCompletedCount())

            repository.markNpsSurveyPending(3)
            assertTrue(repository.isNpsSurveyPending())
            assertEquals(3, repository.npsSurveyCompletedCount())

            repository.markNpsSurveyFired()
            assertTrue(repository.hasNpsSurveyFired())
            assertFalse(repository.isNpsSurveyPending())
        }

    @Test
    fun markNpsSurveyPendingNoOpAfterFired() =
        runTest {
            repository.markNpsSurveyFired()
            repository.markNpsSurveyPending(10)
            assertFalse(repository.isNpsSurveyPending())
        }

    // ---- Hide-completed toggles ----

    @Test
    fun hideCompletedDefaults() =
        runTest {
            assertTrue(repository.hideCompletedInFeedsStream.first())
            assertFalse(repository.hideCompletedInShowDetailsStream.first())
            assertTrue(repository.hideCompletedInHomeStream.first())
            assertTrue(repository.hideCompletedInSubsStream.first())

            repository.setHideCompletedInFeeds(false)
            repository.setHideCompletedInShowDetails(true)
            repository.setHideCompletedInHome(false)
            repository.setHideCompletedInSubs(false)

            assertFalse(repository.hideCompletedInFeedsStream.first())
            assertTrue(repository.hideCompletedInShowDetailsStream.first())
            assertFalse(repository.hideCompletedInHomeStream.first())
            assertFalse(repository.hideCompletedInSubsStream.first())
        }

    @Test
    fun overriddenRecPodcastIdSetAndClear() =
        runTest {
            assertNull(repository.overriddenRecPodcastIdStream.first())
            repository.setOverriddenRecPodcastId("pod-1")
            assertEquals("pod-1", repository.overriddenRecPodcastIdStream.first())
            repository.setOverriddenRecPodcastId(null)
            assertNull(repository.overriddenRecPodcastIdStream.first())
        }

    // ---- Smart & auto downloads ----

    @Test
    fun smartDownloadDefaults() =
        runTest {
            assertFalse(repository.smartDownloadsEnabledStream.first())
            assertEquals(10, repository.smartDownloadsMaxEpisodesStream.first())
            assertEquals(1000L, repository.smartDownloadsStorageBudgetStream.first())
            assertTrue(repository.smartDownloadsWifiOnlyStream.first())
            assertFalse(repository.smartDownloadsChargingOnlyStream.first())
            assertEquals("after_24h", repository.smartDownloadsCleanupRuleStream.first())
            assertEquals(0L, repository.smartDownloadsLastSyncTimeStream.first())
        }

    @Test
    fun smartDownloadSettersRoundTrip() =
        runTest {
            repository.setSmartDownloadsEnabled(true)
            repository.setSmartDownloadsMaxEpisodes(25)
            repository.setSmartDownloadsStorageBudget(2048L)
            repository.setSmartDownloadsWifiOnly(false)
            repository.setSmartDownloadsChargingOnly(true)
            repository.setSmartDownloadsCleanupRule("never")
            repository.setSmartDownloadsLastSyncTime(123L)

            assertTrue(repository.smartDownloadsEnabledStream.first())
            assertEquals(25, repository.smartDownloadsMaxEpisodesStream.first())
            assertEquals(2048L, repository.smartDownloadsStorageBudgetStream.first())
            assertFalse(repository.smartDownloadsWifiOnlyStream.first())
            assertTrue(repository.smartDownloadsChargingOnlyStream.first())
            assertEquals("never", repository.smartDownloadsCleanupRuleStream.first())
            assertEquals(123L, repository.smartDownloadsLastSyncTimeStream.first())
        }

    @Test
    fun autoDownloadDefaultsAndSetters() =
        runTest {
            assertTrue(repository.autoDownloadWifiOnlyStream.first())
            assertEquals(2, repository.autoDownloadMaxEpisodesStream.first())
            assertTrue(repository.autoDownloadDeleteCompletedStream.first())

            repository.setAutoDownloadWifiOnly(false)
            repository.setAutoDownloadMaxEpisodes(5)
            repository.setAutoDownloadDeleteCompleted(false)

            assertFalse(repository.autoDownloadWifiOnlyStream.first())
            assertEquals(5, repository.autoDownloadMaxEpisodesStream.first())
            assertFalse(repository.autoDownloadDeleteCompletedStream.first())
        }

    // ---- Last-seen episodes ----

    @Test
    fun lastSeenEpisodesMapSetAndRemove() =
        runTest {
            assertTrue(repository.lastSeenEpisodesStream.first().isEmpty())

            repository.setLastSeenEpisodeId("pod-1", "ep-1")
            repository.setLastSeenEpisodeId("pod-2", "ep-2")

            val map = repository.lastSeenEpisodesStream.first()
            assertEquals("ep-1", map["pod-1"])
            assertEquals("ep-2", map["pod-2"])

            repository.removeLastSeenEpisodeId("pod-1")
            val after = repository.lastSeenEpisodesStream.first()
            assertNull(after["pod-1"])
            assertEquals("ep-2", after["pod-2"])
        }
}
