package com.logiclabs.core.bridge

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BreadboardCircuitPowerTest {

    private lateinit var circuit: BreadboardCircuit

    @Before
    fun setup() {
        circuit = BreadboardCircuit()
    }

    @Test
    fun testCircuitDoesNotProduceOutputWithoutVccJumperWire() {
        // Place a 7400 NAND chip at trench 1, column 10
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Wire chip power pins to the breadboard distribution rails:
        // Pin 14 -> Top Rail 0 (+5V rail), Pin 7 -> Top Rail 1 (GND rail)
        circuit.addWire(chip.getPinSocket(14), 1280 + 10, WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), 1280 + 88 + 10, WireColor.BLACK)

        // Connect SW0 and SW1 to Gate 1 inputs (Pins 1 and 2)
        circuit.addWire(1896, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(1897, chip.getPinSocket(2), WireColor.ORANGE)

        // Connect Gate 1 output (Pin 3) to LED0 (socket 1908)
        circuit.addWire(chip.getPinSocket(3), 1908, WireColor.GREEN)

        // But do NOT connect TERM_POWER_VCC (1934) or TERM_POWER_GND (1935) to the rails!
        // SW0 = 0, SW1 = 0. In a powered 7400 NAND gate, NOT(0 AND 0) = 1 (HIGH).
        circuit.switches[0] = false
        circuit.switches[1] = false
        circuit.masterPower = true
        circuit.step()

        // Since VCC is NOT wired from the DC power supply, the chip is unpowered (HIGH_Z)
        // LED0 must NOT produce output (must remain false/OFF)
        assertFalse("LED0 must be OFF when VCC is not wired to the rail", circuit.ledValues[0])
    }

    @Test
    fun testCircuitProducesOutputWhenVccAndGndAreWired() {
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Connect DC Power Supply terminals to distribution rails:
        circuit.addWire(AD200Topology.TERM_POWER_VCC, 1280, WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, 1280 + 88, WireColor.BLACK)

        // Wire chip power pins to rails
        circuit.addWire(chip.getPinSocket(14), 1280 + 10, WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), 1280 + 88 + 10, WireColor.BLACK)

        // Connect SW0 and SW1 to Gate 1 inputs (Pins 1 and 2)
        circuit.addWire(1896, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(1897, chip.getPinSocket(2), WireColor.ORANGE)

        // Connect Gate 1 output (Pin 3) to LED0 (socket 1908)
        circuit.addWire(chip.getPinSocket(3), 1908, WireColor.GREEN)

        circuit.switches[0] = false
        circuit.switches[1] = false
        circuit.masterPower = true
        circuit.step()

        // 7400 NAND: NOT(0 AND 0) = 1 (HIGH) -> LED0 must turn ON
        assertTrue("LED0 must be ON when VCC and GND are properly wired and master power is ON", circuit.ledValues[0])

        // Switch to SW0=1, SW1=1: NOT(1 AND 1) = 0 (LOW) -> LED0 turns OFF
        circuit.switches[0] = true
        circuit.switches[1] = true
        circuit.step()
        assertFalse("LED0 must turn OFF when NAND inputs are 1, 1", circuit.ledValues[0])

        // Switch back to SW0=0: NOT(0 AND 1) = 1 -> LED0 turns ON
        circuit.switches[0] = false
        circuit.step()
        assertTrue("LED0 must turn ON when NAND inputs are 0, 1", circuit.ledValues[0])
    }

    @Test
    fun testMasterPowerToggleControlsVccPower() {
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        circuit.addWire(AD200Topology.TERM_POWER_VCC, 1280, WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, 1280 + 88, WireColor.BLACK)
        circuit.addWire(chip.getPinSocket(14), 1280 + 10, WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), 1280 + 88 + 10, WireColor.BLACK)
        circuit.addWire(1896, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(1897, chip.getPinSocket(2), WireColor.ORANGE)
        circuit.addWire(chip.getPinSocket(3), 1908, WireColor.GREEN)

        circuit.switches[0] = false
        circuit.switches[1] = false

        // Master power ON
        circuit.masterPower = true
        circuit.step()
        assertTrue("Circuit works when master power is ON", circuit.ledValues[0])

        // Master power OFF
        circuit.masterPower = false
        circuit.step()
        assertFalse("Circuit stops producing output when master power is OFF", circuit.ledValues[0])

        // Master power ON again
        circuit.masterPower = true
        circuit.step()
        assertTrue("Circuit resumes output when master power is restored", circuit.ledValues[0])
    }

    @Test
    fun testRemovingVccWireDisablesCircuitOutput() {
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        val vccWire = circuit.addWire(AD200Topology.TERM_POWER_VCC, 1280, WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, 1280 + 88, WireColor.BLACK)
        circuit.addWire(chip.getPinSocket(14), 1280 + 10, WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), 1280 + 88 + 10, WireColor.BLACK)
        circuit.addWire(1896, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(1897, chip.getPinSocket(2), WireColor.ORANGE)
        circuit.addWire(chip.getPinSocket(3), 1908, WireColor.GREEN)

        circuit.switches[0] = false
        circuit.switches[1] = false
        circuit.masterPower = true
        circuit.step()
        assertTrue("Circuit is ON initially", circuit.ledValues[0])

        // Remove the VCC wire
        circuit.removeWire(vccWire.id)
        circuit.step()
        assertFalse("Removing VCC wire turns off circuit output", circuit.ledValues[0])
    }

    @Test
    fun testRemovingGndWireDisablesCircuitOutput() {
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        circuit.addWire(AD200Topology.TERM_POWER_VCC, 1280, WireColor.RED)
        val gndWire = circuit.addWire(AD200Topology.TERM_POWER_GND, 1280 + 88, WireColor.BLACK)
        circuit.addWire(chip.getPinSocket(14), 1280 + 10, WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), 1280 + 88 + 10, WireColor.BLACK)
        circuit.addWire(1896, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(1897, chip.getPinSocket(2), WireColor.ORANGE)
        circuit.addWire(chip.getPinSocket(3), 1908, WireColor.GREEN)

        circuit.switches[0] = false
        circuit.switches[1] = false
        circuit.masterPower = true
        circuit.step()
        assertTrue("Circuit is ON initially", circuit.ledValues[0])

        // Remove the GND wire
        circuit.removeWire(gndWire.id)
        circuit.step()
        assertFalse("Removing GND wire turns off circuit output", circuit.ledValues[0])
    }

    @Test
    fun testDualBottomPowerRailsPowerCircuit() {
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Connect DC power supply to BOTTOM power rails (Rail 2 VCC, Rail 3 GND)
        val botVccSocket = AD200Topology.railSocket(AD200Topology.RAIL_BOT_VCC_5V, 0)
        val botGndSocket = AD200Topology.railSocket(AD200Topology.RAIL_BOT_GND, 0)
        circuit.addWire(AD200Topology.TERM_POWER_VCC, botVccSocket, WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, botGndSocket, WireColor.BLACK)

        // Wire chip power pins to the BOTTOM rails
        circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_BOT_VCC_5V, 10), WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_BOT_GND, 10), WireColor.BLACK)

        // Connect inputs & output
        circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
        circuit.addWire(chip.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

        circuit.switches[0] = false
        circuit.switches[1] = false
        circuit.masterPower = true
        circuit.step()

        // 7400 NAND: !(0 && 0) = 1 (HIGH) -> LED0 turns ON via bottom power rails!
        assertTrue("Circuit must function when powered through dual bottom power rails", circuit.ledValues[0])

        circuit.switches[0] = true
        circuit.switches[1] = true
        circuit.step()
        assertFalse("NAND output becomes LOW when both inputs are HIGH", circuit.ledValues[0])
    }

    @Test
    fun testColumnMultiWireContinuityAndSharing() {
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)

        // Power the chip
        circuit.addWire(AD200Topology.TERM_POWER_VCC, AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0), WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0), WireColor.BLACK)
        circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
        circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

        // Chip Pin 1 is in Block 1, Col 10, Row 0 (Hole A).
        // Real-world breadboard: Plug wire from SW0 into Row 4 (Hole E) of the same column 10!
        val col10RowE = AD200Topology.terminalSocket(1, 10, 4)
        circuit.addWire(AD200Topology.TERM_SW0, col10RowE, WireColor.YELLOW)

        // Also plug another wire into Row 3 (Hole D) of column 10 and verify it shares the same net!
        val col10RowD = AD200Topology.terminalSocket(1, 10, 3)
        assertTrue("Row E and Row D in Column 10 must be in same electrical net", circuit.dsu.areConnected(col10RowE, col10RowD))
        assertTrue("Row E and Chip Pin 1 in Column 10 must be in same electrical net", circuit.dsu.areConnected(col10RowE, chip.getPinSocket(1)))

        // Connect SW1 to Row 2 (Hole C) of column 11 (where Pin 2 sits at Row 0)
        val col11RowC = AD200Topology.terminalSocket(1, 11, 2)
        circuit.addWire(AD200Topology.TERM_SW1, col11RowC, WireColor.ORANGE)

        // Connect Output LED0 to Row 3 (Hole D) of column 12 (where Pin 3 sits at Row 0)
        val col12RowD = AD200Topology.terminalSocket(1, 12, 3)
        circuit.addWire(col12RowD, AD200Topology.TERM_LED0, WireColor.GREEN)

        circuit.switches[0] = false
        circuit.switches[1] = false
        circuit.masterPower = true
        circuit.step()

        assertTrue("Plugging into different rows of the same column must connect to the IC pin", circuit.ledValues[0])

        circuit.switches[0] = true
        circuit.switches[1] = true
        circuit.step()
        assertFalse("NAND with column sharing switches correctly to LOW", circuit.ledValues[0])
    }

    @Test
    fun testRotateChipAndMoveChip() {
        val chip = circuit.addChip("7400", trench = 1, startColumn = 10)
        val initialPin1 = chip.getPinSocket(1)

        // Rotate chip 180°
        val rotated = circuit.rotateChip(chip.placedIc.id)
        assertTrue("rotateChip must return true", rotated)
        val updatedChip = circuit.placedChips.find { it.placedIc.id == chip.placedIc.id }!!
        assertTrue("isRotated180 must be true", updatedChip.placedIc.isRotated180)

        // Pin 1 socket should now be rotated
        val newPin1 = updatedChip.getPinSocket(1)
        assertTrue("Pin 1 socket must change after 180° rotation", initialPin1 != newPin1)

        // Rotate back
        circuit.rotateChip(chip.placedIc.id)

        // Add a wire to Pin 1
        val w = circuit.addWire(AD200Topology.TERM_SW0, initialPin1, WireColor.YELLOW)

        // Move chip to Column 25
        val moved = circuit.moveChip(chip.placedIc.id, 1, 25)
        assertTrue("moveChip must return true", moved)
        val movedChip = circuit.placedChips.find { it.placedIc.id == chip.placedIc.id }!!
        org.junit.Assert.assertEquals(25, movedChip.placedIc.startColumn)

        // Verify that the connected wire migrated to the new Pin 1 socket in Column 25!
        val migratedWire = circuit.wires.find { it.id == w.id }!!
        org.junit.Assert.assertEquals(movedChip.getPinSocket(1), migratedWire.endSocket)
    }
}
