package de.baseball.diamond9.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.baseball.diamond9.*

@Database(
    entities = [
        Game::class,
        Pitcher::class,
        Pitch::class,
        AtBat::class,
        Team::class,
        TeamPosition::class,
        Player::class,
        PitcherAppearance::class,
        LineupEntry::class,
        BenchPlayer::class,
        OwnLineupSlot::class,
        Substitution::class,
        OppSubstitution::class,
        OpponentTeam::class,
        ScoreboardRun::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun pitcherDao(): PitcherDao
    abstract fun atBatDao(): AtBatDao
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun lineupDao(): LineupDao
    abstract fun opponentTeamDao(): OpponentTeamDao
    abstract fun scoreboardDao(): ScoreboardDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS opponent_teams " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL UNIQUE)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN inning INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE games ADD COLUMN outs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pitches ADD COLUMN inning INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE games ADD COLUMN leadoff_slot INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pitches ADD COLUMN at_bat_id INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS at_bats " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "game_id INTEGER NOT NULL, " +
                    "player_id INTEGER NOT NULL, " +
                    "slot INTEGER NOT NULL, " +
                    "inning INTEGER NOT NULL, " +
                    "result TEXT)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS scoreboard_runs " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "game_id INTEGER NOT NULL, " +
                    "inning INTEGER NOT NULL, " +
                    "is_home INTEGER NOT NULL, " +
                    "runs INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN start_time INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate opponent_teams with team_id column.
                // SQLite cannot drop/change a UNIQUE constraint via ALTER TABLE,
                // so we use the rename-copy-drop pattern.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS opponent_teams_new " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "team_id INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_opponent_teams_name_team_id " +
                    "ON opponent_teams_new (name, team_id)"
                )
                db.execSQL(
                    "INSERT INTO opponent_teams_new (id, name, team_id) " +
                    "SELECT id, name, 0 FROM opponent_teams"
                )
                db.execSQL("DROP TABLE opponent_teams")
                db.execSQL("ALTER TABLE opponent_teams_new RENAME TO opponent_teams")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pitcher.db"
                )
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
