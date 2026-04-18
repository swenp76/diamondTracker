package de.baseball.diamond9

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
 *
 * Restore logic applies incremental migrations when importing older backups.
 */
class BackupManager constructor(
    private val context: Context,
    private val db: DatabaseHelper
) {
    constructor(context: Context) : this(context, DatabaseHelper(context))

    companion object {
        const val DB_VERSION = 14

        /** Maximum file size accepted for any import (5 MB). */
        const val MAX_IMPORT_BYTES = 5L * 1024 * 1024

        /**
         * Writes [content] to a temp file in the cache dir and fires an
         * ACTION_SEND share chooser so the user can send it via WhatsApp etc.
         */
        fun shareJson(context: Context, fileName: String, content: String) {
            val file = File(context.cacheDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }

        /** Valid pitch type strings stored in the database. */
        val VALID_PITCH_TYPES = setOf("B", "S", "F", "BF", "SO", "H", "HBP", "W")

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
                put("name", t.name)
            }
        }))

        // games
        root.put("games", JSONArray(db.getAllGames().map { g ->
            JSONObject().apply {
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
                    put("name", opp.name)
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
                    put("name", p.name)
                    put("number", p.number)
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
                    put("name", pitcher.name)
                    put("player_id", pitcher.playerId)
                })
                db.getPitchesForPitcher(pitcher.id).forEach { pitch ->
                    allPitches.put(JSONObject().apply {
                        put("id", pitch.id)
                        put("pitcher_id", pitch.pitcherId)
                        if (pitch.atBatId == 0L) put("at_bat_id", JSONObject.NULL) else put("at_bat_id", pitch.atBatId)
                        put("type", pitch.type)
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
                    put("jersey_number", entry.jerseyNumber)
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
                    put("jersey_number", bench.jerseyNumber)
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
                    put("jersey_out", sub.jerseyOut)
                    put("jersey_in", sub.jerseyIn)
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
                        put("date", app.date)
                        put("batters_faced", app.battersFaced)
                    })
                }
            }
        }
        root.put("pitcher_appearances", allAppearances)

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

        restoreTeams(migrated)
        restoreTeamPositions(migrated)
        restorePlayers(migrated)
        restoreGames(migrated)
        restorePitchers(migrated)
        restorePitches(migrated)
        restoreAtBats(migrated)
        restoreOwnLineup(migrated)
        restoreSubstitutions(migrated)
        restoreOpponentLineup(migrated)
        restoreOpponentBench(migrated)
        restoreOpponentSubstitutions(migrated)
        restoreScoreboardRuns(migrated)
        restorePitcherAppearances(migrated)
        restoreOpponentTeams(migrated)
        restoreLeagueSettings(migrated)
    }

    private fun restoreTeams(json: JSONObject) {
        val arr = json.optJSONArray("teams") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.execSQL(
                "INSERT OR IGNORE INTO teams (id, name) VALUES (?, ?)",
                arrayOf(obj.getLong("id"), obj.getString("name"))
            )
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
            db.execSQL(
                """INSERT OR IGNORE INTO players
                   (id, team_id, name, number, primary_position, secondary_position, is_pitcher, birth_year)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                arrayOf(
                    obj.getLong("id"),
                    obj.getLong("team_id"),
                    obj.getString("name"),
                    obj.getString("number"),
                    obj.getInt("primary_position"),
                    obj.optInt("secondary_position", 0),
                    obj.optInt("is_pitcher", 0),
                    obj.optInt("birth_year", 0)
                )
            )
        }
    }

    private fun restoreGames(json: JSONObject) {
        val arr = json.optJSONArray("games") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.execSQL(
                """INSERT OR IGNORE INTO games
                   (id, date, opponent, team_id, inning, outs, leadoff_slot,
                    start_time, elapsed_time_ms, game_time, is_home, current_inning, is_top_half, game_number)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                arrayOf(
                    obj.getLong("id"),
                    obj.getString("date"),
                    obj.getString("opponent"),
                    obj.optLong("team_id", 0L),
                    obj.optInt("inning", 1),
                    obj.optInt("outs", 0),
                    obj.optInt("leadoff_slot", 1),
                    obj.optLong("start_time", 0L),
                    obj.optLong("elapsed_time_ms", 0L),
                    obj.optString("game_time", ""),
                    obj.optInt("is_home", 1),
                    obj.optInt("current_inning", 1),
                    obj.optInt("is_top_half", 1),
                    obj.optString("game_number", "")
                )
            )
        }
    }

    private fun restorePitchers(json: JSONObject) {
        val arr = json.optJSONArray("pitchers") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.execSQL(
                "INSERT OR IGNORE INTO pitchers (id, game_id, name, player_id) VALUES (?, ?, ?, ?)",
                arrayOf(obj.getLong("id"), obj.getLong("game_id"), obj.getString("name"), obj.getLong("player_id"))
            )
        }
    }

    private fun restorePitches(json: JSONObject) {
        val arr = json.optJSONArray("pitches") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val atBatId = if (obj.isNull("at_bat_id")) null else obj.optLong("at_bat_id")
            db.execSQL(
                "INSERT OR IGNORE INTO pitches (id, pitcher_id, at_bat_id, type, sequence_nr, inning) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(obj.getLong("id"), obj.getLong("pitcher_id"), atBatId, obj.getString("type"), obj.getInt("sequence_nr"), obj.getInt("inning"))
            )
        }
    }

    private fun restoreAtBats(json: JSONObject) {
        val arr = json.optJSONArray("at_bats") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val result = if (obj.isNull("result")) null else obj.optString("result")
            db.execSQL(
                "INSERT OR IGNORE INTO at_bats (id, game_id, player_id, slot, inning, result) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(obj.getLong("id"), obj.getLong("game_id"), obj.getLong("player_id"), obj.getInt("slot"), obj.getInt("inning"), result)
            )
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
            db.execSQL(
                "INSERT OR IGNORE INTO substitutions (id, game_id, slot, player_out_id, player_in_id) VALUES (?, ?, ?, ?, ?)",
                arrayOf(obj.getLong("id"), obj.getLong("game_id"), obj.getInt("slot"), obj.getLong("player_out_id"), obj.getLong("player_in_id"))
            )
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
            db.addOpponentSubstitution(
                obj.getLong("game_id"), obj.getInt("slot"),
                obj.getString("jersey_out"), obj.getString("jersey_in")
            )
        }
    }

    private fun restorePitcherAppearances(json: JSONObject) {
        val arr = json.optJSONArray("pitcher_appearances") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            db.execSQL(
                """INSERT OR IGNORE INTO pitcher_appearances (id, player_id, game_id, date, batters_faced)
                   VALUES (?, ?, ?, ?, ?)""",
                arrayOf(obj.getLong("id"), obj.getLong("player_id"), obj.getLong("game_id"), obj.getString("date"), obj.getInt("batters_faced"))
            )
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

    // ── Single Game Export/Import ───────────────────────────────────────────────

    fun exportGame(gameId: Long): JSONObject {
        val game = db.getGame(gameId) ?: return JSONObject()
        val root = JSONObject()
        root.put("type", "single_game")
        root.put("dbVersion", DB_VERSION)

        // Game metadata
        val gObj = JSONObject().apply {
            put("date", game.date)
            put("opponent", game.opponent)
            put("inning", game.inning)
            put("outs", game.outs)
            put("leadoff_slot", game.leadoffSlot)
            put("start_time", game.startTime)
            put("elapsed_time_ms", game.elapsedTimeMs)
            put("game_time", game.gameTime)
            put("is_home", game.isHome)
            put("current_inning", game.currentInning)
            put("is_top_half", game.isTopHalf)
            put("game_number", game.gameNumber)
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
                put("name", p.name)
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
        db.updateGameState(gameId, gData.optInt("inning", 1), gData.optInt("outs", 0))
        db.updateLeadoffSlot(gameId, gData.optInt("leadoff_slot", 1))
        db.setStartTime(gameId, gData.optLong("start_time", 0L))
        db.setElapsedTime(gameId, gData.optLong("elapsed_time_ms", 0L))

        // Players cache for this team
        val teamPlayers = db.getPlayersForTeam(teamId)
        fun findOrCreatePlayer(pObj: JSONObject?): Long {
            if (pObj == null) return 0L
            val name = pObj.getString("name").take(50)
            val number = pObj.getString("number").take(3)
            val existing = teamPlayers.find { it.name == name && it.number == number }
            if (existing != null) return existing.id
            return db.insertPlayer(teamId, name, number, 1) // Default pos 1 (P)
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
    fun exportTeam(teamId: Long): String {
        val team = db.getAllTeams().first { it.id == teamId }
        val posArray = JSONArray()
        db.getEnabledPositions(teamId).sorted().forEach { posArray.put(it) }
        val playersArray = JSONArray()
        db.getPlayersForTeam(teamId).forEach { p ->
            playersArray.put(JSONObject().apply {
                put("name", p.name)
                put("number", p.number)
                put("primary_position", p.primaryPosition)
                put("secondary_position", p.secondaryPosition)
                put("is_pitcher", p.isPitcher)
                put("birth_year", p.birthYear)
            })
        }
        return JSONObject().apply {
            put("type", "team")
            put("version", 1)
            put("name", team.name)
            put("positions", posArray)
            put("players", playersArray)
        }.toString(2)
    }

    /**
     * Imports a team from a JSON object produced by [exportTeam].
     * Creates a new team with players and positions; never overwrites an existing team.
     */
    fun importTeam(json: JSONObject) {
        val teamId = db.insertTeam(json.getString("name"))

        db.getEnabledPositions(teamId).forEach { db.setPositionEnabled(teamId, it, false) }
        val posArray = json.optJSONArray("positions")
        if (posArray != null) {
            for (p in 0 until posArray.length()) {
                db.setPositionEnabled(teamId, posArray.getInt(p), true)
            }
        }

        val playersArray = json.optJSONArray("players")
        if (playersArray != null) {
            for (p in 0 until playersArray.length()) {
                val pl = playersArray.getJSONObject(p)
                db.insertPlayer(
                    teamId,
                    pl.getString("name"),
                    pl.optString("number", ""),
                    pl.optInt("primary_position", 0),
                    pl.optInt("secondary_position", 0),
                    pl.optBoolean("is_pitcher", false),
                    pl.optInt("birth_year", 0)
                )
            }
        }
    }
}
