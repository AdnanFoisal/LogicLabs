package com.logiclabs.core.testing.tier4_courseware

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertPinLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Before
import org.junit.Test

/**
 * Tier 4 Real-World Application Scenario: University Lab 4.
 * Title: Combinational Decoding & 7-Segment Display with 7448 Decoder.
 * Objective: Drive 7448 BCD inputs via SW0..SW3 and verify numeric digits 0..9.
 */
class Lab4MultiplexersDecodersExperimentTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testLab4_BcdToSevenSegmentAllNumerals() {
        val uDec = circuit.placeChip("7448", trench = 1, startColumn = 20)
        // Standard power: Pin 16 VCC, Pin 8 GND
        circuit.wirePin(uDec, 16, 1934)
        circuit.wirePin(uDec, 8, 1935)

        // Tie controls inactive: ~LT=1 (Pin 3), ~BI=1 (Pin 4), ~RBI=1 (Pin 5)
        circuit.wirePin(uDec, 3, 1934)
        circuit.wirePin(uDec, 4, 1934)
        circuit.wirePin(uDec, 5, 1934)

        // Connect BCD inputs to SW0..SW3:
        // A (Pin 7) <- SW0
        // B (Pin 1) <- SW1
        // C (Pin 2) <- SW2
        // D (Pin 6) <- SW3
        circuit.wirePin(uDec, 7, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(uDec, 1, BreadboardGeometry.TERM_SW1)
        circuit.wirePin(uDec, 2, BreadboardGeometry.TERM_SW2)
        circuit.wirePin(uDec, 6, BreadboardGeometry.TERM_SW3)

        // Expected active segments for digits 0 to 9 (bits 6..0: g, f, e, d, c, b, a)
        val expectedSegments = intArrayOf(
            0x3F, // 0: a,b,c,d,e,f
            0x06, // 1: b,c
            0x5B, // 2: a,b,d,e,g
            0x4F, // 3: a,b,c,d,g
            0x66, // 4: b,c,f,g
            0x6D, // 5: a,c,d,f,g
            0x7D, // 6: a,c,d,e,f,g
            0x07, // 7: a,b,c
            0x7F, // 8: a,b,c,d,e,f,g
            0x6F  // 9: a,b,c,d,f,g
        )

        val segPinMap = listOf(13, 12, 11, 10, 9, 15, 14) // a, b, c, d, e, f, g

        for (digit in 0..9) {
            for (bit in 0..3) {
                circuit.switches[bit] = ((digit shr bit) and 1) == 1
            }
            circuit.step()

            val expectedMask = expectedSegments[digit]
            for (segIndex in 0..6) {
                val pin = segPinMap[segIndex]
                val expectedHigh = ((expectedMask shr segIndex) and 1) == 1
                val expectedLevel = if (expectedHigh) ElectricalLevel.HIGH else ElectricalLevel.LOW
                assertPinLevel(circuit, uDec, pin, expectedLevel, "Digit $digit segment $segIndex mismatch")
            }
        }
    }
}
