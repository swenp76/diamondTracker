package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupManagerSecurityTest {

    private lateinit var appDb: AppDatabase
    private lateinit var db: DatabaseHelper
    private lateinit var bm: BackupManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db = DatabaseHelper(appDb)
        bm = BackupManager(context, db)
    }

    @After
    fun tearDown() {
        appDb.close()
    }

    // ── Path Traversal ────────────────────────────────────────────────────────

    @Test
    fun shareJson_sanitizesFileName() {
        val maliciousName = "../../../malicious.json"
        BackupManager.shareJson(context, maliciousName, "{}")

        val expectedSafeFile = File(context.cacheDir, ".._.._.._malicious.json")
        assertTrue("File should be saved with sanitized name", expectedSafeFile.exists())
        
        val illegalFile = File(context.cacheDir.parentFile, "malicious.json")
        assertFalse("File should NOT be saved outside cache directory", illegalFile.exists())
        
        expectedSafeFile.delete()
    }

    // ── String Capping (Full Restore) ─────────────────────────────────────────

    @Test
    fun restoreFromJson_capsTeamNames() {
        val longName = "A".repeat(100)
        val json = JSONObject().apply {
            put("dbVersion", BackupManager.DB_VERSION)
            put("teams", JSONArray().put(JSONObject().apply {
                put("id", 1L)
                put("name", longName)
            }))
        }
        
        bm.restoreFromJson(json)
        
        val team = db.getAllTeams().first()
        assertEquals(50, team.name.length)
        assertTrue(team.name == "A".repeat(50))
    }

    @Test
    fun restoreFromJson_capsPlayerNamesAndNumbers() {
        val longName = "B".repeat(100)
        val longNumber = "123456"
        
        val teamId = db.insertTeam("Team")
        val json = JSONObject().apply {
            put("dbVersion", BackupManager.DB_VERSION)
            put("teams", JSONArray().put(JSONObject().apply { put("id", teamId); put("name", "Team") }))
            put("players", JSONArray().put(JSONObject().apply {
                put("id", 1L)
                put("team_id", teamId)
                put("name", longName)
                put("number", longNumber)
            }))
        }
        
        bm.restoreFromJson(json)
        
        val player = db.getPlayersForTeam(teamId).first()
        assertEquals(50, player.name.length)
        assertEquals(3, player.number.length)
    }

    @Test
    fun restoreFromJson_capsGameData() {
        val longOpponent = "C".repeat(100)
        val longGameNumber = "D".repeat(100)
        
        val teamId = db.insertTeam("Team")
        val json = JSONObject().apply {
            put("dbVersion", BackupManager.DB_VERSION)
            put("teams", JSONArray().put(JSONObject().apply { put("id", teamId); put("name", "Team") }))
            put("games", JSONArray().put(JSONObject().apply {
                put("id", 1L)
                put("team_id", teamId)
                put("date", "2026-04-01-MALICIOUS-EXTRA")
                put("opponent", longOpponent)
                put("game_number", longGameNumber)
            }))
        }
        
        bm.restoreFromJson(json)
        
        val game = db.getGame(1L)!!
        assertEquals(10, game.date.length)
        assertEquals(50, game.opponent.length)
        assertEquals(20, game.gameNumber.length)
    }

    // ── String Capping (Team Import) ──────────────────────────────────────────

    @Test
    fun importTeam_capsNames() {
        val longTeamName = "T".repeat(100)
        val longPlayerName = "P".repeat(100)
        
        val json = JSONObject().apply {
            put("type", "team")
            put("name", longTeamName)
            put("players", JSONArray().put(JSONObject().apply {
                put("name", longPlayerName)
                put("number", "9999")
            }))
        }
        
        bm.importTeam(json)
        
        val team = db.getAllTeams().find { it.name.startsWith("T") }!!
        assertEquals(50, team.name.length)
        
        val player = db.getPlayersForTeam(team.id).first()
        assertEquals(50, player.name.length)
        assertEquals(3, player.number.length)
    }

    // ── String Capping (Game Import) ──────────────────────────────────────────

    @Test
    fun importGame_capsData() {
        val teamId = db.insertTeam("Team")
        val longOpponent = "O".repeat(100)
        
        val json = JSONObject().apply {
            put("game", JSONObject().apply {
                put("date", "2026-01-01")
                put("opponent", longOpponent)
                put("is_home", 1)
            })
            put("pitchers", JSONArray().put(JSONObject().apply {
                put("name", "P".repeat(100))
                put("player", JSONObject().apply {
                    put("name", "PL".repeat(100))
                    put("number", "12345")
                })
            }))
        }
        
        val gameId = bm.importGame(teamId, json)
        val game = db.getGame(gameId)!!
        assertEquals(50, game.opponent.length)
        
        val pitcher = db.getPitchersForGame(gameId).first()
        assertEquals(50, pitcher.name.length)
        
        val player = db.getPlayerById(pitcher.playerId)!!
        assertEquals(50, player.name.length)
        assertEquals(3, player.number.length)
    }
}
