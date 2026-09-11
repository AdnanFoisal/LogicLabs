package com.logiclabs.app.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.core.bridge.model.JumperWire
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.designsystem.component.GlassSurface
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.Motion
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.core.designsystem.theme.wireColorOf

/**
 * Contextual action ribbon, shown only while something is selected or a wire is being
 * drawn.
 *
 * This replaces two permanently-visible bars — a wire-action ribbon and a separate
 * "active wire colour" row — plus a duplicated colour palette. The two palettes had
 * different swatch sizes and wrote to different targets, and both offered only 8 of the
 * 10 `WireColor` entries, so `GRAY` and `BROWN` were unreachable from the UI entirely.
 * There is now one [WirePalette] used for both jobs and it enumerates `WireColor.entries`,
 * so adding a colour to the model surfaces it here automatically.
 */
@Composable
fun ContextRibbon(
    selectedWire: JumperWire?,
    selectedChipLabel: String?,
    activeWireColor: WireColor,
    wireModeActive: Boolean,
    onWireColorPicked: (WireColor) -> Unit,
    onFlipWire: () -> Unit,
    onToggleRouting: () -> Unit,
    onDeleteSelection: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    onNudgeChipLeft: (() -> Unit)? = null,
    onNudgeChipRight: (() -> Unit)? = null,
    onRotateChip: (() -> Unit)? = null
) {
    val visible = selectedWire != null || selectedChipLabel != null || wireModeActive

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Motion.DurationSheetSlide)) +
            expandVertically(tween(Motion.DurationSheetSlide)),
        exit = fadeOut(tween(Motion.DurationSwitchTravel)) +
            shrinkVertically(tween(Motion.DurationSwitchTravel)),
        modifier = modifier
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.CommandBarInset),
            shape = RoundedCornerShape(Dimens.RadiusLg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2),
                verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Header. weight(1f) so a long chip label ellipsizes instead of
                    // pushing the action buttons off the edge.
                    Text(
                        text = when {
                            selectedWire != null ->
                                "WIRE · ${selectedWire.color.displayName.uppercase()}"
                            selectedChipLabel != null -> "IC · $selectedChipLabel"
                            else -> "WIRE COLOUR"
                        },
                        style = LogicLabsType.SwitchPlate,
                        color = AccentCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.weight(1f)
                    )

                    if (selectedChipLabel != null) {
                        if (onNudgeChipLeft != null) {
                            RibbonIcon(
                                icon = LogicIcons.Back,
                                label = "Move IC left",
                                onClick = onNudgeChipLeft
                            )
                        }
                        if (onNudgeChipRight != null) {
                            RibbonIcon(
                                icon = LogicIcons.ChevronRight,
                                label = "Move IC right",
                                onClick = onNudgeChipRight
                            )
                        }
                        if (onRotateChip != null) {
                            RibbonIcon(
                                icon = LogicIcons.Rotate,
                                label = "Rotate IC 180 degrees",
                                onClick = onRotateChip
                            )
                        }
                    }

                    if (selectedWire != null) {
                        RibbonIcon(
                            icon = LogicIcons.Flip,
                            label = "Flip wire ends",
                            onClick = onFlipWire
                        )
                        RibbonIcon(
                            icon = if (selectedWire.isManhattan) LogicIcons.RouteCurve
                            else LogicIcons.RouteSquare,
                            label = if (selectedWire.isManhattan) "Curved routing"
                            else "Right-angle routing",
                            onClick = onToggleRouting
                        )
                    }

                    if (selectedWire != null || selectedChipLabel != null) {
                        RibbonIcon(
                            icon = LogicIcons.Trash,
                            label = "Delete selection",
                            tint = ShortCircuitAlert,
                            onClick = onDeleteSelection
                        )
                        RibbonIcon(
                            icon = LogicIcons.Close,
                            label = "Clear selection",
                            onClick = onClearSelection
                        )
                    }
                }

                // The palette edits the selected wire when there is one, and otherwise
                // sets the colour the next wire will be drawn in. While merely arming
                // the next colour it collapses to a one-line strip — the full
                // two-row 48dp-target grid used to sit on screen the entire time wire
                // mode was active, eating ~100dp of the bench. It expands on tap, and
                // is always expanded while a wire is selected (colour editing is then
                // the job at hand).
                if (selectedWire != null || wireModeActive) {
                    val current = selectedWire?.color ?: activeWireColor
                    var paletteOpen by remember { mutableStateOf(false) }
                    val expanded = selectedWire != null || paletteOpen

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.RadiusMd))
                            .background(SurfaceCard)
                            .clickable(enabled = selectedWire == null) { paletteOpen = !paletteOpen }
                            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(wireColorOf(current.hexArgb))
                                    .border(2.dp, TextPrimary, CircleShape)
                            )
                            Text(
                                text = if (selectedWire != null) {
                                    "WIRE COLOUR — TAP A SWATCH TO RECOLOUR"
                                } else {
                                    "NEXT WIRE · ${current.displayName.uppercase()}"
                                },
                                style = LogicLabsType.SwitchPlate,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                        if (selectedWire == null) {
                            Icon(
                                imageVector = LogicIcons.ChevronRight,
                                contentDescription = if (expanded) "Hide colour palette" else "Show colour palette",
                                tint = TextTertiary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(if (expanded) 270f else 90f)
                            )
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        WirePalette(
                            current = current,
                            onPick = onWireColorPicked
                        )
                    }
                }
            }
        }
    }
}

/**
 * All ten insulation colours as tappable swatches.
 *
 * Enumerates `WireColor.entries` rather than a hand-written list, which is how `GRAY` and
 * `BROWN` become reachable. Ten swatches in a single row cannot all meet
 * [Dimens.MinTouchTarget] (≈30dp each on a 360dp screen), so the palette wraps into two
 * rows of full 48dp cells with a 18–22dp visual — the same treatment the Settings
 * wire-palette received.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WirePalette(
    current: WireColor,
    onPick: (WireColor) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        WireColor.entries.forEach { wc ->
            val selected = wc == current
            Box(
                modifier = Modifier
                    .size(Dimens.MinTouchTarget)
                    .clip(CircleShape)
                    .clickable { onPick(wc) }
                    .semantics { contentDescription = wc.displayName },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 22.dp else 18.dp)
                        .clip(CircleShape)
                        .background(wireColorOf(wc.hexArgb))
                        .border(
                            width = if (selected) 2.dp else Dimens.Hairline,
                            color = if (selected) TextPrimary else SurfaceCardBorder,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun RibbonIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = TextTertiary
) {
    Box(
        modifier = Modifier
            .size(Dimens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
