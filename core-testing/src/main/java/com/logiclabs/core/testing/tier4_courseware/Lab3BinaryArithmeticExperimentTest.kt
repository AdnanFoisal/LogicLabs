package com.logiclabs.core.testing.tier4_courseware

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLed
import com.logiclabs.core.testing.harness.SimulationCircuit
import org.junit.Before
import org.junit.Test

/**
 * Tier 4 Real-World Application Scenario: University Lab 3.
 * Title: Binary Arithmetic with 7483 4-Bit Binary Full Adder.
 * Objective: Wire 7483 with SW0..SW3 as 4-bit input A, SW4..SW7 as 4-bit input B,
 * and LEDs L0..L4 as 5-bit result (Sum + Carry Out).
 */
class Lab3BinaryArithmeticExperimentTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testLab3_FourBitBinaryAdderWithLedDisplay() {
        val uAdder = circuit.placeChip("7483", trench = 1, startColumn = 15)

        // Non-standard power: Pin 5 = VCC, Pin 12 = GND
        circuit.wirePin(uAdder, 5, 1934)
        circuit.wirePin(uAdder, 12, 1935)

        // Carry In (C0, Pin 13) grounded
        circuit.wirePin(uAdder, 13, 1935)

        // Connect 4-bit Input A (SW0..SW3):
        // A1 (Pin 10) <- SW0
        // A2 (Pin 8)  <- SW1
        // A3 (Pin 3)  <- SW2
        // A4 (Pin 1)  <- SW3
        circuit.wirePin(uAdder, 10, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(uAdder, 8, BreadboardGeometry.TERM_SW1)
        circuit.wirePin(uAdder, 3, BreadboardGeometry.TERM_SW2)
        circuit.wirePin(uAdder, 1, BreadboardGeometry.TERM_SW3)

        // Connect 4-bit Input B (SW4..SW7):
        // B1 (Pin 11) <- SW4
        // B2 (Pin 7)  <- SW5
        // B3 (Pin 4)  <- SW6
        // B4 (Pin 16) <- SW7
        circuit.wirePin(uAdder, 11, BreadboardGeometry.TERM_SW4)
        circuit.wirePin(uAdder, 7, BreadboardGeometry.TERM_SW5)
        circuit.wirePin(uAdder, 4, BreadboardGeometry.TERM_SW6)
        circuit.wirePin(uAdder, 16, BreadboardGeometry.TERM_SW7)

        // Connect Sum Outputs to LEDs L0..L3:
        // S1 (Pin 9)  -> LED0
        // S2 (Pin 6)  -> LED1
        // S3 (Pin 2)  -> LED2
        // S4 (Pin 15) -> LED3
        circuit.wirePin(uAdder, 9, BreadboardGeometry.TERM_LED0)
        circuit.wirePin(uAdder, 6, BreadboardGeometry.TERM_LED1)
        circuit.wirePin(uAdder, 2, BreadboardGeometry.TERM_LED2)
        circuit.wirePin(uAdder, 15, BreadboardGeometry.TERM_LED3)

        // Connect Carry Out (C4, Pin 14) -> LED4
        circuit.wirePin(uAdder, 14, BreadboardGeometry.TERM_LED4)

        fun setOperands(a: Int, b: Int) {
            for (i in 0..3) {
                circuit.switches[i] = ((a shr i) and 1) == 1
                circuit.switches[i + 4] = ((b shr i) and 1) == 1
            }
            circuit.step()
        }

        fun assertSumResult(expectedTotal: Int) {
            val s1 = circuit.ledValues[0]
            val s2 = circuit.ledValues[1]
            val s3 = circuit.ledValues[2]
            val s4 = circuit.ledValues[3]
            val c4 = circuit.ledValues[4]

            val actualTotal = (if (s1) 1 else 0) or
                    (if (s2) 2 else 0) or
                    (if (s3) 4 else 0) or
                    (if (s4) 8 else 0) or
                    (if (c4) 16 else 0)

            org.junit.Assert.assertEquals(
                "Addition result mismatch",
                expectedTotal,
                actualTotal
            )
        }

        // Test 1: 0 + 0 = 0
        setOperands(0, 0)
        assertSumResult(0)

        // Test 2: 3 + 5 = 8
        setOperands(3, 5)
        assertSumResult(8)

        // Test 3: 7 + 8 = 15
        setOperands(7, 8)
        assertSumResult(15)

        // Test 4: 9 + 10 = 19 (Overflow past 15 -> C4=1, Sum=3)
        setOperands(9, 10)
        assertSumResult(19)

        // Test 5: 15 + 15 = 30 (C4=1, Sum=14)
        setOperands(15, 15)
        assertSumResult(30)
    }
}
