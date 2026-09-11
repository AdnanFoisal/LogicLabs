package com.logiclabs.feature.tools.courseware

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology

// Experiments 13 to 17, all built exclusively from fundamental basic gates: 7404 Hex Inverter,
// 7408 Quad 2-Input AND, 7432 Quad 2-Input OR, 74266 Quad XNOR and 7420 Dual 4-Input NAND.
//
// Experiments 13 and 14 model the foundational university digital logic curriculum (e.g. CUET CSE),
// where students must derive and wire arithmetic circuits from Boolean first principles
// (SOP minterms and cascaded half-adders) before reaching for MSI integrated adders like the 7483
// or specialised parity gates like the 7486 XOR.
//
// Experiments 15 to 17 close the gate-coverage gaps against the sandbox catalog: the XNOR
// (equality) gate the classic labs never teach, the non-inverting buffer, and the 7420's 4-input
// NAND — every part used is one the bench can actually place.
//
// The experiments provide selectable variations evaluated through truth-table vectors:
//  - Half Adder: Variation A (Sum S = A'B + AB'), Variation B (Carry C = AB)
//  - Full Adder: Variation A (Sum S = A ⊕ B ⊕ Cin via basic gates), Variation B (Cout = AB + BCin + ACin)
//  - XNOR: Variation A (74266 quad XNOR), Variation B (XNOR as 7486 XOR + 7404 inverter)
//  - Buffer: Variation A (two cascaded 7404 inverters), Variation B (7432 OR gate with tied inputs)
//  - Wide NAND: Variation A (7420 4-input NAND), Variation B (4-input AND from 7420 + 7404)

private const val EXP13_TITLE = "Half Adder from Basic Gates"
private const val EXP14_TITLE = "Full Adder from Basic Gates"
private const val EXP15_TITLE = "Exclusive-NOR (XNOR) Gate Logic"
private const val EXP16_TITLE = "Non-Inverting Buffer Stage"
private const val EXP17_TITLE = "4-Input NAND Gate Logic (7420)"

private fun wireBasicPowerRails(circuit: BreadboardCircuit) {
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

private fun powerDip14(circuit: BreadboardCircuit, chip: BreadboardCircuit.PlacedChipRuntime) {
    val col = chip.placedIc.startColumn
    circuit.addWire(
        chip.getPinSocket(14),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, col),
        WireColor.RED
    )
    circuit.addWire(
        chip.getPinSocket(7),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, col + 6),
        WireColor.BLACK
    )
}

private fun tiePinsLow(
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

val basicGateAdderLabs: List<LabExperiment> = listOf(

    // ---------------------------------------------------------------------------------------
    // Experiment 13 — Half Adder using basic gates (7404, 7408, 7432)
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp13_half_adder_basic_sum",
        labNumber = 13,
        title = EXP13_TITLE,
        subtitle = "Variation A · Sum S = A'B + AB' (AND-OR-NOT)",
        description = "Synthesize the Half Adder Sum function strictly using fundamental gates: " +
            "two 7404 inverters, two 7408 AND gates, and one 7432 OR gate. In Boolean algebra, " +
            "A XOR B is expressed in sum-of-products form as A'·B + A·B'. Sweep SW0 (A) and " +
            "SW1 (B) to verify that LED0 lights up when exactly one input is HIGH (01 and 10).",
        targetChips = listOf("7404", "7408", "7432"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0),
        inputLabels = listOf("A (SW0)", "B (SW1)"),
        outputLabels = listOf("SUM S (LED0)"),
        objectives = listOf(
            LabObjective("obj1", "Power all 74xx packages", "Connect Pin 14 to +5V rail and Pin 7 to GND rail for 7404, 7408, and 7432"),
            LabObjective("obj2", "Generate input complements", "Feed SW0 (A) to 7404 Pin 1 for A' (Pin 2); SW1 (B) to Pin 3 for B' (Pin 4)"),
            LabObjective("obj3", "Form product terms", "7408 Gate 1 (Pins 1,2->3) produces A'·B; Gate 2 (Pins 4,5->6) produces A·B'"),
            LabObjective("obj4", "Sum minterms", "Feed both AND outputs into 7432 Gate 1 (Pins 1,2); connect Pin 3 (Sum S) to LED0"),
            LabObjective("obj5", "Ground unused inputs", "Tie all unused gate inputs to the ground rail to prevent floating TTL inputs")
        ),
        expectedFunction = { inputs ->
            val a = inputs[0]
            val b = inputs[1]
            listOf(a xor b)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val uInv = circuit.addChip("7404", trench = 1, startColumn = 10)
            val uAnd = circuit.addChip("7408", trench = 1, startColumn = 24)
            val uOr = circuit.addChip("7432", trench = 1, startColumn = 38)
            powerDip14(circuit, uInv)
            powerDip14(circuit, uAnd)
            powerDip14(circuit, uOr)

            // 7404 Inverters:
            // Gate 1 (1 -> 2): Invert A
            circuit.addWire(AD200Topology.TERM_SW0, uInv.getPinSocket(1), WireColor.YELLOW)
            // Gate 2 (3 -> 4): Invert B
            circuit.addWire(AD200Topology.TERM_SW1, uInv.getPinSocket(3), WireColor.ORANGE)
            tiePinsLow(circuit, uInv, listOf(5, 9, 11, 13))

            // 7408 AND gates:
            // Gate 1 (1, 2 -> 3): A' · B
            circuit.addWire(uInv.getPinSocket(2), uAnd.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW1, uAnd.getPinSocket(2), WireColor.ORANGE)

            // Gate 2 (4, 5 -> 6): A · B'
            circuit.addWire(AD200Topology.TERM_SW0, uAnd.getPinSocket(4), WireColor.YELLOW)
            circuit.addWire(uInv.getPinSocket(4), uAnd.getPinSocket(5), WireColor.PURPLE)
            tiePinsLow(circuit, uAnd, listOf(9, 10, 12, 13))

            // 7432 OR gate:
            // Gate 1 (1, 2 -> 3): (A'·B) + (A·B') = Sum S
            circuit.addWire(uAnd.getPinSocket(3), uOr.getPinSocket(1), WireColor.WHITE)
            circuit.addWire(uAnd.getPinSocket(6), uOr.getPinSocket(2), WireColor.WHITE)
            circuit.addWire(uOr.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
            tiePinsLow(circuit, uOr, listOf(4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp13_half_adder_basic_carry",
        labNumber = 13,
        title = EXP13_TITLE,
        subtitle = "Variation B · Carry C = AB (AND gate)",
        description = "Synthesize the Half Adder Carry function using a 7408 2-input AND gate. " +
            "In binary addition, a carry is generated to the next higher order bit if and only if " +
            "both operand bits are 1 (C = A·B). Sweep SW0 (A) and SW1 (B) to verify that LED0 " +
            "lights up exclusively when both inputs are 1 (input vector 11).",
        targetChips = listOf("7404", "7408", "7432"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0),
        inputLabels = listOf("A (SW0)", "B (SW1)"),
        outputLabels = listOf("CARRY C (LED0)"),
        objectives = listOf(
            LabObjective("obj1", "Power all 74xx packages", "Connect Pin 14 to +5V rail and Pin 7 to GND rail for 7404, 7408, and 7432"),
            LabObjective("obj2", "Route inputs to AND gate", "Connect SW0 (A) to 7408 Pin 1 and SW1 (B) to 7408 Pin 2"),
            LabObjective("obj3", "Connect Carry output to LED0", "Route 7408 Pin 3 (Carry C = A·B) to LED0 indicator"),
            LabObjective("obj4", "Verify Carry truth table", "LED0 should only illuminate when both A and B switches are active (11)"),
            LabObjective("obj5", "Ground all unused inputs", "Tie spare inputs on 7404, 7408, and 7432 to ground rail")
        ),
        expectedFunction = { inputs ->
            val a = inputs[0]
            val b = inputs[1]
            listOf(a && b)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val uInv = circuit.addChip("7404", trench = 1, startColumn = 10)
            val uAnd = circuit.addChip("7408", trench = 1, startColumn = 24)
            val uOr = circuit.addChip("7432", trench = 1, startColumn = 38)
            powerDip14(circuit, uInv)
            powerDip14(circuit, uAnd)
            powerDip14(circuit, uOr)

            // 7408 AND Gate 1 (1, 2 -> 3): Carry C = A · B
            circuit.addWire(AD200Topology.TERM_SW0, uAnd.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, uAnd.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(uAnd.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
            tiePinsLow(circuit, uAnd, listOf(4, 5, 9, 10, 12, 13))

            // Spare chips on bench
            tiePinsLow(circuit, uInv, listOf(1, 3, 5, 9, 11, 13))
            tiePinsLow(circuit, uOr, listOf(1, 2, 4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    // ---------------------------------------------------------------------------------------
    // Experiment 14 — Full Adder using basic gates (7404, 7408, 7432)
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp14_full_adder_basic_sum",
        labNumber = 14,
        title = EXP14_TITLE,
        subtitle = "Variation A · Sum S = A ⊕ B ⊕ Cin (AND-OR-NOT)",
        description = "Synthesize the 3-input Full Adder Sum function using only fundamental gates. " +
            "Two half-adder XOR structures are cascaded: first X = A ⊕ B = A'B + AB' is formed using " +
            "two inverters, two ANDs, and one OR gate. Then, S = X ⊕ Cin = X'·Cin + X·Cin' is formed " +
            "using two additional inverters, two ANDs, and a second OR gate. Observe the parity " +
            "behavior on LED0: the Sum output is 1 whenever an odd number of inputs are HIGH.",
        targetChips = listOf("7404", "7408", "7432"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0),
        inputLabels = listOf("A (SW0)", "B (SW1)", "Cin (SW2)"),
        outputLabels = listOf("SUM S (LED0)"),
        objectives = listOf(
            LabObjective("obj1", "Power all three packages", "Pin 14 to +5V and Pin 7 to GND on 7404, 7408, and 7432"),
            LabObjective("obj2", "Synthesize first XOR stage", "Form X = A'B + AB' on 7432 Pin 3 using 7404 Gates 1-2 and 7408 Gates 1-2"),
            LabObjective("obj3", "Invert Cin and intermediate X", "7404 Gate 3 (Pin 5->6) inverts Cin; Gate 4 (Pin 9->8) inverts X"),
            LabObjective("obj4", "Synthesize second XOR stage", "7408 Gates 3-4 form X'·Cin and X·Cin'; 7432 Gate 2 (Pins 4,5->6) forms final Sum S"),
            LabObjective("obj5", "Sweep 8 truth table vectors", "Verify LED0 illuminates for vectors 001, 010, 100, and 111")
        ),
        expectedFunction = { inputs ->
            val a = inputs[0]
            val b = inputs[1]
            val cin = inputs[2]
            listOf(a xor b xor cin)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val uInv = circuit.addChip("7404", trench = 1, startColumn = 10)
            val uAnd = circuit.addChip("7408", trench = 1, startColumn = 24)
            val uOr = circuit.addChip("7432", trench = 1, startColumn = 38)
            powerDip14(circuit, uInv)
            powerDip14(circuit, uAnd)
            powerDip14(circuit, uOr)

            // 1. Invert A, B, Cin:
            // 7404 Gate 1 (1 -> 2): Invert A
            circuit.addWire(AD200Topology.TERM_SW0, uInv.getPinSocket(1), WireColor.YELLOW)
            // 7404 Gate 2 (3 -> 4): Invert B
            circuit.addWire(AD200Topology.TERM_SW1, uInv.getPinSocket(3), WireColor.ORANGE)
            // 7404 Gate 3 (5 -> 6): Invert Cin
            circuit.addWire(AD200Topology.TERM_SW2, uInv.getPinSocket(5), WireColor.PURPLE)

            // 2. Stage 1: X = A ⊕ B = A'B + AB'
            // 7408 Gate 1 (1, 2 -> 3): A' · B
            circuit.addWire(uInv.getPinSocket(2), uAnd.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(AD200Topology.TERM_SW1, uAnd.getPinSocket(2), WireColor.ORANGE)

            // 7408 Gate 2 (4, 5 -> 6): A · B'
            circuit.addWire(AD200Topology.TERM_SW0, uAnd.getPinSocket(4), WireColor.YELLOW)
            circuit.addWire(uInv.getPinSocket(4), uAnd.getPinSocket(5), WireColor.BLUE)

            // 7432 Gate 1 (1, 2 -> 3): X = (A'B) + (AB')
            circuit.addWire(uAnd.getPinSocket(3), uOr.getPinSocket(1), WireColor.WHITE)
            circuit.addWire(uAnd.getPinSocket(6), uOr.getPinSocket(2), WireColor.WHITE)

            // 3. Invert X: 7404 Gate 4 (9 -> 8)
            circuit.addWire(uOr.getPinSocket(3), uInv.getPinSocket(9), WireColor.WHITE)
            tiePinsLow(circuit, uInv, listOf(11, 13))

            // 4. Stage 2: S = X ⊕ Cin = X'·Cin + X·Cin'
            // 7408 Gate 3 (9, 10 -> 8): X' · Cin
            circuit.addWire(uInv.getPinSocket(8), uAnd.getPinSocket(9), WireColor.GRAY)
            circuit.addWire(AD200Topology.TERM_SW2, uAnd.getPinSocket(10), WireColor.PURPLE)

            // 7408 Gate 4 (12, 13 -> 11): X · Cin'
            circuit.addWire(uOr.getPinSocket(3), uAnd.getPinSocket(12), WireColor.WHITE)
            circuit.addWire(uInv.getPinSocket(6), uAnd.getPinSocket(13), WireColor.GRAY)

            // 7432 Gate 2 (4, 5 -> 6): Sum S = (X'·Cin) + (X·Cin')
            circuit.addWire(uAnd.getPinSocket(8), uOr.getPinSocket(4), WireColor.GRAY)
            circuit.addWire(uAnd.getPinSocket(11), uOr.getPinSocket(5), WireColor.GRAY)
            circuit.addWire(uOr.getPinSocket(6), AD200Topology.TERM_LED0, WireColor.GREEN)
            tiePinsLow(circuit, uOr, listOf(9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp14_full_adder_basic_carry",
        labNumber = 14,
        title = EXP14_TITLE,
        subtitle = "Variation B · Carry-Out Cout = AB + BCin + ACin (AND-OR)",
        description = "Synthesize the Full Adder Carry-Out function in two-level SOP form using " +
            "three 7408 AND gates and two 7432 OR gates. The carry-out condition Cout = AB + BCin + ACin " +
            "evaluates to 1 whenever at least two of the three input bits are active HIGH. Sweep " +
            "SW0 (A), SW1 (B), and SW2 (Cin) to verify the carry-out truth table on LED0.",
        targetChips = listOf("7404", "7408", "7432"),
        switchIndices = listOf(0, 1, 2),
        ledIndices = listOf(0),
        inputLabels = listOf("A (SW0)", "B (SW1)", "Cin (SW2)"),
        outputLabels = listOf("CARRY-OUT COUT (LED0)"),
        objectives = listOf(
            LabObjective("obj1", "Power all three packages", "Pin 14 to +5V and Pin 7 to GND on 7404, 7408, and 7432"),
            LabObjective("obj2", "Generate product terms", "7408 Gate 1 gives A·B; Gate 2 gives B·Cin; Gate 3 gives A·Cin"),
            LabObjective("obj3", "Combine first two carry terms", "7432 Gate 1 (Pins 1,2->3) forms AB + BCin"),
            LabObjective("obj4", "Form final Carry-Out", "7432 Gate 2 (Pins 4,5->6) sums (AB + BCin) with ACin; route Pin 6 to LED0"),
            LabObjective("obj5", "Verify majority logic", "Verify that LED0 is 1 for inputs 011, 101, 110, and 111")
        ),
        expectedFunction = { inputs ->
            val a = inputs[0]
            val b = inputs[1]
            val cin = inputs[2]
            listOf((a && b) || (b && cin) || (a && cin))
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val uInv = circuit.addChip("7404", trench = 1, startColumn = 10)
            val uAnd = circuit.addChip("7408", trench = 1, startColumn = 24)
            val uOr = circuit.addChip("7432", trench = 1, startColumn = 38)
            powerDip14(circuit, uInv)
            powerDip14(circuit, uAnd)
            powerDip14(circuit, uOr)

            // 7408 Gate 1 (1, 2 -> 3): A · B
            circuit.addWire(AD200Topology.TERM_SW0, uAnd.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, uAnd.getPinSocket(2), WireColor.ORANGE)

            // 7408 Gate 2 (4, 5 -> 6): B · Cin
            circuit.addWire(AD200Topology.TERM_SW1, uAnd.getPinSocket(4), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, uAnd.getPinSocket(5), WireColor.PURPLE)

            // 7408 Gate 3 (9, 10 -> 8): A · Cin
            circuit.addWire(AD200Topology.TERM_SW0, uAnd.getPinSocket(9), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW2, uAnd.getPinSocket(10), WireColor.PURPLE)
            tiePinsLow(circuit, uAnd, listOf(12, 13))

            // 7432 Gate 1 (1, 2 -> 3): AB + BCin
            circuit.addWire(uAnd.getPinSocket(3), uOr.getPinSocket(1), WireColor.WHITE)
            circuit.addWire(uAnd.getPinSocket(6), uOr.getPinSocket(2), WireColor.WHITE)

            // 7432 Gate 2 (4, 5 -> 6): (AB + BCin) + ACin = Cout
            circuit.addWire(uOr.getPinSocket(3), uOr.getPinSocket(4), WireColor.WHITE)
            circuit.addWire(uAnd.getPinSocket(8), uOr.getPinSocket(5), WireColor.WHITE)
            circuit.addWire(uOr.getPinSocket(6), AD200Topology.TERM_LED0, WireColor.GREEN)
            tiePinsLow(circuit, uOr, listOf(9, 10, 12, 13))

            // Spare inverter
            tiePinsLow(circuit, uInv, listOf(1, 3, 5, 9, 11, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.step()
        }
    ),

    // ---------------------------------------------------------------------------------------
    // Experiment 15 — Exclusive-NOR (XNOR): the equality gate missing from the classic labs.
    //
    // XNOR is the complement of XOR: HIGH when the inputs match, LOW when they differ. Two
    // honest builds: the catalog's real XNOR part (the 74266), and the classic synthesis
    // from a 7486 XOR plus a 7404 inverter.
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp15_xnor_74266",
        labNumber = 15,
        title = EXP15_TITLE,
        subtitle = "Variation A · 74266 quad XNOR gate",
        description = "The complement of Lab 6's XOR: Y = A ⊙ B is HIGH when the inputs are EQUAL " +
            "and LOW when they differ, which is why XNOR is called the equality gate — coincidence " +
            "detectors and comparators are built from it. This variation uses the 74266, the only " +
            "true XNOR in the drawer. Read its pin map before wiring: gates 2 and 3 are mirrored " +
            "relative to a 7486, so the outputs land on Pins 3, 4, 10 and 11 rather than 3, 6, 8 " +
            "and 11, and Pins 5, 6, 8 and 9 are all inputs. (The real 'LS266 also has open-collector " +
            "outputs that need a pull-up resistor; the trainer models ordinary driven outputs, so " +
            "none is needed here.)",
        targetChips = listOf("74266"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0),
        inputLabels = listOf("A (SW0)", "B (SW1)"),
        outputLabels = listOf("Y (LED0)"),
        objectives = listOf(
            LabObjective("obj1", "Power the 74266", "Pin 14 to the +5V rail, Pin 7 to the ground rail"),
            LabObjective("obj2", "Mind the mirrored pinout", "Gate 1 is Pins 1, 2 -> 3 as usual, but Gate 2's output is Pin 4 and its inputs are Pins 5 and 6"),
            LabObjective("obj3", "Wire the equality test", "SW0 to 1A (Pin 1), SW1 to 1B (Pin 2), 1Y (Pin 3) to LED0"),
            LabObjective("obj4", "Ground the three spare gates", "Pins 5, 6, 8, 9, 12 and 13 to the ground rail"),
            LabObjective("obj5", "Verify the XNOR truth table", "LED0 is lit for 00 and 11, dark for 01 and 10 — the mirror image of Lab 6")
        ),
        expectedFunction = { inputs ->
            listOf(!(inputs[0] xor inputs[1]))
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val xnor = circuit.addChip("74266", trench = 1, startColumn = 10)
            powerDip14(circuit, xnor)

            // Gate 1 (Pins 1,2 -> 3): Y = A ⊙ B. Note gate 1 of the '266 does follow the
            // familiar layout; it is gates 2 and 3 that are mirrored.
            circuit.addWire(AD200Topology.TERM_SW0, xnor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, xnor.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(xnor.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

            // Gates 2 (5,6 -> 4), 3 (8,9 -> 10) and 4 (12,13 -> 11) are spare.
            tiePinsLow(circuit, xnor, listOf(5, 6, 8, 9, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp15_xnor_7486_7404",
        labNumber = 15,
        title = EXP15_TITLE,
        subtitle = "Variation B · XNOR from 7486 XOR + 7404 inverter",
        description = "Build the same equality gate from parts the classic labs already introduced: " +
            "A ⊙ B is NOT(A ⊕ B), so one 7486 XOR stage followed by one 7404 inverter does it. " +
            "LED1 shows the raw XOR and LED0 the inverted XNOR, so sweeping the four input " +
            "vectors displays both columns of the pair at once and the relationship between the " +
            "two gates is visible rather than asserted. Two packages and one extra gate delay " +
            "over the 74266 — the standard price of not owning the specialised part.",
        targetChips = listOf("7486", "7404"),
        switchIndices = listOf(0, 1),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("A (SW0)", "B (SW1)"),
        outputLabels = listOf("XNOR (LED0)", "XOR (LED1)"),
        objectives = listOf(
            LabObjective("obj1", "Power both packages", "Pin 14 to +5V and Pin 7 to ground on the 7486 and the 7404"),
            LabObjective("obj2", "Form the XOR first", "SW0 to 7486 Pin 1, SW1 to Pin 2; Pin 3 is A ⊕ B — wire it to LED1"),
            LabObjective("obj3", "Invert it into XNOR", "7486 Pin 3 to 7404 Pin 1; Pin 2 is A ⊙ B — wire it to LED0"),
            LabObjective("obj4", "Ground every spare input", "Six XOR inputs and five inverter inputs"),
            LabObjective("obj5", "Compare LED0 and LED1", "The two columns must be exact complements on every row")
        ),
        expectedFunction = { inputs ->
            val xor = inputs[0] xor inputs[1]
            listOf(!xor, xor)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val xor = circuit.addChip("7486", trench = 1, startColumn = 10)
            val inv = circuit.addChip("7404", trench = 1, startColumn = 24)
            powerDip14(circuit, xor)
            powerDip14(circuit, inv)

            // XOR stage: Gate 1 (Pins 1,2 -> 3) of the 7486.
            circuit.addWire(AD200Topology.TERM_SW0, xor.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, xor.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(xor.getPinSocket(3), AD200Topology.TERM_LED1, WireColor.GREEN)

            // Inverting stage: 7404 gate 1 (1 -> 2) turns XOR into XNOR.
            circuit.addWire(xor.getPinSocket(3), inv.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(inv.getPinSocket(2), AD200Topology.TERM_LED0, WireColor.GREEN)

            tiePinsLow(circuit, xor, listOf(4, 5, 9, 10, 12, 13))
            tiePinsLow(circuit, inv, listOf(3, 5, 9, 11, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.step()
        }
    ),

    // ---------------------------------------------------------------------------------------
    // Experiment 16 — the non-inverting buffer. NOT(NOT A) = A, but with drive restored.
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp16_buffer_7404",
        labNumber = 16,
        title = EXP16_TITLE,
        subtitle = "Variation A · two cascaded 7404 inverters",
        description = "A buffer passes logic through unchanged — Y = A — while re-driving it at " +
            "full TTL strength, which is what a weak source needs before it can fan out to many " +
            "gates. The purest build is two cascaded inverters: the first complements A, the " +
            "second complements it back, and NOT(NOT A) = A. LED1 taps the midpoint so the " +
            "intermediate inversion is on display next to the restored output; LED0 shows the " +
            "buffered result. The cost is two gate delays, which is why real designers reach for " +
            "a 7407/7434 when they need many — parts this trainer does not stock, so the cascade " +
            "is the honest technique on this bench.",
        targetChips = listOf("7404"),
        switchIndices = listOf(0),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("A (SW0)"),
        outputLabels = listOf("Y = A (LED0)", "A' (LED1)"),
        objectives = listOf(
            LabObjective("obj1", "Power the 7404", "Pin 14 to +5V, Pin 7 to ground"),
            LabObjective("obj2", "First inversion", "SW0 to Pin 1; Pin 2 is A' — wire it to LED1"),
            LabObjective("obj3", "Second inversion", "Pin 2 to Pin 3; Pin 4 is A again — wire it to LED0"),
            LabObjective("obj4", "Ground the four spare inverters", "Pins 5, 9, 11 and 13 to the ground rail"),
            LabObjective("obj5", "Verify the identity", "LED0 tracks SW0 exactly on both rows; LED1 is its complement")
        ),
        expectedFunction = { inputs ->
            listOf(inputs[0], !inputs[0])
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val inv = circuit.addChip("7404", trench = 1, startColumn = 10)
            powerDip14(circuit, inv)

            // Gate 1 (1 -> 2): A'. Gate 2 (3 -> 4): A again.
            circuit.addWire(AD200Topology.TERM_SW0, inv.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(inv.getPinSocket(2), AD200Topology.TERM_LED1, WireColor.BLUE)
            circuit.addWire(inv.getPinSocket(2), inv.getPinSocket(3), WireColor.WHITE)
            circuit.addWire(inv.getPinSocket(4), AD200Topology.TERM_LED0, WireColor.GREEN)

            tiePinsLow(circuit, inv, listOf(5, 9, 11, 13))

            circuit.switches[0] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp16_buffer_7432",
        labNumber = 16,
        title = EXP16_TITLE,
        subtitle = "Variation B · 7432 OR gate with tied inputs",
        description = "The same buffer from a single gate: tie both inputs of an OR gate " +
            "together and Y = A + A = A. One gate, one package, one gate delay — cheaper than " +
            "Variation A's two inverters, at the price of an unused input that must be treated " +
            "as a real input and wired, never left open. This is the same trick the D/A " +
            "converter experiment uses to drive its threshold indicators from a gate stage " +
            "rather than a bare wire. LED1 shows the raw switch line and LED0 the buffered " +
            "output, so the identity can be checked directly against the source.",
        targetChips = listOf("7432"),
        switchIndices = listOf(0),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("A (SW0)"),
        outputLabels = listOf("Y = A (LED0)", "A direct (LED1)"),
        objectives = listOf(
            LabObjective("obj1", "Power the 7432", "Pin 14 to +5V, Pin 7 to ground"),
            LabObjective("obj2", "Tie the gate inputs", "SW0 to Pin 1 and Pin 1 to Pin 2, so the gate sees A on both inputs"),
            LabObjective("obj3", "Read the output", "Pin 3 is Y = A + A = A — wire it to LED0"),
            LabObjective("obj4", "Display the source beside it", "Jumper SW0 straight to LED1 so the buffered and raw lines sit side by side"),
            LabObjective("obj5", "Ground the three spare gates", "Pins 4, 5, 9, 10, 12 and 13 to the ground rail")
        ),
        expectedFunction = { inputs ->
            listOf(inputs[0], inputs[0])
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val or = circuit.addChip("7432", trench = 1, startColumn = 10)
            powerDip14(circuit, or)

            // Gate 1 with inputs tied: Y = A + A = A.
            circuit.addWire(AD200Topology.TERM_SW0, or.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(or.getPinSocket(1), or.getPinSocket(2), WireColor.YELLOW)
            circuit.addWire(or.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

            // The raw switch line, for direct comparison with the buffered output.
            circuit.addWire(AD200Topology.TERM_SW0, AD200Topology.TERM_LED1, WireColor.WHITE)

            tiePinsLow(circuit, or, listOf(4, 5, 9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.step()
        }
    ),

    // ---------------------------------------------------------------------------------------
    // Experiment 17 — the 7420 dual 4-input NAND: wide-input logic and the NC pins.
    // ---------------------------------------------------------------------------------------

    LabExperiment(
        id = "exp17_quad_nand_7420",
        labNumber = 17,
        title = EXP17_TITLE,
        subtitle = "Variation A · 7420 4-input NAND truth table",
        description = "Wide-input logic: a 4-input NAND is LOW only when all four inputs are HIGH " +
            "simultaneously — sixteen input combinations, one of which is special. The 7420 " +
            "packages two of them, and its pin map carries a lesson of its own: Pins 3 and 11 " +
            "are internally not connected, left over from the 14-pin package's layout, so the " +
            "gate-1 inputs are Pins 1, 2, 4 and 5 straddling the dead Pin 3. Wire all four " +
            "inputs and sweep SW0..SW3 through all sixteen vectors; only 1111 extinguishes LED0.",
        targetChips = listOf("7420"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0),
        inputLabels = listOf("A (SW0)", "B (SW1)", "C (SW2)", "D (SW3)"),
        outputLabels = listOf("Y (LED0)"),
        objectives = listOf(
            LabObjective("obj1", "Power the 7420", "Pin 14 to +5V, Pin 7 to ground"),
            LabObjective("obj2", "Note the NC pins", "Pins 3 and 11 connect to nothing inside the package — do not wire them"),
            LabObjective("obj3", "Wire gate 1's four inputs", "SW0 to 1A (Pin 1), SW1 to 1B (Pin 2), SW2 to 1C (Pin 4), SW3 to 1D (Pin 5)"),
            LabObjective("obj4", "Read the output", "1Y is Pin 6 — wire it to LED0"),
            LabObjective("obj5", "Sweep all sixteen vectors", "LED0 is dark only for A=B=C=D=1"),
            LabObjective("obj6", "Ground the second gate", "Pins 9, 10, 12 and 13 to the ground rail")
        ),
        expectedFunction = { inputs ->
            listOf(!(inputs[0] && inputs[1] && inputs[2] && inputs[3]))
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val nand = circuit.addChip("7420", trench = 1, startColumn = 10)
            powerDip14(circuit, nand)

            // Gate 1: inputs 1A/1B/1C/1D on Pins 1, 2, 4, 5 (Pin 3 is NC), output 1Y on Pin 6.
            circuit.addWire(AD200Topology.TERM_SW0, nand.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, nand.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, nand.getPinSocket(4), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW3, nand.getPinSocket(5), WireColor.BROWN)
            circuit.addWire(nand.getPinSocket(6), AD200Topology.TERM_LED0, WireColor.GREEN)

            // Gate 2 (Pins 9, 10, 12, 13 -> 8) is spare.
            tiePinsLow(circuit, nand, listOf(9, 10, 12, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.step()
        }
    ),

    LabExperiment(
        id = "exp17_quad_and_7420_7404",
        labNumber = 17,
        title = EXP17_TITLE,
        subtitle = "Variation B · 4-input AND from 7420 + 7404",
        description = "The wide-gate version of Lab 8: NAND then invert, and the 4-input AND " +
            "A·B·C·D falls out — HIGH only when all four inputs are HIGH. LED0 shows the raw " +
            "4-input NAND and LED1 the recovered AND, so both columns of the sixteen-row table " +
            "are on the bench at once and the inversion is visible rather than asserted. One " +
            "7420, one 7404, and the same De Morgan identity the 2-input labs established, now " +
            "with four literals.",
        targetChips = listOf("7420", "7404"),
        switchIndices = listOf(0, 1, 2, 3),
        ledIndices = listOf(0, 1),
        inputLabels = listOf("A (SW0)", "B (SW1)", "C (SW2)", "D (SW3)"),
        outputLabels = listOf("NAND (LED0)", "AND (LED1)"),
        objectives = listOf(
            LabObjective("obj1", "Power both packages", "Pin 14 to +5V and Pin 7 to ground on the 7420 and the 7404"),
            LabObjective("obj2", "Wire the wide NAND", "SW0..SW3 to 7420 Pins 1, 2, 4 and 5; Pin 6 is (A·B·C·D)' — wire it to LED0"),
            LabObjective("obj3", "Invert to AND", "7420 Pin 6 to 7404 Pin 1; Pin 2 is A·B·C·D — wire it to LED1"),
            LabObjective("obj4", "Ground the spares", "Gate 2 of the 7420 and five inverters of the 7404"),
            LabObjective("obj5", "Verify the recovered AND", "LED1 is lit only for A=B=C=D=1, and is the complement of LED0 on every row")
        ),
        expectedFunction = { inputs ->
            val and = inputs[0] && inputs[1] && inputs[2] && inputs[3]
            listOf(!and, and)
        },
        buildCircuit = { circuit ->
            circuit.clearAll()
            circuit.masterPower = true
            wireBasicPowerRails(circuit)

            val nand = circuit.addChip("7420", trench = 1, startColumn = 10)
            val inv = circuit.addChip("7404", trench = 1, startColumn = 24)
            powerDip14(circuit, nand)
            powerDip14(circuit, inv)

            // 7420 gate 1: the wide NAND.
            circuit.addWire(AD200Topology.TERM_SW0, nand.getPinSocket(1), WireColor.YELLOW)
            circuit.addWire(AD200Topology.TERM_SW1, nand.getPinSocket(2), WireColor.ORANGE)
            circuit.addWire(AD200Topology.TERM_SW2, nand.getPinSocket(4), WireColor.PURPLE)
            circuit.addWire(AD200Topology.TERM_SW3, nand.getPinSocket(5), WireColor.BROWN)
            circuit.addWire(nand.getPinSocket(6), AD200Topology.TERM_LED0, WireColor.GREEN)

            // 7404 gate 1 (1 -> 2): invert the NAND into a 4-input AND.
            circuit.addWire(nand.getPinSocket(6), inv.getPinSocket(1), WireColor.BLUE)
            circuit.addWire(inv.getPinSocket(2), AD200Topology.TERM_LED1, WireColor.GREEN)

            tiePinsLow(circuit, nand, listOf(9, 10, 12, 13))
            tiePinsLow(circuit, inv, listOf(3, 5, 9, 11, 13))

            circuit.switches[0] = false
            circuit.switches[1] = false
            circuit.switches[2] = false
            circuit.switches[3] = false
            circuit.step()
        }
    )
)
