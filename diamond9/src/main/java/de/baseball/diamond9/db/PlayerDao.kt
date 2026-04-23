package de.baseball.diamond9.db

import androidx.room.*
import de.baseball.diamond9.*

@Dao
abstract class PlayerDao {

    @Query("SELECT * FROM players WHERE team_id = :teamId ORDER BY number ASC, name ASC")
    abstract fun getPlayersForTeam(teamId: Long): List<Player>

    @Query("SELECT * FROM players WHERE id = :playerId")
    abstract fun getPlayerById(playerId: Long): Player?

    @Insert
    abstract fun insertPlayer(player: Player): Long

    @Update
    abstract fun updatePlayer(player: Player)

    @Transaction
    open fun deletePlayerWithCascade(playerId: Long) {
        deleteAtBatsForPlayer(playerId)
        deletePitcherAppearancesForPlayer(playerId)
        deleteOwnLineupForPlayer(playerId)
        deleteSubstitutionsForPlayer(playerId)
        deletePitchersForPlayer(playerId)
        deletePlayer(playerId)
    }

    @Query("DELETE FROM players WHERE id = :playerId")
    abstract fun deletePlayer(playerId: Long)

    @Query("DELETE FROM at_bats WHERE player_id = :playerId")
    abstract fun deleteAtBatsForPlayer(playerId: Long)

    @Query("DELETE FROM pitcher_appearances WHERE player_id = :playerId")
    abstract fun deletePitcherAppearancesForPlayer(playerId: Long)

    @Query("DELETE FROM own_lineup WHERE player_id = :playerId")
    abstract fun deleteOwnLineupForPlayer(playerId: Long)

    @Query("DELETE FROM substitutions WHERE player_out_id = :playerId OR player_in_id = :playerId")
    abstract fun deleteSubstitutionsForPlayer(playerId: Long)

    @Query("DELETE FROM pitchers WHERE player_id = :playerId")
    abstract fun deletePitchersForPlayer(playerId: Long)
}
