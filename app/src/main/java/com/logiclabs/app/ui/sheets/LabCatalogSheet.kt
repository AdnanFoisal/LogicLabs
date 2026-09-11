package com.logiclabs.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.core.designsystem.component.InstantSearchBar
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.component.SheetDragHandle
import com.logiclabs.core.designsystem.component.SheetScaffold
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import com.logiclabs.feature.tools.courseware.LabExperiment
import com.logiclabs.feature.tools.courseware.displayName

/**
 * The experiment catalog — and the home of the app's most visible layout defect.
 *
 * Lists [ExperimentCatalog.groups], so it covers the twelve sealed labs and the extended
 * experiments in one place. An experiment that can be built more than one way shows a variation
 * picker on its card and loads whichever build is selected; the twelve classic labs each have a
 * single build and so show no picker.
 *
 * `LAB 12: 4-Bit Binary Full Adder Arithmetic` used to render as a one-character-wide
 * vertical column of letters. The card was innocent: the enclosing `Column` had no
 * `fillMaxWidth()`, so inside `ModalBottomSheet`'s `minWidth = 0` content slot it sized
 * itself to child intrinsics; the header `Row` then held two long unconstrained `Text`s
 * that over-committed the available width, the `LazyColumn` sibling was handed a
 * collapsed constraint, and the `fillMaxWidth()` card inside it faithfully filled that
 * near-zero width.
 *
 * Three structural guarantees now prevent it, and all three live in [SheetScaffold] so
 * they cannot be forgotten at a call site. On top of those, per card: the title is
 * `maxLines = 2` + ellipsis, and the badge strip is a [FlowRow] so the `IN:` descriptor
 * wraps onto its own line instead of being squeezed toward zero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabCatalogSheet(
    activeLabId: String?,
    onDismiss: () -> Unit,
    onLoadLab: (LabExperiment) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groups = remember { ExperimentCatalog.groups }
    val buildCount = remember { ExperimentCatalog.allLabs.size }
    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(searchQuery, groups) {
        if (searchQuery.isBlank()) {
            groups
        } else {
            val q = searchQuery.trim().lowercase()
            groups.filter { g ->
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
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisBase,
        dragHandle = { SheetDragHandle() },
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        SheetScaffold(
            title = "DIGITAL LOGIC EXPERIMENT CATALOG",
            subtitle = if (searchQuery.isBlank()) "${groups.size} experiments · $buildCount complete builds · one tap to load" else "${filteredGroups.size} of ${groups.size} experiments",
            onDismiss = onDismiss
        ) {
            InstantSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "SEARCH EXPERIMENTS...",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Dimens.Space2))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    // heightIn, not height: a 460dp fixed list overflowed short screens.
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space3)
            ) {
                if (filteredGroups.isEmpty()) {
                    item {
                        Text(
                            text = "No experiments matching \"$searchQuery\"",
                            style = LogicLabsType.BodySm,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = Dimens.Space3)
                        )
                    }
                } else {
                    items(filteredGroups, key = { it.key }) { group ->
                        ExperimentCard(
                            group = group,
                            activeLabId = activeLabId,
                            onLoadLab = onLoadLab
                        )
                    }
                }
            }
        }
    }
}

/**
 * One experiment card, showing whichever variation is selected.
 *
 * The selection is card-local state rather than something hoisted to the caller: picking a
 * variation is a browsing action, and only tapping LOAD commits anything to the breadboard. It
 * seeds from [activeLabId] so reopening the sheet shows the build that is actually on the board.
 */
@Composable
private fun ExperimentCard(
    group: ExperimentCatalog.Group,
    activeLabId: String?,
    onLoadLab: (LabExperiment) -> Unit
) {
    var selected by remember(group.key, activeLabId) {
        mutableStateOf(
            group.variations.indexOfFirst { it.id == activeLabId }.let { if (it >= 0) it else 0 }
        )
    }
    val lab = group.variations[selected.coerceIn(0, group.variations.lastIndex)]

    LabCard(
        lab = lab,
        isActive = lab.id == activeLabId,
        variations = group.variations,
        selectedVariation = selected,
        onSelectVariation = { selected = it },
        onLoad = { onLoadLab(lab) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabCard(
    lab: LabExperiment,
    isActive: Boolean,
    variations: List<LabExperiment>,
    selectedVariation: Int,
    onSelectVariation: (Int) -> Unit,
    onLoad: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(if (isActive) SurfaceRaised else SurfaceCard)
            .border(
                width = if (isActive) Dimens.BorderMd else Dimens.Hairline,
                color = if (isActive) AccentCyan else GlassHairline,
                shape = RoundedCornerShape(Dimens.RadiusLg)
            )
            .padding(Dimens.Space3)
    ) {
        Text(
            text = "LAB ${lab.labNumber}: ${lab.title}",
            style = LogicLabsType.TitleSm,
            color = AmberCore,
            // Bounded, so a long title wraps to a second line and stops. It can no
            // longer over-commit the row and starve a sibling into a 1ch column.
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Dimens.Space1))

        Text(
            text = lab.subtitle,
            style = LogicLabsType.TechnicalSm,
            color = AccentCyan,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        if (variations.size > 1) {
            Spacer(Modifier.height(Dimens.Space2))
            VariationPicker(
                variations = variations,
                selected = selectedVariation,
                onSelect = onSelectVariation
            )
        }

        Spacer(Modifier.height(Dimens.Space2))

        Text(
            text = lab.description,
            style = LogicLabsType.BodySm,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Dimens.Space2))

        // FlowRow, not Row: badges keep their intrinsic width and the IN: descriptor
        // moves to the next line rather than being compressed to nothing.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space1)
        ) {
            lab.targetChips.forEach { chip ->
                Badge(text = "IC $chip", accent = AccentCyan)
            }
            if (lab.inputLabels.isNotEmpty()) {
                Badge(
                    text = "IN: ${lab.inputLabels.joinToString(", ")}",
                    accent = AmberCore
                )
            }
            if (lab.outputLabels.isNotEmpty()) {
                Badge(
                    text = "OUT: ${lab.outputLabels.joinToString(", ")}",
                    accent = PhosphorCore
                )
            }
        }

        Spacer(Modifier.height(Dimens.Space3))

        LoadButton(isActive = isActive, onClick = onLoad)
    }
}

/**
 * Chooses between the ways one experiment can be built.
 *
 * A [FlowRow] of chips rather than a `SegmentedControl`: the number of variations is
 * data-driven (two today, but a course sheet can list more), the labels are full phrases like
 * "Variation A · Diode OR gate", and the rail's equal-width segments would ellipsise them into
 * uselessness. Chips keep their intrinsic width and wrap.
 *
 * The variation letter is pulled out of the subtitle so the chip can stay short. `subtitle` is
 * formatted "Variation A · <what it is>" by every contributing file; anything that does not match
 * falls back to its own text, so an unformatted subtitle degrades to a long chip rather than a
 * blank one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariationPicker(
    variations: List<LabExperiment>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "BUILD VARIATION",
            style = LogicLabsType.SwitchPlate,
            color = TextSecondary
        )
        Spacer(Modifier.height(Dimens.Space1))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space1)
        ) {
            variations.forEachIndexed { index, variation ->
                val isPicked = index == selected
                val accent = if (isPicked) AccentCyan else TextSecondary
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.RadiusSm))
                        .background(accent.copy(alpha = if (isPicked) 0.16f else 0.06f))
                        .border(
                            width = if (isPicked) Dimens.BorderMd else Dimens.Hairline,
                            color = accent.copy(alpha = if (isPicked) 0.9f else 0.35f),
                            shape = RoundedCornerShape(Dimens.RadiusSm)
                        )
                        .clickable { onSelect(index) }
                        // 32dp, not the 48dp floor: this is a chip inside an already-tappable
                        // card, sits in a wrapping row of its peers, and the card's own LOAD
                        // button carries the full target. Raising these to 48dp would push a
                        // three-variation strip past the card width on a 360dp screen.
                        .heightIn(min = 32.dp)
                        .padding(horizontal = Dimens.Space2, vertical = Dimens.Space1),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = variationChipLabel(variation.subtitle, index),
                        style = LogicLabsType.TechnicalXs,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The short label for a variation chip: "A · Diode OR gate" out of
 * "Variation A · Diode OR gate".
 *
 * Falls back to the whole subtitle, and then to a bare letter derived from [index], so no chip is
 * ever empty regardless of how a future preset words itself.
 */
private fun variationChipLabel(subtitle: String, index: Int): String {
    val prefix = "Variation "
    if (subtitle.startsWith(prefix)) {
        val rest = subtitle.removePrefix(prefix).trim()
        if (rest.isNotEmpty()) return rest
    }
    if (subtitle.isNotEmpty()) return subtitle
    return ('A' + index).toString()
}

@Composable
private fun Badge(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(accent.copy(alpha = 0.12f))
            .border(Dimens.Hairline, accent.copy(alpha = 0.45f), RoundedCornerShape(Dimens.RadiusSm))
            .padding(horizontal = Dimens.Space2, vertical = 2.dp)
    ) {
        Text(text = text, style = LogicLabsType.TechnicalXs, color = accent)
    }
}

@Composable
private fun LoadButton(isActive: Boolean, onClick: () -> Unit) {
    val accent = if (isActive) PhosphorCore else AccentCyan
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Full 48dp target, which the old 32dp-content Button did not give.
            .heightIn(min = Dimens.MinTouchTarget)
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(accent.copy(alpha = 0.14f))
            .border(Dimens.Hairline, accent, RoundedCornerShape(Dimens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            // Import (arrow arriving at the bench) — the IEC standby glyph read as an
            // on/off symbol, which is not what "load this build" means.
            imageVector = if (isActive) LogicIcons.Reload else LogicIcons.Import,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isActive) "RELOAD THIS BUILD" else "LOAD ONTO BREADBOARD",
            style = LogicLabsType.SwitchPlateLg,
            color = accent,
            maxLines = 1,
            modifier = Modifier.padding(start = Dimens.Space2)
        )
    }
}
