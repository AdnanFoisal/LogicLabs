package com.logiclabs.core.testing.tier4_courseware

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLed
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.harness.TruthTableVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 4 Real-World Application Scenario: University Lab 2.
 * Title: Universal NAND Gate Synthesis (XOR & Half-Adder).
 * Objective: Synthesize an XOR gate and Half-Adder exclusively using 7400 Quad NAND ICs.
 */
class Lab2UniversalNandExperimentTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testUniversalNandXorSynthesis() {
        // Construct XOR using all 4 gates of a single 7400 chip:
        // Gate 1: 1A=Pin 1, 1B=Pin 2 -> 1Y=Pin 3 = NAND(A, B)
        // Gate 2: 2A=Pin 4, 2B=Pin 5 -> 2Y=Pin 6 = NAND(A, 1Y)
        // Gate 3: 3A=Pin 9, 3B=Pin 10 -> 3Y=Pin 8 = NAND(B, 1Y)
        // Gate 4: 4A=Pin 12, 4B=Pin 13 -> 4Y=Pin 11 = NAND(2Y, 3Y) = A XOR B
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)

        // Inputs from SW0 (A) and SW1 (B)
        // Connect A to Gate 1 (Pin 1) and Gate 2 (Pin 4)
        circuit.wirePin(u1, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(u1, 4, BreadboardGeometry.TERM_SW0)

        // Connect B to Gate 1 (Pin 2) and Gate 3 (Pin 10)
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_SW1)
        circuit.wirePin(u1, 10, BreadboardGeometry.TERM_SW1)

        // Connect Gate 1 output (Pin 3) to Gate 2 (Pin 5) and Gate 3 (Pin 9)
        circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(5))
        circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(9))

        // Connect Gate 2 output (Pin 6) to Gate 4 (Pin 12)
        circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(12))

        // Connect Gate 3 output (Pin 8) to Gate 4 (Pin 13)
        circuit.addWire(u1.getPinSocket(8), u1.getPinSocket(13))

        // Connect Final Output (Pin 11) to LED0
        circuit.wirePin(u1, 11, BreadboardGeometry.TERM_LED0)

        // Verify full XOR truth table
        // 00 -> 0
        circuit.switches[0] = false; circuit.switches[1] = false
        circuit.step()
        assertLed(circuit, 0, false)

        // 01 -> 1
        circuit.switches[0] = false; circuit.switches[1] = true
        circuit.step()
        assertLed(circuit, 0, true)

        // 10 -> 1
        circuit.switches[0] = true; circuit.switches[1] = false
        circuit.step()
        assertLed(circuit, 0, true)

        // 11 -> 0
        circuit.switches[0] = true; circuit.switches[1] = true
        circuit.step()
        assertLed(circuit, 0, false)
    }

    @Test
    fun testUniversalNandHalfAdderWithCryptographicVerification() {
        // Build complete Half-Adder:
        // Sum = A XOR B (synthesized with U1 7400) -> LED0
        // Carry = A AND B = NAND(NAND(A, B)) (synthesized with U2 7400 Gate 1) -> LED1
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        val u2 = circuit.placeChip("7400", trench = 1, startColumn = 30)

        circuit.wireStandardPower(u1)
        circuit.wireStandardPower(u2)

        // Wire XOR on U1
        circuit.wirePin(u1, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(u1, 4, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_SW1)
        circuit.wirePin(u1, 10, BreadboardGeometry.TERM_SW1)
        circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(5))
        circuit.addWire(u1.getPinSocket(3), u1.getPinSocket(9))
        circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(12))
        circuit.addWire(u1.getPinSocket(8), u1.getPinSocket(13))

        // Sum Output -> LED0
        circuit.wirePin(u1, 11, BreadboardGeometry.TERM_LED0)

        // Carry = NOT(NAND(A, B)) = Invert U1 Pin 3 using U2 Gate 1 (Pins 1, 2 -> 3)
        circuit.addWire(u1.getPinSocket(3), u2.getPinSocket(1))
        circuit.addWire(u1.getPinSocket(3), u2.getPinSocket(2))

        // Carry Output -> LED1
        circuit.wirePin(u2, 3, BreadboardGeometry.TERM_LED1)

        // Exhaustive 2^N verification sweep
        val result = TruthTableVerifier.verify(
            circuit = circuit,
            switchIndices = listOf(0, 1),
            outputReader = {
                listOf(circuit.ledValues[0], circuit.ledValues[1]) // Sum, Carry
            },
            expectedFunction = { inputs ->
                val a = inputs[0]
                val b = inputs[1]
                val sum = a xor b
                val carry = a && b
                listOf(sum, carry)
            }
        )

        assertEquals("4 vectors expected", 4, result.totalVectors)
        assertEquals("4 vectors passed", 4, result.passedVectors)
        assertTrue(result.isAllPassed)
        assertTrue("Cryptographic HMAC signature present", result.hmacSha256Hash.isNotEmpty())
    }
}
