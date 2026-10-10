package com.logiclabs.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.component.InstantSearchBar
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import com.logiclabs.feature.tools.courseware.LabExperiment
import com.logiclabs.feature.tools.courseware.displayName
import com.logiclabs.feature.tools.feedback.Haptics

/**
 * Coursework tab featuring categorized units, collapsible accordion cards,
 * ANSI logic gate schematics, educational principles, and live truth tables
 * matching professional logic simulation reference designs.
 */
@Composable
fun CourseworkTab(
    verifiedLabIds: Set<String>,
    onLaunchLab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val allGroups = remember { ExperimentCatalog.groups }

    // Categorize groups into strictly disjoint curriculum units
    val (unit1, unit3, unit4, unit2) = remember(allGroups) {
        val u1 = mutableListOf<ExperimentCatalog.Group>() // Unit 1: gates
        val u3 = mutableListOf<ExperimentCatalog.Group>() // Unit 2: arithmetic
        val u4 = mutableListOf<ExperimentCatalog.Group>() // Unit 3: sequential
        val u2 = mutableListOf<ExperimentCatalog.Group>() // Unit 4: combinational/converters

        for (g in allGroups) {
            val k = g.key.lowercase()
            val t = g.title.lowercase()
            val searchable = "$k $t"
            when {
                // Unit 2: Arithmetic circuits (adders, subtractors, 7483)
                searchable.contains("adder") || searchable.contains("subtractor") ||
                    k.contains("7483") -> {
                    u3.add(g)
                }
                // Unit 3: Sequential logic (flip-flops, latches, counters, 7474, 7476)
                searchable.contains("flip-flop") || searchable.contains("flip flop") ||
                    k.contains("flip_flop") || searchable.contains("latch") ||
                    searchable.contains("counter") || k.contains("7474") ||
                    k.contains("7476") || k.contains("shift") -> {
                    u4.add(g)
                }
                // Unit 4: Combinational building blocks (decoders, comparators, muxes,
                // converters, DAC). Deliberately matched BEFORE the gate catch-all:
                // "comparator" contains "or" and "Multiplexer and 1:4 Demultiplexer"
                // contains the standalone word "and", either of which would otherwise
                // drag these cards into Unit 1.
                searchable.contains("comparator") || searchable.contains("multiplex") ||
                    searchable.contains("demultiplex") || searchable.contains("decoder") ||
                    searchable.contains("converter") || searchable.contains("gray") ||
                    k.contains("bcd") || k.contains("7448") || searchable.contains("dac") ||
                    k.contains("d_a") || k.contains("ladder") -> {
                    u2.add(g)
                }
                // Unit 1: Fundamental logic gates, universal logic & Boolean algebra
                else -> {
                    u1.add(g)
                }
            }
        }
        listOf(u1, u3, u4, u2)
    }

    var searchQuery by remember { mutableStateOf("") }

    val fUnit1 = remember(searchQuery, unit1) { filterCourseworkGroups(unit1, searchQuery) }
    val fUnit3 = remember(searchQuery, unit3) { filterCourseworkGroups(unit3, searchQuery) }
    val fUnit4 = remember(searchQuery, unit4) { filterCourseworkGroups(unit4, searchQuery) }
    val fUnit2 = remember(searchQuery, unit2) { filterCourseworkGroups(unit2, searchQuery) }

    // Nothing pre-expanded: the accordion opens only on a deliberate tap. The old
    // `allGroups.firstOrNull()?.key` initial value auto-opened the first unit card
    // every time the coursework tab appeared.
    var expandedGroupKey by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dimens.Space3,
            end = Dimens.Space3,
            top = Dimens.Space2,
            bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "coursework_search_bar") {
            InstantSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "SEARCH COURSEWORK...",
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }

        if (searchQuery.isNotBlank() && fUnit1.isEmpty() && fUnit3.isEmpty() && fUnit4.isEmpty() && fUnit2.isEmpty()) {
            item(key = "no_coursework_results") {
                Text(
                    text = "No coursework experiments matching \"$searchQuery\"",
                    style = LogicLabsType.BodySm,
                    color = TextTertiary,
                    modifier = Modifier.padding(vertical = Dimens.Space4, horizontal = Dimens.Space2)
                )
            }
        }

        // Unit 1
        if (fUnit1.isNotEmpty()) {
            item(key = "header_unit1") {
                UnitSectionBanner(
                    title = "UNIT 1: FUNDAMENTAL LOGIC GATES",
                    description = "Boolean operations, universal NAND/NOR logic, XNOR and buffer stages, and multi-input gates"
                )
            }
            items(fUnit1, key = { "u1_${it.key}" }) { group ->
                TechnicalAccordionCard(
                    group = group,
                    isExpanded = expandedGroupKey == group.key,
                    onToggleExpand = {
                        expandedGroupKey = if (expandedGroupKey == group.key) null else group.key
                    },
                    isVerified = group.variations.any { it.id in verifiedLabIds },
                    onLaunch = onLaunchLab
                )
            }
        }

        // Unit 2 (Arithmetic)
        if (fUnit3.isNotEmpty()) {
            item(key = "header_unit3") {
                Spacer(Modifier.height(8.dp))
                UnitSectionBanner(
                    title = "UNIT 2: ARITHMETIC & ADDER CIRCUITS",
                    description = "Half/full adders and subtractors, and the 7483 4-bit binary parallel adder"
                )
            }
            items(fUnit3, key = { "u3_${it.key}" }) { group ->
                TechnicalAccordionCard(
                    group = group,
                    isExpanded = expandedGroupKey == group.key,
                    onToggleExpand = {
                        expandedGroupKey = if (expandedGroupKey == group.key) null else group.key
                    },
                    isVerified = group.variations.any { it.id in verifiedLabIds },
                    onLaunch = onLaunchLab
                )
            }
        }

        // Unit 3 (Sequential)
        if (fUnit4.isNotEmpty()) {
            item(key = "header_unit4") {
                Spacer(Modifier.height(8.dp))
                UnitSectionBanner(
                    title = "UNIT 3: SEQUENTIAL LOGIC & FLIP-FLOPS",
                    description = "Clocked bistables, D and JK flip-flops, and binary ripple counters"
                )
            }
            items(fUnit4, key = { "u4_${it.key}" }) { group ->
                TechnicalAccordionCard(
                    group = group,
                    isExpanded = expandedGroupKey == group.key,
                    onToggleExpand = {
                        expandedGroupKey = if (expandedGroupKey == group.key) null else group.key
                    },
                    isVerified = group.variations.any { it.id in verifiedLabIds },
                    onLaunch = onLaunchLab
                )
            }
        }

        // Unit 4 (Combinational / Converters)
        if (fUnit2.isNotEmpty()) {
            item(key = "header_unit2") {
                Spacer(Modifier.height(8.dp))
                UnitSectionBanner(
                    title = "UNIT 4: COMBINATIONAL & CONVERTER CIRCUITS",
                    description = "Code converters, decoders, comparators, multiplexers, and D/A converters"
                )
            }
            items(fUnit2, key = { "u2_${it.key}" }) { group ->
                TechnicalAccordionCard(
                    group = group,
                    isExpanded = expandedGroupKey == group.key,
                    onToggleExpand = {
                        expandedGroupKey = if (expandedGroupKey == group.key) null else group.key
                    },
                    isVerified = group.variations.any { it.id in verifiedLabIds },
                    onLaunch = onLaunchLab
                )
            }
        }
    }
}

private fun filterCourseworkGroups(list: List<ExperimentCatalog.Group>, query: String): List<ExperimentCatalog.Group> {
    if (query.isBlank()) return list
    val q = query.trim().lowercase()
    return list.filter { g ->
        g.key.lowercase().contains(q) ||
        g.title.lowercase().contains(q) ||
        g.variations.any { v ->
            v.title.lowercase().contains(q) ||
            v.subtitle.lowercase().contains(q) ||
            v.description.lowercase().contains(q) ||
            v.displayName.lowercase().contains(q) ||
            v.targetChips.any { chip -> chip.lowercase().contains(q) }
        }
    }
}

@Composable
private fun UnitSectionBanner(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = LogicLabsType.SwitchPlate,
            color = AmberCore,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = description,
            style = LogicLabsType.BodySm,
            color = TextTertiary,
            fontSize = 11.sp
        )
    }
}

/**
 * Expandable Technical Accordion Card modeled directly from the reference app design.
 */
@Composable
private fun TechnicalAccordionCard(
    group: ExperimentCatalog.Group,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isVerified: Boolean,
    onLaunch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var selectedVariationId by remember(group.key) { mutableStateOf(group.default.id) }
    val activeLab = remember(selectedVariationId, group) {
        group.variations.find { it.id == selectedVariationId } ?: group.default
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevronRotation"
    )

    val shape = RoundedCornerShape(10.dp)
    // Theme tokens, not literals: the card chrome used to be hardcoded near-dark, so the
    // coursework tab stayed black under the daylight CLEANROOM theme while the rest of
    // the home rack went light.
    val cardBg = SurfaceCard
    val borderCol = if (isExpanded) AmberCore else SurfaceCardBorder

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .border(1.dp, borderCol, shape)
    ) {
        // --- Collapsed Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        Haptics.tick(view)
                        onToggleExpand()
                    }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Miniature Logic Gate ANSI Symbol
                LogicGateGlyph(
                    name = group.title,
                    variationSubtitle = activeLab.subtitle,
                    modifier = Modifier.size(32.dp, 24.dp)
                )

                Column {
                    Text(
                        text = group.title,
                        style = LogicLabsType.TitleSm,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = getTechnicalSubtitle(group.title, activeLab.subtitle),
                        style = LogicLabsType.BodySm,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isVerified) {
                    Text(
                        text = "VERIFIED",
                        style = LogicLabsType.SwitchPlate,
                        color = PhosphorCore,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .background(PhosphorCore.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Icon(
                    imageVector = LogicIcons.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotationAngle + 90f),
                    tint = TextSecondary
                )
            }
        }

        // --- Expanded Technical Content ---
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SurfaceCardBorder)
                )

                // Technical Definition
                Text(
                    text = getTechnicalDescription(group.title, activeLab.subtitle),
                    style = LogicLabsType.BodySm,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                // Large Vector Schematic Diagram
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(ChassisBase, RoundedCornerShape(8.dp))
                        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    LargeSchematicDiagram(
                        name = group.title,
                        variationSubtitle = activeLab.subtitle
                    )
                }

                // Styled Truth Table
                TruthTableSection(lab = activeLab)

                // Implementations / Variations (if multiple available)
                if (group.hasVariations) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "IMPLEMENTATION VARIATIONS:",
                            style = LogicLabsType.SwitchPlate,
                            color = AccentCyan,
                            fontSize = 9.sp
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(group.variations) { variation ->
                                val isSelected = variation.id == selectedVariationId
                                val vCol = if (isSelected) AmberCore else SurfaceCardBorder
                                val textCol = if (isSelected) ChassisBase else TextSecondary

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) AmberCore else SurfaceCard)
                                        .border(1.dp, vCol, RoundedCornerShape(6.dp))
                                        .clickable {
                                            Haptics.tick(view)
                                            selectedVariationId = variation.id
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = variation.subtitle.ifEmpty { variation.title },
                                        style = LogicLabsType.SwitchPlate,
                                        color = textCol,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Action Button
                Button(
                    onClick = {
                        Haptics.thud(view)
                        onLaunch(activeLab.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberCore,
                        contentColor = ChassisBase
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = LogicIcons.Breadboard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = getDynamicButtonLabel(activeLab),
                            style = LogicLabsType.SwitchPlate,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TruthTableSection(lab: LabExperiment) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "TRUTH TABLE:",
            style = LogicLabsType.SwitchPlate,
            color = AmberCore,
            fontSize = 9.sp
        )

        val inputs = lab.inputLabels.ifEmpty { listOf("A", "B") }
        val outputs = lab.outputLabels.ifEmpty { listOf("Q") }

        // The table sweeps at most four inputs (16 rows) so it stays readable. Anything
        // beyond that is pinned LOW and called out beneath the table — the lab bench's own
        // verifier sweeps the full 2^N input space.
        val sweptVars = inputs.size.coerceAtMost(4)
        val pinnedLabels = inputs.drop(sweptVars).map { it.substringBefore(" (") }
        val numRows = 1 shl sweptVars

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(6.dp))
        ) {
            // Header Row — strip the "(SWn)/(LEDn)" suffixes so multi-column tables fit.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceRaised)
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                inputs.take(sweptVars).forEach { inp ->
                    Text(
                        text = inp.substringBefore(" ("),
                        style = LogicLabsType.SwitchPlate,
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                outputs.forEach { out ->
                    Text(
                        text = out.substringBefore(" ("),
                        style = LogicLabsType.SwitchPlate,
                        color = AmberCore,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Truth Table Rows
            for (r in 0 until numRows) {
                val inBits = (0 until sweptVars).map { (r shr (sweptVars - 1 - it)) and 1 }
                // Always feed the expected function the lab's FULL input width: a 4+-input
                // lambda indexes inputs[3] and beyond, so a truncated list would crash the
                // card. Inputs the preview does not sweep are held LOW.
                val fullInputs = (0 until inputs.size).map { i ->
                    if (i < sweptVars) inBits[i] == 1 else false
                }
                val outBits = lab.expectedFunction(fullInputs).map { if (it) 1 else 0 }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (r % 2 == 0) SurfaceCard else ChassisBase)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    inBits.forEach { bit ->
                        Text(
                            text = "$bit",
                            style = LogicLabsType.SwitchPlate,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    // One column per declared output — a half adder shows SUM *and* CARRY,
                    // a decoder shows all of its decoded lines.
                    outBits.forEach { bit ->
                        Text(
                            text = "$bit",
                            style = LogicLabsType.SwitchPlate,
                            color = if (bit == 1) AmberCore else TextTertiary,
                            fontSize = 10.sp,
                            fontWeight = if (bit == 1) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        if (pinnedLabels.isNotEmpty()) {
            Text(
                text = "Preview sweeps the first $sweptVars inputs with ${pinnedLabels.joinToString()} held LOW — " +
                    "the lab bench sweeps all ${inputs.size}.",
                style = LogicLabsType.SwitchPlate,
                color = TextTertiary,
                fontSize = 8.sp
            )
        }
    }
}

private enum class CircuitDiagramKind {
    NOT,
    BUFFER,
    NAND,
    NOR,
    XOR,
    XNOR,
    AND,
    OR,
    WIDE_NAND,
    WIDE_AND,
    HALF_ADDER,
    FULL_ADDER,
    ADDER_4BIT,
    SUBTRACTOR,
    CONVERTER,
    FLIP_FLOP_D,
    FLIP_FLOP_JK,
    FLIP_FLOP,
    SR_LATCH,
    COUNTER,
    COMPARATOR,
    MUX,
    DEMUX,
    OCTAL_DECODER,
    BCD_DECODER,
    DAC,
    GENERIC_IC
}

/**
 * Maps a course group's title (plus the active variation subtitle when applicable) to a
 * schematic kind.
 *
 * Only references parts the sandbox catalog actually stocks: the ripple counter is the 7476
 * (there is no 7493), the decoders are the gate-built 3-to-8 (7411/7410) and the 7448 — never
 * a 74138 or 7447, which the bench cannot place.
 */
private fun classifyCircuit(name: String, variationSubtitle: String = ""): CircuitDiagramKind {
    val lower = name.lowercase()
    val sub = variationSubtitle.lowercase()
    return when {
        // 4-bit adder (7483) must precede "full adder" because Lab 12's title is
        // "4-Bit Binary Full Adder Arithmetic".
        lower.contains("7483") || sub.contains("7483") ||
            (lower.contains("adder") && (lower.contains("4-bit") || lower.contains("binary full adder"))) ->
            CircuitDiagramKind.ADDER_4BIT
        // Variation-specific overrides for multi-variation adder groups (exp04)
        sub.contains("full adder") -> CircuitDiagramKind.FULL_ADDER
        sub.contains("half adder") -> CircuitDiagramKind.HALF_ADDER
        lower.contains("half adder") || lower.contains("half_adder") -> CircuitDiagramKind.HALF_ADDER
        lower.contains("full adder") || lower.contains("full_adder") -> CircuitDiagramKind.FULL_ADDER
        lower.contains("subtractor") -> CircuitDiagramKind.SUBTRACTOR
        // XNOR must be classified before NOR: "Exclusive-NOR" contains the word "nor".
        lower.contains("xnor") -> CircuitDiagramKind.XNOR
        // 4-input AND variation of exp17 vs 4-input NAND
        sub.contains("4-input and") -> CircuitDiagramKind.WIDE_AND
        // The wide gate must be classified before the generic 2-input NAND.
        lower.contains("4-input") || lower.contains("wide") -> CircuitDiagramKind.WIDE_NAND
        lower.contains("buffer") -> CircuitDiagramKind.BUFFER
        lower.contains("bcd") || lower.contains("7-segment") || lower.contains("seven segment") -> CircuitDiagramKind.BCD_DECODER
        lower.contains("comparator") -> CircuitDiagramKind.COMPARATOR
        sub.contains("demultiplex") || sub.contains("demux") -> CircuitDiagramKind.DEMUX
        lower.contains("multiplex") -> CircuitDiagramKind.MUX
        lower.contains("decoder") || lower.contains("octal") -> CircuitDiagramKind.OCTAL_DECODER
        lower.contains("gray") -> CircuitDiagramKind.CONVERTER
        // Counter must precede "7476" because exp13's title is
        // "2-Bit Asynchronous Ripple Counter (7476)".
        lower.contains("counter") -> CircuitDiagramKind.COUNTER
        lower.contains("d flip") || lower.contains("7474") -> CircuitDiagramKind.FLIP_FLOP_D
        lower.contains("jk") || lower.contains("7476") -> CircuitDiagramKind.FLIP_FLOP_JK
        lower.contains("latch") -> CircuitDiagramKind.SR_LATCH
        lower.contains("flip") -> CircuitDiagramKind.FLIP_FLOP
        lower.contains("dac") || lower.contains("d/a") || lower.contains("weighted") || lower.contains("ladder") -> CircuitDiagramKind.DAC
        // Discrete & Universal gate variation overrides
        sub.contains("diode or") -> CircuitDiagramKind.OR
        sub.contains("diode and") -> CircuitDiagramKind.AND
        sub.contains("transistor not") -> CircuitDiagramKind.NOT
        sub.contains("7402 nors only") -> CircuitDiagramKind.NOR
        sub.contains("7400 nands only") -> CircuitDiagramKind.NAND
        Regex("\\bnot\\b").containsMatchIn(lower) || lower.contains("inverter") -> CircuitDiagramKind.NOT
        Regex("\\bnand\\b").containsMatchIn(lower) -> CircuitDiagramKind.NAND
        Regex("\\bnor\\b").containsMatchIn(lower) -> CircuitDiagramKind.NOR
        Regex("\\bxor\\b").containsMatchIn(lower) -> CircuitDiagramKind.XOR
        Regex("\\band\\b").containsMatchIn(lower) -> CircuitDiagramKind.AND
        Regex("\\bor\\b").containsMatchIn(lower) -> CircuitDiagramKind.OR
        else -> CircuitDiagramKind.GENERIC_IC
    }
}

private fun getDynamicButtonLabel(lab: LabExperiment): String {
    val s = lab.subtitle.lowercase()
    val t = (lab.title + " " + lab.subtitle).lowercase()
    return when {
        t.contains("7483") || t.contains("4-bit binary full adder") || t.contains("4-bit adder") || t.contains("binary adder") -> "TEST 4-BIT ADDER"
        s.contains("full adder") -> "BUILD FULL ADDER"
        s.contains("half adder") -> "BUILD HALF ADDER"
        t.contains("half adder") -> "BUILD HALF ADDER"
        t.contains("full adder") -> "BUILD FULL ADDER"
        t.contains("subtractor") -> "BUILD SUBTRACTOR"
        t.contains("counter") -> "TEST RIPPLE COUNTER"
        t.contains("flip") && t.contains("jk") -> "TEST JK FLIP-FLOP"
        t.contains("d flip") || t.contains("d-type") -> "TEST D FLIP-FLOP"
        t.contains("flip") -> "TEST FLIP-FLOP"
        t.contains("latch") -> "TEST SR LATCH"
        t.contains("bcd") || t.contains("7-segment") -> "TEST BCD DECODER"
        t.contains("decoder") -> "TEST 3-TO-8 DECODER"
        t.contains("comparator") -> "TEST COMPARATOR"
        s.contains("demultiplexer") || s.contains("demux") -> "TEST DEMULTIPLEXER"
        t.contains("multiplexer") || t.contains("mux") -> "TEST MULTIPLEXER"
        t.contains("gray") -> "TEST CODE CONVERTER"
        t.contains("simplification") -> "PROVE THE REDUCTION"
        t.contains("dac") || t.contains("d/a") || t.contains("weighted-resistor") || t.contains("weighted ladder") -> "TEST WEIGHTED DAC"
        t.contains("buffer") -> "VERIFY BUFFER"
        // XNOR before NOR: "exclusive-nor" contains the standalone word "nor".
        t.contains("xnor") -> "VERIFY XNOR GATE"
        // Variation-specific gate checks so group titles don't shadow the active variation
        s.contains("diode or") -> "VERIFY OR GATE"
        s.contains("diode and") -> "VERIFY AND GATE"
        s.contains("transistor not") -> "VERIFY NOT GATE"
        s.contains("7402 nors only") -> "VERIFY NOR GATE"
        s.contains("7400 nands only") -> "VERIFY NAND GATE"
        s.contains("4-input and") -> "VERIFY AND GATE"
        Regex("\\bnand\\b").containsMatchIn(t) -> "VERIFY NAND GATE"
        Regex("\\bnor\\b").containsMatchIn(t) -> "VERIFY NOR GATE"
        Regex("\\bxor\\b").containsMatchIn(t) -> "VERIFY XOR GATE"
        Regex("\\bnot\\b").containsMatchIn(t) || t.contains("inverter") -> "VERIFY NOT GATE"
        Regex("\\band\\b").containsMatchIn(t) -> "VERIFY AND GATE"
        Regex("\\bor\\b").containsMatchIn(t) -> "VERIFY OR GATE"
        else -> "LAUNCH EXPERIMENT"
    }
}

/**
 * Procedural ANSI logic gate schematic rendering on Canvas.
 */
@Composable
private fun LargeSchematicDiagram(
    name: String,
    variationSubtitle: String = "",
    modifier: Modifier = Modifier
) {
    val kind = remember(name, variationSubtitle) { classifyCircuit(name, variationSubtitle) }
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 26f
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val titlePaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F59E0B")
            textSize = 28f
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val path3 = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val strokeCol = AmberCore
        val busCol = AccentCyan
        val textYOffset = 9f

        when (kind) {
            CircuitDiagramKind.NOT -> {
                val gateX = w * 0.38f
                val gateY = h * 0.20f
                val gateW = w * 0.24f
                val gateH = h * 0.60f
                path1.reset()
                path1.moveTo(gateX, gateY)
                path1.lineTo(gateX + gateW - 12f, gateY + gateH / 2f)
                path1.lineTo(gateX, gateY + gateH)
                path1.close()
                drawPath(path1, strokeCol, style = stroke)
                drawCircle(strokeCol, radius = 5f, center = Offset(gateX + gateW - 6f, gateY + gateH / 2f), style = stroke)
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH / 2f), Offset(gateX, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                drawLine(strokeCol, Offset(gateX + gateW - 1f, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("A", w * 0.08f, gateY + gateH / 2f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("Q = Ā", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
            }
            CircuitDiagramKind.AND, CircuitDiagramKind.NAND -> {
                val gateX = w * 0.35f
                val gateY = h * 0.20f
                val gateW = w * 0.28f
                val gateH = h * 0.60f
                path1.reset()
                path1.moveTo(gateX, gateY)
                path1.lineTo(gateX + gateW * 0.5f, gateY)
                path1.arcTo(Rect(gateX, gateY, gateX + gateW, gateY + gateH), -90f, 180f, false)
                path1.lineTo(gateX, gateY + gateH)
                path1.close()
                drawPath(path1, strokeCol, style = stroke)
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH * 0.28f), Offset(gateX, gateY + gateH * 0.28f), strokeWidth = 2.dp.toPx())
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH * 0.72f), Offset(gateX, gateY + gateH * 0.72f), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("A", w * 0.08f, gateY + gateH * 0.28f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("B", w * 0.08f, gateY + gateH * 0.72f + textYOffset, labelPaint)
                if (kind == CircuitDiagramKind.NAND) {
                    drawCircle(strokeCol, radius = 5f, center = Offset(gateX + gateW + 5f, gateY + gateH / 2f), style = stroke)
                    drawLine(strokeCol, Offset(gateX + gateW + 10f, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText("Q = (A·B)′", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
                } else {
                    drawLine(strokeCol, Offset(gateX + gateW, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText("Q = A·B", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
                }
            }
            CircuitDiagramKind.OR, CircuitDiagramKind.NOR -> {
                val gateX = w * 0.35f
                val gateY = h * 0.20f
                val gateW = w * 0.28f
                val gateH = h * 0.60f
                path1.reset()
                path1.moveTo(gateX, gateY)
                path1.quadraticBezierTo(gateX + gateW * 0.5f, gateY, gateX + gateW - 10f, gateY + gateH / 2f)
                path1.quadraticBezierTo(gateX + gateW * 0.5f, gateY + gateH, gateX, gateY + gateH)
                path1.quadraticBezierTo(gateX + gateW * 0.25f, gateY + gateH / 2f, gateX, gateY)
                path1.close()
                drawPath(path1, strokeCol, style = stroke)
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH * 0.28f), Offset(gateX + 6f, gateY + gateH * 0.28f), strokeWidth = 2.dp.toPx())
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH * 0.72f), Offset(gateX + 6f, gateY + gateH * 0.72f), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("A", w * 0.08f, gateY + gateH * 0.28f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("B", w * 0.08f, gateY + gateH * 0.72f + textYOffset, labelPaint)
                if (kind == CircuitDiagramKind.NOR) {
                    drawCircle(strokeCol, radius = 5f, center = Offset(gateX + gateW - 4f, gateY + gateH / 2f), style = stroke)
                    drawLine(strokeCol, Offset(gateX + gateW + 1f, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText("Q = (A+B)′", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
                } else {
                    drawLine(strokeCol, Offset(gateX + gateW - 10f, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText("Q = A+B", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
                }
            }
            CircuitDiagramKind.XOR -> {
                val gateX = w * 0.35f
                val gateY = h * 0.20f
                val gateW = w * 0.28f
                val gateH = h * 0.60f
                path1.reset()
                path1.moveTo(gateX, gateY)
                path1.quadraticBezierTo(gateX + gateW * 0.5f, gateY, gateX + gateW - 10f, gateY + gateH / 2f)
                path1.quadraticBezierTo(gateX + gateW * 0.5f, gateY + gateH, gateX, gateY + gateH)
                path1.quadraticBezierTo(gateX + gateW * 0.25f, gateY + gateH / 2f, gateX, gateY)
                path1.close()
                drawPath(path1, strokeCol, style = stroke)
                path2.reset()
                path2.moveTo(gateX - 8f, gateY)
                path2.quadraticBezierTo(gateX - 8f + gateW * 0.25f, gateY + gateH / 2f, gateX - 8f, gateY + gateH)
                drawPath(path2, strokeCol, style = stroke)
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH * 0.28f), Offset(gateX - 4f, gateY + gateH * 0.28f), strokeWidth = 2.dp.toPx())
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH * 0.72f), Offset(gateX - 4f, gateY + gateH * 0.72f), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("A", w * 0.08f, gateY + gateH * 0.28f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("B", w * 0.08f, gateY + gateH * 0.72f + textYOffset, labelPaint)
                drawLine(strokeCol, Offset(gateX + gateW - 10f, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("Q = A⊕B", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
            }
            CircuitDiagramKind.BUFFER -> {
                // A buffer is the inverter's triangle without the output bubble: Y = A.
                val gateX = w * 0.38f
                val gateY = h * 0.20f
                val gateW = w * 0.24f
                val gateH = h * 0.60f
                path1.reset()
                path1.moveTo(gateX, gateY)
                path1.lineTo(gateX + gateW, gateY + gateH / 2f)
                path1.lineTo(gateX, gateY + gateH)
                path1.close()
                drawPath(path1, strokeCol, style = stroke)
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH / 2f), Offset(gateX, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                drawLine(strokeCol, Offset(gateX + gateW, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("A", w * 0.08f, gateY + gateH / 2f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("Q = A", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
            }
            CircuitDiagramKind.XNOR -> {
                // XOR body with the extra input arc AND an inversion bubble: Y = A ⊙ B.
                val gateX = w * 0.35f
                val gateY = h * 0.20f
                val gateW = w * 0.28f
                val gateH = h * 0.60f
                path1.reset()
                path1.moveTo(gateX, gateY)
                path1.quadraticBezierTo(gateX + gateW * 0.5f, gateY, gateX + gateW - 10f, gateY + gateH / 2f)
                path1.quadraticBezierTo(gateX + gateW * 0.5f, gateY + gateH, gateX, gateY + gateH)
                path1.quadraticBezierTo(gateX + gateW * 0.25f, gateY + gateH / 2f, gateX, gateY)
                path1.close()
                drawPath(path1, strokeCol, style = stroke)
                path2.reset()
                path2.moveTo(gateX - 8f, gateY)
                path2.quadraticBezierTo(gateX - 8f + gateW * 0.25f, gateY + gateH / 2f, gateX - 8f, gateY + gateH)
                drawPath(path2, strokeCol, style = stroke)
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH * 0.28f), Offset(gateX - 4f, gateY + gateH * 0.28f), strokeWidth = 2.dp.toPx())
                drawLine(strokeCol, Offset(w * 0.15f, gateY + gateH * 0.72f), Offset(gateX - 4f, gateY + gateH * 0.72f), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("A", w * 0.08f, gateY + gateH * 0.28f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("B", w * 0.08f, gateY + gateH * 0.72f + textYOffset, labelPaint)
                drawCircle(strokeCol, radius = 5f, center = Offset(gateX + gateW - 4f, gateY + gateH / 2f), style = stroke)
                drawLine(strokeCol, Offset(gateX + gateW + 1f, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText("Q = A⊙B", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
            }
            CircuitDiagramKind.WIDE_NAND, CircuitDiagramKind.WIDE_AND -> {
                // AND body with four input stubs (plus inversion bubble for WIDE_NAND).
                val gateX = w * 0.35f
                val gateY = h * 0.16f
                val gateW = w * 0.28f
                val gateH = h * 0.68f
                path1.reset()
                path1.moveTo(gateX, gateY)
                path1.lineTo(gateX + gateW * 0.5f, gateY)
                path1.arcTo(Rect(gateX, gateY, gateX + gateW, gateY + gateH), -90f, 180f, false)
                path1.lineTo(gateX, gateY + gateH)
                path1.close()
                drawPath(path1, strokeCol, style = stroke)
                val stubs = listOf(0.15f, 0.38f, 0.62f, 0.85f)
                val labels = listOf("A", "B", "C", "D")
                for ((i, frac) in stubs.withIndex()) {
                    val y = gateY + gateH * frac
                    drawLine(strokeCol, Offset(w * 0.15f, y), Offset(gateX, y), strokeWidth = 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(labels[i], w * 0.08f, y + textYOffset, labelPaint)
                }
                if (kind == CircuitDiagramKind.WIDE_NAND) {
                    drawCircle(strokeCol, radius = 5f, center = Offset(gateX + gateW + 5f, gateY + gateH / 2f), style = stroke)
                    drawLine(strokeCol, Offset(gateX + gateW + 10f, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText("Q = (A·B·C·D)′", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
                } else {
                    drawLine(strokeCol, Offset(gateX + gateW, gateY + gateH / 2f), Offset(w * 0.85f, gateY + gateH / 2f), strokeWidth = 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText("Q = A·B·C·D", w * 0.92f, gateY + gateH / 2f + textYOffset, labelPaint)
                }
            }
            CircuitDiagramKind.HALF_ADDER -> {
                val gateW = w * 0.20f
                val gateH = h * 0.36f
                val gateX = w * 0.40f
                val topY = h * 0.08f
                path1.reset()
                path1.moveTo(gateX, topY)
                path1.quadraticBezierTo(gateX + gateW * 0.5f, topY, gateX + gateW - 6f, topY + gateH / 2f)
                path1.quadraticBezierTo(gateX + gateW * 0.5f, topY + gateH, gateX, topY + gateH)
                path1.quadraticBezierTo(gateX + gateW * 0.25f, topY + gateH / 2f, gateX, topY)
                path1.close()
                drawPath(path1, strokeCol, style = stroke)
                path2.reset()
                path2.moveTo(gateX - 6f, topY)
                path2.quadraticBezierTo(gateX - 6f + gateW * 0.25f, topY + gateH / 2f, gateX - 6f, topY + gateH)
                drawPath(path2, strokeCol, style = stroke)

                val botY = h * 0.54f
                path3.reset()
                path3.moveTo(gateX, botY)
                path3.lineTo(gateX + gateW * 0.5f, botY)
                path3.arcTo(Rect(gateX, botY, gateX + gateW, botY + gateH), -90f, 180f, false)
                path3.lineTo(gateX, botY + gateH)
                path3.close()
                drawPath(path3, strokeCol, style = stroke)

                val inAX = w * 0.16f
                val branchAX = w * 0.28f
                drawLine(busCol, Offset(inAX, topY + gateH * 0.30f), Offset(gateX - 4f, topY + gateH * 0.30f), strokeWidth = 1.5.dp.toPx())
                drawLine(busCol, Offset(branchAX, topY + gateH * 0.30f), Offset(branchAX, botY + gateH * 0.30f), strokeWidth = 1.5.dp.toPx())
                drawLine(busCol, Offset(branchAX, botY + gateH * 0.30f), Offset(gateX, botY + gateH * 0.30f), strokeWidth = 1.5.dp.toPx())
                drawCircle(busCol, radius = 3f, center = Offset(branchAX, topY + gateH * 0.30f))

                val inBX = inAX
                val branchBX = w * 0.34f
                drawLine(busCol, Offset(inBX, topY + gateH * 0.70f), Offset(gateX - 4f, topY + gateH * 0.70f), strokeWidth = 1.5.dp.toPx())
                drawLine(busCol, Offset(branchBX, topY + gateH * 0.70f), Offset(branchBX, botY + gateH * 0.70f), strokeWidth = 1.5.dp.toPx())
                drawLine(busCol, Offset(branchBX, botY + gateH * 0.70f), Offset(gateX, botY + gateH * 0.70f), strokeWidth = 1.5.dp.toPx())
                drawCircle(busCol, radius = 3f, center = Offset(branchBX, topY + gateH * 0.70f))

                val outX = w * 0.80f
                drawLine(strokeCol, Offset(gateX + gateW - 6f, topY + gateH / 2f), Offset(outX, topY + gateH / 2f), strokeWidth = 1.5.dp.toPx())
                drawLine(strokeCol, Offset(gateX + gateW, botY + gateH / 2f), Offset(outX, botY + gateH / 2f), strokeWidth = 1.5.dp.toPx())

                drawContext.canvas.nativeCanvas.drawText("A", inAX - 18f, topY + gateH * 0.30f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("B", inBX - 18f, topY + gateH * 0.70f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("Σ (SUM)", outX + 44f, topY + gateH / 2f + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("C (CARRY)", outX + 50f, botY + gateH / 2f + textYOffset, labelPaint)
            }
            CircuitDiagramKind.FULL_ADDER, CircuitDiagramKind.ADDER_4BIT, CircuitDiagramKind.SUBTRACTOR -> {
                val blockX = w * 0.28f
                val blockY = h * 0.12f
                val blockW = w * 0.44f
                val blockH = h * 0.76f

                drawRoundRect(
                    color = SurfaceRaised,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawRoundRect(
                    color = strokeCol,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = stroke
                )

                val inY1 = blockY + blockH * 0.22f
                val inY2 = blockY + blockH * 0.50f
                val inY3 = blockY + blockH * 0.78f
                drawLine(busCol, Offset(w * 0.12f, inY1), Offset(blockX, inY1), strokeWidth = 2.dp.toPx())
                drawLine(busCol, Offset(w * 0.12f, inY2), Offset(blockX, inY2), strokeWidth = 2.dp.toPx())
                drawLine(busCol, Offset(w * 0.12f, inY3), Offset(blockX, inY3), strokeWidth = 2.dp.toPx())

                val outY1 = blockY + blockH * 0.33f
                val outY2 = blockY + blockH * 0.67f
                drawLine(strokeCol, Offset(blockX + blockW, outY1), Offset(w * 0.88f, outY1), strokeWidth = 2.dp.toPx())
                drawLine(strokeCol, Offset(blockX + blockW, outY2), Offset(w * 0.88f, outY2), strokeWidth = 2.dp.toPx())

                val is4Bit = kind == CircuitDiagramKind.ADDER_4BIT
                val isSub = kind == CircuitDiagramKind.SUBTRACTOR
                val title = when {
                    is4Bit -> "7483 4-BIT ADDER"
                    isSub -> "SUBTRACTOR"
                    else -> "FULL ADDER"
                }
                val in1 = if (is4Bit) "A[1..4]" else "A"
                val in2 = if (is4Bit) "B[1..4]" else "B"
                val in3 = when {
                    is4Bit -> "C0"
                    isSub -> "Bin"
                    else -> "Cin"
                }
                val out1 = when {
                    is4Bit -> "Σ[1..4]"
                    isSub -> "DIFF"
                    else -> "Σ (SUM)"
                }
                val out2 = when {
                    is4Bit -> "C4"
                    isSub -> "BOUT"
                    else -> "Cout"
                }

                drawContext.canvas.nativeCanvas.drawText(title, blockX + blockW / 2f, blockY + blockH * 0.55f, titlePaint)
                drawContext.canvas.nativeCanvas.drawText(in1, w * 0.06f, inY1 + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText(in2, w * 0.06f, inY2 + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText(in3, w * 0.06f, inY3 + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText(out1, w * 0.94f, outY1 + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText(out2, w * 0.94f, outY2 + textYOffset, labelPaint)
            }
            CircuitDiagramKind.FLIP_FLOP_D, CircuitDiagramKind.FLIP_FLOP_JK,
            CircuitDiagramKind.FLIP_FLOP, CircuitDiagramKind.SR_LATCH -> {
                val blockX = w * 0.32f
                val blockY = h * 0.12f
                val blockW = w * 0.36f
                val blockH = h * 0.76f

                drawRoundRect(
                    color = SurfaceRaised,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawRoundRect(
                    color = strokeCol,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = stroke
                )

                val isClocked = kind != CircuitDiagramKind.SR_LATCH
                val clkY = blockY + blockH * 0.50f
                if (isClocked) {
                    drawLine(busCol, Offset(w * 0.14f, clkY), Offset(blockX, clkY), strokeWidth = 2.dp.toPx())
                    path1.reset()
                    path1.moveTo(blockX, clkY - 8f)
                    path1.lineTo(blockX + 12f, clkY)
                    path1.lineTo(blockX, clkY + 8f)
                    drawPath(path1, strokeCol, style = stroke)
                }

                val inY1 = blockY + blockH * 0.25f
                val inY2 = blockY + blockH * 0.75f
                drawLine(busCol, Offset(w * 0.14f, inY1), Offset(blockX, inY1), strokeWidth = 2.dp.toPx())
                if (kind != CircuitDiagramKind.FLIP_FLOP_D) {
                    drawLine(busCol, Offset(w * 0.14f, inY2), Offset(blockX, inY2), strokeWidth = 2.dp.toPx())
                }

                val outY1 = blockY + blockH * 0.25f
                val outY2 = blockY + blockH * 0.75f
                drawLine(strokeCol, Offset(blockX + blockW, outY1), Offset(w * 0.86f, outY1), strokeWidth = 2.dp.toPx())
                drawCircle(strokeCol, radius = 4f, center = Offset(blockX + blockW + 5f, outY2), style = stroke)
                drawLine(strokeCol, Offset(blockX + blockW + 9f, outY2), Offset(w * 0.86f, outY2), strokeWidth = 2.dp.toPx())

                val chipLabel = when (kind) {
                    CircuitDiagramKind.FLIP_FLOP_JK -> "7476 JK-FF"
                    CircuitDiagramKind.FLIP_FLOP_D -> "7474 D-FF"
                    CircuitDiagramKind.SR_LATCH -> "7400 SR LATCH"
                    else -> "7400 SR-FF"
                }
                val in1Label = when (kind) {
                    CircuitDiagramKind.FLIP_FLOP_JK -> "J"
                    CircuitDiagramKind.FLIP_FLOP_D -> "D"
                    CircuitDiagramKind.SR_LATCH -> "~S"
                    else -> "S"
                }
                val in2Label = when (kind) {
                    CircuitDiagramKind.FLIP_FLOP_JK -> "K"
                    CircuitDiagramKind.FLIP_FLOP_D -> ""
                    CircuitDiagramKind.SR_LATCH -> "~R"
                    else -> "R"
                }

                drawContext.canvas.nativeCanvas.drawText(chipLabel, blockX + blockW / 2f, blockY + blockH * 0.55f, titlePaint)
                drawContext.canvas.nativeCanvas.drawText(in1Label, w * 0.07f, inY1 + textYOffset, labelPaint)
                if (isClocked) {
                    drawContext.canvas.nativeCanvas.drawText("CLK", w * 0.07f, clkY + textYOffset, labelPaint)
                }
                if (in2Label.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(in2Label, w * 0.07f, inY2 + textYOffset, labelPaint)
                }
                drawContext.canvas.nativeCanvas.drawText("Q", w * 0.93f, outY1 + textYOffset, labelPaint)
                drawContext.canvas.nativeCanvas.drawText("Q̄", w * 0.93f, outY2 + textYOffset, labelPaint)
            }
            CircuitDiagramKind.COUNTER -> {
                val blockX = w * 0.25f
                val blockY = h * 0.12f
                val blockW = w * 0.50f
                val blockH = h * 0.76f

                drawRoundRect(
                    color = SurfaceRaised,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawRoundRect(
                    color = strokeCol,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = stroke
                )

                val clkY = blockY + blockH * 0.50f
                drawLine(busCol, Offset(w * 0.08f, clkY), Offset(blockX, clkY), strokeWidth = 2.dp.toPx())
                path1.reset()
                path1.moveTo(blockX, clkY - 8f)
                path1.lineTo(blockX + 12f, clkY)
                path1.lineTo(blockX, clkY + 8f)
                drawPath(path1, strokeCol, style = stroke)

                // The 7476 gives two stages: Q0 is the LSB (÷2) and Q1 the MSB (÷4).
                for (i in 0..1) {
                    val outY = blockY + blockH * (0.32f + i * 0.36f)
                    drawLine(strokeCol, Offset(blockX + blockW, outY), Offset(w * 0.88f, outY), strokeWidth = 1.5.dp.toPx())
                    val qName = "Q$i"
                    drawContext.canvas.nativeCanvas.drawText(qName, w * 0.94f, outY + textYOffset, labelPaint)
                }

                drawContext.canvas.nativeCanvas.drawText("7476 ÷4 COUNTER", blockX + blockW / 2f, blockY + blockH * 0.55f, titlePaint)
                drawContext.canvas.nativeCanvas.drawText("CLK", w * 0.04f, clkY + textYOffset, labelPaint)
            }
            CircuitDiagramKind.OCTAL_DECODER, CircuitDiagramKind.BCD_DECODER,
            CircuitDiagramKind.COMPARATOR, CircuitDiagramKind.MUX, CircuitDiagramKind.DEMUX,
            CircuitDiagramKind.CONVERTER, CircuitDiagramKind.DAC, CircuitDiagramKind.GENERIC_IC -> {
                // One parameterised block for the MSI-style experiments. Titles only name
                // parts the sandbox actually stocks (7448) or the gate families that build
                // them — never a 74138 or 7447 the bench cannot place.
                val (title, inLabels, outLabel) = when (kind) {
                    CircuitDiagramKind.OCTAL_DECODER -> Triple("3:8 DECODER", listOf("A", "B", "C"), "O[0..7]")
                    CircuitDiagramKind.BCD_DECODER -> Triple("7448 BCD→7SEG", listOf("A", "B", "C", "D"), "a..g")
                    CircuitDiagramKind.COMPARATOR -> Triple("2-BIT COMPARATOR", listOf("A[1:0]", "B[1:0]"), "> = <")
                    CircuitDiagramKind.MUX -> Triple("4:1 MUX", listOf("D[0..3]", "S1", "S0"), "Y")
                    CircuitDiagramKind.DEMUX -> Triple("1:4 DEMUX", listOf("D", "S1", "S0"), "Y[0..3]")
                    CircuitDiagramKind.CONVERTER -> Triple("BIN ↔ GRAY", listOf("B[3:0]"), "G[3:0]")
                    CircuitDiagramKind.DAC -> Triple("WEIGHTED DAC", listOf("D[3:0]"), "Vout")
                    else -> Triple("LOGIC MODULE", listOf("A", "B"), "Y")
                }

                val blockX = w * 0.28f
                val blockY = h * 0.12f
                val blockW = w * 0.44f
                val blockH = h * 0.76f

                drawRoundRect(
                    color = SurfaceRaised,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawRoundRect(
                    color = strokeCol,
                    topLeft = Offset(blockX, blockY),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = stroke
                )

                val inCount = inLabels.size
                for ((i, label) in inLabels.withIndex()) {
                    val inY = blockY + blockH * ((i + 1f) / (inCount + 1f))
                    drawLine(busCol, Offset(w * 0.10f, inY), Offset(blockX, inY), strokeWidth = 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(label, w * 0.05f, inY + textYOffset, labelPaint)
                }

                for (i in 0..2) {
                    val outY = blockY + blockH * (0.20f + i * 0.20f)
                    drawCircle(strokeCol, radius = 3f, center = Offset(blockX + blockW + 4f, outY), style = stroke)
                    drawLine(strokeCol, Offset(blockX + blockW + 7f, outY), Offset(w * 0.88f, outY), strokeWidth = 1.5.dp.toPx())
                }

                drawContext.canvas.nativeCanvas.drawText(title, blockX + blockW / 2f, blockY + blockH * 0.55f, titlePaint)
                drawContext.canvas.nativeCanvas.drawText(outLabel, w * 0.94f, blockY + blockH * 0.50f + textYOffset, labelPaint)
            }
        }
    }
}

@Composable
private fun LogicGateGlyph(
    name: String,
    variationSubtitle: String = "",
    modifier: Modifier = Modifier
) {
    val kind = remember(name, variationSubtitle) { classifyCircuit(name, variationSubtitle) }
    val path = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.5.dp.toPx())
        val col = AmberCore

        path.reset()
        when (kind) {
            CircuitDiagramKind.NOT, CircuitDiagramKind.BUFFER -> {
                path.moveTo(2f, 2f)
                path.lineTo(w - 8f, h / 2f)
                path.lineTo(2f, h - 2f)
                path.close()
                drawPath(path, col, style = stroke)
                if (kind == CircuitDiagramKind.NOT) {
                    drawCircle(col, radius = 3f, center = Offset(w - 4f, h / 2f), style = stroke)
                }
            }
            CircuitDiagramKind.OR, CircuitDiagramKind.NOR, CircuitDiagramKind.XOR, CircuitDiagramKind.XNOR -> {
                path.moveTo(2f, 2f)
                path.quadraticBezierTo(w * 0.5f, 2f, w - 4f, h / 2f)
                path.quadraticBezierTo(w * 0.5f, h - 2f, 2f, h - 2f)
                path.quadraticBezierTo(w * 0.25f, h / 2f, 2f, 2f)
                path.close()
                drawPath(path, col, style = stroke)
                if (kind == CircuitDiagramKind.NOR || kind == CircuitDiagramKind.XNOR) {
                    drawCircle(col, radius = 2.5f, center = Offset(w - 2f, h / 2f), style = stroke)
                }
                if (kind == CircuitDiagramKind.XOR || kind == CircuitDiagramKind.XNOR) {
                    val arc = Path().apply {
                        moveTo(0f, 2f)
                        quadraticBezierTo(w * 0.20f, h / 2f, 0f, h - 2f)
                    }
                    drawPath(arc, col, style = stroke)
                }
            }
            CircuitDiagramKind.HALF_ADDER, CircuitDiagramKind.FULL_ADDER,
            CircuitDiagramKind.ADDER_4BIT, CircuitDiagramKind.SUBTRACTOR -> {
                path.moveTo(w - 4f, 2f)
                path.lineTo(4f, 2f)
                path.lineTo(w * 0.45f, h / 2f)
                path.lineTo(4f, h - 2f)
                path.lineTo(w - 4f, h - 2f)
                drawPath(path, col, style = stroke)
            }
            CircuitDiagramKind.FLIP_FLOP, CircuitDiagramKind.FLIP_FLOP_D,
            CircuitDiagramKind.FLIP_FLOP_JK, CircuitDiagramKind.SR_LATCH -> {
                drawRoundRect(col, Offset(2f, 2f), Size(w - 4f, h - 4f), style = stroke)
                if (kind != CircuitDiagramKind.SR_LATCH) {
                    path.moveTo(2f, h / 2f - 4f)
                    path.lineTo(8f, h / 2f)
                    path.lineTo(2f, h / 2f + 4f)
                    drawPath(path, col, style = stroke)
                }
            }
            CircuitDiagramKind.COUNTER -> {
                path.moveTo(2f, h - 3f)
                path.lineTo(w * 0.33f, h - 3f)
                path.lineTo(w * 0.33f, 3f)
                path.lineTo(w * 0.66f, 3f)
                path.lineTo(w * 0.66f, h - 3f)
                path.lineTo(w - 2f, h - 3f)
                drawPath(path, col, style = stroke)
            }
            CircuitDiagramKind.OCTAL_DECODER, CircuitDiagramKind.BCD_DECODER,
            CircuitDiagramKind.COMPARATOR, CircuitDiagramKind.MUX, CircuitDiagramKind.DEMUX,
            CircuitDiagramKind.CONVERTER, CircuitDiagramKind.DAC, CircuitDiagramKind.GENERIC_IC -> {
                drawRoundRect(col, Offset(2f, 2f), Size(w - 4f, h - 4f), style = stroke)
                drawLine(col, Offset(w * 0.35f, 2f), Offset(w * 0.35f, h - 2f), strokeWidth = 1.dp.toPx())
            }
            CircuitDiagramKind.AND, CircuitDiagramKind.WIDE_AND,
            CircuitDiagramKind.NAND, CircuitDiagramKind.WIDE_NAND -> {
                path.moveTo(2f, 2f)
                path.lineTo(w * 0.5f, 2f)
                path.arcTo(Rect(2f, 2f, w - 4f, h - 2f), -90f, 180f, false)
                path.lineTo(2f, h - 2f)
                path.close()
                drawPath(path, col, style = stroke)
                if (kind == CircuitDiagramKind.NAND || kind == CircuitDiagramKind.WIDE_NAND) {
                    drawCircle(col, radius = 2.5f, center = Offset(w - 2f, h / 2f), style = stroke)
                }
            }
        }
    }
}

private fun getTechnicalSubtitle(title: String, variationSubtitle: String = ""): String {
    val t = title.lowercase()
    val s = variationSubtitle.lowercase()
    return when {
        // 4-bit adder (7483) must precede "full adder" because Lab 12's title is
        // "4-Bit Binary Full Adder Arithmetic".
        t.contains("7483") || s.contains("7483") ||
            (t.contains("adder") && (t.contains("4-bit") || t.contains("binary full adder"))) ->
            "4-Bit binary full adder with internal fast carry"
        s.contains("full adder") -> "Cascadable 3-input binary adder stage"
        s.contains("half adder") -> "2-Bit modulo-2 sum & carry generator"
        t.contains("half_adder") || t.contains("half adder") -> "2-Bit modulo-2 sum & carry generator"
        t.contains("full_adder") || t.contains("full adder") -> "Cascadable 3-input binary adder stage"
        t.contains("subtractor") -> "Modulo-2 difference & borrow generator"
        t.contains("bcd") || t.contains("7-segment") -> "BCD word to seven active-HIGH segment strokes (7448)"
        t.contains("gray") -> "Unit-distance code conversion by XOR difference (7486)"
        t.contains("comparator") -> "Bit-pair equality via XNOR plus magnitude logic (74266/7486)"
        t.contains("multiplex") -> "2-bit channel-select data routing from AND-OR logic (7408/7432)"
        t.contains("octal") || t.contains("3-to-8") -> "One-of-eight minterm decode from 7411 ANDs / 7410 NANDs"
        // Counter must precede "7476" because exp13's title contains "(7476)".
        t.contains("counter") -> "2-bit asynchronous ripple counter from the 7476 dual J-K"
        t.contains("7474") || t.contains("d flip") -> "Dual D-type positive-edge-triggered flip-flop"
        t.contains("7476") || t.contains("jk") -> "Dual J-K flip-flop with preset and clear"
        t.contains("dac") || t.contains("d/a") || t.contains("weighted") -> "Binary-weighted resistor ladder D/A converter"
        t.contains("latch") -> "Cross-coupled NAND bistable storage element"
        t.contains("clocked") -> "NAND-steered S-R latch, transparent while clocked HIGH"
        t.contains("discrete") -> "Diode and transistor gate primitives from discrete parts"
        t.contains("simplification") -> "One function, two circuits: literal SOP versus reduced form"
        t.contains("universal") -> "Functional completeness from a single gate type"
        t.contains("buffer") -> "Non-inverting drive restoration: Y = A"
        t.contains("xnor") -> "Equality gate: HIGH when inputs match (74266 / 7486+7404)"
        t.contains("4-input") || t.contains("wide") -> "Dual 4-input NAND with NC pins 3 and 11 (7420)"
        Regex("\\bnand\\b").containsMatchIn(t) -> "Universal inverted conjunction operator (7400)"
        Regex("\\bnor\\b").containsMatchIn(t) -> "Universal inverted disjunction operator (7402)"
        Regex("\\bxor\\b").containsMatchIn(t) -> "Logical exclusive disjunction operator (7486)"
        Regex("\\bnot\\b").containsMatchIn(t) || t.contains("inverter") -> "Logical negation operator (7404)"
        Regex("\\band\\b").containsMatchIn(t) -> "Logical conjunction operator (7408)"
        Regex("\\bor\\b").containsMatchIn(t) -> "Logical disjunction operator (7432)"
        else -> "Combinational digital logic module"
    }
}

private fun getTechnicalDescription(title: String, variationSubtitle: String = ""): String {
    val t = title.lowercase()
    val s = variationSubtitle.lowercase()
    return when {
        // 4-bit adder (7483) must precede "full adder" because Lab 12's title is
        // "4-Bit Binary Full Adder Arithmetic".
        t.contains("7483") || s.contains("7483") ||
            (t.contains("adder") && (t.contains("4-bit") || t.contains("binary full adder"))) ->
            "The 7483 4-Bit Binary Full Adder performs high-speed parallel addition of two 4-bit binary words with internal lookahead fast-carry logic, outputting four sum bits (Σ1..Σ4) and carry-out C4."
        s.contains("full adder") ->
            "The Full Adder calculates the sum of three 1-bit inputs: operands A and B plus an incoming carry (Cin). Cascading multiple full adders creates n-bit parallel binary adders."
        s.contains("half adder") ->
            "The Half Adder computes the arithmetic sum of two 1-bit binary inputs (A, B). The sum (Σ) is generated via modulo-2 addition (XOR), and the carry out (Cout) is generated via logical conjunction (AND)."
        t.contains("half_adder") || t.contains("half adder") ->
            "The Half Adder computes the arithmetic sum of two 1-bit binary inputs (A, B). The sum (Σ) is generated via modulo-2 addition (XOR), and the carry out (Cout) is generated via logical conjunction (AND)."
        t.contains("full_adder") || t.contains("full adder") ->
            "The Full Adder calculates the sum of three 1-bit inputs: operands A and B plus an incoming carry (Cin). Cascading multiple full adders creates n-bit parallel binary adders."
        t.contains("subtractor") ->
            "The Subtractor mirrors the adder with one asymmetry: the difference bit is the same XOR as the sum, but the borrow is A'·B — true only when a larger bit is taken from a smaller one. The full subtractor adds a borrow-in and a second product term built from the XNOR of A and B."
        t.contains("bcd") || t.contains("7-segment") ->
            "The 7448 BCD-to-Seven-Segment Decoder reads a 4-bit binary-coded-decimal word and drives the seven active-HIGH segment outputs that light a common-cathode digit's strokes, with lamp-test and blanking controls that override the decode."
        t.contains("gray") ->
            "A Binary-Gray Code Converter maps a binary word to reflected Gray code, where exactly one bit changes between consecutive values. Each Gray bit is the XOR of two adjacent binary bits, so a single 7486 converts four bits with three gates."
        t.contains("comparator") ->
            "The Magnitude Comparator tests two 2-bit numbers for A>B, A=B and A<B. Bit-pair equality comes from XNOR gates — a 74266 directly, or a 7486 XOR followed by a 7404 inverter — and the greater/less verdicts combine the equality bits with AND-OR product terms."
        t.contains("multiplex") ->
            "The 4:1 Multiplexer routes one of four data lines to a single output under a 2-bit select code. Each product term is a 3-input AND split across two 7408 gates, and a 7432 OR tree sums the four gated terms into Y. Read backwards, the same decode fans one data line out to four outputs: a demultiplexer."
        t.contains("octal") || t.contains("3-to-8") ->
            "The 3-to-8 Line Decoder asserts exactly one of eight output lines for each 3-bit input code. A 7404 generates the complements, and the eight minterms are formed by triple 3-input gates — 7411 ANDs for active-HIGH outputs, or 7410 NANDs plus an inversion stage for the same table."
        t.contains("clocked") ->
            "The Clocked S-R Flip-Flop gates a cross-coupled NAND latch with steering gates, so the stored bit can only change while the clock is asserted. The trainer's clock generator or the debounced pulser drives the gate, and the asynchronous inputs override it entirely."
        t.contains("counter") ->
            "The 2-Bit Asynchronous Ripple Counter chains both halves of a 7476 dual J-K flip-flop with J=K=1, so each stage toggles on its falling clock edge. Stage 2 is clocked by stage 1's Q output, which makes the count advance 00, 01, 10, 11 — a divide-by-four with the carry rippling between stages."
        t.contains("latch") ->
            "The S-R Latch cross-couples two NAND gates so each output feeds the other's input, creating a bistable that stores one bit. Asserting ~S sets Q, asserting ~R resets it, and releasing both holds the stored state indefinitely."
        t.contains("dac") || t.contains("d/a") || t.contains("weighted") ->
            "The 4-Bit Weighted-Resistor D/A Converter sums one weighted resistor per bit — each arm half the resistance of the bit below it — so the currents (and the output voltage) scale with the binary place values. Four threshold indicators grade the ladder's monotonicity across all sixteen codes."
        t.contains("discrete") ->
            "Fundamental gates built from discrete components: a diode OR and a diode AND from two diodes and a resistor, and an inverter from a single NPN transistor with a base resistor and collector load. The diode gates degrade their levels; the transistor stage regenerates them from the rails."
        t.contains("simplification") ->
            "Boolean Simplification builds the same function two ways: the literal three-term sum-of-products across four packages, and the factored two-gate form. Sweeping both against one target function measures the algebraic identity instead of asserting it."
        t.contains("universal") ->
            "NAND and NOR are each functionally complete: NOT, AND and OR can all be built from one gate type alone. The NAND build and the NOR build of the same three operations are swept against a single truth table, and their agreement is the proof."
        t.contains("buffer") ->
            "The Non-Inverting Buffer passes logic through unchanged — Y = A — while re-driving it at full TTL strength. Two cascaded 7404 inverters implement NOT(NOT A); a 7432 OR gate with its inputs tied implements A + A. Both restore fan-out capability to a weak source."
        t.contains("xnor") ->
            "The Exclusive-NOR (XNOR) gate outputs HIGH when its inputs are equal and LOW when they differ — the complement of XOR, and the reason it is called the equality gate. The 74266 provides four true XNOR gates; the same function is synthesized from a 7486 XOR plus a 7404 inverter."
        t.contains("4-input") || t.contains("wide") ->
            "The 7420 Dual 4-Input NAND is LOW only when all four inputs are HIGH simultaneously — one gate tests a four-literal product. Pins 3 and 11 are internally not connected, so gate 1's inputs straddle the dead Pin 3 on Pins 1, 2, 4 and 5."
        t.contains("flip") ->
            "Bistable multivibrators store one bit of digital state. State transitions occur synchronously on the triggering clock edge according to excitation inputs (D or J-K) with complementary Q and Q̄ outputs."
        Regex("\\bnand\\b").containsMatchIn(t) ->
            "The 7400 Quad 2-Input NAND gate is functionally complete and universal. Any Boolean function can be realized using NAND networks alone. Output is LOW only when all inputs are simultaneously HIGH."
        Regex("\\bnor\\b").containsMatchIn(t) ->
            "The 7402 Quad 2-Input NOR gate is an inverting OR universal building block. Output is driven HIGH only when all inputs remain at logic level LOW."
        Regex("\\bxor\\b").containsMatchIn(t) ->
            "The 7486 Quad 2-Input XOR gate performs exclusive disjunction (modulo-2 addition). The output is HIGH when inputs differ, and LOW when both inputs share identical logic levels."
        Regex("\\bnot\\b").containsMatchIn(t) || t.contains("inverter") ->
            "Inverting NOT gates (7404 Hex Inverter) implement logical complementation. The output voltage level is inverted relative to the input logic state: HIGH forces LOW, and LOW forces HIGH."
        Regex("\\band\\b").containsMatchIn(t) ->
            "The 7408 Quad 2-Input AND gate implements standard logical conjunction. Output attains logic HIGH (1) if and only if all inputs are HIGH (1). Any LOW (0) pulls the output LOW."
        Regex("\\bor\\b").containsMatchIn(t) ->
            "The 7432 Quad 2-Input OR gate implements logical disjunction. Output is HIGH (1) whenever one or more inputs are HIGH (1). It transitions to LOW only when all inputs are LOW (0)."
        else ->
            "Standard TTL digital logic integrated circuit implementing discrete Boolean switching operations with high noise immunity and deterministic propagation delay."
    }
}
