package com.logiclabs.app.ui.sheets

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.designsystem.component.SheetDragHandle
import com.logiclabs.core.designsystem.component.SheetScaffold
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.feature.tools.oscilloscope.ScopePanel
import com.logiclabs.feature.tools.oscilloscope.rememberScopeSettings

/**
 * The oscilloscope, as a sheet.
 *
 * All of the instrument logic — the CRT, the controls, the probe pickers and the
 * `withFrameNanos` sweep loop that steps the circuit — lives in
 * [ScopePanel] in `:feature-tools`. This file is only the sheet frame, so the scope can
 * be hosted anywhere else without dragging sheet plumbing along.
 *
 * `ScopePanel` owns the sampling loop while it is composed, so the host must not also
 * drive `circuit.step()` for the scope's benefit; [onSample] is the hook for repainting
 * the rest of the workbench as samples land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeSheet(
    circuit: BreadboardCircuit,
    onDismiss: () -> Unit,
    onSample: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = rememberScopeSettings()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisBase,
        dragHandle = { SheetDragHandle() },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        SheetScaffold(
            title = "DUAL-TRACE OSCILLOSCOPE",
            subtitle = "8x10 graticule · phosphor CRT · live sweep",
            onDismiss = onDismiss
        ) {
            ScopePanel(
                circuit = circuit,
                settings = settings,
                modifier = Modifier
                    .fillMaxWidth()
                    // The controls plus two probe racks exceed a short screen; scroll
                    // rather than clip the pickers off the bottom.
                    .verticalScroll(rememberScrollState()),
                onSample = onSample
            )
        }
    }
}
