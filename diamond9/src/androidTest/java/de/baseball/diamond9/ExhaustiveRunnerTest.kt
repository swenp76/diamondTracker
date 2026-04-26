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
    // Uses advanceOnHit(..., 1). Implements force play rules (same as walk in this app).

    @Test
    fun testSingle_BasesEmpty() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(), b, 1)
        assertEquals(1, next.size); assertEquals(1, next[1]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testSingle_RunnerOn1st() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1), b, 1)
        assertEquals(2, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testSingle_RunnerOn2nd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(2), b, 1)
        // Non-forced runner on 2nd stays at 2nd (automation default)
        assertEquals(2, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testSingle_RunnerOn3rd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(3), b, 1)
        // Non-forced runner on 3rd stays at 3rd
        assertEquals(2, next.size); assertEquals(1, next[1]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testSingle_RunnersOn1stAnd2nd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1, 2), b, 1)
        assertEquals(3, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testSingle_RunnersOn1stAnd3rd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1, 3), b, 1)
        // R1 forced to 2nd, R3 stays at 3rd
        assertEquals(3, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testSingle_RunnersOn2ndAnd3rd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(2, 3), b, 1)
        // No forces beyond 1st
        assertEquals(3, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testSingle_BasesLoaded() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1, 2, 3), b, 1)
        assertEquals(3, next.size); assertEquals(1, next[1]?.base); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertEquals(1, scored.size); assertEquals("R3", scored[0].name)
    }

    // ── DOUBLE (2B) ─────────────────────────────────────────────────────────────
    // Automation: R2 and R3 score, R1 moves to 3rd.

    @Test
    fun testDouble_BasesEmpty() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(), b, 2)
        assertEquals(1, next.size); assertEquals(2, next[2]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testDouble_RunnerOn1st() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1), b, 2)
        assertEquals(2, next.size); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertTrue(scored.isEmpty())
    }

    @Test
    fun testDouble_RunnerOn2nd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(2), b, 2)
        assertEquals(1, next.size); assertEquals(2, next[2]?.base)
        assertEquals(1, scored.size); assertEquals("R2", scored[0].name)
    }

    @Test
    fun testDouble_RunnerOn3rd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(3), b, 2)
        assertEquals(1, next.size); assertEquals(2, next[2]?.base)
        assertEquals(1, scored.size); assertEquals("R3", scored[0].name)
    }

    @Test
    fun testDouble_RunnersOn1stAnd2nd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1, 2), b, 2)
        assertEquals(2, next.size); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertEquals(1, scored.size); assertEquals("R2", scored[0].name)
    }

    @Test
    fun testDouble_RunnersOn1stAnd3rd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1, 3), b, 2)
        assertEquals(2, next.size); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertEquals(1, scored.size); assertEquals("R3", scored[0].name)
    }

    @Test
    fun testDouble_RunnersOn2ndAnd3rd() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(2, 3), b, 2)
        assertEquals(1, next.size); assertEquals(2, next[2]?.base)
        assertEquals(2, scored.size)
    }

    @Test
    fun testDouble_BasesLoaded() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1, 2, 3), b, 2)
        assertEquals(2, next.size); assertEquals(2, next[2]?.base); assertEquals(3, next[3]?.base)
        assertEquals(2, scored.size)
    }

    // ── TRIPLE (3B) ─────────────────────────────────────────────────────────────
    // Automation: Everyone scores.

    @Test
    fun testTriple_BasesLoaded() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1, 2, 3), b, 3)
        assertEquals(1, next.size); assertEquals(3, next[3]?.base)
        assertEquals(3, scored.size)
    }

    // ── HOME RUN (HR) ───────────────────────────────────────────────────────────
    // Automation: Everyone scores.

    @Test
    fun testHomeRun_BasesLoaded() {
        val (next, scored) = RunnerManager.advanceOnHit(getBases(1, 2, 3), b, 4)
        assertTrue(next.isEmpty())
        assertEquals(4, scored.size)
    }
}
