package de.baseball.diamond9.db

import androidx.room.*
import de.baseball.diamond9.*

@Dao
abstract class PitcherDao {

    @Insert
    abstract fun insertPitcher(pitcher: Pitcher): Long

    @Query("""
        SELECT p.id, p.game_id,
               CASE WHEN p.player_id > 0 THEN pl.name ELSE p.name END AS name,
               p.player_id
        FROM pitchers p
        LEFT JOIN players pl ON pl.id = p.player_id
        WHERE p.game_id = :gameId
        ORDER BY p.id ASC
    """)
    abstract fun getPitchersForGame(gameId: Long): List<Pitcher>

    @Query("SELECT id, game_id, name, player_id FROM pitchers WHERE id = :pitcherId")
    abstract fun getPitcherById(pitcherId: Long): Pitcher?

    @Transaction
    open fun deletePitcher(pitcherId: Long) {
        deletePitchesForPitcher(pitcherId)
        deletePitcherById(pitcherId)
    }

    @Query("DELETE FROM pitches WHERE pitcher_id = :pitcherId")
    abstract fun deletePitchesForPitcher(pitcherId: Long)

    @Query("DELETE FROM pitchers WHERE id = :pitcherId")
    abstract fun deletePitcherById(pitcherId: Long)

    @Insert
    abstract fun insertPitch(pitch: Pitch): Long

    @Query("SELECT * FROM pitches WHERE pitcher_id = :pitcherId ORDER BY sequence_nr ASC")
    abstract fun getPitchesForPitcher(pitcherId: Long): List<Pitch>

    @Transaction
    open fun undoLastPitch(pitcherId: Long) {
        getLastPitchId(pitcherId)?.let { deletePitchById(it) }
    }

    @Query("SELECT id FROM pitches WHERE pitcher_id = :pitcherId ORDER BY sequence_nr DESC LIMIT 1")
    abstract fun getLastPitchId(pitcherId: Long): Long?

    @Query("DELETE FROM pitches WHERE id = :pitchId")
    abstract fun deletePitchById(pitchId: Long)

    @Query("SELECT COALESCE(MAX(sequence_nr), 0) + 1 FROM pitches WHERE pitcher_id = :pitcherId")
    abstract fun getNextSequenceNr(pitcherId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM pitches pi
        JOIN pitchers p ON p.id = pi.pitcher_id
        WHERE p.game_id = :gameId AND pi.type = 'BF'
    """)
    abstract fun getTotalBFForGame(gameId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM pitches pi
        JOIN pitchers p ON p.id = pi.pitcher_id
        JOIN games g ON g.id = p.game_id
        WHERE p.player_id = :playerId AND g.date = :date AND pi.type = 'BF'
    """)
    abstract fun getTotalBFForPlayerOnDate(playerId: Long, date: String): Int

    @Query("""
        SELECT pi.type FROM pitches pi
        JOIN pitchers p ON p.id = pi.pitcher_id
        WHERE p.game_id = :gameId AND p.id != :currentPitcherId
        ORDER BY p.id ASC, pi.sequence_nr ASC
    """)
    abstract fun getAllPitchTypesBeforePitcher(gameId: Long, currentPitcherId: Long): List<String>

    @Query("""
        SELECT p.player_id AS player_id,
            COUNT(CASE WHEN pi.type IN ('B', 'S', 'F', 'HBP', 'H', '1B', '2B', '3B', 'HR', 'GO', 'FO', 'LO', 'FC', 'E', 'DP', 'SAC') THEN 1 END) AS total_pitches,
            COUNT(CASE WHEN pi.type = 'BF'                           THEN 1 END)           AS bf,
            COUNT(CASE WHEN pi.type = 'B'                            THEN 1 END)           AS balls,
            COUNT(CASE WHEN pi.type IN ('S', 'F', 'H', '1B', '2B', '3B', 'HR', 'GO', 'FO', 'LO', 'FC', 'E', 'DP', 'SAC') THEN 1 END) AS strikes,
            COUNT(CASE WHEN pi.type = 'F'                            THEN 1 END)           AS fouls,
            COUNT(CASE WHEN pi.type = 'W'                            THEN 1 END)           AS walks,
            COUNT(CASE WHEN pi.type IN ('H', '1B', '2B', '3B', 'HR') THEN 1 END)           AS hits,
            COUNT(CASE WHEN pi.type IN ('SO', 'KL')                  THEN 1 END)           AS ks,
            COUNT(CASE WHEN pi.type = 'HR'                           THEN 1 END)           AS homers,
            COUNT(CASE WHEN pi.type = 'GO'                           THEN 1 END)           AS gos,
            COUNT(CASE WHEN pi.type IN ('FO', 'LO')                  THEN 1 END)           AS fos
        FROM pitches pi
        JOIN pitchers p ON p.id = pi.pitcher_id
        JOIN games g ON g.id = p.game_id
        WHERE g.team_id = :teamId AND p.player_id > 0
        GROUP BY p.player_id
    """)
    abstract fun getSeasonPitcherStats(teamId: Long): List<SeasonPitcherRow>
}
