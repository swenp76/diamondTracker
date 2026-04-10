package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.baseball.diamond9.DatabaseHelper
import kotlinx.coroutines.delay
import java.util.*

class GameHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameId = intent.getLongExtra("game_id", -1L)
        val date = intent.getStringExtra("date") ?: ""
        val opponent = intent.getStringExtra("opponent") ?: ""
        val ownTeam = intent.getStringExtra("own_team") ?: ""
        val ownTeamId = intent.getLongExtra("own_team_id", -1L)
        val isHome = intent.getIntExtra("is_home", 1)
        val db = DatabaseHelper(this)

        setContent {
            GameHubScreen(
                gameId = gameId,
                date = date,
                opponent = opponent,
                ownTeam = ownTeam,
                ownTeamId = ownTeamId.toString(),
                isHome = isHome,
                db = db,
                onBack = { finish() },
                onOffense = {
                    val intent = Intent(this, BattingTrackActivity::class.java).apply {
                        putExtra("gameId", gameId)
                    }
                    startActivity(intent)
                },
                onDefense = {
                    val intent = Intent(this, PitchTrackActivity::class.java).apply {
                        putExtra("gameId", gameId)
                    }
                    startActivity(intent)
                },
                onLineup = {
                    val intent = Intent(this, OwnLineupActivity::class.java).apply {
                        putExtra("gameId", gameId)
                    }
                    startActivity(intent)
                },
                onOpponentLineup = {
                    val intent = Intent(this, OpponentLineupActivity::class.java).apply {
                        putExtra("gameId", gameId)
                    }
                    startActivity(intent)
                },
                onSeasonStats = {
                    val intent = Intent(this, SeasonStatsActivity::class.java)
                    startActivity(intent)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHubScreen(
    gameId: Long,
    date: String,
    opponent: String,
    ownTeam: String,
    ownTeamId: String,
    isHome: Int,
    db: DatabaseHelper,
    onBack: () -> Unit,
    onOffense: () -> Unit,
    onDefense: () -> Unit,
    onLineup: () -> Unit,
    onOpponentLineup: () -> Unit,
    onSeasonStats: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(opponent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(date, fontSize = 14.sp, color = colorResource(R.color.color_text_secondary))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(colorResource(R.color.color_background)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Scoreboard(gameId, ownTeam, opponent, isHome, db)

            GameTimer(gameId, db)

            Spacer(modifier = Modifier.height(8.dp))

            HubButton(stringResource(R.string.gamehub_offense), colorResource(R.color.color_primary), onOffense)
            HubButton(stringResource(R.string.gamehub_defense), colorResource(R.color.color_primary), onDefense)
            HubButton(stringResource(R.string.gamehub_lineup), colorResource(R.color.color_primary), onLineup)
            HubButton(stringResource(R.string.gamehub_oppo_lineup), colorResource(R.color.color_primary), onOpponentLineup)
            HubButton(stringResource(R.string.season_stats_btn), colorResource(R.color.color_primary), onSeasonStats)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Scoreboard(gameId: Long, ownTeam: String, opponent: String, isHome: Int, db: DatabaseHelper) {
    val guestTeam = if (isHome == 1) opponent else ownTeam
    val homeTeam = if (isHome == 1) ownTeam else opponent

    // teamIndex: 0 = guest, 1 = home
    var editCell by remember { mutableStateOf<Pair<Int, Int>?>(null) } // teamIndex to inning
    // runs[teamIndex][inning-1]
    val runs = remember { mutableStateOf(Array(2) { t -> IntArray(9) { inn -> db.getScoreboardRuns(gameId, inn + 1, t) } }) }
    val hasEntry = remember { mutableStateOf(Array(2) { t -> BooleanArray(9) { inn -> db.hasScoreboardEntry(gameId, inn + 1, t) } }) }

    fun reload() {
        runs.value = Array(2) { t -> IntArray(9) { inn -> db.getScoreboardRuns(gameId, inn + 1, t) } }
        hasEntry.value = Array(2) { t -> BooleanArray(9) { inn -> db.hasScoreboardEntry(gameId, inn + 1, t) } }
    }

    fun totalFor(teamIndex: Int): Int = runs.value[teamIndex].sum()

    // Dialog for entering runs
    editCell?.let { (teamIndex, inning) ->
        val current = runs.value[teamIndex][inning - 1]
        var input by remember(editCell) { mutableStateOf(if (hasEntry.value[teamIndex][inning - 1]) current.toString() else "") }
        AlertDialog(
            onDismissRequest = { editCell = null },
            title = { Text("Inning $inning – ${if (teamIndex == 0) guestTeam else homeTeam}") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) input = it },
                    label = { Text(stringResource(R.string.scoreboard_runs_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = input.toIntOrNull() ?: 0
                    db.upsertScoreboardRun(gameId, inning, teamIndex, r)
                    reload()
                    editCell = null
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editCell = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            // Header Row
            Row(modifier = Modifier.background(colorResource(R.color.color_primary))) {
                ScoreCell("", 100.dp, true)
                (1..9).forEach { ScoreCell(it.toString(), 30.dp, true) }
                ScoreCell(stringResource(R.string.scoreboard_total_label), 40.dp, true)
            }
            // Guest Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreCell(guestTeam, 100.dp, false, FontWeight.Bold)
                (1..9).forEach { inn ->
                    val r = if (hasEntry.value[0][inn - 1]) runs.value[0][inn - 1].toString() else "-"
                    ScoreCell(r, 30.dp, false, onClick = { editCell = Pair(0, inn) })
                }
                ScoreCell(totalFor(0).toString(), 40.dp, false, FontWeight.Bold)
            }
            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
            // Home Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreCell(homeTeam, 100.dp, false, FontWeight.Bold)
                (1..9).forEach { inn ->
                    val r = if (hasEntry.value[1][inn - 1]) runs.value[1][inn - 1].toString() else "-"
                    ScoreCell(r, 30.dp, false, onClick = { editCell = Pair(1, inn) })
                }
                ScoreCell(totalFor(1).toString(), 40.dp, false, FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GameTimer(gameId: Long, db: DatabaseHelper) {
    var startTime by remember { mutableStateOf(db.getStartTime(gameId)) }
    var baseElapsedMs by remember { mutableStateOf(db.getElapsedTime(gameId)) }
    var currentElapsedMs by remember { mutableStateOf(0L) }
    var isRunning by remember { mutableStateOf(startTime > 0L) }
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(startTime, isRunning) {
        if (isRunning && startTime > 0L) {
            while (isRunning) {
                currentElapsedMs = System.currentTimeMillis() - startTime
                delay(1000L)
            }
        } else {
            currentElapsedMs = 0L
        }
    }

    val totalElapsedMs = baseElapsedMs + currentElapsedMs
    val hours = totalElapsedMs / 3_600_000L
    val minutes = (totalElapsedMs % 3_600_000L) / 60_000L
    val seconds = (totalElapsedMs % 60_000L) / 1_000L
    val timeString = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.dialog_timer_reset_title)) },
            text = { Text(stringResource(R.string.dialog_timer_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    db.setStartTime(gameId, 0L)
                    db.setElapsedTime(gameId, 0L)
                    startTime = 0L
                    baseElapsedMs = 0L
                    currentElapsedMs = 0L
                    isRunning = false
                    showResetDialog = false
                }) {
                    Text(stringResource(R.string.btn_confirm), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = timeString,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) colorResource(R.color.color_primary) else colorResource(R.color.color_text_secondary)
                )
                if (totalElapsedMs > 0L) {
                    Text(
                        text = if (isRunning) stringResource(R.string.timer_label_running) else stringResource(R.string.timer_label_paused),
                        fontSize = 12.sp,
                        color = colorResource(R.color.color_text_secondary)
                    )
                }
            }

            Row {
                if (!isRunning) {
                    // Start or Resume
                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            db.setStartTime(gameId, now)
                            startTime = now
                            isRunning = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_green)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (totalElapsedMs == 0L) stringResource(R.string.timer_btn_start) else stringResource(R.string.timer_btn_resume),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Pause
                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            val sessionElapsed = now - startTime
                            val newTotalElapsed = baseElapsedMs + sessionElapsed
                            db.setElapsedTime(gameId, newTotalElapsed)
                            db.setStartTime(gameId, 0L)
                            baseElapsedMs = newTotalElapsed
                            startTime = 0L
                            currentElapsedMs = 0L
                            isRunning = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(stringResource(R.string.timer_btn_pause), fontWeight = FontWeight.Bold)
                    }
                }

                if (totalElapsedMs > 0L) {
                    // Reset
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.timer_btn_reset), color = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreCell(
    text: String,
    width: Dp,
    isHeader: Boolean,
    fontWeight: FontWeight = FontWeight.Normal,
    textColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (isHeader) 14.sp else 16.sp,
            fontWeight = if (isHeader) FontWeight.Bold else fontWeight,
            color = if (isHeader) Color.White else (if (textColor == Color.Unspecified) colorResource(R.color.color_text_primary) else textColor)
        )
    }
}

@Composable
private fun HubButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
