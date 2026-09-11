package com.logiclabs.feature.breadboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.JumperWire
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.feature.breadboard.physics.BezierWireGeometry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Board-space geometry for the AD-200 faceplate.
 *
 * Socket *ids* are owned by [AD200Topology] and are never re-derived here — electrical behaviour
 * and all lab presets depend on them. This class only decides where each id is *drawn*, and every
 * hit tester reads the same functions so the visual and tappable boards can never disagree.
 *
 * [Offset] is a Compose value class (a packed `Long`), so returning one costs no heap allocation.
 */
class BreadboardGeometryMapper(
    val socketPitch: Float = 24f, // Spacing between sockets in board units
    val originX: Float = 150f,
    val originY: Float = 150f
) {
    val blockGap: Float = socketPitch * 2.5f // DIP center trench
    val sectionGap: Float = socketPitch * 3.5f // Gap between top and bottom halves

    // Bottom Y coordinate of Block 3
    val bottomBlock3Y: Float
        get() = originY + 15 * socketPitch + blockGap * 2 + sectionGap + 4 * socketPitch

    /**
     * Distribution-rail pitch as a fraction of [socketPitch].
     *
     * 88 rail sockets have to share the width of a 64-column terminal block. The previous
     * `63f/87f` (0.7241) compression aligned rail holes with *nothing*; three quarters of a pitch
     * lands every fourth rail hole exactly on a terminal column line, which is what reads as a
     * real bus strip. Socket ids are untouched — only the x they are painted at changes.
     */
    val railPitch: Float get() = socketPitch * RAIL_PITCH_RATIO

    /** X of the last rail socket — the bus line and the holes have to end together. */
    val railSpanEnd: Float get() = originX + (AD200Topology.SOCKETS_PER_RAIL - 1) * railPitch

    /** X of the last terminal column. */
    val terminalSpanEnd: Float get() = originX + (AD200Topology.COLUMNS_PER_BLOCK - 1) * socketPitch

    // --- Horizontal chassis chain -------------------------------------------------------------

    /** Left edge of the DC power panel plate — the left-most thing the renderer paints. */
    val powerPanelLeft: Float get() = originX - POWER_PANEL_INSET

    /** Left edge of the phenolic slab. */
    val bodyLeft: Float get() = originX - PHENOLIC_LIP

    /**
     * Right edge of the phenolic slab and of the two instrument plates.
     *
     * `max` rather than plain [railSpanEnd] because which strip reaches further right is a
     * function of [RAIL_PITCH_RATIO]: at 0.75 the 88 rail holes overshoot the 64 terminal
     * columns by 54 units, while the old `63f/87f` compression ended level with them. Taking
     * the wider of the two means the slab cannot be resized out from under either strip if the
     * ratio is ever retuned.
     */
    val bodyRight: Float get() = max(railSpanEnd, terminalSpanEnd) + PHENOLIC_LIP

    // Chassis edges, exposed as scalars rather than only as a [Rect] so the per-frame readers in
    // [BoardCache] can compare them without allocating one.
    val boardLeft: Float get() = powerPanelLeft - BEZEL_MARGIN
    val boardRight: Float get() = bodyRight + BEZEL_MARGIN
    val boardTop: Float get() = originY - CHASSIS_TOP_MARGIN
    val boardBottom: Float get() = bottomBlock3Y + CHASSIS_BOTTOM_MARGIN

    val topVccY: Float get() = originY - 45f
    val topGndY: Float get() = originY - 25f
    val botVccY: Float get() = bottomBlock3Y + 22f
    val botGndY: Float get() = bottomBlock3Y + 42f

    /** Y of the first row of each terminal block, indexed by block 0..3. */
    fun blockTopY(block: Int): Float = when (block) {
        0 -> originY
        1 -> originY + 5 * socketPitch + blockGap
        2 -> originY + 10 * socketPitch + blockGap + sectionGap
        else -> originY + 15 * socketPitch + blockGap * 2 + sectionGap
    }

    companion object {
        const val RAIL_PITCH_RATIO = 0.75f

        /** Rail holes are drawn in groups of this many, matching a real bus strip. */
        const val RAIL_GROUP = 4

        // --- Horizontal chassis margins -------------------------------------------------------
        // Every horizontal edge of the faceplate is derived from where the outermost hole
        // actually is, plus one of the margins below. The right edge used to be
        // `COLUMNS_PER_BLOCK * socketPitch + 240f + 110f` instead — a padded constant that had
        // drifted 148 units past the last rail hole, so the board carried a strip of blank plate
        // down its right-hand side; and because `fitScale = viewportWidth / boardWidth()`, it
        // also rendered the useful board ~8% smaller than the viewport allowed.

        /**
         * Clearance between the chassis edge and the outermost thing painted on it.
         *
         * The left edge has always sat exactly this far outside the DC power panel plate. Naming
         * it once is what keeps the two sides symmetric instead of one being a leftover.
         */
        const val BEZEL_MARGIN = 12f

        /**
         * How far the phenolic slab overhangs the outermost hole *centre*. Comfortably clears a
         * tie-point's recess ring ([TiePointPainter.RECESS_RADIUS] = 6) and the row-letter
         * legend that sits 13 units outside the last column.
         */
        const val PHENOLIC_LIP = 22f

        /** Inset of the DC power panel plate from [originX]; it fixes the board's left extent. */
        const val POWER_PANEL_INSET = 128f

        /** Chassis margin above the first terminal row and below the last, for the two panels. */
        const val CHASSIS_TOP_MARGIN = 105f
        const val CHASSIS_BOTTOM_MARGIN = 180f

        val PERIPHERAL_SOCKETS = intArrayOf(
            AD200Topology.TERM_SW0, AD200Topology.TERM_SW1, AD200Topology.TERM_SW2, AD200Topology.TERM_SW3,
            AD200Topology.TERM_SW4, AD200Topology.TERM_SW5, AD200Topology.TERM_SW6, AD200Topology.TERM_SW7,
            AD200Topology.TERM_LED0, AD200Topology.TERM_LED1, AD200Topology.TERM_LED2, AD200Topology.TERM_LED3,
            AD200Topology.TERM_LED4, AD200Topology.TERM_LED5, AD200Topology.TERM_LED6, AD200Topology.TERM_LED7,
            AD200Topology.TERM_POWER_VCC, AD200Topology.TERM_POWER_GND,
            AD200Topology.TERM_CLK, AD200Topology.TERM_CLK_INV,
            AD200Topology.TERM_PULSER_A_P, AD200Topology.TERM_PULSER_A_N,
            AD200Topology.TERM_PULSER_B_P, AD200Topology.TERM_PULSER_B_N,
            AD200Topology.TERM_SEG_A_BCD_A, AD200Topology.TERM_SEG_A_BCD_B,
            AD200Topology.TERM_SEG_A_BCD_C, AD200Topology.TERM_SEG_A_BCD_D,
            AD200Topology.TERM_SEG_B_BCD_A, AD200Topology.TERM_SEG_B_BCD_B,
            AD200Topology.TERM_SEG_B_BCD_C, AD200Topology.TERM_SEG_B_BCD_D
        )

        /** Terminal + 4 active power rails: the id range the dense grid lookup covers. */
        val ACTIVE_GRID_LIMIT =
            AD200Topology.TOTAL_TERMINAL_SOCKETS + 4 * AD200Topology.SOCKETS_PER_RAIL

        /** Allocation-free id arithmetic — [AD200Topology.terminalSocket] costs three `require`s. */
        fun terminalId(block: Int, col: Int, row: Int): Int =
            block * AD200Topology.SOCKETS_PER_BLOCK + col * AD200Topology.ROWS_PER_BLOCK + row

        fun railId(rail: Int, pos: Int): Int =
            AD200Topology.TOTAL_TERMINAL_SOCKETS + rail * AD200Topology.SOCKETS_PER_RAIL + pos
    }

    fun getSocketPosition(socketId: Int): Offset {
        if (socketId < AD200Topology.TOTAL_TERMINAL_SOCKETS) {
            val block = socketId / AD200Topology.SOCKETS_PER_BLOCK
            val rem = socketId % AD200Topology.SOCKETS_PER_BLOCK
            val col = rem / AD200Topology.ROWS_PER_BLOCK
            val row = rem % AD200Topology.ROWS_PER_BLOCK
            return Offset(originX + col * socketPitch, blockTopY(block) + row * socketPitch)
        } else if (socketId < AD200Topology.TOTAL_BREADBOARD_SOCKETS) {
            val railOffset = socketId - AD200Topology.TOTAL_TERMINAL_SOCKETS
            val rail = railOffset / AD200Topology.SOCKETS_PER_RAIL
            val pos = railOffset % AD200Topology.SOCKETS_PER_RAIL

            val x = originX + pos * railPitch
            val y = when (rail) {
                AD200Topology.RAIL_TOP_VCC_5V -> topVccY
                AD200Topology.RAIL_TOP_GND -> topGndY
                AD200Topology.RAIL_BOT_VCC_5V -> botVccY
                AD200Topology.RAIL_BOT_GND -> botGndY
                else -> topVccY - rail * 20f
            }
            return Offset(x, y)
        } else if (socketId in AD200Topology.TERM_SW0..AD200Topology.TERM_SW7) {
            // Bottom space: Input switch sockets SW7..SW0 aligned with dock switches (MSB Left, LSB Right).
            val swIdx = socketId - AD200Topology.TERM_SW0
            return Offset(originX + 110f + (7 - swIdx) * 185f, bottomBlock3Y + 92f)
        } else if (socketId in AD200Topology.TERM_LED0..AD200Topology.TERM_LED7) {
            // Top space: Output sockets L7..L0 aligned with dock LEDs (MSB Left, LSB Right).
            // -68, not -60: at -60 the LED panel sockets (bezel ring out to 12.5 units)
            // overlapped the top VCC rail's tie-point recess rings by ~3.5 units at
            // every LED column. -68 clears the rail below and the panel title above.
            val ledIdx = socketId - AD200Topology.TERM_LED0
            return Offset(originX + 110f + (7 - ledIdx) * 185f, originY - 68f)
        } else if (socketId in AD200Topology.TERM_BOT_LED0..AD200Topology.TERM_BOT_LED7) {
            val ledIdx = socketId - AD200Topology.TERM_BOT_LED0
            return Offset(originX + 110f + (7 - ledIdx) * 185f, bottomBlock3Y + 100f)
        } else if (socketId == AD200Topology.TERM_POWER_VCC) {
            return Offset(originX - 75f, originY + 35f)
        } else if (socketId == AD200Topology.TERM_POWER_GND) {
            return Offset(originX - 75f, originY + 130f)
        } else if (socketId == AD200Topology.TERM_CLK) {
            return Offset(originX - 98f, originY + 225f)
        } else if (socketId == AD200Topology.TERM_CLK_INV) {
            return Offset(originX - 52f, originY + 225f)
        } else if (socketId == AD200Topology.TERM_PULSER_A_P) {
            return Offset(originX - 98f, originY + 325f)
        } else if (socketId == AD200Topology.TERM_PULSER_A_N) {
            return Offset(originX - 52f, originY + 325f)
        } else if (socketId == AD200Topology.TERM_PULSER_B_P) {
            return Offset(originX - 98f, originY + 375f)
        } else if (socketId == AD200Topology.TERM_PULSER_B_N) {
            return Offset(originX - 52f, originY + 375f)
        } else if (socketId == AD200Topology.TERM_SEG_A_BCD_A) {
            return Offset(originX - 98f, originY + 465f)
        } else if (socketId == AD200Topology.TERM_SEG_A_BCD_B) {
            return Offset(originX - 98f, originY + 505f)
        } else if (socketId == AD200Topology.TERM_SEG_A_BCD_C) {
            return Offset(originX - 98f, originY + 545f)
        } else if (socketId == AD200Topology.TERM_SEG_A_BCD_D) {
            return Offset(originX - 98f, originY + 585f)
        } else if (socketId == AD200Topology.TERM_SEG_B_BCD_A) {
            return Offset(originX - 52f, originY + 465f)
        } else if (socketId == AD200Topology.TERM_SEG_B_BCD_B) {
            return Offset(originX - 52f, originY + 505f)
        } else if (socketId == AD200Topology.TERM_SEG_B_BCD_C) {
            return Offset(originX - 52f, originY + 545f)
        } else if (socketId == AD200Topology.TERM_SEG_B_BCD_D) {
            return Offset(originX - 52f, originY + 585f)
        } else {
            // Other peripheral terminals
            val termIdx = socketId - AD200Topology.TERM_SW0
            return Offset(originX - 80f, originY + termIdx * (socketPitch * 1.5f))
        }
    }

    /**
     * Full board extent in board coordinates, used for fit-to-viewport and viewport culling.
     *
     * Both horizontal edges are content-derived (see [boardLeft] / [boardRight]), so the chassis
     * the renderer paints, the rect the [BoardCache] picture is sized to and the rect the culler
     * treats as "the whole board" are the same rectangle by construction.
     */
    fun boardBounds(): Rect = Rect(boardLeft, boardTop, boardRight, boardBottom)

    fun boardWidth(): Float = boardRight - boardLeft

    fun boardHeight(): Float = boardBottom - boardTop

    /**
     * Returns all interconnected sockets in the same physical tie-point group:
     * - For terminal blocks: all 5 sockets in the column (Row A..E or F..J)
     * - For power rails: all sockets in that rail (Rail 0..3)
     */
    fun getConnectedColumnSockets(socketId: Int): List<Int> {
        if (socketId < AD200Topology.TOTAL_TERMINAL_SOCKETS) {
            val block = socketId / AD200Topology.SOCKETS_PER_BLOCK
            val rem = socketId % AD200Topology.SOCKETS_PER_BLOCK
            val col = rem / AD200Topology.ROWS_PER_BLOCK
            return (0 until AD200Topology.ROWS_PER_BLOCK).map { r -> terminalId(block, col, r) }
        } else if (socketId < ACTIVE_GRID_LIMIT) {
            val railOffset = socketId - AD200Topology.TOTAL_TERMINAL_SOCKETS
            val rail = railOffset / AD200Topology.SOCKETS_PER_RAIL
            return (0 until AD200Topology.SOCKETS_PER_RAIL).map { p -> railId(rail, p) }
        }
        return listOf(socketId)
    }

    /**
     * Finds the closest socket to [point] within [maxRadius], both in **board** coordinates.
     *
     * Callers that work in screen space must divide the radius by the current zoom scale;
     * [com.logiclabs.feature.breadboard.interaction.SocketHitTester] does that for you and should
     * be preferred. This scans a coarse column-bucketed candidate set (~15 sockets) rather than
     * all 1,632 dense sockets.
     */
    fun findClosestSocket(point: Offset, maxRadius: Float = 14f): Int? {
        var closestId = -1
        var closestDistSq = maxRadius * maxRadius

        // --- 1. Terminal blocks: bucket by column, then by block and row ---------------------
        val colGuess = ((point.x - originX) / socketPitch).roundToInt()
        val colLo = max(0, colGuess - 1)
        val colHi = min(AD200Topology.COLUMNS_PER_BLOCK - 1, colGuess + 1)
        if (colLo <= colHi) {
            var block = 0
            while (block < AD200Topology.TERMINAL_BLOCK_COUNT) {
                val top = blockTopY(block)
                // Skip whole blocks whose row band cannot be within maxRadius of the point.
                if (point.y >= top - maxRadius - socketPitch &&
                    point.y <= top + 4 * socketPitch + maxRadius + socketPitch
                ) {
                    val rowGuess = ((point.y - top) / socketPitch).roundToInt()
                    val rowLo = max(0, rowGuess - 1)
                    val rowHi = min(AD200Topology.ROWS_PER_BLOCK - 1, rowGuess + 1)
                    var col = colLo
                    while (col <= colHi) {
                        val cx = originX + col * socketPitch
                        val dx = cx - point.x
                        var row = rowLo
                        while (row <= rowHi) {
                            val dy = (top + row * socketPitch) - point.y
                            val d = dx * dx + dy * dy
                            if (d < closestDistSq) {
                                closestDistSq = d
                                closestId = terminalId(block, col, row)
                            }
                            row++
                        }
                        col++
                    }
                }
                block++
            }
        }

        // --- 2. The four active power rails: bucket by rail pitch ---------------------------
        val posGuess = ((point.x - originX) / railPitch).roundToInt()
        val posLo = max(0, posGuess - 1)
        val posHi = min(AD200Topology.SOCKETS_PER_RAIL - 1, posGuess + 1)
        if (posLo <= posHi) {
            var rail = 0
            while (rail < 4) {
                val ry = railY(rail)
                val dy = ry - point.y
                if (dy * dy < closestDistSq) {
                    var pos = posLo
                    while (pos <= posHi) {
                        val dx = (originX + pos * railPitch) - point.x
                        val d = dx * dx + dy * dy
                        if (d < closestDistSq) {
                            closestDistSq = d
                            closestId = railId(rail, pos)
                        }
                        pos++
                    }
                }
                rail++
            }
        }

        // --- 3. Peripheral terminals (26 total, generous snap radius) ------------------------
        val periRadius = max(maxRadius, 28f)
        val periRadiusSq = periRadius * periRadius
        var periBest = Float.MAX_VALUE
        var periId = -1
        for (sockId in PERIPHERAL_SOCKETS) {
            val pos = getSocketPosition(sockId)
            val dx = pos.x - point.x
            val dy = pos.y - point.y
            val distSq = dx * dx + dy * dy
            if (distSq < periRadiusSq && distSq < periBest) {
                periBest = distSq
                periId = sockId
            }
        }
        if (periId >= 0 && (closestId < 0 || periBest < closestDistSq)) return periId

        return if (closestId >= 0) closestId else null
    }

    fun railY(rail: Int): Float = when (rail) {
        AD200Topology.RAIL_TOP_VCC_5V -> topVccY
        AD200Topology.RAIL_TOP_GND -> topGndY
        AD200Topology.RAIL_BOT_VCC_5V -> botVccY
        AD200Topology.RAIL_BOT_GND -> botGndY
        else -> topVccY - rail * 20f
    }

    /**
     * Finds the closest wire to [point] within [maxDist], delegating all curve maths to
     * [BezierWireGeometry] so hit-testing and rendering share one definition of "the wire".
     * Iterates topmost-first without allocating a reversed copy.
     */
    fun findClosestWire(
        point: Offset,
        wires: List<JumperWire>,
        maxDist: Float = 68f
    ): JumperWire? {
        var closestWire: JumperWire? = null
        var closestDistSq = maxDist * maxDist
        var i = wires.lastIndex
        while (i >= 0) {
            val w = wires[i]
            i--
            val a = getSocketPosition(w.startSocket)
            val b = getSocketPosition(w.endSocket)

            // Direct check against the wire's terminal plugs.
            val distA = (point.x - a.x) * (point.x - a.x) + (point.y - a.y) * (point.y - a.y)
            val distB = (point.x - b.x) * (point.x - b.x) + (point.y - b.y) * (point.y - b.y)
            val minTerminalDist = min(distA, distB)
            if (minTerminalDist < closestDistSq) {
                closestDistSq = minTerminalDist
                closestWire = w
            }

            val d = if (w.isManhattan) {
                BezierWireGeometry.distanceSqToManhattan(point.x, point.y, a.x, a.y, b.x, b.y)
            } else {
                BezierWireGeometry.distanceSqToCurve(
                    point.x, point.y, a.x, a.y, b.x, b.y,
                    elevationOffset = w.elevationLevel * 10f
                )
            }
            if (d < closestDistSq) {
                closestDistSq = d
                closestWire = w
            }
        }
        return closestWire
    }

    /** Board-space bounds of a placed chip's moulded body. */
    fun chipBodyRect(chip: BreadboardCircuit.PlacedChipRuntime): Rect {
        val pin1 = getSocketPosition(chip.getPinSocket(1))
        val half = chip.model.pinCount / 2
        val lastLower = getSocketPosition(chip.getPinSocket(half))
        val pinLast = getSocketPosition(chip.getPinSocket(chip.model.pinCount))

        val leftX = min(pin1.x, lastLower.x)
        val rightX = max(pin1.x, lastLower.x)
        val topY = min(pin1.y, pinLast.y)
        val bottomY = max(pin1.y, pinLast.y)
        return Rect(leftX - 10f, topY + 5f, rightX + 10f, bottomY - 5f)
    }

    /**
     * Finds the closest placed IC/chip to [point], returning null if no chip was tapped.
     * Iterates topmost-first without allocating a reversed copy.
     */
    fun findClosestChip(
        point: Offset,
        placedChips: List<BreadboardCircuit.PlacedChipRuntime>
    ): BreadboardCircuit.PlacedChipRuntime? {
        var i = placedChips.lastIndex
        while (i >= 0) {
            val chip = placedChips[i]
            i--
            val r = chipBodyRect(chip)
            if (point.x >= r.left && point.x <= r.right &&
                point.y >= r.top - 3f && point.y <= r.bottom + 3f
            ) return chip
        }
        return null
    }
}
