package de.baseball.diamond9

/**
 * Pure logic – no Android dependencies.
 */
data class HalfInningState(val inning: Int, val isTopHalf: Boolean) {
    /** Full label, e.g. "▲ TOP  •  Inning 3" */
    val label: String
        get() = "${if (isTopHalf) "▲ TOP" else "▼ BOT"}  •  Inning $inning"

    /** Short label for compact displays, e.g. "▲ 3" */
    val shortLabel: String
        get() = "${if (isTopHalf) "▲" else "▼"} $inning"
}

object HalfInningManager {
    /** Returns the next half-inning state after the current one ends. */
    fun next(current: HalfInningState): HalfInningState =
        if (current.isTopHalf) current.copy(isTopHalf = false)
        else HalfInningState(inning = current.inning + 1, isTopHalf = true)
}
