package com.logiclabs.core.testing.tier4_courseware

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.ElectricalAssertions.assertPinLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Before
import org.junit.Test

/**
 * Tier 4 Real-World Application Scenario: University Lab 5.
 * Title: Sequential Logic & Frequency Division with Edge-Triggered Flip-Flops.
 * Objective: Build a 2-stage binary ripple counter / frequency divider (divide-by-2, divide-by-4)
 * using the 7474 Dual D-Type Flip-Flop.
 */
class Lab5SequentialLogicExperimentTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testLab5_TwoStageFrequencyDividerWith7474() {
        val u1 = circuit.placeChip("7474", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)

        // Asynchronous pins inactive (HIGH):
        // 1~CLR (Pin 1), 1~PRE (Pin 4), 2~CLR (Pin 13), 2~PRE (Pin 10)
        circuit.wirePin(u1, 1, 1934)
        circuit.wirePin(u1, 4, 1934)
        circuit.wirePin(u1, 13, 1934)
        circuit.wirePin(u1, 10, 1934)

        // Stage 1 Toggle Configuration:
        // Feedback 1~Q (Pin 6) -> 1D (Pin 2)
        circuit.addWire(u1.getPinSocket(6), u1.getPinSocket(2))

        // Clock input: 1CLK (Pin 3) driven by Stepped Clock
        circuit.wirePin(u1, 3, BreadboardGeometry.TERM_CLK)

        // Stage 2 Toggle Configuration:
        // Feedback 2~Q (Pin 8) -> 2D (Pin 12)
        circuit.addWire(u1.getPinSocket(8), u1.getPinSocket(12))

        // Stage 2 Clock: 2CLK (Pin 11) driven by Stage 1 1Q (Pin 5)
        circuit.addWire(u1.getPinSocket(5), u1.getPinSocket(11))

        // Wire outputs to LEDs:
        // L0 <- 1Q (f / 2)
        // L1 <- 2Q (f / 4)
        circuit.addWire(u1.getPinSocket(5), BreadboardGeometry.TERM_LED0)
        circuit.addWire(u1.getPinSocket(9), BreadboardGeometry.TERM_LED1)

        // Initial state: Clock LOW
        circuit.clockState = false
        circuit.step()
        assertPinLevel(circuit, u1, 5, ElectricalLevel.LOW, "Initial 1Q must be 0")
        assertPinLevel(circuit, u1, 9, ElectricalLevel.LOW, "Initial 2Q must be 0")

        // Cycle 1 Rising Edge: Clock 0 -> 1
        circuit.tickClock()
        // 1Q toggles 0 -> 1. This is a rising edge on 2CLK! So 2Q also toggles 0 -> 1!
        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH, "1Q toggled to 1")
        assertPinLevel(circuit, u1, 9, ElectricalLevel.HIGH, "2Q toggled to 1")

        // Falling edge: Clock 1 -> 0 (no state change)
        circuit.tickClock()
        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 9, ElectricalLevel.HIGH)

        // Cycle 2 Rising Edge: Clock 0 -> 1
        circuit.tickClock()
        // 1Q toggles 1 -> 0 (falling edge into Stage 2 -> 2Q remains unchanged at 1)
        assertPinLevel(circuit, u1, 5, ElectricalLevel.LOW, "1Q toggled to 0")
        assertPinLevel(circuit, u1, 9, ElectricalLevel.HIGH, "2Q maintains state 1 on falling edge of 1Q")

        // Falling edge
        circuit.tickClock()

        // Cycle 3 Rising Edge: Clock 0 -> 1
        circuit.tickClock()
        // 1Q toggles 0 -> 1 (rising edge into Stage 2 -> 2Q toggles 1 -> 0)
        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH, "1Q toggled to 1")
        assertPinLevel(circuit, u1, 9, ElectricalLevel.LOW, "2Q toggled to 0 (divide-by-4 completed full cycle)")
    }
}
