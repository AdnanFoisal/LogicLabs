package com.logiclabs.feature.tools.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AccentCyanDim
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SuccessGreen
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.feature.tools.testbench.TestBenchReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The provenance certificate for one verification run.
 *
 * Replaces the previous treatment of the HMAC seal — 8sp cyan monospace text with no
 * affordances at all — with a full document card: a guilloche security border, the lab
 * identity, a PASS/FAIL seal, the digest in four readable 16-character rows, and the
 * two actions users actually want.
 *
 * @param report the sealed result from `TestBenchVerifier.verify`. Never re-verified here.
 * @param onCopyHash invoked for `COPY HASH`; wire to
 *   `LabCertificatePdf.copyHashToClipboard(context, report.hmacSha256Hash)`.
 * @param onExportPdf invoked for `EXPORT REPORT`; wire to
 *   `LabCertificatePdf.export(context, report)` and share the returned Uri.
 * @param issuedAt display timestamp, in epoch millis. Zero (the default) means "the
 *   moment this report was first shown", captured once per report — re-composing the
 *   card for an hour must not re-stamp it. Pass a stored value when re-displaying an
 *   old report.
 */
@Composable
fun CertificateCard(
    report: TestBenchReport,
    onCopyHash: () -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier,
    issuedAt: Long = 0L
) {
    val passed = report.isAllPassed
    val sealAccent = if (passed) SuccessGreen else ShortCircuitAlert
    val hashRows = remember(report.hmacSha256Hash) { LabCertificatePdf.hashRows(report.hmacSha256Hash) }
    // Keyed on the report itself, not just the hash: the seal is deterministic, so a
    // re-test of the same lab produces the same digest, and the card would otherwise
    // keep showing the first display's timestamp. Composition alone never re-stamps.
    val timestamp = remember(report, issuedAt) {
        val stamp = if (issuedAt != 0L) issuedAt else System.currentTimeMillis()
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(stamp))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(SurfaceCard)
            .border(Dimens.Hairline, GlassHairline, RoundedCornerShape(Dimens.RadiusLg))
    ) {
        // Guilloche security engraving. Purely decorative, drawn behind the content, and
        // built from a single hoisted Path so it allocates nothing per frame.
        val guillochePath = remember { Path() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGuilloche(guillochePath, size.width, size.height)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Space4),
            verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // weight(1f), and the title bounded: both children of this Row were
                // unweighted, and an unweighted child is measured against the full
                // incoming constraints first. A long experiment title therefore took
                // the whole row and pushed SealStamp — measured second — off the right
                // edge, so the PASS/FAIL seal disappeared on exactly the labs whose
                // names are longest. Same defect class as the PDF's overlapping columns.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = Dimens.Space2)
                ) {
                    Silkscreen("VERIFICATION CERTIFICATE")
                    Spacer(modifier = Modifier.height(Dimens.Space1))
                    // TODO(tokens): swap for Type.kt title style once it lands.
                    Text(
                        text = report.experimentTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(Dimens.Space1))
                    Mono(text = timestamp, color = TextSecondary)
                }
                SealStamp(passed = passed, accent = sealAccent)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)
            ) {
                Field(label = "VECTORS", value = "${report.passedCount} / ${report.totalCount}")
                Field(
                    label = "DIAGNOSTICS",
                    value = report.diagnostics.count { it.isError }.let { if (it == 0) "CLEAN" else "$it ERR" },
                    accent = if (report.diagnostics.any { it.isError }) ShortCircuitAlert else PhosphorCore
                )
                Field(label = "ALGORITHM", value = "HMAC-SHA256")
            }

            Spacer(modifier = Modifier.height(Dimens.Space1))
            Silkscreen("PROVENANCE DIGEST")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.RadiusSm))
                    .background(ChassisBevelShadow)
                    .border(Dimens.Hairline, AccentCyanDim, RoundedCornerShape(Dimens.RadiusSm))
                    .padding(Dimens.Space2),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (row in hashRows) {
                    // TODO(tokens): swap for Type.kt digest style once it lands.
                    Text(
                        text = row,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AccentCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Space1))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
            ) {
                ActionButton(
                    text = "COPY HASH",
                    icon = LogicIcons.Copy,
                    accent = AccentCyan,
                    onClick = onCopyHash,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "EXPORT REPORT",
                    icon = LogicIcons.Export,
                    accent = PhosphorCore,
                    onClick = onExportPdf,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SealStamp(passed: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(accent.copy(alpha = 0.12f))
            .border(Dimens.BorderMd, accent.copy(alpha = 0.85f), RoundedCornerShape(Dimens.RadiusSm))
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2)
    ) {
        // TODO(tokens): swap for Type.kt seal style once it lands.
        Text(
            text = if (passed) "PASS" else "FAIL",
            fontSize = 16.sp,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

@Composable
private fun Field(label: String, value: String, accent: Color = TextPrimary) {
    Column {
        Silkscreen(label)
        Spacer(modifier = Modifier.height(2.dp))
        Mono(text = value, color = accent, bold = true)
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            // Dimens.MinTouchTarget is the accessibility floor for these actions.
            .heightIn(min = Dimens.MinTouchTarget)
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(SurfaceRaised)
            .border(Dimens.Hairline, accent.copy(alpha = 0.6f), RoundedCornerShape(Dimens.RadiusSm))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // TODO(tokens): swap for Type.kt button style once it lands.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            modifier = Modifier.padding(start = Dimens.Space1)
        )
    }
}

@Composable
private fun Silkscreen(text: String) {
    // TODO(tokens): swap for Type.kt silkscreen style once it lands.
    Text(
        text = text,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = ChassisSilkscreen
    )
}

@Composable
private fun Mono(text: String, color: Color, bold: Boolean = false) {
    // TODO(tokens): swap for Type.kt mono body style once it lands.
    Text(
        text = text,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color
    )
}

/**
 * Concentric offset lissajous strokes — the cheap trick that reads as banknote
 * guilloche engraving. Each band is one closed curve traced at a slightly different
 * radius and phase, so the bands beat against each other and produce the interference
 * moire real guilloche has.
 *
 * The [path] is supplied by the caller and reset per band, so a full redraw allocates
 * nothing.
 */
private fun DrawScope.drawGuilloche(path: Path, w: Float, h: Float) {
    if (w <= 0f || h <= 0f) return
    val inset = 6f
    val cx = w / 2f
    val cy = h / 2f
    val rx = (w / 2f) - inset
    val ry = (h / 2f) - inset
    if (rx <= 8f || ry <= 8f) return

    for (band in 0 until GUILLOCHE_BANDS) {
        val shrink = 1f - band * 0.045f
        val phase = band * 0.7f
        val lobes = GUILLOCHE_LOBES + band
        path.reset()
        var first = true
        var t = 0f
        while (t <= TWO_PI) {
            // Base ellipse modulated by a small radial ripple: r(θ) = 1 + a·sin(kθ+φ).
            val ripple = 1f + GUILLOCHE_AMPLITUDE * sin(lobes * t + phase)
            val x = cx + rx * shrink * ripple * cos(t)
            val y = cy + ry * shrink * ripple * sin(t)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            t += GUILLOCHE_STEP
        }
        path.close()
        drawPath(
            path = path,
            color = GuillocheInk.copy(alpha = 0.16f - band * 0.02f),
            style = GuillocheStroke
        )
    }
}

private const val GUILLOCHE_BANDS = 5
private const val GUILLOCHE_LOBES = 11
private const val GUILLOCHE_AMPLITUDE = 0.035f
private const val TWO_PI = (2.0 * Math.PI).toFloat()
private const val GUILLOCHE_STEP = TWO_PI / 220f

private val GuillocheStroke = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val GuillocheInk = AccentCyan
