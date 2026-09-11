package com.logiclabs.feature.breadboard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.designsystem.component.GlassSurface
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary

/**
 * Compact glass pill zoom controls (+ / - / FIT) positioned in the top-right corner of the canvas.
 * Does not obstruct columns 50-64 or navigation insets.
 */
@Composable
internal fun BoxScope.BreadboardControls(
    state: BreadboardCanvasState,
    mapper: com.logiclabs.feature.breadboard.canvas.BreadboardGeometryMapper,
    circuit: BreadboardCircuit? = null,
    isWireModeActive: Boolean = false,
    hasSelection: Boolean = false,
    onDeleteEquipment: (() -> Unit)? = null,
    onToggleWireMode: (() -> Unit)? = null
) {
    GlassSurface(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = Dimens.Space3, top = Dimens.Space3),
        shape = RoundedCornerShape(Dimens.RadiusPill)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ZoomPillButton(icon = LogicIcons.ZoomIn, contentDescription = "Zoom in") {
                state.nudgeZoom(1.25f)
            }
            ZoomPillButton(icon = LogicIcons.ZoomOut, contentDescription = "Zoom out") {
                state.nudgeZoom(1f / 1.25f)
            }
            ZoomPillButton("FIT", isLabel = true) { state.applyFit(mapper, circuit) }
        }
    }
}

@Composable
private fun ZoomPillButton(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String? = null,
    isLabel: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            // Compact 30dp pill visual, but the touch target is expanded to the
            // 48dp floor — these are the board's primary zoom controls.
            .minimumInteractiveComponentSize()
            .size(if (isLabel) 36.dp else 30.dp, 30.dp)
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(SurfaceCard.copy(alpha = 0.6f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = label.orEmpty(),
                style = if (isLabel) LogicLabsType.SwitchPlate else LogicLabsType.TitleSm,
                color = if (isLabel) TextPrimary else AccentCyan
            )
        }
    }
}

/** Hint strip shown while a tap-to-connect wire is half-drawn. */
@Composable
internal fun BoxScope.TapToConnectHint(
    visible: Boolean,
    armedLabel: String? = null
) {
    if (!visible) return
    Card(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = Dimens.Space2),
        shape = RoundedCornerShape(Dimens.RadiusPill),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(Dimens.Hairline, AmberCore)
    ) {
        Text(
            text = if (armedLabel == null) {
                "Tap a second hole to finish the wire"
            } else {
                "Armed $armedLabel — tap a second hole to connect"
            },
            color = AmberCore,
            style = LogicLabsType.BodySm,
            modifier = Modifier.padding(horizontal = Dimens.Space4, vertical = Dimens.Space1)
        )
    }
}

/**
 * Gentle empty-bench affordance, shown only while the board carries no chips and no
 * wires — the first thing a fresh sandbox shows. Disappears for good the moment
 * anything is placed, and never appears for a loaded lab or project.
 */
@Composable
internal fun BoxScope.EmptyBenchHint(visible: Boolean) {
    if (!visible) return
    Card(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = Dimens.Space4),
        shape = RoundedCornerShape(Dimens.RadiusPill),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.85f)),
        border = BorderStroke(Dimens.Hairline, GlassHairline)
    ) {
        Text(
            text = "Empty bench — open ICs to place a chip, then WIRE to connect",
            color = TextSecondary,
            style = LogicLabsType.BodySm,
            modifier = Modifier.padding(horizontal = Dimens.Space4, vertical = Dimens.Space2)
        )
    }
}
