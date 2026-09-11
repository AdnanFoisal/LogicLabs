package com.logiclabs.core.bridge

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BreadboardCircuitCollisionTest {

    private lateinit var circuit: BreadboardCircuit

    @Before
    fun setup() {
        circuit = BreadboardCircuit()
    }

    @Test
    fun testMoveChipOutOfBoundsReturnsFalse() {
        // 7400 is a 14-pin DIP (7 columns wide, columns 0..63)
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Negative column
        assertFalse(circuit.moveChip(chip.placedIc.id, 1, -1))
        // Column exceeds AD200Topology.COLUMNS_PER_BLOCK - half (64 - 7 = 57)
        assertFalse(circuit.moveChip(chip.placedIc.id, 1, 58))
        // Valid edge column (57)
        assertTrue(circuit.moveChip(chip.placedIc.id, 1, 57))
        // Invalid trench (trench must be 1 or 2)
        assertFalse(circuit.moveChip(chip.placedIc.id, 0, 10))
        assertFalse(circuit.moveChip(chip.placedIc.id, 3, 10))
    }

    @Test
    fun testMoveChipCollisionInSameTrenchReturnsFalse() {
        // Chip A at columns 10..16 in trench 1 (7 cols)
        val chipA = circuit.addChip("7400", trench = 1, startColumn = 10)
        // Chip B at columns 20..26 in trench 1 (7 cols)
        val chipB = circuit.addChip("7408", trench = 1, startColumn = 20)

        // Try moving Chip A to overlap Chip B:
        // Overlap left edge: startColumn = 14 (14..20 overlaps 20..26 at 20)
        assertFalse("Should fail when overlapping at start boundary", circuit.moveChip(chipA.placedIc.id, 1, 14))
        // Exact collision: startColumn = 20
        assertFalse("Should fail when starting at same column", circuit.moveChip(chipA.placedIc.id, 1, 20))
        // Overlap right edge: startColumn = 25 (25..31 overlaps 20..26)
        assertFalse("Should fail when overlapping inside range", circuit.moveChip(chipA.placedIc.id, 1, 25))

        // Position of chipA should remain unchanged
        val currentA = circuit.placedChips.find { it.placedIc.id == chipA.placedIc.id }!!
        assertEquals(10, currentA.placedIc.startColumn)
    }

    @Test
    fun testMoveChipAdjacentNonOverlappingReturnsTrue() {
        // Chip A at columns 10..16 in trench 1 (columns 10, 11, 12, 13, 14, 15, 16)
        val chipA = circuit.addChip("7400", trench = 1, startColumn = 10)
        // Chip B at columns 20..26 in trench 1
        val chipB = circuit.addChip("7408", trench = 1, startColumn = 20)

        // Move Chip A right up to Chip B's edge without overlap:
        // Chip A ends at col 13+7=20 (occupies 13..19), Chip B starts at 20.
        assertTrue("Moving adjacent before other chip should succeed", circuit.moveChip(chipA.placedIc.id, 1, 13))

        // Move Chip A past Chip B: Chip B occupies 20..26 (ends before 27).
        assertTrue("Moving adjacent after other chip should succeed", circuit.moveChip(chipA.placedIc.id, 1, 27))
    }

    @Test
    fun testMoveChipSameColumnDifferentTrenchReturnsTrue() {
        // Chip A at columns 10..16 in trench 1
        val chipA = circuit.addChip("7400", trench = 1, startColumn = 10)
        // Chip B at columns 10..16 in trench 2
        val chipB = circuit.addChip("7408", trench = 2, startColumn = 10)

        // Both can coexist at the same column because trenches are independent
        assertEquals(1, chipA.placedIc.trench)
        assertEquals(2, chipB.placedIc.trench)

        // Move Chip A to trench 2 adjacent to Chip B (e.g. column 20)
        assertTrue(circuit.moveChip(chipA.placedIc.id, 2, 20))
        // But moving Chip A to column 10 in trench 2 should collide with Chip B!
        assertFalse(circuit.moveChip(chipA.placedIc.id, 2, 10))
    }

    @Test
    fun testMovingEmptyChipDoesNotSnatchOrMoveOtherWires() {
        // Chip B at col 20 with wires attached
        val chipB = circuit.addChip("7408", trench = 1, startColumn = 20)
        val wireB1 = circuit.addWire(AD200Topology.TERM_SW0, chipB.getPinSocket(1), WireColor.YELLOW)
        val wireB2 = circuit.addWire(chipB.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

        // Chip A is an empty chip (no wires) at col 5
        val chipA = circuit.addChip("7400", trench = 1, startColumn = 5)

        // Move empty Chip A to col 30 (skipping past Chip B)
        assertTrue(circuit.moveChip(chipA.placedIc.id, 1, 30))

        // Verify that Chip B's wires remain intact and attached to Chip B
        val currentWire1 = circuit.wires.find { it.id == wireB1.id }!!
        val currentWire2 = circuit.wires.find { it.id == wireB2.id }!!
        assertEquals(chipB.getPinSocket(1), currentWire1.endSocket)
        assertEquals(chipB.getPinSocket(3), currentWire2.startSocket)
    }

    @Test
    fun testMovingWiredChipMigratesOnlyItsOwnWiresAndPreservesOtherChipWires() {
        // Chip A at col 5, Chip B at col 30
        val chipA = circuit.addChip("7400", trench = 1, startColumn = 5)
        val chipB = circuit.addChip("7408", trench = 1, startColumn = 30)

        // Wire on Chip A (SW0 -> Pin 1)
        val wireA = circuit.addWire(AD200Topology.TERM_SW0, chipA.getPinSocket(1), WireColor.YELLOW)
        // Wire between Chip A and Chip B (Chip A Pin 3 -> Chip B Pin 1)
        val wireAB = circuit.addWire(chipA.getPinSocket(3), chipB.getPinSocket(1), WireColor.BLUE)
        // Wire on Chip B (Chip B Pin 3 -> LED0)
        val wireB = circuit.addWire(chipB.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

        // Move Chip A from col 5 to col 15
        assertTrue(circuit.moveChip(chipA.placedIc.id, 1, 15))

        val movedA = circuit.placedChips.find { it.placedIc.id == chipA.placedIc.id }!!
        val unmovedB = circuit.placedChips.find { it.placedIc.id == chipB.placedIc.id }!!

        // Wire A migrated to new Pin 1 of Chip A
        val updatedWireA = circuit.wires.find { it.id == wireA.id }!!
        assertEquals(movedA.getPinSocket(1), updatedWireA.endSocket)
        assertEquals(AD200Topology.TERM_SW0, updatedWireA.startSocket)

        // Wire AB: start migrated to Chip A's new Pin 3, but end remained on Chip B's Pin 1
        val updatedWireAB = circuit.wires.find { it.id == wireAB.id }!!
        assertEquals(movedA.getPinSocket(3), updatedWireAB.startSocket)
        assertEquals(unmovedB.getPinSocket(1), updatedWireAB.endSocket)

        // Wire B: completely unchanged!
        val updatedWireB = circuit.wires.find { it.id == wireB.id }!!
        assertEquals(unmovedB.getPinSocket(3), updatedWireB.startSocket)
        assertEquals(AD200Topology.TERM_LED0, updatedWireB.endSocket)
    }
}
