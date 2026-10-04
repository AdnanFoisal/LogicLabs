package com.logiclabs.app

import com.logiclabs.app.ui.verify.LabRelevanceEvaluator
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.core.data.persistence.CircuitShareBytecode
import com.logiclabs.core.data.persistence.ProjectPersistence
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabRelevanceEvaluatorTest {

    @Test
    fun testEmptyCircuitReturnsNoMatches() {
        val circuit = BreadboardCircuit()
        val results = LabRelevanceEvaluator.evaluateRelevance(circuit, ExperimentCatalog.allLabs)
        assertTrue("Empty circuit must return empty candidate list", results.isEmpty())
    }

    @Test
    fun testFullAdderSumMatchesAt100Percent() {
        // Standard Full Adder Sum lab build
        val fullAdderLab = ExperimentCatalog.allLabs.first { it.id == "exp14_full_adder_basic_sum" }
        val circuit = BreadboardCircuit()
        fullAdderLab.buildCircuit(circuit)

        val results = LabRelevanceEvaluator.evaluateRelevance(circuit, ExperimentCatalog.allLabs)
        assertTrue("Results should not be empty", results.isNotEmpty())

        val topMatch = results.first()
        assertEquals("Top match must be Full Adder Sum", "exp14_full_adder_basic_sum", topMatch.lab.id)
        assertEquals("Total score must be 100%", 100, topMatch.totalScorePercent)
        assertEquals("Functional score must be 100%", 100, topMatch.functionalScorePercent)
        assertEquals("IC score must be 100%", 100, topMatch.icScorePercent)
        assertEquals(8, topMatch.matchingVectors)
        assertEquals(8, topMatch.totalVectors)
        assertTrue("Required ICs should be matched", topMatch.matchedIcs.containsAll(listOf("7404", "7408", "7432")))
    }

    @Test
    fun testCorrectedUserSopFullAdderScores100Percent() {
        // Corrected 4-term SOP share code
        val fixedShareCode = "LOGIC-eNoVj69Lg3EQhz_3i6_f6RgYTMKyKJbB0CDCDBN50TCDIBZh6IYgyKsyMCiKRXBrGgSxrFgGMrD4X_gjWCwWizAEnckz3MMH7o7nLknmSgSZPQRMKAOhvFcRzCMIPA5MhHPLCkLTahGWQ1FgdWpFcM9GBbi2MYGs2rSAhm1ewFvWjtCuXUZQ3048J9aICHV5dNbk2-f7OuV81knvzmgq0IJWPVdpx7lL-27c1EGGDumxc49Off4HC84neo2QX3pz4zt9-G6WPhn0pRfOA9wpNId7Bh70CqAzXgTkhSt-84reDgDLvGbQG9lW8Ia2_f9GSAO0Iyn-vSVmLdORe8tY8lzg9ch_IWwvvA"
        val project = CircuitShareBytecode.decodeToProject(fixedShareCode)
        val circuit = BreadboardCircuit()
        ProjectPersistence.deserialize(project, circuit)

        val results = LabRelevanceEvaluator.evaluateRelevance(circuit, ExperimentCatalog.allLabs)
        assertTrue("Results should not be empty", results.isNotEmpty())

        val topMatch = results.first()
        assertEquals("Top match must be Full Adder Sum", "exp14_full_adder_basic_sum", topMatch.lab.id)
        assertEquals("Total score must be 100%", 100, topMatch.totalScorePercent)
        assertEquals(8, topMatch.matchingVectors)
        assertEquals(8, topMatch.totalVectors)
    }

    @Test
    fun testArbitrarySwitchesAndLedsFullAdderRelevance() {
        // Full Adder Sum using arbitrary switches SW5, SW6, SW7 and LED3
        val arbitraryShareCode = "LOGIC-eNoVi79KgnEUhp_3_Dzf-RBaWnII3LwEl4YgcRNaHLqEClJoyMUhob4CQV1aupIWFxcXlxaX7iU6LQ8P75_RaHClKJfPUExtijqEetCNlQOx9htB39ugjd-mz72TfvSn9J3n1M79RcSDHlvERHMnpmpqNNOX_Sdbt2z3LdNCB0M7_Vbo20qF1VxXMOYj8sWmtkyayrRkEfBpJ8mlnQZ2FrOC3zEUfs9F8pWfZMMx-cYh-c5e_AFR9SAQ"
        val project = CircuitShareBytecode.decodeToProject(arbitraryShareCode)
        val circuit = BreadboardCircuit()
        ProjectPersistence.deserialize(project, circuit)

        val results = LabRelevanceEvaluator.evaluateRelevance(circuit, ExperimentCatalog.allLabs)
        assertTrue("Results should not be empty", results.isNotEmpty())

        val topMatch = results.first()
        assertEquals("exp14_full_adder_basic_sum", topMatch.lab.id)
        assertEquals(100, topMatch.totalScorePercent)
        assertEquals(listOf(5, 6, 7), topMatch.effectiveSwitches)
        assertEquals(listOf(3), topMatch.effectiveLeds)
    }

    @Test
    fun testPartialWiringScoresProportionally() {
        // User's original circuit where A was un-inverted (giving 4/8 vectors = 50% functional)
        val userOriginalCode = "LOGIC-eNoNkK1Lg2EUxc_94tmjjoHBNFgeA4swNIigReRFwxYGwyIM3RAEeVUGBsW5IuiahoFYViyCCBb_Cz-CxWKxCCLoTN5wfxzuPZdzuUmyME-QuQPAhEYgVPAqg3kCgUtAKZxaVhDOrBlhOZQF1qJeBH9ZXoBLKwqkbjMCGrdFAW_aIELv7CKChtZxnVg7IrTk0dmUH_cPddr5rJM-ndVUoFPacN2gbecO7Xniho4ydEyPnB06dO5S17d-seT-Y6ww5IleI-SP3jz9nT68n6VPBn3ruXMftwrN4Z6BB-0DdMLLgLxwxe-v6XUGqPKqQbu85s4r2VLwug78I-2QBuiNpMA_0Ucvxw"
        val project = CircuitShareBytecode.decodeToProject(userOriginalCode)
        val circuit = BreadboardCircuit()
        ProjectPersistence.deserialize(project, circuit)

        val results = LabRelevanceEvaluator.evaluateRelevance(circuit, ExperimentCatalog.allLabs, limit = 10)
        assertTrue(results.isNotEmpty())

        val fullAdderResult = results.firstOrNull { it.lab.id == "exp14_full_adder_basic_sum" }
        assertTrue("Full Adder Sum should be among candidates", fullAdderResult != null)
        fullAdderResult!!

        // Functional = 50% (4/8), IC = 100% (7408, 7404, 7432) -> Total = 50*0.75 + 100*0.25 = 62.5 -> 63%
        assertEquals(4, fullAdderResult.matchingVectors)
        assertEquals(8, fullAdderResult.totalVectors)
        assertEquals(50, fullAdderResult.functionalScorePercent)
        assertEquals(100, fullAdderResult.icScorePercent)
        assertEquals(63, fullAdderResult.totalScorePercent)
    }

    @Test
    fun testHalfAdderScores100Percent() {
        // Half Adder: 7486 (XOR) and 7408 (AND)
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

        val swA = AD200Topology.TERM_SW0
        val swB = AD200Topology.TERM_SW1
        val ledSum = AD200Topology.TERM_LED0
        val ledCarry = AD200Topology.TERM_LED1

        circuit.addWire(swA, uXor.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(swB, uXor.getPinSocket(2), WireColor.ORANGE)
        circuit.addWire(swA, uAnd.getPinSocket(1), WireColor.YELLOW)
        circuit.addWire(swB, uAnd.getPinSocket(2), WireColor.ORANGE)

        circuit.addWire(uXor.getPinSocket(3), ledSum, WireColor.GREEN)
        circuit.addWire(uAnd.getPinSocket(3), ledCarry, WireColor.BLUE)

        val results = LabRelevanceEvaluator.evaluateRelevance(circuit, ExperimentCatalog.allLabs)
        assertTrue(results.isNotEmpty())

        val topMatch = results.first()
        assertTrue("Top match should be a Half Adder lab", topMatch.lab.id in listOf("lab7_half_adder", "exp04_half_adder"))
        assertEquals(100, topMatch.totalScorePercent)
        assertEquals(4, topMatch.matchingVectors)
        assertEquals(4, topMatch.totalVectors)
    }

    @Test
    fun testUnconnectedArbitraryGateScoresLowOrZero() {
        // Place just a 7476 (Dual JK Flip-Flop) with no inputs/outputs connected
        val circuit = BreadboardCircuit()
        circuit.masterPower = true
        circuit.addChip("7476", trench = 1, startColumn = 10)

        val results = LabRelevanceEvaluator.evaluateRelevance(circuit, ExperimentCatalog.allLabs)
        // Without any connected switches or LEDs, the score should not meet 35%
        assertTrue("Unconnected circuit should produce no candidates exceeding threshold", results.isEmpty())
    }

    @Test
    fun testSnapshotIsolation() {
        val original = BreadboardCircuit()
        original.masterPower = true
        original.addChip("7408", trench = 1, startColumn = 10)
        original.addWire(AD200Topology.TERM_SW0, AD200Topology.terminalSocket(0, 10, 0), WireColor.RED)
        original.switches[0] = true
        original.step()

        val snapshot = original.snapshot()
        assertEquals(1, snapshot.placedChips.size)
        assertEquals(1, snapshot.wires.size)
        assertTrue(snapshot.switches[0])

        // Mutate snapshot
        snapshot.switches[0] = false
        snapshot.addChip("7404", trench = 1, startColumn = 20)
        snapshot.step()

        // Verify original is completely unaffected
        assertEquals(1, original.placedChips.size)
        assertTrue(original.switches[0])
        assertEquals("7408", original.placedChips[0].placedIc.partNumber)
    }

    @Test
    fun testEvaluationOnSnapshotDoesNotMutateOriginalCircuit() {
        val fullAdderLab = ExperimentCatalog.allLabs.first { it.id == "exp14_full_adder_basic_sum" }
        val original = BreadboardCircuit()
        fullAdderLab.buildCircuit(original)
        original.switches[0] = true
        original.switches[1] = false
        original.switches[2] = true
        original.step()

        val origSwitchesCopy = original.switches.copyOf()
        val origWireCount = original.wires.size
        val origChipCount = original.placedChips.size

        val snapshot = original.snapshot()
        val results = LabRelevanceEvaluator.evaluateRelevance(snapshot, ExperimentCatalog.allLabs)
        assertTrue(results.isNotEmpty())

        // Verify original circuit state is strictly preserved
        assertEquals(origChipCount, original.placedChips.size)
        assertEquals(origWireCount, original.wires.size)
        for (i in 0..7) {
            assertEquals("Switch $i must not be altered by snapshot evaluation", origSwitchesCopy[i], original.switches[i])
        }
    }
}

