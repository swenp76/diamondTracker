package de.baseball.diamond9

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [JsonDispatchActivity.inferType] — the heuristic fallback that
 * detects the file type from JSON structure when the "type" key is absent.
 */
@RunWith(AndroidJUnit4::class)
class JsonDispatchInferTypeTest {

    // ── single_game ──────────────────────────────────────────────────────────

    @Test
    fun inferType_withGameKey_returnsSingleGame() {
        val json = JSONObject().apply { put("game", JSONObject()) }
        assertEquals("single_game", JsonDispatchActivity.inferType(json))
    }

    @Test
    fun inferType_gameKeyTakesPrecedenceOverNameAndPlayers() {
        // A JSON that has both "game" and "name"+"players" should still be single_game
        val json = JSONObject().apply {
            put("game", JSONObject())
            put("name", "Cardinals")
            put("players", JSONArray())
        }
        assertEquals("single_game", JsonDispatchActivity.inferType(json))
    }

    // ── team ─────────────────────────────────────────────────────────────────

    @Test
    fun inferType_withNameAndPlayersKey_returnsTeam() {
        val json = JSONObject().apply {
            put("name", "Cardinals")
            put("players", JSONArray())
        }
        assertEquals("team", JsonDispatchActivity.inferType(json))
    }

    @Test
    fun inferType_nameWithoutPlayers_doesNotReturnTeam() {
        val json = JSONObject().apply { put("name", "Cardinals") }
        // Only "name" without "players" is not enough to identify as team
        assertEquals("", JsonDispatchActivity.inferType(json))
    }

    @Test
    fun inferType_playersWithoutName_doesNotReturnTeam() {
        val json = JSONObject().apply { put("players", JSONArray()) }
        assertEquals("", JsonDispatchActivity.inferType(json))
    }

    // ── league_settings ──────────────────────────────────────────────────────

    @Test
    fun inferType_withInningsKey_returnsLeagueSettings() {
        val json = JSONObject().apply { put("innings", 9) }
        assertEquals("league_settings", JsonDispatchActivity.inferType(json))
    }

    @Test
    fun inferType_inningsWithTimeLimit_returnsLeagueSettings() {
        val json = JSONObject().apply {
            put("innings", 7)
            put("time_limit_minutes", 120)
        }
        assertEquals("league_settings", JsonDispatchActivity.inferType(json))
    }

    // ── unknown ──────────────────────────────────────────────────────────────

    @Test
    fun inferType_emptyJson_returnsEmpty() {
        assertEquals("", JsonDispatchActivity.inferType(JSONObject()))
    }

    @Test
    fun inferType_unrelatedKeys_returnsEmpty() {
        val json = JSONObject().apply {
            put("foo", "bar")
            put("number", 42)
        }
        assertEquals("", JsonDispatchActivity.inferType(json))
    }
}
