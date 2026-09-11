package com.logiclabs.core.testing.tier2_boundary

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Tier 2 Boundary & Corner Cases: Stepped Clock Frequency Extremes.
 * Tests: 0 Hz (halted), 0.5 Hz (ultra-low), 100 kHz (extreme high), rapid 1000-cycle clocking, power gating.
 * Requirement: >= 5 distinct tests.
 */
class BoundaryClockFrequenciesTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testZeroHertzClockPaused() {
        circuit.clockFrequencyHz = 0.0
        circuit.step()

        val clkTerm = BreadboardGeometry.TERM_CLK
        val clkInvTerm = BreadboardGeometry.TERM_CLK_INV

        // When paused (0 Hz), clock terminals do not drive active levels (remain HIGH_Z)
        assertLevel(ElectricalLevel.HIGH_Z, circuit.getSocketLevel(clkTerm))
        assertLevel(ElectricalLevel.HIGH_Z, circuit.getSocketLevel(clkInvTerm))
    }

    @Test
    fun testUltraLowFrequency_PointFiveHz() {
        circuit.clockFrequencyHz = 0.5
        circuit.clockState = false
        circuit.step()

        assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK))
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK_INV))

        circuit.tickClock()
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK))
        assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK_INV))
    }

    @Test
    fun testMaximumFrequency_OneHundredKilohertz() {
        circuit.clockFrequencyHz = 100000.0
        circuit.clockState = true
        circuit.step()

        // 100 kHz clock operates stably
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK))
        assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK_INV))
        assertFalse(circuit.isOscillationClamped)
    }

    @Test
    fun testRapidOneThousandClockCycles() {
        // Connect clock to D-flip flop configured as divide-by-2 toggle
        val u1 = circuit.placeChip("7474", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934) // ~CLR = 1
        circuit.wirePin(u1, 4, 1934) // ~PRE = 1
        circuit.wirePin(u1, 3, BreadboardGeometry.TERM_CLK) // CLK
        // Wire 1~Q (Pin 6) back to 1D (Pin 2) to toggle
        circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(2))

        circuit.clockFrequencyHz = 10000.0

        // Run 1,000 rapid clock ticks
        for (i in 0 until 1000) {
            circuit.tickClock()
        }

        // Circuit must complete all 1,000 cycles without crashing or hanging
        assertFalse(circuit.isOscillationClamped)
        assertFalse(circuit.contentionDetected)
    }

    @Test
    fun testMasterPowerOffHaltsClockOutput() {
        circuit.clockFrequencyHz = 1000.0
        circuit.clockState = true
        circuit.step()
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK))

        // Switch Master AC power OFF
        circuit.masterPower = false
        circuit.step()
        assertLevel(ElectricalLevel.UNPOWERED, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK))
        assertLevel(ElectricalLevel.UNPOWERED, circuit.getSocketLevel(BreadboardGeometry.TERM_CLK_INV))
    }
}
