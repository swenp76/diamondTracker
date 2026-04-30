# Room and Database Entities
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-keep class de.baseball.diamond9.db.** { *; }

# Keep all entities and data classes used in the database
-keep class de.baseball.diamond9.Game { *; }
-keep class de.baseball.diamond9.Pitcher { *; }
-keep class de.baseball.diamond9.Pitch { *; }
-keep class de.baseball.diamond9.AtBat { *; }
-keep class de.baseball.diamond9.Team { *; }
-keep class de.baseball.diamond9.Player { *; }
-keep class de.baseball.diamond9.GameRunner { *; }
-keep class de.baseball.diamond9.ScoreboardRun { *; }
-keep class de.baseball.diamond9.LeagueSettings { *; }

# Keep data classes used for query results and state
-keep class de.baseball.diamond9.GameBatterStatsRow { *; }
-keep class de.baseball.diamond9.SeasonBatterRow { *; }
-keep class de.baseball.diamond9.SeasonPitcherRow { *; }
-keep class de.baseball.diamond9.PitcherStats { *; }
-keep class de.baseball.diamond9.HalfInningState { *; }

# Keep game logic and state objects
-keep class de.baseball.diamond9.RunnerManager { *; }
-keep class de.baseball.diamond9.HalfInningManager { *; }
-keep class de.baseball.diamond9.GameAction** { *; }
-keep class de.baseball.diamond9.GameActionStack { *; }

# Keep Material 3 components that might use reflection
-keep class androidx.compose.material3.** { *; }

# Preserve resource IDs to prevent mismatched mapping in App Bundles
-keepclassmembers class **.R$* {
    public static <fields>;
}
-keep class **.R { *; }

# Prevent shrinking of strings that might be accessed by ID
-keepclassmembers class de.baseball.diamond9.R$string {
    public static <fields>;
}
