package com.logiclabs.feature.tools

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.ElectricalLevel
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import com.logiclabs.feature.tools.courseware.LabCurriculum
import com.logiclabs.feature.tools.courseware.LabExperiment
import com.logiclabs.feature.tools.testbench.TestBenchVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural and functional coverage for the extended experiments — Experiments 01-12 and their
 * variations, i.e. everything in [ExperimentCatalog.extendedLabs].
 *
 * [LabPresetIntegrityTest] guards the twelve sealed classic labs by pinning their HMAC digests.
 * That approach is deliberately *not* copied here. A pinned hash is the right instrument for a
 * frozen preset whose only permitted change is "none"; these presets are new, are expected to be
 * re-routed as the board's silkscreen and column budget evolve, and pinning them would turn every
 * legitimate re-route into a red test with a tempting one-line "fix".
 *
 * So the extended presets are checked by *property* instead: whatever route a preset takes, its
 * sockets must be addressable, its switches and lamps must actually reach the console, its packages
 * must be powered, its footprints must not overlap, and — the one that matters most — sweeping it
 * through the real [TestBenchVerifier] must pass every vector. That last assertion is what the
 * out-of-engine Python sweep could not give: it exercises `BreadboardCircuit`'s own relaxation
 * solver and LED resolution rather than a reimplementation of them.
 *
 * Nothing here reads [LabCurriculum.classicLabs] except [extendedLabsDoNotDisturbTheSealedTwelve],
 * which asserts the two sets stay disjoint — so the engine-preservation gate keeps its meaning.
 */
class ExtendedPresetIntegrityTest {

    /** Fresh board with the preset built onto it. Every test starts from one of these. */
    private fun build(lab: LabExperiment): BreadboardCircuit =
        BreadboardCircuit().also { lab.buildCircuit(it) }

    private val extendedLabs: List<LabExperiment> get() = ExperimentCatalog.extendedLabs

    @Test
    fun theCatalogExposesEveryExtendedExperimentExactlyOnce() {
        assertTrue("extendedLabs is empty — the catalog lost its wiring", extendedLabs.isNotEmpty())

        val duplicateIds = extendedLabs.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        assertTrue("Duplicate extended lab ids: ${duplicateIds.keys}", duplicateIds.isEmpty())

        // Every extended preset must carry an expNN_ id, because that prefix is what
        // ExperimentCatalog.groupKeyOf folds variations on. An id without it silently becomes its
        // own single-variation group and the picker disappears.
        for (lab in extendedLabs) {
            assertTrue(
                "${lab.id}: extended presets must be named expNN_<build> so variations group",
                Regex("^exp\\d\\d_[a-z0-9_]+$").matches(lab.id)
            )
        }

        // All eighteen course experiments are present. The course sheet originally numbered
        // 01..14; experiments 15-18 (XNOR, buffer, the 7420 wide NAND and the 7448 decoder)
        // were added to close the gate-coverage gap against the sandbox catalog, and this
        // range is the course sheet's new table of contents.
        val prefixes = extendedLabs.map { it.id.substringBefore('_') }.toSortedSet()
        assertEquals(
            "The extended catalog is missing or has gained an experiment",
            (1..18).map { "exp%02d".format(it) }.toSortedSet(),
            prefixes
        )
    }

    @Test
    fun variationsOfOneExperimentShareATitleAndDifferInSubtitle() {
        // The catalog UI folds a group under one card and distinguishes the builds by subtitle,
        // so a group whose titles disagree draws two cards, and one whose subtitles agree draws a
        // picker with two identical-looking chips.
        for ((prefix, variations) in extendedLabs.groupBy { it.id.substringBefore('_') }) {
            val titles = variations.map { it.title }.distinct()
            assertEquals("$prefix: variations must share one title, got $titles", 1, titles.size)

            val subtitles = variations.map { it.subtitle }
            assertEquals(
                "$prefix: variation subtitles must be distinct, got $subtitles",
                subtitles.size,
                subtitles.distinct().size
            )
        }
    }

    @Test
    fun everyPresetDeclaresACoherentIoContract() {
        for (lab in extendedLabs) {
            assertTrue("${lab.id}: declares no input switches", lab.switchIndices.isNotEmpty())
            assertTrue("${lab.id}: declares no output LEDs", lab.ledIndices.isNotEmpty())

            assertEquals(
                "${lab.id}: inputLabels must describe exactly the swept switches",
                lab.switchIndices.size,
                lab.inputLabels.size
            )
            assertEquals(
                "${lab.id}: outputLabels must describe exactly the read LEDs",
                lab.ledIndices.size,
                lab.outputLabels.size
            )

            assertEquals(
                "${lab.id}: switchIndices must be distinct",
                lab.switchIndices.size,
                lab.switchIndices.distinct().size
            )
            assertTrue(
                "${lab.id}: switchIndices out of range 0..7: ${lab.switchIndices}",
                lab.switchIndices.all { it in 0..7 }
            )

            // TestBenchVerifier checks LED connectivity over `0 until outputNames.size` — it reads
            // TERM_LED0 + i for that range regardless of what ledIndices says. So a preset that
            // reads, say, LED2 and LED3 would be graded on LED0 and LED1 and fail with a
            // "floating" diagnostic it cannot fix by rewiring. The contract is therefore that
            // ledIndices is exactly 0 until outputLabels.size, contiguous from LED0.
            assertEquals(
                "${lab.id}: ledIndices must be contiguous from LED0 — see TestBenchVerifier",
                (0 until lab.outputLabels.size).toList(),
                lab.ledIndices
            )

            // The sweep is 2^n vectors; the verifier clamps n to 8, so a preset declaring more
            // switches than that would be silently under-tested.
            assertTrue(
                "${lab.id}: ${lab.switchIndices.size} switches exceeds the verifier's 8-bit sweep",
                lab.switchIndices.size <= 8
            )

            assertTrue("${lab.id}: names no target chip", lab.targetChips.isNotEmpty())
            assertTrue("${lab.id}: has no objectives", lab.objectives.isNotEmpty())
            assertEquals(
                "${lab.id}: objective ids must be distinct",
                lab.objectives.size,
                lab.objectives.map { it.id }.distinct().size
            )
        }
    }

    @Test
    fun expectedFunctionIsTotalAndCorrectlyShapedOverItsWholeInputSpace() {
        // Called for every vector the verifier will drive, so a preset whose lambda indexes past
        // its input list or returns the wrong arity fails here with a legible message rather than
        // as an opaque row mismatch inside the sweep.
        for (lab in extendedLabs) {
            val n = lab.switchIndices.size
            for (v in 0 until (1 shl n)) {
                val inputs = (0 until n).map { i -> ((v shr (n - 1 - i)) and 1) == 1 }
                val outputs = try {
                    lab.expectedFunction(inputs)
                } catch (t: Throwable) {
                    throw AssertionError("${lab.id}: expectedFunction threw on $inputs", t)
                }
                assertEquals(
                    "${lab.id}: expectedFunction returned ${outputs.size} outputs for $inputs, " +
                        "but the preset declares ${lab.ledIndices.size}",
                    lab.ledIndices.size,
                    outputs.size
                )
            }
        }
    }

    @Test
    fun everyPresetWiresOnlyAddressableSocketsAndNeverShortsOneToItself() {
        for (lab in extendedLabs) {
            val circuit = build(lab)

            assertTrue("${lab.id}: preset placed no wires", circuit.wires.isNotEmpty())

            // The bound is TOTAL_ACTIVE_SOCKETS (1948): 0..1895 are phenolic tie-points and
            // 1896..1947 are the console terminals. Anything above is DSU headroom and would not
            // resolve to a drawable, touchable socket.
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
                    "${lab.id}: wire ${w.id} shorts socket ${w.startSocket} to itself",
                    w.startSocket != w.endSocket
                )
            }

            // Experiments 01 and 12 place real resistors for their analog networks. Those are
            // inert in the netlist (rebuildNetlist only unions passives below 100 Ohms) but they
            // are drawn, so their endpoints must be addressable for the same reason wires' are.
            for (p in circuit.passives) {
                assertTrue(
                    "${lab.id}: passive ${p.id} socket ${p.socketA} out of range",
                    p.socketA in 0 until AD200Topology.TOTAL_ACTIVE_SOCKETS
                )
                assertTrue(
                    "${lab.id}: passive ${p.id} socket ${p.socketB} out of range",
                    p.socketB in 0 until AD200Topology.TOTAL_ACTIVE_SOCKETS
                )
                assertTrue(
                    "${lab.id}: passive ${p.id} spans zero length",
                    p.socketA != p.socketB
                )
            }
        }
    }

    @Test
    fun everyDeclaredSwitchAndLampActuallyReachesTheBoard() {
        // The same two connectivity conditions TestBenchVerifier raises error diagnostics for,
        // asserted directly so a failure names the switch or lamp rather than surfacing as every
        // row of the truth table being marked failed.
        for (lab in extendedLabs) {
            val circuit = build(lab)

            for (swIdx in lab.switchIndices) {
                val socket = AD200Topology.TERM_SW0 + swIdx
                assertTrue(
                    "${lab.id}: SW$swIdx is declared as an input but is wired to nothing",
                    circuit.dsu.getNetSize(socket) > 1
                )
            }

            for (ledIdx in lab.outputLabels.indices) {
                val socket = AD200Topology.TERM_LED0 + ledIdx
                assertTrue(
                    "${lab.id}: LED$ledIdx is declared as an output but floats",
                    circuit.dsu.getNetSize(socket) > 2
                )
            }
        }
    }

    @Test
    fun everyPackageIsPoweredAndNoTwoFootprintsOverlap() {
        for (lab in extendedLabs) {
            val circuit = build(lab)

            assertTrue("${lab.id}: preset placed no ICs", circuit.placedChips.isNotEmpty())
            assertTrue("${lab.id}: preset left master power off", circuit.masterPower)

            // Placement must match what the lab advertises, or the catalog card lists parts the
            // student will not find on the board.
            val placedParts = circuit.placedChips.map { it.placedIc.partNumber }.toSortedSet()
            assertEquals(
                "${lab.id}: targetChips disagrees with what buildCircuit placed",
                lab.targetChips.toSortedSet(),
                placedParts
            )

            circuit.step()

            for (chip in circuit.placedChips) {
                val part = chip.placedIc.partNumber
                assertEquals(
                    "${lab.id}: $part VCC (pin ${chip.model.vccPin}) is not at +5V",
                    ElectricalLevel.HIGH,
                    circuit.getSocketLevel(chip.getPinSocket(chip.model.vccPin))
                )
                assertEquals(
                    "${lab.id}: $part GND (pin ${chip.model.gndPin}) is not at 0V",
                    ElectricalLevel.LOW,
                    circuit.getSocketLevel(chip.getPinSocket(chip.model.gndPin))
                )
            }

            // A DIP occupies pinCount/2 columns in its trench. Two packages sharing a column
            // would share tie-points, so their pins would be shorted together — a build that
            // could pass its truth table by accident while being physically impossible.
            val spans = circuit.placedChips.map { chip ->
                val width = chip.model.pinCount / 2
                Triple(chip.placedIc.trench, chip.placedIc.startColumn, width)
            }
            for (i in spans.indices) {
                for (j in i + 1 until spans.size) {
                    val (trenchA, colA, widthA) = spans[i]
                    val (trenchB, colB, widthB) = spans[j]
                    if (trenchA != trenchB) continue
                    val overlaps = colA < colB + widthB && colB < colA + widthA
                    assertTrue(
                        "${lab.id}: two packages overlap in trench $trenchA — " +
                            "columns $colA..${colA + widthA - 1} and $colB..${colB + widthB - 1}",
                        !overlaps
                    )
                }
            }
        }
    }

    @Test
    fun noPresetIsBornWithAnErrorDiagnostic() {
        // Separated from the sweep because a single error diagnostic marks *every* row failed —
        // so without this, a power-wiring slip and a genuine logic error look identical.
        for (lab in extendedLabs) {
            val circuit = build(lab)

            val report = TestBenchVerifier.verify(
                circuit = circuit,
                switchIndices = lab.switchIndices,
                outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
                expectedFunction = lab.expectedFunction,
                inputNames = lab.inputLabels,
                outputNames = lab.outputLabels,
                experimentTitle = lab.title
            )

            val errors = report.diagnostics.filter { it.isError }
            assertTrue(
                "${lab.id}: built with ${errors.size} error diagnostic(s):\n" +
                    errors.joinToString("\n") { "    ${it.message}" },
                errors.isEmpty()
            )
        }
    }

    @Test
    fun everyPresetPassesItsOwnTruthTableThroughTheRealEngine() {
        // The assertion this whole file exists for. Each preset is swept by the same verifier the
        // app uses, against the same expectedFunction the catalog ships, on a real
        // BreadboardCircuit — so the relaxation solver, the DSU net resolution and the LED
        // readback are all in the loop.
        val failures = mutableListOf<String>()

        for (lab in extendedLabs) {
            val circuit = build(lab)

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
                val detail = report.rows.filterNot { it.isPassed }.joinToString("\n") { row ->
                    val bits = { bs: List<Boolean> -> bs.joinToString("") { if (it) "1" else "0" } }
                    "        IN ${bits(row.inputValues)}  " +
                        "expected ${bits(row.expectedOutputs)}  actual ${bits(row.actualOutputs)}"
                }
                failures += "    ${lab.id} (${lab.subtitle}) — " +
                    "${report.passedCount}/${report.totalCount} vectors\n$detail"
            }
        }

        assertTrue(
            "Extended presets failed their own truth tables:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }

    @Test
    fun sweepingAPresetLeavesTheBoardAsItFoundIt() {
        // The verifier saves and restores circuit.switches around its sweep. A preset that is
        // graded and then handed back to the user must be in the state its buildCircuit left it
        // in, or the board the student sees does not match the one that was just certified.
        for (lab in extendedLabs) {
            val circuit = build(lab)
            val before = circuit.switches.copyOf()

            TestBenchVerifier.verify(
                circuit = circuit,
                switchIndices = lab.switchIndices,
                outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
                expectedFunction = lab.expectedFunction,
                inputNames = lab.inputLabels,
                outputNames = lab.outputLabels,
                experimentTitle = lab.title
            )

            assertEquals(
                "${lab.id}: switch state was not restored after the sweep",
                before.toList(),
                circuit.switches.toList()
            )
        }
    }

    @Test
    fun buildingAPresetTwiceProducesTheSameBoardAndTheSameSeal() {
        // Catches any preset whose buildCircuit depends on iteration order, object identity or a
        // stale captured value — the class of defect a single build cannot see.
        for (lab in extendedLabs) {
            val seals = (0 until 2).map {
                val circuit = build(lab)
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
            assertEquals("${lab.id}: seal is not reproducible", seals[0], seals[1])
        }
    }

    @Test
    fun extendedLabsDoNotDisturbTheSealedTwelve() {
        // The whole point of keeping the extended presets in their own lists. If an extended id
        // ever collided with a classic one, ExperimentCatalog.findById would resolve to whichever
        // came first and a classic lab's pinned digest would move.
        val classicIds = LabCurriculum.classicLabs.map { it.id }.toSet()
        val collisions = extendedLabs.map { it.id }.filter { it in classicIds }
        assertTrue("Extended presets collide with classic lab ids: $collisions", collisions.isEmpty())

        assertEquals(
            "allLabs must be exactly the classic twelve plus the extended set",
            LabCurriculum.classicLabs.size + extendedLabs.size,
            ExperimentCatalog.allLabs.size
        )

        for (lab in extendedLabs) {
            assertEquals(
                "${lab.id}: findById does not round-trip",
                lab.id,
                ExperimentCatalog.findById(lab.id)?.id
            )
            assertTrue(
                "${lab.id}: belongs to no group, so the catalog cannot draw it",
                ExperimentCatalog.groupOf(lab.id) != null
            )
        }
    }
}
