package com.logiclabs.feature.tools.courseware

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology

data class LabObjective(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false
)

data class LabExperiment(
    val id: String,
    val labNumber: Int,
    val title: String,
    val subtitle: String,
    val description: String,
    val targetChips: List<String>,
    val switchIndices: List<Int>,
    val ledIndices: List<Int>,
    val inputLabels: List<String>,
    val outputLabels: List<String>,
    val objectives: List<LabObjective>,
    val expectedFunction: (List<Boolean>) -> List<Boolean>,
    val buildCircuit: (BreadboardCircuit) -> Unit
)

object LabCurriculum {

    private fun setupPowerRails(circuit: BreadboardCircuit) {
        // Connect DC Power Supply (+5V VCC and GND) to the Top Power Rails.
        // Top Rails (Rail 0 VCC, Rail 1 GND) — the only rails any classic lab uses:
        // every chip powers and grounds from the top pair, so the former bottom-rail
        // bridge jumpers carried power to rails nothing consumes. They were pure
        // visual clutter in every one-click build (verified: removing them changes
        // no lab's sealed sweep), so they are gone.
        circuit.addWire(AD200Topology.TERM_POWER_VCC, AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0), WireColor.RED)
        circuit.addWire(AD200Topology.TERM_POWER_GND, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0), WireColor.BLACK)
    }

    val classicLabs: List<LabExperiment> = listOf(
        // Lab 1: Inverter / NOT Gate (7404)
        LabExperiment(
            id = "lab1_not",
            labNumber = 1,
            title = "Inverter / NOT Gate Logic",
            subtitle = "7404 Hex Inverter IC",
            description = "Construct an elementary digital logic inverter circuit using the 7404 Hex Inverter. Verify Boolean complementation: Output Y = NOT A.",
            targetChips = listOf("7404"),
            switchIndices = listOf(0),
            ledIndices = listOf(0),
            inputLabels = listOf("A (SW0)"),
            outputLabels = listOf("Y (LED0)"),
            objectives = listOf(
                LabObjective("obj1", "Wire VCC (Pin 14) and GND (Pin 7)", "Connect positive +5V rail to Pin 14 and ground rail to Pin 7"),
                LabObjective("obj2", "Route SW0 to Input 1A (Pin 1)", "Connect logic switch 0 to Gate 1 inverter input"),
                LabObjective("obj3", "Route Output 1Y (Pin 2) to LED0", "Observe inverted logic state on LED0"),
                LabObjective("obj4", "Verify NOT Truth Table", "Verify Output is 1 when input is 0, and 0 when input is 1")
            ),
            expectedFunction = { inputs -> listOf(!inputs[0]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7404", trench = 1, startColumn = 10)
                // Power: Pin 14 to Top VCC Rail, Pin 7 to Top GND Rail
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                // Input: SW0 to Gate 1A (Pin 1)
                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                // Output: Gate 1Y (Pin 2) to LED0
                circuit.addWire(chip.getPinSocket(2), AD200Topology.TERM_LED0, WireColor.GREEN)

                circuit.switches[0] = false
                circuit.step()
            }
        ),

        // Lab 2: 2-Input NAND Gate (7400)
        LabExperiment(
            id = "lab2_nand",
            labNumber = 2,
            title = "2-Input NAND Gate Logic",
            subtitle = "7400 Quad 2-Input NAND Gate",
            description = "Investigate the fundamental universal building block of digital computing: the Quad 2-Input NAND Gate. Verify Boolean relation: Y = NOT(A AND B).",
            targetChips = listOf("7400"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0),
            inputLabels = listOf("A (SW0)", "B (SW1)"),
            outputLabels = listOf("Y (LED0)"),
            objectives = listOf(
                LabObjective("obj1", "Power 7400 IC", "Connect Pin 14 to +5V and Pin 7 to GND"),
                LabObjective("obj2", "Route SW0 and SW1 to Pins 1 & 2", "Apply inputs A and B to Gate 1"),
                LabObjective("obj3", "Connect Pin 3 to LED0", "Monitor output on LED0"),
                LabObjective("obj4", "Verify 4 Truth Table Vectors", "Cycle switches through 00, 01, 10, 11")
            ),
            expectedFunction = { inputs -> listOf(!(inputs[0] && inputs[1])) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7400", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
                circuit.addWire(chip.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        ),

        // Lab 3: 2-Input NOR Gate (7402)
        LabExperiment(
            id = "lab3_nor",
            labNumber = 3,
            title = "2-Input NOR Gate Logic",
            subtitle = "7402 Quad 2-Input NOR Gate",
            description = "Construct and verify the universal NOR gate using the 7402 IC. Note the unique inverted pinout where Pin 1 is Output 1Y and Pins 2 & 3 are Inputs 1A & 1B.",
            targetChips = listOf("7402"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0),
            inputLabels = listOf("A (SW0)", "B (SW1)"),
            outputLabels = listOf("Y (LED0)"),
            objectives = listOf(
                LabObjective("obj1", "Power 7402 IC", "Connect Pin 14 to +5V and Pin 7 to GND"),
                LabObjective("obj2", "Wire Inputs 1A (Pin 2) and 1B (Pin 3)", "Connect SW0 and SW1 to inputs"),
                LabObjective("obj3", "Wire Output 1Y (Pin 1) to LED0", "Connect Output 1Y to LED indicator"),
                LabObjective("obj4", "Verify NOR Truth Table", "Verify Output is 1 only when both inputs are 0")
            ),
            expectedFunction = { inputs -> listOf(!(inputs[0] || inputs[1])) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7402", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                // Note: 7402 Gate 1 inputs are Pin 2 (1A) and Pin 3 (1B); Output is Pin 1 (1Y)
                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(2), WireColor.YELLOW)
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(3), WireColor.ORANGE)
                circuit.addWire(chip.getPinSocket(1), AD200Topology.TERM_LED0, WireColor.GREEN)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        ),

        // Lab 4: 2-Input AND Gate (7408)
        LabExperiment(
            id = "lab4_and",
            labNumber = 4,
            title = "2-Input AND Gate Logic",
            subtitle = "7408 Quad 2-Input AND Gate",
            description = "Implement the logical conjunction function using the 7408 Quad 2-Input AND Gate. Output Y is HIGH if and only if both inputs A and B are HIGH.",
            targetChips = listOf("7408"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0),
            inputLabels = listOf("A (SW0)", "B (SW1)"),
            outputLabels = listOf("Y (LED0)"),
            objectives = listOf(
                LabObjective("obj1", "Power 7408 IC", "Connect Pin 14 to +5V and Pin 7 to GND"),
                LabObjective("obj2", "Wire Inputs SW0 & SW1 to Gate 1", "Connect SW0 to Pin 1, SW1 to Pin 2"),
                LabObjective("obj3", "Wire Output Pin 3 to LED0", "Observe AND gate response"),
                LabObjective("obj4", "Verify Conjunction Table", "Verify Output is 1 only when A=1 and B=1")
            ),
            expectedFunction = { inputs -> listOf(inputs[0] && inputs[1]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7408", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
                circuit.addWire(chip.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        ),

        // Lab 5: 2-Input OR Gate (7432)
        LabExperiment(
            id = "lab5_or",
            labNumber = 5,
            title = "2-Input OR Gate Logic",
            subtitle = "7432 Quad 2-Input OR Gate",
            description = "Implement logical disjunction using the 7432 Quad 2-Input OR Gate. Output Y is HIGH whenever either or both inputs A or B are HIGH.",
            targetChips = listOf("7432"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0),
            inputLabels = listOf("A (SW0)", "B (SW1)"),
            outputLabels = listOf("Y (LED0)"),
            objectives = listOf(
                LabObjective("obj1", "Power 7432 IC", "Connect Pin 14 to +5V and Pin 7 to GND"),
                LabObjective("obj2", "Wire Inputs SW0 & SW1 to Gate 1", "Connect SW0 to Pin 1, SW1 to Pin 2"),
                LabObjective("obj3", "Wire Output Pin 3 to LED0", "Observe OR gate response"),
                LabObjective("obj4", "Verify Disjunction Table", "Verify Output is 0 only when A=0 and B=0")
            ),
            expectedFunction = { inputs -> listOf(inputs[0] || inputs[1]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7432", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
                circuit.addWire(chip.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        ),

        // Lab 6: Exclusive-OR (XOR) Gate (7486)
        LabExperiment(
            id = "lab6_xor",
            labNumber = 6,
            title = "Exclusive-OR (XOR) Gate Logic",
            subtitle = "7486 Quad 2-Input XOR Gate",
            description = "Construct an Exclusive-OR (XOR) parity circuit using the 7486 IC. Output Y is HIGH if and only if the inputs differ (odd parity detection).",
            targetChips = listOf("7486"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0),
            inputLabels = listOf("A (SW0)", "B (SW1)"),
            outputLabels = listOf("Y (LED0)"),
            objectives = listOf(
                LabObjective("obj1", "Power 7486 IC", "Connect Pin 14 to +5V and Pin 7 to GND"),
                LabObjective("obj2", "Wire Inputs SW0 & SW1", "Connect SW0 to Pin 1, SW1 to Pin 2"),
                LabObjective("obj3", "Wire Output Pin 3 to LED0", "Monitor XOR output on LED0"),
                LabObjective("obj4", "Verify Parity Table", "Verify Output is 1 on (0,1) and (1,0); 0 on (0,0) and (1,1)")
            ),
            expectedFunction = { inputs -> listOf(inputs[0] xor inputs[1]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7486", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
                circuit.addWire(chip.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        ),

        // Lab 7: Half Adder Circuit (7486 XOR + 7408 AND)
        LabExperiment(
            id = "lab7_half_adder",
            labNumber = 7,
            title = "Half Adder Binary Arithmetic",
            subtitle = "7486 (XOR) + 7408 (AND)",
            description = "Synthesize an arithmetic Half Adder by cascading a 7486 XOR gate for the Sum bit (A ⊕ B) and a 7408 AND gate for the Carry bit (A · B).",
            targetChips = listOf("7486", "7408"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0, 1),
            inputLabels = listOf("A (SW0)", "B (SW1)"),
            outputLabels = listOf("SUM (LED0)", "CARRY (LED1)"),
            objectives = listOf(
                LabObjective("obj1", "Mount Dual ICs", "Place 7486 at Col 10 and 7408 at Col 24"),
                LabObjective("obj2", "Power Both Chips", "Wire Pin 14 of both chips to VCC and Pin 7 to GND"),
                LabObjective("obj3", "Distribute Inputs A & B", "Connect SW0 to Pin 1 of both chips; SW1 to Pin 2 of both chips"),
                LabObjective("obj4", "Wire Sum & Carry Outputs", "Route 7486 Pin 3 to LED0 (Sum) and 7408 Pin 3 to LED1 (Carry)"),
                LabObjective("obj5", "Verify Binary Addition", "Verify 1 + 1 = 2 (Sum=0, Carry=1)")
            ),
            expectedFunction = { inputs -> listOf(inputs[0] xor inputs[1], inputs[0] && inputs[1]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val xorChip = circuit.addChip("7486", trench = 1, startColumn = 10)
                val andChip = circuit.addChip("7408", trench = 1, startColumn = 24)

                // Power both chips
                circuit.addWire(xorChip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(xorChip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                circuit.addWire(andChip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 24), WireColor.RED)
                circuit.addWire(andChip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 24), WireColor.BLACK)

                // Input A: SW0 -> 7486 Pin 1, and jumper to 7408 Pin 1
                circuit.addWire(AD200Topology.TERM_SW0, xorChip.getPinSocket(1), WireColor.YELLOW)
                circuit.addWire(xorChip.getPinSocket(1), andChip.getPinSocket(1), WireColor.YELLOW)

                // Input B: SW1 -> 7486 Pin 2, and jumper to 7408 Pin 2
                circuit.addWire(AD200Topology.TERM_SW1, xorChip.getPinSocket(2), WireColor.ORANGE)
                circuit.addWire(xorChip.getPinSocket(2), andChip.getPinSocket(2), WireColor.ORANGE)

                // Outputs: 7486 Pin 3 -> LED0 (Sum), 7408 Pin 3 -> LED1 (Carry)
                circuit.addWire(xorChip.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
                circuit.addWire(andChip.getPinSocket(3), AD200Topology.TERM_LED1, WireColor.BLUE)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        ),

        // Lab 8: Universal NAND: AND Gate (7400)
        LabExperiment(
            id = "lab8_nand_and",
            labNumber = 8,
            title = "Universal NAND: AND Gate Synthesis",
            subtitle = "7400 Quad 2-Input NAND Gate",
            description = "Synthesize a 2-input AND gate exclusively using two gates from a 7400 NAND IC: Gate 1 performs NAND, and Gate 2 inverts the output.",
            targetChips = listOf("7400"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0),
            inputLabels = listOf("A (SW0)", "B (SW1)"),
            outputLabels = listOf("Y (LED0)"),
            objectives = listOf(
                LabObjective("obj1", "Wire Gate 1 NAND", "Connect SW0 to Pin 1, SW1 to Pin 2"),
                LabObjective("obj2", "Cascade to Gate 2 Inverter", "Connect Pin 3 (Output 1) to Pin 4 & Pin 5 (tied together)"),
                LabObjective("obj3", "Wire Final Output", "Route Pin 6 to LED0"),
                LabObjective("obj4", "Verify Synthesized AND Function", "Verify Output is 1 only when A=1 and B=1")
            ),
            expectedFunction = { inputs -> listOf(inputs[0] && inputs[1]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7400", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                // Gate 1: Inputs A and B
                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)

                // Gate 1 Output (Pin 3) to Gate 2 Input 2A (Pin 4) and 2B (Pin 5)
                circuit.addWire(chip.getPinSocket(3), chip.getPinSocket(4), WireColor.BLUE)
                circuit.addWire(chip.getPinSocket(4), chip.getPinSocket(5), WireColor.BLUE)

                // Gate 2 Output (Pin 6) to LED0
                circuit.addWire(chip.getPinSocket(6), AD200Topology.TERM_LED0, WireColor.GREEN)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        ),

        // Lab 9: Universal NAND: OR Gate (7400 De Morgan)
        LabExperiment(
            id = "lab9_nand_or",
            labNumber = 9,
            title = "Universal NAND: OR Gate (De Morgan)",
            subtitle = "7400 Quad 2-Input NAND Gate",
            description = "Synthesize an OR gate using De Morgan's theorem: A + B = NOT(NOT A · NOT B), using three NAND gates from a single 7400 chip.",
            targetChips = listOf("7400"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0),
            inputLabels = listOf("A (SW0)", "B (SW1)"),
            outputLabels = listOf("Y (LED0)"),
            objectives = listOf(
                LabObjective("obj1", "Invert Input A with Gate 1", "Tie Pins 1 & 2 to SW0, Output ~A on Pin 3"),
                LabObjective("obj2", "Invert Input B with Gate 2", "Tie Pins 4 & 5 to SW1, Output ~B on Pin 6"),
                LabObjective("obj3", "Combine with Gate 3", "Connect Pin 3 to Pin 9 and Pin 6 to Pin 10"),
                LabObjective("obj4", "Verify De Morgan OR Equivalence", "Observe Pin 8 output on LED0")
            ),
            expectedFunction = { inputs -> listOf(inputs[0] || inputs[1]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7400", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                // Gate 1: Invert A (Pins 1 & 2 tied to SW0)
                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                circuit.addWire(chip.getPinSocket(1), chip.getPinSocket(2), WireColor.YELLOW)

                // Gate 2: Invert B (Pins 4 & 5 tied to SW1)
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(4), WireColor.ORANGE)
                circuit.addWire(chip.getPinSocket(4), chip.getPinSocket(5), WireColor.ORANGE)

                // Gate 3: Inputs ~A (Pin 3) to Pin 9, ~B (Pin 6) to Pin 10
                circuit.addWire(chip.getPinSocket(3), chip.getPinSocket(9), WireColor.BLUE)
                circuit.addWire(chip.getPinSocket(6), chip.getPinSocket(10), WireColor.PURPLE)

                // Gate 3 Output (Pin 8) to LED0
                circuit.addWire(chip.getPinSocket(8), AD200Topology.TERM_LED0, WireColor.GREEN)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        ),

        // Lab 10: SR Latch (7400 Cross-Coupled NANDs)
        LabExperiment(
            id = "lab10_sr_latch",
            labNumber = 10,
            title = "SR Latch Bistable Multivibrator",
            subtitle = "7400 Cross-Coupled NAND Gates",
            description = "Construct an active-LOW Set-Reset (SR) latch using cross-coupled NAND gates. Demonstrates bit storage, state retention, and feedback memory.",
            targetChips = listOf("7400"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0, 1),
            inputLabels = listOf("~S (SW0)", "~R (SW1)"),
            outputLabels = listOf("Q (LED0)", "~Q (LED1)"),
            objectives = listOf(
                LabObjective("obj1", "Wire Cross-Coupled Feedback", "Connect Pin 3 (Q) to Pin 5; Pin 6 (~Q) to Pin 2"),
                LabObjective("obj2", "Connect ~Set and ~Reset Switches", "SW0 to Pin 1 (~S); SW1 to Pin 4 (~R)"),
                LabObjective("obj3", "Monitor Q and ~Q", "Pin 3 to LED0; Pin 6 to LED1"),
                LabObjective("obj4", "Verify Latch Memory", "Pulse ~S to 0 to Set; pulse ~R to 0 to Reset; keep 11 to hold state")
            ),
            expectedFunction = { inputs ->
                // ~S=0, ~R=1 -> Q=1, ~Q=0 (Set)
                // ~S=1, ~R=0 -> Q=0, ~Q=1 (Reset)
                // ~S=1, ~R=1 -> Hold (treated as stable 1,0 or 0,1)
                val setLow = !inputs[0]
                val resetLow = !inputs[1]
                if (setLow && !resetLow) listOf(true, false)
                else if (!setLow && resetLow) listOf(false, true)
                else listOf(true, false)
            },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7400", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                // Gate 1: Pin 1 (~S), Pin 2 (cross-coupled from Gate 2 Pin 6), Pin 3 (Q)
                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                // Gate 2: Pin 4 (~R), Pin 5 (cross-coupled from Gate 1 Pin 3), Pin 6 (~Q)
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(4), WireColor.ORANGE)

                // Cross-coupling feedback wires:
                circuit.addWire(chip.getPinSocket(3), chip.getPinSocket(5), WireColor.BLUE)
                circuit.addWire(chip.getPinSocket(6), chip.getPinSocket(2), WireColor.PURPLE)

                // Outputs to LEDs
                circuit.addWire(chip.getPinSocket(3), AD200Topology.TERM_LED0, WireColor.GREEN)
                circuit.addWire(chip.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.WHITE)

                // Default state: ~S=1, ~R=1 (quiescent hold)
                circuit.switches[0] = true
                circuit.switches[1] = true
                circuit.step()
            }
        ),

        // Lab 11: D Flip-Flop (7474 Dual D-Type Flip-Flop)
        LabExperiment(
            id = "lab11_d_flipflop",
            labNumber = 11,
            title = "D Flip-Flop Edge-Triggered Register",
            subtitle = "7474 Dual D-Type Positive-Edge-Triggered",
            description = "Examine synchronous edge-triggered data storage with the 7474 D Flip-Flop. The Q output captures the D data input on the rising edge of CLK.",
            targetChips = listOf("7474"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0, 1),
            inputLabels = listOf("D (SW0)", "CLK (SW1)"),
            outputLabels = listOf("Q (LED0)", "~Q (LED1)"),
            objectives = listOf(
                LabObjective("obj1", "Power 7474 IC", "Pin 14 to VCC, Pin 7 to GND"),
                LabObjective("obj2", "De-assert Asynchronous Inputs", "Wire ~1CLR (Pin 1) and ~1PRE (Pin 4) to +5V VCC"),
                LabObjective("obj3", "Wire Data and Clock", "Connect SW0 to 1D (Pin 2); SW1 to 1CLK (Pin 3)"),
                LabObjective("obj4", "Wire Complementary Outputs", "Connect 1Q (Pin 5) to LED0 and ~1Q (Pin 6) to LED1"),
                LabObjective("obj5", "Verify Edge-Triggered Transfer", "Toggle CLK 0 -> 1 and verify data latching")
            ),
            expectedFunction = { inputs -> listOf(inputs[0], !inputs[0]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                val chip = circuit.addChip("7474", trench = 1, startColumn = 10)
                circuit.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 10), WireColor.RED)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 10), WireColor.BLACK)

                // De-assert active-LOW Clear (Pin 1) and Preset (Pin 4) to +5V VCC
                circuit.addWire(chip.getPinSocket(1), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 11), WireColor.RED)
                circuit.addWire(chip.getPinSocket(4), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 12), WireColor.RED)

                // Data input D: SW0 -> Pin 2
                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(2), WireColor.YELLOW)
                // Clock input CLK: SW1 -> Pin 3
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(3), WireColor.ORANGE)

                // Outputs: Pin 5 (Q) -> LED0, Pin 6 (~Q) -> LED1
                circuit.addWire(chip.getPinSocket(5), AD200Topology.TERM_LED0, WireColor.GREEN)
                circuit.addWire(chip.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.BLUE)

                circuit.switches[0] = true
                circuit.switches[1] = true
                circuit.step()
            }
        ),

        // Lab 12: 4-Bit Binary Full Adder (7483)
        LabExperiment(
            id = "lab12_full_adder",
            labNumber = 12,
            title = "4-Bit Binary Full Adder Arithmetic",
            subtitle = "7483 4-Bit Binary Full Adder IC",
            description = "Construct a high-speed parallel binary adder with carry lookahead using the 7483 16-pin IC. With A2..A4 and B2..B4 tied low, the chip adds the two LSBs: Pin 9 (S1) shows the sum bit, and the carry out of bit 1 surfaces on Pin 6 (S2), since bit 2 of the sum is exactly that carry when the higher operands are zero.",
            targetChips = listOf("7483"),
            switchIndices = listOf(0, 1),
            ledIndices = listOf(0, 1),
            inputLabels = listOf("A0 (SW0)", "B0 (SW1)"),
            outputLabels = listOf("S0 (LED0)", "C1 (LED1)"),
            objectives = listOf(
                LabObjective("obj1", "Power 7483 16-Pin IC", "Connect Pin 5 to VCC and Pin 12 to GND"),
                LabObjective("obj2", "Wire Carry In C0 to Ground", "Tie Pin 13 (C0) to GND rail"),
                LabObjective("obj3", "Connect Operands A0 & B0", "SW0 to Pin 10 (A1); SW1 to Pin 11 (B1)"),
                LabObjective("obj4", "Tie Unused Bits to GND", "Connect Pins 8, 7, 3, 4, 1, 16 to GND"),
                LabObjective("obj5", "Monitor Sum & Carry", "Pin 9 (S1) to LED0; Pin 6 (S2) to LED1")
            ),
            expectedFunction = { inputs -> listOf(inputs[0] xor inputs[1], inputs[0] && inputs[1]) },
            buildCircuit = { circuit ->
                circuit.clearAll()
                circuit.masterPower = true
                setupPowerRails(circuit)

                // 7483 is a 16-pin chip: Pin 5 VCC, Pin 12 GND
                val chip = circuit.addChip("7483", trench = 1, startColumn = 12)
                circuit.addWire(chip.getPinSocket(5), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 12), WireColor.RED)
                circuit.addWire(chip.getPinSocket(12), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 12), WireColor.BLACK)

                // Carry In (Pin 13) to GND
                circuit.addWire(chip.getPinSocket(13), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 13), WireColor.BLACK)

                // Input A0 (Pin 10) to SW0
                circuit.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(10), WireColor.YELLOW)
                // Input B0 (Pin 11) to SW1
                circuit.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(11), WireColor.ORANGE)

                // Tie remaining inputs (A2..4, B2..4) to GND
                circuit.addWire(chip.getPinSocket(8), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 8), WireColor.BLACK)
                circuit.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 7), WireColor.BLACK)
                circuit.addWire(chip.getPinSocket(3), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 3), WireColor.BLACK)
                circuit.addWire(chip.getPinSocket(4), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 4), WireColor.BLACK)
                circuit.addWire(chip.getPinSocket(1), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 1), WireColor.BLACK)
                circuit.addWire(chip.getPinSocket(16), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 16), WireColor.BLACK)

                // Output Sum S0 (Pin 9) -> LED0, Carry Out C1 (Pin 6) -> LED1
                circuit.addWire(chip.getPinSocket(9), AD200Topology.TERM_LED0, WireColor.GREEN)
                circuit.addWire(chip.getPinSocket(6), AD200Topology.TERM_LED1, WireColor.BLUE)

                circuit.switches[0] = false
                circuit.switches[1] = false
                circuit.step()
            }
        )
    )
}
