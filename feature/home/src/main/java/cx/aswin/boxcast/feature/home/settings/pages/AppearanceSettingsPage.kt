package cx.aswin.boxcast.feature.home.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.BrandSeeds
import cx.aswin.boxcast.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxcast.core.designsystem.theme.isCustomThemeBrand
import cx.aswin.boxcast.core.designsystem.theme.resolveThemeSeedColor
import cx.aswin.boxcast.feature.home.settings.components.AccentSwatchGrid
import cx.aswin.boxcast.feature.home.settings.components.SettingsChoiceRow
import cx.aswin.boxcast.feature.home.settings.components.SettingsContent
import cx.aswin.boxcast.feature.home.settings.components.SettingsDivider
import cx.aswin.boxcast.feature.home.settings.components.SettingsGroup
import cx.aswin.boxcast.feature.home.settings.components.SettingsScaffold
import cx.aswin.boxcast.feature.home.settings.components.SettingsSwitchRow
import cx.aswin.boxcast.feature.home.settings.dialogs.AccentColorPickerDialog

/** Current values shown on [AppearanceSettingsPage]. Also used by [cx.aswin.boxcast.feature.home.settings.SettingsScreen]. */
data class AppearanceUiState(
    val currentThemeConfig: String,
    val isDynamicColorEnabled: Boolean,
    val currentThemeBrand: String,
    val currentSurfaceStyle: String,
)

/** Callbacks for [AppearanceSettingsPage], grouped to keep the page's parameter count small. */
data class AppearanceActions(
    val onSetThemeConfig: (String) -> Unit,
    val onToggleDynamicColor: (Boolean) -> Unit,
    val onSetThemeBrand: (String) -> Unit,
    val onSetSurfaceStyle: (String) -> Unit,
)

@Composable
internal fun AppearanceSettingsPage(
    state: AppearanceUiState,
    actions: AppearanceActions,
    onBack: () -> Unit,
) {
    val look = BackgroundLook.fromSurfaceStyle(state.currentSurfaceStyle)

    fun applyStyle(style: String) {
        selectSurfaceStyle(
            style = style,
            onSetSurfaceStyle = actions.onSetSurfaceStyle,
            onToggleDynamicColor = actions.onToggleDynamicColor,
            onSetThemeBrand = actions.onSetThemeBrand,
        )
    }

    SettingsScaffold(
        title = "Appearance",
        onBack = onBack,
    ) {
        ThemeModeSection(
            currentThemeConfig = state.currentThemeConfig,
            currentSurfaceStyle = state.currentSurfaceStyle,
            onSetThemeConfig = actions.onSetThemeConfig,
            onUnlockToAutomatic = { automaticStyle ->
                applyStyle(automaticStyle)
            },
        )

        BackgroundLookSection(
            selectedLook = look,
            onSelectLook = { nextLook ->
                applyStyle(nextLook.surfaceStyleKey)
            },
        )

        ColorsSection(
            isDynamicColorEnabled = state.isDynamicColorEnabled,
            onToggleDynamicColor = actions.onToggleDynamicColor,
            currentThemeBrand = state.currentThemeBrand,
            onSetThemeBrand = actions.onSetThemeBrand,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSection(
    currentThemeConfig: String,
    currentSurfaceStyle: String,
    onSetThemeConfig: (String) -> Unit,
    onUnlockToAutomatic: (String) -> Unit,
) {
    val modeLock = themeModeLockFor(currentSurfaceStyle)
    val selectedMode = modeLock?.mode ?: ThemeMode.fromKey(currentThemeConfig) ?: ThemeMode.SYSTEM

    SettingsGroup(
        title = "Theme",
        footer = modeLock?.let {
            "This background was locked to ${it.mode.label.lowercase()}. Choosing another theme unlocks it."
        } ?: "Chooses whether the app looks light or dark. Backgrounds follow this.",
    ) {
        SettingsContent {
            if (modeLock != null) {
                ForcedModeBadge(modeLock.mode)
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = {
                            if (modeLock != null && mode != modeLock.mode) {
                                onUnlockToAutomatic(modeLock.automaticSiblingStyle)
                            }
                            onSetThemeConfig(mode.key)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size,
                        ),
                        label = { Text(mode.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ForcedModeBadge(mode: ThemeMode) {
    Surface(
        modifier = Modifier.padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Locked to ${mode.label.lowercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BackgroundLookSection(
    selectedLook: BackgroundLook,
    onSelectLook: (BackgroundLook) -> Unit,
) {
    SettingsGroup(
        title = "Background",
        footer = "How the app’s surfaces look. They follow Theme above.",
    ) {
        BackgroundLook.entries.forEachIndexed { index, look ->
            SettingsChoiceRow(
                title = look.label,
                supportingText = look.subtext,
                selected = selectedLook == look,
                onClick = { onSelectLook(look) },
            )
            if (index < BackgroundLook.entries.lastIndex) {
                SettingsDivider()
            }
        }
    }
}

@Composable
private fun ColorsSection(
    isDynamicColorEnabled: Boolean,
    onToggleDynamicColor: (Boolean) -> Unit,
    currentThemeBrand: String,
    onSetThemeBrand: (String) -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val customSelected = isCustomThemeBrand(currentThemeBrand)
    val customPreview = resolveThemeSeedColor(currentThemeBrand)

    SettingsGroup(
        title = "Colors",
        footer = if (isDynamicColorEnabled) {
            "Uses your wallpaper. Turn off to pick a fixed color."
        } else {
            null
        },
    ) {
        SettingsSwitchRow(
            title = "Wallpaper colors",
            supportingText = "Use colors from your home-screen wallpaper",
            checked = isDynamicColorEnabled,
            onCheckedChange = onToggleDynamicColor,
        )
        AnimatedVisibility(visible = !isDynamicColorEnabled) {
            val seeds = remember {
                BrandSeeds.map { (key, brand) ->
                    Triple(key, brand.first, brand.second)
                }
            }
            Column {
                SettingsDivider()
                AccentSwatchGrid(
                    seeds = seeds,
                    selectedKey = if (customSelected) "" else currentThemeBrand,
                    onSelect = onSetThemeBrand,
                )
                SettingsDivider()
                SettingsChoiceRow(
                    title = "Custom color",
                    supportingText = if (customSelected) {
                        currentThemeBrand
                    } else {
                        "Pick any accent with the full color picker"
                    },
                    selected = customSelected,
                    onClick = { showColorPicker = true },
                    leading = {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = customPreview,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {}
                    },
                )
            }
        }
    }

    if (showColorPicker) {
        AccentColorPickerDialog(
            initialColor = customPreview,
            onConfirm = { hex ->
                onSetThemeBrand(hex)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

private fun selectSurfaceStyle(
    style: String,
    onSetSurfaceStyle: (String) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSetThemeBrand: (String) -> Unit,
) {
    onSetSurfaceStyle(style)
    when (style) {
        SurfaceStyles.CLASSIC_DYNAMIC -> {
            onToggleDynamicColor(false)
            onSetThemeBrand(DEFAULT_BRAND)
        }

        SurfaceStyles.STANDARD -> {
            onToggleDynamicColor(true)
        }

        SurfaceStyles.DYNAMIC_OLED_WHITE -> {
            onToggleDynamicColor(false)
        }
    }
}

/**
 * Legacy locked styles still resolve in the theme engine. Changing Theme unlocks them
 * to Soft/Pure automatic so Theme can drive light/dark again.
 */
private fun themeModeLockFor(surfaceStyle: String): ThemeModeLock? = when (surfaceStyle) {
    SurfaceStyles.AMOLED -> ThemeModeLock(
        mode = ThemeMode.DARK,
        automaticSiblingStyle = SurfaceStyles.DYNAMIC_OLED_WHITE,
    )
    SurfaceStyles.PURE_WHITE -> ThemeModeLock(
        mode = ThemeMode.LIGHT,
        automaticSiblingStyle = SurfaceStyles.DYNAMIC_OLED_WHITE,
    )
    SurfaceStyles.CLASSIC_DARK -> ThemeModeLock(
        mode = ThemeMode.DARK,
        automaticSiblingStyle = SurfaceStyles.CLASSIC_DYNAMIC,
    )
    SurfaceStyles.CLASSIC_LIGHT -> ThemeModeLock(
        mode = ThemeMode.LIGHT,
        automaticSiblingStyle = SurfaceStyles.CLASSIC_DYNAMIC,
    )
    else -> null
}

private enum class BackgroundLook(
    val label: String,
    val subtext: String,
    val surfaceStyleKey: String,
) {
    Classic(
        label = "boxlore classic",
        subtext = "Gentle gray when dark, warm cream when light",
        surfaceStyleKey = SurfaceStyles.CLASSIC_DYNAMIC,
    ),
    Pure(
        label = "Pure",
        subtext = "True black when dark, pure white when light",
        surfaceStyleKey = SurfaceStyles.DYNAMIC_OLED_WHITE,
    ),
    MaterialYouSoft(
        label = "Material You Soft",
        subtext = "Colored surfaces from your wallpaper (or your accent below)",
        surfaceStyleKey = SurfaceStyles.STANDARD,
    ),
    ;

    companion object {
        fun fromSurfaceStyle(style: String): BackgroundLook = when (style) {
            SurfaceStyles.CLASSIC_DYNAMIC,
            SurfaceStyles.CLASSIC_DARK,
            SurfaceStyles.CLASSIC_LIGHT -> Classic

            SurfaceStyles.DYNAMIC_OLED_WHITE,
            SurfaceStyles.AMOLED,
            SurfaceStyles.PURE_WHITE -> Pure

            else -> MaterialYouSoft
        }
    }
}

private data class ThemeModeLock(
    val mode: ThemeMode,
    val automaticSiblingStyle: String,
)

private enum class ThemeMode(
    val key: String,
    val label: String,
) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;

    companion object {
        fun fromKey(key: String): ThemeMode? = entries.firstOrNull { it.key == key }
    }
}

private const val DEFAULT_BRAND = "violet"
