package de.baseball.diamond9

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.baseball.diamond9.db.OwnLineupSlot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Handles backup and restore of all app data as JSON.
 *
 * DB version history:
 *  5 → initial backup support (teams, players, games, pitchers, pitches,
 *      at_bats, lineups, substitutions, opponent_teams)
 *  6 → added scoreboard_runs table
 *  7 → added start_time column to games table
 *  8 → opponent_teams now scoped per team (team_id column added)
 *  9 → game_time column added to games
 *  10 → is_home column added to games (1 = home, 0 = away)
 *  11 → elapsed_time_ms column added to games
 *  12 → current_inning and is_top_half columns added to games
 *  13 → league_settings table introduced
 *  14 → game_number column added to games
 *  15 → pitchers.name backfilled (null → '')
 *  16 → pitches.type backfilled (null → '')
 *  17 → pitches.at_bat_id backfilled (null → 0)
 *  18 → added foreign key constraints with CASCADE
 *  19 → pitches table: pitcher_id and at_bat_id made nullable
 *  20 → added game_runners table
 *  21 → added is_locked column to games
 *
 * Restore logic applies incremental migrations when importing older backups.
 */
class BackupManager constructor(
    private val context: Context,
    private val db: DatabaseHelper
) {
    constructor(context: Context) : this(context, DatabaseHelper(context))

    companion object {
        const val DB_VERSION = 21

        /** Maximum file size accepted for any import (5 MB). */
        const val MAX_IMPORT_BYTES = 5L * 1024 * 1024

        /**
         * Writes [content] to a temp file in the cache dir and fires an
         * ACTION_SEND share chooser so the user can send it via WhatsApp etc.
         */
        fun shareJson(context: Context, fileName: String, content: String) {
            val safeName = fileName.replace("/", "_").replace("\\", "_")
            val file = File(context.cacheDir, safeName)
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        /** Valid pitch type strings stored in the database. */
        val VALID_PITCH_TYPES = setOf(
            // Core pitch types
            "B",    // Ball
            "S",    // Strike
            "F",    // Foul
            "BF",   // Batter Faced (marker)
            "RO",   // Runner Out (marker)
            // Outcomes recorded as pitches (defense)
            "SO",   // Strikeout pitch
            "H",    // Hit (generic, legacy)
            "1B",   // Single
            "2B",   // Double
            "3B",   // Triple
            "HR",   // Home Run
            "HBP",  // Hit by Pitch
            "W",    // Walk
            "GO",   // Ground Out
            "FO",   // Fly Out
            "LO",   // Line Out
            "KL"    // Strikeout Looking
        )

        /** Valid at-bat result strings stored in the database. */
        val VALID_AT_BAT_RESULTS = setOf(
            "K", "KL", "GO", "FO", "LO", "BB", "H", "HBP", "SAC", "FC", "E", "DP", "OUT",
            "1B", "2B", "3B", "HR",
            "ROE"
        )
    }

    // ── Export ──────────────────────────────────────────────────────────────────

    fun exportToJson(): JSONObject {
        val root = JSONObject()
        root.put("dbVersion", DB_VERSION)
        root.put("exportDate", System.currentTimeMillis())

        // teams
        root.put("teams", JSONArray(db.getAllTeams().map { t ->
            JSONObject().apply {
                put("id", t.id)
                put("name", t.name ?: "")
            }
        }))

        // games
        root.put("games", JSONArray(db.getAllGames().map { g ->
            JSONObject().apply {
                put("id", g.id)
                put("date", g.date ?: "")
                put("opponent", g.opponent ?: "")
                put("team_id", g.teamId)
                put("inning", g.inning)
                put("outs", g.outs)
                put("leadoff_slot", g.leadoffSlot)
                put("start_time", g.startTime)
                put("elapsed_time_ms", g.elapsedTimeMs)
                put("game_time", g.gameTime ?: "")
                put("is_home", g.isHome)
                put("current_inning", g.currentInning)
                put("is_top_half", g.isTopHalf)
                put("game_number", g.gameNumber ?: "")
                put("is_locked", g.isLocked)
            }
        }))

        // scoreboard_runs (added in DB version 6)
        val allScoreboardRuns = JSONArray()
        db.getAllGames().forEach { game ->
            db.getScoreboard(game.id).forEach { run ->
                allScoreboardRuns.put(JSONObject().apply {
                    put("id", run.id)
                    put("game_id", run.gameId)
                    put("inning", run.inning)
                    put("is_home", run.isHome)
                    put("runs", run.runs)
                })
            }
        }
        root.put("scoreboard_runs", allScoreboardRuns)

        // opponent_teams (scoped per team since DB version 8)
        val allOpponentTeams = JSONArray()
        db.getAllTeams().forEach { team ->
            db.getOpponentTeamsForTeam(team.id).forEach { opp ->
                allOpponentTeams.put(JSONObject().apply {
                    put("id", opp.id)
                    put("name", opp.name ?: "")
                    put("team_id", opp.teamId)
                })
            }
        }
        root.put("opponent_teams", allOpponentTeams)

        // league_settings (added in DB version 13)
        val allLeagueSettings = JSONArray()
        db.getAllTeams().forEach { team ->
            val ls = db.getLeagueSettings(team.id)
            allLeagueSettings.put(JSONObject().apply {
                put("team_id", ls.teamId)
                put("innings", ls.innings)
                if (ls.timeLimitMinutes != null) put("time_limit_minutes", ls.timeLimitMinutes)
                else put("time_limit_minutes", JSONObject.NULL)
            })
        }
        root.put("league_settings", allLeagueSettings)

        // team_positions
        val allTeamPositions = JSONArray()
        db.getAllTeams().forEach { team ->
            db.getEnabledPositions(team.id).forEach { pos ->
                allTeamPositions.put(JSONObject().apply {
                    put("team_id", team.id)
                    put("position", pos)
                })
            }
        }
        root.put("team_positions", allTeamPositions)

        // players
        val allPlayers = JSONArray()
        db.getAllTeams().forEach { team ->
            db.getPlayersForTeam(team.id).forEach { p ->
                allPlayers.put(JSONObject().apply {
                    put("id", p.id)
                    put("team_id", p.teamId)
                    put("name", p.name ?: "")
                    put("number", p.number ?: "")
                    put("primary_position", p.primaryPosition)
                    put("secondary_position", p.secondaryPosition)
                    put("is_pitcher", if (p.isPitcher) 1 else 0)
                    put("birth_year", p.birthYear)
                })
            }
        }
        root.put("players", allPlayers)

        // pitchers + pitches
        val allPitchers = JSONArray()
        val allPitches = JSONArray()
        db.getAllGames().forEach { game ->
            db.getPitchersForGame(game.id).forEach { pitcher ->
                allPitchers.put(JSONObject().apply {
                    put("id", pitcher.id)
                    put("game_id", pitcher.gameId)
                    put("name", pitcher.name.ifEmpty { "" })
                    put("player_id", pitcher.playerId)
                })
                db.getPitchesForPitcher(pitcher.id).forEach { pitch ->
                    allPitches.put(JSONObject().apply {
                        put("id", pitch.id)
                        put("pitcher_id", pitch.pitcherId)
                put("at_bat_id", if (pitch.atBatId == 0L) JSONObject.NULL else pitch.atBatId)
                        put("type", pitch.type ?: "")
                        put("sequence_nr", pitch.sequenceNr)
                        put("inning", pitch.inning)
                    })
                }
            }
        }
        root.put("pitchers", allPitchers)
        root.put("pitches", allPitches)

        // at_bats
        val allAtBats = JSONArray()
        db.getAllGames().forEach { game ->
            db.getAtBatsForGame(game.id).forEach { ab ->
                allAtBats.put(JSONObject().apply {
                    put("id", ab.id)
                    put("game_id", ab.gameId)
                    put("player_id", ab.playerId)
                    put("slot", ab.slot)
                    put("inning", ab.inning)
                    if (ab.result != null) put("result", ab.result) else put("result", JSONObject.NULL)
                })
            }
        }
        root.put("at_bats", allAtBats)

        // own_lineup
        val allOwnLineup = JSONArray()
        db.getAllGames().forEach { game ->
            db.getOwnLineup(game.id).forEach { (slot, player) ->
                allOwnLineup.put(JSONObject().apply {
                    put("game_id", game.id)
                    put("slot", slot)
                    put("player_id", player.id)
                })
            }
        }
        root.put("own_lineup", allOwnLineup)

        // substitutions (own)
        val allSubstitutions = JSONArray()
        db.getAllGames().forEach { game ->
            db.getSubstitutionsForGame(game.id).forEach { sub ->
                allSubstitutions.put(JSONObject().apply {
                    put("id", sub.id)
                    put("game_id", sub.gameId)
                    put("slot", sub.slot)
                    put("player_out_id", sub.playerOutId)
                    put("player_in_id", sub.playerInId)
                })
            }
        }
        root.put("substitutions", allSubstitutions)

        // opponent_lineup
        val allOpponentLineup = JSONArray()
        db.getAllGames().forEach { game ->
            db.getLineup(game.id).forEach { entry ->
                allOpponentLineup.put(JSONObject().apply {
                    put("game_id", entry.gameId)
                    put("batting_order", entry.battingOrder)
                    put("jersey_number", entry.jerseyNumber ?: "")
                })
            }
        }
        root.put("opponent_lineup", allOpponentLineup)

        // opponent_bench
        val allOpponentBench = JSONArray()
        db.getAllGames().forEach { game ->
            db.getBenchPlayers(game.id).forEach { bench ->
                allOpponentBench.put(JSONObject().apply {
                    put("id", bench.id)
                    put("game_id", bench.gameId)
                    put("jersey_number", bench.jerseyNumber ?: "")
                })
            }
        }
        root.put("opponent_bench", allOpponentBench)

        // opponent_substitutions
        val allOpponentSubs = JSONArray()
        db.getAllGames().forEach { game ->
            db.getOpponentSubstitutionsForGame(game.id).forEach { sub ->
                allOpponentSubs.put(JSONObject().apply {
                    put("id", sub.id)
                    put("game_id", sub.gameId)
                    put("slot", sub.slot)
                    put("jersey_out", sub.jerseyOut ?: "")
                    put("jersey_in", sub.jerseyIn ?: "")
                })
            }
        }
        root.put("opponent_substitutions", allOpponentSubs)

        // pitcher_appearances
        val allAppearances = JSONArray()
        db.getAllTeams().forEach { team ->
            db.getPlayersForTeam(team.id).forEach { player ->
                db.getPitcherAppearances(player.id).forEach { app ->
                    allAppearances.put(JSONObject().apply {
                        put("id", app.id)
                        put("player_id", app.playerId)
                        put("game_id", app.gameId)
                        put("date", app.date ?: "")
                        put("batters_faced", app.battersFaced)
                    })
                }
            }
        }
        root.put("pitcher_appearances", allAppearances)

        // game_runners (added in DB version 20)
        val allRunners = JSONArray()
        db.getAllGames().forEach { game ->
            db.getRunners(game.id).forEach { runner ->
                allRunners.put(JSONObject().apply {
                    put("id", runner.id)
                    put("game_id", runner.gameId)
                    put("base", runner.base)
                    put("player_id", runner.playerId)
                    put("slot", runner.slot)
                    put("jersey_number", runner.jerseyNumber ?: JSONObject.NULL)
                    put("name", runner.name)
                })
            }
        }
        root.put("game_runners", allRunners)

        return root
    }

    // ── Restore ─────────────────────────────────────────────────────────────────

    /**
     * Restores data from a JSON backup.
     * Applies incremental schema migrations for older backup versions.
     */
    fun restoreFromJson(json: JSONObject) {
        val type = json.optString("type", "")
        if (type.isNotEmpty()) {
            throw IllegalArgumentException("Not a full backup file (type: \"$type\"). Use the game/team import instead.")
        }
        if (!json.has("dbVersion") || !json.has("teams")) {
            throw IllegalArgumentException("Not a valid diamond9 backup file.")
        }
        val backupVersion = json.optInt("dbVersion", 1)
        if (backupVersion > DB_VERSION) {
            throw IllegalArgumentException(
                "Backup version $backupVersion is newer than this app (v$DB_VERSION). Please update the app first."
            )
        }
        val migrated = applyBackupMigrations(json, fromVersion = backupVersion, toVersion = DB_VERSION)

        db.purgeAllData()

        db.runInTransaction {
            restoreTeams(migrated)
            restorePlayers(migrated)
            restoreGames(migrated)
            restoreTeamPositions(migrated)
            restorePitchers(migrated)
            restoreAtBats(migrated)
            restorePitches(migrated)
            restoreOwnLineup(migrated)
            restoreSubstitutions(migrated)
            restoreOpponentLineup(migrated)
            restoreOpponentBench(migrated)
            restoreOpponentSubstitutions(migrated)
            restoreScoreboardRuns(migrated)
            restorePitcherAppearances(migrated)
            restoreOpponentTeams(migrated)
            restoreLeagueSettings(migrated)
            restoreGameRunners(migrated)
        }
    }

    private fun restoreTeams(json: JSONObject) {
        val arr = json.optJSONArray("teams") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("teams", ContentValues().apply {
                put("id", obj.getLong("id"))
                put("name", obj.getString("name").take(50))
            })
        }
    }

    private fun restoreTeamPositions(json: JSONObject) {
        val arr = json.optJSONArray("team_positions") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.setPositionEnabled(obj.getLong("team_id"), obj.getInt("position"), true)
        }
    }

    private fun restorePlayers(json: JSONObject) {
        val arr = json.optJSONArray("players") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("players", ContentValues().apply {
                put("id", obj.getLong("id"))
                put("team_id", obj.getLong("team_id"))
                put("name", obj.getString("name").take(50))
                put("number", obj.optString("number", "").take(3))
                put("primary_position", obj.optInt("primary_position", 0))
                put("secondary_position", obj.optInt("secondary_position", 0))
                put("is_pitcher", obj.optInt("is_pitcher", 0))
                put("birth_year", obj.optInt("birth_year", 0))
            })
        }
    }

    private fun restoreGames(json: JSONObject) {
        val arr = json.optJSONArray("games") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("games", ContentValues().apply {
                put("id", obj.getLong("id"))
                put("date", obj.getString("date").take(10))
                put("opponent", obj.getString("opponent").take(50))
                put("team_id", obj.optLong("team_id", 0L))
                put("inning", obj.optInt("inning", 1))
                put("outs", obj.optInt("outs", 0))
                put("leadoff_slot", obj.optInt("leadoff_slot", 1))
                put("start_time", obj.optLong("start_time", 0L))
                put("elapsed_time_ms", obj.optLong("elapsed_time_ms", 0L))
                put("game_time", obj.optString("game_time", "").take(5))
                put("is_home", obj.optInt("is_home", 1))
                put("current_inning", obj.optInt("current_inning", 1))
                put("is_top_half", obj.optInt("is_top_half", 1))
                put("game_number", obj.optString("game_number", "").take(20))
                put("is_locked", obj.optInt("is_locked", 0))
            })
        }
    }

    private fun restorePitchers(json: JSONObject) {
        val arr = json.optJSONArray("pitchers") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("pitchers", ContentValues().apply {
                put("id", obj.getLong("id"))
                put("game_id", obj.getLong("game_id"))
                put("name", obj.getString("name").take(50))
                put("player_id", obj.getLong("player_id"))
            })
        }
    }

    private fun restorePitches(json: JSONObject) {
        val arr = json.optJSONArray("pitches") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("pitches", ContentValues().apply {
                put("id", obj.getLong("id"))
                
                val pid = obj.optLong("pitcher_id", 0L)
                if (obj.isNull("pitcher_id") || pid == 0L) putNull("pitcher_id")
                else put("pitcher_id", pid)

                val abid = obj.optLong("at_bat_id", 0L)
                if (obj.isNull("at_bat_id") || abid == 0L) putNull("at_bat_id")
                else put("at_bat_id", abid)

                put("type", obj.getString("type"))
                put("sequence_nr", obj.getInt("sequence_nr"))
                put("inning", obj.getInt("inning"))
            })
        }
    }

    private fun restoreAtBats(json: JSONObject) {
        val arr = json.optJSONArray("at_bats") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("at_bats", ContentValues().apply {
                put("id", obj.getLong("id"))
                put("game_id", obj.getLong("game_id"))
                put("player_id", obj.getLong("player_id"))
                put("slot", obj.getInt("slot"))
                put("inning", obj.getInt("inning"))
                if (obj.isNull("result")) putNull("result")
                else put("result", obj.getString("result"))
            })
        }
    }

    private fun restoreOwnLineup(json: JSONObject) {
        val arr = json.optJSONArray("own_lineup") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.setOwnLineupPlayer(obj.getLong("game_id"), obj.getInt("slot"), obj.getLong("player_id"))
        }
    }

    private fun restoreSubstitutions(json: JSONObject) {
        val arr = json.optJSONArray("substitutions") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("substitutions", ContentValues().apply {
                put("id", obj.getLong("id"))
                put("game_id", obj.getLong("game_id"))
                put("slot", obj.getInt("slot"))
                put("player_out_id", obj.getLong("player_out_id"))
                put("player_in_id", obj.getLong("player_in_id"))
            })
        }
    }

    private fun restoreOpponentLineup(json: JSONObject) {
        val arr = json.optJSONArray("opponent_lineup") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.upsertLineupEntry(obj.getLong("game_id"), obj.getInt("batting_order"), obj.getString("jersey_number"))
        }
    }

    private fun restoreOpponentBench(json: JSONObject) {
        val arr = json.optJSONArray("opponent_bench") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.insertBenchPlayer(obj.getLong("game_id"), obj.getString("jersey_number"))
        }
    }

    private fun restoreOpponentSubstitutions(json: JSONObject) {
        val arr = json.optJSONArray("opponent_substitutions") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("opponent_substitutions", ContentValues().apply {
                put("id", obj.getLong("id"))
                put("game_id", obj.getLong("game_id"))
                put("slot", obj.getInt("slot"))
                put("jersey_out", obj.getString("jersey_out"))
                put("jersey_in", obj.getString("jersey_in"))
            })
        }
    }

    private fun restorePitcherAppearances(json: JSONObject) {
        val arr = json.optJSONArray("pitcher_appearances") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.rawInsertWithConflictIgnore("pitcher_appearances", ContentValues().apply {
                put("id", obj.getLong("id"))
                put("player_id", obj.getLong("player_id"))
                put("game_id", obj.getLong("game_id"))
                put("date", obj.getString("date"))
                put("batters_faced", obj.getInt("batters_faced"))
            })
        }
    }

    /**
     * Applies incremental backup migrations from [fromVersion] up to [toVersion].
     * Each step can add default values or restructure JSON before the restore.
     */
    private fun applyBackupMigrations(json: JSONObject, fromVersion: Int, toVersion: Int): JSONObject {
        var current = json
        var v = fromVersion

        // Migration 5 → 6: scoreboard_runs table introduced – no data migration needed,
        // older backups simply have no scoreboard_runs array.
        if (v < 6 && toVersion >= 6) {
            if (!current.has("scoreboard_runs")) {
                current.put("scoreboard_runs", JSONArray())
            }
            v = 6
        }

        // Migration 6 → 7: start_time added to games – default 0 for older backups.
        if (v < 7 && toVersion >= 7) {
            val games = current.optJSONArray("games")
            if (games != null) {
                for (i in 0 until games.length()) {
                    val g = games.getJSONObject(i)
                    if (!g.has("start_time")) g.put("start_time", 0L)
                }
            }
            v = 7
        }

        // Migration 7 → 8: opponent_teams now have a team_id column – default 0 for older backups.
        if (v < 8 && toVersion >= 8) {
            val opps = current.optJSONArray("opponent_teams")
            if (opps != null) {
                for (i in 0 until opps.length()) {
                    val o = opps.getJSONObject(i)
                    if (!o.has("team_id")) o.put("team_id", 0L)
                }
            }
            v = 8
        }

        // Migration 8 → 9: game_time added to games – default "" for older backups.
        if (v < 9 && toVersion >= 9) {
            val games = current.optJSONArray("games")
            if (games != null) {
                for (i in 0 until games.length()) {
                    val g = games.getJSONObject(i)
                    if (!g.has("game_time")) g.put("game_time", "")
                }
            }
            v = 9
        }

        // Migration 9 → 10: is_home added to games – default 1 (home) for older backups.
        if (v < 10 && toVersion >= 10) {
            val games = current.optJSONArray("games")
            if (games != null) {
                for (i in 0 until games.length()) {
                    val g = games.getJSONObject(i)
                    if (!g.has("is_home")) g.put("is_home", 1)
                }
            }
            v = 10
        }

        // Migration 10 → 11: elapsed_time_ms added to games – default 0 for older backups.
        if (v < 11 && toVersion >= 11) {
            val games = current.optJSONArray("games")
            if (games != null) {
                for (i in 0 until games.length()) {
                    val g = games.getJSONObject(i)
                    if (!g.has("elapsed_time_ms")) g.put("elapsed_time_ms", 0L)
                }
            }
            v = 11
        }

        // Migration 11 → 12: current_inning and is_top_half added to games – defaults 1 for older backups.
        if (v < 12 && toVersion >= 12) {
            val games = current.optJSONArray("games")
            if (games != null) {
                for (i in 0 until games.length()) {
                    val g = games.getJSONObject(i)
                    if (!g.has("current_inning")) g.put("current_inning", 1)
                    if (!g.has("is_top_half")) g.put("is_top_half", 1)
                }
            }
            v = 12
        }

        // Migration 12 → 13: league_settings table introduced – older backups have no league_settings array.
        if (v < 13 && toVersion >= 13) {
            if (!current.has("league_settings")) {
                current.put("league_settings", JSONArray())
            }
            v = 13
        }

        // Migration 13 → 14: game_number added to games – default "" for older backups.
        if (v < 14 && toVersion >= 14) {
            val games = current.optJSONArray("games")
            if (games != null) {
                for (i in 0 until games.length()) {
                    val g = games.getJSONObject(i)
                    if (!g.has("game_number")) g.put("game_number", "")
                }
            }
            v = 14
        }

        // Migration 14 → 15: pitcher name backfill – null names default to empty string.
        if (v < 15 && toVersion >= 15) {
            val pitchers = current.optJSONArray("pitchers")
            if (pitchers != null) {
                for (i in 0 until pitchers.length()) {
                    val p = pitchers.getJSONObject(i)
                    if (!p.has("name") || p.isNull("name")) {
                        p.put("name", "")
                    }
                }
            }
            v = 15
        }

        // Migration 15 → 16: pitch type NOT NULL backfill.
        if (v < 16 && toVersion >= 16) {
            val pitches = current.optJSONArray("pitches")
            if (pitches != null) {
                for (i in 0 until pitches.length()) {
                    val p = pitches.getJSONObject(i)
                    if (!p.has("type") || p.isNull("type")) {
                        p.put("type", "")
                    }
                }
            }
            v = 16
        }

        // Migration 16 → 17: at_bat_id NOT NULL backfill.
        if (v < 17 && toVersion >= 17) {
            val pitches = current.optJSONArray("pitches")
            if (pitches != null) {
                for (i in 0 until pitches.length()) {
                    val p = pitches.getJSONObject(i)
                    if (p.isNull("at_bat_id")) {
                        p.put("at_bat_id", 0L)
                    }
                }
            }
            v = 17
        }

        // Migration 17 → 18: Foreign Key CASCADE – no JSON structure change needed.
        if (v < 18 && toVersion >= 18) {
            v = 18
        }

        // Migration 18 → 19: Nullable pitch columns – backfill 0 to null.
        if (v < 19 && toVersion >= 19) {
            val pitches = current.optJSONArray("pitches")
            if (pitches != null) {
                for (i in 0 until pitches.length()) {
                    val p = pitches.getJSONObject(i)
                    if (p.has("pitcher_id") && p.optLong("pitcher_id") == 0L) {
                        p.put("pitcher_id", JSONObject.NULL)
                    }
                    if (p.has("at_bat_id") && p.optLong("at_bat_id") == 0L) {
                        p.put("at_bat_id", JSONObject.NULL)
                    }
                }
            }
            v = 19
        }

        // Migration 19 → 20: game_runners table introduced.
        if (v < 20 && toVersion >= 20) {
            if (!current.has("game_runners")) {
                current.put("game_runners", JSONArray())
            }
            v = 20
        }

        // Migration 20 → 21: is_locked column added to games.
        if (v < 21 && toVersion >= 21) {
            val games = current.optJSONArray("games")
            if (games != null) {
                for (i in 0 until games.length()) {
                    val g = games.getJSONObject(i)
                    if (!g.has("is_locked")) g.put("is_locked", 0)
                }
            }
            v = 21
        }

        return current
    }

    private fun restoreScoreboardRuns(json: JSONObject) {
        val arr = json.optJSONArray("scoreboard_runs") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.upsertScoreboardRun(
                gameId = obj.getLong("game_id"),
                inning = obj.getInt("inning"),
                isHome = obj.getInt("is_home"),
                runs = obj.getInt("runs")
            )
        }
    }

    private fun restoreOpponentTeams(json: JSONObject) {
        val arr = json.optJSONArray("opponent_teams") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.insertOpponentTeamForTeam(
                name = obj.getString("name"),
                teamId = obj.optLong("team_id", 0L)
            )
        }
    }

    private fun restoreLeagueSettings(json: JSONObject) {
        val arr = json.optJSONArray("league_settings") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.saveLeagueSettings(
                LeagueSettings(
                    teamId = obj.getLong("team_id"),
                    innings = obj.optInt("innings", 9),
                    timeLimitMinutes = if (obj.isNull("time_limit_minutes")) null
                                       else obj.optInt("time_limit_minutes")
                )
            )
        }
    }

    private fun restoreGameRunners(json: JSONObject) {
        val arr = json.optJSONArray("game_runners") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.insertRunner(
                GameRunner(
                    id = obj.optLong("id", 0L),
                    gameId = obj.getLong("game_id"),
                    base = obj.getInt("base"),
                    playerId = obj.optLong("player_id", 0L),
                    slot = obj.optInt("slot", 0),
                    jerseyNumber = if (obj.isNull("jersey_number")) null else obj.getString("jersey_number"),
                    name = obj.optString("name", "")
                )
            )
        }
    }

    // ── Single Game Export/Import ───────────────────────────────────────────────

    fun exportGame(gameId: Long): JSONObject {
        val game = db.getGame(gameId) ?: return JSONObject()
        val root = JSONObject()
        root.put("type", "single_game")
        root.put("dbVersion", DB_VERSION)

        // League settings
        val ls = db.getLeagueSettings(game.teamId)
        root.put("league_settings", JSONObject().apply {
            put("innings", ls.innings)
            put("time_limit_minutes", ls.timeLimitMinutes ?: JSONObject.NULL)
        })

        // Game metadata
        val gObj = JSONObject().apply {
            put("date", game.date ?: "")
            put("opponent", game.opponent ?: "")
            put("inning", game.inning)
            put("outs", game.outs)
            put("leadoff_slot", game.leadoffSlot)
            put("start_time", game.startTime)
            put("elapsed_time_ms", game.elapsedTimeMs)
            put("game_time", game.gameTime ?: "")
            put("is_home", game.isHome)
            put("current_inning", game.currentInning)
            put("is_top_half", game.isTopHalf)
            put("game_number", game.gameNumber ?: "")
            put("is_locked", game.isLocked)
        }
        root.put("game", gObj)

        // Scoreboard
        root.put("scoreboard", JSONArray(db.getScoreboard(gameId).map { r ->
            JSONObject().apply {
                put("inning", r.inning)
                put("is_home", r.isHome)
                put("runs", r.runs)
            }
        }))

        // Helper to get player info
        fun getPlayerInfo(playerId: Long): JSONObject? {
            val p = db.getPlayerById(playerId) ?: return null
            return JSONObject().apply {
                put("name", p.name)
                put("number", p.number)
            }
        }

        // Own Lineup
        val lineupArr = JSONArray()
        db.getOwnLineup(gameId).forEach { (slot, player) ->
            lineupArr.put(JSONObject().apply {
                put("slot", slot)
                put("player", JSONObject().apply {
                    put("name", player.name)
                    put("number", player.number)
                })
            })
        }
        root.put("own_lineup", lineupArr)

        // Own Substitutions
        root.put("substitutions", JSONArray(db.getSubstitutionsForGame(gameId).map { s ->
            JSONObject().apply {
                put("slot", s.slot)
                put("player_out", getPlayerInfo(s.playerOutId))
                put("player_in", getPlayerInfo(s.playerInId))
            }
        }))

        // At-Bats & their pitches
        val abArr = JSONArray()
        db.getAtBatsForGame(gameId).forEach { ab ->
            val abObj = JSONObject().apply {
                put("slot", ab.slot)
                put("inning", ab.inning)
                put("result", ab.result)
                put("player", getPlayerInfo(ab.playerId))
                put("pitches", JSONArray(db.getPitchesForAtBat(ab.id).map { p ->
                    JSONObject().apply {
                        put("type", p.type)
                        put("sequence_nr", p.sequenceNr)
                        put("inning", p.inning)
                    }
                }))
            }
            abArr.put(abObj)
        }
        root.put("at_bats", abArr)

        // Pitchers & their pitches
        val pitcherArr = JSONArray()
        db.getPitchersForGame(gameId).forEach { p ->
            val pObj = JSONObject().apply {
                put("name", p.name.ifEmpty { "" })
                put("player", getPlayerInfo(p.playerId))
                put("pitches", JSONArray(db.getPitchesForPitcher(p.id).map { pitch ->
                    JSONObject().apply {
                        put("type", pitch.type)
                        put("sequence_nr", pitch.sequenceNr)
                        put("inning", pitch.inning)
                    }
                }))
            }
            pitcherArr.put(pObj)
        }
        root.put("pitchers", pitcherArr)

        // Opponent
        root.put("opponent_lineup", JSONArray(db.getLineup(gameId).map { l ->
            JSONObject().apply {
                put("batting_order", l.battingOrder)
                put("jersey_number", l.jerseyNumber)
            }
        }))
        root.put("opponent_bench", JSONArray(db.getBenchPlayers(gameId).map { b ->
            JSONObject().apply { put("jersey_number", b.jerseyNumber) }
        }))
        root.put("opponent_substitutions", JSONArray(db.getOpponentSubstitutionsForGame(gameId).map { os ->
            JSONObject().apply {
                put("slot", os.slot)
                put("jersey_out", os.jerseyOut)
                put("jersey_in", os.jerseyIn)
            }
        }))

        return root
    }

    fun importGame(teamId: Long, json: JSONObject): Long {
        val gData = json.optJSONObject("game") ?: return -1
        val isHome = gData.optInt("is_home", 1)
        require(isHome in 0..1) { "Invalid is_home value: $isHome" }
        val gameId = db.insertGame(
            date = gData.getString("date").take(10),
            opponent = gData.getString("opponent").take(50),
            teamId = teamId,
            gameTime = gData.optString("game_time", "").take(5),
            isHome = isHome,
            gameNumber = gData.optString("game_number", "").take(20)
        )
        db.setGameLocked(gameId, gData.optInt("is_locked", 0) == 1)
        db.updateGameState(gameId, gData.optInt("inning", 1), gData.optInt("outs", 0))
        db.updateLeadoffSlot(gameId, gData.optInt("leadoff_slot", 1))
        db.setStartTime(gameId, gData.optLong("start_time", 0L))
        db.setElapsedTime(gameId, gData.optLong("elapsed_time_ms", 0L))

        // Players cache for this team
        val teamPlayers = db.getPlayersForTeam(teamId).toMutableList()
        fun findOrCreatePlayer(pObj: JSONObject?): Long {
            if (pObj == null) return 0L
            val name = pObj.getString("name").take(50)
            val number = pObj.getString("number").take(3)
            
            val potentialMatch = PlayerMatcher.findPotentialMatch(name, number, teamPlayers)
            if (potentialMatch != null) return potentialMatch.id
            
            val newId = db.insertPlayer(teamId, name, number, 1) // Default pos 1 (P)
            // Add to cache to avoid duplicates within the same import
            teamPlayers.add(Player(newId, teamId, name, number, 1, 0, false, 0))
            return newId
        }

        // Scoreboard
        val sbArr = json.optJSONArray("scoreboard")
        if (sbArr != null) {
            for (i in 0 until sbArr.length()) {
                val r = sbArr.getJSONObject(i)
                val inning = r.getInt("inning")
                val isHome = r.getInt("is_home")
                val runs = r.getInt("runs")
                require(inning in 1..20) { "Invalid scoreboard inning: $inning" }
                require(isHome in 0..1) { "Invalid is_home value: $isHome" }
                require(runs in 0..99) { "Invalid runs value: $runs" }
                db.upsertScoreboardRun(gameId, inning, isHome, runs)
            }
        }

        // Own Lineup
        val lineupArr = json.optJSONArray("own_lineup")
        if (lineupArr != null) {
            for (i in 0 until lineupArr.length()) {
                val entry = lineupArr.getJSONObject(i)
                val slot = entry.getInt("slot")
                require(slot in 1..20) { "Invalid lineup slot: $slot" }
                val pid = findOrCreatePlayer(entry.getJSONObject("player"))
                db.setOwnLineupPlayer(gameId, slot, pid)
            }
        }

        // Own Substitutions
        val subArr = json.optJSONArray("substitutions")
        if (subArr != null) {
            for (i in 0 until subArr.length()) {
                val s = subArr.getJSONObject(i)
                val slot = s.getInt("slot")
                require(slot in 1..20) { "Invalid substitution slot: $slot" }
                val pOut = findOrCreatePlayer(s.optJSONObject("player_out"))
                val pIn = findOrCreatePlayer(s.optJSONObject("player_in"))
                db.addSubstitution(gameId, slot, pOut, pIn)
            }
        }

        // At-Bats
        val abArr = json.optJSONArray("at_bats")
        if (abArr != null) {
            for (i in 0 until abArr.length()) {
                val abObj = abArr.getJSONObject(i)
                val abSlot = abObj.getInt("slot")
                val abInning = abObj.getInt("inning")
                require(abSlot in 1..20) { "Invalid at-bat slot: $abSlot" }
                require(abInning in 1..20) { "Invalid at-bat inning: $abInning" }
                val pid = findOrCreatePlayer(abObj.optJSONObject("player"))
                val abId = db.insertAtBat(gameId, pid, abSlot, abInning)
                val result = if (abObj.isNull("result")) null else abObj.getString("result")
                require(result == null || result in VALID_AT_BAT_RESULTS) { "Invalid at-bat result: $result" }
                db.updateAtBatResult(abId, result)
                val pArr = abObj.optJSONArray("pitches")
                if (pArr != null) {
                    for (j in 0 until pArr.length()) {
                        val p = pArr.getJSONObject(j)
                        val pitchType = p.getString("type")
                        val pitchInning = p.getInt("inning")
                        require(pitchType in VALID_PITCH_TYPES) { "Invalid pitch type: $pitchType" }
                        require(pitchInning in 1..20) { "Invalid pitch inning: $pitchInning" }
                        db.insertPitchForAtBat(abId, pitchType, pitchInning)
                    }
                }
            }
        }

        // Pitchers
        val pitcherArr = json.optJSONArray("pitchers")
        if (pitcherArr != null) {
            for (i in 0 until pitcherArr.length()) {
                val pObj = pitcherArr.getJSONObject(i)
                val pid = findOrCreatePlayer(pObj.optJSONObject("player"))
                val pitcherId = db.insertPitcher(gameId, pObj.getString("name").take(50), pid)
                val pArr = pObj.optJSONArray("pitches")
                if (pArr != null) {
                    for (j in 0 until pArr.length()) {
                        val p = pArr.getJSONObject(j)
                        val pitchType = p.getString("type")
                        val pitchInning = p.getInt("inning")
                        require(pitchType in VALID_PITCH_TYPES) { "Invalid pitch type: $pitchType" }
                        require(pitchInning in 1..20) { "Invalid pitch inning: $pitchInning" }
                        db.insertPitch(pitcherId, pitchType, pitchInning)
                    }
                }
            }
        }

        // Opponent
        val oppLArr = json.optJSONArray("opponent_lineup")
        if (oppLArr != null) {
            for (i in 0 until oppLArr.length()) {
                val l = oppLArr.getJSONObject(i)
                val order = l.getInt("batting_order")
                require(order in 1..10) { "Invalid batting order: $order" }
                db.upsertLineupEntry(gameId, order, l.getString("jersey_number").take(3))
            }
        }
        val oppBArr = json.optJSONArray("opponent_bench")
        if (oppBArr != null) {
            for (i in 0 until oppBArr.length()) {
                db.insertBenchPlayer(gameId, oppBArr.getJSONObject(i).getString("jersey_number").take(3))
            }
        }
        val oppSArr = json.optJSONArray("opponent_substitutions")
        if (oppSArr != null) {
            for (i in 0 until oppSArr.length()) {
                val os = oppSArr.getJSONObject(i)
                val slot = os.getInt("slot")
                require(slot in 1..10) { "Invalid opponent substitution slot: $slot" }
                db.addOpponentSubstitution(gameId, slot, os.getString("jersey_out").take(3), os.getString("jersey_in").take(3))
            }
        }

        return gameId
    }

    // ── Team Export / Import ─────────────────────────────────────────────────────

    /** Builds a JSON string for the given team (roster + positions). */
    fun exportTeam(teamId: Long, includeGames: Boolean = false): String {
        val team = db.getAllTeams().first { it.id == teamId }
        val root = JSONObject().apply {
            put("type", "team")
            put("version", 1)
            put("dbVersion", DB_VERSION)
            put("includeGames", includeGames)
            put("name", team.name)
            
            val posArray = JSONArray()
            db.getEnabledPositions(teamId).sorted().forEach { posArray.put(it) }
            put("positions", posArray)

            val ls = db.getLeagueSettings(teamId)
            put("league_settings", JSONObject().apply {
                put("innings", ls.innings)
                put("time_limit_minutes", ls.timeLimitMinutes ?: JSONObject.NULL)
            })

            val playersArray = JSONArray()
            val teamPlayers = db.getPlayersForTeam(teamId)
            teamPlayers.forEach { p ->
                playersArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("number", p.number)
                    put("primary_position", p.primaryPosition)
                    put("secondary_position", p.secondaryPosition)
                    put("is_pitcher", p.isPitcher)
                    put("birth_year", p.birthYear)
                })
            }
            put("players", playersArray)

            if (includeGames) {
                val games = db.getGamesForTeam(teamId)
                val gamesArray = JSONArray()
                val scoreboardArray = JSONArray()
                val pitchersArray = JSONArray()
                val pitchesArray = JSONArray()
                val atBatsArray = JSONArray()
                val ownLineupArray = JSONArray()
                val substitutionsArray = JSONArray()
                val oppLineupArray = JSONArray()
                val oppBenchArray = JSONArray()
                val oppSubsArray = JSONArray()
                val appearancesArray = JSONArray()
                val runnersArray = JSONArray()

                games.forEach { g ->
                    gamesArray.put(JSONObject().apply {
                        put("id", g.id)
                        put("date", g.date)
                        put("opponent", g.opponent)
                        put("team_id", g.teamId)
                        put("inning", g.inning)
                        put("outs", g.outs)
                        put("leadoff_slot", g.leadoffSlot)
                        put("start_time", g.startTime)
                        put("elapsed_time_ms", g.elapsedTimeMs)
                        put("game_time", g.gameTime)
                        put("is_home", g.isHome)
                        put("current_inning", g.currentInning)
                        put("is_top_half", g.isTopHalf)
                        put("game_number", g.gameNumber)
                        put("is_locked", g.isLocked)
                    })

                    db.getScoreboard(g.id).forEach { r ->
                        scoreboardArray.put(JSONObject().apply {
                            put("game_id", g.id)
                            put("inning", r.inning)
                            put("is_home", r.isHome)
                            put("runs", r.runs)
                        })
                    }

                    db.getPitchersForGame(g.id).forEach { p ->
                        pitchersArray.put(JSONObject().apply {
                            put("id", p.id)
                            put("game_id", p.gameId)
                            put("name", p.name)
                            put("player_id", p.playerId)
                        })
                        db.getPitchesForPitcher(p.id).forEach { pitch ->
                            pitchesArray.put(JSONObject().apply {
                                put("id", pitch.id)
                                put("pitcher_id", pitch.pitcherId)
                                put("at_bat_id", pitch.atBatId ?: JSONObject.NULL)
                                put("type", pitch.type)
                                put("sequence_nr", pitch.sequenceNr)
                                put("inning", pitch.inning)
                            })
                        }
                    }

                    db.getAtBatsForGame(g.id).forEach { ab ->
                        atBatsArray.put(JSONObject().apply {
                            put("id", ab.id)
                            put("game_id", ab.gameId)
                            put("player_id", ab.playerId)
                            put("slot", ab.slot)
                            put("inning", ab.inning)
                            put("result", ab.result ?: JSONObject.NULL)
                        })
                    }

                    db.getRunners(g.id).forEach { runner ->
                        runnersArray.put(JSONObject().apply {
                            put("game_id", g.id)
                            put("base", runner.base)
                            put("player_id", runner.playerId)
                            put("slot", runner.slot)
                            put("jersey_number", runner.jerseyNumber ?: JSONObject.NULL)
                            put("name", runner.name)
                        })
                    }

                    db.getOwnLineup(g.id).forEach { (slot, player) ->
                        ownLineupArray.put(JSONObject().apply {
                            put("game_id", g.id)
                            put("slot", slot)
                            put("player_id", player.id)
                        })
                    }

                    db.getSubstitutionsForGame(g.id).forEach { s ->
                        substitutionsArray.put(JSONObject().apply {
                            put("game_id", g.id)
                            put("slot", s.slot)
                            put("player_out_id", s.playerOutId)
                            put("player_in_id", s.playerInId)
                        })
                    }

                    db.getLineup(g.id).forEach { l ->
                        oppLineupArray.put(JSONObject().apply {
                            put("game_id", g.id)
                            put("batting_order", l.battingOrder)
                            put("jersey_number", l.jerseyNumber)
                        })
                    }

                    db.getBenchPlayers(g.id).forEach { b ->
                        oppBenchArray.put(JSONObject().apply {
                            put("game_id", g.id)
                            put("jersey_number", b.jerseyNumber)
                        })
                    }

                    db.getOpponentSubstitutionsForGame(g.id).forEach { os ->
                        oppSubsArray.put(JSONObject().apply {
                            put("game_id", g.id)
                            put("slot", os.slot)
                            put("jersey_out", os.jerseyOut)
                            put("jersey_in", os.jerseyIn)
                        })
                    }
                }

                teamPlayers.forEach { player ->
                    db.getPitcherAppearances(player.id).forEach { app ->
                        appearancesArray.put(JSONObject().apply {
                            put("player_id", app.playerId)
                            put("game_id", app.gameId)
                            put("date", app.date)
                            put("batters_faced", app.battersFaced)
                        })
                    }
                }

                put("games", gamesArray)
                put("scoreboard_runs", scoreboardArray)
                put("pitchers", pitchersArray)
                put("pitches", pitchesArray)
                put("at_bats", atBatsArray)
                put("own_lineup", ownLineupArray)
                put("substitutions", substitutionsArray)
                put("opponent_lineup", oppLineupArray)
                put("opponent_bench", oppBenchArray)
                put("opponent_substitutions", oppSubsArray)
                put("pitcher_appearances", appearancesArray)
                put("game_runners", runnersArray)

                val oppTeamsArray = JSONArray()
                db.getOpponentTeamsForTeam(teamId).forEach { opp ->
                    oppTeamsArray.put(JSONObject().apply {
                        put("name", opp.name)
                        put("team_id", teamId)
                    })
                }
                put("opponent_teams", oppTeamsArray)
            }
        }
        return root.toString(2)
    }

    fun importTeam(json: JSONObject) {
        val includeGames = json.optBoolean("includeGames", false)
        val teamId = db.insertTeam(json.getString("name").take(50))

        db.getEnabledPositions(teamId).forEach { db.setPositionEnabled(teamId, it, false) }
        val posArray = json.optJSONArray("positions")
        if (posArray != null) {
            for (p in 0 until posArray.length()) {
                db.setPositionEnabled(teamId, posArray.getInt(p), true)
            }
        }

        val lsObj = json.optJSONObject("league_settings")
        if (lsObj != null) {
            db.saveLeagueSettings(LeagueSettings(
                teamId = teamId,
                innings = lsObj.optInt("innings", 9),
                timeLimitMinutes = if (lsObj.isNull("time_limit_minutes")) null else lsObj.optInt("time_limit_minutes")
            ))
        }

        val playerMapping = mutableMapOf<Long, Long>()
        val playersArray = json.optJSONArray("players")
        if (playersArray != null) {
            for (p in 0 until playersArray.length()) {
                val pl = playersArray.getJSONObject(p)
                val oldId = pl.optLong("id", -1L)
                val newId = db.insertPlayer(
                    teamId,
                    pl.getString("name").take(50),
                    pl.optString("number", "").take(3),
                    pl.optInt("primary_position", 0),
                    pl.optInt("secondary_position", 0),
                    pl.optBoolean("is_pitcher", false),
                    pl.optInt("birth_year", 0)
                )
                if (oldId != -1L) playerMapping[oldId] = newId
            }
        }

        if (includeGames) {
            val gameMapping = mutableMapOf<Long, Long>()
            val gamesArr = json.optJSONArray("games")
            if (gamesArr != null) {
                for (i in 0 until gamesArr.length()) {
                    val obj = gamesArr.getJSONObject(i)
                    val oldId = obj.getLong("id")
                    val newId = db.insertGame(
                        date = obj.getString("date").take(10),
                        opponent = obj.getString("opponent").take(50),
                        teamId = teamId,
                        gameTime = obj.optString("game_time", "").take(5),
                        isHome = obj.optInt("is_home", 1),
                        gameNumber = obj.optString("game_number", "").take(20)
                    )
                    db.setGameLocked(newId, obj.optInt("is_locked", 0) == 1)
                    db.updateGameState(newId, obj.optInt("inning", 1), obj.optInt("outs", 0))
                    db.updateLeadoffSlot(newId, obj.optInt("leadoff_slot", 1))
                    db.setStartTime(newId, obj.optLong("start_time", 0L))
                    db.setElapsedTime(newId, obj.optLong("elapsed_time_ms", 0L))
                    db.updateHalfInning(newId, obj.optInt("current_inning", 1), obj.optInt("is_top_half", 1) == 1)
                    gameMapping[oldId] = newId
                }
            }

            val scoreboardArr = json.optJSONArray("scoreboard_runs")
            if (scoreboardArr != null) {
                for (i in 0 until scoreboardArr.length()) {
                    val obj = scoreboardArr.getJSONObject(i)
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    db.upsertScoreboardRun(gid, obj.getInt("inning"), obj.getInt("is_home"), obj.getInt("runs"))
                }
            }

            val pitcherMapping = mutableMapOf<Long, Long>()
            val pitchersArr = json.optJSONArray("pitchers")
            if (pitchersArr != null) {
                for (i in 0 until pitchersArr.length()) {
                    val obj = pitchersArr.getJSONObject(i)
                    val oldId = obj.getLong("id")
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    val pid = playerMapping[obj.getLong("player_id")] ?: 0L
                    val newId = db.insertPitcher(gid, obj.getString("name").take(50), pid)
                    pitcherMapping[oldId] = newId
                }
            }

            val atBatMapping = mutableMapOf<Long, Long>()
            val atBatsArr = json.optJSONArray("at_bats")
            if (atBatsArr != null) {
                for (i in 0 until atBatsArr.length()) {
                    val obj = atBatsArr.getJSONObject(i)
                    val oldId = obj.getLong("id")
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    val pid = playerMapping[obj.getLong("player_id")] ?: 0L
                    val newId = db.insertAtBat(gid, pid, obj.getInt("slot"), obj.getInt("inning"))
                    if (!obj.isNull("result")) db.updateAtBatResult(newId, obj.getString("result"))
                    atBatMapping[oldId] = newId
                }
            }

            val pitchesArr = json.optJSONArray("pitches")
            if (pitchesArr != null) {
                for (i in 0 until pitchesArr.length()) {
                    val obj = pitchesArr.getJSONObject(i)
                    val pitcherId = pitcherMapping[obj.getLong("pitcher_id")] ?: continue
                    val abId = if (obj.isNull("at_bat_id")) null else atBatMapping[obj.getLong("at_bat_id")]
                    
                    if (abId != null) {
                        db.insertPitchForAtBat(abId, obj.getString("type"), obj.getInt("inning"))
                    } else {
                        db.insertPitch(pitcherId, obj.getString("type"), obj.getInt("inning"))
                    }
                }
            }

            val ownLineupArr = json.optJSONArray("own_lineup")
            if (ownLineupArr != null) {
                for (i in 0 until ownLineupArr.length()) {
                    val obj = ownLineupArr.getJSONObject(i)
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    val pid = playerMapping[obj.getLong("player_id")] ?: continue
                    db.setOwnLineupPlayer(gid, obj.getInt("slot"), pid)
                }
            }

            val subsArr = json.optJSONArray("substitutions")
            if (subsArr != null) {
                for (i in 0 until subsArr.length()) {
                    val obj = subsArr.getJSONObject(i)
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    val pOut = playerMapping[obj.getLong("player_out_id")] ?: continue
                    val pIn = playerMapping[obj.getLong("player_in_id")] ?: continue
                    db.addSubstitution(gid, obj.getInt("slot"), pOut, pIn)
                }
            }

            val oppLineupArr = json.optJSONArray("opponent_lineup")
            if (oppLineupArr != null) {
                for (i in 0 until oppLineupArr.length()) {
                    val obj = oppLineupArr.getJSONObject(i)
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    db.upsertLineupEntry(gid, obj.getInt("batting_order"), obj.getString("jersey_number").take(3))
                }
            }

            val oppBenchArr = json.optJSONArray("opponent_bench")
            if (oppBenchArr != null) {
                for (i in 0 until oppBenchArr.length()) {
                    val obj = oppBenchArr.getJSONObject(i)
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    db.insertBenchPlayer(gid, obj.getString("jersey_number").take(3))
                }
            }

            val oppSubsArr = json.optJSONArray("opponent_substitutions")
            if (oppSubsArr != null) {
                for (i in 0 until oppSubsArr.length()) {
                    val obj = oppSubsArr.getJSONObject(i)
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    db.addOpponentSubstitution(gid, obj.getInt("slot"), obj.getString("jersey_out").take(3), obj.getString("jersey_in").take(3))
                }
            }

            val appsArr = json.optJSONArray("pitcher_appearances")
            if (appsArr != null) {
                for (i in 0 until appsArr.length()) {
                    val obj = appsArr.getJSONObject(i)
                    val pid = playerMapping[obj.getLong("player_id")] ?: continue
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    db.savePitcherAppearance(pid, gid, obj.optString("date", ""), obj.getInt("batters_faced"))
                }
            }

            val runnersArr = json.optJSONArray("game_runners")
            if (runnersArr != null) {
                for (i in 0 until runnersArr.length()) {
                    val obj = runnersArr.getJSONObject(i)
                    val gid = gameMapping[obj.getLong("game_id")] ?: continue
                    val pid = playerMapping[obj.getLong("player_id")] ?: 0L
                    db.insertRunner(
                        GameRunner(
                            gameId = gid,
                            base = obj.getInt("base"),
                            playerId = pid,
                            slot = obj.optInt("slot", 0),
                            jerseyNumber = if (obj.isNull("jersey_number")) null else obj.getString("jersey_number"),
                            name = obj.optString("name", "")
                        )
                    )
                }
            }

            val oppTeamsArr = json.optJSONArray("opponent_teams")
            if (oppTeamsArr != null) {
                for (i in 0 until oppTeamsArr.length()) {
                    val obj = oppTeamsArr.getJSONObject(i)
                    db.insertOpponentTeamForTeam(obj.getString("name").take(50), teamId)
                }
            }
        }
    }
}
