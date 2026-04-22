package de.baseball.diamond9

import kotlin.math.min

/**
 * Utility to match players and identify duplicates or potential re-mappings
 * using fuzzy logic (Levenshtein distance) and jersey numbers.
 */
object PlayerMatcher {

    /**
     * Calculates the Levenshtein distance between two strings.
     * Lower distance means higher similarity.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    /**
     * Checks if two player names are similar based on a normalized threshold.
     */
    fun isSimilar(name1: String, name2: String, threshold: Int = 2): Boolean {
        val n1 = name1.trim().lowercase()
        val n2 = name2.trim().lowercase()
        if (n1 == n2) return true
        if (n1.isEmpty() || n2.isEmpty()) return false
        
        val distance = levenshteinDistance(n1, n2)
        return distance <= threshold
    }

    /**
     * Searches for a potential player match in a list of players.
     * Priority:
     * 1. Exact Name & Number
     * 2. Similar Name & Exact Number
     * 3. Exact Name & Empty/Different Number
     */
    fun findPotentialMatch(
        targetName: String,
        targetNumber: String,
        players: List<Player>
    ): Player? {
        // 1. Exact match
        players.find { it.name == targetName && it.number == targetNumber }?.let { return it }

        // 2. Similar name + exact number
        if (targetNumber.isNotEmpty()) {
            players.filter { it.number == targetNumber }
                .find { isSimilar(it.name, targetName) }
                ?.let { return it }
        }

        // 3. Exact name, ignore number if it's a weak match
        players.find { it.name == targetName }?.let { return it }

        return null
    }
}
