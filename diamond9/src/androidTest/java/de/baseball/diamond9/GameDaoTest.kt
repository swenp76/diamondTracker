package de.baseball.diamond9

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.baseball.diamond9.db.AppDatabase
import de.baseball.diamond9.db.GameDao
import de.baseball.diamond9.db.PitcherDao
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var gameDao: GameDao
    private lateinit var pitcherDao: PitcherDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = db.gameDao()
        pitcherDao = db.pitcherDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertGame_and_getAllGames_returnsInsertedGame() {
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Tigers", teamId = 0))

        val games = gameDao.getAllGames()
        assertEquals(1, games.size)
        assertEquals("Tigers", games[0].opponent)
        assertEquals("01.04.2026", games[0].date)
        assertEquals(id, games[0].id)
    }

    @Test
    fun getAllGames_returnsNewestFirst() {
        gameDao.insertGame(Game(date = "01.04.2026", opponent = "Tigers", teamId = 0))
        gameDao.insertGame(Game(date = "02.04.2026", opponent = "Bears", teamId = 0))

        val games = gameDao.getAllGames()
        assertEquals("Bears", games[0].opponent)
        assertEquals("Tigers", games[1].opponent)
    }

    @Test
    fun getGame_returnsNull_whenNotFound() {
        val result = gameDao.getGame(999L)
        assertNull(result)
    }

    @Test
    fun updateGame_changesDateAndOpponent() {
        val id = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Tigers", teamId = 0))

        gameDao.updateGame(id, "15.04.2026", "Lions", "18:00", 1)

        val updated = gameDao.getGame(id)
        assertNotNull(updated)
        assertEquals("15.04.2026", updated!!.date)
        assertEquals("Lions", updated.opponent)
    }

    @Test
    fun deleteGameWithCascade_removesPitchersAndPitches() {
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Tigers", teamId = 0))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S", sequenceNr = 2))

        gameDao.deleteGameWithCascade(gameId)

        assertTrue(gameDao.getAllGames().isEmpty())
        assertTrue(pitcherDao.getPitchersForGame(gameId).isEmpty())
        assertTrue(pitcherDao.getPitchesForPitcher(pitcherId).isEmpty())
    }

    @Test
    fun deleteGameWithCascade_doesNotAffectOtherGames() {
        val game1 = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Tigers", teamId = 0))
        val game2 = gameDao.insertGame(Game(date = "02.04.2026", opponent = "Bears", teamId = 0))
        pitcherDao.insertPitcher(Pitcher(gameId = game1, name = "Müller", playerId = 0))

        gameDao.deleteGameWithCascade(game1)

        val remaining = gameDao.getAllGames()
        assertEquals(1, remaining.size)
        assertEquals(game2, remaining[0].id)
    }
}
