package de.baseball.diamond9.db

import androidx.room.*
import de.baseball.diamond9.LeagueSettings

@Dao
interface LeagueSettingsDao {

    @Query("SELECT * FROM league_settings WHERE team_id = :teamId LIMIT 1")
    fun get(teamId: Long): LeagueSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(settings: LeagueSettings)
}
