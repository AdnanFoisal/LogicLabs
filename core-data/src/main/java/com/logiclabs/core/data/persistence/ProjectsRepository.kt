package com.logiclabs.core.data.persistence

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** The stored form of a project plus whether this save created it. */
data class StoredProject(val project: SerializedProject, val isNew: Boolean)

/** A placed DIP, in the exact form a thumbnail can lay it back down. */
data class ChipSpot(
    val partNumber: String,
    val trench: Int,
    val startColumn: Int,
    val isRotated180: Boolean
)

/** One wire endpoint pair plus its insulation colour, for the same purpose. */
data class WireSpot(
    val startSocket: Int,
    val endSocket: Int,
    val colorArgb: Long
)

/** What a project list needs to show: identity, census, and enough geometry to sketch. */
data class ProjectSummary(
    val id: String,
    val title: String,
    val updatedAtMillis: Long,
    val chipCount: Int,
    val wireCount: Int,
    val activeLabId: String?,
    val chipSpots: List<ChipSpot> = emptyList(),
    val wireSpots: List<WireSpot> = emptyList()
)

/**
 * File-backed project store: one JSON document per project under `filesDir/projects/`.
 *
 * ## Why plain files and not DataStore
 * A project is a single document of unbounded size (a dense board is hundreds of chips
 * and wires), it is written as a whole, and it is shared as a whole — all of which fit
 * a filesystem and none of which fit Preferences. DataStore stays for the small
 * key/value state in the sibling repositories.
 *
 * ## Consistency model
 * Every mutation goes through [mutex] and bumps [version], so `list()` callers can
 * observe changes by collecting the version and re-reading. Writes happen on IO
 * dispatchers; reads are cheap enough to run on demand.
 *
 * Files whose names start with `_` are internal (the autosave draft) and never appear
 * in [list].
 */
class ProjectsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val dir: File
        get() = File(appContext.filesDir, "projects").apply { mkdirs() }

    /** Bumped after every successful write/delete so UI can re-query [list]. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    /**
     * Lenient on purpose: `ignoreUnknownKeys` keeps files written by a newer app
     * version loadable, and a `null` result from a corrupt file is skipped in [list]
     * rather than failing the whole directory listing.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** The crash-recovery draft, excluded from every listing. */
    private val draftFile: File get() = File(dir, "$DRAFT_ID.json")

    /** All projects, newest first. Corrupt files are skipped, never thrown. */
    suspend fun list(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        val files = dir.listFiles { f -> f.isFile && f.extension == "json" } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            if (file.name.startsWith("_")) return@mapNotNull null
            runCatching {
                val p = json.decodeFromString<SerializedProject>(file.readText())
                file.nameWithoutExtension.toProjectId(p)
            }.getOrNull()
        }.sortedByDescending { it.updatedAtMillis }
    }

    suspend fun load(id: String): SerializedProject? = withContext(Dispatchers.IO) {
        val file = fileFor(id)
        if (!file.isFile) return@withContext null
        runCatching { json.decodeFromString<SerializedProject>(file.readText()) }.getOrNull()
    }

    /**
     * Writes [project]. A blank id gets a fresh UUID and the project is treated as new;
     * otherwise the existing file is overwritten. The timestamp is always refreshed —
     * `list` sorts on it. Returns what was actually stored, so the caller can adopt the
     * minted id, plus whether this was a first save.
     */
    suspend fun save(project: SerializedProject): StoredProject = mutex.withLock {
        withContext(Dispatchers.IO) {
            val isNew = project.id.isBlank()
            val settled = if (isNew) {
                project.copy(id = UUID.randomUUID().toString())
            } else {
                project
            }.copy(timestampMillis = System.currentTimeMillis())
            fileFor(settled.id).writeText(json.encodeToString(SerializedProject.serializer(), settled))
            _version.value++
            StoredProject(settled, isNew)
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val gone = fileFor(id).delete()
            if (gone) _version.value++
            gone
        }
    }

    suspend fun rename(id: String, title: String) {
        val p = load(id) ?: return
        if (title.isBlank() || title == p.title) return
        save(p.copy(title = title.trim()))
    }

    /** Raw JSON text for sharing; `null` when the project does not exist. */
    suspend fun exportText(id: String): String? = withContext(Dispatchers.IO) {
        val file = fileFor(id)
        if (!file.isFile) return@withContext null
        runCatching { file.readText() }.getOrNull()
    }

    /**
     * Parses shared text and stores it as a **new** project (fresh id, title kept),
     * so an import can never clobber a same-named local project. Returns the stored
     * project, or `null` if the text is not a Logic Labs project.
     */
    suspend fun import(text: String): SerializedProject? {
        val parsed = runCatching { json.decodeFromString<SerializedProject>(text) }.getOrNull()
            ?: return null
        val stored = parsed.copy(id = UUID.randomUUID().toString())
        save(stored)
        return stored
    }

    // -- Draft (autosave / crash recovery) -----------------------------------

    suspend fun saveDraft(project: SerializedProject) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                draftFile.writeText(
                    json.encodeToString(SerializedProject.serializer(), project.copy(id = DRAFT_ID))
                )
            }
        }
    }

    suspend fun loadDraft(): SerializedProject? = withContext(Dispatchers.IO) {
        if (!draftFile.isFile) return@withContext null
        runCatching { json.decodeFromString<SerializedProject>(draftFile.readText()) }.getOrNull()
    }

    suspend fun clearDraft() {
        mutex.withLock {
            withContext(Dispatchers.IO) { draftFile.delete() }
        }
    }

    /**
     * Fire-and-forget draft write for composition `onDispose` blocks, which cannot
     * suspend. Runs on the repository's own scope, which outlives any screen.
     */
    fun saveDraftAsync(project: SerializedProject) {
        scope.launch { saveDraft(project) }
    }

    // -- Internals -------------------------------------------------------------

    private fun fileFor(id: String): File {
        // Ids are UUIDs we minted, but the file name is also derived from shared text
        // on import, so confine it to safe characters regardless of origin.
        val safe = id.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { UUID.randomUUID().toString() }
        return File(dir, "$safe.json")
    }

    private fun String.toProjectId(p: SerializedProject) = ProjectSummary(
        id = this,
        title = p.title,
        updatedAtMillis = p.timestampMillis,
        chipCount = p.chips.size,
        wireCount = p.wires.size,
        activeLabId = p.activeLabId,
        chipSpots = p.chips.map { ChipSpot(it.partNumber, it.trench, it.startColumn, it.isRotated180) },
        wireSpots = p.wires.map { WireSpot(it.startSocket, it.endSocket, wireArgbOf(it.colorName)) }
    )

    private fun wireArgbOf(colorName: String): Long = try {
        com.logiclabs.core.bridge.model.WireColor.valueOf(colorName).hexArgb
    } catch (_: Exception) {
        com.logiclabs.core.bridge.model.WireColor.RED.hexArgb
    }

    private companion object {
        const val DRAFT_ID = "_draft"
    }
}
