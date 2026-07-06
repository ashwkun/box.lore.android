package cx.aswin.boxcast.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.designsystem.R
import cx.aswin.boxcast.core.designsystem.theme.contrastColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
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
    onExportJson: (android.net.Uri) -> Unit = {},
    onExportOpml: (android.net.Uri) -> Unit = {},
    onImportJson: (android.net.Uri) -> Unit = {},
    onImportOpml: (android.net.Uri) -> Unit = {},
    skipBehavior: String = "just_skip",
    onSetSkipBehavior: (String) -> Unit = {},
    hideCompletedInHome: Boolean = true,
    onSetHideCompletedInHome: (Boolean) -> Unit = {},
    hideCompletedInSubs: Boolean = true,
    onSetHideCompletedInSubs: (Boolean) -> Unit = {},
    hideCompletedInShowDetails: Boolean = false,
    onSetHideCompletedInShowDetails: (Boolean) -> Unit = {},
    onNavigateToSmartDownloads: () -> Unit = {},
    onNavigateToAutoDownloads: () -> Unit = {}
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var isDeletionExpanded by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let { onExportJson(it) } }
    )
    val exportOpmlLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/x-opml"),
        onResult = { uri -> uri?.let { onExportOpml(it) } }
    )
    val importJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportJson(it) } }
    )
    val importOpmlLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportOpml(it) } }
    )

    LaunchedEffect(Unit) {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsScreenViewed("home_top_bar")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SectionCard("Library & Content", Icons.AutoMirrored.Rounded.LibraryBooks, isCollapsible = false) {
                    ContentLibrarySection(
                        currentRegion = currentRegion,
                        onSetRegion = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("content_region_changed", it)
                            onSetRegion(it) 
                        },
                        onExport = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("library_export")
                            exportJsonLauncher.launch("boxlore_backup_${System.currentTimeMillis()}.json") 
                        },
                        onExportOpml = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("library_export_opml")
                            exportOpmlLauncher.launch("boxlore_subscriptions_${System.currentTimeMillis()}.opml") 
                        },
                        onImportJson = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("library_import_json")
                            importJsonLauncher.launch(arrayOf("application/json")) 
                        },
                        onImportOpml = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("library_import_opml")
                            importOpmlLauncher.launch(arrayOf("*/*")) 
                        },
                        onSmartDownloadsClick = onNavigateToSmartDownloads,
                        onAutoDownloadsClick = onNavigateToAutoDownloads
                    )
                }
            }

            item {
                SectionCard("Appearance", Icons.Rounded.Palette, isCollapsible = true, initiallyExpanded = false) {
                    AppearanceSection(
                        currentThemeConfig = currentThemeConfig, 
                        onSetThemeConfig = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("theme_mode_changed", it)
                            onSetThemeConfig(it) 
                        }, 
                        isDynamicColorEnabled = isDynamicColorEnabled, 
                        onToggleDynamicColor = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("dynamic_color_toggled", it.toString())
                            onToggleDynamicColor(it) 
                        }, 
                        currentThemeBrand = currentThemeBrand, 
                        onSetThemeBrand = { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("theme_brand_changed", it)
                            onSetThemeBrand(it) 
                        },
                        currentSurfaceStyle = currentSurfaceStyle,
                        onSetSurfaceStyle = {
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("surface_style_changed", it)
                            onSetSurfaceStyle(it)
                        }
                    )
                }
            }

            item {
                SectionCard("App Behaviour", Icons.Rounded.Tune, isCollapsible = true, initiallyExpanded = false) {
                    AppBehaviourSection(
                        skipBehavior = skipBehavior,
                        onSetSkipBehavior = onSetSkipBehavior,
                        hideCompletedInHome = hideCompletedInHome,
                        onSetHideCompletedInHome = onSetHideCompletedInHome,
                        hideCompletedInSubs = hideCompletedInSubs,
                        onSetHideCompletedInSubs = onSetHideCompletedInSubs,
                        hideCompletedInShowDetails = hideCompletedInShowDetails,
                        onSetHideCompletedInShowDetails = onSetHideCompletedInShowDetails
                    )
                }
            }

            item {
                SectionCard("Data Management", Icons.Rounded.Storage, isCollapsible = true, initiallyExpanded = true) {
                    DataManagementSection(
                        appInstanceId = appInstanceId,
                        onResetAnalytics = onResetAnalytics,
                        showResetDialog = showResetDialog,
                        onShowResetDialogChange = { showResetDialog = it },
                        isDeletionExpanded = isDeletionExpanded,
                        onDeletionExpandedChange = { isDeletionExpanded = it }
                    )
                }
            }

            item {
                SectionCard("Powered by Podcast Index", Icons.Rounded.Info, isCollapsible = false) {
                    PodcastIndexSection()
                }
            }
        }
    }

    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Reset Analytics Identity?") },
            text = {
                Column {
                    Text("This acts as a 'Forget Me' for our cloud servers.")
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                         color = MaterialTheme.colorScheme.surfaceContainer,
                         shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Local Data Safe", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Your listening history, subscriptions, and downloads are stored ON THIS DEVICE and WILL NOT be deleted.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• Your cloud analytics ID will be regenerated.")
                    Text("• Orphaned data is auto-deleted after 14 months.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("analytics_reset")
                        onResetAnalytics()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -------------------------------------------------------------------------------------------------
// PAGE CONTENT COMPOSABLES
// -------------------------------------------------------------------------------------------------

@Composable
fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isCollapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isCollapsible) {
                        Modifier.clickable { expanded = !expanded }
                    } else Modifier
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isCollapsible) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(visible = !isCollapsible || expanded) {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AppearanceSection(
    currentThemeConfig: String, onSetThemeConfig: (String) -> Unit,
    isDynamicColorEnabled: Boolean, onToggleDynamicColor: (Boolean) -> Unit,
    currentThemeBrand: String, onSetThemeBrand: (String) -> Unit,
    currentSurfaceStyle: String = "standard", onSetSurfaceStyle: (String) -> Unit = {}
) {
    val isThemeModeLocked = currentSurfaceStyle == "amoled" || currentSurfaceStyle == "purewhite" ||
                            currentSurfaceStyle == "classic_dark" || currentSurfaceStyle == "classic_light"
    val lockedThemeModeLabel = if (currentSurfaceStyle == "amoled" || currentSurfaceStyle == "classic_dark") "Dark" else "Light"

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

        // ── Theme Mode (Standard M3 Segmented Button Row) ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Theme Mode", 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isThemeModeLocked) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "Forced $lockedThemeModeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            @OptIn(ExperimentalMaterial3Api::class)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                val themes = listOf(
                    "system" to "System",
                    "light" to "Light",
                    "dark" to "Dark"
                )
                themes.forEachIndexed { index, (mode, label) ->
                    val isSelected = if (isThemeModeLocked) {
                        (lockedThemeModeLabel == "Dark" && mode == "dark") || (lockedThemeModeLabel == "Light" && mode == "light")
                    } else {
                        currentThemeConfig == mode
                    }
                    
                    SegmentedButton(
                        selected = isSelected,
                        onClick = {
                            if (isThemeModeLocked) {
                                val clickedLockedValue = if (lockedThemeModeLabel == "Dark") "dark" else "light"
                                if (mode != clickedLockedValue) {
                                    onSetSurfaceStyle("classic_dynamic")
                                }
                            }
                            onSetThemeConfig(mode)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                        label = { Text(label) }
                    )
                }
            }
            
            if (isThemeModeLocked) {
                val styleName = when (currentSurfaceStyle) {
                    "amoled" -> "Pitch Black"
                    "purewhite" -> "Pure White"
                    "classic_dark" -> "Blackish"
                    "classic_light" -> "Whitish"
                    else -> "Locked"
                }
                Text(
                    text = "Locked by '$styleName' style. Selecting another mode reverts background to Default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 16.sp
                )
            }
        }

        // ── Surface Background Style (Grouped Cards) ──
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Surface Background Style", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            val entries = cx.aswin.boxcast.core.designsystem.theme.SurfaceStyles.entries.filter { it.key != "highcontrast" }

            // 1. Standalone Default (Almost Dynamic) Option
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                val defaultEntry = entries.first { it.key == "classic_dynamic" }
                SurfaceStyleItem(
                    entry = defaultEntry,
                    isSelected = currentSurfaceStyle == "classic_dynamic",
                    onSelect = {
                        onSetSurfaceStyle("classic_dynamic")
                        onSetThemeConfig("system")
                        onToggleDynamicColor(false)
                        onSetThemeBrand("violet")
                    }
                )
            }

            // 1.5. Standalone Material You Option
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                val standardEntry = entries.first { it.key == "standard" }
                SurfaceStyleItem(
                    entry = standardEntry,
                    isSelected = currentSurfaceStyle == "standard",
                    onSelect = {
                        onSetSurfaceStyle("standard")
                        onSetThemeConfig("system")
                        onToggleDynamicColor(true)
                    }
                )
            }

            // 2. Almost Group
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Almost",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column {
                        val almostKeys = listOf("classic_dark", "classic_light")
                        val almostEntries = entries.filter { it.key in almostKeys }
                        almostEntries.forEachIndexed { index, entry ->
                            val isSelected = currentSurfaceStyle == entry.key
                            SurfaceStyleItem(
                                entry = entry,
                                isSelected = isSelected,
                                onSelect = {
                                    onSetSurfaceStyle(entry.key)
                                    onToggleDynamicColor(false)
                                }
                            )
                            if (index < almostEntries.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

            // 3. Pitch & Pure Group
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Pitch & Pure",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column {
                        val pitchPureKeys = listOf("dynamic_oled_white", "amoled", "purewhite")
                        val pitchPureEntries = entries.filter { it.key in pitchPureKeys }
                        pitchPureEntries.forEachIndexed { index, entry ->
                            val isSelected = currentSurfaceStyle == entry.key
                            SurfaceStyleItem(
                                entry = entry,
                                isSelected = isSelected,
                                onSelect = {
                                    onSetSurfaceStyle(entry.key)
                                    if (entry.key == "dynamic_oled_white") {
                                        onSetThemeConfig("system")
                                    }
                                    onToggleDynamicColor(false)
                                }
                            )
                            if (index < pitchPureEntries.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Accent Colors (Dynamic Switch & Brand Seed Palette) ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Accent Colors", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Material You (Dynamic)", 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Use accent colors generated from your system wallpaper (Android 12+).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = isDynamicColorEnabled, 
                            onCheckedChange = onToggleDynamicColor
                        )
                    }

                    AnimatedVisibility(visible = !isDynamicColorEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Accent Palette", 
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val currentBrandLabel = cx.aswin.boxcast.core.designsystem.theme.BrandSeeds[currentThemeBrand]?.first ?: ""
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = currentBrandLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                cx.aswin.boxcast.core.designsystem.theme.BrandSeeds.forEach { (id, pair) ->
                                    val (_, color) = pair
                                    val isSelected = currentThemeBrand == id
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                ) else Modifier
                                            )
                                            .padding(if (isSelected) 3.dp else 0.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { onSetThemeBrand(id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                null,
                                                tint = color.contrastColor(),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    AnimatedVisibility(visible = isDynamicColorEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Active theme uses system wallpaper colors. Disable dynamic mode to choose a custom brand color.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
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

@Composable
fun ContentLibrarySection(
    currentRegion: String, 
    onSetRegion: (String) -> Unit,
    onExport: () -> Unit, 
    onExportOpml: () -> Unit, 
    onImportJson: () -> Unit, 
    onImportOpml: () -> Unit,
    onSmartDownloadsClick: () -> Unit,
    onAutoDownloadsClick: () -> Unit
) {
    var backupExpanded by remember { mutableStateOf(false) }

    Column {
        Text("Content Region", style = MaterialTheme.typography.titleMedium)
        Text("Select your region for localized recommendations and feeds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val regions = listOf(
                "us" to "USA",
                "in" to "India",
                "gb" to "UK",
                "fr" to "France"
            )
            regions.forEach { (code, label) ->
                val isSelected = when (code) {
                    "gb" -> currentRegion == "gb" || currentRegion == "uk"
                    "in" -> currentRegion == "in" || currentRegion == "ind"
                    else -> currentRegion == code
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { onSetRegion(code) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { backupExpanded = !backupExpanded }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (backupExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (backupExpanded) "Collapse Backup Options" else "Expand Backup Options",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        AnimatedVisibility(visible = backupExpanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Export Library Backup (JSON)") },
                    supportingContent = { Text("Save subscriptions & liked episodes to JSON") },
                    leadingContent = { Icon(Icons.Rounded.FileUpload, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onExport() }
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Export Subscriptions (OPML)") },
                    supportingContent = { Text("Export OPML XML for other podcast apps") },
                    leadingContent = { Icon(Icons.Rounded.FileUpload, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onExportOpml() }
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Import Library Backup") },
                    supportingContent = { Text("Restore a previous JSON backup") },
                    leadingContent = { Icon(Icons.Rounded.FileDownload, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onImportJson() }
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Import from OPML") },
                    supportingContent = { Text("Migrate subscriptions from other apps") },
                    leadingContent = { Icon(Icons.Rounded.FileDownload, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onImportOpml() }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSmartDownloadsClick() }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Smart Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onAutoDownloadsClick() }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(cx.aswin.boxcast.feature.home.R.drawable.ic_cloud_download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Auto-Download New Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DataManagementSection(
    appInstanceId: String?,
    onResetAnalytics: () -> Unit,
    showResetDialog: Boolean,
    onShowResetDialogChange: (Boolean) -> Unit,
    isDeletionExpanded: Boolean,
    onDeletionExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Our Philosophy", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "boxlore is a 0-monetary-gain, exploratory pet project. We track anonymous app usage (including device models and approximate regions via PostHog) solely to understand what features work and what to build next. Your data is completely anonymous and will never be sold. 0 ads, forever.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Why track podcasts?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "We log which podcasts are being played to eventually build native, community-driven charts. This data is tied to an anonymous device ID, meaning we can analyze listenership without ever knowing who you actually are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Breakdown
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Anonymous Usage", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Device-Level Plays", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Device & Approximate Location", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cancel, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("NO PII (Personal Info)", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
        
        // DANGER ZONE INTEGRATED
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Data Management", 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                )
                
                // Reset Identity
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Reset Identity") },
                    supportingContent = { Text("Generate new anonymous ID.") },
                    leadingContent = {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShowResetDialogChange(true) }
                )
                
                // Request Deletion
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Request Immediate Deletion", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Permanently erase your server data.", color = MaterialTheme.colorScheme.error.copy(alpha=0.8f)) },
                    leadingContent = {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    trailingContent = {
                        Icon(
                            if (isDeletionExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("delete_account_requested")
                            onDeletionExpandedChange(!isDeletionExpanded) 
                        }
                )

                // Expanded Deletion UI
                AnimatedVisibility(visible = isDeletionExpanded) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 8.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val postHogId = cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.getDistinctId()
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("delete_id_copied")
                                    clipboardManager.setText(AnnotatedString(postHogId))
                                    Toast.makeText(context, "ID Copied", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.getDistinctId(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("delete_email_clicked")
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("support@aswin.cx"))
                                    putExtra(Intent.EXTRA_SUBJECT, "Data Deletion Request")
                                    putExtra(Intent.EXTRA_TEXT, "Please delete data associated with Instance ID: ${cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.getDistinctId()}")
                                }
                                try { context.startActivity(intent) } catch(_: Exception) {
                                    Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Email support@aswin.cx")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PodcastIndexSection() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.ic_podcast_index_logo),
            contentDescription = "Podcast Index Logo",
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(180.dp)
                .padding(vertical = 8.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Text Description
        Text(
            text = "The Podcast Index is here to preserve, protect and extend the open, independent podcasting ecosystem.\n\n" +
                   "We do this by enabling developers to have access to an open, categorized index that will always be available for free, for any use.\n\n" +
                   "Try a new podcast app integrated with Podcast index today and see how much better the experience can be.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Visit Podcast Index Button
        Button(
            onClick = {
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("podcast_index_homepage_clicked")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://podcastindex.org"))
                try { context.startActivity(intent) } catch(_: Exception) {}
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.Launch,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Visit Podcast Index")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
        }
        Text("boxlore v$versionName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(4.dp))
        Text("Made with ❤️", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
fun AppBehaviourSection(
    skipBehavior: String,
    onSetSkipBehavior: (String) -> Unit,
    hideCompletedInHome: Boolean,
    onSetHideCompletedInHome: (Boolean) -> Unit,
    hideCompletedInSubs: Boolean,
    onSetHideCompletedInSubs: (Boolean) -> Unit,
    hideCompletedInShowDetails: Boolean,
    onSetHideCompletedInShowDetails: (Boolean) -> Unit
) {
    Column {
        Text("Skip Behavior", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Determine what happens when you skip to the next episode via gestures or notification controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val behaviors = listOf(
                "just_skip" to "Just Skip",
                "mark_completed_skip" to "Mark Completed & Skip"
            )
            behaviors.forEach { (mode, label) ->
                FilterChip(
                    selected = skipBehavior == mode,
                    onClick = { onSetSkipBehavior(mode) },
                    label = { Text(label) },
                    leadingIcon = { if (skipBehavior == mode) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Text("Hide completed episodes from", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Option 1: Home Feeds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetHideCompletedInHome(!hideCompletedInHome) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Home Feeds", style = MaterialTheme.typography.bodyLarge)
            }
            Switch(
                checked = hideCompletedInHome,
                onCheckedChange = onSetHideCompletedInHome
            )
        }

        // Option 2: Subscription Activity
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetHideCompletedInSubs(!hideCompletedInSubs) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("New episodes (library)", style = MaterialTheme.typography.bodyLarge)
            }
            Switch(
                checked = hideCompletedInSubs,
                onCheckedChange = onSetHideCompletedInSubs
            )
        }

        // Option 3: Show Details
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetHideCompletedInShowDetails(!hideCompletedInShowDetails) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Details", style = MaterialTheme.typography.bodyLarge)
            }
            Switch(
                checked = hideCompletedInShowDetails,
                onCheckedChange = onSetHideCompletedInShowDetails
            )
        }
    }
}

@Composable
private fun SurfaceStyleItem(
    entry: cx.aswin.boxcast.core.designsystem.theme.SurfaceStyles.Entry,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else Color.Transparent
        ),
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (entry.key == "amoled" || entry.key == "classic_dark" || entry.key == "purewhite" || entry.key == "classic_light") {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = if (entry.key == "amoled" || entry.key == "classic_dark") "Dark Only" else "Light Only",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        },
        trailingContent = {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    )
}

