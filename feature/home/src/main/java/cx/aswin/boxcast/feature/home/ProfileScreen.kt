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
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.R

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
    onExportJson: (android.net.Uri) -> Unit = {},
    onImportJson: (android.net.Uri) -> Unit = {},
    onImportOpml: (android.net.Uri) -> Unit = {}
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
                SectionCard("Appearance", Icons.Rounded.Palette) {
                    AppearanceSection(
                        currentThemeConfig, onSetThemeConfig, 
                        isDynamicColorEnabled, onToggleDynamicColor, 
                        currentThemeBrand, onSetThemeBrand
                    )
                }
            }

            item {
                SectionCard("Library & Content", Icons.Rounded.LibraryBooks) {
                    ContentLibrarySection(
                        currentRegion, onSetRegion,
                        { exportJsonLauncher.launch("boxcast_backup_${System.currentTimeMillis()}.json") },
                        { importJsonLauncher.launch(arrayOf("application/json")) },
                        { importOpmlLauncher.launch(arrayOf("*/*")) }
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
    currentThemeBrand: String, onSetThemeBrand: (String) -> Unit
) {
    Column {
        Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val modes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
            modes.forEach { (mode, label) ->
                FilterChip(
                    selected = currentThemeConfig == mode,
                    onClick = { onSetThemeConfig(mode) },
                    label = { Text(label) },
                    leadingIcon = { if (currentThemeConfig == mode) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Dynamic Color", style = MaterialTheme.typography.titleMedium)
                Text("Use wallpaper colors (Android 12+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = isDynamicColorEnabled, onCheckedChange = onToggleDynamicColor)
        }

        AnimatedVisibility(visible = !isDynamicColorEnabled) {
            Column {
                Spacer(Modifier.height(24.dp))
                Text("Color Palette", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                
                val brands = listOf(
                    Triple("violet", "Violet", Color(0xFF6750A4)),
                    Triple("emerald", "Emerald", Color(0xFF006C4C)),
                    Triple("ocean", "Ocean", Color(0xFF0061A4)),
                    Triple("sakura", "Sakura", Color(0xFFBC004B)),
                    Triple("tangerine", "Tangerine", Color(0xFF964900)),
                    Triple("crimson", "Crimson", Color(0xFFB91823)),
                    Triple("canary", "Canary", Color(0xFF725C00))
                )
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    brands.forEach { (id, label, color) ->
                        val isSelected = currentThemeBrand == id
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { onSetThemeBrand(id) }
                                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text("Choose region for trending charts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val isGlobal = currentRegion != "in"
            FilterChip(
                selected = !isGlobal, onClick = { onSetRegion("in") }, label = { Text("India (IN)") },
                leadingIcon = { if (!isGlobal) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }, modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = isGlobal, onClick = { onSetRegion("us") }, label = { Text("Global (US)") },
                leadingIcon = { if (isGlobal) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }, modifier = Modifier.weight(1f)
            )
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
                    "boxcast is a 0-monetary-gain, exploratory pet project. We track app usage solely to understand what features work, what to remove, and what to build next. Your data never leaves our own databases, is completely anonymous, and will never be sold. 0 ads, forever.",
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
                    "We log which podcasts are being played to eventually build native, community-driven charts. This data is strictly aggregated and is absolutely never linked back to you or your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Breakdown
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Anonymous Usage", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Aggregated Plays", style = MaterialTheme.typography.bodySmall)
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cancel, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("NO Location Data", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cancel, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("NO Personal IDs", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // Verify It Nudge
        Text("Don't Trust Us? Verify It.", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Our entire app (not just the tracking engine) is 100% open source. You can audit the code on GitHub or ask AI to verify it for you.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(12.dp))

        // GitHub Link Button
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ashwkun/box.cast.android"))
                try { context.startActivity(intent) } catch(_:Exception){}
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Code, null)
            Spacer(Modifier.width(8.dp))
            Text("View App Source Code on GitHub")
        }

        Spacer(Modifier.height(12.dp))

        // AI Prompt block
        val aiPrompt = "Analyze the source code of this Android app at https://github.com/ashwkun/box.cast.android.\n\nDoes it collect any Personally Identifiable Information (PII), track device identifiers, or contain any third-party advertising SDKs?"
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("AI Verification Prompt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = aiPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(aiPrompt))
                        Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Prompt", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        // Privacy Policy Link
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Read Privacy Policy") },
            leadingContent = {
                Icon(Icons.Rounded.Policy, null, tint = MaterialTheme.colorScheme.onSurface)
            },
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aswin.cx/boxcast/privacy"))
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
                        .clickable { onDeletionExpandedChange(!isDeletionExpanded) }
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
                                    appInstanceId?.let {
                                        clipboardManager.setText(AnnotatedString(it))
                                        Toast.makeText(context, "ID Copied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = appInstanceId ?: "Generating ID...",
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
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("privacy@aswin.cx"))
                                    putExtra(Intent.EXTRA_SUBJECT, "Data Deletion Request")
                                    putExtra(Intent.EXTRA_TEXT, "Please delete data associated with Instance ID: ${appInstanceId ?: "UNKNOWN"}")
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
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ashwkun/box.cast.android"))
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
            Text("boxcast v1.0.0 (Beta)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(4.dp))
            Text("Made with ❤️", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
