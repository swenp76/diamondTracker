package de.baseball.diamond9

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class PitcherListActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper
    private var gameId: Long = -1
    private lateinit var pitchTrackLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        db = DatabaseHelper(this)

        gameId = intent.getLongExtra("gameId", -1)
        val gameOpponent = intent.getStringExtra("gameOpponent") ?: ""
        val gameDate = intent.getStringExtra("gameDate") ?: ""

        pitchTrackLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == GameHubActivity.RESULT_HALF_INNING_SWITCHED) {
                setResult(GameHubActivity.RESULT_HALF_INNING_SWITCHED, result.data)
                finish()
            }
        }

        setContent {
            var pitchers by remember { mutableStateOf(db.getPitchersForGame(gameId)) }
            var halfInningState by remember { mutableStateOf(db.getHalfInningState(gameId)) }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        halfInningState = db.getHalfInningState(gameId)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            fun refreshPitchers() {
                pitchers = db.getPitchersForGame(gameId)
            }

            val subtitle = if (gameDate.isNotEmpty()) "$gameDate  •  ${halfInningState.shortLabel}"
                           else halfInningState.shortLabel

            PitcherListScreen(
                title = gameOpponent,
                subtitle = subtitle,
                pitchers = pitchers,
                onTrack = { pitcher ->
                    pitchTrackLauncher.launch(
                        Intent(this, PitchTrackActivity::class.java).apply {
                            putExtra("pitcherId", pitcher.id)
                            putExtra("pitcherName", pitcher.name)
                            putExtra("gameId", gameId)
                        }
                    )
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
        val effectiveLineup = db.getEffectiveLineup(gameId)
        val teamId = db.getGame(gameId)?.teamId ?: 0L
        
        val players = if (effectiveLineup.isNotEmpty()) {
            effectiveLineup.values.toList().sortedBy { it.number.toIntOrNull() ?: 999 }
        } else if (teamId > 0) {
            db.getPlayersForTeam(teamId)
        } else {
            emptyList()
        }

        if (players.isEmpty()) {
            val et = EditText(this).apply {
                hint = getString(R.string.hint_pitcher_name)
                setPadding(48, 24, 48, 24)
                filters = arrayOf(InputFilter.LengthFilter(50))
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
        containerColor = colorResource(R.color.color_background),
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPitcherClick,
                containerColor = colorResource(R.color.color_primary),
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
                    containerColor = colorResource(R.color.color_primary),
                    disabledContainerColor = colorResource(R.color.color_primary).copy(alpha = 0.35f)
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(stringResource(R.string.btn_pitch), color = Color.White)
            }

            Button(
                onClick = onStats,
                colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_green_dark)),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(stringResource(R.string.btn_stats), color = Color.White)
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
