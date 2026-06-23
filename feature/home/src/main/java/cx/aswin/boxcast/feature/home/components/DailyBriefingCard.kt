package cx.aswin.boxcast.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Briefing
import cx.aswin.boxcast.core.model.BriefingSource
import cx.aswin.boxcast.core.model.EpisodeStatus

enum class DailyBriefingCardState {
    NORMAL,
    CONFIRM_DISMISS,
    CONFIRM_FOREVER
}

@Composable
fun DailyBriefingCard(
    briefing: Briefing,
    chapters: List<cx.aswin.boxcast.core.model.Chapter>,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    playbackStatus: EpisodeStatus? = null,
    playbackProgress: Float? = null,
    onDismissForever: () -> Unit = {},
    onFeedbackClick: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    
    val formattedDate = remember(briefing.date) {
        try {
            val localDate = java.time.LocalDate.parse(briefing.date)
            val dayOfWeek = localDate.format(java.time.format.DateTimeFormatter.ofPattern("E", java.util.Locale.US))
            val dayOfMonth = localDate.dayOfMonth
            val suffix = when (dayOfMonth) {
                11, 12, 13 -> "th"
                else -> when (dayOfMonth % 10) {
                    1 -> "st"
                    2 -> "nd"
                    3 -> "rd"
                    else -> "th"
                }
            }
            val month = localDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM", java.util.Locale.US))
            "$dayOfWeek, $dayOfMonth$suffix $month"
        } catch (e: Exception) {
            briefing.date
        }
    }

    val durationMin = remember(chapters) {
        if (chapters.isNotEmpty()) {
            val totalSeconds = chapters.last().startTime + 30.0
            val mins = Math.round(totalSeconds / 60.0).toInt()
            Math.max(1, mins)
        } else {
            3
        }
    }

    val timeLeftMin = remember(durationMin, playbackProgress) {
        if (playbackProgress != null) {
            val totalSeconds = if (chapters.isNotEmpty()) {
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .expressiveClickable(
                shape = RoundedCornerShape(24.dp),
                onClick = onClick
            )
    ) {
        // Background cover art
        OptimizedImage(
            url = briefing.coverUrl,
            proxyWidth = 720,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        )

        // Gradient overlay — multi-stop for dramatic effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.45f),
                            0.3f to Color.Black.copy(alpha = 0.6f),
                            0.55f to Color.Black.copy(alpha = 0.8f),
                            1.0f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Content overlay
        var cardState by remember { mutableStateOf(DailyBriefingCardState.NORMAL) }

        // Content overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
        ) {
            // Static Top bar: Logo & Date Column + Dismiss
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // BoxCast Brief logo and date column
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(
                            id = cx.aswin.boxcast.core.designsystem.R.drawable.ic_boxlore_brief_logo
                        ),
                        contentDescription = "The Boxcast Brief",
                        modifier = Modifier.height(48.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                    )
                    
                    // Date chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.28f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = formattedDate,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Dismiss / Cancel button
                IconButton(
                    onClick = {
                        if (cardState == DailyBriefingCardState.NORMAL) {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingDismissInitiated(
                                region = briefing.region,
                                date = briefing.date
                            )
                            cardState = DailyBriefingCardState.CONFIRM_DISMISS
                        } else {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingDismissCancelled(
                                region = briefing.region,
                                date = briefing.date,
                                previousState = cardState.name
                            )
                            cardState = DailyBriefingCardState.NORMAL
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = if (cardState == DailyBriefingCardState.NORMAL) "Dismiss briefing" else "Cancel dismissal",
                        modifier = Modifier.size(18.dp)
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
                modifier = Modifier.fillMaxWidth()
            ) { state ->
                when (state) {
                    DailyBriefingCardState.NORMAL -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Title
                            Text(
                                text = briefing.title,
                                fontFamily = SectionHeaderFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 26.sp,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Listen Now / Playing pill button
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .height(48.dp)
                                    .widthIn(min = 180.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable { onPlayPauseClick() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause briefing" else "Play briefing",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            isPlaying -> "Playing"
                                            playbackStatus == EpisodeStatus.IN_PROGRESS -> "Resume · ${timeLeftMin} min left"
                                            else -> "Listen Now · ${durationMin} min"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }

                            // Vertical chapters list (showing 3 by default, max 1 line per chapter)
                            if (chapters.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val visibleChapters = if (expanded) chapters else chapters.take(3)
                                    visibleChapters.forEachIndexed { index, chapter ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .then(
                                                    if ((index == 2 && !expanded && chapters.size > 3) || 
                                                        (index == chapters.size - 1 && expanded && chapters.size > 3)) {
                                                        Modifier.clickable {
                                                            val nextExpanded = !expanded
                                                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingCardChaptersToggled(
                                                                region = briefing.region,
                                                                date = briefing.date,
                                                                expanded = nextExpanded
                                                            )
                                                            expanded = nextExpanded
                                                        }
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val mins = chapter.startTime.toLong() / 60
                                            val secs = chapter.startTime.toLong() % 60
                                            val timeStr = String.format(java.util.Locale.US, "%d:%02d", mins, secs)
                                            Text(
                                                text = timeStr,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                                modifier = Modifier.width(36.dp)
                                            )
                                            Text(
                                                text = chapter.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            // Inline expand/collapse indicator
                                            if (index == 2 && !expanded && chapters.size > 3) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier.padding(start = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "+${chapters.size - 3}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White.copy(alpha = 0.6f)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Rounded.ExpandMore,
                                                        contentDescription = "Show more",
                                                        tint = Color.White.copy(alpha = 0.6f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else if (index == chapters.size - 1 && expanded && chapters.size > 3) {
                                                Icon(
                                                    imageVector = Icons.Rounded.ExpandLess,
                                                    contentDescription = "Show less",
                                                    tint = Color.White.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp).padding(start = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Bottom padding
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    DailyBriefingCardState.CONFIRM_DISMISS -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Dismiss for Today Button (styled exactly like the Listen Now button for visual consistency)
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .height(48.dp)
                                    .widthIn(min = 180.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable {
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingDismissedToday(
                                            region = briefing.region,
                                            date = briefing.date
                                        )
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Dismiss for Today",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Dismiss forever",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .clickable {
                                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingDismissForeverInitiated(
                                            region = briefing.region,
                                            date = briefing.date
                                        )
                                        cardState = DailyBriefingCardState.CONFIRM_FOREVER
                                    }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    DailyBriefingCardState.CONFIRM_FOREVER -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Title
                            Text(
                                text = "Are you sure?",
                                fontFamily = SectionHeaderFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "We would love to hear your feedback and improve the brief.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Buttons side by side (using standard M3 48.dp height & 24.dp shape)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .weight(1f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable {
                                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingFeedbackClicked(
                                                region = briefing.region,
                                                date = briefing.date
                                            )
                                            onFeedbackClick()
                                        }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = "Send Feedback",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    contentColor = Color.White,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .weight(1f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .clickable {
                                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDailyBriefingDismissedForever(
                                                region = briefing.region,
                                                date = briefing.date
                                            )
                                            onDismissForever()
                                        }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = "I'm Sure",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
