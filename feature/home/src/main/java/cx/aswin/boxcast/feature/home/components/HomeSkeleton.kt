package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.background
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
fun HeroSkeleton() {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Header Text lines
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBlock(width = 120.dp, height = 20.dp, baseColor = baseColor, highlightColor = highlightColor) // "TOP IN..."
        Spacer(modifier = Modifier.height(12.dp))
        
        // Hero Card (Just Shimmer, No Shapes)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(MaterialTheme.shapes.extraLarge)
                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.extraLarge)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun RisingSkeleton() {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column {
        // Header handled by real component now
        Spacer(modifier = Modifier.height(12.dp))

        // Rail
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) { 
                Column {
                    // Cover Art Shimmer
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.large)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBlock(width = 100.dp, height = 16.dp, baseColor = baseColor, highlightColor = highlightColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBlock(width = 60.dp, height = 12.dp, baseColor = baseColor, highlightColor = highlightColor)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.GridSkeletonItems() {
    items(6) { index ->
        val isTall = index % 3 == 0
        GridSkeletonItem(isTall = isTall)
    }
}

@Composable
fun GridSkeletonItem(isTall: Boolean) {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
    
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTall) 280.dp else 220.dp)
                .clip(MaterialTheme.shapes.large)
                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.large)
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBlock(width = 80.dp, height = 16.dp, baseColor = baseColor, highlightColor = highlightColor)
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBlock(width = 50.dp, height = 12.dp, baseColor = baseColor, highlightColor = highlightColor)
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
fun YourShowsSkeleton() {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val isDark = (0.2126f * MaterialTheme.colorScheme.surface.red + 0.7152f * MaterialTheme.colorScheme.surface.green + 0.0722f * MaterialTheme.colorScheme.surface.blue) < 0.5f
    val cardContainerColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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

        // Selector covers LazyRow
        LazyRow(
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(5) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(12.dp))
                )
            }
        }

        // Large Mixtape Card
        OutlinedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = cardContainerColor
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                // Mixtape Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        SkeletonBlock(width = 100.dp, height = 20.dp, baseColor = baseColor, highlightColor = highlightColor)
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonBlock(width = 200.dp, height = 12.dp, baseColor = baseColor, highlightColor = highlightColor)
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .m3Shimmer(baseColor, highlightColor, shape = CircleShape)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Dense Episode Rows (Shimmers) inside matching 280.dp height
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                SkeletonBlock(width = 180.dp, height = 16.dp, baseColor = baseColor, highlightColor = highlightColor)
                                Spacer(modifier = Modifier.height(6.dp))
                                SkeletonBlock(width = 100.dp, height = 12.dp, baseColor = baseColor, highlightColor = highlightColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeBlockSkeleton() {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

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
                        Column(modifier = Modifier.width(140.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.large)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SkeletonBlock(width = 120.dp, height = 16.dp, baseColor = baseColor, highlightColor = highlightColor)
                            Spacer(modifier = Modifier.height(2.dp))
                            SkeletonBlock(width = 80.dp, height = 12.dp, baseColor = baseColor, highlightColor = highlightColor)
                        }
                    }
                }
            }
            if (railIndex == 0) {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
