package de.baseball.diamond9

data class PendingScorer(val runner: GameRunner, val stayBase: Int, val isForced: Boolean = false)

data class HitAdvanceResult(
    val nextRunners: Map<Int, GameRunner>,
    val autoScoring: List<GameRunner>,
    val pendingScorers: List<PendingScorer>
)

object RunnerManager {

    /**
     * Calculates the next state of runners after a batter reaches a base.
     *
     * Forced runners advance automatically (including scoring when forced home).
     * Non-forced runners advance automatically to their suggested base, except when
     * that suggested base is home — those are returned as [HitAdvanceResult.pendingScorers]
     * so the UI can ask the coach for confirmation.
     *
     * [current]: Current runners on base (base index to Runner)
     * [batter]: The batter who just put the ball in play
     * [bases]: How many bases the batter reached (1=1B, 2=2B, 3=3B, 4=HR)
     */
    fun advanceOnHit(
        current: Map<Int, GameRunner>,
        batter: GameRunner,
        bases: Int
    ): HitAdvanceResult {
        if (bases >= 4) {
            // Everyone scores, but must be confirmed. All are forced.
            val pendingScorers = (current.values.toList().sortedByDescending { it.base } + batter).map { 
                PendingScorer(it, stayBase = if (it.base == 0) 3 else it.base, isForced = true) 
            }
            return HitAdvanceResult(emptyMap(), emptyList(), pendingScorers)
        }

        if (bases == 3) {
            // Everyone scores, but must be confirmed. All are forced. Batter stays on 3B.
            val pendingScorers = current.values.toList().sortedByDescending { it.base }.map { 
                PendingScorer(it, stayBase = it.base, isForced = true) 
            }
            return HitAdvanceResult(mapOf(3 to batter.copy(base = 3)), emptyList(), pendingScorers)
        }

        val next = current.toMutableMap()
        val autoScoring = mutableListOf<GameRunner>()
        val pendingScorers = mutableListOf<PendingScorer>()

        when (bases) {
            1 -> {
                val force2 = current.containsKey(1)
                val force3 = force2 && current.containsKey(2)
                val forceHome = force3 && current.containsKey(3)

                // 3B runner
                next.remove(3)?.let { 
                    pendingScorers.add(PendingScorer(it, stayBase = 3, isForced = forceHome)) 
                }
                
                if (force3) {
                    next.remove(2)?.let { next[3] = it.copy(base = 3) }
                } else {
                    // Non-forced runner on 2nd: could score or stay on 3rd
                    next.remove(2)?.let { pendingScorers.add(PendingScorer(it, stayBase = 3, isForced = false)) }
                }

                if (force2) next.remove(1)?.let { next[2] = it.copy(base = 2) }
                next[1] = batter.copy(base = 1)
            }
            2 -> {
                // Potential scorers from 3B, 2B and maybe 1B
                val force2 = current.containsKey(1)
                val force3 = force2 && current.containsKey(2)
                val forceHome = force3 && current.containsKey(3)

                // Runner on 3rd is forced if bases were loaded (or at least 1st and 2nd occupied)
                next.remove(3)?.let { pendingScorers.add(PendingScorer(it, stayBase = 3, isForced = forceHome)) }
                
                // Runner on 2nd is forced to 3rd by the batter reaching 2nd IF 1st was occupied.
                // If they go for home, it's a choice UNLESS they were forced home by preceding runners.
                // Wait: If I hit a double, I occupy 2B. 
                // If there was a runner on 2B, they MUST move to 3B or Home. They cannot stay on 2B.
                // So they are forced to at least 3B. If we ask about Home, it's usually a choice
                // UNLESS the runner behind them (from 1B) is also forced to 3B.
                next.remove(2)?.let { pendingScorers.add(PendingScorer(it, stayBase = 3, isForced = force3)) }
                
                // Runner on 1B is forced to at least 2B. On a double they usually reach 3rd.
                next.remove(1)?.let { next[3] = it.copy(base = 3) }

                next[2] = batter.copy(base = 2)
            }
        }

        return HitAdvanceResult(next.toMap(), autoScoring, pendingScorers)
    }

    /**
     * Checks if moving a runner from [currentBase] to [newBase] would overtake another runner.
     * [current]: Map of base index to GameRunner.
     * [currentBase]: The base where the runner is currently.
     * [newBase]: The target base (1, 2, 3, or 0/4 for Home/Score).
     * 
     * Returns true if the move is BLOCKED (overtaking occurs).
     */
    fun isOvertaking(current: Map<Int, GameRunner>, currentBase: Int, newBase: Int): Boolean {
        // Target base 0 or 4 means Home
        val target = if (newBase == 0) 4 else newBase
        
        // If moving forward (target > currentBase)
        if (target > currentBase) {
            // Check all bases between currentBase and target
            for (b in (currentBase + 1)..minOf(target, 3)) {
                if (current.containsKey(b)) return true
            }
        }
        // If moving backward (target < currentBase)
        else if (target < currentBase && target > 0) {
             // Check if target base is occupied
             if (current.containsKey(target)) return true
        }
        
        return false
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
