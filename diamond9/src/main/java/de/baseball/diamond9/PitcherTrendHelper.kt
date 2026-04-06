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
    var batterNr = 1

    pitches.forEach { pitch ->
        when (pitch.type) {
            "B" -> balls++
            "S" -> strikes++
            "F" -> fouls++
            "BF" -> {
                val total = balls + strikes + fouls
                if (total > 0) {
                    batters.add(
                        BatterStats(
                            batterNr = batterNr++,
                            balls = balls,
                            strikes = strikes,
                            fouls = fouls,
                            total = total,
                            strikePercent = (strikes + fouls).toFloat() / total
                        )
                    )
                }
                balls = 0; strikes = 0; fouls = 0
            }
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
