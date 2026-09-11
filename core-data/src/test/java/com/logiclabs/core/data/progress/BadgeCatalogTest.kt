package com.logiclabs.core.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The badge catalog's predicates, pinned. The once-only reveal contract —
 * [BadgeCatalog.newlyEarned] returns exactly what became earned between two snapshots
 * and nothing on a no-change step — is what the verifier's celebration relies on.
 */
class BadgeCatalogTest {

    @Test
    fun `an empty bench has earned nothing`() {
        assertTrue(BadgeCatalog.earnedBy(BenchStats(), classicLabCount = 12).isEmpty())
    }

    @Test
    fun `badges light at their thresholds`() {
        val built = BenchStats(circuitsBuilt = 1)
        assertTrue(BadgeCatalog.FIRST_CIRCUIT.id in BadgeCatalog.earnedBy(built, 12))

        val fourLabs = built.copy(verifiedLabIds = (1..4).map { "lab$it" }.toSet())
        val earned = BadgeCatalog.earnedBy(fourLabs, 12)
        assertTrue(BadgeCatalog.TRUTH_SEEKER.id in earned)
        assertTrue(BadgeCatalog.GATE_KEEPER.id in earned)
        assertFalse(BadgeCatalog.HALF_COURSE.id in earned) // 4 of 12 is not half
        assertFalse(BadgeCatalog.FULL_COURSE.id in earned)

        val sixLabs = built.copy(verifiedLabIds = (1..6).map { "lab$it" }.toSet())
        assertTrue(BadgeCatalog.HALF_COURSE.id in BadgeCatalog.earnedBy(sixLabs, 12))

        val twelve = built.copy(verifiedLabIds = (1..12).map { "lab$it" }.toSet())
        assertTrue(BadgeCatalog.FULL_COURSE.id in BadgeCatalog.earnedBy(twelve, 12))
    }

    @Test
    fun `bench time badge lands at exactly one hour`() {
        val justUnder = BenchStats(benchTimeMillis = 59L * 60_000)
        assertFalse(BadgeCatalog.BENCH_TIME.id in BadgeCatalog.earnedBy(justUnder, 12))

        val exactly = BenchStats(benchTimeMillis = 60L * 60_000)
        assertTrue(BadgeCatalog.BENCH_TIME.id in BadgeCatalog.earnedBy(exactly, 12))
    }

    @Test
    fun `newly earned is the once-only reveal set`() {
        val before = BenchStats()
        val after = before.copy(circuitsBuilt = 1)

        assertEquals(listOf(BadgeCatalog.FIRST_CIRCUIT), BadgeCatalog.newlyEarned(before, after, 12))

        // A no-change step reveals nothing, so a re-run can never re-celebrate.
        assertTrue(BadgeCatalog.newlyEarned(after, after, 12).isEmpty())

        // Re-verifying an already-verified lab changes no predicate.
        val sameIds = after.copy(verifiedLabIds = setOf("lab1"))
        val stillSame = sameIds.copy(verifiedLabIds = setOf("lab1"))
        assertTrue(BadgeCatalog.newlyEarned(sameIds, stillSame, 12).isEmpty())
    }

    @Test
    fun `derive marks earned flags in catalog order`() {
        val stats = BenchStats(circuitsBuilt = 1, verifiedLabIds = setOf("lab1"))
        val derived = BadgeCatalog.derive(stats, classicLabCount = 12)
        assertEquals(BadgeCatalog.all.size, derived.size)
        assertEquals(BadgeCatalog.FIRST_CIRCUIT, derived.first().first)
        assertTrue(derived.first { it.first == BadgeCatalog.TRUTH_SEEKER }.second)
        assertFalse(derived.first { it.first == BadgeCatalog.FULL_COURSE }.second)
    }
}
