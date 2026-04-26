package de.baseball.diamond9

import org.junit.Test
import org.junit.Assert.*

class BaseAdvanceScenariosTest {

    private val batter = GameRunner(gameId = 1, base = 0, name = "Batter", slot = 0)
    private val r1 = GameRunner(gameId = 1, base = 1, name = "R1", slot = 1)
    private val r2 = GameRunner(gameId = 1, base = 2, name = "R2", slot = 2)
    private val r3 = GameRunner(gameId = 1, base = 3, name = "R3", slot = 3)

    private fun testScenario(occupied: List<Int>, bases: Int, isWalk: Boolean = false) {
        val current = mutableMapOf<Int, GameRunner>()
        if (1 in occupied) current[1] = r1
        if (2 in occupied) current[2] = r2
        if (3 in occupied) current[3] = r3

        val (next, scoring) = if (isWalk) {
            RunnerManager.advanceOnWalk(current, batter)
        } else {
            RunnerManager.advanceOnHit(current, batter, bases)
        }

        val action = if (isWalk) "Walk" else "Hit ($bases)"
        val state = occupied.joinToString(",")
        val resultRunners = next.keys.sorted().joinToString(",")
        val scoredCount = scoring.size

        println("Scenario: $state | Action: $action -> Runners: [$resultRunners] | Scored: $scoredCount")
    }

    @Test
    fun evaluateAllCombinations() {
        val combinations = listOf(
            emptyList<Int>(),
            listOf(1),
            listOf(2),
            listOf(3),
            listOf(1, 2),
            listOf(1, 3),
            listOf(2, 3),
            listOf(1, 2, 3)
        )

        println("--- Testing SINGLE (bases=1) ---")
        combinations.forEach { testScenario(it, 1) }

        println("\n--- Testing WALK ---")
        combinations.forEach { testScenario(it, 1, true) }

        println("\n--- Testing DOUBLE (bases=2) ---")
        combinations.forEach { testScenario(it, 2) }
    }
}
