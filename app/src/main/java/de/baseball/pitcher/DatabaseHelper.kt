package de.baseball.pitcher

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Game(val id: Long = 0, val date: String, val opponent: String, val teamId: Long = 0)
data class Pitcher(val id: Long = 0, val gameId: Long, val name: String, val playerId: Long = 0)
data class Pitch(val id: Long = 0, val pitcherId: Long, val type: String, val sequenceNr: Int)
// type: "B" = Ball, "S" = Strike, "BF" = Batter Faced

data class Team(val id: Long = 0, val name: String)

data class Player(
    val id: Long = 0,
    val teamId: Long,
    val name: String,
    val number: String,
    val primaryPosition: Int,
    val secondaryPosition: Int = 0,
    val isPitcher: Boolean = false,
    val birthYear: Int = 0
)

data class PitcherAppearance(
    val id: Long = 0,
    val playerId: Long,
    val gameId: Long,
    val date: String,       // Format: YYYY-MM-DD
    val battersFaced: Int
)

object BaseballPositions {
    val ALL = listOf(
        1 to "1 – Pitcher",
        2 to "2 – Catcher",
        3 to "3 – First Base",
        4 to "4 – Second Base",
        5 to "5 – Third Base",
        6 to "6 – Shortstop",
        7 to "7 – Left Field",
        8 to "8 – Center Field",
        9 to "9 – Right Field",
        10 to "DH – Designated Hitter"
    )
    fun label(pos: Int) = ALL.firstOrNull { it.first == pos }?.second ?: "?"
    fun shortLabel(pos: Int) = when(pos) {
        1 -> "P"; 2 -> "C"; 3 -> "1B"; 4 -> "2B"; 5 -> "3B"
        6 -> "SS"; 7 -> "LF"; 8 -> "CF"; 9 -> "RF"; 10 -> "DH"
        else -> "?"
    }
}

data class PitcherStats(
    val pitcher: Pitcher,
    val bf: Int,
    val balls: Int,
    val strikes: Int,
    val totalPitches: Int,
    val pitches: List<Pitch>
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "pitcher.db", null, 7) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE games (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                opponent TEXT NOT NULL,
                team_id INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(team_id) REFERENCES teams(id)
            )
        """)
        db.execSQL("""
            CREATE TABLE pitchers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                game_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                player_id INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(game_id) REFERENCES games(id),
                FOREIGN KEY(player_id) REFERENCES players(id)
            )
        """)
        db.execSQL("""
            CREATE TABLE pitches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pitcher_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                sequence_nr INTEGER NOT NULL,
                FOREIGN KEY(pitcher_id) REFERENCES pitchers(id)
            )
        """)
        db.execSQL("""
            CREATE TABLE teams (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE team_positions (
                team_id INTEGER NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(team_id, position),
                FOREIGN KEY(team_id) REFERENCES teams(id)
            )
        """)
        db.execSQL("""
            CREATE TABLE players (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                team_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                number TEXT NOT NULL DEFAULT '',
                primary_position INTEGER NOT NULL DEFAULT 0,
                secondary_position INTEGER NOT NULL DEFAULT 0,
                is_pitcher INTEGER NOT NULL DEFAULT 0,
                birth_year INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(team_id) REFERENCES teams(id)
            )
        """)
        db.execSQL("""
            CREATE TABLE pitcher_appearances (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_id INTEGER NOT NULL,
                game_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                batters_faced INTEGER NOT NULL DEFAULT 0,
                UNIQUE(player_id, game_id),
                FOREIGN KEY(player_id) REFERENCES players(id),
                FOREIGN KEY(game_id) REFERENCES games(id)
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE players ADD COLUMN secondary_position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE players ADD COLUMN is_pitcher INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE pitchers ADD COLUMN player_id INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE games ADD COLUMN team_id INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE players ADD COLUMN birth_year INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                CREATE TABLE pitcher_appearances (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id INTEGER NOT NULL,
                    game_id INTEGER NOT NULL,
                    date TEXT NOT NULL,
                    batters_faced INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(player_id, game_id),
                    FOREIGN KEY(player_id) REFERENCES players(id),
                    FOREIGN KEY(game_id) REFERENCES games(id)
                )
            """)
        }
    }

    // --- Games ---
    fun insertGame(date: String, opponent: String, teamId: Long): Long {
        val cv = ContentValues().apply {
            put("date", date)
            put("opponent", opponent)
            put("team_id", teamId)
        }
        return writableDatabase.insert("games", null, cv)
    }

    fun getAllGames(): List<Game> {
        val list = mutableListOf<Game>()
        val cursor = readableDatabase.rawQuery("SELECT id, date, opponent, team_id FROM games ORDER BY id DESC", null)
        while (cursor.moveToNext()) {
            list.add(Game(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)))
        }
        cursor.close()
        return list
    }

    fun getGame(gameId: Long): Game? {
        val c = readableDatabase.rawQuery(
            "SELECT id, date, opponent, team_id FROM games WHERE id=?", arrayOf(gameId.toString())
        )
        val game = if (c.moveToFirst()) Game(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)) else null
        c.close()
        return game
    }

    fun deleteGame(gameId: Long) {
        val db = writableDatabase
        val pitcherIds = mutableListOf<Long>()
        val c = db.rawQuery("SELECT id FROM pitchers WHERE game_id=?", arrayOf(gameId.toString()))
        while (c.moveToNext()) pitcherIds.add(c.getLong(0))
        c.close()
        pitcherIds.forEach { db.delete("pitches", "pitcher_id=?", arrayOf(it.toString())) }
        db.delete("pitchers", "game_id=?", arrayOf(gameId.toString()))
        db.delete("games", "id=?", arrayOf(gameId.toString()))
    }

    // --- Pitchers ---
    fun insertPitcher(gameId: Long, name: String, playerId: Long = 0): Long {
        val cv = ContentValues().apply {
            put("game_id", gameId)
            put("name", name)
            put("player_id", playerId)
        }
        return writableDatabase.insert("pitchers", null, cv)
    }

    fun getPitchersForGame(gameId: Long): List<Pitcher> {
        val list = mutableListOf<Pitcher>()
        // Wenn player_id gesetzt: aktuellen Namen aus players holen (Name-Änderungen werden übernommen)
        val cursor = readableDatabase.rawQuery("""
            SELECT p.id, p.game_id,
                   CASE WHEN p.player_id > 0 THEN pl.name ELSE p.name END AS display_name,
                   p.player_id
            FROM pitchers p
            LEFT JOIN players pl ON pl.id = p.player_id
            WHERE p.game_id = ?
            ORDER BY p.id ASC
        """.trimIndent(), arrayOf(gameId.toString()))
        while (cursor.moveToNext()) {
            list.add(Pitcher(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getLong(3)))
        }
        cursor.close()
        return list
    }

    fun deletePitcher(pitcherId: Long) {
        writableDatabase.delete("pitches", "pitcher_id=?", arrayOf(pitcherId.toString()))
        writableDatabase.delete("pitchers", "id=?", arrayOf(pitcherId.toString()))
    }

    // --- Pitches ---
    fun insertPitch(pitcherId: Long, type: String): Long {
        val next = getNextSequenceNr(pitcherId)
        val cv = ContentValues().apply {
            put("pitcher_id", pitcherId)
            put("type", type)
            put("sequence_nr", next)
        }
        return writableDatabase.insert("pitches", null, cv)
    }

    fun undoLastPitch(pitcherId: Long) {
        val cursor = readableDatabase.rawQuery(
            "SELECT id FROM pitches WHERE pitcher_id=? ORDER BY sequence_nr DESC LIMIT 1",
            arrayOf(pitcherId.toString())
        )
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            writableDatabase.delete("pitches", "id=?", arrayOf(id.toString()))
        }
        cursor.close()
    }

    fun getPitchesForPitcher(pitcherId: Long): List<Pitch> {
        val list = mutableListOf<Pitch>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM pitches WHERE pitcher_id=? ORDER BY sequence_nr ASC",
            arrayOf(pitcherId.toString())
        )
        while (cursor.moveToNext()) {
            list.add(Pitch(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getInt(3)))
        }
        cursor.close()
        return list
    }

    fun getStatsForPitcher(pitcherId: Long): PitcherStats {
        val pitcher = getPitcherById(pitcherId)
        val pitches = getPitchesForPitcher(pitcherId)
        return PitcherStats(
            pitcher = pitcher,
            bf = pitches.count { it.type == "BF" },
            balls = pitches.count { it.type == "B" },
            strikes = pitches.count { it.type == "S" },
            totalPitches = pitches.count { it.type == "B" || it.type == "S" },
            pitches = pitches
        )
    }

    private fun getPitcherById(pitcherId: Long): Pitcher {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM pitchers WHERE id=?", arrayOf(pitcherId.toString())
        )
        cursor.moveToFirst()
        val p = Pitcher(cursor.getLong(0), cursor.getLong(1), cursor.getString(2))
        cursor.close()
        return p
    }

    private fun getNextSequenceNr(pitcherId: Long): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT MAX(sequence_nr) FROM pitches WHERE pitcher_id=?",
            arrayOf(pitcherId.toString())
        )
        val max = if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0) else 0
        cursor.close()
        return max + 1
    }

    // --- Teams ---
    fun insertTeam(name: String): Long {
        val cv = ContentValues().apply { put("name", name) }
        val id = writableDatabase.insert("teams", null, cv)
        (1..9).forEach { pos -> setPositionEnabled(id, pos, true) }
        return id
    }

    fun getAllTeams(): List<Team> {
        val list = mutableListOf<Team>()
        val c = readableDatabase.rawQuery("SELECT id, name FROM teams ORDER BY name ASC", null)
        while (c.moveToNext()) list.add(Team(c.getLong(0), c.getString(1)))
        c.close()
        return list
    }

    fun updateTeamName(teamId: Long, name: String) {
        val cv = ContentValues().apply { put("name", name) }
        writableDatabase.update("teams", cv, "id=?", arrayOf(teamId.toString()))
    }

    fun deleteTeam(teamId: Long) {
        val db = writableDatabase
        db.delete("players", "team_id=?", arrayOf(teamId.toString()))
        db.delete("team_positions", "team_id=?", arrayOf(teamId.toString()))
        db.delete("teams", "id=?", arrayOf(teamId.toString()))
    }

    // --- Positions ---
    fun getEnabledPositions(teamId: Long): Set<Int> {
        val set = mutableSetOf<Int>()
        val c = readableDatabase.rawQuery(
            "SELECT position FROM team_positions WHERE team_id=?", arrayOf(teamId.toString())
        )
        while (c.moveToNext()) set.add(c.getInt(0))
        c.close()
        return set
    }

    fun setPositionEnabled(teamId: Long, position: Int, enabled: Boolean) {
        if (enabled) {
            val cv = ContentValues().apply {
                put("team_id", teamId)
                put("position", position)
            }
            writableDatabase.insertWithOnConflict("team_positions", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            writableDatabase.delete("team_positions",
                "team_id=? AND position=?",
                arrayOf(teamId.toString(), position.toString()))
        }
    }

    // --- Players ---
    fun insertPlayer(teamId: Long, name: String, number: String, primaryPosition: Int, secondaryPosition: Int = 0, isPitcher: Boolean = false, birthYear: Int = 0): Long {
        val cv = ContentValues().apply {
            put("team_id", teamId)
            put("name", name)
            put("number", number)
            put("primary_position", primaryPosition)
            put("secondary_position", secondaryPosition)
            put("is_pitcher", if (isPitcher) 1 else 0)
            put("birth_year", birthYear)
        }
        return writableDatabase.insert("players", null, cv)
    }

    fun getPlayersForTeam(teamId: Long): List<Player> {
        val list = mutableListOf<Player>()
        val c = readableDatabase.rawQuery(
            "SELECT id, team_id, name, number, primary_position, secondary_position, is_pitcher, birth_year FROM players WHERE team_id=? ORDER BY number ASC, name ASC",
            arrayOf(teamId.toString())
        )
        while (c.moveToNext()) {
            list.add(Player(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getInt(4), c.getInt(5), c.getInt(6) == 1, c.getInt(7)))
        }
        c.close()
        return list
    }

    fun updatePlayer(player: Player) {
        val cv = ContentValues().apply {
            put("name", player.name)
            put("number", player.number)
            put("primary_position", player.primaryPosition)
            put("secondary_position", player.secondaryPosition)
            put("is_pitcher", if (player.isPitcher) 1 else 0)
            put("birth_year", player.birthYear)
        }
        writableDatabase.update("players", cv, "id=?", arrayOf(player.id.toString()))
    }

    fun deletePlayer(playerId: Long) {
        writableDatabase.delete("players", "id=?", arrayOf(playerId.toString()))
    }

    // --- Pitcher Appearances ---

    // Speichert oder aktualisiert einen Pitcheinsatz (ein Spieler kann pro Spiel nur einmal pitchen)
    fun savePitcherAppearance(playerId: Long, gameId: Long, date: String, bf: Int): Long {
        val cv = ContentValues().apply {
            put("player_id", playerId)
            put("game_id", gameId)
            put("date", date)
            put("batters_faced", bf)
        }
        return writableDatabase.insertWithOnConflict(
            "pitcher_appearances", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getPitcherAppearances(playerId: Long): List<PitcherAppearance> {
        val list = mutableListOf<PitcherAppearance>()
        val c = readableDatabase.rawQuery(
            "SELECT id, player_id, game_id, date, batters_faced FROM pitcher_appearances WHERE player_id=? ORDER BY date ASC",
            arrayOf(playerId.toString())
        )
        while (c.moveToNext()) {
            list.add(PitcherAppearance(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.getInt(4)))
        }
        c.close()
        return list
    }

    // BF eines Spielers an einem bestimmten Datum – summiert über alle Spiele des Tages
    fun getTotalBFForDate(playerId: Long, date: String): Int {
        val c = readableDatabase.rawQuery(
            "SELECT SUM(batters_faced) FROM pitcher_appearances WHERE player_id=? AND date=?",
            arrayOf(playerId.toString(), date)
        )
        val total = if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else 0
        c.close()
        return total
    }
}
