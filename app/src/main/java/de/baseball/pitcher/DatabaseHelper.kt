package de.baseball.pitcher

import android.content.Context
import androidx.room.*
import de.baseball.pitcher.db.*

// ── Entities ──────────────────────────────────────────────────────────────────

@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val opponent: String,
    @ColumnInfo(name = "team_id") val teamId: Long = 0
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
    @ColumnInfo(name = "pitcher_id") val pitcherId: Long,
    val type: String,
    @ColumnInfo(name = "sequence_nr") val sequenceNr: Int
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
        1 to "1 – Pitcher",
        2 to "2 – Catcher",
        3 to "3 – First Base",
        4 to "4 – Second Base",
        5 to "5 – Third Base",
        6 to "6 – Shortstop",
        7 to "7 – Left Field",
        8 to "8 – Center Field",
        9 to "9 – Right Field",
        10 to "DH – Designated Hitter"
    )
    fun label(pos: Int) = ALL.firstOrNull { it.first == pos }?.second ?: "?"
    fun shortLabel(pos: Int) = when (pos) {
        1 -> "P"; 2 -> "C"; 3 -> "1B"; 4 -> "2B"; 5 -> "3B"
        6 -> "SS"; 7 -> "LF"; 8 -> "CF"; 9 -> "RF"; 10 -> "DH"
        else -> "?"
    }
}

data class PitcherStats(
    val pitcher: Pitcher,
    val bf: Int,
    val balls: Int,
    val strikes: Int,
    val totalPitches: Int,
    val pitches: List<Pitch>
)

// ── DatabaseHelper – Room-Wrapper mit identischer API ─────────────────────────

class DatabaseHelper(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val gameDao = db.gameDao()
    private val pitcherDao = db.pitcherDao()
    private val teamDao = db.teamDao()
    private val playerDao = db.playerDao()
    private val lineupDao = db.lineupDao()

    // ── Games ──────────────────────────────────────────────────────────────────

    fun insertGame(date: String, opponent: String, teamId: Long): Long =
        gameDao.insertGame(Game(date = date, opponent = opponent, teamId = teamId))

    fun getAllGames(): List<Game> = gameDao.getAllGames()

    fun getGame(gameId: Long): Game? = gameDao.getGame(gameId)

    fun updateGame(gameId: Long, date: String, opponent: String) =
        gameDao.updateGame(gameId, date, opponent)

    fun deleteGame(gameId: Long) = gameDao.deleteGameWithCascade(gameId)

    fun copyGame(sourceGameId: Long, newOpponent: String): Long {
        val source = getGame(sourceGameId) ?: return -1
        return insertGame(source.date, newOpponent, source.teamId)
    }

    // ── Pitchers ───────────────────────────────────────────────────────────────

    fun insertPitcher(gameId: Long, name: String, playerId: Long = 0): Long =
        pitcherDao.insertPitcher(Pitcher(gameId = gameId, name = name, playerId = playerId))

    fun getPitchersForGame(gameId: Long): List<Pitcher> =
        pitcherDao.getPitchersForGame(gameId)

    fun deletePitcher(pitcherId: Long) = pitcherDao.deletePitcher(pitcherId)

    // ── Pitches ────────────────────────────────────────────────────────────────

    fun insertPitch(pitcherId: Long, type: String): Long {
        val next = pitcherDao.getNextSequenceNr(pitcherId)
        return pitcherDao.insertPitch(Pitch(pitcherId = pitcherId, type = type, sequenceNr = next))
    }

    fun undoLastPitch(pitcherId: Long) = pitcherDao.undoLastPitch(pitcherId)

    fun getPitchesForPitcher(pitcherId: Long): List<Pitch> =
        pitcherDao.getPitchesForPitcher(pitcherId)

    fun getStatsForPitcher(pitcherId: Long): PitcherStats {
        val pitcher = pitcherDao.getPitcherById(pitcherId)
        val pitches = getPitchesForPitcher(pitcherId)
        return PitcherStats(
            pitcher = pitcher,
            bf = pitches.count { it.type == "BF" },
            balls = pitches.count { it.type == "B" },
            strikes = pitches.count { it.type == "S" },
            totalPitches = pitches.count { it.type == "B" || it.type == "S" || it.type == "F" },
            pitches = pitches
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

    fun getOwnLineupStarters(gameId: Long): List<Player> =
        getOwnLineup(gameId).filter { it.key in 1..9 }.toSortedMap().values.toList()

    // ── Own Substitutions ──────────────────────────────────────────────────────

    fun addSubstitution(gameId: Long, slot: Int, playerOutId: Long, playerInId: Long): Long =
        lineupDao.insertSubstitution(
            Substitution(gameId = gameId, slot = slot, playerOutId = playerOutId, playerInId = playerInId)
        )

    fun getSubstitutionsForGame(gameId: Long): List<Substitution> =
        lineupDao.getSubstitutionsForGame(gameId)
}
