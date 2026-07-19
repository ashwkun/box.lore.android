package cx.aswin.boxlore.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.ranking.FeatureSlot
import cx.aswin.boxlore.core.ranking.LearnerFacetDebug
import cx.aswin.boxlore.core.ranking.LearnerFeatureWeightDebug
import cx.aswin.boxlore.core.ranking.LearnerInspectorSnapshot
import cx.aswin.boxlore.core.ranking.LearningEvent
import cx.aswin.boxlore.core.ranking.LearningEventKind
import cx.aswin.boxlore.core.ranking.PreferenceFacetType
import cx.aswin.boxlore.core.ranking.RankingDebugSnapshot
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingShadowSnapshot
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private enum class LearnerPane(
    val label: String,
) {
    Signals("Signals"),
    Taste("Taste"),
    Model("Model"),
}

internal data class AdaptiveLearnerDebugState(
    val snapshot: LearnerInspectorSnapshot?,
    val events: List<LearningEvent>,
    val logEnabled: Boolean,
    val shadowDiagnostics: List<RankingShadowSnapshot>,
    val loading: Boolean,
)

/**
 * Debug inspector for the on-device ranking engine. Three concrete, non-animated views:
 * a live signal feed (what hit the engine and how it moved it), the durable taste profile,
 * and the model internals. The signal feed is gated by [AdaptiveLearnerDebugState.logEnabled].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveLearnerDebugSection(
    state: AdaptiveLearnerDebugState,
    onSetLogEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    var paneIndex by remember { mutableIntStateOf(0) }
    val panes = remember { LearnerPane.entries }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LearnerHeader(
            snapshot = state.snapshot,
            loading = state.loading,
            onRefresh = onRefresh,
        )
        LogToggleCard(
            enabled = state.logEnabled,
            eventCount = state.events.size,
            onToggle = onSetLogEnabled,
        )

        PrimaryScrollableTabRow(
            selectedTabIndex = paneIndex,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            panes.forEachIndexed { index, pane ->
                Tab(
                    selected = paneIndex == index,
                    onClick = { paneIndex = index },
                    text = {
                        Text(
                            text = pane.label,
                            fontWeight = if (paneIndex == index) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                )
            }
        }

        AnimatedContent(
            targetState = panes[paneIndex],
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            transitionSpec = {
                fadeIn(tween(180, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(120))
            },
            label = "learner_pane",
        ) { pane ->
            when (pane) {
                LearnerPane.Signals ->
                    SignalsPane(
                        events = state.events,
                        logEnabled = state.logEnabled,
                        bottomContentPadding = bottomContentPadding,
                    )
                LearnerPane.Taste ->
                    TastePane(
                        snapshot = state.snapshot,
                        bottomContentPadding = bottomContentPadding,
                    )
                LearnerPane.Model ->
                    ModelPane(
                        snapshot = state.snapshot,
                        shadow = state.shadowDiagnostics,
                        bottomContentPadding = bottomContentPadding,
                    )
            }
        }
    }
}

@Composable
private fun LearnerHeader(
    snapshot: LearnerInspectorSnapshot?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val discovery = snapshot?.objectives?.firstOrNull { it.objective == RankingObjective.DISCOVERY }
    val stage =
        when {
            discovery == null -> "Idle"
            discovery.updateCount == 0L -> "Cold start"
            discovery.updateCount < 50L -> "Learning"
            else -> "Adaptive"
        }
    val stageColor =
        when (stage) {
            "Adaptive" -> MaterialTheme.colorScheme.primary
            "Learning" -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.tertiary
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(stageColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = stageColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Learning engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "$stage · ${discovery?.updateCount ?: 0} outcomes · Discovery blend ${(
                        ((discovery?.learnedBlend ?: 0.0) * 100)
                            .toInt()
                    )}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onRefresh,
                enabled = !loading,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Refresh taste/model snapshot",
                    modifier =
                        Modifier
                            .padding(10.dp)
                            .size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun LogToggleCard(
    enabled: Boolean,
    eventCount: Int,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Signal logging", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text =
                        if (enabled) {
                            "Capturing live · $eventCount events this session"
                        } else {
                            "Off — turn on to capture every signal (session only, no storage)"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

// ---------------------------------------------------------------------------
// Signals pane
// ---------------------------------------------------------------------------

private const val MAX_FEED_ROWS = 120

@Composable
private fun SignalsPane(
    events: List<LearningEvent>,
    logEnabled: Boolean,
    bottomContentPadding: Dp,
) {
    if (!logEnabled) {
        InfoCard(
            title = "Signal logging is off",
            body = "Toggle it on above to record every action, reward, taste shift and model update in real time — and exactly how each one moved the engine.",
        )
        return
    }
    if (events.isEmpty()) {
        InfoCard(
            title = "No signals yet",
            body = "Scroll the Home rails, then play, like, skip, subscribe or queue something. Each signal and its effect on the model appears here instantly.",
        )
        return
    }

    val feed = remember(events) { events.take(MAX_FEED_ROWS) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "session_counters") {
            SessionCounters(events = events)
        }
        item(key = "feed_label") {
            Text(
                text = "Live feed · newest first",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(
            items = feed,
            key = { it.id },
        ) { event ->
            EventRow(event = event, now = now)
        }
        if (events.size > MAX_FEED_ROWS) {
            item(key = "feed_overflow") {
                Text(
                    text = "Showing newest $MAX_FEED_ROWS of ${events.size} buffered events.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionCounters(events: List<LearningEvent>) {
    val counts = remember(events) { events.groupingBy { it.kind }.eachCount() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        counterPill(counts, LearningEventKind.ACTION, "Actions")
        counterPill(counts, LearningEventKind.FACET, "Taste")
        counterPill(counts, LearningEventKind.RESOLUTION, "Model")
        counterPill(counts, LearningEventKind.IMPRESSION, "Shown")
        counterPill(counts, LearningEventKind.DUPLICATE, "Ignored")
        counterPill(counts, LearningEventKind.PRUNE, "Pruned")
    }
}

@Composable
private fun counterPill(
    counts: Map<LearningEventKind, Int>,
    kind: LearningEventKind,
    label: String,
) {
    val value = counts[kind] ?: 0
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$value", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventRow(
    event: LearningEvent,
    now: Long,
) {
    val accent = eventAccent(event)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .size(10.dp)
                        .background(accent, CircleShape),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = relativeAge(now - event.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = event.effect,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Taste pane (durable profile)
// ---------------------------------------------------------------------------

@Composable
private fun TastePane(
    snapshot: LearnerInspectorSnapshot?,
    bottomContentPadding: Dp,
) {
    if (snapshot == null) {
        InfoCard(title = "No taste profile yet", body = "Interact with content to build genre, show and source affinities.")
        return
    }
    val byType = remember(snapshot.facets) { snapshot.facets.groupBy { it.type } }
    val genres = byType[PreferenceFacetType.GENRE].orEmpty()
    val shows = byType[PreferenceFacetType.SHOW].orEmpty()
    val sources = byType[PreferenceFacetType.SOURCE].orEmpty()
    val other =
        remember(snapshot.facets) {
            snapshot.facets.filter {
                it.type != PreferenceFacetType.GENRE &&
                    it.type != PreferenceFacetType.SHOW &&
                    it.type != PreferenceFacetType.SOURCE
            }
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "genres") { FacetGroupCard("Genres", genres) }
        item(key = "shows") { FacetGroupCard("Shows", shows) }
        item(key = "sources") { FacetGroupCard("Sources", sources) }
        if (other.isNotEmpty()) {
            item(key = "context") { FacetGroupCard("Context", other) }
        }
    }
}

@Composable
private fun FacetGroupCard(
    title: String,
    facets: List<LearnerFacetDebug>,
) {
    SectionCard(title = title, subtitle = if (facets.isEmpty()) "no evidence yet" else "${facets.size} learned") {
        if (facets.isEmpty()) {
            Text(
                "Nothing here yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val ordered = remember(facets) { facets.sortedByDescending { abs(it.affinity) } }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ordered.forEach { facet ->
                key(facet.type, facet.key) {
                    AffinityRow(
                        label = facet.key,
                        affinity = facet.affinity,
                        evidence = "+${format1(facet.positiveEvidence)} / -${format1(facet.negativeEvidence)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun AffinityRow(
    label: String,
    affinity: Double,
    evidence: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.width(104.dp)) {
            Text(
                text = prettyLabel(label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = evidence,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        DivergingBar(value = affinity, modifier = Modifier.weight(1f))
        Text(
            text = signed(affinity),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
            color = if (affinity >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun DivergingBar(
    value: Double,
    modifier: Modifier = Modifier,
) {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val clamped = value.toFloat().coerceIn(-1f, 1f)
    Box(
        modifier =
            modifier
                .height(12.dp)
                .background(track, CircleShape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mid = size.width / 2f
            drawLine(
                color = Color.Gray.copy(alpha = 0.4f),
                start = Offset(mid, 0f),
                end = Offset(mid, size.height),
                strokeWidth = 1.5f,
            )
            val bar = (size.width / 2f) * abs(clamped)
            val color = if (clamped >= 0) positive else negative
            val left = if (clamped >= 0) mid else mid - bar
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(bar, size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Model pane (internals)
// ---------------------------------------------------------------------------

@Composable
private fun ModelPane(
    snapshot: LearnerInspectorSnapshot?,
    shadow: List<RankingShadowSnapshot>,
    bottomContentPadding: Dp,
) {
    if (snapshot == null) {
        InfoCard(title = "No model yet", body = "The bandit initializes after the first resolved outcome.")
        return
    }
    val shadowByObjective = remember(shadow) { shadow.associateBy { it.objective } }
    val weights =
        remember(snapshot.featureWeights) {
            snapshot.featureWeights
                .filter { it.slot != FeatureSlot.INTERCEPT }
                .sortedByDescending { abs(it.weight) }
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "objectives") {
            SectionCard(title = "Objectives", subtitle = "per-goal bandit state · adaptive vs prior reordering") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    snapshot.objectives.forEach { obj ->
                        ObjectiveRow(obj = obj, shadow = shadowByObjective[obj.objective])
                    }
                }
            }
        }
        item(key = "feature_weights") {
            SectionCard(title = "Feature weights", subtitle = "Discovery \u03b8 · what the ranker currently leans on") {
                if (weights.all { it.weight == 0.0 }) {
                    Text(
                        "No learned weights yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val maxAbs = weights.maxOfOrNull { abs(it.weight) }?.coerceAtLeast(1e-6) ?: 1.0
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        weights.forEach { weight -> FeatureWeightRow(weight = weight, maxAbs = maxAbs) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObjectiveRow(
    obj: RankingDebugSnapshot,
    shadow: RankingShadowSnapshot?,
) {
    val stage =
        when {
            obj.updateCount == 0L -> "cold"
            obj.updateCount < 50L -> "learning"
            else -> "adaptive"
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(prettyLabel(obj.objective.name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "$stage · ${obj.updateCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text =
                    buildString {
                        append("blend ")
                        append((obj.learnedBlend * 100).toInt())
                        append("% · explore ")
                        append(if (obj.explorationEnabled) "on" else "off")
                        if (shadow != null) {
                            append(" · rank shift ")
                            append(format1(shadow.meanAbsoluteRankShift))
                            append(" · top5 overlap ")
                            append(shadow.topFiveOverlap)
                            append("/5")
                        }
                    },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureWeightRow(
    weight: LearnerFeatureWeightDebug,
    maxAbs: Double,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = prettyLabel(weight.slot.name),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
        DivergingBar(value = (weight.weight / maxAbs), modifier = Modifier.weight(1f))
        Text(
            text = signed(weight.weight),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
            color = if (weight.weight >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(52.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun eventAccent(event: LearningEvent): Color =
    when (event.kind) {
        LearningEventKind.IMPRESSION -> MaterialTheme.colorScheme.tertiary
        LearningEventKind.DUPLICATE -> MaterialTheme.colorScheme.outline
        LearningEventKind.PRUNE -> MaterialTheme.colorScheme.secondary
        LearningEventKind.ACTION,
        LearningEventKind.FACET,
        LearningEventKind.RESOLUTION,
        -> {
            val reward = event.reward
            when {
                reward == null -> MaterialTheme.colorScheme.tertiary
                reward >= 0.0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
        }
    }

private fun prettyLabel(raw: String): String =
    raw
        .trim()
        .lowercase()
        .split('_', ' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

private fun signed(value: Double): String = (if (value >= 0) "+" else "") + String.format(Locale.US, "%.2f", value)

private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun relativeAge(ageMs: Long): String {
    val minutes = ageMs / 60_000L
    val seconds = ageMs / 1_000L
    return when {
        seconds < 5 -> "now"
        minutes < 1 -> "${seconds}s"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}
