package cx.aswin.boxlore.feature.home

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.components.DailyBriefingCard
import java.time.LocalDate
import java.time.ZoneOffset

internal fun LazyStaggeredGridScope.dailyBriefingItem(
    feedState: PodcastFeedUiState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    context: Context,
) {
    val briefing = feedState.briefing ?: return
    item(span = StaggeredGridItemSpan.FullLine, key = "briefing", contentType = "briefing") {
        PinnedGridItemContent {
            val briefingId = "briefing_${briefing.region}_${briefing.date}"
            val playbackState = playback.episodePlaybackState.map[briefingId]
            LaunchedEffect(briefing.region, briefing.date) {
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingCardImpression(
                    region = briefing.region,
                    date = briefing.date,
                    playbackStatus = playbackState?.first?.name ?: "NOT_STARTED",
                )
            }
            DailyBriefingCard(
                briefing = briefing,
                chapters = feedState.briefingChapters,
                isPlaying = playback.player.isPlaying && playback.player.currentPlayingEpisodeId == briefingId,
                playbackStatus = playbackState?.first,
                playbackProgress = playbackState?.second,
                isBuffering = playback.player.isPlayerLoading && playback.player.currentPlayingEpisodeId == briefingId,
                onPlayPauseClick = {
                    playDailyBriefingFromFeed(
                        briefing = briefing,
                        briefingId = briefingId,
                        playback = playback.player,
                        callbacks = callbacks,
                        context = context,
                    )
                },
                onClick = {
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingBannerTapped(
                        region = briefing.region,
                        date = briefing.date,
                    )
                    callbacks.onBriefingClick(briefing.region)
                },
                onDismiss = callbacks.onDismissBriefing,
                onDismissForever = callbacks.onDismissBriefingForever,
                onFeedbackClick = callbacks.onFeedbackClick,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

private fun playDailyBriefingFromFeed(
    briefing: Briefing,
    briefingId: String,
    playback: HomePlaybackUi,
    callbacks: HomeFeedCallbacks,
    context: Context,
) {
    val isCurrentPlaying = playback.isPlaying && playback.currentPlayingEpisodeId == briefingId
    trackDailyBriefingPlayPause(briefing, isCurrentPlaying)
    val localCoverUrl = dailyBriefingLocalCoverUrl(context, briefing.region)
    callbacks.onPlayEpisode(
        dailyBriefingEpisode(briefing, briefingId, localCoverUrl),
        dailyBriefingPodcast(briefing, localCoverUrl),
        PlaybackEntryPoint.GENERIC,
    )
}

private fun trackDailyBriefingPlayPause(
    briefing: Briefing,
    isCurrentPlaying: Boolean,
) {
    if (isCurrentPlaying) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingPauseClicked(
            region = briefing.region,
            date = briefing.date,
            source = "home_banner",
        )
    } else {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingPlayClicked(
            region = briefing.region,
            date = briefing.date,
            source = "home_banner",
        )
    }
}

private fun dailyBriefingEpisode(
    briefing: Briefing,
    briefingId: String,
    localCoverUrl: String,
): Episode {
    val versionParam = dailyBriefingVersionParam(briefing.audioUrl)
    return Episode(
        id = briefingId,
        title = briefing.title,
        description = "Your daily AI-generated news briefing for ${briefing.region.uppercase()}.",
        audioUrl = briefing.audioUrl,
        imageUrl = localCoverUrl,
        podcastId = "briefing_${briefing.region}",
        podcastTitle = "The Boxlore Brief",
        podcastImageUrl = localCoverUrl,
        podcastArtist = "BoxCast AI",
        duration = 180,
        publishedDate = dailyBriefingPublishedDate(briefing.date),
        transcriptUrl =
            briefing.transcriptUrl
                ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/transcript/${briefing.region}?d=${briefing.date}$versionParam",
        chaptersUrl =
            briefing.chaptersUrl
                ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/${briefing.region}?d=${briefing.date}$versionParam",
    )
}

private fun dailyBriefingPodcast(
    briefing: Briefing,
    localCoverUrl: String,
): Podcast =
    Podcast(
        id = "briefing_${briefing.region}",
        title = "The Boxlore Brief",
        artist = "BoxCast AI",
        imageUrl = localCoverUrl,
    )

private fun dailyBriefingPublishedDate(date: String): Long =
    try {
        LocalDate
            .parse(date)
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()
    } catch (e: Exception) {
        System.currentTimeMillis() / 1000
    }

private fun dailyBriefingVersionParam(audioUrl: String): String {
    val version = Uri.parse(audioUrl).getQueryParameter("v")
    return if (version != null) "&v=$version" else ""
}

private fun dailyBriefingLocalCoverUrl(
    context: Context,
    region: String,
): String {
    val resId =
        when (region.lowercase()) {
            "in", "ind" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_india
            "uk", "gb" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_uk
            "us", "usa" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_usa
            else -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_global
        }
    return "android.resource://${context.packageName}/$resId"
}
