package de.baseball.diamond9

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for all pure functions in PitcherTrendHelper.kt:
 * buildBatterStats, rollingAverage, getTrendLevel, groupPitchesByBatter.
 *
 * No database needed – Pitch is just a plain data class at runtime.
 */
@RunWith(AndroidJUnit4::class)
class PitcherTrendHelperTest {

    private fun pitch(type: String, inning: Int = 1) =
        Pitch(pitcherId = 0, type = type, sequenceNr = 0, inning = inning)

    // ── buildBatterStats ──────────────────────────────────────────────────────

    @Test
    fun buildBatterStats_emptyList_returnsEmpty() {
        assertTrue(buildBatterStats(emptyList()).isEmpty())
    }

    @Test
    fun buildBatterStats_noBfMarker_returnsEmpty() {
        // Without a BF boundary the partial at-bat is not committed
        assertTrue(buildBatterStats(listOf(pitch("B"), pitch("S"))).isEmpty())
    }

    @Test
    fun buildBatterStats_singleBatter_correctCounts() {
        val pitches = listOf(pitch("B"), pitch("B"), pitch("S"), pitch("F"), pitch("BF"))
        val stats = buildBatterStats(pitches)
        assertEquals(1, stats.size)
        assertEquals(2, stats[0].balls)
        assertEquals(1, stats[0].strikes)
        assertEquals(1, stats[0].fouls)
        assertEquals(4, stats[0].total)
    }

    @Test
    fun buildBatterStats_strikePercent_includesFouls() {
        // 0 balls, 2 strikes, 2 fouls → (2+2)/4 = 1.0
        val pitches = listOf(pitch("S"), pitch("S"), pitch("F"), pitch("F"), pitch("BF"))
        val stats = buildBatterStats(pitches)
        assertEquals(1.0f, stats[0].strikePercent, 0.001f)
    }

    @Test
    fun buildBatterStats_allBalls_strikePercentZero() {
        val pitches = listOf(pitch("B"), pitch("B"), pitch("B"), pitch("BF"))
        val stats = buildBatterStats(pitches)
        assertEquals(0.0f, stats[0].strikePercent, 0.001f)
    }

    @Test
    fun buildBatterStats_soPitchCountsAsStrike() {
        // B, S, SO (strikeout pitch), BF → 1 ball, 2 strikes, total 3
        val pitches = listOf(pitch("B"), pitch("S"), pitch("SO"), pitch("BF"))
        val stats = buildBatterStats(pitches)
        assertEquals(1, stats.size)
        assertEquals(1, stats[0].balls)
        assertEquals(2, stats[0].strikes)   // S + SO
        assertEquals(3, stats[0].total)
        assertEquals(2f / 3f, stats[0].strikePercent, 0.001f)
    }

    @Test
    fun buildBatterStats_hbpCountsInTotalNotStrikes() {
        // B, B, B, HBP, BF → 3 balls, 0 strikes, total 4
        val pitches = listOf(pitch("B"), pitch("B"), pitch("B"), pitch("HBP"), pitch("BF"))
        val stats = buildBatterStats(pitches)
        assertEquals(1, stats.size)
        assertEquals(3, stats[0].balls)
        assertEquals(0, stats[0].strikes)
        assertEquals(4, stats[0].total)
        assertEquals(0f, stats[0].strikePercent, 0.001f)
    }

    @Test
    fun buildBatterStats_hPitchCountsInTotalNotStrikes() {
        // S, S, H, BF → 0 balls, 2 strikes, 1 H-pitch, total 3
        val pitches = listOf(pitch("S"), pitch("S"), pitch("H"), pitch("BF"))
        val stats = buildBatterStats(pitches)
        assertEquals(1, stats.size)
        assertEquals(0, stats[0].balls)
        assertEquals(2, stats[0].strikes)
        assertEquals(3, stats[0].total)
        assertEquals(2f / 3f, stats[0].strikePercent, 0.001f)
    }

    @Test
    fun buildBatterStats_bfWithNoPrecedingPitches_skipped() {
        // Leading BF (no pitches before it) should not emit a BatterStats entry
        val pitches = listOf(pitch("BF"), pitch("B"), pitch("S"), pitch("BF"))
        val stats = buildBatterStats(pitches)
        assertEquals(1, stats.size)
        assertEquals(2, stats[0].batterNr)   // first BF skipped, second emits batterNr=2
    }

    @Test
    fun buildBatterStats_multipleBatters_correctBatterNrs() {
        val pitches = listOf(
            pitch("B"), pitch("BF"),
            pitch("S"), pitch("BF"),
            pitch("F"), pitch("BF")
        )
        val stats = buildBatterStats(pitches)
        assertEquals(3, stats.size)
        assertEquals(1, stats[0].batterNr)
        assertEquals(2, stats[1].batterNr)
        assertEquals(3, stats[2].batterNr)
    }

    @Test
    fun buildBatterStats_resetsCountersAfterBf() {
        val pitches = listOf(
            pitch("B"), pitch("B"), pitch("BF"),   // batter 1: 2 balls
            pitch("S"), pitch("S"), pitch("S"), pitch("BF")  // batter 2: 3 strikes
        )
        val stats = buildBatterStats(pitches)
        assertEquals(2, stats[0].balls)
        assertEquals(0, stats[0].strikes)
        assertEquals(0, stats[1].balls)
        assertEquals(3, stats[1].strikes)
    }

    // ── rollingAverage ────────────────────────────────────────────────────────

    @Test
    fun rollingAverage_emptyList_returnsEmpty() {
        assertTrue(rollingAverage(emptyList()).isEmpty())
    }

    @Test
    fun rollingAverage_singleElement_returnsSelf() {
        val result = rollingAverage(listOf(BatterStats(1, 0, 3, 0, 3, 1.0f)), window = 3)
        assertEquals(1, result.size)
        assertEquals(1.0f, result[0], 0.001f)
    }

    @Test
    fun rollingAverage_window1_returnsSameValues() {
        val batters = listOf(
            BatterStats(1, 0, 0, 0, 1, 1.0f),
            BatterStats(2, 0, 0, 0, 1, 0.0f),
            BatterStats(3, 0, 0, 0, 1, 0.5f)
        )
        val result = rollingAverage(batters, window = 1)
        assertEquals(3, result.size)
        assertEquals(1.0f, result[0], 0.001f)
        assertEquals(0.0f, result[1], 0.001f)
        assertEquals(0.5f, result[2], 0.001f)
    }

    @Test
    fun rollingAverage_window3_partialWindowAtStart() {
        val batters = listOf(
            BatterStats(1, 0, 0, 0, 1, 0.0f),
            BatterStats(2, 0, 0, 0, 1, 0.5f),
            BatterStats(3, 0, 0, 0, 1, 1.0f),
            BatterStats(4, 0, 0, 0, 1, 0.5f)
        )
        val result = rollingAverage(batters, window = 3)
        assertEquals(4, result.size)
        assertEquals(0.0f,  result[0], 0.001f)   // only index 0
        assertEquals(0.25f, result[1], 0.001f)   // avg(0.0, 0.5)
        assertEquals(0.5f,  result[2], 0.001f)   // avg(0.0, 0.5, 1.0)
        assertEquals(0.667f, result[3], 0.01f)   // avg(0.5, 1.0, 0.5)
    }

    @Test
    fun rollingAverage_windowLargerThanList_averagesAll() {
        val batters = listOf(
            BatterStats(1, 0, 0, 0, 1, 0.4f),
            BatterStats(2, 0, 0, 0, 1, 0.8f)
        )
        val result = rollingAverage(batters, window = 10)
        assertEquals(0.4f, result[0], 0.001f)
        assertEquals(0.6f, result[1], 0.001f)
    }

    // ── getTrendLevel ─────────────────────────────────────────────────────────

    @Test
    fun getTrendLevel_emptyList_returnsGood() {
        assertEquals(TrendLevel.GOOD, getTrendLevel(emptyList()))
    }

    @Test
    fun getTrendLevel_avgAbove60_returnsGood() {
        assertEquals(TrendLevel.GOOD, getTrendLevel(listOf(BatterStats(1, 0, 0, 0, 1, 0.70f))))
    }

    @Test
    fun getTrendLevel_avgExactly60_returnsGood() {
        assertEquals(TrendLevel.GOOD, getTrendLevel(listOf(BatterStats(1, 0, 0, 0, 1, 0.60f))))
    }

    @Test
    fun getTrendLevel_avgBetween40And60_returnsWatch() {
        assertEquals(TrendLevel.WATCH, getTrendLevel(listOf(BatterStats(1, 0, 0, 0, 1, 0.50f))))
    }

    @Test
    fun getTrendLevel_avgExactly40_returnsWatch() {
        assertEquals(TrendLevel.WATCH, getTrendLevel(listOf(BatterStats(1, 0, 0, 0, 1, 0.40f))))
    }

    @Test
    fun getTrendLevel_avgBelow40_returnsChange() {
        assertEquals(TrendLevel.CHANGE, getTrendLevel(listOf(BatterStats(1, 0, 0, 0, 1, 0.30f))))
    }

    @Test
    fun getTrendLevel_usesLastThreeBattersOnly() {
        // First batter is terrible; last three are excellent → should be GOOD
        val batters = listOf(
            BatterStats(1, 0, 0, 0, 1, 0.0f),  // ignored (only takeLast(3) used)
            BatterStats(2, 0, 0, 0, 1, 0.7f),
            BatterStats(3, 0, 0, 0, 1, 0.8f),
            BatterStats(4, 0, 0, 0, 1, 0.9f)
        )
        assertEquals(TrendLevel.GOOD, getTrendLevel(batters))
    }

    @Test
    fun getTrendLevel_singleBatterBelowThreshold_returnsChange() {
        assertEquals(TrendLevel.CHANGE, getTrendLevel(listOf(BatterStats(1, 0, 0, 0, 1, 0.39f))))
    }

    // ── groupPitchesByBatter ──────────────────────────────────────────────────

    @Test
    fun groupPitchesByBatter_emptyList_returnsEmpty() {
        assertTrue(groupPitchesByBatter(emptyList()).isEmpty())
    }

    @Test
    fun groupPitchesByBatter_noBfMarker_returnsSingleOpenGroup() {
        // No BF = open at-bat at end of outing
        val groups = groupPitchesByBatter(listOf(pitch("B"), pitch("S")))
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].pitches.size)
    }

    @Test
    fun groupPitchesByBatter_singleCompletedBatter_correctPitchCount() {
        val groups = groupPitchesByBatter(listOf(pitch("B"), pitch("S"), pitch("BF")))
        assertEquals(1, groups.size)
        assertEquals(1, groups[0].batterNr)
        assertEquals(2, groups[0].pitches.size)
    }

    @Test
    fun groupPitchesByBatter_multipleBatters_allGroupsCreated() {
        val pitches = listOf(
            pitch("B"), pitch("BF"),
            pitch("S"), pitch("S"), pitch("BF"),
            pitch("F"), pitch("BF")
        )
        val groups = groupPitchesByBatter(pitches)
        assertEquals(3, groups.size)
        assertEquals(1, groups[0].pitches.size)
        assertEquals(2, groups[1].pitches.size)
        assertEquals(1, groups[2].pitches.size)
    }

    @Test
    fun groupPitchesByBatter_battingSlotWrapsAt9() {
        // After 9 batters the 10th should wrap back to slot 1
        val pitches = mutableListOf<Pitch>()
        repeat(10) { pitches.add(pitch("B")); pitches.add(pitch("BF")) }
        val groups = groupPitchesByBatter(pitches)
        assertEquals(10, groups.size)
        assertEquals(1, groups[0].battingSlot)
        assertEquals(9, groups[8].battingSlot)
        assertEquals(1, groups[9].battingSlot)
    }

    @Test
    fun groupPitchesByBatter_inningTakenFromFirstPitchInGroup() {
        val pitches = listOf(pitch("B", inning = 3), pitch("S", inning = 3), pitch("BF", inning = 3))
        val groups = groupPitchesByBatter(pitches)
        assertEquals(3, groups[0].inning)
    }

    @Test
    fun groupPitchesByBatter_leadingBfWithNoPitches_skipped() {
        // Leading BF with no preceding pitches should not emit a group for an empty at-bat
        val pitches = listOf(pitch("BF"), pitch("B"), pitch("S"))
        val groups = groupPitchesByBatter(pitches)
        // Only the trailing open group should exist
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].pitches.size)
    }

    @Test
    fun groupPitchesByBatter_openGroupAtEndHasCorrectBatterNr() {
        // 2 completed batters + 1 open
        val pitches = listOf(
            pitch("B"), pitch("BF"),
            pitch("S"), pitch("BF"),
            pitch("F")               // open
        )
        val groups = groupPitchesByBatter(pitches)
        assertEquals(3, groups.size)
        assertEquals(3, groups[2].batterNr)
    }
}
