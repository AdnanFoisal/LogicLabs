package com.logiclabs.feature.tools

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.feature.tools.courseware.LabCurriculum
import com.logiclabs.feature.tools.testbench.TestBenchVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine-preservation gate for the UI overhaul.
 *
 * The view layer was rewritten around the simulation engine; nothing inside it was meant
 * to change. Correct pass/fail outcomes alone would not prove that — a subtly different
 * netlist can still satisfy a truth table. The HMAC seal is the sharper instrument: it is
 * computed over the ordered vector payload, so *any* drift in sweep order, socket
 * resolution, LED mapping or chip evaluation changes the digest.
 *
 * So these hashes are pinned. They were captured from the engine as it stands and are
 * checked byte for byte.
 *
 * **If one of these fails, do not update the constant.** A changed digest means the
 * simulation now produces a different sequence of results than it did, and the change
 * that caused it is the bug. The only legitimate reason to re-baseline is a deliberate,
 * reviewed change to `TestBenchVerifier`'s payload format or to a lab's definition.
 */
class LabPresetIntegrityTest {

    /** Where each lab's ICs must land: lab id -> (part, trench, startColumn) triples. */
    private val expectedPlacements: Map<String, List<Triple<String, Int, Int>>> = mapOf(
        "lab1_not" to listOf(Triple("7404", 1, 10)),
        "lab2_nand" to listOf(Triple("7400", 1, 10)),
        "lab3_nor" to listOf(Triple("7402", 1, 10)),
        "lab4_and" to listOf(Triple("7408", 1, 10)),
        "lab5_or" to listOf(Triple("7432", 1, 10)),
        "lab6_xor" to listOf(Triple("7486", 1, 10)),
        "lab7_half_adder" to listOf(Triple("7486", 1, 10), Triple("7408", 1, 24)),
        "lab8_nand_and" to listOf(Triple("7400", 1, 10)),
        "lab9_nand_or" to listOf(Triple("7400", 1, 10)),
        "lab10_sr_latch" to listOf(Triple("7400", 1, 10)),
        "lab11_d_flipflop" to listOf(Triple("7474", 1, 10)),
        "lab12_full_adder" to listOf(Triple("7483", 1, 12))
    )

    @Test
    fun everyLabPlacesItsIcsAtTheExpectedTrenchAndColumn() {
        assertEquals(12, LabCurriculum.classicLabs.size)

        for (lab in LabCurriculum.classicLabs) {
            val circuit = BreadboardCircuit()
            lab.buildCircuit(circuit)

            val actual = circuit.placedChips.map {
                Triple(it.placedIc.partNumber, it.placedIc.trench, it.placedIc.startColumn)
            }
            val expected = expectedPlacements.getValue(lab.id)

            assertEquals("${lab.id}: IC placement drifted", expected, actual)
        }
    }

    @Test
    fun everyLabWiresSocketsAndReportsAStableCensus() {
        for (lab in LabCurriculum.classicLabs) {
            val circuit = BreadboardCircuit()
            lab.buildCircuit(circuit)

            assertTrue("${lab.id}: preset placed no wires", circuit.wires.isNotEmpty())

            // Every wire endpoint must be a real addressable node, and no wire may short a
            // socket to itself — either would mean the preset's socket arithmetic moved.
            //
            // The bound is TOTAL_ACTIVE_SOCKETS (1948), not TOTAL_BREADBOARD_SOCKETS (1896):
            // ids 0..1895 are phenolic tie-points, 1896..1947 are the console terminals
            // (TERM_SW0 = 1896, TERM_LED0 = 1908, …), and everything above is DSU headroom.
            // Both spaces are legitimate endpoints — a lab that wired only tie-points would
            // have nothing driving it and nothing to read.
            for (w in circuit.wires) {
                assertTrue(
                    "${lab.id}: wire ${w.id} start socket ${w.startSocket} out of range",
                    w.startSocket in 0 until AD200Topology.TOTAL_ACTIVE_SOCKETS
                )
                assertTrue(
                    "${lab.id}: wire ${w.id} end socket ${w.endSocket} out of range",
                    w.endSocket in 0 until AD200Topology.TOTAL_ACTIVE_SOCKETS
                )
                assertTrue(
                    "${lab.id}: wire ${w.id} shorts a socket to itself",
                    w.startSocket != w.endSocket
                )
            }

            // Every lab must reach the console in both directions, or its truth-table sweep
            // is driving and reading nothing. Checked as a property rather than a socket
            // list so a preset may be re-routed without touching this test.
            val terminals = circuit.wires
                .flatMap { listOf(it.startSocket, it.endSocket) }
                .filter { it >= AD200Topology.TOTAL_BREADBOARD_SOCKETS }
            assertTrue(
                "${lab.id}: preset wires no console terminal, so nothing can drive or read it",
                terminals.isNotEmpty()
            )
            assertTrue(
                "${lab.id}: preset wires no data switch (TERM_SW0..SW7)",
                terminals.any { it in AD200Topology.TERM_SW0..AD200Topology.TERM_SW7 }
            )
            assertTrue(
                "${lab.id}: preset wires no logic-indicator LED",
                terminals.any {
                    it in AD200Topology.TERM_LED0..AD200Topology.TERM_LED7 ||
                        it in AD200Topology.TERM_BOT_LED0..AD200Topology.TERM_BOT_LED7
                }
            )
        }
    }

    @Test
    fun sealedHashesAreByteIdenticalToTheRecordedBaseline() {
        val drifted = mutableListOf<String>()

        for (lab in LabCurriculum.classicLabs) {
            val expected = baselineHashes[lab.id] ?: continue

            val circuit = BreadboardCircuit()
            lab.buildCircuit(circuit)

            val report = TestBenchVerifier.verify(
                circuit = circuit,
                switchIndices = lab.switchIndices,
                outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
                expectedFunction = lab.expectedFunction,
                inputNames = lab.inputLabels,
                outputNames = lab.outputLabels,
                experimentTitle = lab.title
            )

            if (report.hmacSha256Hash != expected) {
                drifted += "${lab.id}\n    expected $expected\n    actual   ${report.hmacSha256Hash}"
            }
        }

        assertTrue(
            "Provenance seals changed — the simulation now produces different results.\n" +
                "Do not re-baseline; find what changed.\n" + drifted.joinToString("\n"),
            drifted.isEmpty()
        )
    }

    @Test
    fun sealingIsDeterministicAcrossRuns() {
        // Two independent builds of the same lab must seal identically. Catches any
        // ordering that depends on hash iteration or object identity rather than the
        // board — the class of bug a single-run baseline would not see.
        for (lab in LabCurriculum.classicLabs) {
            val hashes = (0 until 2).map {
                val circuit = BreadboardCircuit()
                lab.buildCircuit(circuit)
                TestBenchVerifier.verify(
                    circuit = circuit,
                    switchIndices = lab.switchIndices,
                    outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
                    expectedFunction = lab.expectedFunction,
                    inputNames = lab.inputLabels,
                    outputNames = lab.outputLabels,
                    experimentTitle = lab.title
                ).hmacSha256Hash
            }
            assertEquals("${lab.id}: seal is not deterministic", hashes[0], hashes[1])
        }
    }

    @Test
    fun everyClassicLabPassesItsOwnTruthTableThroughTheRealEngine() {
        // The sealed-digest test above cannot catch this, and that is not a theoretical gap: the
        // digest covers the sweep's *actual* outputs, so a preset whose declared `expectedFunction`
        // disagrees with its own circuit still seals byte-perfectly and still passes. Two of the
        // twelve shipped exactly that way — lab10_sr_latch reported the Set state for the forbidden
        // ~S=~R=0 row, and lab11_d_flipflop described a transparent latch where the 7474 is
        // positive-edge triggered — grading 2/4 and 3/4 in the app while this file stayed green.
        //
        // So the twelve get the same property check the extended presets already had: sweep the
        // preset with the verifier the app uses, on a real BreadboardCircuit, and require every
        // vector to match. This is an added assertion on top of the seal, not a replacement for it
        // — the digest still catches simulation drift, which this cannot see.
        val failures = mutableListOf<String>()

        for (lab in LabCurriculum.classicLabs) {
            val circuit = BreadboardCircuit()
            lab.buildCircuit(circuit)

            val report = TestBenchVerifier.verify(
                circuit = circuit,
                switchIndices = lab.switchIndices,
                outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
                expectedFunction = lab.expectedFunction,
                inputNames = lab.inputLabels,
                outputNames = lab.outputLabels,
                experimentTitle = lab.title
            )

            if (!report.isAllPassed) {
                val bits = { bs: List<Boolean> -> bs.joinToString("") { if (it) "1" else "0" } }
                val detail = report.rows.filterNot { it.isPassed }.joinToString("\n") { row ->
                    "        IN ${bits(row.inputValues)}  " +
                        "expected ${bits(row.expectedOutputs)}  actual ${bits(row.actualOutputs)}"
                }
                failures += "    ${lab.id} (${lab.subtitle}) — " +
                    "${report.passedCount}/${report.totalCount} vectors\n$detail"
            }
        }

        assertTrue(
            "Classic labs disagreed with their own declared truth tables:\n" +
                failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    private companion object {
        /**
         * Recorded from the engine at the start of the UI overhaul. See the class KDoc
         * before touching any of these.
         */
        val baselineHashes: Map<String, String> = mapOf(
            "lab1_not" to
                "fd549071cc87ea4636a571efcbe9c3087616a4e890eabd46695562dcb046651e",
            "lab2_nand" to
                "545392990d2f09e4cadcef1d511428dd43bdc9aa7f69f1c79b855043413d36d6",
            "lab3_nor" to
                "2ffa59b5a18f03fead9767eb1a20466a28b75badbd50729a1af45d3f65787cf2",
            "lab4_and" to
                "582e02ac83897829924f5346eee2f505377b1530a12b7689b3307dfc82dddc72",
            "lab5_or" to
                "6eb81194379063eb55c1d9525ebc57779e74c39ab9331c73538cba0dd92108b5",
            "lab6_xor" to
                "05b771d4386023bba244c76b9c2d2f3364a8c98fb643486264d5d0218a6529ff",
            "lab7_half_adder" to
                "3ab3bb2122375299ecb0ca22bc64fc74b234bd03c17d277a71e9d660138a898f",
            "lab8_nand_and" to
                "582e02ac83897829924f5346eee2f505377b1530a12b7689b3307dfc82dddc72",
            "lab9_nand_or" to
                "6eb81194379063eb55c1d9525ebc57779e74c39ab9331c73538cba0dd92108b5",
            "lab10_sr_latch" to
                "853aeb13a213762162caf60304f039560d14ca28c39caf04da3a8f5fa5ba84f7",
            "lab11_d_flipflop" to
                "2bb5befc54d55af28f7a27015b05200cb62d2851c35fc1bd0691717b3389bff3",
            "lab12_full_adder" to
                "3ab3bb2122375299ecb0ca22bc64fc74b234bd03c17d277a71e9d660138a898f"
        )
    }
}
