package com.logiclabs.core.testing.tier3_combination

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLed
import com.logiclabs.core.testing.harness.SimulationCircuit
import org.junit.Before
import org.junit.Test

/**
 * Tier 3 Cross-Feature Combination: Switches -> Logic Gates -> Buffered LEDs.
 * Verifies end-to-end signal flow from manual SPDT switches through combinations of
 * 7408 (AND), 7432 (OR), and 7404 (NOT) chips to console LED monitors.
 */
class SwitchesToGatesToLedsTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testSwitchesDrivingAndGateToLed0() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)

        // Wire SW0 -> 1A (Pin 1), SW1 -> 1B (Pin 2)
        circuit.wirePin(u1, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_SW1)

        // Wire 1Y (Pin 3) -> LED L0 (Terminal 1908)
        circuit.wirePin(u1, 3, BreadboardGeometry.TERM_LED0)

        // 00 -> LED0 OFF
        circuit.switches[0] = false; circuit.switches[1] = false
        circuit.step()
        assertLed(circuit, 0, false)

        // 01 -> LED0 OFF
        circuit.switches[0] = false; circuit.switches[1] = true
        circuit.step()
        assertLed(circuit, 0, false)

        // 10 -> LED0 OFF
        circuit.switches[0] = true; circuit.switches[1] = false
        circuit.step()
        assertLed(circuit, 0, false)

        // 11 -> LED0 ON!
        circuit.switches[0] = true; circuit.switches[1] = true
        circuit.step()
        assertLed(circuit, 0, true)
    }

    @Test
    fun testSwitchesDrivingOrGateToLed1() {
        val u1 = circuit.placeChip("7432", trench = 1, startColumn = 25)
        circuit.wireStandardPower(u1)

        // SW2 -> 1A (Pin 1), SW3 -> 1B (Pin 2)
        circuit.wirePin(u1, 1, BreadboardGeometry.TERM_SW2)
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_SW3)
        // 1Y (Pin 3) -> LED L1
        circuit.wirePin(u1, 3, BreadboardGeometry.TERM_LED1)

        // 00 -> LED1 OFF
        circuit.switches[2] = false; circuit.switches[3] = false
        circuit.step()
        assertLed(circuit, 1, false)

        // 10 -> LED1 ON
        circuit.switches[2] = true; circuit.switches[3] = false
        circuit.step()
        assertLed(circuit, 1, true)

        // 01 -> LED1 ON
        circuit.switches[2] = false; circuit.switches[3] = true
        circuit.step()
        assertLed(circuit, 1, true)
    }

    @Test
    fun testSwitchDrivingInverterToLed2() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 40)
        circuit.wireStandardPower(u1)

        // SW4 -> 1A (Pin 1)
        circuit.wirePin(u1, 1, BreadboardGeometry.TERM_SW4)
        // 1Y (Pin 2) -> LED L2
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_LED2)

        // SW4 = 0 -> LED2 ON
        circuit.switches[4] = false
        circuit.step()
        assertLed(circuit, 2, true)

        // SW4 = 1 -> LED2 OFF
        circuit.switches[4] = true
        circuit.step()
        assertLed(circuit, 2, false)
    }

    @Test
    fun testMultiStageCombinationalLogicDrivingLeds() {
        // Build: Y = (SW0 AND SW1) OR NOT(SW2) -> LED L7
        val uAnd = circuit.placeChip("7408", trench = 1, startColumn = 10)
        val uOr = circuit.placeChip("7432", trench = 1, startColumn = 25)
        val uNot = circuit.placeChip("7404", trench = 1, startColumn = 40)

        circuit.wireStandardPower(uAnd)
        circuit.wireStandardPower(uOr)
        circuit.wireStandardPower(uNot)

        // SW0 & SW1 -> AND
        circuit.wirePin(uAnd, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(uAnd, 2, BreadboardGeometry.TERM_SW1)

        // SW2 -> NOT
        circuit.wirePin(uNot, 1, BreadboardGeometry.TERM_SW2)

        // AND output (Pin 3) & NOT output (Pin 2) -> OR inputs (Pins 1 & 2)
        circuit.addWire(uAnd.getPinSocket(3), uOr.getPinSocket(1))
        circuit.addWire(uNot.getPinSocket(2), uOr.getPinSocket(2))

        // OR output (Pin 3) -> LED L7
        circuit.wirePin(uOr, 3, BreadboardGeometry.TERM_LED7)

        // Case 1: SW0=0, SW1=0, SW2=1 -> (0 AND 0) OR NOT(1) = 0 OR 0 = 0 -> LED7 OFF
        circuit.switches[0] = false; circuit.switches[1] = false; circuit.switches[2] = true
        circuit.step()
        assertLed(circuit, 7, false)

        // Case 2: SW0=0, SW1=0, SW2=0 -> (0 AND 0) OR NOT(0) = 0 OR 1 = 1 -> LED7 ON
        circuit.switches[0] = false; circuit.switches[1] = false; circuit.switches[2] = false
        circuit.step()
        assertLed(circuit, 7, true)

        // Case 3: SW0=1, SW1=1, SW2=1 -> (1 AND 1) OR NOT(1) = 1 OR 0 = 1 -> LED7 ON
        circuit.switches[0] = true; circuit.switches[1] = true; circuit.switches[2] = true
        circuit.step()
        assertLed(circuit, 7, true)
    }
}
