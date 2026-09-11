package com.logiclabs.core.testing.tier1_coverage

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertNetConnected
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertNetIsolated
import com.logiclabs.core.testing.harness.NetlistDsu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Coverage: AD-200 Breadboard Geometry & NetlistDSU.
 * Verifies:
 * - 1,896 tie-points layout (1,280 terminal sockets, 616 distribution rail sockets).
 * - Zero-allocation iterative path compression & union-by-size.
 * - Column continuity (5 sockets per column).
 * - Rail continuity (88 sockets per rail).
 * - Center trench isolation.
 * - Instant arraycopy resetToBase().
 * Requirement: >= 5 distinct tests.
 */
class BreadboardDsuFeatureTest {

    private lateinit var dsu: NetlistDsu

    @Before
    fun setUp() {
        dsu = NetlistDsu()
    }

    @Test
    fun testMathematicalSocketCountsAndBounds() {
        assertEquals("Terminal sockets must be 1,280", 1280, BreadboardGeometry.TOTAL_TERMINAL_SOCKETS)
        assertEquals("Rail sockets must be 616", 616, BreadboardGeometry.TOTAL_RAIL_SOCKETS)
        assertEquals("Total AD-200 breadboard sockets must be 1,896", 1896, BreadboardGeometry.TOTAL_BREADBOARD_SOCKETS)

        // Verify boundary indices
        val sFirst = BreadboardGeometry.terminalSocket(0, 0, 0)
        assertEquals(0, sFirst)
        val sLastTerminal = BreadboardGeometry.terminalSocket(3, 63, 4)
        assertEquals(1279, sLastTerminal)

        val rFirst = BreadboardGeometry.railSocket(0, 0)
        assertEquals(1280, rFirst)
        val rLast = BreadboardGeometry.railSocket(6, 87)
        assertEquals(1895, rLast)
    }

    @Test
    fun testColumnContinuity() {
        // Within any block and column, all 5 rows (A, B, C, D, E) must be continuous
        for (block in 0..3) {
            for (col in listOf(0, 15, 32, 63)) {
                val row0 = BreadboardGeometry.terminalSocket(block, col, 0)
                for (row in 1..4) {
                    val rowN = BreadboardGeometry.terminalSocket(block, col, row)
                    assertTrue(
                        "Block $block Col $col Row 0 and Row $row must be in the same net",
                        dsu.areConnected(row0, rowN)
                    )
                }
                assertEquals("Net size of a column must be 5", 5, dsu.getNetSize(row0))
            }
        }
    }

    @Test
    fun testRailContinuity() {
        // Across all 7 rails, all 88 sockets must be connected
        for (rail in 0..6) {
            val s0 = BreadboardGeometry.railSocket(rail, 0)
            for (pos in listOf(1, 20, 44, 70, 87)) {
                val sN = BreadboardGeometry.railSocket(rail, pos)
                assertTrue(
                    "Rail $rail Socket 0 and Socket $pos must be connected",
                    dsu.areConnected(s0, sN)
                )
            }
            assertEquals("Net size of distribution rail must be 88", 88, dsu.getNetSize(s0))
        }
    }

    @Test
    fun testTrenchAndAdjacentColumnIsolation() {
        // Block 0 and Block 1 must be isolated across DIP Trench 1
        val b0Col10Row4 = BreadboardGeometry.terminalSocket(0, 10, 4)
        val b1Col10Row0 = BreadboardGeometry.terminalSocket(1, 10, 0)
        assertTrue(
            "Trench 1 must isolate Block 0 and Block 1",
            !dsu.areConnected(b0Col10Row4, b1Col10Row0)
        )

        // Block 2 and Block 3 must be isolated across DIP Trench 2
        val b2Col20Row4 = BreadboardGeometry.terminalSocket(2, 20, 4)
        val b3Col20Row0 = BreadboardGeometry.terminalSocket(3, 20, 0)
        assertTrue(
            "Trench 2 must isolate Block 2 and Block 3",
            !dsu.areConnected(b2Col20Row4, b3Col20Row0)
        )

        // Adjacent columns in same block must be isolated
        val col1 = BreadboardGeometry.terminalSocket(0, 1, 0)
        val col2 = BreadboardGeometry.terminalSocket(0, 2, 0)
        assertTrue("Adjacent columns must be isolated", !dsu.areConnected(col1, col2))

        // VCC rail (Rail 0) and GND rail (Rail 1) must be isolated
        val vcc = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_VCC_5V, 0)
        val gnd = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_GND, 0)
        assertTrue("VCC and GND rails must be isolated", !dsu.areConnected(vcc, gnd))
    }

    @Test
    fun testJumperWireUnionAndTransitiveMerging() {
        val colA = BreadboardGeometry.terminalSocket(0, 5, 0)
        val colB = BreadboardGeometry.terminalSocket(1, 15, 2)
        val colC = BreadboardGeometry.terminalSocket(2, 25, 4)

        // Initially isolated
        assertTrue(!dsu.areConnected(colA, colB))
        assertTrue(!dsu.areConnected(colB, colC))

        // Wire A <-> B
        dsu.union(colA, colB)
        assertTrue(dsu.areConnected(colA, colB))
        assertEquals(10, dsu.getNetSize(colA)) // two columns merged: 5 + 5 = 10

        // Wire B <-> C: Transitive connection A <-> C
        dsu.union(colB, colC)
        assertTrue("Transitive connection A <-> C must hold", dsu.areConnected(colA, colC))
        assertEquals(15, dsu.getNetSize(colA)) // three columns merged: 15
    }

    @Test
    fun testInstantResetToBase() {
        val colA = BreadboardGeometry.terminalSocket(0, 10, 0)
        val colB = BreadboardGeometry.terminalSocket(3, 50, 0)
        dsu.union(colA, colB)
        assertTrue(dsu.areConnected(colA, colB))

        // Instant reset
        val startNano = System.nanoTime()
        dsu.resetToBase()
        val durationNano = System.nanoTime() - startNano

        assertTrue("Reset must execute in < 100 microseconds", durationNano < 100_000)
        assertTrue("Reset must restore isolation", !dsu.areConnected(colA, colB))
        assertEquals(5, dsu.getNetSize(colA))
        assertEquals(5, dsu.getNetSize(colB))
    }
}
