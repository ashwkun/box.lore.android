package cx.aswin.boxlore.feature.library.history

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.feature.library.DetailedHistoryStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


@Composable
fun ActivityCalendarStrip(
    activeDays: Set<LocalDate>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val dates = remember(today) {
        (0..13).map { today.minusDays(it.toLong()) }.reversed()
    }
    
    val listState = rememberLazyListState()
    
    val targetDate = selectedDate ?: today
    val targetIndex = remember(dates, targetDate) {
        dates.indexOf(targetDate).coerceAtLeast(0)
    }
    
    LaunchedEffect(targetIndex) {
        listState.animateScrollToItem(targetIndex)
    }
    
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(dates) { date ->
                    CalendarDayItem(
                        date = date,
                        isSelected = date == selectedDate,
                        isToday = date == today,
                        hasActivity = activeDays.contains(date),
                        onDateSelected = onDateSelected
                    )
                }
            }
        }
    }
}


@Composable
private fun CalendarDayItem(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasActivity: Boolean,
    onDateSelected: (LocalDate?) -> Unit
) {
    val dayOfWeek = date.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT,
        java.util.Locale.getDefault()
    ).take(1)
    val dayOfMonth = date.dayOfMonth.toString()
    
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        hasActivity -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        hasActivity -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    
    val borderStroke = if (isToday && !isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else null
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = borderStroke,
        modifier = Modifier
            .width(46.dp)
            .aspectRatio(0.8f)
            .clickable {
                if (isSelected) {
                    onDateSelected(null)
                } else {
                    onDateSelected(date)
                }
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = dayOfWeek,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dayOfMonth,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (hasActivity) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        )
                )
            } else {
                Spacer(modifier = Modifier.size(6.dp))
            }
        }
    }
}


@Composable
fun CalendarInsightBanner(
    stats: DetailedHistoryStats,
    selectedDate: LocalDate?,
    groupedHistory: Map<LocalDate, List<ListeningHistoryEntity>>,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val isFiltered = selectedDate != null
    
    val containerColor = if (isFiltered) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    }
    
    val contentColor = if (isFiltered) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    
    val borderStroke = BorderStroke(
        width = 1.dp,
        color = if (isFiltered) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        }
    )

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Crossfade(
            targetState = selectedDate,
            label = "insight_banner_transition"
        ) { targetDate ->
            val hasFilter = targetDate != null
            val icon = calculateBannerIcon(hasFilter, today, stats)
            val iconColor = if (hasFilter) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                val descText = if (targetDate != null) {
                    calculateBannerFilteredText(targetDate, today, groupedHistory[targetDate] ?: emptyList())
                } else {
                    calculateBannerUnfilteredText(today, stats)
                }

                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                
                if (hasFilter) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClearFilter,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear filter",
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}


private fun calculateBannerIcon(hasFilter: Boolean, today: LocalDate, stats: DetailedHistoryStats): androidx.compose.ui.graphics.vector.ImageVector {
    if (hasFilter) return Icons.Rounded.PlayArrow
    val last14Days = (0..13).map { today.minusDays(it.toLong()) }
    val activeCount = last14Days.count { stats.activeDays.contains(it) }
    return if (activeCount >= 5) Icons.Rounded.Whatshot else Icons.Rounded.Bolt
}


private fun calculateBannerFilteredText(
    targetDate: LocalDate,
    today: LocalDate,
    episodes: List<ListeningHistoryEntity>
): String {
    val dateStr = when (targetDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> targetDate.format(DateTimeFormatter.ofPattern("MMMM d"))
    }
    var dailyMs = 0L
    episodes.forEach { entity ->
        val isComplete = entity.isCompleted || (entity.durationMs > 0 && entity.progressMs > entity.durationMs * 0.9f)
        dailyMs += if (isComplete && entity.durationMs > 0) entity.durationMs else entity.progressMs
    }
    val hours = TimeUnit.MILLISECONDS.toHours(dailyMs)
    val mins = TimeUnit.MILLISECONDS.toMinutes(dailyMs) % 60
    val durationStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    val epCount = episodes.size
    return "On $dateStr, you played $epCount ${if (epCount == 1) "episode" else "episodes"} for a total of $durationStr."
}


private fun calculateBannerUnfilteredText(today: LocalDate, stats: DetailedHistoryStats): String {
    val last14Days = (0..13).map { today.minusDays(it.toLong()) }
    val activeCount = last14Days.count { stats.activeDays.contains(it) }
    return when {
        activeCount == 14 -> "Perfect fortnight! You listened every day for the last 14 days."
        activeCount >= 10 -> "Incredible consistency! You listened on $activeCount of the last 14 days."
        activeCount >= 5 -> "Great habit! You listened on $activeCount of the last 14 days."
        activeCount >= 1 -> "You listened on $activeCount of the last 14 days recently. Keep it up!"
        else -> "No listening history in the last 14 days. Start listening today!"
    }
}

