package com.logiclabs.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn vector icons for the bench.
 *
 * The toolbar these replace used emoji glyphs as `Text` — `🧩 📈 📚 🔌 🖐️ 🗑️`. Emoji are
 * font data: they render at whatever size, weight, colour and *shape* the platform's
 * emoji font happens to supply, they cannot be tinted, they shift baseline against the
 * label beneath them, and they look nothing like the rest of the chassis. Every glyph
 * here is a stroked path instead: tintable, crisp at any density, no new dependency, and
 * drawn in the same visual language as the drawn hardware.
 *
 * All icons are authored on a 24x24 grid with a 1.8dp stroke, round caps and round
 * joins, so they sit together on a rail without one reading heavier than its neighbours.
 * Fills are used only where a shape is genuinely solid (an LED lens, a pin-1 dot).
 */
object LogicIcons {

    private const val VIEWPORT = 24f
    private val STROKE = 1.8f

    private inline fun icon(
        name: String,
        block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT
    ).apply(block).build()

    private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.stroked(
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder
    )

    private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.filled(
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) = path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder)

    /**
     * DIP package, seen from above: a body rectangle with three leads down each side and
     * the pin-1 notch cut into the left edge. Replaces `🧩`.
     */
    val Chip: ImageVector by lazy {
        icon("Chip") {
            stroked {
                // Body.
                moveTo(7f, 5f); lineTo(17f, 5f); lineTo(17f, 19f); lineTo(7f, 19f); close()
                // Pin-1 notch, cut into the left edge rather than bumped out of it.
                moveTo(7f, 10.5f); lineTo(9.2f, 12f); lineTo(7f, 13.5f)
                // Leads, left then right.
                moveTo(7f, 8f); lineTo(4f, 8f)
                moveTo(7f, 12f); lineTo(4f, 12f)
                moveTo(7f, 16f); lineTo(4f, 16f)
                moveTo(17f, 8f); lineTo(20f, 8f)
                moveTo(17f, 12f); lineTo(20f, 12f)
                moveTo(17f, 16f); lineTo(20f, 16f)
            }
        }
    }

    /**
     * Oscilloscope: screen frame with a square wave crossing it. Replaces `📈` — and is
     * accurate, since the traces on this bench are logic levels, not a rising curve.
     */
    val Scope: ImageVector by lazy {
        icon("Scope") {
            stroked {
                moveTo(3.5f, 5.5f); lineTo(20.5f, 5.5f); lineTo(20.5f, 18.5f)
                lineTo(3.5f, 18.5f); close()
                // Square wave: low, rise, high, fall, low.
                moveTo(6f, 15f); lineTo(9f, 15f); lineTo(9f, 9f); lineTo(13f, 9f)
                lineTo(13f, 15f); lineTo(16f, 15f); lineTo(16f, 9f); lineTo(18f, 9f)
            }
        }
    }

    /** Stacked lab manuals with a bookmark. Replaces `📚`. */
    val Labs: ImageVector by lazy {
        icon("Labs") {
            stroked {
                moveTo(5f, 4.5f); lineTo(19f, 4.5f); lineTo(19f, 19.5f); lineTo(5f, 19.5f); close()
                moveTo(8.5f, 4.5f); lineTo(8.5f, 19.5f)
                moveTo(11.5f, 8.5f); lineTo(16f, 8.5f)
                moveTo(11.5f, 12f); lineTo(16f, 12f)
                moveTo(11.5f, 15.5f); lineTo(14f, 15.5f)
            }
        }
    }

    /**
     * Jumper wire: a sagging catenary between two tie-points, matching how wires are
     * actually drawn on the board. Replaces `🔌`.
     */
    val Wire: ImageVector by lazy {
        icon("Wire") {
            stroked {
                // The sag mirrors the board's distance-proportional droop.
                moveTo(5f, 8f)
                curveTo(8f, 18f, 16f, 18f, 19f, 8f)
            }
            filled {
                // Terminal boots.
                moveTo(5f, 6f)
                arcToRelative(2f, 2f, 0f, true, true, 0.1f, 0f)
                close()
                moveTo(19f, 6f)
                arcToRelative(2f, 2f, 0f, true, true, 0.1f, 0f)
                close()
            }
        }
    }

    /** Open hand for pan/roam mode. Replaces `🖐️`. */
    val Roam: ImageVector by lazy {
        icon("Roam") {
            stroked {
                // Palm and thumb.
                moveTo(7f, 13f)
                lineTo(7f, 9.5f)
                moveTo(7f, 13f)
                curveTo(7f, 18f, 9.5f, 20f, 12.5f, 20f)
                curveTo(15.5f, 20f, 17.5f, 18f, 17.5f, 14.5f)
                lineTo(17.5f, 9f)
                // Fingers.
                moveTo(10.5f, 10f); lineTo(10.5f, 5f)
                moveTo(14f, 10f); lineTo(14f, 4.5f)
                moveTo(17.5f, 11f); lineTo(17.5f, 6.5f)
                // Thumb web.
                moveTo(7f, 13.5f); lineTo(5f, 15.5f)
            }
        }
    }

    /** Waste bin. Replaces `🗑️`. */
    val Trash: ImageVector by lazy {
        icon("Trash") {
            stroked {
                moveTo(4.5f, 7f); lineTo(19.5f, 7f)
                moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f)
                moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
                moveTo(10.5f, 10.5f); lineTo(10.5f, 16.5f)
                moveTo(13.5f, 10.5f); lineTo(13.5f, 16.5f)
            }
        }
    }

    /** Verification check inside a seal ring. */
    val Verify: ImageVector by lazy {
        icon("Verify") {
            stroked {
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, -0.1f, 0f)
                close()
                moveTo(8f, 12.2f); lineTo(11f, 15.2f); lineTo(16.2f, 9f)
            }
        }
    }

    /** Close / dismiss. */
    val Close: ImageVector by lazy {
        icon("Close") {
            stroked {
                moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
            }
        }
    }

    /**
     * Fit-to-viewport: corner brackets framing the whole board, bisected by its centre
     * line — the same affordance the zoom pill's FIT button triggers.
     */
    val FitBoard: ImageVector by lazy {
        icon("FitBoard") {
            stroked {
                moveTo(4f, 8f); lineTo(4f, 5f); lineTo(7f, 5f)
                moveTo(17f, 5f); lineTo(20f, 5f); lineTo(20f, 8f)
                moveTo(20f, 16f); lineTo(20f, 19f); lineTo(17f, 19f)
                moveTo(7f, 19f); lineTo(4f, 19f); lineTo(4f, 16f)
                moveTo(8.5f, 12f); lineTo(15.5f, 12f)
            }
        }
    }

    /** Pen nib for wire-draw mode. */
    val WireDraw: ImageVector by lazy {
        icon("WireDraw") {
            stroked {
                moveTo(4.5f, 19.5f); lineTo(6f, 15f); lineTo(16.5f, 4.5f)
                lineTo(19.5f, 7.5f); lineTo(9f, 18f); close()
                moveTo(14.5f, 6.5f); lineTo(17.5f, 9.5f)
            }
        }
    }

    /** Cursor arrow for wire-select mode. */
    val WireSelect: ImageVector by lazy {
        icon("WireSelect") {
            stroked {
                moveTo(6f, 4f); lineTo(6f, 18f); lineTo(10f, 14f)
                lineTo(13f, 20.5f); lineTo(15.5f, 19.5f); lineTo(12.5f, 13f)
                lineTo(18f, 12f); close()
            }
        }
    }

    /** Copy-to-clipboard: two offset sheets. */
    val Copy: ImageVector by lazy {
        icon("Copy") {
            stroked {
                moveTo(9f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 15f); lineTo(9f, 15f); close()
                moveTo(15f, 18.5f); lineTo(4.5f, 18.5f); lineTo(4.5f, 8f); lineTo(6.5f, 8f)
            }
        }
    }

    /** Export: a document with an arrow leaving it. */
    val Export: ImageVector by lazy {
        icon("Export") {
            stroked {
                moveTo(13f, 4.5f); lineTo(6f, 4.5f); lineTo(6f, 19.5f); lineTo(18f, 19.5f)
                lineTo(18f, 9.5f)
                moveTo(13f, 4.5f); lineTo(18f, 9.5f); lineTo(13f, 9.5f); close()
                moveTo(9.5f, 14.5f); lineTo(15f, 14.5f)
                moveTo(12.8f, 12.3f); lineTo(15f, 14.5f); lineTo(12.8f, 16.7f)
            }
        }
    }

    /** Flip a wire end-for-end. */
    val Flip: ImageVector by lazy {
        icon("Flip") {
            stroked {
                moveTo(4f, 9.5f); lineTo(20f, 9.5f)
                moveTo(17.2f, 6.7f); lineTo(20f, 9.5f); lineTo(17.2f, 12.3f)
                moveTo(20f, 15.5f); lineTo(4f, 15.5f)
                moveTo(6.8f, 12.7f); lineTo(4f, 15.5f); lineTo(6.8f, 18.3f)
            }
        }
    }

    /** Manhattan (right-angle) routing. */
    val RouteSquare: ImageVector by lazy {
        icon("RouteSquare") {
            stroked {
                moveTo(4f, 18f); lineTo(4f, 10f); lineTo(12f, 10f); lineTo(12f, 6f)
                lineTo(20f, 6f)
            }
        }
    }

    /** Curved (catenary) routing. */
    val RouteCurve: ImageVector by lazy {
        icon("RouteCurve") {
            stroked {
                moveTo(4f, 8f)
                curveTo(4f, 18f, 20f, 6f, 20f, 16f)
            }
        }
    }

    /** Reload / re-run. */
    val Reload: ImageVector by lazy {
        icon("Reload") {
            stroked {
                moveTo(19.5f, 12f)
                arcToRelative(7.5f, 7.5f, 0f, true, true, -2.4f, -5.5f)
                moveTo(19.5f, 4f); lineTo(19.5f, 7.5f); lineTo(16f, 7.5f)
            }
        }
    }

    /** Power: the IEC standby mark. */
    val Power: ImageVector by lazy {
        icon("Power") {
            stroked {
                moveTo(12f, 3.5f); lineTo(12f, 11f)
                moveTo(7.2f, 7f)
                arcToRelative(7f, 7f, 0f, true, false, 9.6f, 0f)
            }
        }
    }

    /** Logic probe: a needle tip, barrel and trailing lead. */
    val Probe: ImageVector by lazy {
        icon("Probe") {
            stroked {
                // Needle down to the contact point.
                moveTo(5f, 19f); lineTo(11f, 13f)
                // Barrel.
                moveTo(10f, 12f); lineTo(15f, 7f); lineTo(19f, 11f); lineTo(14f, 16f); close()
                // Lead leaving the back of the barrel.
                moveTo(17f, 13f); lineTo(21f, 17f)
            }
            filled {
                // Contact spark at the tip.
                moveTo(4.6f, 18.2f)
                arcToRelative(1.4f, 1.4f, 0f, true, true, 0.1f, 0f)
                close()
            }
        }
    }

    /**
     * Clock face with hour and minute hands. Currently unused by the console (the clock
     * dial is drawn bespoke); kept as the natural glyph for any future clock control.
     */
    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroked {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, true, true, -0.1f, 0f)
                close()
                moveTo(12f, 12f); lineTo(12f, 6.5f)
                moveTo(12f, 12f); lineTo(16f, 14f)
            }
        }
    }

    /**
     * Settings gear: a precision-machined mechanical cog with eight teeth and
     * a central bore.
     */
    val Gear: ImageVector by lazy {
        icon("Gear") {
            stroked {
                // Centre bore (concentric circle of radius 3 centered at (12, 12))
                moveTo(12f, 9f)
                arcToRelative(3f, 3f, 0f, false, true, 0f, 6f)
                arcToRelative(3f, 3f, 0f, false, true, 0f, -6f)
                close()

                // Eight machined teeth around the outer rim
                moveTo(19.4f, 15f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, 0.33f, 1.82f)
                lineToRelative(0.06f, 0.06f)
                arcToRelative(2f, 2f, 0f, false, true, 0f, 2.83f)
                arcToRelative(2f, 2f, 0f, false, true, -2.83f, 0f)
                lineToRelative(-0.06f, -0.06f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -1.82f, -0.33f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -1f, 1.51f)
                verticalLineTo(21f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineToRelative(-0.09f)
                arcTo(1.65f, 1.65f, 0f, false, false, 9f, 19.4f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -1.82f, 0.33f)
                lineToRelative(-0.06f, 0.06f)
                arcToRelative(2f, 2f, 0f, false, true, -2.83f, 0f)
                arcToRelative(2f, 2f, 0f, false, true, 0f, -2.83f)
                lineToRelative(0.06f, -0.06f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, 0.33f, -1.82f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -1.51f, -1f)
                horizontalLineTo(3f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineToRelative(0.09f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.6f, 9f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -0.33f, -1.82f)
                lineToRelative(-0.06f, -0.06f)
                arcToRelative(2f, 2f, 0f, false, true, 0f, -2.83f)
                arcToRelative(2f, 2f, 0f, false, true, 2.83f, 0f)
                lineToRelative(0.06f, 0.06f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, 1.82f, 0.33f)
                horizontalLineTo(9f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, 1f, -1.51f)
                verticalLineTo(3f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineToRelative(0.09f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, 1f, 1.51f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, 1.82f, -0.33f)
                lineToRelative(0.06f, -0.06f)
                arcToRelative(2f, 2f, 0f, false, true, 2.83f, 0f)
                arcToRelative(2f, 2f, 0f, false, true, 0f, 2.83f)
                lineToRelative(-0.06f, 0.06f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -0.33f, 1.82f)
                verticalLineTo(9f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, 1.51f, 1f)
                horizontalLineTo(21f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineToRelative(-0.09f)
                arcToRelative(1.65f, 1.65f, 0f, false, false, -1.51f, 1f)
                close()
            }
        }
    }

    /** Backward arrow, for returning to the instrument rack. */
    val Back: ImageVector by lazy {
        icon("Back") {
            stroked {
                moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(12f, 5f); lineTo(5f, 12f); lineTo(12f, 19f)
            }
        }
    }

    /** Forward chevron, for a card's go-ahead affordance. */
    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            stroked {
                moveTo(9.5f, 5.5f); lineTo(15.5f, 12f); lineTo(9.5f, 18.5f)
            }
        }
    }

    /** Breadboard: body, centre DIP trench, two rows of tie-points. */
    val Breadboard: ImageVector by lazy {
        icon("Breadboard") {
            stroked {
                moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f); lineTo(19.5f, 17.5f); lineTo(4.5f, 17.5f); close()
                moveTo(4.5f, 12f); lineTo(19.5f, 12f)
            }
            filled {
                // Top row of sockets.
                dot(8f, 9.2f); dot(12f, 9.2f); dot(16f, 9.2f)
                // Bottom row.
                dot(8f, 14.8f); dot(12f, 14.8f); dot(16f, 14.8f)
            }
        }
    }

    /** Achievement rosette: a seal with two ribbon tails. */
    val Rosette: ImageVector by lazy {
        icon("Rosette") {
            stroked {
                moveTo(15.8f, 7.5f)
                arcToRelative(3.8f, 3.8f, 0f, true, true, -0.1f, 0f)
                close()
                moveTo(9.2f, 10.6f); lineTo(8f, 19.5f); lineTo(12f, 17.2f); lineTo(16f, 19.5f); lineTo(14.8f, 10.6f)
            }
        }
    }

    /** Home: roofline over a doored face. */
    val Home: ImageVector by lazy {
        icon("Home") {
            stroked {
                moveTo(4.5f, 10.5f); lineTo(12f, 4.5f); lineTo(19.5f, 10.5f)
                moveTo(6.5f, 9.8f); lineTo(6.5f, 19.5f); lineTo(17.5f, 19.5f); lineTo(17.5f, 9.8f)
                moveTo(10.4f, 19.5f); lineTo(10.4f, 15f); lineTo(13.6f, 15f); lineTo(13.6f, 19.5f)
            }
        }
    }

    /** One filled socket dot, used by [Breadboard] and any grid-of-holes glyph. */
    private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.dot(x: Float, y: Float) = filled {
        moveTo(x - 0.85f, y)
        arcToRelative(0.85f, 0.85f, 0f, true, true, 0.1f, 0f)
        close()
    }

    /** Save: the classic protected-floppy outline — body, shutter notch, label well. */
    val Save: ImageVector by lazy {
        icon("Save") {
            stroked {
                moveTo(5f, 4.5f); lineTo(16.5f, 4.5f); lineTo(19.5f, 7.5f); lineTo(19.5f, 19.5f); lineTo(5f, 19.5f); close()
                moveTo(8f, 4.5f); lineTo(8f, 9.5f); lineTo(15f, 9.5f); lineTo(15f, 4.5f)
                moveTo(8f, 19.5f); lineTo(8f, 14f); lineTo(16f, 14f); lineTo(16f, 19.5f)
            }
        }
    }

    /** Information icon: a circle with an 'i' glyph. */
    val Info: ImageVector by lazy {
        icon("Info") {
            stroked {
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, -0.1f, 0f)
                close()
                moveTo(12f, 11f); lineTo(12f, 16.5f)
            }
            filled {
                moveTo(12f, 7.2f)
                arcToRelative(1.1f, 1.1f, 0f, true, true, 0.1f, 0f)
                close()
            }
        }
    }

    /** Import: a document with an arrow arriving. */
    val Import: ImageVector by lazy {
        icon("Import") {
            stroked {
                moveTo(6f, 4.5f); lineTo(18f, 4.5f); lineTo(18f, 19.5f); lineTo(6f, 19.5f); close()
                moveTo(12f, 8f); lineTo(12f, 16f)
                moveTo(9.2f, 13.2f); lineTo(12f, 16f); lineTo(14.8f, 13.2f)
            }
        }
    }

    // --- Additions from the icon audit ---------------------------------------
    // Same grid, stroke and cap discipline as everything above; additive names only,
    // so no existing caller can break.

    /**
     * Gate-level schematic: an AND-gate body — flat input edge, semicircular output
     * edge — with two input leads and one output lead, the canonical symbol of a
     * boolean diagram. Reserved for the upcoming schematic (gate-diagram) view toggle,
     * so breadboard view and gate view read as peers on the rail.
     */
    val Schematic: ImageVector by lazy {
        icon("Schematic") {
            stroked {
                // Gate body.
                moveTo(7f, 5f); lineTo(12f, 5f)
                arcToRelative(7f, 7f, 0f, false, true, 0f, 14f)
                lineTo(7f, 19f); close()
                // Two inputs on the flat edge, one output from the curve.
                moveTo(3.5f, 9.5f); lineTo(7f, 9.5f)
                moveTo(3.5f, 14.5f); lineTo(7f, 14.5f)
                moveTo(19f, 12f); lineTo(20.5f, 12f)
            }
        }
    }

    /**
     * Truth table: framed grid with a header band, an input/output column split and a
     * body rule — the shape every lab's vector table takes.
     */
    val TruthTable: ImageVector by lazy {
        icon("TruthTable") {
            stroked {
                moveTo(4.5f, 5.5f); lineTo(19.5f, 5.5f); lineTo(19.5f, 18.5f)
                lineTo(4.5f, 18.5f); close()
                // Header band.
                moveTo(4.5f, 9.5f); lineTo(19.5f, 9.5f)
                // Inputs | outputs column split, plus one body rule.
                moveTo(13.5f, 5.5f); lineTo(13.5f, 18.5f)
                moveTo(4.5f, 14f); lineTo(19.5f, 14f)
            }
        }
    }

    /** Magnifier with a plus — zoom in. Pairs with [FitBoard] on the canvas zoom pill. */
    val ZoomIn: ImageVector by lazy {
        icon("ZoomIn") {
            stroked {
                moveTo(10.5f, 5f)
                arcToRelative(5.5f, 5.5f, 0f, true, true, -0.1f, 0f)
                close()
                moveTo(14.7f, 14.7f); lineTo(19f, 19f)
                moveTo(8.3f, 10.5f); lineTo(12.7f, 10.5f)
                moveTo(10.5f, 8.3f); lineTo(10.5f, 12.7f)
            }
        }
    }

    /** Magnifier with a minus — zoom out. */
    val ZoomOut: ImageVector by lazy {
        icon("ZoomOut") {
            stroked {
                moveTo(10.5f, 5f)
                arcToRelative(5.5f, 5.5f, 0f, true, true, -0.1f, 0f)
                close()
                moveTo(14.7f, 14.7f); lineTo(19f, 19f)
                moveTo(8.3f, 10.5f); lineTo(12.7f, 10.5f)
            }
        }
    }

    /**
     * Rotate a placed IC 180°: a clockwise half-swing over a DIP-style body. Distinct
     * from [Flip] (which swaps a wire's ends) and from [Reload] (which re-runs a lab)
     * — the tool rail currently borrows [Flip] for this action.
     */
    val Rotate: ImageVector by lazy {
        icon("Rotate") {
            stroked {
                // Clockwise half-swing over the top of the part.
                moveTo(5f, 12f)
                arcToRelative(7f, 7f, 0f, false, true, 14f, 0f)
                // Arrowhead bracket where the swing lands.
                moveTo(19f, 8.4f); lineTo(19f, 12f); lineTo(15.4f, 12f)
                // The part being turned.
                moveTo(8f, 14.5f); lineTo(16f, 14.5f); lineTo(16f, 19.5f)
                lineTo(8f, 19.5f); close()
            }
        }
    }

    /**
     * Light/dark chassis contrast: a dial split into a filled and an empty half — the
     * theme-picker affordance for the appearance module.
     */
    val Contrast: ImageVector by lazy {
        icon("Contrast") {
            stroked {
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, -0.1f, 0f)
                close()
                moveTo(12f, 3.5f); lineTo(12f, 20.5f)
            }
            filled {
                // The lit half.
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, false, true, 0f, 17f)
                close()
            }
        }
    }

    /** Speaker cone with two sound waves — the console-audio affordance. */
    val Speaker: ImageVector by lazy {
        icon("Speaker") {
            stroked {
                moveTo(4.5f, 9.5f); lineTo(8f, 9.5f); lineTo(12.5f, 5.5f)
                lineTo(12.5f, 18.5f); lineTo(8f, 14.5f); lineTo(4.5f, 14.5f); close()
                moveTo(15.5f, 9.5f)
                curveTo(16.8f, 10.8f, 16.8f, 13.2f, 15.5f, 14.5f)
                moveTo(18f, 7.5f)
                curveTo(20.2f, 9.7f, 20.2f, 14.3f, 18f, 16.5f)
            }
        }
    }

    /** Search: a magnifying glass with a circular lens and angled handle. */
    val Search: ImageVector by lazy {
        icon("Search") {
            stroked {
                moveTo(10.5f, 5f)
                arcToRelative(5.5f, 5.5f, 0f, true, true, -0.1f, 0f)
                close()
                moveTo(14.7f, 14.7f); lineTo(19.5f, 19.5f)
            }
        }
    }

    /** Share: three nodes connected by branches in a broadcast topology. */
    val Share: ImageVector by lazy {
        icon("Share") {
            stroked {
                // Connecting branches
                moveTo(8.5f, 10.7f); lineTo(15.5f, 7.2f)
                moveTo(8.5f, 13.3f); lineTo(15.5f, 16.8f)
                // Source node (left)
                moveTo(6f, 9.5f)
                arcToRelative(2.5f, 2.5f, 0f, true, true, -0.1f, 0f)
                close()
                // Top-right node
                moveTo(18f, 3.5f)
                arcToRelative(2.5f, 2.5f, 0f, true, true, -0.1f, 0f)
                close()
                // Bottom-right node
                moveTo(18f, 15.5f)
                arcToRelative(2.5f, 2.5f, 0f, true, true, -0.1f, 0f)
                close()
            }
        }
    }

    // --- Aliases -------------------------------------------------------------
    // Same glyph, the name the call site naturally reaches for. `by lazy` above
    // means these cost one reference each, not a second ImageVector.

    /** Alias of [Trash] — the destructive "clear the board" action. */
    val Clear: ImageVector get() = Trash

    /** Alias of [Verify] — a confirmation checkmark. */
    val Check: ImageVector get() = Verify

    /** Alias of [Gear] — bench configuration and system preferences. */
    val Settings: ImageVector get() = Gear
}
