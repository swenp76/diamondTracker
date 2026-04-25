package de.baseball.diamond9

object RunnerManager {

    /**
     * Calculates the next state of runners after a batter reaches a base.
     * 
     * [current]: Current runners on base (base index to Runner)
     * [batter]: The batter who just put the ball in play
     * [bases]: How many bases the batter reached (1=1B, 2=2B, 3=3B, 4=HR)
     * 
     * Returns a Pair of (Updated Runners, Number of Scores)
     */
    fun advanceOnHit(
        current: Map<Int, GameRunner>,
        batter: GameRunner,
        bases: Int
    ): Pair<Map<Int, GameRunner>, List<GameRunner>> {
        val next = mutableMapOf<Int, GameRunner>()
        val scoringRunners = mutableListOf<GameRunner>()

        // Simple "Advance N Bases" logic for all current runners
        for (base in 1..3) {
            val r = current[base] ?: continue
            val target = base + bases
            if (target >= 4) {
                scoringRunners.add(r.copy(base = 4))
            } else {
                next[target] = r.copy(base = target)
            }
        }

        // Add batter to their base
        if (bases < 4) {
            next[bases] = batter.copy(base = bases)
        } else {
            scoringRunners.add(batter.copy(base = 4))
        }

        return next to scoringRunners
    }

    /**
     * Calculates the next state of runners on a Walk (BB) or HBP.
     * Only runners forced by the batter or preceding runners advance.
     */
    fun advanceOnWalk(
        current: Map<Int, GameRunner>,
        batter: GameRunner
    ): Pair<Map<Int, GameRunner>, List<GameRunner>> {
        val next = current.toMutableMap()
        val scoringRunners = mutableListOf<GameRunner>()

        if (current.containsKey(1)) {
            if (current.containsKey(2)) {
                if (current.containsKey(3)) {
                    scoringRunners.add(current[3]!!.copy(base = 4))
                }
                next[3] = current[2]!!.copy(base = 3)
            }
            next[2] = current[1]!!.copy(base = 2)
        }
        next[1] = batter.copy(base = 1)

        return next to scoringRunners
    }
}
