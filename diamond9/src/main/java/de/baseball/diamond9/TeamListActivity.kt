package de.baseball.diamond9

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

class TeamListActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
            val size = pfd?.statSize ?: 0L
            pfd?.close()
            if (size > BackupManager.MAX_IMPORT_BYTES) {
                Toast.makeText(this, getString(R.string.toast_import_file_too_large), Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            val json = contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@registerForActivityResult
            showImportConfirmDialog(json)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_file_read_error), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)

        setContent {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            var teams by remember { mutableStateOf(db.getAllTeams()) }

            fun refreshTeams() {
                teams = db.getAllTeams()
            }

            AppDrawer(
                drawerState = drawerState,
                scope = scope,
                currentActivity = TeamListActivity::class.java,
                context = this
            ) {
                TeamListScreen(
                    teams = teams,
                    onTeamClick = { team ->
                        val intent = Intent(this, TeamHubActivity::class.java)
                        intent.putExtra("teamId", team.id)
                        intent.putExtra("teamName", team.name)
                        startActivity(intent)
                    },
                    onDeleteTeam = { team ->
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.dialog_delete_team_title))
                            .setMessage(getString(R.string.dialog_delete_team_message, team.name))
                            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                                db.deleteTeam(team.id)
                                refreshTeams()
                            }
                            .setNegativeButton(getString(R.string.btn_cancel), null)
                            .show()
                    },
                    onAddTeamClick = { showAddTeamDialog { refreshTeams() } },
                    onImportClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
        }
    }

    private fun showAddTeamDialog(onSuccess: () -> Unit) {
        val et = EditText(this).apply {
            hint = getString(R.string.hint_team_name)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_team_title))
            .setView(et)
            .setPositiveButton(getString(R.string.btn_create)) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    db.insertTeam(name)
                    onSuccess()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showImportConfirmDialog(json: String) {
        val root = try { JSONObject(json) } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_invalid_json), Toast.LENGTH_LONG).show()
            return
        }
        if (!root.has("name")) {
            Toast.makeText(this, getString(R.string.toast_invalid_team_format), Toast.LENGTH_LONG).show()
            return
        }

        val teamName = root.getString("name")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_import_team_title))
            .setMessage(getString(R.string.dialog_import_team_message, teamName))
            .setPositiveButton(getString(R.string.btn_import)) { _, _ -> 
                importTeam(root)
                recreate() 
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun importTeam(root: JSONObject) {
        try {
            val teamId = db.insertTeam(root.getString("name"))

            db.getEnabledPositions(teamId).forEach { db.setPositionEnabled(teamId, it, false) }
            val posArray = root.optJSONArray("positions")
            if (posArray != null) {
                for (p in 0 until posArray.length()) {
                    db.setPositionEnabled(teamId, posArray.getInt(p), true)
                }
            }

            val playersArray = root.optJSONArray("players")
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

            Toast.makeText(this, getString(R.string.toast_team_imported, root.getString("name")), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_import_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamListScreen(
    teams: List<Team>,
    onTeamClick: (Team) -> Unit,
    onDeleteTeam: (Team) -> Unit,
    onAddTeamClick: () -> Unit,
    onImportClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.teams_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = onImportClick) {
                        Text(stringResource(R.string.menu_import_team))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTeamClick,
                containerColor = colorResource(R.color.color_primary),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_add_team)) }
            )
        }
    ) { padding ->
        if (teams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_teams),
                    fontSize = 15.sp,
                    color = colorResource(R.color.color_text_secondary),
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(teams) { team ->
                    TeamItem(
                        team = team,
                        onClick = { onTeamClick(team) },
                        onDelete = { onDeleteTeam(team) }
                    )
                }
            }
        }
    }
}

@Composable
fun TeamItem(
    team: Team,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = colorResource(R.color.color_primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = team.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.color_text_primary)
                )
                Text(
                    text = stringResource(R.string.team_tap_to_open),
                    fontSize = 13.sp,
                    color = colorResource(R.color.color_text_secondary)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.content_desc_delete),
                    tint = colorResource(R.color.color_strike)
                )
            }
        }
    }
}
