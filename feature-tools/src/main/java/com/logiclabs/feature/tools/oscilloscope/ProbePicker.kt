package com.logiclabs.feature.tools.oscilloscope

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.ElectricalLevel
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.PhosphorDim
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * A named signal the scope can be clipped onto, resolved to an AD-200 socket id.
 *
 * `socket == null` is the explicit "None" entry, which detaches the probe.
 */
data class ProbeSource(val name: String, val socket: Int?)

/**
 * Every source the front panel exposes, in bench order.
 *
 * Built once as a constant list — this is read on every recomposition of the picker,
 * so it must not allocate.
 */
val ProbeSources: List<ProbeSource> = buildList {
    add(ProbeSource("None", null))
    add(ProbeSource("Pulser A", AD200Topology.TERM_PULSER_A_P))
    add(ProbeSource("Pulser B", AD200Topology.TERM_PULSER_B_P))
    add(ProbeSource("Clock", AD200Topology.TERM_CLK))
    add(ProbeSource("Clock̄", AD200Topology.TERM_CLK_INV))
    for (i in 0..7) add(ProbeSource("SW$i", AD200Topology.TERM_SW0 + i))
    for (i in 0..7) add(ProbeSource("LED$i", AD200Topology.TERM_LED0 + i))
}

/**
 * Horizontal scrolling rack of probe sources for one channel.
 *
 * The caller performs the actual write in [onSelect] — normally
 * `settings.probe1Socket = it` (see [ScopeSettings]) which [ScopePanel] then pushes
 * into `circuit.probe1Socket` / `circuit.probe2Socket`.
 *
 * This picker is the missing link that made the scope useless: those two circuit
 * fields were previously assigned only from unit tests, so in the running app both
 * `OscilloscopeBuffer`s stayed all-zeros and both traces drew perfectly flat.
 *
 * @param circuit read (never mutated) to show each source's present logic level.
 * @param channel 1 or 2, used only for the label and the accent colour.
 * @param currentSocket the socket the probe is on now, or null for detached.
 */
@Composable
fun ProbePicker(
    circuit: BreadboardCircuit,
    channel: Int,
    currentSocket: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (channel == 1) Ch1TraceAccent else Ch2TraceAccent
    val sources = remember { ProbeSources }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(SurfaceCard)
            .border(Dimens.Hairline, GlassHairline, RoundedCornerShape(Dimens.RadiusMd))
            .padding(Dimens.Space2)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TODO(tokens): swap for Type.kt silkscreen style once it lands.
            Text(
                text = "CH$channel PROBE",
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = sources.firstOrNull { it.socket == currentSocket }?.name ?: "None",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(Dimens.Space1))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)
        ) {
            items(sources, key = { it.name }) { source ->
                val selected = source.socket == currentSocket
                val live = source.socket?.let {
                    circuit.getSocketLevel(it) == ElectricalLevel.HIGH
                } ?: false
                ProbeChip(
                    name = source.name,
                    selected = selected,
                    live = live,
                    hasIndicator = source.socket != null,
                    accent = accent,
                    onClick = { onSelect(source.socket) }
                )
            }
        }
    }
}

@Composable
private fun ProbeChip(
    name: String,
    selected: Boolean,
    live: Boolean,
    hasIndicator: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            // Dimens.MinTouchTarget is the accessibility floor for interactive chips.
            .heightIn(min = Dimens.MinTouchTarget)
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(if (selected) SurfaceRaised else ChassisBevelShadow)
            .border(
                width = Dimens.Hairline,
                color = if (selected) accent.copy(alpha = 0.75f) else ChassisBevelHighlight,
                shape = RoundedCornerShape(Dimens.RadiusSm)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)
    ) {
        if (hasIndicator) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (live) PhosphorCore else PhosphorDim)
            )
        }
        // TODO(tokens): swap for Type.kt key label style once it lands.
        Text(
            text = name,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> accent
                hasIndicator -> ChassisSilkscreen
                else -> TextTertiary
            },
            maxLines = 1
        )
    }
}
