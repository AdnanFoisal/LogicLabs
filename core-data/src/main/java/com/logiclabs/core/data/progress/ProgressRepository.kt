package com.logiclabs.core.data.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lifetime bench statistics — the quiet kind of gamification this app ships with.
 *
 * No points, no levels, no streak pressure: verified labs, circuits built and time on
 * the bench, plus a short list of badges derived from exactly those numbers. The Home
 * screen shows the counts; a badge is *revealed* exactly once, at the moment it is
 * earned, by [markLabVerified] and [incrementCircuitsBuilt] returning the newly earned
 * set.
 */
data class BenchStats(
    val verifiedLabIds: Set<String> = emptySet(),
    val circuitsBuilt: Int = 0,
    val benchTimeMillis: Long = 0L,
    val firstLaunchAtMillis: Long = 0L
) {
    val benchTimeMinutes: Long get() = benchTimeMillis / 60_000
}

/** A single named achievement. Earned state is derived, never stored. */
data class Badge(
    val id: String,
    val title: String,
    val description: String
)

/** The badge catalog and the predicates that light each one. */
object BadgeCatalog {

    val FIRST_CIRCUIT = Badge("first_circuit", "First Circuit", "Built your first circuit")
    val TRUTH_SEEKER = Badge("truth_seeker", "Truth Seeker", "Verified your first experiment")
    val GATE_KEEPER = Badge("gate_keeper", "Gate Keeper", "Verified four experiments")
    val HALF_COURSE = Badge("half_course", "Halfway Bench", "Verified half the classic course")
    val FULL_COURSE = Badge("full_course", "Full Course", "Verified every classic lab")
    val BENCH_TIME = Badge("bench_time", "Bench Time", "An hour on the bench")

    val all: List<Badge> = listOf(
        FIRST_CIRCUIT,
        TRUTH_SEEKER,
        GATE_KEEPER,
        HALF_COURSE,
        FULL_COURSE,
        BENCH_TIME
    )

    /** The subset of [all] a stats snapshot has earned. */
    fun earnedBy(stats: BenchStats, classicLabCount: Int): Set<String> = buildSet {
        if (stats.circuitsBuilt >= 1) add(FIRST_CIRCUIT.id)
        if (stats.verifiedLabIds.isNotEmpty()) add(TRUTH_SEEKER.id)
        if (stats.verifiedLabIds.size >= 4) add(GATE_KEEPER.id)
        if (classicLabCount > 0 && stats.verifiedLabIds.size >= (classicLabCount + 1) / 2) add(HALF_COURSE.id)
        if (classicLabCount > 0 && stats.verifiedLabIds.size >= classicLabCount) add(FULL_COURSE.id)
        if (stats.benchTimeMillis >= 60L * 60_000) add(BENCH_TIME.id)
    }

    /** Badges in catalog order with their earned flag, for a badge list UI. */
    fun derive(stats: BenchStats, classicLabCount: Int): List<Pair<Badge, Boolean>> =
        all.map { it to (it.id in earnedBy(stats, classicLabCount)) }

    /** What became earned between two snapshots — the once-only reveal set. */
    fun newlyEarned(before: BenchStats, after: BenchStats, classicLabCount: Int): List<Badge> {
        if (before == after) return emptyList()
        val b = earnedBy(before, classicLabCount)
        return all.filter { it.id !in b && it.id in earnedBy(after, classicLabCount) }
    }
}

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "logiclabs_progress"
)

/**
 * Persists [BenchStats] over Preferences DataStore.
 *
 * Sets of verified lab ids are stored with `stringSetPreferencesKey` directly — a set
 * is exactly the shape of the fact. Badge reveal reads happen inside the same `edit`
 * transaction that writes the new stats, so "which badges are new" can never race a
 * second writer.
 */
class ProgressRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val stats: Flow<BenchStats> = appContext.progressDataStore.data.map { p ->
        BenchStats(
            verifiedLabIds = p[KEY_VERIFIED] ?: emptySet(),
            circuitsBuilt = p[KEY_CIRCUITS] ?: 0,
            benchTimeMillis = p[KEY_TIME] ?: 0L,
            firstLaunchAtMillis = p[KEY_FIRST] ?: 0L
        )
    }

    /** Stamps the install time once; safe to call from every launch. */
    fun ensureFirstLaunch() {
        scope.launch {
            appContext.progressDataStore.edit { p ->
                if (p[KEY_FIRST] == null || p[KEY_FIRST] == 0L) {
                    p[KEY_FIRST] = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * Records a verified experiment and returns the badges this run *newly* earned, so
     * the caller can reveal exactly those. Re-verifying an already-verified lab earns
     * nothing new by construction.
     */
    suspend fun markLabVerified(labId: String, classicLabCount: Int): List<Badge> {
        var newly: List<Badge> = emptyList()
        appContext.progressDataStore.edit { p ->
            val before = p.toStats()
            val after = before.copy(verifiedLabIds = before.verifiedLabIds + labId)
            p[KEY_VERIFIED] = after.verifiedLabIds
            newly = BadgeCatalog.newlyEarned(before, after, classicLabCount)
        }
        return newly
    }

    /** Counts a first-time project save. Returns any badges that reveal. */
    suspend fun incrementCircuitsBuilt(): List<Badge> {
        var newly: List<Badge> = emptyList()
        appContext.progressDataStore.edit { p ->
            val before = p.toStats()
            val after = before.copy(circuitsBuilt = before.circuitsBuilt + 1)
            p[KEY_CIRCUITS] = after.circuitsBuilt
            newly = BadgeCatalog.newlyEarned(before, after, 0)
        }
        return newly
    }

    /** Adds elapsed bench time; called in coarse chunks, so int rounding is moot. */
    fun addBenchTime(millis: Long) {
        if (millis <= 0) return
        scope.launch {
            appContext.progressDataStore.edit { it[KEY_TIME] = (it[KEY_TIME] ?: 0L) + millis }
        }
    }

    /** Erases all progress. */
    fun reset() {
        scope.launch {
            appContext.progressDataStore.edit { it.clear() }
        }
    }

    private fun Preferences.toStats() = BenchStats(
        verifiedLabIds = this[KEY_VERIFIED] ?: emptySet(),
        circuitsBuilt = this[KEY_CIRCUITS] ?: 0,
        benchTimeMillis = this[KEY_TIME] ?: 0L,
        firstLaunchAtMillis = this[KEY_FIRST] ?: 0L
    )

    private companion object {
        val KEY_VERIFIED = stringSetPreferencesKey("verified_lab_ids")
        val KEY_CIRCUITS = intPreferencesKey("circuits_built")
        val KEY_TIME = longPreferencesKey("bench_time_millis")
        val KEY_FIRST = longPreferencesKey("first_launch_at")
    }
}
