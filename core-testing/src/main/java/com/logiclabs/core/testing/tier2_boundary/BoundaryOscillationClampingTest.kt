package com.logiclabs.core.testing.tier2_boundary

import com.logiclabs.core.testing.harness.ElectricalAssertions.assertOscillationClamped
import com.logiclabs.core.testing.harness.SimulationCircuit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 2 Boundary & Corner Cases: 10,000 Event Ring Oscillator Clamping & Glitch Safeguards.
 * Acceptance criterion: "Ring oscillators and high-frequency clocks are safeguarded
 * at 10,000 events/tick without freezing the main application loop."
 * Requirement: >= 5 distinct tests.
 */
class BoundaryOscillationClampingTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testThreeInverterRingOscillatorClamping() {
        // Construct 3-inverter closed loop: 1->2 -> 3->4 -> 5->6 -> 1
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)

        // Wire Inverter 1 (1->2) to Inverter 2 (3->4)
        circuit.addWire(u1.getPinSocket(2), u1.getPinSocket(3))
        // Wire Inverter 2 (3->4) to Inverter 3 (5->6)
        circuit.addWire(u1.getPinSocket(4), u1.getPinSocket(5))
        // Close loop: Inverter 3 output (Pin 6) back to Inverter 1 input (Pin 1)
        circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(1))

        // Step simulation: loop oscillates endlessly
        circuit.step()

        // Engine must clamp at 10,000 events and set isOscillationClamped = true
        assertOscillationClamped(circuit)
        assertEquals(SimulationCircuit.MAX_EVENTS_PER_TICK, circuit.totalEventsLastTick)
    }

    @Test
    fun testFiveInverterRingOscillatorClamping() {
        // 5-inverter ring: 1->2 -> 3->4 -> 5->6 -> 9->8 -> 11->10 -> 1
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)

        circuit.addWire(u1.getPinSocket(2), u1.getPinSocket(3))
        circuit.addWire(u1.getPinSocket(4), u1.getPinSocket(5))
        circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(9))
        circuit.addWire(u1.getPinSocket(8), u1.getPinSocket(11))
        circuit.addWire(u1.getPinSocket(10), u1.getPinSocket(1)) // Feedback

        circuit.step()

        assertOscillationClamped(circuit)
        assertEquals(SimulationCircuit.MAX_EVENTS_PER_TICK, circuit.totalEventsLastTick)
    }

    @Test
    fun testSingleInverterSelfFeedback() {
        // 1-inverter self-feedback: Pin 2 (output) directly connected to Pin 1 (input)
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        circuit.addWire(u1.getPinSocket(2), u1.getPinSocket(1))

        circuit.step()
        assertOscillationClamped(circuit)
    }

    @Test
    fun testClampedOscillatorDoesNotFreezeOrCrash() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        circuit.addWire(u1.getPinSocket(2), u1.getPinSocket(3))
        circuit.addWire(u1.getPinSocket(4), u1.getPinSocket(5))
        circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(1))

        val startNano = System.nanoTime()
        // Execute 10 consecutive ticks on clamped circuit
        for (i in 0 until 10) {
            circuit.step()
            assertTrue(circuit.isOscillationClamped)
        }
        val elapsedMs = (System.nanoTime() - startNano) / 1_000_000

        // Clamped execution must yield within bounded time (< 5000ms for 100,000 evaluations) without hanging
        assertTrue("Clamping must prevent thread locking: took ${elapsedMs}ms", elapsedMs < 5000)
    }

    @Test
    fun testBreakingRingOscillatorRestoresEquilibrium() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        circuit.addWire(u1.getPinSocket(2), u1.getPinSocket(3))
        circuit.addWire(u1.getPinSocket(4), u1.getPinSocket(5))
        circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(1))

        circuit.step()
        assertTrue(circuit.isOscillationClamped)

        // Reset and remove the feedback loop wire
        circuit.reset()
        val u2 = circuit.placeChip("7404", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u2)
        circuit.addWire(u2.getPinSocket(2), u2.getPinSocket(3))
        circuit.wirePin(u2, 1, 1934) // Input fixed to VCC (1)

        circuit.step()
        assertFalse(circuit.isOscillationClamped)
        assertTrue(circuit.totalEventsLastTick < 10) // Settles in few iterations
    }
}
