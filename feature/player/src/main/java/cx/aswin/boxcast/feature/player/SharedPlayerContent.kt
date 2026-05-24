package cx.aswin.boxcast.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.Toc
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Person
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.Chapter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import cx.aswin.boxcast.core.designsystem.components.AdvancedPlayerControls
import cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState
import cx.aswin.boxcast.feature.player.components.SimplePlayerControls
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

@Composable
fun SharedPlayerContent(
    podcast: Podcast,
    episode: Episode?, // Nullable
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    playbackSpeed: Float,
    sleepTimerEnd: Long?,
    isLiked: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkipPreviousEpisode: () -> Unit = {},
    onSkipNextEpisode: () -> Unit = {},
    onSetSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onQueueClick: () -> Unit,
    onEpisodeInfoClick: () -> Unit = {},
    onPodcastInfoClick: () -> Unit = {},
    showTitleTip: Boolean = false,
    onTitleTipDismissed: () -> Unit = {},
    isExpanded: Boolean = true,
    chapters: List<cx.aswin.boxcast.core.model.Chapter> = emptyList(),
    transcript: List<cx.aswin.boxcast.core.data.TranscriptSegment> = emptyList(),
    onChaptersClick: () -> Unit = {},
    onFullscreenTranscriptClick: () -> Unit = {},
    isChaptersLoading: Boolean = false,
    autoTranscriptState: AutoTranscriptState = AutoTranscriptState.NONE,
    autoChaptersState: AutoTranscriptState = AutoTranscriptState.NONE,
    autoTranscriptLimitLeft: Int? = null,
    onGenerateTranscript: () -> Unit = {},
    isSyncEnabled: Boolean = true,
    onSyncEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    extraContent: @Composable () -> Unit = {},
    footerContent: @Composable ColumnScope.() -> Unit = {},
    controller: androidx.media3.common.Player? = null
) {
    val controlTint = colorScheme.primary
    val context = LocalContext.current
    var showTranscript by remember(episode?.id) { mutableStateOf(false) }
    var showGenerateConfirmation by remember { mutableStateOf(false) }
    
    MaterialTheme(colorScheme = colorScheme) {

        BoxWithConstraints(
            modifier = modifier.fillMaxSize()
        ) {
            // Responsive breakpoints based on available height
            val isCompact = maxHeight < 600.dp
            
            // Calculate exact safe size for artwork to maximize space without clipping
            val controlsEstimatedHeight = if (isCompact) 300.dp else 350.dp
            val availableHeightForArtwork = maxHeight - controlsEstimatedHeight
            
            // Maximize artwork up to 85% of screen width, bounded by available vertical space 
            val optimalArtworkSize = androidx.compose.ui.unit.min(maxWidth * 0.85f, availableHeightForArtwork).coerceAtLeast(150.dp)
            
            // Control sizing for PlayerControls
            val controlRowHeight = if (isCompact) 64.dp else 80.dp
            val actionButtonSize = if (isCompact) 40.dp else 48.dp
            val spacingSmall = if (isCompact) 8.dp else 16.dp
            
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                
                AnimatedContent(
                    targetState = showTranscript,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically { height -> height / 12 } + fadeIn(animationSpec = tween(350))) togetherWith 
                            (scaleOut(targetScale = 0.96f) + fadeOut(animationSpec = tween(250)))
                        } else {
                            (scaleIn(initialScale = 0.96f) + fadeIn(animationSpec = tween(350))) togetherWith 
                            (slideOutVertically { height -> height / 12 } + fadeOut(animationSpec = tween(250)))
                        }
                    },
                    label = "transcriptToggleTransition",
                    modifier = if (showTranscript) {
                        Modifier.fillMaxWidth().weight(1f)
                    } else {
                        Modifier.fillMaxWidth().wrapContentHeight()
                    }
                ) { isTranscriptVisible ->
                    if (isTranscriptVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp)
                        ) {
                            TranscriptView(
                                transcript = transcript,
                                positionMs = positionMs,
                                colorScheme = colorScheme,
                                onSeek = { seekPos ->
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.setSeekSource("transcript_tap")
                                    onSeek(seekPos)
                                },
                                isSyncEnabled = isSyncEnabled,
                                onSyncEnabledChange = onSyncEnabledChange,
                                modifier = Modifier.fillMaxSize(),
                                transcriptUrl = episode?.transcriptUrl
                            )
                            
                            // Buttons row on top-right of the transcript view
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Fullscreen Button
                                IconButton(
                                    onClick = onFullscreenTranscriptClick,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomOutMap,
                                        contentDescription = "Fullscreen Transcript",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Spacer(modifier = Modifier.height(spacingSmall))
                            // 1. Album Art - responsive sizing
                            val activeChapter = remember(chapters, positionMs) {
                                chapters.lastOrNull { (it.startTime * 1000).toLong() <= positionMs }
                              }
                              val artworkUrl = activeChapter?.img?.takeIf { it.isNotBlank() } ?: episode?.imageUrl?.takeIf { it.isNotBlank() } ?: podcast.imageUrl
                              
                              Surface(
                                  modifier = Modifier
                                      .size(optimalArtworkSize)
                                      .shadow(
                                          12.dp, 
                                          RoundedCornerShape(28.dp), 
                                          ambientColor = controlTint.copy(alpha = 0.3f), 
                                          spotColor = controlTint.copy(alpha = 0.5f)
                                      ),
                                  shape = RoundedCornerShape(28.dp),
                                  color = colorScheme.surfaceVariant
                              ) {
                                  if (episode?.enclosureType?.startsWith("video/") == true && controller != null) {
                                      androidx.compose.ui.viewinterop.AndroidView(
                                          factory = { ctx ->
                                              androidx.media3.ui.PlayerView(ctx).apply {
                                                  player = controller
                                                  useController = false // Use BoxCast controls instead of default overlay
                                                  resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                              }
                                          },
                                          update = { playerView ->
                                              playerView.player = controller
                                          },
                                          modifier = Modifier
                                              .fillMaxSize()
                                              .pointerInput(isExpanded) {
                                                  if (!isExpanded) return@pointerInput
                                                  
                                                  var totalDrag = 0f
                                                  detectHorizontalDragGestures(
                                                      onDragEnd = {
                                                          if (totalDrag > 100f) {
                                                              onSkipPreviousEpisode()
                                                          } else if (totalDrag < -100f) {
                                                              onSkipNextEpisode()
                                                          }
                                                          totalDrag = 0f
                                                      }
                                                  ) { change, dragAmount ->
                                                      change.consume()
                                                      totalDrag += dragAmount
                                                  }
                                              }
                                      )
                                  } else {
                                      AsyncImage(
                                          model = ImageRequest.Builder(context)
                                              .data(artworkUrl)
                                              .crossfade(true)
                                              .allowHardware(false) // Required for Palette if needed, safe to keep
                                              .build(),
                                          contentDescription = "Album Art",
                                          contentScale = ContentScale.Crop,
                                          modifier = Modifier
                                              .fillMaxSize()
                                              .pointerInput(isExpanded) {
                                                  if (!isExpanded) return@pointerInput
                                                  
                                                  var totalDrag = 0f
                                                  detectHorizontalDragGestures(
                                                      onDragEnd = {
                                                          if (totalDrag > 100f) {
                                                              onSkipPreviousEpisode() // Swipe Right -> Prev Episode
                                                          } else if (totalDrag < -100f) {
                                                              onSkipNextEpisode() // Swipe Left -> Next Episode
                                                          }
                                                          totalDrag = 0f
                                                      }
                                                  ) { change, dragAmount ->
                                                      change.consume()
                                                      totalDrag += dragAmount
                                                  }
                                              }
                                      )
                                  }
                              }
                              
                              Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
                          }
                      }
                  }
                  
                  Spacer(modifier = Modifier.weight(0.02f))
        
            // 2. Metadata - removed fixed height for responsiveness
            Column(
                modifier = Modifier.fillMaxWidth(), 
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = (episode?.title ?: podcast.title).replace("+", " "),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (episode != null) onEpisodeInfoClick() }
                        .basicMarquee()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (episode != null) podcast.title.replace("+", " ") else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPodcastInfoClick() }
                        .basicMarquee()
                )
                
                // One-time title tap tip
                if (showTitleTip) {
                    var tipVisible by remember { mutableStateOf(true) }
                    
                    LaunchedEffect(isExpanded) {
                        if (isExpanded) {
                            delay(3500)
                            tipVisible = false
                            onTitleTipDismissed()
                        }
                    }  
                    AnimatedVisibility(
                        visible = tipVisible,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(500))
                    ) {
                        Text(
                            text = "Tap title for episode details",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(0.015f))
            
            // 3. Linear Buffered Slider
            if (durationMs > 0) {
                val bufferedPercentage = (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                
                LinearBufferedSlider(
                    position = positionMs,
                    duration = durationMs,
                    bufferedPercentage = bufferedPercentage,
                    onSeek = onSeek,
                    color = controlTint,
                    chapters = chapters
                )
            }
            
            Spacer(modifier = Modifier.weight(0.02f))
            
            // Slot for Visualizer or other content
            extraContent()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 4. Player Controls (Play/Pause/Skip)
            PlayerControls(
                isPlaying = isPlaying,
                isLoading = isLoading,
                colorScheme = colorScheme,
                controlTint = controlTint,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                height = controlRowHeight
            )
            
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))
            
            // 5. Controls Section
            // Row 1: Playback Modifiers (Speed, Timer)
            SimplePlayerControls(
                 playbackSpeed = playbackSpeed,
                 sleepTimerEnd = sleepTimerEnd,
                 duration = durationMs, // Pass duration
                 position = positionMs, // Pass position
                 colorScheme = colorScheme,
                 onSpeedChange = onSetSpeed,
                 onSleepClick = onSetSleepTimer,
                 modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 18.dp))
            
            // Row 2: Actions (Like, Download, Queue, Chapters, Transcript)
            AdvancedPlayerControls(
                 isLiked = isLiked, 
                 isDownloaded = isDownloaded,
                 isDownloading = isDownloading,
                 colorScheme = colorScheme,
                 onLikeClick = onLikeClick,
                 onDownloadClick = onDownloadClick,
                 onQueueClick = onQueueClick,
                 hasChapters = !episode?.chaptersUrl.isNullOrEmpty() || chapters.isNotEmpty(),
                 isChaptersLoading = isChaptersLoading,
                 autoTranscriptState = autoTranscriptState,
                 autoChaptersState = autoChaptersState,
                 isTranscriptActive = showTranscript,
                 onChaptersClick = onChaptersClick,
                 onTranscriptClick = {
                     if (transcript.isNotEmpty()) {
                         showTranscript = !showTranscript
                         if (showTranscript) {
                             cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("transcript_view")
                         }
                     } else {
                         when (autoTranscriptState) {
                             AutoTranscriptState.NOT_GENERATED, AutoTranscriptState.FAILED -> {
                                 showGenerateConfirmation = true
                             }
                             AutoTranscriptState.NONE, AutoTranscriptState.COMPLETED -> {
                                 showTranscript = !showTranscript
                                 if (showTranscript) {
                                     cx.aswin.boxcast.core.data.analytics.PlayerSessionAggregator.logAction("transcript_view")
                                 }
                             }
                             else -> { /* CHECKING/GENERATING — do nothing */ }
                         }
                     }
                 },
                 style = cx.aswin.boxcast.core.designsystem.components.ControlStyle.Squircle,
                 controlSize = actionButtonSize,
                 modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

            
            // Footer (e.g. Up Next List)
            footerContent()
            }
        }
    }

    // AI Transcript Generation Confirmation Dialog
    if (showGenerateConfirmation) {
        val estimatedTime = remember(episode?.duration, durationMs) {
            val durationSec = if (durationMs > 0) {
                durationMs / 1000
            } else {
                (episode?.duration ?: 0).toLong()
            }
            when {
                durationSec <= 0 -> "~1-2 min"
                durationSec < 600 -> "~30s"
                durationSec < 1800 -> "~1 min"
                durationSec < 3600 -> "~1-2 min"
                else -> "~2-3 min"
            }
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showGenerateConfirmation = false }
        ) {
            Surface(
                shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 28.dp, smoothnessAsPercentTL = 60,
                    cornerRadiusTR = 28.dp, smoothnessAsPercentTR = 60,
                    cornerRadiusBL = 28.dp, smoothnessAsPercentBL = 60,
                    cornerRadiusBR = 28.dp, smoothnessAsPercentBR = 60
                ),
                color = colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon with tinted background circle
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = colorScheme.tertiaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        "Generate Transcript",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    Text(
                        if (autoTranscriptLimitLeft == 0) {
                            "Daily AI limit reached. Please try again tomorrow."
                        } else {
                            "AI transcription is in beta and may contain errors."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (autoTranscriptLimitLeft == 0) colorScheme.error else colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Estimated time pill
                        Surface(
                            shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 12.dp, smoothnessAsPercentTL = 60,
                                cornerRadiusTR = 12.dp, smoothnessAsPercentTR = 60,
                                cornerRadiusBL = 12.dp, smoothnessAsPercentBL = 60,
                                cornerRadiusBR = 12.dp, smoothnessAsPercentBR = 60
                            ),
                            color = colorScheme.surfaceContainerHighest
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Est. $estimatedTime",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Credits remaining pill
                        if (autoTranscriptLimitLeft != null) {
                            Surface(
                                shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(
                                    cornerRadiusTL = 12.dp, smoothnessAsPercentTL = 60,
                                    cornerRadiusTR = 12.dp, smoothnessAsPercentTR = 60,
                                    cornerRadiusBL = 12.dp, smoothnessAsPercentBL = 60,
                                    cornerRadiusBR = 12.dp, smoothnessAsPercentBR = 60
                                ),
                                color = if (autoTranscriptLimitLeft == 0) colorScheme.errorContainer else colorScheme.tertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (autoTranscriptLimitLeft == 0) colorScheme.onErrorContainer else colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (autoTranscriptLimitLeft == 0) "0 left for the day" else "$autoTranscriptLimitLeft left for the day",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (autoTranscriptLimitLeft == 0) colorScheme.onErrorContainer else colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    val canGenerate = autoTranscriptLimitLeft == null || autoTranscriptLimitLeft > 0
                    Button(
                        enabled = canGenerate,
                        onClick = {
                            showGenerateConfirmation = false
                            onGenerateTranscript()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 14.dp, smoothnessAsPercentTL = 60,
                            cornerRadiusTR = 14.dp, smoothnessAsPercentTR = 60,
                            cornerRadiusBL = 14.dp, smoothnessAsPercentBL = 60,
                            cornerRadiusBR = 14.dp, smoothnessAsPercentBR = 60
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.12f),
                            disabledContentColor = colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Text(
                            if (canGenerate) "Generate" else "Limit Reached",
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showGenerateConfirmation = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Cancel",
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LinearBufferedSlider(
    position: Long,
    duration: Long,
    bufferedPercentage: Float,
    onSeek: (Long) -> Unit,
    color: Color,
    chapters: List<Chapter> = emptyList()
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Floating seek/chapter preview pill
        AnimatedVisibility(
            visible = dragValue != null,
            enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(250)) + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            dragValue?.let { value ->
                val seekTime = value.toLong()
                val matchingChapter = chapters.lastOrNull { (it.startTime * 1000).toLong() <= seekTime }
                val previewText = if (matchingChapter != null) {
                    "${formatTime(seekTime)} • ${matchingChapter.title}"
                } else {
                    formatTime(seekTime)
                }
                
                Surface(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .wrapContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // M3 Slider with buffer and notch visualization
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp), 
            contentAlignment = Alignment.Center
        ) {
            // 1. Buffer track (behind)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.CenterStart
            ) {
                // Buffered portion
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedPercentage)
                        .fillMaxHeight()
                        .background(
                            color.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
            
            // 2. Slider (middle - draws native thick active M3 track and thumb)
            Slider(
                value = dragValue ?: position.toFloat(),
                onValueChange = { 
                    dragValue = it
                    onSeek(it.toLong())
                },
                onValueChangeFinished = {
                    dragValue = null
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = Color.Transparent
                )
            )

            // 3. Chapter Ticks / Differentiators (on top of Slider track)
            if (chapters.isNotEmpty() && duration > 0) {
                val tickColor = MaterialTheme.colorScheme.surface
                val thumbTime = dragValue ?: position.toFloat()
                val thumbPct = thumbTime / duration.toFloat().coerceAtLeast(1f)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        chapters.forEach { chapter ->
                            val startTimeMs = (chapter.startTime * 1000).toLong()
                            if (startTimeMs > 0 && startTimeMs < duration) {
                                val pct = startTimeMs.toFloat() / duration.toFloat()
                                
                                // Skip drawing if notch is under or very close to active seek thumb
                                if (Math.abs(pct - thumbPct) > 0.015f) {
                                    val x = size.width * pct
                                    drawLine(
                                        color = tickColor,
                                        start = androidx.compose.ui.geometry.Offset(x = x, y = 0f),
                                        end = androidx.compose.ui.geometry.Offset(x = x, y = size.height),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = color.copy(alpha = 0.85f)
            )
            Text(
                text = "-" + formatTime((duration - position).coerceAtLeast(0)),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = color.copy(alpha = 0.85f)
            )
        }
    }
}
