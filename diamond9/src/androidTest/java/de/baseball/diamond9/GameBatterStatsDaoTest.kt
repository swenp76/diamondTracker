package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for AtBatDao.getGameBatterStats(gameId) — the per-game batter stats query.
 * Complements SeasonStatsDaoTest which tests the cross-game aggregation variant.
 */
@RunWith(AndroidJUnit4::class)
class GameBatterStatsDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var gameDao: GameDao
    private lateinit var atBatDao: AtBatDao
    private lateinit var teamDao: TeamDao
    private lateinit var playerDao: PlayerDao
    private var teamId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = db.gameDao()
        atBatDao = db.atBatDao()
        teamDao = db.teamDao()
        playerDao = db.playerDao()
        teamId = teamDao.insertTeam(Team(name = "Cardinals"))
    }

    @After
    fun tearDown() { db.close() }

    private fun newGame(date: String = "01.04.2026", opponent: String = "Bears") =
        gameDao.insertGame(Game(date = date, opponent = opponent, teamId = teamId))

    private fun newPlayer(name: String = "Müller", number: String = "7") =
        playerDao.insertPlayer(Player(teamId = teamId, name = name, number = number, primaryPosition = 3))

    private fun ab(gameId: Long, playerId: Long, inning: Int, result: String?) =
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = inning, result = result))

    // ── Basic aggregation ─────────────────────────────────────────────────────

    @Test
    fun getGameBatterStats_emptyGame_returnsEmpty() {
        assertTrue(atBatDao.getGameBatterStats(newGame()).isEmpty())
    }

    @Test
    fun getGameBatterStats_countsAbHitsWalksStrikeoutsHbp() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "H")    // AB + hit
        ab(gId, pId, 2, "K")    // AB + K
        ab(gId, pId, 3, "OUT")  // AB only
        ab(gId, pId, 4, "BB")   // walk, not AB
        ab(gId, pId, 5, "HBP")  // HBP, not AB

        val stats = atBatDao.getGameBatterStats(gId)
        assertEquals(1, stats.size)
        assertEquals(3, stats[0].ab)
        assertEquals(1, stats[0].hits)
        assertEquals(1, stats[0].strikeouts)
        assertEquals(1, stats[0].walks)
        assertEquals(1, stats[0].hbp)
    }

    @Test
    fun getGameBatterStats_bbAndHbpDoNotCountAsAb() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "BB")
        ab(gId, pId, 2, "HBP")

        val stats = atBatDao.getGameBatterStats(gId)
        assertEquals(1, stats.size)
        assertEquals(0, stats[0].ab)
    }

    @Test
    fun getGameBatterStats_nullResultsExcluded() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, null)  // in-progress at-bat
        ab(gId, pId, 2, "H")

        val stats = atBatDao.getGameBatterStats(gId)
        assertEquals(1, stats[0].ab)
        assertEquals(1, stats[0].hits)
    }

    @Test
    fun getGameBatterStats_playerIdZeroExcluded() {
        val gId = newGame()
        ab(gId, 0L, 1, "H")  // unassigned slot

        assertTrue(atBatDao.getGameBatterStats(gId).isEmpty())
    }

    // ── Game isolation ────────────────────────────────────────────────────────

    @Test
    fun getGameBatterStats_isolatedFromOtherGames() {
        val pId = newPlayer()
        val gId1 = newGame(date = "01.04.2026")
        val gId2 = newGame(date = "08.04.2026", opponent = "Tigers")

        repeat(4) { i -> ab(gId1, pId, i + 1, "H") }
        repeat(2) { i -> ab(gId2, pId, i + 1, "K") }

        assertEquals(4, atBatDao.getGameBatterStats(gId1)[0].ab)
        assertEquals(2, atBatDao.getGameBatterStats(gId2)[0].ab)
    }

    @Test
    fun getGameBatterStats_noAbsInGame_returnsEmpty() {
        val pId = newPlayer()
        val gId1 = newGame(date = "01.04.2026")
        val gId2 = newGame(date = "08.04.2026")
        ab(gId1, pId, 1, "H")

        // gId2 has no at-bats
        assertTrue(atBatDao.getGameBatterStats(gId2).isEmpty())
    }

    // ── Multiple players ──────────────────────────────────────────────────────

    @Test
    fun getGameBatterStats_multiplePlayersSeparated() {
        val p1 = newPlayer(name = "Müller", number = "7")
        val p2 = newPlayer(name = "Schmidt", number = "9")
        val gId = newGame()
        ab(gId, p1, 1, "H"); ab(gId, p1, 2, "K")
        ab(gId, p2, 1, "OUT"); ab(gId, p2, 2, "BB")

        val stats = atBatDao.getGameBatterStats(gId)
        assertEquals(2, stats.size)

        val s1 = stats.first { it.playerId == p1 }
        assertEquals(2, s1.ab); assertEquals(1, s1.hits)

        val s2 = stats.first { it.playerId == p2 }
        assertEquals(1, s2.ab); assertEquals(1, s2.walks)
    }

    // ── getOutsForGame ────────────────────────────────────────────────────────

    @Test
    fun getOutsForGame_countsKAndOut() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "K")
        ab(gId, pId, 2, "OUT")
        ab(gId, pId, 3, "H")   // not an out
        ab(gId, pId, 4, "BB")  // not an out

        assertEquals(2, atBatDao.getOutsForGame(gId))
    }

    @Test
    fun getOutsForGame_noOuts_returnsZero() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "H")
        ab(gId, pId, 2, "BB")

        assertEquals(0, atBatDao.getOutsForGame(gId))
    }

    @Test
    fun getOutsForGame_isolatedFromOtherGames() {
        val pId = newPlayer()
        val gId1 = newGame(date = "01.04.2026")
        val gId2 = newGame(date = "08.04.2026")
        ab(gId1, pId, 1, "K"); ab(gId1, pId, 2, "K"); ab(gId1, pId, 3, "K")  // 3 outs
        ab(gId2, pId, 1, "OUT")                                                 // 1 out

        assertEquals(3, atBatDao.getOutsForGame(gId1))
        assertEquals(1, atBatDao.getOutsForGame(gId2))
    }

    @Test
    fun getOutsForGame_countsAllNewOutTypes() {
        // KL, GO, FO, LO, DP are all in the SQL IN-list alongside K and OUT
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "KL")
        ab(gId, pId, 2, "GO")
        ab(gId, pId, 3, "FO")
        ab(gId, pId, 4, "LO")
        ab(gId, pId, 5, "DP")

        assertEquals(5, atBatDao.getOutsForGame(gId))
    }

    @Test
    fun getOutsForGame_sacCountsAsOut() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "SAC")

        assertEquals(1, atBatDao.getOutsForGame(gId))
    }

    // ── New result types — AB / strikeout counting ────────────────────────────

    @Test
    fun getGameBatterStats_sacExcludedFromAb() {
        // SAC is not an at-bat (like BB and HBP), but it is a plate appearance
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "SAC")

        val stats = atBatDao.getGameBatterStats(gId)
        assertEquals(1, stats.size)
        assertEquals(1, stats[0].pa)
        assertEquals(0, stats[0].ab)
    }

    @Test
    fun getGameBatterStats_klCountsAsStrikeout() {
        // KL (strikeout looking) counts as AB and strikeout, same as K
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "KL")

        val stats = atBatDao.getGameBatterStats(gId)
        assertEquals(1, stats.size)
        assertEquals(1, stats[0].ab)
        assertEquals(1, stats[0].strikeouts)
    }

    @Test
    fun getGameBatterStats_goFoLoDpCountAsAb() {
        // Ground/Fly/Line out and double play all count as at-bats
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "GO")
        ab(gId, pId, 2, "FO")
        ab(gId, pId, 3, "LO")
        ab(gId, pId, 4, "DP")

        val stats = atBatDao.getGameBatterStats(gId)
        assertEquals(1, stats.size)
        assertEquals(4, stats[0].ab)
        assertEquals(0, stats[0].strikeouts)
    }

    // ── ROE (Reached on Error) ────────────────────────────────────────────────

    @Test
    fun getGameBatterStats_roeCountsAsAbNotHit() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "ROE")

        val stats = atBatDao.getGameBatterStats(gId)
        assertEquals(1, stats.size)
        assertEquals(1, stats[0].pa)
        assertEquals(1, stats[0].ab)
        assertEquals(0, stats[0].hits)
        assertEquals(0, stats[0].walks)
        assertEquals(0, stats[0].hbp)
    }

    @Test
    fun getGameBatterStats_roeDoesNotCountAsOut() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "ROE")

        assertEquals(0, atBatDao.getOutsForGame(gId))
    }

    @Test
    fun getSeasonStats_roeCountsAsAbNotHit() {
        val pId = newPlayer(); val gId = newGame()
        ab(gId, pId, 1, "ROE")
        ab(gId, pId, 2, "1B")

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(2, stats[0].ab)
        assertEquals(1, stats[0].hits)
    }
}
