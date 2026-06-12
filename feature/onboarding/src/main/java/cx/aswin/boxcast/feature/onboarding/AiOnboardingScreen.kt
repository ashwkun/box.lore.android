package cx.aswin.boxcast.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.designsystem.components.BoxCastLoader
import cx.aswin.boxcast.core.designsystem.components.LogRecomposition
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxcast.core.network.model.OnboardingPart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class ChatMessage {
    abstract val key: String
    data class ModelMessage(val text: String, override val key: String) : ChatMessage()
    data class UserMessage(val text: String, override val key: String) : ChatMessage()
    object LoadingMessage : ChatMessage() { override val key: String = "loading" }
    data class ErrorMessage(val errorText: String, override val key: String) : ChatMessage()
}

@Composable
internal fun AiOnboardingScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onOptionToggle: (String) -> Unit,
    onCustomInputChange: (String) -> Unit,
    onContinue: () -> Unit,
    onRevealSuggestions: () -> Unit,
    onRetryCuration: () -> Unit,
    onSwitchToManual: () -> Unit,
    onBuildFeedNow: () -> Unit
) {
    AiChatOnboardingScreen(
        uiState = uiState,
        onBack = onBack,
        onOptionToggle = onOptionToggle,
        onCustomInputChange = onCustomInputChange,
        onContinue = onContinue,
        onRevealSuggestions = onRevealSuggestions,
        onRetryCuration = onRetryCuration,
        onSwitchToManual = onSwitchToManual,
        onBuildFeedNow = onBuildFeedNow
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AiChatOnboardingScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onOptionToggle: (String) -> Unit,
    onCustomInputChange: (String) -> Unit,
    onContinue: () -> Unit,
    onRevealSuggestions: () -> Unit,
    onRetryCuration: () -> Unit,
    onSwitchToManual: () -> Unit,
    onBuildFeedNow: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val chatMessages = remember(uiState.aiHistory, uiState.isAiLoading, uiState.isSynthesizing, uiState.onboardingError) {
        val list = mutableListOf<ChatMessage>()
        
        list.add(
            ChatMessage.ModelMessage(
                text = "Hi! I'm boxcast. To start, what kind of a listener are you?",
                key = "init_model"
            )
        )
        
        uiState.aiHistory.forEachIndexed { index, entry ->
            val text = entry.parts.joinToString(". ") { it.text }
            if (entry.role == "user") {
                list.add(ChatMessage.UserMessage(text, key = "user_$index"))
            } else {
                list.add(ChatMessage.ModelMessage(text, key = "model_$index"))
            }
        }
        
        if (uiState.isAiLoading && !uiState.isSynthesizing) {
            list.add(ChatMessage.LoadingMessage)
        }

        if (uiState.onboardingError != null) {
            list.add(ChatMessage.ErrorMessage(uiState.onboardingError, key = "error"))
        }
        
        list
    }

    val listState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 50)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var isTextFieldFocused by remember { mutableStateOf(false) }
    val animatedMessageKeys = remember { mutableStateListOf<String>() }

    val isKeyboardVisible = WindowInsets.isImeVisible

    // Intercept back button/gesture to clear focus first, or navigate back if not focused
    BackHandler(enabled = true) {
        if (isTextFieldFocused) {
            focusManager.clearFocus()
        } else {
            onBack()
        }
    }

    // Clear focus when keyboard is hidden to update focused state and cursor visibility
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    // In-flight seconds counter
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.isAiLoading) {
        if (uiState.isAiLoading) {
            elapsedSeconds = 0
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        } else {
            elapsedSeconds = 0
        }
    }

    val isCurriculumReady = uiState.aiCurriculumRows.isNotEmpty()
    val suggestionsVisible = !isCurriculumReady && uiState.onboardingError == null && !uiState.isSynthesizing && !uiState.isAiLoading
    val suggestionsEnabled = !isTextFieldFocused && !isKeyboardVisible && uiState.aiCustomInputText.isEmpty() && !uiState.isAiLoading

    LaunchedEffect(chatMessages.size, uiState.isAiLoading, isKeyboardVisible, isTextFieldFocused, suggestionsVisible) {
        if (chatMessages.size > 1) {
            val targetIndex = chatMessages.size + 1
            delay(100)
            listState.animateScrollToItem(targetIndex)
        } else if (chatMessages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Gradient backgrounds for visual depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.08f),
                            secondaryColor.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(x = 1000f, y = -100f),
                        radius = 1000f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = 0.06f),
                            primaryColor.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(x = -200f, y = 1600f),
                        radius = 1200f
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showTopBarTitle,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 }
                        ) {
                            Text(
                                text = "AI Feed Assistant",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(
                                text = "Curate Your Feed",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Answer a few questions to help our AI customize your podcast experience.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(chatMessages, key = { it.key }) { message ->
                        val shouldAnimate = remember(message.key) {
                            if (message.key in animatedMessageKeys) {
                                false
                            } else {
                                animatedMessageKeys.add(message.key)
                                true
                            }
                        }

                        val lastMessageIndex = chatMessages.indexOfLast { it !is ChatMessage.LoadingMessage && it !is ChatMessage.ErrorMessage }
                        val currentMessageIndex = chatMessages.indexOf(message)
                        val isCompact = currentMessageIndex > 0 && 
                                message::class == chatMessages[currentMessageIndex - 1]::class &&
                                message !is ChatMessage.LoadingMessage && message !is ChatMessage.ErrorMessage

                        Box(modifier = Modifier.fillMaxWidth()) {
                            val bubbleContent = @Composable {
                                when (message) {
                                    is ChatMessage.ModelMessage -> {
                                        AiMessageBubble(text = message.text, isCompact = isCompact)
                                    }
                                    is ChatMessage.UserMessage -> {
                                        UserMessageBubble(text = message.text, isCompact = isCompact)
                                    }
                                    is ChatMessage.LoadingMessage -> {
                                        AiLoadingBubble(stage = uiState.aiLoadingStage, elapsedSeconds = elapsedSeconds, onSwitchToManual = onSwitchToManual)
                                    }
                                    is ChatMessage.ErrorMessage -> {
                                        // Error message bubble is rendered below as error card panel
                                        Spacer(modifier = Modifier.height(1.dp))
                                    }
                                }
                            }
                            if (shouldAnimate) {
                                AnimatedMessageContainer(content = bubbleContent)
                            } else {
                                bubbleContent()
                            }
                        }
                    }
                    
                    item(key = "bottom_spacer") {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (suggestionsVisible && !isKeyboardVisible) 140.dp else 16.dp)
                        )
                    }
                }

                // Input / Synthesis Loader / Error Card Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    val isFinalSynthesis = uiState.isSynthesizing
                    val isError = uiState.onboardingError != null
                    
                    if (isError) {
                        // Error Card layout
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Temporary Hiccup",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = uiState.onboardingError ?: "We encountered a temporary hiccup. Let's try that again, or you can choose to customize your feed manually.",
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onRetryCuration,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Retry Curation", fontWeight = FontWeight.Bold)
                                    }
                                    
                                    OutlinedButton(
                                        onClick = onSwitchToManual,
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ) {
                                        Text("Choose Topics Manually", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (isFinalSynthesis) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            FinalSynthesisLoadingPanel(stage = uiState.aiLoadingStage, elapsedSeconds = elapsedSeconds, onSwitchToManual = onSwitchToManual)
                        }
                    } else if (isCurriculumReady) {
                        // Reveal Suggestions Card Panel (Synthesis Complete)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.4f),
                                        tertiaryColor.copy(alpha = 0.2f)
                                    )
                                )
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = listOf(
                                                    primaryColor.copy(alpha = 0.12f),
                                                    tertiaryColor.copy(alpha = 0.12f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(percent = 50)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "AI SYNTHESIS COMPLETE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp
                                        ),
                                        color = primaryColor
                                    )
                                }

                                Text(
                                    text = "Done! I've analyzed your preferences and curated a personalized feed of recommended podcasts for you.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .background(primaryColor, shape = RoundedCornerShape(25.dp))
                                        .expressiveClickable(shape = RoundedCornerShape(25.dp)) {
                                            onRevealSuggestions()
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Reveal My Suggestions",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    } else if (uiState.aiOptions.isEmpty() && !uiState.isAiLoading) {
                        // Reveal Build My Feed Card Panel (AI decided it has enough context)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(percent = 50)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "INFO SYNTHESIS READY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp
                                        ),
                                        color = primaryColor
                                    )
                                }

                                Text(
                                    text = "I've got a great understanding of your podcast vibe! Let's generate your custom audio guide.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .background(primaryColor, shape = RoundedCornerShape(25.dp))
                                        .expressiveClickable(shape = RoundedCornerShape(25.dp)) {
                                            onContinue()
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Build My Feed",
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    } else {
                        // Conversational Turn (Options & Custom Input Bar)
                        AnimatedVisibility(
                            visible = suggestionsVisible,
                            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(250)) + shrinkVertically(animationSpec = tween(250))
                        ) {
                            if (uiState.aiCurrentTurn == 1) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    uiState.aiOptions.forEach { opt ->
                                        val isSelected = opt.isNotEmpty() && opt in uiState.aiSelectedOptions
                                        val parts = opt.split("|")
                                        val title = parts.getOrNull(0)?.trim() ?: opt
                                        val description = parts.getOrNull(1)?.trim() ?: ""
                                        val icons = getOptionIcons(title)
                                        ChoiceRow(
                                            title = title,
                                            description = description,
                                            icon = icons.first,
                                            selectedIcon = icons.second,
                                            isSelected = isSelected,
                                            enabled = suggestionsEnabled,
                                            modifier = Modifier.fillMaxWidth().height(72.dp),
                                            onClick = {
                                                if (opt.isNotEmpty()) {
                                                    focusManager.clearFocus()
                                                    onOptionToggle(opt)
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    uiState.aiOptions.forEach { option ->
                                        SuggestionBubble(
                                            option = option,
                                            isSelected = option in uiState.aiSelectedOptions,
                                            enabled = suggestionsEnabled,
                                            onClick = {
                                                focusManager.clearFocus()
                                                onOptionToggle(option)
                                            }
                                        )
                                    }

                                    if (uiState.aiCurrentTurn >= 4) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "AI will auto-build once it has enough context",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.weight(1f)
                                            )
                                            
                                            Text(
                                                text = "or",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            
                                            TextButton(
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    onBuildFeedNow()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Build my feed now",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Premium Custom Chat Input Bar
                        val canContinue = (uiState.aiSelectedOptions.isNotEmpty() || uiState.aiCustomInputText.isNotBlank()) && !uiState.isAiLoading
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    color = if (isTextFieldFocused) 
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(28.dp)
                                )
                                .border(
                                    width = if (isTextFieldFocused) 2.dp else 1.dp,
                                    color = if (isTextFieldFocused) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(28.dp)
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = if (isTextFieldFocused) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (uiState.aiCustomInputText.isEmpty()) {
                                    val exampleText = "I love listening to true crime while I sleep and news when I commute"
                                    Text(
                                        text = if (isTextFieldFocused)
                                            "e.g. $exampleText..."
                                        else
                                            "OR just type in your preferences",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (isTextFieldFocused) {
                                                    Modifier.clickable {
                                                        onCustomInputChange(exampleText)
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                }
                                
                                BasicTextField(
                                    value = uiState.aiCustomInputText,
                                    onValueChange = onCustomInputChange,
                                    enabled = !uiState.isAiLoading,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(primaryColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            isTextFieldFocused = focusState.isFocused
                                            if (focusState.isFocused) {
                                                coroutineScope.launch {
                                                    delay(250)
                                                    if (chatMessages.size > 1) {
                                                        listState.animateScrollToItem(chatMessages.size + 1)
                                                    } else if (chatMessages.isNotEmpty()) {
                                                        listState.animateScrollToItem(0)
                                                    }
                                                }
                                            }
                                        },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Send
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (canContinue) {
                                                focusManager.clearFocus()
                                                onContinue()
                                            }
                                        }
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            val sendBtnBgColor = if (canContinue) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            val sendBtnContentColor = if (canContinue) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                
                            IconButton(
                                onClick = {
                                    if (canContinue) {
                                        focusManager.clearFocus()
                                        onContinue()
                                    }
                                },
                                enabled = canContinue,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(sendBtnBgColor, shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send",
                                    tint = sendBtnContentColor,
                                    modifier = Modifier.size(18.dp)
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
private fun ChoiceRow(
    title: String,
    description: String,
    icon: ImageVector,
    selectedIcon: ImageVector,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val borderStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer { alpha = if (enabled) 1.0f else 0.38f }
            .expressiveClickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) selectedIcon else icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AiAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                ),
                shape = CircleShape
            )
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AiMessageBubble(text: String, isCompact: Boolean = false) {
    val alpha by animateFloatAsState(targetValue = if (isCompact) 0.5f else 1.0f, label = "alpha")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isCompact) {
            AiAvatar(modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(44.dp))
        }
        
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .background(
                    if (isCompact) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    width = 1.dp,
                    color = if (isCompact)
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                )
                .padding(
                    horizontal = if (isCompact) 12.dp else 16.dp, 
                    vertical = if (isCompact) 8.dp else 12.dp
                )
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (isCompact) 13.sp else 15.sp,
                    lineHeight = if (isCompact) 18.sp else 22.sp
                ),
                color = if (isCompact) 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) 
                else 
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun UserMessageBubble(text: String, isCompact: Boolean = false) {
    val alpha by animateFloatAsState(targetValue = if (isCompact) 0.5f else 1.0f, label = "alpha")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp))
                .background(
                    if (isCompact) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                )
                .padding(
                    horizontal = if (isCompact) 12.dp else 16.dp, 
                    vertical = if (isCompact) 8.dp else 12.dp
                )
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (isCompact) 13.sp else 15.sp,
                    lineHeight = if (isCompact) 18.sp else 22.sp
                ),
                fontWeight = if (isCompact) FontWeight.Normal else FontWeight.Medium,
                color = if (isCompact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun DelayBypassBanner(
    onSwitchToManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Rounded.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 1.dp)
                )
                Text(
                    text = "Taking longer than expected? You can skip the chat and customize your feed directly by picking your favorite topics.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            FilledTonalButton(
                onClick = onSwitchToManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    text = "Choose Topics Manually",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AiLoadingBubble(stage: AiLoadingStage, elapsedSeconds: Int, onSwitchToManual: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        AiAvatar(modifier = Modifier.padding(top = 4.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThinkingIndicator(stage = stage, elapsedSeconds = elapsedSeconds)
                
                AnimatedVisibility(
                    visible = elapsedSeconds >= 15,
                    enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
                    exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        DelayBypassBanner(onSwitchToManual = onSwitchToManual)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotAlphas = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = index * 150, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha_$index"
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        dotAlphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { this.alpha = alpha.value }
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
            )
        }
    }
}

@Composable
private fun ThinkingIndicator(stage: AiLoadingStage, elapsedSeconds: Int) {
    val thinkingMessages = remember(stage) {
        when (stage) {
            AiLoadingStage.GENERATING_RESPONSE -> listOf(
                "Analyzing conversation context...",
                "Formulating suggestions...",
                "Searching podcast indexes...",
                "Formatting questions..."
            )
            AiLoadingStage.SYNTHESIZING_PREFERENCES -> listOf(
                "Analyzing conversation history...",
                "Extracting your topic preferences...",
                "Building interest matrices..."
            )
            AiLoadingStage.FETCHING_CATALOGS -> listOf(
                "Searching podcast database...",
                "Connecting to vector index...",
                "Matching topic catalogs to shows..."
            )
            AiLoadingStage.ASSEMBLING_FEED -> listOf(
                "Generating tailored rows...",
                "Curating top choices...",
                "Polishing curriculum guide..."
            )
            else -> listOf(
                "Thinking...",
                "Refining curation..."
            )
        }
    }
    
    var currentMessageIndex by remember { mutableStateOf(0) }
    
    LaunchedEffect(thinkingMessages) {
        currentMessageIndex = 0
        while (true) {
            delay(2500)
            currentMessageIndex = (currentMessageIndex + 1) % thinkingMessages.size
        }
    }
    
    val baseText = thinkingMessages.getOrElse(currentMessageIndex) { "Thinking..." }
    val displayedText = if (elapsedSeconds >= 15) {
        "Hmm, this is taking slightly longer than expected. Should be done in about ${maxOf(1, 45 - elapsedSeconds)}s..."
    } else {
        baseText
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (elapsedSeconds >= 15) {
            BoxCastLoader.Expressive(size = 18.dp)
        } else {
            TypingIndicator()
        }
        
        AnimatedContent(
            targetState = displayedText,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "thinkingText"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinalSynthesisLoadingBubble(stage: AiLoadingStage, elapsedSeconds: Int, onSwitchToManual: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        AiAvatar(modifier = Modifier.padding(top = 4.dp))
        Spacer(modifier = Modifier.width(8.dp))
        
        FinalSynthesisLoadingPanel(stage = stage, elapsedSeconds = elapsedSeconds, onSwitchToManual = onSwitchToManual)
    }
}

@Composable
private fun FinalSynthesisLoadingPanel(stage: AiLoadingStage, elapsedSeconds: Int, onSwitchToManual: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale1 by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale1"
            )
            val pulseScale2 by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale2"
            )

            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    drawCircle(
                        color = Color(0xFF81B29A).copy(alpha = 0.15f * (1f - pulseScale2 / 1.2f)),
                        radius = size.minDimension / 2 * pulseScale2
                    )
                }
                Canvas(modifier = Modifier.size(56.dp)) {
                    drawCircle(
                        color = Color(0xFF3D5A80).copy(alpha = 0.2f * (1f - pulseScale1 / 1.2f)),
                        radius = size.minDimension / 2 * pulseScale1
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            ThinkingIndicator(stage = stage, elapsedSeconds = elapsedSeconds)

            AnimatedVisibility(
                visible = elapsedSeconds >= 15,
                enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 1.dp)
                    DelayBypassBanner(onSwitchToManual = onSwitchToManual)
                }
            }
        }
    }
}

@Composable
private fun AnimatedMessageContainer(
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 500)) +
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) +
                scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                ),
        exit = fadeOut(animationSpec = tween(durationMillis = 300))
    ) {
        content()
    }
}

private fun getOptionIcons(option: String): Pair<ImageVector, ImageVector> {
    val lower = option.lowercase()
    return when {
        // Story / Narrative / True Crime
        lower.contains("story") || lower.contains("mystery") || lower.contains("fiction") || 
        lower.contains("crime") || lower.contains("detective") || lower.contains("thriller") || 
        lower.contains("narrative") || lower.contains("case") || lower.contains("murder") || 
        lower.contains("novel") || lower.contains("book") -> {
            Pair(Icons.Outlined.AutoStories, Icons.Rounded.AutoStories)
        }
        
        // Learn / Info / Science / Deep Dive / History / Tech / Business
        lower.contains("learn") || lower.contains("deep") || lower.contains("science") || 
        lower.contains("tech") || lower.contains("mind") || lower.contains("knowledge") || 
        lower.contains("teach") || lower.contains("educat") || lower.contains("history") || 
        lower.contains("documentary") || lower.contains("space") || lower.contains("fact") ||
        lower.contains("business") || lower.contains("career") || lower.contains("finance") ||
        lower.contains("explain") || lower.contains("intellect") || lower.contains("discover") ||
        lower.contains("explore") || lower.contains("curious") -> {
            Pair(Icons.Outlined.Lightbulb, Icons.Rounded.Lightbulb)
        }
        
        // Conversation / Talk / Comedy
        lower.contains("comedy") || lower.contains("conversation") || lower.contains("chat") || 
        lower.contains("talk") || lower.contains("host") || lower.contains("interview") || 
        lower.contains("forum") || lower.contains("banter") || lower.contains("laugh") || 
        lower.contains("humor") || lower.contains("discuss") || lower.contains("society") ||
        lower.contains("culture") -> {
            Pair(Icons.Outlined.Forum, Icons.Rounded.Forum)
        }
        
        // Relax / Calm / Sleep / Spa
        lower.contains("relax") || lower.contains("wind") || lower.contains("sooth") || 
        lower.contains("sleep") || lower.contains("spa") || lower.contains("calm") || 
        lower.contains("quiet") || lower.contains("meditat") || lower.contains("mindful") ||
        lower.contains("peace") || lower.contains("ambient") || lower.contains("nature") ||
        lower.contains("chill") -> {
            Pair(Icons.Outlined.Spa, Icons.Rounded.Spa)
        }
        
        // News / Politics
        lower.contains("news") || lower.contains("daily") || lower.contains("current") || 
        lower.contains("today") || lower.contains("politic") || lower.contains("world") ||
        lower.contains("report") || lower.contains("journalism") -> {
            Pair(Icons.Outlined.Newspaper, Icons.Rounded.Newspaper)
        }
        
        // Music
        lower.contains("music") || lower.contains("song") || lower.contains("audio") || 
        lower.contains("sound") || lower.contains("melody") || lower.contains("beat") -> {
            Pair(Icons.Outlined.MusicNote, Icons.Rounded.MusicNote)
        }
        
        // Sports
        lower.contains("sport") || lower.contains("game") || lower.contains("play") || 
        lower.contains("football") || lower.contains("f1") || lower.contains("race") ||
        lower.contains("athlete") || lower.contains("match") || lower.contains("league") -> {
            Pair(Icons.Outlined.EmojiEvents, Icons.Rounded.EmojiEvents)
        }
        
        // Health / Fitness
        lower.contains("health") || lower.contains("fit") || lower.contains("body") || 
        lower.contains("well") || lower.contains("exercise") || lower.contains("medicine") ||
        lower.contains("mental") || lower.contains("doctor") -> {
            Pair(Icons.Outlined.MonitorHeart, Icons.Rounded.MonitorHeart)
        }
        
        // Default
        else -> {
            Pair(Icons.Outlined.Mic, Icons.Rounded.Mic)
        }
    }
}

@Composable
private fun SuggestionBubble(
    option: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected && enabled) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val icons = getOptionIcons(option)
    val icon = if (isSelected) icons.second else icons.first

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.outlinedCardElevation(
            defaultElevation = if (isSelected && enabled) 3.dp else 0.dp
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1.0f else 0.38f
            }
            .then(
                if (enabled) {
                    Modifier.expressiveClickable { onClick() }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = option,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildFeedNowChip(
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        .compositeOver(MaterialTheme.colorScheme.surface)

    val contentColor = MaterialTheme.colorScheme.primary

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .graphicsLayer {
                alpha = if (enabled) 1.0f else 0.38f
            }
            .then(
                if (enabled) {
                    Modifier.expressiveClickable { onClick() }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "✨ Build my feed now",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = "Synthesize choices and generate recommendations",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
