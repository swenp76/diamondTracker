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

        // Standard Hit: Only move forced runners (those on 1B, and those ahead of them if also forced)
        // Note: In common amateur/coach tracking, a "Hit" often only automatically moves the batter to 1B/2B/3B
        // and forces the runner on 1B to 2B. Other movements are usually manual.
        
        // 1. Determine if runner on 1st is forced
        if (current.containsKey(1)) {
            val r1 = current[1]!!
            // If there's a runner on 2nd, they are also forced
            if (current.containsKey(2)) {
                val r2 = current[2]!!
                // If there's a runner on 3rd, they are also forced
                if (current.containsKey(3)) {
                    scoringRunners.add(current[3]!!.copy(base = 4))
                    next.remove(3)
                }
                next[3] = r2.copy(base = 3)
                next.remove(2)
            }
            next[2] = r1.copy(base = 2)
            next.remove(1)
        }

        // 2. Add batter to their reached base
        // If the base is already occupied (e.g. batter reaches 2B but 2B runner didn't move),
        // we overwrite it for now (manual correction needed by coach).
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
