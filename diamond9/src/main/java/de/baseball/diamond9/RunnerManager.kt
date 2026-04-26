package de.baseball.diamond9

object RunnerManager {

    /**
     * Calculates the next state of runners after a batter reaches a base.
     * 
     * [current]: Current runners on base (base index to Runner)
     * [batter]: The batter who just put the ball in play
     * [bases]: How many bases the batter reached (1=1B, 2=2B, 3=3B, 4=HR)
     * 
     * Returns a Pair of (Updated Runners Map, List of Scoring Runners)
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
            current.values.forEach { scoringRunners.add(it.copy(base = 4)) }
            scoringRunners.add(batter.copy(base = 4))
            return emptyMap<Int, GameRunner>() to scoringRunners
        }

        // 1. Identify which runners are forced or displaced by the batter.
        // A runner is "displaced" if the batter takes their current base or any base ahead of them
        // that they haven't reached yet.
        // In this app, we automate the obvious moves:
        // - Single (1B): Forced runners move 1 base. Non-forced stay.
        // - Double (2B): All runners move at least 2 bases. 2nd and 3rd score. 1st moves to 3rd.
        // - Triple (3B): All runners score.

        when (bases) {
            1 -> {
                // Single: Only move runners who are forced
                val force2 = current.containsKey(1)
                val force3 = force2 && current.containsKey(2)
                val forceHome = force3 && current.containsKey(3)

                // Move from 3rd down to 1st
                if (forceHome) next.remove(3)?.let { scoringRunners.add(it.copy(base = 4)) }
                if (force3) next.remove(2)?.let { next[3] = it.copy(base = 3) }
                if (force2) next.remove(1)?.let { next[2] = it.copy(base = 2) }
                next[1] = batter.copy(base = 1)
            }
            2 -> {
                // Double: Move all runners at least 2 bases
                // Runners on 2nd and 3rd always score on a double in this automation
                next.remove(3)?.let { scoringRunners.add(it.copy(base = 4)) }
                next.remove(2)?.let { scoringRunners.add(it.copy(base = 4)) }
                // Runner on 1st moves to 3rd
                next.remove(1)?.let { next[3] = it.copy(base = 3) }
                next[2] = batter.copy(base = 2)
            }
            3 -> {
                // Triple: All existing runners score
                current.values.forEach { scoringRunners.add(it.copy(base = 4)) }
                next.clear()
                next[3] = batter.copy(base = 3)
            }
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

        // A walk forces 1st. 2nd is forced if 1st was occupied. 3rd is forced if 1st and 2nd were occupied.
        val force2 = current.containsKey(1)
        val force3 = force2 && current.containsKey(2)
        val forceHome = force3 && current.containsKey(3)

        if (forceHome) next.remove(3)?.let { scoringRunners.add(it.copy(base = 4)) }
        if (force3) next.remove(2)?.let { next[3] = it.copy(base = 3) }
        if (force2) next.remove(1)?.let { next[2] = it.copy(base = 2) }
        next[1] = batter.copy(base = 1)

        return next to scoringRunners
    }
}
