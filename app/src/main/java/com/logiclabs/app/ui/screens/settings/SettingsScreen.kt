package com.logiclabs.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.logiclabs.app.di.AppContainer
import com.logiclabs.app.ui.component.BenchDialog
import com.logiclabs.app.ui.component.BenchDialogAction
import com.logiclabs.app.ui.component.BenchDialogActionDivider
import com.logiclabs.app.ui.component.BenchDialogTone
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.data.settings.Settings
import com.logiclabs.core.data.settings.ThemeMode
import com.logiclabs.core.designsystem.component.ChamferedPanel
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.component.SegmentItem
import com.logiclabs.core.designsystem.component.SegmentedControl
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.CanvasBackground
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.Motion
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.SwitchLeverShade
import com.logiclabs.core.designsystem.theme.SwitchWell
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.tools.feedback.Haptics

/**
 * Bench configuration, laid out as a stack of rack modules — one chamfered panel per
 * concern, each with a silkscreened section legend.
 *
 * Every control writes straight through to the SettingsRepository (DataStore), so
 * there is deliberately no save button: a change is live the moment the rocker lands.
 * Destructive actions (reset) sit behind a confirmation, styled like the bench's
 * existing board-actions dialog.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val settings by container.settings.settings.collectAsState(initial = Settings())
    val view = LocalView.current
    var confirmResetPreferences by remember { mutableStateOf(false) }
    var confirmResetProgress by remember { mutableStateOf(false) }
    var showAboutCreator by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .windowInsetsPadding(
                WindowInsets.statusBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = Dimens.Space6)
    ) {
        // Header: back control and title.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Space2, vertical = Dimens.Space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.MinTouchTarget)
                    .clip(RoundedCornerShape(Dimens.RadiusMd))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onBack
                    )
                    .semantics { contentDescription = "Back to home" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LogicIcons.Back,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    "SETTINGS",
                    style = LogicLabsType.DisplayLg,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    "BENCH CONFIGURATION · APPLIED IMMEDIATELY",
                    style = LogicLabsType.SwitchPlate,
                    color = TextTertiary,
                    maxLines = 1
                )
            }
        }

        SettingsModule(
            title = "APPEARANCE",
            subtitle = "Hardware instrument chassis styling",
            icon = LogicIcons.Contrast
        ) {
            // Always show the currently selected theme at the top as a summary
            val currentOption = THEME_OPTIONS.find { it.mode == settings.themeMode }
            if (currentOption != null) {
                Text(
                    text = "ACTIVE: ${currentOption.title}",
                    style = LogicLabsType.TechnicalSm,
                    color = currentOption.accentColor,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = Dimens.Space2)
                )
            }

            // Warm, classic dark lab finishes.
            ThemeCategory(
                title = "DARK INSTRUMENTS",
                subtitle = themeCountLabel(DARK_THEMES),
                themes = DARK_THEMES,
                selectedMode = settings.themeMode,
                onSelect = { container.settings.setThemeMode(it) }
            )

            Spacer(Modifier.height(Dimens.Space2))

            // Cool-toned dark precision finishes. HP Slate and Titanium Frost are dark
            // chassis colours (see their swatches) — an earlier "LIGHT & PRECISION"
            // bucket put them under a light label they do not have.
            ThemeCategory(
                title = "MIDNIGHT PRECISION",
                subtitle = themeCountLabel(COOL_THEMES),
                themes = COOL_THEMES,
                selectedMode = settings.themeMode,
                onSelect = { container.settings.setThemeMode(it) }
            )

            Spacer(Modifier.height(Dimens.Space2))

            // High-contrast accent finishes.
            ThemeCategory(
                title = "SPECIALTY",
                subtitle = themeCountLabel(ACCENT_THEMES),
                themes = ACCENT_THEMES,
                selectedMode = settings.themeMode,
                onSelect = { container.settings.setThemeMode(it) }
            )

            Spacer(Modifier.height(Dimens.Space2))

            // The single daylight finish.
            ThemeCategory(
                title = "DAYLIGHT",
                subtitle = themeCountLabel(LIGHT_THEMES),
                themes = LIGHT_THEMES,
                selectedMode = settings.themeMode,
                onSelect = { container.settings.setThemeMode(it) }
            )

            Spacer(Modifier.height(Dimens.Space2))

            // System Default always visible
            val systemOption = THEME_OPTIONS.find { it.mode == ThemeMode.SYSTEM }
            if (systemOption != null) {
                ThemeCard(
                    option = systemOption,
                    isSelected = settings.themeMode == systemOption.mode,
                    onSelect = { container.settings.setThemeMode(systemOption.mode) }
                )
            }
        }

        SettingsModule(
            title = "SOUND & HAPTICS",
            subtitle = "Console feedback",
            icon = LogicIcons.Speaker
        ) {
            SettingsToggleRow(
                title = "Console sound",
                description = "Relay clicks and switch pops",
                isOn = settings.soundEnabled,
                onToggle = { container.settings.setSoundEnabled(it) }
            )
            SettingsToggleRow(
                title = "Haptics",
                description = "Switch throws, detents and verification results",
                isOn = settings.hapticsEnabled,
                onToggle = { container.settings.setHapticsEnabled(it) }
            )
        }

        SettingsModule(
            title = "SIGNAL RENDERING",
            subtitle = "How live logic levels are drawn",
            icon = LogicIcons.Scope
        ) {
            SettingsToggleRow(
                title = "LED afterglow",
                description = "Persistence-of-vision dimming on fast signals",
                isOn = settings.ledAfterglow,
                onToggle = { container.settings.setLedAfterglow(it) }
            )
            SettingsToggleRow(
                title = "Bloom effects",
                description = "Light spill around lit LEDs and displays",
                isOn = settings.bloomEffects,
                onToggle = { container.settings.setBloomEffects(it) }
            )
        }

        SettingsModule(
            title = "WORKSPACE",
            subtitle = "Defaults for new jumpers",
            icon = LogicIcons.Wire
        ) {
            Text(
                text = "DEFAULT WIRE COLOUR",
                style = LogicLabsType.SwitchPlate,
                color = TextSecondary
            )
            Spacer(Modifier.height(Dimens.Space2))
            WireColorRow(
                selected = settings.defaultWireColor,
                onSelect = { container.settings.setDefaultWireColor(it.name) }
            )
            Spacer(Modifier.height(Dimens.Space3))
            Text(
                text = "DEFAULT ROUTING",
                style = LogicLabsType.SwitchPlate,
                color = TextSecondary
            )
            Spacer(Modifier.height(Dimens.Space2))
            SegmentedControl(
                items = listOf(
                    SegmentItem(label = "CURVED", icon = { tint ->
                        Icon(LogicIcons.RouteCurve, null, tint = tint, modifier = Modifier.size(16.dp))
                    }),
                    SegmentItem(label = "SQUARE", icon = { tint ->
                        Icon(LogicIcons.RouteSquare, null, tint = tint, modifier = Modifier.size(16.dp))
                    })
                ),
                selectedIndex = if (settings.defaultRoutingManhattan) 1 else 0,
                onSelect = { index -> container.settings.setDefaultRoutingManhattan(index == 1) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsModule(
            title = "DATA",
            subtitle = "Local storage on this device",
            icon = LogicIcons.Save
        ) {
            SettingsToggleRow(
                title = "Autosave",
                description = "Keep a recovery draft of the open bench",
                isOn = settings.autosaveEnabled,
                onToggle = { container.settings.setAutosaveEnabled(it) }
            )
            Spacer(Modifier.height(Dimens.Space2))
            SettingsActionRow(
                title = "Reset preferences",
                description = "Theme, sound, haptics, and routing return to factory defaults. Coursework progress and saved projects are preserved.",
                destructive = false,
                onClick = { confirmResetPreferences = true }
            )
            Spacer(Modifier.height(Dimens.Space2))
            SettingsActionRow(
                title = "Reset coursework progress",
                description = "Completed lab verifications, badges, and bench statistics return to zero. Bench preferences and saved projects are preserved.",
                destructive = true,
                onClick = { confirmResetProgress = true }
            )
        }

        SettingsModule(
            title = "ABOUT",
            subtitle = "Mission, creator backstory & digital twin",
            icon = LogicIcons.Info
        ) {
            CreatorProfileCard(
                onClick = {
                    Haptics.tick(view)
                    showAboutCreator = true
                }
            )
            Spacer(Modifier.height(Dimens.Space2))
            AboutRow(label = "APP", value = "Logic Labs · Offline Digital Twin")
            AboutRow(label = "MACHINE", value = "K&H IDL-800A Digital Lab")
            AboutRow(label = "CREATOR", value = "Adnan Foisal (CUET CSE)")
            AboutRow(label = "NETWORK", value = "None. The bench is fully offline.")
            AboutRow(label = "ACCOUNTS", value = "None. Nothing leaves this device.")
        }
    }

    if (confirmResetPreferences) {
        ResetPreferencesDialog(
            onConfirm = {
                container.settings.reset()
                confirmResetPreferences = false
            },
            onDismiss = { confirmResetPreferences = false }
        )
    }

    if (confirmResetProgress) {
        ResetProgressDialog(
            onConfirm = {
                container.progress.reset()
                confirmResetProgress = false
            },
            onDismiss = { confirmResetProgress = false }
        )
    }

    if (showAboutCreator) {
        AboutCreatorDialog(
            onDismiss = { showAboutCreator = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Hardware Theme Suite
// ---------------------------------------------------------------------------

private data class ThemeOption(
    val mode: ThemeMode,
    val title: String,
    val subtitle: String,
    val primaryColor: Color,
    val accentColor: Color
)

private val THEME_OPTIONS = listOf(
    ThemeOption(
        mode = ThemeMode.OBSIDIAN,
        title = "OBSIDIAN STEALTH",
        subtitle = "AMOLED Night Bench · Phosphor Emerald",
        primaryColor = Color(0xFF060709),
        accentColor = Color(0xFF10B981)
    ),
    ThemeOption(
        mode = ThemeMode.AMBER_CRT,
        title = "AMBER CRT",
        subtitle = "Vintage Tektronix · Warm Amber",
        primaryColor = Color(0xFF1C1917),
        accentColor = Color(0xFFF59E0B)
    ),
    ThemeOption(
        mode = ThemeMode.HP_SLATE,
        title = "HP SLATE",
        subtitle = "Precision Instrument · Ice Blue",
        primaryColor = Color(0xFF1E293B),
        accentColor = Color(0xFF38BDF8)
    ),
    ThemeOption(
        mode = ThemeMode.CLEANROOM,
        title = "CLEANROOM WHITE",
        subtitle = "Daylight Architectural · Precision Graphite",
        primaryColor = Color(0xFFFAFAFA),
        accentColor = Color(0xFF0EA5E9)
    ),
    ThemeOption(
        mode = ThemeMode.CYBERPUNK_NEON,
        title = "CYBERPUNK NEON",
        subtitle = "Midnight Synthwave · Laser Cyan & Magenta",
        primaryColor = Color(0xFF140C24),
        accentColor = Color(0xFFF43F5E)
    ),
    ThemeOption(
        mode = ThemeMode.TOKYO_NIGHT,
        title = "TOKYO NIGHT",
        subtitle = "Midnight Indigo · Electric Violet & Cyan",
        primaryColor = Color(0xFF1A1B26),
        accentColor = Color(0xFF7AA2F7)
    ),
    ThemeOption(
        mode = ThemeMode.SOLARIZED_DARK,
        title = "SOLARIZED DARK",
        subtitle = "Classic Engineer Teal · Terminal Contrast",
        primaryColor = Color(0xFF073642),
        accentColor = Color(0xFF2AA198)
    ),
    ThemeOption(
        mode = ThemeMode.VINTAGE_BRITISH_LAB,
        title = "VINTAGE BRITISH LAB",
        subtitle = "1980s Dark Forest Enamel · Emerald Phosphor",
        primaryColor = Color(0xFF16281F),
        accentColor = Color(0xFF34D399)
    ),
    ThemeOption(
        mode = ThemeMode.TITANIUM_FROST,
        title = "TITANIUM FROST",
        subtitle = "Aerospace Brushed Titanium · Glacial Ice Cyan",
        primaryColor = Color(0xFF1A202C),
        accentColor = Color(0xFF67E8F9)
    ),
    ThemeOption(
        mode = ThemeMode.SYSTEM,
        title = "SYSTEM DEFAULT",
        subtitle = "Follows Android system night/day mode",
        primaryColor = Color(0xFF262A35),
        accentColor = Color(0xFFA0AEC0)
    )
)

@Composable
private fun ThemeCard(
    option: ThemeOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(if (isSelected) option.accentColor.copy(alpha = 0.12f) else SurfaceCard)
            .border(
                width = if (isSelected) 1.5.dp else Dimens.Hairline,
                color = if (isSelected) option.accentColor else SurfaceCardBorder,
                shape = RoundedCornerShape(Dimens.RadiusMd)
            )
            .clickable {
                if (!isSelected) {
                    Haptics.tick(view)
                    onSelect()
                }
            }
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Swatch preview: two-tone pill
        Row(
            modifier = Modifier
                .size(width = 38.dp, height = 22.dp)
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .border(Dimens.Hairline, Color.White.copy(alpha = 0.2f), RoundedCornerShape(Dimens.RadiusPill))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(option.primaryColor)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(option.accentColor)
            )
        }

        Spacer(Modifier.width(Dimens.Space3))

        Column(Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = LogicLabsType.SwitchPlate,
                color = if (isSelected) option.accentColor else TextPrimary,
                maxLines = 1
            )
            Text(
                text = option.subtitle,
                style = LogicLabsType.TechnicalXs,
                color = TextTertiary,
                maxLines = 1
            )
        }

        if (isSelected) {
            Icon(
                imageVector = LogicIcons.Verify,
                contentDescription = "Selected",
                tint = option.accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Theme categories — logically grouped for the collapsible APPEARANCE section.
// Buckets follow the chassis' actual lightness: only CLEANROOM is a light finish,
// so no dark theme ever sits under a "light" label.
private val DARK_THEMES = THEME_OPTIONS.filter {
    it.mode in setOf(
        ThemeMode.OBSIDIAN, ThemeMode.AMBER_CRT, ThemeMode.SOLARIZED_DARK,
        ThemeMode.VINTAGE_BRITISH_LAB
    )
}

private val COOL_THEMES = THEME_OPTIONS.filter {
    it.mode in setOf(ThemeMode.HP_SLATE, ThemeMode.TOKYO_NIGHT, ThemeMode.TITANIUM_FROST)
}

private val ACCENT_THEMES = THEME_OPTIONS.filter {
    it.mode == ThemeMode.CYBERPUNK_NEON
}

private val LIGHT_THEMES = THEME_OPTIONS.filter {
    it.mode == ThemeMode.CLEANROOM
}

/** "3 themes" / "1 theme" — the category header's trailing count. */
private fun themeCountLabel(themes: List<ThemeOption>): String =
    if (themes.size == 1) "1 theme" else "${themes.size} themes"

/** A collapsible rack sub-section: tappable header expands/collapses its child theme cards. */
@Composable
private fun ThemeCategory(
    title: String,
    subtitle: String,
    themes: List<ThemeOption>,
    selectedMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val view = LocalView.current
    val containsSelected = themes.any { it.mode == selectedMode }
    // Auto-expand the category that contains the selected theme. Keyed on
    // containsSelected so picking a theme in one bucket collapses the previously
    // selected bucket instead of leaving two expanded categories on screen.
    var expanded by remember(containsSelected) { mutableStateOf(containsSelected) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = Motion.TactilePress,
        label = "chevron"
    )

    Column {
        // Tappable category header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.RadiusSm))
                .background(SurfaceCard.copy(alpha = 0.5f))
                .clickable {
                    Haptics.tick(view)
                    expanded = !expanded
                }
                .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LogicIcons.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = if (containsSelected) PhosphorCore else TextSecondary,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
            Spacer(Modifier.width(Dimens.Space2))
            Text(
                text = title,
                style = LogicLabsType.SwitchPlate,
                color = if (containsSelected) TextPrimary else TextSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = subtitle,
                style = LogicLabsType.TechnicalXs,
                color = TextTertiary,
                maxLines = 1
            )
            // Show a dot indicator when a selected theme is inside a collapsed category
            if (containsSelected && !expanded) {
                Spacer(Modifier.width(Dimens.Space1))
                val activeTheme = themes.first { it.mode == selectedMode }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(activeTheme.accentColor)
                )
            }
        }

        // Expandable theme cards
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = Dimens.Space1),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
            ) {
                themes.forEach { option ->
                    ThemeCard(
                        option = option,
                        isSelected = selectedMode == option.mode,
                        onSelect = { onSelect(option.mode) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Module scaffolding
// ---------------------------------------------------------------------------

/**
 * One rack module: chamfered panel, silkscreen legend, and its controls.
 *
 * [icon] is the module's rack-slot glyph, drawn beside the legend — it makes each
 * section scannable while scrolling and matches how the bench labels its modules.
 */
@Composable
private fun SettingsModule(
    title: String,
    subtitle: String?,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    ChamferedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Space4)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Dimens.Space2))
                }
                Column {
                    Text(
                        text = title,
                        style = LogicLabsType.SwitchPlateLg,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = LogicLabsType.BodySm,
                            color = TextTertiary,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.Space3))
            content()
        }
    }
}

/** A setting row whose control is a tactile hardware switchplate. */
@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = {
                    if (!isOn) {
                        Haptics.tick(view)
                    }
                    onToggle(!isOn)
                }
            )
            .padding(vertical = Dimens.Space1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = LogicLabsType.BodyMd,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = description,
                style = LogicLabsType.BodySm,
                color = TextTertiary,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(Dimens.Space3))
        TactileSwitchPlate(
            isOn = isOn,
            onToggle = onToggle
        )
    }
}

/**
 * Tactile hardware switchplate with embossed "ON" and "OFF" silkscreen legends.
 * Enforces the tactile contract: haptics fire strictly on OFF -> ON throw, silent on release.
 */
@Composable
private fun TactileSwitchPlate(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val thumbOffset by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = Motion.TactilePress,
        label = "switchPlateOffset"
    )

    Box(
        modifier = modifier
            .size(width = 56.dp, height = 28.dp)
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(if (isOn) PhosphorCore.copy(alpha = 0.15f) else SwitchWell)
            .border(
                width = 1.dp,
                color = if (isOn) PhosphorCore.copy(alpha = 0.8f) else SurfaceCardBorder,
                shape = RoundedCornerShape(Dimens.RadiusPill)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = {
                    if (!isOn) {
                        Haptics.tick(view)
                    }
                    onToggle(!isOn)
                }
            )
            .semantics {
                contentDescription = if (isOn) "Switch is ON" else "Switch is OFF"
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ON",
                style = LogicLabsType.TechnicalXs,
                color = if (isOn) PhosphorCore else TextTertiary.copy(alpha = 0.5f)
            )
            Text(
                text = "OFF",
                style = LogicLabsType.TechnicalXs,
                color = if (!isOn) TextTertiary else TextTertiary.copy(alpha = 0.5f)
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .graphicsLayer {
                    val travel = 56.dp.toPx() - 28.dp.toPx()
                    translationX = thumbOffset * travel
                }
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isOn) PhosphorCore else SwitchLeverShade)
                .border(
                    width = Dimens.Hairline,
                    color = if (isOn) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        )
    }
}

/** A setting row whose control is a whole-row tap. */
@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    destructive: Boolean,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val accent = if (destructive) ShortCircuitAlert else AccentCyan
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .border(Dimens.Hairline, accent.copy(alpha = 0.45f), RoundedCornerShape(Dimens.RadiusMd))
            .background(accent.copy(alpha = 0.10f))
            .clickable {
                Haptics.tick(view)
                onClick()
            }
            .padding(Dimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = LogicLabsType.BodyMd,
                color = accent,
                maxLines = 1
            )
            Text(
                text = description,
                style = LogicLabsType.BodySm,
                color = TextTertiary,
                maxLines = 3
            )
        }
    }
}

/**
 * The default jumper colour, as the insulation itself.
 *
 * A wrapping [FlowRow], not a single [Row]: `WireColor` has ten entries and a flat row
 * of 34dp swatches is ~412dp wide — wider than the panel it lives in on a phone, which
 * silently pushed the last colours off-screen. Each swatch keeps a full
 * [Dimens.MinTouchTarget] cell with a 30dp visual, matching how the bench's own palette
 * trades compact visuals for full-size targets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WireColorRow(selected: String, onSelect: (WireColor) -> Unit) {
    val view = LocalView.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space1)
    ) {
        WireColor.entries.forEach { color ->
            val isPicked = color.name == selected
            val swatch = Color(color.hexArgb)
            Box(
                modifier = Modifier
                    .size(Dimens.MinTouchTarget)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            Haptics.tick(view)
                            onSelect(color)
                        }
                    )
                    .semantics { contentDescription = "Default wire colour ${color.displayName}" },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (isPicked) Dimens.BorderMd else Dimens.Hairline,
                            color = if (isPicked) PhosphorCore else TextTertiary.copy(alpha = 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPicked) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(PhosphorCore)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Space1),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = LogicLabsType.SwitchPlate,
            color = TextTertiary,
            modifier = Modifier.width(84.dp)
        )
        Text(
            text = value,
            style = LogicLabsType.BodySm,
            color = TextPrimary
        )
    }
}

@Composable
private fun ResetPreferencesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BenchDialog(
        onDismissRequest = onDismiss,
        icon = LogicIcons.Gear,
        title = "Reset preferences?",
        message = "Theme, sound, haptics, simulation settings and default wire routing return " +
            "to factory defaults. Verified labs, badges and saved projects are not affected.",
        actions = {
            BenchDialogAction("CANCEL", onDismiss, tone = BenchDialogTone.NEUTRAL)
            BenchDialogAction(
                "RESET PREFERENCES", onConfirm,
                leadingIcon = LogicIcons.Gear
            )
        }
    )
}

@Composable
private fun ResetProgressDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BenchDialog(
        onDismissRequest = onDismiss,
        icon = LogicIcons.Trash,
        headerTone = BenchDialogTone.DESTRUCTIVE,
        title = "Reset coursework progress?",
        message = "All 12 lab verifications, earned badges and bench-time statistics return " +
            "to zero. Preferences and saved projects are not touched. This cannot be undone.",
        actions = {
            BenchDialogAction("CANCEL", onDismiss, tone = BenchDialogTone.NEUTRAL)
            BenchDialogActionDivider()
            BenchDialogAction(
                "ERASE PROGRESS", onConfirm,
                tone = BenchDialogTone.DESTRUCTIVE,
                leadingIcon = LogicIcons.Trash
            )
        }
    )
}

@Composable
private fun CreatorProfileCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(SurfaceCard)
            .border(Dimens.Hairline, PhosphorCore.copy(alpha = 0.4f), RoundedCornerShape(Dimens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(Dimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Dimens.RadiusSm))
                .background(PhosphorCore.copy(alpha = 0.12f))
                .border(Dimens.Hairline, PhosphorCore.copy(alpha = 0.5f), RoundedCornerShape(Dimens.RadiusSm)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LogicIcons.Chip,
                contentDescription = "Creator Profile",
                tint = PhosphorCore,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(Dimens.Space3))

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
            ) {
                Text(
                    text = "ADNAN FOISAL",
                    style = LogicLabsType.SwitchPlateLg,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.RadiusPill))
                        .background(PhosphorCore.copy(alpha = 0.18f))
                        .border(Dimens.Hairline, PhosphorCore.copy(alpha = 0.6f), RoundedCornerShape(Dimens.RadiusPill))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "CREATOR",
                        style = LogicLabsType.TechnicalXs,
                        color = PhosphorCore
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = "Computer Science & Engineering · CUET",
                style = LogicLabsType.BodySm,
                color = TextSecondary,
                maxLines = 1
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Tap to read backstory & motivation",
                style = LogicLabsType.TechnicalXs,
                color = AccentCyan
            )
        }

        Icon(
            imageVector = LogicIcons.ChevronRight,
            contentDescription = "View Profile",
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AboutCreatorDialog(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    BenchDialog(
        onDismissRequest = onDismiss,
        icon = LogicIcons.Chip,
        headerTone = BenchDialogTone.POSITIVE,
        title = "About the creator",
        message = "Adnan Foisal — CUET CSE. Why Logic Labs exists, in three parts.",
        content = {
            Spacer(Modifier.height(Dimens.Space3))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space3)
            ) {
                Text(
                    text = "Hi! I'm Adnan Foisal, currently studying Computer Science & Engineering at Chittagong University of Engineering and Technology (CUET).",
                    style = LogicLabsType.BodySm,
                    color = TextPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
                    Text(
                        text = "ORIGIN & LAB MOTIVATION",
                        style = LogicLabsType.SwitchPlate,
                        color = AmberCore
                    )
                    Text(
                        text = "During our university coursework, I always felt frustrated with the tight time limits in hardware electronics labs. In a group setting, it was nearly impossible for every member to get hands-on time with the physical kit, wire up the day's experiment, and test the circuit before the lab session ended.",
                        style = LogicLabsType.BodySm,
                        color = TextSecondary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
                    Text(
                        text = "THE DIGITAL TWIN VISION",
                        style = LogicLabsType.SwitchPlate,
                        color = AccentCyan
                    )
                    Text(
                        text = "When I looked for existing circuit simulator apps and websites, none matched our actual lab trainer kit. Their breadboard layouts, jumper wiring rules, and power rail mechanics were completely different from the real equipment we used. I decided to build a true customized digital twin of the K&H IDL-800A trainer kit that would look and behave exactly like real life, letting anyone practice beforehand and implement experiments with total confidence.",
                        style = LogicLabsType.BodySm,
                        color = TextSecondary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
                    Text(
                        text = "ARCHITECTURE & CRAFT",
                        style = LogicLabsType.SwitchPlate,
                        color = PhosphorCore
                    )
                    Text(
                        text = "Logic Labs is built completely offline with zero telemetry or network dependencies. Powered by a discrete-event digital logic simulation engine in pure Kotlin and zero-allocation 2.5D Jetpack Compose canvas rendering, it replicates the authentic AD-200 tie-point topology, stepped clock, debounced pulses, and 74xx IC logic.",
                        style = LogicLabsType.BodySm,
                        color = TextSecondary
                    )
                }
            }
        },
        actions = {
            BenchDialogAction(
                "CLOSE BENCH PROFILE", onDismiss,
                tone = BenchDialogTone.POSITIVE
            )
        }
    )
}

