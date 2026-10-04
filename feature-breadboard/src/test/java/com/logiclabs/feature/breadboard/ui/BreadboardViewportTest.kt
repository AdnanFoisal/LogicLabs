package com.logiclabs.feature.breadboard.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.logiclabs.feature.breadboard.canvas.BreadboardGeometryMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Viewport framing: the zoom-out floor and what happens to the board when the pane changes size.
 *
 * Both bugs these tests pin were reported from a real device as "at max zoom-out there is a lot of
 * black space below the breadboard" and "when zoomed in I can't go down from the zoomed part".
 * They are the same class of defect — the board was framed against a viewport that no longer
 * matched the one on screen.
 *
 * The pane is not a constant: the console dock collapses and expands, the context ribbon appears
 * and disappears, and the device rotates. Every one of those changes the measured viewport.
 */
class BreadboardViewportTest {

    private val mapper = BreadboardGeometryMapper()

    /** A portrait phone pane: 1080 px wide, with the console dock taking the bottom third. */
    private val portrait = Size(1080f, 1400f)

    private class Frame(val top: Float, val bottom: Float) {
        val height get() = bottom - top
    }

    private fun frameOf(state: BreadboardCanvasState, vp: Size): Frame {
        val s = state.scale.value
        val p = state.pan.value
        return Frame(p.y + mapper.boardTop * s, p.y + mapper.boardBottom * s)
    }

    private fun frameXOf(state: BreadboardCanvasState): Pair<Float, Float> {
        val s = state.scale.value
        val p = state.pan.value
        return (p.x + mapper.boardLeft * s) to (p.x + mapper.boardRight * s)
    }

    private fun fitted(vp: Size): BreadboardCanvasState =
        BreadboardCanvasState().also { it.onViewportMeasured(vp, mapper, null) }

    private fun zoomOutUntilStuck(state: BreadboardCanvasState) {
        var guard = 0
        while (guard++ < 60) {
            val before = state.scale.value
            state.nudgeZoom(1f / 1.05f)
            if (kotlin.math.abs(state.scale.value - before) < 1e-5f) return
        }
    }

    private fun zoomInUntilStuck(state: BreadboardCanvasState) {
        var guard = 0
        while (guard++ < 80) {
            val before = state.scale.value
            state.nudgeZoom(1.05f)
            if (kotlin.math.abs(state.scale.value - before) < 1e-5f) return
        }
    }

    // -----------------------------------------------------------------------------------------
    // Zoom-out floor
    // -----------------------------------------------------------------------------------------

    @Test
    fun zoomingOutStopsWhereTheWholeBoardIsVisible() {
        val state = fitted(portrait)
        zoomOutUntilStuck(state)

        // The board is a 1740x945 landscape slab in a 1080-wide pane, so width is the binding
        // axis: at the floor it must span the pane exactly, not shrink to a fraction of it.
        val (left, right) = frameXOf(state)
        assertEquals("board must fill the pane width at max zoom-out", 1080f, right - left, 1f)
        assertEquals("and sit flush left", 0f, left, 1f)
        assertEquals("and flush right", 1080f, right, 1f)

        val expected = minOf(1080f / mapper.boardWidth(), 1400f / mapper.boardHeight())
        assertEquals("floor is the whole-board contain fit", expected, state.scale.value, 1e-3f)
    }

    @Test
    fun zoomingOutNeverMakesTheBoardSmallerThanTheFit() {
        val state = fitted(portrait)
        val floor = state.minZoom.value
        val fitted = state.scale.value

        assertTrue("the opening fit must not start below the floor", fitted >= floor - 1e-4f)
        zoomOutUntilStuck(state)
        assertTrue("zoom-out must not pass the floor", state.scale.value >= floor - 1e-4f)
    }

    @Test
    fun theZoomFloorTracksThePane() {
        val widthBound = 1080f / mapper.boardWidth()

        // On a normal portrait pane the width is what runs out first, so extra height buys
        // nothing: 1400 px and 1900 px of pane share the same floor.
        val normal = fitted(Size(1080f, 1400f))
        val tall = fitted(Size(1080f, 1900f))
        assertEquals("width-bound floor", widthBound, normal.minZoom.value, 1e-3f)
        assertEquals("extra height must not raise the floor", widthBound, tall.minZoom.value, 1e-3f)

        // Squash the pane below the board's own aspect and height becomes the binding axis, so
        // the floor drops to let the whole board stay visible.
        val squashed = fitted(Size(1080f, 400f))
        assertEquals("height-bound floor", 400f / mapper.boardHeight(), squashed.minZoom.value, 1e-3f)
        assertTrue(
            "a short pane must allow a smaller zoom-out than a tall one",
            squashed.minZoom.value < normal.minZoom.value
        )
    }

    // -----------------------------------------------------------------------------------------
    // Resizing the pane
    // -----------------------------------------------------------------------------------------

    @Test
    fun growingThePaneRecentresInsteadOfStrandingTheBoardAtTheTop() {
        // The reported symptom: the pane grows and every extra pixel becomes empty canvas below
        // the board, because the old pan was computed for the old pane and never revisited.
        val state = BreadboardCanvasState()
        state.onViewportMeasured(Size(1080f, 900f), mapper, null)
        state.onViewportMeasured(Size(1080f, 1900f), mapper, null)

        val f = frameOf(state, Size(1080f, 1900f))
        val above = f.top
        val below = 1900f - f.bottom
        assertEquals("board must be re-centred after the pane grows", above, below, 1f)
        assertTrue("and must not be stranded above a field of empty canvas", below < 700f)
    }

    @Test
    fun theVerticalAxisIsNeverDead() {
        // The reported symptom. The board is only ~587 px tall in a ~953 px pane, so "the whole
        // board fits vertically" was true for the first two steps of the zoom control — and the
        // old clamp answered that by pinning the axis to dead centre, leaving exactly zero pixels
        // of travel, so a drag did nothing at all. Every zoom level must leave the board movable.
        val state = fitted(portrait)

        for (step in 0..6) {
            if (step > 0) state.nudgeZoom(1.25f)
            val s = state.scale.value
            val here = state.pan.value

            val up = state.clampPan(here + Offset(0f, -80f), s)
            val down = state.clampPan(here + Offset(0f, 80f), s)
            val travel = maxOf(
                kotlin.math.abs(up.y - here.y),
                kotlin.math.abs(down.y - here.y)
            )

            assertTrue(
                "at scale $s the vertical axis has no travel (board ${frameOf(state, portrait).height} " +
                    "px in a ${portrait.height} px pane), so dragging cannot move it",
                travel > 1f
            )
        }
    }

    @Test
    fun aFittingBoardCanBeParkedAnywhereInsideThePaneButNotOffIt() {
        val state = fitted(portrait)

        // Up as far as it goes: flush with the top edge margin, no further.
        state.panBy(Offset(0f, -100_000f))
        val high = frameOf(state, portrait)
        assertEquals("board top stops at the edge margin", 24f, high.top, 1f)

        // Down as far as it goes: flush with the bottom edge margin, no further.
        state.panBy(Offset(0f, 100_000f))
        val low = frameOf(state, portrait)
        assertEquals("board bottom stops at the edge margin", 1400f - 24f, low.bottom, 1f)
    }

    @Test
    fun shrinkingThePanePullsTheBoardBackInside() {
        // The other half: the pane shrinks and the board's bottom ends up past the visible edge,
        // where it cannot even be panned to because the board still fits and the axis is locked.
        val state = BreadboardCanvasState()
        state.onViewportMeasured(Size(1080f, 1900f), mapper, null)
        state.onViewportMeasured(Size(1080f, 900f), mapper, null)

        val f = frameOf(state, Size(1080f, 900f))
        assertTrue("board top must be inside the pane", f.top >= -1f)
        assertTrue("board bottom must be inside the pane, was ${f.bottom}", f.bottom <= 901f)
        assertEquals("re-centred", f.top, 900f - f.bottom, 1f)
    }

    @Test
    fun resizingRepeatedlyLeavesNoAccumulatedDrift() {
        val state = BreadboardCanvasState()
        val sizes = listOf(
            Size(1080f, 1400f), Size(1080f, 900f), Size(1080f, 1900f),
            Size(1080f, 900f), Size(1080f, 1400f)
        )
        for (s in sizes) state.onViewportMeasured(s, mapper, null)

        val f = frameOf(state, portrait)
        assertEquals("framing must depend only on the final pane", f.top, 1400f - f.bottom, 1f)
        assertTrue(state.scale.value >= state.minZoom.value - 1e-4f)
    }

    // -----------------------------------------------------------------------------------------
    // Panning while zoomed in
    // -----------------------------------------------------------------------------------------

    @Test
    fun bothBoardEdgesAreReachableWhenZoomedIn() {
        val state = fitted(portrait)
        zoomInUntilStuck(state)
        assertTrue("must actually be zoomed in", state.scale.value > 1.5f)

        // Drag up as far as it will go: the bottom of the board comes to the pane's bottom edge.
        state.panBy(Offset(0f, -100_000f))
        val low = frameOf(state, portrait)
        assertEquals("bottom edge must be reachable", 1400f - 24f, low.bottom, 1f)
        assertTrue("and the top may be off-screen", low.top < 0f)

        // Drag down as far as it will go: the top of the board comes to the pane's top edge.
        state.panBy(Offset(0f, 100_000f))
        val high = frameOf(state, portrait)
        assertEquals("top edge must be reachable", 24f, high.top, 1f)
        assertTrue("and the bottom may be off-screen", high.bottom > 1400f)
    }

    @Test
    fun aResizedPaneIsImmediatelyPannableToItsNewLimits() {
        // Zoom in, shrink the pane, then pan: the limits must be the *new* pane's, so the board's
        // bottom is still reachable without the user having to guess that a gesture is needed to
        // repair the framing.
        val state = fitted(portrait)
        zoomInUntilStuck(state)
        state.onViewportMeasured(Size(1080f, 900f), mapper, null)

        state.panBy(Offset(0f, -100_000f))
        val f = frameOf(state, Size(1080f, 900f))
        assertEquals("bottom edge reachable in the shrunken pane", 900f - 24f, f.bottom, 1f)
    }
}
