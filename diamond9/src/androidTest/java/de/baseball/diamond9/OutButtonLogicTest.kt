package de.baseball.diamond9

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests the out-counting and batter-advancing logic from BattingTrackScreen.
 *
 * Fix #4: Out-Button (incrementOuts) must NOT advance the batter.
 * Only strikeOut() (3 strikes) increments outs AND advances the batter.
 */
class OutButtonLogicTest {

    private var inning = 1
    private var outs = 0
    private var batterSlot = 1
    private val maxSlot = 9

    @Before
    fun setUp() {
        inning = 1
        outs = 0
        batterSlot = 1
        isTopHalf = true
    }

    private var isTopHalf = true

    // ── replicated logic from BattingTrackScreen ───────────────────────────────

    private fun incrementOuts() {
        val newOuts = outs + 1
        if (newOuts >= 3) {
            if (!isTopHalf) {
                inning++
            }
            outs = 0
            isTopHalf = !isTopHalf // simplified transition for this test
        } else {
            outs = newOuts
        }
    }

    private fun nextBatter() {
        batterSlot = (batterSlot % maxSlot) + 1
    }

    private fun strikeOut() {
        incrementOuts()
        nextBatter()
    }

    // ── Out-Button: only outs counter, no batter change ───────────────────────

    @Test
    fun outButton_incrementsOuts() {
        incrementOuts()
        assertEquals(1, outs)
    }

    @Test
    fun outButton_doesNotAdvanceBatter() {
        incrementOuts()
        assertEquals(1, batterSlot)
    }

    @Test
    fun outButton_twoOuts_doesNotAdvanceBatter() {
        incrementOuts()
        incrementOuts()
        assertEquals(2, outs)
        assertEquals(1, batterSlot)
    }

    @Test
    fun outButton_threeOuts_staysInFirstInning_resetsOuts() {
        incrementOuts()
        incrementOuts()
        incrementOuts()
        assertEquals(1, inning) // TOP 1 finished -> still Inning 1 (now BOT 1)
        assertEquals(0, outs)
    }

    @Test
    fun outButton_sixOuts_advancesInning_resetsOuts() {
        repeat(3) { incrementOuts() } // TOP 1 ends
        repeat(3) { incrementOuts() } // BOT 1 ends
        assertEquals(2, inning)
        assertEquals(0, outs)
    }

    @Test
    fun outButton_threeOuts_stillDoesNotAdvanceBatter() {
        incrementOuts()
        incrementOuts()
        incrementOuts()
        assertEquals(1, batterSlot)
    }

    // ── StrikeOut: outs counter + batter advance ───────────────────────────────

    @Test
    fun strikeOut_incrementsOuts() {
        strikeOut()
        assertEquals(1, outs)
    }

    @Test
    fun strikeOut_advancesBatter() {
        strikeOut()
        assertEquals(2, batterSlot)
    }

    @Test
    fun strikeOut_sixOuts_advancesInning_andBatter() {
        repeat(3) { strikeOut() } // TOP 1
        repeat(3) { strikeOut() } // BOT 1
        assertEquals(2, inning)
        assertEquals(0, outs)
        assertEquals(7, batterSlot)
    }

    @Test
    fun strikeOut_batterWrapsAroundAfterSlot9() {
        repeat(9) { strikeOut() }
        assertEquals(1, batterSlot)
    }

    // ── Mixed: out buttons and strikeouts interleaved ─────────────────────────

    @Test
    fun mixed_outAndStrikeOut_correctOutCount() {
        incrementOuts()  // out button: outs=1, batter unchanged
        strikeOut()      // strikeout:  outs=2, batter advances
        assertEquals(2, outs)
        assertEquals(2, batterSlot)
    }

    @Test
    fun mixed_stolenBaseOut_doesNotChangeBatter_thenStrikeOutDoes() {
        // Stolen base out (Out button) → batter stays
        incrementOuts()
        assertEquals(1, batterSlot)
        // Then batter strikes out → next batter
        strikeOut()
        assertEquals(2, batterSlot)
    }
}
