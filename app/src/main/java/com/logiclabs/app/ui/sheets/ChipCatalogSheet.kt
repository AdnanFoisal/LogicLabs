package com.logiclabs.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.core.bridge.catalog.TTLChipCatalog
import com.logiclabs.core.designsystem.component.InstantSearchBar
import com.logiclabs.core.designsystem.component.SheetDragHandle
import com.logiclabs.core.designsystem.component.SheetScaffold
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.TextPrimary
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * TTL part picker.
 *
 * Every layout guard that the old inline sheet was missing now comes from
 * [SheetScaffold]: a full-width root, a bounded header, and a `ColumnScope` body where
 * `weight` resolves against the real sheet width. The list is `heightIn(max=)` rather
 * than a hardcoded `height(320.dp)`, so it shrinks on a short screen instead of forcing
 * the sheet past the viewport.
 *
 * @param onPick receives the part number; the caller decides where on the board it lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChipCatalogSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parts = remember { TTLChipCatalog.getAllPartNumbers() }
    var inspectingPart by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredParts = remember(searchQuery, parts) {
        if (searchQuery.isBlank()) {
            parts
        } else {
            val q = searchQuery.trim().lowercase()
            parts.filter { part ->
                part.lowercase().contains(q) ||
                (TTLChipCatalog.getInfo(part)?.description?.lowercase()?.contains(q) == true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ChassisBase,
        dragHandle = { SheetDragHandle() },
        // SheetScaffold owns navigationBars + ime insets; don't apply them twice.
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        SheetScaffold(
            title = "TTL 74XX COMPONENT CATALOG",
            subtitle = if (searchQuery.isBlank()) "${parts.size} logic families · tap to place on the board" else "${filteredParts.size} of ${parts.size} logic families",
            onDismiss = onDismiss
        ) {
            InstantSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "SEARCH IC CATALOG...",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Dimens.Space2))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
            ) {
                if (filteredParts.isEmpty()) {
                    item {
                        Text(
                            text = "No ICs matching \"$searchQuery\"",
                            style = LogicLabsType.BodySm,
                            color = TextTertiary,
                            modifier = Modifier.padding(vertical = Dimens.Space3)
                        )
                    }
                } else {
                    items(filteredParts, key = { it }) { part ->
                        val info = TTLChipCatalog.getInfo(part)
                        PartRow(
                            part = part,
                            description = info?.description ?: "",
                            pinCount = info?.pinCount ?: 14,
                            onClick = { onPick(part) },
                            onInfoClick = { inspectingPart = part }
                        )
                    }
                }
            }
        }
    }

    inspectingPart?.let { part ->
        IcPinoutInspectorSheet(
            partNumber = part,
            onDismiss = { inspectingPart = null }
        )
    }
}

@Composable
private fun PartRow(
    part: String,
    description: String,
    pinCount: Int,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(SurfaceCard)
            .border(Dimens.Hairline, GlassHairline, RoundedCornerShape(Dimens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // weight(1f) plus bounded lines: a long description can never squeeze the pin
        // badge to zero width, which is the same failure the lab catalog had.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = part,
                style = LogicLabsType.TechnicalMd,
                color = AccentCyan,
                maxLines = 1
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = LogicLabsType.BodySm,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(start = Dimens.Space2)
                .clip(RoundedCornerShape(Dimens.RadiusSm))
                .border(Dimens.Hairline, GlassHairline, RoundedCornerShape(Dimens.RadiusSm))
                .padding(horizontal = Dimens.Space2, vertical = 2.dp)
        ) {
            Text(
                text = "$pinCount PIN",
                style = LogicLabsType.TechnicalXs,
                color = TextTertiary,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .padding(start = Dimens.Space2)
                // 28dp visual, full 48dp hit area — the plain Box would otherwise
                // fall well under the MinTouchTarget floor.
                .minimumInteractiveComponentSize()
                .size(28.dp)
                .clip(RoundedCornerShape(Dimens.RadiusSm))
                .border(Dimens.Hairline, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(Dimens.RadiusSm))
                .clickable(onClick = onInfoClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LogicIcons.Info,
                contentDescription = "Pinout info for $part",
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
