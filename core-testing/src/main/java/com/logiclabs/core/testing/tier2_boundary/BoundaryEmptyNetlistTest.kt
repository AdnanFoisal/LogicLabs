package com.logiclabs.core.testing.tier2_boundary

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 2 Boundary & Corner Cases: Empty Netlists, Missing Connections & Unpowered Chips.
 * Requirement: >= 5 distinct tests.
 */
class BoundaryEmptyNetlistTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testEmptyBreadboardStepsCleanly() {
        // Circuit with zero user components and default power
        circuit.step()
        assertFalse(circuit.contentionDetected)
        assertFalse(circuit.isOscillationClamped)
        assertFalse(circuit.isReversePolarityBurned)
        assertEquals(0, circuit.placedChips.size)
    }

    @Test
    fun testUnpoweredChipOutputsAreUnpowered() {
        // Place chip but do NOT connect VCC or GND
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        // Connect inputs to GND
        circuit.wirePin(u1, 1, 1935)
        circuit.wirePin(u1, 2, 1935)
        circuit.step()

        // Unpowered chip: output must remain High-Impedance (HIGH_Z) per hardware specification
        val outLvl = circuit.getPinLevel(u1, 3)
        assertTrue("Unpowered chip outputs must be High-Z", outLvl.isHighZ || outLvl.isUnpowered)
    }

    @Test
    fun testSingleRailConnected_OnlyVccMissingGnd() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 15)
        // Only wire Pin 14 to VCC; leave Pin 7 floating
        circuit.wirePin(u1, 14, 1934)
        circuit.wirePin(u1, 1, 1934)
        circuit.wirePin(u1, 2, 1934)
        circuit.step()

        // Missing GND -> chip is unpowered, outputs enter HIGH_Z
        val outLvl = circuit.getPinLevel(u1, 3)
        assertTrue("Missing GND must disable chip outputs into High-Z", outLvl.isHighZ || outLvl.isUnpowered)
    }

    @Test
    fun testSingleRailConnected_OnlyGndMissingVcc() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 20)
        // Only wire Pin 7 to GND; leave Pin 14 floating
        circuit.wirePin(u1, 7, 1935)
        circuit.wirePin(u1, 1, 1935)
        circuit.step()

        // Missing VCC -> chip is unpowered, outputs enter HIGH_Z
        val outLvl = circuit.getPinLevel(u1, 2)
        assertTrue("Missing VCC must disable chip outputs into High-Z", outLvl.isHighZ || outLvl.isUnpowered)
    }

    @Test
    fun testIsolatedUnconnectedJumperWiresDoNotCorruptNetlist() {
        // Place multiple floating jumper wires connecting random unused terminal sockets
        val s1 = BreadboardGeometry.terminalSocket(0, 50, 0)
        val s2 = BreadboardGeometry.terminalSocket(1, 50, 2)
        val s3 = BreadboardGeometry.terminalSocket(2, 50, 4)
        circuit.addWire(s1, s2)
        circuit.addWire(s2, s3)
        circuit.step()

        // Sockets are joined together in their own net with HIGH_Z
        assertLevel(ElectricalLevel.HIGH_Z, circuit.getSocketLevel(s1))
        assertLevel(ElectricalLevel.HIGH_Z, circuit.getSocketLevel(s3))
        assertEquals(15, circuit.dsu.getNetSize(s1)) // 3 columns connected: 5*3=15
        assertFalse(circuit.contentionDetected)
    }
}
