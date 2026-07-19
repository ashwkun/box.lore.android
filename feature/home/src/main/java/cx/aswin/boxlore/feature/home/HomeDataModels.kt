package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.data.content.ContentDaypart
import cx.aswin.boxlore.core.data.content.ContentSection
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

internal data class SelectedPodcastSignal(
    val podcastId: String,
    val lastPlayedEpisodeId: String?,
    val sort: String,
    val rssRefreshVersion: Long,
)

data class HomeDataWrapper(
    val trending: List<Podcast>,
    val resume: List<cx.aswin.boxlore.core.playback.PlaybackSession>,
    val subs: List<Podcast>,
    val history: List<HomeListeningHistoryItem>,
    val resolvedSerial: Map<String, Episode>,
    val recommendations: List<Episode> = emptyList(),
    val completedEpisodeIds: Set<String> = emptySet(),
    val isTrendingLoaded: Boolean = false,
    val isRecommendationsLoaded: Boolean = false,
    val hasDismissedImportBanner: Boolean = false,
    val briefing: Briefing? = null,
    val briefingChapters: List<cx.aswin.boxlore.core.model.Chapter> = emptyList(),
    val briefingDismissedDate: String = "",
    val briefingDismissedForever: Boolean = false,
    val seemsToLikePodcast: Podcast? = null,
    val becauseYouLikeRecommendations: List<Episode> = emptyList(),
    val becauseYouLikePodcasts: List<Podcast> = emptyList(),
    val isBecauseYouLikeLoading: Boolean = false,
    val isRecommendationsFallback: Boolean = true,
    val adaptiveSections: List<ContentSection> = emptyList(),
)

internal data class HomeCoreSlice(
    val trending: List<Podcast>,
    val resume: List<cx.aswin.boxlore.core.playback.PlaybackSession>,
    val subs: List<Podcast>,
    val history: List<HomeListeningHistoryItem>,
    val resolvedSerial: Map<String, Episode>,
)

internal data class HomeRecsSlice(
    val recommendations: List<Episode>,
    val completedEpisodeIds: Set<String>,
    val isTrendingLoaded: Boolean,
    val isRecommendationsLoaded: Boolean,
    val hasDismissedImportBanner: Boolean,
)

internal data class HomeBriefingSlice(
    val briefing: Briefing?,
    val briefingDismissedDate: String,
    val briefingChapters: List<cx.aswin.boxlore.core.model.Chapter>,
    val briefingDismissedForever: Boolean,
)

internal data class HomeBecauseYouLikeSlice(
    val seemsToLikePodcast: Podcast?,
    val becauseYouLikeRecommendations: List<Episode>,
    val becauseYouLikePodcasts: List<Podcast>,
    val isBecauseYouLikeLoading: Boolean,
    val isRecommendationsFallback: Boolean,
)

internal data class AdaptiveContentTrigger(
    val region: String,
    val daypart: ContentDaypart,
    val sectionDaypart: String,
    val date: java.time.LocalDate,
    val timezoneOffsetMinutes: Int,
    val subscriptionIds: Set<String>,
    /** Coarse bucket so per-episode history writes do not cancel in-flight section loads. */
    val historyMaturityBucket: Int,
)

internal data class HomeClockContext(
    val daypart: ContentDaypart,
    val sectionDaypart: String,
    val date: java.time.LocalDate,
    val timezoneOffsetMinutes: Int,
)
