package com.logiclabs.feature.tools.diagram

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.topology.AD200Topology

/**
 * Derives the clean gate-level [LogicGraph] from the physical breadboard.
 *
 * ## How the derivation works
 *
 * 1. **Unit decomposition** — every placed IC is split into its datasheet gate
 *    units (a 7400 becomes four NAND nodes) or block units (7483 adder, 7448
 *    decoder, one block per 7474/7476 flip-flop).
 * 2. **Net census** — every signal pin of every unit is mapped through
 *    `BreadboardCircuit.getPinSocket` to its physical socket and then through
 *    the circuit's DSU netlist (`dsu.find`) to its electrical net. Because the
 *    DSU pre-unions each breadboard column, *sockets in the same column are the
 *    same net* — a wire touching any of a column's five tie-points connects to
 *    everything else on that column, exactly like the real board. Jumper wires
 *    and sub-100Ω resistors merge nets via `rebuildNetlist`.
 * 3. **Inclusion pruning** — a terminal, unit or LED is kept only when one of
 *    its ports shares a net with another port. Unused gates of a partially
 *    wired chip drop out; power pins (VCC/GND roles) never enter the signal
 *    graph. The +5V/GND *terminals* do enter, but only when wired to a signal
 *    pin — that is how a TTL input pulled to a rail renders as the constant 1/0.
 * 4. **Edge construction** — a unit input pin consumes every driver found on
 *    its net (a unit output or a driving terminal). LEDs and 7-segment BCD
 *    inputs are sinks; a sink with no driver is flagged as undriven.
 * 5. **Level assignment** — depth = 1 + max(driver depths) via a DFS with
 *    on-stack (gray) marking; an edge to a gray node is a feedback edge, is
 *    excluded from depth, and is drawn dashed on a below-lane. Sequential
 *    blocks (7474/7476) participate normally, so a flip-flop's Q feeding the
 *    logic that drives its own D reads as one feedback edge, not an infinite
 *    expression.
 *
 * ## Limits
 *  - Bus contention (two totem-pole outputs on one net) is *shown* (red, ⚠ in
 *    the expression) rather than resolved.
 *  - Combinational loops (ring oscillators) are broken at an arbitrary edge;
 *    every inverter still shows, with the loop dashed.
 *  - Sub-100Ω resistors are jumpers (the engine merges them); larger passives
 *    are inert in the engine and ignored here.
 *  - The trainer's own 7-segment decoders are sinks only; ~LT/~RBI/~BI display
 *    control terminals are not modelled as diagram nodes.
 */
internal object LogicGraphBuilder {

    /** Net census accumulator. */
    private class NetInfo {
        val unitIns = mutableListOf<Pair<UnitNode, Int>>()
        val unitOuts = mutableListOf<Pair<UnitNode, Int>>()
        val terminals = mutableListOf<InputNode>()
        val sinks = mutableListOf<OutputNode>()

        /** Ports that are not this net's own… all of them count for inclusion. */
        val endpointCount: Int
            get() = unitIns.size + unitOuts.size + terminals.size + sinks.size
    }

    fun build(circuit: BreadboardCircuit): LogicGraph {
        val dsu = circuit.dsu
        val chips = circuit.placedChips.toList()
        val nets = HashMap<Int, NetInfo>()

        fun netOf(socket: Int): Int = dsu.find(socket)
        fun infoOf(socket: Int): NetInfo = nets.getOrPut(netOf(socket)) { NetInfo() }

        // --- 1. Decompose placed ICs into units ---------------------------------
        val units = mutableListOf<UnitNode>()
        chips.forEachIndexed { chipIdx, chip ->
            val designator = "U${chipIdx + 1}"
            val part = chip.placedIc.partNumber
            val sockets = IntArray(chip.model.pinCount + 1) { i ->
                if (i == 0) -1 else chip.getPinSocket(i)
            }
            val gateSpecs = ChipDecomposition.gatesFor(part)
            if (gateSpecs != null) {
                gateSpecs.forEachIndexed { gi, g ->
                    units += UnitNode(
                        id = "${chip.placedIc.id}#$gi",
                        chipDesignator = designator,
                        unitSuffix = "${'A' + gi}",
                        partNumber = part,
                        partTitle = chip.model.description,
                        spec = UnitSpec.Gate(g),
                        sockets = sockets
                    )
                }
            } else {
                val blockSpecs = ChipDecomposition.blocksFor(part)
                    ?: listOf(ChipDecomposition.fallbackBlock(chip.model))
                blockSpecs.forEachIndexed { bi, b ->
                    units += UnitNode(
                        id = "${chip.placedIc.id}#B$bi",
                        chipDesignator = designator,
                        unitSuffix = if (blockSpecs.size > 1) "${'A' + bi}" else "",
                        partNumber = part,
                        partTitle = chip.model.description,
                        spec = UnitSpec.Block(b),
                        sockets = sockets
                    )
                }
            }
        }

        // --- 2. Register every signal pin on its net ----------------------------
        for (u in units) {
            for (p in u.inputPins) infoOf(u.socketOf(p)).unitIns += u to p
            for (p in u.outputPins) infoOf(u.socketOf(p)).unitOuts += u to p
        }

        // Candidate driving terminals. Included later only when actually wired
        // into signal logic, so an unwired SW5 or a +5V rail feeding only chip
        // power pins never shows in the diagram.
        val candidateSources = mutableListOf<InputNode>()
        fun addSource(label: String, socket: Int, kind: SourceKind, swIdx: Int = -1, pulserIdx: Int = -1) {
            candidateSources += InputNode("IN:$label", label, socket, kind, swIdx, pulserIdx)
        }
        for (i in 0..7) addSource("SW$i", AD200Topology.TERM_SW0 + i, SourceKind.SWITCH, swIdx = i)
        addSource("CLK", AD200Topology.TERM_CLK, SourceKind.CLOCK)
        addSource("~CLK", AD200Topology.TERM_CLK_INV, SourceKind.CLOCK_INV)
        addSource("PLS-A", AD200Topology.TERM_PULSER_A_P, SourceKind.PULSER_P, pulserIdx = 0)
        addSource("~PLS-A", AD200Topology.TERM_PULSER_A_N, SourceKind.PULSER_N, pulserIdx = 0)
        addSource("PLS-B", AD200Topology.TERM_PULSER_B_P, SourceKind.PULSER_P, pulserIdx = 1)
        addSource("~PLS-B", AD200Topology.TERM_PULSER_B_N, SourceKind.PULSER_N, pulserIdx = 1)
        addSource("+5V", AD200Topology.TERM_POWER_VCC, SourceKind.VCC)
        addSource("GND", AD200Topology.TERM_POWER_GND, SourceKind.GND)
        for (s in candidateSources) infoOf(s.socket).terminals += s

        // Candidate sinks: LED monitors L0..L7 and both 7-segment BCD nibbles.
        val candidateSinks = mutableListOf<OutputNode>()
        for (i in 0..7) {
            candidateSinks += OutputNode("OUT:LED$i", "LED$i", AD200Topology.TERM_LED0 + i, SinkKind.LED, i)
        }
        val segLabels = listOf("A", "B", "C", "D")
        for (i in 0..3) {
            candidateSinks += OutputNode("OUT:SA$i", "SEG-A${segLabels[i]}", AD200Topology.TERM_SEG_A_BCD_A + i, SinkKind.SEGMENT)
            candidateSinks += OutputNode("OUT:SB$i", "SEG-B${segLabels[i]}", AD200Topology.TERM_SEG_B_BCD_A + i, SinkKind.SEGMENT)
        }
        for (s in candidateSinks) infoOf(s.socket).sinks += s

        // --- 3. Inclusion pruning ----------------------------------------------
        val includedUnits = units.filter { u ->
            (u.inputPins.asSequence() + u.outputPins.asSequence())
                .any { p -> infoOf(u.socketOf(p)).endpointCount >= 2 }
        }.toList()
        val includedUnitIds = includedUnits.map { it.id }.toHashSet()

        fun isSignalNet(info: NetInfo): Boolean =
            (info.unitIns.isNotEmpty() || info.unitOuts.isNotEmpty() || info.sinks.isNotEmpty()) &&
                info.endpointCount >= 2

        // A terminal joins the diagram only when its net also carries a signal
        // pin or a sink — i.e. it is wired into logic, not just sitting there.
        val includedInputs = candidateSources.filter { s -> isSignalNet(infoOf(s.socket)) }.toList()
        val includedInputIds = includedInputs.map { it.id }.toHashSet()

        val includedOutputs = candidateSinks.filter { o ->
            val info = infoOf(o.socket)
            (info.unitIns.isNotEmpty() || info.unitOuts.isNotEmpty() || info.terminals.isNotEmpty())
        }.toList()

        // --- 4. Edges, floating inputs, contention ------------------------------
        // A driver is either a unit output pin or a driving trainer terminal.
        class Driver(val unit: UnitNode?, val pin: Int, val terminal: InputNode?)

        fun driversOf(info: NetInfo): List<Driver> {
            val drivers = mutableListOf<Driver>()
            for ((du, dp) in info.unitOuts) if (du.id in includedUnitIds) drivers += Driver(du, dp, null)
            for (t in info.terminals) if (t.id in includedInputIds) drivers += Driver(null, -1, t)
            return drivers
        }

        val edges = mutableListOf<LogicEdge>()
        val floatingInputs = mutableListOf<FloatingInput>()
        val undrivenOutputs = mutableListOf<OutputNode>()
        var edgeId = 0L

        for (u in includedUnits) {
            for (p in u.inputPins) {
                val drivers = driversOf(infoOf(u.socketOf(p)))
                if (drivers.isEmpty()) {
                    floatingInputs += FloatingInput(u, p)
                    continue
                }
                for (d in drivers) {
                    edges += LogicEdge(edgeId++, d.unit, d.pin, d.terminal, u, p, null)
                }
            }
        }
        for (o in includedOutputs) {
            val drivers = driversOf(infoOf(o.socket))
            if (drivers.isEmpty()) {
                undrivenOutputs += o
                continue
            }
            for (d in drivers) {
                edges += LogicEdge(edgeId++, d.unit, d.pin, d.terminal, null, -1, o)
            }
        }

        // Dangling unit outputs: a net that contains nothing but the pin itself.
        val danglingOutputs = mutableListOf<DanglingOutput>()
        val edgeSourceKeys = edges.map { it.fromUnit?.id + ":" + it.fromPin }.toHashSet()
        for (u in includedUnits) {
            for (p in u.outputPins) {
                if (infoOf(u.socketOf(p)).endpointCount <= 1 &&
                    "${u.id}:$p" !in edgeSourceKeys
                ) {
                    danglingOutputs += DanglingOutput(u, p)
                }
            }
        }

        // Contention: signal nets carrying more than one driver.
        var contentionNets = 0
        for (info in nets.values) {
            val driverCount = info.unitOuts.count { it.first.id in includedUnitIds } +
                info.terminals.count { it.id in includedInputIds }
            if (driverCount > 1 && info.endpointCount >= 2) contentionNets++
        }

        // Signal net count: nets that carry at least one included driver and
        // one included consumer.
        var netCount = 0
        for (info in nets.values) {
            val hasDriver = info.unitOuts.any { it.first.id in includedUnitIds } ||
                info.terminals.any { it.id in includedInputIds }
            val hasConsumer = info.unitIns.any { it.first.id in includedUnitIds } || info.sinks.isNotEmpty()
            if (hasDriver && hasConsumer) netCount++
        }

        // --- 5. Level assignment with cycle breaking -----------------------------
        val depth = HashMap<String, Int>()
        val dfsState = HashMap<String, Int>() // 0 = white, 1 = gray, 2 = black
        val backEdgeKeys = HashSet<String>()

        fun depthOfUnit(u: UnitNode): Int {
            when (dfsState[u.id]) {
                2 -> return depth[u.id] ?: 1
                1 -> return -1
            }
            dfsState[u.id] = 1
            var d = 0
            for (p in u.inputPins) {
                val info = infoOf(u.socketOf(p))
                for ((du, _) in info.unitOuts) {
                    if (du.id !in includedUnitIds) continue
                    val dd = depthOfUnit(du)
                    if (dd < 0) {
                        backEdgeKeys += "${du.id}>${u.id}:$p"
                        continue
                    }
                    if (dd > d) d = dd
                }
                // Terminal drivers contribute depth 0.
            }
            dfsState[u.id] = 2
            val result = d + 1
            depth[u.id] = result
            return result
        }
        for (u in includedUnits) depthOfUnit(u)
        for (s in includedInputs) depth[s.id] = 0

        var maxUnitDepth = 0
        for (u in includedUnits) maxUnitDepth = maxOf(maxUnitDepth, depth[u.id] ?: 1)
        val outputColumn = maxUnitDepth + 1
        for (o in includedOutputs) depth[o.id] = outputColumn

        // Tag DFS-detected cycles onto the edge objects; also catch any
        // unit→unit edge that would regress columns, as a safety net.
        for (e in edges) {
            val fu = e.fromUnit
            val tu = e.toUnit
            if (fu == null || tu == null) continue
            val key = "${fu.id}>${tu.id}:${e.toPin}"
            e.isBackEdge = key in backEdgeKeys || (depth[tu.id] ?: 0) <= (depth[fu.id] ?: 0)
        }

        // --- 6. Power-wiring diagnostics ----------------------------------------
        val unpoweredChips = mutableListOf<String>()
        val vccRoot = dsu.find(AD200Topology.TERM_POWER_VCC)
        val gndRoot = dsu.find(AD200Topology.TERM_POWER_GND)
        chips.forEachIndexed { chipIdx, chip ->
            val designator = "U${chipIdx + 1}"
            val hasIncludedUnits = includedUnits.any { it.chipDesignator == designator }
            if (!hasIncludedUnits) return@forEachIndexed
            val vccOk = dsu.find(chip.getPinSocket(chip.model.vccPin)) == vccRoot
            val gndOk = dsu.find(chip.getPinSocket(chip.model.gndPin)) == gndRoot
            if (!vccOk || !gndOk) {
                unpoweredChips += "$designator (${chip.placedIc.partNumber})"
            }
        }

        return LogicGraph(
            inputs = includedInputs,
            units = includedUnits,
            outputs = includedOutputs,
            edges = edges,
            floatingInputs = floatingInputs,
            danglingOutputs = danglingOutputs,
            undrivenOutputs = undrivenOutputs,
            depth = depth,
            outputColumn = outputColumn,
            unpoweredChips = unpoweredChips,
            contentionNetCount = contentionNets,
            netCount = netCount,
            placedChipCount = chips.size
        )
    }
}
