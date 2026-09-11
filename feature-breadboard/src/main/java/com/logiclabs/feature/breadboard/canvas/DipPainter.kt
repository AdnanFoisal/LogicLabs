package com.logiclabs.feature.breadboard.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.designsystem.theme.ChipBodyChamfer
import com.logiclabs.core.designsystem.theme.ChipEpoxy
import com.logiclabs.core.designsystem.theme.ChipEpoxyTop
import com.logiclabs.core.designsystem.theme.ChipLeadShadow
import com.logiclabs.core.designsystem.theme.ChipLeadSilver
import com.logiclabs.core.designsystem.theme.ChipNotchShadow
import com.logiclabs.core.designsystem.theme.ChipPin1Dot
import com.logiclabs.core.designsystem.theme.ChipSilkscreenEmboss
import com.logiclabs.core.designsystem.theme.ChipSilkscreenText
import com.logiclabs.core.designsystem.theme.SelectionHalo
import com.logiclabs.core.designsystem.theme.ThermalGlow

/**
 * Paints a through-hole DIP package: matte epoxy body, gull-wing leads, a notch **cut into** the
 * body, and a laser-etched part number.
 *
 * Three fixes over the previous `DIPRenderer`:
 *  - the pin-1 notch was an arc drawn at `bodyLeft - 5f`, i.e. *outside* the silhouette, so it read
 *    as a bump. Here it is a semicircle subtracted from the body path and refilled with
 *    [ChipNotchShadow], inset within the outline;
 *  - leads were flat 4px lines. Each is now a two-segment gull wing (shoulder + foot) with a
 *    [ChipLeadSilver] top face and a [ChipLeadShadow] underside;
 *  - the `"180° ROT"` / `"TTL"` debug sublabel is gone.
 *
 * All paths, brushes and paints are object-level and reset per call, so a frame with 12 ICs
 * allocates nothing here.
 */
object DipPainter {

    /** Below this zoom leads collapse to single strokes and the label is dropped. */
    const val LOD_DETAIL_MIN_SCALE = 0.6f

    private const val LABEL_SIZE_DP = 7.5f

    private val bodyPath = Path()
    private val notchPath = Path()
    private val clippedBodyPath = Path()
    private val leadPath = Path()

    private val labelPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        isFakeBoldText = true
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private val labelEmbossPaint = android.graphics.Paint(labelPaint)

    private var appliedDensity = 0f

    private fun applyDensity(density: Float) {
        if (density == appliedDensity || density <= 0f) return
        appliedDensity = density
        labelPaint.textSize = LABEL_SIZE_DP * density
        labelEmbossPaint.textSize = LABEL_SIZE_DP * density
        labelPaint.color = ChipSilkscreenText.toArgbInt()
        labelEmbossPaint.color = ChipSilkscreenEmboss.toArgbInt()
    }

    private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
        (alpha * 255f + 0.5f).toInt(),
        (red * 255f + 0.5f).toInt(),
        (green * 255f + 0.5f).toInt(),
        (blue * 255f + 0.5f).toInt()
    )

    private val epoxyBrush = Brush.verticalGradient(
        0f to ChipEpoxyTop,
        0.45f to ChipEpoxy,
        1f to ChipEpoxy
    )

    private val burnedBrush = Brush.verticalGradient(
        0f to ThermalGlow,
        1f to ChipEpoxy
    )

    fun draw(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        chip: BreadboardCircuit.PlacedChipRuntime,
        density: Float,
        zoomScale: Float,
        isBurned: Boolean = false,
        isSelected: Boolean = false,
        explosionTimestamp: Long = 0L
    ) {
        applyDensity(density)
        val body = mapper.chipBodyRect(chip)
        val detail = zoomScale >= LOD_DETAIL_MIN_SCALE
        val isRotated = chip.placedIc.isRotated180

        drawLeads(scope, mapper, chip, body, detail)
        drawBodyShell(scope, body, isBurned, isRotated, detail)

        if (isBurned) {
            // Scorch mark on charred chip body
            scope.drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF260D00), Color(0x88451800), Color.Transparent),
                    center = body.center,
                    radius = body.width * 0.45f
                ),
                radius = body.width * 0.45f,
                center = body.center
            )
        }

        if (detail) {
            drawPin1Dot(scope, mapper, chip, body, isRotated)
            drawLabel(scope, chip, body, isRotated)
        }

        if (isSelected) {
            scope.drawRoundRect(
                color = SelectionHalo,
                topLeft = Offset(body.left - 3f, body.top - 3f),
                size = Size(body.width + 6f, body.height + 6f),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(2.5f)
            )
        }

        val elapsed = if (explosionTimestamp > 0L) System.currentTimeMillis() - explosionTimestamp else Long.MAX_VALUE
        if (elapsed in 0L..1500L) {
            drawExplosion(scope, body, elapsed / 1500f)
        }
    }

    /**
     * Gull-wing leads: from the socket, a vertical foot, then a shoulder that tucks under the
     * epoxy. Drawn as a filled outline so the top face and the underside can differ.
     */
    private fun drawLeads(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        chip: BreadboardCircuit.PlacedChipRuntime,
        body: Rect,
        detail: Boolean
    ) {
        val midY = body.center.y
        var pin = 1
        while (pin <= chip.model.pinCount) {
            val sock = mapper.getSocketPosition(chip.getPinSocket(pin))
            val below = sock.y > midY
            val shoulderY = if (below) body.bottom else body.top
            val kneeY = if (below) body.bottom + 4f else body.top - 4f

            if (!detail) {
                scope.drawLine(
                    color = ChipLeadSilver,
                    start = sock,
                    end = Offset(sock.x, shoulderY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                pin++
                continue
            }

            // Lead shadow cast on the phenolic, offset down-right from the top-left light.
            scope.drawLine(
                color = ChipLeadShadow,
                start = Offset(sock.x + 1.2f, sock.y + 1.2f),
                end = Offset(sock.x + 1.2f, kneeY + 1.2f),
                strokeWidth = 4.2f,
                cap = StrokeCap.Round
            )

            leadPath.reset()
            val halfW = 2.1f
            leadPath.moveTo(sock.x - halfW, sock.y)
            leadPath.lineTo(sock.x - halfW, kneeY)
            // Shoulder tucks 3px inboard under the package.
            leadPath.lineTo(sock.x - halfW - 1.5f, shoulderY)
            leadPath.lineTo(sock.x + halfW + 1.5f, shoulderY)
            leadPath.lineTo(sock.x + halfW, kneeY)
            leadPath.lineTo(sock.x + halfW, sock.y)
            leadPath.close()
            scope.drawPath(leadPath, color = ChipLeadShadow)

            // Silver top face: a narrower highlight down the left edge of the lead, matching the
            // virtual top-left light source used by the wires.
            scope.drawLine(
                color = ChipLeadSilver,
                start = Offset(sock.x - 0.6f, sock.y),
                end = Offset(sock.x - 0.6f, shoulderY),
                strokeWidth = 2.2f,
                cap = StrokeCap.Butt
            )
            pin++
        }
    }

    /** Epoxy body with the pin-1 notch subtracted from its silhouette. */
    private fun drawBodyShell(
        scope: DrawScope,
        body: Rect,
        isBurned: Boolean,
        isRotated: Boolean,
        detail: Boolean
    ) {
        // Contact shadow under the package.
        scope.drawRoundRect(
            color = Color(0x55000000),
            topLeft = Offset(body.left + 1.5f, body.top + 4f),
            size = Size(body.width, body.height),
            cornerRadius = CornerRadius(6f, 6f)
        )

        val notchR = 6f
        val notchCx = if (!isRotated) body.left + notchR * 0.55f else body.right - notchR * 0.55f
        val notchCy = body.center.y

        if (!detail) {
            scope.drawRoundRect(
                brush = if (isBurned) burnedBrush else epoxyBrush,
                topLeft = body.topLeft,
                size = body.size,
                cornerRadius = CornerRadius(6f, 6f)
            )
            return
        }

        bodyPath.reset()
        bodyPath.addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = body.left,
                top = body.top,
                right = body.right,
                bottom = body.bottom,
                cornerRadius = CornerRadius(6f, 6f)
            )
        )
        notchPath.reset()
        notchPath.addOval(
            Rect(notchCx - notchR, notchCy - notchR, notchCx + notchR, notchCy + notchR)
        )
        clippedBodyPath.reset()
        clippedBodyPath.op(bodyPath, notchPath, PathOperation.Difference)

        scope.drawPath(clippedBodyPath, brush = if (isBurned) burnedBrush else epoxyBrush)

        // The notch well itself, filled inside the outline so it reads as a cut, not a bump.
        notchPath.reset()
        notchPath.addOval(
            Rect(notchCx - notchR, notchCy - notchR, notchCx + notchR, notchCy + notchR)
        )
        clippedBodyPath.reset()
        clippedBodyPath.addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = body.left,
                top = body.top,
                right = body.right,
                bottom = body.bottom,
                cornerRadius = CornerRadius(6f, 6f)
            )
        )
        notchPath.op(notchPath, clippedBodyPath, PathOperation.Intersect)
        scope.drawPath(notchPath, color = ChipNotchShadow)

        // Chamfered bevel: a bright top-left inner edge falling to nothing at the bottom-right.
        scope.drawRoundRect(
            color = ChipBodyChamfer,
            topLeft = Offset(body.left + 1.5f, body.top + 1.5f),
            size = Size(body.width - 3f, body.height - 3f),
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(1.2f)
        )
        scope.drawLine(
            color = Color(0x22FFFFFF),
            start = Offset(body.left + 5f, body.top + 2.2f),
            end = Offset(body.right - 5f, body.top + 2.2f),
            strokeWidth = 1f
        )
    }

    private fun drawPin1Dot(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        chip: BreadboardCircuit.PlacedChipRuntime,
        body: Rect,
        isRotated: Boolean
    ) {
        val pin1 = mapper.getSocketPosition(chip.getPinSocket(1))
        val x = if (!isRotated) body.left + 11f else body.right - 11f
        val y = if (pin1.y < body.center.y) body.top + 7f else body.bottom - 7f
        scope.drawCircle(color = ChipNotchShadow, radius = 3.2f, center = Offset(x, y))
        scope.drawCircle(color = ChipPin1Dot, radius = 2.2f, center = Offset(x, y))
    }

    /** Laser-etched part number: a dark 1px pass under a light one gives the embossed edge. */
    private fun drawLabel(
        scope: DrawScope,
        chip: BreadboardCircuit.PlacedChipRuntime,
        body: Rect,
        isRotated: Boolean = false
    ) {
        val text = "SN${chip.placedIc.partNumber}N"
        val cx = body.center.x
        val cy = body.center.y + labelPaint.textSize * 0.35f
        scope.drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            if (isRotated) {
                nc.save()
                nc.rotate(180f, body.center.x, body.center.y)
                nc.drawText(text, cx, cy + 1f, labelEmbossPaint)
                nc.drawText(text, cx, cy, labelPaint)
                nc.restore()
            } else {
                nc.drawText(text, cx, cy + 1f, labelEmbossPaint)
                nc.drawText(text, cx, cy, labelPaint)
            }
        }
    }

    /**
     * 1.5-second procedural explosion animation:
     * - 0.0s - 0.35s: White-hot plasma core flash and rapid shockwave
     * - 0.1s - 0.85s: Fiery sparks radiating outward
     * - 0.25s - 1.5s: Rising dark charcoal soot & smoke puffs fading out
     */
    private fun drawExplosion(scope: DrawScope, body: Rect, progress: Float) {
        val cx = body.center.x
        val cy = body.center.y
        val maxR = body.width * 0.85f

        // Phase 1: White-hot plasma flash (0.0 to 0.35)
        if (progress < 0.35f) {
            val flashNorm = progress / 0.35f
            val flashR = 12f + flashNorm * 28f
            val flashAlpha = (1f - flashNorm).coerceIn(0f, 1f)
            scope.drawCircle(
                color = Color.White.copy(alpha = flashAlpha * 0.9f),
                radius = flashR * 0.6f,
                center = Offset(cx, cy)
            )
            scope.drawCircle(
                color = Color(0xFFFF9800).copy(alpha = flashAlpha * 0.7f),
                radius = flashR,
                center = Offset(cx, cy)
            )
        }

        // Phase 2: Shockwave ring (0.05 to 0.6)
        if (progress in 0.05f..0.6f) {
            val waveNorm = (progress - 0.05f) / 0.55f
            val waveR = 8f + waveNorm * maxR * 1.4f
            val waveAlpha = (1f - waveNorm).coerceIn(0f, 1f)
            scope.drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = waveAlpha * 0.85f),
                radius = waveR,
                center = Offset(cx, cy),
                style = Stroke(width = (4f * (1f - waveNorm)).coerceAtLeast(1f))
            )
        }

        // Phase 3: Fiery sparks radiating outward (0.1 to 0.85)
        if (progress in 0.1f..0.85f) {
            val sparkNorm = (progress - 0.1f) / 0.75f
            val sparkDist = sparkNorm * maxR * 1.2f
            val sparkAlpha = (1f - sparkNorm).coerceIn(0f, 1f)
            val sparkAngles = floatArrayOf(0f, 0.785f, 1.57f, 2.356f, 3.141f, 3.927f, 4.712f, 5.498f)
            for (i in sparkAngles.indices) {
                val angle = sparkAngles[i]
                val dist = sparkDist * (0.8f + (i % 3) * 0.2f)
                val sx = cx + kotlin.math.cos(angle) * dist
                val sy = cy + kotlin.math.sin(angle) * dist
                val color = if (i % 2 == 0) Color(0xFFFF5722) else Color(0xFFFFEB3B)
                scope.drawCircle(
                    color = color.copy(alpha = sparkAlpha),
                    radius = (3.5f * (1f - sparkNorm)).coerceAtLeast(1f),
                    center = Offset(sx, sy)
                )
            }
        }

        // Phase 4: Rising soot & smoke clouds (0.25 to 1.0)
        if (progress > 0.25f) {
            val smokeNorm = (progress - 0.25f) / 0.75f
            val smokeAlpha = (1f - smokeNorm).coerceIn(0f, 1f) * 0.65f
            val smokeYOffset = -smokeNorm * 22f
            val r1 = 8f + smokeNorm * 18f
            val r2 = 6f + smokeNorm * 14f
            val r3 = 7f + smokeNorm * 16f
            scope.drawCircle(
                color = Color(0xFF212121).copy(alpha = smokeAlpha),
                radius = r1,
                center = Offset(cx - 5f, cy + smokeYOffset - 4f)
            )
            scope.drawCircle(
                color = Color(0xFF424242).copy(alpha = smokeAlpha * 0.8f),
                radius = r2,
                center = Offset(cx + 6f, cy + smokeYOffset - 2f)
            )
            scope.drawCircle(
                color = Color(0xFF1B1B1B).copy(alpha = smokeAlpha * 0.9f),
                radius = r3,
                center = Offset(cx, cy + smokeYOffset - 8f)
            )
        }
    }
}
