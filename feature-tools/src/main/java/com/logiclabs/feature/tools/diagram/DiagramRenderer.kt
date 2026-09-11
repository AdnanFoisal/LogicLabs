package com.logiclabs.feature.tools.diagram

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.ElectricalLevel
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.BusGnd
import com.logiclabs.core.designsystem.theme.BusVcc
import com.logiclabs.core.designsystem.theme.CanvasVignette
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisDivider
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.FloatingWarning
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import kotlin.math.ceil

/**
 * Native-canvas text paints for the diagram, in **world dp** text sizes.
 *
 * The renderer draws inside a `scale(density × zoom)` transform, so paint sizes
 * are authored in dp and need no density math here (unlike the faceplate
 * painters, which draw raw pixels). Colours are theme-varying tokens and are
 * re-inked once per frame by [inkPaints] — the reads are tracked snapshot
 * reads, so a palette flip re-executes the draw lambda on its own.
 *
 * Layout-time measurement ([android.graphics.Paint.measureText]) shares these
 * instances, so a tag measured at layout time is exactly the tag drawn.
 */
internal object DiagramPaints {
    private fun base() = android.graphics.Paint().apply {
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }

    /** Input/output terminal tag labels. */
    val tagLabelPaint = base().apply {
        textSize = 9f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.LEFT
    }

    /** U-designators above gate bodies. */
    val gateLabelPaint = base().apply {
        textSize = 8f
        textAlign = android.graphics.Paint.Align.CENTER
    }

    /** Block header titles. */
    val blockTitlePaint = base().apply {
        textSize = 8.5f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    /** Pin names inside blocks. */
    val blockPinLeftPaint = base().apply {
        textSize = 7.5f
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val blockPinRightPaint = base().apply {
        textSize = 7.5f
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    /** The "1" hint on floating input stubs. */
    val floatHintPaint = base().apply {
        textSize = 7f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
}

/**
 * Pre-allocated per-composition scratch state. Nothing in the draw lambda
 * allocates: all Path objects live here and are `reset()` per frame, and the
 * vignette Brush is rebuilt only when the canvas size or theme edge colour
 * actually changes.
 */
internal class DiagramScratch {
    val gridPath = Path()

    // Forward nets, batched per live level.
    val high = Path()
    val low = Path()
    val floating = Path()
    val conflict = Path()

    // Feedback nets (dashed), batched per live level.
    val backHigh = Path()
    val backLow = Path()
    val backFloat = Path()
    val backConflict = Path()

    private var vigW = -1f
    private var vigH = -1f
    private var vigEdge: Color = Color.Unspecified
    private var vignette: Brush? = null

    fun resetEdges() {
        high.reset(); low.reset(); floating.reset(); conflict.reset()
        backHigh.reset(); backLow.reset(); backFloat.reset(); backConflict.reset()
    }

    fun vignetteFor(w: Float, h: Float, edge: Color): Brush {
        val cached = vignette
        if (cached != null && vigW == w && vigH == h && vigEdge == edge) return cached
        val built = Brush.radialGradient(
            colorStops = arrayOf(0.55f to Color.Transparent, 1f to edge.copy(alpha = 0.32f)),
            center = Offset(w / 2f, h * 0.42f),
            radius = maxOf(w, h) * 0.75f
        )
        vignette = built
        vigW = w
        vigH = h
        vigEdge = edge
        return built
    }
}

/** Live net level buckets; LOW doubles as the neutral "power off" colour. */
private enum class Bucket { HIGH, LOW, FLOAT, CONFLICT }

private fun DiagramScratch.bucketPath(b: Bucket): Path = when (b) {
    Bucket.HIGH -> high
    Bucket.LOW -> low
    Bucket.FLOAT -> floating
    Bucket.CONFLICT -> conflict
}

private fun DiagramScratch.backBucketPath(b: Bucket): Path = when (b) {
    Bucket.HIGH -> backHigh
    Bucket.LOW -> backLow
    Bucket.FLOAT -> backFloat
    Bucket.CONFLICT -> backConflict
}

// Object-constant strokes: never reallocated inside the draw loop.
private val WireCoreStroke = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val WireBloomStroke = Stroke(width = 5.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val WireBackStroke = Stroke(
    width = 1.4f,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
)
private val GridStroke = Stroke(width = 0.5f)
private val BodyStroke = Stroke(width = 1.1f, join = StrokeJoin.Round)
private val BlockStroke = Stroke(width = 1.0f, join = StrokeJoin.Round)
private val OutlineStroke = Stroke(width = 1.0f)
private val TagCorner = CornerRadius(4f)
private val BlockCorner = CornerRadius(5f)

/** Drop-shadow offsets for gates, blocks and tags (virtual light top-left). */
private const val SHADOW_DX = 1.3f
private const val SHADOW_DY = 2.4f

/** Below this zoom, silkscreen-style labels are dropped (LOD, like the faceplate). */
internal const val LOD_LABEL_MIN_ZOOM = 0.55f

/**
 * Fixed neutral for LOW nets. ChassisSilkscreen is theme-varying (amber on AmberCrt,
 * rose on CyberpunkNeon), which made LOW collide with the fixed FLOAT amber / CONFLICT
 * red in those palettes and collapse the four-state language. A fixed slate-neutral
 * stays distinct from HIGH cyan, FLOAT amber and CONFLICT red in every palette.
 */
internal val LowNetColor = Color(0xFF7A8090)

/**
 * Paints the whole diagram. Everything is world-dp geometry pre-built in the
 * [LayoutResult]; this function only classifies nets by live level into the
 * pre-allocated bucket paths and strokes them batched per colour.
 *
 * @param zoom px-per-dp scale (density × user zoom) — used for the transform.
 * @param userZoom density-normalised zoom — used for LOD decisions. The old code
 *   compared the raw px scale against [LOD_LABEL_MIN_ZOOM], so on any device with
 *   density > ~1.6 the LOD never engaged and fitted-out labels rendered as smudge.
 *
 * Zero allocations per frame: colours are [Color] value classes, [Offset]s are
 * value classes, paths and paints are shared, and the vignette brush is cached
 * by size+colour.
 */
internal fun DrawScope.drawDiagram(
    layout: LayoutResult,
    circuit: BreadboardCircuit,
    zoom: Float,
    panX: Float,
    panY: Float,
    userZoom: Float,
    scratch: DiagramScratch
) {
    inkPaints()

    withTransform({ translate(panX, panY); scale(zoom, zoom, pivot = Offset.Zero) }) {
        drawGrid(layout, zoom, userZoom, panX, panY, scratch)
        drawEdges(layout, circuit)
        drawJunctions(layout, circuit)
        drawStubs(layout, userZoom)
        drawBlocks(layout, userZoom)
        drawGates(layout, userZoom)
        drawTags(layout, circuit)
    }

    // Glass falloff in screen space, over everything.
    drawRect(
        brush = scratch.vignetteFor(size.width, size.height, ChassisBevelShadow),
        size = size
    )
}

private fun inkPaints() {
    DiagramPaints.tagLabelPaint.color = TextPrimary.toArgb()
    DiagramPaints.gateLabelPaint.color = ChassisSilkscreen.toArgb()
    DiagramPaints.blockTitlePaint.color = AccentCyan.toArgb()
    DiagramPaints.blockPinLeftPaint.color = TextSecondary.toArgb()
    DiagramPaints.blockPinRightPaint.color = TextSecondary.toArgb()
    DiagramPaints.floatHintPaint.color = FloatingWarning.toArgb()
}

private fun DrawScope.drawGrid(
    layout: LayoutResult,
    zoom: Float,
    userZoom: Float,
    panX: Float,
    panY: Float,
    scratch: DiagramScratch
) {
    scratch.gridPath.reset()
    val step = if (userZoom < LOD_LABEL_MIN_ZOOM) 48f else 24f
    val invZ = 1f / zoom
    val visL = (-panX) * invZ
    val visT = (-panY) * invZ
    val visR = (size.width - panX) * invZ
    val visB = (size.height - panY) * invZ
    val gridTop = maxOf(visT, -40f)
    val gridBottom = minOf(visB, layout.worldH + 40f)
    val gridLeft = maxOf(visL, -40f)
    val gridRight = minOf(visR, layout.worldW + 40f)

    var gx = ceil(visL / step) * step
    while (gx <= gridRight) {
        scratch.gridPath.moveTo(gx, gridTop)
        scratch.gridPath.lineTo(gx, gridBottom)
        gx += step
    }
    var gy = ceil(gridTop / step) * step
    while (gy <= gridBottom) {
        scratch.gridPath.moveTo(gridLeft, gy)
        scratch.gridPath.lineTo(gridRight, gy)
        gy += step
    }
    drawPath(scratch.gridPath, ChassisDivider.copy(alpha = 0.55f), style = GridStroke)
}

private fun DrawScope.drawEdges(
    layout: LayoutResult,
    circuit: BreadboardCircuit
) {
    val powerOff = !circuit.masterPower

    // Pass 1: Luminous bloom pass under Active HIGH nets
    if (!powerOff) {
        for (e in layout.edges) {
            if (circuit.getSocketLevel(e.levelSocket) == ElectricalLevel.HIGH) {
                val bloomAlpha = if (e.isBackEdge) 0.18f else 0.35f
                drawPath(
                    path = e.path,
                    color = e.neonColor.copy(alpha = bloomAlpha),
                    style = WireBloomStroke
                )
            }
        }
    }

    // Pass 2: Net-preserving cores and feedback
    for (e in layout.edges) {
        val stroke = if (e.isBackEdge) WireBackStroke else WireCoreStroke
        val color = if (powerOff) {
            val alpha = if (e.isBackEdge) 0.35f else 0.45f
            e.neonColor.copy(alpha = alpha)
        } else {
            when (circuit.getSocketLevel(e.levelSocket)) {
                ElectricalLevel.HIGH -> {
                    if (e.isBackEdge) e.neonColor.copy(alpha = 0.85f) else e.neonColor
                }
                ElectricalLevel.LOW -> {
                    val alpha = if (e.isBackEdge) 0.35f else 0.45f
                    e.neonColor.copy(alpha = alpha)
                }
                ElectricalLevel.HIGH_Z -> {
                    if (e.isBackEdge) FloatingWarning.copy(alpha = 0.65f) else FloatingWarning
                }
                else -> {
                    if (e.isBackEdge) ShortCircuitAlert.copy(alpha = 0.75f) else ShortCircuitAlert
                }
            }
        }
        drawPath(path = e.path, color = color, style = stroke)
    }
}

private fun liveColor(circuit: BreadboardCircuit, socket: Int): Color {
    if (!circuit.masterPower) return LowNetColor
    return when (circuit.getSocketLevel(socket)) {
        ElectricalLevel.HIGH -> AccentCyan
        ElectricalLevel.LOW -> LowNetColor
        ElectricalLevel.HIGH_Z -> FloatingWarning
        else -> ShortCircuitAlert
    }
}

private fun DrawScope.drawJunctions(layout: LayoutResult, circuit: BreadboardCircuit) {
    val powerOff = !circuit.masterPower
    for (j in layout.junctions) {
        val baseColor = if (j.neonColor != Color.Unspecified) j.neonColor else liveColor(circuit, j.levelSocket)
        val color = if (powerOff) {
            baseColor.copy(alpha = 0.45f)
        } else {
            when (circuit.getSocketLevel(j.levelSocket)) {
                ElectricalLevel.HIGH -> baseColor
                ElectricalLevel.LOW -> baseColor.copy(alpha = 0.45f)
                ElectricalLevel.HIGH_Z -> FloatingWarning
                else -> ShortCircuitAlert
            }
        }
        if (!powerOff && circuit.getSocketLevel(j.levelSocket) == ElectricalLevel.HIGH) {
            drawCircle(
                color = baseColor.copy(alpha = 0.35f),
                radius = 5.2f,
                center = Offset(j.x, j.y)
            )
        }
        drawCircle(
            color = color,
            radius = 2.4f,
            center = Offset(j.x, j.y)
        )
    }
}

private fun DrawScope.drawStubs(layout: LayoutResult, userZoom: Float) {
    for (s in layout.stubs) {
        val dir = if (s.isInput) -1f else 1f
        drawLine(
            color = FloatingWarning,
            start = Offset(s.x, s.y),
            end = Offset(s.x + dir * 11f, s.y),
            strokeWidth = 1.2f
        )
        val cx = s.x + dir * 15f
        drawCircle(color = CanvasVignette, radius = 3.2f, center = Offset(cx, s.y))
        drawCircle(color = FloatingWarning, radius = 3.2f, center = Offset(cx, s.y), style = OutlineStroke)
        if (s.showOne && userZoom > LOD_LABEL_MIN_ZOOM) {
            drawContext.canvas.nativeCanvas.drawText("1", cx, s.y - 5.5f, DiagramPaints.floatHintPaint)
        }
    }
}

private fun DrawScope.drawGates(layout: LayoutResult, userZoom: Float) {
    val shadow = ChassisBevelShadow.copy(alpha = 0.55f)
    for (g in layout.gates) {
        withTransform({ translate(SHADOW_DX, SHADOW_DY) }) {
            drawPath(g.path, shadow)
        }
        drawPath(g.path, SurfaceCard)
        drawPath(g.path, SurfaceCardBorder, style = BodyStroke)
        if (g.decor != null) {
            drawPath(g.decor, SurfaceCardBorder, style = BodyStroke)
        }
        if (userZoom > LOD_LABEL_MIN_ZOOM) {
            drawContext.canvas.nativeCanvas.drawText(
                g.label,
                g.bodyLeft + g.bodyW / 2f,
                g.bodyTop - 4f,
                DiagramPaints.gateLabelPaint
            )
        }
    }
}

private fun DrawScope.drawBlocks(layout: LayoutResult, userZoom: Float) {
    val shadow = ChassisBevelShadow.copy(alpha = 0.55f)
    for (b in layout.blocks) {
        val tl = Offset(b.left, b.top)
        val sz = Size(b.w, b.h)
        withTransform({ translate(SHADOW_DX, SHADOW_DY) }) {
            drawRoundRect(color = shadow, topLeft = tl, size = sz, cornerRadius = BlockCorner)
        }
        drawRoundRect(color = SurfaceRaised, topLeft = tl, size = sz, cornerRadius = BlockCorner)
        drawRoundRect(color = SurfaceCardBorder, topLeft = tl, size = sz, cornerRadius = BlockCorner, style = BlockStroke)
        // Header rule under the title, in the theme accent.
        drawLine(
            color = AccentCyan.copy(alpha = 0.45f),
            start = Offset(b.left + 8f, b.top + 19f),
            end = Offset(b.left + b.w - 8f, b.top + 19f),
            strokeWidth = 0.8f
        )
        // Clock wedges: filled + outlined.
        drawPath(b.wedges, ChassisSilkscreen.copy(alpha = 0.55f))
        drawPath(b.wedges, SurfaceCardBorder, style = OutlineStroke)
        // Inversion bubbles: filled with the block fill, then outlined.
        drawPath(b.bubbles, SurfaceRaised)
        drawPath(b.bubbles, SurfaceCardBorder, style = OutlineStroke)
        // Title + pin names (silkscreen LOD — gated like gate labels, which these
        // previously missed entirely).
        if (userZoom > LOD_LABEL_MIN_ZOOM) {
            drawContext.canvas.nativeCanvas.drawText(
                b.title,
                b.left + b.w / 2f,
                b.top + 13.5f,
                DiagramPaints.blockTitlePaint
            )
            for (p in b.inPins) {
                drawContext.canvas.nativeCanvas.drawText(p.name, b.left + 8f, p.y + 2.6f, DiagramPaints.blockPinLeftPaint)
            }
            for (p in b.outPins) {
                drawContext.canvas.nativeCanvas.drawText(p.name, b.left + b.w - 8f, p.y + 2.6f, DiagramPaints.blockPinRightPaint)
            }
        }
    }
}

private fun DrawScope.drawTags(layout: LayoutResult, circuit: BreadboardCircuit) {
    val shadow = ChassisBevelShadow.copy(alpha = 0.55f)
    for (t in layout.inputTags) {
        drawTag(t, circuit, isInput = true, shadow = shadow)
    }
    for (t in layout.outputTags) {
        drawTag(t, circuit, isInput = false, shadow = shadow)
    }
}

private fun DrawScope.drawTag(t: TagVisual, circuit: BreadboardCircuit, isInput: Boolean, shadow: Color) {
    val tl = Offset(t.cx - t.w / 2f, t.cy - t.h / 2f)
    val sz = Size(t.w, t.h)
    val borderColor = when (t.role) {
        TagRole.OUTPUT -> PhosphorCore.copy(alpha = 0.40f)
        TagRole.CONSTANT_HIGH -> BusVcc.copy(alpha = 0.55f)
        TagRole.CONSTANT_LOW -> BusGnd.copy(alpha = 0.55f)
        // ChassisBevelHighlight is white on CLEANROOM — an invisible border on the
        // white SurfaceCard tag fill. ChassisDivider keeps a visible rim everywhere.
        TagRole.INPUT -> ChassisDivider
    }
    withTransform({ translate(SHADOW_DX - 0.1f, SHADOW_DY - 0.2f) }) {
        drawRoundRect(color = shadow, topLeft = tl, size = sz, cornerRadius = TagCorner)
    }
    drawRoundRect(color = SurfaceCard, topLeft = tl, size = sz, cornerRadius = TagCorner)
    drawRoundRect(color = borderColor, topLeft = tl, size = sz, cornerRadius = TagCorner, style = OutlineStroke)

    val on = if (isInput) sourceLevel(circuit, t) else sinkLevel(circuit, t)
    val dotColor = when (t.role) {
        TagRole.CONSTANT_HIGH -> BusVcc
        TagRole.CONSTANT_LOW -> BusGnd
        else -> if (on) PhosphorCore else ChassisDivider
    }
    val dotX = t.cx + t.w / 2f - 10f
    if (on && t.role != TagRole.CONSTANT_LOW) {
        drawCircle(color = dotColor.copy(alpha = 0.22f), radius = 6.5f, center = Offset(dotX, t.cy))
    }
    drawCircle(color = dotColor, radius = 3.2f, center = Offset(dotX, t.cy))
    drawContext.canvas.nativeCanvas.drawText(
        t.label,
        t.cx - t.w / 2f + 8f,
        t.cy + 3.2f,
        DiagramPaints.tagLabelPaint
    )
}

/** Live level of a driving terminal (switch / pulser / clock / rail constant). */
internal fun sourceLevel(circuit: BreadboardCircuit, t: TagVisual): Boolean {
    val s = t.source ?: return false
    // Gate on master power like the bench's own indicators (the LED bank dims when
    // the console switch is off); the wires already read LOW via routeEdges.
    if (!circuit.masterPower && s.kind != SourceKind.VCC && s.kind != SourceKind.GND) return false
    return when (s.kind) {
        SourceKind.SWITCH -> circuit.switches[s.switchIndex]
        SourceKind.VCC -> true
        SourceKind.GND -> false
        SourceKind.PULSER_P -> if (s.pulserIndex == 0) circuit.pulserAPressed else circuit.pulserBPressed
        SourceKind.PULSER_N -> if (s.pulserIndex == 0) !circuit.pulserAPressed else !circuit.pulserBPressed
        SourceKind.CLOCK -> circuit.clockRunning && circuit.clockFrequencyHz > 0.0 && circuit.clockState
        SourceKind.CLOCK_INV -> circuit.clockRunning && circuit.clockFrequencyHz > 0.0 && !circuit.clockState
    }
}

/** Live level of an observed output (LED monitor / 7-segment BCD bit). */
internal fun sinkLevel(circuit: BreadboardCircuit, t: TagVisual): Boolean {
    if (!circuit.masterPower) return false
    val s = t.sink ?: return false
    return when (s.kind) {
        SinkKind.LED -> circuit.ledValues[s.ledIndex]
        SinkKind.SEGMENT -> circuit.getSocketLevel(s.socket) == ElectricalLevel.HIGH
    }
}
