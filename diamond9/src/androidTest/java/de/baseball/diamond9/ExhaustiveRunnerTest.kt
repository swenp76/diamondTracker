package de.baseball.diamond9

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Senior QA Automation Engineer Suite - Comprehensive Edition
 * 
 * DESIGN DECISION:
 * - WALKS: Automatic advancement. Forced runners scoring via Walk are added to 'autoScoring'.
 * - HITS (S, D, T, HR): All runners reaching home are added to 'pendingScorers' for manual 
 *   confirmation, even if forced. They must have 'isForced = true' if a force play exists.
 */
@RunWith(Parameterized::class)
class ExhaustiveRunnerTest(
    private val config: String,
    private val event: Event,
    private val expectedRunners: Set<Int>, // Bases occupied after auto-proposal (1, 2, 3)
    private val expectedRuns: Int,        // Runs automatically scored (ONLY for Walks)
    private val expectedPending: Int      // Runners requiring confirmation (ALL scorers on hits + choice advances)
) {

    enum class Event(val bases: Int) {
        WALK(0), SINGLE(1), DOUBLE(2), TRIPLE(3), HOME_RUN(4)
    }

    private val batter = GameRunner(gameId = 1L, base = 0, name = "Batter", slot = 1)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: Config {0} + {1}")
        fun data(): Collection<Array<Any>> = listOf(
            // --- WALK / HBP (Only forced runners advance, forced home = autoScoring) ---
            arrayOf("---", Event.WALK,   setOf(1),       0, 0),
            arrayOf("1--", Event.WALK,   setOf(1, 2),    0, 0),
            arrayOf("-2-", Event.WALK,   setOf(1, 2),    0, 0),
            arrayOf("--3", Event.WALK,   setOf(1, 3),    0, 0),
            arrayOf("12-", Event.WALK,   setOf(1, 2, 3), 0, 0),
            arrayOf("1-3", Event.WALK,   setOf(1, 2, 3), 0, 0),
            arrayOf("-23", Event.WALK,   setOf(1, 2, 3), 0, 0),
            arrayOf("123", Event.WALK,   setOf(1, 2, 3), 1, 0), // Forced home = autoScoring

            // --- SINGLE (Batter to 1B, scorers are always pending) ---
            arrayOf("---", Event.SINGLE, setOf(1),       0, 0),
            arrayOf("1--", Event.SINGLE, setOf(1, 2),    0, 0),
            arrayOf("-2-", Event.SINGLE, setOf(1),       0, 1), // 2B -> Home choice (pending)
            arrayOf("--3", Event.SINGLE, setOf(1),       0, 1), // 3B -> Home choice (pending)
            arrayOf("12-", Event.SINGLE, setOf(1, 2, 3), 0, 0), // 1B->2B forced, 2B->3B forced
            arrayOf("1-3", Event.SINGLE, setOf(1, 2),    0, 1), // 1B->2B forced, 3B -> Home choice (pending)
            arrayOf("-23", Event.SINGLE, setOf(1),       0, 2), // 2B/3B both choices (pending)
            arrayOf("123", Event.SINGLE, setOf(1, 2, 3), 0, 1), // 3B forced home -> pendingScorers

            // --- DOUBLE (Batter to 2B, scorers are always pending) ---
            arrayOf("---", Event.DOUBLE, setOf(2),       0, 0),
            arrayOf("1--", Event.DOUBLE, setOf(2, 3),    0, 0), // 1B -> 3B auto
            arrayOf("-2-", Event.DOUBLE, setOf(2),       0, 1), // 2B -> Home choice (pending)
            arrayOf("--3", Event.DOUBLE, setOf(2),       0, 1), // 3B -> Home choice (pending)
            arrayOf("12-", Event.DOUBLE, setOf(2, 3),    0, 1), // 1B -> 3B auto, 2B -> Home choice (pending)
            arrayOf("1-3", Event.DOUBLE, setOf(2, 3),    0, 1), // 1B -> 3B auto, 3B -> Home choice (pending)
            arrayOf("-23", Event.DOUBLE, setOf(2),       0, 2), // 2B/3B both choice (pending)
            arrayOf("123", Event.DOUBLE, setOf(2, 3),    0, 2), // 2B/3B both pending

            // --- TRIPLE (Batter to 3B, everyone else scores -> pending) ---
            arrayOf("---", Event.TRIPLE, setOf(3),       0, 0),
            arrayOf("1--", Event.TRIPLE, setOf(3),       0, 1),
            arrayOf("-2-", Event.TRIPLE, setOf(3),       0, 1),
            arrayOf("--3", Event.TRIPLE, setOf(3),       0, 1),
            arrayOf("12-", Event.TRIPLE, setOf(3),       0, 2),
            arrayOf("1-3", Event.TRIPLE, setOf(3),       0, 2),
            arrayOf("-23", Event.TRIPLE, setOf(3),       0, 2),
            arrayOf("123", Event.TRIPLE, setOf(3),       0, 3),

            // --- HOME RUN (Everyone scores -> pending) ---
            arrayOf("---", Event.HOME_RUN, emptySet<Int>(), 0, 1), // Batter pending
            arrayOf("1--", Event.HOME_RUN, emptySet<Int>(), 0, 2),
            arrayOf("-2-", Event.HOME_RUN, emptySet<Int>(), 0, 2),
            arrayOf("--3", Event.HOME_RUN, emptySet<Int>(), 0, 2),
            arrayOf("12-", Event.HOME_RUN, emptySet<Int>(), 0, 3),
            arrayOf("1-3", Event.HOME_RUN, emptySet<Int>(), 0, 3),
            arrayOf("-23", Event.HOME_RUN, emptySet<Int>(), 0, 3),
            arrayOf("123", Event.HOME_RUN, emptySet<Int>(), 0, 4)
        )
    }

    @Test
    fun shouldGenerateCorrectAutoProposal() {
        val currentRunners = parseConfig(config)
        
        if (event == Event.WALK) {
            val (next, scoring) = RunnerManager.advanceOnWalk(currentRunners, batter)
            assertEquals("Wrong occupied bases for WALK on $config", expectedRunners, next.keys)
            assertEquals("Wrong automatic runs for WALK on $config", expectedRuns, scoring.size)
        } else {
            val result = RunnerManager.advanceOnHit(currentRunners, batter, event.bases)
            
            // 1. Verify general counts
            assertEquals("Wrong occupied bases for ${event.name} on $config", expectedRunners, result.nextRunners.keys)
            assertEquals("Wrong automatic runs (expected 0 for hits) for ${event.name} on $config", 0, result.autoScoring.size)
            assertEquals("Wrong pending scorers count for ${event.name} on $config", expectedPending, result.pendingScorers.size)
            
            // 2. Deep Logic Validation: isForced status
            result.pendingScorers.forEach { ps ->
                val runnerBase = ps.runner.base // 0 for batter, 1, 2, 3 for others
                val isActuallyForced = calculateForceStatus(config, event, runnerBase)
                assertEquals(
                    "isForced mismatch for runner on ${if(runnerBase==0) "Batter" else runnerBase} during ${event.name} on $config",
                    isActuallyForced,
                    ps.isForced
                )
            }
            
            // 3. Home Run Integrity: Batter must be a pending scorer
            if (event == Event.HOME_RUN) {
                val batterPending = result.pendingScorers.any { it.runner.slot == 1 }
                assertTrue("Batter (Slot 1) missing from pending scorers on Home Run", batterPending)
            }
        }
    }

    private fun calculateForceStatus(config: String, event: Event, base: Int): Boolean {
        if (event == Event.HOME_RUN || event == Event.TRIPLE) return true
        val r1 = config.contains("1")
        val r2 = config.contains("2")
        return when (base) {
            0 -> true
            1 -> true
            2 -> r1
            3 -> r1 && r2
            else -> false
        }
    }

    private fun parseConfig(c: String): Map<Int, GameRunner> {
        val map = mutableMapOf<Int, GameRunner>()
        if (c.contains("1")) map[1] = GameRunner(gameId = 1L, base = 1, playerId = 101L, slot = 2, name = "R1")
        if (c.contains("2")) map[2] = GameRunner(gameId = 1L, base = 2, playerId = 102L, slot = 3, name = "R2")
        if (c.contains("3")) map[3] = GameRunner(gameId = 1L, base = 3, playerId = 103L, slot = 4, name = "R3")
        return map
    }
}
