package com.logiclabs.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.ChassisDivider
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary

/**
 * The app's dialog chassis — one design for every confirmation on the bench.
 *
 * Replaces the ad-hoc `AlertDialog` usages, which had drifted apart (mixed casing,
 * long button rows that overflowed narrow screens, destructive actions stacked
 * against benign ones with nothing but colour to tell them apart, missing disabled
 * states). Every dialog now has:
 *
 *  * a **header** — a tone-tinted icon chip beside a sentence-case title;
 *  * a **message** — one or two calm sentences in secondary text;
 *  * an optional **content slot** under the message (the save dialog's text field);
 *  * an **action stack** — full-width 48dp rows, tone-coloured legends, optional
 *    leading icons, single-line ellipsised labels, with [BenchDialogActionDivider]
 *    to fence destructive actions away from benign ones.
 *
 * The tone of the header chip follows the first-classed action passed via
 * [headerTone]; actions carry their own [BenchDialogTone].
 */
@Composable
fun BenchDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    headerTone: BenchDialogTone = BenchDialogTone.ACCENT,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(Dimens.RadiusXl),
            color = ChassisBase,
            border = BorderStroke(Dimens.BorderMd, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = Dimens.Space5, vertical = Dimens.Space5)) {
                // Header: tone-tinted icon chip + sentence-case title.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Space3)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(Dimens.RadiusMd))
                            .background(headerTone.color.copy(alpha = 0.13f))
                            .border(
                                Dimens.Hairline,
                                headerTone.color.copy(alpha = 0.45f),
                                RoundedCornerShape(Dimens.RadiusMd)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = headerTone.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = LogicLabsType.TitleSm,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(Dimens.Space4))
                Text(
                    text = message,
                    style = LogicLabsType.BodySm,
                    color = TextSecondary
                )

                content()

                Spacer(Modifier.height(Dimens.Space4))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.Hairline)
                        .background(ChassisDivider)
                )
                Spacer(Modifier.height(Dimens.Space1))

                actions()
            }
        }
    }
}

/** Semantic colouring for dialog headers and actions. */
enum class BenchDialogTone(val color: Color) {
    /** The constructive primary — proceed, save, export. Cyan. */
    ACCENT(AccentCyan),

    /** A positive/confirmatory emphasis. Phosphor green. */
    POSITIVE(PhosphorCore),

    /** Destructive — deleting, resetting, exiting without saving. Alert red. */
    DESTRUCTIVE(ShortCircuitAlert),

    /** Low-emphasis — cancel, stay, keep working. */
    NEUTRAL(TextSecondary)
}

/**
 * One dialog action: a full-width 48dp row, right-aligned, with an optional leading
 * glyph. [enabled] renders the legend dimmed and swallows clicks — the save dialog's
 * blank-name case no longer looks tappable.
 */
@Composable
fun BenchDialogAction(
    label: String,
    onClick: () -> Unit,
    tone: BenchDialogTone = BenchDialogTone.ACCENT,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val contentColor = if (enabled) tone.color else TextSecondary.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget)
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.Space2),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Dimens.Space2))
        }
        Text(
            text = label,
            style = LogicLabsType.SwitchPlateLg,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A hairline fence that separates destructive actions from benign ones, so
 * "RESET BOARD" never sits one 4dp gap below "RELOAD EXPERIMENT" with only its
 * colour as warning.
 */
@Composable
fun BenchDialogActionDivider() {
    Spacer(Modifier.height(Dimens.Space1))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space2)
            .height(Dimens.Hairline)
            .background(ChassisDivider.copy(alpha = 0.7f))
    )
    Spacer(Modifier.height(Dimens.Space1))
}
