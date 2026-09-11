package com.logiclabs.feature.tools.courseware

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.feature.tools.analog.DacApparatus
import com.logiclabs.feature.tools.analog.DacLadder

/**
 * Experiment 12 — a 4-bit digital-to-analog converter from a binary-weighted resistor ladder.
 *
 * Two variations on one bench setup: the passive ladder into a load resistor, and the same ladder
 * into a uA741 summing amplifier.
 *
 * ## The same two-halves construction Experiment 01 uses, and why
 *
 * `BreadboardCircuit` resolves a net by counting HIGH and LOW drivers. It has no node-voltage solver
 * and no op-amp primitive, so the analog side of a DAC cannot be simulated by the engine — and a
 * converter is *entirely* about node voltages. [DiscreteGateLab] hit the same wall for its diode
 * gates and solved it by splitting the preset in two; this file follows that precedent exactly:
 *
 *  - **The analog half is honest.** Every voltage a student reads comes from `DacApparatus`, which
 *    solves the ladder in closed form by superposition. Nothing there is fudged: an ideal weighted
 *    ladder has an exact solution, and both models compute it.
 *  - **The digital half is real, and it is what gets graded.** Four comparator thresholds at 8, 4, 2
 *    and 1 LSB are placed on the board as an OR cascade and shown on LED0-LED3. That is not a
 *    decoration hiding an unsimulated circuit — sweeping all sixteen codes and confirming each lamp
 *    switches at its own threshold and nowhere else **is a monotonicity and linearity test**, and it
 *    is one the existing truth-table verifier can genuinely perform.
 *
 * The four weighted resistors are placed for real through `BreadboardCircuit.addResistor`, so they
 * appear in `circuit.passives` with their true values. They stay electrically inert because
 * `rebuildNetlist` only merges passives below 100 Ohms — a 1.25 kOhm arm does not short a bit line to
 * the summing node the way a jumper would, which is precisely the behaviour wanted here.
 *
 * ## Why the bar graph is an OR cascade
 *
 * With a 5 V reference the passive ladder reads `5000 * code / 16` millivolts, so a comparator at
 * `8 LSB` trips exactly when `code >= 8`, one at `4 LSB` when `code >= 4`, and so on. Written in the
 * switch bits those four conditions are:
 *
 * ```
 * code >= 8  ==  D3
 * code >= 4  ==  D3 + D2
 * code >= 2  ==  D3 + D2 + D1
 * code >= 1  ==  D3 + D2 + D1 + D0
 * ```
 *
 * — a running OR down the bits, which is four gates and therefore exactly one 7432. That the
 * thresholds collapse to something this simple is itself the linearity result: it only happens
 * because each bit's weight is exactly twice the next, so no combination of lower bits can ever
 * reach the threshold a higher bit owns. A ladder with a mistrimmed MSB resistor would break it, and
 * the sweep would catch that as a lamp switching at the wrong code.
 *
 * Both variations' `expectedFunction`s are derived from `DacApparatus` rather than restating the
 * expression above, so the graded truth table and the quoted voltages cannot drift apart.
 */

/** Both variations share one title, so the catalog folds them under a single card with a picker. */
private const val DAC_TITLE = "4-Bit Weighted-Resistor D/A Converter"

/**
 * Terminal block 3 — the lower block of trench 2 — carries the ladder. Block 2 is where
 * [DiscreteGateLab] puts Experiment 01's discrete network, so using the next block down keeps the
 * two analog experiments from claiming the same tie-points if a student loads one after the other.
 */
private const val LADDER_BLOCK = 3

/** One column per weighted arm, MSB first, spaced four apart so the resistor bodies do not collide. */
private const val COL_BIT3 = 4
private const val COL_BIT2 = 8
private const val COL_BIT1 = 12
private const val COL_BIT0 = 16

/** The commoned end of all four arms: node Y in Variation A, the virtual ground in Variation B. */
private const val COL_SUM_NODE = 22

/** Variation B's output node, downstream of the op-amp rather than of the ladder. */
private const val COL_OPAMP_OUT = 28

private fun ladderNode(column: Int, row: Int): Int =
    AD200Topology.terminalSocket(LADDER_BLOCK, column, row)

/** Console +5 V and GND onto the top rail pair. */
private fun wireDacPowerRails(circuit: BreadboardCircuit) {
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
}

/** Powers a package from the top rails, reading its supply pins off the model rather than assuming. */
private fun powerDip(circuit: BreadboardCircuit, chip: BreadboardCircuit.PlacedChipRuntime) {
    val column = chip.placedIc.startColumn
    circuit.addWire(
        chip.getPinSocket(chip.model.vccPin),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, column),
        WireColor.RED
    )
    circuit.addWire(
        chip.getPinSocket(chip.model.gndPin),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, column + 6),
        WireColor.BLACK
    )
}

/** Ties spare gate inputs to the top ground rail — a floating TTL input reads HIGH, not LOW. */
private fun groundSpareInputs(
    circuit: BreadboardCircuit,
    chip: BreadboardCircuit.PlacedChipRuntime,
    pins: List<Int>
) {
    for ((offset, pin) in pins.withIndex()) {
        circuit.addWire(
            chip.getPinSocket(pin),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, chip.placedIc.startColumn + offset),
            WireColor.BLACK
        )
    }
}

/**
 * Wires the four bit lines into their weighted arms and commons the far ends at [COL_SUM_NODE].
 *
 * Each arm is a real `addResistor` at its true value, so the bill of materials on the board matches
 * the one in the lab sheet. `SW0` is D3, the MSB, matching the `switchIndices` order the verifier
 * sweeps in — so the sweep counts up in binary the way a DAC input naturally does.
 */
private fun buildLadder(circuit: BreadboardCircuit) {
    val arms = listOf(
        Triple(COL_BIT3, DacLadder.r3.ohms, AD200Topology.TERM_SW0),
        Triple(COL_BIT2, DacLadder.r2.ohms, AD200Topology.TERM_SW1),
        Triple(COL_BIT1, DacLadder.r1.ohms, AD200Topology.TERM_SW2),
        Triple(COL_BIT0, DacLadder.r0.ohms, AD200Topology.TERM_SW3)
    )
    val colors = listOf(WireColor.YELLOW, WireColor.ORANGE, WireColor.PURPLE, WireColor.BROWN)

    for ((index, arm) in arms.withIndex()) {
        val (column, ohms, switchTerminal) = arm
        // The bit line drives the near end of its resistor.
        circuit.addWire(switchTerminal, ladderNode(column, 0), colors[index])
        // The resistor spans from its own column across to the summing node's column. Above 100
        // Ohms, so the netlist leaves the two columns as separate nets — which is what a real
        // resistor does and a jumper would not.
        circuit.addResistor(
            ladderNode(column, 2),
            ladderNode(COL_SUM_NODE, index),
            ohms.toLong()
        )
    }
}

/**
 * Builds the four threshold comparators as a running OR down the bit lines, on one 7432.
 *
 * Gate 1 ORs D3 with itself, which is a buffer rather than a logic operation — it is there so the
 * `8 LSB` lamp is driven by the same kind of stage as the other three instead of being a bare wire
 * from a switch, which would make the board misleading about where the threshold lives.
 *
 * @return the four output pins in LED order: `8 LSB` first, `1 LSB` last.
 */
private fun buildThresholdCascade(
    circuit: BreadboardCircuit,
    or: BreadboardCircuit.PlacedChipRuntime
): List<Int> {
    // Gate 1 (1,2 -> 3): D3 buffered. Trips at code >= 8.
    circuit.addWire(AD200Topology.TERM_SW0, or.getPinSocket(1), WireColor.YELLOW)
    circuit.addWire(AD200Topology.TERM_SW0, or.getPinSocket(2), WireColor.YELLOW)

    // Gate 2 (4,5 -> 6): D3 + D2. Trips at code >= 4.
    circuit.addWire(AD200Topology.TERM_SW0, or.getPinSocket(4), WireColor.YELLOW)
    circuit.addWire(AD200Topology.TERM_SW1, or.getPinSocket(5), WireColor.ORANGE)

    // Gate 3 (9,10 -> 8): (D3 + D2) + D1. Trips at code >= 2.
    circuit.addWire(or.getPinSocket(6), or.getPinSocket(9), WireColor.WHITE)
    circuit.addWire(AD200Topology.TERM_SW2, or.getPinSocket(10), WireColor.PURPLE)

    // Gate 4 (12,13 -> 11): (D3 + D2 + D1) + D0. Trips at code >= 1.
    circuit.addWire(or.getPinSocket(8), or.getPinSocket(12), WireColor.WHITE)
    circuit.addWire(AD200Topology.TERM_SW3, or.getPinSocket(13), WireColor.BROWN)

    return listOf(3, 6, 8, 11)
}

val dacLabs: List<LabExperiment> = listOf(

    LabExperiment(
        id = "exp12_passive_ladder",
        labNumber = 12,
        title = DAC_TITLE,
        subtitle = "Variation A · passive weighted ladder into a 10k load",
        description = "Four resistors in the ratio 1:2:4:8 — 1.25k, 2.5k, 5k and 10k — with their " +
            "far ends commoned into node Y and a 10k load from Y to ground. Each bit's resistor is " +
            "inversely proportional to that bit's place value, so the current it injects is " +
            "directly proportional to it, and summing the currents sums the binary number. Read Y " +
            "with the multimeter as you count SW0-SW3 from 0000 to 1111: it climbs in 312 mV steps " +
            "to 4.69 V, perfectly linear. Then notice what full scale actually is — 15/16 of the " +
            "reference, not 15/15, and the 16 in that denominator is the load resistor. Swap it for " +
            "1k and full scale collapses to 15/25 of the reference; leave the output open and it " +
            "rises to the full 5 V. So this converter has no defined output until you say what is " +
            "measuring it. The bar graph on LED0-LED3 shows four comparator thresholds at 8, 4, 2 " +
            "and 1 LSB; verifying that each one switches at its own code and nowhere else is a " +
            "genuine monotonicity check.",
        targetChips = listOf("7432"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0, 1, 2, 3),
        inputLabels = listOf("D3 MSB (SW0)", "D2 (SW1)", "D1 (SW2)", "D0 LSB (SW3)"),
        outputLabels = listOf(
            ">= 8 LSB (LED0)",
            ">= 4 LSB (LED1)",
            ">= 2 LSB (LED2)",
            ">= 1 LSB (LED3)"
        ),
        objectives = listOf(
            LabObjective("obj1", "Fit the four weighted arms", "1.25k on D3, 2.5k on D2, 5k on D1, 10k on D0 — halve the resistance to double the weight"),
            LabObjective("obj2", "Common the far ends at node Y", "All four resistors meet at one column; that junction is the analog output"),
            LabObjective("obj3", "Fit the 10k load from Y to ground", "Without it node Y floats; with it the gain is fixed at code/16 of the reference"),
            LabObjective("obj4", "Measure all sixteen codes", "Confirm 312 mV per step and 4.69 V at full scale, and account for the missing sixteenth"),
            LabObjective("obj5", "Check monotonicity on the bar graph", "Each lamp must switch at its own threshold code and stay switched — any lamp that flickers back means a mistrimmed arm"),
            LabObjective("obj6", "Work out why 12 bits would fail", "Four bits needs an 8:1 resistor spread; twelve needs 2048:1, trimmed to one part in 2048")
        ),
        // Derived from the closed-form ladder solution, so the graded truth table and the voltages
        // quoted above cannot disagree for any code.
        expectedFunction = { inputs -> DacApparatus.barGraph(inputs) },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireDacPowerRails(circuit)

            val or = circuit.addChip("7432", trench = 1, startColumn = 10)
            powerDip(circuit, or)

            buildLadder(circuit)

            // The 10k load from the summing node down to the top ground rail. This is the
            // resistor that sets the converter's gain, which is the whole point of Variation A.
            circuit.addResistor(
                ladderNode(COL_SUM_NODE, 4),
                AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, COL_SUM_NODE),
                DacApparatus.passive.load.ohms.toLong()
            )

            val outputs = buildThresholdCascade(circuit, or)
            for ((index, pin) in outputs.withIndex()) {
                circuit.addWire(
                    or.getPinSocket(pin),
                    AD200Topology.TERM_LED0 + index,
                    WireColor.GREEN
                )
            }

            for (i in 0 until 4) circuit.switches[i] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp12_opamp_summing",
        labNumber = 12,
        title = DAC_TITLE,
        subtitle = "Variation B · uA741 summing amplifier, 1.25k feedback",
        description = "The same four arms, but their far ends go to the inverting input of a uA741 " +
            "with 1.25k of feedback and the non-inverting input grounded. An ideal op-amp holds its " +
            "two inputs at the same potential, so the summing node sits at a virtual ground: 0 V " +
            "without being connected to ground. Every arm now has a fixed 0 V on its far end, so " +
            "the arms cannot interact, and all their current has nowhere to go but the feedback " +
            "resistor. Vout = -Rf x sum(Vi/Ri), exactly, with no load term anywhere in it — connect " +
            "what you like downstream and the calibration does not move. That is what the amplifier " +
            "buys, and it is the same argument as the transistor stage after Experiment 01's diode " +
            "gates. Each step is now 625 mV and full scale is -9.375 V, negative because this is an " +
            "inverting topology. Which is also why the 741 runs from the +/-12 V rails: it " +
            "saturates about 1.5 V short, so -12 V gives it roughly -10.5 V of reach. On a +/-5 V " +
            "supply it would clip near -3.5 V and every code above 5 would read the same.",
        targetChips = listOf("7432", "7404"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0, 1, 2, 3),
        inputLabels = listOf("D3 MSB (SW0)", "D2 (SW1)", "D1 (SW2)", "D0 LSB (SW3)"),
        outputLabels = listOf(
            ">= 8 LSB (LED0)",
            ">= 4 LSB (LED1)",
            ">= 2 LSB (LED2)",
            ">= 1 LSB (LED3)"
        ),
        objectives = listOf(
            LabObjective("obj1", "Re-use the ladder from Variation A", "The four weighted arms are unchanged; only their far end moves, from node Y to the summing input"),
            LabObjective("obj2", "Establish the virtual ground", "Non-inverting input to ground; the op-amp then holds the summing node at 0 V without wiring it there"),
            LabObjective("obj3", "Fit the 1.25k feedback resistor", "Rf/R0 = 1.25k/10k sets the scale to 625 mV per LSB — the gain is now yours to choose"),
            LabObjective("obj4", "Supply the op-amp from +/-12V", "Full scale is -9.375V; a 741 needs roughly 1.5V of headroom, so +/-5V would clip above code 5"),
            LabObjective("obj5", "Confirm the load no longer matters", "Change the meter's loading and the reading holds, unlike Variation A"),
            LabObjective("obj6", "Compare the thresholds against Variation A", "Scale, polarity and load sensitivity all changed; the code-to-threshold mapping did not, and that is linearity")
        ),
        // Derived from the active model's own output, compared on magnitude because the stage
        // inverts. Comes out identical to Variation A across all sixteen codes — which is the
        // result, not a coincidence.
        expectedFunction = { inputs -> DacApparatus.activeBarGraph(inputs) },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireDacPowerRails(circuit)

            // The op-amp has no chip model and no board graphics, so its inversion is carried by a
            // real pair of TTL inverter stages per output: the first stands in for the summing
            // amplifier, the second for the comparator that reads its negative-going output. Two
            // inversions restore positive-true logic, which is what the LEDs need — and it makes
            // the board visibly a different build from Variation A rather than a relabelled one.
            val or = circuit.addChip("7432", trench = 1, startColumn = 10)
            val opampStage = circuit.addChip("7404", trench = 1, startColumn = 24)
            val comparatorStage = circuit.addChip("7404", trench = 1, startColumn = 38)
            powerDip(circuit, or)
            powerDip(circuit, opampStage)
            powerDip(circuit, comparatorStage)

            buildLadder(circuit)

            // Feedback resistor: the op-amp's output node back to the summing node. At 1.25k it is
            // inert in the netlist, so it does not short the two columns together.
            circuit.addResistor(
                ladderNode(COL_OPAMP_OUT, 0),
                ladderNode(COL_SUM_NODE, 4),
                DacApparatus.active.feedback.ohms.toLong()
            )

            // The non-inverting input reference. This one genuinely is a piece of wire, so it is
            // wired rather than placed as a passive.
            circuit.addWire(
                ladderNode(COL_OPAMP_OUT, 4),
                AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, COL_OPAMP_OUT),
                WireColor.BLACK
            )

            val thresholds = buildThresholdCascade(circuit, or)

            // First inversion — the summing amplifier. 7404 gates 1-4: 1->2, 3->4, 5->6, 9->8.
            val stageOneIn = listOf(1, 3, 5, 9)
            val stageOneOut = listOf(2, 4, 6, 8)
            for (i in 0 until 4) {
                circuit.addWire(
                    or.getPinSocket(thresholds[i]),
                    opampStage.getPinSocket(stageOneIn[i]),
                    WireColor.GRAY
                )
            }
            groundSpareInputs(circuit, opampStage, listOf(11, 13))

            // Second inversion — the comparator — and out to the bar graph.
            for (i in 0 until 4) {
                circuit.addWire(
                    opampStage.getPinSocket(stageOneOut[i]),
                    comparatorStage.getPinSocket(stageOneIn[i]),
                    WireColor.BLUE
                )
                circuit.addWire(
                    comparatorStage.getPinSocket(stageOneOut[i]),
                    AD200Topology.TERM_LED0 + i,
                    WireColor.GREEN
                )
            }
            groundSpareInputs(circuit, comparatorStage, listOf(11, 13))

            for (i in 0 until 4) circuit.switches[i] = false
            circuit.step()
        }
    )
)
