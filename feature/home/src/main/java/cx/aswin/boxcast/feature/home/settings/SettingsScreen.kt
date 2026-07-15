package cx.aswin.boxcast.feature.home.settings

import android.app.Application
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
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxcast.core.data.analytics.AnalyticsHelper
import cx.aswin.boxcast.feature.home.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxcast.feature.home.settings.dialogs.ResetAnalyticsDialog
import cx.aswin.boxcast.feature.home.settings.dialogs.RssMatchConfirmationDialog
import cx.aswin.boxcast.feature.home.settings.pages.AboutSettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.AppInfo
import cx.aswin.boxcast.feature.home.settings.pages.AppearanceActions
import cx.aswin.boxcast.feature.home.settings.pages.AppearanceSettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.AppearanceUiState
import cx.aswin.boxcast.feature.home.settings.pages.DownloadsSettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.LibraryBackupActions
import cx.aswin.boxcast.feature.home.settings.pages.LibrarySettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.PlaybackActions
import cx.aswin.boxcast.feature.home.settings.pages.PlaybackSettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.PlaybackUiState
import cx.aswin.boxcast.feature.home.settings.pages.PrivacySettingsPage
import cx.aswin.boxcast.feature.home.settings.pages.SettingsHub

/** Where to send the user for the two Downloads settings sub-screens. */
data class DownloadsNavigation(
    val onNavigateToSmartDownloads: () -> Unit = {},
    val onNavigateToAutoDownloads: () -> Unit = {},
)

/**
 * Uri-based read/write callbacks for library export/import. [SettingsScreen] wires these to the
 * platform file pickers and hands [LibrarySettingsPage] the resulting no-arg "open picker"
 * triggers (see [trackedLibraryBackupActions]).
 */
data class LibraryBackupWriters(
    val onExportJson: (Uri) -> Unit = {},
    val onExportOpml: (Uri) -> Unit = {},
    val onImportJson: (Uri) -> Unit = {},
    val onImportOpml: (Uri) -> Unit = {},
)

/** Content-region selector state/action, surfaced from the Library settings sub-page. */
data class RegionSettings(
    val currentRegion: String = "us",
    val onSetRegion: (String) -> Unit = {},
)

/** [SettingsScreen]'s top-level identifiers/callbacks that aren't tied to a specific sub-page. */
data class SettingsScreenConfig(
    val onBack: () -> Unit,
    val onResetAnalytics: () -> Unit,
    val appInstanceId: String? = null,
    /** Optional deep-link page: "library", "appearance", etc. */
    val initialPage: String? = null,
)

/** Appearance sub-page state paired with its actions, so [SettingsScreen] can pass both as one. */
data class AppearanceSettings(
    val state: AppearanceUiState = AppearanceUiState(
        currentThemeConfig = "system",
        isDynamicColorEnabled = true,
        currentThemeBrand = "violet",
        currentSurfaceStyle = "standard",
    ),
    val actions: AppearanceActions = AppearanceActions({}, {}, {}, {}),
)

/** Playback sub-page state paired with its actions, so [SettingsScreen] can pass both as one. */
data class PlaybackSettings(
    val state: PlaybackUiState = PlaybackUiState(
        skipBehavior = "just_skip",
        hideCompletedInHome = true,
        hideCompletedInSubs = true,
        hideCompletedInShowDetails = false,
    ),
    val actions: PlaybackActions = PlaybackActions({}, {}, {}, {}),
)

@Composable
fun SettingsScreen(
    config: SettingsScreenConfig,
    regionSettings: RegionSettings = RegionSettings(),
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    libraryBackupWriters: LibraryBackupWriters = LibraryBackupWriters(),
    downloadsNavigation: DownloadsNavigation = DownloadsNavigation(),
) {
    val currentRegion = regionSettings.currentRegion
    val onSetRegion = regionSettings.onSetRegion
    val onBack = config.onBack
    val onResetAnalytics = config.onResetAnalytics
    val appInstanceId = config.appInstanceId
    val initialPage = config.initialPage
    val appearanceState = appearanceSettings.state
    val appearanceActions = appearanceSettings.actions
    val playbackState = playbackSettings.state
    val playbackActions = playbackSettings.actions
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(application) as T
            }
        },
    )
    val rssState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var destination by rememberSaveable {
        mutableStateOf(initialPage.toSettingsDestination())
    }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var isDeletionExpanded by rememberSaveable { mutableStateOf(false) }
    var analyticsIdVersion by remember { mutableIntStateOf(0) }

    val deletionId = remember(appInstanceId, analyticsIdVersion) {
        AnalyticsHelper.getDistinctId().ifBlank { appInstanceId.orEmpty() }
    }
    val appInfo = rememberAppInfo(context)

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(libraryBackupWriters.onExportJson) },
    )
    val exportOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml"),
        onResult = { uri -> uri?.let(libraryBackupWriters.onExportOpml) },
    )
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(libraryBackupWriters.onImportJson) },
    )
    val importOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(libraryBackupWriters.onImportOpml) },
    )

    LaunchedEffect(Unit) {
        AnalyticsHelper.trackSettingsScreenViewed("home_top_bar")
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = destination != ProfileSettingsDestination.Hub) {
        destination = ProfileSettingsDestination.Hub
    }

    val returnToHub = { destination = ProfileSettingsDestination.Hub }
    AnimatedContent(
        targetState = destination,
        transitionSpec = { settingsDestinationTransitionSpec() },
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
                onAddRssClick = { settingsViewModel.openAddRssDialog() },
                backupActions = trackedLibraryBackupActions(
                    exportJsonLauncher, exportOpmlLauncher, importJsonLauncher, importOpmlLauncher,
                ),
                onBack = returnToHub,
            )

            ProfileSettingsDestination.Appearance -> AppearanceSettingsPage(
                state = appearanceState,
                actions = appearanceActions.trackedForAnalytics(),
                onBack = returnToHub,
            )

            ProfileSettingsDestination.Playback -> PlaybackSettingsPage(
                state = playbackState,
                actions = playbackActions,
                onBack = returnToHub,
            )

            ProfileSettingsDestination.Downloads -> DownloadsSettingsPage(
                onSmartDownloadsClick = downloadsNavigation.onNavigateToSmartDownloads,
                onAutoDownloadsClick = downloadsNavigation.onNavigateToAutoDownloads,
                onBack = returnToHub,
            )

            ProfileSettingsDestination.Privacy -> PrivacySettingsPage(
                deletionId = deletionId,
                isDeletionExpanded = isDeletionExpanded,
                onDeletionExpandedChange = { isDeletionExpanded = it },
                onResetIdentityClick = { showResetDialog = true },
                onCopyDeletionId = { copyDeletionId(context, deletionId) },
                onEmailDeletionRequest = { requestAnalyticsDeletionByEmail(context, deletionId) },
                onBack = returnToHub,
            )

            ProfileSettingsDestination.About -> AboutSettingsPage(
                appInfo = appInfo,
                onVisitPodcastIndex = { visitPodcastIndexHomepage(context) },
                onOpenChangelog = { openChangelog(context) },
                onBack = returnToHub,
            )
        }
    }

    if (rssState.showAddRssDialog) {
        AddRssFeedDialog(
            url = rssState.rssUrl,
            error = rssState.rssError,
            isAdding = rssState.isAddingRss,
            onUrlChange = settingsViewModel::onRssUrlChange,
            onDismiss = settingsViewModel::dismissAddRssDialog,
            onConfirm = settingsViewModel::addSubscription,
        )
    }

    rssState.pendingRssMatch?.let { subscription ->
        val podcastIndexMatch = subscription.potentialPodcastIndexMatch ?: return@let
        RssMatchConfirmationDialog(
            rssTitle = subscription.podcast.title,
            podcastIndexTitle = podcastIndexMatch.title,
            isLinking = rssState.isLinkingRssMatch,
            onUseRssSource = settingsViewModel::confirmPodcastIndexLink,
            onKeepSeparate = settingsViewModel::keepRssMatchSeparate,
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

private fun AnimatedContentTransitionScope<ProfileSettingsDestination>.settingsDestinationTransitionSpec(): ContentTransform {
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
    return (enter togetherWith exit).using(SizeTransform(clip = false))
}

/** Builds the no-arg "open picker" triggers [LibrarySettingsPage] shows, with analytics tracking. */
private fun trackedLibraryBackupActions(
    exportJsonLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    exportOpmlLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    importJsonLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    importOpmlLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
): LibraryBackupActions = LibraryBackupActions(
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
)

/** Wraps the appearance callbacks with their analytics tracking, without changing behavior. */
private fun AppearanceActions.trackedForAnalytics(): AppearanceActions = AppearanceActions(
    onSetThemeConfig = {
        AnalyticsHelper.trackSettingsInteraction("theme_mode_changed", it)
        onSetThemeConfig(it)
    },
    onToggleDynamicColor = {
        AnalyticsHelper.trackSettingsInteraction("dynamic_color_toggled", it.toString())
        onToggleDynamicColor(it)
    },
    onSetThemeBrand = {
        AnalyticsHelper.trackSettingsInteraction("theme_brand_changed", it)
        onSetThemeBrand(it)
    },
    onSetSurfaceStyle = {
        AnalyticsHelper.trackSettingsInteraction("surface_style_changed", it)
        onSetSurfaceStyle(it)
    },
)

@Composable
private fun rememberAppInfo(context: Context): AppInfo {
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
    return remember(versionName, versionCode) {
        AppInfo(
            versionName = versionName,
            versionCode = versionCode,
            packageName = context.packageName,
            androidRelease = android.os.Build.VERSION.RELEASE.orEmpty().ifBlank { "?" },
            sdkInt = android.os.Build.VERSION.SDK_INT,
        )
    }
}

private fun copyDeletionId(context: Context, deletionId: String) {
    AnalyticsHelper.trackSettingsInteraction("delete_id_copied")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Anonymous analytics ID", deletionId))
    Toast.makeText(context, "Analytics ID copied", Toast.LENGTH_SHORT).show()
}

private fun requestAnalyticsDeletionByEmail(context: Context, deletionId: String) {
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
}

private fun visitPodcastIndexHomepage(context: Context) {
    AnalyticsHelper.trackSettingsInteraction("podcast_index_homepage_clicked")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://podcastindex.org")))
    }
}

private fun openChangelog(context: Context) {
    AnalyticsHelper.trackSettingsInteraction("changelog_clicked")
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/ashwkun/boxlore/blob/master/CHANGELOG.md"),
            ),
        )
    }
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
