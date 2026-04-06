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
        Team::class,
        TeamPosition::class,
        Player::class,
        PitcherAppearance::class,
        LineupEntry::class,
        BenchPlayer::class,
        OwnLineupSlot::class,
        Substitution::class,
        OppSubstitution::class,
        OpponentTeam::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun pitcherDao(): PitcherDao
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun lineupDao(): LineupDao
    abstract fun opponentTeamDao(): OpponentTeamDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pitcher.db"
                )
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
