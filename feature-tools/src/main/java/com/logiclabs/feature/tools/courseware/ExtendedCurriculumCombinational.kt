package com.logiclabs.feature.tools.courseware

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology

// Experiments 02 to 05: gate universality, Boolean simplification, and the four arithmetic
// building blocks. These are the presets the course sheet builds entirely from small-scale
// 74-series packages, so unlike the converter and sequential files there is no new chip model
// behind any of them — every gate here already existed before this work started.
//
// The same three house rules the converter file documents apply verbatim, and for the same
// engine reasons:
//
//  1. `isTtlHigh` resolves a floating pin HIGH, so every unused gate input on every placed
//     package is wired to its trench's ground rail. Nothing is left open.
//  2. `TestBenchVerifier` reads outputs at `TERM_LED0 + i` for `i in outputNames.indices`, so a
//     preset's indicator terminals must start at LED0 and run contiguously — a gap reads the
//     wrong net and reports a false failure.
//  3. A 14-pin DIP spans seven columns, so packages sit fourteen columns apart: trench 1 at
//     10 / 24 / 38, trench 2 at 10 / 24.
//
// One thing is specific to this file. Experiments 02 and 03 exist to show that two visibly
// different circuits compute the same function, so in both cases **both variations share a
// single `expectedFunction`** rather than each restating the algebra its own way. That is the
// whole point of the exercise made structural: if the simplified build and the unsimplified
// build did not agree, one of them would fail its sweep against the other's target.

/** Console +5V and GND into the top rail pair, then jumpered down to the bottom pair. */
private fun wireCombinationalPowerRails(circuit: BreadboardCircuit) {
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

private fun vccRailOf(chip: BreadboardCircuit.PlacedChipRuntime): Int =
    AD200Topology.RAIL_TOP_VCC_5V

private fun gndRailOf(chip: BreadboardCircuit.PlacedChipRuntime): Int =
    AD200Topology.RAIL_TOP_GND

/**
 * Wires a package's power pins to the rails serving its trench.
 *
 * Pin numbers come off `chip.model`, not from a 14/7 assumption, so this stays correct for a part
 * whose supply pins sit elsewhere — the 7476's do.
 */
private fun powerPackage(circuit: BreadboardCircuit, chip: BreadboardCircuit.PlacedChipRuntime) {
    val column = chip.placedIc.startColumn
    circuit.addWire(
        chip.getPinSocket(chip.model.vccPin),
        AD200Topology.railSocket(vccRailOf(chip), column),
        WireColor.RED
    )
    circuit.addWire(
        chip.getPinSocket(chip.model.gndPin),
        AD200Topology.railSocket(gndRailOf(chip), column + 6),
        WireColor.BLACK
    )
}

/**
 * Ties the listed spare inputs of a package to ground.
 *
 * Every position on a rail is one net, so the per-pin column offset is cosmetic — it exists so the
 * tie-off wires fan out across the rail on screen instead of stacking into a single socket.
 */
private fun tieUnusedLow(
    circuit: BreadboardCircuit,
    chip: BreadboardCircuit.PlacedChipRuntime,
    pins: List<Int>
) {
    val rail = gndRailOf(chip)
    for ((offset, pin) in pins.withIndex()) {
        circuit.addWire(
            chip.getPinSocket(pin),
            AD200Topology.railSocket(rail, chip.placedIc.startColumn + offset),
            WireColor.BLACK
        )
    }
}

private const val EXP02_TITLE = "NAND and NOR as Universal Gates"
private const val EXP03_TITLE = "Boolean Simplification and Verification"
private const val EXP04_TITLE = "Half Adder and Full Adder"
private const val EXP05_TITLE = "Half Subtractor and Full Subtractor"

/**
 * The function both Experiment 02 variations must produce: NOT A, A AND B, A OR B.
 *
 * Shared deliberately. The NAND build and the NOR build are wired nothing alike, and the only
 * claim the experiment makes is that they agree with each other — so they are swept against one
 * target rather than two independently written ones.
 */
private val exp02Target: (List<Boolean>) -> List<Boolean> = { inputs ->
    val a = inputs[0]
    val b = inputs[1]
    listOf(!a, a && b, a || b)
}

/**
 * The function both Experiment 03 variations must produce.
 *
 * Written in the *simplified* form, `A(B + C)`, because that is the claim under test. The
 * unsimplified build — five AND gates and two OR gates spread over four packages — is swept
 * against this expression, so passing its sweep **is** the proof that
 * `ABC + ABC' + AB'C = A(B + C)`. Restating the three-term sum here instead would make the test
 * tautological and prove nothing.
 */
private val exp03Target: (List<Boolean>) -> List<Boolean> = { inputs ->
    val a = inputs[0]
    val b = inputs[1]
    val c = inputs[2]
    listOf(a && (b || c))
}

val combinationalLabs: List<LabExperiment> = listOf(

    // ---------------------------------------------------------------------------------------
    // Experiment 02 — NAND and NOR universality.
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp02_nand_universal",
        labNumber = 2,
        title = EXP02_TITLE,
        subtitle = "Variation A · NOT, AND and OR from 7400 NANDs only",
        description = "Prove the NAND gate is functionally complete by building all three basic " +
            "operations out of nothing else. NOT A is a NAND with both inputs tied to A. AND is a " +
            "NAND followed by a second NAND used as an inverter, because NOT(NOT(AB)) = AB. OR " +
            "comes from De Morgan: A + B = NOT(A' · B'), so invert each input and NAND the two " +
            "complements. Five gates in total, which is why this needs two packages — a single " +
            "7400 has four. Count the gate delays as you trace it: NOT is one deep, AND is two, " +
            "OR is two. Universality costs depth, and that is the price of building everything " +
            "from one part number.",
        targetChips = listOf("7400"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1, 2),
        inputLabels = listOf("A (SW0)", "B (SW1)"),
        outputLabels = listOf("NOT A (LED0)", "A AND B (LED1)", "A OR B (LED2)"),
        objectives = listOf(
            LabObjective("obj1", "Power both 7400 packages", "Pin 14 to +5V and Pin 7 to ground on each — an unpowered package drives HIGH_Z, not LOW"),
            LabObjective("obj2", "Make an inverter out of a NAND", "Tie Pins 1 and 2 together to A; Pin 3 is A' because NAND(A,A) = A'"),
            LabObjective("obj3", "Build AND as a NAND plus an inverting NAND", "Pins 9,10 take A and B; feed Pin 8 to both Pins 12 and 13; Pin 11 is A·B"),
            LabObjective("obj4", "Apply De Morgan for OR", "NAND the two complements on the second package — NAND(A',B') = A + B"),
            LabObjective("obj5", "Ground every spare input", "Six inputs on the second package are unused; a floating TTL input reads HIGH, not LOW")
        ),
        expectedFunction = exp02Target,
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireCombinationalPowerRails(circuit)

            val u1 = circuit.addChip("7400", trench = 1, startColumn = 10)
            val u2 = circuit.addChip("7400", trench = 1, startColumn = 24)
            powerPackage(circuit, u1)
            powerPackage(circuit, u2)

            // U1 Gate 1 (1,2 -> 3): A' = NAND(A,A). Both inputs on the same net as SW0.
            circuit.addWire(AD200Topology.TERM_SW0, u1.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW0, u1.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(u1.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

            // U1 Gate 2 (4,5 -> 6): B' = NAND(B,B). Not shown on an LED; the OR stage consumes it.
            circuit.addWire(AD200Topology.TERM_SW1, u1.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW1, u1.getPinSocket(5), WireColor.ORANGE)

            // U1 Gate 3 (9,10 -> 8): the raw NAND of A and B.
            circuit.addWire(AD200Topology.TERM_SW0, u1.getPinSocket(9), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, u1.getPinSocket(10), WireColor.ORANGE)

            // U1 Gate 4 (12,13 -> 11): inverts it back, giving A·B.
            circuit.addWire(u1.getPinSocket(8), u1.getPinSocket(12), WireColor.WHITE)
            circuit.addWire(u1.getPinSocket(8), u1.getPinSocket(13), WireColor.WHITE)
            circuit.addWire(u1.getPinSocket(11), AD200Topology.TERM_LED1, WireColor.GREEN)

            // U2 Gate 1 (1,2 -> 3): De Morgan's OR, NAND(A', B').
            circuit.addWire(u1.getPinSocket(3), u2.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(u1.getPinSocket(6), u2.getPinSocket(2), WireColor.BLUE)
            circuit.addWire(u2.getPinSocket(3), AD200Topology.TERM_LED2, WireColor.GREEN)

            // U2 Gates 2, 3 and 4 are spare.
            tieUnusedLow(circuit, u2, listOf(4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp02_nor_universal",
        labNumber = 2,
        title = EXP02_TITLE,
        subtitle = "Variation B · the same three gates from 7402 NORs only",
        description = "Repeat the proof with the dual part. NOT A is a NOR with both inputs on A. " +
            "OR is a NOR inverted by a second NOR. AND is the De Morgan case this time: " +
            "A · B = NOT(A' + B'), so the complements are NOR'd together. Everything that was " +
            "cheap in Variation A is expensive here and vice versa — the NAND build gets AND in " +
            "two levels and OR in two, the NOR build gets OR in two and AND in two, but with the " +
            "roles of the direct and the De Morgan path swapped. Watch the 7402's pinout while " +
            "you wire it: its output is Pin 1 and its inputs are Pins 2 and 3, the reverse of " +
            "every other quad gate in the drawer, and it is the single most common wiring mistake " +
            "in this experiment.",
        targetChips = listOf("7402"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1, 2),
        inputLabels = listOf("A (SW0)", "B (SW1)"),
        outputLabels = listOf("NOT A (LED0)", "A AND B (LED1)", "A OR B (LED2)"),
        objectives = listOf(
            LabObjective("obj1", "Read the 7402 pinout before wiring", "Output first, then inputs: 1Y=Pin 1, 1A=Pin 2, 1B=Pin 3"),
            LabObjective("obj2", "Make an inverter out of a NOR", "Pins 2 and 3 both to A; Pin 1 is A' because NOR(A,A) = A'"),
            LabObjective("obj3", "Build OR as a NOR plus an inverting NOR", "Pins 8,9 take A and B; Pin 10 feeds both Pins 11 and 12; Pin 13 is A + B"),
            LabObjective("obj4", "Apply De Morgan for AND", "NOR the two complements on the second package — NOR(A',B') = A · B"),
            LabObjective("obj5", "Compare the two variations vector by vector", "Both must produce identical LED patterns; that agreement is the universality result")
        ),
        expectedFunction = exp02Target,
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireCombinationalPowerRails(circuit)

            val u1 = circuit.addChip("7402", trench = 1, startColumn = 10)
            val u2 = circuit.addChip("7402", trench = 1, startColumn = 24)
            powerPackage(circuit, u1)
            powerPackage(circuit, u2)

            // U1 Gate 1 (2,3 -> 1): A' = NOR(A,A).
            circuit.addWire(AD200Topology.TERM_SW0, u1.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW0, u1.getPinSocket(3), WireColor.YELLOW)
            circuit.addWire(u1.getPinSocket(1), AD200Topology.TERM_LED0, WireColor.GREEN)

            // U1 Gate 2 (5,6 -> 4): B' = NOR(B,B), consumed by the AND stage.
            circuit.addWire(AD200Topology.TERM_SW1, u1.getPinSocket(5), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW1, u1.getPinSocket(6), WireColor.ORANGE)

            // U1 Gate 3 (8,9 -> 10): the raw NOR of A and B.
            circuit.addWire(AD200Topology.TERM_SW0, u1.getPinSocket(8), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, u1.getPinSocket(9), WireColor.ORANGE)

            // U1 Gate 4 (11,12 -> 13): inverts it back, giving A + B.
            circuit.addWire(u1.getPinSocket(10), u1.getPinSocket(11), WireColor.WHITE)
            circuit.addWire(u1.getPinSocket(10), u1.getPinSocket(12), WireColor.WHITE)
            circuit.addWire(u1.getPinSocket(13), AD200Topology.TERM_LED2, WireColor.GREEN)

            // U2 Gate 1 (2,3 -> 1): De Morgan's AND, NOR(A', B').
            circuit.addWire(u1.getPinSocket(1), u2.getPinSocket(2), WireColor.BLUE)
            circuit.addWire(u1.getPinSocket(4), u2.getPinSocket(3), WireColor.BLUE)
            circuit.addWire(u2.getPinSocket(1), AD200Topology.TERM_LED1, WireColor.GREEN)

            // U2 Gates 2, 3 and 4 are spare.
            tieUnusedLow(circuit, u2, listOf(5, 6, 8, 9, 11, 12))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    // ---------------------------------------------------------------------------------------
    // Experiment 03 — Boolean simplification, both sides of the same identity.
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp03_unsimplified",
        labNumber = 3,
        title = EXP03_TITLE,
        subtitle = "Variation A · the raw three-term sum, built literally",
        description = "Build Y = ABC + ABC' + AB'C exactly as written, one gate per operator, and " +
            "count the cost. Each three-literal product needs two cascaded 2-input ANDs, so three " +
            "products cost six AND gates; summing three products costs two ORs; and the " +
            "complements B' and C' cost two inverters. That is four packages and eleven gates for " +
            "an expression that Variation B implements in two gates on two packages. Load this " +
            "build, sweep it, then load the simplified one and sweep that: both are graded against " +
            "the same target function A(B + C), so agreement across all eight input vectors is the " +
            "experimental proof of the algebra. Nothing is being asserted here — it is measured.",
        targetChips = listOf("7408", "7432", "7404"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0),
        inputLabels = listOf("A (SW0)", "B (SW1)", "C (SW2)"),
        outputLabels = listOf("Y = ABC + ABC' + AB'C (LED0)"),
        objectives = listOf(
            LabObjective("obj1", "Generate the two complements", "7404 Pin 1 takes B and Pin 2 is B'; Pin 3 takes C and Pin 4 is C'"),
            LabObjective("obj2", "Form A·B once and reuse it", "Both ABC and ABC' share the A·B partial product — one gate feeds two"),
            LabObjective("obj3", "Build the third product term", "A·B' on the second 7408, then AND it with C on the third package"),
            LabObjective("obj4", "Sum the three products", "7432 ORs term1+term2, then ORs that result with term3 to give Y"),
            LabObjective("obj5", "Tally the gate count", "Eleven gates and four packages here against two gates and two packages in Variation B")
        ),
        expectedFunction = exp03Target,
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireCombinationalPowerRails(circuit)

            val inv = circuit.addChip("7404", trench = 1, startColumn = 10)
            val and1 = circuit.addChip("7408", trench = 1, startColumn = 24)
            val and2 = circuit.addChip("7408", trench = 1, startColumn = 38)
            val or1 = circuit.addChip("7432", trench = 2, startColumn = 10)
            powerPackage(circuit, inv)
            powerPackage(circuit, and1)
            powerPackage(circuit, and2)
            powerPackage(circuit, or1)

            // Complements. 7404 gate 1 (1 -> 2) gives B'; gate 2 (3 -> 4) gives C'.
            circuit.addWire(AD200Topology.TERM_SW1, inv.getPinSocket(1), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, inv.getPinSocket(3), WireColor.PURPLE)
            tieUnusedLow(circuit, inv, listOf(5, 9, 11, 13))

            // and1 gate 1 (1,2 -> 3): the shared partial product A·B.
            circuit.addWire(AD200Topology.TERM_SW0, and1.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, and1.getPinSocket(2), WireColor.ORANGE)

            // and1 gate 2 (4,5 -> 6): term 1 = (A·B)·C.
            circuit.addWire(and1.getPinSocket(3), and1.getPinSocket(4), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW2, and1.getPinSocket(5), WireColor.PURPLE)

            // and1 gate 3 (9,10 -> 8): term 2 = (A·B)·C'.
            circuit.addWire(and1.getPinSocket(3), and1.getPinSocket(9), WireColor.WHITE)
            circuit.addWire(inv.getPinSocket(4), and1.getPinSocket(10), WireColor.BLUE)

            // and1 gate 4 (12,13 -> 11): the partial product A·B'.
            circuit.addWire(AD200Topology.TERM_SW0, and1.getPinSocket(12), WireColor.YELLOW)
            circuit.addWire(inv.getPinSocket(2), and1.getPinSocket(13), WireColor.BLUE)

            // and2 gate 1 (1,2 -> 3): term 3 = (A·B')·C.
            circuit.addWire(and1.getPinSocket(11), and2.getPinSocket(1), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW2, and2.getPinSocket(2), WireColor.PURPLE)
            tieUnusedLow(circuit, and2, listOf(4, 5, 9, 10, 12, 13))

            // or1 gate 1 (1,2 -> 3): term1 + term2. Gate 2 (4,5 -> 6): + term3 = Y.
            circuit.addWire(and1.getPinSocket(6), or1.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(and1.getPinSocket(8), or1.getPinSocket(2), WireColor.GRAY)
            circuit.addWire(or1.getPinSocket(3), or1.getPinSocket(4), WireColor.WHITE)
            circuit.addWire(and2.getPinSocket(3), or1.getPinSocket(5), WireColor.GRAY)
            circuit.addWire(or1.getPinSocket(6), AD200Topology.TERM_LED0, WireColor.GREEN)
            tieUnusedLow(circuit, or1, listOf(9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp03_simplified",
        labNumber = 3,
        title = EXP03_TITLE,
        subtitle = "Variation B · the reduced form A(B + C)",
        description = "The same function in two gates. Factor A out of all three products: " +
            "ABC + ABC' + AB'C = A(BC + BC' + B'C) = A(B(C + C') + B'C) = A(B + B'C) = A(B + C), " +
            "the last step by absorption. One 7432 forms B + C and one 7408 gates it with A. Two " +
            "packages instead of four, two gates instead of eleven, one gate delay instead of " +
            "three, and no inverters at all — so no complement rail to route. Sweep it and compare " +
            "the LED0 column against Variation A vector by vector. Identical output from a " +
            "visibly smaller circuit is what simplification buys, and it is why the reduction step " +
            "happens on paper before anything is wired.",
        targetChips = listOf("7432", "7408"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0),
        inputLabels = listOf("A (SW0)", "B (SW1)", "C (SW2)"),
        outputLabels = listOf("Y = A(B + C) (LED0)"),
        objectives = listOf(
            LabObjective("obj1", "Factor the expression on paper first", "A(B + B'C) reduces to A(B + C) by absorption, since B + B'C = B + C"),
            LabObjective("obj2", "Form the sum term", "7432 Pins 1 and 2 take B and C; Pin 3 is B + C"),
            LabObjective("obj3", "Gate it with A", "7408 Pins 1 and 2 take A and (B + C); Pin 3 is Y"),
            LabObjective("obj4", "Ground the six spare inputs per package", "Three unused gates on each part, all inputs to the ground rail"),
            LabObjective("obj5", "Verify equivalence, do not assume it", "Both variations are swept against the same target — matching all eight rows is the proof")
        ),
        expectedFunction = exp03Target,
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireCombinationalPowerRails(circuit)

            val or1 = circuit.addChip("7432", trench = 1, startColumn = 10)
            val and1 = circuit.addChip("7408", trench = 1, startColumn = 24)
            powerPackage(circuit, or1)
            powerPackage(circuit, and1)

            // or1 gate 1 (1,2 -> 3): B + C.
            circuit.addWire(AD200Topology.TERM_SW1, or1.getPinSocket(1), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, or1.getPinSocket(2), WireColor.PURPLE)
            tieUnusedLow(circuit, or1, listOf(4, 5, 9, 10, 12, 13))

            // and1 gate 1 (1,2 -> 3): A · (B + C) = Y.
            circuit.addWire(AD200Topology.TERM_SW0, and1.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(or1.getPinSocket(3), and1.getPinSocket(2), WireColor.WHITE)
            circuit.addWire(and1.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
            tieUnusedLow(circuit, and1, listOf(4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    ),

    // ---------------------------------------------------------------------------------------
    // Experiment 04 — half adder, then the full adder built from it.
    //
    // Unlike 02 and 03, the two variations here are genuinely different functions rather than two
    // routes to one function, so each carries its own `expectedFunction`. Variation B is the
    // half adder of Variation A with a carry-in path added, which is why they belong on one card.
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp04_half_adder",
        labNumber = 4,
        title = EXP04_TITLE,
        subtitle = "Variation A · half adder, sum and carry",
        description = "The smallest piece of arithmetic hardware there is. Adding two bits gives a " +
            "two-bit answer: SUM is 1 when exactly one input is 1, which is A XOR B, and CARRY is 1 " +
            "only when both are, which is A AND B. Two gates, and the whole of binary addition " +
            "follows from them. Read the LEDs as a two-bit number with CARRY as the high bit and " +
            "step through the four inputs: 0+0 shows 00, 0+1 and 1+0 show 01, and 1+1 shows 10, " +
            "which is decimal 2. The reason it is called a *half* adder is the missing third input " +
            "— it cannot accept a carry from a lower column, so it can only ever add the least " +
            "significant bit of a multi-bit sum. Variation B fixes that.",
        targetChips = listOf("7486", "7408"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("A (SW0)", "B (SW1)"),
        outputLabels = listOf("SUM (LED0)", "CARRY (LED1)"),
        objectives = listOf(
            LabObjective("obj1", "Wire the sum with an XOR", "7486 Pins 1 and 2 take A and B; Pin 3 is A XOR B"),
            LabObjective("obj2", "Wire the carry with an AND", "7408 Pins 1 and 2 take the same A and B; Pin 3 is A·B"),
            LabObjective("obj3", "Feed both gates from one pair of nets", "A and B each fan out to two packages — one switch net, two loads"),
            LabObjective("obj4", "Read the outputs as a binary number", "CARRY is the 2s place and SUM the 1s place, so 1+1 reads as 10"),
            LabObjective("obj5", "Identify what is missing", "There is no carry-in, which is exactly what makes this a half adder")
        ),
        expectedFunction = { inputs ->
            val a = inputs[0]
            val b = inputs[1]
            listOf(a xor b, a && b)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireCombinationalPowerRails(circuit)

            val xor = circuit.addChip("7486", trench = 1, startColumn = 10)
            val and = circuit.addChip("7408", trench = 1, startColumn = 24)
            powerPackage(circuit, xor)
            powerPackage(circuit, and)

            // SUM = A XOR B on 7486 gate 1 (1,2 -> 3).
            circuit.addWire(AD200Topology.TERM_SW0, xor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(xor.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
            tieUnusedLow(circuit, xor, listOf(4, 5, 9, 10, 12, 13))

            // CARRY = A AND B on 7408 gate 1 (1,2 -> 3).
            circuit.addWire(AD200Topology.TERM_SW0, and.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, and.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(and.getPinSocket(3), AD200Topology.TERM_LED1, WireColor.GREEN)
            tieUnusedLow(circuit, and, listOf(4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp04_full_adder",
        labNumber = 4,
        title = EXP04_TITLE,
        subtitle = "Variation B · full adder with carry-in",
        description = "Add the third input and the cell becomes cascadable. SUM is A XOR B XOR Cin " +
            "— parity of the three inputs, so it is 1 whenever an odd number of them are 1. COUT " +
            "is 1 when at least two inputs are, which factors into (A XOR B)·Cin + A·B: the first " +
            "term is the carry generated by the incoming carry rippling through, the second is the " +
            "carry the two operand bits generate on their own. Five gates across three packages, " +
            "and the A XOR B node is shared between the sum path and the carry path rather than " +
            "being computed twice. Chain four of these, carry-out to carry-in, and you have the " +
            "7483 that Lab 12 uses as a single part. Step Cin with SW2 and watch it change the " +
            "answer for the same A and B.",
        targetChips = listOf("7486", "7408", "7432"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("A (SW0)", "B (SW1)", "Cin (SW2)"),
        outputLabels = listOf("SUM (LED0)", "COUT (LED1)"),
        objectives = listOf(
            LabObjective("obj1", "Build the shared XOR node", "7486 gate 1 gives A XOR B on Pin 3; both the sum and the carry path read it"),
            LabObjective("obj2", "Complete the sum", "7486 gate 2 XORs that node with Cin — Pin 6 is the final SUM"),
            LabObjective("obj3", "Form the two carry terms", "7408 gate 1 is (A XOR B)·Cin and gate 2 is A·B"),
            LabObjective("obj4", "Combine them", "7432 Pins 1 and 2 take the two AND outputs; Pin 3 is COUT"),
            LabObjective("obj5", "Confirm the cascade property", "COUT of one stage is the Cin of the next — this is what makes a ripple-carry adder")
        ),
        expectedFunction = { inputs ->
            val a = inputs[0]
            val b = inputs[1]
            val cin = inputs[2]
            listOf(a xor b xor cin, (a && b) || ((a xor b) && cin))
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireCombinationalPowerRails(circuit)

            val xor = circuit.addChip("7486", trench = 1, startColumn = 10)
            val and = circuit.addChip("7408", trench = 1, startColumn = 24)
            val or = circuit.addChip("7432", trench = 1, startColumn = 38)
            powerPackage(circuit, xor)
            powerPackage(circuit, and)
            powerPackage(circuit, or)

            // 7486 gate 1 (1,2 -> 3): the shared A XOR B node.
            circuit.addWire(AD200Topology.TERM_SW0, xor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(2), WireColor.ORANGE)

            // 7486 gate 2 (4,5 -> 6): SUM = (A XOR B) XOR Cin.
            circuit.addWire(xor.getPinSocket(3), xor.getPinSocket(4), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW2, xor.getPinSocket(5), WireColor.PURPLE)
            circuit.addWire(xor.getPinSocket(6), AD200Topology.TERM_LED0, WireColor.GREEN)
            tieUnusedLow(circuit, xor, listOf(9, 10, 12, 13))

            // 7408 gate 1 (1,2 -> 3): (A XOR B)·Cin, the propagated carry.
            circuit.addWire(xor.getPinSocket(3), and.getPinSocket(1), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW2, and.getPinSocket(2), WireColor.PURPLE)

            // 7408 gate 2 (4,5 -> 6): A·B, the generated carry.
            circuit.addWire(AD200Topology.TERM_SW0, and.getPinSocket(4), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, and.getPinSocket(5), WireColor.ORANGE)
            tieUnusedLow(circuit, and, listOf(9, 10, 12, 13))

            // 7432 gate 1 (1,2 -> 3): COUT = propagated + generated.
            circuit.addWire(and.getPinSocket(3), or.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(and.getPinSocket(6), or.getPinSocket(2), WireColor.GRAY)
            circuit.addWire(or.getPinSocket(3), AD200Topology.TERM_LED1, WireColor.GREEN)
            tieUnusedLow(circuit, or, listOf(4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    ),

    // ---------------------------------------------------------------------------------------
    // Experiment 05 — half subtractor, then the full subtractor.
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp05_half_subtractor",
        labNumber = 5,
        title = EXP05_TITLE,
        subtitle = "Variation A · half subtractor, difference and borrow",
        description = "Subtraction mirrors addition with one asymmetry. DIFFERENCE is A XOR B, " +
            "exactly the same gate as the half adder's sum — the difference bit and the sum bit are " +
            "the same function. BORROW is not: it is A'·B, true only when you take a larger bit " +
            "from a smaller one, which for single bits means 0 minus 1. That inverter is the whole " +
            "structural difference between adding and subtracting, and it is also why BORROW is not " +
            "symmetric in A and B the way CARRY is. Step to A=0, B=1 and watch both LEDs light: " +
            "the answer is 1 with a borrow owed to the next column up, which is the binary way of " +
            "writing that 0 - 1 = -1.",
        targetChips = listOf("7486", "7404", "7408"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("A (SW0)", "B (SW1)"),
        outputLabels = listOf("DIFF (LED0)", "BORROW (LED1)"),
        objectives = listOf(
            LabObjective("obj1", "Wire the difference", "7486 Pins 1 and 2 take A and B; Pin 3 is A XOR B, the same gate as a half adder's sum"),
            LabObjective("obj2", "Complement the minuend", "7404 Pin 1 takes A; Pin 2 is A'"),
            LabObjective("obj3", "Wire the borrow", "7408 Pins 1 and 2 take A' and B; Pin 3 is A'·B"),
            LabObjective("obj4", "Note the asymmetry", "Swapping A and B leaves DIFF unchanged but flips BORROW — subtraction is not commutative"),
            LabObjective("obj5", "Ground all spare inputs", "Three unused XORs, five unused inverter inputs and three unused ANDs")
        ),
        expectedFunction = { inputs ->
            val a = inputs[0]
            val b = inputs[1]
            listOf(a xor b, !a && b)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireCombinationalPowerRails(circuit)

            val xor = circuit.addChip("7486", trench = 1, startColumn = 10)
            val inv = circuit.addChip("7404", trench = 1, startColumn = 24)
            val and = circuit.addChip("7408", trench = 1, startColumn = 38)
            powerPackage(circuit, xor)
            powerPackage(circuit, inv)
            powerPackage(circuit, and)

            // DIFF = A XOR B on 7486 gate 1 (1,2 -> 3).
            circuit.addWire(AD200Topology.TERM_SW0, xor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(xor.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
            tieUnusedLow(circuit, xor, listOf(4, 5, 9, 10, 12, 13))

            // 7404 gate 1 (1 -> 2): A'.
            circuit.addWire(AD200Topology.TERM_SW0, inv.getPinSocket(1), WireColor.YELLOW)
            tieUnusedLow(circuit, inv, listOf(3, 5, 9, 11, 13))

            // BORROW = A'·B on 7408 gate 1 (1,2 -> 3).
            circuit.addWire(inv.getPinSocket(2), and.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW1, and.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(and.getPinSocket(3), AD200Topology.TERM_LED1, WireColor.GREEN)
            tieUnusedLow(circuit, and, listOf(4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp05_full_subtractor",
        labNumber = 5,
        title = EXP05_TITLE,
        subtitle = "Variation B · full subtractor with borrow-in",
        description = "A - B - Bin, the cell you cascade to subtract multi-bit numbers. DIFF is " +
            "A XOR B XOR Bin, parity again and identical to the full adder's sum. BOUT is " +
            "A'·B + (A XOR B)'·Bin: you owe a borrow either because this column cannot cover its " +
            "own subtraction, or because the incoming borrow tips a column that was exactly " +
            "balanced. Note the XNOR in the second term — the inverted XOR, which is why this build " +
            "spends two inverters where the full adder spent none. Seven gates on four packages. " +
            "Sweep all eight rows and read the pair as a signed result: DIFF is the answer bit and " +
            "BOUT says whether the column above has to lend one.",
        targetChips = listOf("7486", "7404", "7408", "7432"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("A (SW0)", "B (SW1)", "Bin (SW2)"),
        outputLabels = listOf("DIFF (LED0)", "BOUT (LED1)"),
        objectives = listOf(
            LabObjective("obj1", "Build the shared XOR node", "7486 gate 1 gives A XOR B; both the difference and the borrow path read Pin 3"),
            LabObjective("obj2", "Complete the difference", "7486 gate 2 XORs that node with Bin — Pin 6 is DIFF"),
            LabObjective("obj3", "Generate both complements", "7404 gate 1 inverts A; gate 2 inverts the A XOR B node to make the XNOR term"),
            LabObjective("obj4", "Form the two borrow terms", "7408 gate 1 is A'·B and gate 2 is (A XOR B)'·Bin"),
            LabObjective("obj5", "Combine and compare", "7432 ORs them into BOUT; compare the DIFF column against Variation A where Bin = 0")
        ),
        expectedFunction = { inputs ->
            val a = inputs[0]
            val b = inputs[1]
            val bin = inputs[2]
            listOf(a xor b xor bin, (!a && b) || (!(a xor b) && bin))
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireCombinationalPowerRails(circuit)

            val xor = circuit.addChip("7486", trench = 1, startColumn = 10)
            val inv = circuit.addChip("7404", trench = 1, startColumn = 24)
            val and = circuit.addChip("7408", trench = 1, startColumn = 38)
            val or = circuit.addChip("7432", trench = 2, startColumn = 10)
            powerPackage(circuit, xor)
            powerPackage(circuit, inv)
            powerPackage(circuit, and)
            powerPackage(circuit, or)

            // 7486 gate 1 (1,2 -> 3): the shared A XOR B node.
            circuit.addWire(AD200Topology.TERM_SW0, xor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(2), WireColor.ORANGE)

            // 7486 gate 2 (4,5 -> 6): DIFF = (A XOR B) XOR Bin.
            circuit.addWire(xor.getPinSocket(3), xor.getPinSocket(4), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW2, xor.getPinSocket(5), WireColor.PURPLE)
            circuit.addWire(xor.getPinSocket(6), AD200Topology.TERM_LED0, WireColor.GREEN)
            tieUnusedLow(circuit, xor, listOf(9, 10, 12, 13))

            // 7404 gate 1 (1 -> 2): A'. Gate 2 (3 -> 4): (A XOR B)', the XNOR term.
            circuit.addWire(AD200Topology.TERM_SW0, inv.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(xor.getPinSocket(3), inv.getPinSocket(3), WireColor.WHITE)
            tieUnusedLow(circuit, inv, listOf(5, 9, 11, 13))

            // 7408 gate 1 (1,2 -> 3): A'·B. Gate 2 (4,5 -> 6): (A XOR B)'·Bin.
            circuit.addWire(inv.getPinSocket(2), and.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW1, and.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(inv.getPinSocket(4), and.getPinSocket(4), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW2, and.getPinSocket(5), WireColor.PURPLE)
            tieUnusedLow(circuit, and, listOf(9, 10, 12, 13))

            // 7432 gate 1 (1,2 -> 3): BOUT.
            circuit.addWire(and.getPinSocket(3), or.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(and.getPinSocket(6), or.getPinSocket(2), WireColor.GRAY)
            circuit.addWire(or.getPinSocket(3), AD200Topology.TERM_LED1, WireColor.GREEN)
            tieUnusedLow(circuit, or, listOf(4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    )
)
