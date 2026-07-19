package cx.aswin.boxlore.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

sealed class ChatMessage {
    abstract val key: String

    data class ModelMessage(
        val text: String,
        override val key: String,
    ) : ChatMessage()

    data class UserMessage(
        val text: String,
        override val key: String,
    ) : ChatMessage()

    object LoadingMessage : ChatMessage() {
        override val key: String = "loading"
    }

    data class ErrorMessage(
        val errorText: String,
        override val key: String,
    ) : ChatMessage()
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AiChatOnboardingScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onOptionToggle: (String) -> Unit,
    onCustomInputChange: (String) -> Unit,
    onContinue: () -> Unit,
    onRevealSuggestions: () -> Unit,
    onRetryCuration: () -> Unit,
    onSwitchToManual: () -> Unit,
    onBuildFeedNow: () -> Unit,
    onSearchInstead: (String?) -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val chatMessages =
        remember(uiState.aiHistory, uiState.isAiLoading, uiState.isSynthesizing, uiState.onboardingError) {
            AiChatMessageListLogic.buildMessages(
                history = uiState.aiHistory,
                isAiLoading = uiState.isAiLoading,
                isSynthesizing = uiState.isSynthesizing,
                onboardingError = uiState.onboardingError,
            )
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

    LaunchedEffect(
        chatMessages.size,
        uiState.isAiLoading,
        isKeyboardVisible,
        isTextFieldFocused,
        suggestionsVisible,
        uiState.aiSearchSuggestion,
    ) {
        if (chatMessages.size > 1) {
            // Target the last *message* (item 0 is the header), not the trailing
            // spacer — scrolling the spacer to the viewport top pushes the
            // message off screen when the bottom options block is tall.
            val targetIndex = chatMessages.size
            // Wait for the bottom options/search-chip block to finish expanding
            // (300ms animation) before scrolling, or the last message ends up
            // pushed above the shrunken viewport.
            delay(350)
            listState.animateScrollToItem(targetIndex)
        } else if (chatMessages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(0)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        // Gradient backgrounds for visual depth
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors =
                                listOf(
                                    primaryColor.copy(alpha = 0.08f),
                                    secondaryColor.copy(alpha = 0.03f),
                                    Color.Transparent,
                                ),
                            center =
                                androidx.compose.ui.geometry
                                    .Offset(x = 1000f, y = -100f),
                            radius = 1000f,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors =
                                listOf(
                                    tertiaryColor.copy(alpha = 0.06f),
                                    primaryColor.copy(alpha = 0.02f),
                                    Color.Transparent,
                                ),
                            center =
                                androidx.compose.ui.geometry
                                    .Offset(x = -200f, y = 1600f),
                            radius = 1200f,
                        ),
                    ),
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
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                        ) {
                            Text(
                                text = "AI Feed Assistant",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onSearchInstead(null) }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Search",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { focusManager.clearFocus() })
                            },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(
                                text = "Curate Your Feed",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "A few quick questions to learn your taste and build your feed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(chatMessages, key = { it.key }) { message ->
                        val shouldAnimate =
                            remember(message.key) {
                                if (message.key in animatedMessageKeys) {
                                    false
                                } else {
                                    animatedMessageKeys.add(message.key)
                                    true
                                }
                            }

                        val lastMessageIndex =
                            chatMessages.indexOfLast {
                                it !is ChatMessage.LoadingMessage &&
                                    it !is ChatMessage.ErrorMessage
                            }
                        val currentMessageIndex = chatMessages.indexOf(message)
                        val isCompact =
                            currentMessageIndex > 0 &&
                                message::class == chatMessages[currentMessageIndex - 1]::class &&
                                message !is ChatMessage.LoadingMessage &&
                                message !is ChatMessage.ErrorMessage

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
                                        AiLoadingBubble(
                                            stage = uiState.aiLoadingStage,
                                            elapsedSeconds = elapsedSeconds,
                                            onSwitchToManual = onSwitchToManual,
                                        )
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (suggestionsVisible && !isKeyboardVisible) 140.dp else 16.dp),
                        )
                    }
                }


                AiChatInputPanel(
                    uiState = uiState,
                    suggestionsVisible = suggestionsVisible,
                    suggestionsEnabled = suggestionsEnabled,
                    isKeyboardVisible = isKeyboardVisible,
                    isTextFieldFocused = isTextFieldFocused,
                    onTextFieldFocusChange = { isTextFieldFocused = it },
                    elapsedSeconds = elapsedSeconds,
                    chatMessageCount = chatMessages.size,
                    listState = listState,
                    onOptionToggle = onOptionToggle,
                    onCustomInputChange = onCustomInputChange,
                    onContinue = onContinue,
                    onRevealSuggestions = onRevealSuggestions,
                    onRetryCuration = onRetryCuration,
                    onSwitchToManual = onSwitchToManual,
                    onBuildFeedNow = onBuildFeedNow,
                    onSearchInstead = onSearchInstead,
                )
            }
        }
    }
}
