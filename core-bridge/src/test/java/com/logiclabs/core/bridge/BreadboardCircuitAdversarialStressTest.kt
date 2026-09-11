package com.logiclabs.core.bridge

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Adversarial stress test harness for R6:
 * - Physical collision detection boundaries (exact, left partial, right partial, internal, engulfing).
 * - Multi-trench isolation and boundary limits.
 * - Strict wire isolation when moving empty chips past wired chips (ZERO wire modification).
 * - Precise wire migration when moving wired chips (only chip's own connections move).
 * - Dense packing stress testing.
 */
class BreadboardCircuitAdversarialStressTest {

    private lateinit var circuit: BreadboardCircuit

    @Before
    fun setup() {
        circuit = BreadboardCircuit()
    }

    @Test
    fun testExactOverlapCollision() {
        val chipA = circuit.addChip("7400", trench = 1, startColumn = 10)
        val chipB = circuit.addChip("7408", trench = 1, startColumn = 30)

        // Try moving B onto A's exact position
        assertFalse("Moving chip B to exact start column of chip A must fail", circuit.moveChip(chipB.placedIc.id, 1, 10))
        assertEquals(30, circuit.placedChips.first { it.placedIc.id == chipB.placedIc.id }.placedIc.startColumn)

        // Try moving A onto B's exact position
        assertFalse("Moving chip A to exact start column of chip B must fail", circuit.moveChip(chipA.placedIc.id, 1, 30))
        assertEquals(10, circuit.placedChips.first { it.placedIc.id == chipA.placedIc.id }.placedIc.startColumn)
    }

    @Test
    fun testLeftPartialOverlapCollision() {
        // Chip B occupies columns 20..26 (14-pin DIP, half = 7)
        val chipB = circuit.addChip("7400", trench = 1, startColumn = 20)
        val chipA = circuit.addChip("7408", trench = 1, startColumn = 0)

        // Chip A has 7 columns.
        // startColumn = 14 -> occupies 14..20. Overlaps with chip B at column 20!
        assertFalse("Overlap on left boundary col 20 must fail", circuit.moveChip(chipA.placedIc.id, 1, 14))

        // startColumn = 15 -> occupies 15..21. Overlaps 20..21!
        assertFalse("Partial overlap must fail", circuit.moveChip(chipA.placedIc.id, 1, 15))

        // startColumn = 19 -> occupies 19..25. Overlaps 20..25!
        assertFalse("Deep overlap on left must fail", circuit.moveChip(chipA.placedIc.id, 1, 19))

        // startColumn = 13 -> occupies 13..19. Exactly adjacent (touches col 20, no overlap) -> MUST SUCCEED
        assertTrue("Touching left edge without overlap must succeed", circuit.moveChip(chipA.placedIc.id, 1, 13))
        assertEquals(13, circuit.placedChips.first { it.placedIc.id == chipA.placedIc.id }.placedIc.startColumn)
    }

    @Test
    fun testRightPartialOverlapCollision() {
        // Chip B occupies columns 20..26
        val chipB = circuit.addChip("7400", trench = 1, startColumn = 20)
        val chipA = circuit.addChip("7408", trench = 1, startColumn = 40)

        // startColumn = 26 -> occupies 26..32. Overlaps with chip B at column 26!
        assertFalse("Overlap on right boundary col 26 must fail", circuit.moveChip(chipA.placedIc.id, 1, 26))

        // startColumn = 25 -> occupies 25..31. Overlaps 25..26!
        assertFalse("Partial overlap on right must fail", circuit.moveChip(chipA.placedIc.id, 1, 25))

        // startColumn = 21 -> occupies 21..27. Overlaps 21..26!
        assertFalse("Deep overlap on right must fail", circuit.moveChip(chipA.placedIc.id, 1, 21))

        // startColumn = 27 -> occupies 27..33. Exactly adjacent (touches col 26, no overlap) -> MUST SUCCEED
        assertTrue("Touching right edge without overlap must succeed", circuit.moveChip(chipA.placedIc.id, 1, 27))
        assertEquals(27, circuit.placedChips.first { it.placedIc.id == chipA.placedIc.id }.placedIc.startColumn)
    }

    @Test
    fun testInternalAndEngulfingOverlapWithDifferentChipSizes() {
        // 7483 is a 16-pin chip (half = 8 columns). Occupies 20..27.
        val chipWide = circuit.addChip("7483", trench = 1, startColumn = 20)
        // 7404 is a 14-pin chip (half = 7 columns).
        val chipNarrow = circuit.addChip("7404", trench = 1, startColumn = 40)

        // 1. Internal overlap: move 14-pin chip inside 16-pin chip range (e.g. col 21 occupies 21..27)
        assertFalse("Internal overlap inside wider chip must fail", circuit.moveChip(chipNarrow.placedIc.id, 1, 21))

        // 2. Shared left boundary: col 20 occupies 20..26 (inside 20..27)
        assertFalse("Shared left edge inside wider chip must fail", circuit.moveChip(chipNarrow.placedIc.id, 1, 20))

        // 3. Move wide chip to engulf narrow chip:
        // Move chipNarrow out to col 10 (occupies 10..16).
        assertTrue(circuit.moveChip(chipNarrow.placedIc.id, 1, 10))

        // Try moving wide chip to col 9 (occupies 9..16, engulfing narrow chip at 10..16)
        assertFalse("Wide chip engulfing narrow chip must fail", circuit.moveChip(chipWide.placedIc.id, 1, 9))

        // Try moving wide chip to col 10 (occupies 10..17, engulfing narrow chip at 10..16)
        assertFalse("Wide chip sharing start and engulfing narrow chip must fail", circuit.moveChip(chipWide.placedIc.id, 1, 10))
    }

    @Test
    fun testBoundaryColumnsAndLimits() {
        // 14-pin chip: half = 7. maxCol = 64 - 7 = 57.
        val chip14 = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Col 0: occupies 0..6 -> valid
        assertTrue("Col 0 must be valid", circuit.moveChip(chip14.placedIc.id, 1, 0))

        // Col 57: occupies 57..63 -> valid
        assertTrue("Col 57 (maxCol) must be valid", circuit.moveChip(chip14.placedIc.id, 1, 57))

        // Col 58: exceeds AD200 COLUMNS_PER_BLOCK (64) -> invalid
        assertFalse("Col 58 must exceed bounds", circuit.moveChip(chip14.placedIc.id, 1, 58))

        // Negative col -> invalid
        assertFalse("Negative col -1 must be invalid", circuit.moveChip(chip14.placedIc.id, 1, -1))
        assertFalse("Large col 100 must be invalid", circuit.moveChip(chip14.placedIc.id, 1, 100))

        // Invalid trenches
        assertFalse("Trench 0 must be invalid", circuit.moveChip(chip14.placedIc.id, 0, 10))
        assertFalse("Trench 3 must be invalid", circuit.moveChip(chip14.placedIc.id, 3, 10))
        assertFalse("Trench -1 must be invalid", circuit.moveChip(chip14.placedIc.id, -1, 10))

        // 16-pin chip: half = 8. maxCol = 64 - 8 = 56.
        val chip16 = circuit.addChip("7483", trench = 2, startColumn = 10)
        assertTrue("Col 56 (maxCol for 16-pin) must be valid", circuit.moveChip(chip16.placedIc.id, 2, 56))
        assertFalse("Col 57 (exceeds for 16-pin) must fail", circuit.moveChip(chip16.placedIc.id, 2, 57))
    }

    @Test
    fun testSameColumnDifferentTrenchIndependence() {
        val chipTrench1 = circuit.addChip("7400", trench = 1, startColumn = 15)
        val chipTrench2 = circuit.addChip("7408", trench = 2, startColumn = 15)

        // Both chips coexist at identical startColumn 15
        assertEquals(1, chipTrench1.placedIc.trench)
        assertEquals(2, chipTrench2.placedIc.trench)
        assertEquals(15, chipTrench1.placedIc.startColumn)
        assertEquals(15, chipTrench2.placedIc.startColumn)

        // Attempting to move chip from trench 1 to trench 2 at same column 15 must collide
        assertFalse("Cross-trench move into occupied position must collide", circuit.moveChip(chipTrench1.placedIc.id, 2, 15))

        // Cross-trench move to vacant position must succeed
        assertTrue("Cross-trench move to vacant column must succeed", circuit.moveChip(chipTrench1.placedIc.id, 2, 30))
        assertEquals(2, circuit.placedChips.first { it.placedIc.id == chipTrench1.placedIc.id }.placedIc.trench)
        assertEquals(30, circuit.placedChips.first { it.placedIc.id == chipTrench1.placedIc.id }.placedIc.startColumn)
    }

    @Test
    fun testMovingEmptyChipPastWiredChipsZeroWiresModified() {
        // Place Chip B at col 20 with 10 distinct wires connected
        val chipB = circuit.addChip("7408", trench = 1, startColumn = 20)

        val w1 = circuit.addWire(AD200Topology.TERM_SW0, chipB.getPinSocket(1), WireColor.YELLOW)
        val w2 = circuit.addWire(AD200Topology.TERM_SW1, chipB.getPinSocket(2), WireColor.BLUE)
        val w3 = circuit.addWire(chipB.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
        val w4 = circuit.addWire(AD200Topology.TERM_SW2, chipB.getPinSocket(4), WireColor.WHITE)
        val w5 = circuit.addWire(AD200Topology.TERM_SW3, chipB.getPinSocket(5), WireColor.PURPLE)
        val w6 = circuit.addWire(chipB.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.ORANGE)
        val w7 = circuit.addWire(chipB.getPinSocket(7), AD200Topology.RAIL_TOP_GND, WireColor.BLACK)
        val w8 = circuit.addWire(chipB.getPinSocket(14), AD200Topology.RAIL_TOP_VCC_5V, WireColor.RED)
        val w9 = circuit.addWire(chipB.getPinSocket(9), AD200Topology.TERM_LED2, WireColor.GRAY)
        val w10 = circuit.addWire(chipB.getPinSocket(10), AD200Topology.TERM_SW4, WireColor.BROWN)

        val initialWireSnapshots = circuit.wires.map { it.copy() }
        assertEquals(10, initialWireSnapshots.size)

        // Empty chip A placed at col 5
        val chipA = circuit.addChip("7400", trench = 1, startColumn = 5)

        // Leap empty chip A across the board and across trenches
        val moveSequence = listOf(
            Pair(1, 10),
            Pair(1, 13), // adjacent left
            Pair(1, 27), // adjacent right, leaping over chip B!
            Pair(1, 40),
            Pair(2, 5),  // trench 2
            Pair(2, 20), // trench 2 same column as chip B
            Pair(1, 5)   // back to starting position
        )

        for ((trench, col) in moveSequence) {
            assertTrue("Move empty chip to trench $trench, col $col must succeed", circuit.moveChip(chipA.placedIc.id, trench, col))
            assertEquals(10, circuit.wires.size)
            for (i in circuit.wires.indices) {
                val current = circuit.wires[i]
                val expected = initialWireSnapshots[i]
                assertEquals("Wire $i startSocket must not change after moving empty chip", expected.startSocket, current.startSocket)
                assertEquals("Wire $i endSocket must not change after moving empty chip", expected.endSocket, current.endSocket)
                assertEquals("Wire $i color must not change", expected.color, current.color)
                assertEquals("Wire $i routing must not change", expected.isManhattan, current.isManhattan)
                assertEquals("Wire $i elevation must not change", expected.elevationLevel, current.elevationLevel)
            }
        }
    }

    @Test
    fun testMovingWiredChipMigratesOnlyItsOwnPinConnections() {
        val chipA = circuit.addChip("7400", trench = 1, startColumn = 5)
        val chipB = circuit.addChip("7408", trench = 1, startColumn = 30)

        // Chip A wires
        val wireA1 = circuit.addWire(AD200Topology.TERM_SW0, chipA.getPinSocket(1), WireColor.YELLOW)
        val wireA2 = circuit.addWire(chipA.getPinSocket(14), AD200Topology.RAIL_TOP_VCC_5V, WireColor.RED)

        // Inter-chip wire: Chip A pin 3 -> Chip B pin 1
        val wireAB = circuit.addWire(chipA.getPinSocket(3), chipB.getPinSocket(1), WireColor.BLUE)

        // Chip B wires
        val wireB1 = circuit.addWire(AD200Topology.TERM_SW1, chipB.getPinSocket(2), WireColor.GREEN)
        val wireB2 = circuit.addWire(chipB.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.ORANGE)
        val wireB3 = circuit.addWire(chipB.getPinSocket(14), AD200Topology.RAIL_TOP_VCC_5V, WireColor.RED)
        val wireB4 = circuit.addWire(chipB.getPinSocket(7), AD200Topology.RAIL_TOP_GND, WireColor.BLACK)

        // Independent rail wire
        val wireRail = circuit.addWire(AD200Topology.RAIL_TOP_VCC_5V, AD200Topology.RAIL_BOT_VCC_5V, WireColor.RED)

        val oldPin1A = chipA.getPinSocket(1)
        val oldPin14A = chipA.getPinSocket(14)
        val oldPin3A = chipA.getPinSocket(3)
        val pin1B = chipB.getPinSocket(1)
        val pin2B = chipB.getPinSocket(2)
        val pin3B = chipB.getPinSocket(3)

        // Move Chip A from col 5 to col 15
        assertTrue(circuit.moveChip(chipA.placedIc.id, 1, 15))

        val movedChipA = circuit.placedChips.first { it.placedIc.id == chipA.placedIc.id }
        val unmovedChipB = circuit.placedChips.first { it.placedIc.id == chipB.placedIc.id }

        val newPin1A = movedChipA.getPinSocket(1)
        val newPin14A = movedChipA.getPinSocket(14)
        val newPin3A = movedChipA.getPinSocket(3)

        // 1. Wire A1: start (SW0) unchanged, end migrated to newPin1A
        val curA1 = circuit.wires.first { it.id == wireA1.id }
        assertEquals(AD200Topology.TERM_SW0, curA1.startSocket)
        assertEquals(newPin1A, curA1.endSocket)

        // 2. Wire A2: start migrated to newPin14A, end (VCC) unchanged
        val curA2 = circuit.wires.first { it.id == wireA2.id }
        assertEquals(newPin14A, curA2.startSocket)
        assertEquals(AD200Topology.RAIL_TOP_VCC_5V, curA2.endSocket)

        // 3. Inter-chip wire AB: start migrated to newPin3A, end STILL on Chip B pin 1!
        val curAB = circuit.wires.first { it.id == wireAB.id }
        assertEquals(newPin3A, curAB.startSocket)
        assertEquals(pin1B, curAB.endSocket)

        // 4. Chip B wires: completely untouched!
        val curB1 = circuit.wires.first { it.id == wireB1.id }
        assertEquals(AD200Topology.TERM_SW1, curB1.startSocket)
        assertEquals(pin2B, curB1.endSocket)

        val curB2 = circuit.wires.first { it.id == wireB2.id }
        assertEquals(pin3B, curB2.startSocket)
        assertEquals(AD200Topology.TERM_LED0, curB2.endSocket)

        val curB3 = circuit.wires.first { it.id == wireB3.id }
        assertEquals(unmovedChipB.getPinSocket(14), curB3.startSocket)
        assertEquals(AD200Topology.RAIL_TOP_VCC_5V, curB3.endSocket)

        val curB4 = circuit.wires.first { it.id == wireB4.id }
        assertEquals(unmovedChipB.getPinSocket(7), curB4.startSocket)
        assertEquals(AD200Topology.RAIL_TOP_GND, curB4.endSocket)

        // 5. Rail wire: completely untouched!
        val curRail = circuit.wires.first { it.id == wireRail.id }
        assertEquals(AD200Topology.RAIL_TOP_VCC_5V, curRail.startSocket)
        assertEquals(AD200Topology.RAIL_BOT_VCC_5V, curRail.endSocket)
    }

    @Test
    fun testDensePackingStressAndAdjacentMovement() {
        // Place 8 14-pin chips and 1 16-pin chip consecutively with 0 gap in trench 1:
        // Chip 0: 0..6 (width 7)
        // Chip 1: 7..13 (width 7)
        // Chip 2: 14..20 (width 7)
        // Chip 3: 21..27 (width 7)
        // Chip 4: 28..34 (width 7)
        // Chip 5: 35..41 (width 7)
        // Chip 6: 42..48 (width 7)
        // Chip 7: 49..55 (width 7)
        // Chip 8: 56..63 (16-pin chip 7483, width 8, reaches exact end 63!)
        val chips = mutableListOf<String>()
        for (i in 0 until 8) {
            val c = circuit.addChip("7400", trench = 1, startColumn = i * 7)
            chips.add(c.placedIc.id)
        }
        val chip8 = circuit.addChip("7483", trench = 1, startColumn = 56)
        chips.add(chip8.placedIc.id)

        assertEquals(9, circuit.placedChips.size)

        // Verify that NO chip can shift by 1 column in either direction due to neighbors
        for (i in 0 until 8) {
            val id = chips[i]
            val currentStart = i * 7
            // Try shift right by 1
            assertFalse("Shift right must collide with neighbor", circuit.moveChip(id, 1, currentStart + 1))
            if (currentStart > 0) {
                // Try shift left by 1
                assertFalse("Shift left must collide with neighbor", circuit.moveChip(id, 1, currentStart - 1))
            }
        }

        // Move middle chip (index 4) to trench 2
        val id4 = chips[4]
        assertTrue("Moving chip 4 to trench 2 must succeed", circuit.moveChip(id4, 2, 28))

        // Now chip 3 (21..27) has space on its right: can move to 22..28 (occupies 22..28, chip 5 starts at 35)
        assertTrue("Moving chip 3 into gap must succeed", circuit.moveChip(chips[3], 1, 22))

        // But chip 3 moving to 29..35 (chip 5 starts at 35 -> 29..35 overlaps at 35) must collide!
        assertFalse("Moving chip 3 into chip 5 boundary must collide", circuit.moveChip(chips[3], 1, 29))
    }
}
