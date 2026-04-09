package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class GameHubActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val gameId = intent.getLongExtra("gameId", -1)
        val gameOpponent = intent.getStringExtra("gameOpponent") ?: ""
        val gameDate = intent.getStringExtra("gameDate") ?: ""

        val db = DatabaseHelper(this)
        val teamName = db.getGame(gameId)?.teamId?.let { db.getTeamById(it)?.name } ?: ""

        setContent {
            GameHubScreen(
                gameId = gameId,
                gameOpponent = gameOpponent,
                gameDate = gameDate,
                teamName = teamName,
                db = db,
                onBackClick = { finish() },
                onOffenseClick = {
                    val intent = Intent(this, BattingTrackActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    }
                    startActivity(intent)
                },
                onDefenseClick = {
                    val intent = Intent(this, PitcherListActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    }
                    startActivity(intent)
                },
                onLineupClick = {
                    val intent = Intent(this, OwnLineupActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    }
                    startActivity(intent)
                },
                onOppoLineupClick = {
                    val intent = Intent(this, OpponentLineupActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("opponentName", gameOpponent)
                    }
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
    gameOpponent: String,
    gameDate: String,
    teamName: String,
    db: DatabaseHelper,
    onBackClick: () -> Unit,
    onOffenseClick: () -> Unit,
    onDefenseClick: () -> Unit,
    onLineupClick: () -> Unit,
    onOppoLineupClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(gameOpponent, style = MaterialTheme.typography.titleLarge)
                        Text(gameDate, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color(0xFFF5F5F5)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Scoreboard(
                    gameId = gameId,
                    awayTeam = gameOpponent,
                    homeTeam = teamName,
                    db = db
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HubButton(
                        text = stringResource(R.string.gamehub_offense),
                        color = Color(0xFF1a5fa8),
                        onClick = onOffenseClick
                    )
                    HubButton(
                        text = stringResource(R.string.gamehub_defense),
                        color = Color(0xFFc0392b),
                        onClick = onDefenseClick
                    )
                    HubButton(
                        text = stringResource(R.string.gamehub_lineup),
                        color = Color(0xFF2c7a2c),
                        onClick = onLineupClick
                    )
                    HubButton(
                        text = stringResource(R.string.gamehub_oppo_lineup),
                        color = Color(0xFF7d3c98),
                        onClick = onOppoLineupClick
                    )
                }
            }
        }
    }
}

@Composable
private fun Scoreboard(
    gameId: Long,
    awayTeam: String,
    homeTeam: String,
    db: DatabaseHelper
) {
    var scoreboardRuns by remember { mutableStateOf(db.getScoreboard(gameId)) }
    var editTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (inning, isHome)
    var inputValue by remember { mutableStateOf("") }

    val totalInnings = 9
    val scrollState = rememberScrollState()

    fun runsFor(inning: Int, isHome: Int): Int =
        scoreboardRuns.find { it.inning == inning && it.isHome == isHome }?.runs ?: 0

    fun hasEntry(inning: Int, isHome: Int): Boolean =
        scoreboardRuns.any { it.inning == inning && it.isHome == isHome }

    fun totalFor(isHome: Int): Int =
        (1..totalInnings).sumOf { runsFor(it, isHome) }

    editTarget?.let { (inning, isHome) ->
        val teamLabel = if (isHome == 1) homeTeam else awayTeam
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(stringResource(R.string.scoreboard_dialog_title, inning, teamLabel)) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { v -> inputValue = v.filter { it.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.scoreboard_runs_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    db.upsertScoreboardRun(gameId, inning, isHome, inputValue.toIntOrNull() ?: 0)
                    scoreboardRuns = db.getScoreboard(gameId)
                    editTarget = null
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Fixed left column: team names
            Column {
                ScoreCell(text = "", width = 90.dp, isHeader = true)
                ScoreCell(
                    text = awayTeam.ifBlank { stringResource(R.string.scoreboard_away) },
                    width = 90.dp,
                    fontWeight = FontWeight.Bold
                )
                ScoreCell(
                    text = homeTeam.ifBlank { stringResource(R.string.scoreboard_home) },
                    width = 90.dp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Scrollable inning columns
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                for (inning in 1..totalInnings) {
                    Column {
                        ScoreCell(text = inning.toString(), isHeader = true)
                        ScoreCell(
                            text = if (hasEntry(inning, 0)) runsFor(inning, 0).toString() else "-",
                            onClick = {
                                inputValue = runsFor(inning, 0).let { if (it > 0) it.toString() else "" }
                                editTarget = Pair(inning, 0)
                            }
                        )
                        ScoreCell(
                            text = if (hasEntry(inning, 1)) runsFor(inning, 1).toString() else "-",
                            onClick = {
                                inputValue = runsFor(inning, 1).let { if (it > 0) it.toString() else "" }
                                editTarget = Pair(inning, 1)
                            }
                        )
                    }
                }
            }

            // Fixed right column: totals
            Column {
                ScoreCell(text = stringResource(R.string.scoreboard_total_label), isHeader = true, width = 36.dp, fontWeight = FontWeight.Bold)
                ScoreCell(text = totalFor(0).toString(), width = 36.dp, fontWeight = FontWeight.Bold, bgColor = Color(0xFFF0F0F0))
                ScoreCell(text = totalFor(1).toString(), width = 36.dp, fontWeight = FontWeight.Bold, bgColor = Color(0xFFF0F0F0))
            }
        }
    }
}

@Composable
private fun ScoreCell(
    text: String,
    width: Dp = 36.dp,
    isHeader: Boolean = false,
    fontWeight: FontWeight = FontWeight.Normal,
    bgColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null
) {
    val background = if (isHeader) Color(0xFF1A5FA8) else bgColor
    val textColor = if (isHeader) Color.White else Color(0xFF333333)

    Box(
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .background(background)
            .border(0.5.dp, Color(0xFFCCCCCC))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun HubButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .padding(vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
