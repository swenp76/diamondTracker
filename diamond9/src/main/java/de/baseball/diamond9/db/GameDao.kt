package de.baseball.diamond9.db

import androidx.room.*
import de.baseball.diamond9.*

@Dao
abstract class GameDao {

    @Query("SELECT * FROM games ORDER BY id DESC")
    abstract fun getAllGames(): List<Game>

    @Query("SELECT * FROM games WHERE team_id = :teamId ORDER BY id DESC")
    abstract fun getGamesForTeam(teamId: Long): List<Game>

    @Query("SELECT * FROM games WHERE id = :gameId")
    abstract fun getGame(gameId: Long): Game?

    @Insert
    abstract fun insertGame(game: Game): Long

    @Query("UPDATE games SET date = :date, opponent = :opponent, game_time = :gameTime WHERE id = :gameId")
    abstract fun updateGame(gameId: Long, date: String, opponent: String, gameTime: String)

    @Query("UPDATE games SET inning = :inning, outs = :outs WHERE id = :gameId")
    abstract fun updateGameState(gameId: Long, inning: Int, outs: Int)

    @Query("UPDATE games SET leadoff_slot = :slot WHERE id = :gameId")
    abstract fun updateLeadoffSlot(gameId: Long, slot: Int)

    @Query("UPDATE games SET start_time = :timestamp WHERE id = :gameId")
    abstract fun updateStartTime(gameId: Long, timestamp: Long)

    @Transaction
    open fun deleteGameWithCascade(gameId: Long) {
        val pitcherIds = getPitcherIdsForGame(gameId)
        if (pitcherIds.isNotEmpty()) deletePitches(pitcherIds)
        deletePitchersForGame(gameId)
        deleteOpponentLineup(gameId)
        deleteOpponentBench(gameId)
        deleteOwnLineup(gameId)
        deleteSubstitutions(gameId)
        deleteOppSubstitutions(gameId)
        deleteScoreboardRuns(gameId)
        deleteGameById(gameId)
    }

    @Query("SELECT id FROM pitchers WHERE game_id = :gameId")
    abstract fun getPitcherIdsForGame(gameId: Long): List<Long>

    @Query("DELETE FROM pitches WHERE pitcher_id IN (:pitcherIds)")
    abstract fun deletePitches(pitcherIds: List<Long>)

    @Query("DELETE FROM pitchers WHERE game_id = :gameId")
    abstract fun deletePitchersForGame(gameId: Long)

    @Query("DELETE FROM opponent_lineup WHERE game_id = :gameId")
    abstract fun deleteOpponentLineup(gameId: Long)

    @Query("DELETE FROM opponent_bench WHERE game_id = :gameId")
    abstract fun deleteOpponentBench(gameId: Long)

    @Query("DELETE FROM own_lineup WHERE game_id = :gameId")
    abstract fun deleteOwnLineup(gameId: Long)

    @Query("DELETE FROM substitutions WHERE game_id = :gameId")
    abstract fun deleteSubstitutions(gameId: Long)

    @Query("DELETE FROM opponent_substitutions WHERE game_id = :gameId")
    abstract fun deleteOppSubstitutions(gameId: Long)

    @Query("DELETE FROM scoreboard_runs WHERE game_id = :gameId")
    abstract fun deleteScoreboardRuns(gameId: Long)

    @Query("DELETE FROM games WHERE id = :gameId")
    abstract fun deleteGameById(gameId: Long)
}
