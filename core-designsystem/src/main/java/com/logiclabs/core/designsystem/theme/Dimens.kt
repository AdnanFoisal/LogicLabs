package com.logiclabs.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing, radii and interaction dimensions for the Logic Labs bench.
 *
 * Everything is on a 4dp grid. [MinTouchTarget] is the hard floor for any
 * interactive element — including breadboard tie-points, which are hit-tested
 * in board coordinates and must be converted through the current zoom scale
 * so the *on-screen* target never falls below this value.
 */
object Dimens {
    // 4dp spacing scale
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 20.dp
    val Space6 = 24.dp
    val Space8 = 32.dp
    val Space10 = 40.dp

    // Corner radii
    val RadiusSm = 4.dp
    val RadiusMd = 8.dp
    val RadiusLg = 12.dp
    val RadiusXl = 16.dp
    val RadiusPill = 999.dp

    // Hairlines & borders
    val Hairline = 1.dp
    val BorderMd = 1.5.dp
    val BevelWidth = 1.dp

    /** Accessibility floor for every interactive element. */
    val MinTouchTarget = 48.dp

    // --- Breadboard interaction ---------------------------------------------
    /** Magnetic snap engages within this screen-space radius of a tie-point. */
    val SocketLockRadius = 10.dp

    /** Snap releases only past this radius — hysteresis stops boundary flicker. */
    val SocketReleaseRadius = 24.dp

    /** Half of [MinTouchTarget]: the effective screen-space socket hit radius. */
    val SocketHitRadius = 24.dp

    /** Wire selection tolerance, screen space. */
    val WireHitRadius = 22.dp

    // --- Loupe --------------------------------------------------------------
    val LoupeRadius = 50.dp
    val LoupeVerticalOffset = 65.dp
    val LoupeBezelWidth = 3.dp

    // --- HUD ----------------------------------------------------------------
    val CommandBarHeight = 48.dp
    val CommandBarInset = 12.dp
    // No ToolRailHeight: the rail is intrinsically sized by its segments, and a stale
    // constant here would silently clip it the moment someone reserved space with it.
    val ContextRibbonHeight = 44.dp
    val DragHandleWidth = 36.dp
    val DragHandleHeight = 4.dp

    // --- Console ------------------------------------------------------------
    val LedLensSize = 22.dp
    val SevenSegWidth = 46.dp
    val SevenSegHeight = 68.dp
    val RockerWidth = 30.dp
    val RockerHeight = 48.dp
    val PulserSize = 44.dp
    val ClockDialSize = 56.dp
    val ScrewSize = 10.dp

    // --- Elevation ----------------------------------------------------------
    val ElevationLow = 2.dp
    val ElevationMd = 8.dp
    val ElevationHigh = 20.dp
}
