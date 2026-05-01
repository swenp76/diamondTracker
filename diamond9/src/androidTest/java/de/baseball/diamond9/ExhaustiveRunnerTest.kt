package de.baseball.diamond9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive tests for RunnerManager automatic tracking logic.
 * 
 * Note: Scenarios requiring manual runner adjustments (Fly Outs, Double Plays, Fielder's Choice outs)
 * are ignored as they are not currently automated by RunnerManager.
 */
class ExhaustiveRunnerTest {

    private val b = GameRunner(gameId = 1, base = 0, name = "Batter", slot = 1)
    private val r1 = GameRunner(gameId = 1, base = 1, name = "R1", slot = 2)
    private val r2 = GameRunner(gameId = 1, base = 2, name = "R2", slot = 3)
    private val r3 = GameRunner(gameId = 1, base = 3, name = "R3", slot = 4)

    // Helper to create base states
    private fun getBases(vararg occupied: Int): Map<Int, GameRunner> {
        val map = mutableMapOf<Int, GameRunner>()
        if (1 in occupied) map[1] = r1
        if (2 in occupied) map[2] = r2
        if (3 in occupied) map[3] = r3
        return map
    }

    // ── HIT BY PITCH (HBP) & WALK (BB) ──────────────────────────────────────────
    // Uses advanceOnWalk which implements force play rules.

    @Test
    fun testWalk_BasesEmpty() {
        val (next, scored) = RunnerManager.advanceOnWalk(getBases(), b)
        assertEquals(1, next.size); assertEquals(1, next[1]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testWalk_RunnerOn1st() {
        val (next, scored) = RunnerManager.advanceOnWalk(getBases(1), b)
        assertEquals(2, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testWalk_RunnerOn2nd() {
        val (next, scored) = RunnerManager.advanceOnWalk(getBases(2), b)
        assertEquals(2, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testWalk_RunnerOn3rd() {
        val (next, scored) = RunnerManager.advanceOnWalk(getBases(3), b)
        assertEquals(2, next.size); assertEquals(1, next[1]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testWalk_RunnersOn1stAnd2nd() {
        val (next, scored) = RunnerManager.advanceOnWalk(getBases(1, 2), b)
        assertEquals(3, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testWalk_RunnersOn1stAnd3rd() {
        val (next, scored) = RunnerManager.advanceOnWalk(getBases(1, 3), b)
        assertEquals(3, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testWalk_RunnersOn2ndAnd3rd() {
        val (next, scored) = RunnerManager.advanceOnWalk(getBases(2, 3), b)
        assertEquals(3, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testWalk_BasesLoaded() {
        val (next, scored) = RunnerManager.advanceOnWalk(getBases(1, 2, 3), b)
        assertEquals(3, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertEquals(1, scored.size); assertEquals("R3", scored[0].name)
    }

    // ── SINGLE (1B) ─────────────────────────────────────────────────────────────
    // Forced runners advance automatically. Non-forced 2B runner auto-advances to 3B.
    // Non-forced 3B runner becomes a pendingScorer (dialog in UI).

    @Test
    fun testSingle_BasesEmpty() {
        val r = RunnerManager.advanceOnHit(getBases(), b, 1)
        assertEquals(1, r.nextRunners.size); assertEquals(1, r.nextRunners[1]?.base)
        assertTrue(r.autoScoring.isEmpty()); assertTrue(r.pendingScorers.isEmpty())
    }

    @Test
    fun testSingle_RunnerOn1st() {
        val r = RunnerManager.advanceOnHit(getBases(1), b, 1)
        assertEquals(2, r.nextRunners.size); assertEquals(1, r.nextRunners[1]?.base); assertEquals(2, r.nextRunners[2]?.base)
        assertTrue(r.autoScoring.isEmpty()); assertTrue(r.pendingScorers.isEmpty())
    }

    @Test
    fun testSingle_RunnerOn2nd() {
        val r = RunnerManager.advanceOnHit(getBases(2), b, 1)
        // Non-forced runner on 2B auto-advances to 3B
        assertEquals(2, r.nextRunners.size); assertEquals(1, r.nextRunners[1]?.base); assertEquals(3, r.nextRunners[3]?.base)
        assertTrue(r.autoScoring.isEmpty()); assertTrue(r.pendingScorers.isEmpty())
    }

    @Test
    fun testSingle_RunnerOn3rd() {
        val r = RunnerManager.advanceOnHit(getBases(3), b, 1)
        // Non-forced runner on 3B → pending scorer
        assertEquals(1, r.nextRunners.size); assertEquals(1, r.nextRunners[1]?.base)
        assertTrue(r.autoScoring.isEmpty())
        assertEquals(1, r.pendingScorers.size); assertEquals("R3", r.pendingScorers[0].runner.name)
    }

    @Test
    fun testSingle_RunnersOn1stAnd2nd() {
        val r = RunnerManager.advanceOnHit(getBases(1, 2), b, 1)
        assertEquals(3, r.nextRunners.size); assertEquals(1, r.nextRunners[1]?.base); assertEquals(2, r.nextRunners[2]?.base); assertEquals(3, r.nextRunners[3]?.base)
        assertTrue(r.autoScoring.isEmpty()); assertTrue(r.pendingScorers.isEmpty())
    }

    @Test
    fun testSingle_RunnersOn1stAnd3rd() {
        val r = RunnerManager.advanceOnHit(getBases(1, 3), b, 1)
        // R1 forced to 2B, R3 non-forced → pending scorer
        assertEquals(2, r.nextRunners.size); assertEquals(1, r.nextRunners[1]?.base); assertEquals(2, r.nextRunners[2]?.base)
        assertTrue(r.autoScoring.isEmpty())
        assertEquals(1, r.pendingScorers.size); assertEquals("R3", r.pendingScorers[0].runner.name)
    }

    @Test
    fun testSingle_RunnersOn2ndAnd3rd() {
        val r = RunnerManager.advanceOnHit(getBases(2, 3), b, 1)
        // R2 auto-advances to 3B; R3 non-forced → pending scorer
        assertEquals(2, r.nextRunners.size); assertEquals(1, r.nextRunners[1]?.base); assertEquals(3, r.nextRunners[3]?.base)
        assertTrue(r.autoScoring.isEmpty())
        assertEquals(1, r.pendingScorers.size); assertEquals("R3", r.pendingScorers[0].runner.name)
    }

    @Test
    fun testSingle_BasesLoaded() {
        val r = RunnerManager.advanceOnHit(getBases(1, 2, 3), b, 1)
        // Bases loaded: full force chain, R3 auto-scores
        assertEquals(3, r.nextRunners.size); assertEquals(1, r.nextRunners[1]?.base); assertEquals(2, r.nextRunners[2]?.base); assertEquals(3, r.nextRunners[3]?.base)
        assertEquals(1, r.autoScoring.size); assertEquals("R3", r.autoScoring[0].name)
        assertTrue(r.pendingScorers.isEmpty())
    }

    // ── DOUBLE (2B) ─────────────────────────────────────────────────────────────
    // R1 auto-advances to 3B. R2 and R3 become pendingScorers (dialog in UI),
    // except when R1 occupies 3B, forcing R2 to auto-score.

    @Test
    fun testDouble_BasesEmpty() {
        val r = RunnerManager.advanceOnHit(getBases(), b, 2)
        assertEquals(1, r.nextRunners.size); assertEquals(2, r.nextRunners[2]?.base)
        assertTrue(r.autoScoring.isEmpty()); assertTrue(r.pendingScorers.isEmpty())
    }

    @Test
    fun testDouble_RunnerOn1st() {
        val r = RunnerManager.advanceOnHit(getBases(1), b, 2)
        assertEquals(2, r.nextRunners.size); assertEquals(2, r.nextRunners[2]?.base); assertEquals(3, r.nextRunners[3]?.base)
        assertTrue(r.autoScoring.isEmpty()); assertTrue(r.pendingScorers.isEmpty())
    }

    @Test
    fun testDouble_RunnerOn2nd() {
        val r = RunnerManager.advanceOnHit(getBases(2), b, 2)
        // R2 non-forced (3B free) → pending scorer
        assertEquals(1, r.nextRunners.size); assertEquals(2, r.nextRunners[2]?.base)
        assertTrue(r.autoScoring.isEmpty())
        assertEquals(1, r.pendingScorers.size); assertEquals("R2", r.pendingScorers[0].runner.name)
    }

    @Test
    fun testDouble_RunnerOn3rd() {
        val r = RunnerManager.advanceOnHit(getBases(3), b, 2)
        // R3 non-forced → pending scorer
        assertEquals(1, r.nextRunners.size); assertEquals(2, r.nextRunners[2]?.base)
        assertTrue(r.autoScoring.isEmpty())
        assertEquals(1, r.pendingScorers.size); assertEquals("R3", r.pendingScorers[0].runner.name)
    }

    @Test
    fun testDouble_RunnersOn1stAnd2nd() {
        val r = RunnerManager.advanceOnHit(getBases(1, 2), b, 2)
        // R1 → 3B auto; 3B occupied → R2 must auto-score
        assertEquals(2, r.nextRunners.size); assertEquals(2, r.nextRunners[2]?.base); assertEquals(3, r.nextRunners[3]?.base)
        assertEquals(1, r.autoScoring.size); assertEquals("R2", r.autoScoring[0].name)
        assertTrue(r.pendingScorers.isEmpty())
    }

    @Test
    fun testDouble_RunnersOn1stAnd3rd() {
        val r = RunnerManager.advanceOnHit(getBases(1, 3), b, 2)
        // R3 → pending scorer; R1 → 3B auto
        assertEquals(2, r.nextRunners.size); assertEquals(2, r.nextRunners[2]?.base); assertEquals(3, r.nextRunners[3]?.base)
        assertTrue(r.autoScoring.isEmpty())
        assertEquals(1, r.pendingScorers.size); assertEquals("R3", r.pendingScorers[0].runner.name)
    }

    @Test
    fun testDouble_RunnersOn2ndAnd3rd() {
        val r = RunnerManager.advanceOnHit(getBases(2, 3), b, 2)
        // Force chain: batter→2B forces R2→3B forces R3→home; both auto-score
        assertEquals(1, r.nextRunners.size); assertEquals(2, r.nextRunners[2]?.base)
        assertEquals(2, r.autoScoring.size)
        assertTrue(r.pendingScorers.isEmpty())
    }

    @Test
    fun testDouble_BasesLoaded() {
        val r = RunnerManager.advanceOnHit(getBases(1, 2, 3), b, 2)
        // R3 forced home (had2B); R1 → 3B auto; R2 forced home (next[3] occupied by R1)
        assertEquals(2, r.nextRunners.size); assertEquals(2, r.nextRunners[2]?.base); assertEquals(3, r.nextRunners[3]?.base)
        assertEquals(2, r.autoScoring.size)
        assertTrue(r.pendingScorers.isEmpty())
    }

    // ── TRIPLE (3B) ─────────────────────────────────────────────────────────────
    // Automation: Everyone scores automatically.

    @Test
    fun testTriple_BasesLoaded() {
        val r = RunnerManager.advanceOnHit(getBases(1, 2, 3), b, 3)
        assertEquals(1, r.nextRunners.size); assertEquals(3, r.nextRunners[3]?.base)
        assertEquals(3, r.autoScoring.size); assertTrue(r.pendingScorers.isEmpty())
    }

    // ── HOME RUN (HR) ───────────────────────────────────────────────────────────
    // Automation: Everyone scores automatically.

    @Test
    fun testHomeRun_BasesLoaded() {
        val r = RunnerManager.advanceOnHit(getBases(1, 2, 3), b, 4)
        assertTrue(r.nextRunners.isEmpty())
        assertEquals(4, r.autoScoring.size); assertTrue(r.pendingScorers.isEmpty())
    }
}
