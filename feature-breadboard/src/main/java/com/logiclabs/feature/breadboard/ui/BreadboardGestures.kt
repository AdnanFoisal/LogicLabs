package com.logiclabs.feature.breadboard.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.feature.breadboard.canvas.BreadboardGeometryMapper
import com.logiclabs.feature.breadboard.interaction.LoupeState
import com.logiclabs.feature.breadboard.interaction.SocketHitTester
import kotlin.math.roundToInt

/**
 * Callbacks the gesture layer raises. Held in one object so the modifier's `pointerInput` key list
 * stays short and the hot path never captures the composable's scope.
 */
class BreadboardGestureCallbacks(
    val onWireSelected: (String?) -> Unit,
    val onChipSelected: (String?) -> Unit,
    val onCircuitChanged: () -> Unit,
    val onSocketSelected: (Int) -> Unit
)

/**
 * Installs breadboard pointer handling: pan, pinch-about-centroid, wire draw, wire-endpoint drag,
 * chip drag and tap selection.
 *
 * Every touch radius comes from [SocketHitTester], which converts dp through the current zoom, so
 * the seven ad-hoc board-space literals the old handler carried (14 / 18 / 20 / 24 / 32 / 72 /
 * 65–130) are gone and a socket is a constant 48dp target at every zoom level.
 *
 * @param density `LocalDensity.current.density`, captured by the pointer coroutine.
 */
fun Modifier.breadboardGestures(
    state: BreadboardCanvasState,
    mapper: BreadboardGeometryMapper,
    hitTester: SocketHitTester,
    circuit: BreadboardCircuit,
    density: Float,
    activeWireColor: WireColor,
    defaultWireManhattan: Boolean,
    isWireModeActive: Boolean,
    isWireSelectMode: Boolean,
    selectedWireId: String?,
    selectedChipId: String?,
    callbacks: BreadboardGestureCallbacks
): Modifier = pointerInput(
    activeWireColor, defaultWireManhattan, isWireModeActive, isWireSelectMode, selectedWireId, selectedChipId, density
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val scaleAtDown = state.scale.value
        val boardDown = state.toBoard(down.position)
        val dragSlop = SocketHitTester.DRAG_SLOP_DP * density

        hitTester.reset()

        // --- Hit test once, at the down event -------------------------------------------------
        val selWire = if (selectedWireId != null) {
            circuit.wires.firstOrNull { it.id == selectedWireId }
        } else null

        var handleIndex = -1 // 0 = start endpoint, 1 = end endpoint
        if (selWire != null) {
            val s = mapper.getSocketPosition(selWire.startSocket)
            val e = mapper.getSocketPosition(selWire.endSocket)
            handleIndex = when {
                hitTester.isOnHandle(boardDown, s, density, scaleAtDown) -> 0
                hitTester.isOnHandle(boardDown, e, density, scaleAtDown) -> 1
                else -> -1
            }
        }

        val hitWire = hitTester.resolveWire(boardDown, circuit.wires, density, scaleAtDown)
        val hitChip = mapper.findClosestChip(boardDown, circuit.placedChips)
        hitTester.reset()
        val hitSocket = hitTester.resolve(boardDown, density, scaleAtDown)
        hitTester.reset()

        val wireDrawing = isWireModeActive && !isWireSelectMode

        when {
            // In wire mode a socket always wins. The old dispatch required
            // `hitWire == null`, so any wire passing near the target hole — a second
            // jumper onto a switch terminal, a hole beside an existing wire boot —
            // hijacked the gesture into pan/select, which is exactly "I can't pick the
            // hole / holes don't select". Selecting wires belongs to SELECT mode.
            wireDrawing && hitSocket != null ->
                drawWire(
                    state, mapper, hitTester, circuit, density, down, hitSocket,
                    activeWireColor, defaultWireManhattan, dragSlop, selectedWireId, callbacks
                )

            // Endpoint handles are a SELECT/ROAM affordance. In wire mode their 24dp grab
            // radius around a wire's holes hijacked presses meant for a *new* wire from
            // the same hole into an endpoint drag — the "one hole, one wire" wall.
            handleIndex >= 0 && selWire != null && !wireDrawing ->
                dragEndpoint(state, mapper, hitTester, circuit, density, down, selWire, handleIndex, callbacks)

            hitChip != null && selectedChipId == hitChip.placedIc.id ->
                dragChip(state, mapper, circuit, down, hitChip, dragSlop, callbacks)

            else -> panZoomOrSelect(
                state, mapper, hitTester, circuit, density, down, boardDown,
                hitWire, hitChip, isWireModeActive, isWireSelectMode, selectedWireId,
                selectedChipId, dragSlop, activeWireColor, defaultWireManhattan, callbacks
            )
        }
    }
}

/** True when the event has enough pointers to be a pinch. Returns the applied zoom or null. */
private fun PointerEvent.asPinch(state: BreadboardCanvasState): Boolean {
    if (changes.size <= 1) return false
    val centroid = calculateCentroid(useCurrent = false)
    if (centroid == Offset.Unspecified) return false
    state.applyPinch(calculateZoom(), calculatePan(), centroid)
    changes.forEach { it.consume() }
    return true
}

// ==============================================================================================
// Wire endpoint drag
// ==============================================================================================

private suspend fun AwaitPointerEventScope.dragEndpoint(
    state: BreadboardCanvasState,
    mapper: BreadboardGeometryMapper,
    hitTester: SocketHitTester,
    circuit: BreadboardCircuit,
    density: Float,
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    wire: com.logiclabs.core.bridge.model.JumperWire,
    handleIndex: Int,
    callbacks: BreadboardGestureCallbacks
) {
    val anchorSocket = if (handleIndex == 0) wire.endSocket else wire.startSocket
    val movingSocket = if (handleIndex == 0) wire.startSocket else wire.endSocket
    state.draftAnchor.value = anchorSocket
    state.draftLocked.value = movingSocket
    state.loupe.value = LoupeState(
        isVisible = true,
        touchPosition = down.position,
        snappedSocket = movingSocket
    )

    var snapped: Int? = movingSocket
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) break
        change.consume()

        val board = state.toBoard(change.position)
        snapped = hitTester.resolve(board, density, state.scale.value) ?: snapped
        state.draftFinger.value = board
        state.draftLocked.value = snapped
        state.draftLockStrength.value = hitTester.lockStrength
        state.loupe.value = state.loupe.value.copy(
            touchPosition = change.position,
            snappedSocket = snapped
        )
    }

    state.hideLoupe()
    state.clearDraft()
    hitTester.reset()

    val target = snapped
    if (target != null) {
        val newStart = if (handleIndex == 0) target else wire.startSocket
        val newEnd = if (handleIndex == 1) target else wire.endSocket
        if (newStart != newEnd) {
            circuit.updateWireEndpoints(wire.id, newStart, newEnd)
            circuit.step()
            callbacks.onCircuitChanged()
        }
    }
    state.recomputeHighlights(mapper, circuit, wire.id)
}

// ==============================================================================================
// Chip drag
// ==============================================================================================

private suspend fun AwaitPointerEventScope.dragChip(
    state: BreadboardCanvasState,
    mapper: BreadboardGeometryMapper,
    circuit: BreadboardCircuit,
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    chip: BreadboardCircuit.PlacedChipRuntime,
    dragSlop: Float,
    callbacks: BreadboardGestureCallbacks
) {
    var travelled = 0f
    var moved = false
    while (true) {
        val event = awaitPointerEvent()
        if (event.asPinch(state)) break
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) break
        change.consume()
        travelled += (change.position - change.previousPosition).getDistance()
        if (travelled <= dragSlop) continue

        val board = state.toBoard(change.position)
        // roundToInt, not toInt: truncation biased every drag half a column to the left and made
        // the chip lag the finger by up to a full pitch.
        val targetCol = ((board.x - mapper.originX) / mapper.socketPitch).roundToInt()
        // Vertical motion now picks the trench, so a chip can be dragged between the two banks.
        val targetTrench = nearestTrench(mapper, board.y)
        if (targetCol != chip.placedIc.startColumn || targetTrench != chip.placedIc.trench) {
            if (circuit.moveChip(chip.placedIc.id, targetTrench, targetCol)) moved = true
        }
    }
    if (moved) {
        circuit.step()
        callbacks.onCircuitChanged()
    }
}

/** Trench 1 spans blocks 0/1, trench 2 spans blocks 2/3; pick whichever centre is nearer. */
private fun nearestTrench(mapper: BreadboardGeometryMapper, boardY: Float): Int {
    val t1 = mapper.originY + 5 * mapper.socketPitch + mapper.blockGap / 2f
    val t2 = mapper.originY + 15 * mapper.socketPitch + mapper.blockGap + mapper.sectionGap +
        mapper.blockGap / 2f
    return if (kotlin.math.abs(boardY - t1) <= kotlin.math.abs(boardY - t2)) 1 else 2
}

// ==============================================================================================
// Wire drawing
// ==============================================================================================

private suspend fun AwaitPointerEventScope.drawWire(
    state: BreadboardCanvasState,
    mapper: BreadboardGeometryMapper,
    hitTester: SocketHitTester,
    circuit: BreadboardCircuit,
    density: Float,
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    startSocket: Int,
    color: WireColor,
    manhattanDefault: Boolean,
    dragSlop: Float,
    selectedWireId: String?,
    callbacks: BreadboardGestureCallbacks
) {
    // Capture the PREVIOUS anchor before this gesture overwrites it — handleWireModeTap
    // needs it to tell "first tap arms" from "second tap completes". Reading it back from
    // state after the assignment below always returned the current socket, which turned
    // every tap into a disarm and broke tap-to-connect entirely.
    val previousAnchor = state.draftAnchor.value

    // The draft anchor is set here — the old code only ever set `wireStartSocket` on the
    // tap-to-connect branch, which is why the drag-to-connect rubber band drew nothing at all.
    state.draftAnchor.value = startSocket
    state.draftFinger.value = state.toBoard(down.position)
    state.draftLocked.value = startSocket
    state.draftLockStrength.value = 1f
    state.recomputeHighlights(mapper, circuit, selectedWireId)
    state.loupe.value = LoupeState(
        isVisible = true,
        touchPosition = down.position,
        snappedSocket = startSocket
    )

    var travelled = 0f
    var snapped: Int? = startSocket
    var lastSnapped: Int? = startSocket

    while (true) {
        val event = awaitPointerEvent()

        if (event.changes.size > 1) {
            // A second finger no longer aborts the draft. "Arm, pinch to zoom, then land
            // the endpoint" is the natural precision flow at low zoom, and silently
            // discarding the in-progress wire on an accidental palm graze is what read
            // as wires vanishing mid-gesture. The pinch zooms the viewport; the draft
            // keeps tracking the original pointer.
            event.asPinch(state)
            continue
        }

        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) break
        change.consume()

        travelled += (change.position - change.previousPosition).getDistance()
        val board = state.toBoard(change.position)
        snapped = hitTester.resolve(board, density, state.scale.value)
        state.draftFinger.value = board
        state.draftLocked.value = snapped
        state.draftLockStrength.value = if (snapped == null) 0f else hitTester.lockStrength
        state.loupe.value = state.loupe.value.copy(
            touchPosition = change.position,
            snappedSocket = snapped
        )
        // Tactile confirmation whenever the magnet lands on a different hole: the user
        // feels each candidate engage instead of discovering the choice after release.
        if (snapped != lastSnapped) {
            lastSnapped = snapped
            if (snapped != null) callbacks.onSocketSelected(snapped)
        }
    }

    state.hideLoupe()
    hitTester.reset()

    // A tremor-sized drag is still a tap — 8dp of slop is tighter than most fingers can
    // hold still, and routing a jittery release into the drag branch made taps that
    // "didn't select anything".
    val tapLike = travelled <= dragSlop * 3f

    if (tapLike) {
        // Tap-to-connect: first tap arms, second completes (or re-tap disarms).
        handleWireModeTap(state, circuit, callbacks, startSocket, previousAnchor, color, manhattanDefault)
    } else {
        // Drag-to-connect.
        val end = snapped
        if (end != null && end != startSocket) {
            state.clearDraft()
            val wire = circuit.addWire(startSocket, end, color, manhattanDefault)
            circuit.step()
            // Deliberately NOT auto-selecting the new wire: selection draws 24dp endpoint
            // handles around its holes, and in wire mode those handles hijacked the next
            // press near the same hole into an endpoint drag — the "one hole, one wire"
            // wall. Wires are selected in SELECT mode or by tapping them in ROAM.
            callbacks.onCircuitChanged()
        } else {
            // Released on nothing (or back on the start hole): keep the start armed
            // instead of silently discarding it, so the user can zoom in and finish
            // the connection from where the finger left off.
            state.draftAnchor.value = startSocket
            state.draftFinger.value = null
            state.draftLocked.value = null
            state.draftLockStrength.value = 0f
            callbacks.onSocketSelected(startSocket)
        }
    }
    state.recomputeHighlights(mapper, circuit, selectedWireId)
}

/**
 * Tap-to-connect state machine shared by the direct tap path ([drawWire]) and the
 * drift-tap path ([panZoomOrSelect]): first tap arms the anchor, tapping the same hole
 * disarms, tapping a second hole completes the wire.
 *
 * [previousAnchor] is passed in (not re-read from state) because [drawWire] has already
 * overwritten the anchor with the current socket by the time this runs.
 */
private fun handleWireModeTap(
    state: BreadboardCanvasState,
    circuit: BreadboardCircuit,
    callbacks: BreadboardGestureCallbacks,
    socket: Int,
    previousAnchor: Int?,
    color: WireColor,
    manhattanDefault: Boolean
) {
    when {
        previousAnchor == null -> {
            state.draftAnchor.value = socket
            state.draftFinger.value = null
            state.draftLocked.value = null
            state.draftLockStrength.value = 0f
            callbacks.onSocketSelected(socket)
        }
        previousAnchor == socket -> {
            state.clearDraft()
        }
        else -> {
            state.clearDraft()
            circuit.addWire(previousAnchor, socket, color, manhattanDefault)
            circuit.step()
            // No auto-select: endpoint handles around the new wire's holes would hijack
            // the next press near either endpoint in wire mode (see drawWire).
            callbacks.onCircuitChanged()
        }
    }
}

// ==============================================================================================
// Pan / pinch / tap selection
// ==============================================================================================

private suspend fun AwaitPointerEventScope.panZoomOrSelect(
    state: BreadboardCanvasState,
    mapper: BreadboardGeometryMapper,
    hitTester: SocketHitTester,
    circuit: BreadboardCircuit,
    density: Float,
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    boardDown: Offset,
    hitWire: com.logiclabs.core.bridge.model.JumperWire?,
    hitChip: BreadboardCircuit.PlacedChipRuntime?,
    isWireModeActive: Boolean,
    isWireSelectMode: Boolean,
    selectedWireId: String?,
    selectedChipId: String?,
    dragSlop: Float,
    activeWireColor: WireColor,
    defaultWireManhattan: Boolean,
    callbacks: BreadboardGestureCallbacks
) {
    var lastPos = down.position
    var travelled = 0f
    var pinched = false

    while (true) {
        val event = awaitPointerEvent()
        event.changes.firstOrNull()?.let { lastPos = it.position }

        if (event.changes.all { !it.pressed }) break

        if (event.asPinch(state)) {
            pinched = true
            travelled += dragSlop * 4f
            continue
        }

        val change = event.changes.firstOrNull()
        if (change != null && change.pressed && change.positionChanged()) {
            val delta = change.position - change.previousPosition
            state.panBy(delta)
            travelled += delta.getDistance()
            change.consume()
        }
    }

    val net = (lastPos - down.position).getDistance()
    if (pinched || (net >= dragSlop * 4f && travelled >= dragSlop * 4f)) return

    // --- Tap: resolve what was under the finger, preferring wires, then chips ------------------
    val boardUp = state.toBoard(lastPos)
    val scale = state.scale.value

    val tappedSocket = hitTester.resolve(boardDown, density, scale)
        ?: hitTester.resolve(boardUp, density, scale)
    hitTester.reset()

    // In wire mode a tap that drifted off its down-point socket (or landed on a socket
    // the down-test missed) still arms/connects the socket — the old resolution
    // preferred wires and chip bodies, so a light tap near a wired hole selected the
    // wire instead of starting a new one.
    if (isWireModeActive && !isWireSelectMode && tappedSocket != null) {
        handleWireModeTap(
            state, circuit, callbacks, tappedSocket,
            state.draftAnchor.value, activeWireColor, defaultWireManhattan
        )
        state.recomputeHighlights(mapper, circuit, selectedWireId)
        return
    }

    val wireAtSocket = if (tappedSocket != null) {
        val net2 = mapper.getConnectedColumnSockets(tappedSocket)
        circuit.wires.firstOrNull { it.startSocket in net2 || it.endSocket in net2 }
    } else null

    val candidate = hitWire
        ?: hitTester.resolveWire(boardUp, circuit.wires, density, scale)
        ?: wireAtSocket

    when {
        candidate != null ->
            callbacks.onWireSelected(if (selectedWireId == candidate.id) null else candidate.id)

        !isWireSelectMode && hitChip != null -> {
            callbacks.onChipSelected(
                if (selectedChipId == hitChip.placedIc.id) null else hitChip.placedIc.id
            )
            callbacks.onWireSelected(null)
        }

        else -> {
            callbacks.onWireSelected(null)
            callbacks.onChipSelected(null)
        }
    }
    state.clearDraft()
    state.recomputeHighlights(mapper, circuit, selectedWireId)
}
