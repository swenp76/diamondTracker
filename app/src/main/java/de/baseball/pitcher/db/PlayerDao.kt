package de.baseball.pitcher.db

import androidx.room.*
import de.baseball.pitcher.*

@Dao
interface PlayerDao {

    @Query("SELECT * FROM players WHERE team_id = :teamId ORDER BY number ASC, name ASC")
    fun getPlayersForTeam(teamId: Long): List<Player>

    @Query("SELECT * FROM players WHERE id = :playerId")
    fun getPlayerById(playerId: Long): Player?

    @Insert
    fun insertPlayer(player: Player): Long

    @Update
    fun updatePlayer(player: Player)

    @Query("DELETE FROM players WHERE id = :playerId")
    fun deletePlayer(playerId: Long)
}
