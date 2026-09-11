package com.logiclabs.feature.tools.diagram

import com.logiclabs.core.bridge.catalog.ChipModel
import com.logiclabs.core.bridge.model.PinRole

/**
 * Logic operators the diagram derivation can recognise.
 *
 * Each operator drives three independent things and they must stay consistent:
 *  - the ANSI/IEEE distinctive symbol drawn for the gate ([shape], [isInverting]),
 *  - the boolean operator used when deriving per-LED expressions,
 *  - nothing else — power pins (VCC/GND) never appear here by design.
 */
internal enum class GateOp {
    AND, OR, XOR, NOT, NAND, NOR, XNOR;

    /** Distinctive-shape family the gate is drawn with. */
    val shape: GateShape
        get() = when (this) {
            AND, NAND -> GateShape.AND
            OR, NOR -> GateShape.OR
            XOR, XNOR -> GateShape.XOR
            NOT -> GateShape.NOT
        }

    /** True when the symbol carries an output inversion bubble. */
    val isInverting: Boolean
        get() = this == NAND || this == NOR || this == XNOR || this == NOT
}

/** The four distinctive gate body shapes (ANSI/IEEE Y32.14 style). */
internal enum class GateShape { AND, OR, XOR, NOT }

/** One gate unit inside a placed SSI chip, e.g. one of a 7400's four NAND gates. */
internal data class GateSpec(val op: GateOp, val inputPins: List<Int>, val outputPin: Int)

/** One named pin on a block-rendered (MSI/sequential) unit. */
internal data class BlockPinSpec(
    val pin: Int,
    val name: String,
    /** Active-low pin: drawn with an IEEE inversion bubble outside the box edge. */
    val isInverted: Boolean = false,
    /** Clock input: drawn with an edge-trigger wedge inside the box. */
    val isClock: Boolean = false,
    /** 7476 clocks on HIGH→LOW, unlike the 7474's LOW→HIGH. Adds a bubble to the wedge. */
    val clockFalls: Boolean = false
)

/**
 * A multi-pin unit that is *not* a simple gate and is therefore rendered as an
 * IEEE-style rectangular block with labelled pins: the 7483 adder, the 7448
 * decoder, and the 7474/7476 flip-flops (one block per flip-flop).
 */
internal data class BlockSpec(
    val partNumber: String,
    val title: String,
    val inputs: List<BlockPinSpec>,
    val outputs: List<BlockPinSpec>,
    /** Sequential elements break combinational expression recursion. */
    val isSequential: Boolean,
    /** Functional legend line, e.g. "Q⁺ = D on CLK↑". Shown under the diagram. */
    val functionalForm: String
)

/** A unit is either a single gate or a block. */
internal sealed class UnitSpec {
    data class Gate(val spec: GateSpec) : UnitSpec()
    data class Block(val spec: BlockSpec) : UnitSpec()
}

/**
 * One node of the derived logic graph: a gate unit or a block unit of a placed IC.
 *
 * Chips are decomposed per their datasheet gate structure — pins are physical
 * pin numbers of the package, so the derivation matches exactly what the
 * simulation engine evaluates in `TTLChipCatalog`/`ExtendedChipModels`.
 */
internal class UnitNode(
    /** Stable id: `"<placedIc.id>#<unitIndex>"`. */
    val id: String,
    /** Bench designator by placement order: U1, U2, … */
    val chipDesignator: String,
    /** Gate ordinal within the chip: A, B, C… empty for single-block chips. */
    val unitSuffix: String,
    val partNumber: String,
    val partTitle: String,
    val spec: UnitSpec,
    /** Physical socket per pin number, 1-indexed (index 0 unused). */
    val sockets: IntArray
) {
    val label: String get() = chipDesignator + unitSuffix

    val inputPins: List<Int>
        get() = when (spec) {
            is UnitSpec.Gate -> spec.spec.inputPins
            is UnitSpec.Block -> spec.spec.inputs.map { it.pin }
        }

    val outputPins: List<Int>
        get() = when (spec) {
            is UnitSpec.Gate -> listOf(spec.spec.outputPin)
            is UnitSpec.Block -> spec.spec.outputs.map { it.pin }
        }

    val isSequential: Boolean
        get() = spec is UnitSpec.Block && spec.spec.isSequential

    /** Display name of a pin (block pin names, "Y" for gate outputs). */
    fun pinName(pin: Int): String = when (spec) {
        is UnitSpec.Gate -> if (pin == spec.spec.outputPin) "Y" else {
            val idx = spec.spec.inputPins.indexOf(pin)
            if (idx >= 0) "${'A' + idx}" else "$pin"
        }
        is UnitSpec.Block -> spec.spec.inputs.firstOrNull { it.pin == pin }?.name
            ?: spec.spec.outputs.firstOrNull { it.pin == pin }?.name
            ?: "$pin"
    }

    fun socketOf(pin: Int): Int = sockets[pin]
}

/** Kind of primary input terminal the diagram can bind to. */
internal enum class SourceKind { SWITCH, PULSER_P, PULSER_N, CLOCK, CLOCK_INV, VCC, GND }

/** A primary input node: a trainer terminal that drives a net (SW0…, CLK, +5V…). */
internal class InputNode(
    val id: String,
    val label: String,
    val socket: Int,
    val kind: SourceKind,
    val switchIndex: Int = -1,
    val pulserIndex: Int = -1
)

/** Kind of observed output terminal. */
internal enum class SinkKind { LED, SEGMENT }

/** An observed output node: an LED monitor or a 7-segment BCD input bit. */
internal class OutputNode(
    val id: String,
    val label: String,
    val socket: Int,
    val kind: SinkKind,
    val ledIndex: Int = -1
)

/**
 * One directed signal connection: driver port → consumer port, derived by
 * resolving both ports' sockets through the breadboard DSU netlist.
 */
internal class LogicEdge(
    val id: Long,
    /** Driving unit, or null when the driver is a trainer terminal. */
    val fromUnit: UnitNode?,
    /** Driver unit's physical pin (valid when [fromUnit] != null). */
    val fromPin: Int,
    /** Driving terminal (valid when [fromUnit] == null). */
    val fromTerminal: InputNode?,
    /** Consuming unit, or null when the consumer is an output terminal. */
    val toUnit: UnitNode?,
    /** Consumer unit's physical pin (valid when [toUnit] != null). */
    val toPin: Int,
    /** Consuming output terminal (valid when [toUnit] == null). */
    val toOutput: OutputNode?,
    /** True when the edge closes a cycle; drawn dashed on a below-lane. */
    var isBackEdge: Boolean = false
) {
    val srcNodeId: String get() = fromUnit?.id ?: fromTerminal!!.id
    val dstNodeId: String get() = toUnit?.id ?: toOutput!!.id
}

/** A unit input pin with no driver on its net — TTL reads these as HIGH. */
internal class FloatingInput(val unit: UnitNode, val pin: Int)

/** A unit output pin whose net connects to nothing else. */
internal class DanglingOutput(val unit: UnitNode, val pin: Int)

/**
 * The derived, pruned logic graph of the breadboard circuit.
 *
 * Inclusion rule: only *participating* structure is kept — a terminal, unit or
 * LED appears only when at least one of its ports shares a net with another
 * port. Unused gates of a partially-wired chip are dropped, which is what makes
 * this the "clean gate-level equivalent" of the physical rat's nest.
 */
internal class LogicGraph(
    val inputs: List<InputNode>,
    val units: List<UnitNode>,
    val outputs: List<OutputNode>,
    val edges: List<LogicEdge>,
    val floatingInputs: List<FloatingInput>,
    val danglingOutputs: List<DanglingOutput>,
    /** Outputs wired to a net that has no driver at all. */
    val undrivenOutputs: List<OutputNode>,
    /** Column depth per node id (inputs = 0). */
    val depth: Map<String, Int>,
    /** Column index of the single outputs column. */
    val outputColumn: Int,
    /** "U2 (7402)" for chips whose VCC/GND never reach the power terminals. */
    val unpoweredChips: List<String>,
    /** Nets carrying more than one driver (bus contention). */
    val contentionNetCount: Int,
    val netCount: Int,
    val placedChipCount: Int
)

/**
 * Datasheet decomposition of every part number the engine can place.
 *
 * Pin numbers mirror the `evaluate` implementations in
 * `core-bridge/catalog/TTLChipCatalog.kt` and `ExtendedChipModels.kt` exactly —
 * notably the non-quirk-free parts: the 7402's inverted pinout (outputs on
 * pins 1/4/10/13), the 7410/7411 straddled first gate (inputs 1,2,13 →
 * output 12), the 7420's NC pins 3/11, and the 74266's mirrored gates 2/3.
 */
internal object ChipDecomposition {

    private val quad = { op: GateOp ->
        listOf(
            GateSpec(op, listOf(1, 2), 3),
            GateSpec(op, listOf(4, 5), 6),
            GateSpec(op, listOf(9, 10), 8),
            GateSpec(op, listOf(12, 13), 11)
        )
    }

    private val gates: Map<String, List<GateSpec>> = mapOf(
        "7400" to quad(GateOp.NAND),
        "7408" to quad(GateOp.AND),
        "7432" to quad(GateOp.OR),
        "7486" to quad(GateOp.XOR),
        "7402" to listOf(
            GateSpec(GateOp.NOR, listOf(2, 3), 1),
            GateSpec(GateOp.NOR, listOf(5, 6), 4),
            GateSpec(GateOp.NOR, listOf(8, 9), 10),
            GateSpec(GateOp.NOR, listOf(11, 12), 13)
        ),
        "7404" to listOf(
            GateSpec(GateOp.NOT, listOf(1), 2),
            GateSpec(GateOp.NOT, listOf(3), 4),
            GateSpec(GateOp.NOT, listOf(5), 6),
            GateSpec(GateOp.NOT, listOf(9), 8),
            GateSpec(GateOp.NOT, listOf(11), 10),
            GateSpec(GateOp.NOT, listOf(13), 12)
        ),
        "7410" to listOf(
            GateSpec(GateOp.NAND, listOf(1, 2, 13), 12),
            GateSpec(GateOp.NAND, listOf(3, 4, 5), 6),
            GateSpec(GateOp.NAND, listOf(9, 10, 11), 8)
        ),
        "7411" to listOf(
            GateSpec(GateOp.AND, listOf(1, 2, 13), 12),
            GateSpec(GateOp.AND, listOf(3, 4, 5), 6),
            GateSpec(GateOp.AND, listOf(9, 10, 11), 8)
        ),
        "7420" to listOf(
            GateSpec(GateOp.NAND, listOf(1, 2, 4, 5), 6),
            GateSpec(GateOp.NAND, listOf(9, 10, 12, 13), 8)
        ),
        "74266" to listOf(
            GateSpec(GateOp.XNOR, listOf(1, 2), 3),
            GateSpec(GateOp.XNOR, listOf(5, 6), 4),
            GateSpec(GateOp.XNOR, listOf(8, 9), 10),
            GateSpec(GateOp.XNOR, listOf(12, 13), 11)
        )
    )

    private val blocks: Map<String, List<BlockSpec>> = mapOf(
        "7483" to listOf(
            BlockSpec(
                partNumber = "7483",
                title = "7483 · 4-BIT ADDER",
                inputs = listOf(
                    BlockPinSpec(10, "A1"), BlockPinSpec(8, "A2"),
                    BlockPinSpec(3, "A3"), BlockPinSpec(1, "A4"),
                    BlockPinSpec(11, "B1"), BlockPinSpec(7, "B2"),
                    BlockPinSpec(4, "B3"), BlockPinSpec(16, "B4"),
                    BlockPinSpec(13, "C0")
                ),
                outputs = listOf(
                    BlockPinSpec(9, "S1"), BlockPinSpec(6, "S2"),
                    BlockPinSpec(2, "S3"), BlockPinSpec(15, "S4"),
                    BlockPinSpec(14, "C4")
                ),
                isSequential = false,
                functionalForm = "S1..S4,C4 = A1..A4 + B1..B4 + C0 (4-bit binary sum)"
            )
        ),
        "7448" to listOf(
            BlockSpec(
                partNumber = "7448",
                title = "7448 · BCD→7-SEG",
                inputs = listOf(
                    BlockPinSpec(7, "A"), BlockPinSpec(1, "B"),
                    BlockPinSpec(2, "C"), BlockPinSpec(6, "D"),
                    BlockPinSpec(3, "~LT", isInverted = true),
                    BlockPinSpec(5, "~RBI", isInverted = true),
                    BlockPinSpec(4, "~BI", isInverted = true)
                ),
                outputs = listOf(
                    BlockPinSpec(13, "a"), BlockPinSpec(12, "b"),
                    BlockPinSpec(11, "c"), BlockPinSpec(10, "d"),
                    BlockPinSpec(9, "e"), BlockPinSpec(15, "f"),
                    BlockPinSpec(14, "g")
                ),
                isSequential = false,
                functionalForm = "a..g = 7-segment decode of (D C B A)"
            )
        ),
        "7474" to listOf(
            BlockSpec(
                partNumber = "7474",
                title = "7474 · D-FF",
                inputs = listOf(
                    BlockPinSpec(1, "~CLR", isInverted = true),
                    BlockPinSpec(2, "D"),
                    BlockPinSpec(3, "CLK", isClock = true),
                    BlockPinSpec(4, "~PRE", isInverted = true)
                ),
                outputs = listOf(
                    BlockPinSpec(5, "Q"),
                    BlockPinSpec(6, "~Q", isInverted = true)
                ),
                isSequential = true,
                functionalForm = "Q⁺ = D on CLK↑ (async ~PRE / ~CLR)"
            ),
            BlockSpec(
                partNumber = "7474",
                title = "7474 · D-FF",
                inputs = listOf(
                    BlockPinSpec(13, "~CLR", isInverted = true),
                    BlockPinSpec(12, "D"),
                    BlockPinSpec(11, "CLK", isClock = true),
                    BlockPinSpec(10, "~PRE", isInverted = true)
                ),
                outputs = listOf(
                    BlockPinSpec(9, "Q"),
                    BlockPinSpec(8, "~Q", isInverted = true)
                ),
                isSequential = true,
                functionalForm = "Q⁺ = D on CLK↑ (async ~PRE / ~CLR)"
            )
        ),
        "7476" to listOf(
            BlockSpec(
                partNumber = "7476",
                title = "7476 · J-K FF",
                inputs = listOf(
                    BlockPinSpec(1, "~CLK", isClock = true, clockFalls = true),
                    BlockPinSpec(2, "~PRE", isInverted = true),
                    BlockPinSpec(3, "~CLR", isInverted = true),
                    BlockPinSpec(4, "J"),
                    BlockPinSpec(16, "K")
                ),
                outputs = listOf(
                    BlockPinSpec(15, "Q"),
                    BlockPinSpec(14, "~Q", isInverted = true)
                ),
                isSequential = true,
                functionalForm = "Q⁺ = J·Q̄ + K̄·Q on CLK↓ (async ~PRE / ~CLR)"
            ),
            BlockSpec(
                partNumber = "7476",
                title = "7476 · J-K FF",
                inputs = listOf(
                    BlockPinSpec(6, "~CLK", isClock = true, clockFalls = true),
                    BlockPinSpec(7, "~PRE", isInverted = true),
                    BlockPinSpec(8, "~CLR", isInverted = true),
                    BlockPinSpec(9, "J"),
                    BlockPinSpec(12, "K")
                ),
                outputs = listOf(
                    BlockPinSpec(11, "Q"),
                    BlockPinSpec(10, "~Q", isInverted = true)
                ),
                isSequential = true,
                functionalForm = "Q⁺ = J·Q̄ + K̄·Q on CLK↓ (async ~PRE / ~CLR)"
            )
        )
    )

    /** Simple-gate decomposition, or null when the part renders as block(s). */
    fun gatesFor(partNumber: String): List<GateSpec>? = gates[partNumber]

    /** Block decomposition (may yield several units, e.g. dual flip-flops). */
    fun blocksFor(partNumber: String): List<BlockSpec>? = blocks[partNumber]

    /**
     * Safety net for part numbers added to the engine after this table: a
     * generic block built straight from the model's pin roles, so a new chip
     * still appears as a labelled IEEE box instead of vanishing.
     */
    fun fallbackBlock(model: ChipModel): BlockSpec {
        val inputs = model.pins.values
            .filter { it.role == PinRole.INPUT }
            .sortedBy { it.pinNumber }
            .map { BlockPinSpec(it.pinNumber, it.name, it.isInverted, it.isClock) }
        val outputs = model.pins.values
            .filter { it.role == PinRole.OUTPUT }
            .sortedBy { it.pinNumber }
            .map { BlockPinSpec(it.pinNumber, it.name, it.isInverted) }
        return BlockSpec(
            partNumber = model.partNumber,
            title = model.partNumber,
            inputs = inputs,
            outputs = outputs,
            isSequential = false,
            functionalForm = "custom part — no functional form recorded"
        )
    }
}
