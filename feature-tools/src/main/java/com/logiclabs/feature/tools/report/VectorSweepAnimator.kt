package com.logiclabs.feature.tools.report

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.ChassisDivider
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.PhosphorBloom
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SuccessGreen
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.tools.testbench.TestBenchReport
import com.logiclabs.feature.tools.testbench.TruthTableRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Observable progress of the vector reveal.
 *
 * `:app` reads [revealedRows] to drive haptics per row and [isComplete] to fire the
 * final success buzz. The holder never touches the circuit.
 */
@Stable
class VectorSweepState internal constructor(totalRows: Int) {
    /** Rows revealed so far, 0..[total]. */
    var revealedRows: Int by mutableIntStateOf(0)
        internal set

    /** True once every row has been revealed and the badge has been raised. */
    var isComplete: Boolean by mutableStateOf(false)
        internal set

    var total: Int by mutableIntStateOf(totalRows)
        internal set
}

/**
 * Per-row reveal cadence. Real sweeps are 2 or 4 vectors — every lab in
 * `LabCurriculum` declares only 1 or 2 `switchIndices` — so the pacing is tuned for a
 * handful of rows, not for a 256-vector worst case. The total is clamped by
 * [MAX_TOTAL_REVEAL_MS] so even a synthetic 8-input report finishes promptly.
 */
private const val ROW_REVEAL_MS = 220L
private const val MAX_TOTAL_REVEAL_MS = 2200L
private const val BADGE_DELAY_MS = 180L

/**
 * Drives the reveal purely from an already-computed [TestBenchReport].
 *
 * `TestBenchVerifier.verify` sweeps the circuit **synchronously** — it writes
 * `circuit.switches` and calls `circuit.step()` once per vector, then restores the
 * original switch state. Animating by re-driving it would thrash the engine and could
 * change what the user sees on the breadboard, so this never calls `verify`: the caller
 * invokes it exactly once and hands the resulting report here.
 *
 * Degenerate cases: a 0-row report completes immediately with the badge suppressed;
 * a 1-row report reveals that row then raises the badge.
 */
@Composable
fun rememberVectorSweepState(report: TestBenchReport?, onComplete: (() -> Unit)? = null): VectorSweepState {
    val state = remember(report) { VectorSweepState(report?.rows?.size ?: 0) }

    LaunchedEffect(report) {
        val rows = report?.rows ?: emptyList()
        state.total = rows.size
        state.revealedRows = 0
        state.isComplete = false

        if (rows.isEmpty()) {
            state.isComplete = true
            onComplete?.invoke()
            return@LaunchedEffect
        }

        val perRow = (MAX_TOTAL_REVEAL_MS / rows.size).coerceAtMost(ROW_REVEAL_MS)
        for (i in rows.indices) {
            delay(perRow)
            state.revealedRows = i + 1
        }
        delay(BADGE_DELAY_MS)
        state.isComplete = true
        onComplete?.invoke()
    }

    return state
}

/**
 * Grid geometry. The `#` and state columns are fixed; the input column and the
 * expected/actual pair share the remaining width at a 1 : 2.1 ratio, which is enough
 * for a 3-to-8 decoder's eight output bits in 12sp mono on a 360dp screen. Header and
 * rows repeat this exact weight structure, so the columns stay aligned at any width
 * without any shared measurement.
 */
private val ColIndexWidth = 30.dp
private val ColStateWidth = 46.dp
private const val OutputGroupWeight = 2.1f

/**
 * Contributes the animated truth-table grid to a [LazyListScope].
 *
 * The old `VectorSweepAnimator` composable carried its own box and rendered only the
 * rows it had revealed, inside the dialog's single scroll column — so the column
 * header scrolled away with everything else and a long table pushed the certificate
 * and the actions out of reach. The grid is now items *of* the host list:
 *
 * 1. a live pass-counter strip,
 * 2. a [stickyHeader] with the column labels and the input/output signal names, which
 *    stays pinned while the vectors scroll under it,
 * 3. one item per revealed vector — zebra-striped, with a leading pass/fail rail,
 *    mismatching output bits highlighted in the ACTUAL column, and a per-row
 *    explanation line when a vector fails,
 * 4. the completion seal once the sweep settles.
 *
 * As before, the reveal is purely presentation over an already-sealed report; the
 * circuit is never stepped from here.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.vectorSweep(
    report: TestBenchReport,
    state: VectorSweepState
) {
    item(key = "vector-status", contentType = "vector-status") {
        SweepStatusStrip(report = report, state = state)
    }

    stickyHeader(key = "vector-grid-header", contentType = "vector-grid-header") {
        TruthTableHeader(report = report)
    }

    items(
        count = state.revealedRows,
        key = { "vector-row-$it" },
        contentType = { "vector-row" }
    ) { index ->
        val row = report.rows[index]
        TruthTableRow(
            index = index,
            row = row,
            outputNames = report.outputNames,
            // Captured once, at the moment this item first composes: true only when
            // this row *just* entered the reveal window. A row recomposing because
            // the user scrolled back to it starts fully present, so recycling never
            // replays the reveal.
            animateIn = remember { state.revealedRows == index + 1 }
        )
    }

    if (report.rows.isEmpty()) {
        item(key = "vector-empty", contentType = "vector-empty") {
            Text(
                text = "NO VECTORS TO SWEEP",
                style = LogicLabsType.TechnicalSm,
                color = TextTertiary,
                modifier = Modifier.padding(vertical = Dimens.Space2)
            )
        }
    }

    item(key = "vector-seal", contentType = "vector-seal") {
        if (state.isComplete && report.rows.isNotEmpty()) {
            SealBadge(passed = report.isAllPassed, modifier = Modifier.padding(top = Dimens.Space2))
        }
    }
}

/** Live sweep telemetry: the running pass count while the rows reveal in sequence. */
@Composable
private fun SweepStatusStrip(report: TestBenchReport, state: VectorSweepState) {
    val rows = report.rows
    val revealed = state.revealedRows
    val passedSoFar = remember(revealed) {
        var n = 0
        for (i in 0 until revealed.coerceAtMost(rows.size)) if (rows[i].isPassed) n++
        n
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.Space1),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "VECTOR SWEEP",
            style = LogicLabsType.SwitchPlate,
            color = ChassisSilkscreen
        )
        Text(
            text = "$passedSoFar / ${rows.size} PASS",
            style = LogicLabsType.TechnicalSm.copy(fontWeight = FontWeight.Bold),
            color = if (passedSoFar == revealed) PhosphorCore else ShortCircuitAlert
        )
    }
}

/**
 * The pinned grid header: column labels over the same weight structure as the data
 * rows, and the signal names (SW0/SW1…, LED0…) beneath them — inputs tinted cyan,
 * outputs phosphor, so which side a column belongs to is readable at a glance.
 */
@Composable
private fun TruthTableHeader(report: TestBenchReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Opaque: a pinned header must fully cover the rows scrolling beneath it.
            .background(SurfaceRaised)
            .drawBehind {
                // Bottom rule separating the pinned header from the rows under it.
                // Draw-scope only — no allocations.
                drawLine(
                    color = ChassisDivider,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = Dimens.Space2, vertical = Dimens.Space1)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "#",
                style = LogicLabsType.SwitchPlate,
                color = ChassisSilkscreen,
                modifier = Modifier.width(ColIndexWidth)
            )
            Text(
                text = "INPUTS",
                style = LogicLabsType.SwitchPlate,
                color = ChassisSilkscreen,
                modifier = Modifier.weight(1f)
            )
            Row(modifier = Modifier.weight(OutputGroupWeight)) {
                Text(
                    text = "EXPECTED",
                    style = LogicLabsType.SwitchPlate,
                    color = ChassisSilkscreen,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "ACTUAL",
                    style = LogicLabsType.SwitchPlate,
                    color = ChassisSilkscreen,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "STATE",
                style = LogicLabsType.SwitchPlate,
                color = ChassisSilkscreen,
                modifier = Modifier.width(ColStateWidth)
            )
        }

        Spacer(Modifier.height(2.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(ColIndexWidth))
            Text(
                text = report.inputNames.joinToString(" ").ifEmpty { "—" },
                style = LogicLabsType.TechnicalXs,
                color = AccentCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = report.outputNames.joinToString(" ").ifEmpty { "—" },
                style = LogicLabsType.TechnicalXs,
                color = PhosphorCore,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(OutputGroupWeight)
            )
            Spacer(Modifier.width(ColStateWidth))
        }
    }
}

/**
 * One vector: the input word, the expected output word, the output actually read on
 * the LEDs, and the row verdict. Failing rows carry an alert rail, an alert tint and a
 * plain-language explanation of exactly which output disagreed (or that the bits agree
 * and a bench fault sank the row); passing rows get a quiet success tint.
 */
@Composable
private fun TruthTableRow(
    index: Int,
    row: TruthTableRow,
    outputNames: List<String>,
    animateIn: Boolean
) {
    // Reveal motion: the row fades in and rises ~12dp as the sweep uncovers it. Built
    // on `Animatable` rather than `animateFloatAsState(targetValue = 1f)`, which
    // initialises at its target on first composition and therefore never animated.
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateIn) appear.animateTo(1f, tween(durationMillis = 200))
    }

    val accent = if (row.isPassed) SuccessGreen else ShortCircuitAlert
    val zebra = if (index % 2 == 0) SurfaceCard else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 12.dp.toPx()
            }
            .background(zebra)
            .background(accent.copy(alpha = if (row.isPassed) 0.05f else 0.10f))
            .drawBehind {
                // Leading state rail. Declared after the backgrounds so it draws on
                // top of them; draw-scope only, so a redraw allocates nothing.
                drawRect(
                    color = accent.copy(alpha = if (row.isPassed) 0.55f else 0.95f),
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .padding(horizontal = Dimens.Space2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                style = LogicLabsType.TechnicalXs,
                color = TextTertiary,
                modifier = Modifier.width(ColIndexWidth)
            )
            Text(
                text = bits(row.inputValues),
                style = LogicLabsType.TechnicalMd,
                color = TextPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Row(modifier = Modifier.weight(OutputGroupWeight)) {
                Text(
                    text = bits(row.expectedOutputs),
                    style = LogicLabsType.TechnicalMd,
                    color = TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = actualBits(row),
                    style = LogicLabsType.TechnicalMd,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (row.isPassed) "PASS" else "FAIL",
                style = LogicLabsType.TechnicalXs.copy(fontWeight = FontWeight.Bold),
                color = accent,
                modifier = Modifier.width(ColStateWidth)
            )
        }

        val explanation = mismatchExplanation(row, outputNames)
        if (explanation != null) {
            Text(
                text = explanation,
                style = LogicLabsType.TechnicalXs,
                color = accent.copy(alpha = 0.85f),
                modifier = Modifier.padding(
                    start = Dimens.Space2 + ColIndexWidth,
                    end = Dimens.Space2,
                    top = 2.dp,
                    bottom = Dimens.Space2
                )
            )
        }
    }
}

/**
 * The ACTUAL word with every bit that disagrees with EXPECTED picked out in the alert
 * colour and bold — on a failing row the eye goes straight to the bit that is wrong.
 * Same-length mono strings keep the per-bit columns aligned across EXPECTED/ACTUAL.
 */
private fun actualBits(row: TruthTableRow): AnnotatedString = buildAnnotatedString {
    val expected = row.expectedOutputs
    val actual = row.actualOutputs
    if (actual.isEmpty()) {
        append('-')
        return@buildAnnotatedString
    }
    for (i in actual.indices) {
        val mismatched = i < expected.size && expected[i] != actual[i]
        withStyle(
            SpanStyle(
                color = if (mismatched) ShortCircuitAlert else TextPrimary,
                fontWeight = if (mismatched) FontWeight.Bold else FontWeight.Medium
            )
        ) {
            append(if (actual[i]) '1' else '0')
        }
    }
}

/**
 * Plain-language reason for a failing row, or null when the row passed.
 *
 * A row can fail with perfectly matching bits: `TestBenchVerifier` fails every row when
 * any hard diagnostic (power off, contention, floating LED…) is present. Inventing a
 * bit-level mismatch there would be a lie, so that case says what actually happened.
 */
private fun mismatchExplanation(row: TruthTableRow, outputNames: List<String>): String? {
    if (row.isPassed) return null
    val expected = row.expectedOutputs
    val actual = row.actualOutputs

    if (expected == actual) {
        return "Bits agree — row failed on a bench fault. See diagnostics."
    }
    if (expected.size != actual.size) {
        return "Output width mismatch: expected ${expected.size} bit(s), read ${actual.size}."
    }

    val names = if (outputNames.size == actual.size) outputNames else List(actual.size) { "OUT$it" }
    val parts = mutableListOf<String>()
    for (i in actual.indices) {
        if (expected[i] != actual[i]) {
            parts += "${names[i]}: expected ${if (expected[i]) "1" else "0"} · read ${if (actual[i]) "1" else "0"}"
        }
    }
    return if (parts.isEmpty()) {
        "Bits agree — row failed on a bench fault. See diagnostics."
    } else {
        parts.joinToString("   ")
    }
}

/** Animated completion seal: overshoot scale plus a phosphor bloom ring. */
@Composable
private fun SealBadge(passed: Boolean, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0.86f) }
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(passed) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        bloom.animateTo(
            targetValue = if (passed) 0.34f else 0.24f,
            animationSpec = tween(durationMillis = 420)
        )
    }

    val accent = if (passed) PhosphorCore else ShortCircuitAlert
    val halo = if (passed) PhosphorBloom else ShortCircuitAlert

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(halo.copy(alpha = bloom.value * 0.35f))
            .border(Dimens.BorderMd, accent.copy(alpha = 0.8f), RoundedCornerShape(Dimens.RadiusMd))
            .padding(vertical = Dimens.Space3),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (passed) "PASSED ALL VECTORS" else "VERIFICATION FAILED",
            style = LogicLabsType.TechnicalLg.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = accent
        )
    }
}

private fun bits(values: List<Boolean>): String {
    if (values.isEmpty()) return "-"
    val sb = StringBuilder(values.size)
    for (v in values) sb.append(if (v) '1' else '0')
    return sb.toString()
}
