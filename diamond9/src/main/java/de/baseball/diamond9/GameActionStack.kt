package de.baseball.diamond9

/**
 * Action types pushed onto the per-session action stack.
 * Pure data — no Android or DB dependencies.
 */
sealed class GameAction {

    /** A pitch recorded for the current at-bat (offense). atBatId needed for undo. */
    data class Pitch(val atBatId: Long) : GameAction()

    /**
     * A batter was put out via the Out-button: at-bat completed, batter advanced.
     * completedAtBatId: the at-bat that received the out result.
     * completedSlot:    the batting-order slot of that batter.
     * prevInning/prevOuts: game state before this out was recorded.
     */
    data class BatterOut(
        val completedAtBatId: Long,
        val completedSlot: Int,
        val prevInning: Int,
        val prevOuts: Int
    ) : GameAction()

    /**
     * A runner was put out via the outs-indicator tap: batter stays, count reset.
     * For offense: newAtBatId is the fresh at-bat started after the reset;
     *              prevAtBatId / prevSlot are what was active before.
     * For defense: newAtBatId / prevAtBatId / prevSlot are unused (= -1).
     * prevInning/prevOuts: game state before this out.
     */
    data class RunnerOut(
        val prevInning: Int,
        val prevOuts: Int,
        val newAtBatId: Long = -1L,
        val prevAtBatId: Long = -1L,
        val prevSlot: Int = -1
    ) : GameAction()

    /**
     * A half-inning switch was confirmed.
     * prevState:        HalfInningState before the switch.
     * prevLeadoffSlot:  leadoff_slot value before the 3rd out was recorded.
     * prevInning:       inning counter before the 3rd out was recorded.
     */
    data class HalfInningChange(
        val prevState: HalfInningState,
        val prevLeadoffSlot: Int,
        val prevInning: Int,
        val prevRunners: List<GameRunner> = emptyList()
    ) : GameAction()

    /**
     * Runners advanced or were manually moved.
     * prevRunners: the state of the runners before this change.
     * prevScoreboardValue: optional, the runs value in the DB for the current half-inning before the change.
     */
    data class RunnerAdvance(
        val prevRunners: List<GameRunner>,
        val prevScoreboardValue: Int? = null
    ) : GameAction()
}

class GameActionStack {
    private val stack = ArrayDeque<GameAction>()
    fun push(action: GameAction) = stack.addLast(action)
    fun pop(): GameAction? = if (stack.isNotEmpty()) stack.removeLast() else null
    val isEmpty get() = stack.isEmpty()
}
