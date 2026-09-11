package com.logiclabs.app.ui.verify

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.logiclabs.core.designsystem.component.InstantSearchBar
import com.logiclabs.feature.tools.courseware.displayName
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.core.data.progress.Badge
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.component.SheetDragHandle
import com.logiclabs.core.designsystem.component.SheetScaffold
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SuccessGreen
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import com.logiclabs.feature.tools.courseware.LabExperiment
import com.logiclabs.feature.tools.feedback.Haptics
import com.logiclabs.feature.tools.report.CertificateCard
import com.logiclabs.feature.tools.report.LabCertificatePdf
import com.logiclabs.feature.tools.report.rememberVectorSweepState
import com.logiclabs.feature.tools.report.vectorSweep
import com.logiclabs.feature.tools.testbench.TestBenchReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Truth-table verification result, as a bench sheet.
 *
 * Why this is a [ModalBottomSheet] and not a dialog: the old `AlertDialog` was a
 * wrap-content surface centered in the window, and nothing bounded its *total* height
 * — only the text column was capped, at 460dp. Title + 460dp + buttons + dialog
 * padding exceeds a landscape phone's ~360dp window, so the alert was clipped at both
 * ends and the DISMISS button sat below the screen: the verifier "sometimes got
 * hidden". A bottom sheet is bounded by the window by construction, and the layout
 * below is three fixed regions — sticky header, weighted scroll body, sticky footer —
 * so the actions are reachable in every orientation and at every font scale.
 *
 * The important architectural point is unchanged: this sheet **never calls
 * `TestBenchVerifier.verify`**. The verifier's sweep is synchronous and drives
 * `circuit.switches` up to 2^N times; animating it by re-running it would step the
 * engine repeatedly. The caller runs `verify` exactly once and passes the finished
 * [TestBenchReport] here, and [vectorSweep] reveals the rows it already contains. The
 * circuit is stepped precisely as many times as before.
 *
 * @param report the sealed result, or null while nothing has been run.
 * @param newBadges badges this passing run earned for the first time. The bench's
 *   single verification call both records the lab and returns what became newly
 *   earned; the reveal here is presentation-only and happens once per run.
 * @param onSelectLab re-targets and re-verifies against a different experiment. That
 *   does step the circuit again — it is a fresh verification, not an animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifierDialog(
    report: TestBenchReport?,
    activeLabId: String?,
    newBadges: List<Badge> = emptyList(),
    onSelectLab: (LabExperiment) -> Unit,
    onRetest: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bodyListState = rememberLazyListState()

    // Presentation-side sweep over an already-computed report, with the completion
    // haptic fired once when the last row lands. A null report never buzzes.
    val sweepState = rememberVectorSweepState(report) {
        val result = report ?: return@rememberVectorSweepState
        if (result.isAllPassed) Haptics.success(view) else Haptics.reject(view)
    }

    // The badge reveal is hoisted above the list: an item that scrolls out of the
    // viewport and back must not replay its entrance or re-fire the success haptic.
    // Badges land only after the sweep itself has settled, staggered.
    var revealedBadges by remember(report) { mutableIntStateOf(0) }
    LaunchedEffect(report, newBadges) {
        revealedBadges = 0
        if (report != null && report.isAllPassed && newBadges.isNotEmpty()) {
            snapshotFlow { sweepState.isComplete }.first { it }
            newBadges.forEachIndexed { index, _ ->
                delay(650)
                revealedBadges = index + 1
                Haptics.success(view)
            }
        }
    }

    // A fresh report starts the body at the top: the diagnostics and the first
    // vectors are on screen when the reveal begins.
    LaunchedEffect(report) { bodyListState.scrollToItem(0) }

    // Slide the sheet out instead of blinking the overlay away.
    val dismissWithAnimation: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = dismissWithAnimation,
        sheetState = sheetState,
        containerColor = ChassisBase,
        dragHandle = { SheetDragHandle() },
        // SheetScaffold owns navigationBars + ime insets; don't apply them twice.
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        SheetScaffold(
            title = "TRUTH TABLE VERIFIER",
            subtitle = report?.experimentTitle,
            titleIcon = LogicIcons.TruthTable,
            onDismiss = dismissWithAnimation
        ) {
            // --- Sticky region 1: the verdict at a glance + the retarget strip ------
            VerdictBar(report = report)
            Spacer(Modifier.height(Dimens.Space2))
            LabSelectorStrip(
                activeLabId = activeLabId,
                reportTitle = report?.experimentTitle,
                onSelect = onSelectLab
            )
            Spacer(Modifier.height(Dimens.Space2))

            // --- The one scrollable region -------------------------------------------
            LazyColumn(
                state = bodyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    // ModalBottomSheet measures its content against the window, so the
                    // weighted child resolves to "whatever the title, verdict bar, lab
                    // strip and footer left behind" — never more than fits on screen.
                    // fill = false lets short content shrink-wrap the sheet instead of
                    // stretching it, and the 640dp ceiling keeps it sheet-sized on
                    // tablets.
                    .weight(1f, fill = false)
                    .heightIn(max = 640.dp)
            ) {
                if (report == null) {
                    item(key = "empty-hint") { EmptyResultHint() }
                } else {
                    report.diagnostics.forEachIndexed { index, issue ->
                        item(key = "diag-$index", contentType = "diagnostic") {
                            Column {
                                DiagnosticBanner(message = issue.message, isError = issue.isError)
                                Spacer(Modifier.height(Dimens.Space1))
                            }
                        }
                    }

                    // Sticky-headered truth-table grid with the animated reveal.
                    vectorSweep(report = report, state = sweepState)

                    // The once-per-run badge reveal, after the sweep settles.
                    if (report.isAllPassed && newBadges.isNotEmpty()) {
                        items(
                            count = newBadges.size,
                            key = { "badge-$it" },
                            contentType = { "badge-reveal" }
                        ) { index ->
                            Column {
                                BadgeRevealRow(
                                    badge = newBadges[index],
                                    visible = index < revealedBadges
                                )
                                Spacer(Modifier.height(Dimens.Space2))
                            }
                        }
                    }

                    item(key = "certificate", contentType = "certificate") {
                        Column {
                            Spacer(Modifier.height(Dimens.Space1))
                            CertificateCard(
                                report = report,
                                onCopyHash = {
                                    LabCertificatePdf.copyHashToClipboard(context, report.hmacSha256Hash)
                                    Haptics.tick(view)
                                    Toast.makeText(context, "Provenance hash copied", Toast.LENGTH_SHORT).show()
                                },
                                onExportPdf = {
                                    val uri = LabCertificatePdf.export(context, report)
                                    if (uri == null) {
                                        Toast.makeText(context, "Could not write the report", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Haptics.tick(view)
                                        shareReport(context, uri)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // --- Sticky region 2: the actions ----------------------------------------
            Spacer(Modifier.height(Dimens.Space3))
            VerifierFooter(onDismiss = dismissWithAnimation, onRetest = onRetest)
        }
    }
}

private fun shareReport(context: android.content.Context, uri: android.net.Uri) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(
            android.content.Intent.createChooser(intent, "Share lab report")
        )
    } catch (_: Throwable) {
        Toast.makeText(context, "No app available to share a PDF", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Compact summary header: the elapsed-style verdict chip on the left, the sweep
 * shape (N inputs → M outputs, vectors passed, sweep time) on the right.
 */
@Composable
private fun VerdictBar(report: TestBenchReport?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerdictChip(report = report)
        Column(
            // weight(1f) so the stats ellipsize instead of pushing the chip out of
            // the row — the unweighted-sibling defect class SheetScaffold documents.
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            if (report == null) {
                Text(
                    text = "Pick a target experiment, then RE-TEST",
                    style = LogicLabsType.TechnicalSm,
                    color = TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            } else {
                Text(
                    text = "${signalInCount(report)} IN → ${signalOutCount(report)} OUT",
                    style = LogicLabsType.TechnicalSm,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${report.passedCount}/${report.totalCount} VECTORS · ${report.sweepDurationMs} ms",
                    style = LogicLabsType.TechnicalXs,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** PASS / FAIL / STANDBY pill with the sweep's elapsed time — the verdict at a glance. */
@Composable
private fun VerdictChip(report: TestBenchReport?) {
    val passed = report?.isAllPassed
    val accent = when (passed) {
        true -> SuccessGreen
        false -> ShortCircuitAlert
        null -> TextTertiary
    }
    val icon = when (passed) {
        true -> LogicIcons.Verify
        false -> LogicIcons.Close
        null -> LogicIcons.Info
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(accent.copy(alpha = 0.14f))
            .border(Dimens.BorderMd, accent.copy(alpha = 0.75f), RoundedCornerShape(Dimens.RadiusPill))
            .padding(horizontal = Dimens.Space3, vertical = Dimens.Space1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Dimens.Space2))
        Text(
            text = when (passed) {
                true -> "PASS"
                false -> "FAIL"
                null -> "STANDBY"
            },
            style = LogicLabsType.SwitchPlateLg,
            color = accent
        )
        if (report != null) {
            Spacer(Modifier.width(Dimens.Space2))
            Text(
                text = "${report.sweepDurationMs} ms",
                style = LogicLabsType.TechnicalSm,
                color = accent.copy(alpha = 0.8f)
            )
        }
    }
}

private fun signalInCount(report: TestBenchReport): Int =
    report.inputNames.size.takeIf { it > 0 }
        ?: report.rows.firstOrNull()?.inputValues?.size
        ?: 0

private fun signalOutCount(report: TestBenchReport): Int =
    report.outputNames.size.takeIf { it > 0 }
        ?: report.rows.firstOrNull()?.actualOutputs?.size
        ?: 0

/** The target lab selector — the strip users retarget the sweep with. */
@Composable
private fun LabSelectorStrip(
    activeLabId: String?,
    reportTitle: String?,
    onSelect: (LabExperiment) -> Unit
) {
    val stripState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredLabs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            ExperimentCatalog.allLabs
        } else {
            val q = searchQuery.trim().lowercase()
            ExperimentCatalog.allLabs.filter { lab ->
                lab.displayName.lowercase().contains(q) ||
                lab.title.lowercase().contains(q) ||
                lab.subtitle.lowercase().contains(q) ||
                lab.id.lowercase().contains(q) ||
                lab.targetChips.any { it.lowercase().contains(q) }
            }
        }
    }

    // Keep the active target in view: the strip covers every catalog entry, so the
    // selected experiment is frequently off-screen to the right.
    LaunchedEffect(activeLabId, reportTitle, filteredLabs) {
        val index = filteredLabs.indexOfFirst { lab ->
            lab.id == activeLabId || (activeLabId == null && reportTitle == lab.title)
        }
        if (index >= 0) stripState.scrollToItem(index)
    }

    Column {
        InstantSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "SEARCH LABS...",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Dimens.Space2))

        Text(
            text = if (searchQuery.isBlank()) "TARGET EXPERIMENT" else "TARGET EXPERIMENT (${filteredLabs.size})",
            style = LogicLabsType.SwitchPlate,
            color = TextSecondary
        )
        Spacer(Modifier.height(Dimens.Space1))
        if (filteredLabs.isEmpty()) {
            Text(
                text = "No experiments matching \"$searchQuery\"",
                style = LogicLabsType.BodySm,
                color = TextTertiary,
                modifier = Modifier.padding(vertical = Dimens.Space2)
            )
        } else {
            LazyRow(
                state = stripState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)
            ) {
                // allLabs, not classicLabs: the strip is how a user retargets the sweep, and with
                // extended experiments loadable from the catalog a classic-only strip would show no
                // selection at all for the build actually on the board.
                items(filteredLabs, key = { it.id }) { lab ->
                    val selected = lab.id == activeLabId ||
                        (activeLabId == null && reportTitle == lab.title)
                    LabTargetChip(lab = lab, selected = selected, onClick = { onSelect(lab) })
                }
            }
        }
    }
}

/** One target chip: lab ordinal prominent, the ICs it uses de-emphasised beside it. */
@Composable
private fun LabTargetChip(lab: LabExperiment, selected: Boolean, onClick: () -> Unit) {
    val chips = lab.targetChips.joinToString("/")
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(if (selected) AccentCyan.copy(alpha = 0.18f) else SurfaceCard)
            .border(
                width = if (selected) Dimens.BorderMd else Dimens.Hairline,
                color = if (selected) AccentCyan else GlassHairline,
                shape = RoundedCornerShape(Dimens.RadiusSm)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = lab.displayName,
            style = LogicLabsType.TechnicalSm.copy(fontWeight = FontWeight.Bold),
            color = if (selected) AccentCyan else TextSecondary
        )
        if (chips.isNotEmpty()) {
            Spacer(Modifier.width(Dimens.Space1))
            Text(
                text = chips,
                style = LogicLabsType.TechnicalXs,
                color = if (selected) AccentCyan.copy(alpha = 0.75f) else TextTertiary,
                maxLines = 1
            )
        }
    }
}

/** Shown while no verification has been run for the current target. */
@Composable
private fun EmptyResultHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Space6),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "NO RESULT YET",
            style = LogicLabsType.SwitchPlate,
            color = TextTertiary
        )
        Spacer(Modifier.height(Dimens.Space1))
        Text(
            text = "Run a verification to seal a report. Pick a target experiment above and press RE-TEST.",
            style = LogicLabsType.BodySm,
            color = TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dimens.Space4)
        )
    }
}

/**
 * One badge reveal: the row scales in after the sweep has had the stage for a beat.
 * Visibility is hoisted by the caller, so it happens exactly once per run — the
 * durable record lives on the Home rack and in the achievements drawer.
 */
@Composable
private fun BadgeRevealRow(badge: Badge, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.72f,
            // Same under-damped throw as Motion.TactilePress, typed for scaleIn.
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f)
        ) + fadeIn()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.RadiusLg))
                .background(AmberCore.copy(alpha = 0.10f))
                .border(Dimens.BorderMd, AmberCore.copy(alpha = 0.55f), RoundedCornerShape(Dimens.RadiusLg))
                .padding(Dimens.Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LogicIcons.Rosette,
                contentDescription = null,
                tint = AmberCore,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(Dimens.Space3))
            Column {
                Text(
                    text = "BADGE EARNED",
                    style = LogicLabsType.SwitchPlate,
                    color = AmberCore,
                    maxLines = 1
                )
                Text(
                    text = badge.title,
                    style = LogicLabsType.TitleSm,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    text = badge.description,
                    style = LogicLabsType.BodySm,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
    }
}

/** Sticky footer actions: dismiss, and re-run the sweep on the current circuit. */
@Composable
private fun VerifierFooter(
    onDismiss: () -> Unit,
    onRetest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        FooterButton(
            text = "DISMISS",
            icon = LogicIcons.Close,
            accent = TextSecondary,
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        )
        FooterButton(
            text = "RE-TEST",
            icon = LogicIcons.Reload,
            accent = AccentCyan,
            onClick = onRetest,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FooterButton(
    text: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = Dimens.MinTouchTarget)
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(accent.copy(alpha = 0.12f))
            .border(Dimens.Hairline, accent.copy(alpha = 0.7f), RoundedCornerShape(Dimens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Dimens.Space2))
        Text(
            text = text,
            style = LogicLabsType.SwitchPlateLg,
            color = accent,
            maxLines = 1
        )
    }
}

/** Bench-fault banner, with a leading severity rail matching the grid's row rails. */
@Composable
private fun DiagnosticBanner(message: String, isError: Boolean) {
    val accent: Color = if (isError) ShortCircuitAlert else AmberCore
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(accent.copy(alpha = 0.12f))
            .drawBehind {
                // Leading severity rail. Declared after the background so it draws on
                // top of it; draw-scope only, so a redraw allocates nothing.
                drawRect(
                    color = accent.copy(alpha = 0.85f),
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
            .padding(
                start = Dimens.Space3,
                top = Dimens.Space1,
                end = Dimens.Space2,
                bottom = Dimens.Space2
            )
    ) {
        Text(
            text = if (isError) "BENCH ERROR" else "NOTE",
            style = LogicLabsType.SwitchPlate,
            color = accent
        )
        Spacer(Modifier.height(Dimens.Space1))
        Text(
            text = message,
            style = LogicLabsType.BodySm,
            color = accent
        )
    }
}
