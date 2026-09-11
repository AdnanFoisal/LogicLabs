package com.logiclabs.core.testing.tier2_boundary

import com.logiclabs.core.testing.harness.ElectricalAssertions.assertBurnedOut
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertPinLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 2 Boundary & Corner Cases: Floating TTL Inputs & Reverse Polarity Protection.
 * Verifies:
 * - Floating TTL inputs default to HIGH (emitter pull-up in bipolar TTL).
 * - Reverse polarity power connection triggers burnout trip.
 * - Non-standard power pin reverse polarity detection (7483, 7476).
 * Requirement: >= 5 distinct tests.
 */
class BoundaryFloatingAndReverseTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testFloatingTtlInputsPullToHigh() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        // Gate 1 inputs (Pins 1 and 2) are left completely floating (no wires)
        circuit.step()

        // Standard bipolar TTL: floating inputs pull up to logic HIGH -> AND(1,1) = 1
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH, "Floating TTL inputs must pull to HIGH")
    }

    @Test
    fun testReversePolarityStandard14PinChip() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        // Reverse power connection: Pin 14 to GND, Pin 7 to VCC
        circuit.wireReversePower(u1)
        circuit.step()

        assertBurnedOut(circuit, "Reverse polarity on 7400 must trigger burnout")
        assertTrue(circuit.isReversePolarityBurned)
    }

    @Test
    fun testReversePolarity7483NonStandardPower() {
        val u1 = circuit.placeChip("7483", trench = 1, startColumn = 10)
        // 7483 power: Pin 5 VCC, Pin 12 GND. Reversed: Pin 5 GND, Pin 12 VCC
        circuit.wirePin(u1, 5, 1935) // Pin 5 to GND
        circuit.wirePin(u1, 12, 1934) // Pin 12 to VCC
        circuit.step()

        assertBurnedOut(circuit, "Reverse polarity on 7483 must trigger burnout")
    }

    @Test
    fun testReversePolarity7476NonStandardPower() {
        val u1 = circuit.placeChip("7476", trench = 2, startColumn = 15)
        // 7476 power: Pin 5 VCC, Pin 13 GND. Reversed: Pin 5 GND, Pin 13 VCC
        circuit.wirePin(u1, 5, 1935) // Pin 5 to GND
        circuit.wirePin(u1, 13, 1934) // Pin 13 to VCC
        circuit.step()

        assertBurnedOut(circuit, "Reverse polarity on 7476 must trigger burnout")
    }

    @Test
    fun testBurnoutDisablesAllOutputsUntilReset() {
        // First place a working chip
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935) // In=0 -> Out=1
        circuit.step()
        assertPinLevel(circuit, u1, 2, ElectricalLevel.HIGH)

        // Now place a chip with reverse power to trip the main breaker
        val u2 = circuit.placeChip("7400", trench = 1, startColumn = 25)
        circuit.wireReversePower(u2)
        circuit.step()

        // Entire trainer power trips
        assertTrue(circuit.isReversePolarityBurned)
        assertEquals(ElectricalLevel.UNPOWERED, circuit.getPinLevel(u1, 2))

        // Reset clears burnout
        circuit.reset()
        assertFalse(circuit.isReversePolarityBurned)
    }
}
