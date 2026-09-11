package com.logiclabs.core.testing.tier4_courseware

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLed
import com.logiclabs.core.testing.harness.SimulationCircuit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 4 Real-World Application Scenario: University Lab 1.
 * Title: Basic Logic Gates & Truth Table Verification.
 * Objective: Concurrently mount 7400 (NAND), 7408 (AND), 7432 (OR), and 7404 (NOT),
 * wire them to input switches SW0, SW1 and monitor outputs via LEDs L0..L3.
 */
class Lab1BasicGatesExperimentTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testLab1_FullBasicGatesCurriculumVerification() {
        // Step 1: Place all 4 chips on the AD-200 breadboard
        val uNand = circuit.placeChip("7400", trench = 1, startColumn = 5)
        val uAnd = circuit.placeChip("7408", trench = 1, startColumn = 20)
        val uOr = circuit.placeChip("7432", trench = 1, startColumn = 35)
        val uNot = circuit.placeChip("7404", trench = 1, startColumn = 50)

        // Step 2: Wire power distribution
        circuit.wireStandardPower(uNand)
        circuit.wireStandardPower(uAnd)
        circuit.wireStandardPower(uOr)
        circuit.wireStandardPower(uNot)

        // Step 3: Connect input switches SW0 (A) and SW1 (B) to all 2-input gates
        circuit.wirePin(uNand, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(uNand, 2, BreadboardGeometry.TERM_SW1)

        circuit.wirePin(uAnd, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(uAnd, 2, BreadboardGeometry.TERM_SW1)

        circuit.wirePin(uOr, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(uOr, 2, BreadboardGeometry.TERM_SW1)

        // Connect SW0 to Inverter
        circuit.wirePin(uNot, 1, BreadboardGeometry.TERM_SW0)

        // Step 4: Connect gate outputs to buffered LED monitors:
        // L0: NAND, L1: AND, L2: OR, L3: NOT
        circuit.wirePin(uNand, 3, BreadboardGeometry.TERM_LED0)
        circuit.wirePin(uAnd, 3, BreadboardGeometry.TERM_LED1)
        circuit.wirePin(uOr, 3, BreadboardGeometry.TERM_LED2)
        circuit.wirePin(uNot, 2, BreadboardGeometry.TERM_LED3)

        // Test Vector 1: SW0 = 0, SW1 = 0
        // Expected: NAND=1, AND=0, OR=0, NOT=1
        circuit.switches[0] = false; circuit.switches[1] = false
        circuit.step()
        assertLed(circuit, 0, true)  // NAND
        assertLed(circuit, 1, false) // AND
        assertLed(circuit, 2, false) // OR
        assertLed(circuit, 3, true)  // NOT

        // Test Vector 2: SW0 = 0, SW1 = 1
        // Expected: NAND=1, AND=0, OR=1, NOT=1
        circuit.switches[0] = false; circuit.switches[1] = true
        circuit.step()
        assertLed(circuit, 0, true)
        assertLed(circuit, 1, false)
        assertLed(circuit, 2, true)
        assertLed(circuit, 3, true)

        // Test Vector 3: SW0 = 1, SW1 = 0
        // Expected: NAND=1, AND=0, OR=1, NOT=0
        circuit.switches[0] = true; circuit.switches[1] = false
        circuit.step()
        assertLed(circuit, 0, true)
        assertLed(circuit, 1, false)
        assertLed(circuit, 2, true)
        assertLed(circuit, 3, false)

        // Test Vector 4: SW0 = 1, SW1 = 1
        // Expected: NAND=0, AND=1, OR=1, NOT=0
        circuit.switches[0] = true; circuit.switches[1] = true
        circuit.step()
        assertLed(circuit, 0, false)
        assertLed(circuit, 1, true)
        assertLed(circuit, 2, true)
        assertLed(circuit, 3, false)

        // Lab 1 objective checklist verified
        assertTrue("Lab 1: All 4 basic gates verified successfully", true)
    }
}
