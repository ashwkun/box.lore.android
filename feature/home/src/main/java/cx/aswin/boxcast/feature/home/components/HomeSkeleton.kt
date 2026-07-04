package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import cx.aswin.boxcast.core.designsystem.theme.m3Shimmer

/**
 * Skeletal Loader for Home Screen.
 * Uses Material 3 Shimmer effect (Gray Pulse) to indicate data loading.
 */
@Composable
fun HomeSkeleton(
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(bottom = 24.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 16.dp,
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Hero Skeleton
        item(span = StaggeredGridItemSpan.FullLine) {
            HeroSkeleton()
        }

        // 2. On The Rise Skeleton
        item(span = StaggeredGridItemSpan.FullLine) {
            RisingSkeleton()
        }

        // 3. Grid Skeleton
        GridSkeletonItems()
    }
}

@Composable
fun HeroSkeleton(
    modifier: Modifier = Modifier
) {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        items(2) {
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.extraLarge)
            )
        }
    }
}

@Composable
fun RisingSkeleton() {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column {
        // Header handled by real component now
        Spacer(modifier = Modifier.height(12.dp))

        // Rail
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) { 
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.large)
                        .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.large)
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}


fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.GridSkeletonItems() {
    items(6) {
        GridSkeletonItem()
    }
}

@Composable
fun GridSkeletonItem(
    modifier: Modifier = Modifier
) {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    
    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Image area skeleton matching aspect ratio 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            )
            
            // Text area skeleton matching padding and layout of FeedMediaCard
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .heightIn(min = 58.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(4.dp))
                )
                // Title line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Artist line
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun SkeletonBlock(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp),
    baseColor: androidx.compose.ui.graphics.Color,
    highlightColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
                .m3Shimmer(baseColor, highlightColor, shape = shape)
    )
}

@Composable
fun YourShowsSkeleton(subscribedCount: Int = 0, modifier: Modifier = Modifier) {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(modifier = modifier.padding(bottom = 16.dp)) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(width = 120.dp, height = 24.dp, baseColor = baseColor, highlightColor = highlightColor)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .m3Shimmer(baseColor, highlightColor, shape = CircleShape)
            )
        }

        // Selector covers - match the dynamic height of YourShowsSection
        if (subscribedCount > 4) {
            // 2-row layout matching the height 156.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1 shimmers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(12.dp))
                        )
                    }
                }
                // Row 2 shimmers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        } else {
            // 1-row layout (LazyRow) matching the height 84.dp
            LazyRow(
                contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(if (subscribedCount > 0) (subscribedCount + 1).coerceAtMost(5) else 5) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        // Large Mixtape Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(24.dp))
        )
    }
}

@Composable
fun TimeBlockSkeleton() {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        // Time Block Master Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .m3Shimmer(baseColor, highlightColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                SkeletonBlock(width = 160.dp, height = 24.dp, baseColor = baseColor, highlightColor = highlightColor)
                Spacer(modifier = Modifier.height(2.dp))
                SkeletonBlock(width = 200.dp, height = 14.dp, baseColor = baseColor, highlightColor = highlightColor)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2 rails for curation block matching data.sections list
        repeat(2) { railIndex ->
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    SkeletonBlock(width = 100.dp, height = 18.dp, baseColor = baseColor, highlightColor = highlightColor)
                }

                // Horizontal row of cards
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) { 
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(204.dp)
                                .clip(MaterialTheme.shapes.large)
                                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.large)
                        )
                    }
                }
            }
            if (railIndex == 0) {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
