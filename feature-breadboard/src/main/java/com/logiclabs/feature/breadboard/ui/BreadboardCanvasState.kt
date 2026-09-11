package com.logiclabs.feature.breadboard.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.feature.breadboard.canvas.BreadboardGeometryMapper
import com.logiclabs.feature.breadboard.interaction.LoupeState

/**
 * Everything the breadboard canvas mutates while the user drags, pans and pinches.
 *
 * ### Why a holder rather than `remember`ed locals
 * Pan and zoom change on every pointer event. If the composable body *read* them, each event would
 * recompose the whole canvas — and, before this, re-enter the circuit's `step()`. Keeping them
 * behind a stable holder means the only readers are draw lambdas and gesture coroutines, neither of
 * which participates in composition, so a pinch invalidates draw and nothing else.
 *
 * State is exposed as [MutableState] rather than delegated properties precisely so that a read is
 * always visibly `.value` at the call site and can be audited.
 */
@Stable
class BreadboardCanvasState {

    // --- Viewport -----------------------------------------------------------------------------
    val scale: MutableState<Float> = mutableStateOf(1f)
    val pan: MutableState<Offset> = mutableStateOf(Offset.Zero)

    /** Zoom that fits the whole board across the measured viewport width. */
    val fitScale: MutableState<Float> = mutableStateOf(1f)

    /** Measured viewport, screen px. [Size.Zero] until the first layout pass. */
    val viewportSize: MutableState<Size> = mutableStateOf(Size.Zero)

    private var didFit = false

    // --- In-progress wire ---------------------------------------------------------------------
    /** Socket the draft wire is anchored to — set by both tap-to-connect and drag-to-connect. */
    val draftAnchor: MutableState<Int?> = mutableStateOf(null)

    /** Finger position in board coordinates while a draft wire is being dragged. */
    val draftFinger: MutableState<Offset?> = mutableStateOf(null)

    /** Socket the magnet has locked onto, and how firmly (0..1). Drives the guide wire's flex. */
    val draftLocked: MutableState<Int?> = mutableStateOf(null)
    val draftLockStrength: MutableState<Float> = mutableStateOf(0f)

    // --- Loupe --------------------------------------------------------------------------------
    val loupe: MutableState<LoupeState> = mutableStateOf(LoupeState())

    // --- Net highlighting ---------------------------------------------------------------------
    /**
     * Sockets on the electrically-selected nets. Recomputed on discrete events only
     * ([recomputeHighlights]) so the draw path never allocates a set per frame.
     */
    val highlights: MutableState<Set<Int>> = mutableStateOf(emptySet())

    // --- Reactive Redraw Triggers -------------------------------------------------------------
    val renderVersion: MutableState<Int> = mutableStateOf(0)
    val circuitVersion: MutableState<Int> = mutableStateOf(0)

    fun toBoard(screen: Offset): Offset {
        val s = scale.value
        return (screen - pan.value) / (if (s <= 0f) 1f else s)
    }

    var mapper: BreadboardGeometryMapper? = null

    /**
     * Clamps panning so the board can never be scrolled past its own edges.
     *
     * * **Board larger than the viewport (zoomed in):** the top edge may never drop
     *   below [EDGE_MARGIN] px and the bottom edge may never rise above
     *   `viewport − EDGE_MARGIN` px — both instrument plates are always recoverable
     *   with a pan and neither can be overscrolled into blank space (the old clamp
     *   allowed the bottom edge 60px of under-scroll and, when zoomed out, pinned the
     *   board to the top with all slack at the bottom). Same rule on the x axis.
     * * **Board fits the viewport (zoomed out):** centred on both axes — there is
     *   nothing to scroll to, and a locked/pegged pan is what made the fit feel broken.
     */
    fun clampPan(candidate: Offset, currentScale: Float): Offset {
        val m = mapper ?: return candidate
        val vp = viewportSize.value
        if (vp == Size.Zero) return candidate
        val s = currentScale

        val boardTop = m.boardTop
        val boardBottom = m.boardBottom
        val contentH = (boardBottom - boardTop) * s
        val clampedY = if (contentH + 2f * EDGE_MARGIN <= vp.height) {
            (vp.height - contentH) / 2f - boardTop * s
        } else {
            val maxPanY = EDGE_MARGIN - boardTop * s
            val minPanY = (vp.height - EDGE_MARGIN) - boardBottom * s
            candidate.y.coerceIn(minPanY, maxPanY)
        }

        val boardLeft = m.boardLeft
        val boardRight = m.boardRight
        val contentW = (boardRight - boardLeft) * s
        val clampedX = if (contentW + 2f * EDGE_MARGIN <= vp.width) {
            (vp.width - contentW) / 2f - boardLeft * s
        } else {
            val maxPanX = EDGE_MARGIN - boardLeft * s
            val minPanX = (vp.width - EDGE_MARGIN) - boardRight * s
            candidate.x.coerceIn(minPanX, maxPanX)
        }

        return Offset(clampedX, clampedY)
    }

    /**
     * Applies a pinch about the gesture [centroid].
     *
     * `scale(s, s, Offset.Zero)` pivots at the canvas origin, so a naive `scale *= zoom` walks the
     * board out from under the fingers. Holding the centroid fixed requires
     * `pan += centroid − (centroid − pan) · (newScale / oldScale)`, which is what this does; the
     * two-finger translation is then added on top. Clamping ensures the top rails stay anchored.
     */
    fun applyPinch(zoomChange: Float, panChange: Offset, centroid: Offset) {
        val old = scale.value
        if (old <= 0f) return
        val next = (old * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = next / old
        val p = pan.value
        val rawPan = Offset(
            centroid.x - (centroid.x - p.x) * k + panChange.x,
            centroid.y - (centroid.y - p.y) * k + panChange.y
        )
        scale.value = next
        pan.value = clampPan(rawPan, next)
    }

    /** Zooms about the viewport centre — used by the +/− buttons, which have no centroid. */
    fun nudgeZoom(factor: Float) {
        val vp = viewportSize.value
        val centroid = if (vp == Size.Zero) Offset.Zero else Offset(vp.width / 2f, vp.height / 2f)
        applyPinch(factor, Offset.Zero, centroid)
    }

    fun panBy(delta: Offset) {
        pan.value = clampPan(pan.value + delta, scale.value)
    }

    /**
     * Records the measured viewport and, on the first pass only, fits the board to it.
     */
    fun onViewportMeasured(size: Size, mapper: BreadboardGeometryMapper, circuit: BreadboardCircuit? = null) {
        if (size.width <= 0f || size.height <= 0f) return
        this.mapper = mapper
        val changed = size != viewportSize.value
        viewportSize.value = size

        val fit = (size.width / mapper.boardWidth()).coerceIn(MIN_SCALE, MAX_SCALE)
        fitScale.value = fit

        if (!didFit || changed && !didFit) {
            didFit = true
            applyFit(mapper, circuit)
        }
    }

    /**
     * Centers and frames either the active circuit bounding-box (if chips/wires are present),
     * or the entire breadboard (if the board is empty).
     */
    fun applyFit(mapper: BreadboardGeometryMapper, circuit: BreadboardCircuit? = null) {
        this.mapper = mapper
        val vp = viewportSize.value
        val bounds = if (circuit != null && (circuit.placedChips.isNotEmpty() || circuit.wires.isNotEmpty())) {
            calculateCircuitBounds(mapper, circuit)
        } else {
            mapper.boardBounds()
        }

        if (bounds.width <= 0f || bounds.height <= 0f || vp.width <= 0f || vp.height <= 0f) {
            val fit = fitScale.value
            val boardBounds = mapper.boardBounds()
            val yGap = if (vp == Size.Zero) 0f else (vp.height - mapper.boardHeight() * fit) / 2f
            scale.value = fit
            pan.value = clampPan(Offset(-boardBounds.left * fit, yGap - boardBounds.top * fit), fit)
            return
        }

        // Add comfortable padding around the active circuit components
        val pad = 120f
        val paddedWidth = bounds.width + pad * 2f
        val paddedHeight = bounds.height + pad * 2f

        val fitX = vp.width / paddedWidth
        val fitY = vp.height / paddedHeight
        val targetScale = minOf(fitX, fitY).coerceIn(MIN_SCALE, 2.0f)

        scale.value = targetScale
        val cx = bounds.left - pad
        val cy = bounds.top - pad
        pan.value = clampPan(
            Offset(
                (vp.width - paddedWidth * targetScale) / 2f - cx * targetScale,
                (vp.height - paddedHeight * targetScale) / 2f - cy * targetScale
            ),
            targetScale
        )
    }

    fun resetFit(circuit: BreadboardCircuit? = null) {
        val m = mapper ?: return
        applyFit(m, circuit)
    }

    private fun calculateCircuitBounds(mapper: BreadboardGeometryMapper, circuit: BreadboardCircuit): Rect {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        fun includePoint(pos: Offset) {
            if (pos != Offset.Unspecified) {
                if (pos.x < minX) minX = pos.x
                if (pos.x > maxX) maxX = pos.x
                if (pos.y < minY) minY = pos.y
                if (pos.y > maxY) maxY = pos.y
            }
        }

        // Always include top panel & power rails so the upper instruments are always in view
        includePoint(Offset(mapper.boardLeft, mapper.boardTop))
        includePoint(Offset(mapper.boardRight, mapper.boardTop))

        for (chip in circuit.placedChips) {
            for (pinNum in 1..chip.model.pinCount) {
                includePoint(mapper.getSocketPosition(chip.getPinSocket(pinNum)))
            }
        }

        for (wire in circuit.wires) {
            includePoint(mapper.getSocketPosition(wire.startSocket))
            includePoint(mapper.getSocketPosition(wire.endSocket))
        }

        return if (minX < maxX && minY < maxY) {
            Rect(minX, minY, maxX, maxY)
        } else {
            mapper.boardBounds()
        }
    }

    fun clearDraft() {
        draftAnchor.value = null
        draftFinger.value = null
        draftLocked.value = null
        draftLockStrength.value = 0f
    }

    fun hideLoupe() {
        val l = loupe.value
        if (l.isVisible) loupe.value = l.copy(isVisible = false)
    }

    /**
     * Rebuilds [highlights] from the draft anchor, the magnet lock and the selected wire.
     * Called from gesture callbacks and on selection changes — never per frame.
     */
    fun recomputeHighlights(
        mapper: BreadboardGeometryMapper,
        circuit: BreadboardCircuit,
        selectedWireId: String?
    ) {
        val anchor = draftAnchor.value
        val locked = draftLocked.value
        if (anchor == null && locked == null && selectedWireId == null) {
            if (highlights.value.isNotEmpty()) highlights.value = emptySet()
            return
        }
        val set = HashSet<Int>(32)
        anchor?.let { set.addAll(mapper.getConnectedColumnSockets(it)) }
        locked?.let { set.addAll(mapper.getConnectedColumnSockets(it)) }
        if (selectedWireId != null) {
            val w = circuit.wires.firstOrNull { it.id == selectedWireId }
            if (w != null) {
                set.addAll(mapper.getConnectedColumnSockets(w.startSocket))
                set.addAll(mapper.getConnectedColumnSockets(w.endSocket))
            }
        }
        highlights.value = set
    }

    companion object {
        const val MIN_SCALE = 0.4f
        const val MAX_SCALE = 4.0f

        /** Screen px of breathing room between the board edges and the viewport when clamped. */
        private const val EDGE_MARGIN = 24f
    }
}
