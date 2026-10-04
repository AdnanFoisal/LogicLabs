package com.logiclabs.app

import com.logiclabs.app.ui.screens.bench.discoverBestIoMapping
import com.logiclabs.app.ui.screens.bench.findBestMatchingLab
import com.logiclabs.app.ui.screens.bench.generateCombinations
import com.logiclabs.app.ui.screens.bench.generatePermutations
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.core.data.persistence.CircuitShareBytecode
import com.logiclabs.core.data.persistence.ProjectPersistence
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import com.logiclabs.feature.tools.testbench.TestBenchVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFullAdderVerificationTest {

    private val userShareCode = "LOGIC-eNoNkK1Lg2EUxc_94tmjjoHBNFgeA4swNIigReRFwxYGwyIM3RAEeVUGBsW5IuiahoFYViyCCBb_Cz-CxWKxCCLoTN5wfxzuPZdzuUmyME-QuQPAhEYgVPAqg3kCgUtAKZxaVhDOrBlhOZQF1qJeBH9ZXoBLKwqkbjMCGrdFAW_aIELv7CKChtZxnVg7IrTk0dmUH_cPddr5rJM-ndVUoFPacN2gbecO7Xniho4ydEyPnB06dO5S17d-seT-Y6ww5IleI-SP3jz9nT68n6VPBn3ruXMftwrN4Z6BB-0DdMLLgLxwxe-v6XUGqPKqQbu85s4r2VLwug78I-2QBuiNpMA_0Ucvxw"

    @Test
    fun testFindBestMatchingLabForUserCircuit() {
        val project = CircuitShareBytecode.decodeToProject(userShareCode)
        val circuit = BreadboardCircuit()
        ProjectPersistence.deserialize(project, circuit)

        val placedChips = circuit.placedChips.map { it.placedIc.partNumber }
        val connectedSwitches = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_SW0 + it) > 1 }
        val connectedLeds = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_LED0 + it) > 2 }

        assertEquals(listOf("7408", "7408", "7408", "7404", "7432"), placedChips)
        assertEquals(listOf(0, 1, 2), connectedSwitches)
        assertEquals(listOf(0), connectedLeds)

        val matchedLab = findBestMatchingLab(
            placedChips = placedChips,
            connectedSwitchCount = connectedSwitches.size,
            connectedLedCount = connectedLeds.size,
            circuit = circuit,
            connectedSwitches = connectedSwitches,
            connectedLeds = connectedLeds
        )
        assertEquals(
            "Sandbox/imported matching must match Full Adder Sum instead of naive first-chip 7408 AND gate",
            "exp14_full_adder_basic_sum",
            matchedLab.id
        )
    }

    @Test
    fun testInspectUserCircuitWiring() {
        val project = CircuitShareBytecode.decodeToProject(userShareCode)
        println("User Project Chips: ${project.chips.size}")
        for ((idx, chip) in project.chips.withIndex()) {
            println("Chip $idx: ${chip.partNumber} at trench=${chip.trench}, col=${chip.startColumn}")
        }
        println("User Project Wires: ${project.wires.size}")
        for ((idx, wire) in project.wires.withIndex()) {
            val startDesc = describeSocket(wire.startSocket, project.chips)
            val endDesc = describeSocket(wire.endSocket, project.chips)
            println("Wire $idx: $startDesc (${wire.startSocket}) -> $endDesc (${wire.endSocket}), color=${wire.colorName}")
        }
    }

    private fun describeSocket(socket: Int, chips: List<com.logiclabs.core.data.persistence.SerializedChip>): String {
        for ((idx, chip) in chips.withIndex()) {
            for (pin in 1..14) {
                if (AD200Topology.icPinSocket(chip.trench, chip.startColumn, pin, 14, chip.isRotated180) == socket) {
                    return "Chip $idx (${chip.partNumber}) Pin $pin"
                }
            }
        }
        if (socket in AD200Topology.TERM_SW0..AD200Topology.TERM_SW7) return "SW${socket - AD200Topology.TERM_SW0}"
        if (socket in AD200Topology.TERM_LED0..AD200Topology.TERM_LED7) return "LED${socket - AD200Topology.TERM_LED0}"
        if (socket in AD200Topology.TERM_BOT_LED0..AD200Topology.TERM_BOT_LED7) return "BOT_LED${socket - AD200Topology.TERM_BOT_LED0}"
        if (socket == AD200Topology.TERM_POWER_VCC) return "TERM_VCC"
        if (socket == AD200Topology.TERM_POWER_GND) return "TERM_GND"
        if (socket in AD200Topology.TOTAL_TERMINAL_SOCKETS until AD200Topology.TOTAL_BREADBOARD_SOCKETS) {
            val rOff = socket - AD200Topology.TOTAL_TERMINAL_SOCKETS
            val rail = rOff / AD200Topology.SOCKETS_PER_RAIL
            val pos = rOff % AD200Topology.SOCKETS_PER_RAIL
            return "Rail $rail Pos $pos"
        }
        if (socket < AD200Topology.TOTAL_TERMINAL_SOCKETS) {
            val block = socket / AD200Topology.SOCKETS_PER_BLOCK
            val rem = socket % AD200Topology.SOCKETS_PER_BLOCK
            val col = rem / AD200Topology.ROWS_PER_BLOCK
            val row = rem % AD200Topology.ROWS_PER_BLOCK
            return "Block $block Col $col Row $row"
        }
        return "Socket $socket"
    }

    @Test
    fun testDiagnoseUserCircuitFailure() {
        val project = CircuitShareBytecode.decodeToProject(userShareCode)
        val circuit = BreadboardCircuit()
        ProjectPersistence.deserialize(project, circuit)

        val fullAdderLab = ExperimentCatalog.allLabs.first { it.id == "exp14_full_adder_basic_sum" }
        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = listOf(0, 1, 2),
            outputReader = { listOf(circuit.ledValues[0]) },
            expectedFunction = fullAdderLab.expectedFunction,
            inputNames = listOf("A", "B", "Cin"),
            outputNames = listOf("SUM"),
            ledIndices = listOf(0)
        )

        assertFalse("User circuit fails verification because A is never inverted", report.isAllPassed)
        assertEquals(4, report.passedCount)
        assertEquals(8, report.totalCount)

        // Mathematical collapse check: verify actual output always equals input A
        for (row in report.rows) {
            val a = row.inputValues[0]
            val actual = row.actualOutputs[0]
            assertEquals("Actual output should strictly equal A due to missing NOT gate on input A", a, actual)
        }
    }

    @Test
    fun testCorrectUserSopCircuit() {
        val project = CircuitShareBytecode.decodeToProject(userShareCode)
        val circuit = BreadboardCircuit()
        ProjectPersistence.deserialize(project, circuit)

        // Identify chips
        val uAnd0 = circuit.placedChips[0]
        val uAnd1 = circuit.placedChips[1]
        val uAnd2 = circuit.placedChips[2]
        val uInv  = circuit.placedChips[3]
        val uOr   = circuit.placedChips[4]

        // 1. Remove the 3 incorrect wires that fed A instead of A' and Cin'
        // Wire 22: Node A2 -> Chip 0 Pin 4
        circuit.wires.removeAll { it.startSocket == 1156 && it.endSocket == uAnd0.getPinSocket(4) }
        // Wire 25: Node A2 -> Chip 0 Pin 9
        circuit.wires.removeAll { it.startSocket == 1157 && it.endSocket == uAnd0.getPinSocket(9) }
        // Wire 38: Node A2 -> Chip 2 Pin 5
        circuit.wires.removeAll { it.startSocket == 1158 && it.endSocket == uAnd2.getPinSocket(5) }

        // Rebuild circuit DSU after wire removal
        val rebuiltCircuit = BreadboardCircuit()
        rebuiltCircuit.masterPower = true
        for (c in circuit.placedChips) {
            rebuiltCircuit.addChip(c.placedIc.partNumber, c.placedIc.trench, c.placedIc.startColumn, c.placedIc.isRotated180)
        }
        for (w in circuit.wires) {
            rebuiltCircuit.addWire(w.startSocket, w.endSocket, w.color, w.isManhattan)
        }

        // 2. Add Inverter Gate 3 for A: SW0 -> uInv Pin 5 (input), Pin 6 (A' output)
        val invA_In = uInv.getPinSocket(5)
        val invA_Out = uInv.getPinSocket(6)
        rebuiltCircuit.addWire(AD200Topology.TERM_SW0, invA_In, WireColor.BLUE)

        // 3. Connect A' to Chip 0 Pin 4 and Chip 0 Pin 9
        rebuiltCircuit.addWire(invA_Out, uAnd0.getPinSocket(4), WireColor.BLUE)
        rebuiltCircuit.addWire(invA_Out, uAnd0.getPinSocket(9), WireColor.BLUE)

        // 4. Connect Cin' (uInv Pin 2) to Chip 2 Pin 5
        val invCin_Out = uInv.getPinSocket(2)
        rebuiltCircuit.addWire(invCin_Out, uAnd2.getPinSocket(5), WireColor.BROWN)

        rebuiltCircuit.step()

        val fullAdderLab = ExperimentCatalog.allLabs.first { it.id == "exp14_full_adder_basic_sum" }
        val report = TestBenchVerifier.verify(
            circuit = rebuiltCircuit,
            switchIndices = listOf(0, 1, 2),
            outputReader = { listOf(rebuiltCircuit.ledValues[0]) },
            expectedFunction = fullAdderLab.expectedFunction,
            inputNames = listOf("A", "B", "Cin"),
            outputNames = listOf("SUM"),
            ledIndices = listOf(0)
        )

        assertTrue("Corrected user SOP circuit must pass all 8 vectors", report.isAllPassed)
        assertEquals(8, report.passedCount)

        val correctedSopShareCode = CircuitShareBytecode.encode(rebuiltCircuit)
        println("Corrected User 4-Term SOP Share Code: $correctedSopShareCode")
        assertTrue(correctedSopShareCode.startsWith(CircuitShareBytecode.PREFIX))

        // Verify that decoding the newly generated share code also passes 8/8
        val decodedProject = CircuitShareBytecode.decodeToProject(correctedSopShareCode)
        val testCircuit = BreadboardCircuit()
        ProjectPersistence.deserialize(decodedProject, testCircuit)
        val decodedReport = TestBenchVerifier.verify(
            circuit = testCircuit,
            switchIndices = listOf(0, 1, 2),
            outputReader = { listOf(testCircuit.ledValues[0]) },
            expectedFunction = fullAdderLab.expectedFunction,
            inputNames = listOf("A", "B", "Cin"),
            outputNames = listOf("SUM"),
            ledIndices = listOf(0)
        )
        assertTrue("Decoded corrected SOP circuit must pass 8/8 vectors", decodedReport.isAllPassed)
        assertEquals(8, decodedReport.passedCount)
    }

    @Test
    fun testArbitrarySwitchesAndArbitraryLedVerification() {
        // Build a valid Full Adder Sum using arbitrary switches (SW5, SW6, SW7) and arbitrary output LED (LED3)
        val circuit = BreadboardCircuit()
        circuit.masterPower = true

        // Wire power rails from console terminals to bus rails
        circuit.addWire(
            AD200Topology.TERM_POWER_VCC,
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0),
            WireColor.RED
        )
        circuit.addWire(
            AD200Topology.TERM_POWER_GND,
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0),
            WireColor.BLACK
        )

        val uInv = circuit.addChip("7404", trench = 1, startColumn = 10)
        val uAnd = circuit.addChip("7408", trench = 1, startColumn = 24)
        val uOr = circuit.addChip("7432", trench = 1, startColumn = 38)

        // Power chips to top rail sockets at distinct columns to prevent socket collision
        circuit.addWire(
            uInv.getPinSocket(14),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10),
            WireColor.RED
        )
        circuit.addWire(
            uInv.getPinSocket(7),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 16),
            WireColor.BLACK
        )
        circuit.addWire(
            uAnd.getPinSocket(14),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 24),
            WireColor.RED
        )
        circuit.addWire(
            uAnd.getPinSocket(7),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 30),
            WireColor.BLACK
        )
        circuit.addWire(
            uOr.getPinSocket(14),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 38),
            WireColor.RED
        )
        circuit.addWire(
            uOr.getPinSocket(7),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 44),
            WireColor.BLACK
        )

        // Arbitrary switch mapping: SW5 = A, SW6 = B, SW7 = Cin
        val swA = AD200Topology.TERM_SW5
        val swB = AD200Topology.TERM_SW6
        val swCin = AD200Topology.TERM_SW7
        val targetLed = AD200Topology.TERM_LED3 // Arbitrary LED3

        // 1. Invert A, B, Cin
        circuit.addWire(swA, uInv.getPinSocket(1), WireColor.YELLOW) // A -> Inv1 in
        circuit.addWire(swB, uInv.getPinSocket(3), WireColor.ORANGE) // B -> Inv2 in
        circuit.addWire(swCin, uInv.getPinSocket(5), WireColor.PURPLE) // Cin -> Inv3 in

        // 2. Stage 1: X = A ⊕ B = A'B + AB'
        circuit.addWire(uInv.getPinSocket(2), uAnd.getPinSocket(1), WireColor.BLUE) // A' -> And1 in1
        circuit.addWire(swB, uAnd.getPinSocket(2), WireColor.ORANGE)                 // B  -> And1 in2
        circuit.addWire(swA, uAnd.getPinSocket(4), WireColor.YELLOW)                 // A  -> And2 in1
        circuit.addWire(uInv.getPinSocket(4), uAnd.getPinSocket(5), WireColor.BLUE) // B' -> And2 in2

        // Or1 forms X = (A'B) + (AB')
        circuit.addWire(uAnd.getPinSocket(3), uOr.getPinSocket(1), WireColor.WHITE)
        circuit.addWire(uAnd.getPinSocket(6), uOr.getPinSocket(2), WireColor.WHITE)

        // 3. Invert X using Inv4 (9 -> 8)
        circuit.addWire(uOr.getPinSocket(3), uInv.getPinSocket(9), WireColor.WHITE)

        // 4. Stage 2: S = X ⊕ Cin = X'·Cin + X·Cin'
        circuit.addWire(uInv.getPinSocket(8), uAnd.getPinSocket(9), WireColor.GRAY)  // X'   -> And3 in1
        circuit.addWire(swCin, uAnd.getPinSocket(10), WireColor.PURPLE)              // Cin  -> And3 in2
        circuit.addWire(uOr.getPinSocket(3), uAnd.getPinSocket(12), WireColor.WHITE) // X    -> And4 in1
        circuit.addWire(uInv.getPinSocket(6), uAnd.getPinSocket(13), WireColor.GRAY) // Cin' -> And4 in2

        // Or2 forms Sum S = (X'·Cin) + (X·Cin') and drives LED3
        circuit.addWire(uAnd.getPinSocket(8), uOr.getPinSocket(4), WireColor.GRAY)
        circuit.addWire(uAnd.getPinSocket(11), uOr.getPinSocket(5), WireColor.GRAY)
        circuit.addWire(uOr.getPinSocket(6), targetLed, WireColor.GREEN)

        // Tie unused inputs low to distinct rail columns
        circuit.addWire(AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 17), uInv.getPinSocket(11), WireColor.BLACK)
        circuit.addWire(AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 18), uInv.getPinSocket(13), WireColor.BLACK)
        circuit.addWire(AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 45), uOr.getPinSocket(9), WireColor.BLACK)
        circuit.addWire(AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 46), uOr.getPinSocket(10), WireColor.BLACK)
        circuit.addWire(AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 47), uOr.getPinSocket(12), WireColor.BLACK)
        circuit.addWire(AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 48), uOr.getPinSocket(13), WireColor.BLACK)

        // Set unused switches SW0, SW1, SW2 to TRUE before verification to verify zero-interference
        circuit.switches[0] = true
        circuit.switches[1] = true
        circuit.switches[2] = true
        circuit.step()

        val connectedSwitches = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_SW0 + it) > 1 }
        val connectedLeds = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_LED0 + it) > 2 }
        assertEquals(listOf(5, 6, 7), connectedSwitches)
        assertEquals(listOf(3), connectedLeds)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = connectedSwitches,
            outputReader = { connectedLeds.map { circuit.ledValues[it] } },
            expectedFunction = { inputs -> listOf(inputs[0] xor inputs[1] xor inputs[2]) },
            inputNames = listOf("A (SW5)", "B (SW6)", "Cin (SW7)"),
            outputNames = listOf("SUM (LED3)"),
            ledIndices = connectedLeds
        )

        assertTrue("Verification must pass all 8 vectors with arbitrary switches and LEDs", report.isAllPassed)
        assertEquals(8, report.passedCount)
        assertEquals(0, report.diagnostics.filter { it.isError }.size)

        // Verify that unused switches (SW0, SW1, SW2) retained their pre-sweep values after test
        assertTrue("SW0 state must be restored", circuit.switches[0])
        assertTrue("SW1 state must be restored", circuit.switches[1])
        assertTrue("SW2 state must be restored", circuit.switches[2])

        // Verify share code encoding
        val shareCode = CircuitShareBytecode.encode(circuit)
        assertTrue(shareCode.startsWith(CircuitShareBytecode.PREFIX))
        println("Generated Valid Arbitrary SW/LED Full Adder Share Code: $shareCode")
    }

    @Test
    fun testGenerateStandardWorkingFullAdderShareCode() {
        // Generate working Full Adder Sum with standard switches SW0, SW1, SW2 and LED0
        val fullAdderLab = ExperimentCatalog.allLabs.first { it.id == "exp14_full_adder_basic_sum" }
        val circuit = BreadboardCircuit()
        fullAdderLab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = fullAdderLab.switchIndices,
            outputReader = { fullAdderLab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = fullAdderLab.expectedFunction,
            inputNames = fullAdderLab.inputLabels,
            outputNames = fullAdderLab.outputLabels,
            ledIndices = fullAdderLab.ledIndices
        )

        assertTrue("Standard Full Adder Sum preset must pass 100%", report.isAllPassed)
        assertEquals(8, report.passedCount)

        val validShareCode = CircuitShareBytecode.encode(circuit)
        println("Standard Working Full Adder Share Code: $validShareCode")
        assertTrue(validShareCode.startsWith(CircuitShareBytecode.PREFIX))
    }

    @Test
    fun testDecodingPriorAttemptShareCodes() {
        val standardCode = "LOGIC-eNoVjCFPgmEUhZ9zX-53v7FRLBDcbP4Ei8FNR2OzGMhSkOicFqZ8m6OwKYXCL7FYKBSKheJ_YVzKs2fnnJ3B4O5WlJsZFFOboh6hS7iILwfi24eCK2-Dlj5On3ovfe-v6RvPqZ37p4ixnlvEk6ZOTDSv0Zt-7JT8umW7bZka7QxtdKjQn5UKq7mvoO-P-Xbto-QDq8gHlrVlO69MC5qAtXWSCzsLrBsvBf79Pfd7_0jufJbceiOOMVMfvg"
        val arbitraryCode = "LOGIC-eNoVi79KgnEUhp_3_Dzf-RBaWnII3LwEl4YgcRNaHLqEClJoyMUhob4CQV1aupIWFxcXlxaX7iU6LQ8P75_RaHClKJfPUExtijqEetCNlQOx9htB39ugjd-mz72TfvSn9J3n1M79RcSDHlvERHMnpmpqNNOX_Sdbt2z3LdNCB0M7_Vbo20qF1VxXMOYj8sWmtkyayrRkEfBpJ8mlnQZ2FrOC3zEUfs9F8pWfZMMx-cYh-c5e_AFR9SAQ"

        // 1. Test standard share code
        val stdProject = CircuitShareBytecode.decodeToProject(standardCode)
        val stdCircuit = BreadboardCircuit()
        ProjectPersistence.deserialize(stdProject, stdCircuit)

        val stdSwitches = (0..7).filter { stdCircuit.dsu.getNetSize(AD200Topology.TERM_SW0 + it) > 1 }
        val stdLeds = (0..7).filter { stdCircuit.dsu.getNetSize(AD200Topology.TERM_LED0 + it) > 2 }
        assertEquals(listOf(0, 1, 2), stdSwitches)
        assertEquals(listOf(0), stdLeds)

        val stdLab = findBestMatchingLab(
            stdCircuit.placedChips.map { it.placedIc.partNumber },
            stdSwitches.size,
            stdLeds.size,
            stdCircuit,
            stdSwitches,
            stdLeds
        )
        assertEquals("exp14_full_adder_basic_sum", stdLab.id)

        val stdReport = TestBenchVerifier.verify(
            circuit = stdCircuit,
            switchIndices = stdSwitches,
            outputReader = { stdLeds.map { stdCircuit.ledValues[it] } },
            expectedFunction = stdLab.expectedFunction,
            ledIndices = stdLeds
        )
        assertTrue("Decoded standard Full Adder share code must pass 100%", stdReport.isAllPassed)
        assertEquals(8, stdReport.passedCount)

        // 2. Test arbitrary switch/LED share code (SW5, SW6, SW7 -> LED3)
        val arbProject = CircuitShareBytecode.decodeToProject(arbitraryCode)
        val arbCircuit = BreadboardCircuit()
        ProjectPersistence.deserialize(arbProject, arbCircuit)

        val arbSwitches = (0..7).filter { arbCircuit.dsu.getNetSize(AD200Topology.TERM_SW0 + it) > 1 }
        val arbLeds = (0..7).filter { arbCircuit.dsu.getNetSize(AD200Topology.TERM_LED0 + it) > 2 }
        assertEquals(listOf(5, 6, 7), arbSwitches)
        assertEquals(listOf(3), arbLeds)

        val arbLab = findBestMatchingLab(
            arbCircuit.placedChips.map { it.placedIc.partNumber },
            arbSwitches.size,
            arbLeds.size,
            arbCircuit,
            arbSwitches,
            arbLeds
        )
        assertEquals("exp14_full_adder_basic_sum", arbLab.id)

        val arbReport = TestBenchVerifier.verify(
            circuit = arbCircuit,
            switchIndices = arbSwitches,
            outputReader = { arbLeds.map { arbCircuit.ledValues[it] } },
            expectedFunction = arbLab.expectedFunction,
            ledIndices = arbLeds
        )
        assertTrue("Decoded arbitrary Full Adder share code must pass 100%", arbReport.isAllPassed)
        assertEquals(8, arbReport.passedCount)
    }

    @Test
    fun testPermutationsForArbitraryWiring() {
        // Build Half Adder: SUM = A ⊕ B, CARRY = A · B
        // Wire inputs: SW7 = A, SW1 = B (reversed/arbitrary)
        // Wire outputs: LED6 = CARRY, LED4 = SUM (arbitrary and non-canonical order)
        val circuit = BreadboardCircuit()
        circuit.masterPower = true

        circuit.addWire(AD200Topology.TERM_POWER_VCC, AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0), WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0), WireColor.BLACK)

        val uXor = circuit.addChip("7486", trench = 1, startColumn = 10)
        val uAnd = circuit.addChip("7408", trench = 1, startColumn = 25)

        circuit.addWire(uXor.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
        circuit.addWire(uXor.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 16), WireColor.BLACK)
        circuit.addWire(uAnd.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 25), WireColor.RED)
        circuit.addWire(uAnd.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 31), WireColor.BLACK)

        val swA = AD200Topology.TERM_SW7
        val swB = AD200Topology.TERM_SW1
        val ledSum = AD200Topology.TERM_LED4
        val ledCarry = AD200Topology.TERM_LED6

        // Inputs to XOR and AND
        circuit.addWire(swA, uXor.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(swB, uXor.getPinSocket(2), WireColor.ORANGE)
        circuit.addWire(swA, uAnd.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(swB, uAnd.getPinSocket(2), WireColor.ORANGE)

        // Outputs
        circuit.addWire(uXor.getPinSocket(3), ledSum, WireColor.GREEN)
        circuit.addWire(uAnd.getPinSocket(3), ledCarry, WireColor.BLUE)

        val connectedSwitches = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_SW0 + it) > 1 }
        val connectedLeds = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_LED0 + it) > 2 }

        assertEquals(listOf(1, 7), connectedSwitches)
        assertEquals(listOf(4, 6), connectedLeds)

        val matchedLab = findBestMatchingLab(
            circuit.placedChips.map { it.placedIc.partNumber },
            connectedSwitches.size,
            connectedLeds.size,
            circuit,
            connectedSwitches,
            connectedLeds
        )
        assertEquals("lab7_half_adder", matchedLab.id)

        // In exp04_half_adder: output 0 is SUM, output 1 is CARRY
        // Notice connectedLeds is [4, 6], where LED4 is SUM and LED6 is CARRY
        // This matches exactly!
        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = connectedSwitches,
            outputReader = { connectedLeds.map { circuit.ledValues[it] } },
            expectedFunction = matchedLab.expectedFunction,
            ledIndices = connectedLeds
        )
        assertTrue("Half Adder with arbitrary switches (SW1, SW7) and LEDs (LED4, LED6) must pass", report.isAllPassed)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testVerifierDiagnosticsAndBounds() {
        val circuit = BreadboardCircuit()

        // 1. Empty switchIndices should be gracefully rejected with diagnostic error, not crash
        val emptyReport = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = emptyList(),
            outputReader = { emptyList() },
            expectedFunction = { emptyList() }
        )
        assertFalse(emptyReport.isAllPassed)
        assertTrue(emptyReport.diagnostics.any { it.message.contains("No input switches") })

        // 2. Out-of-bounds switch or LED index should produce diagnostic error
        val oobReport = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = listOf(99),
            outputReader = { listOf(false) },
            expectedFunction = { listOf(false) },
            ledIndices = listOf(88)
        )
        assertFalse(oobReport.isAllPassed)
        assertTrue(oobReport.diagnostics.any { it.message.contains("Switch index SW99 is out of range") })
        assertTrue(oobReport.diagnostics.any { it.message.contains("LED index LED88 is out of range") })
    }

    @Test
    fun testExtraConnectedSwitchAndLedPermutationDiscovery() {
        // Build valid Full Adder Sum using arbitrary switches (SW5, SW6, SW7) and output LED3
        val fullAdderLab = ExperimentCatalog.allLabs.first { it.id == "exp14_full_adder_basic_sum" }
        val circuit = BreadboardCircuit()
        fullAdderLab.buildCircuit(circuit)

        // Currently lab wires SW0, SW1, SW2 -> LED0
        // Now also wire extra switch SW4 to an unused tied terminal and extra LED LED7
        circuit.addWire(AD200Topology.TERM_SW4, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 55), WireColor.BLACK)
        circuit.addWire(AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 56), AD200Topology.TERM_LED7, WireColor.BLACK)
        circuit.step()

        val connectedSwitches = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_SW0 + it) > 1 }
        val connectedLeds = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_LED0 + it) > 2 }

        assertEquals(listOf(0, 1, 2, 4), connectedSwitches)
        assertEquals(listOf(0, 7), connectedLeds)

        val (bestSw, bestLed, passCount) = discoverBestIoMapping(
            circuit = circuit,
            connectedSwitches = connectedSwitches,
            connectedLeds = connectedLeds,
            requiredSwitchCount = 3,
            requiredLedCount = 1,
            expectedFunction = fullAdderLab.expectedFunction,
            fallbackSwitches = listOf(0, 1, 2),
            fallbackLeds = listOf(0)
        )

        assertEquals("Should select the 3 working switches [0, 1, 2]", listOf(0, 1, 2), bestSw)
        assertEquals("Should select the working output LED [0]", listOf(0), bestLed)
        assertEquals(8, passCount)
    }

    @Test
    fun testFourInputPermutationDiscovery() {
        // Test combination and permutation generator for 4-input logic
        val switches = listOf(1, 3, 5, 7)
        val perms = generatePermutations(switches)
        assertEquals(24, perms.size)
        assertTrue(perms.contains(listOf(7, 5, 3, 1)))
        assertTrue(perms.contains(listOf(1, 7, 3, 5)))

        val allSwitches = listOf(0, 1, 2, 3, 4, 5, 6, 7)
        val combos = generateCombinations(allSwitches, 4)
        assertEquals(70, combos.size)
        assertTrue(combos.contains(listOf(1, 3, 5, 7)))
    }

    @Test
    fun testFindBestMatchingLabWithExtraSwitchesAndLeds() {
        // Circuit with 4 switches and 2 LEDs connected where 3 switches and 1 LED form Full Adder Sum
        val fullAdderLab = ExperimentCatalog.allLabs.first { it.id == "exp14_full_adder_basic_sum" }
        val circuit = BreadboardCircuit()
        fullAdderLab.buildCircuit(circuit)

        circuit.addWire(AD200Topology.TERM_SW5, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 55), WireColor.BLACK)
        circuit.addWire(AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 56), AD200Topology.TERM_LED5, WireColor.BLACK)
        circuit.step()

        val connectedSwitches = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_SW0 + it) > 1 }
        val connectedLeds = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_LED0 + it) > 2 }

        val matched = findBestMatchingLab(
            placedChips = circuit.placedChips.map { it.placedIc.partNumber },
            connectedSwitchCount = connectedSwitches.size,
            connectedLedCount = connectedLeds.size,
            circuit = circuit,
            connectedSwitches = connectedSwitches,
            connectedLeds = connectedLeds
        )

        assertEquals("exp14_full_adder_basic_sum", matched.id)
    }
}
