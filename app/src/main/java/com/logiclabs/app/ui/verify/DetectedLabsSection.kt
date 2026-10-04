package com.logiclabs.app.ui.verify

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.tools.courseware.LabExperiment
import com.logiclabs.feature.tools.courseware.displayName

/**
 * Returns the theme accent color corresponding to the relevance percentage.
 */
private fun relevanceAccent(percent: Int): Color = when {
    percent >= 80 -> PhosphorCore
    percent >= 50 -> AmberCore
    else -> AccentCyan
}

/**
 * Animated horizontal glowing meter bar reflecting candidate match score.
 */
@Composable
private fun RelevanceMeterBar(
    percent: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "relevanceProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(5.dp)
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .background(accentColor)
        )
    }
}

/**
 * Rich industrial metric card for a single candidate lab recommendation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CandidateLabMeterCard(
    candidate: LabRelevanceResult,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = relevanceAccent(candidate.totalScorePercent)
    val shape = RoundedCornerShape(Dimens.RadiusMd)
    val borderStroke = if (isSelected) {
        BorderStroke(1.2.dp, accent)
    } else {
        BorderStroke(Dimens.Hairline, SurfaceCardBorder)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) accent.copy(alpha = 0.08f) else SurfaceCard)
            .border(borderStroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = Dimens.Space2)) {
                Text(
                    text = candidate.lab.displayName,
                    style = LogicLabsType.SwitchPlate,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = candidate.lab.subtitle.ifEmpty { candidate.lab.title },
                    style = LogicLabsType.TechnicalXs,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Big glowing percentage badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.RadiusSm))
                    .background(accent.copy(alpha = 0.16f))
                    .border(Dimens.Hairline, accent.copy(alpha = 0.65f), RoundedCornerShape(Dimens.RadiusSm))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${candidate.totalScorePercent}%",
                    style = LogicLabsType.TechnicalSm.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
        }

        Spacer(Modifier.height(Dimens.Space1))

        RelevanceMeterBar(percent = candidate.totalScorePercent, accentColor = accent)

        Spacer(Modifier.height(Dimens.Space1))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${candidate.matchingVectors}/${candidate.totalVectors} vectors (${candidate.functionalScorePercent}%)",
                style = LogicLabsType.TechnicalXs,
                color = TextTertiary
            )

            if (candidate.matchedIcs.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    candidate.matchedIcs.forEach { ic ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.RadiusPill))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = ic,
                                style = LogicLabsType.TechnicalXs,
                                color = accent
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Zero-state status banner displayed when no lab meets the 35% similarity threshold.
 */
@Composable
fun CustomCircuitBanner(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.RadiusMd)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceCard)
            .border(BorderStroke(Dimens.Hairline, SurfaceCardBorder), shape)
            .padding(Dimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LogicIcons.Labs,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(Dimens.Space2))
        Column {
            Text(
                text = "CUSTOM EXPERIMENTAL CIRCUIT",
                style = LogicLabsType.SwitchPlate,
                color = TextPrimary
            )
            Text(
                text = "No standard coursework lab matched (all < 35%). Custom logic on board.",
                style = LogicLabsType.TechnicalXs,
                color = TextSecondary
            )
        }
    }
}

/**
 * Dedicated section in Truth Table Verifier showing detected candidate lab recommendations.
 */
@Composable
fun DetectedLabsSection(
    candidates: List<LabRelevanceResult>,
    activeLabId: String?,
    onSelectLab: (LabExperiment) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Space1)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = LogicIcons.TruthTable,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(Dimens.Space1))
                Text(
                    text = "CIRCUIT MATCH RELEVANCE",
                    style = LogicLabsType.TechnicalXs.copy(fontWeight = FontWeight.Bold),
                    color = AccentCyan
                )
            }
            if (candidates.isNotEmpty()) {
                Text(
                    text = "TOP ${candidates.size} MATCHES (TAP TO TARGET)",
                    style = LogicLabsType.TechnicalXs,
                    color = TextTertiary
                )
            }
        }

        Spacer(Modifier.height(Dimens.Space1))

        if (candidates.isEmpty()) {
            CustomCircuitBanner()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
                candidates.forEach { candidate ->
                    CandidateLabMeterCard(
                        candidate = candidate,
                        isSelected = candidate.lab.id == activeLabId,
                        onClick = { onSelectLab(candidate.lab) }
                    )
                }
            }
        }
    }
}
