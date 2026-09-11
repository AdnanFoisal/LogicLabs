package com.logiclabs.feature.tools.diagram

/**
 * One derived per-output boolean line, e.g. `LED0 = ¬(SW0 · SW1) + SW2`.
 */
internal class ExpressionLine(
    val outputLabel: String,
    val expression: String,
    /** True when the net has multiple drivers; rendered as a warning. */
    val isConflict: Boolean
)

internal class DerivationResult(
    val expressions: List<ExpressionLine>,
    val notes: List<String>,
    val feedbackCount: Int
)

/**
 * Derives a nested boolean expression per observed output from the pruned
 * logic graph.
 *
 * - Simple gates recurse into their inputs with precedence-aware
 *   parenthesisation (`·` binds tighter than `⊕`, which binds tighter than `+`;
 *   negation is `¬`).
 * - The engine's TTL behaviour is mirrored: an unconnected input reads HIGH,
 *   so a floating gate input appears as the constant `1`, and +5V/GND
 *   terminals wired to inputs appear as `1`/`0`.
 * - Block units (7483/7448/7474/7476) are leaves — the expression references
 *   the block output (`U1·S1`) and the functional form is shown in the notes
 *   (`S = A + B + C0`, `Q⁺ = D on CLK↑`). Their outputs are not pure boolean
 *   functions of inputs, so expanding them would be wrong.
 * - Sequential feedback: a path that revisits a unit renders that unit as a
 *   reference (`U1A`) and the notes call out the dashed feedback edges.
 * - Nets with more than one driver render as `⚠` (bus contention).
 */
internal object BooleanExpressions {

    /** Hard cap on expression size — protects pathological shared-subgraph DAGs. */
    private const val NODE_BUDGET = 220

    // Operator precedence: OR = 1 < XOR = 2 < AND = 3 < NOT = 4 < atom = 5.
    private sealed class Expr {
        object One : Expr()
        object Zero : Expr()
        object Ellipsis : Expr()
        object Conflict : Expr()
        class Var(val name: String) : Expr()
        class Ref(val name: String) : Expr()
        class Not(val child: Expr) : Expr()
        class Binary(val symbol: String, val prec: Int, val children: List<Expr>) : Expr()
    }

    private class Ctx(val graph: LogicGraph) {
        /** Drivers per destination: "unitId:pin" or "OUT:id". */
        val driversByDst = HashMap<String, List<LogicEdge>>()
        val blockUnits = LinkedHashMap<String, UnitNode>()
        var budget = NODE_BUDGET
        var truncated = false
        var sawFeedbackRef = false

        /** Path-scoped visited set — a shared subgraph may expand once per path. */
        val pathVisited = HashSet<String>()

        init {
            for (e in graph.edges) {
                val key = if (e.toUnit != null) "${e.toUnit.id}:${e.toPin}" else "OUT:${e.toOutput!!.id}"
                driversByDst[key] = (driversByDst[key] ?: emptyList()) + e
            }
            for (u in graph.units) {
                if (u.spec is UnitSpec.Block) blockUnits[u.id] = u
            }
        }
    }

    fun derive(graph: LogicGraph): DerivationResult {
        val ctx = Ctx(graph)
        val lines = graph.outputs.map { o ->
            val expr = when {
                graph.undrivenOutputs.any { it.id == o.id } -> Expr.Zero
                else -> exprForDrivers(ctx, "OUT:${o.id}")
            }
            ExpressionLine(
                outputLabel = o.label,
                expression = render(expr, 0),
                isConflict = expr is Expr.Conflict
            )
        }

        val notes = mutableListOf<String>()
        for (u in ctx.blockUnits.values) {
            val b = (u.spec as UnitSpec.Block).spec
            notes += "${u.label} (${u.partNumber}): ${b.functionalForm}"
        }
        if (graph.floatingInputs.isNotEmpty()) {
            notes += "${graph.floatingInputs.size} unconnected TTL input(s) read HIGH (internal pull-up)"
        }
        val feedback = graph.edges.count { it.isBackEdge }
        if (feedback > 0) {
            notes += "$feedback feedback path(s) drawn dashed — sequential / oscillator loop"
        }
        if (graph.contentionNetCount > 0) {
            notes += "bus contention on ${graph.contentionNetCount} net(s): multiple drivers tied together"
        }
        for (chip in graph.unpoweredChips) {
            notes += "$chip has no +5V/GND path — outputs float"
        }
        if (graph.undrivenOutputs.isNotEmpty()) {
            notes += "${graph.undrivenOutputs.size} output(s) wired to an undriven net (read 0)"
        }
        if (graph.outputs.isEmpty() && graph.units.isNotEmpty()) {
            notes += "no LED / 7-segment outputs wired — showing intermediate logic only"
        }
        if (ctx.truncated) {
            notes += "expression size capped for display"
        }
        if (lines.isEmpty() && graph.outputs.isEmpty()) {
            notes += "no outputs wired to LEDs or 7-segment inputs"
        }

        return DerivationResult(lines, notes, feedback)
    }

    // --- expression tree construction -----------------------------------------

    private fun exprForDrivers(ctx: Ctx, dstKey: String): Expr {
        val drivers = ctx.driversByDst[dstKey] ?: return Expr.One // floating TTL input = HIGH
        return when (drivers.size) {
            0 -> Expr.One
            1 -> exprOfDriver(ctx, drivers[0])
            else -> Expr.Conflict
        }
    }

    private fun exprOfDriver(ctx: Ctx, edge: LogicEdge): Expr {
        val terminal = edge.fromTerminal
        if (edge.fromUnit == null) {
            return when (terminal?.kind) {
                SourceKind.VCC -> Expr.One
                SourceKind.GND -> Expr.Zero
                else -> Expr.Var(terminal?.label ?: "?")
            }
        }
        val unit = edge.fromUnit
        return when (val spec = unit.spec) {
            is UnitSpec.Block -> Expr.Ref("${unit.label}·${unit.pinName(edge.fromPin)}")
            is UnitSpec.Gate -> {
                if (unit.id in ctx.pathVisited) {
                    ctx.sawFeedbackRef = true
                    return Expr.Ref(unit.label)
                }
                if (ctx.budget <= 0) {
                    ctx.truncated = true
                    return Expr.Ellipsis
                }
                ctx.budget--
                ctx.pathVisited += unit.id
                val children = spec.spec.inputPins.map { pin ->
                    exprForDrivers(ctx, "${unit.id}:$pin")
                }
                ctx.pathVisited -= unit.id
                val op = spec.spec.op
                when (op) {
                    GateOp.AND -> Expr.Binary("·", 3, children)
                    GateOp.OR -> Expr.Binary("+", 1, children)
                    GateOp.XOR -> Expr.Binary("⊕", 2, children)
                    GateOp.NAND -> Expr.Not(Expr.Binary("·", 3, children))
                    GateOp.NOR -> Expr.Not(Expr.Binary("+", 1, children))
                    GateOp.XNOR -> Expr.Not(Expr.Binary("⊕", 2, children))
                    GateOp.NOT -> Expr.Not(children[0])
                }
            }
        }
    }

    // --- rendering with precedence-aware parenthesisation ----------------------

    private fun render(e: Expr, parentPrec: Int): String = when (e) {
        Expr.One -> "1"
        Expr.Zero -> "0"
        Expr.Ellipsis -> "…"
        Expr.Conflict -> "⚠"
        is Expr.Var -> e.name
        is Expr.Ref -> e.name
        is Expr.Not -> {
            val inner = render(e.child, 4)
            "¬" + if (e.child is Expr.Binary) "($inner)" else inner
        }
        is Expr.Binary -> {
            val sb = StringBuilder()
            for ((i, c) in e.children.withIndex()) {
                if (i > 0) sb.append(' ').append(e.symbol).append(' ')
                sb.append(render(c, e.prec))
            }
            if (e.prec < parentPrec) "(${sb})" else sb.toString()
        }
    }
}
