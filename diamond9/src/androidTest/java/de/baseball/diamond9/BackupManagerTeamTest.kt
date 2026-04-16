package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.AppDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for:
 *  - [BackupManager.exportTeam]         — JSON structure of a team export
 *  - [BackupManager.importTeam]         — DB state after team import
 *  - export → import roundtrip          — data fidelity
 *  - [BackupManager.importGame]         — substitute-slot range fix (1..20)
 *  - [DatabaseHelper.getActiveTeamId]   — returns null / first team id
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerTeamTest {

    private lateinit var appDb: AppDatabase
    private lateinit var db: DatabaseHelper
    private lateinit var bm: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        appDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db = DatabaseHelper(appDb)
        bm = BackupManager(ApplicationProvider.getApplicationContext(), db)
    }

    @After
    fun tearDown() {
        appDb.close()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun insertTeam(name: String = "Cardinals"): Long {
        val id = db.insertTeam(name)
        db.setPositionEnabled(id, 1, true)
        db.setPositionEnabled(id, 2, true)
        return id
    }

    private fun insertPlayer(teamId: Long, name: String, number: String = "7") =
        db.insertPlayer(teamId, name, number, primaryPosition = 3)

    // ── exportTeam ───────────────────────────────────────────────────────────

    @Test
    fun exportTeam_hasTypeField() {
        val id = insertTeam()
        val json = JSONObject(bm.exportTeam(id))
        assertEquals("team", json.getString("type"))
    }

    @Test
    fun exportTeam_hasCorrectName() {
        val id = insertTeam("Red Sox")
        val json = JSONObject(bm.exportTeam(id))
        assertEquals("Red Sox", json.getString("name"))
    }

    @Test
    fun exportTeam_containsPlayers() {
        val id = insertTeam()
        insertPlayer(id, "Müller", "7")
        insertPlayer(id, "Schmidt", "11")

        val json = JSONObject(bm.exportTeam(id))
        val players = json.getJSONArray("players")

        assertEquals(2, players.length())
        val names = (0 until players.length()).map { players.getJSONObject(it).getString("name") }
        assertTrue("Müller" in names)
        assertTrue("Schmidt" in names)
    }

    @Test
    fun exportTeam_containsPositions() {
        val id = insertTeam()  // insertTeam enables positions 1 and 2
        val json = JSONObject(bm.exportTeam(id))
        val positions = json.getJSONArray("positions")

        val posSet = (0 until positions.length()).map { positions.getInt(it) }.toSet()
        assertTrue(1 in posSet)
        assertTrue(2 in posSet)
    }

    @Test
    fun exportTeam_noPlayers_hasEmptyPlayersArray() {
        val id = insertTeam()
        val json = JSONObject(bm.exportTeam(id))
        assertEquals(0, json.getJSONArray("players").length())
    }

    @Test
    fun exportTeam_playerFields_areComplete() {
        val id = insertTeam()
        db.insertPlayer(id, "Braun", "99", primaryPosition = 1,
            secondaryPosition = 2, isPitcher = true, birthYear = 2005)

        val json = JSONObject(bm.exportTeam(id))
        val p = json.getJSONArray("players").getJSONObject(0)

        assertEquals("Braun", p.getString("name"))
        assertEquals("99", p.getString("number"))
        assertEquals(1, p.getInt("primary_position"))
        assertEquals(2, p.getInt("secondary_position"))
        assertTrue(p.getBoolean("is_pitcher"))
        assertEquals(2005, p.getInt("birth_year"))
    }

    // ── importTeam ───────────────────────────────────────────────────────────

    @Test
    fun importTeam_createsNewTeam() {
        val json = JSONObject().apply {
            put("type", "team")
            put("version", 1)
            put("name", "Yankees")
            put("positions", org.json.JSONArray())
            put("players", org.json.JSONArray())
        }
        bm.importTeam(json)

        val teams = db.getAllTeams()
        assertTrue(teams.any { it.name == "Yankees" })
    }

    @Test
    fun importTeam_importsPlayers() {
        val playerJson = JSONObject().apply {
            put("name", "Rivera"); put("number", "42")
            put("primary_position", 1); put("secondary_position", 0)
            put("is_pitcher", true); put("birth_year", 1969)
        }
        val json = JSONObject().apply {
            put("type", "team"); put("version", 1); put("name", "Yankees")
            put("positions", org.json.JSONArray())
            put("players", org.json.JSONArray().put(playerJson))
        }
        bm.importTeam(json)

        val teamId = db.getAllTeams().first { it.name == "Yankees" }.id
        val players = db.getPlayersForTeam(teamId)
        assertEquals(1, players.size)
        assertEquals("Rivera", players[0].name)
        assertEquals("42", players[0].number)
        assertTrue(players[0].isPitcher)
    }

    @Test
    fun importTeam_setsPositions() {
        val posArray = org.json.JSONArray().apply { put(1); put(3); put(5) }
        val json = JSONObject().apply {
            put("type", "team"); put("version", 1); put("name", "Mets")
            put("positions", posArray)
            put("players", org.json.JSONArray())
        }
        bm.importTeam(json)

        val teamId = db.getAllTeams().first { it.name == "Mets" }.id
        val positions = db.getEnabledPositions(teamId)
        assertTrue(1 in positions)
        assertTrue(3 in positions)
        assertTrue(5 in positions)
        assertFalse(2 in positions)
    }

    // ── export → import roundtrip ─────────────────────────────────────────────

    @Test
    fun exportImport_roundtrip_preservesPlayerData() {
        val srcId = insertTeam("Braves")
        insertPlayer(srcId, "Jones", "10")
        insertPlayer(srcId, "Smoltz", "29")

        val json = JSONObject(bm.exportTeam(srcId))
        json.remove("type")  // simulate old export without type field
        json.put("name", "Braves Copy")  // unique name for the imported team
        bm.importTeam(json)

        val dstId = db.getAllTeams().first { it.name == "Braves Copy" }.id
        val players = db.getPlayersForTeam(dstId).sortedBy { it.name }
        assertEquals(2, players.size)
        assertEquals("Jones", players[0].name)
        assertEquals("Smoltz", players[1].name)
    }

    // ── importGame substitute slots ───────────────────────────────────────────

    @Test
    fun importGame_lineupSlot11_doesNotThrow() {
        val teamId = insertTeam("Cubs")
        val json = minimalGameJson().apply {
            put("own_lineup", org.json.JSONArray().put(
                JSONObject().apply {
                    put("slot", 11)
                    put("player", JSONObject().apply { put("name", "SubPlayer"); put("number", "99") })
                }
            ))
        }
        // Must not throw — slots 11-20 are valid substitute slots
        val gameId = bm.importGame(teamId, json)
        assertTrue(gameId > 0)
    }

    @Test
    fun importGame_lineupSlot20_doesNotThrow() {
        val teamId = insertTeam("Cubs2")
        val json = minimalGameJson().apply {
            put("own_lineup", org.json.JSONArray().put(
                JSONObject().apply {
                    put("slot", 20)
                    put("player", JSONObject().apply { put("name", "SubPlayer"); put("number", "88") })
                }
            ))
        }
        val gameId = bm.importGame(teamId, json)
        assertTrue(gameId > 0)
    }

    @Test
    fun importGame_lineupSlot21_throws() {
        val teamId = insertTeam("Cubs3")
        val json = minimalGameJson().apply {
            put("own_lineup", org.json.JSONArray().put(
                JSONObject().apply {
                    put("slot", 21)
                    put("player", JSONObject().apply { put("name", "Invalid"); put("number", "0") })
                }
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bm.importGame(teamId, json)
        }
    }

    @Test
    fun importGame_atBatSlot15_doesNotThrow() {
        val teamId = insertTeam("Cubs4")
        val json = minimalGameJson().apply {
            put("at_bats", org.json.JSONArray().put(
                JSONObject().apply {
                    put("slot", 15)
                    put("inning", 1)
                    put("player", JSONObject().apply { put("name", "SubBatter"); put("number", "55") })
                    put("result", JSONObject.NULL)
                    put("pitches", org.json.JSONArray())
                }
            ))
        }
        val gameId = bm.importGame(teamId, json)
        assertTrue(gameId > 0)
    }

    // ── getActiveTeamId ───────────────────────────────────────────────────────

    @Test
    fun getActiveTeamId_noTeams_returnsNull() {
        assertNull(db.getActiveTeamId())
    }

    @Test
    fun getActiveTeamId_withOneTeam_returnsItsId() {
        val id = db.insertTeam("Pirates")
        assertEquals(id, db.getActiveTeamId())
    }

    @Test
    fun getActiveTeamId_withMultipleTeams_returnsFirstId() {
        val id1 = db.insertTeam("Astros")
        db.insertTeam("Padres")
        // getActiveTeamId returns the first from getAllTeams()
        assertEquals(id1, db.getActiveTeamId())
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun minimalGameJson() = JSONObject().apply {
        put("game", JSONObject().apply {
            put("date", "01.04.2026")
            put("opponent", "Bears")
            put("inning", 1)
            put("outs", 0)
            put("leadoff_slot", 1)
            put("start_time", 0L)
            put("elapsed_time_ms", 0L)
            put("game_time", "")
            put("is_home", 1)
        })
        put("scoreboard", org.json.JSONArray())
        put("own_lineup", org.json.JSONArray())
        put("substitutions", org.json.JSONArray())
        put("at_bats", org.json.JSONArray())
        put("pitchers", org.json.JSONArray())
        put("opponent_lineup", org.json.JSONArray())
        put("opponent_bench", org.json.JSONArray())
        put("opponent_substitutions", org.json.JSONArray())
    }
}
