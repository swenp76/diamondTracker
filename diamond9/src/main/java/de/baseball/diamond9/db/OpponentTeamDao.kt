package de.baseball.diamond9.db

import androidx.room.*
import de.baseball.diamond9.OpponentTeam

@Dao
interface OpponentTeamDao {

    @Query("SELECT * FROM opponent_teams ORDER BY name ASC")
    fun getAll(): List<OpponentTeam>

    @Query("SELECT * FROM opponent_teams WHERE team_id = :teamId ORDER BY name ASC")
    fun getForTeam(teamId: Long): List<OpponentTeam>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(team: OpponentTeam): Long

    @Query("DELETE FROM opponent_teams WHERE id = :id")
    fun delete(id: Long)
}
