package cx.aswin.boxlore.feature.library.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.ListeningDayActivity
import cx.aswin.boxlore.core.model.ListeningInsightSummary
import cx.aswin.boxlore.core.model.ListeningTimeBucket
import cx.aswin.boxlore.feature.library.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.max

private sealed interface InsightCard {
    data class Metric(
        val label: String,
        val value: String,
        val detail: String? = null,
    ) : InsightCard {
        val detailHasNumber: Boolean
            get() = detail?.any { it.isDigit() } == true
    }

    data class TopShow(
        val name: String,
        val detail: String,
        val imageUrl: String?,
    ) : InsightCard
}

private val InsightCardWidth = 280.dp
private val InsightCardHeight = 156.dp

@Composable
fun HistoryInsightCarousel(
    insights: ListeningInsightSummary,
    modifier: Modifier = Modifier,
) {
    val cards = buildInsightCards(insights)

    if (cards.isEmpty()) return

    val pagerState = rememberPagerState { cards.size }
    val horizontalOverscrollLock =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset = Offset(x = available.x, y = 0f)
            }
        }

    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fixed(InsightCardWidth),
        contentPadding = PaddingValues(end = 48.dp),
        pageSpacing = 12.dp,
        modifier =
            modifier
                .fillMaxWidth()
                .height(InsightCardHeight)
                .nestedScroll(horizontalOverscrollLock),
    ) { index ->
        InsightSwipeCard(
            card = cards[index],
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun buildInsightCards(insights: ListeningInsightSummary): List<InsightCard> {
    val peakLabel = peakBucketLabel(insights)
    val peakConsumedMs = peakBucketConsumedMs(insights)
    val peakShare = peakBucketShare(insights, peakConsumedMs)
    return buildList {
        addTopShowCard(insights)
        addMetricCards(insights, peakLabel, peakConsumedMs, peakShare)
    }
}

@Composable
private fun peakBucketLabel(insights: ListeningInsightSummary): String =
    insights.peakBucket?.let { timeBucketLabel(it) }
        ?: stringResource(R.string.history_waiting_for_plays)

private fun peakBucketConsumedMs(insights: ListeningInsightSummary): Long =
    when (insights.peakBucket) {
        ListeningTimeBucket.MORNING -> insights.morningMs
        ListeningTimeBucket.AFTERNOON -> insights.afternoonMs
        ListeningTimeBucket.EVENING -> insights.eveningMs
        ListeningTimeBucket.NIGHT -> insights.nightMs
        null -> 0L
    }

private fun peakBucketShare(
    insights: ListeningInsightSummary,
    peakConsumedMs: Long,
): Int? =
    if (insights.peakBucket != null && insights.totalConsumedMs > 0L) {
        ((peakConsumedMs * 100L) / insights.totalConsumedMs).toInt().coerceIn(0, 100)
    } else {
        null
    }

@Composable
private fun MutableList<InsightCard>.addTopShowCard(insights: ListeningInsightSummary) {
    val topShow = insights.topShow ?: return
    add(
        InsightCard.TopShow(
            name = topShow.podcastName,
            detail =
                if (topShow.consumedMs > 0) {
                    stringResource(
                        R.string.history_top_show_listened,
                        formatDuration(topShow.consumedMs),
                    )
                } else {
                    stringResource(
                        R.string.history_top_show_play_count,
                        topShow.sessionCount,
                    )
                },
            imageUrl = topShow.podcastImageUrl,
        ),
    )
}

@Composable
private fun MutableList<InsightCard>.addMetricCards(
    insights: ListeningInsightSummary,
    peakLabel: String,
    peakConsumedMs: Long,
    peakShare: Int?,
) {
    add(
        InsightCard.Metric(
            label = stringResource(R.string.history_card_active_days),
            value = insights.activeDaysInPeriod.toString(),
            detail = stringResource(R.string.history_active_days_detail),
        ),
    )
    add(
        InsightCard.Metric(
            label = stringResource(R.string.history_card_completion),
            value = insights.completedCount.toString(),
            detail =
                stringResource(
                    R.string.history_completion_in_progress,
                    insights.inProgressCount,
                ),
        ),
    )
    add(
        InsightCard.Metric(
            label = stringResource(R.string.history_card_window),
            value = peakLabel,
            detail =
                when {
                    peakShare != null && peakConsumedMs > 0L ->
                        stringResource(
                            R.string.history_window_share,
                            formatDuration(peakConsumedMs),
                            peakShare,
                        )
                    else -> null
                },
        ),
    )
    if (insights.streakDays > 0) {
        add(
            InsightCard.Metric(
                label = stringResource(R.string.history_stat_streak),
                value = insights.streakDays.toString(),
                detail = null,
            ),
        )
    }
    if (insights.hasEnoughData) {
        add(
            InsightCard.Metric(
                label = stringResource(R.string.history_card_sessions),
                value = formatDuration(insights.averageSessionMs),
                detail =
                    stringResource(
                        R.string.history_sessions_longest,
                        formatDuration(insights.longestSessionMs),
                    ),
            ),
        )
    }
}

@Composable
private fun InsightSwipeCard(
    card: InsightCard,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        when (card) {
            is InsightCard.TopShow -> TopShowCardBody(card)
            is InsightCard.Metric -> MetricCardBody(card)
        }
    }
}

@Composable
private fun MetricCardBody(card: InsightCard.Metric) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        val heightPx = maxHeight.value
        val lengthFactor =
            when {
                card.value.length <= 2 -> 0.62f
                card.value.length <= 4 -> 0.48f
                card.value.length <= 8 -> 0.34f
                else -> 0.24f
            }
        val valueSp =
            (heightPx * lengthFactor).coerceIn(36f, 72f).sp

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!card.detailHasNumber) {
                    card.detail?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = card.value,
                    style =
                        MaterialTheme.typography.displayMedium.copy(
                            fontSize = valueSp,
                            lineHeight = valueSp * 1.05f,
                        ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                if (card.detailHasNumber) {
                    Text(
                        text = card.detail.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopShowCardBody(card: InsightCard.TopShow) {
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!card.imageUrl.isNullOrBlank()) {
            OptimizedImage(
                url = card.imageUrl,
                proxyWidth = 200,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.history_card_top_show),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = card.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun HistoryActivityGraph(
    dailyActivity: List<ListeningDayActivity>,
    modifier: Modifier = Modifier,
) {
    if (dailyActivity.isEmpty()) return

    val weeks =
        remember(dailyActivity) {
            val byDay = dailyActivity.associateBy { it.localDay }
            val minDay = dailyActivity.minOf { it.localDay }
            val maxDay = dailyActivity.maxOf { it.localDay }
            val firstMonday =
                LocalDate.ofEpochDay(minDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val lastSunday =
                LocalDate.ofEpochDay(maxDay).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val weekStarts = mutableListOf<LocalDate>()
            var cursor = firstMonday
            while (!cursor.isAfter(lastSunday)) {
                weekStarts += cursor
                cursor = cursor.plusWeeks(1)
            }
            weekStarts.map { start ->
                (0..6).map { offset ->
                    val date = start.plusDays(offset.toLong())
                    date to (byDay[date.toEpochDay()]?.consumedMs ?: 0L)
                }
            }
        }
    if (weeks.isEmpty()) return

    val pageCount = weeks.size
    val lastPage = (pageCount - 1).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = lastPage) { pageCount }
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage(lastPage)
        }
    }
    val safePage = pagerState.currentPage.coerceIn(0, lastPage)
    val week = weeks[safePage]
    val rangeLabel =
        remember(week) {
            val start = week.first().first
            val end = week.last().first
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            "${start.format(fmt)} – ${end.format(fmt)}"
        }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_activity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = rangeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(148.dp),
            ) { page ->
                val pageWeek = weeks[page]
                val pageMax = pageWeek.maxOf { it.second }.coerceAtLeast(1L)
                WeekdayBars(
                    week = pageWeek,
                    maxMs = pageMax,
                )
            }
            Text(
                text = stringResource(R.string.history_activity_swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HistoryTimeOfDayGraph(
    insights: ListeningInsightSummary,
    modifier: Modifier = Modifier,
) {
    val buckets =
        listOf(
            ListeningTimeBucket.MORNING to insights.morningMs,
            ListeningTimeBucket.AFTERNOON to insights.afternoonMs,
            ListeningTimeBucket.EVENING to insights.eveningMs,
            ListeningTimeBucket.NIGHT to insights.nightMs,
        )
    val totalMs = buckets.sumOf { it.second }
    val maxMs = buckets.maxOf { it.second }.coerceAtLeast(1L)
    val peakLabel =
        when (insights.peakBucket) {
            ListeningTimeBucket.MORNING -> stringResource(R.string.history_bucket_morning)
            ListeningTimeBucket.AFTERNOON -> stringResource(R.string.history_bucket_afternoon)
            ListeningTimeBucket.EVENING -> stringResource(R.string.history_bucket_evening)
            ListeningTimeBucket.NIGHT -> stringResource(R.string.history_bucket_night)
            null -> null
        }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_tod_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (peakLabel != null && totalMs > 0) {
                    Text(
                        text = stringResource(R.string.history_tod_peak, peakLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (totalMs <= 0L) {
                Text(
                    text = stringResource(R.string.history_tod_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TimeOfDayBars(
                    buckets = buckets,
                    peakBucket = insights.peakBucket,
                    maxMs = maxMs,
                    modifier = Modifier.height(148.dp),
                )
            }
        }
    }
}

private data class TimeOfDayBarColors(
    val peak: Color,
    val bar: Color,
    val track: Color,
)

@Composable
private fun TimeOfDayBars(
    buckets: List<Pair<ListeningTimeBucket, Long>>,
    peakBucket: ListeningTimeBucket?,
    maxMs: Long,
    modifier: Modifier = Modifier,
) {
    val colors =
        TimeOfDayBarColors(
            peak = MaterialTheme.colorScheme.primary,
            bar = MaterialTheme.colorScheme.secondary,
            track = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { (bucket, ms) ->
            TimeOfDayBarColumn(
                bucket = bucket,
                ms = ms,
                maxMs = maxMs,
                peakBucket = peakBucket,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun timeBucketLabel(bucket: ListeningTimeBucket): String =
    when (bucket) {
        ListeningTimeBucket.MORNING -> stringResource(R.string.history_bucket_morning)
        ListeningTimeBucket.AFTERNOON -> stringResource(R.string.history_bucket_afternoon)
        ListeningTimeBucket.EVENING -> stringResource(R.string.history_bucket_evening)
        ListeningTimeBucket.NIGHT -> stringResource(R.string.history_bucket_night)
    }

@Composable
private fun TimeOfDayBarColumn(
    bucket: ListeningTimeBucket,
    ms: Long,
    maxMs: Long,
    peakBucket: ListeningTimeBucket?,
    colors: TimeOfDayBarColors,
    modifier: Modifier = Modifier,
) {
    val fraction = (ms.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
    val fill = if (bucket == peakBucket) colors.peak else colors.bar
    val label = timeBucketLabel(bucket)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (ms > 0) formatDuration(ms) else "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.track),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height((96.dp * max(fraction, if (ms > 0) 0.08f else 0f)))
                        .clip(RoundedCornerShape(12.dp))
                        .background(fill),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bucket == peakBucket) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WeekdayBars(
    week: List<Pair<LocalDate, Long>>,
    maxMs: Long,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { (date, ms) ->
            val fraction = (ms.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (ms > 0) formatDuration(ms) else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(trackColor),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height((96.dp * max(fraction, if (ms > 0) 0.08f else 0f)))
                                .clip(RoundedCornerShape(12.dp))
                                .background(barColor),
                    )
                }
                Text(
                    text =
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun HistoryDayChips(
    activeDays: Set<LocalDate>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate?) -> Unit,
    onPickOlderDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = activeDays.sortedDescending().take(7)
    val chipFormatter = DateTimeFormatter.ofPattern("MMM d")

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Keep chips in a horizontal scroll without claiming a second full row.
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(recent.size) { index ->
                    val day = recent[index]
                    FilterChip(
                        selected = selectedDate == day,
                        onClick = { onSelectDate(if (selectedDate == day) null else day) },
                        label = { Text(day.format(chipFormatter)) },
                    )
                }
            }
        }
        if (selectedDate != null) {
            IconButton(onClick = { onSelectDate(null) }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.history_clear_date_filter),
                )
            }
        }
        IconButton(onClick = onPickOlderDate) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = stringResource(R.string.history_pick_date),
            )
        }
    }
}
