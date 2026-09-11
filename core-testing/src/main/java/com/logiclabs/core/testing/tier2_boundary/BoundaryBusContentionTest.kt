package com.logiclabs.core.testing.tier2_boundary

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertContention
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLevel
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertNoContention
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 2 Boundary & Corner Cases: Totem-Pole Bus Contention & Driver Fighting.
 * Verifies:
 * - Opposing totem-pole outputs (HIGH vs LOW) resolve to CONFLICT ('X').
 * - Contention flag and thermal warning dispatch.
 * - Agreement between drivers (HIGH+HIGH or LOW+LOW) causes no contention.
 * Requirement: >= 5 distinct tests.
 */
class BoundaryBusContentionTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testTwoNandOutputsFightingOnSameNet() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)

        // Gate 1: 1A=0, 1B=0 -> 1Y (Pin 3) drives HIGH
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935)

        // Gate 2: 2A=1, 2B=1 -> 2Y (Pin 6) drives LOW
        circuit.wirePin(u1, 4, 1934); circuit.wirePin(u1, 5, 1934)

        // Wire 1Y (Pin 3) directly to 2Y (Pin 6)
        circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(6))
        circuit.step()

        // Contention detected! State must resolve to CONFLICT ('X')
        assertContention(circuit, "Opposing gate outputs must trigger contention")
        val netLvl = circuit.getPinLevel(u1, 3)
        assertEquals(ElectricalLevel.CONFLICT, netLvl)
    }

    @Test
    fun testTwoAndOutputsFightingOnSameNet() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 20)
        circuit.wireStandardPower(u1)

        // Gate 1: 1A=1, 1B=1 -> 1Y (Pin 3) drives HIGH
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934)

        // Gate 2: 2A=0, 2B=0 -> 2Y (Pin 6) drives LOW
        circuit.wirePin(u1, 4, 1935); circuit.wirePin(u1, 5, 1935)

        circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(6))
        circuit.step()

        assertContention(circuit)
        assertEquals(ElectricalLevel.CONFLICT, circuit.getPinLevel(u1, 3))
    }

    @Test
    fun testSwitchShortCircuitedDirectlyToGround() {
        // Switch SW0 set to HIGH (+5V)
        circuit.switches[0] = true

        // Directly connect SW0 output (Terminal 1896) to GND rail (Terminal 1935)
        circuit.addWire(BreadboardGeometry.TERM_SW0, BreadboardGeometry.TERM_POWER_GND)
        circuit.step()

        assertContention(circuit, "Driving switch HIGH directly to GND must detect contention")
        val swLevel = circuit.getSocketLevel(BreadboardGeometry.TERM_SW0)
        assertEquals(ElectricalLevel.CONFLICT, swLevel)
    }

    @Test
    fun testHarmoniousDriversBothHigh_NoContention() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)

        // Gate 1: 00 -> 1Y drives HIGH
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935)
        // Gate 2: 01 -> 2Y drives HIGH
        circuit.wirePin(u1, 4, 1935); circuit.wirePin(u1, 5, 1934)

        // Wire 1Y and 2Y together
        circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(6))
        circuit.step()

        // Both drive HIGH -> harmonious, NO contention
        assertNoContention(circuit)
        assertLevel(ElectricalLevel.HIGH, circuit.getPinLevel(u1, 3))
    }

    @Test
    fun testHarmoniousDriversBothLow_NoContention() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 20)
        circuit.wireStandardPower(u1)

        // Gate 1: 00 -> 1Y drives LOW
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935)
        // Gate 2: 01 -> 2Y drives LOW
        circuit.wirePin(u1, 4, 1935); circuit.wirePin(u1, 5, 1934)

        // Wire 1Y and 2Y together
        circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(6))
        circuit.step()

        // Both drive LOW -> harmonious, NO contention
        assertNoContention(circuit)
        assertLevel(ElectricalLevel.LOW, circuit.getPinLevel(u1, 3))
    }
}
