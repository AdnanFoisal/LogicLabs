package com.logiclabs.feature.tools.diagram

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.designsystem.component.SheetDragHandle
import com.logiclabs.core.designsystem.component.SheetScaffold
import com.logiclabs.core.designsystem.theme.ChassisBase
import kotlinx.coroutines.isActive

/**
 * The boolean logic diagram as a bench modal sheet.
 *
 * A thin sheet frame in the house style (ModalBottomSheet + [SheetScaffold] +
 * [SheetDragHandle], matching `ScopeSheet`) around [BooleanDiagramView], which
 * owns all of the derivation, rendering and expression logic.
 *
 * ## Wiring for integration
 *
 * ```kotlin
 * Overlay.BOOLEAN_DIAGRAM -> BooleanDiagramSheet(
 *     circuit = circuit,
 *     onDismiss = { bench.overlay = Overlay.NONE }
 * )
 * ```
 *
 * The sheet is modal, so the topology cannot change while it is open and no
 * `circuitVersion` is needed. Live repaints (switch flips, clock edges, LED
 * states) are driven internally by a frame ticker whose tick is gated on a
 * cheap signature of the engine state — it goes quiet when nothing changes,
 * so an idle sheet costs no recompositions. Hosts that keep the bench stepping
 * do not need to do anything else; hosts that want the same liveness in a
 * non-modal context should call [BooleanDiagramView] directly and pass their
 * `renderVersion`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooleanDiagramSheet(
    circuit: BreadboardCircuit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // The engine is not snapshot-observable; bump a render stamp only when a
    // cheap signature of its live state actually changes.
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(circuit) {
        var lastSig = Int.MIN_VALUE
        while (isActive) {
            withFrameNanos {
                val sig = liveSignature(circuit)
                if (sig != lastSig) {
                    lastSig = sig
                    frame++
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisBase,
        dragHandle = { SheetDragHandle() },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        SheetScaffold(
            title = "BOOLEAN LOGIC DIAGRAM",
            subtitle = "gate-level equivalent of the breadboard circuit",
            onDismiss = onDismiss
        ) {
            BooleanDiagramView(
                circuit = circuit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 480.dp),
                renderVersion = frame
            )
        }
    }
}

/**
 * Cheap change signature over the engine's live outputs: packed switches,
 * packed LEDs, clock/pulser state, net-level map size and hash. Only net
 * level changes cost more than a couple of arithmetic ops, and the map is a
 * few dozen entries at most.
 */
private fun liveSignature(circuit: BreadboardCircuit): Int {
    var sig = if (circuit.masterPower) 1 else 0
    for (b in circuit.switches) sig = sig * 2 + if (b) 1 else 0
    for (b in circuit.ledValues) sig = sig * 2 + if (b) 1 else 0
    sig = sig * 3 + if (circuit.clockRunning) 1 else 0
    sig = sig * 3 + if (circuit.clockState) 1 else 0
    sig = sig * 3 + if (circuit.pulserAPressed) 1 else 0
    sig = sig * 3 + if (circuit.pulserBPressed) 1 else 0
    sig = sig * 31 + circuit.netLevels.size
    sig = sig * 31 + circuit.netLevels.values.hashCode()
    return sig
}
