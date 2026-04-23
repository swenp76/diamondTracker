package de.baseball.diamond9.db

import androidx.room.*
import de.baseball.diamond9.*

@Dao
abstract class AtBatDao {
    @Insert
    abstract fun insertAtBat(atBat: AtBat): Long

    @Query("SELECT * FROM at_bats WHERE game_id = :gameId ORDER BY id ASC")
    abstract fun getAtBatsForGame(gameId: Long): List<AtBat>

    @Query("SELECT * FROM at_bats WHERE id = :atBatId")
    abstract fun getAtBatById(atBatId: Long): AtBat?

    @Update
    abstract fun updateAtBat(atBat: AtBat)

    @Query("DELETE FROM at_bats WHERE id = :atBatId")
    abstract fun deleteAtBat(atBatId: Long)

    @Query("SELECT * FROM pitches WHERE at_bat_id = :atBatId ORDER BY sequence_nr ASC")
    abstract fun getPitchesForAtBat(atBatId: Long): List<Pitch>

    @Insert
    abstract fun insertPitch(pitch: Pitch): Long

    @Query("SELECT COALESCE(MAX(sequence_nr), 0) + 1 FROM pitches WHERE at_bat_id = :atBatId")
    abstract fun getNextSequenceNr(atBatId: Long): Int

    @Transaction
    open fun undoLastPitch(atBatId: Long) {
        getLastPitchId(atBatId)?.let { deletePitchById(it) }
    }

    @Query("SELECT id FROM pitches WHERE at_bat_id = :atBatId ORDER BY sequence_nr DESC LIMIT 1")
    abstract fun getLastPitchId(atBatId: Long): Long?

    @Query("DELETE FROM pitches WHERE id = :pitchId")
    abstract fun deletePitchById(pitchId: Long)

    @Query("DELETE FROM pitches WHERE at_bat_id = :atBatId")
    abstract fun deletePitchesForAtBat(atBatId: Long)

    @Query("""
        SELECT COUNT(*) FROM at_bats 
        WHERE game_id = :gameId AND inning = :inning 
          AND result IN ('H', '1B', '2B', '3B', 'HR', 'BB', 'HBP', 'FC', 'E', 'ROE')
    """)
    abstract fun getRunnersReachedBase(gameId: Long, inning: Int): Int

    @Query("SELECT COUNT(*) FROM at_bats WHERE game_id = :gameId AND result IN ('K','KL','GO','FO','LO','SAC','DP','OUT')")
    abstract fun getOutsForGame(gameId: Long): Int

    @Query("""
        SELECT ab.player_id,
            pl.name                                                                                 AS player_name,
            pl.number                                                                               AS player_number,
            COUNT(*)                                                                                AS pa,
            SUM(CASE WHEN ab.result NOT IN ('BB','HBP','SAC') THEN 1 ELSE 0 END)                   AS ab,
            SUM(CASE WHEN ab.result IN ('H','1B','2B','3B','HR') THEN 1 ELSE 0 END)                AS hits,
            SUM(CASE WHEN ab.result = '2B'             THEN 1 ELSE 0 END)                          AS doubles,
            SUM(CASE WHEN ab.result = '3B'             THEN 1 ELSE 0 END)                          AS triples,
            SUM(CASE WHEN ab.result = 'HR'             THEN 1 ELSE 0 END)                          AS homers,
            SUM(CASE WHEN ab.result = 'BB'             THEN 1 ELSE 0 END)                          AS walks,
            SUM(CASE WHEN ab.result IN ('K','KL')      THEN 1 ELSE 0 END)                          AS strikeouts,
            SUM(CASE WHEN ab.result = 'HBP'            THEN 1 ELSE 0 END)                          AS hbp
        FROM at_bats ab
        LEFT JOIN players pl ON pl.id = ab.player_id
        WHERE ab.game_id = :gameId AND ab.result IS NOT NULL AND ab.player_id != 0
        GROUP BY ab.player_id
    """)
    abstract fun getGameBatterStats(gameId: Long): List<GameBatterStatsRow>

    @Query("""
        SELECT ab.player_id AS player_id,
            pl.name                                                                                    AS player_name,
            pl.number                                                                                  AS player_number,
            COUNT(*)                                                                                    AS pa,
            SUM(CASE WHEN ab.result NOT IN ('BB','HBP','SAC') THEN 1 ELSE 0 END)                       AS ab,
            SUM(CASE WHEN ab.result IN ('H','1B','2B','3B','HR') THEN 1 ELSE 0 END)                    AS hits,
            SUM(CASE WHEN ab.result = '2B'              THEN 1 ELSE 0 END)                             AS doubles,
            SUM(CASE WHEN ab.result = '3B'              THEN 1 ELSE 0 END)                             AS triples,
            SUM(CASE WHEN ab.result = 'HR'              THEN 1 ELSE 0 END)                             AS homers,
            SUM(CASE WHEN ab.result = 'BB'              THEN 1 ELSE 0 END)                             AS walks,
            SUM(CASE WHEN ab.result IN ('K','KL')       THEN 1 ELSE 0 END)                             AS strikeouts,
            SUM(CASE WHEN ab.result = 'HBP'             THEN 1 ELSE 0 END)                             AS hbp
        FROM at_bats ab
        JOIN games g ON g.id = ab.game_id
        LEFT JOIN players pl ON pl.id = ab.player_id
        WHERE g.team_id = :teamId AND ab.result IS NOT NULL AND ab.player_id != 0
          AND (:startDate IS NULL OR :startDate = '' OR (substr(g.date,7,4)||substr(g.date,4,2)||substr(g.date,1,2)) >= :startDate)
          AND (:endDate IS NULL OR :endDate = '' OR (substr(g.date,7,4)||substr(g.date,4,2)||substr(g.date,1,2)) <= :endDate)
        GROUP BY ab.player_id
    """)
    abstract fun getSeasonBatterStats(teamId: Long, startDate: String? = null, endDate: String? = null): List<SeasonBatterRow>
}
