package com.logiclabs.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.core.bridge.catalog.TTLChipCatalog
import com.logiclabs.core.data.persistence.ChipSpot
import com.logiclabs.core.data.persistence.WireSpot
import com.logiclabs.feature.breadboard.canvas.BreadboardGeometryMapper
import kotlin.math.max
import kotlin.math.min

/**
 * A miniature of the breadboard for project cards: the real AD-200 geometry, scaled
 * down, with DIP bodies and jumper curves drawn in a simplified two-tone style.
 *
 * This is a *sketch*, not a render: no per-socket clips, no silkscreen, no shadows —
 * just enough that "my adder build" is recognizably my adder build at 96 dp wide. The
 * socket math is the production [BreadboardGeometryMapper], so chip and wire positions
 * are truthful; only their paint is reduced.
 */
@Composable
fun BoardThumbnail(
    chips: List<ChipSpot>,
    wires: List<WireSpot>,
    modifier: Modifier = Modifier
) {
    val mapper = remember { BreadboardGeometryMapper() }
    val wirePath = remember { Path() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val bounds: Rect = mapper.boardBounds()
            if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas

            val s = min(size.width / bounds.width, size.height / bounds.height)
            val ox = (size.width - bounds.width * s) / 2f - bounds.left * s
            val oy = (size.height - bounds.height * s) / 2f - bounds.top * s

            translate(left = ox, top = oy) {
                scale(scale = s, pivot = Offset.Zero) {
                    // Phenolic body.
                    drawRoundRect(
                        color = ThumbnailPhenolic,
                        topLeft = Offset(bounds.left, bounds.top),
                        size = Size(bounds.width, bounds.height),
                        cornerRadius = CornerRadius(26f)
                    )
                    // Power rails.
                    drawLine(ThumbnailRailVcc, Offset(bounds.left + 90f, mapper.topVccY), Offset(bounds.right - 40f, mapper.topVccY), 7f)
                    drawLine(ThumbnailRailGnd, Offset(bounds.left + 90f, mapper.topGndY), Offset(bounds.right - 40f, mapper.topGndY), 7f)
                    drawLine(ThumbnailRailVcc, Offset(bounds.left + 90f, mapper.botVccY), Offset(bounds.right - 40f, mapper.botVccY), 7f)
                    drawLine(ThumbnailRailGnd, Offset(bounds.left + 90f, mapper.botGndY), Offset(bounds.right - 40f, mapper.botGndY), 7f)
                    // The centre DIP trench: between the bottom of block 1 and the top
                    // of block 2 (each block spans four 24-unit socket rows).
                    val trenchY = (mapper.blockTopY(1) + 4f * 24f + mapper.blockTopY(2)) / 2f
                    drawLine(
                        color = ThumbnailTrench,
                        start = Offset(bounds.left + 90f, trenchY),
                        end = Offset(bounds.right - 40f, trenchY),
                        strokeWidth = 10f
                    )

                    // Wires: sagging curves between true socket positions.
                    wires.forEach { w ->
                        val a = mapper.getSocketPosition(w.startSocket)
                        val b = mapper.getSocketPosition(w.endSocket)
                        if (a == Offset.Unspecified || b == Offset.Unspecified) return@forEach
                        val sag = ((a.y + b.y) / 2f) + 30f
                        wirePath.rewind()
                        wirePath.moveTo(a.x, a.y)
                        wirePath.quadraticBezierTo((a.x + b.x) / 2f, sag, b.x, b.y)
                        drawPath(wirePath, Color(w.colorArgb), style = Stroke(width = 9f))
                    }

                    // Chips: DIP bodies over their true pin span.
                    chips.forEach { chip ->
                        val pinCount = TTLChipCatalog.create(chip.partNumber)?.pinCount ?: 14
                        val half = pinCount / 2
                        val p1 = mapper.getSocketPosition(
                            AD200Topology.icPinSocket(chip.trench, chip.startColumn, 1, pinCount, chip.isRotated180)
                        )
                        val pLastUpper = mapper.getSocketPosition(
                            AD200Topology.icPinSocket(chip.trench, chip.startColumn, half, pinCount, chip.isRotated180)
                        )
                        val pFirstLower = mapper.getSocketPosition(
                            AD200Topology.icPinSocket(chip.trench, chip.startColumn, half + 1, pinCount, chip.isRotated180)
                        )
                        val pLast = mapper.getSocketPosition(
                            AD200Topology.icPinSocket(chip.trench, chip.startColumn, pinCount, pinCount, chip.isRotated180)
                        )
                        if (p1 == Offset.Unspecified || pLast == Offset.Unspecified) return@forEach
                        val left = minOf(p1.x, pLastUpper.x, pFirstLower.x, pLast.x) - 9f
                        val right = maxOf(p1.x, pLastUpper.x, pFirstLower.x, pLast.x) + 9f
                        val top = minOf(p1.y, pLastUpper.y, pFirstLower.y, pLast.y) - 8f
                        val bottom = maxOf(p1.y, pLastUpper.y, pFirstLower.y, pLast.y) + 8f
                        drawRoundRect(
                            color = ThumbnailEpoxy,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            cornerRadius = CornerRadius(6f)
                        )
                    }
                }
            }
        }
    }
}

// A hair lighter than the board's own tokens: at thumbnail scale the full-contrast
// phenolic reads as a beige slab and the epoxy disappears into the wires.
private val ThumbnailPhenolic = Color(0xFFE3E0D3)
private val ThumbnailEpoxy = Color(0xFF2A2C33)
private val ThumbnailTrench = Color(0xFFC9C6B8)
private val ThumbnailRailVcc = Color(0xFFD64C4C)
private val ThumbnailRailGnd = Color(0xFF4C6ED6)
