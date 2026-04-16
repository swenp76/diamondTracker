package de.baseball.diamond9

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.json.JSONObject

class JsonDispatchActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)

        val uri: Uri? = intent?.data
        if (uri == null) {
            finish()
            return
        }

        val (json, error) = readJson(uri)
        if (json == null) {
            Toast.makeText(this, error ?: getString(R.string.dispatch_read_error), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val fileType = json.optString("type", "").ifEmpty { inferType(json) }

        setContent {
            MaterialTheme {
                DispatchDialog(
                    fileType = fileType,
                    json = json,
                    onDismiss = { finish() }
                )
            }
        }
    }

    @Composable
    private fun DispatchDialog(
        fileType: String,
        json: JSONObject,
        onDismiss: () -> Unit
    ) {
        when (fileType) {
            "single_game"     -> GameImportDialog(json, onDismiss)
            "team"            -> TeamImportDialog(json, onDismiss)
            "league_settings" -> LeagueImportDialog(json, onDismiss)
            else              -> UnknownTypeDialog(fileType, onDismiss)
        }
    }

    // ── single_game ──────────────────────────────────────────────────────────

    @Composable
    private fun GameImportDialog(json: JSONObject, onDismiss: () -> Unit) {
        val teams = remember { db.getAllTeams() }
        var selectedTeamId by remember { mutableStateOf(db.getActiveTeamId()) }
        val gameName = remember {
            val g = json.optJSONObject("game")
            "${g?.optString("date", "??") ?: "??"} vs ${g?.optString("opponent", "??") ?: "??"}"
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dispatch_game_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dispatch_game_message, gameName))
                    if (teams.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.dispatch_select_team),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        teams.forEach { team ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTeamId == team.id,
                                    onClick = { selectedTeamId = team.id }
                                )
                                Text(team.name, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val teamId = selectedTeamId ?: teams.firstOrNull()?.id ?: -1L
                    if (teamId == -1L) {
                        Toast.makeText(
                            this@JsonDispatchActivity,
                            getString(R.string.dispatch_no_team),
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                        return@TextButton
                    }
                    try {
                        val backupManager = BackupManager(this@JsonDispatchActivity)
                        backupManager.importGame(teamId, json)
                        Toast.makeText(
                            this@JsonDispatchActivity,
                            getString(R.string.dispatch_game_imported),
                            Toast.LENGTH_SHORT
                        ).show()
                        val i = Intent(this@JsonDispatchActivity, GameListActivity::class.java)
                        i.putExtra("teamId", teamId)
                        startActivity(i)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@JsonDispatchActivity,
                            getString(R.string.dispatch_import_failed, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onDismiss()
                }) { Text(stringResource(R.string.dispatch_confirm_import)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dispatch_cancel)) }
            }
        )
    }

    // ── team ─────────────────────────────────────────────────────────────────

    @Composable
    private fun TeamImportDialog(json: JSONObject, onDismiss: () -> Unit) {
        val teamName = remember { json.optString("name", "??") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dispatch_team_title)) },
            text = { Text(stringResource(R.string.dispatch_team_message, teamName)) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val backupManager = BackupManager(this@JsonDispatchActivity)
                        backupManager.importTeam(json)
                        Toast.makeText(
                            this@JsonDispatchActivity,
                            getString(R.string.dispatch_team_imported),
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@JsonDispatchActivity,
                            getString(R.string.dispatch_import_failed, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onDismiss()
                }) { Text(stringResource(R.string.dispatch_confirm_import)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dispatch_cancel)) }
            }
        )
    }

    // ── league_settings ──────────────────────────────────────────────────────

    @Composable
    private fun LeagueImportDialog(json: JSONObject, onDismiss: () -> Unit) {
        val teams = remember { db.getAllTeams() }
        var selectedTeamId by remember { mutableStateOf(db.getActiveTeamId()) }
        val innings = remember { json.optInt("innings", 9) }
        val timeLimit = remember {
            if (json.isNull("time_limit_minutes")) null
            else json.optInt("time_limit_minutes").takeIf { json.has("time_limit_minutes") }
        }
        val summary = remember {
            val tl = if (timeLimit != null) "$timeLimit min" else "–"
            "$innings Innings, Zeitlimit: $tl"
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dispatch_league_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dispatch_league_message, summary))
                    if (teams.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.dispatch_select_team),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        teams.forEach { team ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTeamId == team.id,
                                    onClick = { selectedTeamId = team.id }
                                )
                                Text(team.name, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val teamId = selectedTeamId ?: teams.firstOrNull()?.id ?: -1L
                    if (teamId == -1L) {
                        Toast.makeText(
                            this@JsonDispatchActivity,
                            getString(R.string.dispatch_no_team),
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
                        return@TextButton
                    }
                    try {
                        db.saveLeagueSettings(
                            LeagueSettings(
                                teamId = teamId,
                                innings = innings,
                                timeLimitMinutes = timeLimit
                            )
                        )
                        Toast.makeText(
                            this@JsonDispatchActivity,
                            getString(R.string.dispatch_league_imported),
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@JsonDispatchActivity,
                            getString(R.string.dispatch_import_failed, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onDismiss()
                }) { Text(stringResource(R.string.dispatch_confirm_import)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dispatch_cancel)) }
            }
        )
    }

    // ── Unbekannter Typ ──────────────────────────────────────────────────────

    @Composable
    private fun UnknownTypeDialog(fileType: String, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dispatch_unknown_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dispatch_unknown_message,
                        if (fileType.isEmpty()) "–" else fileType
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dispatch_ok)) }
            }
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Heuristic fallback for older exports that predate the "type" field.
     *   - has "game" key             → single_game
     *   - has "name" + "players"     → team
     *   - has "innings" (no "game")  → league_settings
     */
    private fun inferType(json: JSONObject): String = when {
        json.has("game")                          -> "single_game"
        json.has("name") && json.has("players")   -> "team"
        json.has("innings")                       -> "league_settings"
        else                                      -> ""
    }

    private fun readJson(uri: Uri): Pair<JSONObject?, String?> {
        return try {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
            val size = pfd?.statSize ?: 0L
            pfd?.close()
            if (size > BackupManager.MAX_IMPORT_BYTES) {
                return Pair(null, getString(R.string.toast_import_file_too_large))
            }
            val text = contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText()
                ?: return Pair(null, getString(R.string.dispatch_read_error))
            Pair(JSONObject(text), null)
        } catch (e: Exception) {
            Pair(null, getString(R.string.dispatch_read_error))
        }
    }
}
