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
    fun pitcherStats_includesPlayerIdZero() {
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Anonym", playerId = 0L))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B", sequenceNr = 1))

        val stats = pitcherDao.getSeasonPitcherStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(0L, stats[0].playerId)
        assertEquals("Anonym", stats[0].playerName)
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

    // ── Walk / SO / KL result-marker tests ───────────────────────────────────

    @Test
    fun pitcherStats_walkNotCountedAsTotalPitch() {
        // Walk sequence: B B B B W BF — W is a result marker, not an extra pitch
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Weber", playerId = playerId))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 3))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 4))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "W",  sequenceNr = 5))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "BF", sequenceNr = 6))

        val stats = pitcherDao.getSeasonPitcherStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(4, stats[0].totalPitches) // only 4 balls, W is not a pitch
        assertEquals(1, stats[0].walks)
        assertEquals(4, stats[0].balls)
        assertEquals(1, stats[0].bf)
    }

    @Test
    fun pitcherStats_strikeoutSoNotCountedAsTotalPitchOrStrike() {
        // Strikeout sequence: B S S SO BF — SO is a result marker, not an extra pitch/strike
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Weber", playerId = playerId))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S",  sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S",  sequenceNr = 3))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "SO", sequenceNr = 4))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "BF", sequenceNr = 5))

        val stats = pitcherDao.getSeasonPitcherStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(3, stats[0].totalPitches) // B + S + S, SO is a result marker
        assertEquals(2, stats[0].strikes)      // two S pitches, SO not an extra strike
        assertEquals(1, stats[0].ks)
        assertEquals(1, stats[0].bf)
    }

    @Test
    fun pitcherStats_strikeoutKlNotCountedAsTotalPitchOrStrike() {
        // KL (strikeout looking): B S KL BF — same rule as SO
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Weber", playerId = playerId))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S",  sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "KL", sequenceNr = 3))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "BF", sequenceNr = 4))

        val stats = pitcherDao.getSeasonPitcherStats(teamId)
        assertEquals(1, stats.size)
        assertEquals(2, stats[0].totalPitches) // B + S, KL is a result marker
        assertEquals(1, stats[0].strikes)      // one S pitch, KL not an extra strike
        assertEquals(1, stats[0].ks)
    }

    // ── Consistency: season stats match single-game stats ─────────────────────

    @Test
    fun pitcherStats_walkScenario_seasonMatchesSingleGame() {
        val helper = DatabaseHelper(db)
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Weber", playerId = playerId))
        // Walk: B B B B W BF
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 3))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 4))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "W",  sequenceNr = 5))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "BF", sequenceNr = 6))

        val single = helper.getStatsForPitcher(pitcherId)!!
        val season = pitcherDao.getSeasonPitcherStats(teamId).first()

        assertEquals("totalPitches", single.totalPitches, season.totalPitches)
        assertEquals("balls",        single.balls,        season.balls)
        assertEquals("walks",        single.walks,        season.walks)
        assertEquals("bf",           single.bf,           season.bf)
    }

    @Test
    fun pitcherStats_strikeoutScenario_seasonMatchesSingleGame() {
        val helper = DatabaseHelper(db)
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber", number = "1", primaryPosition = 1, isPitcher = true))
        val gameId = gameDao.insertGame(Game(date = "01.04.2026", opponent = "Bears", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Weber", playerId = playerId))
        // Strikeout: B S S SO BF
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "B",  sequenceNr = 1))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S",  sequenceNr = 2))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "S",  sequenceNr = 3))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "SO", sequenceNr = 4))
        pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = "BF", sequenceNr = 5))

        val single = helper.getStatsForPitcher(pitcherId)!!
        val season = pitcherDao.getSeasonPitcherStats(teamId).first()

        assertEquals("totalPitches", single.totalPitches, season.totalPitches)
        assertEquals("strikes",      single.strikes,      season.strikes + season.fouls)
        assertEquals("ks",           single.strikeouts,   season.ks)
        assertEquals("bf",           single.bf,           season.bf)
    }

    // ── Consistency: season batter stats match game-level batter stats ───────────

    @Test
    fun batterStats_singleGame_seasonMatchesGameStats() {
        // Verifies that getSeasonBatterStats filtered to game date == getGameBatterStats
        val pId = playerDao.insertPlayer(Player(teamId = teamId, name = "Müller", number = "7", primaryPosition = 3))
        val gameId = gameDao.insertGame(Game(date = "03.05.2026", opponent = "Nightmares", teamId = teamId))

        // Representative mix: H, K, BB, HBP, 2B (with RBI), SAC, GO, KL
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = pId, slot = 1, inning = 1, result = "H",   rbi = 1))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = pId, slot = 1, inning = 2, result = "K"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = pId, slot = 1, inning = 3, result = "BB"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = pId, slot = 1, inning = 4, result = "2B",  rbi = 2))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = pId, slot = 1, inning = 5, result = "SAC"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = pId, slot = 1, inning = 6, result = "HBP"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = pId, slot = 1, inning = 7, result = "GO"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = pId, slot = 1, inning = 8, result = "KL"))

        val g = atBatDao.getGameBatterStats(gameId).first { it.playerId == pId }
        val s = atBatDao.getSeasonBatterStats(teamId, "20260503", "20260503").first { it.playerId == pId }

        assertEquals("pa",         g.pa,         s.pa)
        assertEquals("ab",         g.ab,         s.ab)
        assertEquals("hits",       g.hits,       s.hits)
        assertEquals("doubles",    g.doubles,    s.doubles)
        assertEquals("triples",    g.triples,    s.triples)
        assertEquals("homers",     g.homers,     s.homers)
        assertEquals("rbi",        g.rbi,        s.rbi)
        assertEquals("walks",      g.walks,      s.walks)
        assertEquals("strikeouts", g.strikeouts, s.strikeouts)
        assertEquals("hbp",        g.hbp,        s.hbp)
    }

    @Test
    fun batterStats_twoGamesOnSameDate_seasonSumsCorrectly() {
        // Season stats filtered to a date must equal sum of both game stats
        val pId = playerDao.insertPlayer(Player(teamId = teamId, name = "Schmidt", number = "9", primaryPosition = 4))
        val g1 = gameDao.insertGame(Game(date = "03.05.2026", opponent = "Nightmares", teamId = teamId))
        val g2 = gameDao.insertGame(Game(date = "03.05.2026", opponent = "Eagles",     teamId = teamId))

        // Game 1: H (1 RBI), BB, K
        atBatDao.insertAtBat(AtBat(gameId = g1, playerId = pId, slot = 1, inning = 1, result = "H",  rbi = 1))
        atBatDao.insertAtBat(AtBat(gameId = g1, playerId = pId, slot = 1, inning = 2, result = "BB"))
        atBatDao.insertAtBat(AtBat(gameId = g1, playerId = pId, slot = 1, inning = 3, result = "K"))

        // Game 2: 2B (2 RBI), HBP, GO, HR (3 RBI)
        atBatDao.insertAtBat(AtBat(gameId = g2, playerId = pId, slot = 1, inning = 1, result = "2B", rbi = 2))
        atBatDao.insertAtBat(AtBat(gameId = g2, playerId = pId, slot = 1, inning = 2, result = "HBP"))
        atBatDao.insertAtBat(AtBat(gameId = g2, playerId = pId, slot = 1, inning = 3, result = "GO"))
        atBatDao.insertAtBat(AtBat(gameId = g2, playerId = pId, slot = 1, inning = 4, result = "HR", rbi = 3))

        val s1 = atBatDao.getGameBatterStats(g1).first()
        val s2 = atBatDao.getGameBatterStats(g2).first()
        val sea = atBatDao.getSeasonBatterStats(teamId, "20260503", "20260503").first()

        assertEquals("pa",         s1.pa         + s2.pa,         sea.pa)
        assertEquals("ab",         s1.ab         + s2.ab,         sea.ab)
        assertEquals("hits",       s1.hits       + s2.hits,       sea.hits)
        assertEquals("doubles",    s1.doubles    + s2.doubles,    sea.doubles)
        assertEquals("homers",     s1.homers     + s2.homers,     sea.homers)
        assertEquals("rbi",        s1.rbi        + s2.rbi,        sea.rbi)
        assertEquals("walks",      s1.walks      + s2.walks,      sea.walks)
        assertEquals("strikeouts", s1.strikeouts + s2.strikeouts, sea.strikeouts)
        assertEquals("hbp",        s1.hbp        + s2.hbp,        sea.hbp)
    }

    @Test
    fun batterStats_twoGamesOnSameDate_multiplePlayersAllConsistent() {
        // With multiple players over two games, every player's season row = sum of game rows
        val p1 = playerDao.insertPlayer(Player(teamId = teamId, name = "Weber",  number = "4",  primaryPosition = 5))
        val p2 = playerDao.insertPlayer(Player(teamId = teamId, name = "Braun",  number = "11", primaryPosition = 7))
        val g1 = gameDao.insertGame(Game(date = "03.05.2026", opponent = "Nightmares", teamId = teamId))
        val g2 = gameDao.insertGame(Game(date = "03.05.2026", opponent = "Eagles",     teamId = teamId))

        atBatDao.insertAtBat(AtBat(gameId = g1, playerId = p1, slot = 1, inning = 1, result = "H",  rbi = 1))
        atBatDao.insertAtBat(AtBat(gameId = g1, playerId = p1, slot = 1, inning = 2, result = "BB"))
        atBatDao.insertAtBat(AtBat(gameId = g1, playerId = p2, slot = 2, inning = 1, result = "K"))
        atBatDao.insertAtBat(AtBat(gameId = g1, playerId = p2, slot = 2, inning = 2, result = "3B", rbi = 2))

        atBatDao.insertAtBat(AtBat(gameId = g2, playerId = p1, slot = 1, inning = 1, result = "2B", rbi = 1))
        atBatDao.insertAtBat(AtBat(gameId = g2, playerId = p1, slot = 1, inning = 2, result = "KL"))
        atBatDao.insertAtBat(AtBat(gameId = g2, playerId = p2, slot = 2, inning = 1, result = "HBP"))
        atBatDao.insertAtBat(AtBat(gameId = g2, playerId = p2, slot = 2, inning = 2, result = "GO"))

        for (pId in listOf(p1, p2)) {
            val gs1 = atBatDao.getGameBatterStats(g1).firstOrNull { it.playerId == pId }
            val gs2 = atBatDao.getGameBatterStats(g2).firstOrNull { it.playerId == pId }
            val sea = atBatDao.getSeasonBatterStats(teamId, "20260503", "20260503").first { it.playerId == pId }

            val expPa      = (gs1?.pa ?: 0)         + (gs2?.pa ?: 0)
            val expAb      = (gs1?.ab ?: 0)         + (gs2?.ab ?: 0)
            val expHits    = (gs1?.hits ?: 0)       + (gs2?.hits ?: 0)
            val expDoubles = (gs1?.doubles ?: 0)    + (gs2?.doubles ?: 0)
            val expTriples = (gs1?.triples ?: 0)    + (gs2?.triples ?: 0)
            val expHomers  = (gs1?.homers ?: 0)     + (gs2?.homers ?: 0)
            val expRbi     = (gs1?.rbi ?: 0)        + (gs2?.rbi ?: 0)
            val expWalks   = (gs1?.walks ?: 0)      + (gs2?.walks ?: 0)
            val expKs      = (gs1?.strikeouts ?: 0) + (gs2?.strikeouts ?: 0)
            val expHbp     = (gs1?.hbp ?: 0)        + (gs2?.hbp ?: 0)

            assertEquals("player $pId pa",         expPa,      sea.pa)
            assertEquals("player $pId ab",         expAb,      sea.ab)
            assertEquals("player $pId hits",       expHits,    sea.hits)
            assertEquals("player $pId doubles",    expDoubles, sea.doubles)
            assertEquals("player $pId triples",    expTriples, sea.triples)
            assertEquals("player $pId homers",     expHomers,  sea.homers)
            assertEquals("player $pId rbi",        expRbi,     sea.rbi)
            assertEquals("player $pId walks",      expWalks,   sea.walks)
            assertEquals("player $pId strikeouts", expKs,      sea.strikeouts)
            assertEquals("player $pId hbp",        expHbp,     sea.hbp)
        }
    }

    @Test
    fun batterStats_realisticGameDay_rbiAndHitTypesConsistent() {
        // Reproduces a high-scoring inning (like inning 4 with 12 runs on 03.05.2026):
        // Multiple batters, various hit types and RBI, SAC and BB mixed in
        val p1 = playerDao.insertPlayer(Player(teamId = teamId, name = "Hoehmann", number = "88", primaryPosition = 8))
        val p2 = playerDao.insertPlayer(Player(teamId = teamId, name = "Grade",    number = "12", primaryPosition = 6))
        val p3 = playerDao.insertPlayer(Player(teamId = teamId, name = "Brazee",   number = "44", primaryPosition = 3))
        val gameId = gameDao.insertGame(Game(date = "03.05.2026", opponent = "Neunkirchen Nightmares", teamId = teamId))

        // Simulated inning 4 with heavy offense
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p1, slot = 1, inning = 4, result = "1B", rbi = 0))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p2, slot = 2, inning = 4, result = "BB"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p3, slot = 3, inning = 4, result = "2B", rbi = 2))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p1, slot = 1, inning = 4, result = "HR", rbi = 3))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p2, slot = 2, inning = 4, result = "SAC"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p3, slot = 3, inning = 4, result = "H",  rbi = 1))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p1, slot = 1, inning = 4, result = "KL"))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p2, slot = 2, inning = 4, result = "3B", rbi = 3))
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = p3, slot = 3, inning = 4, result = "GO"))

        val gameStatsByPlayer = atBatDao.getGameBatterStats(gameId).associateBy { it.playerId }
        val seasonStatsByPlayer = atBatDao.getSeasonBatterStats(teamId, "20260503", "20260503").associateBy { it.playerId }

        assertEquals("same player count", gameStatsByPlayer.size, seasonStatsByPlayer.size)

        for ((pId, g) in gameStatsByPlayer) {
            val s = seasonStatsByPlayer[pId]!!
            assertEquals("player $pId pa",         g.pa,         s.pa)
            assertEquals("player $pId ab",         g.ab,         s.ab)
            assertEquals("player $pId hits",       g.hits,       s.hits)
            assertEquals("player $pId doubles",    g.doubles,    s.doubles)
            assertEquals("player $pId triples",    g.triples,    s.triples)
            assertEquals("player $pId homers",     g.homers,     s.homers)
            assertEquals("player $pId rbi",        g.rbi,        s.rbi)
            assertEquals("player $pId walks",      g.walks,      s.walks)
            assertEquals("player $pId strikeouts", g.strikeouts, s.strikeouts)
            assertEquals("player $pId hbp",        g.hbp,        s.hbp)
        }
    }

    @Test
    fun pitcherStats_realisticMixedSequence_seasonMatchesSingleGame() {
        // Reproduces a realistic pitch sequence from game 03.05.2026:
        // 4 walks (BBBBW BF each), 1 hit (1B BF), 1 runner-out (RO)
        // Expected: BF=5, balls=16, walks=4, hits=1, totalPitches=18
        val helper = DatabaseHelper(db)
        val playerId = playerDao.insertPlayer(Player(teamId = teamId, name = "Niklas", number = "36", primaryPosition = 1, isPitcher = true))
        val gameId = gameDao.insertGame(Game(date = "03.05.2026", opponent = "Neunkirchen Nightmares", teamId = teamId))
        val pitcherId = pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = "Niklas", playerId = playerId))

        val sequence = listOf(
            "B","B","B","B","W","BF",  // walk 1
            "B","S","B","B","B","W","BF",  // walk 2
            "B","B","B","B","W","BF",  // walk 3
            "RO","1B","BF",            // hit (runner out on bases)
            "B","B","B","B","W","BF"   // walk 4
        )
        sequence.forEachIndexed { idx, type ->
            pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = type, sequenceNr = idx + 1))
        }

        val single = helper.getStatsForPitcher(pitcherId)!!
        val season = pitcherDao.getSeasonPitcherStats(teamId).first()

        assertEquals("bf",           5,  single.bf)
        assertEquals("walks",        4,  single.walks)
        assertEquals("hits",         1,  single.hits)
        assertEquals("totalPitches", 18, single.totalPitches)

        assertEquals("season bf",           single.bf,           season.bf)
        assertEquals("season balls",        single.balls,        season.balls)
        assertEquals("season walks",        single.walks,        season.walks)
        assertEquals("season hits",         single.hits,         season.hits)
        assertEquals("season totalPitches", single.totalPitches, season.totalPitches)
    }
}
