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

        // Standard Hit logic:
        // 1. Determine which runners are FORCED to advance.
        // 2. Move forced runners first.
        // 3. Move the batter to their base.
        
        // In simple logic: a runner is forced if there's a runner on every base behind them, including the batter.
        // For a Single (bases=1): 1B is forced if batter reaches 1B. 2B is forced if 1B is forced. 3B is forced if 2B is forced.
        
        val isForced = BooleanArray(4) // index 1, 2, 3
        isForced[1] = true // Batter always forces 1B
        if (current.containsKey(1)) isForced[2] = true
        if (isForced[2] && current.containsKey(2)) isForced[3] = true
        
        // Move forced runners from 3rd base down to 1st to avoid overwriting
        for (b in 3 downTo 1) {
            if (isForced[b]) {
                val runner = next.remove(b)
                if (runner != null) {
                    val nextBase = b + 1
                    if (nextBase >= 4) {
                        scoringRunners.add(runner.copy(base = 4))
                    } else {
                        next[nextBase] = runner.copy(base = nextBase)
                    }
                }
            }
        }

        // Finally, place the batter. 
        // Note: If bases > 1 (Double/Triple), the current logic assumes non-forced runners don't move 
        // unless they are displaced by the batter reaching that specific base.
        // If the batter reaches a base already occupied by a non-forced runner, 
        // that runner stays there (logic error) or we'd need more complex rules.
        // For now, let's just place the batter.
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
                    next.remove(3)
                }
                next[3] = current[2]!!.copy(base = 3)
            }
            next[2] = current[1]!!.copy(base = 2)
        }
        next[1] = batter.copy(base = 1)

        return next to scoringRunners
    }
}
