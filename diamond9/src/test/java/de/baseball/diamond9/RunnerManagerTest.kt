package de.baseball.diamond9

import org.junit.Assert.*
import org.junit.Test

class RunnerManagerTest {

    private val batter = GameRunner(gameId = 1L, base = 0, name = "Batter", slot = 1)
    private val r1 = GameRunner(gameId = 1L, base = 1, name = "Runner1", slot = 2)
    private val r2 = GameRunner(gameId = 1L, base = 2, name = "Runner2", slot = 3)
    private val r3 = GameRunner(gameId = 1L, base = 3, name = "Runner3", slot = 4)

    @Test
    fun single_noRunners_movesBatterTo1B() {
        val current = emptyMap<Int, GameRunner>()
        val (next, scoring) = RunnerManager.advanceOnHit(current, batter, 1)
        
        assertEquals(1, next.size)
        assertEquals(1, next[1]?.base)
        assertTrue(scoring.isEmpty())
    }

    @Test
    fun single_runnerOn3B_doesNotMoveRunnerOn3B() {
        // Force should break at 1B
        val current = mapOf(3 to r3)
        val (next, scoring) = RunnerManager.advanceOnHit(current, batter, 1)
        
        assertEquals(2, next.size)
        assertEquals(1, next[1]?.base) // Batter on 1B
        assertEquals(3, next[3]?.base) // Runner still on 3B
        assertTrue(scoring.isEmpty())
    }

    @Test
    fun single_runnerOn1B_movesRunnerTo2B() {
        val current = mapOf(1 to r1)
        val (next, scoring) = RunnerManager.advanceOnHit(current, batter, 1)
        
        assertEquals(2, next.size)
        assertEquals(1, next[1]?.base) // Batter on 1B
        assertEquals(2, next[2]?.base) // R1 forced to 2B
        assertTrue(scoring.isEmpty())
    }

    @Test
    fun single_basesLoaded_scoresRunnerOn3B() {
        val current = mapOf(1 to r1, 2 to r2, 3 to r3)
        val (next, scoring) = RunnerManager.advanceOnHit(current, batter, 1)
        
        assertEquals(3, next.size)
        assertEquals(1, next[1]?.base)
        assertEquals(2, next[2]?.base)
        assertEquals(3, next[3]?.base)
        assertEquals(1, scoring.size)
        assertEquals("Runner3", scoring[0].name)
    }

    @Test
    fun double_noRunners_movesBatterTo2B() {
        val current = emptyMap<Int, GameRunner>()
        val (next, scoring) = RunnerManager.advanceOnHit(current, batter, 2)
        
        assertEquals(1, next.size)
        assertEquals(2, next[2]?.base)
        assertTrue(scoring.isEmpty())
    }

    @Test
    fun double_runnerOn2B_doesNotMoveRunnerOn2B() {
        // Force breaks at 1B
        val current = mapOf(2 to r2)
        val (next, scoring) = RunnerManager.advanceOnHit(current, batter, 2)
        
        // Note: Batter reaches 2B, overwriting current runner on 2B 
        // in this simple logic (manual correction expected for non-forced runner)
        assertEquals(1, next.size)
        assertEquals(1, next[2]?.slot) // The batter is now on 2B
    }

    @Test
    fun homeRun_basesLoaded_scoresEveryone() {
        val current = mapOf(1 to r1, 2 to r2, 3 to r3)
        val (next, scoring) = RunnerManager.advanceOnHit(current, batter, 4)
        
        assertTrue(next.isEmpty())
        assertEquals(4, scoring.size)
    }
}
