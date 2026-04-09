package de.baseball.diamond9

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles backup and restore of all app data as JSON.
 *
 * DB version history:
 *  5 → initial backup support (teams, players, games, pitchers, pitches,
 *      at_bats, lineups, substitutions, opponent_teams)
 *  6 → added scoreboard_runs table
 *  7 → added start_time column to games table
 *  8 → opponent_teams now scoped per team (team_id column added)
 *
 * Restore logic applies incremental migrations when importing older backups.
 */
class BackupManager(private val context: Context) {

    companion object {
        const val DB_VERSION = 8
    }

    private val db = DatabaseHelper(context)

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

        // TODO: add remaining tables in a future commit (issue #18-#20):
        //  players, team_positions, pitchers, pitches, at_bats,
        //  own_lineup, substitutions, opponent_lineup, opponent_bench,
        //  opponent_substitutions, pitcher_appearances

        return root
    }

    // ── Restore ─────────────────────────────────────────────────────────────────

    /**
     * Restores data from a JSON backup.
     * Applies incremental schema migrations for older backup versions.
     */
    fun restoreFromJson(json: JSONObject) {
        val backupVersion = json.optInt("dbVersion", 1)

        // Apply logical migrations for older backup formats
        val migrated = applyBackupMigrations(json, fromVersion = backupVersion, toVersion = DB_VERSION)

        restoreScoreboardRuns(migrated)
        restoreOpponentTeams(migrated)
        // TODO: restore remaining tables (issue #18-#20)
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
}
