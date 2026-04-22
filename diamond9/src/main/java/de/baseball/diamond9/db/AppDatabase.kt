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
        ScoreboardRun::class,
        LeagueSettings::class
    ],
    version = 18,
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
    abstract fun leagueSettingsDao(): LeagueSettingsDao

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

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN game_time TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN is_home INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN elapsed_time_ms INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN current_inning INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE games ADD COLUMN is_top_half INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS league_settings " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "team_id INTEGER NOT NULL UNIQUE, " +
                    "innings INTEGER NOT NULL DEFAULT 9, " +
                    "time_limit_minutes INTEGER)"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN game_number TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate table to make 'name' NOT NULL with DEFAULT ''
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pitchers_new " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "game_id INTEGER NOT NULL, " +
                    "name TEXT NOT NULL DEFAULT '', " +
                    "player_id INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "INSERT INTO pitchers_new (id, game_id, name, player_id) " +
                    "SELECT id, game_id, IFNULL(name, ''), player_id FROM pitchers"
                )
                db.execSQL("DROP TABLE pitchers")
                db.execSQL("ALTER TABLE pitchers_new RENAME TO pitchers")
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

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate pitches table to add DEFAULT '' to 'type' column
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pitches_new " +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "pitcher_id INTEGER NOT NULL, " +
                    "at_bat_id INTEGER NOT NULL, " +
                    "type TEXT NOT NULL DEFAULT '', " +
                    "sequence_nr INTEGER NOT NULL, " +
                    "inning INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL(
                    "INSERT INTO pitches_new (id, pitcher_id, at_bat_id, type, sequence_nr, inning) " +
                    "SELECT id, pitcher_id, at_bat_id, IFNULL(type, ''), sequence_nr, inning FROM pitches"
                )
                db.execSQL("DROP TABLE pitches")
                db.execSQL("ALTER TABLE pitches_new RENAME TO pitches")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix 'teams'
                db.execSQL("CREATE TABLE IF NOT EXISTS teams_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL DEFAULT '')")
                db.execSQL("INSERT INTO teams_new (id, name) SELECT id, IFNULL(name, '') FROM teams")
                db.execSQL("DROP TABLE teams")
                db.execSQL("ALTER TABLE teams_new RENAME TO teams")

                // Fix 'players'
                db.execSQL("CREATE TABLE IF NOT EXISTS players_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, team_id INTEGER NOT NULL, name TEXT NOT NULL DEFAULT '', number TEXT NOT NULL DEFAULT '', primary_position INTEGER NOT NULL, secondary_position INTEGER NOT NULL, is_pitcher INTEGER NOT NULL, birth_year INTEGER NOT NULL)")
                db.execSQL("INSERT INTO players_new (id, team_id, name, number, primary_position, secondary_position, is_pitcher, birth_year) SELECT id, team_id, IFNULL(name, ''), IFNULL(number, ''), primary_position, secondary_position, is_pitcher, birth_year FROM players")
                db.execSQL("DROP TABLE players")
                db.execSQL("ALTER TABLE players_new RENAME TO players")

                // Fix 'opponent_teams'
                db.execSQL("CREATE TABLE IF NOT EXISTS opponent_teams_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL DEFAULT '', team_id INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO opponent_teams_new (id, name, team_id) SELECT id, IFNULL(name, ''), team_id FROM opponent_teams")
                db.execSQL("DROP TABLE opponent_teams")
                db.execSQL("ALTER TABLE opponent_teams_new RENAME TO opponent_teams")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_opponent_teams_name_team_id ON opponent_teams (name, team_id)")

                // Fix 'pitcher_appearances'
                db.execSQL("CREATE TABLE IF NOT EXISTS pitcher_appearances_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, player_id INTEGER NOT NULL, game_id INTEGER NOT NULL, date TEXT NOT NULL DEFAULT '', batters_faced INTEGER NOT NULL)")
                db.execSQL("INSERT INTO pitcher_appearances_new (id, player_id, game_id, date, batters_faced) SELECT id, player_id, game_id, IFNULL(date, ''), batters_faced FROM pitcher_appearances")
                db.execSQL("DROP TABLE pitcher_appearances")
                db.execSQL("ALTER TABLE pitcher_appearances_new RENAME TO pitcher_appearances")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pitcher_appearances_player_id_game_id ON pitcher_appearances (player_id, game_id)")

                // Fix 'opponent_lineup'
                db.execSQL("CREATE TABLE IF NOT EXISTS opponent_lineup_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, game_id INTEGER NOT NULL, batting_order INTEGER NOT NULL, jersey_number TEXT NOT NULL DEFAULT '')")
                db.execSQL("INSERT INTO opponent_lineup_new (id, game_id, batting_order, jersey_number) SELECT id, game_id, batting_order, IFNULL(jersey_number, '') FROM opponent_lineup")
                db.execSQL("DROP TABLE opponent_lineup")
                db.execSQL("ALTER TABLE opponent_lineup_new RENAME TO opponent_lineup")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_opponent_lineup_game_id_batting_order ON opponent_lineup (game_id, batting_order)")

                // Fix 'opponent_bench'
                db.execSQL("CREATE TABLE IF NOT EXISTS opponent_bench_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, game_id INTEGER NOT NULL, jersey_number TEXT NOT NULL DEFAULT '')")
                db.execSQL("INSERT INTO opponent_bench_new (id, game_id, jersey_number) SELECT id, game_id, IFNULL(jersey_number, '') FROM opponent_bench")
                db.execSQL("DROP TABLE opponent_bench")
                db.execSQL("ALTER TABLE opponent_bench_new RENAME TO opponent_bench")

                // Fix 'opponent_substitutions'
                db.execSQL("CREATE TABLE IF NOT EXISTS opponent_substitutions_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, game_id INTEGER NOT NULL, slot INTEGER NOT NULL, jersey_out TEXT NOT NULL DEFAULT '', jersey_in TEXT NOT NULL DEFAULT '')")
                db.execSQL("INSERT INTO opponent_substitutions_new (id, game_id, slot, jersey_out, jersey_in) SELECT id, game_id, slot, IFNULL(jersey_out, ''), IFNULL(jersey_in, '') FROM opponent_substitutions")
                db.execSQL("DROP TABLE opponent_substitutions")
                db.execSQL("ALTER TABLE opponent_substitutions_new RENAME TO opponent_substitutions")

                // Fix 'league_settings'
                db.execSQL("CREATE TABLE IF NOT EXISTS league_settings_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, team_id INTEGER NOT NULL, innings INTEGER NOT NULL DEFAULT 9, time_limit_minutes INTEGER)")
                db.execSQL("INSERT INTO league_settings_new (id, team_id, innings, time_limit_minutes) SELECT id, team_id, innings, time_limit_minutes FROM league_settings")
                db.execSQL("DROP TABLE league_settings")
                db.execSQL("ALTER TABLE league_settings_new RENAME TO league_settings")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_league_settings_team_id ON league_settings (team_id)")

                // Fix 'scoreboard_runs'
                db.execSQL("CREATE TABLE IF NOT EXISTS scoreboard_runs_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, game_id INTEGER NOT NULL, inning INTEGER NOT NULL, is_home INTEGER NOT NULL, runs INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO scoreboard_runs_new (id, game_id, inning, is_home, runs) SELECT id, game_id, inning, is_home, runs FROM scoreboard_runs")
                db.execSQL("DROP TABLE scoreboard_runs")
                db.execSQL("ALTER TABLE scoreboard_runs_new RENAME TO scoreboard_runs")

                // Fix 'games'
                db.execSQL("CREATE TABLE IF NOT EXISTS games_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, opponent TEXT NOT NULL, team_id INTEGER NOT NULL, inning INTEGER NOT NULL DEFAULT 1, outs INTEGER NOT NULL DEFAULT 0, leadoff_slot INTEGER NOT NULL DEFAULT 1, start_time INTEGER NOT NULL DEFAULT 0, elapsed_time_ms INTEGER NOT NULL DEFAULT 0, game_time TEXT NOT NULL DEFAULT '', is_home INTEGER NOT NULL DEFAULT 1, current_inning INTEGER NOT NULL DEFAULT 1, is_top_half INTEGER NOT NULL DEFAULT 1, game_number TEXT NOT NULL DEFAULT '')")
                db.execSQL("INSERT INTO games_new (id, date, opponent, team_id, inning, outs, leadoff_slot, start_time, elapsed_time_ms, game_time, is_home, current_inning, is_top_half, game_number) SELECT id, date, opponent, team_id, inning, outs, leadoff_slot, start_time, elapsed_time_ms, game_time, is_home, current_inning, is_top_half, game_number FROM games")
                db.execSQL("DROP TABLE games")
                db.execSQL("ALTER TABLE games_new RENAME TO games")

                // Fix 'at_bats'
                db.execSQL("CREATE TABLE IF NOT EXISTS at_bats_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, game_id INTEGER NOT NULL, player_id INTEGER NOT NULL, slot INTEGER NOT NULL, inning INTEGER NOT NULL, result TEXT)")
                db.execSQL("INSERT INTO at_bats_new (id, game_id, player_id, slot, inning, result) SELECT id, game_id, player_id, slot, inning, result FROM at_bats")
                db.execSQL("DROP TABLE at_bats")
                db.execSQL("ALTER TABLE at_bats_new RENAME TO at_bats")

                // Fix 'pitchers'
                db.execSQL("CREATE TABLE IF NOT EXISTS pitchers_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, game_id INTEGER NOT NULL, name TEXT NOT NULL DEFAULT '', player_id INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO pitchers_new (id, game_id, name, player_id) SELECT id, game_id, IFNULL(name, ''), player_id FROM pitchers")
                db.execSQL("DROP TABLE pitchers")
                db.execSQL("ALTER TABLE pitchers_new RENAME TO pitchers")

                // Fix 'pitches'
                db.execSQL("CREATE TABLE IF NOT EXISTS pitches_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pitcher_id INTEGER NOT NULL, at_bat_id INTEGER NOT NULL, type TEXT NOT NULL DEFAULT '', sequence_nr INTEGER NOT NULL, inning INTEGER NOT NULL DEFAULT 1)")
                db.execSQL("INSERT INTO pitches_new (id, pitcher_id, at_bat_id, type, sequence_nr, inning) SELECT id, pitcher_id, at_bat_id, IFNULL(type, ''), sequence_nr, inning FROM pitches")
                db.execSQL("DROP TABLE pitches")
                db.execSQL("ALTER TABLE pitches_new RENAME TO pitches")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 0. Orphan Cleanup: Delete orphaned records to ensure FK constraints aren't violated
                db.execSQL("DELETE FROM at_bats WHERE game_id NOT IN (SELECT id FROM games)")
                db.execSQL("DELETE FROM pitchers WHERE game_id NOT IN (SELECT id FROM games)")
                db.execSQL("DELETE FROM pitches WHERE pitcher_id NOT IN (SELECT id FROM pitchers) OR at_bat_id NOT IN (SELECT id FROM at_bats) AND at_bat_id > 0")

                // 1. at_bats with Foreign Key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS at_bats_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        game_id INTEGER NOT NULL, 
                        player_id INTEGER NOT NULL, 
                        slot INTEGER NOT NULL, 
                        inning INTEGER NOT NULL, 
                        result TEXT,
                        FOREIGN KEY(game_id) REFERENCES games(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO at_bats_new SELECT * FROM at_bats")
                db.execSQL("DROP TABLE at_bats")
                db.execSQL("ALTER TABLE at_bats_new RENAME TO at_bats")

                // 2. pitchers with Foreign Key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pitchers_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        game_id INTEGER NOT NULL, 
                        name TEXT NOT NULL DEFAULT '', 
                        player_id INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(game_id) REFERENCES games(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO pitchers_new SELECT * FROM pitchers")
                db.execSQL("DROP TABLE pitchers")
                db.execSQL("ALTER TABLE pitchers_new RENAME TO pitchers")

                // 3. pitches with Foreign Keys (at_bat_id and pitcher_id)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pitches_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        pitcher_id INTEGER NOT NULL, 
                        at_bat_id INTEGER NOT NULL, 
                        type TEXT NOT NULL DEFAULT '', 
                        sequence_nr INTEGER NOT NULL, 
                        inning INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(pitcher_id) REFERENCES pitchers(id) ON DELETE CASCADE,
                        FOREIGN KEY(at_bat_id) REFERENCES at_bats(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO pitches_new SELECT * FROM pitches")
                db.execSQL("DROP TABLE pitches")
                db.execSQL("ALTER TABLE pitches_new RENAME TO pitches")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
