package com.logiclabs.core.data.persistence

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip guarantees for the project codec. These are the properties the bench's
 * save/load, autosave draft and import/export all rest on: a saved board rebuilds
 * bit-for-bit, and an older or hand-edited file degrades instead of throwing.
 */
class ProjectPersistenceRoundTripTest {

    @Test
    fun `round trip preserves chips wires ids and console state`() {
        val source = BreadboardCircuit()
        val chipA = source.addChip("7400", 1, 10)
        val chipB = source.addChip("7486", 2, 20, isRotated180 = true)
        val wire = source.addWire(1, 300, WireColor.BLUE, isManhattan = true)
        source.switches[0] = true
        source.switches[7] = true
        source.masterPower = false
        source.clockFrequencyHz = 100.0
        source.clockRunning = false
        source.step()

        val saved = ProjectPersistence.serialize(source, title = "Test build", activeLabId = "lab2_nand")
        assertEquals("Test build", saved.title)
        assertEquals("lab2_nand", saved.activeLabId)
        assertFalse(saved.masterPower)
        assertEquals(100.0, saved.clockFrequencyHz, 0.0)
        assertFalse(saved.clockRunning)
        assertEquals(2, saved.chips.size)
        assertEquals(1, saved.wires.size)

        val restored = BreadboardCircuit()
        ProjectPersistence.deserialize(saved, restored)

        // Identity: ids are the netlist's keys, so they must survive exactly.
        assertEquals(listOf(chipA.placedIc.id, chipB.placedIc.id), restored.placedChips.map { it.placedIc.id })
        assertEquals(wire.id, restored.wires.single().id)
        assertEquals(WireColor.BLUE, restored.wires.single().color)
        assertTrue(restored.wires.single().isManhattan)
        assertTrue(restored.placedChips[1].placedIc.isRotated180)

        // Console state.
        assertTrue(restored.switches[0])
        assertTrue(restored.switches[7])
        assertFalse(restored.switches[3])
        assertFalse(restored.masterPower)
        assertEquals(100.0, restored.clockFrequencyHz, 0.0)
        assertFalse(restored.clockRunning)
    }

    @Test
    fun `v1 project without console fields deserializes to factory console state`() {
        val v1 = SerializedProject(
            version = 1,
            chips = listOf(SerializedChip("c1", "7404", 1, 10, false)),
            wires = emptyList(),
            switches = listOf(true, false)
        )

        val restored = BreadboardCircuit()
        ProjectPersistence.deserialize(v1, restored)

        assertTrue(restored.masterPower)
        assertEquals(1.0, restored.clockFrequencyHz, 0.0)
        assertTrue(restored.clockRunning)
        assertEquals(1, restored.placedChips.size)
        assertTrue(restored.switches[0])
    }

    @Test
    fun `unknown wire colour falls back to red instead of throwing`() {
        val edited = SerializedProject(
            chips = emptyList(),
            wires = listOf(SerializedWire("w1", 1, 2, "CHARTREUSE", false)),
            switches = emptyList()
        )

        val restored = BreadboardCircuit()
        ProjectPersistence.deserialize(edited, restored)

        assertEquals(WireColor.RED, restored.wires.single().color)
    }
}
