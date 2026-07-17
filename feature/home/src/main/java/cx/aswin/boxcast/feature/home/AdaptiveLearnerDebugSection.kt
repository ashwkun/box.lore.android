package cx.aswin.boxcast.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.data.ranking.FeatureSlot
import cx.aswin.boxcast.core.data.ranking.LearnerExposureDebug
import cx.aswin.boxcast.core.data.ranking.LearnerFacetDebug
import cx.aswin.boxcast.core.data.ranking.LearnerFeatureWeightDebug
import cx.aswin.boxcast.core.data.ranking.LearnerInspectorSnapshot
import cx.aswin.boxcast.core.data.ranking.PreferenceFacetType
import cx.aswin.boxcast.core.data.ranking.RankingDebugSnapshot
import cx.aswin.boxcast.core.data.ranking.RankingObjective
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private enum class LearnerPane(val label: String) {
    Graph("Graph"),
    Constellation("Constellation"),
    Activity("Activity"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptiveLearnerDebugSection(
    snapshot: LearnerInspectorSnapshot?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    var paneIndex by remember { mutableIntStateOf(0) }
    val panes = remember { LearnerPane.entries }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LearnerHeroHeader(snapshot = snapshot, loading = loading, onRefresh = onRefresh)

        if (snapshot == null) {
            LearnerEmptyState(loading = loading)
            return
        }

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
            transitionSpec = {
                fadeIn(tween(240, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(140))
            },
            label = "learner_pane",
        ) { pane ->
            when (pane) {
                LearnerPane.Graph -> LearnerGraphPane(snapshot)
                LearnerPane.Constellation -> LearnerConstellationPane(snapshot)
                LearnerPane.Activity -> LearnerActivityPane(snapshot)
            }
        }
    }
}

@Composable
private fun LearnerHeroHeader(
    snapshot: LearnerInspectorSnapshot?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val discovery = snapshot?.objectives?.firstOrNull { it.objective == RankingObjective.DISCOVERY }
    val stage = when {
        discovery == null -> "Idle"
        discovery.updateCount == 0L -> "Cold start"
        discovery.updateCount < 50L -> "Learning"
        else -> "Adaptive"
    }
    val stageColor = when (stage) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(stageColor.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = stageColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Neural ranker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "$stage · ${discovery?.updateCount ?: 0} outcomes · blend ${(((discovery?.learnedBlend ?: 0.0) * 100).toInt())}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
private fun LearnerEmptyState(loading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (loading) "Reading local model…" else "No learner graph yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Scroll Home rails, then play or like to light up nodes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LearnerGraphPane(snapshot: LearnerInspectorSnapshot) {
    val discovery = snapshot.objectives.firstOrNull { it.objective == RankingObjective.DISCOVERY }
        ?: snapshot.objectives.first()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GraphCard(
            title = "Score merge graph",
            subtitle = "Prior · learned · explore → blend → final rank",
        ) {
            ScoreMergeNodeGraph(discovery = discovery)
        }
        GraphCard(
            title = "Objective mesh",
            subtitle = "Each ranking objective as a living node",
        ) {
            ObjectiveMeshGraph(objectives = snapshot.objectives)
        }
        Text(
            text = "Edge thickness = influence. Node pulse = active training signal.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LearnerConstellationPane(snapshot: LearnerInspectorSnapshot) {
    val facets = remember(snapshot.facets) {
        snapshot.facets.filter { abs(it.affinity) >= 0.04 }.take(32)
    }
    val weights = remember(snapshot.featureWeights) {
        snapshot.featureWeights
            .filter { it.slot != FeatureSlot.INTERCEPT }
            .sortedByDescending { abs(it.weight) }
            .take(12)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GraphCard(
            title = "Preference constellation",
            subtitle = "Outer genre · mid show · inner source · hub = you",
        ) {
            PreferenceConstellation(facets = facets)
        }
        GraphCard(
            title = "Feature gravity well",
            subtitle = "Discovery θ weights orbiting the ranker core",
        ) {
            FeatureGravityWell(weights = weights)
        }
        GenrePolarityMini(facets = facets.filter { it.type == PreferenceFacetType.GENRE })
    }
}

@Composable
private fun LearnerActivityPane(snapshot: LearnerInspectorSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GraphCard(
            title = "Learning pulse network",
            subtitle = "Resolved outcomes vs waiting impressions",
        ) {
            PulseNetworkCanvas(
                exposures = snapshot.recentExposures,
                pendingCount = snapshot.pendingExposureCount,
                resolvedCount = snapshot.resolvedExposureCount,
            )
        }
        ImpressionNodeList(exposures = snapshot.recentExposures)
        Text(
            text = "Waiting nodes = shown, unresolved. Up spikes = positive reward, down = negative.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GraphCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun ScoreMergeNodeGraph(discovery: RankingDebugSnapshot) {
    val priorW = (1.0 - discovery.learnedBlend).toFloat()
    val learnedW = discovery.learnedBlend.toFloat()
    val exploreW = if (discovery.explorationEnabled) 0.22f else 0.05f
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)

    val infinite = rememberInfiniteTransition(label = "score_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "edge_flow",
    )
    val glow by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "node_glow",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(surface, RoundedCornerShape(20.dp))
            .padding(10.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftX = size.width * 0.14f
            val midX = size.width * 0.50f
            val rightX = size.width * 0.86f
            val yPrior = size.height * 0.18f
            val yLearned = size.height * 0.50f
            val yExplore = size.height * 0.82f
            val yBlend = size.height * 0.50f

            val prior = Offset(leftX, yPrior)
            val learned = Offset(leftX, yLearned)
            val explore = Offset(leftX, yExplore)
            val blend = Offset(midX, yBlend)
            val rank = Offset(rightX, yBlend)
            val gate = Offset(size.width * 0.68f, yBlend)
            val feedback = Offset(size.width * 0.68f, size.height * 0.18f)

            drawGridBackdrop(outline.copy(alpha = 0.14f))
            drawParticleField(primary.copy(alpha = 0.55f), pulse, density = 48)

            // satellite feature inputs into prior / learned
            val sats = listOf(
                Offset(size.width * 0.04f, size.height * 0.08f) to prior,
                Offset(size.width * 0.04f, size.height * 0.30f) to prior,
                Offset(size.width * 0.04f, size.height * 0.42f) to learned,
                Offset(size.width * 0.04f, size.height * 0.58f) to learned,
                Offset(size.width * 0.04f, size.height * 0.72f) to explore,
                Offset(size.width * 0.04f, size.height * 0.92f) to explore,
            )
            sats.forEachIndexed { i, (sat, dest) ->
                drawFlowEdge(sat, dest, outline.copy(alpha = 0.55f), 1.4f, pulse + i * 0.11f, dashed = true)
                drawCircle(color = outline.copy(alpha = 0.8f), radius = 3.5f, center = sat)
            }

            drawFlowEdge(prior, blend, primary, 3f + priorW * 14f, pulse, dashed = false)
            drawFlowEdge(learned, blend, secondary, 3f + learnedW * 16f, pulse + 0.33f, dashed = false)
            drawFlowEdge(
                explore,
                blend,
                tertiary,
                2f + exploreW * 12f,
                pulse + 0.66f,
                dashed = !discovery.explorationEnabled,
            )
            drawFlowEdge(blend, gate, onSurface.copy(alpha = 0.5f), 6f, pulse + 0.15f, dashed = false)
            drawFlowEdge(gate, rank, primary.copy(alpha = 0.75f), 8f, pulse + 0.35f, dashed = false)
            drawFlowEdge(rank, feedback, tertiary.copy(alpha = 0.45f), 2.2f, pulse + 0.5f, dashed = true)
            drawFlowEdge(feedback, learned, secondary.copy(alpha = 0.35f), 1.8f, pulse + 0.7f, dashed = true)

            drawNodeHalo(prior, 15f + priorW * 12f, primary, glow)
            drawNodeHalo(learned, 15f + learnedW * 14f, secondary, glow)
            drawNodeHalo(blend, 24f, outline, glow)
            drawNodeHalo(rank, 28f, primary, glow)

            drawGlowingNode(prior, 15f + priorW * 12f, primary, glow)
            drawGlowingNode(learned, 15f + learnedW * 14f, secondary, glow)
            drawGlowingNode(explore, 12f + exploreW * 10f, tertiary, glow * if (discovery.explorationEnabled) 1f else 0.45f)
            drawGlowingNode(blend, 24f, outline, glow)
            drawGlowingNode(gate, 14f, tertiary, glow)
            drawGlowingNode(rank, 28f, primary, glow)
            drawGlowingNode(feedback, 10f, secondary, glow * 0.8f)
        }

        NodeCaption("Prior", Modifier.align(Alignment.TopStart).padding(6.dp))
        NodeCaption("Learned", Modifier.align(Alignment.CenterStart).padding(6.dp))
        NodeCaption(
            if (discovery.explorationEnabled) "Explore" else "Explore·off",
            Modifier.align(Alignment.BottomStart).padding(6.dp),
        )
        NodeCaption("Blend", Modifier.align(Alignment.Center).offset(x = (-6).dp, y = (-58).dp))
        NodeCaption("Gate", Modifier.align(Alignment.Center).offset(x = 52.dp, y = (-58).dp))
        NodeCaption("θ feedback", Modifier.align(Alignment.TopEnd).padding(6.dp))
        NodeCaption("Rank", Modifier.align(Alignment.CenterEnd).padding(end = 6.dp))
    }
}

@Composable
private fun NodeCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ObjectiveMeshGraph(objectives: List<RankingDebugSnapshot>) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    val infinite = rememberInfiniteTransition(label = "obj_mesh")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "mesh_spin",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "mesh_pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(surface, RoundedCornerShape(20.dp)),
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val hubR = min(cx, cy) * 0.18f
            drawParticleField(secondary.copy(alpha = 0.45f), pulse, density = 40)
            drawCircle(color = outline.copy(alpha = 0.2f), radius = min(cx, cy) * 0.78f, center = Offset(cx, cy), style = Stroke(1.4f))
            drawCircle(color = outline.copy(alpha = 0.12f), radius = min(cx, cy) * 0.48f, center = Offset(cx, cy), style = Stroke(1.2f))
            drawCircle(
                color = tertiary.copy(alpha = 0.1f),
                radius = min(cx, cy) * (0.48f + pulse * 0.08f),
                center = Offset(cx, cy),
                style = Stroke(width = 8f),
            )
            drawGlowingNode(Offset(cx, cy), hubR, primary, pulse)

            val n = max(objectives.size, 1)
            val positions = mutableListOf<Offset>()
            objectives.forEachIndexed { index, obj ->
                val angle = spin * 0.15f + index * (PI * 2 / n).toFloat()
                val orbit = min(cx, cy) * (0.55f + (obj.learnedBlend.toFloat() * 0.2f))
                val pos = Offset(cx + cos(angle) * orbit, cy + sin(angle) * orbit)
                positions += pos
                val maturity = (obj.updateCount / 50f).coerceIn(0f, 1f)
                val color = when {
                    obj.updateCount == 0L -> outline
                    maturity < 1f -> secondary
                    else -> primary
                }
                drawFlowEdge(Offset(cx, cy), pos, color.copy(alpha = 0.55f), 2f + maturity * 6f, pulse, dashed = false)
                drawNodeHalo(pos, 10f + maturity * 12f, color, pulse)
                drawGlowingNode(pos, 10f + maturity * 12f, color, pulse)
                drawArc(
                    color = color.copy(alpha = 0.85f),
                    startAngle = -90f,
                    sweepAngle = 360f * maturity,
                    useCenter = false,
                    topLeft = Offset(pos.x - 18f, pos.y - 18f),
                    size = Size(36f, 36f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                )
            }
            // ring mesh between adjacent objectives
            if (positions.size >= 2) {
                positions.forEachIndexed { i, a ->
                    val b = positions[(i + 1) % positions.size]
                    drawFlowEdge(a, b, outline.copy(alpha = 0.45f), 1.6f, pulse + i * 0.1f, dashed = true)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            objectives.forEach { obj ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "${shortObjective(obj.objective)} ${obj.updateCount}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceConstellation(facets: List<LearnerFacetDebug>) {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val neutral = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    val infinite = rememberInfiniteTransition(label = "constellation")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing)),
        label = "drift",
    )
    val breath by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breath",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(surface, RoundedCornerShape(20.dp)),
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = min(cx, cy)

            drawParticleField(positive.copy(alpha = 0.4f), drift / (PI.toFloat() * 2f), density = 56)

            // orbital rings + radar sweep
            listOf(0.34f, 0.55f, 0.78f).forEach { ring ->
                drawCircle(
                    color = neutral.copy(alpha = 0.22f),
                    radius = maxR * ring,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))),
                )
            }
            drawArc(
                color = positive.copy(alpha = 0.18f),
                startAngle = Math.toDegrees(drift.toDouble()).toFloat(),
                sweepAngle = 48f,
                useCenter = true,
                topLeft = Offset(cx - maxR * 0.78f, cy - maxR * 0.78f),
                size = Size(maxR * 1.56f, maxR * 1.56f),
            )
            drawGlowingNode(Offset(cx, cy), 11f, positive, breath)

            val positions = mutableListOf<Pair<Offset, LearnerFacetDebug>>()
            facets.forEachIndexed { index, facet ->
                val ring = when (facet.type) {
                    PreferenceFacetType.GENRE -> 0.78f
                    PreferenceFacetType.SHOW -> 0.55f
                    PreferenceFacetType.SOURCE -> 0.34f
                    else -> 0.65f
                }
                val baseAngle = (index.toFloat() / max(facets.size, 1)) * (PI * 2).toFloat()
                val angle = baseAngle + drift * when (facet.type) {
                    PreferenceFacetType.GENRE -> 0.08f
                    PreferenceFacetType.SHOW -> -0.12f
                    PreferenceFacetType.SOURCE -> 0.18f
                    else -> 0.05f
                }
                val radius = maxR * ring
                val pos = Offset(cx + cos(angle) * radius, cy + sin(angle) * radius)
                val affinity = facet.affinity.toFloat().coerceIn(-1f, 1f)
                val color = when {
                    affinity > 0.05f -> lerp(neutral, positive, affinity)
                    affinity < -0.05f -> lerp(neutral, negative, -affinity)
                    else -> neutral
                }
                drawLine(
                    color = color.copy(alpha = 0.28f + abs(affinity) * 0.35f),
                    start = Offset(cx, cy),
                    end = pos,
                    strokeWidth = 1.2f + abs(affinity) * 4f,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = color.copy(alpha = 0.22f), radius = 7f + abs(affinity) * 12f, center = pos)
                drawCircle(color = color, radius = 4f + abs(affinity) * 9f * breath, center = pos)
                positions += pos to facet
            }

            // mesh edges between neighbors on the same orbital ring
            for (i in positions.indices) {
                for (j in i + 1 until positions.size) {
                    val (a, fa) = positions[i]
                    val (b, fb) = positions[j]
                    if (fa.type != fb.type) continue
                    val dist = (a - b).getDistance()
                    if (dist > maxR * 0.55f) continue
                    drawLine(
                        color = neutral.copy(alpha = 0.16f),
                        start = a,
                        end = b,
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)),
                    )
                }
            }
        }

        Text(
            text = "outer · genre    mid · show    inner · source",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeatureGravityWell(weights: List<LearnerFeatureWeightDebug>) {
    val positive = MaterialTheme.colorScheme.secondary
    val negative = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    val maxAbs = weights.maxOfOrNull { abs(it.weight) }?.coerceAtLeast(1e-6) ?: 1.0
    val infinite = rememberInfiniteTransition(label = "gravity")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "grav_spin",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(surface, RoundedCornerShape(20.dp)),
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(color = outline.copy(alpha = 0.15f), radius = min(cx, cy) * 0.85f, center = Offset(cx, cy))
            drawGlowingNode(Offset(cx, cy), 16f, positive, 0.8f)

            weights.forEachIndexed { index, item ->
                val strength = (abs(item.weight) / maxAbs).toFloat().coerceIn(0.15f, 1f)
                val angle = spin + index * (PI * 2 / max(weights.size, 1)).toFloat()
                // stronger weights pull closer to core
                val orbit = min(cx, cy) * (0.85f - strength * 0.45f)
                val pos = Offset(cx + cos(angle) * orbit, cy + sin(angle) * orbit)
                val color = if (item.weight >= 0) positive else negative
                drawFlowEdge(Offset(cx, cy), pos, color.copy(alpha = 0.45f), 1.5f + strength * 5f, spin, dashed = item.weight < 0)
                drawGlowingNode(pos, 6f + strength * 10f, color, 0.6f + strength * 0.4f)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            weights.take(6).forEach { item ->
                Surface(
                    color = if (item.weight >= 0) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = item.slot.name.lowercase().replace('_', ' ').take(12),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenrePolarityMini(facets: List<LearnerFacetDebug>) {
    val rows = remember(facets) { facets.sortedByDescending { abs(it.affinity) }.take(6) }
    if (rows.isEmpty()) return
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    GraphCard(title = "Top polarity", subtitle = "Strongest genre pulls right now") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { facet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = facet.key.take(16),
                        modifier = Modifier.width(84.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(11.dp)
                            .background(track, CircleShape),
                    ) {
                        val affinity = facet.affinity.toFloat().coerceIn(-1f, 1f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val mid = size.width / 2f
                            val bar = (size.width / 2f) * abs(affinity)
                            val color = if (affinity >= 0) positive else negative
                            if (affinity >= 0) {
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(mid, 0f),
                                    size = Size(bar, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                                )
                            } else {
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(mid - bar, 0f),
                                    size = Size(bar, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseNetworkCanvas(
    exposures: List<LearnerExposureDebug>,
    pendingCount: Int,
    resolvedCount: Int,
) {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val pending = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    val infinite = rememberInfiniteTransition(label = "pulse_net")
    val flow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "pulse_flow",
    )
    val total = (resolvedCount + pendingCount).coerceAtLeast(1)
    val resolveRate by animateFloatAsState(
        targetValue = resolvedCount.toFloat() / total,
        animationSpec = tween(500),
        label = "resolve_rate",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(surface, RoundedCornerShape(20.dp))
            .padding(12.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseline = size.height * 0.62f
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, outline.copy(alpha = 0.5f), Color.Transparent),
                ),
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 2f,
            )

            // hub node left summarizing resolve rate
            val hub = Offset(size.width * 0.08f, baseline)
            drawGlowingNode(hub, 14f + resolveRate * 10f, positive, 0.5f + resolveRate * 0.5f)
            drawArc(
                color = pending.copy(alpha = 0.7f),
                startAngle = -90f,
                sweepAngle = 360f * (1f - resolveRate),
                useCenter = false,
                topLeft = Offset(hub.x - 20f, hub.y - 20f),
                size = Size(40f, 40f),
                style = Stroke(2.5f, cap = StrokeCap.Round),
            )

            if (exposures.isEmpty()) return@Canvas
            val count = exposures.size
            val startX = size.width * 0.18f
            val usable = size.width * 0.78f
            val step = if (count <= 1) 0f else usable / (count - 1).toFloat()

            exposures.asReversed().forEachIndexed { index, exposure ->
                val x = if (count == 1) startX + usable / 2f else startX + index * step
                val reward = exposure.reward
                val isPending = reward == null
                val color = when {
                    isPending -> pending
                    reward >= 0.0 -> positive
                    else -> negative
                }
                val magnitude = if (isPending) 0.45f else abs(reward).toFloat().coerceIn(0.25f, 1f)
                val direction = when {
                    isPending -> 1f
                    reward >= 0.0 -> 1f
                    else -> -0.5f
                }
                val y = baseline - magnitude * size.height * 0.5f * direction
                val tip = Offset(x, y)
                drawFlowEdge(hub, tip, color.copy(alpha = 0.25f), 1.2f, flow + index * 0.07f, dashed = isPending)
                drawLine(
                    color = color.copy(alpha = 0.65f),
                    start = Offset(x, baseline),
                    end = tip,
                    strokeWidth = if (isPending) 2.2f else 3.2f,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = color.copy(alpha = 0.2f), radius = 8f + magnitude * 6f, center = tip)
                drawCircle(color = color, radius = 4f + magnitude * 5f, center = tip)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPill("$resolvedCount✓", positive)
            StatPill("$pendingCount…", pending)
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun ImpressionNodeList(exposures: List<LearnerExposureDebug>) {
    if (exposures.isEmpty()) return
    val now = remember { System.currentTimeMillis() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Linked impressions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        exposures.take(7).forEach { exposure ->
            val reward = exposure.reward
            val color = when {
                reward == null -> MaterialTheme.colorScheme.tertiary
                reward >= 0 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color, CircleShape),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exposure.entryPoint?.removePrefix("home_adaptive_")?.replace('_', ' ')
                                ?: exposure.objective,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${prettySurface(exposure.surface)} · ${prettySource(exposure.source)} · ${relativeAge(now - exposure.shownAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = when {
                            reward == null -> "wait"
                            reward >= 0 -> "+${"%.2f".format(reward)}"
                            else -> "%.2f".format(reward)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGridBackdrop(color: Color) {
    val step = 22f
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
    // diagonal circuit traces
    val diag = color.copy(alpha = color.alpha * 0.55f)
    var d = -size.height
    while (d < size.width) {
        drawLine(diag, Offset(d, 0f), Offset(d + size.height, size.height), strokeWidth = 0.8f)
        d += step * 2.4f
    }
}

private fun DrawScope.drawParticleField(color: Color, phase: Float, density: Int = 42) {
    val seed = (size.width * 13f + size.height * 7f).toInt()
    for (i in 0 until density) {
        val n = ((i * 1103515245 + seed) and 0x7fffffff)
        val px = (n % 1000) / 1000f * size.width
        val py = ((n / 1000) % 1000) / 1000f * size.height
        val drift = ((phase + i * 0.017f) % 1f)
        val x = (px + drift * 18f) % size.width
        val y = (py + sin((phase + i) * 6.2f) * 6f)
        val r = 1.1f + (n % 5) * 0.35f
        drawCircle(color = color.copy(alpha = 0.14f + (n % 40) / 200f), radius = r, center = Offset(x, y))
    }
}

private fun DrawScope.drawNodeHalo(center: Offset, radius: Float, color: Color, phase: Float) {
    drawCircle(
        color = color.copy(alpha = 0.18f),
        radius = radius + 10f + phase * 6f,
        center = center,
        style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), phase * 20f)),
    )
    drawCircle(
        color = color.copy(alpha = 0.12f),
        radius = radius + 18f,
        center = center,
        style = Stroke(width = 1f),
    )
}

private fun DrawScope.drawFlowEdge(
    from: Offset,
    to: Offset,
    color: Color,
    width: Float,
    phase: Float,
    dashed: Boolean,
) {
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f - 18f)
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(mid.x, mid.y, to.x, to.y)
    }
    drawPath(
        path = path,
        color = color.copy(alpha = 0.75f),
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(10f, 8f), phase * 40f)
            } else {
                PathEffect.dashPathEffect(floatArrayOf(18f, 10f), phase * 28f)
            },
        ),
    )
    // traveling particle
    val t = ((phase % 1f) + 1f) % 1f
    val px = (1 - t) * (1 - t) * from.x + 2 * (1 - t) * t * mid.x + t * t * to.x
    val py = (1 - t) * (1 - t) * from.y + 2 * (1 - t) * t * mid.y + t * t * to.y
    drawCircle(color = color, radius = 3.2f + width * 0.15f, center = Offset(px, py))
}

private fun DrawScope.drawGlowingNode(
    center: Offset,
    radius: Float,
    color: Color,
    glow: Float,
) {
    drawCircle(color = color.copy(alpha = 0.08f + glow * 0.14f), radius = radius + 22f, center = center)
    drawCircle(color = color.copy(alpha = 0.14f + glow * 0.16f), radius = radius + 14f, center = center)
    drawCircle(color = color.copy(alpha = 0.32f), radius = radius + 7f, center = center)
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(
        color = Color.White.copy(alpha = 0.42f),
        radius = radius * 0.34f,
        center = center + Offset(-radius * 0.22f, -radius * 0.22f),
    )
    // micro ports around node
    for (i in 0 until 6) {
        val a = i * (PI.toFloat() / 3f) + glow
        val tip = Offset(center.x + cos(a) * (radius + 5f), center.y + sin(a) * (radius + 5f))
        drawCircle(color = color.copy(alpha = 0.55f), radius = 1.6f, center = tip)
    }
}

private fun shortObjective(objective: RankingObjective): String =
    when (objective) {
        RankingObjective.DISCOVERY -> "Disc"
        RankingObjective.CONTINUATION -> "Cont"
        RankingObjective.OFFLINE -> "Off"
        RankingObjective.YOUR_SHOWS -> "Yours"
        RankingObjective.SLATE -> "Slate"
    }

private fun prettySurface(raw: String): String = when (raw.uppercase()) {
    "HOME" -> "Home"
    "QUEUE" -> "Queue"
    else -> raw.lowercase().replaceFirstChar { it.titlecase() }
}

private fun prettySource(raw: String): String = when (raw.uppercase()) {
    "SERVER_RECOMMENDATION" -> "sections"
    "CURATED_INTENT" -> "curated"
    "TRENDING" -> "trending"
    else -> raw.lowercase().replace('_', ' ').take(12)
}

private fun relativeAge(ageMs: Long): String {
    val minutes = ageMs / 60_000L
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}
