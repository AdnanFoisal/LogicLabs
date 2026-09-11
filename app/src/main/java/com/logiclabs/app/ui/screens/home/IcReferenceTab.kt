package com.logiclabs.app.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.bridge.catalog.ChipModel
import com.logiclabs.core.bridge.catalog.TTLChipCatalog
import com.logiclabs.app.ui.component.DipPackageDiagram
import com.logiclabs.core.bridge.model.PinRole
import com.logiclabs.core.designsystem.component.InstantSearchBar
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.tools.feedback.Haptics

/**
 * One physical pin of one internal gate, resolved from the catalog model.
 *
 * Pin numbers never appear as literals here — they are read off the [ChipModel] the bench
 * itself places, so a reference row can never disagree with the sandbox.
 */
data class GatePin(
    val pinNumber: Int,
    val name: String
)

/**
 * One internal gate (or functional stage) of a package: which pins feed it, which pins it
 * drives, and the Boolean expression it implements.
 */
data class GateMapping(
    val title: String,
    val inputPins: List<GatePin>,
    val outputPins: List<GatePin>,
    val expression: String = ""
) {
    val inputs: String get() = inputPins.joinToString(", ") { "Pin ${it.pinNumber} (${it.name})" }
    val output: String get() = outputPins.joinToString(", ") { "Pin ${it.pinNumber} (${it.name})" }
}

data class IcChipSpec(
    val partNumber: String,
    val name: String,
    val category: String,
    val pinCount: Int,
    val description: String,
    val pinLabels: List<String>,
    val pinRoles: List<PinRole> = emptyList(),
    val propDelayNs: Int = 10,
    val fanOut: Int = 10,
    val gateMappings: List<GateMapping> = emptyList()
)

/** Display-only metadata keyed by part number. Everything electrical comes from the catalog. */
private data class IcDisplayInfo(
    val category: String,
    val gateNoun: String,
    val gateType: String,
    val expression: String,
    val propDelayNs: Int
)

private val IC_DISPLAY_INFO: Map<String, IcDisplayInfo> = mapOf(
    "7400" to IcDisplayInfo("Universal Gates", "Gate", "NAND", "Y = (A·B)′", 10),
    "7402" to IcDisplayInfo("Universal Gates", "Gate", "NOR", "Y = (A+B)′", 10),
    "7404" to IcDisplayInfo("Basic Logic Gates", "Inverter", "NOT", "Y = A′", 10),
    "7408" to IcDisplayInfo("Basic Logic Gates", "Gate", "AND", "Y = A·B", 10),
    "7410" to IcDisplayInfo("Multi-Input Gates", "Gate", "NAND-3", "Y = (A·B·C)′", 10),
    "7411" to IcDisplayInfo("Multi-Input Gates", "Gate", "AND-3", "Y = A·B·C", 10),
    "7420" to IcDisplayInfo("Multi-Input Gates", "Gate", "NAND-4", "Y = (A·B·C·D)′", 10),
    "7432" to IcDisplayInfo("Basic Logic Gates", "Gate", "OR", "Y = A+B", 10),
    "7486" to IcDisplayInfo("Arithmetic & Parity", "Gate", "XOR", "Y = A⊕B", 10),
    "74266" to IcDisplayInfo("Arithmetic & Parity", "Gate", "XNOR", "Y = A⊙B", 18),
    "7483" to IcDisplayInfo("Arithmetic Circuits", "Stage", "ADDER", "Σ = A + B + C0", 16),
    "7474" to IcDisplayInfo("Sequential Logic", "Flip-Flop", "D-FF", "Q = D on ↑CLK", 15),
    "7476" to IcDisplayInfo("Sequential Logic", "Flip-Flop", "JK-FF", "Q+ = f(J,K) on ↓CLK", 15),
    "7448" to IcDisplayInfo("Display Decoding", "Decoder", "BCD→7SEG", "a..g = decode(D..A)", 17)
)

/** Longer prose descriptions, shown on the detail card. Order and pinouts come from the catalog. */
private val IC_PROSE: Map<String, String> = mapOf(
    "7400" to "Contains four independent universal NAND gates. Functionally complete — any Boolean function can be built using 7400 gates alone.",
    "7402" to "Contains four independent universal NOR gates. Output Y is HIGH only when both inputs A and B are simultaneously LOW. Note the inverted pinout: outputs sit at Pins 1, 4, 10 and 13.",
    "7404" to "Contains six independent inverters. Inverts input signals: output Y = A′. Ideal for complement generation and level restoration.",
    "7408" to "Contains four independent 2-input AND gates. Each gate performs the Boolean logical product operation Y = A · B.",
    "7410" to "Contains three independent 3-input NAND gates. Gate 1 straddles the package: its inputs are Pins 1, 2 and 13 and its output returns on Pin 12.",
    "7411" to "Contains three independent 3-input AND gates. Same package layout as the 7410, outputs simply not inverted, so Gate 1 again reads Pins 1, 2, 13 and drives Pin 12.",
    "7420" to "Contains two independent 4-input NAND gates. Pins 3 and 11 are internally not connected (NC). A single 7420 gate detects one of sixteen input combinations.",
    "7432" to "Contains four independent 2-input OR gates. Each gate performs the Boolean logical sum operation Y = A + B.",
    "7486" to "Contains four independent Exclusive-OR gates. Performs modulo-2 binary addition and odd-parity detection.",
    "74266" to "Contains four independent 2-input XNOR (equality) gates. Outputs are HIGH when both inputs match. Real '266 parts are open-collector; this model drives totem-pole. Mind the pinout: gates 2 and 3 are mirrored relative to a 7486.",
    "7483" to "High-speed 4-bit parallel binary adder with internal fast carry. Computes binary sum and carry outputs for two 4-bit words. Supply is on Pins 5 (VCC) and 12 (GND), not the corners.",
    "7474" to "Contains two independent D-type positive-edge-triggered flip-flops with asynchronous active-low preset and clear inputs.",
    "7476" to "Contains two independent negative-edge-triggered J-K flip-flops with active-low preset and clear. Supply is on Pins 5 (VCC) and 13 (GND).",
    "7448" to "BCD-to-seven-segment decoder/driver with active-HIGH segment outputs for common-cathode displays, plus lamp-test, blanking and ripple-blanking controls."
)

/** Builds the reference entry for one part directly from the TTL catalog the bench places. */
internal fun buildIcChipSpec(partNumber: String): IcChipSpec? {
    val model = TTLChipCatalog.getInfo(partNumber) ?: return null
    val info = IC_DISPLAY_INFO[partNumber]
        ?: IcDisplayInfo(category = "Logic", gateNoun = "Gate", gateType = "LOGIC", expression = "", propDelayNs = 10)

    val pinLabels = (1..model.pinCount).map { model.pins[it]?.name ?: "NC" }
    val pinRoles = (1..model.pinCount).map { model.pins[it]?.role ?: PinRole.NO_CONNECT }

    return IcChipSpec(
        partNumber = model.partNumber,
        name = model.description,
        category = info.category,
        pinCount = model.pinCount,
        description = IC_PROSE[partNumber] ?: model.description,
        pinLabels = pinLabels,
        pinRoles = pinRoles,
        propDelayNs = info.propDelayNs,
        gateMappings = model.internalGateMappings(info)
    )
}

/**
 * Every reference entry, iterated from [TTLChipCatalog.getAllPartNumbers] — the exact list the
 * bench's chip picker uses. A part added to the catalog appears here and in the sandbox
 * automatically; the two surfaces cannot diverge.
 */
val CHIP_CATALOG_SPECS: List<IcChipSpec> = TTLChipCatalog.getAllPartNumbers().mapNotNull { buildIcChipSpec(it) }

/** Resolves a pin number by signal name, so curated rows still validate against the model. */
private fun ChipModel.pinOf(name: String): GatePin? =
    pins.values.firstOrNull { it.name == name }?.let { GatePin(it.pinNumber, it.name) }

private fun ChipModel.pinsOf(vararg names: String): List<GatePin> = names.mapNotNull { pinOf(it) }

/**
 * Derives the internal gate breakdown from the model's own pin map.
 *
 * Gate-parts name their pins `<gate><letter>` (1A, 1B, 1Y, 1~CLR, ...), so gates are recovered
 * by grouping on the leading digit. The 7483 and 7448 have no per-gate prefixes, so their
 * stages are described explicitly — but with pin numbers still resolved by signal name from
 * the model, never written as literals.
 */
private fun ChipModel.internalGateMappings(info: IcDisplayInfo): List<GateMapping> {
    if (partNumber == "7483") {
        return listOf(
            GateMapping(
                title = "Stage 1 (LSB)",
                inputPins = pinsOf("A1", "B1", "C0"),
                outputPins = pinsOf("S1"),
                expression = "S1 = A1 ⊕ B1 ⊕ C0"
            ),
            GateMapping(
                title = "Stage 2",
                inputPins = pinsOf("A2", "B2"),
                outputPins = pinsOf("S2"),
                expression = "S2 = A2 ⊕ B2 ⊕ c1"
            ),
            GateMapping(
                title = "Stage 3",
                inputPins = pinsOf("A3", "B3"),
                outputPins = pinsOf("S3"),
                expression = "S3 = A3 ⊕ B3 ⊕ c2"
            ),
            GateMapping(
                title = "Stage 4 (MSB)",
                inputPins = pinsOf("A4", "B4"),
                outputPins = pinsOf("S4", "C4"),
                expression = "C4 = carry out"
            )
        )
    }
    if (partNumber == "7448") {
        return listOf(
            GateMapping(
                title = "BCD Inputs",
                inputPins = pinsOf("A", "B", "C", "D", "~LT", "~BI", "~RBI"),
                outputPins = pinsOf("a", "b", "c", "d", "e", "f", "g"),
                expression = "a..g = decode(D C B A)"
            )
        )
    }

    val gateNumbers = pins.values
        .mapNotNull { Regex("^(\\d)").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }
        .distinct()
        .sorted()

    return gateNumbers.mapNotNull { gate ->
        val gatePins = pins.values
            .filter { it.name.startsWith("$gate") && it.role != PinRole.POWER_VCC && it.role != PinRole.POWER_GND }
            .sortedBy { it.pinNumber }
        val inputs = gatePins.filter { it.role == PinRole.INPUT }.map { GatePin(it.pinNumber, it.name) }
        val outputs = gatePins.filter { it.role == PinRole.OUTPUT }.map { GatePin(it.pinNumber, it.name) }
        if (inputs.isEmpty() && outputs.isEmpty()) {
            null
        } else {
            GateMapping(
                title = "${info.gateNoun} $gate (${info.gateType})",
                inputPins = inputs,
                outputPins = outputs,
                expression = info.expression
            )
        }
    }
}

/**
 * 74xx IC Reference library with interactive DIP package pinouts,
 * gate topologies, and TTL specifications.
 *
 * The part list, package sizes, pin labels, pin roles and internal gate mappings are all
 * derived from [TTLChipCatalog] — the same source the breadboard's chip picker and the
 * simulation engine use — so the reference can never show a part the sandbox cannot place,
 * or a pinout that disagrees with the engine.
 */
@Composable
fun IcReferenceTab(
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSpecs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            CHIP_CATALOG_SPECS
        } else {
            val q = searchQuery.trim().lowercase()
            CHIP_CATALOG_SPECS.filter { spec ->
                spec.partNumber.lowercase().contains(q) ||
                spec.name.lowercase().contains(q) ||
                spec.category.lowercase().contains(q) ||
                spec.description.lowercase().contains(q)
            }
        }
    }

    var selectedPart by remember { mutableStateOf(CHIP_CATALOG_SPECS.firstOrNull()?.partNumber ?: "7400") }
    val currentSpec = remember(selectedPart, filteredSpecs) {
        filteredSpecs.find { it.partNumber == selectedPart }
            ?: filteredSpecs.firstOrNull()
            ?: CHIP_CATALOG_SPECS.first()
    }
    val view = LocalView.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.Space3,
            end = Dimens.Space3,
            top = Dimens.Space2,
            bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "spec_selector") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InstantSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "SEARCH IC REFERENCE...",
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (searchQuery.isBlank()) "74XX IC CATALOG" else "74XX IC CATALOG (${filteredSpecs.size})",
                    style = LogicLabsType.SwitchPlate,
                    color = AmberCore,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

                if (filteredSpecs.isEmpty()) {
                    Text(
                        text = "No ICs matching \"$searchQuery\"",
                        style = LogicLabsType.BodySm,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = Dimens.Space2)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSpecs, key = { it.partNumber }) { spec ->
                            val isSelected = spec.partNumber == currentSpec.partNumber
                            val bgCol = if (isSelected) AmberCore else SurfaceCard
                            val textCol = if (isSelected) ChassisBase else TextPrimary
                            val borderCol = if (isSelected) AmberCore else SurfaceCardBorder

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgCol)
                                    .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                                    .clickable {
                                        Haptics.tick(view)
                                        selectedPart = spec.partNumber
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = spec.partNumber,
                                    style = LogicLabsType.SwitchPlate,
                                    color = textCol,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Chip Details Card
        item(key = "active_chip_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SN${currentSpec.partNumber}N",
                            style = LogicLabsType.TitleMd,
                            color = AmberCore,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentSpec.name,
                            style = LogicLabsType.BodySm,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "DIP-${currentSpec.pinCount}",
                        style = LogicLabsType.SwitchPlate,
                        color = AccentCyan,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = currentSpec.description,
                    style = LogicLabsType.BodySm,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SurfaceCardBorder)
                )

                // Package Pinout Diagram
                Text(
                    text = "DIP-${currentSpec.pinCount} DUAL IN-LINE PACKAGE (HORIZONTAL):",
                    style = LogicLabsType.SwitchPlate,
                    color = AmberCore,
                    fontSize = 10.sp
                )

                HorizontalDipPinoutDiagram(
                    pinCount = currentSpec.pinCount,
                    labels = currentSpec.pinLabels,
                    roles = currentSpec.pinRoles,
                    partNumber = currentSpec.partNumber
                )

                // Internal Gates & I/O Breakdown
                GateMappingSection(spec = currentSpec)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SurfaceCardBorder)
                )

                // Electrical Specifications
                Text(
                    text = "TTL ELECTRICAL SPECIFICATIONS:",
                    style = LogicLabsType.SwitchPlate,
                    color = AccentCyan,
                    fontSize = 10.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpecPill(label = "VCC SUPPLY", value = "4.75V – 5.25V")
                    SpecPill(label = "PROP DELAY", value = "${currentSpec.propDelayNs} ns")
                    SpecPill(label = "FAN-OUT", value = "${currentSpec.fanOut} TTL")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpecPill(label = "VIL (MAX)", value = "0.8 V (LOW)")
                    SpecPill(label = "VIH (MIN)", value = "2.0 V (HIGH)")
                    SpecPill(label = "LOGIC FAMILY", value = "Standard TTL")
                }
            }
        }
    }
}

@Composable
private fun SpecPill(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(ChassisBase, RoundedCornerShape(6.dp))
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = LogicLabsType.SwitchPlate,
            color = TextTertiary,
            fontSize = 8.sp
        )
        Text(
            text = value,
            style = LogicLabsType.SwitchPlate,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Role badge and colour for a pin, derived from the catalog's [PinRole] instead of name sniffing. */
private fun pinRoleBadge(role: PinRole): Pair<String, Color> = when (role) {
    PinRole.POWER_VCC -> "VCC" to ShortCircuitAlert
    PinRole.POWER_GND -> "GND" to AccentCyan
    PinRole.OUTPUT -> "OUT" to AmberCore
    PinRole.NO_CONNECT -> "NC" to TextTertiary
    else -> "IN" to AccentCyan
}

/**
 * The physical package diagram, shared with the pinout inspector: a bench-accurate
 * DIP (epoxy gradient, gull-wing leads with shoulders, index notch, pin-1 dimple,
 * laser-style silkscreen) with on-body pin numbers and role-coded names at the leads.
 */
@Composable
private fun HorizontalDipPinoutDiagram(
    pinCount: Int,
    labels: List<String>,
    roles: List<PinRole>,
    partNumber: String,
    modifier: Modifier = Modifier
) {
    DipPackageDiagram(
        partNumber = partNumber,
        pinCount = pinCount,
        pinLabels = labels,
        pinRoles = roles,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Technical breakdown of each internal gate with its exact input and output pins,
 * generated from the catalog model.
 */
@Composable
private fun GateMappingSection(spec: IcChipSpec) {
    if (spec.gateMappings.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "INTERNAL GATES & PIN FUNCTION MAPPING:",
            style = LogicLabsType.SwitchPlate,
            color = AccentCyan,
            fontSize = 10.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ChassisBase)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            spec.gateMappings.forEach { mapping ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mapping.title,
                            style = LogicLabsType.SwitchPlate,
                            color = AmberCore,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (mapping.expression.isNotEmpty()) {
                            Text(
                                text = mapping.expression,
                                style = LogicLabsType.SwitchPlate,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (mapping.outputPins.isNotEmpty()) {
                        Text(
                            text = "OUT: ${mapping.output}",
                            style = LogicLabsType.SwitchPlate,
                            color = AmberCore,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (mapping.inputPins.isNotEmpty()) {
                        Text(
                            text = "INPUTS: ${mapping.inputs}",
                            style = LogicLabsType.SwitchPlate,
                            color = AccentCyan,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
