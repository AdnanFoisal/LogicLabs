package com.logiclabs.feature.tools.diagram

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagramLayoutTest {

    @Test
    fun neonPaletteHas12DistinctVibrantColors() {
        assertEquals(12, NeonPalette.size)
        val distinctColors = NeonPalette.toSet()
        assertEquals("All 12 colors in NeonPalette must be distinct", 12, distinctColors.size)

        for (color in NeonPalette) {
            assertNotEquals("Palette color must not be Unspecified", Color.Unspecified, color)
            assertTrue("Alpha should be fully opaque", color.alpha >= 0.99f)
        }
    }

    @Test
    fun spacingConstantsAreProperlyExpanded() {
        assertEquals(60f, DiagramLayout.COL_GAP, 0.001f)
        assertEquals(36f, DiagramLayout.ROW_GAP, 0.001f)
        assertEquals(8.5f, DiagramLayout.STAGGER_PITCH, 0.001f)
        assertEquals(3.5f, DiagramLayout.HOP_RADIUS, 0.001f)
    }

    @Test
    fun welshPowellColoringEnforcesGateInputOutputDistinctColors() {
        // Gate U1 with inputs N_IN1, N_IN2 and output N_OUT
        val edges = listOf(
            DiagramLayout.NetEdgeInfo(netKey = "N_IN1", toUnitId = "U1"),
            DiagramLayout.NetEdgeInfo(netKey = "N_IN2", toUnitId = "U1"),
            DiagramLayout.NetEdgeInfo(netKey = "N_OUT", fromUnitId = "U1")
        )

        val colors = DiagramLayout.computeWelshPowellColors(
            unitIds = listOf("U1"),
            outputIds = emptyList(),
            edges = edges
        )

        val cIn1 = colors["N_IN1"]!!
        val cIn2 = colors["N_IN2"]!!
        val cOut = colors["N_OUT"]!!

        // Outputs != Inputs
        assertNotEquals("Gate output must not collide with input 1", cOut, cIn1)
        assertNotEquals("Gate output must not collide with input 2", cOut, cIn2)

        // Inputs != Inputs
        assertNotEquals("Gate input 1 must not collide with input 2", cIn1, cIn2)
    }

    @Test
    fun welshPowellColoringMultiGateZeroCollision() {
        // Chain of 3 gates: U1 -> U2 -> U3
        val edges = listOf(
            DiagramLayout.NetEdgeInfo(netKey = "N1", toUnitId = "U1"),
            DiagramLayout.NetEdgeInfo(netKey = "N2", toUnitId = "U1"),
            DiagramLayout.NetEdgeInfo(netKey = "N3", fromUnitId = "U1", toUnitId = "U2"),
            DiagramLayout.NetEdgeInfo(netKey = "N4", toUnitId = "U2"),
            DiagramLayout.NetEdgeInfo(netKey = "N5", fromUnitId = "U2", toUnitId = "U3"),
            DiagramLayout.NetEdgeInfo(netKey = "N6", fromUnitId = "U3", toOutputId = "O1")
        )

        val colors = DiagramLayout.computeWelshPowellColors(
            unitIds = listOf("U1", "U2", "U3"),
            outputIds = listOf("O1"),
            edges = edges
        )

        // Verify all nets are assigned colors from NeonPalette
        for ((net, color) in colors) {
            assertTrue("Net $net color must belong to NeonPalette", color in NeonPalette)
        }

        // Check U1: N1, N2, N3
        assertNotEquals(colors["N1"], colors["N2"])
        assertNotEquals(colors["N1"], colors["N3"])
        assertNotEquals(colors["N2"], colors["N3"])

        // Check U2: N3, N4, N5
        assertNotEquals(colors["N3"], colors["N4"])
        assertNotEquals(colors["N3"], colors["N5"])
        assertNotEquals(colors["N4"], colors["N5"])

        // Check U3: N5, N6
        assertNotEquals(colors["N5"], colors["N6"])
    }

    @Test
    fun horizontalCrossingDetectionDetectsValidCrossing() {
        val vertSegs = listOf(
            DiagramLayout.VertSeg(netKey = "NET_V", x = 50f, minY = 10f, maxY = 90f)
        )

        // Horizontal segment from (20, 50) to (80, 50) crossing x=50 at y=50
        val crossings = DiagramLayout.detectHorizontalCrossings(
            x1 = 20f,
            x2 = 80f,
            hy = 50f,
            netKey = "NET_H",
            vertSegs = vertSegs,
            junctions = emptyList()
        )

        assertEquals(1, crossings.size)
        assertEquals(50f, crossings[0], 0.001f)
    }

    @Test
    fun horizontalCrossingDetectionIgnoresSameNetAndJunctions() {
        val vertSegs = listOf(
            // Same net - should not produce a bridge hop
            DiagramLayout.VertSeg(netKey = "NET_H", x = 50f, minY = 10f, maxY = 90f)
        )

        val crossings = DiagramLayout.detectHorizontalCrossings(
            x1 = 20f,
            x2 = 80f,
            hy = 50f,
            netKey = "NET_H",
            vertSegs = vertSegs,
            junctions = emptyList()
        )

        assertTrue("Same net crossing must not create hop", crossings.isEmpty())

        // Cross of different net BUT junction dot exists at crossing
        val vertSegsDiff = listOf(
            DiagramLayout.VertSeg(netKey = "NET_V", x = 50f, minY = 10f, maxY = 90f)
        )
        val junctionAtCrossing = listOf(
            JunctionVisual(x = 50f, y = 50f, levelSocket = 1, neonColor = NeonPalette[0])
        )

        val crossingsWithJunction = DiagramLayout.detectHorizontalCrossings(
            x1 = 20f,
            x2 = 80f,
            hy = 50f,
            netKey = "NET_H",
            vertSegs = vertSegsDiff,
            junctions = junctionAtCrossing
        )

        assertTrue("Crossing at junction dot must not create hop", crossingsWithJunction.isEmpty())
    }

    @Test
    fun horizontalCrossingDetectionRespectsHopRadiusClearance() {
        val vertSegs = listOf(
            // Too close to left end (x1=20f, vs.x=22f, min distance is HOP_RADIUS + 0.5f = 4f)
            DiagramLayout.VertSeg(netKey = "NET_V1", x = 22f, minY = 10f, maxY = 90f),
            // Valid crossing
            DiagramLayout.VertSeg(netKey = "NET_V2", x = 50f, minY = 10f, maxY = 90f)
        )

        val crossings = DiagramLayout.detectHorizontalCrossings(
            x1 = 20f,
            x2 = 80f,
            hy = 50f,
            netKey = "NET_H",
            vertSegs = vertSegs
        )

        assertEquals(1, crossings.size)
        assertEquals(50f, crossings[0], 0.001f)
    }

    @Test
    fun crossingNetsReceiveDistinctNeonColorsInWelshPowell() {
        val edges = listOf(
            DiagramLayout.NetEdgeInfo(netKey = "N_HORIZ"),
            DiagramLayout.NetEdgeInfo(netKey = "N_VERT")
        )

        val colors = DiagramLayout.computeWelshPowellColors(
            unitIds = emptyList(),
            outputIds = emptyList(),
            edges = edges,
            crossings = listOf("N_HORIZ" to "N_VERT")
        )

        assertNotEquals(colors["N_HORIZ"], colors["N_VERT"])
    }
}
