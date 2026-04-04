package de.baseball.diamond9

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PitcherListActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper
    private var gameId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)

        gameId = intent.getLongExtra("gameId", -1)
        val gameOpponent = intent.getStringExtra("gameOpponent") ?: ""
        val gameDate = intent.getStringExtra("gameDate") ?: ""

        setContent {
            var pitchers by remember { mutableStateOf(db.getPitchersForGame(gameId)) }

            fun refreshPitchers() {
                pitchers = db.getPitchersForGame(gameId)
            }

            PitcherListScreen(
                title = gameOpponent,
                subtitle = gameDate,
                pitchers = pitchers,
                onTrack = { pitcher ->
                    val intent = Intent(this, PitchTrackActivity::class.java)
                    intent.putExtra("pitcherId", pitcher.id)
                    intent.putExtra("pitcherName", pitcher.name)
                    intent.putExtra("gameId", gameId)
                    startActivity(intent)
                },
                onStats = { pitcher ->
                    val intent = Intent(this, StatsActivity::class.java)
                    intent.putExtra("pitcherId", pitcher.id)
                    startActivity(intent)
                },
                onDelete = { pitcher ->
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_delete_pitcher_title))
                        .setMessage(getString(R.string.dialog_delete_pitcher_message, pitcher.name))
                        .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                            db.deletePitcher(pitcher.id)
                            refreshPitchers()
                        }
                        .setNegativeButton(getString(R.string.btn_cancel), null)
                        .show()
                },
                onAddPitcherClick = { showAddPitcherDialog { refreshPitchers() } },
                onBackClick = { finish() }
            )
        }
    }

    private fun showAddPitcherDialog(onSuccess: () -> Unit) {
        val starters = db.getOwnLineupStarters(gameId)
        val teamId = db.getGame(gameId)?.teamId ?: 0L
        val players = if (starters.isNotEmpty()) starters
        else if (teamId > 0) db.getPlayersForTeam(teamId)
        else emptyList()

        if (players.isEmpty()) {
            val et = EditText(this).apply {
                hint = getString(R.string.hint_pitcher_name)
                setPadding(48, 24, 48, 24)
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_add_pitcher_title))
                .setView(et)
                .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                    val name = et.text.toString().trim()
                    if (name.isNotEmpty()) {
                        db.insertPitcher(gameId, name)
                        onSuccess()
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
            return
        }

        val labels = players.map { "#${it.number}  ${it.name}${if (it.isPitcher) "  P" else ""}" }.toTypedArray()
        var selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_pitcher_title))
            .setSingleChoiceItems(labels, 0) { _, which -> selectedIndex = which }
            .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                val selected = players[selectedIndex]
                db.insertPitcher(gameId, selected.name, selected.id)
                onSuccess()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitcherListScreen(
    title: String,
    subtitle: String,
    pitchers: List<Pitcher>,
    onTrack: (Pitcher) -> Unit,
    onStats: (Pitcher) -> Unit,
    onDelete: (Pitcher) -> Unit,
    onAddPitcherClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPitcherClick,
                containerColor = Color(0xFF1A5FA8),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_add_pitcher)) }
            )
        }
    ) { padding ->
        if (pitchers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_pitchers),
                    fontSize = 15.sp,
                    color = Color(0xFF888888),
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
                itemsIndexed(pitchers) { index, pitcher ->
                    val isActive = index == pitchers.lastIndex
                    PitcherItem(
                        pitcher = pitcher,
                        isActive = isActive,
                        onTrack = { onTrack(pitcher) },
                        onStats = { onStats(pitcher) },
                        onDelete = { onDelete(pitcher) }
                    )
                }
            }
        }
    }
}

@Composable
fun PitcherItem(
    pitcher: Pitcher,
    isActive: Boolean,
    onTrack: () -> Unit,
    onStats: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pitcher.name,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Button(
                onClick = onTrack,
                enabled = isActive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A5FA8),
                    disabledContainerColor = Color(0xFF1A5FA8).copy(alpha = 0.35f)
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(stringResource(R.string.btn_pitch), color = Color.White)
            }

            Button(
                onClick = onStats,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B6D11)),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(stringResource(R.string.btn_stats), color = Color.White)
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFC0392B)
                )
            }
        }
    }
}
