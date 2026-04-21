package de.baseball.diamond9

data class BatterStats(
    val batterNr: Int,
    val balls: Int,
    val strikes: Int,
    val fouls: Int,
    val total: Int,
    val strikePercent: Float
)

fun buildBatterStats(pitches: List<Pitch>): List<BatterStats> {
    val batters = mutableListOf<BatterStats>()
    var balls = 0
    var strikes = 0
    var fouls = 0
    var other = 0  // HBP, H: real thrown pitches that end the at-bat
    var batterNr = 1

    // Result markers that end an at-bat
    val resultTypes = setOf("BF", "W", "HBP", "H", "1B", "2B", "3B", "HR", "SO", "GO", "FO", "LO")

    pitches.forEach { pitch ->
        when (pitch.type) {
            "B"   -> balls++
            "S"   -> strikes++
            "SO"  -> strikes++   // strikeout pitch counts as a strike
            "F"   -> fouls++
            "HBP" -> other++     // hit by pitch counts in total, not strikes
            "H", "1B", "2B", "3B", "HR" -> other++  // ball-in-play counts in total, not strikes
            "GO", "FO", "LO" -> other++  // ground out / fly out / line out counts in total
        }

        if (pitch.type in resultTypes) {
            val total = balls + strikes + fouls + other
            if (total > 0) {
                batters.add(
                    BatterStats(
                        batterNr = batterNr,
                        balls = balls,
                        strikes = strikes,
                        fouls = fouls,
                        total = total,
                        strikePercent = (strikes + fouls).toFloat() / total
                    )
                )
            }
            batterNr++
            balls = 0; strikes = 0; fouls = 0; other = 0
        }
    }
    return batters
}

fun rollingAverage(batters: List<BatterStats>, window: Int = 3): List<Float> {
    return batters.indices.map { i ->
        val from = maxOf(0, i - window + 1)
        val slice = batters.subList(from, i + 1)
        slice.map { it.strikePercent }.average().toFloat()
    }
}

enum class TrendLevel { GOOD, WATCH, CHANGE }

fun getTrendLevel(batters: List<BatterStats>): TrendLevel {
    if (batters.isEmpty()) return TrendLevel.GOOD
    val recent = batters.takeLast(3)
    val avg = recent.map { it.strikePercent }.average()
    return when {
        avg >= 0.60 -> TrendLevel.GOOD
        avg >= 0.40 -> TrendLevel.WATCH
        else -> TrendLevel.CHANGE
    }
}

// ── Batter-Gruppen für PitchGrid ──────────────────────────────────────────────

data class BatterGroup(
    val batterNr: Int,
    val battingSlot: Int,
    val jerseyNumber: String,
    val pitches: List<Pitch>,
    val inning: Int = 1
)

fun groupPitchesByBatter(pitches: List<Pitch>): List<BatterGroup> {
    val groups = mutableListOf<BatterGroup>()
    var current = mutableListOf<Pitch>()
    var batterNr = 1

    // Result markers that end an at-bat
    val resultTypes = setOf("BF", "W", "HBP", "H", "1B", "2B", "3B", "HR", "SO", "GO", "FO", "LO")

    pitches.forEach { pitch ->
        if (pitch.type != "RO") {
            current.add(pitch)
        }
        
        if (pitch.type in resultTypes) {
            // Filter out 'BF' (and potentially other marker-only pitches) from being counted 
            // as real pitches within the group's pitch list.
            val actualPitches = current.filter { it.type != "BF" }

            if (actualPitches.isNotEmpty()) {
                val groupInning = actualPitches.firstOrNull()?.inning ?: current.firstOrNull()?.inning ?: 1
                groups.add(
                    BatterGroup(
                        batterNr = batterNr,
                        battingSlot = ((batterNr - 1) % 9) + 1,
                        jerseyNumber = "",
                        pitches = actualPitches,
                        inning = groupInning
                    )
                )
                current = mutableListOf()
            }
            batterNr++
        }
    }
    // Letzter offener At-Bat
    if (current.isNotEmpty()) {
        val actualPitches = current.filter { it.type != "BF" }
        if (actualPitches.isNotEmpty()) {
            val groupInning = actualPitches.firstOrNull()?.inning ?: current.firstOrNull()?.inning ?: 1
            groups.add(
                BatterGroup(
                    batterNr = batterNr,
                    battingSlot = ((batterNr - 1) % 9) + 1,
                    jerseyNumber = "",
                    pitches = actualPitches,
                    inning = groupInning
                )
            )
        }
    }
    return groups
}
