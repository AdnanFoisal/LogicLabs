package com.logiclabs.app.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.app.ui.component.DipPackageDiagram
import com.logiclabs.app.ui.component.dipPinRoleInfo
import com.logiclabs.app.ui.screens.home.buildIcChipSpec
import com.logiclabs.core.bridge.catalog.TTLChipCatalog
import com.logiclabs.core.bridge.model.PinDefinition
import com.logiclabs.core.bridge.model.PinRole
import com.logiclabs.core.designsystem.component.SheetDragHandle
import com.logiclabs.core.designsystem.component.SheetScaffold
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.BusGnd
import com.logiclabs.core.designsystem.theme.BusVcc
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * IC Pinout Inspector Sheet:
 * Shows a physical graphic of the DIP IC package, pinout breakdown table,
 * and internal logic gate functional schematics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IcPinoutInspectorSheet(
    partNumber: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val model = TTLChipCatalog.getInfo(partNumber) ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisBase,
        dragHandle = { SheetDragHandle() },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        val displayPart = if (partNumber.startsWith("74")) partNumber else "74$partNumber"
        SheetScaffold(
            title = "IC $displayPart PINOUT & SCHEMATIC",
            subtitle = "${model.description} · ${model.pinCount}-Pin Dual In-Line Package (DIP)",
            onDismiss = onDismiss
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space3)
            ) {
                // 1. Physical DIP Graphic
                item {
                    PhysicalDIPGraphic(
                        partNumber = model.partNumber,
                        pinCount = model.pinCount,
                        pins = model.pins
                    )
                }

                // 2. Logic Gate Schematic / Internal Structure
                item {
                    InternalSchematicCard(
                        partNumber = model.partNumber,
                        pins = model.pins
                    )
                }

                // 3. Pinout Breakdown Table
                item {
                    PinoutTableCard(
                        pinCount = model.pinCount,
                        pins = model.pins
                    )
                }
            }
        }
    }
}

/** Graphic of physical IC package (notch, pin 1 marker, pin numbers, signal names). */
@Composable
private fun PhysicalDIPGraphic(
    partNumber: String,
    pinCount: Int,
    pins: Map<Int, PinDefinition>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(SurfaceCard)
            .border(Dimens.Hairline, GlassHairline, RoundedCornerShape(Dimens.RadiusLg))
            .padding(Dimens.Space3),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PHYSICAL DIP-$pinCount PINOUT ORIENTATION",
            style = LogicLabsType.SwitchPlate,
            color = AccentCyan
        )
        Spacer(Modifier.height(Dimens.Space2))

        // The bench-accurate package: epoxy body, gull-wing leads, index notch,
        // pin-1 dimple, on-body pin numbers and role-coded names at the leads —
        // the same rendering language as the placed ICs on the breadboard.
        DipPackageDiagram(
            partNumber = partNumber,
            pinCount = pinCount,
            pinLabels = (1..pinCount).map { p -> pins[p]?.name ?: "$p" },
            pinRoles = (1..pinCount).map { p -> pins[p]?.role ?: PinRole.INPUT },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Internal Logic Gate Schematic / Functional Breakdown */
@Composable
private fun InternalSchematicCard(
    partNumber: String,
    pins: Map<Int, PinDefinition>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(SurfaceCard)
            .border(Dimens.Hairline, GlassHairline, RoundedCornerShape(Dimens.RadiusLg))
            .padding(Dimens.Space3)
    ) {
        Text(
            text = "INTERNAL LOGIC GATE SCHEMATIC",
            style = LogicLabsType.SwitchPlate,
            color = AccentCyan
        )
        Spacer(Modifier.height(Dimens.Space2))

        // Derived from the same TTLChipCatalog models the bench places, so the schematic can
        // never disagree with the reference tab or the sandbox — and every catalog part
        // (including the 7411 and 74266) gets a real gate breakdown instead of a shrug.
        val gates = remember(partNumber) {
            buildIcChipSpec(partNumber)?.gateMappings ?: emptyList()
        }

        if (gates.isEmpty()) {
            Text(
                text = "Consult manufacturer datasheet for custom pin mapping",
                style = LogicLabsType.TechnicalXs,
                color = TextSecondary
            )
        }

        gates.forEach { mapping ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mapping.title,
                        style = LogicLabsType.TechnicalSm.copy(fontWeight = FontWeight.Bold),
                        color = PhosphorCore
                    )
                    if (mapping.expression.isNotEmpty()) {
                        Text(
                            text = mapping.expression,
                            style = LogicLabsType.TechnicalXs,
                            color = TextSecondary
                        )
                    }
                }
                if (mapping.inputPins.isNotEmpty()) {
                    Text(
                        text = "IN  " + mapping.inputPins.joinToString(" · ") { "${it.name} (Pin ${it.pinNumber})" },
                        style = LogicLabsType.TechnicalXs,
                        color = TextPrimary
                    )
                }
                if (mapping.outputPins.isNotEmpty()) {
                    Text(
                        text = "OUT " + mapping.outputPins.joinToString(" · ") { "${it.name} (Pin ${it.pinNumber})" },
                        style = LogicLabsType.TechnicalXs,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/** Pinout Breakdown Table */
@Composable
private fun PinoutTableCard(
    pinCount: Int,
    pins: Map<Int, PinDefinition>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(SurfaceCard)
            .border(Dimens.Hairline, GlassHairline, RoundedCornerShape(Dimens.RadiusLg))
            .padding(Dimens.Space3)
    ) {
        Text(
            text = "PINOUT SPECIFICATION BREAKDOWN",
            style = LogicLabsType.SwitchPlate,
            color = AccentCyan
        )
        Spacer(Modifier.height(Dimens.Space2))

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ChassisBase)
                .padding(horizontal = Dimens.Space2, vertical = Dimens.Space1),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("PIN #", style = LogicLabsType.TechnicalXs, color = TextTertiary, modifier = Modifier.width(44.dp))
            Text("SIGNAL", style = LogicLabsType.TechnicalXs, color = TextTertiary, modifier = Modifier.width(60.dp))
            Text("ROLE", style = LogicLabsType.TechnicalXs, color = TextTertiary, modifier = Modifier.width(90.dp))
            Text("DESCRIPTION", style = LogicLabsType.TechnicalXs, color = TextTertiary, modifier = Modifier.weight(1f))
        }

        for (p in 1..pinCount) {
            val def = pins[p]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(Dimens.Hairline, SurfaceCardBorder)
                    .padding(horizontal = Dimens.Space2, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pin $p",
                    style = LogicLabsType.TechnicalXs.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    modifier = Modifier.width(44.dp)
                )
                Text(
                    text = def?.name ?: "-",
                    style = LogicLabsType.TechnicalSm.copy(fontWeight = FontWeight.Bold),
                    color = dipPinRoleInfo(def?.role ?: PinRole.INPUT).second,
                    modifier = Modifier.width(60.dp)
                )
                val roleLabel = when (def?.role) {
                    PinRole.INPUT -> "INPUT"
                    PinRole.OUTPUT -> "OUTPUT"
                    PinRole.POWER_VCC -> "+5V VCC"
                    PinRole.POWER_GND -> "0V GND"
                    PinRole.NO_CONNECT -> "NO CONNECT"
                    else -> "NC"
                }
                Text(
                    text = roleLabel,
                    style = LogicLabsType.TechnicalXs,
                    color = when (def?.role) {
                        PinRole.INPUT -> AccentCyan
                        PinRole.OUTPUT -> PhosphorCore
                        PinRole.POWER_VCC -> BusVcc
                        PinRole.POWER_GND -> BusGnd
                        else -> TextTertiary
                    },
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = when {
                        def?.role == PinRole.POWER_VCC -> "Primary +5.0V DC supply rail"
                        def?.role == PinRole.POWER_GND -> "Ground reference potential"
                        def?.isClock == true -> "Edge-triggered clock trigger input"
                        def?.isInverted == true -> "Active-LOW control input"
                        def?.role == PinRole.OUTPUT -> "Logic output driver"
                        def?.role == PinRole.INPUT -> "Digital logic input"
                        else -> "Unconnected internal terminal"
                    },
                    style = LogicLabsType.TechnicalXs,
                    color = TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
