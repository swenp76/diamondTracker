package de.baseball.diamond9

import android.content.Context
import androidx.room.*
import de.baseball.diamond9.db.*

// ── Entities ──────────────────────────────────────────────────────────────────

@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val opponent: String,
    @ColumnInfo(name = "team_id") val teamId: Long = 0,
    @ColumnInfo(name = "inning", defaultValue = "1") val inning: Int = 1,
    @ColumnInfo(name = "outs", defaultValue = "0") val outs: Int = 0,
    @ColumnInfo(name = "leadoff_slot", defaultValue = "1") val leadoffSlot: Int = 1,
    @ColumnInfo(name = "start_time", defaultValue = "0") val startTime: Long = 0L,
    @ColumnInfo(name = "elapsed_time_ms", defaultValue = "0") val elapsedTimeMs: Long = 0L,
    @ColumnInfo(name = "game_time", defaultValue = "") val gameTime: String = "",
    @ColumnInfo(name = "is_home", defaultValue = "1") val isHome: Int = 1,  // 1 = home, 0 = away
    @ColumnInfo(name = "current_inning", defaultValue = "1") val currentInning: Int = 1,
    @ColumnInfo(name = "is_top_half", defaultValue = "1") val isTopHalf: Int = 1  // 1 = top, 0 = bottom
)

@Entity(tableName = "pitchers")
data class Pitcher(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    val name: String,
    @ColumnInfo(name = "player_id") val playerId: Long = 0
)

@Entity(tableName = "pitches")
data class Pitch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pitcher_id") val pitcherId: Long = 0,
    @ColumnInfo(name = "at_bat_id") val atBatId: Long = 0,
    val type: String,
    @ColumnInfo(name = "sequence_nr") val sequenceNr: Int,
    @ColumnInfo(name = "inning", defaultValue = "1") val inning: Int = 1
)

@Entity(tableName = "at_bats")
data class AtBat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "player_id") val playerId: Long,
    @ColumnInfo(name = "slot") val slot: Int,
    @ColumnInfo(name = "inning") val inning: Int,
    @ColumnInfo(name = "result") val result: String? = null // e.g., "K", "BB", "H", "HBP", "OUT"
)
// type: "B" = Ball, "S" = Strike, "BF" = Batter Faced

@Entity(tableName = "teams")
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "team_id") val teamId: Long,
    val name: String,
    val number: String,
    @ColumnInfo(name = "primary_position") val primaryPosition: Int,
    @ColumnInfo(name = "secondary_position") val secondaryPosition: Int = 0,
    @ColumnInfo(name = "is_pitcher") val isPitcher: Boolean = false,
    @ColumnInfo(name = "birth_year") val birthYear: Int = 0
)

@Entity(
    tableName = "pitcher_appearances",
    indices = [Index(value = ["player_id", "game_id"], unique = true)]
)
data class PitcherAppearance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "player_id") val playerId: Long,
    @ColumnInfo(name = "game_id") val gameId: Long,
    val date: String,
    @ColumnInfo(name = "batters_faced") val battersFaced: Int
)

@Entity(
    tableName = "opponent_lineup",
    indices = [Index(value = ["game_id", "batting_order"], unique = true)]
)
data class LineupEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "batting_order") val battingOrder: Int,
    @ColumnInfo(name = "jersey_number") val jerseyNumber: String
)

@Entity(tableName = "opponent_bench")
data class BenchPlayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "jersey_number") val jerseyNumber: String
)

@Entity(tableName = "substitutions")
data class Substitution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    val slot: Int,
    @ColumnInfo(name = "player_out_id") val playerOutId: Long,
    @ColumnInfo(name = "player_in_id") val playerInId: Long
)

@Entity(tableName = "opponent_teams", indices = [Index(value = ["name", "team_id"], unique = true)])
data class OpponentTeam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "team_id", defaultValue = "0") val teamId: Long = 0L
)

@Entity(tableName = "scoreboard_runs")
data class ScoreboardRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    @ColumnInfo(name = "inning") val inning: Int,
    @ColumnInfo(name = "is_home") val isHome: Int, // 0 = away, 1 = home
    @ColumnInfo(name = "runs") val runs: Int = 0
)

@Entity(tableName = "opponent_substitutions")
data class OppSubstitution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "game_id") val gameId: Long,
    val slot: Int,
    @ColumnInfo(name = "jersey_out") val jerseyOut: String,
    @ColumnInfo(name = "jersey_in") val jerseyIn: String
)

// ── Utilities ─────────────────────────────────────────────────────────────────

object BaseballPositions {
    val ALL = listOf(
        1 to R.string.pos_1,
        2 to R.string.pos_2,
        3 to R.string.pos_3,
        4 to R.string.pos_4,
        5 to R.string.pos_5,
        6 to R.string.pos_6,
        7 to R.string.pos_7,
        8 to R.string.pos_8,
        9 to R.string.pos_9,
        10 to R.string.pos_10
    )

    fun shortLabelRes(pos: Int): Int = when (pos) {
        1 -> R.string.pos_short_1
        2 -> R.string.pos_short_2
        3 -> R.string.pos_short_3
        4 -> R.string.pos_short_4
        5 -> R.string.pos_short_5
        6 -> R.string.pos_short_6
        7 -> R.string.pos_short_7
        8 -> R.string.pos_short_8
        9 -> R.string.pos_short_9
        10 -> R.string.pos_short_10
        else -> R.string.pos_short_unknown
    }

    fun labelRes(pos: Int): Int = when (pos) {
        1 -> R.string.pos_1
        2 -> R.string.pos_2
        3 -> R.string.pos_3
        4 -> R.string.pos_4
        5 -> R.string.pos_5
        6 -> R.string.pos_6
        7 -> R.string.pos_7
        8 -> R.string.pos_8
        9 -> R.string.pos_9
        10 -> R.string.pos_10
        else -> R.string.pos_short_unknown
    }
}

data class GameBatterStatsRow(
    @ColumnInfo(name = "player_id") val playerId: Long,
    @ColumnInfo(name = "pa")         val pa: Int,
    @ColumnInfo(name = "ab")         val ab: Int,
    @ColumnInfo(name = "hits")       val hits: Int,
    @ColumnInfo(name = "doubles")    val doubles: Int,
    @ColumnInfo(name = "triples")    val triples: Int,
    @ColumnInfo(name = "homers")     val homers: Int,
    @ColumnInfo(name = "walks")      val walks: Int,
    @ColumnInfo(name = "strikeouts") val strikeouts: Int,
    @ColumnInfo(name = "hbp")        val hbp: Int
)

data class SeasonBatterRow(
    @ColumnInfo(name = "player_id") val playerId: Long,
    @ColumnInfo(name = "pa") val pa: Int,
    @ColumnInfo(name = "ab") val ab: Int,
    @ColumnInfo(name = "hits") val hits: Int,
    @ColumnInfo(name = "doubles") val doubles: Int,
    @ColumnInfo(name = "triples") val triples: Int,
    @ColumnInfo(name = "homers") val homers: Int,
    @ColumnInfo(name = "walks") val walks: Int,
    @ColumnInfo(name = "strikeouts") val strikeouts: Int,
    @ColumnInfo(name = "hbp") val hbp: Int
)

data class SeasonPitcherRow(
    @ColumnInfo(name = "player_id") val playerId: Long,
    @ColumnInfo(name = "total_pitches") val totalPitches: Int,
    @ColumnInfo(name = "bf") val bf: Int,
    @ColumnInfo(name = "balls") val balls: Int,
    @ColumnInfo(name = "strikes") val strikes: Int,
    @ColumnInfo(name = "fouls") val fouls: Int,
    @ColumnInfo(name = "walks") val walks: Int,
    @ColumnInfo(name = "hits") val hits: Int,
    @ColumnInfo(name = "ks") val ks: Int,
    @ColumnInfo(name = "homers") val homers: Int,
    @ColumnInfo(name = "gos") val gos: Int,
    @ColumnInfo(name = "fos") val fos: Int
)

data class PitcherStats(
    val pitcher: Pitcher,
    val bf: Int,
    val balls: Int,
    val strikes: Int,
    val walks: Int,
    val hbp: Int,
    val hits: Int,
    val strikeouts: Int,
    val totalPitches: Int,
    val pitches: List<Pitch>,
    val ip: String = ""
)

// ── DatabaseHelper – Room-Wrapper mit identischer API ─────────────────────────

class DatabaseHelper(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val gameDao = db.gameDao()
    private val pitcherDao = db.pitcherDao()
    private val atBatDao = db.atBatDao()
    private val teamDao = db.teamDao()
    private val playerDao = db.playerDao()
    private val lineupDao = db.lineupDao()
    private val opponentTeamDao = db.opponentTeamDao()
    private val scoreboardDao = db.scoreboardDao()

    // ── Games ──────────────────────────────────────────────────────────────────

    fun insertGame(date: String, opponent: String, teamId: Long, gameTime: String = "", isHome: Int = 1): Long =
        gameDao.insertGame(Game(date = date, opponent = opponent, teamId = teamId, gameTime = gameTime, isHome = isHome))

    fun getAllGames(): List<Game> = gameDao.getAllGames()

    fun getGamesForTeam(teamId: Long): List<Game> = gameDao.getGamesForTeam(teamId)

    fun getGame(gameId: Long): Game? = gameDao.getGame(gameId)

    fun updateGame(gameId: Long, date: String, opponent: String, gameTime: String = "", isHome: Int = 1) =
        gameDao.updateGame(gameId, date, opponent, gameTime, isHome)

    fun deleteGame(gameId: Long) = gameDao.deleteGameWithCascade(gameId)

    fun getGameState(gameId: Long): Pair<Int, Int> {
        val game = gameDao.getGame(gameId)
        return Pair(game?.inning ?: 1, game?.outs ?: 0)
    }

    fun updateGameState(gameId: Long, inning: Int, outs: Int) =
        gameDao.updateGameState(gameId, inning, outs)

    fun getLeadoffSlot(gameId: Long): Int =
        gameDao.getGame(gameId)?.leadoffSlot ?: 1

    fun updateLeadoffSlot(gameId: Long, slot: Int) =
        gameDao.updateLeadoffSlot(gameId, slot)

    fun getStartTime(gameId: Long): Long =
        gameDao.getGame(gameId)?.startTime ?: 0L

    fun setStartTime(gameId: Long, timestamp: Long) =
        gameDao.updateStartTime(gameId, timestamp)

    fun getElapsedTime(gameId: Long): Long =
        gameDao.getGame(gameId)?.elapsedTimeMs ?: 0L

    fun setElapsedTime(gameId: Long, elapsedMs: Long) =
        gameDao.updateElapsedTime(gameId, elapsedMs)

    fun getHalfInningState(gameId: Long): HalfInningState {
        val game = gameDao.getGame(gameId)
        return HalfInningState(
            inning = game?.currentInning ?: 1,
            isTopHalf = (game?.isTopHalf ?: 1) == 1
        )
    }

    fun updateHalfInning(gameId: Long, inning: Int, isTopHalf: Boolean) =
        gameDao.updateHalfInning(gameId, inning, if (isTopHalf) 1 else 0)

    fun copyGame(sourceGameId: Long, newOpponent: String): Long {
        val source = getGame(sourceGameId) ?: return -1
        return insertGame(source.date, newOpponent, source.teamId, isHome = source.isHome)
    }

    // ── Pitchers ───────────────────────────────────────────────────────────────

    fun insertPitcher(gameId: Long, name: String, playerId: Long = 0): Long =
        pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = name, playerId = playerId))

    fun getPitchersForGame(gameId: Long): List<Pitcher> =
        pitcherDao.getPitchersForGame(gameId)

    fun deletePitcher(pitcherId: Long) = pitcherDao.deletePitcher(pitcherId)

    // ── Pitches ────────────────────────────────────────────────────────────────

    fun insertPitch(pitcherId: Long, type: String, inning: Int = 1): Long {
        val next = pitcherDao.getNextSequenceNr(pitcherId)
        return pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = type, sequenceNr = next, inning = inning))
    }

    fun undoLastPitch(pitcherId: Long) = pitcherDao.undoLastPitch(pitcherId)

    fun getPitchesForPitcher(pitcherId: Long): List<Pitch> =
        pitcherDao.getPitchesForPitcher(pitcherId)

    private fun formatIP(outs: Int) = "${outs / 3}.${outs % 3}"

    fun getStatsForPitcher(pitcherId: Long): PitcherStats {
        val pitcher = pitcherDao.getPitcherById(pitcherId)
        val pitches = getPitchesForPitcher(pitcherId)
        val outs = atBatDao.getOutsForGame(pitcher.gameId)

        var strikes = 0
        var currentStrikesAtBat = 0
        for (p in pitches) {
            when (p.type) {
                "S", "SO" -> {
                    strikes++
                    currentStrikesAtBat++
                }
                "F" -> {
                    if (currentStrikesAtBat < 2) {
                        strikes++
                        currentStrikesAtBat++
                    }
                }
                "BF" -> {
                    currentStrikesAtBat = 0
                }
            }
        }

        return PitcherStats(
            pitcher = pitcher,
            bf = pitches.count { it.type == "BF" },
            balls = pitches.count { it.type == "B" },
            strikes = strikes,
            walks = pitches.count { it.type == "W" },
            hbp = pitches.count { it.type == "HBP" },
            hits = pitches.count { it.type in setOf("H", "1B", "2B", "3B", "HR") },
            strikeouts = pitches.count { it.type == "SO" },
            totalPitches = pitches.count { it.type == "B" || it.type == "S" || it.type == "SO" || it.type == "F" || it.type == "HBP" },
            pitches = pitches,
            ip = formatIP(outs)
        )
    }

    fun getTotalBFForGame(gameId: Long): Int = pitcherDao.getTotalBFForGame(gameId)

    fun getTotalBFForPlayerOnDate(playerId: Long, date: String): Int =
        pitcherDao.getTotalBFForPlayerOnDate(playerId, date)

    fun getIncompleteAtBatBeforePitcher(gameId: Long, currentPitcherId: Long): List<String> {
        val all = pitcherDao.getAllPitchTypesBeforePitcher(gameId, currentPitcherId)
        val lastBf = all.indexOfLast { it == "BF" }
        val tail = if (lastBf == -1) all else all.drop(lastBf + 1)
        return tail.filter { it == "B" || it == "S" || it == "F" }
    }

    // ── Teams ──────────────────────────────────────────────────────────────────

    fun insertTeam(name: String): Long {
        val id = teamDao.insertTeam(Team(name = name))
        (1..9).forEach { pos -> teamDao.insertTeamPosition(TeamPosition(teamId = id, position = pos)) }
        return id
    }

    fun getAllTeams(): List<Team> = teamDao.getAllTeams()

    fun updateTeamName(teamId: Long, name: String) = teamDao.updateTeamName(teamId, name)

    fun deleteTeam(teamId: Long) = teamDao.deleteTeam(teamId)

    // ── Positions ──────────────────────────────────────────────────────────────

    fun getEnabledPositions(teamId: Long): Set<Int> = teamDao.getEnabledPositions(teamId)

    fun setPositionEnabled(teamId: Long, position: Int, enabled: Boolean) {
        if (enabled) teamDao.insertTeamPosition(TeamPosition(teamId = teamId, position = position))
        else teamDao.deleteTeamPosition(teamId, position)
    }

    // ── Players ────────────────────────────────────────────────────────────────

    fun insertPlayer(
        teamId: Long, name: String, number: String,
        primaryPosition: Int, secondaryPosition: Int = 0,
        isPitcher: Boolean = false, birthYear: Int = 0
    ): Long = playerDao.insertPlayer(
        Player(
            teamId = teamId, name = name, number = number,
            primaryPosition = primaryPosition, secondaryPosition = secondaryPosition,
            isPitcher = isPitcher, birthYear = birthYear
        )
    )

    fun getPlayersForTeam(teamId: Long): List<Player> = playerDao.getPlayersForTeam(teamId)

    fun updatePlayer(player: Player) = playerDao.updatePlayer(player)

    fun deletePlayer(playerId: Long) = playerDao.deletePlayer(playerId)

    fun getPlayerById(playerId: Long): Player? = playerDao.getPlayerById(playerId)

    // ── Pitcher Appearances ────────────────────────────────────────────────────

    fun savePitcherAppearance(playerId: Long, gameId: Long, date: String, bf: Int): Long =
        lineupDao.savePitcherAppearance(
            PitcherAppearance(playerId = playerId, gameId = gameId, date = date, battersFaced = bf)
        )

    fun getPitcherAppearances(playerId: Long): List<PitcherAppearance> =
        lineupDao.getPitcherAppearances(playerId)

    fun getTotalBFForDate(playerId: Long, date: String): Int =
        lineupDao.getTotalBFForDate(playerId, date) ?: 0

    // ── Opponent Lineup ────────────────────────────────────────────────────────

    fun upsertLineupEntry(gameId: Long, battingOrder: Int, jerseyNumber: String) =
        lineupDao.upsertLineupEntry(LineupEntry(gameId = gameId, battingOrder = battingOrder, jerseyNumber = jerseyNumber))

    fun getLineup(gameId: Long): List<LineupEntry> = lineupDao.getLineup(gameId)

    fun getJerseyAtBattingOrder(gameId: Long, battingOrder: Int): String =
        lineupDao.getJerseyAtBattingOrder(gameId, battingOrder) ?: ""

    fun deleteLineupEntry(gameId: Long, battingOrder: Int) =
        lineupDao.deleteLineupEntry(gameId, battingOrder)

    // ── Opponent Bench ─────────────────────────────────────────────────────────

    fun insertBenchPlayer(gameId: Long, jerseyNumber: String): Long =
        lineupDao.insertBenchPlayer(BenchPlayer(gameId = gameId, jerseyNumber = jerseyNumber))

    fun getBenchPlayers(gameId: Long): List<BenchPlayer> = lineupDao.getBenchPlayers(gameId)

    fun deleteBenchPlayer(id: Long) = lineupDao.deleteBenchPlayer(id)

    fun substitutePlayer(gameId: Long, battingOrder: Int, newJerseyNumber: String) =
        upsertLineupEntry(gameId, battingOrder, newJerseyNumber)

    // ── Opponent Substitutions ─────────────────────────────────────────────────

    fun addOpponentSubstitution(gameId: Long, slot: Int, jerseyOut: String, jerseyIn: String): Long =
        lineupDao.insertOppSubstitution(
            OppSubstitution(gameId = gameId, slot = slot, jerseyOut = jerseyOut, jerseyIn = jerseyIn)
        )

    fun getOpponentSubstitutionsForGame(gameId: Long): List<OppSubstitution> =
        lineupDao.getOpponentSubstitutionsForGame(gameId)

    // ── Own Lineup ─────────────────────────────────────────────────────────────

    fun setOwnLineupPlayer(gameId: Long, slot: Int, playerId: Long) =
        lineupDao.setOwnLineupPlayer(OwnLineupSlot(gameId = gameId, slot = slot, playerId = playerId))

    fun clearOwnLineupSlot(gameId: Long, slot: Int) =
        lineupDao.clearOwnLineupSlot(gameId, slot)

    fun clearLineupForGame(gameId: Long) {
        lineupDao.clearOwnLineup(gameId)
        lineupDao.clearSubstitutions(gameId)
        lineupDao.clearOpponentLineup(gameId)
        lineupDao.clearOpponentBench(gameId)
        lineupDao.clearOpponentSubstitutions(gameId)
    }

    fun getOwnLineup(gameId: Long): Map<Int, Player> =
        lineupDao.getOwnLineupRaw(gameId).associate { row ->
            row.slot to Player(
                id = row.playerId,
                teamId = row.teamId,
                name = row.name,
                number = row.number,
                primaryPosition = row.primaryPosition,
                secondaryPosition = row.secondaryPosition,
                isPitcher = row.isPitcher,
                birthYear = row.birthYear
            )
        }

    /**
     * Like getOwnLineup, but applies substitutions: for each slot that has subs,
     * the player returned is the last substitute in (not the original starter).
     * Use this wherever the *current* player on the field matters (e.g. BattingTrackActivity).
     */
    fun getEffectiveLineup(gameId: Long): Map<Int, Player> {
        val base = getOwnLineup(gameId).toMutableMap()
        getSubstitutionsForGame(gameId)
            .groupBy { it.slot }
            .forEach { (slot, slotSubs) ->
                val lastSub = slotSubs.last()
                val current = getPlayerById(lastSub.playerInId)
                if (current != null) base[slot] = current
            }
        return base
    }

    fun getOwnLineupStarters(gameId: Long): List<Player> =
        getOwnLineup(gameId).filter { it.key in 1..9 }.toSortedMap().values.toList()

    // ── Opponent Teams ─────────────────────────────────────────────────────────

    fun getAllOpponentTeams(): List<OpponentTeam> = opponentTeamDao.getAll()

    fun getOpponentTeamsForTeam(teamId: Long): List<OpponentTeam> =
        opponentTeamDao.getForTeam(teamId)

    fun insertOpponentTeamIfNew(name: String): Long =
        opponentTeamDao.insert(OpponentTeam(name = name))

    fun insertOpponentTeamForTeam(name: String, teamId: Long): Long =
        opponentTeamDao.insert(OpponentTeam(name = name, teamId = teamId))

    fun deleteOpponentTeam(id: Long) = opponentTeamDao.delete(id)

    // ── Own Substitutions ──────────────────────────────────────────────────────

    fun addSubstitution(gameId: Long, slot: Int, playerOutId: Long, playerInId: Long): Long =
        lineupDao.insertSubstitution(
            Substitution(gameId = gameId, slot = slot, playerOutId = playerOutId, playerInId = playerInId)
        )

    fun getSubstitutionsForGame(gameId: Long): List<Substitution> =
        lineupDao.getSubstitutionsForGame(gameId)

    // ── At-Bats (Offense) ──────────────────────────────────────────────────────

    fun insertAtBat(gameId: Long, playerId: Long, slot: Int, inning: Int): Long =
        atBatDao.insertAtBat(AtBat(gameId = gameId, playerId = playerId, slot = slot, inning = inning))

    fun getAtBatsForGame(gameId: Long): List<AtBat> = atBatDao.getAtBatsForGame(gameId)

    fun getAtBat(atBatId: Long): AtBat? = atBatDao.getAtBatById(atBatId)

    fun updateAtBatResult(atBatId: Long, result: String?) {
        val ab = atBatDao.getAtBatById(atBatId) ?: return
        atBatDao.updateAtBat(ab.copy(result = result))
    }

    fun deleteAtBat(atBatId: Long) {
        atBatDao.deletePitchesForAtBat(atBatId)
        atBatDao.deleteAtBat(atBatId)
    }

    fun insertPitchForAtBat(atBatId: Long, type: String, inning: Int): Long {
        val next = atBatDao.getNextSequenceNr(atBatId)
        return atBatDao.insertPitch(Pitch(atBatId = atBatId, type = type, sequenceNr = next, inning = inning))
    }

    fun undoLastPitchForAtBat(atBatId: Long) = atBatDao.undoLastPitch(atBatId)

    fun getPitchesForAtBat(atBatId: Long): List<Pitch> = atBatDao.getPitchesForAtBat(atBatId)

    // ── Season Stats ───────────────────────────────────────────────────────────

    fun getGameBatterStats(gameId: Long): List<GameBatterStatsRow> =
        atBatDao.getGameBatterStats(gameId)

    fun getSeasonBatterStats(teamId: Long): List<SeasonBatterRow> =
        atBatDao.getSeasonBatterStats(teamId)

    fun getSeasonPitcherStats(teamId: Long): List<SeasonPitcherRow> =
        pitcherDao.getSeasonPitcherStats(teamId)

    // ── Scoreboard ─────────────────────────────────────────────────────────────

    fun getScoreboard(gameId: Long): List<ScoreboardRun> = scoreboardDao.getScoreboard(gameId)

    fun getScoreboardRuns(gameId: Long, inning: Int, teamIndex: Int): Int =
        scoreboardDao.getRun(gameId, inning, teamIndex)?.runs ?: 0

    fun hasScoreboardEntry(gameId: Long, inning: Int, teamIndex: Int): Boolean =
        scoreboardDao.getRun(gameId, inning, teamIndex) != null

    fun upsertScoreboardRun(gameId: Long, inning: Int, isHome: Int, runs: Int) {
        val existing = scoreboardDao.getRun(gameId, inning, isHome)
        if (existing != null) {
            scoreboardDao.update(existing.copy(runs = runs))
        } else {
            scoreboardDao.insert(ScoreboardRun(gameId = gameId, inning = inning, isHome = isHome, runs = runs))
        }
    }

    fun getTeamById(teamId: Long): Team? = teamDao.getAllTeams().find { it.id == teamId }
}
