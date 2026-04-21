package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

class TeamHubActivity : ComponentActivity() {

    private var teamId: Long = -1
    private var teamName: String = ""

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        teamId = intent.getLongExtra("teamId", -1)
        teamName = intent.getStringExtra("teamName") ?: ""
        db = DatabaseHelper(this)

        setContent {
            TeamHubScreen(
                teamName = teamName,
                onBack = { finish() },
                onGames = {
                    startActivity(Intent(this, GameListActivity::class.java).apply {
                        putExtra("teamId", teamId)
                        putExtra("teamName", teamName)
                    })
                },
                onStats = {
                    startActivity(Intent(this, SeasonStatsActivity::class.java).apply {
                        putExtra("teamId", teamId)
                        putExtra("teamName", teamName)
                    })
                },
                onEditRoster = {
                    startActivity(Intent(this, TeamDetailActivity::class.java).apply {
                        putExtra("teamId", teamId)
                    })
                },
                onSettings = {
                    startActivity(Intent(this, TeamSettingsActivity::class.java).apply {
                        putExtra("teamId", teamId)
                        putExtra("teamName", teamName)
                    })
                },
                onShareTeam = {
                    val safeName = teamName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                    val json = BackupManager(this).exportTeam(teamId)
                    BackupManager.shareJson(this, "$safeName.json", json)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamHubScreen(
    teamName: String,
    onBack: () -> Unit,
    onGames: () -> Unit,
    onStats: () -> Unit,
    onEditRoster: () -> Unit,
    onSettings: () -> Unit,
    onShareTeam: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(teamName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_team)) },
                                onClick = { menuExpanded = false; onShareTeam() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HubButton(
                text = stringResource(R.string.teamhub_games),
                color = colorResource(R.color.color_primary),
                onClick = onGames
            )
            Spacer(modifier = Modifier.height(16.dp))
            HubButton(
                text = stringResource(R.string.teamhub_stats),
                color = colorResource(R.color.color_green_dark),
                onClick = onStats
            )
            Spacer(modifier = Modifier.height(16.dp))
            HubButton(
                text = stringResource(R.string.teamhub_edit_roster),
                color = colorResource(R.color.color_gray),
                onClick = onEditRoster
            )
            Spacer(modifier = Modifier.height(16.dp))
            HubButton(
                text = stringResource(R.string.teamhub_settings),
                color = colorResource(R.color.color_text_subtle),
                onClick = onSettings
            )
        }
    }
}

@Composable
private fun HubButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Text(text = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
