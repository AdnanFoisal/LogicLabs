package com.logiclabs.feature.breadboard.interaction

import androidx.compose.ui.geometry.Offset
import com.logiclabs.core.bridge.model.JumperWire
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.feature.breadboard.canvas.BreadboardGeometryMapper

/**
 * Converts finger positions into socket ids at a constant **screen-space** touch target.
 *
 * ### Why this exists
 * [BreadboardGeometryMapper.findClosestSocket] takes its radius in *board* coordinates, but every
 * caller feeds it a point that has already been divided by the zoom scale. A literal `14f` there
 * is therefore a target that shrinks with the board: about a 5dp radius at 1.0x and 2dp at minimum
 * zoom. This class owns the dp→px→board conversion so the on-screen target is a constant
 * [Dimens.MinTouchTarget] (48dp) across the whole zoom range, and it is the single place where any
 * breadboard touch radius is defined.
 *
 * ### Magnetic hysteresis
 * Once a socket is within [Dimens.SocketLockRadius] it becomes *locked* and is returned even as the
 * finger drifts, right up to [Dimens.SocketReleaseRadius]. Without that asymmetry the snapped
 * socket flickers between neighbours whenever the finger sits on a cell boundary.
 *
 * The instance is cheap and holds only the lock; create one per gesture surface with `remember {}`.
 */
class SocketHitTester(private val mapper: BreadboardGeometryMapper) {

    /** The socket currently held by magnetic lock, or null when nothing is snapped. */
    var lockedSocket: Int? = null
        private set

    /**
     * 0 when the finger is at (or beyond) the release radius, 1 when it is dead-centre on the
     * locked socket. Drives how taut the magnetic guide wire is drawn.
     */
    var lockStrength: Float = 0f
        private set

    /** Clears the lock; call at the start and end of each gesture. */
    fun reset() {
        lockedSocket = null
        lockStrength = 0f
    }

    /**
     * Resolves [boardPoint] (already in board coordinates) to a socket id.
     *
     * @param density `LocalDensity.current.density` — px per dp.
     * @param scale current board zoom, used to keep the screen target size constant.
     */
    fun resolve(boardPoint: Offset, density: Float, scale: Float): Int? {
        val safeScale = if (scale <= 0f) 1f else scale
        // Pitch caps. The dp targets keep the *screen* target constant at high zoom, but
        // at low zoom the board-space dp radius swells past several socket pitches, so a
        // tap could acquire (or hold) a hole far from the finger — the "wrong hole
        // connected" report. Capping each radius to a fraction of the 0.1" pitch means a
        // hole only ever engages from inside its own neighbourhood, at every zoom.
        val pitch = mapper.socketPitch
        val lockBoard = kotlin.math.min(
            boardRadius(Dimens.SocketLockRadius.value, density, safeScale),
            pitch * LOCK_PITCH_FRACTION
        )
        val releaseBoard = kotlin.math.min(
            boardRadius(Dimens.SocketReleaseRadius.value, density, safeScale),
            pitch * RELEASE_PITCH_FRACTION
        )
        val hitBoard = kotlin.math.min(
            boardRadius(Dimens.SocketHitRadius.value, density, safeScale),
            pitch * ACQUIRE_PITCH_FRACTION
        )

        // 1. Hold the existing lock until the finger passes the (larger) release radius.
        val held = lockedSocket
        if (held != null) {
            val pos = mapper.getSocketPosition(held)
            val dx = pos.x - boardPoint.x
            val dy = pos.y - boardPoint.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist <= releaseBoard) {
                lockStrength = (1f - dist / releaseBoard).coerceIn(0f, 1f)
                return held
            }
        }

        // 2. Nothing held (or the lock just broke): acquire the nearest socket in target range.
        val candidate = mapper.findClosestSocket(boardPoint, hitBoard)
        if (candidate == null) {
            lockedSocket = null
            lockStrength = 0f
            return null
        }

        val pos = mapper.getSocketPosition(candidate)
        val dx = pos.x - boardPoint.x
        val dy = pos.y - boardPoint.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)

        // Engage the magnet only inside the (smaller) lock radius; otherwise report the socket
        // without committing to it, so a wandering finger stays free.
        if (dist <= lockBoard) {
            lockedSocket = candidate
            lockStrength = (1f - dist / releaseBoard).coerceIn(0f, 1f)
        } else {
            lockedSocket = null
            lockStrength = 0f
        }
        return candidate
    }

    /** Board-space position of the locked socket, or null. */
    fun lockedPosition(): Offset? = lockedSocket?.let { mapper.getSocketPosition(it) }

    /**
     * Finds the wire under [boardPoint] using the shared screen-space wire tolerance.
     * Delegates the curve maths to [com.logiclabs.feature.breadboard.physics.BezierWireGeometry].
     */
    fun resolveWire(
        boardPoint: Offset,
        wires: List<JumperWire>,
        density: Float,
        scale: Float
    ): JumperWire? {
        val safeScale = if (scale <= 0f) 1f else scale
        return mapper.findClosestWire(
            boardPoint,
            wires,
            boardRadius(Dimens.WireHitRadius.value, density, safeScale)
        )
    }

    /** True when [boardPoint] is within grab range of an endpoint handle at [handlePos]. */
    fun isOnHandle(boardPoint: Offset, handlePos: Offset, density: Float, scale: Float): Boolean {
        val safeScale = if (scale <= 0f) 1f else scale
        val r = boardRadius(HANDLE_RADIUS_DP, density, safeScale)
        val dx = handlePos.x - boardPoint.x
        val dy = handlePos.y - boardPoint.y
        return dx * dx + dy * dy <= r * r
    }

    companion object {
        /**
         * The three radii that replace the seven ad-hoc board-space literals the gesture handler
         * used to carry (14 / 18 / 20 / 24 / 32 / 72 / 65–130). All are **screen** dp; convert
         * through [boardRadius].
         */
        /** Socket acquisition — half of [Dimens.MinTouchTarget], i.e. a 48dp target. */
        val SOCKET_RADIUS_DP = Dimens.SocketHitRadius.value

        /** Wire selection tolerance. */
        val WIRE_RADIUS_DP = Dimens.WireHitRadius.value

        /** Endpoint / chip drag-handle grab radius. */
        val HANDLE_RADIUS_DP = Dimens.MinTouchTarget.value / 2f

        /** Screen px a drag must cover before it counts as a drag rather than a tap. */
        val DRAG_SLOP_DP = 8f

        // --- Pitch caps (see resolve) ------------------------------------------------------------
        /** A socket only *acquires* from within 0.9 of a pitch — its own neighbourhood. */
        const val ACQUIRE_PITCH_FRACTION = 0.9f

        /** The magnet engages inside roughly half a pitch of the candidate centre. */
        const val LOCK_PITCH_FRACTION = 0.55f

        /** Hysteresis holds the lock out to ~1.7 pitches, then it breaks. */
        const val RELEASE_PITCH_FRACTION = 1.7f

        /**
         * dp → board units at the given zoom. Dividing by [scale] is what keeps the *screen*
         * target fixed: the board shrinks under zoom-out, so the board-space radius must grow.
         */
        fun boardRadius(dp: Float, density: Float, scale: Float): Float = dp * density / scale
    }
}
