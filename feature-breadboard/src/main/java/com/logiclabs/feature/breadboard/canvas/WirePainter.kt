package com.logiclabs.feature.breadboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.JumperWire
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.designsystem.theme.ChipLeadSilver
import com.logiclabs.core.designsystem.theme.SelectionCore
import com.logiclabs.core.designsystem.theme.SelectionHalo
import com.logiclabs.core.designsystem.theme.WireShadow
import com.logiclabs.core.designsystem.theme.WireSpecular
import com.logiclabs.core.designsystem.theme.wireColorOf
import com.logiclabs.feature.breadboard.physics.BezierWireGeometry

/**
 * Paints jumper wires: cubic Béziers with distance-proportional sag, a soft three-pass drop shadow
 * from a **top-left** virtual light, a specular sheen, and moulded terminal boots.
 *
 * ### Lighting
 * One light source, upper left. The shadow therefore falls down-and-right in three decreasing-alpha
 * passes (replacing the single hard `+8y` copy), and the specular highlight sits at `−1y` on the
 * insulation's upper surface.
 *
 * ### Allocation
 * Nothing here allocates per frame:
 *  - one [Path] per [WireColor], reset in place;
 *  - the shadow and specular geometry is appended through the primitive-float
 *    [BezierWireGeometry.appendCurveXY] / `appendManhattanXY`, so the two per-wire `Offset`s the old
 *    renderer built for the shadow copy are gone;
 *  - only colours actually present are stroked, so a two-colour circuit costs 2 `drawPath`s per
 *    layer instead of 10;
 *  - the redundant `selectedWireHaloPath` (identical geometry to `selectedWirePath`) is gone — one
 *    path is stroked twice.
 */
object WirePainter {

    private val colors = WireColor.values()
    private val corePaths = Array(colors.size) { Path() }
    private val bootBrushes = arrayOfNulls<Brush>(colors.size)
    private val used = BooleanArray(colors.size)

    private val shadowPath = Path()
    private val specularPath = Path()
    private val selectedPath = Path()

    /** Offsets and alphas of the three soft-shadow passes, from a top-left light. */
    private val shadowPasses = floatArrayOf(
        // dx,   dy,  width, alphaScale
        2f, 3f, 9f, 0.32f,
        4f, 6f, 7f, 0.22f,
        6f, 9f, 5f, 0.12f
    )

    // Strokes are immutable and every width is a constant, so they live at object
    // level — the draw loop used to construct up to ~16 of them per frame.
    private val shadowStrokes = arrayOf(
        Stroke(9f, cap = StrokeCap.Round),
        Stroke(7f, cap = StrokeCap.Round),
        Stroke(5f, cap = StrokeCap.Round)
    )
    private val coreStroke = Stroke(CORE_WIDTH, cap = StrokeCap.Round)
    private val specularStroke = Stroke(SPECULAR_WIDTH, cap = StrokeCap.Round)
    private val selectionHaloStroke = Stroke(14f, cap = StrokeCap.Round)
    private val selectionCoreStroke = Stroke(5.5f, cap = StrokeCap.Round)
    private val handleRingStroke = Stroke(3f)

    private const val CORE_WIDTH = 5f
    private const val SPECULAR_WIDTH = 1.4f
    private const val BOOT_RADIUS = 6f

    private fun bootBrush(index: Int, argb: Long): Brush {
        bootBrushes[index]?.let { return it }
        val base = wireColorOf(argb)
        val b = Brush.radialGradient(
            0f to Color(
                red = (base.red + 0.35f).coerceAtMost(1f),
                green = (base.green + 0.35f).coerceAtMost(1f),
                blue = (base.blue + 0.35f).coerceAtMost(1f),
                alpha = 1f
            ),
            0.6f to base,
            1f to Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f, 1f),
            center = Offset(-BOOT_RADIUS * 0.35f, -BOOT_RADIUS * 0.35f),
            radius = BOOT_RADIUS * 1.9f
        )
        bootBrushes[index] = b
        return b
    }

    /**
     * @param viewport visible board rect; wires wholly outside are skipped.
     */
    fun draw(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        circuit: BreadboardCircuit,
        selectedWireId: String? = null,
        viewport: Rect = mapper.boardBounds()
    ) {
        var i = 0
        while (i < corePaths.size) {
            corePaths[i].reset()
            used[i] = false
            i++
        }
        shadowPath.reset()
        specularPath.reset()
        selectedPath.reset()

        val wires = circuit.wires
        var w = 0
        while (w < wires.size) {
            val wire = wires[w]
            w++
            val start = mapper.getSocketPosition(wire.startSocket)
            val end = mapper.getSocketPosition(wire.endSocket)

            // Cull by chord bounds inflated by how far the wire can visually leave that
            // box: sag + elevation drag the curve *below* the lower endpoint (up to
            // ~0.85×drop), so the vertical allowance is sag-aware rather than a flat
            // 100f that a long elevated jumper could exceed. Manhattan routes stay
            // inside the endpoint box; both need the stroke/halo width margin.
            val elev = wire.elevationLevel * 10f
            val sag = if (wire.isManhattan) 0f else
                BezierWireGeometry.sagFor(end.x - start.x, end.y - start.y)
            val dipBelow = sag * 0.85f + elev + 16f

            val minX = if (start.x < end.x) start.x else end.x
            val maxX = if (start.x > end.x) start.x else end.x
            val minY = if (start.y < end.y) start.y else end.y
            val maxY = if (start.y > end.y) start.y else end.y
            if (maxX < viewport.left - 16f || minX > viewport.right + 16f) continue
            if (maxY + dipBelow < viewport.top || minY > viewport.bottom + 16f) continue

            val idx = wire.color.ordinal
            val path = corePaths[idx]
            used[idx] = true

            if (wire.isManhattan) {
                BezierWireGeometry.appendManhattanXY(path, start.x, start.y, end.x, end.y)
                BezierWireGeometry.appendManhattanXY(shadowPath, start.x, start.y, end.x, end.y)
                BezierWireGeometry.appendManhattanXY(
                    specularPath, start.x, start.y - 1f, end.x, end.y - 1f
                )
            } else {
                BezierWireGeometry.appendCurveXY(path, start.x, start.y, end.x, end.y, sag, elev)
                BezierWireGeometry.appendCurveXY(
                    shadowPath, start.x, start.y, end.x, end.y, sag, elev
                )
                BezierWireGeometry.appendCurveXY(
                    specularPath, start.x, start.y - 1f, end.x, end.y - 1f, sag, elev
                )
            }
        }

        // 1. Soft drop shadow: three decreasing-alpha passes offset down-right.
        var p = 0
        while (p < shadowPasses.size) {
            val dx = shadowPasses[p]
            val dy = shadowPasses[p + 1]
            val alpha = shadowPasses[p + 3]
            scope.translate(dx, dy) {
                drawPath(
                    path = shadowPath,
                    color = WireShadow,
                    alpha = alpha,
                    style = shadowStrokes[p / 4]
                )
            }
            p += 4
        }

        // 2. Insulation cores, batched by colour — empty colours are skipped entirely.
        var c = 0
        while (c < corePaths.size) {
            if (used[c]) {
                scope.drawPath(
                    path = corePaths[c],
                    color = wireColorOf(colors[c].hexArgb),
                    style = coreStroke
                )
            }
            c++
        }

        // 3. Specular sheen along the top of every wire, one pass for the whole set.
        scope.drawPath(
            path = specularPath,
            color = WireSpecular,
            style = specularStroke
        )

        // 4. Terminal boots.
        var b = 0
        while (b < wires.size) {
            val wire = wires[b]
            b++
            drawBoot(scope, mapper.getSocketPosition(wire.startSocket), wire.color)
            drawBoot(scope, mapper.getSocketPosition(wire.endSocket), wire.color)
        }

        if (selectedWireId != null) drawSelection(scope, mapper, wires, selectedWireId)
    }

    /** Moulded strain-relief boot: a gradient collar around a visible conductor. */
    private fun drawBoot(scope: DrawScope, at: Offset, color: WireColor) {
        val brush = bootBrush(color.ordinal, color.hexArgb)
        scope.translate(at.x, at.y) {
            drawCircle(brush = brush, radius = BOOT_RADIUS, center = Offset.Zero)
        }
        scope.drawCircle(color = ChipLeadSilver, radius = 2.2f, center = at)
    }

    private fun drawSelection(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        wires: List<JumperWire>,
        selectedWireId: String
    ) {
        var i = 0
        var sel: JumperWire? = null
        while (i < wires.size) {
            if (wires[i].id == selectedWireId) {
                sel = wires[i]
                break
            }
            i++
        }
        val wire = sel ?: return

        val start = mapper.getSocketPosition(wire.startSocket)
        val end = mapper.getSocketPosition(wire.endSocket)
        if (wire.isManhattan) {
            BezierWireGeometry.appendManhattanXY(selectedPath, start.x, start.y, end.x, end.y)
        } else {
            BezierWireGeometry.appendCurveXY(
                selectedPath, start.x, start.y, end.x, end.y,
                elevationOffset = wire.elevationLevel * 10f
            )
        }

        // One path, stroked twice: outer halo then crisp core. The core follows the
        // palette (SelectionCore): white on dark benches, near-black on the light
        // CLEANROOM phenolic where a white core was invisible (1.1:1).
        scope.drawPath(
            path = selectedPath,
            color = SelectionHalo,
            alpha = 0.85f,
            style = selectionHaloStroke
        )
        scope.drawPath(
            path = selectedPath,
            color = SelectionCore,
            style = selectionCoreStroke
        )

        drawEndpointHandle(scope, start)
        drawEndpointHandle(scope, end)
    }

    private fun drawEndpointHandle(scope: DrawScope, at: Offset) {
        scope.drawCircle(color = SelectionHalo, radius = 14f, center = at, style = handleRingStroke)
        scope.drawCircle(color = SelectionCore, radius = 7f, center = at)
    }

    private inline fun DrawScope.translate(dx: Float, dy: Float, block: DrawScope.() -> Unit) {
        drawContext.transform.translate(dx, dy)
        block()
        drawContext.transform.translate(-dx, -dy)
    }
}
