package de.baseball.diamond9

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchingLogicTest {

    // Helper data classes to mock the structure for testing logic
    data class TestPitch(val type: String)

    /**
     * Replicates the logic from DatabaseHelper.getStatsForPitcher to test it without a DB.
     */
    private fun calculateStats(pitches: List<TestPitch>): Triple<Int, Int, Int> {
        var strikes = 0
        var currentStrikesAtBat = 0
        var balls = 0
        var totalPitches = 0

        for (p in pitches) {
            when (p.type) {
                "S" -> {
                    strikes++
                    currentStrikesAtBat++
                    totalPitches++
                }
                "B" -> {
                    balls++
                    totalPitches++
                }
                "F" -> {
                    if (currentStrikesAtBat < 2) {
                        strikes++
                        currentStrikesAtBat++
                    }
                    totalPitches++
                }
                "HBP" -> {
                    totalPitches++
                }
                "BF" -> {
                    currentStrikesAtBat = 0
                }
            }
        }
        return Triple(balls, strikes, totalPitches)
    }

    /**
     * Replicates the logic from PitchTrackActivity.currentAtBatCount.
     */
    private fun calculateCurrentCount(pitches: List<TestPitch>): Pair<Int, Int> {
        val lastBf = pitches.indexOfLast { it.type == "BF" }
        val current = if (lastBf == -1) pitches else pitches.drop(lastBf + 1)
        var balls = 0
        var strikes = 0
        for (p in current) {
            when (p.type) {
                "B" -> balls++
                "S" -> strikes++
                "F" -> if (strikes < 2) strikes++
            }
        }
        return Pair(minOf(balls, 3), minOf(strikes, 2))
    }

    @Test
    fun getStats_foulBallWithZeroStrikes_countsAsStrike() {
        val pitches = listOf(TestPitch("F"))
        val (_, strikes, _) = calculateStats(pitches)
        assertEquals(1, strikes)
    }

    @Test
    fun getStats_foulBallWithOneStrike_countsAsStrike() {
        val pitches = listOf(TestPitch("S"), TestPitch("F"))
        val (_, strikes, _) = calculateStats(pitches)
        assertEquals(2, strikes)
    }

    @Test
    fun getStats_foulBallWithTwoStrikes_doesNotCountAsStrike() {
        val pitches = listOf(TestPitch("S"), TestPitch("S"), TestPitch("F"))
        val (_, strikes, _) = calculateStats(pitches)
        assertEquals(2, strikes)
    }

    @Test
    fun getStats_afterBatterFaced_strikeCountResetsForFouls() {
        val pitches = listOf(
            TestPitch("S"), TestPitch("S"), TestPitch("BF"), // Batter 1: 2 strikes
            TestPitch("F")                                  // Batter 2: foul should count as strike
        )
        val (_, strikes, _) = calculateStats(pitches)
        assertEquals(3, strikes)
    }

    @Test
    fun getStats_totalPitches_includesAllFouls() {
        // 2 strikes, then 3 fouls. Total pitches should be 5. Strikes should be 2.
        val pitches = listOf(TestPitch("S"), TestPitch("S"), TestPitch("F"), TestPitch("F"), TestPitch("F"))
        val (_, strikes, total) = calculateStats(pitches)
        assertEquals(2, strikes)
        assertEquals(5, total)
    }

    @Test
    fun getStats_totalPitches_includesHBP() {
        val pitches = listOf(TestPitch("B"), TestPitch("HBP"))
        val (_, _, total) = calculateStats(pitches)
        assertEquals(2, total)
    }

    @Test
    fun getStats_totalPitches_excludesResultMarkers() {
        // A strikeout sequence: S, S, S, SO, BF
        val pitches = listOf(TestPitch("S"), TestPitch("S"), TestPitch("S"), TestPitch("SO"), TestPitch("BF"))
        val (_, strikes, total) = calculateStats(pitches)
        assertEquals(3, strikes)
        assertEquals(3, total) // SO and BF are results, not pitches
    }

    @Test
    fun getStats_complexSequence_isCorrect() {
        val pitches = listOf(
            TestPitch("B"), TestPitch("S"), TestPitch("F"), TestPitch("F"), TestPitch("S"), TestPitch("SO"), TestPitch("BF"), // Batter 1: 1B, 3S (2 from F), 5 pitches (B, S, F, F, S)
            TestPitch("F"), TestPitch("HBP"), TestPitch("BF"),                                                              // Batter 2: 1S (from F), 2 pitches (1 F, 1 HBP)
            TestPitch("B"), TestPitch("B"), TestPitch("B"), TestPitch("B"), TestPitch("W"), TestPitch("BF")                  // Batter 3: 4B, 4 pitches
        )
        val (balls, strikes, total) = calculateStats(pitches)
        assertEquals(5, balls)    // 1 (B1) + 0 (B2) + 4 (B3)
        assertEquals(4, strikes)  // 3 (B1: S, F, S) + 1 (B2: F)
        assertEquals(11, total)   // 5 (B1) + 2 (B2) + 4 (B3)
    }

    @Test
    fun currentCount_capsAtThreeTwo() {
        val pitches = listOf(
            TestPitch("B"), TestPitch("B"), TestPitch("B"), TestPitch("B"), // 4 balls
            TestPitch("S"), TestPitch("S"), TestPitch("S")                  // 3 strikes
        )
        val count = calculateCurrentCount(pitches)
        assertEquals(3, count.first)
        assertEquals(2, count.second)
    }

    @Test
    fun currentCount_foulDoesNotExceedTwoStrikes() {
        val pitches = listOf(TestPitch("S"), TestPitch("S"), TestPitch("F"))
        val count = calculateCurrentCount(pitches)
        assertEquals(2, count.second)
    }

    @Test
    fun currentCount_resetsAfterBF() {
        val pitches = listOf(TestPitch("S"), TestPitch("S"), TestPitch("BF"), TestPitch("B"))
        val count = calculateCurrentCount(pitches)
        assertEquals(1, count.first)
        assertEquals(0, count.second)
    }
}
