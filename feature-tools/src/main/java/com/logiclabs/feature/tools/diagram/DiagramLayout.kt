package com.logiclabs.feature.tools.diagram

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import java.util.TreeMap

/**
 * 12-color high-contrast neon palette for collision-free net coloring.
 */
internal val NeonPalette: List<Color> = listOf(
    Color(0xFF00F0FF), // Electric Cyan
    Color(0xFFFF007F), // Neon Magenta
    Color(0xFF39FF14), // Electric Lime
    Color(0xFFFFB300), // Neon Amber
    Color(0xFFBF00FF), // Electric Violet
    Color(0xFFFF5F00), // Safety Orange
    Color(0xFF0066FF), // Electric Blue
    Color(0xFFE5FF00), // Acid Yellow
    Color(0xFF00F5D4), // Neon Turquoise
    Color(0xFFFF3366), // Neon Coral
    Color(0xFFB388FF), // Bright Lavender
    Color(0xFF00ACC1)  // Deep Cyan
)

/**
 * Geometry of one input/output terminal tag (pill).
 */
internal enum class TagRole { INPUT, OUTPUT, CONSTANT_HIGH, CONSTANT_LOW }

internal class TagVisual(
    val nodeId: String,
    /** Center coordinates, world dp. */
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val label: String,
    val role: TagRole,
    val socket: Int,
    val source: InputNode?,
    val sink: OutputNode?
)

/**
 * One ANSI/IEEE distinctive-shape gate, with its body path pre-built in world
 * coordinates so the render loop never builds geometry.
 */
internal class GateVisual(
    val nodeId: String,
    val label: String,
    val bodyLeft: Float,
    val bodyTop: Float,
    val bodyW: Float,
    val h: Float,
    val shape: GateShape,
    val isInverting: Boolean,
    val outX: Float,
    val outY: Float,
    val path: Path,
    /** Stroke-only decoration (the XOR input arc). */
    val decor: Path?
)

internal class BlockPinVisual(
    val name: String,
    val y: Float,
    val isInverted: Boolean,
    val isClock: Boolean,
    val clockFalls: Boolean
)

/** One IEEE-style rectangular block (adder, decoder, flip-flop). */
internal class BlockVisual(
    val nodeId: String,
    val label: String,
    val title: String,
    val left: Float,
    val top: Float,
    val w: Float,
    val h: Float,
    val inPins: List<BlockPinVisual>,
    val outPins: List<BlockPinVisual>,
    /** Clock wedge triangles, stroke+fill. */
    val wedges: Path,
    /** Inversion bubbles hanging off the box edges. */
    val bubbles: Path
)

/**
 * One orthogonal net polyline. [pts] is a flat x0,y0,x1,y1… array in world dp.
 * [levelSocket] is the source-side socket whose net level colours the wire live.
 * [neonColor] is the Welsh-Powell assigned collision-free neon net color.
 * [path] contains the pre-built orthogonal polyline with semicircular bridge hop arcs.
 */
internal class EdgeVisual(
    val pts: FloatArray,
    val isBackEdge: Boolean,
    val levelSocket: Int,
    val neonColor: Color = Color.Unspecified,
    val path: Path = Path()
)

/** Fan-out junction dot (one driver feeding two or more consumers). */
internal class JunctionVisual(
    val x: Float,
    val y: Float,
    val levelSocket: Int,
    val neonColor: Color = Color.Unspecified
)

/**
 * An unconnected pin marker: a short stub ending in an open circle.
 * `showOne` adds the "1" hint — the engine reads floating TTL inputs as HIGH.
 */
internal class StubVisual(val x: Float, val y: Float, val isInput: Boolean, val showOne: Boolean)

internal class DiagramStats(
    val chipCount: Int,
    val gateCount: Int,
    val blockCount: Int,
    val netCount: Int,
    val inputCount: Int,
    val outputCount: Int
)

/**
 * Fully positioned, path-prebuilt diagram ready for a zero-allocation draw.
 * All coordinates are in world dp; the renderer applies pan/zoom.
 */
internal class LayoutResult(
    val worldW: Float,
    val worldH: Float,
    val inputTags: List<TagVisual>,
    val outputTags: List<TagVisual>,
    val gates: List<GateVisual>,
    val blocks: List<BlockVisual>,
    val edges: List<EdgeVisual>,
    val junctions: List<JunctionVisual>,
    val stubs: List<StubVisual>,
    val expressions: List<ExpressionLine>,
    val notes: List<String>,
    val stats: DiagramStats,
    val isEmpty: Boolean
)

/**
 * Left-to-right level layout with barycenter crossing reduction and
 * orthogonal (H-V-H) net routing.
 *
 * - Column 0 holds input terminal tags, columns 1…N hold gate/block units by
 *   longest-path depth, and a single outputs column holds the LED/7-seg tags.
 * - Within-column order is refined by 4 barycenter sweeps (forward + backward)
 *   over non-feedback adjacency, which keeps the common lab circuits
 *   crossing-free; wires that must cross still read clearly because nodes are
 *   drawn over the nets.
 * - Feedback edges are routed on dedicated lanes below the diagram, dashed.
 */
internal object DiagramLayout {

    // --- world geometry, in dp --------------------------------------------------
    const val PAD_X = 30f
    const val PAD_TOP = 36f
    const val PAD_BOTTOM = 26f
    const val COL_GAP = 60f
    const val ROW_GAP = 36f
    const val STAGGER_PITCH = 8.5f
    const val HOP_RADIUS = 3.5f

    val NeonPalette: List<Color> get() = com.logiclabs.feature.tools.diagram.NeonPalette

    internal data class VertSeg(val netKey: String, val x: Float, val minY: Float, val maxY: Float)

    internal data class NetEdgeInfo(
        val netKey: String,
        val fromUnitId: String? = null,
        val toUnitId: String? = null,
        val toOutputId: String? = null
    )

    internal fun detectHorizontalCrossings(
        x1: Float,
        x2: Float,
        hy: Float,
        netKey: String,
        vertSegs: List<VertSeg>,
        junctions: List<JunctionVisual> = emptyList()
    ): List<Float> {
        val minX = minOf(x1, x2)
        val maxX = maxOf(x1, x2)
        val crossings = mutableListOf<Float>()

        for (vs in vertSegs) {
            if (vs.netKey != netKey &&
                vs.x >= minX + HOP_RADIUS + 0.5f &&
                vs.x <= maxX - HOP_RADIUS - 0.5f &&
                hy >= vs.minY + 1.0f &&
                hy <= vs.maxY - 1.0f
            ) {
                val hasJunction = junctions.any {
                    kotlin.math.abs(it.x - vs.x) < 1.0f && kotlin.math.abs(it.y - hy) < 1.0f
                }
                if (!hasJunction) {
                    crossings += vs.x
                }
            }
        }

        if (crossings.isEmpty()) return emptyList()

        val sortedCrossings = if (x1 < x2) crossings.sorted() else crossings.sortedDescending()
        val filteredCrossings = mutableListOf<Float>()
        for (cx in sortedCrossings) {
            if (filteredCrossings.isEmpty() ||
                kotlin.math.abs(cx - filteredCrossings.last()) >= HOP_RADIUS * 2f
            ) {
                filteredCrossings += cx
            }
        }
        return filteredCrossings
    }

    internal fun computeWelshPowellColors(
        unitIds: List<String>,
        outputIds: List<String>,
        edges: List<NetEdgeInfo>,
        crossings: List<Pair<String, String>> = emptyList()
    ): Map<String, Color> {
        val allNets = edges.map { it.netKey }.distinct()
        val conflictGraph = HashMap<String, MutableSet<String>>()
        for (net in allNets) conflictGraph[net] = mutableSetOf()

        // Unit sharing conflicts
        for (uId in unitIds) {
            val inNets = edges.filter { it.toUnitId == uId }.map { it.netKey }.distinct()
            val outNets = edges.filter { it.fromUnitId == uId }.map { it.netKey }.distinct()

            // inputs != outputs: output pins of gates strictly receive distinct neon colors from input pins
            for (inN in inNets) {
                for (outN in outNets) {
                    if (inN != outN) {
                        conflictGraph[inN]?.add(outN)
                        conflictGraph[outN]?.add(inN)
                    }
                }
            }

            // inputs != inputs
            for (i in 0 until inNets.size) {
                for (j in i + 1 until inNets.size) {
                    val n1 = inNets[i]
                    val n2 = inNets[j]
                    conflictGraph[n1]?.add(n2)
                    conflictGraph[n2]?.add(n1)
                }
            }

            // outputs != outputs
            for (i in 0 until outNets.size) {
                for (j in i + 1 until outNets.size) {
                    val n1 = outNets[i]
                    val n2 = outNets[j]
                    conflictGraph[n1]?.add(n2)
                    conflictGraph[n2]?.add(n1)
                }
            }
        }

        // Output tag conflicts
        for (oId in outputIds) {
            val outTagNets = edges.filter { it.toOutputId == oId }.map { it.netKey }.distinct()
            for (i in 0 until outTagNets.size) {
                for (j in i + 1 until outTagNets.size) {
                    val n1 = outTagNets[i]
                    val n2 = outTagNets[j]
                    conflictGraph[n1]?.add(n2)
                    conflictGraph[n2]?.add(n1)
                }
            }
        }

        // Crossing nets conflicts
        for ((net1, net2) in crossings) {
            if (net1 != net2) {
                conflictGraph[net1]?.add(net2)
                conflictGraph[net2]?.add(net1)
            }
        }

        // Welsh-Powell greedy coloring
        val sortedNets = allNets.sortedWith(
            compareByDescending<String> { conflictGraph[it]?.size ?: 0 }
                .thenBy { it }
        )
        val netColors = HashMap<String, Color>()
        val coloredNets = HashSet<String>()
        var colorIndex = 0

        for (seed in sortedNets) {
            if (seed in coloredNets) continue
            val assignedColor = NeonPalette[colorIndex % NeonPalette.size]
            colorIndex++
            netColors[seed] = assignedColor
            coloredNets.add(seed)
            val currentGroup = mutableListOf(seed)

            for (candidate in sortedNets) {
                if (candidate in coloredNets) continue
                val conflicts = conflictGraph[candidate] ?: emptySet()
                if (currentGroup.none { it in conflicts }) {
                    netColors[candidate] = assignedColor
                    coloredNets.add(candidate)
                    currentGroup.add(candidate)
                }
            }
        }
        return netColors
    }

    private const val TAG_H = 24f
    private const val TAG_MIN_W = 56f
    private const val TAG_TEXT_PAD = 34f

    private const val NOT_H = 24f
    private const val NOT_W = 30f
    private const val BUBBLE_R = 3.5f
    private const val XOR_GAP = 5f
    /** Reserved space left of a gate body for floating-input markers. */
    private const val FLOAT_ZONE = 16f
    /** Space above a gate body for the U-designator label. */
    private const val LABEL_SPACE = 13f

    private const val BLOCK_W = 104f
    private const val BLOCK_PIN_PITCH = 15f
    private const val BLOCK_HEADER = 22f
    private const val BLOCK_PAD_V = 8f

    private const val BACK_LANE_START = 18f
    private const val BACK_LANE_PITCH = 11f

    fun build(graph: LogicGraph): LayoutResult = Builder(graph).build()

    private class Builder(private val graph: LogicGraph) {

        private val columns = TreeMap<Int, MutableList<Any>>()
        private val outAnchors = HashMap<String, FloatArray>()
        private val inAnchors = HashMap<String, FloatArray>()

        private val inputTags = mutableListOf<TagVisual>()
        private val outputTags = mutableListOf<TagVisual>()
        private val gates = mutableListOf<GateVisual>()
        private val blocks = mutableListOf<BlockVisual>()
        private val stubs = mutableListOf<StubVisual>()

        fun build(): LayoutResult {
            // --- 1. Bucket nodes into depth columns ------------------------------
            for (s in graph.inputs) column(0).add(s)
            for (u in graph.units) column(graph.depth[u.id] ?: 1).add(u)
            for (o in graph.outputs) column(graph.outputColumn).add(o)

            val depthKeys = columns.keys.toIntArray()
            val colLists = depthKeys.map { columns[it]!!.toMutableList() }.toMutableList()
            val nCols = colLists.size

            // --- 2. Barycenter ordering ------------------------------------------
            val preds = HashMap<String, MutableList<String>>()
            val succs = HashMap<String, MutableList<String>>()
            for (e in graph.edges) {
                if (e.isBackEdge) continue
                preds.getOrPut(e.dstNodeId) { mutableListOf() }.add(e.srcNodeId)
                succs.getOrPut(e.srcNodeId) { mutableListOf() }.add(e.dstNodeId)
            }
            val nodeCy = HashMap<String, Float>()
            fun recomputeCy() {
                for (list in colLists) {
                    list.forEachIndexed { i, n -> nodeCy[nodeId(n)] = i * 70f }
                }
            }
            recomputeCy()

            fun sortByBary(list: MutableList<Any>, neighbors: (String) -> List<String>): MutableList<Any> {
                val keys = HashMap<String, Float>(list.size)
                list.forEachIndexed { i, n ->
                    val id = nodeId(n)
                    val nb = neighbors(id)
                    keys[id] = if (nb.isEmpty()) {
                        nodeCy[id] ?: i * 70f
                    } else {
                        var sum = 0f
                        for (x in nb) sum += nodeCy[x] ?: 0f
                        sum / nb.size
                    }
                }
                return list.sortedBy { keys[nodeId(it)]!! }.toMutableList()
            }

            if (nCols > 1) {
                repeat(2) {
                    for (c in 1 until nCols) colLists[c] = sortByBary(colLists[c]) { preds[it] ?: emptyList() }
                    for (c in nCols - 2 downTo 0) colLists[c] = sortByBary(colLists[c]) { succs[it] ?: emptyList() }
                    recomputeCy()
                }
            }

            // --- 3. Column x positions -------------------------------------------
            val colLeft = FloatArray(nCols)
            val colWidth = FloatArray(nCols)
            var xCursor = PAD_X
            for (c in 0 until nCols) {
                var w = 0f
                for (node in colLists[c]) w = maxOf(w, widthOf(node))
                colWidth[c] = w
                colLeft[c] = xCursor
                xCursor += w + COL_GAP
            }
            val worldW = xCursor - COL_GAP + PAD_X

            // --- 4. Row y positions ----------------------------------------------
            val nodeTop = HashMap<String, Float>()
            var contentBottom = PAD_TOP
            for (c in 0 until nCols) {
                var slotH = 0f
                for (node in colLists[c]) slotH = maxOf(slotH, heightOf(node))
                slotH += ROW_GAP
                var y = PAD_TOP
                for (node in colLists[c]) {
                    val h = heightOf(node)
                    nodeTop[nodeId(node)] = y + (slotH - ROW_GAP - h) / 2f
                    y += slotH
                }
                contentBottom = maxOf(contentBottom, PAD_TOP + slotH * colLists[c].size)
            }

            // --- 5. Node visuals + port anchors -----------------------------------
            for (c in 0 until nCols) {
                val left = colLeft[c]
                val w = colWidth[c]
                for (node in colLists[c]) {
                    val top = nodeTop[nodeId(node)]!!
                    when (node) {
                        is InputNode -> {
                            val tw = tagWidth(node.label)
                            // cx is the tag CENTRE (the renderer draws tl = cx − w/2).
                            // It previously carried the column-relative LEFT edge, which
                            // shifted every pill half a width left and detached its wire
                            // anchor from the drawn pill.
                            val cx = left + w / 2f
                            val cy = top + TAG_H / 2f
                            val role = when (node.kind) {
                                SourceKind.VCC -> TagRole.CONSTANT_HIGH
                                SourceKind.GND -> TagRole.CONSTANT_LOW
                                else -> TagRole.INPUT
                            }
                            inputTags += TagVisual(node.id, cx, cy, tw, TAG_H, node.label, role, node.socket, node, null)
                            outAnchors[node.id] = floatArrayOf(cx + tw / 2f, cy)
                        }
                        is OutputNode -> {
                            val tw = tagWidth(node.label)
                            val cx = left + w / 2f
                            val cy = top + TAG_H / 2f
                            outputTags += TagVisual(node.id, cx, cy, tw, TAG_H, node.label, TagRole.OUTPUT, node.socket, null, node)
                            inAnchors[node.id] = floatArrayOf(cx - tw / 2f, cy)
                        }
                        is UnitNode -> when (val spec = node.spec) {
                            is UnitSpec.Gate -> buildGate(node, spec.spec, left, w, top)
                            is UnitSpec.Block -> buildBlock(node, spec.spec, left, w, top)
                        }
                    }
                }
            }

            // --- 6. Net routing ----------------------------------------------------
            val fanOut = HashMap<String, Int>()
            for (e in graph.edges) {
                val k = srcKey(e)
                fanOut[k] = (fanOut[k] ?: 0) + 1
            }
            val emitted = HashMap<String, Int>()
            val backEdges = graph.edges.filter { it.isBackEdge }
            val edgeVisuals = mutableListOf<EdgeVisual>()
            val junctions = mutableListOf<JunctionVisual>()

            class RawEdge(
                val edge: LogicEdge,
                val pts: FloatArray,
                val isBackEdge: Boolean,
                val levelSocket: Int,
                val netKey: String
            )
            val rawEdges = mutableListOf<RawEdge>()

            for (e in graph.edges) {
                if (e.isBackEdge) continue
                val s = outAnchors[srcKey(e)] ?: continue
                val d = inAnchors[dstKey(e)] ?: continue
                val key = srcKey(e)
                val fan = fanOut[key] ?: 1
                val ord = emitted[key] ?: 0
                emitted[key] = ord + 1
                val stagger = if (fan > 1) (ord - (fan - 1) / 2f) * STAGGER_PITCH else 0f
                val mx = (s[0] + d[0]) / 2f + stagger
                val pts = if (kotlin.math.abs(s[1] - d[1]) < 0.6f) {
                    floatArrayOf(s[0], s[1], d[0], d[1])
                } else {
                    floatArrayOf(s[0], s[1], mx, s[1], mx, d[1], d[0], d[1])
                }
                rawEdges += RawEdge(e, pts, isBackEdge = false, levelSocket = srcSocket(e), netKey = key)
            }

            backEdges.forEachIndexed { i, e ->
                val s = outAnchors[srcKey(e)] ?: return@forEachIndexed
                val d = inAnchors[dstKey(e)] ?: return@forEachIndexed
                val laneY = contentBottom + BACK_LANE_START + i * BACK_LANE_PITCH
                val pts = floatArrayOf(
                    s[0], s[1],
                    s[0] + 9f, s[1],
                    s[0] + 9f, laneY,
                    d[0] - 13f, laneY,
                    d[0] - 13f, d[1],
                    d[0], d[1]
                )
                rawEdges += RawEdge(e, pts, isBackEdge = true, levelSocket = srcSocket(e), netKey = srcKey(e))
            }

            // Collect all vertical segments from all edges for 2D crossing detection
            val vertSegs = mutableListOf<VertSeg>()
            for (re in rawEdges) {
                val pts = re.pts
                for (k in 0 until pts.size - 2 step 2) {
                    val x1 = pts[k]
                    val y1 = pts[k + 1]
                    val x2 = pts[k + 2]
                    val y2 = pts[k + 3]
                    if (kotlin.math.abs(x1 - x2) < 0.5f && kotlin.math.abs(y1 - y2) > 1.0f) {
                        vertSegs += VertSeg(re.netKey, x1, minOf(y1, y2), maxOf(y1, y2))
                    }
                }
            }

            // Find crossing pairs
            val crossingPairs = mutableListOf<Pair<String, String>>()
            for (re in rawEdges) {
                val pts = re.pts
                for (k in 0 until pts.size - 2 step 2) {
                    val x1 = pts[k]
                    val y1 = pts[k + 1]
                    val x2 = pts[k + 2]
                    val y2 = pts[k + 3]
                    if (kotlin.math.abs(y1 - y2) < 0.5f && kotlin.math.abs(x1 - x2) > 1.0f) {
                        val hy = y1
                        val minX = minOf(x1, x2)
                        val maxX = maxOf(x1, x2)
                        for (vs in vertSegs) {
                            if (vs.netKey != re.netKey &&
                                vs.x >= minX + HOP_RADIUS + 0.5f &&
                                vs.x <= maxX - HOP_RADIUS - 0.5f &&
                                hy >= vs.minY + 1.0f &&
                                hy <= vs.maxY - 1.0f
                            ) {
                                crossingPairs += (re.netKey to vs.netKey)
                            }
                        }
                    }
                }
            }

            // Welsh-Powell greedy coloring
            val edgeInfos = rawEdges.map {
                NetEdgeInfo(
                    netKey = it.netKey,
                    fromUnitId = it.edge.fromUnit?.id,
                    toUnitId = it.edge.toUnit?.id,
                    toOutputId = it.edge.toOutput?.id
                )
            }
            val netColors = computeWelshPowellColors(
                unitIds = graph.units.map { it.id },
                outputIds = graph.outputs.map { it.id },
                edges = edgeInfos,
                crossings = crossingPairs
            )

            // Fan-out junction dots with net color
            val emittedJunctions = HashSet<String>()
            for (re in rawEdges) {
                if (re.isBackEdge) continue
                val key = re.netKey
                val fan = fanOut[key] ?: 1
                if (fan > 1 && key !in emittedJunctions) {
                    emittedJunctions.add(key)
                    val s = outAnchors[key]
                    if (s != null) {
                        val neon = netColors[key] ?: NeonPalette[0]
                        junctions += JunctionVisual(s[0], s[1], re.levelSocket, neonColor = neon)
                    }
                }
            }

            // Build EdgeVisuals with semicircular bridge hop arcs on horizontal crossings
            for (re in rawEdges) {
                val pts = re.pts
                val path = Path()
                path.moveTo(pts[0], pts[1])

                for (k in 0 until pts.size - 2 step 2) {
                    val x1 = pts[k]
                    val y1 = pts[k + 1]
                    val x2 = pts[k + 2]
                    val y2 = pts[k + 3]

                    if (kotlin.math.abs(y1 - y2) < 0.5f && kotlin.math.abs(x1 - x2) > 1.0f) {
                        // Horizontal segment
                        val hy = y1
                        val crossings = detectHorizontalCrossings(x1, x2, hy, re.netKey, vertSegs, junctions)

                        if (crossings.isEmpty()) {
                            path.lineTo(x2, y2)
                        } else {
                            if (x1 < x2) {
                                for (cx in crossings) {
                                    path.lineTo(cx - HOP_RADIUS, hy)
                                    path.arcTo(
                                        Rect(cx - HOP_RADIUS, hy - HOP_RADIUS, cx + HOP_RADIUS, hy + HOP_RADIUS),
                                        180f,
                                        -180f,
                                        false
                                    )
                                }
                                path.lineTo(x2, hy)
                            } else {
                                for (cx in crossings) {
                                    path.lineTo(cx + HOP_RADIUS, hy)
                                    path.arcTo(
                                        Rect(cx - HOP_RADIUS, hy - HOP_RADIUS, cx + HOP_RADIUS, hy + HOP_RADIUS),
                                        0f,
                                        180f,
                                        false
                                    )
                                }
                                path.lineTo(x2, hy)
                            }
                        }
                    } else {
                        // Vertical segment
                        path.lineTo(x2, y2)
                    }
                }

                val neonColor = netColors[re.netKey] ?: NeonPalette[0]
                edgeVisuals += EdgeVisual(
                    pts = pts,
                    isBackEdge = re.isBackEdge,
                    levelSocket = re.levelSocket,
                    neonColor = neonColor,
                    path = path
                )
            }

            // --- 7. Floating / dangling stubs ---------------------------------------
            for (f in graph.floatingInputs) {
                val a = inAnchors["${f.unit.id}:${f.pin}"] ?: continue
                stubs += StubVisual(a[0], a[1], isInput = true, showOne = true)
            }
            for (d in graph.danglingOutputs) {
                val a = outAnchors["${d.unit.id}:${d.pin}"] ?: continue
                stubs += StubVisual(a[0], a[1], isInput = false, showOne = false)
            }
            for (o in graph.undrivenOutputs) {
                val a = inAnchors[o.id] ?: continue
                stubs += StubVisual(a[0], a[1], isInput = true, showOne = false)
            }

            // --- 8. Expressions, notes, stats, bounds -------------------------------
            val derivation = BooleanExpressions.derive(graph)
            val worldH = contentBottom + PAD_BOTTOM +
                (if (backEdges.isEmpty()) 0f else BACK_LANE_START + backEdges.size * BACK_LANE_PITCH)

            val chipCount = graph.units.map { it.chipDesignator }.toHashSet().size
            val stats = DiagramStats(
                chipCount = chipCount,
                gateCount = graph.units.count { it.spec is UnitSpec.Gate },
                blockCount = graph.units.count { it.spec is UnitSpec.Block },
                netCount = graph.netCount,
                inputCount = graph.inputs.size,
                outputCount = graph.outputs.size
            )

            return LayoutResult(
                worldW = worldW,
                worldH = worldH,
                inputTags = inputTags,
                outputTags = outputTags,
                gates = gates,
                blocks = blocks,
                edges = edgeVisuals,
                junctions = junctions,
                stubs = stubs,
                expressions = derivation.expressions,
                notes = derivation.notes,
                stats = stats,
                isEmpty = graph.units.isEmpty() && graph.outputs.isEmpty()
            )
        }

        // --- helpers ---------------------------------------------------------------

        private fun column(depth: Int): MutableList<Any> =
            columns.getOrPut(depth) { mutableListOf() }

        private fun nodeId(n: Any): String = when (n) {
            is InputNode -> n.id
            is UnitNode -> n.id
            is OutputNode -> n.id
            else -> throw IllegalArgumentException("unknown node $n")
        }

        private fun tagWidth(label: String): Float {
            val textWidth = try {
                DiagramPaints.tagLabelPaint.measureText(label)
            } catch (_: Throwable) {
                label.length * 6f
            }
            return maxOf(TAG_MIN_W, textWidth + TAG_TEXT_PAD)
        }

        private fun widthOf(node: Any): Float = when (node) {
            is InputNode -> tagWidth(node.label)
            is OutputNode -> tagWidth(node.label)
            is UnitNode -> when (val spec = node.spec) {
                is UnitSpec.Gate -> {
                    val n = spec.spec.inputPins.size
                    val h = gateHeight(n)
                    val bodyW = gateBodyWidth(spec.spec.op.shape, h)
                    FLOAT_ZONE + (if (spec.spec.op.shape == GateShape.XOR) XOR_GAP else 0f) +
                        bodyW + (if (spec.spec.op.isInverting) 2 * BUBBLE_R else 0f) + 4f
                }
                is UnitSpec.Block -> BLOCK_W + 10f
            }
            else -> 0f
        }

        private fun heightOf(node: Any): Float = when (node) {
            is InputNode, is OutputNode -> TAG_H
            is UnitNode -> when (val spec = node.spec) {
                is UnitSpec.Gate -> gateHeight(spec.spec.inputPins.size) + LABEL_SPACE
                is UnitSpec.Block -> blockHeight(spec.spec)
            }
            else -> 0f
        }

        private fun gateHeight(n: Int): Float = if (n <= 1) NOT_H else n * 12f + 14f

        private fun gateBodyWidth(shape: GateShape, h: Float): Float = when (shape) {
            GateShape.NOT -> NOT_W
            GateShape.AND -> maxOf(42f, h * 1.12f)
            else -> maxOf(42f, h * 0.92f)
        }

        private fun blockHeight(b: BlockSpec): Float =
            BLOCK_HEADER + maxOf(b.inputs.size, b.outputs.size) * BLOCK_PIN_PITCH + BLOCK_PAD_V

        private fun buildGate(u: UnitNode, g: GateSpec, colLeft: Float, colW: Float, top: Float) {
            val n = g.inputPins.size
            val h = gateHeight(n)
            val bodyW = gateBodyWidth(g.op.shape, h)
            val xorPad = if (g.op.shape == GateShape.XOR) XOR_GAP else 0f
            val totalW = FLOAT_ZONE + xorPad + bodyW + (if (g.op.isInverting) 2 * BUBBLE_R else 0f)
            val bodyLeft = colLeft + (colW - totalW) / 2f + FLOAT_ZONE + xorPad
            val bodyTop = top + LABEL_SPACE
            val cy = bodyTop + h / 2f

            // Input pins spread evenly across the body height.
            for (i in 0 until n) {
                val pinY = bodyTop + h * (i + 1) / (n + 1)
                inAnchors["${u.id}:${g.inputPins[i]}"] = floatArrayOf(bodyLeft - xorPad, pinY)
            }
            val bodyRight = bodyLeft + bodyW
            val outX = bodyRight + if (g.op.isInverting) 2 * BUBBLE_R else 0f
            outAnchors["${u.id}:${g.outputPin}"] = floatArrayOf(outX, cy)

            val path = buildGatePath(g.op.shape, bodyLeft, bodyTop, bodyW, h, cy)
            if (g.op.isInverting) {
                path.addBubble(bodyRight + BUBBLE_R, cy)
            }
            val decor = if (g.op.shape == GateShape.XOR) buildXorDecorArc(bodyLeft, bodyTop, bodyW, h) else null
            gates += GateVisual(
                nodeId = u.id,
                label = u.label,
                bodyLeft = bodyLeft,
                bodyTop = bodyTop,
                bodyW = bodyW,
                h = h,
                shape = g.op.shape,
                isInverting = g.op.isInverting,
                outX = outX,
                outY = cy,
                path = path,
                decor = decor
            )
        }

        private fun buildGatePath(shape: GateShape, bl: Float, bt: Float, bw: Float, h: Float, cy: Float): Path {
            val p = Path()
            when (shape) {
                GateShape.AND -> {
                    val r = h / 2f
                    val flat = bw - r
                    p.moveTo(bl, bt)
                    p.lineTo(bl + flat, bt)
                    p.arcTo(Rect(bl + bw - 2 * r, bt, bl + bw, bt + h), -90f, 180f, false)
                    p.lineTo(bl, bt + h)
                    p.close()
                }
                GateShape.OR, GateShape.XOR -> {
                    // Same body; XOR adds a second input-side arc as a decor path.
                    p.moveTo(bl, bt)
                    p.cubicTo(bl + bw * 0.52f, bt, bl + bw * 0.96f, bt + h * 0.16f, bl + bw, cy)
                    p.cubicTo(bl + bw * 0.96f, bt + h * 0.84f, bl + bw * 0.52f, bt + h, bl, bt + h)
                    p.quadraticBezierTo(bl + bw * 0.34f, cy, bl, bt)
                    p.close()
                }
                GateShape.NOT -> {
                    p.moveTo(bl, bt)
                    p.lineTo(bl + bw, cy)
                    p.lineTo(bl, bt + h)
                    p.close()
                }
            }
            return p
        }

        /** Inversion bubble: appended to the gate body path so it fills with it. */
        private fun Path.addBubble(cx: Float, cy: Float) {
            addOval(Rect(cx - BUBBLE_R, cy - BUBBLE_R, cx + BUBBLE_R, cy + BUBBLE_R))
        }

        private fun buildXorDecorArc(bl: Float, bt: Float, bw: Float, h: Float): Path {
            val p = Path()
            val cy = bt + h / 2f
            p.moveTo(bl - XOR_GAP, bt)
            p.quadraticBezierTo(bl - XOR_GAP + bw * 0.34f, cy, bl - XOR_GAP, bt + h)
            return p
        }

        private fun buildBlock(u: UnitNode, b: BlockSpec, colLeft: Float, colW: Float, top: Float) {
            val w = BLOCK_W
            val h = blockHeight(b)
            val left = colLeft + (colW - w) / 2f
            val right = left + w
            val bodyTop = top + BLOCK_HEADER
            val bodyH = h - BLOCK_HEADER

            val wedges = Path()
            val bubbles = Path()

            val inPins = b.inputs.mapIndexed { i, pin ->
                val y = bodyTop + bodyH * (i + 1) / (b.inputs.size + 1)
                if (pin.isClock) {
                    wedges.moveTo(left + 3f, y - 4.5f)
                    wedges.lineTo(left + 3f, y + 4.5f)
                    wedges.lineTo(left + 11f, y)
                    wedges.close()
                }
                if (pin.isInverted) bubbles.addBubble(left - BUBBLE_R, y)
                inAnchors["${u.id}:${pin.pin}"] =
                    floatArrayOf(left - if (pin.isInverted) 2 * BUBBLE_R else 0f, y)
                BlockPinVisual(pin.name, y, pin.isInverted, pin.isClock, pin.clockFalls)
            }
            val outPins = b.outputs.mapIndexed { i, pin ->
                val y = bodyTop + bodyH * (i + 1) / (b.outputs.size + 1)
                if (pin.isInverted) bubbles.addBubble(right + BUBBLE_R, y)
                outAnchors["${u.id}:${pin.pin}"] =
                    floatArrayOf(right + if (pin.isInverted) 2 * BUBBLE_R else 0f, y)
                BlockPinVisual(pin.name, y, pin.isInverted, pin.isClock, pin.clockFalls)
            }

            blocks += BlockVisual(
                nodeId = u.id,
                label = u.label,
                title = b.title,
                left = left,
                top = top,
                w = w,
                h = h,
                inPins = inPins,
                outPins = outPins,
                wedges = wedges,
                bubbles = bubbles
            )
        }

        private fun srcKey(e: LogicEdge): String =
            if (e.fromUnit != null) "${e.fromUnit.id}:${e.fromPin}" else e.fromTerminal!!.id

        private fun dstKey(e: LogicEdge): String =
            if (e.toUnit != null) "${e.toUnit.id}:${e.toPin}" else e.toOutput!!.id

        private fun srcSocket(e: LogicEdge): Int =
            if (e.fromUnit != null) e.fromUnit.socketOf(e.fromPin) else e.fromTerminal!!.socket
    }
}
