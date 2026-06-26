package cx.aswin.boxcast.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    onImportJson: (android.net.Uri) -> Unit = {},
    onImportOpml: (android.net.Uri) -> Unit = {},
    skipBehavior: String = "just_skip",
    onSetSkipBehavior: (String) -> Unit = {},
    hideCompletedInHome: Boolean = true,
    onSetHideCompletedInHome: (Boolean) -> Unit = {},
    hideCompletedInSubs: Boolean = true,
    onSetHideCompletedInSubs: (Boolean) -> Unit = {},
    hideCompletedInShowDetails: Boolean = false,
    onSetHideCompletedInShowDetails: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var isDeletionExpanded by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let { onExportJson(it) } }
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SectionCard("App Behaviour", Icons.Rounded.Tune) {
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
                SectionCard("Appearance", Icons.Rounded.Palette) {
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
                SectionCard("Library & Content", Icons.AutoMirrored.Rounded.LibraryBooks) {
                    ContentLibrarySection(
                        currentRegion, 
                        { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("content_region_changed", it)
                            onSetRegion(it) 
                        },
                        { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("library_export")
                            exportJsonLauncher.launch("boxcast_backup_${System.currentTimeMillis()}.json") 
                        },
                        { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("library_import_json")
                            importJsonLauncher.launch(arrayOf("application/json")) 
                        },
                        { 
                            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("library_import_opml")
                            importOpmlLauncher.launch(arrayOf("*/*")) 
                        }
                    )
                }
            }

            item {
                SectionCard("Data & Privacy", Icons.Rounded.Security) {
                    PrivacySection(
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
                SectionCard("Community", Icons.Rounded.Public) {
                    CommunitySection(context)
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
fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            content()
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
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

        // ── Theme Mode (Segmented Pill Bar) ──
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf(
                    "system" to "System",
                    "light" to "Light",
                    "dark" to "Dark"
                )
                modes.forEach { (mode, label) ->
                    val isSelected = currentThemeConfig == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSetThemeConfig(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Surface Style ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Surface Style", style = MaterialTheme.typography.titleMedium)

            cx.aswin.boxcast.core.designsystem.theme.SurfaceStyles.entries.forEach { entry ->
                val isSelected = currentSurfaceStyle == entry.key
                val swatchColor = when (entry.key) {
                    "amoled" -> Color.Black
                    "purewhite" -> Color.White
                    "highcontrast" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                val swatchBorder = when (entry.key) {
                    "purewhite" -> MaterialTheme.colorScheme.outlineVariant
                    else -> Color.Transparent
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            else Color.Transparent
                        )
                        .clickable { onSetSurfaceStyle(entry.key) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(1.dp, swatchBorder, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = null
                    )
                }
            }
        }

        // ── Color ──
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Dynamic Color toggle
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onToggleDynamicColor(!isDynamicColorEnabled) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dynamic Color", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Use wallpaper colors (Android 12+)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isDynamicColorEnabled, onCheckedChange = onToggleDynamicColor)
            }

            // Accent Color palette (when dynamic color is off)
            AnimatedVisibility(visible = !isDynamicColorEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                        val currentBrandLabel = cx.aswin.boxcast.core.designsystem.theme.BrandSeeds[currentThemeBrand]?.first ?: ""
                        Text(
                            text = currentBrandLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        cx.aswin.boxcast.core.designsystem.theme.BrandSeeds.forEach { (id, pair) ->
                            val (_, color) = pair
                            val isSelected = currentThemeBrand == id
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
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
        }
    }
}

@Composable
fun ContentLibrarySection(
    currentRegion: String, onSetRegion: (String) -> Unit,
    onExport: () -> Unit, onImportJson: () -> Unit, onImportOpml: () -> Unit
) {
    Column {
        Text("Content Region", style = MaterialTheme.typography.titleMedium)
        Text("Select your region for localized recommendations and feeds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                val isSelected = currentRegion == code
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

        Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
        Spacer(Modifier.height(8.dp))

        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Export Library Backup") },
            supportingContent = { Text("Save subscriptions & liked episodes to JSON") },
            leadingContent = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onExport() }
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Import Library Backup") },
            supportingContent = { Text("Restore a previous JSON backup") },
            leadingContent = { Icon(Icons.Rounded.Upload, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onImportJson() }
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Import from OPML") },
            supportingContent = { Text("Migrate subscriptions from other apps") },
            leadingContent = { Icon(Icons.Rounded.ImportExport, null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { onImportOpml() }
        )
    }
}

@Composable
fun PrivacySection(
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
                    "boxcast is a 0-monetary-gain, exploratory pet project. We track anonymous app usage (including device models and approximate regions via PostHog) solely to understand what features work and what to build next. Your data is completely anonymous and will never be sold. 0 ads, forever.",
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

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // GitHub Link Button
        OutlinedButton(
            onClick = {
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("github_repo_clicked")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ashwkun/box.lore.android"))
                try { context.startActivity(intent) } catch(_:Exception){}
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(androidx.compose.ui.res.painterResource(id = cx.aswin.boxcast.core.designsystem.R.drawable.ic_github), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Review the code on Github")
        }

        Spacer(Modifier.height(12.dp))



        // Privacy Policy Link
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Read Privacy Policy") },
            leadingContent = {
                Icon(Icons.Rounded.Policy, null, tint = MaterialTheme.colorScheme.onSurface)
            },
            modifier = Modifier.clickable {
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("privacy_policy_clicked")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aswin.cx/boxlore/privacy"))
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
        )
        
        Spacer(Modifier.height(16.dp))
        
        // DANGER ZONE INTEGRATED
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
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
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("privacy@aswin.cx"))
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
                            Text("Email privacy@aswin.cx")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommunitySection(context: android.content.Context) {
    Column {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("GitHub Repository") },
            supportingContent = { Text("Open Source. Star, fork, or contribute!") },
            leadingContent = {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_github),
                    contentDescription = "GitHub",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            },
            modifier = Modifier.clickable {
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("github_repo_clicked")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ashwkun/box.lore.android"))
                try { context.startActivity(intent) } catch(_:Exception){}
            }
        )
        HorizontalDivider()

        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Podcast Index") },
            supportingContent = { Text("Powered by the decentralized index.") },
            leadingContent = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFFE22828))
            },
            modifier = Modifier.clickable {
                cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackSettingsInteraction("podcast_index_clicked")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://podcastindex.org"))
                try { context.startActivity(intent) } catch(_:Exception){}
            }
        )
        HorizontalDivider()

        Spacer(Modifier.height(16.dp))
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }
            }
            Text("boxcast v$versionName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            Text("Made with ❤️", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
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
