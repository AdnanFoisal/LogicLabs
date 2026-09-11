package com.logiclabs.app.ui.hud

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.core.designsystem.component.GlassSurface
import com.logiclabs.core.designsystem.component.LogicIcons
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.logiclabs.core.bridge.model.SimulationMode
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.Motion
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * Floating command pill that sits over the canvas.
 *
 * Replaces a `TopAppBar` plus four more stacked bars that between them ate roughly a
 * third of the viewport. Nothing here is a surface the board has to make room for — it
 * floats, so the breadboard keeps the full screen and the HUD reads as instrumentation
 * laid over the bench rather than chrome bolted above it.
 *
 * @param verificationPassed `null` before any run, `true`/`false` after. Drives the
 *   badge: a settled check when a run passed, and a slow amber breath while a result is
 *   pending or stale, which is the only animation in the bar that runs unprompted.
 */
@Composable
fun CommandBar(
    chipCount: Int,
    wireCount: Int,
    masterPower: Boolean,
    verificationPassed: Boolean?,
    onVerifyClick: () -> Unit,
    modifier: Modifier = Modifier,
    projectTitle: String? = null,
    onSaveClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    simulationMode: SimulationMode = SimulationMode.IDEAL,
    onToggleSimulationMode: (() -> Unit)? = null,
    hasBurnedChips: Boolean = false,
    onRestoreChips: (() -> Unit)? = null
) {
    GlassSurface(
        modifier = modifier
            // Notch guard. The status bar alone is not enough: in landscape the cutout
            // sits on a short edge of the screen, so statusBars ∪ displayCutout —
            // applied on the top and horizontal sides — is what actually keeps the pill
            // clear of the camera. windowInsetsPadding consumes what it applies, so an
            // ancestor that already handled part of the cutout never pads twice.
            .windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = Dimens.CommandBarInset, vertical = Dimens.Space2)
            .fillMaxWidth()
            .heightIn(min = Dimens.CommandBarHeight),
        shape = RoundedCornerShape(Dimens.RadiusPill)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The whole bar scrolls. With the back control, wordmark, mode pill and
                // save/verify actions all present, the fixed targets alone can exceed a
                // 360dp phone's width — which used to push SAVE and VERIFY off the
                // screen. Scrolling keeps every option reachable instead of clipped.
                .horizontalScroll(rememberScrollState())
                .padding(start = if (onBackClick != null) Dimens.Space2 else Dimens.Space4, end = Dimens.Space2)
                .heightIn(min = Dimens.CommandBarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LogicIcons.Back,
                        contentDescription = "Leave bench",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(Dimens.Space1))
            }

            Wordmark(
                machineLine = if (projectTitle != null) projectTitle.uppercase() else "IDL-800A",
                // Bounded so a long project title cannot stretch the scrollable bar to
                // a page width; the machine line ellipsizes inside the cap.
                modifier = Modifier.widthIn(max = 132.dp)
            )

            Spacer(Modifier.width(Dimens.Space2))

            if (onToggleSimulationMode != null) {
                SimulationModePill(mode = simulationMode, onClick = onToggleSimulationMode)
            }

            if (hasBurnedChips && onRestoreChips != null) {
                Spacer(Modifier.width(Dimens.Space1))
                RestoreIcPill(onClick = onRestoreChips)
            }

            Spacer(Modifier.width(Dimens.Space2))

            // Telemetry readouts, inline between the identity block and the actions.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryPill(label = "IC", value = chipCount.toString())
                TelemetryPill(label = "WIRE", value = wireCount.toString())
                RailPill(live = masterPower)
            }

            Spacer(Modifier.width(Dimens.Space2))

            // Save action: only when the bench owns a saveable context. The wordmark's
            // machine designation doubles as the project title readout.
            if (onSaveClick != null) {
                SaveAction(title = projectTitle, onClick = onSaveClick)
                Spacer(Modifier.width(Dimens.Space2))
            }

            if (onShareClick != null) {
                ShareAction(onClick = onShareClick)
                Spacer(Modifier.width(Dimens.Space2))
            }

            VerifyAction(passed = verificationPassed, onClick = onVerifyClick)
        }
    }
}

@Composable
private fun Wordmark(machineLine: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "LOGIC LABS",
            style = LogicLabsType.SwitchPlateLg,
            color = TextPrimary,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = machineLine,
            style = LogicLabsType.TechnicalXs,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

/**
 * Save action with a compact project label. Sits left of the verify action and carries
 * the same 48dp target and circular chrome so the two read as a matched pair.
 */
@Composable
private fun SaveAction(title: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = LogicLabsType.TechnicalSm,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(Dimens.MinTouchTarget)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Save project" },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.14f))
                    .border(Dimens.Hairline, AccentCyan.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LogicIcons.Save,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Share action button using [LogicIcons.Share].
 */
@Composable
private fun ShareAction(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Share circuit code" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.14f))
                .border(Dimens.Hairline, AccentCyan.copy(alpha = 0.65f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LogicIcons.Share,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Small labelled readout. Values are monospace so digit changes don't shift width. */
@Composable
private fun TelemetryPill(
    label: String,
    value: String,
    accent: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                Dimens.Hairline,
                SurfaceCardBorder,
                RoundedCornerShape(Dimens.RadiusSm)
            )
            .padding(horizontal = Dimens.Space2, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = LogicLabsType.TechnicalXs, color = TextTertiary, softWrap = false)
        Spacer(Modifier.width(4.dp))
        Text(text = value, style = LogicLabsType.TechnicalSm, color = accent, softWrap = false)
    }
}

/**
 * Supply-rail readout whose colour interpolates between live phosphor and a dead red as
 * master power is switched, rather than cutting between two hardcoded colours.
 */
@Composable
private fun RailPill(live: Boolean) {
    val target = if (live) PhosphorCore else ShortCircuitAlert
    val railColor by animateColorAsState(
        targetValue = target,
        animationSpec = Motion.VoltageFade,
        label = "railVoltageColor"
    )

    Row(
        modifier = Modifier
            .widthIn(min = 54.dp)
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(SurfaceCard)
            .border(Dimens.Hairline, SurfaceCardBorder, RoundedCornerShape(Dimens.RadiusPill))
            .padding(horizontal = Dimens.Space2, vertical = 3.dp)
            .semantics {
                contentDescription =
                    if (live) "Supply rail live, 5 volts" else "Supply rail off, 0 volts"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(railColor)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (live) "+5.0V" else "0.0V",
            style = LogicLabsType.TechnicalSm,
            color = railColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Verification action with a state badge.
 *
 * A settled green ring means the last run passed. Amber means there is no current
 * result — either nothing has been run or the board changed since — and it breathes on
 * an `infiniteRepeatable` so a stale result is noticeable without being loud. Red is a
 * recorded failure and deliberately does *not* animate: a failure is a fact, not a
 * prompt.
 */
@Composable
private fun VerifyAction(passed: Boolean?, onClick: () -> Unit) {
    val accent = when (passed) {
        true -> PhosphorCore
        false -> ShortCircuitAlert
        null -> AccentCyan
    }

    val pulse: Float = if (passed == null) {
        val transition = rememberInfiniteTransition(label = "verifyPending")
        val breath by transition.animateFloat(
            initialValue = 0.28f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = Motion.BloomPulse,
                repeatMode = RepeatMode.Reverse
            ),
            label = "verifyBreath"
        )
        breath
    } else {
        0.65f
    }

    Box(
        modifier = Modifier
            .size(Dimens.MinTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = when (passed) {
                    true -> "Verify circuit. Last run passed."
                    false -> "Verify circuit. Last run failed."
                    null -> "Verify circuit. No result yet."
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = pulse * 0.18f))
                .border(Dimens.Hairline, accent.copy(alpha = pulse), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LogicIcons.Verify,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SimulationModePill(
    mode: SimulationMode,
    onClick: () -> Unit
) {
    val isPractical = mode == SimulationMode.PRACTICAL
    val accent = if (isPractical) AmberCore else AccentCyan
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(accent.copy(alpha = 0.15f))
            .border(
                Dimens.Hairline,
                accent.copy(alpha = 0.6f),
                RoundedCornerShape(Dimens.RadiusSm)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space2, vertical = 3.dp)
            .semantics {
                contentDescription = "Simulation Mode: ${mode.name}. Tap to switch."
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isPractical) "PRACTICAL" else "IDEAL",
            style = LogicLabsType.TechnicalSm,
            color = accent
        )
    }
}

@Composable
private fun RestoreIcPill(
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "restorePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "restoreAlpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(ShortCircuitAlert.copy(alpha = 0.22f * alpha))
            .border(
                Dimens.Hairline,
                ShortCircuitAlert.copy(alpha = alpha),
                RoundedCornerShape(Dimens.RadiusSm)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space2, vertical = 3.dp)
            .semantics {
                contentDescription = "Damaged IC detected. Tap to restore IC."
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ShortCircuitAlert)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "RESTORE IC",
            style = LogicLabsType.TechnicalSm,
            color = ShortCircuitAlert
        )
    }
}

