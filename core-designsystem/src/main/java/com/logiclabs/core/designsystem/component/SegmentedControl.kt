package com.logiclabs.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.Motion
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextOnAccent
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * One segment of a [SegmentedControl].
 *
 * [icon] receives the tint the segment resolved for its current state, so a caller can
 * hand over `LogicIcons.Chip` and get correct colouring for free.
 */
data class SegmentItem(
    val label: String,
    val icon: (@Composable (Color) -> Unit)? = null,
    val enabled: Boolean = true
)

/**
 * Minimum height per segment.
 *
 * Sized for the stacked icon-over-label layout in [Segment]: an 18dp icon, a
 * [Dimens.Space1] gap and one 12dp line of [LogicLabsType.SwitchPlate] come to 34dp of
 * content, plus [Dimens.Space1] of vertical padding top and bottom — 42dp. The 52dp here
 * leaves that a little air and, unlike the 44dp this used to be, clears
 * [Dimens.MinTouchTarget]. This is the only height the rail has — the old
 * `Dimens.ToolRailHeight` is gone, since a 44dp token nothing read would eventually be
 * used to reserve space and clip the thing it was named after.
 */
private val SegmentMinHeight = 52.dp

/**
 * Mode rail with a sliding selection pill.
 *
 * Every segment is laid out with `Modifier.weight(1f)`, so all of them are **exactly**
 * the same width. The hand-rolled toolbar this replaces sized segments to their content
 * and measured anywhere from 132px to 168px across, which made the rail look broken as
 * labels changed length.
 *
 * The pill's position and width animate with [Motion.PillSlideFloat]: a
 * [BoxWithConstraints] gives us the rail width, the pill width is `railWidth / n`, and
 * only the fractional index is animated — one float, no per-segment animation state.
 *
 * Segment content is stacked (icon over label) rather than inline, so a six-segment rail
 * on a phone still has room to spell its legends out. See [Segment].
 */
@Composable
fun SegmentedControl(
    items: List<SegmentItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AccentCyan,
    height: Dp = SegmentMinHeight
) {
    if (items.isEmpty()) return

    val shape = RoundedCornerShape(Dimens.RadiusMd)
    val pillShape = RoundedCornerShape(Dimens.RadiusSm)
    val clampedIndex = selectedIndex.coerceIn(0, items.lastIndex)

    val indexFraction by animateFloatAsState(
        targetValue = clampedIndex.toFloat(),
        animationSpec = Motion.PillSlideFloat,
        label = "segmentPillOffset"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Fixed, not heightIn: the pill and every segment call fillMaxHeight, which
            // needs a bounded max height to resolve against.
            .height(height.coerceAtLeast(SegmentMinHeight))
            .clip(shape)
            .background(SurfaceCard)
            .border(BorderStroke(Dimens.Hairline, SurfaceCardBorder), shape)
            .selectableGroup()
    ) {
        val inset = 3.dp
        val trackWidth = maxWidth - inset * 2
        val pillWidth = trackWidth / items.size

        // Sliding pill, drawn under the labels.
        Box(
            modifier = Modifier
                .padding(inset)
                .offset(x = pillWidth * indexFraction)
                .width(pillWidth)
                .fillMaxHeight()
                .clip(pillShape)
                .background(SurfaceRaised)
                .border(BorderStroke(Dimens.Hairline, accent.copy(alpha = 0.45f)), pillShape)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                Segment(
                    item = item,
                    selected = index == clampedIndex,
                    accent = accent,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Segment(
    item: SegmentItem,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val targetTint = when {
        !item.enabled -> TextTertiary
        selected -> accent
        pressed -> ChassisBevelHighlight
        else -> TextSecondary
    }
    val tint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = Motion.VoltageFade,
        label = "segmentTint"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (pressed && item.enabled) 0.96f else 1f,
        animationSpec = Motion.TactilePress,
        label = "segmentPress"
    )

    Box(
        modifier = modifier
            .heightIn(min = SegmentMinHeight)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                enabled = item.enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .alpha(if (item.enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center
    ) {
        // Icon over label, not beside it. Six segments on a 360dp screen leave ~52dp each;
        // side by side, an 18dp icon plus its gap plus horizontal padding left the label
        // about 20dp, so every legend collapsed to one character and an ellipsis. Stacked,
        // the label gets the segment's whole width and the icon costs height instead —
        // which is why SegmentMinHeight grew to fit both.
        //
        // No horizontal padding at all, for the same reason: the widest legend the rail
        // carries ("DEL WIRE") measures ~50dp, so any inset here would start clipping it.
        // The centred short labels keep plenty of visual air on their own, and the pill
        // behind them is already inset from the track edge.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.Space1),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.Space1)
                .scale(pressScale)
        ) {
            item.icon?.invoke(tint)
            Text(
                text = item.label,
                style = LogicLabsType.SwitchPlate,
                color = if (selected) accent else tint,
                maxLines = 1,
                // Kept as a safety net for a caller-supplied label longer than the rail
                // budgets for, or a large system font scale — not as the normal case.
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Unselected label colour, exported for callers building matching chrome. */
val SegmentInactiveColor: Color = TextSecondary

/** Selected-segment content colour when a filled pill is used instead of an outline. */
val SegmentOnAccentColor: Color = TextOnAccent
