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

    /**
     * Zoom-out floor for the current viewport: the scale at which the whole board exactly fits
     * inside it (`min(viewportW / boardW, viewportH / boardH)`).
     *
     * This is what replaced the bare [MIN_SCALE] constant. On a portrait phone the board is a
     * 1740x945 landscape slab, so the width-fit scale is already only ~0.62 — the old floor of
     * 0.4 let the user shrink it to ~64% of that, leaving a small board floating in a field of
     * empty canvas on every side. Zooming out past "the whole board is visible" shows nothing
     * new, so the floor now sits exactly there and moves with the pane.
     */
    val minZoom: MutableState<Float> = mutableStateOf(MIN_SCALE)

    /** Measured viewport, screen px. [Size.Zero] until the first layout pass. */
    val viewportSize: MutableState<Size> = mutableStateOf(Size.Zero)

    private var didFit = false

    /**
     * Scale at which [viewport] shows the entire board, or [MIN_SCALE] if it cannot be measured.
     *
     * `min` of the two axes is a *contain* fit: the board ends up flush against whichever pair of
     * edges runs out first, and the other axis carries the letterbox. That letterbox is inherent
     * to showing a landscape board on a portrait pane — the point is that it is symmetric and
     * that the board can never be made smaller than it.
     */
    private fun wholeBoardFitScale(viewport: Size): Float {
        val m = mapper ?: return MIN_SCALE
        if (viewport.width <= 0f || viewport.height <= 0f) return MIN_SCALE
        val fit = minOf(viewport.width / m.boardWidth(), viewport.height / m.boardHeight())
        return fit.coerceIn(MIN_SCALE, MAX_SCALE)
    }

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
     * Clamps panning so the board can never be scrolled out of the viewport.
     *
     * The two bounds are placed once and then simply ordered, which is what makes this rule
     * behave correctly in both directions:
     *
     *  - `flushTop` is the pan that puts the board's top edge at [EDGE_MARGIN].
     *  - `flushBottom` is the pan that puts its bottom edge at `viewport − EDGE_MARGIN`.
     *
     * When the board is *smaller* than the pane, `flushTop < flushBottom` and the board may be
     * parked anywhere between those two — dragged up, dragged down, but never off the pane.
     * When it is *larger*, the ordering reverses and the same two numbers become the travel
     * limits of a scroll: you can reach the top edge and the bottom edge, and nothing beyond.
     *
     * The previous version hard-locked a fitting axis to dead centre instead. On a portrait
     * phone the board is only ~587 px tall in a ~953 px pane, so "fits" was true for the whole
     * first two steps of the zoom control: tapping + and dragging did *nothing at all*, which
     * reads as a broken viewport rather than as a deliberate lock. Same rule on the x axis.
     */
    fun clampPan(candidate: Offset, currentScale: Float): Offset {
        val m = mapper ?: return candidate
        val vp = viewportSize.value
        if (vp == Size.Zero) return candidate
        val s = currentScale

        val flushTop = EDGE_MARGIN - m.boardTop * s
        val flushBottom = (vp.height - EDGE_MARGIN) - m.boardBottom * s
        val clampedY = if (flushTop <= flushBottom) {
            candidate.y.coerceIn(flushTop, flushBottom)
        } else {
            candidate.y.coerceIn(flushBottom, flushTop)
        }

        val flushLeft = EDGE_MARGIN - m.boardLeft * s
        val flushRight = (vp.width - EDGE_MARGIN) - m.boardRight * s
        val clampedX = if (flushLeft <= flushRight) {
            candidate.x.coerceIn(flushLeft, flushRight)
        } else {
            candidate.x.coerceIn(flushRight, flushLeft)
        }

        return Offset(clampedX, clampedY)
    }

    /**
     * Puts a fitting axis back in the middle and leaves an over-flowing one where it is.
     *
     * Called only when the pane itself changes size. [clampPan] deliberately allows free
     * positioning inside the pane, which is right for a gesture — the board should follow the
     * finger — but wrong for a layout change: if the console dock collapses and the pane grows
     * 1000 px taller, keeping the old pan would dump all 1000 px of new space below the board.
     * Re-centring the fitting axes is what stops a resize from stranding the board at one edge.
     */
    private fun recentreFittingAxes(s: Float) {
        val m = mapper ?: return
        val vp = viewportSize.value
        if (vp == Size.Zero) return
        val p = pan.value

        val contentH = m.boardHeight() * s
        val contentW = m.boardWidth() * s

        val y = if (contentH + 2f * EDGE_MARGIN <= vp.height) {
            (vp.height - contentH) / 2f - m.boardTop * s
        } else p.y

        val x = if (contentW + 2f * EDGE_MARGIN <= vp.width) {
            (vp.width - contentW) / 2f - m.boardLeft * s
        } else p.x

        pan.value = clampPan(Offset(x, y), s)
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
        val next = (old * zoomChange).coerceIn(minZoom.value, MAX_SCALE)
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
     * Records the measured viewport, re-derives the zoom floor from it, and fits the board on the
     * first pass.
     *
     * ### Why the resize branch exists
     * The pane is not a constant: the console dock expands and collapses, the context ribbon comes
     * and goes, and the device rotates. This used to update [viewportSize] and nothing else, so the
     * board stayed exactly where the *previous* pane had centred it. Measured on a 1080-wide pane:
     *
     *  - pane grows 900 -> 1900 px: the board keeps its old y, and the extra 1000 px all becomes
     *    empty canvas, with the board stuck near the top (`blackAbove=192, blackBelow=1192`).
     *  - pane shrinks 1900 -> 900 px: the board's bottom lands 308 px below the visible pane, and
     *    because the board still fits vertically the axis is locked to centre — so it cannot be
     *    panned back into view either.
     *
     * Both are the same missing line. Re-clamping on every size change fixes the grow case (the
     * board re-centres) and the shrink case (the board is pulled back inside the pane), and the
     * scale is lifted if the new pane raised the floor above it.
     */
    fun onViewportMeasured(size: Size, mapper: BreadboardGeometryMapper, circuit: BreadboardCircuit? = null) {
        if (size.width <= 0f || size.height <= 0f) return
        this.mapper = mapper
        val changed = size != viewportSize.value
        viewportSize.value = size
        minZoom.value = wholeBoardFitScale(size)
        fitScale.value = (size.width / mapper.boardWidth()).coerceIn(minZoom.value, MAX_SCALE)

        if (!didFit) {
            didFit = true
            applyFit(mapper, circuit)
            return
        }

        if (changed) {
            val s = scale.value.coerceAtLeast(minZoom.value)
            scale.value = s
            recentreFittingAxes(s)
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
            val fit = minZoom.value
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
        // Ceiling keeps a small circuit from being auto-zoomed to the moon; floor is the
        // whole-board fit, so the padding can never push the opening view below it. `cap` is
        // raised to meet the floor rather than the two being coerced against each other, which
        // would throw on a pane big enough to make the floor exceed 2x.
        val cap = maxOf(AUTO_FIT_MAX_SCALE, minZoom.value)
        val targetScale = minOf(fitX, fitY).coerceIn(minZoom.value, cap)

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
        /**
         * Absolute zoom-out guard for degenerate viewports (a 1 px pane during animation, a
         * measurement taken before layout). The *effective* floor is [minZoom], which is derived
         * from the board and the pane — never smaller than the whole board needs.
         */
        const val MIN_SCALE = 0.05f
        const val MAX_SCALE = 4.0f

        /** Ceiling for the automatic opening fit, so a tiny circuit is not blown up to 4x. */
        private const val AUTO_FIT_MAX_SCALE = 2.0f

        /** Screen px of breathing room between the board edges and the viewport when clamped. */
        private const val EDGE_MARGIN = 24f
    }
}
