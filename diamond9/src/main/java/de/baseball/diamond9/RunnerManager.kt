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
        val next = current.toMutableMap()
        val scoringRunners = mutableListOf<GameRunner>()

        if (bases >= 4) {
            // Home Run: Everyone scores
            for (base in 1..3) {
                current[base]?.let { scoringRunners.add(it.copy(base = 4)) }
            }
            scoringRunners.add(batter.copy(base = 4))
            return emptyMap<Int, GameRunner>() to scoringRunners
        }

        // Standard Hit: Only move runners if they are FORCED.
        // A runner is forced only if the batter takes their base, OR if the runner 
        // behind them is forced to move into their base.
        
        // Batter always takes 'bases' (1B, 2B, or 3B). 
        // We calculate forces starting from 1B because the batter always takes 1B initially.
        
        var forceOnBase = 1 // Batter always forces 1B
        for (b in 1..3) {
            if (b == forceOnBase) {
                val runnerAtB = next[b]
                if (runnerAtB != null) {
                    // Runner at this base is forced to the next
                    next.remove(b)
                    if (b + 1 >= 4) {
                        scoringRunners.add(runnerAtB.copy(base = 4))
                    } else {
                        next[b + 1] = runnerAtB.copy(base = b + 1)
                        // The force now moves to the next base
                        forceOnBase = b + 1
                    }
                } else {
                    // No runner at this base, the force chain breaks here
                    break
                }
            }
        }

        // Add batter to their reached base. 
        next[bases] = batter.copy(base = bases)

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
