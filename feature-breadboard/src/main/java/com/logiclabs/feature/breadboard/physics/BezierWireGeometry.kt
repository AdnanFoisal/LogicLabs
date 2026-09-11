package com.logiclabs.feature.breadboard.physics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Single source of truth for jumper-wire curve geometry.
 *
 * Both the renderer ([com.logiclabs.feature.breadboard.canvas.WirePainter]) and the hit tester
 * ([com.logiclabs.feature.breadboard.interaction.SocketHitTester]) build their geometry here, so
 * the drawn curve and the tappable curve can never drift apart.
 *
 * Every entry point has a primitive-float overload so callers never have to materialise an
 * [Offset] for an intermediate (shadow / specular / sample) position.
 */
object BezierWireGeometry {

    /** Control-point placement along the chord. */
    private const val CP1_T = 0.25f
    private const val CP2_T = 0.75f

    /** How much of the chord's vertical delta leaks into the tangents. */
    private const val TANGENT_BIAS = 0.1f

    private const val SAG_MIN = 12.0f
    private const val SAG_MAX = 90.0f
    private const val SAG_RATE = 0.18f

    /** Bias applied to the guide wire's control points so it flexes toward the finger. */
    private const val FINGER_BIAS = 0.34f

    /** Extra pull toward a hysteresis-locked socket so the wire visibly snaps home. */
    private const val LOCK_PULL = 0.55f

    /**
     * Calculates distance-proportional gravity sag:
     * sag = min(90.0, max(12.0, 0.18 * distance))
     */
    fun calculateSag(start: Offset, end: Offset): Float = sagFor(end.x - start.x, end.y - start.y)

    /** Allocation-free sag for a raw chord delta. */
    fun sagFor(dx: Float, dy: Float): Float =
        min(SAG_MAX, max(SAG_MIN, SAG_RATE * hypot(dx, dy)))

    /**
     * Appends a cubic Bézier jumper wire curve with natural gravity sag into the target [path]
     * without any heap allocation.
     */
    fun appendCurve(
        path: Path,
        start: Offset,
        end: Offset,
        sag: Float = calculateSag(start, end),
        elevationOffset: Float = 0f
    ) = appendCurveXY(path, start.x, start.y, end.x, end.y, sag, elevationOffset)

    /** Primitive-float form of [appendCurve] — no [Offset] ever constructed. */
    fun appendCurveXY(
        path: Path,
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        sag: Float = sagFor(ex - sx, ey - sy),
        elevationOffset: Float = 0f
    ) {
        val dx = ex - sx
        val dy = ey - sy
        val drop = sag + elevationOffset
        path.moveTo(sx, sy)
        path.cubicTo(
            sx + dx * CP1_T, sy + dy * TANGENT_BIAS + drop,
            sx + dx * CP2_T, ey - dy * TANGENT_BIAS + drop,
            ex, ey
        )
    }

    /**
     * Manhattan 90-degree flush-cut wire routing.
     */
    fun appendManhattan(path: Path, start: Offset, end: Offset) =
        appendManhattanXY(path, start.x, start.y, end.x, end.y)

    /** Primitive-float form of [appendManhattan]. */
    fun appendManhattanXY(path: Path, sx: Float, sy: Float, ex: Float, ey: Float) {
        val midX = (sx + ex) / 2f
        path.moveTo(sx, sy)
        path.lineTo(midX, sy)
        path.lineTo(midX, ey)
        path.lineTo(ex, ey)
    }

    /**
     * Magnetic guide wire for an in-progress connection.
     *
     * The free end lands at [fx]/[fy] (the finger) when [lockStrength] is 0 and at [tx]/[ty]
     * (the hysteresis-locked socket) when it is 1; the control points are biased toward the
     * finger throughout, with an extra pull toward the locked socket, so the wire flexes under
     * the drag and then visibly snaps home.
     */
    fun appendMagneticGuide(
        path: Path,
        sx: Float,
        sy: Float,
        fx: Float,
        fy: Float,
        tx: Float,
        ty: Float,
        lockStrength: Float
    ) {
        val k = lockStrength.coerceIn(0f, 1f)
        val ex = fx + (tx - fx) * k
        val ey = fy + (ty - fy) * k

        val dx = ex - sx
        val dy = ey - sy
        // A locked wire pulls taut: it loses a third of its slack.
        val drop = sagFor(dx, dy) * (1f - 0.34f * k)

        var cp1x = sx + dx * CP1_T
        var cp1y = sy + dy * TANGENT_BIAS + drop
        var cp2x = sx + dx * CP2_T
        var cp2y = ey - dy * TANGENT_BIAS + drop

        // Flex toward the finger (shoulder bends less than the free end).
        val nearBias = FINGER_BIAS * 0.5f
        cp1x += (fx - cp1x) * nearBias
        cp1y += (fy - cp1y) * nearBias
        cp2x += (fx - cp2x) * FINGER_BIAS
        cp2y += (fy - cp2y) * FINGER_BIAS

        // Snap-home pull.
        val pull = LOCK_PULL * k
        cp2x += (tx - cp2x) * pull
        cp2y += (ty - cp2y) * pull

        path.moveTo(sx, sy)
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, ex, ey)
    }

    /**
     * Squared distance from [px]/[py] to the drawn sag curve between two sockets.
     * Mirrors [appendCurveXY] exactly, so hit-testing can never drift from rendering.
     */
    fun distanceSqToCurve(
        px: Float,
        py: Float,
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        elevationOffset: Float = 0f
    ): Float {
        val dx = ex - sx
        val dy = ey - sy
        val dist = hypot(dx, dy)
        val drop = min(SAG_MAX, max(SAG_MIN, SAG_RATE * dist)) + elevationOffset

        val cp1x = sx + dx * CP1_T
        val cp1y = sy + dy * TANGENT_BIAS + drop
        val cp2x = sx + dx * CP2_T
        val cp2y = ey - dy * TANGENT_BIAS + drop

        val steps = (dist / 20f).toInt().coerceIn(16, 40)
        var best = Float.MAX_VALUE
        var prevX = sx
        var prevY = sy
        var step = 1
        while (step <= steps) {
            val t = step.toFloat() / steps
            val u = 1f - t
            val curX = u * u * u * sx + 3f * u * u * t * cp1x + 3f * u * t * t * cp2x + t * t * t * ex
            val curY = u * u * u * sy + 3f * u * u * t * cp1y + 3f * u * t * t * cp2y + t * t * t * ey
            val d = distanceSqToSegment(px, py, prevX, prevY, curX, curY)
            if (d < best) best = d
            prevX = curX
            prevY = curY
            step++
        }
        return best
    }

    /** Squared distance from [px]/[py] to the three-segment Manhattan route. */
    fun distanceSqToManhattan(
        px: Float,
        py: Float,
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float
    ): Float {
        val midX = (sx + ex) / 2f
        val d1 = distanceSqToSegment(px, py, sx, sy, midX, sy)
        val d2 = distanceSqToSegment(px, py, midX, sy, midX, ey)
        val d3 = distanceSqToSegment(px, py, midX, ey, ex, ey)
        return min(d1, min(d2, d3))
    }

    /** Allocation-free point-to-segment squared distance. */
    fun distanceSqToSegment(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val abx = bx - ax
        val aby = by - ay
        val l2 = abx * abx + aby * aby
        if (l2 == 0f) {
            val dx = px - ax
            val dy = py - ay
            return dx * dx + dy * dy
        }
        val t = (((px - ax) * abx + (py - ay) * aby) / l2).coerceIn(0f, 1f)
        val dx = px - (ax + t * abx)
        val dy = py - (ay + t * aby)
        return dx * dx + dy * dy
    }
}
