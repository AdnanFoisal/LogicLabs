package com.logiclabs.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.Motion
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.TextSecondary

/** Dead-rail red for [VoltagePill]. Desaturated so it reads "off", not "error". */
private val RailDeadRed = Color(0xFF7F1D1D)

private val LedDotSize = 6.dp

/**
 * Chassis telemetry readout: a silkscreened [label] and a monospaced [value].
 *
 * The value uses [LogicLabsType.TechnicalSm] (Roboto Mono) so digits are tabular — a
 * gate count ticking 9 → 10 must not shift the pill's width. Labels use
 * [LogicLabsType.SwitchPlate]; pass them **already uppercased**.
 */
@Composable
fun TelemetryPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.RadiusPill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(SurfaceCard)
            .border(BorderStroke(Dimens.Hairline, accent.copy(alpha = 0.35f)), shape)
            .padding(horizontal = Dimens.Space2, vertical = Dimens.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)
    ) {
        Text(
            text = label,
            style = LogicLabsType.SwitchPlate,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = LogicLabsType.TechnicalSm,
            color = accent,
            maxLines = 1
        )
    }
}

/**
 * Supply-rail indicator. Reads `+5.0V` when the bench is energised and `0.0V` when it
 * is not, and crossfades its accent between [PhosphorCore] and a dead-rail red over
 * [Motion.VoltageFade] so power-up feels like a supply coming up rather than a boolean
 * flipping.
 */
@Composable
fun VoltagePill(
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val accent by animateColorAsState(
        targetValue = if (isLive) PhosphorCore else RailDeadRed,
        animationSpec = Motion.VoltageFade,
        label = "vccAccent"
    )

    val shape = RoundedCornerShape(Dimens.RadiusPill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(SurfaceCard)
            .border(BorderStroke(Dimens.Hairline, accent.copy(alpha = 0.4f)), shape)
            .padding(horizontal = Dimens.Space2, vertical = Dimens.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)
    ) {
        // Tiny pilot lamp; same accent, so it tracks the crossfade for free.
        Canvas(modifier = Modifier.size(LedDotSize)) {
            drawCircle(color = accent)
        }
        Text(
            text = "VCC",
            style = LogicLabsType.SwitchPlate,
            color = TextSecondary,
            maxLines = 1
        )
        Text(
            text = if (isLive) "+5.0V" else "0.0V",
            style = LogicLabsType.TechnicalSm,
            color = accent,
            maxLines = 1
        )
    }
}
