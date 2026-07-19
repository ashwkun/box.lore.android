package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AiChatInputPanel(
    uiState: OnboardingUiState,
    suggestionsVisible: Boolean,
    suggestionsEnabled: Boolean,
    isKeyboardVisible: Boolean,
    isTextFieldFocused: Boolean,
    onTextFieldFocusChange: (Boolean) -> Unit,
    elapsedSeconds: Int,
    chatMessageCount: Int,
    listState: LazyListState,
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
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val isCurriculumReady = uiState.aiCurriculumRows.isNotEmpty()
// Input / Synthesis Loader / Error Card Area
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.background,
                            ),
                    ),
                ).padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        val isFinalSynthesis = uiState.isSynthesizing
        val onboardingError = uiState.onboardingError

        if (onboardingError != null) {
            // Error Card layout
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                border =
                    BorderStroke(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Temporary Hiccup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Text(
                        text = onboardingError,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onRetryCuration,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Curation", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onSwitchToManual,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        ) {
                            Text("Choose Topics Manually", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (isFinalSynthesis) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            ) {
                FinalSynthesisLoadingPanel(
                    stage = uiState.aiLoadingStage,
                    elapsedSeconds = elapsedSeconds,
                    onSwitchToManual = onSwitchToManual,
                )
            }
        } else if (isCurriculumReady) {
            // Reveal Suggestions Card Panel (Synthesis Complete)
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                border =
                    BorderStroke(
                        width = 1.5.dp,
                        brush =
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors =
                                    listOf(
                                        primaryColor,
                                        tertiaryColor,
                                    ),
                            ),
                    ),
                shape = RoundedCornerShape(24.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier =
                            Modifier
                                .background(
                                    brush =
                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    primaryColor.copy(alpha = 0.12f),
                                                    tertiaryColor.copy(alpha = 0.12f),
                                                ),
                                        ),
                                    shape = RoundedCornerShape(percent = 50),
                                ).padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "AI SYNTHESIS COMPLETE",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                ),
                            color = primaryColor,
                        )
                    }

                    Text(
                        text = "Done! I've analyzed your preferences and curated a personalized feed of recommended podcasts for you.",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(primaryColor, shape = RoundedCornerShape(25.dp))
                                .expressiveClickable(shape = RoundedCornerShape(25.dp)) {
                                    onRevealSuggestions()
                                },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reveal My Suggestions",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        } else if (uiState.aiOptions.isEmpty() && !uiState.isAiLoading) {
            // Reveal Build My Feed Card Panel (AI decided it has enough context)
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer
                                .copy(
                                    alpha = 0.15f,
                                ).compositeOver(MaterialTheme.colorScheme.surface),
                    ),
                border =
                    BorderStroke(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    ),
                shape = RoundedCornerShape(24.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier =
                            Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(percent = 50),
                                ).padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "INFO SYNTHESIS READY",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                ),
                            color = primaryColor,
                        )
                    }

                    Text(
                        text = "I've got a great understanding of your podcast vibe! Let's generate your custom audio guide.",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(primaryColor, shape = RoundedCornerShape(25.dp))
                                .expressiveClickable(shape = RoundedCornerShape(25.dp)) {
                                    onContinue()
                                },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Build My Feed",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        } else {
            // Conversational Turn (Options & Custom Input Bar)
            AnimatedVisibility(
                visible = suggestionsVisible && !isKeyboardVisible && !isTextFieldFocused,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(250)) + shrinkVertically(animationSpec = tween(250)),
            ) {
                if (uiState.aiCurrentTurn == 1) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
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
                                },
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                    ) {
                        uiState.aiSearchSuggestion?.let { suggestion ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(16.dp),
                                        ).expressiveClickable(shape = RoundedCornerShape(16.dp)) {
                                            focusManager.clearFocus()
                                            onSearchInstead(suggestion)
                                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Search for \u201C$suggestion\u201D",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "Fastest way to find that exact show",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    )
                                }
                            }
                        }

                        uiState.aiOptions.forEach { option ->
                            SuggestionBubble(
                                option = option,
                                isSelected = option in uiState.aiSelectedOptions,
                                enabled = suggestionsEnabled,
                                onClick = {
                                    focusManager.clearFocus()
                                    onOptionToggle(option)
                                },
                            )
                        }

                        if (uiState.aiCurrentTurn >= 4) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "AI will auto-build once it has enough context",
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f),
                                )

                                Text(
                                    text = "or",
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )

                                TextButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        onBuildFeedNow()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Build my feed now",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Premium Custom Chat Input Bar
            val canContinue =
                (uiState.aiSelectedOptions.isNotEmpty() || uiState.aiCustomInputText.isNotBlank()) && !uiState.isAiLoading
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            color =
                                if (isTextFieldFocused) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            shape = RoundedCornerShape(28.dp),
                        ).border(
                            width = if (isTextFieldFocused) 2.dp else 1.dp,
                            color =
                                if (isTextFieldFocused) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            shape = RoundedCornerShape(28.dp),
                        ).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint =
                        if (isTextFieldFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (uiState.aiCustomInputText.isEmpty()) {
                        val exampleText = "I love listening to true crime while I sleep and news when I commute"
                        Text(
                            text =
                                if (isTextFieldFocused) {
                                    "e.g. $exampleText..."
                                } else {
                                    "OR just type in your preferences"
                                },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isTextFieldFocused) {
                                            Modifier.clickable {
                                                onCustomInputChange(exampleText)
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                        )
                    }

                    BasicTextField(
                        value = uiState.aiCustomInputText,
                        onValueChange = onCustomInputChange,
                        enabled = !uiState.isAiLoading,
                        textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        cursorBrush = SolidColor(primaryColor),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    onTextFieldFocusChange(focusState.isFocused)
                                    if (focusState.isFocused) {
                                        coroutineScope.launch {
                                            delay(250)
                                            if (chatMessageCount > 1) {
                                                listState.animateScrollToItem(chatMessageCount + 1)
                                            } else if (chatMessageCount > 0) {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                    }
                                },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onSend = {
                                    if (canContinue) {
                                        focusManager.clearFocus()
                                        onContinue()
                                    }
                                },
                            ),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                val sendBtnBgColor =
                    if (canContinue) {
                        primaryColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.1f,
                        )
                    }
                val sendBtnContentColor =
                    if (canContinue) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(
                                alpha = 0.4f,
                            )
                    }

                IconButton(
                    onClick = {
                        if (canContinue) {
                            focusManager.clearFocus()
                            onContinue()
                        }
                    },
                    enabled = canContinue,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .background(sendBtnBgColor, shape = CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = sendBtnContentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            val showLanguageTip =
                remember(uiState.aiCustomInputText) {
                    val text = uiState.aiCustomInputText.lowercase().trim()
                    if (text.isEmpty()) {
                        return@remember java.util.Locale
                            .getDefault()
                            .language != "en"
                    }

                    // 1. Check for non-Latin scripts (Hindi, Arabic, Japanese, Cyrillic, etc.)
                    val hasNonLatin =
                        text.any { ch ->
                            val script = Character.UnicodeScript.of(ch.code)
                            script != Character.UnicodeScript.LATIN &&
                                script != Character.UnicodeScript.COMMON &&
                                script != Character.UnicodeScript.INHERITED
                        }
                    if (hasNonLatin) return@remember true

                    // 2. Check for Latin characters with non-English accents (á, é, í, ó, ú, ñ, ü, ç, etc.)
                    val hasAccents = text.any { it in "áéíóúñüçàèùâêîôûëïäößæœ" }
                    if (hasAccents) return@remember true

                    // 3. Check for common distinct non-English vocabulary and stopwords
                    val nonEnglishWords =
                        setOf(
                            // Spanish
                            "el",
                            "la",
                            "los",
                            "las",
                            "un",
                            "una",
                            "unas",
                            "unos",
                            "que",
                            "por",
                            "para",
                            "como",
                            "pero",
                            "hola",
                            "gracias",
                            "bueno",
                            "buenos",
                            "dias",
                            "tardes",
                            "noches",
                            "amigo",
                            "amigos",
                            "musica",
                            "futbol",
                            "ciencia",
                            "tecnologia",
                            "salud",
                            "negocios",
                            "deporte",
                            "deportes",
                            "historia",
                            "comedia",
                            "noticias",
                            "mundo",
                            "tiempo",
                            "casa",
                            "vida",
                            "trabajo",
                            "nuevo",
                            // French
                            "les",
                            "des",
                            "et",
                            "est",
                            "dans",
                            "une",
                            "pour",
                            "qui",
                            "avec",
                            "mais",
                            "bonjour",
                            "merci",
                            "oui",
                            "histoire",
                            "musique",
                            "comedie",
                            "sante",
                            "nouvelles",
                            // German
                            "der",
                            "die",
                            "das",
                            "und",
                            "ist",
                            "ein",
                            "eine",
                            "mit",
                            "von",
                            "zu",
                            "nicht",
                            "hallo",
                            "danke",
                            "ja",
                            "bitte",
                            "musik",
                            "geschichte",
                            "wissenschaft",
                            "sport",
                            "nachrichten",
                            // Italian/Portuguese
                            "che",
                            "per",
                            "gli",
                            "em",
                            "com",
                            "uma",
                            "mais",
                            "bom",
                            "dia",
                            "obrigado",
                            "ciao",
                        )
                    val words = text.split(Regex("\\s+")).map { it.replace(Regex("[^a-z]"), "") }
                    if (words.any { it in nonEnglishWords }) return@remember true

                    // 4. Fallback: system locale language
                    java.util.Locale
                        .getDefault()
                        .language != "en"
                }

            val showAnonymousInfo = !showLanguageTip && uiState.aiCurrentTurn == 1

            AnimatedVisibility(visible = showAnonymousInfo) {
                Text(
                    text = "Chats are anonymous and used solely to improve recommendations and the AI model.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }

            AnimatedVisibility(visible = showLanguageTip) {
                Text(
                    text = "Tip: podcast coverage is strongest in English, but I'll do my best in your language.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}
