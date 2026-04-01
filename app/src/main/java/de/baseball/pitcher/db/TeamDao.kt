package de.baseball.pitcher.db

import androidx.room.*
import de.baseball.pitcher.*

@Entity(tableName = "team_positions", primaryKeys = ["team_id", "position"])
data class TeamPosition(
    @ColumnInfo(name = "team_id") val teamId: Long,
    val position: Int
)

@Dao
abstract class TeamDao {

    @Query("SELECT * FROM teams ORDER BY name ASC")
    abstract fun getAllTeams(): List<Team>

    @Insert
    abstract fun insertTeam(team: Team): Long

    @Query("UPDATE teams SET name = :name WHERE id = :teamId")
    abstract fun updateTeamName(teamId: Long, name: String)

    @Transaction
    open fun deleteTeam(teamId: Long) {
        deletePlayersForTeam(teamId)
        deleteTeamPositions(teamId)
        deleteTeamById(teamId)
    }

    @Query("DELETE FROM players WHERE team_id = :teamId")
    abstract fun deletePlayersForTeam(teamId: Long)

    @Query("DELETE FROM team_positions WHERE team_id = :teamId")
    abstract fun deleteTeamPositions(teamId: Long)

    @Query("DELETE FROM teams WHERE id = :teamId")
    abstract fun deleteTeamById(teamId: Long)

    @Query("SELECT position FROM team_positions WHERE team_id = :teamId")
    abstract fun getEnabledPositionsRaw(teamId: Long): List<Int>

    fun getEnabledPositions(teamId: Long): Set<Int> = getEnabledPositionsRaw(teamId).toSet()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertTeamPosition(position: TeamPosition)

    @Query("DELETE FROM team_positions WHERE team_id = :teamId AND position = :position")
    abstract fun deleteTeamPosition(teamId: Long, position: Int)
}
