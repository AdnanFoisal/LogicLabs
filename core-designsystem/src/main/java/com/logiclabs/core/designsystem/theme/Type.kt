package com.logiclabs.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.R

/**
 * Engineering monospace font family: JetBrains Mono.
 * Used across numeric telemetry, pin ordinals, hex displays, clock rates, IC designations,
 * truth table vectors, and terminal text. Tabular digits prevent jittering.
 */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_medium, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_medium, FontWeight.Bold)
)

/**
 * Hardware interface sans font family: Inter.
 * Used for chassis nameplates, switchplates, dialog titles, buttons, and coursework descriptions.
 */
val Inter = FontFamily(
    Font(R.font.inter_semibold, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold)
)

/**
 * Type scale for the Logic Labs bench.
 */
object LogicLabsType {

    // -----------------------------------------------------------------------
    // Technical (monospace) — instrumentation readouts (JetBrains Mono)
    // -----------------------------------------------------------------------

    /** Smallest legible mono. Pin ordinals, socket coordinates, per-pin badges. */
    val TechnicalXs: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        lineHeight = 11.sp
    )

    /** Default readout size. Telemetry pill values, net counters, clock rates. */
    val TechnicalSm: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp
    )

    /** Inline code, hex values, register dumps. */
    val TechnicalMd: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    /** Prominent single-value readouts (frequency dial, probe voltage). */
    val TechnicalLg: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )

    /** HMAC digests and long opaque identifiers — loosened so runs stay scannable. */
    val HashMono: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )

    // -----------------------------------------------------------------------
    // Geometric (Inter) — UI voice
    // -----------------------------------------------------------------------

    val DisplayLg: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em
    )

    val TitleMd: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em
    )

    val TitleSm: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.01).em
    )

    val BodyMd: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.01).em
    )

    val BodySm: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = (-0.01).em
    )

    val LabelSm: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    // -----------------------------------------------------------------------
    // Switchplate — silkscreened hardware legends (Inter)
    // -----------------------------------------------------------------------

    /**
     * Tracked-out legend text, the way it is screen-printed next to a rocker.
     */
    val SwitchPlate: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.12.em
    )

    /** Section headers on the chassis. Same treatment, one step up. */
    val SwitchPlateLg: TextStyle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.12.em
    )
}

/**
 * Material 3 [Typography] wired onto [LogicLabsType].
 */
val LogicLabsTypography: Typography = Typography(
    displayLarge = LogicLabsType.DisplayLg,
    titleLarge = LogicLabsType.TitleMd,
    titleMedium = LogicLabsType.TitleSm,
    titleSmall = LogicLabsType.LabelSm,
    bodyLarge = LogicLabsType.BodyMd,
    bodyMedium = LogicLabsType.BodyMd,
    bodySmall = LogicLabsType.BodySm,
    labelLarge = LogicLabsType.LabelSm,
    labelMedium = LogicLabsType.SwitchPlateLg,
    labelSmall = LogicLabsType.SwitchPlate
)

