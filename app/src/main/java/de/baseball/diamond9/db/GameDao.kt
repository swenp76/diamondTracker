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

    @Query("UPDATE games SET date = :date, opponent = :opponent WHERE id = :gameId")
    abstract fun updateGame(gameId: Long, date: String, opponent: String)

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

    @Query("DELETE FROM games WHERE id = :gameId")
    abstract fun deleteGameById(gameId: Long)
}
