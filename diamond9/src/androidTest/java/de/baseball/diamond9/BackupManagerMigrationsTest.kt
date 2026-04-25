package de.baseball.diamond9

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for BackupManager.applyBackupMigrations (called via reflection since it's private)
 * and the DB_VERSION constant.
 *
 * These tests protect against regressions when new DB migrations are added:
 * the migration logic must correctly backfill default values for older backup formats.
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerMigrationsTest {

    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        manager = BackupManager(ApplicationProvider.getApplicationContext())
    }

    /** Calls the private applyBackupMigrations via reflection. */
    private fun migrate(json: JSONObject, fromVersion: Int, toVersion: Int = BackupManager.DB_VERSION): JSONObject {
        val method = BackupManager::class.java.getDeclaredMethod(
            "applyBackupMigrations",
            JSONObject::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(manager, json, fromVersion, toVersion) as JSONObject
    }

    // ── DB_VERSION sanity ─────────────────────────────────────────────────────

    @Test
    fun dbVersion_is19() {
        // Bumping AppDatabase.version without updating DB_VERSION breaks backup compatibility.
        assertEquals(19, BackupManager.DB_VERSION)
    }

    // ── Migration 18 → 19 : pitcher_id and at_bat_id nullable ─────────────────

    @Test
    fun migration_v18_nullfillsZeroIds() {
        val pitch = JSONObject().apply {
            put("id", 1)
            put("pitcher_id", 0L)
            put("at_bat_id", 0L)
        }
        val json = JSONObject().apply {
            put("dbVersion", 18)
            put("pitches", JSONArray().put(pitch))
        }
        val result = migrate(json, fromVersion = 18)
        val p = result.getJSONArray("pitches").getJSONObject(0)
        assertTrue(p.isNull("pitcher_id"))
        assertTrue(p.isNull("at_bat_id"))
    }

    // ── Migration 5 → 6 : scoreboard_runs array added ─────────────────────────

    @Test
    fun migration_v5_addsEmptyScoreboardRunsArray() {
        val json = JSONObject().apply {
            put("dbVersion", 5)
            put("games", JSONArray())
            put("opponent_teams", JSONArray())
        }
        val result = migrate(json, fromVersion = 5)
        assertTrue("scoreboard_runs should be added", result.has("scoreboard_runs"))
        assertEquals(0, result.getJSONArray("scoreboard_runs").length())
    }

    @Test
    fun migration_v6_doesNotOverwriteExistingScoreboardRuns() {
        val existingRun = JSONObject().apply {
            put("game_id", 1); put("inning", 1); put("is_home", 1); put("runs", 3)
        }
        val json = JSONObject().apply {
            put("dbVersion", 6)
            put("games", JSONArray())
            put("opponent_teams", JSONArray())
            put("scoreboard_runs", JSONArray().put(existingRun))
        }
        val result = migrate(json, fromVersion = 6)
        assertEquals(1, result.getJSONArray("scoreboard_runs").length())
    }

    // ── Migration 6 → 7 : start_time added to games ───────────────────────────

    @Test
    fun migration_v6_addsStartTimeToGames() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
        }
        val json = JSONObject().apply {
            put("dbVersion", 6)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray())
        }
        val result = migrate(json, fromVersion = 6)
        val g = result.getJSONArray("games").getJSONObject(0)
        assertEquals(0L, g.getLong("start_time"))
    }

    @Test
    fun migration_v7_doesNotOverwriteExistingStartTime() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
            put("start_time", 1234567890L)
        }
        val json = JSONObject().apply {
            put("dbVersion", 7)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray())
        }
        val result = migrate(json, fromVersion = 7)
        val g = result.getJSONArray("games").getJSONObject(0)
        assertEquals(1234567890L, g.getLong("start_time"))
    }

    // ── Migration 7 → 8 : team_id added to opponent_teams ────────────────────

    @Test
    fun migration_v7_addsTeamIdToOpponentTeams() {
        val opp = JSONObject().apply { put("id", 1); put("name", "Bears") }
        val json = JSONObject().apply {
            put("dbVersion", 7)
            put("games", JSONArray())
            put("opponent_teams", JSONArray().put(opp))
        }
        val result = migrate(json, fromVersion = 7)
        val o = result.getJSONArray("opponent_teams").getJSONObject(0)
        assertEquals(0L, o.getLong("team_id"))
    }

    @Test
    fun migration_v8_doesNotOverwriteExistingTeamId() {
        val opp = JSONObject().apply {
            put("id", 1); put("name", "Bears"); put("team_id", 42L)
        }
        val json = JSONObject().apply {
            put("dbVersion", 8)
            put("games", JSONArray())
            put("opponent_teams", JSONArray().put(opp))
        }
        val result = migrate(json, fromVersion = 8)
        assertEquals(42L, result.getJSONArray("opponent_teams").getJSONObject(0).getLong("team_id"))
    }

    // ── Migration 8 → 9 : game_time added to games ────────────────────────────

    @Test
    fun migration_v8_addsGameTimeToGames() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
            put("start_time", 0L)
        }
        val json = JSONObject().apply {
            put("dbVersion", 8)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray())
        }
        val result = migrate(json, fromVersion = 8)
        val g = result.getJSONArray("games").getJSONObject(0)
        assertTrue(g.has("game_time"))
        assertEquals("", g.getString("game_time"))
    }

    // ── Migration 9 → 10 : is_home added to games ─────────────────────────────

    @Test
    fun migration_v9_addsIsHomeToGames() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
            put("start_time", 0L); put("game_time", "")
        }
        val json = JSONObject().apply {
            put("dbVersion", 9)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray())
        }
        val result = migrate(json, fromVersion = 9)
        val g = result.getJSONArray("games").getJSONObject(0)
        assertEquals(1, g.getInt("is_home"))
    }

    // ── Migration 10 → 11 : elapsed_time_ms added to games ───────────────────

    @Test
    fun migration_v10_addsElapsedTimeMsToGames() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
            put("start_time", 0L); put("game_time", ""); put("is_home", 1)
        }
        val json = JSONObject().apply {
            put("dbVersion", 10)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray())
        }
        val result = migrate(json, fromVersion = 10)
        val g = result.getJSONArray("games").getJSONObject(0)
        assertEquals(0L, g.getLong("elapsed_time_ms"))
    }

    // ── Migration 13 → 14 : game_number added to games ───────────────────────

    @Test
    fun migration_v13_addsGameNumberToGames() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
            put("start_time", 0L); put("game_time", ""); put("is_home", 1)
            put("elapsed_time_ms", 0L); put("current_inning", 1); put("is_top_half", 1)
        }
        val json = JSONObject().apply {
            put("dbVersion", 13)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray())
            put("league_settings", JSONArray())
        }
        val result = migrate(json, fromVersion = 13)
        val g = result.getJSONArray("games").getJSONObject(0)
        assertTrue(g.has("game_number"))
        assertEquals("", g.getString("game_number"))
    }

    @Test
    fun migration_v13_doesNotOverwriteExistingGameNumber() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
            put("start_time", 0L); put("game_time", ""); put("is_home", 1)
            put("elapsed_time_ms", 0L); put("current_inning", 1); put("is_top_half", 1)
            put("game_number", "1234")
        }
        val json = JSONObject().apply {
            put("dbVersion", 14)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray())
            put("league_settings", JSONArray())
        }
        val result = migrate(json, fromVersion = 14)
        assertEquals("1234", result.getJSONArray("games").getJSONObject(0).getString("game_number"))
    }

    // ── Migration 14 → 15 : pitcher name null backfill ────────────────────────

    @Test
    fun migration_v14_backfillsNullPitcherName() {
        val pitcher = JSONObject().apply {
            put("id", 1)
            put("game_id", 1)
            put("name", JSONObject.NULL)
            put("player_id", 0)
        }
        val json = JSONObject().apply {
            put("dbVersion", 14)
            put("games", JSONArray())
            put("pitchers", JSONArray().put(pitcher))
            put("opponent_teams", JSONArray())
            put("league_settings", JSONArray())
        }
        val result = migrate(json, fromVersion = 14)
        val p = result.getJSONArray("pitchers").getJSONObject(0)
        assertEquals("", p.getString("name"))
    }

    @Test
    fun migration_v14_doesNotOverwriteExistingPitcherName() {
        val pitcher = JSONObject().apply {
            put("id", 1)
            put("game_id", 1)
            put("name", "Müller")
            put("player_id", 0)
        }
        val json = JSONObject().apply {
            put("dbVersion", 14)
            put("games", JSONArray())
            put("pitchers", JSONArray().put(pitcher))
            put("opponent_teams", JSONArray())
            put("league_settings", JSONArray())
        }
        val result = migrate(json, fromVersion = 14)
        assertEquals("Müller", result.getJSONArray("pitchers").getJSONObject(0).getString("name"))
    }

    // ── Migration 15 → 16 : pitch type null backfill ─────────────────────────

    @Test
    fun migration_v15_backfillsNullPitchType() {
        val pitch = JSONObject().apply {
            put("id", 1)
            put("pitcher_id", 1)
            put("type", JSONObject.NULL)
        }
        val json = JSONObject().apply {
            put("dbVersion", 15)
            put("games", JSONArray())
            put("pitches", JSONArray().put(pitch))
            put("opponent_teams", JSONArray())
        }
        val result = migrate(json, fromVersion = 15)
        val p = result.getJSONArray("pitches").getJSONObject(0)
        assertEquals("", p.getString("type"))
    }

    // ── Migration 16 → 17 : pitch at_bat_id null backfill ───────────────────

    @Test
    fun migration_v16_backfillsNullAtBatId() {
        val pitch = JSONObject().apply {
            put("id", 1)
            put("pitcher_id", 1)
            put("at_bat_id", JSONObject.NULL)
        }
        val json = JSONObject().apply {
            put("dbVersion", 16)
            put("games", JSONArray())
            put("pitches", JSONArray().put(pitch))
            put("opponent_teams", JSONArray())
        }
        val result = migrate(json, fromVersion = 16)
        val p = result.getJSONArray("pitches").getJSONObject(0)
        // Migration 16 -> 17 fills null with 0L
        // Then Migration 18 -> 19 fills 0L with NULL
        assertTrue(p.isNull("at_bat_id"))
    }

    // ── Full chain: v5 → latest ───────────────────────────────────────────────

    @Test
    fun migration_v5ToLatest_appliesAllDefaults() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
        }
        val opp = JSONObject().apply { put("id", 1); put("name", "Bears") }
        val pitch = JSONObject().apply {
            put("id", 1); put("pitcher_id", 1); put("type", JSONObject.NULL); put("at_bat_id", JSONObject.NULL)
        }
        val json = JSONObject().apply {
            put("dbVersion", 5)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray().put(opp))
            put("pitches", JSONArray().put(pitch))
        }

        val result = migrate(json, fromVersion = 5)
        val g = result.getJSONArray("games").getJSONObject(0)
        val o = result.getJSONArray("opponent_teams").getJSONObject(0)
        val p = result.getJSONArray("pitches").getJSONObject(0)

        // All game fields should have their defaults
        assertEquals(0L,  g.getLong("start_time"))
        assertEquals("",  g.getString("game_time"))
        assertEquals(1,   g.getInt("is_home"))
        assertEquals(0L,  g.getLong("elapsed_time_ms"))
        assertEquals("",  g.getString("game_number"))

        // Pitch defaults
        assertEquals("", p.getString("type"))
        assertTrue(p.isNull("at_bat_id"))

        // scoreboard_runs array should be present
        assertTrue(result.has("scoreboard_runs"))

        // Opponent team should have team_id = 0
        assertEquals(0L, o.getLong("team_id"))
    }

    @Test
    fun migration_alreadyAtLatest_isIdempotent() {
        val game = JSONObject().apply {
            put("id", 1); put("date", "01.04.2026"); put("opponent", "Bears")
            put("start_time", 111L); put("game_time", "14:00")
            put("is_home", 0); put("elapsed_time_ms", 9000L)
        }
        val json = JSONObject().apply {
            put("dbVersion", BackupManager.DB_VERSION)
            put("games", JSONArray().put(game))
            put("opponent_teams", JSONArray())
            put("scoreboard_runs", JSONArray())
        }

        val result = migrate(json, fromVersion = BackupManager.DB_VERSION)
        val g = result.getJSONArray("games").getJSONObject(0)

        // Values must not be overwritten
        assertEquals(111L,   g.getLong("start_time"))
        assertEquals("14:00", g.getString("game_time"))
        assertEquals(0,       g.getInt("is_home"))
        assertEquals(9000L,   g.getLong("elapsed_time_ms"))
    }

    @Test
    fun validPitchTypes_containsAllUsedTypes() {
        val required = listOf(
            "B", "S", "F", "BF", "RO",
            "SO", "H", "1B", "2B", "3B", "HR",
            "HBP", "W", "GO", "FO", "LO", "KL"
        )
        required.forEach { type ->
            assertTrue(
                "VALID_PITCH_TYPES missing: $type",
                BackupManager.VALID_PITCH_TYPES.contains(type)
            )
        }
    }
}
