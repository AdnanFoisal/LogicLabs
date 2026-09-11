package com.logiclabs.feature.tools.courseware

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.feature.tools.analog.DiscreteApparatus
import com.logiclabs.feature.tools.analog.NodeLevel

/**
 * Experiment 01 — fundamental gates built from discrete components.
 *
 * Three variations on one bench setup: a diode OR, a diode AND, and a resistor-transistor
 * inverter, all from two diodes, a 1 kOhm resistor, a 4 kOhm resistor and one NPN.
 *
 * ## Why this file looks the way it does
 *
 * The simulation engine is digital. `BreadboardCircuit` resolves a net by counting HIGH and LOW
 * drivers on it; `NetlistDsu` knows only about continuity. There is no diode primitive, no BJT
 * primitive, and `addChip` resolves its part number through the TTL catalog, so there is nothing
 * to place for a 1N4148. Adding an analog solver to carry three gates would be a poor trade and is
 * out of scope here.
 *
 * So each preset is built from two halves that are deliberately kept distinct:
 *
 *  - **The Boolean half is real.** A 74-series gate whose function is identical to the discrete
 *    network is placed on the board and wired switch-to-input, output-to-LED, through the same
 *    tie-point columns the discrete network's nodes occupy. The LED indicators, the DSU, the
 *    truth-table sweep and the HMAC seal all work unmodified, because from the engine's point of
 *    view this is an ordinary TTL circuit. Note also that `TestBenchVerifier` raises a hard
 *    diagnostic when `placedChips` is empty, so a chip-less preset could never pass verification
 *    even if the engine could simulate one.
 *  - **The analog half is honest.** Every node voltage the student reads comes from
 *    `DiscreteApparatus` in the analog package, which computes it arithmetically from the switch
 *    bits under an ideal-switch model of each device. The two halves cannot drift apart, because
 *    each preset's `expectedFunction` is *derived from* the analog model rather than written out
 *    independently — see the `expectedFunction` lambdas below.
 *
 * The two physical resistors are placed for real via `BreadboardCircuit.addResistor`, which records
 * them in `circuit.passives`. They stay electrically inert: `rebuildNetlist` only merges passives
 * below 100 Ohms, so a 1 kOhm pull-down does not short node Y to ground the way a jumper would.
 * The emitter-to-ground connection, by contrast, genuinely *is* a piece of wire, so it is wired.
 *
 * The diode bodies and the transistor have no board graphics and no placement type — see
 * [DISCRETE_NODE_BLOCK] for what drawing them would take. Their tie-point columns are still wired
 * up, so the board a student loads is genuinely populated rather than a bare chip.
 */

/** Every variation shares one title; the UI groups the three rows under it. */
private const val EXPERIMENT_TITLE = "Fundamental Gates from Discrete Components"

/**
 * Terminal block 2 — the upper block of trench 2, on the lower breadboard — is left empty by the
 * classic labs, so the discrete network's nodes live there, clear of the surrogate IC in trench 1.
 *
 * A column's five holes are one net by base continuity, so a node can take a lead in at row 0, pass
 * it on at row 2 and still have row 4 free for its resistor — which is how the same node is
 * physically wired at a real bench.
 *
 * ## What it would take to actually draw a diode or a transistor here
 *
 * These columns are where the parts *would* sit, and nothing renders them today. Four pieces are
 * missing, in dependency order:
 *
 *  1. **A model type.** `PassiveComponent` in core-bridge is a sealed class with `Resistor` and
 *     `Capacitor` cases, each two-terminal. A diode needs a two-terminal case that is *polarised*
 *     (anode/cathode, not the interchangeable `socketA`/`socketB`), and a BJT needs a three-terminal
 *     case — which the sealed hierarchy's `socketA`/`socketB` shape cannot express, so it would need
 *     widening rather than just extending.
 *  2. **Placement plumbing.** `BreadboardCircuit` has `addResistor` and nothing else; it would want
 *     `addDiode`/`addTransistor` alongside it, plus the corresponding cases in `clearAll` and in
 *     `ProjectPersistence` so a saved project round-trips.
 *  3. **Geometry and a painter.** `BreadboardGeometryMapper` maps socket ids to canvas points and
 *     the `canvas` package has `DipPainter`, `TiePointPainter` and `WirePainter` but nothing for a
 *     discrete body. A diode wants an axial body with a cathode band drawn along the line between
 *     its two tie-points; a TO-92 transistor wants a D-shaped can over three adjacent holes with a
 *     lead fan-out, which is the first component in this app whose footprint is neither a DIP nor a
 *     two-point line.
 *  4. **Hit-testing and gestures.** `SocketHitTester` and `BreadboardGestures` would need to pick a
 *     three-lead body up and rotate it, and rotation for a polarised part has to swap terminal
 *     *roles*, not just coordinates — the existing `rotateChip` socket-remapping trick does not
 *     carry over.
 *
 * Note that none of that makes the parts *simulate* — that is a separate and larger change to the
 * netlist resolver, which today counts drivers rather than solving for node voltages.
 */
private const val DISCRETE_NODE_BLOCK = 2

private const val COL_NODE_A = 4
private const val COL_NODE_B = 8
private const val COL_NODE_Y = 12
private const val COL_NODE_EMITTER = 16

/**
 * Variation C occupies the same three columns as the diode gates, so it names them for what they
 * are on the transistor side: the base-resistor input node and the base node itself.
 */
private const val COL_NODE_INPUT = COL_NODE_A
private const val COL_NODE_BASE = COL_NODE_B

/** The surrogate IC sits where every classic lab puts its first chip, for a familiar board. */
private const val SURROGATE_TRENCH = 1
private const val SURROGATE_COLUMN = 10

private fun node(column: Int, row: Int): Int =
    AD200Topology.terminalSocket(DISCRETE_NODE_BLOCK, column, row)

/**
 * Brings the DC supply to all four distribution rails.
 *
 * `LabCurriculum.setupPowerRails` is private, so this is an independent equivalent rather than a
 * call into it: console +5 V and GND land on the top pair of rails, and a jumper carries each down
 * to the bottom pair, which is where the discrete network's pull-up and pull-down return to.
 */
private fun wireSupplyRails(circuit: BreadboardCircuit) {
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

/** Powers a 14-pin DIP surrogate from the top rails: pin 14 to +5 V, pin 7 to ground. */
private fun powerSurrogate(circuit: BreadboardCircuit, chip: BreadboardCircuit.PlacedChipRuntime) {
    circuit.addWire(
        chip.getPinSocket(14),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, SURROGATE_COLUMN),
        WireColor.RED
    )
    circuit.addWire(
        chip.getPinSocket(7),
        AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, SURROGATE_COLUMN),
        WireColor.BLACK
    )
}

/**
 * Variation A — **diode OR**.
 *
 * Topology: D1's anode takes input A and D2's anode takes input B; the two *cathodes* are commoned
 * and that common cathode is the output node Y; R1 (1 kOhm) runs from Y down to ground as the
 * pull-down. Whichever input is HIGH forward-biases its diode and drags Y up to within one forward
 * drop of it, while the other diode is reverse-biased and cannot back-feed the LOW input. With both
 * inputs LOW nothing conducts and the pull-down is the only thing touching Y, so Y rests at 0 V.
 *
 * Levels: 0.00 V / 4.30 V / 4.30 V / 4.30 V across the four input vectors. The Boolean is a clean
 * OR; the HIGH is a diode drop short of the rail.
 */
private val diodeOrLab = LabExperiment(
    id = "exp01_diode_or",
    labNumber = 1,
    title = EXPERIMENT_TITLE,
    subtitle = "Variation A · Diode OR gate",
    description = "Build a two-input OR gate from nothing but two silicon diodes and one 1 kOhm " +
        "resistor. Anodes take the inputs, the commoned cathodes form node Y, and the resistor " +
        "pulls Y down to ground. Watch the voltmeter, not just the LED: a HIGH output measures " +
        "4.30 V, not 5.00 V, because node Y can only ever climb to within one forward drop " +
        "(0.70 V) of whichever input is driving it. The logic is right and the level is already " +
        "degraded — that is the point of the experiment. Cascade a second diode stage and you lose " +
        "another 0.70 V, which is why passive diode logic cannot be stacked and why Variation C's " +
        "transistor stage exists.",
    targetChips = listOf("7432"),
    switchIndices = listOf(0, 1),
    ledIndices = listOf(0),
    inputLabels = listOf("A (SW0)", "B (SW1)"),
    outputLabels = listOf("Y (LED0)"),
    objectives = listOf(
        LabObjective("obj1", "Set the anodes facing the inputs", "D1 anode to node A, D2 anode to node B — the anode is the end that must be driven up for the diode to conduct"),
        LabObjective("obj2", "Common the two cathodes at node Y", "Tie both cathodes together; that junction is the output"),
        LabObjective("obj3", "Fit R1 (1 kOhm) from Y to ground", "Without the pull-down, node Y floats when both inputs are LOW and reads nothing"),
        LabObjective("obj4", "Verify the OR truth table on LED0", "Cycle SW0/SW1 through 00, 01, 10, 11"),
        LabObjective("obj5", "Measure node Y with the voltmeter", "Confirm 0.00 V LOW and 4.30 V HIGH, and account for the missing 0.70 V")
    ),
    // Derived from the ideal-switch model, so the truth table can never disagree with the
    // millivolts the voltmeter reports for the same vector.
    expectedFunction = { inputs ->
        listOf(DiscreteApparatus.diodeOr.evaluate(inputs[0], inputs[1]).isHigh)
    },
    buildCircuit = { circuit ->
        circuit.clearAll()
        circuit.masterPower = true
        wireSupplyRails(circuit)

        // The 7432 carries the Boolean the diode network produces; the diodes themselves cannot be
        // placed, so the wire from each input node to a gate input stands in for one diode.
        val surrogate = circuit.addChip("7432", trench = SURROGATE_TRENCH, startColumn = SURROGATE_COLUMN)
        powerSurrogate(circuit, surrogate)

        // Input A: SW0 to node A (D1's anode), node A on to gate input 1A.
        circuit.addWire(AD200Topology.TERM_SW0, node(COL_NODE_A, 0), WireColor.YELLOW)
        circuit.addWire(node(COL_NODE_A, 2), surrogate.getPinSocket(1), WireColor.YELLOW)

        // Input B: SW1 to node B (D2's anode), node B on to gate input 1B.
        circuit.addWire(AD200Topology.TERM_SW1, node(COL_NODE_B, 0), WireColor.ORANGE)
        circuit.addWire(node(COL_NODE_B, 2), surrogate.getPinSocket(2), WireColor.ORANGE)

        // Node Y: the commoned cathodes. Gate output 1Y drives it, and LED0 reads it.
        circuit.addWire(surrogate.getPinSocket(3), node(COL_NODE_Y, 0), WireColor.GREEN)
        circuit.addWire(node(COL_NODE_Y, 2), AD200Topology.TERM_LED0, WireColor.GREEN)

        // R1 as the pull-down. A resistor and not a jumper: at 1 kOhm the netlist leaves it open,
        // which is correct — a jumper here would short node Y to ground.
        circuit.addResistor(
            node(COL_NODE_Y, 4),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, COL_NODE_Y),
            DiscreteApparatus.r1.ohms.toLong()
        )

        circuit.switches[0] = false
        circuit.switches[1] = false
        circuit.step()
    }
)

/**
 * Variation B — **diode AND**.
 *
 * Topology: D1's cathode takes input A and D2's cathode takes input B; the two *anodes* are commoned
 * and that common anode is the output node Y; R2 (4 kOhm) runs from Y up to +5 V as the pull-up.
 * The diodes are reversed relative to Variation A, and so is the resistor's return, which flips the
 * function. A single LOW input forward-biases its diode — current runs from the rail, through the
 * pull-up, through the diode and into the switch — clamping Y one forward drop *above* that input.
 *
 * The both-HIGH case is worth deriving carefully, because the obvious answer is wrong. For either
 * diode to conduct, Y would have to reach 5.00 + 0.70 = 5.70 V. Nothing on the board can put it
 * there: the only thing pulling Y up is the 4 kOhm resistor, and the far end of that resistor *is*
 * the 5.00 V rail. So both diodes simply switch off, the pull-up carries no current, an unloaded
 * resistor drops no voltage, and Y rests at exactly the rail. The real node voltage is 5.00 V — the
 * 5.70 V figure is not clipped, it never occurs.
 *
 * Levels: 0.70 V / 0.70 V / 0.70 V / 5.00 V across the four input vectors. Note where this gate's
 * damage lands, mirror-image to the OR: its HIGH is perfect, but its LOW sits 0.70 V off ground with
 * only 100 mV of margin against the 0.80 V a TTL input is still guaranteed to read as LOW.
 */
private val diodeAndLab = LabExperiment(
    id = "exp01_diode_and",
    labNumber = 1,
    title = EXPERIMENT_TITLE,
    subtitle = "Variation B · Diode AND gate",
    description = "Reverse both diodes and move the resistor to the supply, and the same two parts " +
        "give you AND instead of OR. Cathodes take the inputs, the commoned anodes form node Y, and " +
        "R2 (4 kOhm) pulls Y up to +5 V. Read the voltmeter: a LOW output measures 0.70 V, not " +
        "0.00 V, because a conducting diode clamps node Y one forward drop above whichever input is " +
        "pulling it down. That leaves just 100 mV of noise margin under the 0.80 V a 74-series input " +
        "is guaranteed to still accept as LOW. Then check the HIGH case and reason it out: node Y " +
        "cannot reach 5.70 V, because the only thing pulling it up is a resistor whose far end is " +
        "the 5.00 V rail — both diodes switch off, the unloaded resistor drops nothing, and Y sits " +
        "at exactly 5.00 V. Between them the two diode gates lose margin at both ends: the OR " +
        "spoils its HIGH, the AND spoils its LOW.",
    targetChips = listOf("7408"),
    switchIndices = listOf(0, 1),
    ledIndices = listOf(0),
    inputLabels = listOf("A (SW0)", "B (SW1)"),
    outputLabels = listOf("Y (LED0)"),
    objectives = listOf(
        LabObjective("obj1", "Turn both diodes around", "D1 cathode to node A, D2 cathode to node B — the opposite of Variation A"),
        LabObjective("obj2", "Common the two anodes at node Y", "That junction is the output; it is the end the pull-up feeds"),
        LabObjective("obj3", "Fit R2 (4 kOhm) from Y to +5 V", "The pull-up both sources the diode current and defines Y when both diodes are off"),
        LabObjective("obj4", "Verify the AND truth table on LED0", "Y goes HIGH only for 11"),
        LabObjective("obj5", "Measure the LOW output", "Confirm 0.70 V, not 0.00 V, and compare it against TTL V_IL(max) = 0.80 V"),
        LabObjective("obj6", "Predict the HIGH output before measuring", "Explain why it is 5.00 V and not 5.70 V")
    ),
    expectedFunction = { inputs ->
        listOf(DiscreteApparatus.diodeAnd.evaluate(inputs[0], inputs[1]).isHigh)
    },
    buildCircuit = { circuit ->
        circuit.clearAll()
        circuit.masterPower = true
        wireSupplyRails(circuit)

        val surrogate = circuit.addChip("7408", trench = SURROGATE_TRENCH, startColumn = SURROGATE_COLUMN)
        powerSurrogate(circuit, surrogate)

        // Input A: SW0 to node A (D1's cathode this time), node A on to gate input 1A.
        circuit.addWire(AD200Topology.TERM_SW0, node(COL_NODE_A, 0), WireColor.YELLOW)
        circuit.addWire(node(COL_NODE_A, 2), surrogate.getPinSocket(1), WireColor.YELLOW)

        // Input B: SW1 to node B (D2's cathode), node B on to gate input 1B.
        circuit.addWire(AD200Topology.TERM_SW1, node(COL_NODE_B, 0), WireColor.ORANGE)
        circuit.addWire(node(COL_NODE_B, 2), surrogate.getPinSocket(2), WireColor.ORANGE)

        // Node Y: the commoned anodes.
        circuit.addWire(surrogate.getPinSocket(3), node(COL_NODE_Y, 0), WireColor.GREEN)
        circuit.addWire(node(COL_NODE_Y, 2), AD200Topology.TERM_LED0, WireColor.GREEN)

        // R2 as the pull-up, returning to the top +5 V rail. Left open by the netlist at
        // 4 kOhm, so it cannot short node Y to the supply.
        circuit.addResistor(
            node(COL_NODE_Y, 4),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, COL_NODE_Y),
            DiscreteApparatus.r2.ohms.toLong()
        )

        circuit.switches[0] = false
        circuit.switches[1] = false
        circuit.step()
    }
)

/**
 * Variation C — **RTL NOT** (resistor-transistor inverter).
 *
 * Topology: the input goes through R1 (1 kOhm) into Q1's base; R2 (4 kOhm) is the collector load,
 * from +5 V down to the collector; the emitter is tied to ground; the output node Y *is* the
 * collector.
 *
 * Input LOW: V_BE = 0 V, under the 0.70 V turn-on, so Q1 is cut off. No collector current means no
 * drop across the 4 kOhm load, so Y is pulled to the rail — **5.00 V, HIGH**.
 *
 * Input HIGH: the base clamps at V_BE(on) = 0.70 V, so the base resistor delivers
 * I_B = (5.00 - 0.70) / 1 kOhm = 4.30 mA. The collector can draw at most
 * I_C(sat) = (5.00 - 0.20) / 4 kOhm = 1.20 mA, so the forced beta needed is only 0.279 — two orders
 * of magnitude under any real device's beta. Q1 is therefore deep in saturation, not in its active
 * region, and Y collapses to **0.20 V, LOW**.
 *
 * This is the variation that pays off the other two. The inverter does not pass a level through and
 * degrade it further; it *regenerates* both levels from the rails, taking whatever the diode gates
 * mangled and handing back a full-swing 0.20 V / 5.00 V output. That regeneration is why real logic
 * families are built from transistors and why the diode gates above are a dead end.
 */
private val rtlNotLab = LabExperiment(
    id = "exp01_rtl_not",
    labNumber = 1,
    title = EXPERIMENT_TITLE,
    subtitle = "Variation C · RTL NOT gate",
    description = "One NPN transistor, a 1 kOhm base resistor and a 4 kOhm collector load make an " +
        "inverter — and, unlike the two diode gates, a stage that can actually be cascaded. Drive " +
        "the base HIGH and the 1 kOhm resistor delivers 4.30 mA of base current where saturation " +
        "needs only a forced beta of 0.28, so the transistor slams into saturation and the collector " +
        "sits at V_CE(sat) = 0.20 V. Drive it LOW and the transistor cuts off, no current flows " +
        "through the collector load, and the output is pulled to a full 5.00 V. Compare those " +
        "readings against Variations A and B on the voltmeter: the diode OR could only reach 4.30 V " +
        "and the diode AND could only fall to 0.70 V, because passive gates can only ever subtract " +
        "from the levels they are given. The transistor stage regenerates them from the rails " +
        "instead. That is the whole reason logic is built from transistors.",
    targetChips = listOf("7404"),
    switchIndices = listOf(0),
    ledIndices = listOf(0),
    inputLabels = listOf("A (SW0)"),
    outputLabels = listOf("Y (LED0)"),
    objectives = listOf(
        LabObjective("obj1", "Fit R1 (1 kOhm) as the base resistor", "In series from the input node to Q1's base — it limits base current once the junction clamps at 0.70 V"),
        LabObjective("obj2", "Fit R2 (4 kOhm) as the collector load", "From +5 V down to the collector; it sets I_C(sat) at 1.20 mA"),
        LabObjective("obj3", "Ground the emitter", "A plain jumper to the ground rail — this one really is a piece of wire"),
        LabObjective("obj4", "Take the output from the collector", "Node Y is the collector itself, not the emitter"),
        LabObjective("obj5", "Verify inversion on LED0", "SW0 LOW lights LED0; SW0 HIGH extinguishes it"),
        LabObjective("obj6", "Measure both output levels", "Confirm 0.20 V saturated and 5.00 V cut off, then compare with Variations A and B")
    ),
    expectedFunction = { inputs ->
        listOf(DiscreteApparatus.rtlNot.evaluate(inputs[0]).isHigh)
    },
    buildCircuit = { circuit ->
        circuit.clearAll()
        circuit.masterPower = true
        wireSupplyRails(circuit)

        // A 7404 inverter is the apt surrogate here: a TTL inverter's output stage is itself a
        // saturating transistor pair, so the Boolean and the level-restoring behaviour both match
        // what the discrete stage does.
        val surrogate = circuit.addChip("7404", trench = SURROGATE_TRENCH, startColumn = SURROGATE_COLUMN)
        powerSurrogate(circuit, surrogate)

        // Input: SW0 to the base-resistor input node, then on to inverter input 1A.
        circuit.addWire(AD200Topology.TERM_SW0, node(COL_NODE_INPUT, 0), WireColor.YELLOW)
        circuit.addWire(node(COL_NODE_INPUT, 2), surrogate.getPinSocket(1), WireColor.YELLOW)

        // R1 in series into the base node. The base node terminates there because Q1 itself cannot
        // be placed; the column is reserved for it so the board reads correctly.
        circuit.addResistor(
            node(COL_NODE_INPUT, 4),
            node(COL_NODE_BASE, 0),
            DiscreteApparatus.r1.ohms.toLong()
        )

        // Node Y: the collector. Inverter output 1Y drives it, LED0 reads it.
        circuit.addWire(surrogate.getPinSocket(2), node(COL_NODE_Y, 0), WireColor.GREEN)
        circuit.addWire(node(COL_NODE_Y, 2), AD200Topology.TERM_LED0, WireColor.GREEN)

        // R2 as the collector load, up to the top +5 V rail.
        circuit.addResistor(
            node(COL_NODE_Y, 4),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, COL_NODE_Y),
            DiscreteApparatus.r2.ohms.toLong()
        )

        // The emitter really is bonded to ground by a conductor, so this one is a jumper.
        circuit.addWire(
            node(COL_NODE_EMITTER, 0),
            AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, COL_NODE_EMITTER),
            WireColor.BLACK
        )

        circuit.switches[0] = false
        circuit.step()
    }
)

/**
 * Experiment 01's three variations, in the order the UI lists them.
 *
 * All three share [EXPERIMENT_TITLE]; the `subtitle` is what distinguishes them, and the `exp01_`
 * id prefix is what the UI groups on.
 */
val discreteGateLabs: List<LabExperiment> = listOf(
    diodeOrLab,
    diodeAndLab,
    rtlNotLab
)

/**
 * The analog side of the readout: what a voltmeter clipped to node Y actually measures.
 *
 * `LabExperiment` carries only Booleans, which is all the LED indicators and the truth-table
 * verifier need, so the millivolt figures live here instead of being forced into that contract.
 * Both readouts come from the same `DiscreteApparatus` model — [read] returns the node voltage and
 * its own Boolean, and each preset's `expectedFunction` is that same Boolean — so the meter and the
 * lamp cannot contradict each other.
 */
object DiscreteGateVoltmeter {

    const val LAB_DIODE_OR = "exp01_diode_or"
    const val LAB_DIODE_AND = "exp01_diode_and"
    const val LAB_RTL_NOT = "exp01_rtl_not"

    /**
     * Node Y's voltage for one input vector, or null if [labId] is not one of Experiment 01's.
     *
     * [inputs] is indexed the same way the verifier's sweep supplies it: element 0 is SW0. The
     * inverter ignores anything past the first bit.
     */
    fun read(labId: String, inputs: List<Boolean>): NodeLevel? = when (labId) {
        LAB_DIODE_OR -> DiscreteApparatus.diodeOr.evaluate(inputs[0], inputs[1])
        LAB_DIODE_AND -> DiscreteApparatus.diodeAnd.evaluate(inputs[0], inputs[1])
        LAB_RTL_NOT -> DiscreteApparatus.rtlNot.evaluate(inputs[0])
        else -> null
    }

    /**
     * The free hole in node Y's tie-point column where the meter's probe goes. Rows 0, 2 and 4 of
     * that column are taken by the incoming lead, the outgoing lead and the resistor, so row 3 is
     * left clear for exactly this — and being the same column, it is the same net.
     */
    val probeSocket: Int = node(COL_NODE_Y, 3)
}
