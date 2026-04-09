package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.AppDatabase
import de.baseball.diamond9.db.GameDao
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameTimerTest {

    private lateinit var db: AppDatabase
    private lateinit var gameDao: GameDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = db.gameDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun newGame_startTimeIsZero() {
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        val game = gameDao.getGame(id)
        assertNotNull(game)
        assertEquals(0L, game!!.startTime)
    }

    @Test
    fun notStarted_elapsedIsZero() {
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        val game = gameDao.getGame(id)
        val elapsed = if (game!!.startTime > 0L) System.currentTimeMillis() - game.startTime else 0L
        assertEquals(0L, elapsed)
    }

    // ── start timer ───────────────────────────────────────────────────────────

    @Test
    fun updateStartTime_storesCorrectTimestamp() {
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        val now = System.currentTimeMillis()

        gameDao.updateStartTime(id, now)

        assertEquals(now, gameDao.getGame(id)!!.startTime)
    }

    @Test
    fun updateStartTime_nonZero_indicatesRunningTimer() {
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        gameDao.updateStartTime(id, System.currentTimeMillis())

        val started = gameDao.getGame(id)!!.startTime > 0L
        assertTrue(started)
    }

    // ── elapsed time calculation ───────────────────────────────────────────────

    @Test
    fun elapsed_isPositive_afterStart() {
        val startTime = System.currentTimeMillis() - 3_000L  // 3 seconds ago
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        gameDao.updateStartTime(id, startTime)

        val elapsed = System.currentTimeMillis() - gameDao.getGame(id)!!.startTime
        assertTrue(elapsed >= 3_000L)
    }

    @Test
    fun elapsed_isApproximatelyCorrect() {
        val startTime = System.currentTimeMillis() - 10_000L  // 10 seconds ago
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        gameDao.updateStartTime(id, startTime)

        val elapsed = System.currentTimeMillis() - gameDao.getGame(id)!!.startTime
        assertTrue("elapsed should be >= 10s", elapsed >= 10_000L)
        assertTrue("elapsed should be < 11s", elapsed < 11_000L)
    }

    // ── persistence ───────────────────────────────────────────────────────────

    @Test
    fun startTime_persistsAfterReRead() {
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        val fixed = 1_700_000_000_000L  // fixed past timestamp
        gameDao.updateStartTime(id, fixed)

        // Re-read (simulates DatabaseHelper re-instantiation / app restart)
        val retrieved = gameDao.getGame(id)!!.startTime
        assertEquals(fixed, retrieved)
    }

    @Test
    fun startTime_independentPerGame() {
        val id1 = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        val id2 = gameDao.insertGame(Game(date = "08.04.2026", opponent = "Tigers", teamId = 1L))
        val t1 = 1_700_000_000_000L
        val t2 = 1_700_000_900_000L

        gameDao.updateStartTime(id1, t1)
        gameDao.updateStartTime(id2, t2)

        assertEquals(t1, gameDao.getGame(id1)!!.startTime)
        assertEquals(t2, gameDao.getGame(id2)!!.startTime)
    }

    // ── other game fields unaffected ───────────────────────────────────────────

    @Test
    fun updateStartTime_doesNotChangeOtherFields() {
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = 1L))
        gameDao.updateGameState(id, inning = 3, outs = 2)

        gameDao.updateStartTime(id, System.currentTimeMillis())

        val game = gameDao.getGame(id)!!
        assertEquals(3, game.inning)
        assertEquals(2, game.outs)
        assertEquals("Bears", game.opponent)
    }
}
