package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/**
 * Pure application of "Hide this show" feedback across the three personalized rails (Taste,
 * Because You Like, greeting mission) so the show disappears immediately — before the next
 * [cx.aswin.boxlore.core.catalog.home.HomePersonalizationCoordinator.loadSlate] reallocation
 * confirms the hard exclusion server-side.
 */
internal object HomeFeedbackLogic {
    data class HiddenRailsResult(
        val taste: List<Episode>,
        val becauseYouLikeEpisodes: List<Episode>,
        val becauseYouLikePodcasts: List<Podcast>,
        val missionEpisodes: List<Episode>,
        val anchorCleared: Boolean,
    )

    fun hideShowEverywhere(
        taste: List<Episode>,
        becauseYouLikeEpisodes: List<Episode>,
        becauseYouLikePodcasts: List<Podcast>,
        missionEpisodes: List<Episode>,
        anchorPodcastId: String?,
        hiddenPodcastId: String,
    ): HiddenRailsResult =
        HiddenRailsResult(
            taste = taste.filterNot { it.podcastId == hiddenPodcastId },
            becauseYouLikeEpisodes = becauseYouLikeEpisodes.filterNot { it.podcastId == hiddenPodcastId },
            becauseYouLikePodcasts = becauseYouLikePodcasts.filterNot { it.id == hiddenPodcastId },
            missionEpisodes = missionEpisodes.filterNot { it.podcastId == hiddenPodcastId },
            anchorCleared = anchorPodcastId == hiddenPodcastId,
        )
}
