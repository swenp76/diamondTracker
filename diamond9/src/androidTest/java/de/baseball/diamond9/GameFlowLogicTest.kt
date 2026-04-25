package de.baseball.diamond9

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests the game flow logic: inning progression, half-inning transitions,
 * and run suggestion parameters (team index and inning number).
 */
class GameFlowLogicTest {

    private lateinit var halfInningState: HalfInningState
    private var inning = 1
    private var outs = 0

    @Before
    fun setUp() {
        halfInningState = HalfInningState(inning = 1, isTopHalf = true)
        inning = 1
        outs = 0
    }

    /**
     * Replicates the logic from BattingTrackActivity and PitchTrackActivity
     * after a 3rd out is recorded.
     */
    private fun handleThirdOut(): Pair<Int, Int> {
        // 1. Capture parameters for RunSuggestionDialog
        val capturedInningForSuggestion = halfInningState.inning
        val teamIndexForSuggestion = if (halfInningState.isTopHalf) 0 else 1

        // 2. Logic to update the internal 'inning' counter (used for DB/UI labels)
        if (!halfInningState.isTopHalf) {
            inning++
        }
        outs = 0

        // 3. Transition to next half-inning (happens in the confirm sheet)
        halfInningState = HalfInningManager.next(halfInningState)

        return capturedInningForSuggestion to teamIndexForSuggestion
    }

    @Test
    fun topOfFirst_ends_suggestsInning1_andAwayTeam() {
        // Current state: ▲ 1
        val (suggestedInning, teamIndex) = handleThirdOut()
        
        assertEquals("Should suggest Inning 1", 1, suggestedInning)
        assertEquals("Should suggest Away team (Index 0)", 0, teamIndex)
        assertEquals("Internal Inning counter should still be 1 after TOP 1 ends", 1, inning)
        assertEquals("Next state should be BOT 1", HalfInningState(1, false), halfInningState)
    }

    @Test
    fun bottomOfFirst_ends_suggestsInning1_andHomeTeam() {
        // Simulate start of BOT 1
        halfInningState = HalfInningState(1, false)
        inning = 1
        outs = 0

        val (suggestedInning, teamIndex) = handleThirdOut()
        
        assertEquals("Should suggest Inning 1", 1, suggestedInning)
        assertEquals("Should suggest Home team (Index 1)", 1, teamIndex)
        assertEquals("Internal Inning counter should increment to 2 after BOT 1 ends", 2, inning)
        assertEquals("Next state should be TOP 2", HalfInningState(2, true), halfInningState)
    }

    @Test
    fun fullGameProgression_maintainsCorrectInningAndTeams() {
        // TOP 1
        val (s1, t1) = handleThirdOut()
        assertEquals(1, s1); assertEquals(0, t1); assertEquals(1, inning)
        
        // BOT 1
        val (s2, t2) = handleThirdOut()
        assertEquals(1, s2); assertEquals(1, t2); assertEquals(2, inning)
        
        // TOP 2
        val (s3, t3) = handleThirdOut()
        assertEquals(2, s3); assertEquals(0, t3); assertEquals(2, inning)
        
        // BOT 2
        val (s4, t4) = handleThirdOut()
        assertEquals(2, s4); assertEquals(1, t4); assertEquals(3, inning)
    }
}
