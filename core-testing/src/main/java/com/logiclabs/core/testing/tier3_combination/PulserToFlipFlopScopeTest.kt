package com.logiclabs.core.testing.tier3_combination

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertPinLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tier 3 Cross-Feature Combination: Debounced Pulser -> 7474 D-FF -> Virtual Scope Probes.
 * Verifies interactive manual pulse clocking, data latching, and oscilloscope probe waveform capture.
 */
class PulserToFlipFlopScopeTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testPulserClockingDFlipFlopWithOscilloscopeSampling() {
        val u1 = circuit.placeChip("7474", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)

        // ~CLR=1, ~PRE=1
        circuit.wirePin(u1, 1, 1934)
        circuit.wirePin(u1, 4, 1934)

        // D input (Pin 2) driven by SW0
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_SW0)

        // Clock input (Pin 3) driven by Pulser A True Output P (Terminal 1904)
        circuit.wirePin(u1, 3, BreadboardGeometry.TERM_PULSER_A_P)

        // Scope Probe 1 (Yellow) attached to Pulser A P
        circuit.probe1Socket = BreadboardGeometry.TERM_PULSER_A_P

        // Scope Probe 2 (Cyan) attached to 7474 1Q Output (Pin 5)
        circuit.probe2Socket = u1.getPinSocket(5)

        // Initial setup: SW0 = HIGH (1), Pulser released (P = 0)
        circuit.switches[0] = true
        circuit.pulserAPressed = false
        circuit.step()

        // Before press: Pulser is 0V, Q is initially 0V
        assertEquals(0.0f, circuit.scopeCh1.latestSample(), 0.01f)
        assertEquals(0.0f, circuit.scopeCh2.latestSample(), 0.01f)

        // User presses Pulser A button -> Rising edge (P: 0 -> 1)
        circuit.pulserAPressed = true
        circuit.step()

        // 7474 latches D (1) onto Q (1)
        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH, "1Q should latch 1 on pulser press")

        // Scope samples both channels at 5.0V
        assertEquals(5.0f, circuit.scopeCh1.latestSample(), 0.01f) // Pulser P is 5V
        assertEquals(5.0f, circuit.scopeCh2.latestSample(), 0.01f) // Q is 5V

        // User releases Pulser A button -> Falling edge (P: 1 -> 0)
        circuit.pulserAPressed = false
        circuit.step()

        // Pulser drops to 0V; Q maintains state at 5V
        assertEquals(0.0f, circuit.scopeCh1.latestSample(), 0.01f)
        assertEquals(5.0f, circuit.scopeCh2.latestSample(), 0.01f)
        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH, "1Q must retain stored state when pulser released")
    }
}
