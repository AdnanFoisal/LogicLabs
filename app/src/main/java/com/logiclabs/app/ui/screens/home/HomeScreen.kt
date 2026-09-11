package com.logiclabs.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.logiclabs.app.ui.screens.settings.SettingsScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.logiclabs.core.data.persistence.CircuitShareBytecode
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.app.di.AppContainer
import com.logiclabs.app.ui.component.BenchDialog
import com.logiclabs.app.ui.component.BenchDialogAction
import com.logiclabs.app.ui.component.BenchDialogActionDivider
import com.logiclabs.app.ui.component.BenchDialogTone
import com.logiclabs.app.ui.component.BoardThumbnail
import com.logiclabs.app.ui.navigation.BenchRequest
import com.logiclabs.app.ui.sheets.BadgesSheet
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.data.persistence.ChipSpot
import com.logiclabs.core.data.persistence.ProjectSummary
import com.logiclabs.core.data.persistence.WireSpot
import com.logiclabs.core.data.progress.BadgeCatalog
import com.logiclabs.core.data.progress.BenchStats
import com.logiclabs.core.data.session.Session
import com.logiclabs.core.data.session.SessionKind
import com.logiclabs.core.designsystem.component.ChamferedPanel
import com.logiclabs.core.designsystem.component.LogicIcons
import com.logiclabs.core.designsystem.component.PanelScrew
import com.logiclabs.core.designsystem.component.TelemetryPill
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.CanvasBackground
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.Motion
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import com.logiclabs.feature.tools.courseware.LabCurriculum
import com.logiclabs.feature.tools.courseware.LabExperiment
import com.logiclabs.feature.tools.feedback.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The instrument rack: what the app opens onto after the splash.
 *
 * The design idea is that Home is the trainer's *front panel* — every card is a rack
 * module on the same chassis, lit by the same top-left light, and the CONTINUE card is
 * the big friendly power switch for whatever you were last doing.
 *
 * Layout, top to bottom: masthead (identity, status, settings) · CONTINUE hero ·
 * telemetry strip (course progress, circuits built, bench time, achievements) ·
 * COURSEWORK with the twelve sealed labs as a journey (verified check, one NEXT UP at
 * a time) · EXTENDED BENCH for the extra experiments · SANDBOX · version footer.
 *
 * Cards enter with a short staggered rise — one per 45 ms, Motion-style ease — so the
 * rack feels switched on rather than dumped on screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onContinue: (BenchRequest) -> Unit,
    onOpenSettings: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val session by container.session.lastSession.collectAsState(initial = null)
    val stats by container.progress.stats.collectAsState(initial = BenchStats())

    val classicLabs = remember { LabCurriculum.classicLabs }
    val classicCount = classicLabs.size
    // The coursework journey is the twelve sealed labs; the extended experiments are a
    // second, clearly separated shelf. Both come from the same grouped catalog.
    val classicGroups = remember { ExperimentCatalog.groups.filter { g -> classicLabs.any { it.id == g.key } } }
    val extendedGroups = remember { ExperimentCatalog.groups.filter { g -> classicLabs.none { it.id == g.key } } }

    val verifiedClassic = classicLabs.count { it.id in stats.verifiedLabIds }
    val nextUpId = classicLabs.firstOrNull { it.id !in stats.verifiedLabIds }?.id

    var showBadges by remember { mutableStateOf(false) }
    var showImportCodeDialog by remember { mutableStateOf(false) }
    var deletingProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(HomeTab.PROJECTS) }

    // If not on Projects tab, back pops to Projects tab
    BackHandler(enabled = selectedTab != HomeTab.PROJECTS) {
        selectedTab = HomeTab.PROJECTS
    }

    // Home is the root: the system back gesture asks before it powers the bench down
    BackHandler(enabled = selectedTab == HomeTab.PROJECTS && !confirmExit) {
        confirmExit = true
    }

    // Saved projects: re-queried whenever the store's version bumps (save, delete,
    // import), so the rack is always in step with the bench.
    var projects by remember { mutableStateOf<List<ProjectSummary>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(container.projects.version) {
        projects = container.projects.list()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (text != null && container.projects.import(text) != null) {
                    Haptics.success(view)
                }
            }
        }
    }

    // One shared entrance clock: items animate from 0 to 1 with a per-index delay.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(90)
        entered = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            // Notch guard: statusBars ∪ displayCutout on the top + horizontal sides.
            // The status bar alone is not enough — a landscape side cutout (or a
            // portrait corner cutout) would otherwise sit over the masthead. The system
            // bars are persistent (MainActivity no longer hides them), so these insets
            // are real, and the padding is consumed for the tabs below.
            .windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    ) {
        Masthead(onNewCircuit = { onContinue(BenchRequest.Sandbox) })

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                HomeTab.PROJECTS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Dimens.Space3,
                            end = Dimens.Space3,
                            top = Dimens.Space2,
                            bottom = 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
                    ) {
                        item(key = "hero") {
                            ContinueHero(
                                session = session,
                                projects = projects,
                                entered = entered,
                                onClick = { onContinue(BenchRequest.Continue) }
                            )
                        }

                        item(key = "sandbox") {
                            SandboxCard(
                                entered = entered,
                                onClick = { onContinue(BenchRequest.Sandbox) }
                            )
                        }

                        item(key = "stats") {
                            StatsStrip(
                                stats = stats,
                                verifiedClassic = verifiedClassic,
                                classicCount = classicCount,
                                entered = entered,
                                onShowBadges = { showBadges = true }
                            )
                        }

                        item(key = "projects-header") {
                            SectionHeader(
                                title = "SAVED PROJECTS",
                                countLabel = "${projects.size} ON DEVICE",
                                progress = -1f,
                                entered = entered,
                                trailing = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ImportChip(onClick = {
                                            runCatching {
                                                importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                                            }
                                        })
                                        ImportCodeChip(onClick = {
                                            showImportCodeDialog = true
                                        })
                                    }
                                }
                            )
                        }

                        if (projects.isEmpty()) {
                            item(key = "projects-empty") {
                                Text(
                                    text = "No saved circuits yet. Build something on the bench and press SAVE — or import a shared .logiclabs.json above.",
                                    style = LogicLabsType.BodySm,
                                    color = TextTertiary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.Space2, horizontal = Dimens.Space2)
                                )
                            }
                        } else {
                            itemsIndexed(projects, key = { _, p -> "p_${p.id}" }) { index, project ->
                                ProjectCard(
                                    project = project,
                                    entered = entered,
                                    index = index,
                                    onClick = { onContinue(BenchRequest.Project(project.id)) },
                                    onDelete = { deletingProject = project }
                                )
                            }
                        }

                        item(key = "footer") {
                            Footer()
                        }
                    }
                }
                HomeTab.COURSEWORK -> {
                    CourseworkTab(
                        verifiedLabIds = stats.verifiedLabIds,
                        onLaunchLab = { labId -> onContinue(BenchRequest.Lab(labId)) }
                    )
                }
                HomeTab.REFERENCE -> {
                    IcReferenceTab()
                }
                HomeTab.SETTINGS -> {
                    SettingsScreen(
                        container = container,
                        onBack = { selectedTab = HomeTab.PROJECTS }
                    )
                }
            }
        }

        HomeBottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }

    if (showBadges) {
        BadgesSheet(
            stats = stats,
            classicLabCount = classicCount,
            onDismiss = { showBadges = false }
        )
    }

    if (showImportCodeDialog) {
        ImportCodeDialog(
            onDismiss = { showImportCodeDialog = false },
            onImplement = { code ->
                scope.launch {
                    try {
                        val project = CircuitShareBytecode.decodeToProject(code)
                        container.projects.saveDraft(project)
                        container.session.recordSandbox()
                        showImportCodeDialog = false
                        Haptics.success(view)
                        onContinue(BenchRequest.Continue)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to import code: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    deletingProject?.let { project ->
        BenchDialog(
            onDismissRequest = { deletingProject = null },
            icon = LogicIcons.Trash,
            headerTone = BenchDialogTone.DESTRUCTIVE,
            title = "Delete project?",
            message = "“${project.title}” — ${project.chipCount} ICs and ${project.wireCount} wires — " +
                "will be removed from this device. This cannot be undone.",
            actions = {
                BenchDialogAction("CANCEL", { deletingProject = null }, tone = BenchDialogTone.NEUTRAL)
                BenchDialogActionDivider()
                BenchDialogAction(
                    "DELETE", {
                        scope.launch { container.projects.delete(project.id) }
                        deletingProject = null
                    },
                    tone = BenchDialogTone.DESTRUCTIVE,
                    leadingIcon = LogicIcons.Trash
                )
            }
        )
    }

    if (confirmExit) {
        BenchDialog(
            onDismissRequest = { confirmExit = false },
            icon = LogicIcons.Power,
            title = "Power off bench?",
            message = "The bench keeps its state — CONTINUE brings you straight back to this machine.",
            actions = {
                BenchDialogAction("STAY", { confirmExit = false }, tone = BenchDialogTone.NEUTRAL)
                BenchDialogActionDivider()
                BenchDialogAction(
                    "POWER OFF", {
                        confirmExit = false
                        (context as? android.app.Activity)?.finish()
                    },
                    tone = BenchDialogTone.DESTRUCTIVE,
                    leadingIcon = LogicIcons.Power
                )
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Masthead
// ---------------------------------------------------------------------------

/**
 * Modern edge-to-edge header with brand title, IDL-800A trainer subtitle,
 * live phosphor READY pulse indicator, and a quick "+ NEW CIRCUIT" button.
 */
@Composable
private fun Masthead(onNewCircuit: () -> Unit) {
    val view = LocalView.current
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChassisBase)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Space3, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "LOGIC LABS",
                        style = LogicLabsType.TitleMd,
                        color = TextPrimary,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Glowing BENCH READY pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.RadiusPill))
                            .background(PhosphorCore.copy(alpha = 0.12f))
                            .border(1.dp, PhosphorCore.copy(alpha = pulseAlpha * 0.6f), RoundedCornerShape(Dimens.RadiusPill))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(PhosphorCore.copy(alpha = pulseAlpha))
                        )
                        Text(
                            text = "READY",
                            style = LogicLabsType.SwitchPlate,
                            color = PhosphorCore,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "IDL-800A DIGITAL TRAINER BENCH",
                    style = LogicLabsType.SwitchPlate,
                    color = TextTertiary
                )
            }

            // Quick "+ NEW CIRCUIT" button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AmberCore)
                    .clickable {
                        Haptics.thud(view)
                        onNewCircuit()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = LogicIcons.Breadboard,
                        contentDescription = null,
                        tint = ChassisBase,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "NEW CIRCUIT",
                        style = LogicLabsType.SwitchPlate,
                        color = ChassisBase,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SurfaceCardBorder)
        )
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

/** What the CONTINUE hero shows for the current session. */
private data class HeroContent(
    val label: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

private fun getUnitAndExp(lab: LabExperiment): Pair<String, String> {
    if (lab.id.startsWith("exp")) {
        val num = lab.id.removePrefix("exp").take(2).toIntOrNull() ?: lab.labNumber
        return "EXTENDED BENCH" to "EXP %02d".format(num)
    }
    return when (lab.labNumber) {
        1 -> "UNIT 01: LOGIC GATES" to "EXP 01"
        2 -> "UNIT 01: LOGIC GATES" to "EXP 02"
        3 -> "UNIT 01: LOGIC GATES" to "EXP 03"
        4 -> "UNIT 01: LOGIC GATES" to "EXP 04"
        5 -> "UNIT 01: LOGIC GATES" to "EXP 05"
        6 -> "UNIT 01: LOGIC GATES" to "EXP 06"
        7 -> "UNIT 03: ARITHMETIC ARCHITECTURE" to "EXP 07"
        8 -> "UNIT 02: COMBINATIONAL SYSTEMS" to "EXP 08"
        9 -> "UNIT 02: COMBINATIONAL SYSTEMS" to "EXP 09"
        10 -> "UNIT 04: SEQUENTIAL REGISTERS" to "EXP 10"
        11 -> "UNIT 04: SEQUENTIAL REGISTERS" to "EXP 11"
        12 -> "UNIT 03: ARITHMETIC ARCHITECTURE" to "EXP 12"
        else -> "EXTENDED BENCH" to "EXP %02d".format(lab.labNumber)
    }
}

@Composable
private fun ContinueHero(
    session: Session?,
    projects: List<ProjectSummary>,
    entered: Boolean,
    onClick: () -> Unit
) {
    val (chips, wires) = remember(session?.id, session?.kind, projects) {
        when (session?.kind) {
            SessionKind.LAB -> {
                val lab = session.id?.let { ExperimentCatalog.findById(it) }
                if (lab != null) {
                    val circuit = BreadboardCircuit()
                    runCatching { lab.buildCircuit(circuit) }
                    val c = circuit.placedChips.map {
                        ChipSpot(
                            partNumber = it.placedIc.partNumber,
                            trench = it.placedIc.trench,
                            startColumn = it.placedIc.startColumn,
                            isRotated180 = it.placedIc.isRotated180
                        )
                    }
                    val w = circuit.wires.map {
                        WireSpot(
                            startSocket = it.startSocket,
                            endSocket = it.endSocket,
                            colorArgb = it.color.hexArgb
                        )
                    }
                    c to w
                } else {
                    emptyList<ChipSpot>() to emptyList<WireSpot>()
                }
            }
            SessionKind.PROJECT -> {
                val proj = projects.find { it.id == session.id }
                (proj?.chipSpots ?: emptyList()) to (proj?.wireSpots ?: emptyList())
            }
            else -> emptyList<ChipSpot>() to emptyList<WireSpot>()
        }
    }

    val content = when (session?.kind) {
        SessionKind.LAB -> {
            val lab = ExperimentCatalog.findById(session.id ?: "")
            if (lab != null) {
                val (unitName, expName) = getUnitAndExp(lab)
                val shortUnit = unitName.substringBefore(":")
                HeroContent(
                    label = "CONTINUE · $shortUnit: $expName",
                    title = lab.title,
                    subtitle = lab.subtitle,
                    icon = LogicIcons.Labs,
                    accent = PhosphorCore
                )
            } else {
                introContent()
            }
        }
        SessionKind.PROJECT -> {
            val proj = projects.find { it.id == session.id }
            HeroContent(
                label = "CONTINUE PROJECT",
                title = proj?.title ?: "Saved circuit",
                subtitle = proj?.let { "${it.chipCount} ICs · ${it.wireCount} wires" } ?: "Reload your last build onto the breadboard",
                icon = LogicIcons.Chip,
                accent = AccentCyan
            )
        }
        SessionKind.SANDBOX -> HeroContent(
            label = "CONTINUE SANDBOX",
            title = "Open bench",
            subtitle = "Back to your free-build bench",
            icon = LogicIcons.Breadboard,
            accent = AccentCyan
        )
        null -> introContent()
    }

    PressableCard(
        modifier = Modifier
            .fillMaxWidth()
            .enterStagger(entered, index = 0),
        accent = content.accent,
        onClick = onClick
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Space4),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (chips.isNotEmpty() || wires.isNotEmpty()) {
                    BoardThumbnail(
                        chips = chips,
                        wires = wires,
                        modifier = Modifier
                            .size(width = 88.dp, height = 58.dp)
                            .clip(RoundedCornerShape(Dimens.RadiusSm))
                            .background(SurfaceCard)
                            .border(Dimens.Hairline, content.accent.copy(alpha = 0.35f), RoundedCornerShape(Dimens.RadiusSm))
                    )
                } else {
                    IconWell(icon = content.icon, accent = content.accent, size = 54.dp)
                }
                Spacer(Modifier.width(Dimens.Space3))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = content.label,
                        style = LogicLabsType.SwitchPlate,
                        color = content.accent,
                        maxLines = 1
                    )
                    Text(
                        text = content.title,
                        style = LogicLabsType.TitleMd,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = content.subtitle,
                        style = LogicLabsType.BodySm,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(Dimens.Space2))
                Icon(
                    imageVector = LogicIcons.ChevronRight,
                    contentDescription = null,
                    tint = content.accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            // The POWER ON strip: the hero's affordance, styled like a guarded switch.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Dimens.Space4, end = Dimens.Space4, bottom = Dimens.Space3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.RadiusPill))
                        .background(content.accent.copy(alpha = 0.14f))
                        .border(
                            Dimens.Hairline,
                            content.accent.copy(alpha = 0.8f),
                            RoundedCornerShape(Dimens.RadiusPill)
                        )
                        .padding(horizontal = Dimens.Space3, vertical = Dimens.Space1)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = LogicIcons.Power,
                            contentDescription = null,
                            tint = content.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = " POWER ON",
                            style = LogicLabsType.SwitchPlateLg,
                            color = content.accent,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun introContent(): HeroContent = HeroContent(
    label = "START HERE · UNIT 01: EXP 01",
    title = "EXP 01 · Inverter / NOT Gate Logic",
    subtitle = "7404 Hex Inverter IC — your first powered experiment",
    icon = LogicIcons.Labs,
    accent = PhosphorCore
)

/** A recessed square well holding an icon, like a labelled rack slot. */
@Composable
private fun IconWell(icon: ImageVector, accent: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(accent.copy(alpha = 0.12f))
            .border(Dimens.Hairline, accent.copy(alpha = 0.4f), RoundedCornerShape(Dimens.RadiusMd)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size / 2.1f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsStrip(
    stats: BenchStats,
    verifiedClassic: Int,
    classicCount: Int,
    entered: Boolean,
    onShowBadges: () -> Unit
) {
    val earned = BadgeCatalog.earnedBy(stats, classicCount).size
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .enterStagger(entered, index = 1),
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        TelemetryPill(label = "LABS", value = "$verifiedClassic/$classicCount", accent = PhosphorCore)
        TelemetryPill(label = "CIRCUITS", value = stats.circuitsBuilt.toString(), accent = AccentCyan)
        TelemetryPill(label = "BENCH", value = formatBenchTime(stats.benchTimeMillis), accent = AmberCore)

        // Achievements chip: same pill anatomy, but tappable.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.RadiusPill))
                .background(SurfaceCard)
                .border(Dimens.Hairline, AmberCore.copy(alpha = 0.35f), RoundedCornerShape(Dimens.RadiusPill))
                .clickable(onClick = onShowBadges)
                .semantics { contentDescription = "Show achievements" }
                .padding(horizontal = Dimens.Space2, vertical = Dimens.Space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)
        ) {
            Icon(
                imageVector = LogicIcons.Rosette,
                contentDescription = null,
                tint = AmberCore,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "$earned/${BadgeCatalog.all.size}",
                style = LogicLabsType.TechnicalSm,
                color = AmberCore,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    countLabel: String,
    progress: Float,
    entered: Boolean,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .enterStagger(entered, index = 2)
            .padding(top = Dimens.Space3, bottom = Dimens.Space1)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = LogicLabsType.SwitchPlateLg,
                color = TextSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(Dimens.Space2))
            }
            Text(
                text = countLabel,
                style = LogicLabsType.TechnicalSm,
                color = if (progress >= 0f) PhosphorCore else TextTertiary,
                maxLines = 1
            )
        }
        // A negative progress means "no bar" (the extended shelf is not a journey yet).
        if (progress >= 0f) {
            Spacer(Modifier.height(Dimens.Space1))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusPill))
                    .background(SurfaceCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Dimens.RadiusPill))
                        .background(PhosphorCore)
                )
            }
        }
    }
}

/** One rack-module lab card: number block, identity, IC badges, verified / NEXT state.
 *  For multi-variation groups, tapping expands a sub-list of selectable variation rows. */
@Composable
private fun LabCard(
    group: ExperimentCatalog.Group,
    lab: LabExperiment,
    verified: Boolean,
    verifiedLabIds: Set<String>,
    isNextUp: Boolean,
    entered: Boolean,
    index: Int,
    onVariationClick: (String) -> Unit
) {
    val accent = when {
        verified -> PhosphorCore
        isNextUp -> AccentCyan
        else -> TextSecondary
    }

    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }

    PressableCard(
        modifier = Modifier
            .fillMaxWidth()
            .enterStagger(entered, index),
        accent = accent,
        onClick = {
            if (group.hasVariations) {
                expanded = !expanded
            } else {
                onVariationClick(lab.id)
            }
        }
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.Space3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lab ordinal, silkscreened like a module number.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(46.dp)
                ) {
                    Text(
                        text = "EXP",
                        style = LogicLabsType.TechnicalXs,
                        color = TextTertiary,
                        maxLines = 1
                    )
                    Text(
                        text = "%02d".format(lab.labNumber),
                        style = LogicLabsType.TechnicalLg,
                        color = if (verified) PhosphorCore else AmberCore,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.width(Dimens.Space3))

                Column(Modifier.weight(1f)) {
                    val (unitName, expName) = getUnitAndExp(lab)
                    Text(
                        text = "$expName · ${lab.title}",
                        style = LogicLabsType.TitleSm,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (group.hasVariations) "$unitName · ${group.variations.size} variations"
                               else "$unitName · ${lab.subtitle}",
                        style = LogicLabsType.BodySm,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1),
                        modifier = Modifier.padding(top = Dimens.Space1)
                    ) {
                        lab.targetChips.take(3).forEach { chip ->
                            Text(
                                text = "IC $chip",
                                style = LogicLabsType.TechnicalXs,
                                color = TextTertiary,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimens.RadiusSm))
                                    .background(SurfaceCard)
                                    .padding(horizontal = Dimens.Space1, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(Dimens.Space2))

                when {
                    verified -> Icon(
                        imageVector = LogicIcons.Verify,
                        contentDescription = "Verified",
                        tint = PhosphorCore,
                        modifier = Modifier.size(20.dp)
                    )
                    isNextUp -> NextUpChip()
                }

                // Show expand chevron for multi-variation labs
                if (group.hasVariations) {
                    Spacer(Modifier.width(Dimens.Space1))
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (expanded) 90f else 0f,
                        animationSpec = tween(200),
                        label = "varChevron"
                    )
                    Icon(
                        imageVector = LogicIcons.ChevronRight,
                        contentDescription = if (expanded) "Collapse variations" else "Expand variations",
                        tint = AccentCyan,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }

            // Expandable variation picker
            AnimatedVisibility(
                visible = expanded && group.hasVariations,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Dimens.Space3, end = Dimens.Space3, bottom = Dimens.Space3),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space1)
                ) {
                    // Thin separator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SurfaceCard)
                    )
                    Spacer(Modifier.height(Dimens.Space1))
                    Text(
                        text = "SELECT VARIATION",
                        style = LogicLabsType.TechnicalXs,
                        color = TextTertiary,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = Dimens.Space1)
                    )
                    group.variations.forEach { variation ->
                        val varVerified = variation.id in verifiedLabIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimens.RadiusSm))
                                .background(
                                    if (varVerified) PhosphorCore.copy(alpha = 0.08f)
                                    else SurfaceCard.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = Dimens.Hairline,
                                    color = if (varVerified) PhosphorCore.copy(alpha = 0.4f)
                                            else AccentCyan.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(Dimens.RadiusSm)
                                )
                                .clickable {
                                    Haptics.tick(view)
                                    onVariationClick(variation.id)
                                }
                                .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = variation.subtitle,
                                    style = LogicLabsType.SwitchPlate,
                                    color = if (varVerified) PhosphorCore else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = variation.outputLabels.joinToString(" · "),
                                    style = LogicLabsType.TechnicalXs,
                                    color = TextTertiary,
                                    maxLines = 1
                                )
                            }
                            if (varVerified) {
                                Icon(
                                    imageVector = LogicIcons.Verify,
                                    contentDescription = "Verified",
                                    tint = PhosphorCore,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = LogicIcons.ChevronRight,
                                    contentDescription = "Open",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The single pulsing NEXT marker that keeps the coursework journey pointed. */
@Composable
private fun NextUpChip() {
    val transition = rememberInfiniteTransition(label = "nextUp")
    val breath by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = Motion.BloomPulse,
            repeatMode = RepeatMode.Reverse
        ),
        label = "nextBreath"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(AccentCyan.copy(alpha = 0.12f * breath))
            .border(Dimens.Hairline, AccentCyan.copy(alpha = breath), RoundedCornerShape(Dimens.RadiusPill))
            .padding(horizontal = Dimens.Space2, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "NEXT",
            style = LogicLabsType.TechnicalXs,
            color = AccentCyan,
            maxLines = 1
        )
    }
}

@Composable
private fun SandboxCard(entered: Boolean, onClick: () -> Unit) {
    PressableCard(
        modifier = Modifier
            .fillMaxWidth()
            .enterStagger(entered, index = 3),
        accent = AccentCyan,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Space4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconWell(icon = LogicIcons.Breadboard, accent = AccentCyan, size = 46.dp)
            Spacer(Modifier.width(Dimens.Space3))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "SANDBOX",
                    style = LogicLabsType.SwitchPlateLg,
                    color = AccentCyan,
                    maxLines = 1
                )
                Text(
                    text = "Empty powered bench · free build",
                    style = LogicLabsType.BodyMd,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = LogicIcons.ChevronRight,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun Footer() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }
    Text(
        text = "LOGIC LABS v$version · OFFLINE BENCH · NO ACCOUNTS · NO TRACKING",
        style = LogicLabsType.TechnicalXs,
        color = TextTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Space3),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

// ---------------------------------------------------------------------------
// Shared card plumbing
// ---------------------------------------------------------------------------

/**
 * The rack-module card: a chamfered chassis panel that scales a hair under the finger
 * and ticks, so every tap feels like pressing a real module's latch.
 */
@Composable
private fun PressableCard(
    modifier: Modifier = Modifier,
    accent: Color,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = Motion.TactilePress,
        label = "cardPress"
    )

    ChamferedPanel(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        Haptics.tick(view)
                        onClick()
                    }
                )
        ) {
            content()
        }
    }
}

/** Staggered rise-in for rack modules: 18 dp up, 340 ms, EaseOutQuint, 45 ms apart. */
@Composable
private fun Modifier.enterStagger(entered: Boolean, index: Int): Modifier {
    val p by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = 340,
            delayMillis = index * 45,
            easing = EaseOutQuint
        ),
        label = "rackEnter"
    )
    return graphicsLayer {
        alpha = p
        translationY = (1f - p) * 18.dp.toPx()
    }
}

/** Bench time as "42m" / "1h 07m"; anything under a minute still reads "0m". */
private fun formatBenchTime(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours <= 0) "${minutes}m" else "${hours}h ${"%02d".format(minutes)}m"
}

// ---------------------------------------------------------------------------
// Saved projects
// ---------------------------------------------------------------------------

/** One saved project: a true-to-geometry board sketch, category badge, name, census and chips. */
@Composable
private fun ProjectCard(
    project: ProjectSummary,
    entered: Boolean,
    index: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val (categoryLabel, categoryColor) = when {
        project.id == "_draft" -> "AUTOSAVED DRAFT" to AmberCore
        project.activeLabId != null -> "LAB ATTEMPT" to AccentCyan
        else -> "SAVED PROJECT" to PhosphorCore
    }
    val distinctChips = remember(project.chipSpots) {
        project.chipSpots.map { it.partNumber }.distinct()
    }

    PressableCard(
        modifier = Modifier
            .fillMaxWidth()
            .enterStagger(entered, index),
        accent = categoryColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.Space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoardThumbnail(
                chips = project.chipSpots,
                wires = project.wireSpots,
                modifier = Modifier
                    .size(width = 104.dp, height = 76.dp)
                    .clip(RoundedCornerShape(Dimens.RadiusSm))
                    .background(SurfaceCard)
                    .border(Dimens.Hairline, categoryColor.copy(alpha = 0.35f), RoundedCornerShape(Dimens.RadiusSm))
            )
            Spacer(Modifier.width(Dimens.Space3))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.RadiusPill))
                        .background(categoryColor.copy(alpha = 0.12f))
                        .border(Dimens.Hairline, categoryColor.copy(alpha = 0.45f), RoundedCornerShape(Dimens.RadiusPill))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = categoryLabel,
                        style = LogicLabsType.TechnicalSm.copy(fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = categoryColor,
                        maxLines = 1
                    )
                }
                Text(
                    text = project.title,
                    style = LogicLabsType.TitleSm,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${project.chipCount} ICs · ${project.wireCount} wires · ${relativeTime(project.updatedAtMillis)}",
                    style = LogicLabsType.TechnicalSm,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (distinctChips.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        distinctChips.take(3).forEach { chip ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SurfaceRaised)
                                    .border(Dimens.Hairline, categoryColor.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = chip,
                                    style = LogicLabsType.TechnicalSm.copy(fontSize = 9.sp),
                                    color = TextSecondary
                                )
                            }
                        }
                        if (distinctChips.size > 3) {
                            Text(
                                text = "+${distinctChips.size - 3}",
                                style = LogicLabsType.TechnicalSm.copy(fontSize = 9.sp),
                                color = TextTertiary
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(Dimens.MinTouchTarget)
                    .clip(RoundedCornerShape(Dimens.RadiusMd))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onDelete
                    )
                    .semantics { contentDescription = "Delete project ${project.title}" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LogicIcons.Trash,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Small silkscreened chip that opens the document picker for an import. */
@Composable
private fun ImportChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(AccentCyan.copy(alpha = 0.10f))
            .border(Dimens.Hairline, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(Dimens.RadiusPill))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Import a shared project" }
            .padding(horizontal = Dimens.Space2, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)
    ) {
        Icon(
            imageVector = LogicIcons.Import,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = "IMPORT",
            style = LogicLabsType.TechnicalXs,
            color = AccentCyan,
            maxLines = 1
        )
    }
}

/** Small silkscreened chip that opens the bytecode import dialog. */
@Composable
private fun ImportCodeChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.RadiusPill))
            .background(AccentCyan.copy(alpha = 0.10f))
            .border(Dimens.Hairline, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(Dimens.RadiusPill))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Import a shared circuit code" }
            .padding(horizontal = Dimens.Space2, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space1)
    ) {
        Icon(
            imageVector = LogicIcons.Share,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = "IMPORT CODE",
            style = LogicLabsType.TechnicalXs,
            color = AccentCyan,
            maxLines = 1
        )
    }
}

/**
 * Dialog for importing a circuit from "LOGIC-..." bytecode with live validation and IMPLEMENT action.
 */
@Composable
private fun ImportCodeDialog(
    onDismiss: () -> Unit,
    onImplement: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var codeText by remember { mutableStateOf("") }

    val validationResult = remember(codeText) {
        val trimmed = codeText.trim()
        if (trimmed.isEmpty()) null else CircuitShareBytecode.validate(trimmed)
    }

    BenchDialog(
        onDismissRequest = onDismiss,
        icon = LogicIcons.Import,
        title = "Import circuit code",
        message = "Paste a shared “LOGIC-...” bytecode string to load onto the breadboard.",
        content = {
            Spacer(Modifier.height(Dimens.Space3))
            androidx.compose.material3.OutlinedTextField(
                value = codeText,
                onValueChange = { codeText = it },
                placeholder = {
                    Text("LOGIC-...", style = LogicLabsType.BodySm, color = TextTertiary)
                },
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipboard?.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                if (pasteText.isNotBlank()) {
                                    codeText = pasteText.trim()
                                    Haptics.tick(view)
                                }
                            }
                        }
                    ) {
                        Text("PASTE", style = LogicLabsType.SwitchPlate, color = AccentCyan)
                    }
                },
                singleLine = true,
                textStyle = LogicLabsType.TechnicalSm.copy(color = TextPrimary),
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

            Spacer(Modifier.height(Dimens.Space2))

            if (validationResult != null) {
                if (validationResult.isValid) {
                    Text(
                        text = "✓ Valid circuit: ${validationResult.chipCount} ICs · ${validationResult.wireCount} wires",
                        style = LogicLabsType.TechnicalSm,
                        color = PhosphorCore
                    )
                } else {
                    Text(
                        text = "✗ ${validationResult.errorMessage ?: "Invalid circuit code"}",
                        style = LogicLabsType.TechnicalSm,
                        color = ShortCircuitAlert
                    )
                }
            } else {
                Text(
                    text = "Requires valid LOGIC- binary bytecode format.",
                    style = LogicLabsType.TechnicalXs,
                    color = TextTertiary
                )
            }
        },
        actions = {
            BenchDialogAction(
                "IMPLEMENT",
                onClick = {
                    onImplement(codeText.trim())
                },
                enabled = validationResult?.isValid == true,
                leadingIcon = LogicIcons.Check,
                tone = BenchDialogTone.ACCENT
            )
            BenchDialogAction("CANCEL", onDismiss, tone = BenchDialogTone.NEUTRAL)
        }
    )
}

/** Coarse age, the way a bench notebook dates an entry. */
private fun relativeTime(millis: Long): String {
    val delta = System.currentTimeMillis() - millis
    return when {
        delta < 60_000L -> "just now"
        delta < 3_600_000L -> "${delta / 60_000L}m ago"
        delta < 86_400_000L -> "${delta / 3_600_000L}h ago"
        else -> "${delta / 86_400_000L}d ago"
    }
}
