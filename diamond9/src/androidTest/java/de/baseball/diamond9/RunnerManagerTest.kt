package de.baseball.diamond9

import org.junit.Assert.*
import org.junit.Test

/**
 * Technical Logic Tests for RunnerManager.
 * 
 * DESIGN DECISION:
 * - WALKS: Automatic advancement. Forced runners scoring via Walk are added to 'autoScoring'.
 * - HITS: All runners reaching home are added to 'pendingScorers' for manual confirmation.
 */
class RunnerManagerTest {

    private val batter = GameRunner(gameId = 1L, base = 0, name = "Batter", slot = 1)
    private val r1 = GameRunner(gameId = 1L, base = 1, name = "Runner1", slot = 2)
    private val r2 = GameRunner(gameId = 1L, base = 2, name = "Runner2", slot = 3)
    private val r3 = GameRunner(gameId = 1L, base = 3, name = "Runner3", slot = 4)

    // ── WALK SCENARIOS (Walks still use autoScoring for force plays) ──────────

    @Test
    fun walk_basesLoaded_scoresRunnerOn3BAutomatically() {
        val current = mapOf(1 to r1, 2 to r2, 3 to r3)
        val (next, scoring) = RunnerManager.advanceOnWalk(current, batter)

        assertEquals(3, next.size)
        assertEquals(1, scoring.size)
        assertEquals("Runner3", scoring[0].name)
        assertEquals(4, scoring[0].base)
    }

    @Test
    fun walk_noForceOn3B_runnerStays() {
        val current = mapOf(1 to r1, 3 to r3) // 2nd base is empty
        val (next, scoring) = RunnerManager.advanceOnWalk(current, batter)

        assertEquals(3, next.size)
        assertTrue(scoring.isEmpty())
        assertNotNull(next[3])
        assertNotNull(next[2])
        assertNotNull(next[1])
    }

    // ── HIT SCENARIOS (Forced runners home are PENDING, not autoScoring) ───────

    @Test
    fun single_basesLoaded_runnerOn3BIsForcedButPending() {
        val current = mapOf(1 to r1, 2 to r2, 3 to r3)
        val result = RunnerManager.advanceOnHit(current, batter, 1)

        // 3B runner is forced home, but must be confirmed (Pending)
        assertTrue(result.autoScoring.isEmpty())
        assertEquals(1, result.pendingScorers.size)
        assertEquals("Runner3", result.pendingScorers[0].runner.name)
        assertTrue("Runner on 3B must be marked as FORCED on bases loaded single", result.pendingScorers[0].isForced)
    }

    @Test
    fun homeRun_basesLoaded_everyoneIsPending() {
        val current = mapOf(1 to r1, 2 to r2, 3 to r3)
        val result = RunnerManager.advanceOnHit(current, batter, 4)

        assertTrue(result.autoScoring.isEmpty())
        assertEquals(4, result.pendingScorers.size)
        assertTrue(result.pendingScorers.all { it.isForced })
        
        val batterPending = result.pendingScorers.find { it.runner.slot == 1 }
        assertNotNull("Batter must be in pending list on Home Run", batterPending)
    }

    // ── OVERTAKING LOGIC (Specialized) ────────────────────────────────────────

    @Test
    fun isOvertaking_forward_blockedByRunner() {
        val current = mapOf(2 to r2)
        // Runner on 1B wants to go to 3B, but 2B is occupied
        assertTrue(RunnerManager.isOvertaking(current, 1, 3))
    }

    @Test
    fun isOvertaking_forward_clearPath() {
        val current = mapOf(3 to r3)
        // Runner on 1B wants to go to 2B, path to 2B is clear
        assertFalse(RunnerManager.isOvertaking(current, 1, 2))
    }

    @Test
    fun isOvertaking_backward_blockedByRunner() {
        val current = mapOf(1 to r1)
        // Runner on 2B wants to go back to 1B, but 1B is occupied
        assertTrue(RunnerManager.isOvertaking(current, 2, 1))
    }

    @Test
    fun isOvertaking_toHome_blockedByRunnerOn3B() {
        val current = mapOf(3 to r3)
        // Runner on 2B wants to go Home (0), but 3B is occupied
        assertTrue(RunnerManager.isOvertaking(current, 2, 0))
    }
}
