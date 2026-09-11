package com.logiclabs.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.ChassisDivider
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary

/**
 * Standard content root for every modal sheet on the bench.
 *
 * This exists to make one specific bug unrepeatable. `ModalBottomSheet` measures its
 * content slot with `minWidth = 0`, so a content `Column` that does **not** declare
 * `fillMaxWidth()` sizes itself to its children's intrinsic widths. When such a column
 * held a header `Row` with two long unconstrained `Text`s, the header over-committed the
 * available width, the `LazyColumn` sibling was left a collapsed constraint, and a
 * `fillMaxWidth()` card inside it faithfully filled that near-zero width — which is why
 * `LAB 12: 4-Bit Binary Full Adder Arithmetic` used to render as a one-character-wide
 * vertical column of letters.
 *
 * The three guarantees that fix it, applied here once instead of at each call site:
 *
 * 1. The root is `fillMaxWidth()`, so children are measured against the real sheet width.
 * 2. The title block takes `weight(1f)` inside the header row and its text is bounded by
 *    `maxLines` + ellipsis, so a long title can never starve a sibling.
 * 3. [content] is a plain [ColumnScope] body under a full-width root, so `weight(1f)`
 *    inside it resolves against the sheet, not against child intrinsics.
 *
 * It also owns the inset contract: `navigationBars ∪ displayCutout` padding keeps
 * content clear of the gesture bar and of the cutout in any orientation (a side notch
 * in landscape would otherwise sit over the sheet's edges), and [imePadding] lifts it
 * above the keyboard. Callers should pass `windowInsets = WindowInsets(0)` to
 * `ModalBottomSheet` so those are not applied twice.
 */
@Composable
fun SheetScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Optional leading glyph for the header — e.g. TruthTable on the verifier. */
    titleIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onDismiss: (() -> Unit)? = null,
    accent: Color = AccentCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            // (1) Non-negotiable. Everything below depends on a real width.
            .fillMaxWidth()
            // navigationBars ∪ displayCutout: gesture bar at the bottom, side cutout in
            // landscape. windowInsetsPadding consumes what it applies, so an ancestor
            // that already handled part of the cutout doesn't double it.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(horizontal = Dimens.Space4)
            .padding(bottom = Dimens.Space4)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (titleIcon != null) {
                Icon(
                    imageVector = titleIcon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(Dimens.Space2))
            }

            // (2) weight(1f) plus bounded lines: the title can shrink, never overrun.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LogicLabsType.TitleSm,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = LogicLabsType.TechnicalSm,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onDismiss != null) {
                Spacer(Modifier.width(Dimens.Space2))
                IconButton(
                    onClick = onDismiss,
                    // Full 48dp target; the old sheets used a 26dp IconButton.
                    modifier = Modifier.size(Dimens.MinTouchTarget)
                ) {
                    Icon(
                        imageVector = LogicIcons.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.Space2))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.Hairline)
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .background(ChassisDivider)
        )

        Spacer(Modifier.height(Dimens.Space3))

        // (3) Full-width root above means weight() inside `content` behaves.
        content()
    }
}

/**
 * Drag handle for the bench's sheets: a 36x4dp pill on the chassis divider colour.
 *
 * Pass as `dragHandle = { SheetDragHandle() }`. Material's default handle is tinted from
 * the colour scheme and reads as a stray blue bar against the chassis.
 */
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Space3),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.DragHandleWidth)
                .height(Dimens.DragHandleHeight)
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .background(ChassisDivider)
        )
    }
}
