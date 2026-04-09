package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.AppDatabase
import de.baseball.diamond9.db.GameDao
import de.baseball.diamond9.db.ScoreboardDao
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScoreboardDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var gameDao: GameDao
    private lateinit var scoreboardDao: ScoreboardDao

    private var gameId = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = db.gameDao()
        scoreboardDao = db.scoreboardDao()
        gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── insert & retrieve ──────────────────────────────────────────────────────

    @Test
    fun insert_and_getScoreboard_returnsRun() {
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 0, runs = 3))

        val result = scoreboardDao.getScoreboard(gameId)
        assertEquals(1, result.size)
        assertEquals(3, result[0].runs)
        assertEquals(1, result[0].inning)
        assertEquals(0, result[0].isHome)
    }

    @Test
    fun getScoreboard_emptyWhenNoRuns() {
        assertTrue(scoreboardDao.getScoreboard(gameId).isEmpty())
    }

    @Test
    fun getScoreboard_orderedByInningThenIsHome() {
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 2, isHome = 0, runs = 1))
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 1, runs = 2))
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 0, runs = 3))

        val result = scoreboardDao.getScoreboard(gameId)
        assertEquals(3, result.size)
        assertEquals(1, result[0].inning); assertEquals(0, result[0].isHome)  // inning 1, away
        assertEquals(1, result[1].inning); assertEquals(1, result[1].isHome)  // inning 1, home
        assertEquals(2, result[2].inning)                                      // inning 2
    }

    // ── getRun ─────────────────────────────────────────────────────────────────

    @Test
    fun getRun_returnsNull_whenNotFound() {
        assertNull(scoreboardDao.getRun(gameId, 1, 0))
    }

    @Test
    fun getRun_returnsCorrectEntry() {
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 3, isHome = 1, runs = 5))

        val result = scoreboardDao.getRun(gameId, 3, 1)
        assertNotNull(result)
        assertEquals(5, result!!.runs)
    }

    @Test
    fun getRun_distinguishesAwayFromHome() {
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 0, runs = 2))
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 1, runs = 4))

        assertEquals(2, scoreboardDao.getRun(gameId, 1, 0)!!.runs)
        assertEquals(4, scoreboardDao.getRun(gameId, 1, 1)!!.runs)
    }

    // ── update ─────────────────────────────────────────────────────────────────

    @Test
    fun update_changesRunsValue() {
        val id = scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 0, runs = 2))
        scoreboardDao.update(ScoreboardRun(id = id, gameId = gameId, inning = 1, isHome = 0, runs = 7))

        assertEquals(7, scoreboardDao.getRun(gameId, 1, 0)!!.runs)
    }

    // ── deleteForGame ──────────────────────────────────────────────────────────

    @Test
    fun deleteForGame_removesAllRunsForGame() {
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 0, runs = 1))
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 2, isHome = 0, runs = 2))
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 1, runs = 3))

        scoreboardDao.deleteForGame(gameId)

        assertTrue(scoreboardDao.getScoreboard(gameId).isEmpty())
    }

    @Test
    fun deleteForGame_doesNotAffectOtherGames() {
        val game2 = gameDao.insertGame(Game(date = "02.04.2026", opponent = "Tigers", teamId = 1L))
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 0, runs = 1))
        scoreboardDao.insert(ScoreboardRun(gameId = game2, inning = 1, isHome = 0, runs = 5))

        scoreboardDao.deleteForGame(gameId)

        assertTrue(scoreboardDao.getScoreboard(gameId).isEmpty())
        assertEquals(1, scoreboardDao.getScoreboard(game2).size)
        assertEquals(5, scoreboardDao.getScoreboard(game2)[0].runs)
    }

    // ── isolation between games ────────────────────────────────────────────────

    @Test
    fun getScoreboard_onlyReturnsRunsForSpecificGame() {
        val game2 = gameDao.insertGame(Game(date = "02.04.2026", opponent = "Tigers", teamId = 1L))
        scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = 1, isHome = 0, runs = 3))
        scoreboardDao.insert(ScoreboardRun(gameId = game2, inning = 1, isHome = 0, runs = 9))

        val result = scoreboardDao.getScoreboard(gameId)
        assertEquals(1, result.size)
        assertEquals(3, result[0].runs)
    }

    // ── total run calculation ──────────────────────────────────────────────────

    @Test
    fun totalRuns_summedCorrectly() {
        for (inning in 1..9) {
            scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = inning, isHome = 0, runs = 1))
        }

        val total = scoreboardDao.getScoreboard(gameId).filter { it.isHome == 0 }.sumOf { it.runs }
        assertEquals(9, total)
    }
}
