package com.logiclabs.feature.breadboard.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.BenchPalette
import com.logiclabs.core.designsystem.theme.BenchPaletteState
import com.logiclabs.core.designsystem.theme.BreadboardPhenolic
import com.logiclabs.core.designsystem.theme.BreadboardPhenolicShade
import com.logiclabs.core.designsystem.theme.BreadboardSilkscreen
import com.logiclabs.core.designsystem.theme.BreadboardSilkscreenMajor
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.BusGnd
import com.logiclabs.core.designsystem.theme.BusVcc
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisDivider
import com.logiclabs.core.designsystem.theme.ContactClipRecess
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SuccessGreen
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.SwitchWell
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TrenchShadow

/**
 * Paints the AD-200 faceplate: phenolic body, moulded trenches, tie-point clips, silkscreen
 * legend, distribution buses and the three instrument panels.
 *
 * The renderer is split in two so the expensive half can be cached:
 *  - [drawStatic] never reads circuit state. Everything it draws depends only on geometry, density
 *    and the LOD bucket, so [BoardCache] records it into an `android.graphics.Picture` once and
 *    replays it as a single draw op per frame.
 *  - [drawDynamic] draws only what changes: net highlights and the live 1/0, ON/OFF and voltage
 *    readouts.
 *
 * ### Level of detail
 * | zoom | behaviour |
 * |------|-----------|
 * | `< 0.5` | tie-points collapse to dots ([TiePointPainter.LOD_CLIP_MIN_SCALE]) |
 * | `< 0.75` | silkscreen text is dropped entirely ([LOD_SILKSCREEN_MIN_SCALE]) |
 *
 * ### Density
 * Every `textSize` here is authored in **dp** and multiplied by the display density before it
 * reaches a `Paint`. The previous raw-pixel sizes (8.5f … 14f) came out around 3dp on a 2.75x
 * phone, which is why the legend rendered as hairlines.
 */
object FaceplateRenderer {

    /** Below this zoom no silkscreen text is drawn — lowered to 0.5f so FIT scale displays readouts. */
    const val LOD_SILKSCREEN_MIN_SCALE = 0.5f

    // --- Silkscreen type scale, in dp ----------------------------------------------------------
    private const val SIZE_COLUMN_NUM = 7.5f
    private const val SIZE_ROW_LETTER = 8.0f
    private const val SIZE_BUS_MARK = 10.0f
    private const val SIZE_PANEL_TITLE = 8.5f
    private const val SIZE_PANEL_SUB = 7.0f
    private const val SIZE_TERMINAL_LABEL = 8.0f
    private const val SIZE_STATE = 8.0f

    // --- Faceplate plate metrics, in board units -----------------------------------------------
    /** Inset of the top/bottom instrument plates from [BreadboardGeometryMapper.originX]. */
    private const val PANEL_PLATE_INSET = 15f

    /** Width of the left DC power plate; its left edge is the mapper's `powerPanelLeft`. */
    private const val POWER_PANEL_WIDTH = 106f

    /** Lip the moulded DIP trench keeps either side of the terminal column grid. */
    private const val TRENCH_LIP = 12f

    // Hoisted out of the per-frame draw path.
    private val TOP_ROW_LETTERS = arrayOf("A", "B", "C", "D", "E")
    private val BOTTOM_ROW_LETTERS = arrayOf("F", "G", "H", "I", "J")
    private val COLUMN_LABELS = Array(AD200Topology.COLUMNS_PER_BLOCK) { (it + 1).toString() }
    private val LED_LABELS = Array(8) { "L$it" }
    private val SW_LABELS = Array(8) { "SW$it" }

    private fun paint(color: Color, sizeDp: Float, bold: Boolean = false) =
        android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            textSize = sizeDp // replaced by applyDensity()
            isFakeBoldText = bold
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }

    private fun Color.toArgb(): Int = android.graphics.Color.argb(
        (alpha * 255f + 0.5f).toInt(),
        (red * 255f + 0.5f).toInt(),
        (green * 255f + 0.5f).toInt(),
        (blue * 255f + 0.5f).toInt()
    )

    private val columnNumPaint = paint(BreadboardSilkscreen, SIZE_COLUMN_NUM)
    private val columnNumMajorPaint = paint(BreadboardSilkscreenMajor, SIZE_COLUMN_NUM, bold = true)
    private val rowLetterPaint = paint(BreadboardSilkscreenMajor, SIZE_ROW_LETTER, bold = true)
    private val vccMarkPaint = paint(BusVcc, SIZE_BUS_MARK, bold = true)
    private val gndMarkPaint = paint(BusGnd, SIZE_BUS_MARK, bold = true)
    private val panelTitlePaint = paint(AccentCyan, SIZE_PANEL_TITLE, bold = true)
    private val panelSubPaint = paint(TextSecondary, SIZE_PANEL_SUB, bold = true)
    private val terminalLabelPaint = paint(TextPrimary, SIZE_TERMINAL_LABEL)
    private val stateOnPaint = paint(SuccessGreen, SIZE_STATE, bold = true)
    private val stateOffPaint = paint(ShortCircuitAlert, SIZE_STATE, bold = true)
    private val stateInfoPaint = paint(BusGnd, SIZE_STATE, bold = true)

    private val allPaints = arrayOf(
        columnNumPaint to SIZE_COLUMN_NUM,
        columnNumMajorPaint to SIZE_COLUMN_NUM,
        rowLetterPaint to SIZE_ROW_LETTER,
        vccMarkPaint to SIZE_BUS_MARK,
        gndMarkPaint to SIZE_BUS_MARK,
        panelTitlePaint to SIZE_PANEL_TITLE,
        panelSubPaint to SIZE_PANEL_SUB,
        terminalLabelPaint to SIZE_TERMINAL_LABEL,
        stateOnPaint to SIZE_STATE,
        stateOffPaint to SIZE_STATE,
        stateInfoPaint to SIZE_STATE
    )

    private var appliedDensity = 0f
    private var appliedPalette: BenchPalette? = null

    /**
     * Scales every silkscreen paint from dp to px, and re-inks the theme-varying ones
     * when the bench palette flips. Both checks are cheap identity comparisons, so
     * calling this per frame costs nothing; only a density or theme change does work.
     * The rest of the legend is physical colour and never re-inks.
     */
    fun applyDensity(density: Float) {
        val palette = BenchPaletteState.value
        if (density == appliedDensity && palette == appliedPalette) return
        if (density != appliedDensity && density > 0f) {
            for ((p, dp) in allPaints) p.textSize = dp * density
            appliedDensity = density
        }
        panelSubPaint.color = TextSecondary.toArgb()
        terminalLabelPaint.color = TextPrimary.toArgb()
        appliedPalette = palette
    }

    private val phenolicBrush = Brush.verticalGradient(
        0f to BreadboardPhenolic,
        1f to BreadboardPhenolicShade
    )

    // ==========================================================================================
    // Static layer (cacheable)
    // ==========================================================================================

    /**
     * Draws everything that does not depend on circuit state.
     *
     * @param viewport visible board rect; anything fully outside is skipped. Pass
     *   [BreadboardGeometryMapper.boardBounds] when recording into [BoardCache].
     */
    fun drawStatic(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        density: Float,
        zoomScale: Float,
        viewport: Rect
    ) {
        applyDensity(density)
        val silkscreen = zoomScale >= LOD_SILKSCREEN_MIN_SCALE

        // The slab spans the mapper's content-derived edges rather than a padded width
        // constant, so it ends where the last hole does instead of trailing blank plate.
        val bodyLeft = mapper.bodyLeft
        val bodyWidth = mapper.bodyRight - bodyLeft
        val bbTop = mapper.originY - 55f
        val bbHeight = (mapper.bottomBlock3Y + 58f) - bbTop

        drawChassis(scope, mapper)
        drawBody(scope, bodyLeft, bbTop, bodyWidth, bbHeight)
        drawTrenches(scope, mapper)
        drawBuses(scope, mapper, silkscreen)
        drawTiePoints(scope, mapper, zoomScale, viewport)
        drawRailHoles(scope, mapper, zoomScale, viewport)
        if (silkscreen) drawLegend(scope, mapper)
        drawPanels(scope, mapper, silkscreen)
        drawPanelSockets(scope, mapper, zoomScale, silkscreen)
    }

    /**
     * The trainer case. Drawn to exactly [BreadboardGeometryMapper.boardBounds] so the painted
     * chassis, the fit-to-viewport scale and the culling viewport can never disagree on an edge.
     */
    private fun drawChassis(scope: DrawScope, mapper: BreadboardGeometryMapper) {
        val chassisLeft = mapper.boardLeft
        val chassisTop = mapper.boardTop
        val size = Size(mapper.boardWidth(), mapper.boardHeight())
        // Palette-backed, not literals: the trainer case follows the HardwareTheme
        // (these used to be fixed near-black, which painted a black case on the
        // daylight CLEANROOM bench). Recorded into the BoardCache picture, which
        // re-records whenever the palette flips.
        scope.drawRoundRect(
            color = ChassisBase,
            topLeft = Offset(chassisLeft, chassisTop),
            size = size,
            cornerRadius = CornerRadius(16f, 16f)
        )
        scope.drawRoundRect(
            color = ChassisBevelHighlight,
            topLeft = Offset(chassisLeft, chassisTop),
            size = size,
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(2f)
        )
    }

    private fun drawBody(scope: DrawScope, left: Float, top: Float, w: Float, h: Float) {
        scope.drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(left, top + 8f),
            size = Size(w, h),
            cornerRadius = CornerRadius(14f, 14f)
        )
        scope.drawRoundRect(
            brush = phenolicBrush,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(14f, 14f)
        )
        scope.drawRoundRect(
            color = BreadboardPhenolicShade,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(1.5f)
        )
    }

    private fun drawTrenches(scope: DrawScope, mapper: BreadboardGeometryMapper) {
        // Spans the terminal columns with a matching [TRENCH_LIP] either side. It was previously
        // sized off `COLUMNS_PER_BLOCK * pitch`, one whole pitch wider than the 63-pitch column
        // grid, so the moulding ran a column past the block on the right only.
        val left = mapper.originX - TRENCH_LIP
        val w = (mapper.terminalSpanEnd + TRENCH_LIP) - left
        val h = mapper.blockGap - 8f
        val tops = floatArrayOf(
            mapper.originY + 5 * mapper.socketPitch + 4f,
            mapper.originY + 15 * mapper.socketPitch + mapper.blockGap + mapper.sectionGap + 4f
        )
        for (top in tops) {
            scope.drawRoundRect(
                color = TrenchShadow,
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(3f, 3f)
            )
            scope.drawRoundRect(
                color = Color(0x22000000),
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(1f)
            )
        }
    }

    /** The four distribution buses: a coloured line plus periodic +/- marks. */
    private fun drawBuses(scope: DrawScope, mapper: BreadboardGeometryMapper, silkscreen: Boolean) {
        val left = mapper.originX
        val right = mapper.railSpanEnd
        val rails = intArrayOf(
            AD200Topology.RAIL_TOP_VCC_5V,
            AD200Topology.RAIL_TOP_GND,
            AD200Topology.RAIL_BOT_VCC_5V,
            AD200Topology.RAIL_BOT_GND
        )
        for (rail in rails) {
            val y = mapper.railY(rail)
            val isVcc = rail == AD200Topology.RAIL_TOP_VCC_5V || rail == AD200Topology.RAIL_BOT_VCC_5V
            scope.drawLine(
                color = if (isVcc) BusVcc else BusGnd,
                start = Offset(left, y + if (isVcc) -7f else 7f),
                end = Offset(right, y + if (isVcc) -7f else 7f),
                strokeWidth = 2f
            )
        }
        if (!silkscreen) return
        scope.drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            var pos = 0
            while (pos < AD200Topology.SOCKETS_PER_RAIL) {
                val x = mapper.originX + pos * mapper.railPitch - mapper.railPitch * 0.85f
                nc.drawText("+", x, mapper.topVccY + 3f, vccMarkPaint)
                nc.drawText("−", x, mapper.topGndY + 3f, gndMarkPaint)
                nc.drawText("+", x, mapper.botVccY + 3f, vccMarkPaint)
                nc.drawText("−", x, mapper.botGndY + 3f, gndMarkPaint)
                pos += 12
            }
        }
    }

    /**
     * The 1,280 terminal tie-points plus their 256 column continuity channels.
     *
     * Viewport culling is per column, not per socket: one x-range test skips 5 sockets and a
     * channel at once, which is why the whole grid costs ~4 rejections when zoomed in.
     */
    private fun drawTiePoints(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        zoomScale: Float,
        viewport: Rect
    ) {
        val pitch = mapper.socketPitch
        val colFrom = (((viewport.left - mapper.originX) / pitch).toInt() - 1)
            .coerceIn(0, AD200Topology.COLUMNS_PER_BLOCK - 1)
        val colTo = (((viewport.right - mapper.originX) / pitch).toInt() + 1)
            .coerceIn(0, AD200Topology.COLUMNS_PER_BLOCK - 1)
        val channelWide = zoomScale >= TiePointPainter.LOD_CLIP_MIN_SCALE

        var block = 0
        while (block < AD200Topology.TERMINAL_BLOCK_COUNT) {
            val top = mapper.blockTopY(block)
            val bottom = top + 4 * pitch
            if (bottom >= viewport.top - pitch && top <= viewport.bottom + pitch) {
                var col = colFrom
                while (col <= colTo) {
                    val x = mapper.originX + col * pitch
                    if (channelWide) {
                        // Moulded continuity channel behind the five holes of the column.
                        scope.drawRoundRect(
                            color = BreadboardPhenolicShade,
                            topLeft = Offset(x - 7.5f, top - 8f),
                            size = Size(15f, (bottom - top) + 16f),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                    var row = 0
                    while (row < AD200Topology.ROWS_PER_BLOCK) {
                        TiePointPainter.draw(scope, x, top + row * pitch, zoomScale, false)
                        row++
                    }
                    col++
                }
            }
            block++
        }
    }

    /** The 352 bus holes, drawn in groups of four like a real distribution strip. */
    private fun drawRailHoles(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        zoomScale: Float,
        viewport: Rect
    ) {
        val pitch = mapper.railPitch
        val from = (((viewport.left - mapper.originX) / pitch).toInt() - 1)
            .coerceIn(0, AD200Topology.SOCKETS_PER_RAIL - 1)
        val to = (((viewport.right - mapper.originX) / pitch).toInt() + 1)
            .coerceIn(0, AD200Topology.SOCKETS_PER_RAIL - 1)

        var rail = 0
        while (rail < 4) {
            val y = mapper.railY(rail)
            if (y >= viewport.top - pitch && y <= viewport.bottom + pitch) {
                var pos = from
                while (pos <= to) {
                    TiePointPainter.draw(scope, mapper.originX + pos * pitch, y, zoomScale, false)
                    pos++
                }
            }
            rail++
        }
    }

    /** Column numbers (1, 5, 10 … 60, 64) and row letters A–E / F–J on both margins. */
    private fun drawLegend(scope: DrawScope, mapper: BreadboardGeometryMapper) {
        scope.drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val trench1Mid = mapper.originY + 5 * mapper.socketPitch + 4f + (mapper.blockGap - 8f) / 2f
            val trench2Mid = mapper.originY + 15 * mapper.socketPitch + mapper.blockGap +
                mapper.sectionGap + 4f + (mapper.blockGap - 8f) / 2f

            var col = 0
            while (col < AD200Topology.COLUMNS_PER_BLOCK) {
                val num = col + 1
                if (num == 1 || num == AD200Topology.COLUMNS_PER_BLOCK || num % 5 == 0) {
                    val label = COLUMN_LABELS[col]
                    val x = mapper.originX + col * mapper.socketPitch
                    val p = if (num % 10 == 0 || num == 1) columnNumMajorPaint else columnNumPaint
                    nc.drawText(label, x, mapper.originY - 11f, p)
                    nc.drawText(label, x, trench1Mid + 3f, p)
                    nc.drawText(label, x, trench2Mid + 3f, p)
                    nc.drawText(label, x, mapper.bottomBlock3Y + 13f, p)
                }
                col++
            }

            val leftX = mapper.originX - 13f
            val rightX = mapper.terminalSpanEnd + 13f
            var r = 0
            while (r < AD200Topology.ROWS_PER_BLOCK) {
                var block = 0
                while (block < AD200Topology.TERMINAL_BLOCK_COUNT) {
                    val letters = if (block % 2 == 0) TOP_ROW_LETTERS else BOTTOM_ROW_LETTERS
                    val y = mapper.blockTopY(block) + r * mapper.socketPitch + 3f
                    nc.drawText(letters[r], leftX, y, rowLetterPaint)
                    nc.drawText(letters[r], rightX, y, rowLetterPaint)
                    block++
                }
                r++
            }
        }
    }

    private fun drawPanels(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        silkscreen: Boolean
    ) {
        // Both instrument plates run the full width of the phenolic slab they sit above/below.
        val plateLeft = mapper.originX - PANEL_PLATE_INSET
        val plateWidth = mapper.bodyRight - plateLeft

        // Top output panel.
        val topPanelTop = mapper.originY - 98f
        panelPlate(scope, plateLeft, topPanelTop, plateWidth, 46f, 10f)

        // Bottom switch panel. Houses the single row of switch terminals (SW0..SW7).
        val botPanelTop = mapper.bottomBlock3Y + 64f
        panelPlate(scope, plateLeft, botPanelTop, plateWidth, 68f, 10f)

        // 1. Left DC power panel.
        val pwrLeft = mapper.powerPanelLeft
        val pwrTop = mapper.originY - 55f
        val pwrHeight = 205f
        panelPlate(scope, pwrLeft, pwrTop, POWER_PANEL_WIDTH, pwrHeight, 10f)
        scope.drawLine(
            color = ChassisDivider,
            start = Offset(pwrLeft + 10f, pwrTop + 38f),
            end = Offset(pwrLeft + POWER_PANEL_WIDTH - 10f, pwrTop + 38f),
            strokeWidth = 1f
        )

        // 2. Clock Generator panel
        val clkTop = mapper.originY + 165f
        val clkHeight = 85f
        panelPlate(scope, pwrLeft, clkTop, POWER_PANEL_WIDTH, clkHeight, 10f)

        // 3. Pulse Generator panel
        val plsTop = mapper.originY + 265f
        val plsHeight = 135f
        panelPlate(scope, pwrLeft, plsTop, POWER_PANEL_WIDTH, plsHeight, 10f)

        // 4. 7-Segment Display BCD Input panel
        val segTop = mapper.originY + 415f
        val segHeight = 210f
        panelPlate(scope, pwrLeft, segTop, POWER_PANEL_WIDTH, segHeight, 10f)

        if (!silkscreen) return
        val pwrCenterX = mapper.originX - 75f
        scope.drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val midX = plateLeft + plateWidth / 2f
            nc.drawText("LOGIC OUTPUT TERMINALS  L0 – L7", midX, topPanelTop + 15f, panelTitlePaint)
            // +14, not +18: the switch-row title must clear the LED test-point sockets
            // (bottomBlock3Y + 100) by a full lane, not share a band with them.
            nc.drawText("INPUT SWITCH TERMINALS  SW0 – SW7", midX, botPanelTop + 14f, panelTitlePaint)

            // DC Power silkscreen
            nc.drawText("DC POWER", pwrCenterX, pwrTop + 17f, panelTitlePaint)
            nc.drawText("SUPPLY", pwrCenterX, pwrTop + 30f, panelSubPaint)
            nc.drawText("+5V VCC", pwrCenterX, mapper.originY + 35f - 17f, terminalLabelPaint)
            nc.drawText("0V GND", pwrCenterX, mapper.originY + 130f - 17f, terminalLabelPaint)

            // Clock Generator silkscreen
            nc.drawText("CLOCK GEN", pwrCenterX, clkTop + 16f, panelTitlePaint)
            nc.drawText("0.5Hz – 100kHz", pwrCenterX, clkTop + 29f, panelSubPaint)

            // Pulser silkscreen
            nc.drawText("PULSE GEN", pwrCenterX, plsTop + 16f, panelTitlePaint)
            nc.drawText("DEBOUNCED", pwrCenterX, plsTop + 29f, panelSubPaint)

            // 7-Segment silkscreen
            nc.drawText("7-SEGMENT", pwrCenterX, segTop + 16f, panelTitlePaint)
            nc.drawText("BCD INPUTS", pwrCenterX, segTop + 29f, panelSubPaint)
            nc.drawText("IDL-800A", pwrCenterX, segTop + segHeight - 7f, panelSubPaint)
        }
    }

    private fun panelPlate(
        scope: DrawScope,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        radius: Float
    ) {
        // Raised instrument plates read from the palette (SurfaceRaised fill,
        // SurfaceCardBorder rim) so they follow the HardwareTheme like the chassis.
        scope.drawRoundRect(
            color = SurfaceRaised,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(radius, radius)
        )
        scope.drawRoundRect(
            color = SurfaceCardBorder,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(1.5f)
        )
    }

    /**
     * Panel-mount sockets: the eight top outputs, the eight **bottom output test points**
     * (`TERM_BOT_LED0..7` — DSU-unioned and hit-testable but previously never drawn, so they were
     * invisible-yet-tappable), the eight switch terminals and the two DC power posts.
     */
    private fun drawPanelSockets(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        zoomScale: Float,
        silkscreen: Boolean
    ) {
        var i = 0
        while (i < 8) {
            val top = mapper.getSocketPosition(AD200Topology.TERM_LED0 + i)
            TiePointPainter.drawPanelSocket(scope, top.x, top.y, zoomScale, 8f, Color(0xFF4A4E60))

            val sw = mapper.getSocketPosition(AD200Topology.TERM_SW0 + i)
            TiePointPainter.drawPanelSocket(scope, sw.x, sw.y, zoomScale, 10.5f, Color(0xFF5A5E72))
            i++
        }
        val vcc = mapper.getSocketPosition(AD200Topology.TERM_POWER_VCC)
        TiePointPainter.drawPanelSocket(scope, vcc.x, vcc.y, zoomScale, 10.5f, BusVcc)
        val gnd = mapper.getSocketPosition(AD200Topology.TERM_POWER_GND)
        TiePointPainter.drawPanelSocket(scope, gnd.x, gnd.y, zoomScale, 10.5f, BusGnd)

        // Clock Generator sockets
        val clk = mapper.getSocketPosition(AD200Topology.TERM_CLK)
        TiePointPainter.drawPanelSocket(scope, clk.x, clk.y, zoomScale, 9f, AccentCyan)
        val clkInv = mapper.getSocketPosition(AD200Topology.TERM_CLK_INV)
        TiePointPainter.drawPanelSocket(scope, clkInv.x, clkInv.y, zoomScale, 9f, AccentCyan)

        // Pulser sockets
        val pAp = mapper.getSocketPosition(AD200Topology.TERM_PULSER_A_P)
        TiePointPainter.drawPanelSocket(scope, pAp.x, pAp.y, zoomScale, 9f, AmberCore)
        val pAn = mapper.getSocketPosition(AD200Topology.TERM_PULSER_A_N)
        TiePointPainter.drawPanelSocket(scope, pAn.x, pAn.y, zoomScale, 9f, AmberCore)
        val pBp = mapper.getSocketPosition(AD200Topology.TERM_PULSER_B_P)
        TiePointPainter.drawPanelSocket(scope, pBp.x, pBp.y, zoomScale, 9f, AmberCore)
        val pBn = mapper.getSocketPosition(AD200Topology.TERM_PULSER_B_N)
        TiePointPainter.drawPanelSocket(scope, pBn.x, pBn.y, zoomScale, 9f, AmberCore)

        // 7-Segment BCD input sockets
        for (sock in intArrayOf(
            AD200Topology.TERM_SEG_A_BCD_A, AD200Topology.TERM_SEG_A_BCD_B,
            AD200Topology.TERM_SEG_A_BCD_C, AD200Topology.TERM_SEG_A_BCD_D,
            AD200Topology.TERM_SEG_B_BCD_A, AD200Topology.TERM_SEG_B_BCD_B,
            AD200Topology.TERM_SEG_B_BCD_C, AD200Topology.TERM_SEG_B_BCD_D
        )) {
            val p = mapper.getSocketPosition(sock)
            TiePointPainter.drawPanelSocket(scope, p.x, p.y, zoomScale, 8.5f, PhosphorCore)
        }

        if (!silkscreen) return
        scope.drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            // Clock labels
            nc.drawText("CLK", clk.x, clk.y - 13f, terminalLabelPaint)
            nc.drawText("~CLK", clkInv.x, clkInv.y - 13f, terminalLabelPaint)

            // Pulser labels
            nc.drawText("PLS-A", mapper.originX - 75f, pAp.y - 14f, panelSubPaint)
            nc.drawText("P", pAp.x - 15f, pAp.y + 4f, terminalLabelPaint)
            nc.drawText("~P", pAn.x + 16f, pAn.y + 4f, terminalLabelPaint)
            nc.drawText("PLS-B", mapper.originX - 75f, pBp.y - 14f, panelSubPaint)
            nc.drawText("P", pBp.x - 15f, pBp.y + 4f, terminalLabelPaint)
            nc.drawText("~P", pBn.x + 16f, pBn.y + 4f, terminalLabelPaint)

            // 7-Segment labels
            nc.drawText("HEX A", mapper.originX - 98f, mapper.originY + 448f, terminalLabelPaint)
            nc.drawText("HEX B", mapper.originX - 52f, mapper.originY + 448f, terminalLabelPaint)
            val segLabels = arrayOf("A(1)", "B(2)", "C(4)", "D(8)")
            for (idx in 0..3) {
                val yRow = mapper.originY + 465f + idx * 40f
                nc.drawText(segLabels[idx], mapper.originX - 75f, yRow + 3f, panelSubPaint)
            }
            var n = 0
            while (n < 8) {
                // Labels sit BESIDE their sockets (32 units left) rather than stacked in
                // the lane above: the panel title already owns that band.
                val top = mapper.getSocketPosition(AD200Topology.TERM_LED0 + n)
                nc.drawText(LED_LABELS[n], top.x - 32f, top.y + 4f, terminalLabelPaint)
                val sw = mapper.getSocketPosition(AD200Topology.TERM_SW0 + n)
                nc.drawText(SW_LABELS[n], sw.x - 32f, sw.y + 4f, terminalLabelPaint)
                n++
            }
        }
    }

    // ==========================================================================================
    // Dynamic layer (per frame)
    // ==========================================================================================

    /**
     * Draws only the circuit-dependent overlay: highlighted nets and live state readouts.
     * At 40 wires and 12 ICs this is a few dozen ops, not a few thousand.
     */
    fun drawDynamic(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        circuit: BreadboardCircuit,
        density: Float,
        zoomScale: Float,
        highlightSockets: Set<Int>,
        viewport: Rect
    ) {
        applyDensity(density)
        drawHighlights(scope, mapper, zoomScale, highlightSockets, viewport)
        drawSwitchIndicators(scope, mapper, circuit)
        if (zoomScale >= LOD_SILKSCREEN_MIN_SCALE) drawStateReadouts(scope, mapper, circuit)
    }

    private fun drawSwitchIndicators(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        circuit: BreadboardCircuit
    ) {
        for (i in 0 until 8) {
            val sw = mapper.getSocketPosition(AD200Topology.TERM_SW0 + i)
            val swOn = circuit.masterPower && circuit.switches[i]
            if (swOn) {
                scope.drawCircle(
                    color = SuccessGreen.copy(alpha = 0.35f),
                    radius = 13.5f,
                    center = sw
                )
                scope.drawCircle(
                    color = SuccessGreen,
                    radius = 5.5f,
                    center = sw
                )
            } else {
                scope.drawCircle(
                    color = SwitchWell,
                    radius = 5.5f,
                    center = sw
                )
            }
        }
    }

    private fun drawHighlights(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        zoomScale: Float,
        highlightSockets: Set<Int>,
        viewport: Rect
    ) {
        if (highlightSockets.isEmpty()) return
        for (id in highlightSockets) {
            val pos = mapper.getSocketPosition(id)
            if (pos.x < viewport.left - 12f || pos.x > viewport.right + 12f) continue
            if (pos.y < viewport.top - 12f || pos.y > viewport.bottom + 12f) continue
            TiePointPainter.draw(scope, pos.x, pos.y, zoomScale, highlighted = true)
        }
    }

    private fun drawStateReadouts(
        scope: DrawScope,
        mapper: BreadboardGeometryMapper,
        circuit: BreadboardCircuit
    ) {
        scope.drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            var i = 0
            while (i < 8) {
                val on = circuit.masterPower && circuit.ledValues[i]
                val p = if (on) stateOnPaint else stateOffPaint
                val label = if (on) "1" else "0"
                // LED states sit BESIDE their terminals (32 units right, mirroring the
                // silkscreen label on the left).
                val top = mapper.getSocketPosition(AD200Topology.TERM_LED0 + i)
                nc.drawText(label, top.x + 32f, top.y + 4f, p)

                // Switch state keeps its own lane below the terminal
                val swOn = circuit.masterPower && circuit.switches[i]
                val sw = mapper.getSocketPosition(AD200Topology.TERM_SW0 + i)
                nc.drawText(
                    if (swOn) "ON (1)" else "OFF (0)",
                    sw.x,
                    sw.y + 24f,
                    if (swOn) stateOnPaint else stateOffPaint
                )
                i++
            }
            val vcc = mapper.getSocketPosition(AD200Topology.TERM_POWER_VCC)
            nc.drawText(
                if (circuit.masterPower) "+5.0V" else "0.0V OFF",
                mapper.originX - 75f,
                vcc.y + 27f,
                if (circuit.masterPower) stateOnPaint else stateOffPaint
            )
            val gnd = mapper.getSocketPosition(AD200Topology.TERM_POWER_GND)
            nc.drawText("GROUND", mapper.originX - 75f, gnd.y + 27f, stateInfoPaint)
        }
    }

    /**
     * Uncached fallback / preview path: static + dynamic in one call. [BreadboardCanvas] normally
     * routes the static half through [BoardCache] instead.
     */
    fun draw(
        drawScope: DrawScope,
        mapper: BreadboardGeometryMapper,
        circuit: BreadboardCircuit,
        zoomScale: Float,
        highlightSockets: Set<Int> = emptySet(),
        density: Float = 1f,
        viewport: Rect = mapper.boardBounds()
    ) {
        drawStatic(drawScope, mapper, density, zoomScale, viewport)
        drawDynamic(drawScope, mapper, circuit, density, zoomScale, highlightSockets, viewport)
    }

    /** Marker fill used behind the cached picture so a missing cache is still legible. */
    internal val fallbackFill = ContactClipRecess
}
