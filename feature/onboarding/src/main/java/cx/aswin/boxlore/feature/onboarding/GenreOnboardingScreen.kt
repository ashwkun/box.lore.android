package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun GenrePickerScreen(
    selectedGenres: Set<String>,
    onToggleGenre: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Can we get to know you better?",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = selectedGenres.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            if (selectedGenres.isEmpty()) "Pick at least 1"
                            else "Continue (${selectedGenres.size} selected)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Pick the topics you enjoy",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ONBOARDING_GENRES.forEach { genre ->
                    val isSelected = genre.value in selectedGenres
                    GenreChip(
                        genre = genre,
                        isSelected = isSelected,
                        onClick = { onToggleGenre(genre.value) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GenreChip(
    genre: GenreItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh
    
    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface
    
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .height(64.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                genre.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else contentColor
            )
            Text(
                genre.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val SUB_GENRES_MAP = mapOf(
    "News" to listOf("Daily News", "Politics", "World News", "Tech News", "Local News"),
    "Technology" to listOf("Software", "Gadgets", "AI & Tech", "Startups", "Cybersecurity"),
    "Business" to listOf("Investing", "Marketing", "Personal Finance", "Management", "Real Estate"),
    "Comedy" to listOf("Standup", "Improv", "Pop Culture Comedy", "Storytelling Comedy"),
    "True Crime" to listOf("Investigative", "Cold Cases", "Serial Killers", "History Crime"),
    "Sports" to listOf("Football", "Basketball", "F1 & Racing", "Fantasy Sports", "Fitness"),
    "Health" to listOf("Mental Health", "Nutrition", "Medicine", "Biohacking", "Yoga & Meditation"),
    "History" to listOf("Ancient History", "Modern History", "Military History", "Biographies"),
    "Arts" to listOf("Design", "Literature", "Visual Arts", "Performing Arts", "Fashion"),
    "Society & Culture" to listOf("Philosophy", "Relationships", "Pop Culture", "Personal Journals"),
    "Education" to listOf("Language Learning", "Self Improvement", "How-To", "Courses"),
    "Science" to listOf("Space & Physics", "Biology", "Psychology", "Nature", "Earth Sciences"),
    "TV & Film" to listOf("Movie Reviews", "TV Show Reviews", "Anime", "Pop Culture"),
    "Fiction" to listOf("Sci-Fi & Fantasy", "Drama", "Mystery & Thriller", "Comedy Fiction"),
    "Music" to listOf("Music History", "Songwriting", "Interviews", "Music Commentary"),
    "Religion & Spirituality" to listOf("Mindfulness", "Spirituality", "Religion", "Philosophy"),
    "Kids & Family" to listOf("Parenting", "Bedtime Stories", "Education for Kids", "Family Activity"),
    "Leisure" to listOf("Gaming", "Hobbies", "Travel", "Food & Drink", "Automotive"),
    "Government" to listOf("Law & Justice", "Public Policy", "Municipal News", "National Politics")
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SubGenrePickerScreen(
    selectedGenres: Set<String>,
    selectedSubGenres: Set<String>,
    onToggleSubGenre: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Dive a bit deeper",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Select specific topics (Optional)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val parentGenres = selectedGenres.toList()
            if (parentGenres.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No genres selected. Go back to pick some.")
                }
            } else {
                parentGenres.forEach { parentGenre ->
                    val subGenres = SUB_GENRES_MAP[parentGenre] ?: emptyList()
                    if (subGenres.isNotEmpty()) {
                        Text(
                            text = parentGenre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                        ) {
                            subGenres.forEach { subGenre ->
                                val isSelected = subGenre in selectedSubGenres
                                SubGenreChip(
                                    label = subGenre,
                                    isSelected = isSelected,
                                    onClick = { onToggleSubGenre(subGenre) }
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
private fun SubGenreChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh
    
    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface
    
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .height(56.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityPickerScreen(
    selectedActivities: Set<String>,
    activityGenreMap: Map<String, Set<String>>,
    allSelectedGenres: Set<String>,
    onToggleActivity: (String) -> Unit,
    onSetGenresForActivity: (String, Set<String>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val activities = listOf(
        ActivityItem("Commuting", "commuting", Icons.Rounded.DirectionsCar),
        ActivityItem("Working", "working", Icons.Rounded.Work),
        ActivityItem("Meditating / Chilling", "meditating", Icons.Rounded.SelfImprovement),
        ActivityItem("Winding Down", "winding down", Icons.Rounded.Bedtime),
        ActivityItem("Working Out", "working out", Icons.Rounded.FitnessCenter),
        ActivityItem("Doing Chores", "doing chores", Icons.Rounded.Brush)
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "When do you listen?",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = selectedActivities.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            text = if (selectedActivities.isEmpty()) "Select at least 1" else "Continue (${selectedActivities.size} selected)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Select all that apply",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            activities.forEach { item ->
                val isSelected = item.value in selectedActivities
                val cardColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = MaterialTheme.shapes.extraLarge,
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .animateContentSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveClickable { onToggleActivity(item.value) }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                        androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isSelected) {
                            val mappedGenres = activityGenreMap[item.value] ?: emptySet()
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Target Genres (tap to toggle):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    allSelectedGenres.forEach { genre ->
                                        val isGenreAssigned = genre in mappedGenres
                                        val chipContainerColor = if (isGenreAssigned) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        }
                                        val chipContentColor = if (isGenreAssigned) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        val chipBorderColor = if (isGenreAssigned) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        }

                                        Surface(
                                            color = chipContainerColor,
                                            contentColor = chipContentColor,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, chipBorderColor),
                                            modifier = Modifier
                                                .expressiveClickable {
                                                    val newGenres = if (isGenreAssigned) {
                                                        mappedGenres - genre
                                                    } else {
                                                        mappedGenres + genre
                                                    }
                                                    onSetGenresForActivity(item.value, newGenres)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (isGenreAssigned) {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Text(
                                                    text = genre,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isGenreAssigned) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LengthPickerScreen(
    selectedLengths: Set<String>,
    lengthGenreMap: Map<String, Set<String>>,
    allSelectedGenres: Set<String>,
    onToggleLength: (String) -> Unit,
    onSetGenresForLength: (String, Set<String>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val lengths = listOf(
        LengthItem("Short", "short", "Under 15 minutes", Icons.Rounded.Timer10),
        LengthItem("Medium", "medium", "15 to 45 minutes", Icons.Rounded.Timer),
        LengthItem("Long", "long", "Over 45 minutes", Icons.Rounded.HourglassEmpty)
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Preferred episode length",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = selectedLengths.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            text = if (selectedLengths.isEmpty()) "Select at least 1" else "Generate My Feed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Select all that apply",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            lengths.forEach { item ->
                val isSelected = item.value in selectedLengths
                val cardColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = MaterialTheme.shapes.extraLarge,
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .animateContentSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveClickable { onToggleLength(item.value) }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondaryContainer,
                                        androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isSelected) {
                            val mappedGenres = lengthGenreMap[item.value] ?: emptySet()
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Target Genres (tap to toggle):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    allSelectedGenres.forEach { genre ->
                                        val isGenreAssigned = genre in mappedGenres
                                        val chipContainerColor = if (isGenreAssigned) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        }
                                        val chipContentColor = if (isGenreAssigned) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        val chipBorderColor = if (isGenreAssigned) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        }

                                        Surface(
                                            color = chipContainerColor,
                                            contentColor = chipContentColor,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, chipBorderColor),
                                            modifier = Modifier
                                                .expressiveClickable {
                                                    val newGenres = if (isGenreAssigned) {
                                                        mappedGenres - genre
                                                    } else {
                                                        mappedGenres + genre
                                                    }
                                                    onSetGenresForLength(item.value, newGenres)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (isGenreAssigned) {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Text(
                                                    text = genre,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (isGenreAssigned) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


private data class ActivityItem(val label: String, val value: String, val icon: ImageVector)
private data class LengthItem(val label: String, val value: String, val description: String, val icon: ImageVector)

