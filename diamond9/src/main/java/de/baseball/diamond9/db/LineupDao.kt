package de.baseball.diamond9.db

import androidx.room.*
import de.baseball.diamond9.*

@Entity(tableName = "own_lineup", primaryKeys = ["game_id", "slot"])
data class OwnLineupSlot(
    @ColumnInfo(name = "game_id") val gameId: Long,
    val slot: Int,
    @ColumnInfo(name = "player_id") val playerId: Long
)

data class OwnLineupRow(
    val slot: Int,
    @ColumnInfo(name = "player_id") val playerId: Long,
    @ColumnInfo(name = "team_id") val teamId: Long,
    val name: String,
    val number: String,
    @ColumnInfo(name = "primary_position") val primaryPosition: Int,
    @ColumnInfo(name = "secondary_position") val secondaryPosition: Int,
    @ColumnInfo(name = "is_pitcher") val isPitcher: Boolean,
    @ColumnInfo(name = "birth_year") val birthYear: Int
)

@Dao
interface LineupDao {

    // ── Pitcher Appearances ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun savePitcherAppearance(appearance: PitcherAppearance): Long

    @Query("SELECT * FROM pitcher_appearances WHERE player_id = :playerId ORDER BY date ASC")
    fun getPitcherAppearances(playerId: Long): List<PitcherAppearance>

    @Query("SELECT SUM(batters_faced) FROM pitcher_appearances WHERE player_id = :playerId AND date = :date")
    fun getTotalBFForDate(playerId: Long, date: String): Int?

    // ── Opponent Lineup ───────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertLineupEntry(entry: LineupEntry)

    @Query("SELECT * FROM opponent_lineup WHERE game_id = :gameId ORDER BY batting_order ASC")
    fun getLineup(gameId: Long): List<LineupEntry>

    @Query("SELECT jersey_number FROM opponent_lineup WHERE game_id = :gameId AND batting_order = :battingOrder")
    fun getJerseyAtBattingOrder(gameId: Long, battingOrder: Int): String?

    @Query("DELETE FROM opponent_lineup WHERE game_id = :gameId AND batting_order = :battingOrder")
    fun deleteLineupEntry(gameId: Long, battingOrder: Int)

    // ── Opponent Bench ────────────────────────────────────────────────────────

    @Insert
    fun insertBenchPlayer(player: BenchPlayer): Long

    @Query("SELECT * FROM opponent_bench WHERE game_id = :gameId ORDER BY id ASC")
    fun getBenchPlayers(gameId: Long): List<BenchPlayer>

    @Query("DELETE FROM opponent_bench WHERE id = :id")
    fun deleteBenchPlayer(id: Long)

    // ── Opponent Substitutions ────────────────────────────────────────────────

    @Insert
    fun insertOppSubstitution(sub: OppSubstitution): Long

    @Query("SELECT * FROM opponent_substitutions WHERE game_id = :gameId ORDER BY id ASC")
    fun getOpponentSubstitutionsForGame(gameId: Long): List<OppSubstitution>

    // ── Own Lineup ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setOwnLineupPlayer(slot: OwnLineupSlot)

    @Query("DELETE FROM own_lineup WHERE game_id = :gameId AND slot = :slot")
    fun clearOwnLineupSlot(gameId: Long, slot: Int)

    @Query("DELETE FROM own_lineup WHERE game_id = :gameId")
    fun clearOwnLineup(gameId: Long)

    @Query("DELETE FROM opponent_lineup WHERE game_id = :gameId")
    fun clearOpponentLineup(gameId: Long)

    @Query("DELETE FROM opponent_bench WHERE game_id = :gameId")
    fun clearOpponentBench(gameId: Long)

    @Query("DELETE FROM substitutions WHERE game_id = :gameId")
    fun clearSubstitutions(gameId: Long)

    @Query("DELETE FROM opponent_substitutions WHERE game_id = :gameId")
    fun clearOpponentSubstitutions(gameId: Long)

    @Query("""
        SELECT ol.slot, pl.id AS player_id, pl.team_id, pl.name, pl.number,
               pl.primary_position, pl.secondary_position, pl.is_pitcher, pl.birth_year
        FROM own_lineup ol
        JOIN players pl ON pl.id = ol.player_id
        WHERE ol.game_id = :gameId
        ORDER BY ol.slot ASC
    """)
    fun getOwnLineupRaw(gameId: Long): List<OwnLineupRow>

    // ── Own Substitutions ─────────────────────────────────────────────────────

    @Insert
    fun insertSubstitution(sub: Substitution): Long

    @Query("SELECT * FROM substitutions WHERE game_id = :gameId ORDER BY id ASC")
    fun getSubstitutionsForGame(gameId: Long): List<Substitution>
}
