package com.logiclabs.core.testing.harness

import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Fluent electrical domain assertions for Logic Labs E2E testing.
 */
object ElectricalAssertions {

    fun assertLevel(expected: ElectricalLevel, actual: ElectricalLevel, message: String = "") {
        assertEquals(
            if (message.isEmpty()) "Electrical level mismatch" else message,
            expected,
            actual
        )
    }

    fun assertPinLevel(
        circuit: SimulationCircuit,
        chip: PlacedChip,
        pinNumber: Int,
        expected: ElectricalLevel,
        message: String = ""
    ) {
        val actual = circuit.getPinLevel(chip, pinNumber)
        assertEquals(
            if (message.isEmpty()) "${chip.model.partNumber} Pin $pinNumber level mismatch" else message,
            expected,
            actual
        )
    }

    fun assertLed(circuit: SimulationCircuit, ledIndex: Int, expectedLit: Boolean) {
        assertEquals(
            "LED L$ledIndex lit state mismatch",
            expectedLit,
            circuit.ledValues[ledIndex]
        )
    }

    fun assertContention(circuit: SimulationCircuit, message: String = "Expected totem-pole bus contention") {
        assertTrue(message, circuit.contentionDetected)
    }

    fun assertNoContention(circuit: SimulationCircuit, message: String = "Unexpected bus contention detected") {
        assertFalse(message, circuit.contentionDetected)
    }

    fun assertOscillationClamped(
        circuit: SimulationCircuit,
        message: String = "Expected ring oscillator 10,000-event safeguard clamping"
    ) {
        assertTrue(message, circuit.isOscillationClamped)
    }

    fun assertBurnedOut(
        circuit: SimulationCircuit,
        message: String = "Expected reverse polarity burnout trip"
    ) {
        assertTrue(message, circuit.isReversePolarityBurned)
    }

    fun assertNetConnected(circuit: SimulationCircuit, socketA: Int, socketB: Int) {
        assertTrue(
            "Sockets $socketA and $socketB should be connected in same net",
            circuit.dsu.areConnected(socketA, socketB)
        )
    }

    fun assertNetIsolated(circuit: SimulationCircuit, socketA: Int, socketB: Int) {
        assertFalse(
            "Sockets $socketA and $socketB should be isolated",
            circuit.dsu.areConnected(socketA, socketB)
        )
    }
}
