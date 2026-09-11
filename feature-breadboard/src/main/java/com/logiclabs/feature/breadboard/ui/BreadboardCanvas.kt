package com.logiclabs.feature.breadboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.WireSpecular
import com.logiclabs.core.designsystem.theme.wireColorOf
import com.logiclabs.feature.breadboard.canvas.BoardCache
import com.logiclabs.feature.breadboard.canvas.BreadboardGeometryMapper
import com.logiclabs.feature.breadboard.canvas.DipPainter
import com.logiclabs.feature.breadboard.canvas.FaceplateRenderer
import com.logiclabs.feature.breadboard.canvas.WirePainter
import com.logiclabs.feature.breadboard.interaction.LoupeRenderer
import com.logiclabs.feature.breadboard.interaction.SocketHitTester
import com.logiclabs.feature.breadboard.physics.BezierWireGeometry

/**
 * The breadboard viewport: a pan/zoomable AD-200 faceplate with placed ICs and jumper wires.
 *
 * ### Structure
 * The body stays short on purpose. Gesture handling lives in [breadboardGestures], the floating
 * controls and context ribbons in `BreadboardOverlays.kt`, and all mutable viewport/draft state in
 * [BreadboardCanvasState]. That split is what keeps pan and zoom out of composition: `scale` and
 * `pan` are only ever read inside draw lambdas and pointer coroutines, so a pinch invalidates draw
 * and never re-runs this function or re-enters `circuit.step()`.
 *
 * ### Performance
 * The static faceplate is recorded into an `android.graphics.Picture` by [BoardCache] and replayed
 * as one draw op; only net highlights, live readouts, chips and wires are re-issued per frame.
 *
 * ### Fit to viewport
 * The board is measured against the real viewport via [BoxWithConstraints] and fitted on first
 * layout, so it no longer renders ~44% off-screen at a hardcoded 1.0x.
 */
@Composable
fun BreadboardCanvas(
    circuit: BreadboardCircuit,
    modifier: Modifier = Modifier,
    activeWireColor: WireColor = WireColor.RED,
    defaultWireManhattan: Boolean = false,
    isWireModeActive: Boolean = false,
    isWireSelectMode: Boolean = false,
    selectedWireId: String? = null,
    selectedChipId: String? = null,
    circuitVersion: Int = 0,
    renderVersion: Int = 0,
    resetFitTrigger: Int = 0,
    onWireSelected: ((String?) -> Unit)? = null,
    onChipSelected: ((String?) -> Unit)? = null,
    onDeleteEquipment: (() -> Unit)? = null,
    onToggleWireMode: (() -> Unit)? = null,
    onSocketSelected: ((Int) -> Unit)? = null,
    onCircuitChanged: (() -> Unit)? = null
) {
    val density = LocalDensity.current.density
    val mapper = remember { BreadboardGeometryMapper() }
    val state = remember { BreadboardCanvasState() }
    val hitTester = remember(mapper) { SocketHitTester(mapper) }
    val boardCache = remember { BoardCache() }
    val guidePath = remember { Path() }

    val onWire by rememberUpdatedState(onWireSelected)
    val onChip by rememberUpdatedState(onChipSelected)
    val onChanged by rememberUpdatedState(onCircuitChanged)
    val onSocket by rememberUpdatedState(onSocketSelected)

    val callbacks = remember {
        BreadboardGestureCallbacks(
            onWireSelected = { onWire?.invoke(it) },
            onChipSelected = { onChip?.invoke(it) },
            onCircuitChanged = { onChanged?.invoke() },
            onSocketSelected = { onSocket?.invoke(it) }
        )
    }

    DisposableEffect(Unit) { onDispose { boardCache.release() } }

    // Highlights are recomputed on discrete changes only, never per frame.
    LaunchedEffect(selectedWireId, circuitVersion) {
        state.recomputeHighlights(mapper, circuit, selectedWireId)
    }
    LaunchedEffect(isWireSelectMode, isWireModeActive) {
        state.clearDraft()
        state.recomputeHighlights(mapper, circuit, selectedWireId)
    }
    LaunchedEffect(resetFitTrigger) {
        if (resetFitTrigger > 0) {
            state.resetFit(circuit)
        }
    }

    state.renderVersion.value = renderVersion
    state.circuitVersion.value = circuitVersion

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val vw = with(LocalDensity.current) { maxWidth.toPx() }
        val vh = with(LocalDensity.current) { maxHeight.toPx() }
        LaunchedEffect(vw, vh) { state.onViewportMeasured(Size(vw, vh), mapper, circuit) }

        val hasActiveExplosion = remember(circuit.chipExplosionTimestamps.size, state.renderVersion.value) {
            val now = System.currentTimeMillis()
            circuit.chipExplosionTimestamps.values.any { now - it < 1600L }
        }
        var explosionTick by remember { mutableStateOf(0L) }
        if (hasActiveExplosion) {
            LaunchedEffect(hasActiveExplosion, circuit.chipExplosionTimestamps.size) {
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < 1600L) {
                    withFrameMillis { frameTime -> explosionTick = frameTime }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The draw scope transforms content outside its own bounds; without a
                // clip a zoomed-in board painted over the context ribbon above and the
                // console dock below the canvas pane.
                .clipToBounds()
                .breadboardGestures(
                    state = state,
                    mapper = mapper,
                    hitTester = hitTester,
                    circuit = circuit,
                    density = density,
                    activeWireColor = activeWireColor,
                    defaultWireManhattan = defaultWireManhattan,
                    isWireModeActive = isWireModeActive,
                    isWireSelectMode = isWireSelectMode,
                    selectedWireId = selectedWireId,
                    selectedChipId = selectedChipId,
                    callbacks = callbacks
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") state.circuitVersion.value // reactive redraw key for topology
                @Suppress("UNUSED_EXPRESSION") state.renderVersion.value // reactive redraw key for switches, LEDs, and power
                @Suppress("UNUSED_EXPRESSION") explosionTick // continuous redraw key during IC explosion
                val scale = state.scale.value
                val pan = state.pan.value
                val viewport = visibleBoardRect(size, pan, scale)

                withTransform({
                    translate(pan.x, pan.y)
                    scale(scale, scale, Offset.Zero)
                }) {
                    boardCache.drawStatic(this, mapper, density, scale)
                    FaceplateRenderer.drawDynamic(
                        scope = this,
                        mapper = mapper,
                        circuit = circuit,
                        density = density,
                        zoomScale = scale,
                        highlightSockets = state.highlights.value,
                        viewport = viewport
                    )

                    for (chip in circuit.placedChips) {
                        DipPainter.draw(
                            scope = this,
                            mapper = mapper,
                            chip = chip,
                            density = density,
                            zoomScale = scale,
                            isBurned = chip.placedIc.id in circuit.burnedChipIds ||
                                (circuit.isReversePolarityBurned && circuit.burnedChipId == chip.placedIc.id),
                            isSelected = chip.placedIc.id == selectedChipId,
                            explosionTimestamp = circuit.chipExplosionTimestamps[chip.placedIc.id] ?: 0L
                        )
                    }

                    WirePainter.draw(this, mapper, circuit, selectedWireId, viewport)
                    drawDraftWire(mapper, state, guidePath, activeWireColor)
                }

                LoupeRenderer.draw(
                    scope = this,
                    state = state.loupe.value,
                    mapper = mapper,
                    circuit = circuit,
                    boardCache = boardCache,
                    pan = pan,
                    scale = scale,
                    density = density,
                    selectedChipId = selectedChipId
                )
            }

            TapToConnectHint(
                visible = isWireModeActive && !isWireSelectMode &&
                    state.draftAnchor.value != null && state.draftFinger.value == null &&
                    selectedWireId == null && selectedChipId == null,
                armedLabel = LoupeRenderer.coordinateLabel(state.draftAnchor.value)
            )

            EmptyBenchHint(
                visible = circuit.placedChips.isEmpty() && circuit.wires.isEmpty() &&
                    circuit.passives.isEmpty()
            )

            BreadboardControls(
                state = state,
                mapper = mapper,
                circuit = circuit,
                isWireModeActive = isWireModeActive,
                hasSelection = selectedWireId != null || selectedChipId != null,
                onDeleteEquipment = onDeleteEquipment,
                onToggleWireMode = onToggleWireMode
            )
        }
    }
}

/** Board-space rect currently visible, used to cull everything outside the screen. */
private fun visibleBoardRect(canvas: Size, pan: Offset, scale: Float): Rect {
    val s = if (scale <= 0f) 1f else scale
    return Rect(
        left = -pan.x / s,
        top = -pan.y / s,
        right = (canvas.width - pan.x) / s,
        bottom = (canvas.height - pan.y) / s
    )
}

/**
 * Draws the in-progress connection: the armed anchor ring plus, while dragging, a magnetic guide
 * wire that flexes toward the finger and pulls taut into the hysteresis-locked socket.
 *
 * The old rubber band was gated on a field the drag path never set, so dragging drew nothing.
 */
private fun DrawScope.drawDraftWire(
    mapper: BreadboardGeometryMapper,
    state: BreadboardCanvasState,
    guidePath: Path,
    color: WireColor
) {
    val anchor = state.draftAnchor.value ?: return
    val anchorPos = mapper.getSocketPosition(anchor)

    // Armed anchor: a ring the user can see before the second tap.
    drawCircle(color = AccentCyan, radius = 11f, center = anchorPos, style = Stroke(width = 2.5f))
    drawCircle(color = Color.White, radius = 4f, center = anchorPos)

    val finger = state.draftFinger.value ?: return
    val locked = state.draftLocked.value
    val lockPos = if (locked != null) mapper.getSocketPosition(locked) else finger
    val strength = if (locked == null) 0f else state.draftLockStrength.value

    guidePath.reset()
    BezierWireGeometry.appendMagneticGuide(
        path = guidePath,
        sx = anchorPos.x, sy = anchorPos.y,
        fx = finger.x, fy = finger.y,
        tx = lockPos.x, ty = lockPos.y,
        lockStrength = strength
    )

    // Deliberately not spring-animated. `lockStrength` is `1 - dist/releaseRadius`,
    // recomputed on every pointer move, so the pull toward the socket already ramps
    // continuously off the finger's real position — which is what a magnet does. Routing
    // it through a time-based spec would decouple the flex from the finger and add lag
    // mid-drag. There is no release to animate either: when the lock breaks, clearDraft()
    // nulls the anchor and the guide wire is gone in the same frame.
    drawPath(guidePath, color = wireColorOf(color.hexArgb), style = Stroke(5f, cap = StrokeCap.Round))
    drawPath(guidePath, color = WireSpecular, style = Stroke(1.4f, cap = StrokeCap.Round))

    if (locked != null) {
        // Ring opacity tracks the magnet: a merely-hovered candidate reads dim, and the
        // ring brightens to full as the lock firms — one more "which hole will connect"
        // signal alongside the loupe and the coordinate readout.
        drawCircle(
            color = AccentCyan.copy(alpha = 0.35f + 0.65f * strength),
            radius = 10f + 4f * strength,
            center = lockPos,
            style = Stroke(width = 2f)
        )
    }
}
