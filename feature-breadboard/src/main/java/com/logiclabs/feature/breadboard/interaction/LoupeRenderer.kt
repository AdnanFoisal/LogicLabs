package com.logiclabs.feature.breadboard.interaction

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ScrewBody
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.WireShadow
import com.logiclabs.feature.breadboard.canvas.BoardCache
import com.logiclabs.feature.breadboard.canvas.BreadboardGeometryMapper
import com.logiclabs.feature.breadboard.canvas.DipPainter
import com.logiclabs.feature.breadboard.canvas.FaceplateRenderer
import com.logiclabs.feature.breadboard.canvas.WirePainter
import com.logiclabs.core.designsystem.theme.Dimens

/**
 * A real magnifier, not an occluder.
 *
 * The previous overlay drew an opaque disc 140px above the finger, which hid exactly the part of
 * the board the user was trying to aim at. This clips a circle, transforms into it, and **re-issues
 * the board** — the cached faceplate picture plus wires and chips — at [LoupeState.magnification],
 * so the disc shows a genuine magnified view of what is underneath it.
 *
 * All three previously-dead [LoupeState] fields drive it: [LoupeState.magnification],
 * [LoupeState.radiusDp] and [LoupeState.verticalOffsetDp]. No new state is introduced.
 *
 * Geometry note: to magnify the board point `b` at the centre of a disc positioned at screen point
 * `c`, the inner transform is `translate(c) · scale(m) · translate(−b)`. `b` is derived from the
 * finger, so the crosshair always sits on the socket being targeted.
 */
object LoupeRenderer {

    private const val CROSSHAIR_ARM = 9f
    private const val COORD_SIZE_DP = 9f

    private val clipPath = Path()

    private val coordPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private var appliedDensity = 0f
    private var appliedPalette: com.logiclabs.core.designsystem.theme.BenchPalette? = null

    // Rebuilt only when the bench palette flips (see applyDensity); the bezel's metal
    // tones are theme-varying, so a stale dark capture would ring the daylight bench.
    private var bezelBrush = Brush.verticalGradient(
        0f to ChassisBevelHighlight,
        0.5f to ScrewBody,
        1f to ChassisBevelShadow
    )

    /**
     * Draws the loupe in **screen** space. Call outside the board's `withTransform`.
     *
     * @param pan current board pan, screen px.
     * @param scale current board zoom.
     */
    fun draw(
        scope: DrawScope,
        state: LoupeState,
        mapper: BreadboardGeometryMapper,
        circuit: BreadboardCircuit,
        boardCache: BoardCache,
        pan: Offset,
        scale: Float,
        density: Float,
        selectedChipId: String? = null
    ) {
        if (!state.isVisible) return
        applyDensity(density)

        val radius = state.radiusDp * density
        // Keep the whole disc (glass + bezel + outer shadow) inside the canvas. When the
        // natural position — 65dp above the finger — would push it past the top edge, the
        // loupe flips below the finger instead of being clipped off-screen: the magnified
        // point is anchored to the disc centre by the transform below, so moving the disc
        // never changes *what* is magnified, only where the window sits.
        val bezel = Dimens.LoupeBezelWidth.value * density
        val edge = radius + bezel * 2.2f
        val naturalY = state.touchPosition.y - state.verticalOffsetDp * density
        val cy = if (naturalY - edge < 0f) {
            (state.touchPosition.y + state.verticalOffsetDp * density + radius).coerceAtMost(
                scope.size.height - edge
            )
        } else {
            naturalY.coerceIn(edge, (scope.size.height - edge).coerceAtLeast(edge))
        }
        val cx = state.touchPosition.x.coerceIn(edge, (scope.size.width - edge).coerceAtLeast(edge))
        val center = Offset(cx, cy)

        // The board point under the finger is what we magnify — not the point under the disc.
        val focus = (state.touchPosition - pan) / (if (scale <= 0f) 1f else scale)
        val effective = scale * state.magnification

        clipPath.reset()
        clipPath.addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))

        // Ground the lens so board gaps do not show the app background through the glass.
        scope.drawCircle(color = Color(0xFF0C0D10), radius = radius, center = center)

        scope.clipPath(clipPath) {
            withTransform({
                translate(cx, cy)
                scale(effective, effective, Offset.Zero)
                translate(-focus.x, -focus.y)
            }) {
                // Only the board region the lens can actually show.
                val half = radius / effective
                val viewport = Rect(
                    focus.x - half, focus.y - half,
                    focus.x + half, focus.y + half
                )

                boardCache.drawStatic(this, mapper, density, effective)
                FaceplateRenderer.drawDynamic(
                    scope = this,
                    mapper = mapper,
                    circuit = circuit,
                    density = density,
                    zoomScale = effective,
                    highlightSockets = emptySet(),
                    viewport = viewport
                )
                for (chip in circuit.placedChips) {
                    DipPainter.draw(
                        scope = this,
                        mapper = mapper,
                        chip = chip,
                        density = density,
                        zoomScale = effective,
                        isBurned = circuit.isReversePolarityBurned &&
                            circuit.burnedChipId == chip.placedIc.id,
                        isSelected = chip.placedIc.id == selectedChipId
                    )
                }
                WirePainter.draw(this, mapper, circuit, selectedWireId = null, viewport = viewport)

                // The snapped socket, marked in board space so it lands exactly on the hole.
                state.snappedSocket?.let { id ->
                    val p = mapper.getSocketPosition(id)
                    drawCircle(
                        color = AccentCyan,
                        radius = 9f,
                        center = p,
                        style = Stroke(width = 2f / effective * scale)
                    )
                }
            }
        }

        drawBezel(scope, center, radius, density)
        drawCrosshair(scope, center, density)
        drawCoordinate(scope, center, radius, state.snappedSocket, density)
    }

    /** Machined metal bezel: outer shadow, gradient ring, inner cyan witness line. */
    private fun drawBezel(scope: DrawScope, center: Offset, radius: Float, density: Float) {
        val bezel = Dimens.LoupeBezelWidth.value * density
        scope.drawCircle(
            color = WireShadow,
            radius = radius + bezel * 1.6f,
            center = center,
            style = Stroke(width = bezel * 1.6f)
        )
        scope.drawCircle(
            brush = bezelBrush,
            radius = radius + bezel / 2f,
            center = center,
            style = Stroke(width = bezel)
        )
        scope.drawCircle(
            color = AccentCyan,
            alpha = 0.65f,
            radius = radius - 1f,
            center = center,
            style = Stroke(width = 1.2f)
        )
    }

    private fun drawCrosshair(scope: DrawScope, center: Offset, density: Float) {
        val arm = CROSSHAIR_ARM * density
        val gap = arm * 0.35f
        val c = AccentCyan
        scope.drawLine(c, Offset(center.x - arm, center.y), Offset(center.x - gap, center.y), 1.4f)
        scope.drawLine(c, Offset(center.x + gap, center.y), Offset(center.x + arm, center.y), 1.4f)
        scope.drawLine(c, Offset(center.x, center.y - arm), Offset(center.x, center.y - gap), 1.4f)
        scope.drawLine(c, Offset(center.x, center.y + gap), Offset(center.x, center.y + arm), 1.4f)
    }

    /** Monospace `B-27` style readout on the bezel's lower edge. */
    private fun drawCoordinate(
        scope: DrawScope,
        center: Offset,
        radius: Float,
        socketId: Int?,
        density: Float
    ) {
        val label = coordinateLabel(socketId) ?: return
        val y = center.y + radius - 6f * density
        val h = COORD_SIZE_DP * density * 1.5f
        scope.drawRoundRect(
            color = Color(0xCC0A0B0E),
            topLeft = Offset(center.x - radius * 0.62f, y - h * 0.75f),
            size = androidx.compose.ui.geometry.Size(radius * 1.24f, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
        )
        scope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(label, center.x, y + h * 0.2f, coordPaint)
        }
    }

    /**
     * Human-readable name for a socket id: `B-27` for terminal holes, `+TOP-14` / `−BOT-14` for
     * bus holes, and the panel label for peripherals.
     */
    fun coordinateLabel(socketId: Int?): String? {
        val id = socketId ?: return null
        if (id < AD200Topology.TOTAL_TERMINAL_SOCKETS) {
            val block = id / AD200Topology.SOCKETS_PER_BLOCK
            val rem = id % AD200Topology.SOCKETS_PER_BLOCK
            val col = rem / AD200Topology.ROWS_PER_BLOCK
            val row = rem % AD200Topology.ROWS_PER_BLOCK
            val letters = if (block % 2 == 0) TOP_LETTERS else BOTTOM_LETTERS
            return "${letters[row]}-${col + 1}"
        }
        if (id < BreadboardGeometryMapper.ACTIVE_GRID_LIMIT) {
            val off = id - AD200Topology.TOTAL_TERMINAL_SOCKETS
            val rail = off / AD200Topology.SOCKETS_PER_RAIL
            val pos = off % AD200Topology.SOCKETS_PER_RAIL
            val name = when (rail) {
                AD200Topology.RAIL_TOP_VCC_5V -> "+TOP"
                AD200Topology.RAIL_TOP_GND -> "−TOP"
                AD200Topology.RAIL_BOT_VCC_5V -> "+BOT"
                else -> "−BOT"
            }
            return "$name-${pos + 1}"
        }
        return when (id) {
            in AD200Topology.TERM_SW0..AD200Topology.TERM_SW7 -> "SW${id - AD200Topology.TERM_SW0}"
            in AD200Topology.TERM_LED0..AD200Topology.TERM_LED7 -> "L${id - AD200Topology.TERM_LED0}"
            in AD200Topology.TERM_BOT_LED0..AD200Topology.TERM_BOT_LED7 ->
                "L${id - AD200Topology.TERM_BOT_LED0}·B"
            AD200Topology.TERM_POWER_VCC -> "+5V"
            AD200Topology.TERM_POWER_GND -> "GND"
            AD200Topology.TERM_CLK -> "CLK"
            AD200Topology.TERM_CLK_INV -> "~CLK"
            AD200Topology.TERM_PULSER_A_P -> "PLS-A (P)"
            AD200Topology.TERM_PULSER_A_N -> "PLS-A (~P)"
            AD200Topology.TERM_PULSER_B_P -> "PLS-B (P)"
            AD200Topology.TERM_PULSER_B_N -> "PLS-B (~P)"
            AD200Topology.TERM_SEG_A_BCD_A -> "HEX-A · A(1)"
            AD200Topology.TERM_SEG_A_BCD_B -> "HEX-A · B(2)"
            AD200Topology.TERM_SEG_A_BCD_C -> "HEX-A · C(4)"
            AD200Topology.TERM_SEG_A_BCD_D -> "HEX-A · D(8)"
            AD200Topology.TERM_SEG_B_BCD_A -> "HEX-B · A(1)"
            AD200Topology.TERM_SEG_B_BCD_B -> "HEX-B · B(2)"
            AD200Topology.TERM_SEG_B_BCD_C -> "HEX-B · C(4)"
            AD200Topology.TERM_SEG_B_BCD_D -> "HEX-B · D(8)"
            else -> "#$id"
        }
    }

    private val TOP_LETTERS = arrayOf("A", "B", "C", "D", "E")
    private val BOTTOM_LETTERS = arrayOf("F", "G", "H", "I", "J")

    private fun applyDensity(density: Float) {
        val palette = com.logiclabs.core.designsystem.theme.BenchPaletteState.value
        if (density == appliedDensity && palette === appliedPalette) return
        if (density != appliedDensity && density > 0f) {
            coordPaint.textSize = COORD_SIZE_DP * density
            appliedDensity = density
        }
        // The coordinate readout sits on a fixed dark chip (0xCC0A0B0E), so its text
        // must stay light in every theme — following TextPrimary made it near-black on
        // near-black under the daylight CLEANROOM palette.
        coordPaint.color = android.graphics.Color.argb(255, 0xEC, 0xEE, 0xF2)
        bezelBrush = Brush.verticalGradient(
            0f to ChassisBevelHighlight,
            0.5f to ScrewBody,
            1f to ChassisBevelShadow
        )
        appliedPalette = palette
    }
}
