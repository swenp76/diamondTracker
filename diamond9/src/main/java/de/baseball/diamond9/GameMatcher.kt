package de.baseball.diamond9

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Utility to identify and merge duplicate games.
 */
class GameMatcher(private val context: Context, private val db: DatabaseHelper) {

    /**
     * Checks if two games are potential duplicates.
     * Heuristic: Same date, same opponent, same game number, and similar time.
     */
    fun isPotentialDuplicate(g1: Game, g2: Game): Boolean {
        if (g1.id == g2.id) return false
        if (g1.date != g2.date) return false
        if (g1.opponent != g2.opponent) return false

        // 1. Check game number: if both have one and they differ, they are not duplicates
        if (g1.gameNumber.isNotEmpty() && g2.gameNumber.isNotEmpty() && g1.gameNumber != g2.gameNumber) {
            return false
        }

        // 2. Check game time (HH:MM): if both have one and they differ, they are not duplicates
        if (g1.gameTime.isNotEmpty() && g2.gameTime.isNotEmpty() && g1.gameTime != g2.gameTime) {
            return false
        }

        // 3. Check start time (timestamp): if both are > 0 and differ by more than 30 mins, not duplicates
        if (g1.startTime > 0 && g2.startTime > 0) {
            val timeDiff = Math.abs(g1.startTime - g2.startTime)
            if (timeDiff > TimeUnit.MINUTES.toMillis(30)) {
                return false
            }
        }
        
        return true
    }

    /**
     * Merges two games into a target master game.
     * Creates an undo snapshot before proceeding.
     */
    fun mergeGames(masterId: Long, duplicateId: Long): Boolean {
        val bm = BackupManager(context, db)
        val masterGame = db.getGame(masterId) ?: return false
        
        // Create undo snapshot
        val masterSnapshot = bm.exportGame(masterId)
        val duplicateSnapshot = bm.exportGame(duplicateId)
        val snapshot = JSONObject().apply {
            put("master", masterSnapshot)
            put("duplicate", duplicateSnapshot)
            put("masterId", masterId)
            put("teamId", masterGame.teamId)
            put("timestamp", System.currentTimeMillis())
        }
        saveUndoSnapshot(snapshot)

        // 1. Merge Scoreboard: Higher run value wins per inning
        val masterSB = db.getScoreboard(masterId)
        val duplicateSB = db.getScoreboard(duplicateId)
        
        duplicateSB.forEach { dRun ->
            val mRun = masterSB.find { it.inning == dRun.inning && it.isHome == dRun.isHome }
            if (mRun == null) {
                db.upsertScoreboardRun(masterId, dRun.inning, dRun.isHome, dRun.runs)
            } else if (dRun.runs > mRun.runs) {
                db.upsertScoreboardRun(masterId, mRun.inning, mRun.isHome, dRun.runs)
            }
        }

        // 2. Merge At-Bats
        val masterABs = db.getAtBatsForGame(masterId)
        val duplicateABs = db.getAtBatsForGame(duplicateId)
        
        duplicateABs.forEach { dAB ->
            // Simple logic: If slot/inning is free in master, move it. 
            // Otherwise, it's a conflict and we skip it for now (or could add to end).
            val conflict = masterABs.any { it.slot == dAB.slot && it.inning == dAB.inning }
            if (!conflict) {
                // We need a way to re-parent the AtBat and its Pitches.
                // DatabaseHelper needs a method for this.
                db.reparentAtBat(dAB.id, masterId)
            }
        }

        // 3. Merge Pitchers
        val duplicatePitchers = db.getPitchersForGame(duplicateId)
        duplicatePitchers.forEach { dp ->
            db.reparentPitcher(dp.id, masterId)
        }

        // 4. Delete the duplicate game (CASCADE will handle children if not reparented)
        db.deleteGame(duplicateId)
        
        return true
    }

    private fun saveUndoSnapshot(snapshot: JSONObject) {
        val prefs = context.getSharedPreferences("merges", Context.MODE_PRIVATE)
        prefs.edit().putString("last_merge_undo", snapshot.toString()).apply()
    }

    fun undoLastMerge(): Boolean {
        val prefs = context.getSharedPreferences("merges", Context.MODE_PRIVATE)
        val snapshotStr = prefs.getString("last_merge_undo", null) ?: return false
        val snapshot = JSONObject(snapshotStr)
        
        val masterId = snapshot.optLong("masterId", -1L)
        val teamId = snapshot.optLong("teamId", -1L)
        val masterSnapshot = snapshot.optJSONObject("master")
        val duplicateSnapshot = snapshot.optJSONObject("duplicate")

        if (masterId != -1L && teamId != -1L && masterSnapshot != null && duplicateSnapshot != null) {
            val bm = BackupManager(context, db)
            // 1. Delete the merged master game
            db.deleteGame(masterId)
            
            // 2. Restore the two original games
            bm.importGame(teamId, masterSnapshot)
            bm.importGame(teamId, duplicateSnapshot)
            
            prefs.edit().remove("last_merge_undo").apply()
            return true
        }
        
        prefs.edit().remove("last_merge_undo").apply()
        return false
    }
}
