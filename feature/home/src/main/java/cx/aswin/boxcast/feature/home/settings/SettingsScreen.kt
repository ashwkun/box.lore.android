package cx.aswin.boxcast.feature.home.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxcast.core.data.RssPodcastRepository
import cx.aswin.boxcast.core.data.RssSubscriptionResult
import cx.aswin.boxcast.core.data.analytics.AnalyticsHelper
import cx.aswin.boxcast.feature.home.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxcast.feature.home.settings.dialogs.RssMatchConfirmationDialog
import cx.aswin.boxcast.feature.home.settings.dialogs.ResetAnalyticsDialog
import cx.aswin.boxcast.feature.home.settings.pages.AboutSettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.AppearanceSettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.DownloadsSettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.LibrarySettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.PlaybackSettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.PrivacySettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.SettingsHub
import java.io.IOException
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentRegion: String = "us",
    onSetRegion: (String) -> Unit = {},
    onBack: () -> Unit,
    onResetAnalytics: () -> Unit,
    appInstanceId: String? = null,
    currentThemeConfig: String = "system",
    isDynamicColorEnabled: Boolean = true,
    currentThemeBrand: String = "violet",
    onSetThemeConfig: (String) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onSetThemeBrand: (String) -> Unit = {},
    currentSurfaceStyle: String = "standard",
    onSetSurfaceStyle: (String) -> Unit = {},
    onExportJson: (Uri) -> Unit = {},
    onExportOpml: (Uri) -> Unit = {},
    onImportJson: (Uri) -> Unit = {},
    onImportOpml: (Uri) -> Unit = {},
    skipBehavior: String = "just_skip",
    onSetSkipBehavior: (String) -> Unit = {},
    hideCompletedInHome: Boolean = true,
    onSetHideCompletedInHome: (Boolean) -> Unit = {},
    hideCompletedInSubs: Boolean = true,
    onSetHideCompletedInSubs: (Boolean) -> Unit = {},
    hideCompletedInShowDetails: Boolean = false,
    onSetHideCompletedInShowDetails: (Boolean) -> Unit = {},
    onNavigateToSmartDownloads: () -> Unit = {},
    onNavigateToAutoDownloads: () -> Unit = {},
    /** Optional deep-link page: "library", "appearance", etc. */
    initialPage: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var destination by rememberSaveable {
        mutableStateOf(initialPage.toSettingsDestination())
    }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showAddRssDialog by rememberSaveable { mutableStateOf(false) }
    var rssUrl by rememberSaveable { mutableStateOf("") }
    var rssError by rememberSaveable { mutableStateOf<String?>(null) }
    var isAddingRss by remember { mutableStateOf(false) }
    var pendingRssMatch by remember { mutableStateOf<RssSubscriptionResult?>(null) }
    var isLinkingRssMatch by remember { mutableStateOf(false) }
    var isDeletionExpanded by rememberSaveable { mutableStateOf(false) }
    var analyticsIdVersion by remember { mutableIntStateOf(0) }

    val deletionId = remember(appInstanceId, analyticsIdVersion) {
        AnalyticsHelper.getDistinctId().ifBlank { appInstanceId.orEmpty() }
    }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "Not available" }
    }
    val versionCode = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(onExportJson) },
    )
    val exportOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml"),
        onResult = { uri -> uri?.let(onExportOpml) },
    )
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onImportJson) },
    )
    val importOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onImportOpml) },
    )

    LaunchedEffect(Unit) {
        AnalyticsHelper.trackSettingsScreenViewed("home_top_bar")
    }

    BackHandler(enabled = destination != ProfileSettingsDestination.Hub) {
        destination = ProfileSettingsDestination.Hub
    }

    val returnToHub = { destination = ProfileSettingsDestination.Hub }
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val enterFromRight = targetState != ProfileSettingsDestination.Hub
            val motionSpec = spring<IntOffset>(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow,
            )
            val enter = slideInHorizontally(
                animationSpec = motionSpec,
                initialOffsetX = { width -> if (enterFromRight) width / 3 else -width / 3 },
            ) + fadeIn()
            val exit = slideOutHorizontally(
                animationSpec = motionSpec,
                targetOffsetX = { width -> if (enterFromRight) -width / 4 else width / 4 },
            ) + fadeOut()
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "settings_destination",
    ) { currentDestination ->
        when (currentDestination) {
            ProfileSettingsDestination.Hub -> SettingsHub(
                onBack = onBack,
                onNavigate = { destination = it },
            )

            ProfileSettingsDestination.Library -> LibrarySettingsPage(
                currentRegion = currentRegion,
                onSetRegion = {
                    AnalyticsHelper.trackSettingsInteraction("content_region_changed", it)
                    onSetRegion(it)
                },
                onAddRssClick = { showAddRssDialog = true },
                onExportJson = {
                    AnalyticsHelper.trackSettingsInteraction("library_export")
                    exportJsonLauncher.launch("boxlore_backup_${System.currentTimeMillis()}.json")
                },
                onExportOpml = {
                    AnalyticsHelper.trackSettingsInteraction("library_export_opml")
                    exportOpmlLauncher.launch("boxlore_subscriptions_${System.currentTimeMillis()}.opml")
                },
                onImportJson = {
                    AnalyticsHelper.trackSettingsInteraction("library_import_json")
                    importJsonLauncher.launch(arrayOf("application/json"))
                },
                onImportOpml = {
                    AnalyticsHelper.trackSettingsInteraction("library_import_opml")
                    importOpmlLauncher.launch(arrayOf("*/*"))
                },
                onBack = returnToHub,
            )

            ProfileSettingsDestination.Appearance -> AppearanceSettingsPage(
                currentThemeConfig = currentThemeConfig,
                onSetThemeConfig = {
                    AnalyticsHelper.trackSettingsInteraction("theme_mode_changed", it)
                    onSetThemeConfig(it)
                },
                isDynamicColorEnabled = isDynamicColorEnabled,
                onToggleDynamicColor = {
                    AnalyticsHelper.trackSettingsInteraction("dynamic_color_toggled", it.toString())
                    onToggleDynamicColor(it)
                },
                currentThemeBrand = currentThemeBrand,
                onSetThemeBrand = {
                    AnalyticsHelper.trackSettingsInteraction("theme_brand_changed", it)
                    onSetThemeBrand(it)
                },
                currentSurfaceStyle = currentSurfaceStyle,
                onSetSurfaceStyle = {
                    AnalyticsHelper.trackSettingsInteraction("surface_style_changed", it)
                    onSetSurfaceStyle(it)
                },
                onBack = returnToHub,
            )

            ProfileSettingsDestination.Playback -> PlaybackSettingsPage(
                skipBehavior = skipBehavior,
                onSetSkipBehavior = onSetSkipBehavior,
                hideCompletedInHome = hideCompletedInHome,
                onSetHideCompletedInHome = onSetHideCompletedInHome,
                hideCompletedInSubs = hideCompletedInSubs,
                onSetHideCompletedInSubs = onSetHideCompletedInSubs,
                hideCompletedInShowDetails = hideCompletedInShowDetails,
                onSetHideCompletedInShowDetails = onSetHideCompletedInShowDetails,
                onBack = returnToHub,
            )

            ProfileSettingsDestination.Downloads -> DownloadsSettingsPage(
                onSmartDownloadsClick = onNavigateToSmartDownloads,
                onAutoDownloadsClick = onNavigateToAutoDownloads,
                onBack = returnToHub,
            )

            ProfileSettingsDestination.Privacy -> PrivacySettingsPage(
                deletionId = deletionId,
                isDeletionExpanded = isDeletionExpanded,
                onDeletionExpandedChange = { isDeletionExpanded = it },
                onResetIdentityClick = { showResetDialog = true },
                onCopyDeletionId = {
                    AnalyticsHelper.trackSettingsInteraction("delete_id_copied")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Anonymous analytics ID", deletionId),
                    )
                    Toast.makeText(context, "Analytics ID copied", Toast.LENGTH_SHORT).show()
                },
                onEmailDeletionRequest = {
                    AnalyticsHelper.trackSettingsInteraction("delete_email_clicked")
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("support@aswin.cx"))
                        putExtra(Intent.EXTRA_SUBJECT, "Analytics data deletion request")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Please delete PostHog analytics data associated with this distinct ID: $deletionId",
                        )
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            Toast.makeText(context, "No email app is available", Toast.LENGTH_SHORT).show()
                        }
                },
                onBack = returnToHub,
            )

            ProfileSettingsDestination.About -> AboutSettingsPage(
                versionName = versionName,
                versionCode = versionCode,
                packageName = context.packageName,
                androidRelease = android.os.Build.VERSION.RELEASE.orEmpty().ifBlank { "?" },
                sdkInt = android.os.Build.VERSION.SDK_INT,
                onVisitPodcastIndex = {
                    AnalyticsHelper.trackSettingsInteraction("podcast_index_homepage_clicked")
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://podcastindex.org")),
                        )
                    }
                },
                onOpenChangelog = {
                    AnalyticsHelper.trackSettingsInteraction("changelog_clicked")
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/ashwkun/boxlore/blob/master/CHANGELOG.md"),
                            ),
                        )
                    }
                },
                onBack = returnToHub,
            )
        }
    }

    if (showAddRssDialog) {
        AddRssFeedDialog(
            url = rssUrl,
            error = rssError,
            isAdding = isAddingRss,
            onUrlChange = {
                rssUrl = it
                rssError = null
            },
            onDismiss = {
                if (!isAddingRss) {
                    showAddRssDialog = false
                    rssError = null
                }
            },
            onConfirm = {
                isAddingRss = true
                rssError = null
                scope.launch {
                    runCatching {
                        RssPodcastRepository.getInstance(context).addSubscription(rssUrl)
                    }.onSuccess { subscription ->
                        showAddRssDialog = false
                        rssUrl = ""
                        if (subscription.potentialPodcastIndexMatch != null) {
                            pendingRssMatch = subscription
                        } else {
                            val message = when {
                                subscription.linkedPodcastIndexId != null ->
                                    "Switched ${subscription.podcast.title} to its RSS source."
                                subscription.automaticUpdateChecksSupported ->
                                    "Added ${subscription.podcast.title} (${subscription.episodeCount} episodes)."
                                else ->
                                    "Added ${subscription.podcast.title}. To check for new episodes, open the podcast and refresh."
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }.onFailure { error ->
                        rssError = error.toRssErrorMessage()
                    }
                    isAddingRss = false
                }
            },
        )
    }

    pendingRssMatch?.let { subscription ->
        val podcastIndexMatch = subscription.potentialPodcastIndexMatch ?: return@let
        RssMatchConfirmationDialog(
            rssTitle = subscription.podcast.title,
            podcastIndexTitle = podcastIndexMatch.title,
            isLinking = isLinkingRssMatch,
            onUseRssSource = {
                isLinkingRssMatch = true
                scope.launch {
                    runCatching {
                        RssPodcastRepository.getInstance(context).confirmPodcastIndexLink(
                            rssPodcastId = subscription.podcast.id,
                            podcastIndexId = podcastIndexMatch.id,
                        )
                    }.onSuccess {
                        Toast.makeText(
                            context,
                            "Using the RSS source for ${subscription.podcast.title}.",
                            Toast.LENGTH_LONG,
                        ).show()
                        pendingRssMatch = null
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            error.toRssErrorMessage(),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    isLinkingRssMatch = false
                }
            },
            onKeepSeparate = {
                pendingRssMatch = null
                Toast.makeText(
                    context,
                    "Kept both subscriptions separate.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    if (showResetDialog) {
        ResetAnalyticsDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = {
                AnalyticsHelper.trackSettingsInteraction("analytics_reset")
                onResetAnalytics()
                analyticsIdVersion++
                showResetDialog = false
            },
        )
    }
}

private fun Throwable.toRssErrorMessage(): String = when (this) {
    is IllegalArgumentException ->
        "Check that this is a valid HTTPS podcast RSS feed."
    is IOException ->
        "The RSS feed could not be downloaded. Check your connection and try again."
    else ->
        "We couldn't add this RSS feed."
}

private fun String?.toSettingsDestination(): ProfileSettingsDestination = when (this?.trim()?.lowercase()) {
    "library" -> ProfileSettingsDestination.Library
    "appearance" -> ProfileSettingsDestination.Appearance
    "playback" -> ProfileSettingsDestination.Playback
    "downloads" -> ProfileSettingsDestination.Downloads
    "privacy" -> ProfileSettingsDestination.Privacy
    "about" -> ProfileSettingsDestination.About
    else -> ProfileSettingsDestination.Hub
}
