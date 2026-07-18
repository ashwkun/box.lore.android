package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.SectionHeaderFontFamily

/**
 * Discover Section - Genre-filtered podcast discovery.
 *
 * Contains:
 * - Section Header ("Discover" or "Top in [Category]")
 * - Genre Selector (FilterChips)
 * - Loading State (BoxLoreLoader.Expressive)
 * - Podcast Grid (via callback - grid is rendered in parent for staggered layout)
 *
 * @param selectedCategory Current selected category (null = "For You")
 * @param isLoading Whether content is loading
 * @param onCategorySelected Callback when a category chip is tapped
 * @param modifier Modifier for the section
 */
@Composable
fun DiscoverSection(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header (Matches OnTheRiseSection styling)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Title Group
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (selectedCategory == null) "Explore" else "Top in $selectedCategory",
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = SectionHeaderFontFamily,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Action / Decorator
            androidx.compose.material3.FilledTonalIconButton(
                onClick = onHeaderClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "See All",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Genre Selector Chips
        GenreSelector(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}
