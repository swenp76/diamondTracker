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
class PitcherDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var gameDao: GameDao
    private lateinit var pitcherDao: PitcherDao

    private var gameId: Long = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameDao = db.gameDao()
        pitcherDao = db.pitcherDao()
        gameId = gameDao.insertGame(Game(date = "02.04.2026", opponent = "Tigers", teamId = 0))
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Pitcher ───────────────────────────────────────────────────────────────

    @Test
    fun insertPitcher_and_getPitchersForGame_returnsPitcher() {
        pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))

        val pitchers = pitcherDao.getPitchersForGame(gameId)
        assertEquals(1, pitchers.size)
        assertEquals("Müller", pitchers[0].name)
    }

    @Test
    fun getPitchersForGame_returnsInInsertOrder() {
        pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Erster", playerId = 0))
        pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Zweiter", playerId = 0))

        val pitchers = pitcherDao.getPitchersForGame(gameId)
        assertEquals("Erster", pitchers[0].name)
        assertEquals("Zweiter", pitchers[1].name)
    }

    @Test
    fun getPitchersForGame_emptyWhenNoPitchers() {
        assertTrue(pitcherDao.getPitchersForGame(gameId).isEmpty())
    }

    @Test
    fun deletePitcher_removesPitcherAndItsPitches() {
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S", sequenceNr = 2))

        pitcherDao.deletePitcher(pitcherId)

        assertTrue(pitcherDao.getPitchersForGame(gameId).isEmpty())
        assertTrue(pitcherDao.getPitchesForPitcher(pitcherId).isEmpty())
    }

    @Test
    fun deletePitcher_doesNotAffectOtherPitchers() {
        val p1 = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Erster", playerId = 0))
        val p2 = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Zweiter", playerId = 0))

        pitcherDao.deletePitcher(p1)

        val remaining = pitcherDao.getPitchersForGame(gameId)
        assertEquals(1, remaining.size)
        assertEquals(p2, remaining[0].id)
    }

    // ── Pitches ───────────────────────────────────────────────────────────────

    @Test
    fun insertPitch_and_getPitchesForPitcher_returnsPitch() {
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S", sequenceNr = 1))

        val pitches = pitcherDao.getPitchesForPitcher(pitcherId)
        assertEquals(1, pitches.size)
        assertEquals("S", pitches[0].type)
    }

    @Test
    fun getPitchesForPitcher_returnsInSequenceOrder() {
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B", sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S", sequenceNr = 1))

        val pitches = pitcherDao.getPitchesForPitcher(pitcherId)
        assertEquals("S", pitches[0].type)
        assertEquals("B", pitches[1].type)
    }

    @Test
    fun undoLastPitch_removesOnlyLastPitch() {
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S", sequenceNr = 2))

        pitcherDao.undoLastPitch(pitcherId)

        val pitches = pitcherDao.getPitchesForPitcher(pitcherId)
        assertEquals(1, pitches.size)
        assertEquals("B", pitches[0].type)
    }

    @Test
    fun undoLastPitch_onEmptyList_doesNothing() {
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        pitcherDao.undoLastPitch(pitcherId)
        assertTrue(pitcherDao.getPitchesForPitcher(pitcherId).isEmpty())
    }

    @Test
    fun getNextSequenceNr_returnsOneWhenNoPitches() {
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        assertEquals(1, pitcherDao.getNextSequenceNr(pitcherId))
    }

    @Test
    fun getNextSequenceNr_incrementsCorrectly() {
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S", sequenceNr = 2))

        assertEquals(3, pitcherDao.getNextSequenceNr(pitcherId))
    }

    @Test
    fun getTotalBFForGame_countsOnlyBFPitches() {
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Müller", playerId = 0))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "BF", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "BF", sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 3))

        assertEquals(2, pitcherDao.getTotalBFForGame(gameId))
    }
}
