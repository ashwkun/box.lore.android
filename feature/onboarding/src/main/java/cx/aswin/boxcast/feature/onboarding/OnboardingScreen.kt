package cx.aswin.boxcast.feature.onboarding

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.launch

// Genre data matching GenreSelector.kt
data class GenreItem(val label: String, val value: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val ONBOARDING_GENRES = listOf(
    GenreItem("News", "News", Icons.Rounded.Newspaper),
    GenreItem("Tech", "Technology", Icons.Rounded.Computer),
    GenreItem("Business", "Business", Icons.Rounded.Work),
    GenreItem("Comedy", "Comedy", Icons.Rounded.SentimentVerySatisfied),
    GenreItem("True Crime", "True Crime", Icons.Rounded.Fingerprint),
    GenreItem("Sports", "Sports", Icons.Rounded.EmojiEvents),
    GenreItem("Health", "Health", Icons.Rounded.MonitorHeart),
    GenreItem("History", "History", Icons.Rounded.AccountBalance),
    GenreItem("Arts", "Arts", Icons.Rounded.Palette),
    GenreItem("Society", "Society & Culture", Icons.Rounded.Groups),
    GenreItem("Education", "Education", Icons.Rounded.School),
    GenreItem("Science", "Science", Icons.Rounded.Science),
    GenreItem("TV & Film", "TV & Film", Icons.Rounded.Movie),
    GenreItem("Fiction", "Fiction", Icons.Rounded.AutoStories),
    GenreItem("Music", "Music", Icons.Rounded.MusicNote),
    GenreItem("Religion", "Religion & Spirituality", Icons.Rounded.SelfImprovement),
    GenreItem("Family", "Kids & Family", Icons.Rounded.ChildCare),
    GenreItem("Leisure", "Leisure", Icons.Rounded.Weekend),
    GenreItem("Govt", "Government", Icons.Rounded.Gavel)
)

@OptIn(ExperimentalTextApi::class)
private val CondensedGoogleSans = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        Font(
            cx.aswin.boxcast.core.designsystem.R.font.google_sans_variable,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(700),
                FontVariation.Setting("wdth", 75f)
            )
        )
    )
} else {
    FontFamily.Default
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    onImportClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnboardingCompleted = remember { viewModel.isOnboardingCompleted() }
    
    val handleComplete = {
        if (uiState.selectedPodcasts.isNotEmpty()) {
            viewModel.generateRecommendationsFromSearch()
        } else {
            viewModel.completeOnboarding(onComplete)
        }
    }

    val isRootStep = uiState.currentStep == OnboardingStep.WELCOME || 
            uiState.currentStep == OnboardingStep.AI_ONBOARDING

    BackHandler(
        enabled = if (isOnboardingCompleted) {
            true
        } else {
            !isRootStep
        }
    ) {
        if (isOnboardingCompleted && (uiState.currentStep == OnboardingStep.WELCOME || 
                                      uiState.currentStep == OnboardingStep.AI_ONBOARDING || 
                                      uiState.currentStep == OnboardingStep.GENRES)) {
            onBack()
        } else {
            when (uiState.currentStep) {
                OnboardingStep.WELCOME -> {
                    // Handled above
                }
                OnboardingStep.GENRES -> {
                    viewModel.navigateBackToWelcome()
                }
                OnboardingStep.SUB_GENRES -> {
                    viewModel.navigateBackFromSubGenres()
                }
                OnboardingStep.ACTIVITY_PICKER -> {
                    viewModel.navigateBackFromActivityPicker()
                }
                OnboardingStep.LENGTH_PICKER -> {
                    viewModel.navigateBackFromLengthPicker()
                }
                OnboardingStep.SEARCH -> {
                    if (uiState.searchQuery.isNotEmpty()) {
                        viewModel.updateSearchQuery("")
                    } else {
                        viewModel.navigateBackFromSearch()
                    }
                }
                OnboardingStep.AI_ONBOARDING -> {
                    // Handled above
                }
                OnboardingStep.AI_SUGGESTIONS -> {
                    viewModel.navigateBackFromSuggestions()
                }
                else -> {}
            }
        }
    }

    // Main content with animated transitions
    AnimatedContent(
        targetState = uiState.currentStep,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
        },
        label = "onboarding_step"
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> {
                LaunchedEffect(Unit) {
                    viewModel.onWelcomeScreenViewed()
                }
                WelcomeScreen(
                    onHelpMeFind = viewModel::startOnboarding,
                    onSearch = viewModel::navigateToSearch,
                    onSkip = { viewModel.skipOnboarding(onComplete) },
                    onImportClick = onImportClick
                )
            }
            OnboardingStep.GENRES -> {
                LaunchedEffect(Unit) { viewModel.onGenreScreenViewed() }
                GenrePickerScreen(
                    selectedGenres = uiState.selectedGenres,
                    onToggleGenre = viewModel::toggleGenre,
                    onContinue = viewModel::continueToRecommendations,
                    onBack = {
                        if (isOnboardingCompleted) {
                            onBack()
                        } else {
                            viewModel.navigateBackToWelcome()
                        }
                    }
                )
            }
            OnboardingStep.SUB_GENRES -> {
                SubGenrePickerScreen(
                    selectedGenres = uiState.selectedGenres,
                    selectedSubGenres = uiState.selectedSubGenres,
                    onToggleSubGenre = viewModel::toggleSubGenre,
                    onBack = viewModel::navigateBackFromSubGenres,
                    onContinue = viewModel::continueToActivityPicker
                )
            }
            OnboardingStep.ACTIVITY_PICKER -> {
                ActivityPickerScreen(
                    selectedActivities = uiState.listeningActivities,
                    activityGenreMap = uiState.activityGenreMap,
                    allSelectedGenres = uiState.selectedGenres,
                    onToggleActivity = viewModel::toggleListeningActivity,
                    onSetGenresForActivity = viewModel::setGenresForActivity,
                    onBack = viewModel::navigateBackFromActivityPicker,
                    onContinue = viewModel::continueToLengthPicker
                )
            }
            OnboardingStep.LENGTH_PICKER -> {
                LengthPickerScreen(
                    selectedLengths = uiState.preferredLengths,
                    lengthGenreMap = uiState.lengthGenreMap,
                    allSelectedGenres = uiState.selectedGenres,
                    onToggleLength = viewModel::togglePreferredLength,
                    onSetGenresForLength = viewModel::setGenresForLength,
                    onBack = viewModel::navigateBackFromLengthPicker,
                    onContinue = viewModel::synthesizeGenreOnboarding
                )
            }
            OnboardingStep.SEARCH -> {
                OnboardingSearchScreen(
                    query = uiState.searchQuery,
                    results = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    subscribedIds = uiState.subscribedPodcastIds,
                    selectedPodcasts = uiState.selectedPodcasts,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSubscribe = viewModel::toggleSubscriptionFromSearch,
                    onBack = viewModel::navigateBackFromSearch,
                    onDone = handleComplete,
                    popularPodcasts = uiState.popularPodcasts,
                    isPopularLoading = uiState.isPopularLoading,
                    selectedSearchGenre = uiState.selectedSearchGenre,
                    onGenreSelect = viewModel::selectSearchGenre
                )
            }
            OnboardingStep.AI_ONBOARDING -> {
                AiOnboardingScreen(
                    uiState = uiState,
                    onBack = {
                        if (isOnboardingCompleted && uiState.aiCurrentTurn <= 1) {
                            onBack()
                        } else {
                            viewModel.navigateBackInAiOnboarding()
                        }
                    },
                    onOptionToggle = viewModel::toggleAiOption,
                    onCustomInputChange = viewModel::updateAiCustomInput,
                    onContinue = {
                        if (uiState.aiOptions.isEmpty() || uiState.aiCurrentTurn >= 7) {
                            viewModel.synthesizeAndBuildCurriculum()
                        } else {
                            viewModel.sendAiTurnInput()
                        }
                    },
                    onRevealSuggestions = viewModel::navigateToSuggestions,
                    onRetryCuration = viewModel::retryLastAction,
                    onSwitchToManual = viewModel::switchToLegacyOnboarding,
                    onBuildFeedNow = viewModel::synthesizeAndBuildCurriculum
                )
            }
            OnboardingStep.AI_SUGGESTIONS -> {
                AiSuggestionsScreen(
                    uiState = uiState,
                    onBack = viewModel::navigateBackFromSuggestions,
                    onToggleSubscription = viewModel::togglePodcastSubscription,
                    onToggleRowSubscriptions = viewModel::toggleAllPodcastsInRow,
                    onRegionChange = viewModel::setRegion,
                    onRetry = viewModel::retryLastAction,
                    onFinish = {
                        viewModel.finishAiOnboarding(onComplete)
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    onHelpMeFind: () -> Unit,
    onSearch: () -> Unit,
    onSkip: () -> Unit,
    onImportClick: () -> Unit
) {
    val entranceProgress = remember { Animatable(0f) }
    val driftProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Run entrance and drift concurrently for a seamless transition
        launch {
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 4800, // Gentle 4.8 seconds
                    easing = LinearEasing
                )
            )
        }
        launch {
            driftProgress.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 25000, // Matched with 732dp loop distance for a smooth 29dp/s speed
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 0. Shared animation progress (linear timeline mapped to FastOutSlowInEasing)
            val logoProgress = ((entranceProgress.value - 0.20f) / 0.55f).coerceIn(0f, 1f)
            val logoEase = FastOutSlowInEasing.transform(logoProgress)

            // 1. Podcast Cover Grid — occupies entire background, always visible
            CinematicBackgroundGrid(
                entranceProgressProvider = { entranceProgress.value },
                driftProgressProvider = { driftProgress.value }
            )

            // 2. Bottom-heavy gradient scrim — grows dynamically as logo/buttons shift up to cover the final logo position
            val scrimColor = MaterialTheme.colorScheme.surface
            val scrimEdge = 0.68f - (logoEase * 0.23f) // 0.68f → 0.45f
            val scrimMid = 0.76f - (logoEase * 0.24f)  // 0.76f → 0.52f
            val scrimFull = 0.81f - (logoEase * 0.24f) // 0.81f → 0.57f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to scrimColor.copy(alpha = 0.0f),
                                (scrimEdge - 0.15f).coerceAtLeast(0f) to scrimColor.copy(alpha = 0.0f),
                                scrimEdge to scrimColor.copy(alpha = 0.5f),
                                scrimMid to scrimColor.copy(alpha = 0.9f),
                                scrimFull to scrimColor,
                                1.0f to scrimColor
                            )
                        )
                    )
            )

            // 3. Content — logo always visible, slides up to reveal buttons
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Push everything to the bottom half
                Spacer(modifier = Modifier.weight(1f))

                // Logo block — always visible, starts bigger and centered in white space,
                // then scales down and nudges up to make room for buttons.
                val logoScale = 1.3f - (logoEase * 0.3f) // 1.3 → 1.0
                val logoOffsetY = (1f - logoEase) * 150f // starts 150dp lower, ends at 0
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            translationY = logoOffsetY * density
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome to",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CondensedGoogleSans,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    cx.aswin.boxcast.core.designsystem.components.BoxCastLogo(
                        textColor = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Staggered button reveal — starts after the logo has mostly moved up
                val btn1RawProgress = ((entranceProgress.value - 0.45f) / 0.30f).coerceIn(0f, 1f)
                val btn2RawProgress = ((entranceProgress.value - 0.53f) / 0.30f).coerceIn(0f, 1f)
                val btn3RawProgress = ((entranceProgress.value - 0.61f) / 0.30f).coerceIn(0f, 1f)

                val btn1Alpha = FastOutSlowInEasing.transform(btn1RawProgress)
                val btn2Alpha = FastOutSlowInEasing.transform(btn2RawProgress)
                val btn3Alpha = FastOutSlowInEasing.transform(btn3RawProgress)

                // Primary CTA
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onHelpMeFind,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .graphicsLayer {
                                alpha = btn1Alpha
                                translationY = (1f - btn1Alpha) * 20.dp.toPx()
                            },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Build my personalized feed.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "We'll find you perfect shows based on what you love",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Floating AI Badge sitting on the button border
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-24).dp, y = (-6).dp)
                            .graphicsLayer {
                                alpha = btn1Alpha
                                translationY = (1f - btn1Alpha) * 20.dp.toPx()
                            }
                            .background(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(percent = 50)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(percent = 50)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "AI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .graphicsLayer {
                            alpha = btn2Alpha
                            translationY = (1f - btn2Alpha) * 20.dp.toPx()
                        },
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSearch,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 50.dp),
                        shape = RoundedCornerShape(percent = 50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "I know my shows",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onImportClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 50.dp),
                        shape = RoundedCornerShape(percent = 50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Import library",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Skip
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = btn3Alpha
                        }
                ) {
                    Text(
                        text = "Skip Setup",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888888)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun CinematicBackgroundGrid(
    entranceProgressProvider: () -> Float,
    driftProgressProvider: () -> Float
) {
    val context = LocalContext.current
    val allCovers = remember {
        (0..99).map { index ->
            context.resources.getIdentifier("pod_cover_$index", "drawable", context.packageName)
        }.filter { it != 0 }.shuffled()
    }

    if (allCovers.isEmpty()) return

    // 4 rows of covers — top half of the screen
    val row1Covers = remember(allCovers) { allCovers.filterIndexed { idx, _ -> idx % 4 == 0 } }
    val row2Covers = remember(allCovers) { allCovers.filterIndexed { idx, _ -> idx % 4 == 1 } }
    val row3Covers = remember(allCovers) { allCovers.filterIndexed { idx, _ -> idx % 4 == 2 } }
    val row4Covers = remember(allCovers) { allCovers.filterIndexed { idx, _ -> idx % 4 == 3 } }

    val SmoothBurstEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ScrollingRow(
            covers = row1Covers,
            translationX = {
                val ent = entranceProgressProvider()
                val scrollProgress = (ent / 0.75f).coerceIn(0f, 1f)
                val scrollEase = SmoothBurstEasing.transform(scrollProgress)
                val drft = driftProgressProvider()
                -2200f + (600f * scrollEase) + (732f * drft)
            }
        )
        ScrollingRow(
            covers = row2Covers,
            translationX = {
                val ent = entranceProgressProvider()
                val scrollProgress = (ent / 0.75f).coerceIn(0f, 1f)
                val scrollEase = SmoothBurstEasing.transform(scrollProgress)
                val drft = driftProgressProvider()
                -100f - (600f * scrollEase) - (732f * drft)
            }
        )
        ScrollingRow(
            covers = row3Covers,
            translationX = {
                val ent = entranceProgressProvider()
                val scrollProgress = (ent / 0.75f).coerceIn(0f, 1f)
                val scrollEase = SmoothBurstEasing.transform(scrollProgress)
                val drft = driftProgressProvider()
                -2400f + (600f * scrollEase) + (732f * drft)
            }
        )
        ScrollingRow(
            covers = row4Covers,
            translationX = {
                val ent = entranceProgressProvider()
                val scrollProgress = (ent / 0.75f).coerceIn(0f, 1f)
                val scrollEase = SmoothBurstEasing.transform(scrollProgress)
                val drft = driftProgressProvider()
                -300f - (600f * scrollEase) - (732f * drft)
            }
        )
    }
}

@Composable
private fun ScrollingRow(
    covers: List<Int>,
    translationX: () -> Float
) {
    // Take exactly 6 covers for a precise 732dp (6 * 122dp) seamless restart loop
    val loopCovers = remember(covers) { covers.take(6) }
    // Repeat covers list 20 times to simulate an infinite list within the bounds of scroll + drift
    val infiniteCovers = remember(loopCovers) { List(20) { loopCovers }.flatten() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(unbounded = true, align = Alignment.Start)
            .graphicsLayer { this.translationX = translationX().dp.toPx() },
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        infiniteCovers.forEach { drawableResId ->
            val cardShape = RoundedCornerShape(16.dp)
            Card(
                modifier = Modifier
                    .size(110.dp),
                shape = cardShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
