package com.logiclabs.app.ui.screens.bench

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontFamily
import com.logiclabs.core.data.persistence.CircuitShareBytecode
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.bridge.topology.AD200Topology
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.logiclabs.app.di.AppContainer
import com.logiclabs.app.ui.component.BenchDialog
import com.logiclabs.app.ui.component.BenchDialogAction
import com.logiclabs.app.ui.component.BenchDialogActionDivider
import com.logiclabs.app.ui.component.BenchDialogTone
import com.logiclabs.app.ui.hud.CommandBar
import com.logiclabs.app.ui.hud.ContextRibbon
import com.logiclabs.app.ui.hud.ToolAction
import com.logiclabs.app.ui.hud.ToolRail
import com.logiclabs.app.ui.navigation.BenchRequest
import com.logiclabs.app.ui.sheets.ChipCatalogSheet
import com.logiclabs.app.ui.sheets.LabCatalogSheet
import com.logiclabs.app.ui.sheets.ScopeSheet
import com.logiclabs.app.ui.state.BoardMode
import com.logiclabs.app.ui.state.Overlay
import com.logiclabs.app.ui.state.rememberWorkbenchState
import com.logiclabs.app.ui.verify.VerifierDialog
import com.logiclabs.core.bridge.model.SimulationMode
import com.logiclabs.core.bridge.model.JumperWire
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.data.persistence.ProjectPersistence
import com.logiclabs.core.data.progress.Badge
import com.logiclabs.core.data.session.SessionKind
import com.logiclabs.core.data.settings.Settings
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.CanvasBackground
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.feature.breadboard.ui.BreadboardCanvas
import com.logiclabs.feature.instruments.ui.TrainerConsoleDock
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import com.logiclabs.feature.tools.courseware.LabCurriculum
import com.logiclabs.feature.tools.courseware.LabExperiment
import com.logiclabs.feature.tools.diagram.BooleanDiagramView
import com.logiclabs.feature.tools.feedback.Haptics
import com.logiclabs.feature.tools.testbench.TestBenchReport
import com.logiclabs.feature.tools.testbench.TestBenchVerifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The workbench.
 *
 * This file is an orchestrator and nothing else: it owns no drawing, no measurement and
 * no hardware styling. Chrome lives in `ui/hud`, modals in `ui/sheets` and `ui/verify`,
 * the board in `:feature-breadboard`, the console in `:feature-instruments`, and every
 * mutable value in [com.logiclabs.app.ui.state.WorkbenchState].
 *
 * What it opens is decided by [benchRequest] (resolved once per composition): a
 * curriculum lab, a saved project, the recovery continuation of the last session, or a
 * clean sandbox. Every successful load is recorded in the session store, which is what
 * the Home screen's CONTINUE card and the next Continue launch rebuild from.
 *
 * Two things it deliberately keeps:
 *
 * 1. **Undo-able deletion.** Wire and chip removal both push a snackbar with UNDO that
 *    re-adds the equipment with its original id, so a mis-tap on a 48dp target is
 *    recoverable. Restoring by id matters — the netlist keys off it.
 * 2. **A single verification entry point.** [runVerification] calls
 *    `TestBenchVerifier.verify` exactly once per invocation and hands the sealed report
 *    to [VerifierDialog], which animates it presentation-side. A passing run is also
 *    recorded with the progress repository exactly once, which is where the badge
 *    reveals come from.
 */
@Composable
fun LogicLabsMainScreen(
    benchRequest: BenchRequest,
    container: AppContainer,
    onNavigateHome: () -> Unit
) {
    val bench = rememberWorkbenchState()
    val circuit = bench.circuit
    val view = LocalView.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var activeLab by remember { mutableStateOf<LabExperiment?>(null) }
    var report by remember { mutableStateOf<TestBenchReport?>(null) }
    var newBadges by remember { mutableStateOf<List<Badge>>(emptyList()) }

    // Project context: non-null while the bench is backed by a saved (or about-to-be
    // saved) project file. Labs and the sandbox clear it; opening a project sets it.
    var currentProjectId by remember { mutableStateOf<String?>(null) }
    var currentProjectTitle by remember { mutableStateOf<String?>(null) }
    var saveAsOpen by remember { mutableStateOf(false) }
    var shareDialogOpen by remember { mutableStateOf(false) }
    var confirmLeaveBench by remember { mutableStateOf(false) }
    var resetFitTrigger by remember { mutableStateOf(0) }

    DisposableEffect(context, bench) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "com.logiclabs.ACTION_BUILD_STEP") {
                    val step = intent.getIntExtra("step", -1)
                    when (step) {
                        0 -> { // Clear all, power on
                            bench.driveTopology {
                                it.clearAll()
                                it.masterPower = true
                            }
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        1 -> { // Power bus rails
                            bench.driveTopology {
                                it.addWire(AD200Topology.TERM_POWER_VCC, AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 0), WireColor.RED)
                                it.addWire(AD200Topology.TERM_POWER_GND, AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 0), WireColor.BLACK)
                            }
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        2 -> { // Add 7408 AND Gate
                            bench.driveTopology { it.addChip("7408", 1, 12) }
                            com.logiclabs.feature.tools.feedback.Haptics.thud(view)
                        }
                        3 -> { // VCC to chip
                            bench.driveTopology {
                                val chip = it.placedChips.firstOrNull { c -> c.placedIc.partNumber == "7408" }
                                if (chip != null) {
                                    it.addWire(chip.getPinSocket(14), AD200Topology.railSocket(AD200Topology.RAIL_TOP_VCC_5V, 12), WireColor.RED)
                                }
                            }
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        4 -> { // GND to chip
                            bench.driveTopology {
                                val chip = it.placedChips.firstOrNull { c -> c.placedIc.partNumber == "7408" }
                                if (chip != null) {
                                    it.addWire(chip.getPinSocket(7), AD200Topology.railSocket(AD200Topology.RAIL_TOP_GND, 12), WireColor.BLACK)
                                }
                            }
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        5 -> { // Wire SW0 to Pin 1
                            bench.driveTopology {
                                val chip = it.placedChips.firstOrNull { c -> c.placedIc.partNumber == "7408" }
                                if (chip != null) {
                                    it.addWire(AD200Topology.TERM_SW0, chip.getPinSocket(1), WireColor.YELLOW)
                                }
                            }
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        6 -> { // Wire SW1 to Pin 2
                            bench.driveTopology {
                                val chip = it.placedChips.firstOrNull { c -> c.placedIc.partNumber == "7408" }
                                if (chip != null) {
                                    it.addWire(AD200Topology.TERM_SW1, chip.getPinSocket(2), WireColor.ORANGE)
                                }
                            }
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        7 -> { // Wire Pin 3 to LED0
                            bench.driveTopology {
                                val chip = it.placedChips.firstOrNull { c -> c.placedIc.partNumber == "7408" }
                                if (chip != null) {
                                    it.addWire(chip.getPinSocket(3), AD200Topology.TERM_BOT_LED0, WireColor.GREEN)
                                }
                            }
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        8 -> { // Toggle SW0
                            bench.toggleSwitch(0)
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        9 -> { // Toggle SW1
                            bench.toggleSwitch(1)
                            com.logiclabs.feature.tools.feedback.Haptics.tick(view)
                        }
                        10 -> { // Open Save Project Dialog
                            saveAsOpen = true
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("com.logiclabs.ACTION_BUILD_STEP")
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    BackHandler {
        if (bench.overlay != Overlay.NONE) {
            bench.overlay = Overlay.NONE
        } else if (saveAsOpen) {
            saveAsOpen = false
        } else if (shareDialogOpen) {
            shareDialogOpen = false
        } else {
            confirmLeaveBench = true
        }
    }

    /** Re-adds a deleted wire with its original id, so UNDO is a true inverse. */
    fun restoreWire(w: JumperWire) = bench.driveTopology {
        it.addWire(w.startSocket, w.endSocket, w.color, w.isManhattan, w.id)
    }

    fun deleteSelection() {
        val wire = bench.selectedWire()
        if (wire != null) {
            bench.driveTopology { it.removeWire(wire.id) }
            Haptics.tick(view)
            scope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = "Jumper wire removed",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) restoreWire(wire)
            }
            return
        }

        val chipId = bench.selectedChipId
        val chip = chipId?.let { id -> circuit.placedChips.find { it.placedIc.id == id } }
        if (chip != null) {
            val ic = chip.placedIc
            bench.driveTopology { it.removeChip(ic.id) }
            Haptics.tick(view)
            scope.launch {
                val res = snackbarHostState.showSnackbar(
                    message = "IC ${ic.partNumber} removed",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    bench.driveTopology {
                        it.addChip(ic.partNumber, ic.trench, ic.startColumn, ic.isRotated180, ic.id)
                    }
                }
            }
            return
        }

        bench.overlay = Overlay.BOARD_ACTIONS
    }

    /**
     * Seals one report. Verifies against the loaded lab when there is one, otherwise
     * against whichever curriculum lab matches the first placed chip — the same fallback
     * the bench has always used, so an unguided build still gets a meaningful sweep.
     */
    fun runVerification() {
        val lab = activeLab ?: run {
            val firstChip = circuit.placedChips.firstOrNull()?.placedIc?.partNumber
            // Searches classicLabs first by construction — ExperimentCatalog.allLabs puts the
            // twelve sealed labs ahead of the extended ones — so an unguided 7400 build still
            // falls back to lab2_nand rather than to an extended experiment that also uses it.
            ExperimentCatalog.allLabs.find { it.targetChips.contains(firstChip) }
                ?: LabCurriculum.classicLabs[1]
        }
        val sealed = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            inputNames = lab.inputLabels,
            outputNames = lab.outputLabels,
            experimentTitle = lab.title
        )
        // The sweep left the switches wherever the last vector put them; republish so the
        // console shows the board's real state rather than a stale mirror.
        bench.syncFromCircuit()
        report = sealed
        bench.lastVerificationPassed = sealed.isAllPassed
        bench.overlay = Overlay.VERIFIER
        if (sealed.isAllPassed) {
            // Recorded exactly once per passing run; the returned set is what the
            // verifier dialog reveals — empty for a re-verified lab, by construction.
            scope.launch {
                newBadges = container.progress.markLabVerified(lab.id, LabCurriculum.classicLabs.size)
            }
        } else {
            newBadges = emptyList()
        }
    }

    fun loadLab(lab: LabExperiment) {
        activeLab = lab
        currentProjectId = null
        currentProjectTitle = null
        bench.clearSelection()
        report = null
        bench.lastVerificationPassed = null
        bench.driveTopology {
            it.clearAll()
            lab.buildCircuit(it)
        }
        Haptics.thud(view)
        bench.overlay = Overlay.NONE
        container.session.recordLab(lab.id)
    }

    fun loadSandbox() {
        activeLab = null
        currentProjectId = null
        currentProjectTitle = null
        bench.clearSelection()
        report = null
        bench.lastVerificationPassed = null
        bench.driveTopology {
            it.clearAll()
            it.masterPower = true
        }
        Haptics.thud(view)
        bench.overlay = Overlay.NONE
        container.session.recordSandbox()
    }

    /** Restores a saved project; a missing file degrades to the intro lab, not a crash. */
    suspend fun loadProject(projectId: String) {
        val project = container.projects.load(projectId)
        if (project == null) {
            loadLab(LabCurriculum.classicLabs[1])
            return
        }
        ProjectPersistence.deserialize(project, circuit)
        activeLab = project.activeLabId?.let { id ->
            ExperimentCatalog.allLabs.find { it.id == id }
        }
        currentProjectId = project.id
        currentProjectTitle = project.title
        bench.clearSelection()
        report = null
        bench.lastVerificationPassed = null
        bench.syncTopology()
        bench.syncFromCircuit()
        Haptics.thud(view)
        bench.overlay = Overlay.NONE
        container.session.recordProject(projectId)
    }

    /**
     * Writes the bench into the project store. A brand-new title (or a first save from
     * a lab/sandbox) mints a project and counts it in the stats; re-saving overwrites.
     */
    fun performSave(title: String) {
        scope.launch {
            val base = ProjectPersistence.serialize(circuit, title, activeLab?.id)
            val withId = currentProjectId?.let { base.copy(id = it) } ?: base
            val stored = container.projects.save(withId)
            currentProjectId = stored.project.id
            currentProjectTitle = stored.project.title
            container.session.recordProject(stored.project.id)
            if (stored.isNew) {
                val earned = container.progress.incrementCircuitsBuilt()
                Haptics.success(view)
                snackbarHostState.showSnackbar(
                    message = if (earned.isEmpty()) {
                        "Project saved: ${stored.project.title}"
                    } else {
                        "Project saved · Badge earned: ${earned.first().title}"
                    },
                    duration = SnackbarDuration.Short
                )
            } else {
                Haptics.tick(view)
                snackbarHostState.showSnackbar(
                    message = "Project saved: ${stored.project.title}",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // Resolve the opening request exactly once per composition. A recomposition (or a
    // config change, which the manifest absorbs without recreation) must not reload the
    // board; a fresh arrival from Home is a new composition and correctly re-resolves.
    LaunchedEffect(benchRequest) {
        when (val request = benchRequest) {
            BenchRequest.Continue -> {
                val session = container.session.current()
                when (session?.kind) {
                    SessionKind.LAB -> {
                        val lab = ExperimentCatalog.allLabs.find { it.id == session.id }
                        if (lab != null) loadLab(lab) else loadLab(LabCurriculum.classicLabs[1])
                    }
                    SessionKind.PROJECT -> {
                        val id = session.id
                        if (id != null) loadProject(id) else loadLab(LabCurriculum.classicLabs[1])
                    }
                    // A sandbox continuation restores the autosave draft if one exists,
                    // which is what makes "Continue" lossless for free builds.
                    SessionKind.SANDBOX -> {
                        val draft = container.projects.loadDraft()
                        if (draft != null && (draft.chips.isNotEmpty() || draft.wires.isNotEmpty())) {
                            ProjectPersistence.deserialize(draft, circuit)
                            activeLab = draft.activeLabId?.let { id ->
                                ExperimentCatalog.allLabs.find { it.id == id }
                            }
                            bench.clearSelection()
                            report = null
                            bench.lastVerificationPassed = null
                            bench.syncTopology()
                            bench.syncFromCircuit()
                            container.session.recordSandbox()
                        } else {
                            loadSandbox()
                        }
                    }
                    null -> {
                        // First ever launch: the intro NAND build, the same seed the
                        // app has always opened with.
                        loadLab(LabCurriculum.classicLabs[1])
                    }
                }
            }
            is BenchRequest.Lab -> {
                val lab = ExperimentCatalog.allLabs.find { it.id == request.labId }
                if (lab != null) loadLab(lab) else loadLab(LabCurriculum.classicLabs[1])
            }
            is BenchRequest.Project -> loadProject(request.projectId)
            BenchRequest.Sandbox -> {
                // An explicit sandbox request is a deliberate fresh start.
                container.projects.clearDraft()
                loadSandbox()
            }
        }
    }

    // Bench time accrues in coarse one-minute chunks while the workbench is actually
    // on screen; leaving the destination cancels the loop, so backgrounded time never
    // counts. This feeds the Home rack's BENCH readout and the Bench Time badge.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            container.progress.addBenchTime(60_000)
        }
    }

    // Settings that the bench itself consumes: the armed wire colour and the afterglow
    // gate. Applied as the settings change, so a toggle in Settings lands here live.
    val settings by container.settings.settings.collectAsState(initial = Settings())
    LaunchedEffect(settings.defaultWireColor) {
        runCatching { WireColor.valueOf(settings.defaultWireColor) }
            .getOrNull()
            ?.let { bench.activeWireColor = it }
    }
    LaunchedEffect(settings.ledAfterglow) {
        bench.afterglowEnabled = settings.ledAfterglow
        bench.syncFromCircuit()
    }

    // Draft autosave: every topology change arms a four-second debounced write, so a
    // burst of wiring settles into one draft rather than ten. Leaving the bench
    // disposes this effect and writes whatever is armed immediately.
    LaunchedEffect(bench.topologyVersion, settings.autosaveEnabled) {
        if (!settings.autosaveEnabled || bench.topologyVersion <= 1) return@LaunchedEffect
        delay(4_000)
        container.projects.saveDraftAsync(
            ProjectPersistence.serialize(
                circuit,
                currentProjectTitle ?: defaultProjectTitle(activeLab),
                activeLab?.id
            ).copy(id = currentProjectId ?: "")
        )
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val breadboardPane: @Composable (Modifier) -> Unit = { paneModifier ->
        Column(modifier = paneModifier) {
            CommandBar(
                chipCount = bench.chipCount,
                wireCount = bench.wireCount,
                masterPower = bench.masterPower,
                verificationPassed = bench.lastVerificationPassed,
                onVerifyClick = { runVerification() },
                projectTitle = currentProjectTitle,
                onSaveClick = {
                    val title = currentProjectTitle
                    if (title != null) {
                        performSave(title)
                    } else {
                        saveAsOpen = true
                    }
                },
                onShareClick = { shareDialogOpen = true },
                onBackClick = { confirmLeaveBench = true },
                simulationMode = bench.simulationMode,
                onToggleSimulationMode = { bench.toggleSimulationMode() },
                hasBurnedChips = bench.hasBurnedChips,
                onRestoreChips = { bench.restoreBurnedChips() }
                // No modifier: CommandBar applies its own notch-aware status inset.
            )

            ToolRail(
                boardMode = bench.boardMode,
                onAction = { action ->
                    Haptics.tick(view)
                    when (action) {
                        ToolAction.CHIPS -> bench.overlay = Overlay.CHIP_CATALOG
                        ToolAction.SCOPE -> bench.overlay = Overlay.SCOPE
                        ToolAction.LABS -> bench.overlay = Overlay.LAB_CATALOG
                        ToolAction.ROAM -> {
                            bench.boardMode = BoardMode.ROAM
                            bench.clearSelection()
                        }
                        ToolAction.FIT_VIEW -> {
                            bench.clearSelection()
                            resetFitTrigger++
                        }
                        ToolAction.ROTATE -> {
                            val id = bench.selectedChipId
                            if (id != null) {
                                bench.driveTopology { it.rotateChip(id) }
                                Haptics.tick(view)
                            } else {
                                android.widget.Toast.makeText(context, "Select an IC first to rotate it", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        ToolAction.CLEAR -> deleteSelection()
                        ToolAction.DIAGRAM -> {
                            // Version switch: breadboard ⇄ boolean diagram. Topology,
                            // selection and console state are untouched, so toggling
                            // back restores the bench (re-fitted to the circuit).
                            bench.viewingDiagram = !bench.viewingDiagram
                        }
                    }
                },
                onModeChange = { mode ->
                    Haptics.tick(view)
                    bench.boardMode = mode
                    if (mode != BoardMode.WIRE_SELECT) bench.clearSelection()
                },
                deleteLabel = when {
                    bench.selectedWireId != null -> "DEL WIRE"
                    bench.selectedChipId != null -> "DEL IC"
                    else -> "CLEAR"
                },
                simulationModeLabel = if (bench.simulationMode == SimulationMode.PRACTICAL) "PRACTICAL" else "IDEAL",
                chipCount = bench.chipCount,
                wireCount = bench.wireCount,
                diagramActive = bench.viewingDiagram
            )

            ContextRibbon(
                selectedWire = bench.selectedWire(),
                selectedChipLabel = bench.selectedChipId?.let { id ->
                    circuit.placedChips.find { it.placedIc.id == id }?.placedIc?.partNumber
                },
                activeWireColor = bench.activeWireColor,
                wireModeActive = bench.boardMode == BoardMode.WIRE_DRAW,
                onWireColorPicked = { color -> recolorSelection(bench, color) },
                onFlipWire = { flipSelectedWire(bench) },
                onToggleRouting = { toggleSelectedRouting(bench) },
                onDeleteSelection = { deleteSelection() },
                onClearSelection = { bench.clearSelection() },
                onNudgeChipLeft = {
                    bench.selectedChipId?.let { id ->
                        val chip = circuit.placedChips.find { it.placedIc.id == id }?.placedIc
                        if (chip != null && chip.startColumn > 0) {
                            bench.driveTopology { it.moveChip(id, chip.trench, chip.startColumn - 1) }
                            Haptics.tick(view)
                        }
                    }
                },
                onNudgeChipRight = {
                    bench.selectedChipId?.let { id ->
                        val chip = circuit.placedChips.find { it.placedIc.id == id }?.placedIc
                        if (chip != null && chip.startColumn < 55) {
                            bench.driveTopology { it.moveChip(id, chip.trench, chip.startColumn + 1) }
                            Haptics.tick(view)
                        }
                    }
                },
                onRotateChip = {
                    val id = bench.selectedChipId
                    if (id != null) {
                        bench.driveTopology { it.rotateChip(id) }
                        Haptics.tick(view)
                    } else {
                        android.widget.Toast.makeText(context, "Select an IC first to rotate it", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.padding(vertical = Dimens.Space1)
            )

            // The canvas takes the slack: everything above it is intrinsically sized, so
            // it grows on a tall screen instead of the board being pinned to a constant.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (bench.viewingDiagram) {
                    // The boolean (gate-level schematic) rendering of the same circuit:
                    // a view *version* switch, not a mode change — the engine, selection
                    // and console state below are untouched, so LOGIC toggles back to
                    // the physical breadboard exactly as it was left.
                    BooleanDiagramView(
                        circuit = circuit,
                        modifier = Modifier.fillMaxSize(),
                        circuitVersion = bench.topologyVersion,
                        renderVersion = bench.renderVersion
                    )
                } else {
                    BreadboardCanvas(
                        circuit = circuit,
                        modifier = Modifier.fillMaxSize(),
                        activeWireColor = bench.activeWireColor,
                        defaultWireManhattan = settings.defaultRoutingManhattan,
                        isWireModeActive = bench.boardMode == BoardMode.WIRE_DRAW,
                        isWireSelectMode = bench.boardMode == BoardMode.WIRE_SELECT,
                        selectedWireId = bench.selectedWireId,
                        selectedChipId = bench.selectedChipId,
                        circuitVersion = bench.topologyVersion,
                        renderVersion = bench.renderVersion,
                        resetFitTrigger = resetFitTrigger,
                        onWireSelected = { id -> bench.selectWire(id) },
                        onChipSelected = { id -> bench.selectChip(id) },
                        onDeleteEquipment = { deleteSelection() },
                        onToggleWireMode = {
                            bench.boardMode =
                                if (bench.boardMode == BoardMode.WIRE_DRAW) BoardMode.ROAM
                                else BoardMode.WIRE_DRAW
                        },
                        onSocketSelected = { Haptics.tick(view) },
                        onCircuitChanged = {
                            bench.syncTopology()
                            bench.syncFromCircuit()
                        }
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = CanvasBackground,
        // Zero here, and every child that meets a system bar or the cutout applies its
        // own inset: the command bar takes statusBars ∪ displayCutout at the top, the
        // dock and the snackbar host take navigationBars ∪ displayCutout at the bottom,
        // and SheetScaffold takes navigationBars ∪ displayCutout + ime. Letting Scaffold
        // pad as well would double every one of them.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                )
            )
        }
    ) { _ ->
        // Landscape side notches (and portrait corner cutouts): the bench stays
        // edge-to-edge, but no pane is ever laid out under the camera cutout. This
        // consumes the horizontal cutout for everything below, so the command bar's
        // and the dock's own cutout padding never double it.
        val cutoutHorizontal = WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(cutoutHorizontal)
                    .background(CanvasBackground)
            ) {
                // 60% Breadboard on Left
                breadboardPane(
                    Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                )

                // 40% Full Trainer Console Rack on Right
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                ) {
                    TrainerConsoleDock(
                        circuit = circuit,
                        circuitVersion = bench.topologyVersion,
                        onCircuitChanged = { bench.syncFromCircuit() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(cutoutHorizontal)
                    .background(CanvasBackground)
            ) {
                breadboardPane(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                TrainerConsoleDock(
                    circuit = circuit,
                    circuitVersion = bench.topologyVersion,
                    onCircuitChanged = { bench.syncFromCircuit() }
                    // No modifier: the dock applies its own navigation-bar inset.
                )
            }
        }
    }

    when (bench.overlay) {
        Overlay.NONE -> Unit

        Overlay.LAB_CATALOG -> LabCatalogSheet(
            activeLabId = activeLab?.id,
            onDismiss = { bench.overlay = Overlay.NONE },
            onLoadLab = { loadLab(it) }
        )

        Overlay.CHIP_CATALOG -> ChipCatalogSheet(
            onDismiss = { bench.overlay = Overlay.NONE },
            onPick = { part ->
                // Place at the first free trench-1 column, the same landing spot the
                // curriculum presets use.
                bench.driveTopology { it.addChip(part, 1, nextFreeColumn(bench)) }
                Haptics.thud(view)
                bench.overlay = Overlay.NONE
            }
        )

        Overlay.SCOPE -> ScopeSheet(
            circuit = circuit,
            onDismiss = { bench.overlay = Overlay.NONE },
            onSample = { bench.syncFromCircuit() }
        )

        Overlay.VERIFIER -> VerifierDialog(
            report = report,
            activeLabId = activeLab?.id,
            newBadges = newBadges,
            onSelectLab = { lab ->
                activeLab = lab
                runVerification()
            },
            onRetest = { runVerification() },
            onDismiss = { bench.overlay = Overlay.NONE }
        )

        Overlay.BOARD_ACTIONS -> BoardActionsDialog(
            onClearWires = {
                bench.driveTopology { it.clearWires() }
                bench.overlay = Overlay.NONE
            },
            onResetBoard = {
                bench.driveTopology { it.clearAll() }
                activeLab = null
                report = null
                bench.lastVerificationPassed = null
                bench.overlay = Overlay.NONE
            },
            onReloadLab = {
                loadLab(activeLab ?: LabCurriculum.classicLabs[1])
            },
            onExportProject = currentProjectId?.let { id ->
                {
                    scope.launch {
                        exportProjectJson(context, container, id, currentProjectTitle ?: "project")
                    }
                }
            },
            onDismiss = { bench.overlay = Overlay.NONE }
        )
    }

    var pendingExitAfterSave by remember { mutableStateOf(false) }

    if (saveAsOpen) {
        SaveProjectDialog(
            defaultTitle = currentProjectTitle ?: defaultProjectTitle(activeLab),
            onConfirm = { title ->
                saveAsOpen = false
                performSave(title)
                if (pendingExitAfterSave) {
                    pendingExitAfterSave = false
                    onNavigateHome()
                }
            },
            onDismiss = {
                saveAsOpen = false
                pendingExitAfterSave = false
            }
        )
    }

    if (shareDialogOpen) {
        val shareCode = remember(circuit, bench.topologyVersion) {
            CircuitShareBytecode.encode(circuit)
        }
        ShareCircuitDialog(
            code = shareCode,
            onDismiss = { shareDialogOpen = false }
        )
    }

    if (confirmLeaveBench) {
        LeaveBenchDialog(
            isLab = activeLab != null,
            labTitle = activeLab?.title,
            onSaveAndExit = {
                confirmLeaveBench = false
                val title = currentProjectTitle
                if (title != null) {
                    performSave(title)
                    onNavigateHome()
                } else {
                    pendingExitAfterSave = true
                    saveAsOpen = true
                }
            },
            onDiscardAndLeave = {
                confirmLeaveBench = false
                onNavigateHome()
            },
            onKeepEditing = {
                confirmLeaveBench = false
            }
        )
    }
}

/** A sensible first name for a project saved from a lab or the sandbox. */
private fun defaultProjectTitle(activeLab: LabExperiment?): String =
    activeLab?.let { "Lab ${it.labNumber} · ${it.title}" } ?: "Sandbox build"

/**
 * Writes the project's JSON into the provider-scoped exports cache and hands it to the
 * system share sheet — the same no-permission pattern the lab report exporter uses.
 */
private suspend fun exportProjectJson(
    context: android.content.Context,
    container: AppContainer,
    projectId: String,
    title: String
) {
    val text = container.projects.exportText(projectId) ?: return
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = title.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "project" }
            val file = java.io.File(dir, "$safeName.logiclabs.json")
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "com.logiclabs.app.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share project"))
        }
    }
}

/**
 * Recolours the selected wire, or arms the colour for the next one drawn.
 *
 * Routed through `removeWire`/`addWire` rather than assigning `circuit.wires[i]` in place.
 * An in-place write leaves the DSU netlist untouched, so the board could show one thing
 * and solve another; going through the mutators rebuilds it. The original id is
 * preserved so selection survives.
 */
private fun recolorSelection(
    bench: com.logiclabs.app.ui.state.WorkbenchState,
    color: com.logiclabs.core.bridge.model.WireColor
) {
    bench.activeWireColor = color
    val wire = bench.selectedWire() ?: return
    bench.driveTopology {
        it.removeWire(wire.id)
        it.addWire(wire.startSocket, wire.endSocket, color, wire.isManhattan, wire.id)
    }
}

/** Swaps a wire's endpoints, so its sag and boot orientation mirror. */
private fun flipSelectedWire(bench: com.logiclabs.app.ui.state.WorkbenchState) {
    val wire = bench.selectedWire() ?: return
    bench.driveTopology {
        it.removeWire(wire.id)
        it.addWire(wire.endSocket, wire.startSocket, wire.color, wire.isManhattan, wire.id)
    }
}

/** Toggles a wire between catenary and Manhattan routing. */
private fun toggleSelectedRouting(bench: com.logiclabs.app.ui.state.WorkbenchState) {
    val wire = bench.selectedWire() ?: return
    bench.driveTopology {
        it.removeWire(wire.id)
        it.addWire(wire.startSocket, wire.endSocket, wire.color, !wire.isManhattan, wire.id)
    }
}

/**
 * First trench-1 column with room for a 14-pin DIP that does not overlap a placed chip.
 * Falls back to column 10 — the curriculum's landing spot — when the trench is full.
 */
private fun nextFreeColumn(bench: com.logiclabs.app.ui.state.WorkbenchState): Int {
    val occupied = bench.circuit.placedChips
        .filter { it.placedIc.trench == 1 }
        .map { it.placedIc.startColumn }
    var col = 10
    while (col <= 50) {
        if (occupied.none { kotlin.math.abs(it - col) < 8 }) return col
        col += 8
    }
    return 10
}

/** Destructive board operations, kept behind a confirmation. */
@Composable
private fun BoardActionsDialog(
    onClearWires: () -> Unit,
    onResetBoard: () -> Unit,
    onReloadLab: () -> Unit,
    onExportProject: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    BenchDialog(
        onDismissRequest = onDismiss,
        icon = LogicIcons.Breadboard,
        title = "Board actions",
        message = "Nothing is selected — these act on the whole breadboard and cannot be undone.",
        actions = {
            if (onExportProject != null) {
                BenchDialogAction(
                    "EXPORT PROJECT (.JSON)", onExportProject,
                    tone = BenchDialogTone.ACCENT, leadingIcon = LogicIcons.Export
                )
            }
            BenchDialogAction(
                "CLEAR ALL WIRES", onClearWires,
                tone = BenchDialogTone.ACCENT, leadingIcon = LogicIcons.Trash
            )
            BenchDialogAction(
                "RELOAD EXPERIMENT", onReloadLab,
                tone = BenchDialogTone.ACCENT, leadingIcon = LogicIcons.Reload
            )
            BenchDialogAction("CANCEL", onDismiss, tone = BenchDialogTone.NEUTRAL)
            BenchDialogActionDivider()
            BenchDialogAction(
                "RESET BOARD", onResetBoard,
                tone = BenchDialogTone.DESTRUCTIVE, leadingIcon = LogicIcons.Power
            )
        }
    )
}

/** Names a project on first save. */
@Composable
private fun SaveProjectDialog(
    defaultTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }

    BenchDialog(
        onDismissRequest = onDismiss,
        icon = LogicIcons.Save,
        title = "Save project",
        message = "Name this build — it is stored on this device only.",
        content = {
            Spacer(Modifier.height(Dimens.Space3))
            androidx.compose.material3.OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = LogicLabsType.BodyMd.copy(color = TextPrimary),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentCyan,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            // Disabled while blank: the save button used to always render enabled and
            // silently do nothing on a blank name.
            BenchDialogAction(
                "SAVE", { onConfirm(title.trim()) },
                enabled = title.isNotBlank(), leadingIcon = LogicIcons.Save
            )
            BenchDialogAction("CANCEL", onDismiss, tone = BenchDialogTone.NEUTRAL)
        }
    )
}

/**
 * Clean engineering guard dialog when leaving the active workbench.
 */
@Composable
private fun LeaveBenchDialog(
    isLab: Boolean,
    labTitle: String?,
    onSaveAndExit: () -> Unit,
    onDiscardAndLeave: () -> Unit,
    onKeepEditing: () -> Unit
) {
    val title = if (isLab) "Exit lab experiment?" else "Exit simulation?"
    val message = if (isLab) {
        if (labTitle != null) {
            "Leaving “$labTitle” clears the breadboard. Save this build as a project first if you want to keep the wiring."
        } else {
            "Leaving clears the breadboard. Save this build as a project first if you want to keep the wiring."
        }
    } else {
        "The bench is still wired. Save it as a project to keep it, or leave without saving."
    }

    BenchDialog(
        onDismissRequest = onKeepEditing,
        icon = LogicIcons.Power,
        title = title,
        message = message,
        actions = {
            BenchDialogAction(
                "SAVE & EXIT", onSaveAndExit,
                leadingIcon = LogicIcons.Save
            )
            BenchDialogAction("KEEP WORKING", onKeepEditing, tone = BenchDialogTone.NEUTRAL)
            BenchDialogActionDivider()
            BenchDialogAction(
                if (isLab) "EXIT LAB" else "EXIT WITHOUT SAVING",
                onDiscardAndLeave,
                tone = BenchDialogTone.DESTRUCTIVE,
                leadingIcon = LogicIcons.Power
            )
        }
    )
}

/**
 * Dialog displaying compact bytecode with one-tap COPY CODE and SHARE VIA... intent.
 */
@Composable
private fun ShareCircuitDialog(
    code: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current

    BenchDialog(
        onDismissRequest = onDismiss,
        icon = LogicIcons.Share,
        title = "Share circuit",
        message = "Compact bytecode representing all chips, wires, and console states on this breadboard.",
        content = {
            Spacer(Modifier.height(Dimens.Space3))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusSm))
                    .background(SurfaceCard)
                    .border(Dimens.Hairline, SurfaceCardBorder, RoundedCornerShape(Dimens.RadiusSm))
                    .padding(Dimens.Space3)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = code,
                    style = LogicLabsType.TechnicalXs.copy(fontFamily = FontFamily.Monospace),
                    color = AccentCyan
                )
            }
        },
        actions = {
            BenchDialogAction(
                "COPY CODE",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Circuit Share Code", code)
                    clipboard.setPrimaryClip(clip)
                    Haptics.tick(view)
                    android.widget.Toast.makeText(context, "Circuit code copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                leadingIcon = LogicIcons.Export,
                tone = BenchDialogTone.ACCENT
            )
            BenchDialogAction(
                "SHARE VIA...",
                onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, code)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Share circuit code")
                    context.startActivity(shareIntent)
                    onDismiss()
                },
                leadingIcon = LogicIcons.Share,
                tone = BenchDialogTone.ACCENT
            )
            BenchDialogAction("DISMISS", onDismiss, tone = BenchDialogTone.NEUTRAL)
        }
    )
}

