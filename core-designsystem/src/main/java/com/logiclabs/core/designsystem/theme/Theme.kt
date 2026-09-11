package com.logiclabs.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class HardwareTheme {
    OBSIDIAN,
    AMBER_CRT,
    HP_SLATE,
    CLEANROOM,
    CYBERPUNK_NEON,
    TOKYO_NIGHT,
    SOLARIZED_DARK,
    VINTAGE_BRITISH_LAB,
    TITANIUM_FROST
}

private val ObsidianColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = TextOnAccent,
    primaryContainer = AccentCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = PhosphorCore,
    onSecondary = TextOnAccent,
    secondaryContainer = PhosphorDim,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberCore,
    onTertiary = TextOnAccent,
    tertiaryContainer = AmberDim,
    onTertiaryContainer = TextPrimary,
    background = ObsidianPalette.canvasBase,
    onBackground = ObsidianPalette.textPrimary,
    surface = ObsidianPalette.chassisBase,
    onSurface = ObsidianPalette.textPrimary,
    surfaceVariant = ObsidianPalette.surfaceCard,
    onSurfaceVariant = ObsidianPalette.textSecondary,
    surfaceContainer = ObsidianPalette.surfaceCard,
    surfaceContainerHigh = ObsidianPalette.surfaceRaised,
    outline = ObsidianPalette.surfaceCardBorder,
    outlineVariant = ObsidianPalette.chassisDivider,
    error = ShortCircuitAlert,
    onError = TextOnAccent,
    scrim = Color(0xCC000000)
)

private val AmberCrtColorScheme = darkColorScheme(
    primary = AmberCore,
    onPrimary = Color(0xFF1C1917),
    primaryContainer = Color(0xFF451A03),
    onPrimaryContainer = Color(0xFFFDE68A),
    secondary = Color(0xFFD97706),
    onSecondary = Color(0xFF1C1917),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF1C1917),
    background = AmberCrtPalette.canvasBase,
    onBackground = AmberCrtPalette.textPrimary,
    surface = AmberCrtPalette.chassisBase,
    onSurface = AmberCrtPalette.textPrimary,
    surfaceVariant = AmberCrtPalette.surfaceCard,
    onSurfaceVariant = AmberCrtPalette.textSecondary,
    surfaceContainer = AmberCrtPalette.surfaceCard,
    surfaceContainerHigh = AmberCrtPalette.surfaceRaised,
    outline = AmberCrtPalette.surfaceCardBorder,
    outlineVariant = AmberCrtPalette.chassisDivider,
    error = ShortCircuitAlert,
    onError = Color.White,
    scrim = Color(0xCC000000)
)

private val HpSlateColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0B0F17),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFF0F9FF),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFF0B0F17),
    secondaryContainer = Color(0xFF075985),
    onSecondaryContainer = Color(0xFFF0F9FF),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF0B0F17),
    background = HpSlatePalette.canvasBase,
    onBackground = HpSlatePalette.textPrimary,
    surface = HpSlatePalette.chassisBase,
    onSurface = HpSlatePalette.textPrimary,
    surfaceVariant = HpSlatePalette.surfaceCard,
    onSurfaceVariant = HpSlatePalette.textSecondary,
    surfaceContainer = HpSlatePalette.surfaceCard,
    surfaceContainerHigh = HpSlatePalette.surfaceRaised,
    outline = HpSlatePalette.surfaceCardBorder,
    outlineVariant = HpSlatePalette.chassisDivider,
    error = ShortCircuitAlert,
    onError = Color.White,
    scrim = Color(0xCC000000)
)

private val CleanroomColorScheme = lightColorScheme(
    primary = Color(0xFF0F172A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color(0xFFFFFFFF),
    background = CleanroomPalette.canvasBase,
    onBackground = CleanroomPalette.textPrimary,
    surface = CleanroomPalette.chassisBase,
    onSurface = CleanroomPalette.textPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = CleanroomPalette.textSecondary,
    outline = CleanroomPalette.surfaceCardBorder,
    outlineVariant = CleanroomPalette.chassisDivider,
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF)
)

private val CyberpunkNeonColorScheme = darkColorScheme(
    primary = Color(0xFFF43F5E),
    onPrimary = Color(0xFF07040D),
    primaryContainer = Color(0xFF881337),
    onPrimaryContainer = Color(0xFFFDF2F8),
    secondary = Color(0xFF06B6D4),
    onSecondary = Color(0xFF07040D),
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFA855F7),
    onTertiary = Color(0xFF07040D),
    background = CyberpunkNeonPalette.canvasBase,
    onBackground = CyberpunkNeonPalette.textPrimary,
    surface = CyberpunkNeonPalette.chassisBase,
    onSurface = CyberpunkNeonPalette.textPrimary,
    surfaceVariant = CyberpunkNeonPalette.surfaceCard,
    onSurfaceVariant = CyberpunkNeonPalette.textSecondary,
    surfaceContainer = CyberpunkNeonPalette.surfaceCard,
    surfaceContainerHigh = CyberpunkNeonPalette.surfaceRaised,
    outline = CyberpunkNeonPalette.surfaceCardBorder,
    outlineVariant = CyberpunkNeonPalette.chassisDivider,
    error = ShortCircuitAlert,
    onError = Color.White,
    scrim = Color(0xCC000000)
)

private val TokyoNightColorScheme = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF1A1B26),
    primaryContainer = Color(0xFF1F2335),
    onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFFBB9AF7),
    onSecondary = Color(0xFF1A1B26),
    secondaryContainer = Color(0xFF292E42),
    onSecondaryContainer = Color(0xFFE0AF68),
    tertiary = Color(0xFF7DCFFF),
    onTertiary = Color(0xFF1A1B26),
    background = TokyoNightPalette.canvasBase,
    onBackground = TokyoNightPalette.textPrimary,
    surface = TokyoNightPalette.chassisBase,
    onSurface = TokyoNightPalette.textPrimary,
    surfaceVariant = TokyoNightPalette.surfaceCard,
    onSurfaceVariant = TokyoNightPalette.textSecondary,
    surfaceContainer = TokyoNightPalette.surfaceCard,
    surfaceContainerHigh = TokyoNightPalette.surfaceRaised,
    outline = TokyoNightPalette.surfaceCardBorder,
    outlineVariant = TokyoNightPalette.chassisDivider,
    error = ShortCircuitAlert,
    onError = Color.White,
    scrim = Color(0xCC000000)
)

private val SolarizedDarkColorScheme = darkColorScheme(
    primary = Color(0xFF2AA198),
    onPrimary = Color(0xFF002B36),
    primaryContainer = Color(0xFF073642),
    onPrimaryContainer = Color(0xFFFDF6E3),
    secondary = Color(0xFF859900),
    onSecondary = Color(0xFF002B36),
    secondaryContainer = Color(0xFF0B4644),
    onSecondaryContainer = Color(0xFFEEE8D5),
    tertiary = Color(0xFFB58900),
    onTertiary = Color(0xFF002B36),
    background = SolarizedDarkPalette.canvasBase,
    onBackground = SolarizedDarkPalette.textPrimary,
    surface = SolarizedDarkPalette.chassisBase,
    onSurface = SolarizedDarkPalette.textPrimary,
    surfaceVariant = SolarizedDarkPalette.surfaceCard,
    onSurfaceVariant = SolarizedDarkPalette.textSecondary,
    surfaceContainer = SolarizedDarkPalette.surfaceCard,
    surfaceContainerHigh = SolarizedDarkPalette.surfaceRaised,
    outline = SolarizedDarkPalette.surfaceCardBorder,
    outlineVariant = SolarizedDarkPalette.chassisDivider,
    error = Color(0xFFDC322F),
    onError = Color.White,
    scrim = Color(0xCC000000)
)

private val VintageBritishLabColorScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF0A140F),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFECFDF5),
    secondary = Color(0xFF10B981),
    onSecondary = Color(0xFF0A140F),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFA68E4C),
    onTertiary = Color(0xFF0A140F),
    background = VintageBritishLabPalette.canvasBase,
    onBackground = VintageBritishLabPalette.textPrimary,
    surface = VintageBritishLabPalette.chassisBase,
    onSurface = VintageBritishLabPalette.textPrimary,
    surfaceVariant = VintageBritishLabPalette.surfaceCard,
    onSurfaceVariant = VintageBritishLabPalette.textSecondary,
    surfaceContainer = VintageBritishLabPalette.surfaceCard,
    surfaceContainerHigh = VintageBritishLabPalette.surfaceRaised,
    outline = VintageBritishLabPalette.surfaceCardBorder,
    outlineVariant = VintageBritishLabPalette.chassisDivider,
    error = ShortCircuitAlert,
    onError = Color.White,
    scrim = Color(0xCC000000)
)

private val TitaniumFrostColorScheme = darkColorScheme(
    primary = Color(0xFF67E8F9),
    onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF0E4A5C),
    onPrimaryContainer = Color(0xFFF0F9FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0D1117),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFA0AEC0),
    onTertiary = Color(0xFF0D1117),
    background = TitaniumFrostPalette.canvasBase,
    onBackground = TitaniumFrostPalette.textPrimary,
    surface = TitaniumFrostPalette.chassisBase,
    onSurface = TitaniumFrostPalette.textPrimary,
    surfaceVariant = TitaniumFrostPalette.surfaceCard,
    onSurfaceVariant = TitaniumFrostPalette.textSecondary,
    surfaceContainer = TitaniumFrostPalette.surfaceCard,
    surfaceContainerHigh = TitaniumFrostPalette.surfaceRaised,
    outline = TitaniumFrostPalette.surfaceCardBorder,
    outlineVariant = TitaniumFrostPalette.chassisDivider,
    error = ShortCircuitAlert,
    onError = Color.White,
    scrim = Color(0xCC000000)
)

private val DarkColorScheme = ObsidianColorScheme
private val LightColorScheme = CleanroomColorScheme

/**
 * Everything the bench needs that a Material [androidx.compose.material3.ColorScheme]
 * has no slot for.
 *
 * Material models a *document* UI: one surface, one outline, a handful of accents. It
 * has nowhere to put a bevel pair with a fixed virtual light source, a phenolic
 * breadboard, an unlit LED lens, or the specular stroke on a jumper wire. Rather than
 * abuse `surfaceTint` for a screw head, those live here and travel through
 * [LocalLogicLabsTokens].
 *
 * The whole class is [Immutable] and holds only `Color` values, so recomposition
 * scoping stays cheap.
 */
@Immutable
data class LogicLabsTokens(
    // Canvas
    val canvasBase: Color = CanvasBase,
    val canvasVignette: Color = CanvasVignette,

    // Chassis — virtual light source is TOP-LEFT everywhere in this app
    val chassisBase: Color = ChassisBase,
    val chassisBevelHighlight: Color = ChassisBevelHighlight,
    val chassisBevelShadow: Color = ChassisBevelShadow,
    val chassisSilkscreen: Color = ChassisSilkscreen,
    val chassisDivider: Color = ChassisDivider,
    val screwBody: Color = ScrewBody,
    val screwSlot: Color = ScrewSlot,
    val screwHighlight: Color = ScrewHighlight,

    // Surfaces & glass
    val surfaceCard: Color = SurfaceCard,
    val surfaceCardBorder: Color = SurfaceCardBorder,
    val surfaceRaised: Color = SurfaceRaised,
    val glassSurface: Color = GlassSurfaceColor,
    val glassHairline: Color = GlassHairline,
    val glassSpecular: Color = GlassSpecular,

    // Breadboard
    val breadboardPhenolic: Color = BreadboardPhenolic,
    val breadboardPhenolicShade: Color = BreadboardPhenolicShade,
    val contactClipMetal: Color = ContactClipMetal,
    val contactClipRecess: Color = ContactClipRecess,
    val contactClipSpecular: Color = ContactClipSpecular,
    val breadboardSilkscreen: Color = BreadboardSilkscreen,
    val breadboardSilkscreenMajor: Color = BreadboardSilkscreenMajor,
    val trenchShadow: Color = TrenchShadow,
    val busVcc: Color = BusVcc,
    val busGnd: Color = BusGnd,

    // DIP packages
    val chipEpoxy: Color = ChipEpoxy,
    val chipEpoxyTop: Color = ChipEpoxyTop,
    val chipBodyChamfer: Color = ChipBodyChamfer,
    val chipLeadSilver: Color = ChipLeadSilver,
    val chipLeadShadow: Color = ChipLeadShadow,
    val chipSilkscreenText: Color = ChipSilkscreenText,
    val chipSilkscreenEmboss: Color = ChipSilkscreenEmboss,
    val chipPin1Dot: Color = ChipPin1Dot,
    val chipNotchShadow: Color = ChipNotchShadow,

    // Phosphor / amber / CRT
    val phosphorCore: Color = PhosphorCore,
    val phosphorBloom: Color = PhosphorBloom,
    val phosphorDim: Color = PhosphorDim,
    val amberCore: Color = AmberCore,
    val amberBloom: Color = AmberBloom,
    val amberDim: Color = AmberDim,
    val crtScreen: Color = CrtScreen,
    val crtGraticule: Color = CrtGraticule,
    val crtGraticuleAxis: Color = CrtGraticuleAxis,
    val crtBezel: Color = CrtBezel,

    // LED lenses
    val ledOnCore: Color = LedOnCore,
    val ledOnBody: Color = LedOnBody,
    val ledOnAura: Color = LedOnAura,
    val ledOffForest: Color = LedOffForest,
    val ledOffRuby: Color = LedOffRuby,
    val ledLensSpecular: Color = LedLensSpecular,
    val ledBezelRing: Color = LedBezelRing,
    val neonPilotOn: Color = NeonPilotOn,
    val neonPilotOff: Color = NeonPilotOff,

    // Seven-segment
    val sevenSegBezel: Color = SevenSegBezel,
    val sevenSegUnlit: Color = SevenSegUnlit,
    val sevenSegLit: Color = SevenSegLit,
    val sevenSegGlow: Color = SevenSegGlow,
    val sevenSegLeak: Color = SevenSegLeak,

    // Switch hardware
    val switchBezel: Color = SwitchBezel,
    val switchWell: Color = SwitchWell,
    val switchLeverLit: Color = SwitchLeverLit,
    val switchLeverShade: Color = SwitchLeverShade,
    val switchLeverEdge: Color = SwitchLeverEdge,
    val masterSwitchOn: Color = MasterSwitchOn,
    val masterSwitchOff: Color = MasterSwitchOff,
    val pulserDome: Color = PulserDome,
    val pulserDomePressed: Color = PulserDomePressed,
    val pulserDomeLive: Color = PulserDomeLive,

    // Wires
    val wireSpecular: Color = WireSpecular,
    val wireShadow: Color = WireShadow,

    // Text & diagnostics
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,
    val textOnAccent: Color = TextOnAccent,
    val accentCyan: Color = AccentCyan,
    val accentCyanDim: Color = AccentCyanDim,
    val thermalGlow: Color = ThermalGlow,
    val shortCircuitAlert: Color = ShortCircuitAlert,
    val floatingWarning: Color = FloatingWarning,
    val selectionHalo: Color = SelectionHalo
)

/** 1. Obsidian Stealth token set (Default AMOLED). */
val ObsidianTokens = LogicLabsTokens()

/** 2. Amber CRT / Vintage Tektronix token set. */
val AmberCrtTokens = LogicLabsTokens(
    canvasBase = AmberCrtPalette.canvasBase,
    canvasVignette = AmberCrtPalette.canvasVignette,
    chassisBase = AmberCrtPalette.chassisBase,
    chassisBevelHighlight = AmberCrtPalette.chassisBevelHighlight,
    chassisBevelShadow = AmberCrtPalette.chassisBevelShadow,
    chassisSilkscreen = AmberCrtPalette.chassisSilkscreen,
    chassisDivider = AmberCrtPalette.chassisDivider,
    screwBody = AmberCrtPalette.screwBody,
    screwSlot = AmberCrtPalette.screwSlot,
    screwHighlight = AmberCrtPalette.screwHighlight,
    switchBezel = AmberCrtPalette.switchBezel,
    switchWell = AmberCrtPalette.switchWell,
    crtBezel = AmberCrtPalette.crtBezel,
    surfaceCard = AmberCrtPalette.surfaceCard,
    surfaceCardBorder = AmberCrtPalette.surfaceCardBorder,
    surfaceRaised = AmberCrtPalette.surfaceRaised,
    glassSurface = AmberCrtPalette.glassSurface,
    glassHairline = AmberCrtPalette.glassHairline,
    glassSpecular = AmberCrtPalette.glassSpecular,
    textPrimary = AmberCrtPalette.textPrimary,
    textSecondary = AmberCrtPalette.textSecondary,
    textTertiary = AmberCrtPalette.textTertiary,
    textOnAccent = AmberCrtPalette.textOnAccent,
    phosphorCore = AmberCore,
    phosphorBloom = AmberBloom,
    phosphorDim = AmberDim,
    accentCyan = AmberCore,
    selectionHalo = AmberCore
)

/** 3. HP Slate Precision token set. */
val HpSlateTokens = LogicLabsTokens(
    canvasBase = HpSlatePalette.canvasBase,
    canvasVignette = HpSlatePalette.canvasVignette,
    chassisBase = HpSlatePalette.chassisBase,
    chassisBevelHighlight = HpSlatePalette.chassisBevelHighlight,
    chassisBevelShadow = HpSlatePalette.chassisBevelShadow,
    chassisSilkscreen = HpSlatePalette.chassisSilkscreen,
    chassisDivider = HpSlatePalette.chassisDivider,
    screwBody = HpSlatePalette.screwBody,
    screwSlot = HpSlatePalette.screwSlot,
    screwHighlight = HpSlatePalette.screwHighlight,
    switchBezel = HpSlatePalette.switchBezel,
    switchWell = HpSlatePalette.switchWell,
    crtBezel = HpSlatePalette.crtBezel,
    surfaceCard = HpSlatePalette.surfaceCard,
    surfaceCardBorder = HpSlatePalette.surfaceCardBorder,
    surfaceRaised = HpSlatePalette.surfaceRaised,
    glassSurface = HpSlatePalette.glassSurface,
    glassHairline = HpSlatePalette.glassHairline,
    glassSpecular = HpSlatePalette.glassSpecular,
    textPrimary = HpSlatePalette.textPrimary,
    textSecondary = HpSlatePalette.textSecondary,
    textTertiary = HpSlatePalette.textTertiary,
    textOnAccent = HpSlatePalette.textOnAccent,
    phosphorCore = Color(0xFF38BDF8),
    phosphorBloom = Color(0xFF7DD3FC),
    phosphorDim = Color(0xFF0369A1),
    accentCyan = Color(0xFF38BDF8),
    selectionHalo = Color(0xFF38BDF8)
)

/** 4. Cleanroom White token set (Daylight Architectural). */
val CleanroomTokens = LogicLabsTokens(
    // Canvas & vignette: cool daylight paper.
    canvasBase = Color(0xFFFAFAFA),
    canvasVignette = Color(0xFFF1F5F9),

    // Chassis: white composite panel with soft grey bevels.
    chassisBase = Color(0xFFFFFFFF),
    chassisBevelHighlight = Color(0xFFFFFFFF),
    chassisBevelShadow = Color(0xFFCBD5E1),
    chassisSilkscreen = Color(0xFF475569),
    chassisDivider = Color(0xFFE2E8F0),

    // Hardware chrome reads by contrast on a light panel.
    screwBody = Color(0xFF94A3B8),
    screwSlot = Color(0xFF475569),
    screwHighlight = Color(0xFFE2E8F0),
    switchBezel = Color(0xFFE2E8F0),
    switchWell = Color(0xFFF1F5F9),
    crtBezel = Color(0xFFE2E8F0),

    // Surfaces & glass: cards stay white over the paper-grey canvas.
    surfaceCard = Color(0xFFFFFFFF),
    surfaceCardBorder = Color(0xFFE2E8F0),
    surfaceRaised = Color(0xFFF8FAFC),
    glassSurface = Color(0xD9FFFFFF),
    glassHairline = Color(0x1A0F172A),
    glassSpecular = Color(0x0D0F172A),

    // Text: ink on paper, never pure black.
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF64748B),
    textOnAccent = Color(0xFFFFFFFF),
    phosphorCore = Color(0xFF2563EB),
    phosphorBloom = Color(0xFF60A5FA),
    phosphorDim = Color(0xFF1D4ED8),
    accentCyan = Color(0xFF0284C7),
    selectionHalo = Color(0xFF0284C7)
)

/** 5. Cyberpunk Neon token set. */
val CyberpunkNeonTokens = LogicLabsTokens(
    canvasBase = CyberpunkNeonPalette.canvasBase,
    canvasVignette = CyberpunkNeonPalette.canvasVignette,
    chassisBase = CyberpunkNeonPalette.chassisBase,
    chassisBevelHighlight = CyberpunkNeonPalette.chassisBevelHighlight,
    chassisBevelShadow = CyberpunkNeonPalette.chassisBevelShadow,
    chassisSilkscreen = CyberpunkNeonPalette.chassisSilkscreen,
    chassisDivider = CyberpunkNeonPalette.chassisDivider,
    screwBody = CyberpunkNeonPalette.screwBody,
    screwSlot = CyberpunkNeonPalette.screwSlot,
    screwHighlight = CyberpunkNeonPalette.screwHighlight,
    switchBezel = CyberpunkNeonPalette.switchBezel,
    switchWell = CyberpunkNeonPalette.switchWell,
    crtBezel = CyberpunkNeonPalette.crtBezel,
    surfaceCard = CyberpunkNeonPalette.surfaceCard,
    surfaceCardBorder = CyberpunkNeonPalette.surfaceCardBorder,
    surfaceRaised = CyberpunkNeonPalette.surfaceRaised,
    glassSurface = CyberpunkNeonPalette.glassSurface,
    glassHairline = CyberpunkNeonPalette.glassHairline,
    glassSpecular = CyberpunkNeonPalette.glassSpecular,
    textPrimary = CyberpunkNeonPalette.textPrimary,
    textSecondary = CyberpunkNeonPalette.textSecondary,
    textTertiary = CyberpunkNeonPalette.textTertiary,
    textOnAccent = CyberpunkNeonPalette.textOnAccent,
    phosphorCore = CyberpunkNeonPalette.phosphorCore,
    phosphorBloom = CyberpunkNeonPalette.phosphorBloom,
    phosphorDim = CyberpunkNeonPalette.phosphorDim,
    accentCyan = CyberpunkNeonPalette.accentCyan,
    accentCyanDim = CyberpunkNeonPalette.accentCyanDim,
    selectionHalo = CyberpunkNeonPalette.selectionHalo
)

/** 6. Tokyo Night token set. */
val TokyoNightTokens = LogicLabsTokens(
    canvasBase = TokyoNightPalette.canvasBase,
    canvasVignette = TokyoNightPalette.canvasVignette,
    chassisBase = TokyoNightPalette.chassisBase,
    chassisBevelHighlight = TokyoNightPalette.chassisBevelHighlight,
    chassisBevelShadow = TokyoNightPalette.chassisBevelShadow,
    chassisSilkscreen = TokyoNightPalette.chassisSilkscreen,
    chassisDivider = TokyoNightPalette.chassisDivider,
    screwBody = TokyoNightPalette.screwBody,
    screwSlot = TokyoNightPalette.screwSlot,
    screwHighlight = TokyoNightPalette.screwHighlight,
    switchBezel = TokyoNightPalette.switchBezel,
    switchWell = TokyoNightPalette.switchWell,
    crtBezel = TokyoNightPalette.crtBezel,
    surfaceCard = TokyoNightPalette.surfaceCard,
    surfaceCardBorder = TokyoNightPalette.surfaceCardBorder,
    surfaceRaised = TokyoNightPalette.surfaceRaised,
    glassSurface = TokyoNightPalette.glassSurface,
    glassHairline = TokyoNightPalette.glassHairline,
    glassSpecular = TokyoNightPalette.glassSpecular,
    textPrimary = TokyoNightPalette.textPrimary,
    textSecondary = TokyoNightPalette.textSecondary,
    textTertiary = TokyoNightPalette.textTertiary,
    textOnAccent = TokyoNightPalette.textOnAccent,
    phosphorCore = TokyoNightPalette.phosphorCore,
    phosphorBloom = TokyoNightPalette.phosphorBloom,
    phosphorDim = TokyoNightPalette.phosphorDim,
    accentCyan = TokyoNightPalette.accentCyan,
    accentCyanDim = TokyoNightPalette.accentCyanDim,
    selectionHalo = TokyoNightPalette.selectionHalo
)

/** 7. Solarized Dark token set. */
val SolarizedDarkTokens = LogicLabsTokens(
    canvasBase = SolarizedDarkPalette.canvasBase,
    canvasVignette = SolarizedDarkPalette.canvasVignette,
    chassisBase = SolarizedDarkPalette.chassisBase,
    chassisBevelHighlight = SolarizedDarkPalette.chassisBevelHighlight,
    chassisBevelShadow = SolarizedDarkPalette.chassisBevelShadow,
    chassisSilkscreen = SolarizedDarkPalette.chassisSilkscreen,
    chassisDivider = SolarizedDarkPalette.chassisDivider,
    screwBody = SolarizedDarkPalette.screwBody,
    screwSlot = SolarizedDarkPalette.screwSlot,
    screwHighlight = SolarizedDarkPalette.screwHighlight,
    switchBezel = SolarizedDarkPalette.switchBezel,
    switchWell = SolarizedDarkPalette.switchWell,
    crtBezel = SolarizedDarkPalette.crtBezel,
    surfaceCard = SolarizedDarkPalette.surfaceCard,
    surfaceCardBorder = SolarizedDarkPalette.surfaceCardBorder,
    surfaceRaised = SolarizedDarkPalette.surfaceRaised,
    glassSurface = SolarizedDarkPalette.glassSurface,
    glassHairline = SolarizedDarkPalette.glassHairline,
    glassSpecular = SolarizedDarkPalette.glassSpecular,
    textPrimary = SolarizedDarkPalette.textPrimary,
    textSecondary = SolarizedDarkPalette.textSecondary,
    textTertiary = SolarizedDarkPalette.textTertiary,
    textOnAccent = SolarizedDarkPalette.textOnAccent,
    phosphorCore = SolarizedDarkPalette.phosphorCore,
    phosphorBloom = SolarizedDarkPalette.phosphorBloom,
    phosphorDim = SolarizedDarkPalette.phosphorDim,
    accentCyan = SolarizedDarkPalette.accentCyan,
    accentCyanDim = SolarizedDarkPalette.accentCyanDim,
    selectionHalo = SolarizedDarkPalette.selectionHalo
)

/** 8. Vintage British Lab token set. */
val VintageBritishLabTokens = LogicLabsTokens(
    canvasBase = VintageBritishLabPalette.canvasBase,
    canvasVignette = VintageBritishLabPalette.canvasVignette,
    chassisBase = VintageBritishLabPalette.chassisBase,
    chassisBevelHighlight = VintageBritishLabPalette.chassisBevelHighlight,
    chassisBevelShadow = VintageBritishLabPalette.chassisBevelShadow,
    chassisSilkscreen = VintageBritishLabPalette.chassisSilkscreen,
    chassisDivider = VintageBritishLabPalette.chassisDivider,
    screwBody = VintageBritishLabPalette.screwBody,
    screwSlot = VintageBritishLabPalette.screwSlot,
    screwHighlight = VintageBritishLabPalette.screwHighlight,
    switchBezel = VintageBritishLabPalette.switchBezel,
    switchWell = VintageBritishLabPalette.switchWell,
    crtBezel = VintageBritishLabPalette.crtBezel,
    surfaceCard = VintageBritishLabPalette.surfaceCard,
    surfaceCardBorder = VintageBritishLabPalette.surfaceCardBorder,
    surfaceRaised = VintageBritishLabPalette.surfaceRaised,
    glassSurface = VintageBritishLabPalette.glassSurface,
    glassHairline = VintageBritishLabPalette.glassHairline,
    glassSpecular = VintageBritishLabPalette.glassSpecular,
    textPrimary = VintageBritishLabPalette.textPrimary,
    textSecondary = VintageBritishLabPalette.textSecondary,
    textTertiary = VintageBritishLabPalette.textTertiary,
    textOnAccent = VintageBritishLabPalette.textOnAccent,
    phosphorCore = VintageBritishLabPalette.phosphorCore,
    phosphorBloom = VintageBritishLabPalette.phosphorBloom,
    phosphorDim = VintageBritishLabPalette.phosphorDim,
    accentCyan = VintageBritishLabPalette.accentCyan,
    accentCyanDim = VintageBritishLabPalette.accentCyanDim,
    selectionHalo = VintageBritishLabPalette.selectionHalo
)

/** 9. Titanium Frost token set. */
val TitaniumFrostTokens = LogicLabsTokens(
    canvasBase = TitaniumFrostPalette.canvasBase,
    canvasVignette = TitaniumFrostPalette.canvasVignette,
    chassisBase = TitaniumFrostPalette.chassisBase,
    chassisBevelHighlight = TitaniumFrostPalette.chassisBevelHighlight,
    chassisBevelShadow = TitaniumFrostPalette.chassisBevelShadow,
    chassisSilkscreen = TitaniumFrostPalette.chassisSilkscreen,
    chassisDivider = TitaniumFrostPalette.chassisDivider,
    screwBody = TitaniumFrostPalette.screwBody,
    screwSlot = TitaniumFrostPalette.screwSlot,
    screwHighlight = TitaniumFrostPalette.screwHighlight,
    switchBezel = TitaniumFrostPalette.switchBezel,
    switchWell = TitaniumFrostPalette.switchWell,
    crtBezel = TitaniumFrostPalette.crtBezel,
    surfaceCard = TitaniumFrostPalette.surfaceCard,
    surfaceCardBorder = TitaniumFrostPalette.surfaceCardBorder,
    surfaceRaised = TitaniumFrostPalette.surfaceRaised,
    glassSurface = TitaniumFrostPalette.glassSurface,
    glassHairline = TitaniumFrostPalette.glassHairline,
    glassSpecular = TitaniumFrostPalette.glassSpecular,
    textPrimary = TitaniumFrostPalette.textPrimary,
    textSecondary = TitaniumFrostPalette.textSecondary,
    textTertiary = TitaniumFrostPalette.textTertiary,
    textOnAccent = TitaniumFrostPalette.textOnAccent,
    phosphorCore = TitaniumFrostPalette.phosphorCore,
    phosphorBloom = TitaniumFrostPalette.phosphorBloom,
    phosphorDim = TitaniumFrostPalette.phosphorDim,
    accentCyan = TitaniumFrostPalette.accentCyan,
    accentCyanDim = TitaniumFrostPalette.accentCyanDim,
    selectionHalo = TitaniumFrostPalette.selectionHalo
)

private val DarkTokens = ObsidianTokens
private val LightTokens = CleanroomTokens

/**
 * Bench tokens for the current theme.
 */
val LocalLogicLabsTokens = staticCompositionLocalOf { ObsidianTokens }

/**
 * Ergonomic accessor: `MaterialTheme.tokens.phosphorCore` reads the same way as
 * `MaterialTheme.colorScheme.primary`, which keeps call sites from having to know that
 * a CompositionLocal is involved.
 */
val MaterialTheme.tokens: LogicLabsTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalLogicLabsTokens.current

@Composable
fun LogicLabsTheme(
    hardwareTheme: HardwareTheme = HardwareTheme.OBSIDIAN,
    darkTheme: Boolean = (hardwareTheme != HardwareTheme.CLEANROOM),
    content: @Composable () -> Unit
) {
    val target = when (hardwareTheme) {
        HardwareTheme.OBSIDIAN -> ObsidianPalette
        HardwareTheme.AMBER_CRT -> AmberCrtPalette
        HardwareTheme.HP_SLATE -> HpSlatePalette
        HardwareTheme.CLEANROOM -> CleanroomPalette
        HardwareTheme.CYBERPUNK_NEON -> CyberpunkNeonPalette
        HardwareTheme.TOKYO_NIGHT -> TokyoNightPalette
        HardwareTheme.SOLARIZED_DARK -> SolarizedDarkPalette
        HardwareTheme.VINTAGE_BRITISH_LAB -> VintageBritishLabPalette
        HardwareTheme.TITANIUM_FROST -> TitaniumFrostPalette
    }
    if (BenchPaletteState.value != target) {
        BenchPaletteState.value = target
    }

    val colorScheme = when (hardwareTheme) {
        HardwareTheme.OBSIDIAN -> ObsidianColorScheme
        HardwareTheme.AMBER_CRT -> AmberCrtColorScheme
        HardwareTheme.HP_SLATE -> HpSlateColorScheme
        HardwareTheme.CLEANROOM -> CleanroomColorScheme
        HardwareTheme.CYBERPUNK_NEON -> CyberpunkNeonColorScheme
        HardwareTheme.TOKYO_NIGHT -> TokyoNightColorScheme
        HardwareTheme.SOLARIZED_DARK -> SolarizedDarkColorScheme
        HardwareTheme.VINTAGE_BRITISH_LAB -> VintageBritishLabColorScheme
        HardwareTheme.TITANIUM_FROST -> TitaniumFrostColorScheme
    }
    val tokens = when (hardwareTheme) {
        HardwareTheme.OBSIDIAN -> ObsidianTokens
        HardwareTheme.AMBER_CRT -> AmberCrtTokens
        HardwareTheme.HP_SLATE -> HpSlateTokens
        HardwareTheme.CLEANROOM -> CleanroomTokens
        HardwareTheme.CYBERPUNK_NEON -> CyberpunkNeonTokens
        HardwareTheme.TOKYO_NIGHT -> TokyoNightTokens
        HardwareTheme.SOLARIZED_DARK -> SolarizedDarkTokens
        HardwareTheme.VINTAGE_BRITISH_LAB -> VintageBritishLabTokens
        HardwareTheme.TITANIUM_FROST -> TitaniumFrostTokens
    }

    CompositionLocalProvider(LocalLogicLabsTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LogicLabsTypography,
            content = content
        )
    }
}

@Composable
fun LogicLabsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    LogicLabsTheme(
        hardwareTheme = if (darkTheme) HardwareTheme.OBSIDIAN else HardwareTheme.CLEANROOM,
        darkTheme = darkTheme,
        content = content
    )
}
