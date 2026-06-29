package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreSelector(
    selectedCategory: String?, // Null = For You
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    // Dynamic list construction
    val displayGenres = remember(selectedCategory) {
        val topGenres = GENRES.take(5)
        if (selectedCategory != null) {
            val selectedGenre = GENRES.find { it.value == selectedCategory }
            if (selectedGenre != null) {
                // "Top" + Selected + (Top 5 - Selected)
                listOf(selectedGenre) + (topGenres - selectedGenre)
            } else {
                topGenres
            }
        } else {
            topGenres
        }
    }

    val scrollState = rememberScrollState()
    
    LaunchedEffect(selectedCategory) {
        scrollState.animateScrollTo(0)
    }

    // Top horizontal list (Subset) - Optimized by replacing LazyRow with scrollable Row for small static list
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.width(16.dp)) // Offset for the starting content padding (24.dp - 8.dp spacing)

        // 1. "Top" (Always first)
        GenreChip(
            label = "Top",
            isSelected = selectedCategory == null,
            onClick = { onCategorySelected(null) }
        )

        // 2. Dynamic List (Selected + Top Genres)
        displayGenres.forEach { genre ->
            GenreChip(
                label = genre.label,
                isSelected = selectedCategory == genre.value,
                onClick = { onCategorySelected(genre.value) }
            )
        }

        // 3. "More" Button
        FilterChip(
            selected = false,
            onClick = { showSheet = true },
            label = { Text("More...") },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false,
                borderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.width(16.dp)) // Offset for the ending content padding
    }

    // Full Genre Sheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp) // Nav bar padding
            ) {
                Text(
                    text = "Browse Genres",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "Top" in sheet
                    GenreChip(
                        label = "Top",
                        isSelected = selectedCategory == null,
                        onClick = { 
                            onCategorySelected(null)
                            showSheet = false 
                        }
                    )

                    GENRES.forEach { genre ->
                        GenreChip(
                            label = genre.label,
                            isSelected = selectedCategory == genre.value,
                            onClick = { 
                                onCategorySelected(genre.value)
                                showSheet = false 
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = FilterChipDefaults.filterChipBorder(
             enabled = true,
             selected = isSelected,
             borderColor = Color.Transparent,
             selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

// Data Handling (Copied from OnboardingScreen to avoid dependency issues for now)
// TODO: Move to core:model
private data class GenreItem(val label: String, val value: String, val icon: ImageVector)

private val GENRES = listOf(
    GenreItem("News", "News", Icons.Rounded.Newspaper),
    GenreItem("Tech", "Technology", Icons.Rounded.Computer),
    GenreItem("Business", "Business", Icons.Rounded.Work),
    GenreItem("Comedy", "Comedy", Icons.Rounded.EmojiEvents),
    GenreItem("True Crime", "True Crime", Icons.Rounded.Search),
    GenreItem("Sports", "Sports", Icons.Rounded.SportsBaseball),
    GenreItem("Health", "Health", Icons.Rounded.FavoriteBorder),
    GenreItem("History", "History", Icons.Rounded.AccountBalance),
    GenreItem("Arts", "Arts", Icons.Rounded.Palette),
    GenreItem("Society", "Society & Culture", Icons.Rounded.Person),
    GenreItem("Education", "Education", Icons.Rounded.School),
    GenreItem("Science", "Science", Icons.Rounded.Science),
    GenreItem("TV & Film", "TV & Film", Icons.Rounded.Movie),
    GenreItem("Fiction", "Fiction", Icons.Rounded.AutoStories),
    GenreItem("Music", "Music", Icons.Rounded.MusicNote),
    GenreItem("Religion", "Religion & Spirituality", Icons.Rounded.Star),
    GenreItem("Family", "Kids & Family", Icons.Rounded.Face),
    GenreItem("Leisure", "Leisure", Icons.Rounded.Weekend),
    GenreItem("Govt", "Government", Icons.Rounded.Gavel)
)
