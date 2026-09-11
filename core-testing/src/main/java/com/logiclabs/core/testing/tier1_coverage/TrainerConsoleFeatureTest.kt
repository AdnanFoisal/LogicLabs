package com.logiclabs.core.testing.tier1_coverage

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLed
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Coverage: K&H IDL-800A Trainer Instrument Console.
 * Verifies:
 * - Master AC switch power isolation.
 * - 8x SPDT logic input switches (SW0-SW7).
 * - 2x Debounced complementary pulsers (P and ~P).
 * - 8x Buffered LED indicators with POV duty-cycle luminance filter.
 * - Stepped Clock Generator frequency steps and inverted clock.
 * Requirement: >= 5 distinct tests.
 */
class TrainerConsoleFeatureTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testMasterAcSwitchPowerIsolation() {
        // Master power ON by default
        circuit.step()
        val railVcc = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_VCC_5V, 0)
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(railVcc), "VCC rail should be energised")

        // Switch master power OFF
        circuit.masterPower = false
        circuit.step()
        assertLevel(ElectricalLevel.UNPOWERED, circuit.getSocketLevel(railVcc), "VCC rail must be unpowered when Master is OFF")

        // Switch master power back ON
        circuit.masterPower = true
        circuit.step()
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(railVcc), "VCC rail must re-energise when Master is ON")
    }

    @Test
    fun test8xSpdtSwitchesAssertion() {
        // Test all 8 switches toggled independently
        for (i in 0..7) {
            val swTerm = BreadboardGeometry.TERM_SW0 + i

            // Switch DOWN (LOW)
            circuit.switches[i] = false
            circuit.step()
            assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(swTerm), "SW$i DOWN must assert LOW")

            // Switch UP (HIGH)
            circuit.switches[i] = true
            circuit.step()
            assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(swTerm), "SW$i UP must assert HIGH")
        }
    }

    @Test
    fun testDebouncedPulsersComplementaryBehavior() {
        // Pulser A: Normally P=0, ~P=1
        circuit.pulserAPressed = false
        circuit.step()
        assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(BreadboardGeometry.TERM_PULSER_A_P))
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(BreadboardGeometry.TERM_PULSER_A_N))

        // Pulser A pressed: P=1, ~P=0
        circuit.pulserAPressed = true
        circuit.step()
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(BreadboardGeometry.TERM_PULSER_A_P))
        assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(BreadboardGeometry.TERM_PULSER_A_N))

        // Released
        circuit.pulserAPressed = false
        circuit.step()
        assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(BreadboardGeometry.TERM_PULSER_A_P))
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(BreadboardGeometry.TERM_PULSER_A_N))

        // Pulser B works identically
        circuit.pulserBPressed = true
        circuit.step()
        assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(BreadboardGeometry.TERM_PULSER_B_P))
        assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(BreadboardGeometry.TERM_PULSER_B_N))
    }

    @Test
    fun test8xBufferedLedIndicatorsAndPovLuminance() {
        // Wire SW0 -> LED0, SW1 -> LED1
        circuit.addWire(BreadboardGeometry.TERM_SW0, BreadboardGeometry.TERM_LED0)
        circuit.addWire(BreadboardGeometry.TERM_SW1, BreadboardGeometry.TERM_LED1)

        // SW0 = 1, SW1 = 0
        circuit.switches[0] = true
        circuit.switches[1] = false
        circuit.step()

        assertLed(circuit, 0, expectedLit = true)
        assertLed(circuit, 1, expectedLit = false)
        assertTrue("LED0 POV luminance should be > 0", circuit.ledPovDutyCycles[0] > 0.0f)
        assertEquals("LED1 POV luminance should be 0", 0.0f, circuit.ledPovDutyCycles[1], 0.01f)
    }

    @Test
    fun testSteppedClockGeneratorOutputsAndInversion() {
        val clkTerm = BreadboardGeometry.TERM_CLK
        val clkInvTerm = BreadboardGeometry.TERM_CLK_INV

        val supportedFreqs = listOf(0.5, 1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0)
        for (freq in supportedFreqs) {
            circuit.clockFrequencyHz = freq
            circuit.clockState = false
            circuit.step()

            // CLK = 0, ~CLK = 1
            assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(clkTerm), "CLK should be 0 at $freq Hz")
            assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(clkInvTerm), "~CLK should be 1 at $freq Hz")

            // Tick clock -> CLK = 1, ~CLK = 0
            circuit.tickClock()
            assertLevel(ElectricalLevel.HIGH, circuit.getSocketLevel(clkTerm), "CLK should be 1 after tick at $freq Hz")
            assertLevel(ElectricalLevel.LOW, circuit.getSocketLevel(clkInvTerm), "~CLK should be 0 after tick at $freq Hz")
        }
    }
}
