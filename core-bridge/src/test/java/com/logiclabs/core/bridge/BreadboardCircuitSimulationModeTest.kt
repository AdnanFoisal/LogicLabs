package com.logiclabs.core.bridge

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.SimulationMode
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BreadboardCircuitSimulationModeTest {

    private lateinit var circuit: BreadboardCircuit

    @Before
    fun setup() {
        circuit = BreadboardCircuit()
    }

    @Test
    fun testDefaultModeIsIdeal() {
        assertEquals(SimulationMode.IDEAL, circuit.simulationMode)
        assertTrue(circuit.burnedChipIds.isEmpty())
        assertFalse(circuit.isReversePolarityBurned)
    }

    @Test
    fun testPracticalModeBurnsChipOnOutputContention() {
        circuit.simulationMode = SimulationMode.PRACTICAL

        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Connect DC power supply to rails
        circuit.addWire(AD200Topology.TERM_POWER_VCC, AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0), WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0), WireColor.BLACK)

        // Chip VCC (pin 14) and GND (pin 7)
        circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

        // Wire inputs SW0 & SW1 to Gate 1 (pins 1 & 2) and set them LOW (0, 0)
        // 7400 NAND: NOT(0 AND 0) = 1 (HIGH output on pin 3).
        circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
        circuit.switches[0] = false
        circuit.switches[1] = false

        // Short output Pin 3 directly to GND rail (Top Rail GND)!
        circuit.addWire(chip.getPinSocket(3), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 5), WireColor.BLACK)

        circuit.masterPower = true
        circuit.step()

        // Contention should be detected between HIGH gate output and LOW GND rail
        assertTrue("Contention must be flagged", circuit.contentionDetected)
        // Chip must be burned in PRACTICAL mode!
        assertTrue("Chip must be burned due to output short to GND", circuit.burnedChipIds.contains(chip.placedIc.id))
        assertTrue("Explosion timestamp must be recorded", (circuit.chipExplosionTimestamps[chip.placedIc.id] ?: 0L) > 0L)
    }

    @Test
    fun testIdealModeToleratesOutputContentionWithoutBurning() {
        circuit.simulationMode = SimulationMode.IDEAL

        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Connect DC power supply to rails
        circuit.addWire(AD200Topology.TERM_POWER_VCC, AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0), WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0), WireColor.BLACK)
        circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

        // Wire inputs SW0 & SW1 to Gate 1 (pins 1 & 2) and set LOW
        circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
        circuit.switches[0] = false
        circuit.switches[1] = false

        // Short output Pin 3 directly to GND rail
        circuit.addWire(chip.getPinSocket(3), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 5), WireColor.BLACK)

        circuit.masterPower = true
        circuit.step()

        assertTrue("Contention is flagged", circuit.contentionDetected)
        assertFalse("Chip must NOT be burned in IDEAL mode", circuit.burnedChipIds.contains(chip.placedIc.id))
        assertEquals(0, circuit.burnedChipIds.size)
    }

    @Test
    fun testPracticalModeReversePolarityBurnsChip() {
        circuit.simulationMode = SimulationMode.PRACTICAL

        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Reverse polarity: VCC terminal -> Pin 7, GND terminal -> Pin 14
        circuit.addWire(AD200Topology.TERM_POWER_VCC, chip.getPinSocket(7), WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, chip.getPinSocket(14), WireColor.BLACK)

        circuit.masterPower = true
        circuit.step()

        assertTrue("Reverse polarity must be detected", circuit.isReversePolarityBurned)
        assertTrue("Chip must be marked burned in PRACTICAL mode", circuit.burnedChipIds.contains(chip.placedIc.id))
        assertTrue("Explosion timestamp must be set", (circuit.chipExplosionTimestamps[chip.placedIc.id] ?: 0L) > 0L)
    }

    @Test
    fun testBurnedChipStopsFunctioningAndRestorationRevivesIt() {
        circuit.simulationMode = SimulationMode.PRACTICAL

        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Connect DC power supply to rails
        circuit.addWire(AD200Topology.TERM_POWER_VCC, AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0), WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0), WireColor.BLACK)
        circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

        // Wire inputs SW0 & SW1 to Gate 1 (pins 1 & 2) and set LOW
        circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
        circuit.switches[0] = false
        circuit.switches[1] = false

        // Wire Gate 2 inputs (pins 4 & 5) to SW0 & SW1 as well
        circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(4), WireColor.YELLOW)
        circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(5), WireColor.ORANGE)

        // Connect Gate 2 output (pin 6) to LED 1
        circuit.addWire(chip.getPinSocket(6), AD200Topology.TERM_LED0 + 1, WireColor.GREEN)

        // Short Gate 1 output (pin 3) to GND rail to burn chip
        val shortWire = circuit.addWire(chip.getPinSocket(3), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 5), WireColor.BLACK)

        circuit.masterPower = true
        circuit.step()

        assertTrue("Chip must be burned", circuit.burnedChipIds.contains(chip.placedIc.id))
        // Gate 2 must also be dead because whole IC silicon is burned
        assertFalse("Burned chip cannot drive LED", circuit.ledValues[1])

        // Remove the short circuit wire
        circuit.removeWire(shortWire.id)

        // Step before restore: still dead
        circuit.step()
        assertFalse("Still dead before restore", circuit.ledValues[1])

        // Press restore
        circuit.restoreBurnedChips()

        assertTrue("Burned chips list cleared", circuit.burnedChipIds.isEmpty())
        assertTrue("Explosion timestamps cleared", circuit.chipExplosionTimestamps.isEmpty())
        assertFalse("Contention cleared", circuit.contentionDetected)

        // Now Gate 2 operates properly: NAND(0,0)=1 -> LED 1 is ON!
        assertTrue("Revived chip successfully drives LED 1", circuit.ledValues[1])
    }
}
