package com.logiclabs.core.testing.tier3_combination

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertPinLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Before
import org.junit.Test

/**
 * Tier 3 Cross-Feature Combination: Stepped Clock -> Ripple Counter -> 7448 Decoder.
 * Verifies clock pulses driving sequential counter stages, feeding BCD decoder and
 * driving 7-segment display segments.
 */
class ClockToRippleToSevenSegTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testClockDrivenTwoBitCounterTo7448() {
        // Construct a 2-bit ripple counter using 7476 Dual JK-FF
        // FF1 divides CLK by 2 -> Q0 (Pin 15)
        // FF2 divides Q0 by 2 -> Q1 (Pin 11)
        val uJk = circuit.placeChip("7476", trench = 2, startColumn = 10)
        // Power: Pin 5 VCC, Pin 13 GND
        circuit.wirePin(uJk, 5, 1934)
        circuit.wirePin(uJk, 13, 1935)

        // Inactive presets and clears (HIGH)
        circuit.wirePin(uJk, 2, 1934); circuit.wirePin(uJk, 3, 1934)
        circuit.wirePin(uJk, 7, 1934); circuit.wirePin(uJk, 8, 1934)

        // Toggle mode: J=1, K=1 on both flip-flops
        circuit.wirePin(uJk, 4, 1934); circuit.wirePin(uJk, 16, 1934) // 1J=1, 1K=1
        circuit.wirePin(uJk, 9, 1934); circuit.wirePin(uJk, 12, 1934) // 2J=1, 2K=1

        // Clock terminal -> 1~CLK (Pin 1)
        circuit.wirePin(uJk, 1, BreadboardGeometry.TERM_CLK)

        // FF1 1Q (Pin 15) -> 2~CLK (Pin 6) creates ripple counter
        circuit.addWire(uJk.getPinSocket(15), uJk.getPinSocket(6))

        // Place 7448 decoder
        val uDec = circuit.placeChip("7448", trench = 1, startColumn = 25)
        circuit.wirePin(uDec, 16, 1934); circuit.wirePin(uDec, 8, 1935)
        circuit.wirePin(uDec, 3, 1934); circuit.wirePin(uDec, 4, 1934); circuit.wirePin(uDec, 5, 1934)

        // Connect Q0 (Pin 15) -> 7448 A (Pin 7)
        circuit.addWire(uJk.getPinSocket(15), uDec.getPinSocket(7))
        // Connect Q1 (Pin 11) -> 7448 B (Pin 1)
        circuit.addWire(uJk.getPinSocket(11), uDec.getPinSocket(1))
        // C (Pin 2) and D (Pin 6) tied to GND
        circuit.wirePin(uDec, 2, 1935); circuit.wirePin(uDec, 6, 1935)

        // Initial state: Q0=0, Q1=0 -> Count = 0 -> 7448 displays '0'
        circuit.clockState = true
        circuit.step()

        // '0' has g (Pin 14) LOW and a (Pin 13) HIGH
        assertPinLevel(circuit, uDec, 14, ElectricalLevel.LOW, "Segment g must be 0 for digit '0'")
        assertPinLevel(circuit, uDec, 13, ElectricalLevel.HIGH, "Segment a must be 1 for digit '0'")

        // Cycle 1: Falling clock edge -> Count = 1 (Q0=1, Q1=0)
        circuit.clockState = false; circuit.step()
        assertPinLevel(circuit, uJk, 15, ElectricalLevel.HIGH, "Q0 should be 1")
        // '1' has b, c HIGH, and a, g LOW
        assertPinLevel(circuit, uDec, 12, ElectricalLevel.HIGH, "Segment b must be 1 for digit '1'")
        assertPinLevel(circuit, uDec, 11, ElectricalLevel.HIGH, "Segment c must be 1 for digit '1'")
        assertPinLevel(circuit, uDec, 13, ElectricalLevel.LOW, "Segment a must be 0 for digit '1'")

        // Rising clock edge (no change on negative-edge FF)
        circuit.clockState = true; circuit.step()

        // Cycle 2: Falling clock edge -> Count = 2 (Q0=0, Q1=1)
        circuit.clockState = false; circuit.step()
        assertPinLevel(circuit, uJk, 15, ElectricalLevel.LOW, "Q0 should toggle to 0")
        assertPinLevel(circuit, uJk, 11, ElectricalLevel.HIGH, "Q1 should toggle to 1")
        // '2' has segment g (Pin 14) HIGH
        assertPinLevel(circuit, uDec, 14, ElectricalLevel.HIGH, "Segment g must be 1 for digit '2'")
    }
}
