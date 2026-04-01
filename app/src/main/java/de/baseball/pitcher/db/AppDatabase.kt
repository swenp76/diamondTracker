package de.baseball.pitcher.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import de.baseball.pitcher.*

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
        OppSubstitution::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao
    abstract fun pitcherDao(): PitcherDao
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun lineupDao(): LineupDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pitcher.db"
                )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
