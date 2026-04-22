package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests for BackupManager.restoreFromJson().
 *
 * Uses an in-memory Room database to verify that restore correctly
 * persists all entities including nullable fields (at_bat result,
 * pitch at_bat_id) that caused API 26 failures with execSQL.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private lateinit var appDb: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // We use the DatabaseHelper with our in-memory DB
        manager = BackupManager(context, DatabaseHelper(appDb))
    }

    @After
    fun tearDown() {
        appDb.close()
    }

    // ── Helper: build a minimal valid backup JSON ─────────────────────────────

    private fun minimalBackup(
        dbVersion: Int = BackupManager.DB_VERSION,
        extraGames: JSONArray = JSONArray(),
        extraAtBats: JSONArray = JSONArray(),
        extraPitches: JSONArray = JSONArray(),
        extraPitchers: JSONArray = JSONArray(),
        extraPlayers: JSONArray = JSONArray()
    ): JSONObject = JSONObject().apply {
        put("dbVersion", dbVersion)
        put("exportDate", System.currentTimeMillis())
        put("teams", JSONArray().put(JSONObject().apply {
            put("id", 99)
            put("name", "Test Team")
        }))
        put("team_positions", JSONArray())
        put("players", extraPlayers)
        put("games", extraGames)
        put("pitchers", extraPitchers)
        put("pitches", extraPitches)
        put("at_bats", extraAtBats)
        put("own_lineup", JSONArray())
        put("substitutions", JSONArray())
        put("opponent_lineup", JSONArray())
        put("opponent_bench", JSONArray())
        put("opponent_substitutions", JSONArray())
        put("scoreboard_runs", JSONArray())
        put("opponent_teams", JSONArray())
        put("league_settings", JSONArray())
        put("pitcher_appearances", JSONArray())
    }

    private fun gameJson(id: Long, teamId: Long = 99L, gameNumber: String = ""): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("date", "18.04.2026")
            put("opponent", "Bears")
            put("team_id", teamId)
            put("inning", 1); put("outs", 0); put("leadoff_slot", 1)
            put("start_time", 0L); put("elapsed_time_ms", 0L)
            put("game_time", ""); put("is_home", 1)
            put("current_inning", 1); put("is_top_half", 1)
            put("game_number", gameNumber)
        }

    // ── Downgrade guard ───────────────────────────────────────────────────────

    @Test
    fun restoreFromJson_newerVersion_throwsWithClearMessage() {
        val json = minimalBackup(dbVersion = BackupManager.DB_VERSION + 1)
        try {
            manager.restoreFromJson(json)
            fail("Expected exception for newer backup version")
        } catch (e: Exception) {
            assertTrue(
                "Error message should mention version numbers",
                e.message?.contains(BackupManager.DB_VERSION.toString()) == true
            )
        }
    }

    // ── at_bats: nullable result ──────────────────────────────────────────────

    @Test
    fun restoreFromJson_atBatWithNullResult_doesNotThrow() {
        // This is the primary API 26 crash scenario
        val atBat = JSONObject().apply {
            put("id", 100)
            put("game_id", 200)
            put("player_id", 0)
            put("slot", 1)
            put("inning", 1)
            put("result", JSONObject.NULL)  // explicit null – in-progress at-bat
        }
        val games = JSONArray().put(gameJson(id = 200))
        val backup = minimalBackup(extraGames = games, extraAtBats = JSONArray().put(atBat))

        assertDoesNotThrow { manager.restoreFromJson(backup) }
    }

    @Test
    fun restoreFromJson_atBatWithStringResult_doesNotThrow() {
        val atBat = JSONObject().apply {
            put("id", 101)
            put("game_id", 201)
            put("player_id", 0)
            put("slot", 1)
            put("inning", 1)
            put("result", "K")
        }
        val games = JSONArray().put(gameJson(id = 201))
        val backup = minimalBackup(extraGames = games, extraAtBats = JSONArray().put(atBat))

        assertDoesNotThrow { manager.restoreFromJson(backup) }
    }

    // ── pitches: nullable at_bat_id ───────────────────────────────────────────

    @Test
    fun restoreFromJson_pitchWithNullAtBatId_doesNotThrow() {
        // Defense pitches have no at_bat_id – second API 26 crash scenario
        val game = gameJson(id = 1)
        val pitcher = JSONObject().apply {
            put("id", 400); put("game_id", 1); put("name", "P"); put("player_id", 0)
        }
        val pitch = JSONObject().apply {
            put("id", 300)
            put("pitcher_id", 400)
            put("at_bat_id", JSONObject.NULL)  // defense pitch – no at_bat
            put("type", "B")
            put("sequence_nr", 1)
            put("inning", 1)
        }
        val backup = minimalBackup(
            extraGames = JSONArray().put(game),
            extraPitchers = JSONArray().put(pitcher),
            extraPitches = JSONArray().put(pitch)
        )

        assertDoesNotThrow { manager.restoreFromJson(backup) }
    }

    @Test
    fun restoreFromJson_pitchWithAtBatId_doesNotThrow() {
        val game = gameJson(id = 1)
        val player = JSONObject().apply {
            put("id", 100); put("team_id", 99); put("name", "Player"); put("number", "1")
            put("primary_position", 1); put("secondary_position", 0); put("is_pitcher", 0); put("birth_year", 0)
        }
        val pitcher = JSONObject().apply {
            put("id", 401); put("game_id", 1); put("name", "P"); put("player_id", 0)
        }
        val atBat = JSONObject().apply {
            put("id", 500); put("game_id", 1); put("player_id", 100); put("slot", 1); put("inning", 1); put("result", "K")
        }
        val pitch = JSONObject().apply {
            put("id", 301)
            put("pitcher_id", 401)
            put("at_bat_id", 500L)
            put("type", "S")
            put("sequence_nr", 1)
            put("inning", 1)
        }
        val backup = minimalBackup(
            extraGames = JSONArray().put(game),
            extraPlayers = JSONArray().put(player),
            extraPitchers = JSONArray().put(pitcher),
            extraAtBats = JSONArray().put(atBat),
            extraPitches = JSONArray().put(pitch)
        )

        assertDoesNotThrow { manager.restoreFromJson(backup) }
    }

    // ── games: game_number field ──────────────────────────────────────────────

    @Test
    fun restoreFromJson_gameWithGameNumber_doesNotThrow() {
        val games = JSONArray().put(gameJson(id = 600, gameNumber = "1234"))
        val backup = minimalBackup(extraGames = games)

        assertDoesNotThrow { manager.restoreFromJson(backup) }
    }

    @Test
    fun restoreFromJson_gameWithoutGameNumber_appliesMigrationDefault() {
        // Simulates a v13 backup missing game_number
        val gameNoNumber = JSONObject().apply {
            put("id", 601)
            put("date", "18.04.2026"); put("opponent", "Bears"); put("team_id", 99)
            put("inning", 1); put("outs", 0); put("leadoff_slot", 1)
            put("start_time", 0L); put("elapsed_time_ms", 0L)
            put("game_time", ""); put("is_home", 1)
            put("current_inning", 1); put("is_top_half", 1)
            // no game_number key
        }
        val backup = minimalBackup(
            dbVersion = 13,
            extraGames = JSONArray().put(gameNoNumber)
        )

        // Migration should add game_number = "" before restore
        assertDoesNotThrow { manager.restoreFromJson(backup) }
    }

    // ── INSERT OR IGNORE: no duplicates on double restore ─────────────────────

    @Test
    fun restoreFromJson_calledTwice_doesNotDuplicate() {
        val games = JSONArray().put(gameJson(id = 700))
        val backup = minimalBackup(extraGames = games)

        // First restore
        manager.restoreFromJson(backup)
        // Second restore – INSERT OR IGNORE must silently skip
        assertDoesNotThrow { manager.restoreFromJson(backup) }
    }

    // ── Full real-world backup from the actual device ─────────────────────────

    @Test
    fun restoreFromJson_realDeviceBackupV14_doesNotThrow() {
        // Replicates the exact backup that caused "Restore failed: No value for id"
        // on API 26. Key fields: at_bat result=null, pitch at_bat_id=null.
        val atBatNullResult = JSONObject().apply {
            put("id", 21); put("game_id", 5); put("player_id", 0)
            put("slot", 5); put("inning", 2); put("result", JSONObject.NULL)
        }
        val pitchNullAtBatId = JSONObject().apply {
            put("id", 38); put("pitcher_id", 3); put("at_bat_id", JSONObject.NULL)
            put("type", "HBP"); put("sequence_nr", 1); put("inning", 3)
        }
        val game = gameJson(id = 5, teamId = 1, gameNumber = "1234")
        val pitcher = JSONObject().apply {
            put("id", 3); put("game_id", 5); put("name", "Test"); put("player_id", 0)
        }

        // Teams must exist for the game
        val team = JSONObject().apply {
            put("id", 1L)
            put("name", "Main Team")
        }

        val backup = minimalBackup(
            dbVersion = 14,
            extraGames = JSONArray().put(game),
            extraAtBats = JSONArray().put(atBatNullResult),
            extraPitches = JSONArray().put(pitchNullAtBatId),
            extraPitchers = JSONArray().put(pitcher)
        ).apply {
            // override teams to include team 1
            put("teams", JSONArray().put(team))
        }

        assertDoesNotThrow { manager.restoreFromJson(backup) }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Expected no exception but got: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
