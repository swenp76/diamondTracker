package de.baseball.diamond9

import android.content.Context
import de.baseball.diamond9.db.OwnLineupSlot
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
 *  9 → game_time column added to games
 *  10 → is_home column added to games (1 = home, 0 = away)
 *  11 → elapsed_time_ms column added to games
 *
 * Restore logic applies incremental migrations when importing older backups.
 */
class BackupManager(private val context: Context) {

    companion object {
        const val DB_VERSION = 11
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
                put("elapsed_time_ms", g.elapsedTimeMs)
                put("game_time", g.gameTime)
                put("is_home", g.isHome)
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
        val gameId = db.insertGame(
            date = gData.getString("date"),
            opponent = gData.getString("opponent"),
            teamId = teamId,
            gameTime = gData.optString("game_time", ""),
            isHome = gData.optInt("is_home", 1)
        )
        db.updateGameState(gameId, gData.optInt("inning", 1), gData.optInt("outs", 0))
        db.updateLeadoffSlot(gameId, gData.optInt("leadoff_slot", 1))
        db.setStartTime(gameId, gData.optLong("start_time", 0L))
        db.setElapsedTime(gameId, gData.optLong("elapsed_time_ms", 0L))

        // Players cache for this team
        val teamPlayers = db.getPlayersForTeam(teamId)
        fun findOrCreatePlayer(pObj: JSONObject?): Long {
            if (pObj == null) return 0L
            val name = pObj.getString("name")
            val number = pObj.getString("number")
            val existing = teamPlayers.find { it.name == name && it.number == number }
            if (existing != null) return existing.id
            return db.insertPlayer(teamId, name, number, 1) // Default pos 1 (P)
        }

        // Scoreboard
        val sbArr = json.optJSONArray("scoreboard")
        if (sbArr != null) {
            for (i in 0 until sbArr.length()) {
                val r = sbArr.getJSONObject(i)
                db.upsertScoreboardRun(gameId, r.getInt("inning"), r.getInt("is_home"), r.getInt("runs"))
            }
        }

        // Own Lineup
        val lineupArr = json.optJSONArray("own_lineup")
        if (lineupArr != null) {
            for (i in 0 until lineupArr.length()) {
                val entry = lineupArr.getJSONObject(i)
                val pid = findOrCreatePlayer(entry.getJSONObject("player"))
                db.setOwnLineupPlayer(gameId, entry.getInt("slot"), pid)
            }
        }

        // Own Substitutions
        val subArr = json.optJSONArray("substitutions")
        if (subArr != null) {
            for (i in 0 until subArr.length()) {
                val s = subArr.getJSONObject(i)
                val pOut = findOrCreatePlayer(s.optJSONObject("player_out"))
                val pIn = findOrCreatePlayer(s.optJSONObject("player_in"))
                db.addSubstitution(gameId, s.getInt("slot"), pOut, pIn)
            }
        }

        // At-Bats
        val abArr = json.optJSONArray("at_bats")
        if (abArr != null) {
            for (i in 0 until abArr.length()) {
                val abObj = abArr.getJSONObject(i)
                val pid = findOrCreatePlayer(abObj.optJSONObject("player"))
                val abId = db.insertAtBat(gameId, pid, abObj.getInt("slot"), abObj.getInt("inning"))
                val result = if (abObj.isNull("result")) null else abObj.getString("result")
                db.updateAtBatResult(abId, result)
                val pArr = abObj.optJSONArray("pitches")
                if (pArr != null) {
                    for (j in 0 until pArr.length()) {
                        val p = pArr.getJSONObject(j)
                        db.insertPitchForAtBat(abId, p.getString("type"), p.getInt("inning"))
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
                val pitcherId = db.insertPitcher(gameId, pObj.getString("name"), pid)
                val pArr = pObj.optJSONArray("pitches")
                if (pArr != null) {
                    for (j in 0 until pArr.length()) {
                        val p = pArr.getJSONObject(j)
                        db.insertPitch(pitcherId, p.getString("type"), p.getInt("inning"))
                    }
                }
            }
        }

        // Opponent
        val oppLArr = json.optJSONArray("opponent_lineup")
        if (oppLArr != null) {
            for (i in 0 until oppLArr.length()) {
                val l = oppLArr.getJSONObject(i)
                db.upsertLineupEntry(gameId, l.getInt("batting_order"), l.getString("jersey_number"))
            }
        }
        val oppBArr = json.optJSONArray("opponent_bench")
        if (oppBArr != null) {
            for (i in 0 until oppBArr.length()) {
                db.insertBenchPlayer(gameId, oppBArr.getJSONObject(i).getString("jersey_number"))
            }
        }
        val oppSArr = json.optJSONArray("opponent_substitutions")
        if (oppSArr != null) {
            for (i in 0 until oppSArr.length()) {
                val os = oppSArr.getJSONObject(i)
                db.addOpponentSubstitution(gameId, os.getInt("slot"), os.getString("jersey_out"), os.getString("jersey_in"))
            }
        }

        return gameId
    }
}
