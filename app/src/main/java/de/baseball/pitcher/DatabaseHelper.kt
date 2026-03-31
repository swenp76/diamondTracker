package de.baseball.pitcher

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Game(val id: Long = 0, val date: String, val opponent: String)
data class Pitcher(val id: Long = 0, val gameId: Long, val name: String)
data class Pitch(val id: Long = 0, val pitcherId: Long, val type: String, val sequenceNr: Int)
// type: "B" = Ball, "S" = Strike, "BF" = Batter Faced

data class PitcherStats(
    val pitcher: Pitcher,
    val bf: Int,
    val balls: Int,
    val strikes: Int,
    val totalPitches: Int,
    val pitches: List<Pitch>
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "pitcher.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE games (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                opponent TEXT NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE pitchers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                game_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                FOREIGN KEY(game_id) REFERENCES games(id)
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS pitches")
        db.execSQL("DROP TABLE IF EXISTS pitchers")
        db.execSQL("DROP TABLE IF EXISTS games")
        onCreate(db)
    }

    // --- Games ---
    fun insertGame(date: String, opponent: String): Long {
        val cv = ContentValues().apply {
            put("date", date)
            put("opponent", opponent)
        }
        return writableDatabase.insert("games", null, cv)
    }

    fun getAllGames(): List<Game> {
        val list = mutableListOf<Game>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM games ORDER BY id DESC", null)
        while (cursor.moveToNext()) {
            list.add(Game(cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
        }
        cursor.close()
        return list
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
    fun insertPitcher(gameId: Long, name: String): Long {
        val cv = ContentValues().apply {
            put("game_id", gameId)
            put("name", name)
        }
        return writableDatabase.insert("pitchers", null, cv)
    }

    fun getPitchersForGame(gameId: Long): List<Pitcher> {
        val list = mutableListOf<Pitcher>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM pitchers WHERE game_id=? ORDER BY id ASC",
            arrayOf(gameId.toString())
        )
        while (cursor.moveToNext()) {
            list.add(Pitcher(cursor.getLong(0), cursor.getLong(1), cursor.getString(2)))
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
}
