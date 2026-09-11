package com.logiclabs.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Live bench palette
// ---------------------------------------------------------------------------

/**
 * The theme-varying half of the palette, as one immutable snapshot.
 *
 * The rest of this file's colours are **physical** — phenolic, epoxy, phosphor, LED
 * lenses, wire insulation — and a bench photographed in daylight keeps its parts, so
 * they stay plain vals. Only the room changes: canvas, chassis, surfaces, chrome and
 * text live here and flip with the theme.
 *
 * Components keep importing the same top-level names (`ChassisBase`, `TextPrimary`,
 * …); those are now getters over [BenchPaletteState], so a read anywhere —
 * composition, default parameter, or inside a draw lambda — is a tracked snapshot
 * read and re-executes when the theme flips. That is what makes light mode real
 * without rewriting fifty call sites.
 */
@Immutable
data class BenchPalette(
    val canvasBase: Color,
    val canvasVignette: Color,
    val chassisBase: Color,
    val chassisBevelHighlight: Color,
    val chassisBevelShadow: Color,
    val chassisSilkscreen: Color,
    val chassisDivider: Color,
    val screwBody: Color,
    val screwSlot: Color,
    val screwHighlight: Color,
    val switchBezel: Color,
    val switchWell: Color,
    val crtBezel: Color,
    val surfaceCard: Color,
    val surfaceCardBorder: Color,
    val surfaceRaised: Color,
    val glassSurface: Color,
    val glassHairline: Color,
    val glassSpecular: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,
    val phosphorCore: Color = Color(0xFF22C55E),
    val phosphorBloom: Color = Color(0xFF4ADE80),
    val phosphorDim: Color = Color(0xFF14532D),
    /** Lit-LED body/aura — defaulted to the Obsidian phosphor; every palette overrides. */
    val ledOnBody: Color = Color(0xFF22C55E),
    val ledOnAura: Color = Color(0xFF4ADE80),
    val accentCyan: Color = Color(0xFF22D3EE),
    val accentCyanDim: Color = Color(0xFF0E4B57),
    val selectionHalo: Color = Color(0xFF22D3EE),
    /** Crisp core inside [selectionHalo]; white on dark palettes, dark on the light one. */
    val selectionCore: Color = Color(0xFFFFFFFF)
)

/** 1. Obsidian Stealth (default AMOLED): deep black canvas, matte stealth chassis, electric cyan & emerald phosphor. */
val ObsidianPalette = BenchPalette(
    canvasBase = Color(0xFF060709),
    canvasVignette = Color(0xFF0E1015),
    chassisBase = Color(0xFF111318),
    chassisBevelHighlight = Color(0xFF222530),
    chassisBevelShadow = Color(0xFF060709),
    chassisSilkscreen = Color(0xFF8A8F9C),
    chassisDivider = Color(0xFF1C1E26),
    screwBody = Color(0xFF4A4E57),
    screwSlot = Color(0xFF14161A),
    screwHighlight = Color(0xFF767B86),
    switchBezel = Color(0xFF2A2E37),
    switchWell = Color(0xFF0B0C10),
    crtBezel = Color(0xFF151820),
    surfaceCard = Color(0xFF171920),
    surfaceCardBorder = Color(0xFF262934),
    surfaceRaised = Color(0xFF1E212B),
    glassSurface = Color(0xB8111318),
    glassHairline = Color(0x14FFFFFF),
    glassSpecular = Color(0x0FFFFFFF),
    textPrimary = Color(0xFFECEEF2),
    textSecondary = Color(0xFF9BA1AE),
    textTertiary = Color(0xFF6A7080),
    textOnAccent = Color(0xFF060709),
    phosphorCore = Color(0xFF22C55E),
    phosphorBloom = Color(0xFF4ADE80),
    phosphorDim = Color(0xFF14532D),
    ledOnBody = Color(0xFF22C55E),
    ledOnAura = Color(0xFF4ADE80),
    accentCyan = Color(0xFF22D3EE),
    accentCyanDim = Color(0xFF0E4B57),
    selectionHalo = Color(0xFF22D3EE)
)

/** 2. Amber CRT / Vintage Tektronix: warm charcoal chassis, glowing amber phosphor, brass/gold silkscreen. */
val AmberCrtPalette = BenchPalette(
    canvasBase = Color(0xFF0C0A09),
    canvasVignette = Color(0xFF171412),
    chassisBase = Color(0xFF1C1917),
    chassisBevelHighlight = Color(0xFF2E2824),
    chassisBevelShadow = Color(0xFF090807),
    chassisSilkscreen = Color(0xFFD97706),
    chassisDivider = Color(0xFF292524),
    screwBody = Color(0xFF574E45),
    screwSlot = Color(0xFF191614),
    screwHighlight = Color(0xFF8C7E70),
    switchBezel = Color(0xFF332B25),
    switchWell = Color(0xFF120E0C),
    crtBezel = Color(0xFF1F1A17),
    surfaceCard = Color(0xFF231F1C),
    surfaceCardBorder = Color(0xFF3B322B),
    surfaceRaised = Color(0xFF2B2521),
    glassSurface = Color(0xB81C1917),
    glassHairline = Color(0x26F59E0B),
    glassSpecular = Color(0x14F59E0B),
    textPrimary = Color(0xFFFDE68A),
    textSecondary = Color(0xFFD97706),
    textTertiary = Color(0xFF92400E),
    textOnAccent = Color(0xFF1C1917),
    phosphorCore = Color(0xFFF59E0B),
    phosphorBloom = Color(0xFFFBBF24),
    phosphorDim = Color(0xFF4A2E05),
    ledOnBody = Color(0xFFF59E0B),
    ledOnAura = Color(0xFFFBBF24),
    accentCyan = Color(0xFFF59E0B),
    accentCyanDim = Color(0xFF78350F),
    selectionHalo = Color(0xFFF59E0B)
)

/** 3. HP Slate Precision: test bench slate blue-gray chassis, ice-blue phosphor, brushed-aluminum details. */
val HpSlatePalette = BenchPalette(
    canvasBase = Color(0xFF0B0F17),
    canvasVignette = Color(0xFF131B29),
    chassisBase = Color(0xFF1E293B),
    chassisBevelHighlight = Color(0xFF334155),
    chassisBevelShadow = Color(0xFF0F172A),
    chassisSilkscreen = Color(0xFF94A3B8),
    chassisDivider = Color(0xFF293548),
    screwBody = Color(0xFF64748B),
    screwSlot = Color(0xFF0F172A),
    screwHighlight = Color(0xFF94A3B8),
    switchBezel = Color(0xFF334155),
    switchWell = Color(0xFF0F172A),
    crtBezel = Color(0xFF1E293B),
    surfaceCard = Color(0xFF243247),
    surfaceCardBorder = Color(0xFF384963),
    surfaceRaised = Color(0xFF2D3C54),
    glassSurface = Color(0xB81E293B),
    glassHairline = Color(0x2638BDF8),
    glassSpecular = Color(0x1438BDF8),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    textOnAccent = Color(0xFF0B0F17),
    phosphorCore = Color(0xFF38BDF8),
    phosphorBloom = Color(0xFF7DD3FC),
    phosphorDim = Color(0xFF0369A1),
    ledOnBody = Color(0xFF38BDF8),
    ledOnAura = Color(0xFF7DD3FC),
    accentCyan = Color(0xFF38BDF8),
    accentCyanDim = Color(0xFF0C4A6E),
    selectionHalo = Color(0xFF38BDF8)
)

/** 4. Cleanroom White: architectural paper white canvas, anodized aluminum borders, graphite silkscreen. */
val CleanroomPalette = BenchPalette(
    canvasBase = Color(0xFFFAFAFA),
    canvasVignette = Color(0xFFF1F5F9),
    chassisBase = Color(0xFFFFFFFF),
    // A hair above pure white so bevel highlights read on the white chassis (the old
    // white-on-white bevel collapsed ChamferedPanel edges entirely).
    chassisBevelHighlight = Color(0xFFF8FAFC),
    chassisBevelShadow = Color(0xFFCBD5E1),
    chassisSilkscreen = Color(0xFF475569),
    // Dividers at 0xFFE2E8F0 were 1.23:1 on the white chassis — sheet hairlines, drag
    // handles and rules vanished. One step darker restores them.
    chassisDivider = Color(0xFFCBD5E1),
    screwBody = Color(0xFF94A3B8),
    screwSlot = Color(0xFF475569),
    screwHighlight = Color(0xFFE2E8F0),
    switchBezel = Color(0xFFE2E8F0),
    // F1F5F9 was 1.10:1 on white — switch/LED recesses were invisible.
    switchWell = Color(0xFFD9E0EA),
    crtBezel = Color(0xFFE2E8F0),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceCardBorder = Color(0xFFE2E8F0),
    surfaceRaised = Color(0xFFF8FAFC),
    // Tinted frost + a stronger hairline: pure white glass was 1.03:1 against the
    // canvas, so GlassSurface lost both its edge and its layering.
    glassSurface = Color(0xD9F1F5F9),
    glassHairline = Color(0x330F172A),
    glassSpecular = Color(0x0D0F172A),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF64748B),
    textOnAccent = Color(0xFFFFFFFF),
    phosphorCore = Color(0xFF2563EB),
    phosphorBloom = Color(0xFF60A5FA),
    phosphorDim = Color(0xFF1D4ED8),
    ledOnBody = Color(0xFF2563EB),
    ledOnAura = Color(0xFF60A5FA),
    accentCyan = Color(0xFF0284C7),
    accentCyanDim = Color(0xFF075985),
    selectionHalo = Color(0xFF0284C7),
    // White selection cores washed out on white phenolic; near-black reads crisply.
    selectionCore = Color(0xFF0F172A)
)

/** 5. Cyberpunk Neon: midnight synthwave chassis, laser cyan phosphor, electric magenta silkscreen & accents. */
val CyberpunkNeonPalette = BenchPalette(
    canvasBase = Color(0xFF07040D),
    canvasVignette = Color(0xFF130924),
    chassisBase = Color(0xFF140C24),
    chassisBevelHighlight = Color(0xFF2E1B4E),
    chassisBevelShadow = Color(0xFF080410),
    chassisSilkscreen = Color(0xFFF43F5E),
    chassisDivider = Color(0xFF261540),
    screwBody = Color(0xFF582A72),
    screwSlot = Color(0xFF120520),
    screwHighlight = Color(0xFF9D4EDD),
    switchBezel = Color(0xFF2B1445),
    switchWell = Color(0xFF0B0414),
    crtBezel = Color(0xFF1A0E30),
    surfaceCard = Color(0xFF1E1035),
    surfaceCardBorder = Color(0xFF3B1D64),
    surfaceRaised = Color(0xFF281447),
    glassSurface = Color(0xB8140C24),
    glassHairline = Color(0x33F43F5E),
    glassSpecular = Color(0x1AF43F5E),
    textPrimary = Color(0xFFFDF2F8),
    textSecondary = Color(0xFFF472B6),
    textTertiary = Color(0xFF9D4EDD),
    textOnAccent = Color(0xFF07040D),
    phosphorCore = Color(0xFF06B6D4),
    phosphorBloom = Color(0xFF22D3EE),
    phosphorDim = Color(0xFF083344),
    ledOnBody = Color(0xFF06B6D4),
    ledOnAura = Color(0xFF22D3EE),
    accentCyan = Color(0xFFF43F5E),
    accentCyanDim = Color(0xFF881337),
    selectionHalo = Color(0xFF06B6D4)
)

/** 6. Tokyo Night: midnight indigo chassis, deep storm surfaces, neon blue/purple phosphor. */
val TokyoNightPalette = BenchPalette(
    canvasBase = Color(0xFF0E101A),
    canvasVignette = Color(0xFF16192B),
    chassisBase = Color(0xFF1A1B26),
    chassisBevelHighlight = Color(0xFF292E42),
    chassisBevelShadow = Color(0xFF0F1017),
    chassisSilkscreen = Color(0xFF7AA2F7),
    // Divider was exactly the surfaceCard colour (24283B), so rules and bezels were
    // indistinguishable from cards; one step lighter restores the separation.
    chassisDivider = Color(0xFF2F3550),
    screwBody = Color(0xFF414868),
    screwSlot = Color(0xFF10121C),
    screwHighlight = Color(0xFF565F89),
    switchBezel = Color(0xFF24283B),
    switchWell = Color(0xFF13141F),
    crtBezel = Color(0xFF1A1B26),
    surfaceCard = Color(0xFF24283B),
    surfaceCardBorder = Color(0xFF3B4261),
    surfaceRaised = Color(0xFF2A3048),
    glassSurface = Color(0xB81A1B26),
    glassHairline = Color(0x267AA2F7),
    glassSpecular = Color(0x147AA2F7),
    textPrimary = Color(0xFFC0CAF5),
    textSecondary = Color(0xFF9AA5CE),
    textTertiary = Color(0xFF565F89),
    textOnAccent = Color(0xFF1A1B26),
    phosphorCore = Color(0xFF7AA2F7),
    phosphorBloom = Color(0xFFBB9AF7),
    phosphorDim = Color(0xFF1F2335),
    ledOnBody = Color(0xFF7AA2F7),
    ledOnAura = Color(0xFFBB9AF7),
    accentCyan = Color(0xFF7DCFFF),
    accentCyanDim = Color(0xFF1B3B54),
    selectionHalo = Color(0xFF7AA2F7)
)

/** 7. Solarized Dark: classic deep cyan-teal base, rich warm accents, legendary terminal contrast. */
val SolarizedDarkPalette = BenchPalette(
    canvasBase = Color(0xFF001E26),
    canvasVignette = Color(0xFF002B36),
    chassisBase = Color(0xFF073642),
    chassisBevelHighlight = Color(0xFF0D4B5C),
    chassisBevelShadow = Color(0xFF001A21),
    chassisSilkscreen = Color(0xFF2AA198),
    chassisDivider = Color(0xFF0A3F4D),
    screwBody = Color(0xFF586E75),
    screwSlot = Color(0xFF001E26),
    screwHighlight = Color(0xFF657B83),
    switchBezel = Color(0xFF0A3F4D),
    switchWell = Color(0xFF00212B),
    crtBezel = Color(0xFF073642),
    surfaceCard = Color(0xFF094352),
    surfaceCardBorder = Color(0xFF125666),
    surfaceRaised = Color(0xFF0B4F60),
    glassSurface = Color(0xB8073642),
    glassHairline = Color(0x262AA198),
    glassSpecular = Color(0x142AA198),
    textPrimary = Color(0xFFFDF6E3),
    // 93A1A1 on the 094352 card measured 4.06:1 — under the 4.5 body-text floor.
    textSecondary = Color(0xFFA8B8B8),
    textTertiary = Color(0xFF586E75),
    textOnAccent = Color(0xFF002B36),
    phosphorCore = Color(0xFF859900),
    phosphorBloom = Color(0xFFB58900),
    phosphorDim = Color(0xFF053218),
    ledOnBody = Color(0xFF859900),
    ledOnAura = Color(0xFFB58900),
    // 2AA198 as accent text on card measured 3.43:1 (2.89:1 on the raised
    // SegmentedControl pill); one brightness step clears both.
    accentCyan = Color(0xFF35C0B3),
    accentCyanDim = Color(0xFF0B4644),
    selectionHalo = Color(0xFF2AA198)
)

/** 8. Vintage British Lab: 1980s university engineering dark forest enamel chassis, brass screws, emerald phosphor. */
val VintageBritishLabPalette = BenchPalette(
    canvasBase = Color(0xFF0A140F),
    canvasVignette = Color(0xFF11211A),
    chassisBase = Color(0xFF16281F),
    chassisBevelHighlight = Color(0xFF233F32),
    chassisBevelShadow = Color(0xFF0A1510),
    chassisSilkscreen = Color(0xFF34D399),
    chassisDivider = Color(0xFF1E362A),
    screwBody = Color(0xFF7A6836),
    screwSlot = Color(0xFF1C160B),
    screwHighlight = Color(0xFFA68E4C),
    switchBezel = Color(0xFF1F382B),
    switchWell = Color(0xFF0B1711),
    crtBezel = Color(0xFF16281F),
    surfaceCard = Color(0xFF1C3328),
    surfaceCardBorder = Color(0xFF2B4D3D),
    surfaceRaised = Color(0xFF223D30),
    glassSurface = Color(0xB816281F),
    glassHairline = Color(0x2634D399),
    glassSpecular = Color(0x1434D399),
    textPrimary = Color(0xFFECFDF5),
    textSecondary = Color(0xFF6EE7B7),
    textTertiary = Color(0xFF047857),
    textOnAccent = Color(0xFF0A140F),
    phosphorCore = Color(0xFF10B981),
    phosphorBloom = Color(0xFF34D399),
    phosphorDim = Color(0xFF064E3B),
    ledOnBody = Color(0xFF10B981),
    ledOnAura = Color(0xFF34D399),
    accentCyan = Color(0xFF34D399),
    accentCyanDim = Color(0xFF065F46),
    selectionHalo = Color(0xFF34D399)
)

/** 9. Titanium Frost: aerospace brushed titanium chassis, glacial frost-cyan phosphor, diamond lab precision. */
val TitaniumFrostPalette = BenchPalette(
    canvasBase = Color(0xFF0D1117),
    canvasVignette = Color(0xFF151B24),
    chassisBase = Color(0xFF1A202C),
    chassisBevelHighlight = Color(0xFF2D3748),
    chassisBevelShadow = Color(0xFF0F131A),
    chassisSilkscreen = Color(0xFF67E8F9),
    chassisDivider = Color(0xFF263040),
    screwBody = Color(0xFF718096),
    screwSlot = Color(0xFF12161E),
    screwHighlight = Color(0xFFA0AEC0),
    switchBezel = Color(0xFF283244),
    switchWell = Color(0xFF10141C),
    crtBezel = Color(0xFF1A202C),
    surfaceCard = Color(0xFF222B3B),
    surfaceCardBorder = Color(0xFF35425B),
    surfaceRaised = Color(0xFF293447),
    glassSurface = Color(0xB81A202C),
    glassHairline = Color(0x2667E8F9),
    glassSpecular = Color(0x1467E8F9),
    textPrimary = Color(0xFFF0F9FF),
    textSecondary = Color(0xFFBAE6FD),
    textTertiary = Color(0xFF64748B),
    textOnAccent = Color(0xFF0D1117),
    phosphorCore = Color(0xFF38BDF8),
    phosphorBloom = Color(0xFF67E8F9),
    phosphorDim = Color(0xFF075985),
    ledOnBody = Color(0xFF38BDF8),
    ledOnAura = Color(0xFF67E8F9),
    accentCyan = Color(0xFF67E8F9),
    accentCyanDim = Color(0xFF0E4A5C),
    selectionHalo = Color(0xFF67E8F9)
)

/** Default dark palette alias. */
val DarkBenchPalette = ObsidianPalette

/** Default light palette alias. */
val LightBenchPalette = CleanroomPalette

/**
 * The live palette. [LogicLabsTheme] is the only writer (a guarded, idempotent write,
 * so recomposition with an unchanged theme never invalidates anything); every token
 * getter below reads it.
 */
val BenchPaletteState: MutableState<BenchPalette> = mutableStateOf(ObsidianPalette)

// --- Live token getters (theme-varying) --------------------------------------

val CanvasBase: Color get() = BenchPaletteState.value.canvasBase
val CanvasVignette: Color get() = BenchPaletteState.value.canvasVignette
val CanvasBackground: Color get() = BenchPaletteState.value.canvasBase

val ChassisBase: Color get() = BenchPaletteState.value.chassisBase
val ChassisBevelHighlight: Color get() = BenchPaletteState.value.chassisBevelHighlight
val ChassisBevelShadow: Color get() = BenchPaletteState.value.chassisBevelShadow
val ChassisSilkscreen: Color get() = BenchPaletteState.value.chassisSilkscreen
val ChassisDivider: Color get() = BenchPaletteState.value.chassisDivider
val PanelBackground: Color get() = BenchPaletteState.value.chassisBase

val ScrewBody: Color get() = BenchPaletteState.value.screwBody
val ScrewSlot: Color get() = BenchPaletteState.value.screwSlot
val ScrewHighlight: Color get() = BenchPaletteState.value.screwHighlight

val SurfaceCard: Color get() = BenchPaletteState.value.surfaceCard
val SurfaceCardBorder: Color get() = BenchPaletteState.value.surfaceCardBorder
val SurfaceRaised: Color get() = BenchPaletteState.value.surfaceRaised
val GlassSurfaceColor: Color get() = BenchPaletteState.value.glassSurface
val GlassHairline: Color get() = BenchPaletteState.value.glassHairline
val GlassSpecular: Color get() = BenchPaletteState.value.glassSpecular

val TextPrimary: Color get() = BenchPaletteState.value.textPrimary
val TextSecondary: Color get() = BenchPaletteState.value.textSecondary
val TextTertiary: Color get() = BenchPaletteState.value.textTertiary
val TextOnAccent: Color get() = BenchPaletteState.value.textOnAccent

val SwitchBezel: Color get() = BenchPaletteState.value.switchBezel
val SwitchWell: Color get() = BenchPaletteState.value.switchWell
val CrtBezel: Color get() = BenchPaletteState.value.crtBezel

// ---------------------------------------------------------------------------
// Physical colours (identical under any room light)
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Breadboard (phenolic resin + spring-clip contacts)
// ---------------------------------------------------------------------------
val BreadboardPhenolic = Color(0xFFE8E6DB)
val BreadboardPhenolicShade = Color(0xFFD8D6CB)
val BreadboardPlastic = BreadboardPhenolic
val ContactClipMetal = Color(0xFF3A3A3C)
val ContactClipRecess = Color(0xFF1A1A1C)
val ContactClipSpecular = Color(0x33FFFFFF)
val BreadboardSocket = ContactClipMetal
val BreadboardSocketHole = ContactClipRecess
val BreadboardSilkscreen = Color(0xFF6B6F7A)
val BreadboardSilkscreenMajor = Color(0xFF2A2C33)
val TrenchShadow = Color(0xFFBFBDB2)

// Bus lines
val BusVcc = Color(0xFFDC2626)
val BusGnd = Color(0xFF2563EB)
val RailVccRed = BusVcc
val RailGndBlue = BusGnd

// ---------------------------------------------------------------------------
// DIP packages
// ---------------------------------------------------------------------------
val ChipEpoxy = Color(0xFF141416)
val ChipEpoxyTop = Color(0xFF1E1E22)
val ChipBodyCharcoal = ChipEpoxy
val ChipBodyChamfer = Color(0xFF32343B)
val ChipLeadSilver = Color(0xFFC8CCD4)
val ChipLeadShadow = Color(0xFF5E626C)
val ChipSilkscreenText = Color(0xFFD6D8DE)
val ChipSilkscreenEmboss = Color(0xFF08080A)
val ChipPin1Dot = Color(0xFF9AA0AC)
val ChipNotchShadow = Color(0xFF090A0C)

// ---------------------------------------------------------------------------
// Phosphor / Amber (displays, traces, telemetry)
// ---------------------------------------------------------------------------
val PhosphorCore: Color get() = BenchPaletteState.value.phosphorCore
val PhosphorBloom: Color get() = BenchPaletteState.value.phosphorBloom
val PhosphorDim: Color get() = BenchPaletteState.value.phosphorDim
val AmberCore = Color(0xFFF59E0B)
val AmberBloom = Color(0xFFFBBF24)
val AmberDim = Color(0xFF4A2E05)
val CrtScreen = Color(0xFF050A07)
val CrtGraticule = Color(0xFF16281C)
val CrtGraticuleAxis = Color(0xFF244032)

// ---------------------------------------------------------------------------
// Illuminated status indicators (LED lenses)
// ---------------------------------------------------------------------------
val LedOnCore = Color(0xFFEAFFF2)
// Getters, not plain vals: a plain `val LedOnBody = PhosphorCore` froze the Obsidian
// green at file-init, so lit LEDs rendered Obsidian-green in the eight other themes.
val LedOnBody: Color get() = PhosphorCore
val LedOnAura: Color get() = PhosphorBloom
val LedOffForest = Color(0xFF0A2014)
val LedOffRuby = Color(0xFF2A0A0C)
val LedLensSpecular = Color(0x40FFFFFF)
val LedBezelRing = Color(0xFF3A3E47)
val LedUnlit = LedOffForest
val LedGlowRed = Color(0xFFEF4444)
val LedGlowGreen: Color get() = PhosphorCore
val LedGlowAmber = AmberCore
val LedGlowCyan: Color get() = AccentCyan

// Neon pilot lamp (master power)
val NeonPilotOn = Color(0xFFFF3355)
val NeonPilotOff = Color(0xFF2A0A0E)

// ---------------------------------------------------------------------------
// Seven-segment modules
// ---------------------------------------------------------------------------
val SevenSegBezel = Color(0xFF0D0E11)
val SevenSegUnlit = Color(0x8C2A0A0C)
val SevenSegLit = Color(0xFFFF2233)
val SevenSegGlow = Color(0x66FF2233)
val SevenSegLeak = Color(0x1AFF2233)

// ---------------------------------------------------------------------------
// Switch hardware (the levers are physical; bezels/wells live in the palette)
// ---------------------------------------------------------------------------
val SwitchLeverLit = Color(0xFFD6DAE2)
val SwitchLeverShade = Color(0xFF71767F)
val SwitchLeverEdge = Color(0xFF44484F)
val MasterSwitchOn: Color get() = PhosphorCore
val MasterSwitchOff = Color(0xFFB91C1C)
val PulserDome = Color(0xFF3B4048)
val PulserDomePressed = Color(0xFF23262B)
val PulserDomeLive = AmberCore

// ---------------------------------------------------------------------------
// Accents & diagnostics
// ---------------------------------------------------------------------------
val AccentCyan: Color get() = BenchPaletteState.value.accentCyan
val AccentCyanDim: Color get() = BenchPaletteState.value.accentCyanDim
val ThermalGlow = Color(0xFFFF5722)
val ShortCircuitAlert = Color(0xFFEF4444)
val FloatingWarning = AmberCore
val SuccessGreen: Color get() = PhosphorCore
val SelectionHalo: Color get() = BenchPaletteState.value.selectionHalo

/**
 * The crisp core drawn inside a selection halo (selected wire, endpoint handles).
 * White on dark palettes; near-black on the light CLEANROOM palette, where a white
 * core on white phenolic was ~1.1:1 and the selection read as a smudge.
 */
val SelectionCore: Color get() = BenchPaletteState.value.selectionCore

/**
 * Single source of truth for jumper-wire colours.
 *
 * The insulation colour is authoritative in [com.logiclabs.core.bridge.model.WireColor.hexArgb];
 * this maps a raw ARGB long to a Compose [Color] so the render layer never re-declares palettes.
 * The previous duplicate `Wire*` token block disagreed with the model values and is gone.
 */
fun wireColorOf(hexArgb: Long): Color = Color(hexArgb)

/** Insulation sheen stroke drawn atop a wire, from a top-left virtual light source. */
val WireSpecular = Color(0x59FFFFFF)

/** Soft contact shadow cast by wires onto the board. */
val WireShadow = Color(0x40000000)
