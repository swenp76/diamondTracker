package de.baseball.diamond9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunnerManagerTest {

    private val batter = GameRunner(gameId = 1L, base = 0, name = "Batter", slot = 1)
    private val r1 = GameRunner(gameId = 1L, base = 1, name = "Runner1", slot = 2)
    private val r2 = GameRunner(gameId = 1L, base = 2, name = "Runner2", slot = 3)
    private val r3 = GameRunner(gameId = 1L, base = 3, name = "Runner3", slot = 4)

    @Test
    fun single_noRunners_movesBatterTo1B() {
        val result = RunnerManager.advanceOnHit(emptyMap(), batter, 1)

        assertEquals(1, result.nextRunners.size)
        assertEquals(1, result.nextRunners[1]?.base)
        assertTrue(result.autoScoring.isEmpty())
        assertTrue(result.pendingScorers.isEmpty())
    }

    @Test
    fun single_runnerOn3B_becomesPendingScorer() {
        val result = RunnerManager.advanceOnHit(mapOf(3 to r3), batter, 1)

        // 3B runner is non-forced → pending scorer, not in nextRunners
        assertEquals(1, result.nextRunners.size)
        assertEquals(1, result.nextRunners[1]?.base)
        assertTrue(result.autoScoring.isEmpty())
        assertEquals(1, result.pendingScorers.size)
        assertEquals("Runner3", result.pendingScorers[0].runner.name)
        assertEquals(3, result.pendingScorers[0].stayBase)
    }

    @Test
    fun single_runnerOn1B_movesRunnerTo2B() {
        val result = RunnerManager.advanceOnHit(mapOf(1 to r1), batter, 1)

        assertEquals(2, result.nextRunners.size)
        assertEquals(1, result.nextRunners[1]?.base)
        assertEquals(2, result.nextRunners[2]?.base)
        assertTrue(result.autoScoring.isEmpty())
        assertTrue(result.pendingScorers.isEmpty())
    }

    @Test
    fun single_basesLoaded_scoresRunnerOn3BAutomatically() {
        val result = RunnerManager.advanceOnHit(mapOf(1 to r1, 2 to r2, 3 to r3), batter, 1)

        assertEquals(3, result.nextRunners.size)
        assertEquals(1, result.nextRunners[1]?.base)
        assertEquals(2, result.nextRunners[2]?.base)
        assertEquals(3, result.nextRunners[3]?.base)
        assertEquals(1, result.autoScoring.size)
        assertEquals("Runner3", result.autoScoring[0].name)
        assertTrue(result.pendingScorers.isEmpty())
    }

    @Test
    fun double_noRunners_movesBatterTo2B() {
        val result = RunnerManager.advanceOnHit(emptyMap(), batter, 2)

        assertEquals(1, result.nextRunners.size)
        assertEquals(2, result.nextRunners[2]?.base)
        assertTrue(result.autoScoring.isEmpty())
        assertTrue(result.pendingScorers.isEmpty())
    }

    @Test
    fun double_runnerOn2B_becomesPendingScorer() {
        val result = RunnerManager.advanceOnHit(mapOf(2 to r2), batter, 2)

        assertEquals(1, result.nextRunners.size)
        assertEquals(1, result.nextRunners[2]?.slot) // batter is on 2B
        assertTrue(result.autoScoring.isEmpty())
        assertEquals(1, result.pendingScorers.size)
        assertEquals("Runner2", result.pendingScorers[0].runner.name)
    }

    @Test
    fun homeRun_basesLoaded_scoresEveryone() {
        val result = RunnerManager.advanceOnHit(mapOf(1 to r1, 2 to r2, 3 to r3), batter, 4)

        assertTrue(result.nextRunners.isEmpty())
        assertEquals(4, result.autoScoring.size)
        assertTrue(result.pendingScorers.isEmpty())
    }

    @Test
    fun walk_runnerOn3BOnly_doesNotMoveRunnerOn3B() {
        val current = mapOf(3 to r3)
        val (next, scoring) = RunnerManager.advanceOnWalk(current, batter)

        assertEquals(2, next.size)
        assertEquals(1, next[1]?.base) // Batter on 1B
        assertEquals(3, next[3]?.base) // Runner still on 3B
        assertTrue(scoring.isEmpty())
    }

    @Test
    fun walk_runnerOn2BAnd3B_doesNotMoveRunners() {
        val current = mapOf(2 to r2, 3 to r3)
        val (next, scoring) = RunnerManager.advanceOnWalk(current, batter)

        assertEquals(3, next.size)
        assertEquals(1, next[1]?.base) // Batter
        assertEquals(2, next[2]?.base) // Runner on 2B
        assertEquals(3, next[3]?.base) // Runner on 3B
        assertTrue(scoring.isEmpty())
    }

    @Test
    fun walk_runnersOn1BAnd3B_movesRunnerTo2B() {
        val current = mapOf(1 to r1, 3 to r3)
        val (next, scoring) = RunnerManager.advanceOnWalk(current, batter)

        assertEquals(3, next.size)
        assertEquals(1, next[1]?.base) // Batter
        assertEquals(2, next[2]?.base) // R1 forced to 2B
        assertEquals(3, next[3]?.base) // R3 NOT forced
        assertTrue(scoring.isEmpty())
    }

    // ── Manual Overtaking Checks ──────────────────────────────────────────────

    @Test
    fun isOvertaking_1Bto3B_withRunnerOn2B_returnsTrue() {
        val current = mapOf(1 to r1, 2 to r2)
        assertTrue(RunnerManager.isOvertaking(current, 1, 3))
    }

    @Test
    fun isOvertaking_1Bto3B_noRunnerOn2B_returnsFalse() {
        val current = mapOf(1 to r1)
        org.junit.Assert.assertFalse(RunnerManager.isOvertaking(current, 1, 3))
    }

    @Test
    fun isOvertaking_1BtoHome_withRunnerOn3B_returnsTrue() {
        val current = mapOf(1 to r1, 3 to r3)
        assertTrue(RunnerManager.isOvertaking(current, 1, 0)) // 0 means Score/Home
    }

    @Test
    fun isOvertaking_2BtoHome_noRunnerOn3B_returnsFalse() {
        val current = mapOf(2 to r2)
        org.junit.Assert.assertFalse(RunnerManager.isOvertaking(current, 2, 0))
    }

    @Test
    fun isOvertaking_2Bto1B_withRunnerOn1B_returnsTrue() {
        val current = mapOf(2 to r2, 1 to r1)
        assertTrue(RunnerManager.isOvertaking(current, 2, 1))
    }
}
