package de.baseball.pitcher.db

import androidx.room.*
import de.baseball.pitcher.*

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
    abstract fun getPitcherById(pitcherId: Long): Pitcher

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
}
