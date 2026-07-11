package cx.aswin.boxcast.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.EngagementBottomSheetScaffold
import kotlinx.coroutines.launch

/**
 * Milestone review prompt — M3 Expressive bottom sheet.
 * Diverts happy users to Play Store, frustrated users to Feedback.
 *
 * @param variant Milestone uses episode-based copy; [ReviewPromptVariant.PromoterHandoff]
 *   acknowledges the user already scored 8+ on NPS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPromptSheet(
    completedCount: Int,
    onDismissRequest: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    variant: ReviewPromptVariant = ReviewPromptVariant.Milestone,
) {
    val title =
        when (variant) {
            ReviewPromptVariant.PromoterHandoff -> "Glad you're enjoying boxcast"
            ReviewPromptVariant.Milestone ->
                when {
                    completedCount >= 30 -> "You're a podcast pro"
                    completedCount >= 15 -> "You're a regular listener"
                    else -> "You're on a roll"
                }
        }

    val body =
        when (variant) {
            ReviewPromptVariant.PromoterHandoff ->
                "Thanks for the great score — a quick Play Store rating helps other listeners discover boxcast."
            ReviewPromptVariant.Milestone ->
                when {
                    completedCount >= 30 ->
                        "$completedCount episodes and counting — your review would mean the world to us."
                    completedCount >= 15 ->
                        "You've finished $completedCount episodes. Mind leaving a quick rating?"
                    else -> "You've completed $completedCount episodes. How's boxcast treating you?"
                }
        }

    val primaryLabel =
        when (variant) {
            ReviewPromptVariant.PromoterHandoff -> "Rate on Play Store"
            ReviewPromptVariant.Milestone ->
                if (completedCount >= 15) "Rate boxcast" else "Loving it"
        }

    val secondaryLabel =
        when (variant) {
            ReviewPromptVariant.PromoterHandoff -> "Not right now"
            ReviewPromptVariant.Milestone ->
                if (completedCount >= 30) "Not right now" else "Could be better"
        }

    val onSecondaryClick =
        when (variant) {
            ReviewPromptVariant.PromoterHandoff -> onDismissRequest
            ReviewPromptVariant.Milestone ->
                if (completedCount >= 30) onDismissRequest else onNavigateToFeedback
        }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    EngagementBottomSheetScaffold(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        showCloseButton = false,
        properties = ModalBottomSheetProperties(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onNavigateToReview,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSecondaryClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Copy variant for [ReviewPromptSheet] — milestone-based or post-NPS promoter handoff. */
enum class ReviewPromptVariant {
    /** Episode-count milestone (5/15/30 completed). */
    Milestone,

    /** Deferred Play review after the user scored 8+ on NPS. */
    PromoterHandoff,
}

/**
 * Shown after the Play Store review flow returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostReviewSheet(
    onDismissRequest: () -> Unit,
    onNavigateToFeedback: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "Glad you're enjoying boxcast",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "If you didn't get a chance to leave a rating, no worries. We also love hearing your feature ideas!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Button(
                onClick = onNavigateToFeedback,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share an idea", style = MaterialTheme.typography.labelLarge)
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(
                onClick = {
                    onDismissRequest()
                    val pkgName = context.packageName
                    try {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkgName")))
                    } catch (e: Exception) {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkgName")))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rate on Google Play", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Maybe later",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Feedback form — submits to Cloudflare Worker proxy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(
    appVersion: String,
    onSubmit: suspend (category: String, message: String, version: String, email: String) -> Boolean,
    onRateInstead: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("feature") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            kotlinx.coroutines.delay(1800)
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share your thoughts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            if (isSuccess) {
                // Success state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.ThumbUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sent — thank you!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // Category selector
                Text(
                    text = "What's this about?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        "feature" to "Feature idea",
                        "bug" to "Bug report",
                        "other" to "Other"
                    )
                    
                    categories.forEach { (id, label) ->
                        FilterChip(
                            selected = selectedCategory == id,
                            onClick = { selectedCategory = id },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Message input
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 2000) message = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { 
                         Text(
                             text = when (selectedCategory) {
                                 "feature" -> "Describe the feature you'd like to see..."
                                 "bug" -> "What happened and how can we reproduce it?"
                                 else -> "Tell us what's on your mind..."
                             },
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                         ) 
                    },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                
                // Character count
                Text(
                    text = "${message.length}/2000",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.End
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when (selectedCategory) {
                        "feature" -> "Mind sharing your email? We'd love to follow up and chat more about your idea."
                        "bug" -> "Mind sharing your email? We may need a few more details to help squash this bug."
                        else -> "Mind sharing your email? We really appreciate the feedback and may reach out."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.labelMedium.lineHeight
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Email input (optional)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    placeholder = { 
                         Text(
                             text = "you@example.com (optional)",
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                         ) 
                    },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Feedback is 100% anonymous — no account info is shared.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Submit button
                Button(
                    onClick = {
                        if (message.isBlank()) {
                            errorMsg = "Please write a message first"
                            return@Button
                        }
                        
                        isSubmitting = true
                        errorMsg = null
                        
                        scope.launch {
                            val success = onSubmit(selectedCategory, message.trim(), appVersion, email.trim())
                            if (success) {
                                isSuccess = true
                            } else {
                                errorMsg = "Couldn't send right now. Please try again."
                                isSubmitting = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSubmitting && message.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending...", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("Send feedback", style = MaterialTheme.typography.labelLarge)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onRateInstead,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rate on Google Play", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
