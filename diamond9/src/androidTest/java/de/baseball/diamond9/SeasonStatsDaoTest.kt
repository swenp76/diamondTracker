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

@RunWith(AndroidJUnit4::class)
class SeasonStatsDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var gameDao: GameDao
    private lateinit var atBatDao: AtBatDao
    private lateinit var pitcherDao: PitcherDao
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
        pitcherDao = db.pitcherDao()
        teamDao = db.teamDao()
        playerDao = db.playerDao()
        teamId = teamDao.insertTeam(Team(name = "Cardinals"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Batter Stats ──────────────────────────────────────────────────────────

    @Test
    fun batterStats_aggregatesHitsAndAtBats() {
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Müller", number = "7", primaryPosition = 3))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 1, result = "H"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 2, result = "K"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 3, result = "OUT"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(3, stats[0].ab)
        assertEquals(1, stats[0].hits)
        assertEquals(1, stats[0].strikeouts)
    }

    @Test
    fun batterStats_bbAndHbp_doNotCountAsAtBat() {
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Müller", number = "7", primaryPosition = 3))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 1, result = "BB"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 2, result = "HBP"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 3, result = "H"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats[0].ab)      // only H counts as AB
        assertEquals(1, stats[0].walks)
        assertEquals(1, stats[0].hbp)
        assertEquals(1, stats[0].hits)
    }

    @Test
    fun batterStats_aggregatesAcrossMultipleGames() {
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Schmidt", number = "9", primaryPosition = 7))
        val game1 = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val game2 = gameDao.insertGame(Game(date = "08.04.2026", opponent = "Tigers", teamId = teamId))

        atBatDao.insertAtBat(AtBat(gameId = game1, playerId = playerId, slot = 1, inning = 1, result = "H"))
        atBatDao.insertAtBat(AtBat(gameId = game1, playerId = playerId, slot = 1, inning = 2, result = "K"))
        atBatDao.insertAtBat(AtBat(gameId = game2, playerId = playerId, slot = 1, inning = 1, result = "H"))
        atBatDao.insertAtBat(AtBat(gameId = game2, playerId = playerId, slot = 1, inning = 2, result = "OUT"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(4, stats[0].ab)
        assertEquals(2, stats[0].hits)
        assertEquals(1, stats[0].strikeouts)
    }

    @Test
    fun batterStats_excludesPlayerIdZero() {
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = 0L, slot = 1, inning = 1, result = "H"))

        assertTrue(atBatDao.getSeasonBatterStats(teamId).isEmpty())
    }

    @Test
    fun batterStats_excludesNullResults() {
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "4", primaryPosition = 4))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        // at-bat still in progress (no result yet)
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 1, result = null))

        assertTrue(atBatDao.getSeasonBatterStats(teamId).isEmpty())
    }

    @Test
    fun batterStats_excludesOtherTeams() {
        val team2 = teamDao.insertTeam(Team(name = "Cubs"))
        val player1 = playerDao.insertPlayer(Player(teamId = teamId, name = "Müller", number = "7", primaryPosition = 3))
        val player2 = playerDao.insertPlayer(Player(teamId = team2, name = "Fischer", number = "11", primaryPosition = 6))

        val game1 = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val game2 = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Lions", teamId = team2))
        atBatDao.insertAtBat(AtBat(gameId = game1, playerId = player1, slot = 1, inning = 1, result = "H"))
        atBatDao.insertAtBat(AtBat(gameId = game2, playerId = player2, slot = 1, inning = 1, result = "K"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(player1, stats[0].playerId)
    }

    @Test
    fun batterStats_multiplePlayersGroupedSeparately() {
        val player1 = playerDao.insertPlayer(Player(teamId = teamId, name = "Müller", number = "7", primaryPosition = 3))
        val player2 = playerDao.insertPlayer(Player(teamId = teamId, name = "Schmidt", number = "9", primaryPosition = 7))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))

        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = player1, slot = 1, inning = 1, result = "H"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = player2, slot = 2, inning = 1, result = "K"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = player2, slot = 2, inning = 2, result = "K"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(2, stats.size)
        val p1stats = stats.first { it.playerId == player1 }
        val p2stats = stats.first { it.playerId == player2 }
        assertEquals(1, p1stats.hits)
        assertEquals(2, p2stats.strikeouts)
    }

    // ── Pitcher Stats ─────────────────────────────────────────────────────────

    @Test
    fun pitcherStats_countsBasicPitches() {
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Weber", playerId = playerId))

        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S", sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "F", sequenceNr = 3))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "BF", sequenceNr = 4)) // not counted in total_pitches

        val stats = pitcherDao.getSeasonPitcherStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(3, stats[0].totalPitches)  // B + S + F
        assertEquals(1, stats[0].bf)
        assertEquals(1, stats[0].balls)
        assertEquals(1, stats[0].strikes)
        assertEquals(1, stats[0].fouls)
    }

    @Test
    fun pitcherStats_aggregatesAcrossMultipleGames() {
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val game1 = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val game2 = gameDao.insertGame(Game(date = "08.04.2026", opponent = "Tigers", teamId = teamId))

        val p1 = pitcherDao.insertPitcher(Pitcher(gameId = game1, name = "Weber", playerId = playerId))
        pitcherDao.insertPitch(Pitch(pitcherId = p1, type = "B", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = p1, type = "S", sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = p1, type = "BF", sequenceNr = 3))

        val p2 = pitcherDao.insertPitcher(Pitcher(gameId = game2, name = "Weber", playerId = playerId))
        pitcherDao.insertPitch(Pitch(pitcherId = p2, type = "S", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = p2, type = "S", sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = p2, type = "BF", sequenceNr = 3))

        val stats = pitcherDao.getSeasonPitcherStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(4, stats[0].totalPitches)  // B+S (game1) + S+S (game2)
        assertEquals(2, stats[0].bf)
        assertEquals(1, stats[0].balls)
        assertEquals(3, stats[0].strikes)
    }

    @Test
    fun pitcherStats_excludesPlayerIdZero() {
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Anonym", playerId = 0L))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B", sequenceNr = 1))

        assertTrue(pitcherDao.getSeasonPitcherStats(teamId).isEmpty())
    }

    @Test
    fun pitcherStats_excludesOtherTeams() {
        val team2 = teamDao.insertTeam(Team(name = "Cubs"))
        val p1 = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val p2 = playerDao.insertPlayer(Player(teamId = team2, name = "Lange", number = "10", primaryPosition = 1, isPitcher = true))

        val game1 = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val game2 = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Lions", teamId = team2))

        val pitcher1 = pitcherDao.insertPitcher(Pitcher(gameId = game1, name = "Weber", playerId = p1))
        val pitcher2 = pitcherDao.insertPitcher(Pitcher(gameId = game2, name = "Lange", playerId = p2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcher1, type = "S", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcher2, type = "B", sequenceNr = 1))

        val stats = pitcherDao.getSeasonPitcherStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(p1, stats[0].playerId)
        assertEquals(1, stats[0].strikes)
    }

    @Test
    fun pitcherStats_multiplePitchersGroupedSeparately() {
        val p1 = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val p2 = playerDao.insertPlayer(Player(teamId = teamId, name = "Braun", number = "2", primaryPosition = 1, isPitcher = true))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))

        val pitcher1 = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Weber", playerId = p1))
        val pitcher2 = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Braun", playerId = p2))

        pitcherDao.insertPitch(Pitch(pitcherId = pitcher1, type = "S", sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcher1, type = "S", sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcher2, type = "B", sequenceNr = 1))

        val stats = pitcherDao.getSeasonPitcherStats(teamId)
        assertEquals(2, stats.size)
        val s1 = stats.first { it.playerId == p1 }
        val s2 = stats.first { it.playerId == p2 }
        assertEquals(2, s1.strikes)
        assertEquals(1, s2.balls)
    }

    // ── PA / AB / hbp field coverage ─────────────────────────────────────────

    @Test
    fun batterStats_paIncludesBBAndHBP() {
        // PA = COUNT(*); BB and HBP count as PA but not AB
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Müller", number = "7", primaryPosition = 3))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 1, result = "H"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 2, result = "BB"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 3, result = "HBP"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(3, stats[0].pa)   // all three count as PA
        assertEquals(1, stats[0].ab)   // only H counts as AB
    }

    @Test
    fun batterStats_sacExcludedFromAb() {
        // SAC is a PA but not an AB (same rule as BB and HBP)
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "4", primaryPosition = 4))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 1, result = "SAC"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(1, stats[0].pa)
        assertEquals(0, stats[0].ab)
    }

    @Test
    fun batterStats_klCountsAsStrikeout() {
        // KL (strikeout looking) increments strikeouts just like K
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Schmidt", number = "9", primaryPosition = 7))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 1, result = "KL"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(1, stats[0].ab)
        assertEquals(1, stats[0].strikeouts)
    }

    @Test
    fun batterStats_hbpFieldCorrect() {
        // hbp field is used to compute OBP; verify it is counted separately
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Braun", number = "2", primaryPosition = 5))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 1, result = "HBP"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 2, result = "HBP"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = 1, inning = 3, result = "H"))

        val stats = atBatDao.getSeasonBatterStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(3, stats[0].pa)
        assertEquals(2, stats[0].hbp)
        assertEquals(1, stats[0].hits)
    }

    // ── empty cases ───────────────────────────────────────────────────────────

    @Test
    fun batterStats_emptyWhenNoGames() {
        assertTrue(atBatDao.getSeasonBatterStats(teamId).isEmpty())
    }

    @Test
    fun pitcherStats_emptyWhenNoGames() {
        assertTrue(pitcherDao.getSeasonPitcherStats(teamId).isEmpty())
    }
}
