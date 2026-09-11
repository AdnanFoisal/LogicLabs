package com.logiclabs.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.logiclabs.core.bridge.model.PinRole
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.BusGnd
import com.logiclabs.core.designsystem.theme.BusVcc
import com.logiclabs.core.designsystem.theme.ChipBodyChamfer
import com.logiclabs.core.designsystem.theme.ChipEpoxy
import com.logiclabs.core.designsystem.theme.ChipEpoxyTop
import com.logiclabs.core.designsystem.theme.ChipLeadShadow
import com.logiclabs.core.designsystem.theme.ChipLeadSilver
import com.logiclabs.core.designsystem.theme.ChipNotchShadow
import com.logiclabs.core.designsystem.theme.ChipPin1Dot
import com.logiclabs.core.designsystem.theme.ChipSilkscreenEmboss
import com.logiclabs.core.designsystem.theme.ChipSilkscreenText
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * The datasheet DIP package diagram, drawn in the same physical language as the
 * breadboard's placed ICs — epoxy gradient body, silver gull-wing leads with
 * shoulders, left index notch, pin-1 dimple and laser-style silkscreen — instead of
 * the flat gradient rectangle the reference tab and pinout inspector used to show.
 *
 * Numbering follows the datasheet convention both call sites already used:
 * counterclockwise from the bottom-left — pin 1 starts the bottom row running
 * left→right, pin N/2+1 continues up the right side and the top row runs
 * right→left back to pin N at the top-left.
 *
 * Pin numbers are printed on the package next to each lead junction (dim
 * silkscreen); pin names and role badges sit outside the package beside their
 * leads, colour-coded by [DipPinRoleInfo].
 */
@Composable
fun DipPackageDiagram(
    partNumber: String,
    pinCount: Int,
    pinLabels: List<String>,
    pinRoles: List<PinRole>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val half = pinCount / 2

    // Geometry in dp: one 44dp column per pin per side, 13dp leads, 86dp body,
    // 30dp label zones above and below.
    val slotW = 44.dp
    val leadLen = 13.dp
    val bodyH = 86.dp
    val labelZone = 30.dp
    val pad = 10.dp
    val firstLeadInset = 26.dp
    val totalW = pad * 2 + firstLeadInset + slotW * half + 12.dp
    val totalH = labelZone * 2 + leadLen * 2 + bodyH + pad * 2

    val paints = remember { DipDiagramPaints() }

    Box(modifier = modifier.horizontalScroll(rememberScrollState())) {
        Canvas(
            modifier = Modifier
                .width(totalW)
                .height(totalH)
        ) {
            val u = 1.dp.toPx()
            val bodyLeft = pad.toPx() + 2.dp.toPx()
            val bodyTop = (pad + labelZone + leadLen).toPx()
            val bodyBottom = bodyTop + bodyH.toPx()
            val firstLeadX = bodyLeft + firstLeadInset.toPx()
            val leadStep = slotW.toPx()
            val bodyRight = firstLeadX + leadStep * (half - 1) + 24.dp.toPx()
            val bodyW = bodyRight - bodyLeft
            val cy = (bodyTop + bodyBottom) / 2f

            // --- leads first, so the body laps their shoulders -----------------------
            for (i in 0 until half) {
                val x = firstLeadX + leadStep * i
                drawLead(x, bodyTop, leadLen.toPx(), u, top = true)
                drawLead(x, bodyBottom, leadLen.toPx(), u, top = false)
            }

            // --- epoxy body --------------------------------------------------------------
            // Drop shadow.
            drawRoundRect(
                color = Color(0x40000000),
                topLeft = Offset(bodyLeft + 3 * u, bodyTop + 4 * u),
                size = Size(bodyW, bodyH.toPx()),
                cornerRadius = CornerRadius(5 * u, 5 * u)
            )
            // Epoxy gradient (same tokens as the bench's DipPainter).
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to ChipEpoxyTop,
                    0.5f to ChipEpoxy,
                    1f to ChipEpoxyTop
                ),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH.toPx()),
                cornerRadius = CornerRadius(5 * u, 5 * u)
            )
            // Bevel: light top edge, dark bottom edge, chamfer outline.
            drawLine(
                ChipBodyChamfer.copy(alpha = 0.8f),
                Offset(bodyLeft + 6 * u, bodyTop + 2.5f * u),
                Offset(bodyRight - 6 * u, bodyTop + 2.5f * u),
                1.6f * u
            )
            drawLine(
                ChipNotchShadow.copy(alpha = 0.7f),
                Offset(bodyLeft + 6 * u, bodyBottom - 2.5f * u),
                Offset(bodyRight - 6 * u, bodyBottom - 2.5f * u),
                1.6f * u
            )
            drawRoundRect(
                color = ChipLeadShadow.copy(alpha = 0.8f),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH.toPx()),
                cornerRadius = CornerRadius(5 * u, 5 * u),
                style = Stroke(1.2f * u)
            )

            // --- index notch (left edge) and pin-1 dimple -----------------------------------
            drawCircle(ChipNotchShadow, radius = 8 * u, center = Offset(bodyLeft, cy))
            drawCircle(
                ChipLeadShadow.copy(alpha = 0.9f), radius = 8 * u,
                center = Offset(bodyLeft, cy), style = Stroke(1.2f * u)
            )
            drawCircle(ChipNotchShadow, radius = 3.4f * u, center = Offset(bodyLeft + 16 * u, bodyBottom - 10 * u))
            drawCircle(ChipPin1Dot.copy(alpha = 0.6f), radius = 1.2f * u, center = Offset(bodyLeft + 16 * u, bodyBottom - 10 * u))

            // --- silkscreen: part number + house brand (embossed) ----------------------------
            val nc = drawContext.canvas.nativeCanvas
            val centerX = (bodyLeft + bodyRight) / 2f
            nc.drawText(
                "SN${partNumber}N",
                centerX, cy - 2 * u,
                paints.silkscreenPart(density.density).apply {
                    setColor(android.graphics.Color.argb(
                        255,
                        (ChipSilkscreenText.red * 255).toInt(),
                        (ChipSilkscreenText.green * 255).toInt(),
                        (ChipSilkscreenText.blue * 255).toInt()
                    ))
                }
            )
            nc.drawText(
                "LOGIC LABS",
                centerX, cy + 10 * u,
                paints.silkscreenBrand(density.density).apply {
                    setColor(android.graphics.Color.argb(
                        255,
                        (ChipSilkscreenEmboss.red * 255).toInt(),
                        (ChipSilkscreenEmboss.green * 255).toInt(),
                        (ChipSilkscreenEmboss.blue * 255).toInt()
                    ))
                }
            )

            // --- pin numbers on the body + names/badges outside -------------------------------
            for (i in 0 until half) {
                val x = firstLeadX + leadStep * i
                val bottomPin = i + 1
                val topPin = pinCount - i

                // On-body pin numbers beside each lead junction.
                paints.pinNumber(density.density).apply {
                    setColor(android.graphics.Color.argb(
                        230,
                        (ChipSilkscreenText.red * 255).toInt(),
                        (ChipSilkscreenText.green * 255).toInt(),
                        (ChipSilkscreenText.blue * 255).toInt()
                    ))
                }.let { p ->
                    nc.drawText("$bottomPin", x, bodyBottom - 8 * u, p)
                    nc.drawText("$topPin", x, bodyTop + 14 * u, p)
                }

                // Outside labels: name + role badge, colour-coded.
                drawPinLabel(nc, paints, density.density, x, bodyBottom + leadLen.toPx() + 6 * u,
                    bottomPin, pinLabels, pinRoles, top = false)
                drawPinLabel(nc, paints, density.density, x, bodyTop - leadLen.toPx() - 4 * u,
                    topPin, pinLabels, pinRoles, top = true)
            }
        }
    }
}

/** One gull-wing lead: a widening shoulder at the body junction plus the silver wing. */
private fun DrawScope.drawLead(x: Float, bodyEdgeY: Float, leadLen: Float, u: Float, top: Boolean) {
    val dir = if (top) -1f else 1f
    val leadW = 5.5f * u
    val shoulderW = 10f * u

    // Shoulder: trapezoid widening from the wing into the body.
    val shoulder = Path().apply {
        moveTo(x - leadW / 2f, bodyEdgeY + dir * 4.5f * u)
        lineTo(x + leadW / 2f, bodyEdgeY + dir * 4.5f * u)
        lineTo(x + shoulderW / 2f, bodyEdgeY - dir * 1f * u)
        lineTo(x - shoulderW / 2f, bodyEdgeY - dir * 1f * u)
        close()
    }
    drawPath(shoulder, ChipLeadSilver.copy(alpha = 0.85f))
    drawPath(shoulder, ChipLeadShadow.copy(alpha = 0.5f), style = Stroke(0.8f * u))

    // Wing: silver gradient bar with a shadowed edge.
    val wingTop = if (top) bodyEdgeY - leadLen else bodyEdgeY
    drawRoundRect(
        brush = Brush.verticalGradient(
            if (top) listOf(ChipLeadSilver, ChipLeadShadow) else listOf(ChipLeadShadow, ChipLeadSilver)
        ),
        topLeft = Offset(x - leadW / 2f, wingTop),
        size = Size(leadW, leadLen),
        cornerRadius = CornerRadius(1.5f * u, 1.5f * u)
    )
    drawLine(
        ChipLeadShadow.copy(alpha = 0.6f),
        Offset(x + leadW / 2f, wingTop),
        Offset(x + leadW / 2f, wingTop + leadLen),
        0.8f * u
    )
}

/** The outside name + role badge for one pin, centred on its lead. */
private fun drawPinLabel(
    nc: android.graphics.Canvas,
    paints: DipDiagramPaints,
    density: Float,
    x: Float,
    edgeY: Float,
    pin: Int,
    labels: List<String>,
    roles: List<PinRole>,
    top: Boolean
) {
    val name = labels.getOrNull(pin - 1) ?: "$pin"
    val role = roles.getOrNull(pin - 1) ?: PinRole.INPUT
    val (badge, color) = dipPinRoleInfo(role)

    fun toArgb(c: Color, alpha: Int = 255): Int = android.graphics.Color.argb(
        alpha,
        (c.red * 255).toInt(),
        (c.green * 255).toInt(),
        (c.blue * 255).toInt()
    )

    val namePaint = paints.pinName(density).apply { setColor(toArgb(color)) }
    val badgePaint = paints.pinBadge(density).apply { setColor(toArgb(color, 190)) }

    val nameY = if (top) edgeY - 6 * density else edgeY + 12 * density
    val badgeY = if (top) edgeY - 17 * density else edgeY + 23 * density

    nc.drawText(name, x, nameY, namePaint)
    if (role != PinRole.INPUT) {
        // INPUT is the default everywhere; only badge the special roles.
        nc.drawText(badge, x, badgeY, badgePaint)
    }
}

/** Badge text + colour for a pin role — the shared source for diagrams and legends. */
fun dipPinRoleInfo(role: PinRole): Pair<String, Color> = when (role) {
    PinRole.POWER_VCC -> "VCC" to BusVcc
    PinRole.POWER_GND -> "GND" to BusGnd
    PinRole.OUTPUT -> "OUT" to AmberCore
    PinRole.NO_CONNECT -> "NC" to TextTertiary
    PinRole.CLOCK -> "CLK" to AccentCyan
    else -> "IN" to AccentCyan
}

/** Shared, reused text paints (house rule: nothing allocated per draw). */
private class DipDiagramPaints {
    private val part = android.graphics.Paint().apply {
        isAntiAlias = true; isFakeBoldText = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private val brand = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private val number = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private val name = android.graphics.Paint().apply {
        isAntiAlias = true; isFakeBoldText = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private val badge = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.CENTER
    }

    fun silkscreenPart(density: Float) = part.apply { textSize = 11f * density }
    fun silkscreenBrand(density: Float) = brand.apply { textSize = 7f * density; letterSpacing = 0.12f }
    fun pinNumber(density: Float) = number.apply { textSize = 8f * density }
    fun pinName(density: Float) = name.apply { textSize = 9.5f * density }
    fun pinBadge(density: Float) = badge.apply { textSize = 7f * density; letterSpacing = 0.08f }
}
