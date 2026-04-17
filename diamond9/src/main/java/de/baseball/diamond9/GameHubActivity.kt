package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.baseball.diamond9.DatabaseHelper
import kotlinx.coroutines.delay
import java.util.*

class GameHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
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
                ownTeamId = ownTeamId,
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
                    val intent = Intent(this, PitcherListActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", opponent)
                        putExtra("gameDate", date)
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
                onStats = {
                    startActivity(Intent(this, BatterStatsActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", opponent)
                        putExtra("gameDate", date)
                    })
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
    ownTeamId: Long,
    isHome: Int,
    db: DatabaseHelper,
    onBack: () -> Unit,
    onOffense: () -> Unit,
    onDefense: () -> Unit,
    onLineup: () -> Unit,
    onOpponentLineup: () -> Unit,
    onStats: () -> Unit
) {
    val leagueSettings = remember { db.getLeagueSettings(ownTeamId) }

    Scaffold(
        containerColor = colorResource(R.color.color_background),
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
                actions = {
                    IconButton(onClick = onStats) {
                        Icon(Icons.Default.ShowChart, contentDescription = stringResource(R.string.btn_stats))
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
            Scoreboard(gameId, ownTeam, opponent, isHome, leagueSettings.innings, db)

            GameTimer(gameId, db)

            HalfInningBar(gameId, db, isHome, leagueSettings.timeLimitMinutes, onOffense, onDefense)

            Spacer(modifier = Modifier.height(8.dp))

            HubButton(stringResource(R.string.gamehub_offense), colorResource(R.color.color_primary), onOffense)
            HubButton(stringResource(R.string.gamehub_defense), colorResource(R.color.color_primary), onDefense)
            HubButton(stringResource(R.string.gamehub_lineup), colorResource(R.color.color_primary), onLineup)
            HubButton(stringResource(R.string.gamehub_oppo_lineup), colorResource(R.color.color_primary), onOpponentLineup)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Scoreboard(
    gameId: Long,
    ownTeam: String,
    opponent: String,
    isHome: Int,
    innings: Int,
    db: DatabaseHelper
) {
    val guestTeam = if (isHome == 1) opponent else ownTeam
    val homeTeam = if (isHome == 1) ownTeam else opponent

    // teamIndex: 0 = guest, 1 = home
    var editCell by remember { mutableStateOf<Pair<Int, Int>?>(null) } // teamIndex to inning
    var currentInning by remember { mutableStateOf(db.getHalfInningState(gameId).inning) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentInning = db.getHalfInningState(gameId).inning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // runs[teamIndex][inning-1]
    val runs = remember { mutableStateOf(Array(2) { t -> IntArray(innings) { inn -> db.getScoreboardRuns(gameId, inn + 1, t) } }) }
    val hasEntry = remember { mutableStateOf(Array(2) { t -> BooleanArray(innings) { inn -> db.hasScoreboardEntry(gameId, inn + 1, t) } }) }

    fun reload() {
        runs.value = Array(2) { t -> IntArray(innings) { inn -> db.getScoreboardRuns(gameId, inn + 1, t) } }
        hasEntry.value = Array(2) { t -> BooleanArray(innings) { inn -> db.hasScoreboardEntry(gameId, inn + 1, t) } }
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
        Column(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(modifier = Modifier.background(colorResource(R.color.color_primary))) {
                ScoreCell("", 100.dp, true)
                (1..innings).forEach { ScoreCell(it.toString(), 30.dp, true) }
                ScoreCell(stringResource(R.string.scoreboard_total_label), 40.dp, true)
            }
            // Guest Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreCell(guestTeam, 100.dp, false, FontWeight.Bold, startPadding = 8.dp)
                (1..innings).forEach { inn ->
                    val r = if (hasEntry.value[0][inn - 1]) runs.value[0][inn - 1].toString() else "-"
                    ScoreCell(r, 30.dp, false, onClick = { editCell = Pair(0, inn) }, highlightEdge = if (inn == currentInning) "top" else "none")
                }
                ScoreCell(totalFor(0).toString(), 40.dp, false, FontWeight.Bold)
            }
            HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
            // Home Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreCell(homeTeam, 100.dp, false, FontWeight.Bold, startPadding = 8.dp)
                (1..innings).forEach { inn ->
                    val r = if (hasEntry.value[1][inn - 1]) runs.value[1][inn - 1].toString() else "-"
                    ScoreCell(r, 30.dp, false, onClick = { editCell = Pair(1, inn) }, highlightEdge = if (inn == currentInning) "bottom" else "none")
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
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
                        modifier = Modifier.widthIn(min = 96.dp).padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (totalElapsedMs == 0L) stringResource(R.string.timer_btn_start) else stringResource(R.string.timer_btn_resume),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        modifier = Modifier.widthIn(min = 96.dp).padding(end = 4.dp)
                    ) {
                        Text(stringResource(R.string.timer_btn_pause), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (totalElapsedMs > 0L) {
                    // Reset
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.timer_btn_reset), color = Color.Red, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onClick: (() -> Unit)? = null,
    highlightEdge: String = "none",
    startPadding: Dp = 0.dp
) {
    val hlColor = colorResource(R.color.color_primary)
    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .then(if (highlightEdge != "none") Modifier.drawBehind {
                val stroke = 2.dp.toPx()
                val h = stroke / 2
                drawLine(hlColor, Offset(h, 0f), Offset(h, size.height), stroke)
                drawLine(hlColor, Offset(size.width - h, 0f), Offset(size.width - h, size.height), stroke)
                if (highlightEdge == "top") drawLine(hlColor, Offset(0f, h), Offset(size.width, h), stroke)
                else drawLine(hlColor, Offset(0f, size.height - h), Offset(size.width, size.height - h), stroke)
            } else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = startPadding),
        contentAlignment = if (startPadding > 0.dp) Alignment.CenterStart else Alignment.Center
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
        Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HalfInningBar(
    gameId: Long,
    db: DatabaseHelper,
    isHome: Int,
    timeLimitMinutes: Int?,
    onOffense: () -> Unit,
    onDefense: () -> Unit
) {
    var state by remember { mutableStateOf(db.getHalfInningState(gameId)) }
    var showEditDialog by remember { mutableStateOf(false) }
    var pendingState by remember { mutableStateOf<HalfInningState?>(null) }
    var showTimeLimitSheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = db.getHalfInningState(gameId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun applyState(newState: HalfInningState) {
        db.updateHalfInning(gameId, newState.inning, newState.isTopHalf)
        state = newState
    }

    if (showEditDialog) {
        ManualHalfInningDialog(
            current = state,
            onConfirm = { newState ->
                showEditDialog = false
                if (timeLimitMinutes != null && newState.inning > state.inning) {
                    val limitMs = timeLimitMinutes * 60_000L
                    if (db.getTotalElapsedMs(gameId) >= limitMs) {
                        pendingState = newState
                        showTimeLimitSheet = true
                        return@ManualHalfInningDialog
                    }
                }
                applyState(newState)
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showTimeLimitSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showTimeLimitSheet = false
                pendingState = null
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.gamehub_time_limit_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.color_strike)
                )
                Text(
                    text = stringResource(R.string.gamehub_time_limit_message, timeLimitMinutes!!),
                    fontSize = 14.sp,
                    color = colorResource(R.color.color_text_primary)
                )
                Button(
                    onClick = {
                        pendingState?.let { applyState(it) }
                        showTimeLimitSheet = false
                        pendingState = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(R.color.color_strike),
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.gamehub_time_limit_override), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        showTimeLimitSheet = false
                        pendingState = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.gamehub_time_limit_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    val isOurOffense = (state.isTopHalf && isHome == 0) || (!state.isTopHalf && isHome == 1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(
                color = colorResource(R.color.color_primary),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = state.label,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = if (isOurOffense) onOffense else onDefense,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = stringResource(if (isOurOffense) R.string.gamehub_offense else R.string.gamehub_defense),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { showEditDialog = true }) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.content_desc_edit_half_inning),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun ManualHalfInningDialog(
    current: HalfInningState,
    onConfirm: (HalfInningState) -> Unit,
    onDismiss: () -> Unit
) {
    var inning by remember { mutableStateOf(current.inning) }
    var isTop by remember { mutableStateOf(current.isTopHalf) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.half_inning_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Inning stepper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (inning > 1) inning-- },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("−", fontSize = 20.sp) }
                    Text(
                        text = "Inning $inning",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { if (inning < 15) inning++ },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("+", fontSize = 20.sp) }
                }
                // Top / Bottom toggle
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isTop = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTop) colorResource(R.color.color_primary) else Color.LightGray
                        )
                    ) { Text(stringResource(R.string.half_inning_label_top), color = Color.White) }
                    Button(
                        onClick = { isTop = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isTop) colorResource(R.color.color_primary) else Color.LightGray
                        )
                    ) { Text(stringResource(R.string.half_inning_label_bot), color = Color.White) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(HalfInningState(inning, isTop)) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
