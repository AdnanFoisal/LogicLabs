package com.logiclabs.feature.tools.courseware

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology

// Combinational conversion and selection experiments, added after the twelve classic labs were
// sealed. They reuse LabExperiment / LabObjective unchanged so the catalog can splice them into
// the same list; the only new idea here is that one experiment may ship as several entries.
//
// Three house rules run through every preset below, all of them consequences of how the engine
// resolves a net:
//
//  1. `isTtlHigh` treats a floating pin as HIGH, faithfully to bipolar TTL's internal pull-up.
//     An unused gate input left open therefore reads 1, not 0 — so every input on every placed
//     package is wired, and the ones no gate needs go to the ground rail. Nothing is left to
//     the reader's charity.
//  2. `TestBenchVerifier` flags LED0 .. LED(outputLabels.size - 1) as floating if they are not
//     on a driven net, so the indicator terminals a preset claims must start at LED0 and run
//     contiguously. Top-bank terminals only: the DSU pre-unions TERM_LED0..7 with
//     TERM_BOT_LED0..7, so the faceplate's two banks are eight nets, not sixteen.
//  3. A 14-pin DIP spans seven columns, so ICs sit fourteen columns apart — 10, 24, 38 — the way
//     `lab7_half_adder` spaces its pair. Three packages fill trench 1 comfortably at that pitch;
//     a fourth and fifth go to trench 2 at 10 and 24 and draw power from the bottom rail pair,
//     which `wireConverterPowerRails` jumpers to the top pair. Signals that have to reach every
//     package — the complement bus, most often — then run across the trenches rather than the
//     length of one, which is also the shorter path on the physical board.

/**
 * Feeds the console's +5V and GND binding posts into the top rail pair, then jumpers that pair
 * down to the bottom rails so trench-2 packages have power local to them.
 *
 * `LabCurriculum` keeps its equivalent private, so this is a deliberate twin rather than a
 * shared utility: these presets must not be able to change the twelve sealed labs' rails.
 */
private fun wireConverterPowerRails(circuit: BreadboardCircuit) {
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

/** The VCC rail serving all chips: all chips power from the top rail. */
private fun vccRailFor(chip: BreadboardCircuit.PlacedChipRuntime): Int =
    AD200Topology.RAIL_TOP_VCC_5V

/** The ground rail serving all chips: all chips ground from the top rail. */
private fun gndRailFor(chip: BreadboardCircuit.PlacedChipRuntime): Int =
    AD200Topology.RAIL_TOP_GND

/**
 * Wires a package's power pins to its trench's rails. Reads the pin numbers off the model rather
 * than assuming 14/7, so a 16-pin part dropped in later is powered correctly without an edit.
 *
 * Without this the model's `checkPower` reports unpowered and every output goes HIGH_Z.
 */
private fun powerChip(circuit: BreadboardCircuit, chip: BreadboardCircuit.PlacedChipRuntime) {
    val column = chip.placedIc.startColumn
    circuit.addWire(
        chip.getPinSocket(chip.model.vccPin),
        AD200Topology.railSocket(vccRailFor(chip), column),
        WireColor.RED
    )
    circuit.addWire(
        chip.getPinSocket(chip.model.gndPin),
        AD200Topology.railSocket(gndRailFor(chip), column + 6),
        WireColor.BLACK
    )
}

/**
 * Grounds the listed input pins of a package whose gates this preset does not use.
 *
 * This is not tidiness. A floating TTL input resolves HIGH, so an unused gate left open sits
 * with all inputs at 1 and drives its output accordingly — harmless while nothing reads it, but
 * it hides the mistake of having wired a real signal to the wrong gate. Grounding every spare
 * input makes an unused gate's output provably a function of the rails alone.
 */
private fun groundUnusedInputs(
    circuit: BreadboardCircuit,
    chip: BreadboardCircuit.PlacedChipRuntime,
    pins: List<Int>
) {
    val rail = gndRailFor(chip)
    for ((offset, pin) in pins.withIndex()) {
        circuit.addWire(
            chip.getPinSocket(pin),
            AD200Topology.railSocket(rail, chip.placedIc.startColumn + offset),
            WireColor.BLACK
        )
    }
}

/**
 * Code-conversion, decoding, comparison and data-selection experiments, each shipped as two
 * variations of one build.
 *
 * Variations share a `title` and are distinguished by `subtitle`, so the catalog can render a
 * single heading with a picker beneath it. Every entry's `id` is prefixed with its experiment
 * number (`exp06_` .. `exp09_`, plus `exp18_`) — that prefix is what groups them.
 *
 * The pairing is always the same argument: variation A builds the circuit the way the apparatus
 * list intends, and variation B rebuilds the identical function from a different gate set. B is
 * not a fallback. Seeing the same truth table come out of a NAND-plus-inverter decode as out of
 * a straight AND decode is the point of the exercise.
 */

/**
 * The 7448's segment font, bit 0 = segment a through bit 6 = segment g.
 *
 * Mirrors `Chip7448.font7448` in the engine — deliberately a copy rather than a shared constant,
 * because the engine's table is private and these presets must not widen the core API. If the
 * two ever disagree, `everyPresetPassesItsOwnTruthTableThroughTheRealEngine` fails immediately,
 * so the duplication cannot silently drift.
 */
private val FONT_7448 = intArrayOf(
    0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07,
    0x7F, 0x6F, 0x58, 0x4C, 0x62, 0x69, 0x78, 0x00
)

/** Segment states a..g (in LED0..LED6 order) that the 7448 shows for one BCD code. */
private fun segmentsOf(code: Int): List<Boolean> {
    val mask = FONT_7448[code and 0x0F]
    return (0..6).map { (mask and (1 shl it)) != 0 }
}
val converterLabs: List<LabExperiment> = listOf(

    // Exp 06 A: Binary -> Gray. Three XOR gates and a wire.
    LabExperiment(
        id = "exp06_binary_to_gray",
        labNumber = 6,
        title = "4-Bit Binary-Gray Code Converter",
        subtitle = "Variation A · 7486-based binary-to-Gray",
        description = "Convert a 4-bit binary word to reflected Gray code with a single 7486. " +
            "Each Gray bit is the XOR of two adjacent binary bits — G2 = B3 ⊕ B2, G1 = B2 ⊕ B1, " +
            "G0 = B1 ⊕ B0 — while the most significant bit passes through untouched. Gray code " +
            "changes exactly one bit between consecutive values, which is why shaft encoders use it.",
        targetChips = listOf("7486"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0, 1, 2, 3),
        inputLabels = listOf("B0 (SW0)", "B1 (SW1)", "B2 (SW2)", "B3 (SW3)"),
        outputLabels = listOf("G0 (LED0)", "G1 (LED1)", "G2 (LED2)", "G3 (LED3)"),
        objectives = listOf(
            LabObjective("obj1", "Power the 7486", "Pin 14 to the +5V rail, Pin 7 to the ground rail"),
            LabObjective("obj2", "Wire the three XOR stages", "B1/B0 to Gate 1, B2/B1 to Gate 2, B3/B2 to Gate 3"),
            LabObjective("obj3", "Pass B3 straight through", "Jumper SW3 to LED3 — G3 equals B3, so no gate is involved"),
            LabObjective("obj4", "Ground the spare gate", "Tie Pins 12 and 13 to ground; a floating TTL input reads HIGH"),
            LabObjective("obj5", "Verify the unit-distance property", "Step the binary count 0000..1111 and confirm exactly one Gray bit changes per step")
        ),
        expectedFunction = { inputs ->
            val b0 = inputs[0]
            val b1 = inputs[1]
            val b2 = inputs[2]
            val b3 = inputs[3]
            // G3 is B3 verbatim; every lower bit is a difference between neighbours.
            listOf(b1 xor b0, b2 xor b1, b3 xor b2, b3)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            val xor = circuit.addChip("7486", trench = 1, startColumn = 10)
            powerChip(circuit, xor)

            // Gate 1 (Pins 1,2 -> 3): G0 = B1 ⊕ B0
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW0, xor.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(xor.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

            // Gate 2 (Pins 4,5 -> 6): G1 = B2 ⊕ B1
            circuit.addWire(AD200Topology.TERM_SW2, xor.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(5), WireColor.YELLOW)
            circuit.addWire(xor.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.GREEN)

            // Gate 3 (Pins 9,10 -> 8): G2 = B3 ⊕ B2
            circuit.addWire(AD200Topology.TERM_SW3, xor.getPinSocket(9), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW2, xor.getPinSocket(10), WireColor.ORANGE)
            circuit.addWire(xor.getPinSocket(8), AD200Topology.TERM_LED2, WireColor.GREEN)

            // G3 = B3. No gate is needed for the most significant bit, so it is a bare jumper
            // from the switch post to the indicator post — the converter's one free bit.
            circuit.addWire(AD200Topology.TERM_SW3, AD200Topology.TERM_LED3, WireColor.WHITE)

            // Gate 4 (Pins 12,13 -> 11) is unused. Ground both inputs.
            groundUnusedInputs(circuit, xor, listOf(12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.step()
        }
    ),

    // Exp 06 B: Gray -> Binary. Same chip, same three gates, but chained instead of parallel.
    LabExperiment(
        id = "exp06_gray_to_binary",
        labNumber = 6,
        title = "4-Bit Binary-Gray Code Converter",
        subtitle = "Variation B · 7486-based Gray-to-binary cascade",
        description = "Run the converter backwards: B3 = G3, then B2 = B3 ⊕ G2, B1 = B2 ⊕ G1, " +
            "B0 = B1 ⊕ G0. Note what changed from Variation A. There, each XOR took two switch " +
            "inputs and the three gates were independent — one gate delay from input to every " +
            "output. Here each stage consumes the previous stage's output, so the chain is four " +
            "levels deep and the relaxation solver must settle through all of them before the " +
            "low bits are trustworthy. Same package, same gate count, very different circuit.",
        targetChips = listOf("7486"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0, 1, 2, 3),
        inputLabels = listOf("G0 (SW0)", "G1 (SW1)", "G2 (SW2)", "G3 (SW3)"),
        outputLabels = listOf("B0 (LED0)", "B1 (LED1)", "B2 (LED2)", "B3 (LED3)"),
        objectives = listOf(
            LabObjective("obj1", "Power the 7486", "Pin 14 to the +5V rail, Pin 7 to the ground rail"),
            LabObjective("obj2", "Pass G3 straight through", "Jumper SW3 to LED3 — the MSB is common to both codes"),
            LabObjective("obj3", "Build the first stage", "B3 (SW3) and G2 (SW2) into Gate 1; Pin 3 carries B2"),
            LabObjective("obj4", "Chain stages two and three", "Pin 3 to Pin 4 with G1 on Pin 5; Pin 6 to Pin 9 with G0 on Pin 10"),
            LabObjective("obj5", "Observe the ripple", "Each stage waits on the one above it, unlike the parallel forward converter")
        ),
        expectedFunction = { inputs ->
            val g0 = inputs[0]
            val g1 = inputs[1]
            val g2 = inputs[2]
            val g3 = inputs[3]
            // Running XOR from the top down: every binary bit depends on all Gray bits above it.
            val b3 = g3
            val b2 = b3 xor g2
            val b1 = b2 xor g1
            val b0 = b1 xor g0
            listOf(b0, b1, b2, b3)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            val xor = circuit.addChip("7486", trench = 1, startColumn = 10)
            powerChip(circuit, xor)

            // B3 = G3, taken straight off the switch post to the indicator post.
            circuit.addWire(AD200Topology.TERM_SW3, AD200Topology.TERM_LED3, WireColor.WHITE)

            // Stage 1, Gate 1 (Pins 1,2 -> 3): B2 = B3 ⊕ G2. B3 is still just SW3 at this point.
            circuit.addWire(AD200Topology.TERM_SW3, xor.getPinSocket(1), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW2, xor.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(xor.getPinSocket(3), AD200Topology.TERM_LED2, WireColor.GREEN)

            // Stage 2, Gate 2 (Pins 4,5 -> 6): B1 = B2 ⊕ G1. Pin 3 feeds Pin 4 — this is the
            // cascade. The solver cannot know B1 until B2 has settled.
            circuit.addWire(xor.getPinSocket(3), xor.getPinSocket(4), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(5), WireColor.YELLOW)
            circuit.addWire(xor.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.GREEN)

            // Stage 3, Gate 3 (Pins 9,10 -> 8): B0 = B1 ⊕ G0.
            circuit.addWire(xor.getPinSocket(6), xor.getPinSocket(9), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW0, xor.getPinSocket(10), WireColor.YELLOW)
            circuit.addWire(xor.getPinSocket(8), AD200Topology.TERM_LED0, WireColor.GREEN)

            // Gate 4 (Pins 12,13 -> 11) is unused. Ground both inputs.
            groundUnusedInputs(circuit, xor, listOf(12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.step()
        }
    ),

    // Exp 07 A: 3-to-8 octal decode from nine 3-input ANDs, of which eight are wanted.
    LabExperiment(
        id = "exp07_octal_decoder_7411",
        labNumber = 7,
        title = "3-to-8 Line Binary-to-Octal Decoder",
        subtitle = "Variation A · 7411-based decoder",
        description = "Decode a 3-bit binary word into eight mutually exclusive octal lines. A " +
            "7404 supplies the complements Ā, B̄ and C̄, and three 7411 triple 3-input AND " +
            "packages form the eight minterms — O0 = Ā·B̄·C̄ through O7 = A·B·C. Nine gates are " +
            "available and eight are used, so one gate is deliberately grounded. Exactly one " +
            "output is HIGH for any input, which is what makes this a decoder rather than a " +
            "collection of AND gates.",
        targetChips = listOf("7411", "7404"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0, 1, 2, 3, 4, 5, 6, 7),
        inputLabels = listOf("A (SW0)", "B (SW1)", "C (SW2)"),
        outputLabels = listOf(
            "O0 (LED0)", "O1 (LED1)", "O2 (LED2)", "O3 (LED3)",
            "O4 (LED4)", "O5 (LED5)", "O6 (LED6)", "O7 (LED7)"
        ),
        objectives = listOf(
            LabObjective("obj1", "Mount and power four packages", "7411s at Cols 10, 24, 38 in trench 1; 7404 at Col 10 in trench 2"),
            LabObjective("obj2", "Generate the three complements", "SW0/SW1/SW2 into 7404 Pins 1, 3, 5; read Ā, B̄, C̄ off Pins 2, 4, 6"),
            LabObjective("obj3", "Form the eight minterms", "Each 7411 gate takes one true/complement combination of A, B and C"),
            LabObjective("obj4", "Mind the 7411 gate 1 pinout", "Gate 1's inputs are Pins 1, 2 and 13 and its output is Pin 12, not Pin 3"),
            LabObjective("obj5", "Ground every spare input", "The ninth AND gate and the three spare inverters go to the ground rail"),
            LabObjective("obj6", "Verify one-hot decoding", "Sweep 000..111 and confirm exactly one of the eight LEDs lights per code")
        ),
        expectedFunction = { inputs ->
            // A is the least significant bit, so the lit line is the input read as a binary number.
            val code = (if (inputs[2]) 4 else 0) or (if (inputs[1]) 2 else 0) or (if (inputs[0]) 1 else 0)
            (0..7).map { it == code }
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            // The inverter sits in trench 2 so the three AND packages get the whole of trench 1
            // at the fourteen-column spacing; four 14-pin parts in one trench would fit, but the
            // complement bus is easier to follow running across the board rather than along it.
            val inv = circuit.addChip("7404", trench = 2, startColumn = 10)
            val and1 = circuit.addChip("7411", trench = 1, startColumn = 10)
            val and2 = circuit.addChip("7411", trench = 1, startColumn = 24)
            val and3 = circuit.addChip("7411", trench = 1, startColumn = 38)

            powerChip(circuit, inv)
            powerChip(circuit, and1)
            powerChip(circuit, and2)
            powerChip(circuit, and3)

            // Complement generation: Ā on Pin 2, B̄ on Pin 4, C̄ on Pin 6.
            circuit.addWire(AD200Topology.TERM_SW0, inv.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, inv.getPinSocket(3), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, inv.getPinSocket(5), WireColor.PURPLE)
            val notA = inv.getPinSocket(2)
            val notB = inv.getPinSocket(4)
            val notC = inv.getPinSocket(6)

            // Package 1 -- the three codes with C low and A/B varying, plus O0.
            // Gate 1 (Pins 1,2,13 -> 12): O0 = Ā·B̄·C̄
            circuit.addWire(notA, and1.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(notB, and1.getPinSocket(2), WireColor.BROWN)
            circuit.addWire(notC, and1.getPinSocket(13), WireColor.WHITE)
            circuit.addWire(and1.getPinSocket(12), AD200Topology.TERM_LED0, WireColor.GREEN)

            // Gate 2 (Pins 3,4,5 -> 6): O1 = A·B̄·C̄
            circuit.addWire(AD200Topology.TERM_SW0, and1.getPinSocket(3), WireColor.YELLOW)
            circuit.addWire(notB, and1.getPinSocket(4), WireColor.BROWN)
            circuit.addWire(notC, and1.getPinSocket(5), WireColor.WHITE)
            circuit.addWire(and1.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.GREEN)

            // Gate 3 (Pins 9,10,11 -> 8): O2 = Ā·B·C̄
            circuit.addWire(notA, and1.getPinSocket(9), WireColor.GRAY)
            circuit.addWire(AD200Topology.TERM_SW1, and1.getPinSocket(10), WireColor.ORANGE)
            circuit.addWire(notC, and1.getPinSocket(11), WireColor.WHITE)
            circuit.addWire(and1.getPinSocket(8), AD200Topology.TERM_LED2, WireColor.GREEN)

            // Package 2 -- O3, then the first two codes with C high.
            // Gate 1: O3 = A·B·C̄
            circuit.addWire(AD200Topology.TERM_SW0, and2.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, and2.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(notC, and2.getPinSocket(13), WireColor.WHITE)
            circuit.addWire(and2.getPinSocket(12), AD200Topology.TERM_LED3, WireColor.GREEN)

            // Gate 2: O4 = Ā·B̄·C
            circuit.addWire(notA, and2.getPinSocket(3), WireColor.GRAY)
            circuit.addWire(notB, and2.getPinSocket(4), WireColor.BROWN)
            circuit.addWire(AD200Topology.TERM_SW2, and2.getPinSocket(5), WireColor.PURPLE)
            circuit.addWire(and2.getPinSocket(6), AD200Topology.TERM_LED4, WireColor.GREEN)

            // Gate 3: O5 = A·B̄·C
            circuit.addWire(AD200Topology.TERM_SW0, and2.getPinSocket(9), WireColor.YELLOW)
            circuit.addWire(notB, and2.getPinSocket(10), WireColor.BROWN)
            circuit.addWire(AD200Topology.TERM_SW2, and2.getPinSocket(11), WireColor.PURPLE)
            circuit.addWire(and2.getPinSocket(8), AD200Topology.TERM_LED5, WireColor.GREEN)

            // Package 3 -- the top two codes. Gate 3 is the spare.
            // Gate 1: O6 = Ā·B·C
            circuit.addWire(notA, and3.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(AD200Topology.TERM_SW1, and3.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, and3.getPinSocket(13), WireColor.PURPLE)
            circuit.addWire(and3.getPinSocket(12), AD200Topology.TERM_LED6, WireColor.GREEN)

            // Gate 2: O7 = A·B·C
            circuit.addWire(AD200Topology.TERM_SW0, and3.getPinSocket(3), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, and3.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, and3.getPinSocket(5), WireColor.PURPLE)
            circuit.addWire(and3.getPinSocket(6), AD200Topology.TERM_LED7, WireColor.GREEN)

            // Spares. Left open, the ninth AND gate would read 1·1·1 and sit HIGH, and the three
            // idle inverters would sit LOW — states that look like real decoded outputs to anyone
            // probing the board. Ground them and they are unambiguously idle.
            groundUnusedInputs(circuit, and3, listOf(9, 10, 11))
            groundUnusedInputs(circuit, inv, listOf(9, 11, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    ),

    // Exp 07 B: the same decode from NAND packages already in the catalog, at the cost of a
    // second inverter package to undo the inversion the 7410 introduces.
    LabExperiment(
        id = "exp07_octal_decoder_7410",
        labNumber = 7,
        title = "3-to-8 Line Binary-to-Octal Decoder",
        subtitle = "Variation B · 7410-based decoder",
        description = "Build the identical decoder from 7410 triple 3-input NAND packages. The " +
            "7410 has the 7411's exact pinout with inverted outputs, so each gate produces the " +
            "complement of its minterm and the selected line reads LOW while the other seven " +
            "read HIGH — an active-low decoder, which is what a real 74138 gives you. Restoring " +
            "active-high indicators costs an extra inversion stage: eight output inverters on " +
            "top of the three needed for Ā, B̄ and C̄, which is eleven, so two 7404 packages. " +
            "That second package is the whole price of not having an AND part on hand.",
        targetChips = listOf("7410", "7404"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0, 1, 2, 3, 4, 5, 6, 7),
        inputLabels = listOf("A (SW0)", "B (SW1)", "C (SW2)"),
        outputLabels = listOf(
            "O0 (LED0)", "O1 (LED1)", "O2 (LED2)", "O3 (LED3)",
            "O4 (LED4)", "O5 (LED5)", "O6 (LED6)", "O7 (LED7)"
        ),
        objectives = listOf(
            LabObjective("obj1", "Mount and power five packages", "7410s at Cols 10, 24, 38 in trench 1; 7404s at Cols 10 and 24 in trench 2"),
            LabObjective("obj2", "Generate Ā, B̄ and C̄", "First 7404, gates 1 to 3; the same complement bus as Variation A"),
            LabObjective("obj3", "Form the eight active-low minterms", "Each 7410 gate outputs the complement of one decoded line"),
            LabObjective("obj4", "Add the output inversion stage", "Gates 4 to 6 of the first 7404 and gates 1 to 5 of the second restore active-high"),
            LabObjective("obj5", "Ground every spare input", "The ninth NAND gate and the twelfth inverter go to the ground rail"),
            LabObjective("obj6", "Compare against Variation A", "Identical truth table, two extra gate delays, one extra package")
        ),
        expectedFunction = { inputs ->
            val code = (if (inputs[2]) 4 else 0) or (if (inputs[1]) 2 else 0) or (if (inputs[0]) 1 else 0)
            (0..7).map { it == code }
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            val inv1 = circuit.addChip("7404", trench = 2, startColumn = 10)
            val inv2 = circuit.addChip("7404", trench = 2, startColumn = 24)
            val nand1 = circuit.addChip("7410", trench = 1, startColumn = 10)
            val nand2 = circuit.addChip("7410", trench = 1, startColumn = 24)
            val nand3 = circuit.addChip("7410", trench = 1, startColumn = 38)

            powerChip(circuit, inv1)
            powerChip(circuit, inv2)
            powerChip(circuit, nand1)
            powerChip(circuit, nand2)
            powerChip(circuit, nand3)

            // First 7404, gates 1 to 3: the complement bus, exactly as in Variation A.
            circuit.addWire(AD200Topology.TERM_SW0, inv1.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, inv1.getPinSocket(3), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, inv1.getPinSocket(5), WireColor.PURPLE)
            val notA = inv1.getPinSocket(2)
            val notB = inv1.getPinSocket(4)
            val notC = inv1.getPinSocket(6)

            // NAND package 1: complements of O0, O1, O2.
            circuit.addWire(notA, nand1.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(notB, nand1.getPinSocket(2), WireColor.BROWN)
            circuit.addWire(notC, nand1.getPinSocket(13), WireColor.WHITE)

            circuit.addWire(AD200Topology.TERM_SW0, nand1.getPinSocket(3), WireColor.YELLOW)
            circuit.addWire(notB, nand1.getPinSocket(4), WireColor.BROWN)
            circuit.addWire(notC, nand1.getPinSocket(5), WireColor.WHITE)

            circuit.addWire(notA, nand1.getPinSocket(9), WireColor.GRAY)
            circuit.addWire(AD200Topology.TERM_SW1, nand1.getPinSocket(10), WireColor.ORANGE)
            circuit.addWire(notC, nand1.getPinSocket(11), WireColor.WHITE)

            // NAND package 2: complements of O3, O4, O5.
            circuit.addWire(AD200Topology.TERM_SW0, nand2.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, nand2.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(notC, nand2.getPinSocket(13), WireColor.WHITE)

            circuit.addWire(notA, nand2.getPinSocket(3), WireColor.GRAY)
            circuit.addWire(notB, nand2.getPinSocket(4), WireColor.BROWN)
            circuit.addWire(AD200Topology.TERM_SW2, nand2.getPinSocket(5), WireColor.PURPLE)

            circuit.addWire(AD200Topology.TERM_SW0, nand2.getPinSocket(9), WireColor.YELLOW)
            circuit.addWire(notB, nand2.getPinSocket(10), WireColor.BROWN)
            circuit.addWire(AD200Topology.TERM_SW2, nand2.getPinSocket(11), WireColor.PURPLE)

            // NAND package 3: complements of O6 and O7. Gate 3 is the spare.
            circuit.addWire(notA, nand3.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(AD200Topology.TERM_SW1, nand3.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, nand3.getPinSocket(13), WireColor.PURPLE)

            circuit.addWire(AD200Topology.TERM_SW0, nand3.getPinSocket(3), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, nand3.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, nand3.getPinSocket(5), WireColor.PURPLE)

            // Output inversion stage. The first 7404's remaining three gates take O0..O2; the
            // second package takes O3..O7. Gate pinout on the 7404 runs 1->2, 3->4, 5->6, 9->8,
            // 11->10, 13->12 -- the second half reads right to left, which is easy to get wrong.
            circuit.addWire(nand1.getPinSocket(12), inv1.getPinSocket(9), WireColor.BLUE)
            circuit.addWire(inv1.getPinSocket(8), AD200Topology.TERM_LED0, WireColor.GREEN)
            circuit.addWire(nand1.getPinSocket(6), inv1.getPinSocket(11), WireColor.BLUE)
            circuit.addWire(inv1.getPinSocket(10), AD200Topology.TERM_LED1, WireColor.GREEN)
            circuit.addWire(nand1.getPinSocket(8), inv1.getPinSocket(13), WireColor.BLUE)
            circuit.addWire(inv1.getPinSocket(12), AD200Topology.TERM_LED2, WireColor.GREEN)

            circuit.addWire(nand2.getPinSocket(12), inv2.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(inv2.getPinSocket(2), AD200Topology.TERM_LED3, WireColor.GREEN)
            circuit.addWire(nand2.getPinSocket(6), inv2.getPinSocket(3), WireColor.BLUE)
            circuit.addWire(inv2.getPinSocket(4), AD200Topology.TERM_LED4, WireColor.GREEN)
            circuit.addWire(nand2.getPinSocket(8), inv2.getPinSocket(5), WireColor.BLUE)
            circuit.addWire(inv2.getPinSocket(6), AD200Topology.TERM_LED5, WireColor.GREEN)
            circuit.addWire(nand3.getPinSocket(12), inv2.getPinSocket(9), WireColor.BLUE)
            circuit.addWire(inv2.getPinSocket(8), AD200Topology.TERM_LED6, WireColor.GREEN)
            circuit.addWire(nand3.getPinSocket(6), inv2.getPinSocket(11), WireColor.BLUE)
            circuit.addWire(inv2.getPinSocket(10), AD200Topology.TERM_LED7, WireColor.GREEN)

            // Spares: the ninth NAND gate and the twelfth inverter.
            groundUnusedInputs(circuit, nand3, listOf(9, 10, 11))
            groundUnusedInputs(circuit, inv2, listOf(13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    ),

    // Exp 08 A: 2-bit magnitude comparator around a 74266 equality core.
    //
    // Derivation, since the greater-than expression is not obvious. Compare A = A1A0 against
    // B = B1B0 the way you compare two decimal numbers: look at the high digit first, and only
    // if it ties does the low digit matter.
    //
    //   A > B  when  A1 > B1                       -- the high bits already decide it:  A1·B̄1
    //          or    A1 = B1  and  A0 > B0         -- high bits tie, low bits decide: (A1⊙B1)·A0·B̄0
    //
    // "one bit greater than another" is just that bit AND the complement of the other, and
    // "two bits equal" is their XNOR, so:
    //
    //   A > B = A1·B̄1 + (A1⊙B1)·A0·B̄0
    //   A < B = Ā1·B1 + (A1⊙B1)·Ā0·B0        (the same argument with the operands swapped)
    //   A = B = (A1⊙B1)·(A0⊙B0)              (both bit pairs must agree)
    //
    // The XNOR term (A1⊙B1) appears in all three, which is why the equality core is built first
    // and shared three ways rather than recomputed per output.
    LabExperiment(
        id = "exp08_comparator_74266",
        labNumber = 8,
        title = "2-Bit Magnitude Comparator",
        subtitle = "Variation A · 74266-based comparator",
        description = "Compare two 2-bit numbers and light one of three verdicts: A>B, A=B or " +
            "A<B. A 74266 quad XNOR tests the two bit pairs for equality, a 7404 supplies the " +
            "complements, two 7408 packages form the product terms — the (A1⊙B1)·A0·B̄0 terms " +
            "are 3-input, so each takes a pair of cascaded 2-input gates — and a 7432 sums each " +
            "verdict's two terms. The three outputs are mutually exclusive and exactly one is " +
            "always HIGH: two numbers are greater, equal or less, never none and never two.",
        targetChips = listOf("74266", "7404", "7408", "7432"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0, 1, 2),
        inputLabels = listOf("A1 (SW0)", "A0 (SW1)", "B1 (SW2)", "B0 (SW3)"),
        outputLabels = listOf("A>B (LED0)", "A=B (LED1)", "A<B (LED2)"),
        objectives = listOf(
            LabObjective("obj1", "Mount and power five packages", "74266, 7404, 7408 in trench 1 at Cols 10/24/38; 7408, 7432 in trench 2 at Cols 10/24"),
            LabObjective("obj2", "Mind the 74266 pinout", "Outputs are Pins 3, 4, 10, 11 — Pins 5, 6, 8, 9 are inputs, unlike a 7486"),
            LabObjective("obj3", "Build the equality core", "A1/B1 into Gate 1, A0/B0 into Gate 2; Pin 3 is A1⊙B1 and Pin 4 is A0⊙B0"),
            LabObjective("obj4", "Cascade the 3-input products", "A 7408 gate has two inputs, so (A1⊙B1)·A0·B̄0 needs two of them in series"),
            LabObjective("obj5", "Sum each verdict", "7432 Gate 1 gives A>B from its two terms; Gate 2 gives A<B"),
            LabObjective("obj6", "Verify mutual exclusivity", "Sweep all sixteen operand pairs and confirm exactly one LED lights each time")
        ),
        expectedFunction = { inputs ->
            val a = (if (inputs[0]) 2 else 0) or (if (inputs[1]) 1 else 0)
            val b = (if (inputs[2]) 2 else 0) or (if (inputs[3]) 1 else 0)
            listOf(a > b, a == b, a < b)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            val xnor = circuit.addChip("74266", trench = 1, startColumn = 10)
            val inv = circuit.addChip("7404", trench = 1, startColumn = 24)
            val and1 = circuit.addChip("7408", trench = 1, startColumn = 38)
            val and2 = circuit.addChip("7408", trench = 2, startColumn = 10)
            val orGate = circuit.addChip("7432", trench = 2, startColumn = 24)

            powerChip(circuit, xnor)
            powerChip(circuit, inv)
            powerChip(circuit, and1)
            powerChip(circuit, and2)
            powerChip(circuit, orGate)

            // Equality core. 74266 Gate 1 is (Pins 1,2 -> 3); Gate 2 is (Pins 5,6 -> 4). The
            // output of gate 2 sits between the two gates' inputs on this package, so read the
            // pin map, do not pattern-match it against the 7486.
            circuit.addWire(AD200Topology.TERM_SW0, xnor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW2, xnor.getPinSocket(2), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW1, xnor.getPinSocket(5), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW3, xnor.getPinSocket(6), WireColor.BROWN)
            val eq1 = xnor.getPinSocket(3)
            val eq0 = xnor.getPinSocket(4)

            // Complements. Ā1 on Pin 2, Ā0 on Pin 4, B̄1 on Pin 6, B̄0 on Pin 8.
            circuit.addWire(AD200Topology.TERM_SW0, inv.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, inv.getPinSocket(3), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, inv.getPinSocket(5), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW3, inv.getPinSocket(9), WireColor.BROWN)
            val notA1 = inv.getPinSocket(2)
            val notA0 = inv.getPinSocket(4)
            val notB1 = inv.getPinSocket(6)
            val notB0 = inv.getPinSocket(8)

            // First AND package: the four 2-input products, all four gates used.
            // Gate 1 (1,2 -> 3): A1·B̄1, the outright greater-than on the high bit.
            circuit.addWire(AD200Topology.TERM_SW0, and1.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(notB1, and1.getPinSocket(2), WireColor.GRAY)
            // Gate 2 (4,5 -> 6): A0·B̄0, the low-bit greater-than awaiting its tie test.
            circuit.addWire(AD200Topology.TERM_SW1, and1.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(notB0, and1.getPinSocket(5), WireColor.GRAY)
            // Gate 3 (9,10 -> 8): Ā1·B1, the mirror of gate 1.
            circuit.addWire(notA1, and1.getPinSocket(9), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW2, and1.getPinSocket(10), WireColor.PURPLE)
            // Gate 4 (12,13 -> 11): Ā0·B0, the mirror of gate 2.
            circuit.addWire(notA0, and1.getPinSocket(12), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW3, and1.getPinSocket(13), WireColor.BROWN)

            // Second AND package: the tie tests. This is where the 3-input products finish --
            // gate 1 completes (A1⊙B1)·A0·B̄0 by ANDing the equality bit onto the partial product
            // the first package left on its Pin 6.
            circuit.addWire(eq1, and2.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(and1.getPinSocket(6), and2.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(eq1, and2.getPinSocket(4), WireColor.BLUE)
            circuit.addWire(and1.getPinSocket(11), and2.getPinSocket(5), WireColor.WHITE)
            // Gate 3 (9,10 -> 8): A=B needs no OR stage, both bit pairs equal is the whole test.
            circuit.addWire(eq1, and2.getPinSocket(9), WireColor.BLUE)
            circuit.addWire(eq0, and2.getPinSocket(10), WireColor.BLUE)
            circuit.addWire(and2.getPinSocket(8), AD200Topology.TERM_LED1, WireColor.GREEN)

            // Sum stage. Gate 1: A>B = A1·B̄1 + (A1⊙B1)·A0·B̄0. Gate 2: the same for A<B.
            circuit.addWire(and1.getPinSocket(3), orGate.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(and2.getPinSocket(3), orGate.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(orGate.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
            circuit.addWire(and1.getPinSocket(8), orGate.getPinSocket(4), WireColor.WHITE)
            circuit.addWire(and2.getPinSocket(6), orGate.getPinSocket(5), WireColor.WHITE)
            circuit.addWire(orGate.getPinSocket(6), AD200Topology.TERM_LED2, WireColor.GREEN)

            // Spares: two XNOR gates, two inverters, one AND gate, two OR gates.
            groundUnusedInputs(circuit, xnor, listOf(8, 9, 12, 13))
            groundUnusedInputs(circuit, inv, listOf(11, 13))
            groundUnusedInputs(circuit, and2, listOf(12, 13))
            groundUnusedInputs(circuit, orGate, listOf(9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.step()
        }
    ),

    // Exp 08 B: the same comparator with the XNOR synthesised, so no part outside the original
    // catalog is needed.
    LabExperiment(
        id = "exp08_comparator_7486",
        labNumber = 8,
        title = "2-Bit Magnitude Comparator",
        subtitle = "Variation B · 7486-based comparator",
        description = "Rebuild the comparator without an XNOR package. A ⊙ B is NOT(A ⊕ B), so " +
            "a 7486 gate followed by an inverter does the job — and the inverter package was " +
            "already on the board for Ā1, Ā0, B̄1 and B̄0. Four complements plus two equality " +
            "inversions is six, which is exactly one 7404 with nothing left over. The product " +
            "and sum stages are untouched from Variation A; only the equality core differs, and " +
            "the cost is one more gate delay in front of every output.",
        targetChips = listOf("7486", "7404", "7408", "7432"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0, 1, 2),
        inputLabels = listOf("A1 (SW0)", "A0 (SW1)", "B1 (SW2)", "B0 (SW3)"),
        outputLabels = listOf("A>B (LED0)", "A=B (LED1)", "A<B (LED2)"),
        objectives = listOf(
            LabObjective("obj1", "Mount and power five packages", "7486, 7404, 7408 in trench 1 at Cols 10/24/38; 7408, 7432 in trench 2 at Cols 10/24"),
            LabObjective("obj2", "Detect inequality first", "7486 Gate 1 gives A1 ⊕ B1 on Pin 3; Gate 2 gives A0 ⊕ B0 on Pin 6"),
            LabObjective("obj3", "Invert to get equality", "7404 gates 1 and 2 turn each XOR into the XNOR the comparator wants"),
            LabObjective("obj4", "Fill the rest of the 7404", "Gates 3 to 6 supply Ā1, Ā0, B̄1 and B̄0 — all six inverters are in use"),
            LabObjective("obj5", "Reuse the product and sum stages", "Identical to Variation A from the 7408s onward"),
            LabObjective("obj6", "Confirm the seals agree", "Both variations must produce the same sixteen-row truth table")
        ),
        expectedFunction = { inputs ->
            val a = (if (inputs[0]) 2 else 0) or (if (inputs[1]) 1 else 0)
            val b = (if (inputs[2]) 2 else 0) or (if (inputs[3]) 1 else 0)
            listOf(a > b, a == b, a < b)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            val xor = circuit.addChip("7486", trench = 1, startColumn = 10)
            val inv = circuit.addChip("7404", trench = 1, startColumn = 24)
            val and1 = circuit.addChip("7408", trench = 1, startColumn = 38)
            val and2 = circuit.addChip("7408", trench = 2, startColumn = 10)
            val orGate = circuit.addChip("7432", trench = 2, startColumn = 24)

            powerChip(circuit, xor)
            powerChip(circuit, inv)
            powerChip(circuit, and1)
            powerChip(circuit, and2)
            powerChip(circuit, orGate)

            // Inequality detection: Pin 3 is A1 ⊕ B1, Pin 6 is A0 ⊕ B0.
            circuit.addWire(AD200Topology.TERM_SW0, xor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW2, xor.getPinSocket(2), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW3, xor.getPinSocket(5), WireColor.BROWN)

            // All six inverters: two to flip the XORs into XNORs, four for the operand complements.
            circuit.addWire(xor.getPinSocket(3), inv.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(xor.getPinSocket(6), inv.getPinSocket(3), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW0, inv.getPinSocket(5), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, inv.getPinSocket(9), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, inv.getPinSocket(11), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW3, inv.getPinSocket(13), WireColor.BROWN)
            val eq1 = inv.getPinSocket(2)
            val eq0 = inv.getPinSocket(4)
            val notA1 = inv.getPinSocket(6)
            val notA0 = inv.getPinSocket(8)
            val notB1 = inv.getPinSocket(10)
            val notB0 = inv.getPinSocket(12)

            // Product terms, unchanged from Variation A.
            circuit.addWire(AD200Topology.TERM_SW0, and1.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(notB1, and1.getPinSocket(2), WireColor.GRAY)
            circuit.addWire(AD200Topology.TERM_SW1, and1.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(notB0, and1.getPinSocket(5), WireColor.GRAY)
            circuit.addWire(notA1, and1.getPinSocket(9), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW2, and1.getPinSocket(10), WireColor.PURPLE)
            circuit.addWire(notA0, and1.getPinSocket(12), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW3, and1.getPinSocket(13), WireColor.BROWN)

            // Tie tests and equality, unchanged from Variation A.
            circuit.addWire(eq1, and2.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(and1.getPinSocket(6), and2.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(eq1, and2.getPinSocket(4), WireColor.BLUE)
            circuit.addWire(and1.getPinSocket(11), and2.getPinSocket(5), WireColor.WHITE)
            circuit.addWire(eq1, and2.getPinSocket(9), WireColor.BLUE)
            circuit.addWire(eq0, and2.getPinSocket(10), WireColor.BLUE)
            circuit.addWire(and2.getPinSocket(8), AD200Topology.TERM_LED1, WireColor.GREEN)

            // Sum stage, unchanged from Variation A.
            circuit.addWire(and1.getPinSocket(3), orGate.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(and2.getPinSocket(3), orGate.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(orGate.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
            circuit.addWire(and1.getPinSocket(8), orGate.getPinSocket(4), WireColor.WHITE)
            circuit.addWire(and2.getPinSocket(6), orGate.getPinSocket(5), WireColor.WHITE)
            circuit.addWire(orGate.getPinSocket(6), AD200Topology.TERM_LED2, WireColor.GREEN)

            // Spares: two XOR gates, one AND gate, two OR gates. The 7404 has none.
            groundUnusedInputs(circuit, xor, listOf(9, 10, 12, 13))
            groundUnusedInputs(circuit, and2, listOf(12, 13))
            groundUnusedInputs(circuit, orGate, listOf(9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.step()
        }
    ),

    // Exp 09 A: 4:1 multiplexer.
    //
    // The thing worth noticing before wiring anything: every product term in the mux equation is
    // a 3-input AND -- one data bit and both select bits, true or complemented. The 7408 is a
    // quad *2-input* AND, so no single gate can form a term. Each one is split in two: a first
    // gate ANDs the two select literals into a channel-enable minterm, a second gate ANDs the
    // data bit onto that minterm. Four terms x two gates = eight, which is exactly two packages,
    // and that is why the apparatus list asks for two 7408s to build four AND terms.
    //
    // Splitting them this way rather than arbitrarily also buys something: the intermediate
    // minterms are the channel-select decode, so they can go straight to indicators.
    LabExperiment(
        id = "exp09_mux_4to1",
        labNumber = 9,
        title = "4:1 Multiplexer and 1:4 Demultiplexer",
        subtitle = "Variation A · 4:1 multiplexer",
        description = "Route one of four data inputs to a single output under a 2-bit select " +
            "code: Y = D0·S̄1·S̄0 + D1·S̄1·S0 + D2·S1·S̄0 + D3·S1·S0. Six switches drive it — four " +
            "data, two select — and the console has eight, so there is room. A 7404 supplies S̄1 " +
            "and S̄0, the first 7408 decodes the four channel-enable minterms, the second gates " +
            "each data bit with its enable, and a 7432 sums the four terms in a two-then-one OR " +
            "tree. The four minterms are also brought out to LED1..LED4 so the live channel is " +
            "visible next to the data it is passing.",
        targetChips = listOf("7404", "7408", "7432"),
        switchIndices = listOf(0, 1, 2, 3, 4, 5),
        ledIndices = listOf(0, 1, 2, 3, 4),
        inputLabels = listOf("D0 (SW0)", "D1 (SW1)", "D2 (SW2)", "D3 (SW3)", "S1 (SW4)", "S0 (SW5)"),
        outputLabels = listOf("Y (LED0)", "CH0 (LED1)", "CH1 (LED2)", "CH2 (LED3)", "CH3 (LED4)"),
        objectives = listOf(
            LabObjective("obj1", "Mount and power four packages", "7404, 7408, 7408 in trench 1 at Cols 10/24/38; 7432 in trench 2 at Col 10"),
            LabObjective("obj2", "Complement the select lines", "SW4 and SW5 into 7404 Pins 1 and 3; S̄1 on Pin 2, S̄0 on Pin 4"),
            LabObjective("obj3", "Decode four channel enables", "First 7408: each gate ANDs one true/complement pair of the select bits"),
            LabObjective("obj4", "Gate the data with the enables", "Second 7408: each gate ANDs one data switch with its channel enable"),
            LabObjective("obj5", "Sum with a two-level OR tree", "7432 gates 1 and 2 pair the terms; gate 3 combines their outputs into Y"),
            LabObjective("obj6", "Verify channel selection", "Set a distinct pattern on D0..D3 and step the select code 00, 01, 10, 11")
        ),
        expectedFunction = { inputs ->
            // Select code S1 S0 read as a 2-bit number indexes straight into D0..D3, which occupy
            // the first four positions of the input list.
            val channel = (if (inputs[4]) 2 else 0) or (if (inputs[5]) 1 else 0)
            listOf(inputs[channel], channel == 0, channel == 1, channel == 2, channel == 3)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            val inv = circuit.addChip("7404", trench = 1, startColumn = 10)
            val sel = circuit.addChip("7408", trench = 1, startColumn = 24)
            val dataGate = circuit.addChip("7408", trench = 1, startColumn = 38)
            val orGate = circuit.addChip("7432", trench = 2, startColumn = 10)

            powerChip(circuit, inv)
            powerChip(circuit, sel)
            powerChip(circuit, dataGate)
            powerChip(circuit, orGate)

            // Select complements. Only two of six inverters are needed here.
            circuit.addWire(AD200Topology.TERM_SW4, inv.getPinSocket(1), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW5, inv.getPinSocket(3), WireColor.BROWN)
            val notS1 = inv.getPinSocket(2)
            val notS0 = inv.getPinSocket(4)

            // First half of each 3-input term: the channel enables.
            // Gate 1 (1,2 -> 3): channel 0 enable = S̄1·S̄0
            circuit.addWire(notS1, sel.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(notS0, sel.getPinSocket(2), WireColor.WHITE)
            // Gate 2 (4,5 -> 6): channel 1 enable = S̄1·S0
            circuit.addWire(notS1, sel.getPinSocket(4), WireColor.GRAY)
            circuit.addWire(AD200Topology.TERM_SW5, sel.getPinSocket(5), WireColor.BROWN)
            // Gate 3 (9,10 -> 8): channel 2 enable = S1·S̄0
            circuit.addWire(AD200Topology.TERM_SW4, sel.getPinSocket(9), WireColor.PURPLE)
            circuit.addWire(notS0, sel.getPinSocket(10), WireColor.WHITE)
            // Gate 4 (12,13 -> 11): channel 3 enable = S1·S0
            circuit.addWire(AD200Topology.TERM_SW4, sel.getPinSocket(12), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW5, sel.getPinSocket(13), WireColor.BROWN)

            // The enables cost nothing extra to display, and watching one of four indicators
            // follow the select code makes it obvious why Y ignores three of the data switches.
            circuit.addWire(sel.getPinSocket(3), AD200Topology.TERM_LED1, WireColor.BLUE)
            circuit.addWire(sel.getPinSocket(6), AD200Topology.TERM_LED2, WireColor.BLUE)
            circuit.addWire(sel.getPinSocket(8), AD200Topology.TERM_LED3, WireColor.BLUE)
            circuit.addWire(sel.getPinSocket(11), AD200Topology.TERM_LED4, WireColor.BLUE)

            // Second half of each 3-input term: data AND enable. All four gates used.
            circuit.addWire(AD200Topology.TERM_SW0, dataGate.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(sel.getPinSocket(3), dataGate.getPinSocket(2), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW1, dataGate.getPinSocket(4), WireColor.YELLOW)
            circuit.addWire(sel.getPinSocket(6), dataGate.getPinSocket(5), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW2, dataGate.getPinSocket(9), WireColor.ORANGE)
            circuit.addWire(sel.getPinSocket(8), dataGate.getPinSocket(10), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW3, dataGate.getPinSocket(12), WireColor.ORANGE)
            circuit.addWire(sel.getPinSocket(11), dataGate.getPinSocket(13), WireColor.BLUE)

            // OR tree. A 7432 gate takes two inputs, so four terms need two gates to pair them
            // and a third to combine the pairs. Gate 3's inputs come off gates 1 and 2 of the
            // same package -- the mux's only intra-package feedforward.
            circuit.addWire(dataGate.getPinSocket(3), orGate.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(dataGate.getPinSocket(6), orGate.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(dataGate.getPinSocket(8), orGate.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(dataGate.getPinSocket(11), orGate.getPinSocket(5), WireColor.ORANGE)
            circuit.addWire(orGate.getPinSocket(3), orGate.getPinSocket(9), WireColor.GRAY)
            circuit.addWire(orGate.getPinSocket(6), orGate.getPinSocket(10), WireColor.GRAY)
            circuit.addWire(orGate.getPinSocket(8), AD200Topology.TERM_LED0, WireColor.GREEN)

            // Spares: four inverters and the fourth OR gate.
            groundUnusedInputs(circuit, inv, listOf(5, 9, 11, 13))
            groundUnusedInputs(circuit, orGate, listOf(12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.switches[4] = false
            circuit.switches[5] = false
            circuit.step()
        }
    ),

    // Exp 09 B: 1:4 demultiplexer -- the multiplexer read backwards.
    LabExperiment(
        id = "exp09_demux_1to4",
        labNumber = 9,
        title = "4:1 Multiplexer and 1:4 Demultiplexer",
        subtitle = "Variation B · 1:4 demultiplexer",
        description = "Fan one data line out to four outputs under the same 2-bit select code: " +
            "Y0 = D·S̄1·S̄0, Y1 = D·S̄1·S0, Y2 = D·S1·S̄0, Y3 = D·S1·S0. The select decode is " +
            "identical to the multiplexer's — the same four channel enables from the same 7408 — " +
            "and the second 7408 gates the single data line against each of them instead of four " +
            "separate data lines against one each. Every term is again a 3-input AND split across " +
            "two 2-input gates, so this is eight AND gates and two packages just like Variation A. " +
            "No OR stage is needed: the outputs stay separate rather than being merged, so the " +
            "7432 the multiplexer required has nothing to do here.",
        targetChips = listOf("7404", "7408"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0, 1, 2, 3),
        inputLabels = listOf("D (SW0)", "S1 (SW1)", "S0 (SW2)"),
        outputLabels = listOf("Y0 (LED0)", "Y1 (LED1)", "Y2 (LED2)", "Y3 (LED3)"),
        objectives = listOf(
            LabObjective("obj1", "Mount and power three packages", "7404 at Col 10, 7408 at Col 24, 7408 at Col 38, all in trench 1"),
            LabObjective("obj2", "Complement the select lines", "SW1 and SW2 into 7404 Pins 1 and 3; S̄1 on Pin 2, S̄0 on Pin 4"),
            LabObjective("obj3", "Reuse the channel decode", "First 7408 forms the same four enables the multiplexer used"),
            LabObjective("obj4", "Fan the data line out", "Second 7408: all four gates take D on one input and an enable on the other"),
            LabObjective("obj5", "Verify the routing", "With D=1 exactly one output is HIGH; with D=0 all four are LOW"),
            LabObjective("obj6", "Contrast with the multiplexer", "Same decode, same gate count, no summing stage")
        ),
        expectedFunction = { inputs ->
            val channel = (if (inputs[1]) 2 else 0) or (if (inputs[2]) 1 else 0)
            // Nothing is asserted at all while the data line is LOW -- the demux gates the data,
            // it does not merely steer a constant.
            (0..3).map { inputs[0] && it == channel }
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            val inv = circuit.addChip("7404", trench = 1, startColumn = 10)
            val sel = circuit.addChip("7408", trench = 1, startColumn = 24)
            val outGate = circuit.addChip("7408", trench = 1, startColumn = 38)

            powerChip(circuit, inv)
            powerChip(circuit, sel)
            powerChip(circuit, outGate)

            circuit.addWire(AD200Topology.TERM_SW1, inv.getPinSocket(1), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW2, inv.getPinSocket(3), WireColor.BROWN)
            val notS1 = inv.getPinSocket(2)
            val notS0 = inv.getPinSocket(4)

            // Channel enables, identical to Variation A's first 7408.
            circuit.addWire(notS1, sel.getPinSocket(1), WireColor.GRAY)
            circuit.addWire(notS0, sel.getPinSocket(2), WireColor.WHITE)
            circuit.addWire(notS1, sel.getPinSocket(4), WireColor.GRAY)
            circuit.addWire(AD200Topology.TERM_SW2, sel.getPinSocket(5), WireColor.BROWN)
            circuit.addWire(AD200Topology.TERM_SW1, sel.getPinSocket(9), WireColor.PURPLE)
            circuit.addWire(notS0, sel.getPinSocket(10), WireColor.WHITE)
            circuit.addWire(AD200Topology.TERM_SW1, sel.getPinSocket(12), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW2, sel.getPinSocket(13), WireColor.BROWN)

            // Output stage: the one data switch fans to all four gates, each gated by its enable.
            circuit.addWire(AD200Topology.TERM_SW0, outGate.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(sel.getPinSocket(3), outGate.getPinSocket(2), WireColor.BLUE)
            circuit.addWire(outGate.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

            circuit.addWire(AD200Topology.TERM_SW0, outGate.getPinSocket(4), WireColor.YELLOW)
            circuit.addWire(sel.getPinSocket(6), outGate.getPinSocket(5), WireColor.BLUE)
            circuit.addWire(outGate.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.GREEN)

            circuit.addWire(AD200Topology.TERM_SW0, outGate.getPinSocket(9), WireColor.YELLOW)
            circuit.addWire(sel.getPinSocket(8), outGate.getPinSocket(10), WireColor.BLUE)
            circuit.addWire(outGate.getPinSocket(8), AD200Topology.TERM_LED2, WireColor.GREEN)

            circuit.addWire(AD200Topology.TERM_SW0, outGate.getPinSocket(12), WireColor.YELLOW)
            circuit.addWire(sel.getPinSocket(11), outGate.getPinSocket(13), WireColor.BLUE)
            circuit.addWire(outGate.getPinSocket(11), AD200Topology.TERM_LED3, WireColor.GREEN)

            // Spares: four inverters. Both 7408s are fully committed.
            groundUnusedInputs(circuit, inv, listOf(5, 9, 11, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    ),

    // Exp 18 A: BCD-to-seven-segment decode on the 7448 — the one decoder the catalog actually
    // stocks, and the only classic MSI part the extended course had not yet taught.
    LabExperiment(
        id = "exp18_bcd_seven_segment_7448",
        labNumber = 18,
        title = "BCD-to-7-Segment Decoder (7448)",
        subtitle = "Variation A · BCD 0-9 decode on the 7448",
        description = "Drive a seven-segment digit from binary: the 7448 reads a 4-bit BCD word " +
            "(A is the LSB on Pin 7, D the MSB on Pin 6) and asserts the active-HIGH segment " +
            "outputs a..g that light the digit's strokes. The seven segments are brought out to " +
            "LED0..LED6 so each stroke of the font is visible individually — sweep SW0..SW3 and " +
            "watch 0000 draw a 0, 0001 draw a 1, and so on. Codes 10 to 15 are outside decimal " +
            "BCD and produce the same odd non-digit patterns the real part shows, with 15 blank. " +
            "Power note: the 7448 is a 16-pin part with VCC on Pin 16 and GND on Pin 8.",
        targetChips = listOf("7448"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0, 1, 2, 3, 4, 5, 6),
        inputLabels = listOf("A (SW0)", "B (SW1)", "C (SW2)", "D (SW3)"),
        outputLabels = listOf(
            "a (LED0)", "b (LED1)", "c (LED2)", "d (LED3)",
            "e (LED4)", "f (LED5)", "g (LED6)"
        ),
        objectives = listOf(
            LabObjective("obj1", "Power the 7448 on Pins 16 and 8", "VCC is Pin 16 and GND is Pin 8 — the 16-pin corner positions"),
            LabObjective("obj2", "Wire the BCD word", "SW0 to A (Pin 7), SW1 to B (Pin 1), SW2 to C (Pin 2), SW3 to D (Pin 6)"),
            LabObjective("obj3", "Park the controls inactive", "~LT (Pin 3), ~BI (Pin 4) and ~RBI (Pin 5) all to +5V — they are active-LOW"),
            LabObjective("obj4", "Bring the segments out", "a..g on Pins 13, 12, 11, 10, 9, 15 and 14 to LED0..LED6"),
            LabObjective("obj5", "Sweep the sixteen codes", "Confirm each decimal digit's stroke pattern, and that 15 blanks the digit")
        ),
        expectedFunction = { inputs ->
            // A (inputs[0]) is the LSB of the BCD word, D (inputs[3]) the MSB.
            val code = (if (inputs[0]) 1 else 0) or
                (if (inputs[1]) 2 else 0) or
                (if (inputs[2]) 4 else 0) or
                (if (inputs[3]) 8 else 0)
            segmentsOf(code)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            // 16-pin package: eight columns wide, so it starts two columns in like the 7483 and
            // 7476 presets do.
            val decoder = circuit.addChip("7448", trench = 1, startColumn = 12)
            powerChip(circuit, decoder)

            // BCD inputs. A is the LSB.
            circuit.addWire(AD200Topology.TERM_SW0, decoder.getPinSocket(7), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, decoder.getPinSocket(1), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, decoder.getPinSocket(2), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW3, decoder.getPinSocket(6), WireColor.BROWN)

            // Active-LOW controls parked inactive on the +5V rail: lamp test, blanking and
            // ripple blanking must all read HIGH for normal decoding.
            circuit.addWire(decoder.getPinSocket(3), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 13), WireColor.RED)
            circuit.addWire(decoder.getPinSocket(4), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 14), WireColor.RED)
            circuit.addWire(decoder.getPinSocket(5), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 15), WireColor.RED)

            // Segment outputs a..g to LED0..LED6. Pin order is not sequential: f comes back
            // around the top on Pin 15 and g on Pin 14.
            val segmentPins = listOf(13, 12, 11, 10, 9, 15, 14)
            for ((index, pin) in segmentPins.withIndex()) {
                circuit.addWire(
                    decoder.getPinSocket(pin),
                    AD200Topology.TERM_LED0 + index,
                    WireColor.GREEN
                )
            }

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.step()
        }
    ),

    // Exp 18 B: the same decoder exercising its asynchronous lamp-test control.
    LabExperiment(
        id = "exp18_bcd_seven_segment_controls",
        labNumber = 18,
        title = "BCD-to-7-Segment Decoder (7448)",
        subtitle = "Variation B · lamp test and blanking controls",
        description = "The 7448's three control pins override the BCD decode entirely, and ~LT " +
            "(lamp test, Pin 3) is the one to know: pull it LOW and all seven segment outputs go " +
            "HIGH whatever the BCD word is doing — the digit burns a full 8. That is how a " +
            "technician checks every stroke of a display in one press, before trusting any " +
            "reading it shows. SW0 now drives ~LT directly (active-LOW), SW1..SW4 carry the BCD " +
            "word, and ~BI and ~RBI stay parked inactive. Sweep the word with ~LT HIGH for the " +
            "normal font, then drop ~LT and watch all seven LEDs light on every row.",
        targetChips = listOf("7448"),
        switchIndices = listOf(0, 1, 2, 3, 4),
        ledIndices = listOf(0, 1, 2, 3, 4, 5, 6),
        inputLabels = listOf("~LT (SW0)", "A (SW1)", "B (SW2)", "C (SW3)", "D (SW4)"),
        outputLabels = listOf(
            "a (LED0)", "b (LED1)", "c (LED2)", "d (LED3)",
            "e (LED4)", "f (LED5)", "g (LED6)"
        ),
        objectives = listOf(
            LabObjective("obj1", "Power the 7448 on Pins 16 and 8", "VCC is Pin 16 and GND is Pin 8"),
            LabObjective("obj2", "Drive the lamp test", "SW0 to ~LT (Pin 3) — the input is active-LOW, so a LOW switch lights every segment"),
            LabObjective("obj3", "Wire the BCD word", "SW1 to A (Pin 7), SW2 to B (Pin 1), SW3 to C (Pin 2), SW4 to D (Pin 6)"),
            LabObjective("obj4", "Keep blanking inactive", "~BI (Pin 4) and ~RBI (Pin 5) to +5V, or the display would go dark"),
            LabObjective("obj5", "Prove the override", "With ~LT LOW, every one of the thirty-two sweep rows must light all seven LEDs")
        ),
        expectedFunction = { inputs ->
            val lampTestAsserted = !inputs[0] // ~LT is active-LOW
            if (lampTestAsserted) {
                // Lamp test: all seven segments HIGH regardless of the BCD word.
                List(7) { true }
            } else {
                val code = (if (inputs[1]) 1 else 0) or
                    (if (inputs[2]) 2 else 0) or
                    (if (inputs[3]) 4 else 0) or
                    (if (inputs[4]) 8 else 0)
                segmentsOf(code)
            }
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireConverterPowerRails(circuit)

            val decoder = circuit.addChip("7448", trench = 1, startColumn = 12)
            powerChip(circuit, decoder)

            // Lamp test under switch control, active-LOW.
            circuit.addWire(AD200Topology.TERM_SW0, decoder.getPinSocket(3), WireColor.YELLOW)

            // BCD inputs on SW1..SW4.
            circuit.addWire(AD200Topology.TERM_SW1, decoder.getPinSocket(7), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, decoder.getPinSocket(1), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW3, decoder.getPinSocket(2), WireColor.BROWN)
            circuit.addWire(AD200Topology.TERM_SW4, decoder.getPinSocket(6), WireColor.GRAY)

            // Blanking controls parked inactive on the +5V rail.
            circuit.addWire(decoder.getPinSocket(4), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 14), WireColor.RED)
            circuit.addWire(decoder.getPinSocket(5), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 15), WireColor.RED)

            val segmentPins = listOf(13, 12, 11, 10, 9, 15, 14)
            for ((index, pin) in segmentPins.withIndex()) {
                circuit.addWire(
                    decoder.getPinSocket(pin),
                    AD200Topology.TERM_LED0 + index,
                    WireColor.GREEN
                )
            }

            circuit.switches[0] = true
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.switches[4] = false
            circuit.step()
        }
    )
)
