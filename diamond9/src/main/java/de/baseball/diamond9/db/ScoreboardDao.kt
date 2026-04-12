package de.baseball.diamond9.db

import androidx.room.*
import de.baseball.diamond9.ScoreboardRun

@Dao
interface ScoreboardDao {

    @Query("SELECT * FROM scoreboard_runs WHERE game_id = :gameId ORDER BY inning ASC, is_home ASC")
    fun getScoreboard(gameId: Long): List<ScoreboardRun>

    @Query("SELECT * FROM scoreboard_runs WHERE game_id = :gameId AND inning = :inning AND is_home = :isHome LIMIT 1")
    fun getRun(gameId: Long, inning: Int, isHome: Int): ScoreboardRun?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(run: ScoreboardRun): Long

    @Update
    fun update(run: ScoreboardRun)

    @Query("DELETE FROM scoreboard_runs WHERE game_id = :gameId")
    fun deleteForGame(gameId: Long)
}
