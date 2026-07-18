package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.EpisodeStatus

enum class DailyBriefingCardState {
    NORMAL,
    CONFIRM_DISMISS,
    CONFIRM_FOREVER,
}

@Composable
fun DailyBriefingCard(
    briefing: Briefing,
    chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    playbackStatus: EpisodeStatus? = null,
    playbackProgress: Float? = null,
    isBuffering: Boolean = false,
    onDismissForever: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    val formattedDate =
        remember(briefing.date) {
            try {
                val localDate = java.time.LocalDate.parse(briefing.date)
                val dayOfWeek =
                    localDate.format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("E", java.util.Locale.US),
                    )
                val dayOfMonth = localDate.dayOfMonth
                val suffix =
                    when (dayOfMonth) {
                        11, 12, 13 -> "th"
                        else ->
                            when (dayOfMonth % 10) {
                                1 -> "st"
                                2 -> "nd"
                                3 -> "rd"
                                else -> "th"
                            }
                    }
                val month =
                    localDate.format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("MMMM", java.util.Locale.US),
                    )
                "$dayOfWeek, $dayOfMonth$suffix $month"
            } catch (e: Exception) {
                briefing.date
            }
        }

    val durationMin =
        remember(chapters) {
            if (chapters.isNotEmpty()) {
                val totalSeconds = chapters.last().startTime + 30.0
                val mins = Math.round(totalSeconds / 60.0).toInt()
                Math.max(1, mins)
            } else {
                3
            }
        }

    val timeLeftMin =
        remember(durationMin, playbackProgress) {
            if (playbackProgress != null) {
                val totalSeconds =
                    if (chapters.isNotEmpty()) {
                        chapters.last().startTime + 30.0
                    } else {
                        180.0
                    }
                val remainingSeconds = totalSeconds * (1.0f - playbackProgress)
                val mins = Math.round(remainingSeconds / 60.0).toInt()
                Math.max(1, mins)
            } else {
                durationMin
            }
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .expressiveClickable(
                    shape = RoundedCornerShape(24.dp),
                    onClick = onClick,
                ).clip(RoundedCornerShape(24.dp))
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(24.dp),
                ),
    ) {
        val coverResId =
            remember(briefing.region) {
                when (briefing.region.lowercase()) {
                    "in", "ind" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_india
                    "uk", "gb" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_uk
                    "us", "usa" -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_usa
                    else -> cx.aswin.boxlore.core.designsystem.R.drawable.daily_briefing_global
                }
            }

        // Background cover art
        androidx.compose.foundation.Image(
            painter = painterResource(id = coverResId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .matchParentSize(),
        )

        // Gradient overlay — multi-stop for dramatic effect
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.45f),
                                    0.3f to Color.Black.copy(alpha = 0.6f),
                                    0.55f to Color.Black.copy(alpha = 0.8f),
                                    1.0f to Color.Black.copy(alpha = 0.95f),
                                ),
                        ),
                    ),
        )

        // Content overlay
        var cardState by remember { mutableStateOf(DailyBriefingCardState.NORMAL) }

        // Content overlay
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                    ),
        ) {
            // Static Top bar: Logo & Date Column + Dismiss
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Boxlore Brief logo and date column
                val primaryColor = MaterialTheme.colorScheme.primary

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.foundation.Image(
                        painter =
                            painterResource(
                                id = cx.aswin.boxlore.core.designsystem.R.drawable.ic_boxlore_brief_logo,
                            ),
                        contentDescription = "The Boxlore Brief",
                        modifier = Modifier.height(48.dp),
                        colorFilter =
                            androidx.compose.ui.graphics.ColorFilter
                                .tint(Color.White),
                    )

                    // Date chip: using opaque Material 3 container colors to avoid transparency visibility issues on dark backdrop
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                primaryColor,
                            ),
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(
                            text = formattedDate,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                // Dismiss / Cancel button
                IconButton(
                    onClick = {
                        if (cardState == DailyBriefingCardState.NORMAL) {
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                                action = "dismiss_initiated",
                                region = briefing.region,
                                date = briefing.date,
                            )
                            cardState = DailyBriefingCardState.CONFIRM_DISMISS
                        } else {
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                                action = "dismiss_cancelled",
                                region = briefing.region,
                                date = briefing.date,
                                extraProps = mapOf("previous_state" to cardState.name),
                            )
                            cardState = DailyBriefingCardState.NORMAL
                        }
                    },
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f),
                        ),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = if (cardState == DailyBriefingCardState.NORMAL) "Dismiss briefing" else "Cancel dismissal",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Static Spacer for the art to breathe
            Spacer(modifier = Modifier.height(80.dp))

            // Animated body content
            AnimatedContent(
                targetState = cardState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "card_body_transition",
                modifier = Modifier.fillMaxWidth(),
            ) { state ->
                when (state) {
                    DailyBriefingCardState.NORMAL -> {
                        DailyBriefingNormalContent(
                            state =
                                DailyBriefingVisualState(
                                    briefing = briefing,
                                    chapters = chapters,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    playbackStatus = playbackStatus,
                                    durationMin = durationMin,
                                    timeLeftMin = timeLeftMin,
                                ),
                            onPlayPauseClick = onPlayPauseClick,
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                        )
                    }
                    DailyBriefingCardState.CONFIRM_DISMISS -> {
                        DailyBriefingDismissContent(
                            briefing = briefing,
                            onDismiss = onDismiss,
                            onDismissForeverClick = { cardState = DailyBriefingCardState.CONFIRM_FOREVER },
                        )
                    }
                    DailyBriefingCardState.CONFIRM_FOREVER -> {
                        DailyBriefingForeverContent(
                            briefing = briefing,
                            onFeedbackClick = onFeedbackClick,
                            onDismissForever = onDismissForever,
                        )
                    }
                }
            }
        }
    }
}

private data class DailyBriefingVisualState(
    val briefing: Briefing,
    val chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val playbackStatus: EpisodeStatus?,
    val durationMin: Int,
    val timeLeftMin: Int,
)

@Composable
private fun DailyBriefingChapterRow(
    chapter: cx.aswin.boxlore.core.model.Chapter,
    index: Int,
    totalChapters: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if ((index == 2 && !expanded && totalChapters > 3) ||
                        (index == totalChapters - 1 && expanded && totalChapters > 3)
                    ) {
                        Modifier.clickable { onToggleExpanded() }
                    } else {
                        Modifier
                    },
                ).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val mins = chapter.startTime.toLong() / 60
        val secs = chapter.startTime.toLong() % 60
        val timeStr = String.format(java.util.Locale.US, "%d:%02d", mins, secs)
        Text(
            text = timeStr,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            modifier = Modifier.width(36.dp),
        )
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Inline expand/collapse indicator
        if (index == 2 && !expanded && totalChapters > 3) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(
                    text = "+${totalChapters - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = "Show more",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        } else if (index == totalChapters - 1 && expanded && totalChapters > 3) {
            Icon(
                imageVector = Icons.Rounded.ExpandLess,
                contentDescription = "Show less",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun DailyBriefingPlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackStatus: EpisodeStatus?,
    timeLeftMin: Int,
    durationMin: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier =
            Modifier
                .padding(horizontal = 20.dp)
                .height(48.dp)
                .widthIn(min = 180.dp)
                .clip(RoundedCornerShape(24.dp))
                .expressiveClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isBuffering) {
                BoxLoreLoader.CircularWavy(
                    size = 20.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause briefing" else "Play briefing",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text =
                    when {
                        isPlaying -> "Playing"
                        playbackStatus == EpisodeStatus.IN_PROGRESS -> "Resume · $timeLeftMin min left"
                        else -> "Listen Now · $durationMin min"
                    },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
private fun DailyBriefingChaptersList(
    chapters: List<cx.aswin.boxlore.core.model.Chapter>,
    briefingRegion: String,
    briefingDate: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    if (chapters.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val visibleChapters = if (expanded) chapters else chapters.take(3)
            visibleChapters.forEachIndexed { index, chapter ->
                DailyBriefingChapterRow(
                    chapter = chapter,
                    index = index,
                    totalChapters = chapters.size,
                    expanded = expanded,
                    onToggleExpanded = {
                        val nextExpanded = !expanded
                        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                            action = "card_chapters_toggled",
                            region = briefingRegion,
                            date = briefingDate,
                            extraProps = mapOf("expanded" to nextExpanded),
                        )
                        onExpandedChange(nextExpanded)
                    },
                )
            }
        }
    }
}

@Composable
private fun DailyBriefingNormalContent(
    state: DailyBriefingVisualState,
    onPlayPauseClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Title
        Text(
            text = state.briefing.title,
            fontFamily = SectionHeaderFontFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 26.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        DailyBriefingPlayButton(
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            playbackStatus = state.playbackStatus,
            timeLeftMin = state.timeLeftMin,
            durationMin = state.durationMin,
            onClick = onPlayPauseClick,
        )

        DailyBriefingChaptersList(
            chapters = state.chapters,
            briefingRegion = state.briefing.region,
            briefingDate = state.briefing.date,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )

        // Bottom padding
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DailyBriefingDismissContent(
    briefing: Briefing,
    onDismiss: () -> Unit,
    onDismissForeverClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Dismiss for Today Button (styled exactly like the Listen Now button for visual consistency)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier =
                Modifier
                    .padding(horizontal = 20.dp)
                    .height(48.dp)
                    .widthIn(min = 180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable {
                        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                            action = "dismissed_today",
                            region = briefing.region,
                            date = briefing.date,
                        )
                        onDismiss()
                    },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Dismiss for Today",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    letterSpacing = 0.3.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Dismiss forever",
            style =
                MaterialTheme.typography.labelMedium.copy(
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                ),
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.6f),
            modifier =
                Modifier
                    .padding(horizontal = 20.dp)
                    .clickable {
                        cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                            action = "dismiss_forever_initiated",
                            region = briefing.region,
                            date = briefing.date,
                        )
                        onDismissForeverClick()
                    },
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DailyBriefingForeverContent(
    briefing: Briefing,
    onFeedbackClick: () -> Unit,
    onDismissForever: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Title
        Text(
            text = "Are you sure?",
            fontFamily = SectionHeaderFontFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "We would love to hear your feedback and improve the brief.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons side by side (using standard M3 48.dp height & 24.dp shape)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .height(48.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                                action = "feedback_clicked",
                                region = briefing.region,
                                date = briefing.date,
                            )
                            onFeedbackClick()
                        },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = "Send Feedback",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                contentColor = Color.White,
                modifier =
                    Modifier
                        .height(48.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackDailyBriefingInteraction(
                                action = "dismissed_forever",
                                region = briefing.region,
                                date = briefing.date,
                            )
                            onDismissForever()
                        },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = "I'm Sure",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
