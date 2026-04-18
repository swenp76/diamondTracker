package de.baseball.diamond9

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch

class PitchTrackActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper
    private var pitcherId: Long = -1
    private var gameId: Long = -1

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        pitcherId = intent.getLongExtra("pitcherId", -1)
        gameId = intent.getLongExtra("gameId", -1)
        val pitcherName = intent.getStringExtra("pitcherName") ?: getString(R.string.pitcher_default_name)

        db = DatabaseHelper(this)

        // Pitcherwechsel: offenen At-Bat vom Vorgänger übernehmen
        if (gameId != -1L && db.getPitchesForPitcher(pitcherId).isEmpty()) {
            db.getIncompleteAtBatBeforePitcher(gameId, pitcherId).forEach { type ->
                db.insertPitch(pitcherId, type)
            }
        }

        setContent {
            PitchTrackScreen(pitcherName)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PitchTrackScreen(pitcherName: String) {
        var stats by remember { mutableStateOf(db.getStatsForPitcher(pitcherId)) }
        val listState = rememberLazyListState()
        var inning by remember { mutableStateOf(1) }
        var outs by remember { mutableStateOf(0) }
        var showInningSnackbar by remember { mutableStateOf(false) }
        var showTrendSheet by remember { mutableStateOf(false) }
        var showHitSheet by remember { mutableStateOf(false) }
        var showOutSheet by remember { mutableStateOf(false) }
        var showHalfInningSheet by remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var halfInningState by remember { mutableStateOf(db.getHalfInningState(gameId)) }
        val actionStack = remember { GameActionStack() }

        // Captured before 3rd out, used by HalfInningChange undo
        var prevLeadoffForHalfInning by remember { mutableStateOf(1) }
        var prevInningForHalfInning by remember { mutableStateOf(1) }

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

        fun refresh() {
            stats = db.getStatsForPitcher(pitcherId)
        }

        /** Out-button: batter was put out (BF already inserted before this call). */
        fun recordBatterOut() {
            val savedInning = inning
            val savedOuts = outs
            val newOuts = savedOuts + 1
            if (newOuts >= 3) {
                prevLeadoffForHalfInning = if (gameId != -1L) db.getLeadoffSlot(gameId) else 1
                prevInningForHalfInning = savedInning
                inning++
                outs = 0
                showHalfInningSheet = true
            } else {
                outs = newOuts
            }
            if (gameId != -1L) db.updateGameState(gameId, inning, outs)
            actionStack.push(GameAction.BatterOut(
                completedAtBatId = -1L,  // defense doesn't use at-bat IDs
                completedSlot = -1,
                prevInning = savedInning,
                prevOuts = savedOuts
            ))
        }

        /** Outs-indicator tap: runner was put out, batter stays, count resets. */
        fun recordRunnerOut() {
            val savedInning = inning
            val savedOuts = outs
            // Insert "RO" marker to reset the current at-bat pitch count display
            db.insertPitch(pitcherId, "RO", inning)
            val newOuts = savedOuts + 1
            if (newOuts >= 3) {
                prevLeadoffForHalfInning = if (gameId != -1L) db.getLeadoffSlot(gameId) else 1
                prevInningForHalfInning = savedInning
                inning++
                outs = 0
                showHalfInningSheet = true
            } else {
                outs = newOuts
            }
            if (gameId != -1L) db.updateGameState(gameId, inning, outs)
            actionStack.push(GameAction.RunnerOut(prevInning = savedInning, prevOuts = savedOuts))
            refresh()
        }

        val opponentLineupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val jumpToSlot = result.data?.getIntExtra("jumpToSlot", -1) ?: -1
                if (jumpToSlot != -1) {
                    val gameBF = if (gameId != -1L) db.getTotalBFForGame(gameId) else stats.bf
                    val currentSlot = (gameBF % 9) + 1

                    if (currentSlot != jumpToSlot) {
                        val lastPitches = stats.pitches.takeLastWhile { it.type != "BF" }
                        if (lastPitches.isNotEmpty()) {
                            db.insertPitch(pitcherId, "BF", inning)
                            recordBatterOut()
                        }

                        var newGameBF = if (gameId != -1L) db.getTotalBFForGame(gameId) else stats.bf
                        var nextSlot = (newGameBF % 9) + 1
                        while (nextSlot != jumpToSlot) {
                            db.insertPitch(pitcherId, "BF", inning)
                            newGameBF = if (gameId != -1L) db.getTotalBFForGame(gameId) else stats.bf
                            nextSlot = (newGameBF % 9) + 1
                        }
                    }
                }
                refresh()
            }
        }

        LaunchedEffect(Unit) {
            if (gameId != -1L) {
                val (i, o) = db.getGameState(gameId)
                inning = i
                outs = o
            }
        }

        LaunchedEffect(showInningSnackbar) {
            if (showInningSnackbar) {
                snackbarHostState.showSnackbar(
                    message = getString(R.string.snackbar_inning_starts, inning)
                )
                showInningSnackbar = false
            }
        }

        Scaffold(
            containerColor = colorResource(R.color.color_background),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(pitcherName)
                            Text(
                                text = halfInningState.shortLabel,
                                fontSize = 13.sp,
                                color = colorResource(R.color.color_text_secondary)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = colorResource(R.color.color_text_primary),
                        navigationIconContentColor = colorResource(R.color.color_text_primary)
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(colorResource(R.color.color_background))
            ) {
                StatsBar(stats, inning, outs, onRunnerOut = { recordRunnerOut() })
                BatterStrip(stats, opponentLineupLauncher)
                Box(modifier = Modifier.weight(1f)) {
                    PitchLog(stats, listState)
                }
                ActionButtons(
                    onBall = {
                        val (ballsBefore, _) = currentAtBatCount(stats.pitches)
                        db.insertPitch(pitcherId, "B", inning)
                        actionStack.push(GameAction.Pitch(-1L))
                        if (ballsBefore >= 3) {
                            db.insertPitch(pitcherId, "W", inning)
                            db.insertPitch(pitcherId, "BF", inning)
                        }
                        refresh()
                    },
                    onStrike = {
                        val (_, strikesBefore) = currentAtBatCount(stats.pitches)
                        db.insertPitch(pitcherId, "S", inning)
                        actionStack.push(GameAction.Pitch(-1L))
                        if (strikesBefore >= 2) {
                            db.insertPitch(pitcherId, "SO", inning)
                            db.insertPitch(pitcherId, "BF", inning)
                            recordBatterOut()
                        }
                        refresh()
                    },
                    onShowHitSheet = { showHitSheet = true },
                    onFoul = {
                        db.insertPitch(pitcherId, "F", inning)
                        actionStack.push(GameAction.Pitch(-1L))
                        refresh()
                    },
                    onHbp = {
                        db.insertPitch(pitcherId, "HBP", inning)
                        db.insertPitch(pitcherId, "BF", inning)
                        actionStack.push(GameAction.Pitch(-1L))
                        refresh()
                    },
                    onBf = {
                        db.insertPitch(pitcherId, "BF", inning)
                        actionStack.push(GameAction.Pitch(-1L))
                        refresh()
                    },
                    onUndo = {
                        when (val action = actionStack.pop()) {
                            is GameAction.Pitch -> {
                                db.undoLastPitch(pitcherId)
                                refresh()
                            }
                            is GameAction.BatterOut -> {
                                // undo the BF + out-type pitch, restore out counter
                                db.undoLastPitch(pitcherId) // BF
                                db.undoLastPitch(pitcherId) // GO/FO/SO
                                inning = action.prevInning
                                outs = action.prevOuts
                                if (gameId != -1L) db.updateGameState(gameId, inning, outs)
                                refresh()
                            }
                            is GameAction.RunnerOut -> {
                                // undo the RO marker, restore out counter
                                db.undoLastPitch(pitcherId) // RO
                                inning = action.prevInning
                                outs = action.prevOuts
                                if (gameId != -1L) db.updateGameState(gameId, inning, outs)
                                refresh()
                            }
                            is GameAction.HalfInningChange -> {
                                db.updateHalfInning(gameId, action.prevState.inning, action.prevState.isTopHalf)
                                inning = action.prevInning
                                outs = 2
                                if (gameId != -1L) db.updateGameState(gameId, inning, outs)
                                halfInningState = action.prevState
                            }
                            null -> {
                                // fallback: plain pitch undo
                                db.undoLastPitch(pitcherId)
                                refresh()
                            }
                        }
                    },
                    onShowOutSheet = { showOutSheet = true },
                    onShowTrend = { showTrendSheet = true }
                )
            }
        }

        if (showHalfInningSheet) {
            val suggested = HalfInningManager.next(halfInningState)
            ModalBottomSheet(onDismissRequest = { showHalfInningSheet = false }) {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.half_inning_change_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.half_inning_next_label, suggested.label),
                        fontSize = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val prevState = halfInningState
                                db.updateHalfInning(gameId, suggested.inning, suggested.isTopHalf)
                                halfInningState = suggested
                                showHalfInningSheet = false
                                actionStack.push(GameAction.HalfInningChange(
                                    prevState = prevState,
                                    prevLeadoffSlot = prevLeadoffForHalfInning,
                                    prevInning = prevInningForHalfInning
                                ))
                                val resultIntent = Intent().apply {
                                    putExtra(GameHubActivity.EXTRA_NEXT_IS_OFFENSE, true)
                                }
                                setResult(GameHubActivity.RESULT_HALF_INNING_SWITCHED, resultIntent)
                                finish()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_primary))
                        ) { Text(stringResource(R.string.half_inning_confirm), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        OutlinedButton(
                            onClick = { showHalfInningSheet = false },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.half_inning_keep_going), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }

        if (showTrendSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTrendSheet = false },
                containerColor = Color.White
            ) {
                PitcherTrendSheet(stats)
            }
        }

        if (showHitSheet) {
            ModalBottomSheet(
                onDismissRequest = { showHitSheet = false },
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        listOf("1B", "2B", "3B", "HR").forEachIndexed { i, type ->
                            if (i > 0) Spacer(modifier = Modifier.width(8.dp))
                            val color = if (type == "HR") colorResource(R.color.color_hit_homer) else colorResource(R.color.color_hit)
                            Button(
                                onClick = {
                                    db.insertPitch(pitcherId, type, inning)
                                    db.insertPitch(pitcherId, "BF", inning)
                                    refresh()
                                    showHitSheet = false
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                colors = ButtonDefaults.buttonColors(containerColor = color),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(type, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        if (showOutSheet) {
            ModalBottomSheet(
                onDismissRequest = { showOutSheet = false },
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        listOf("GO", "FO", "LO").forEachIndexed { i, label ->
                            if (i > 0) Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    db.insertPitch(pitcherId, label, inning)
                                    db.insertPitch(pitcherId, "BF", inning)
                                    recordBatterOut()
                                    refresh()
                                    showOutSheet = false
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun StatsBar(stats: PitcherStats, inning: Int, outs: Int, onRunnerOut: () -> Unit) {
        val outsDesc = stringResource(R.string.content_desc_outs_indicator)
        val date = if (gameId != -1L) db.getGame(gameId)?.date ?: "" else ""
        val totalBF = if (stats.pitcher.playerId > 0 && date.isNotEmpty())
            db.getTotalBFForPlayerOnDate(stats.pitcher.playerId, date)
        else
            stats.bf

        val (atBatBalls, atBatStrikes) = currentAtBatCount(stats.pitches)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(stringResource(R.string.stat_bf), totalBF.toString(), colorResource(R.color.color_text_primary))
                StatItem(stringResource(R.string.stat_balls), stats.balls.toString(), colorResource(R.color.color_primary))
                StatItem(stringResource(R.string.stat_strikes), stats.strikes.toString(), colorResource(R.color.color_strike))
                StatItem(stringResource(R.string.stat_walks), stats.walks.toString(), colorResource(R.color.color_walk))
                StatItem(stringResource(R.string.stat_hbp), stats.hbp.toString(), colorResource(R.color.color_hbp))
                StatItem(stringResource(R.string.stat_pitch), (stats.totalPitches + 1).toString(), colorResource(R.color.color_text_primary))
                StatItem(stringResource(R.string.stat_count), "$atBatBalls-$atBatStrikes", colorResource(R.color.color_primary))
            }
            HorizontalDivider(color = colorResource(R.color.color_divider_light))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_inning, inning),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.color_text_primary)
                )
                // Outs dots — tappable for runner-out
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = onRunnerOut)
                        .semantics { contentDescription = outsDesc }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_outs),
                        fontSize = 14.sp,
                        color = colorResource(R.color.color_text_secondary)
                    )
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(14.dp)
                                .background(
                                    color = if (index < outs) colorResource(R.color.color_strike) else colorResource(R.color.color_divider),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = colorResource(R.color.color_text_secondary))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }

    @Composable
    fun BatterStrip(stats: PitcherStats, opponentLineupLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        val context = LocalContext.current
        val gameBF = if (gameId != -1L) db.getTotalBFForGame(gameId) else stats.bf
        val currentBattingOrder = (gameBF % 9) + 1
        val jerseyDisplay = getBatterJersey(currentBattingOrder)
        val batterText = if (jerseyDisplay.isNotEmpty())
            stringResource(R.string.label_batter_slot_with_jersey, jerseyDisplay, currentBattingOrder)
        else
            stringResource(R.string.label_batter_slot, currentBattingOrder)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.color_primary))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.label_batter), fontSize = 13.sp, color = colorResource(R.color.color_primary_light))
            Spacer(modifier = Modifier.width(8.dp))
            Text(batterText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.hint_lineup),
                fontSize = 12.sp,
                color = colorResource(R.color.color_primary_light),
                modifier = Modifier
                    .clickable {
                        if (gameId != -1L) {
                            val i = Intent(context, OpponentLineupActivity::class.java)
                            i.putExtra("gameId", gameId)
                            val opponentTeamName = db.getGame(gameId)?.opponent ?: stats.pitcher.name
                            i.putExtra("opponentName", opponentTeamName)
                            opponentLineupLauncher.launch(i)
                        }
                    }
                    .padding(8.dp)
            )
        }
    }

    @Composable
    fun PitchLog(stats: PitcherStats, listState: androidx.compose.foundation.lazy.LazyListState) {
        val gameBF = if (gameId != -1L) db.getTotalBFForGame(gameId) else stats.bf

        // Count total pitches to maintain correct numbering even when reversed
        val totalPitchesCount = stats.pitches.count { it.type == "B" || it.type == "S" || it.type == "F" }

        // Reverse order for display
        val displayItems = stats.pitches.reversed()

        var bfCount = gameBF
        var pitchNumber = totalPitchesCount

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayItems) { pitch ->
                when (pitch.type) {
                    "BF" -> {
                        val battingOrder = (bfCount % 9) + 1
                        val nextJersey = getBatterJersey(battingOrder)
                        val batterLabel = if (nextJersey.isNotEmpty())
                            stringResource(R.string.pitch_label_slot_with_jersey, nextJersey, battingOrder)
                        else
                            stringResource(R.string.pitch_label_slot, battingOrder)

                        Text(batterLabel, color = colorResource(R.color.color_text_secondary), fontSize = 11.sp)
                        bfCount--
                    }
                    "HBP" -> Text(stringResource(R.string.pitch_label_hbp), color = colorResource(R.color.color_hbp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "H"  -> Text(stringResource(R.string.pitch_label_hit), color = colorResource(R.color.color_hit), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "1B" -> Text(stringResource(R.string.pitch_label_1b), color = colorResource(R.color.color_hit), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "2B" -> Text(stringResource(R.string.pitch_label_2b), color = colorResource(R.color.color_hit), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "3B" -> Text(stringResource(R.string.pitch_label_3b), color = colorResource(R.color.color_hit), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "HR" -> Text(stringResource(R.string.pitch_label_hr), color = colorResource(R.color.color_hit_homer), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "GO" -> Text(stringResource(R.string.pitch_label_go), color = colorResource(R.color.color_text_secondary), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "FO" -> Text(stringResource(R.string.pitch_label_fo), color = colorResource(R.color.color_text_secondary), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "W"  -> Text(stringResource(R.string.pitch_label_walk), color = colorResource(R.color.color_walk), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "RO" -> Text(stringResource(R.string.pitch_label_runner_out), color = colorResource(R.color.color_text_secondary), fontSize = 11.sp)
                    "SO" -> Text(stringResource(R.string.pitch_label_strikeout), color = colorResource(R.color.color_green_bright), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "B", "S", "F" -> {
                        val currentNum = pitchNumber
                        pitchNumber--
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$currentNum.", fontSize = 12.sp, color = colorResource(R.color.color_text_tertiary), modifier = Modifier.width(32.dp))
                            val label = when (pitch.type) {
                                "B" -> stringResource(R.string.pitch_label_ball)
                                "F" -> stringResource(R.string.pitch_label_foul)
                                else -> stringResource(R.string.pitch_label_strike)
                            }
                            val bgColor = when (pitch.type) {
                                "B" -> colorResource(R.color.color_primary)
                                "F" -> colorResource(R.color.color_foul)
                                else -> colorResource(R.color.color_strike)
                            }
                            Surface(
                                color = bgColor,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ActionButtons(
        onBall: () -> Unit,
        onStrike: () -> Unit,
        onShowHitSheet: () -> Unit,
        onFoul: () -> Unit,
        onHbp: () -> Unit,
        onBf: () -> Unit,
        onUndo: () -> Unit,
        onShowOutSheet: () -> Unit,
        onShowTrend: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onShowTrend) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = stringResource(R.string.content_desc_trend),
                            tint = colorResource(R.color.color_primary)
                        )
                    }
                }
                Row(modifier = Modifier.height(80.dp)) {
                    Button(
                        onClick = onBall,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_primary)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_ball), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onStrike,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_strike), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.height(64.dp)) {
                    Button(
                        onClick = onShowHitSheet,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_hit)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_hit), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onHbp,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_hbp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_hbp), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onFoul,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_foul)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_foul), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.height(56.dp)) {
                    Button(
                        onClick = onBf,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_green_dark)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_add_batter), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onShowOutSheet,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_out), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onUndo,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_text_secondary)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_undo), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    private fun currentAtBatCount(pitches: List<Pitch>): Pair<Int, Int> {
        val lastBf = pitches.indexOfLast { it.type == "BF" || it.type == "RO" }
        val current = if (lastBf == -1) pitches else pitches.drop(lastBf + 1)
        var balls = 0
        var strikes = 0
        for (p in current) {
            when (p.type) {
                "B" -> balls++
                "S" -> strikes++
                "F" -> if (strikes < 2) strikes++
            }
        }
        return Pair(minOf(balls, 3), minOf(strikes, 2))
    }

    private fun getBatterJersey(battingOrder: Int): String {
        if (gameId == -1L) return ""
        return db.getJerseyAtBattingOrder(gameId, battingOrder)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PitcherTrendSheet(stats: PitcherStats) {
        val batters = remember(stats.pitches) { buildBatterStats(stats.pitches) }
        val rolling = remember(batters) { rollingAverage(batters) }
        val trendLevel = remember(batters) { getTrendLevel(batters) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.trend_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.color_text_primary),
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (batters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.trend_no_data),
                        color = colorResource(R.color.color_text_secondary),
                        fontSize = 14.sp
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        com.github.mikephil.charting.charts.LineChart(ctx).apply {
                            description.isEnabled = false
                            legend.isEnabled = false
                            setTouchEnabled(false)
                            setDrawGridBackground(false)
                            axisRight.isEnabled = false

                            xAxis.apply {
                                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                                granularity = 1f
                                textColor = android.graphics.Color.parseColor("#888888")
                                textSize = 10f
                                setDrawGridLines(false)
                            }

                            axisLeft.apply {
                                axisMinimum = 0f
                                axisMaximum = 1f
                                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                                    override fun getFormattedValue(value: Float) = "${(value * 100).toInt()}%"
                                }
                                textColor = android.graphics.Color.parseColor("#888888")
                                textSize = 10f
                                addLimitLine(
                                    com.github.mikephil.charting.components.LimitLine(0.6f, "60%").apply {
                                        lineColor = android.graphics.Color.parseColor("#27AE60")
                                        lineWidth = 1f
                                        textColor = android.graphics.Color.parseColor("#27AE60")
                                        textSize = 9f
                                        labelPosition = com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_TOP
                                    }
                                )
                                addLimitLine(
                                    com.github.mikephil.charting.components.LimitLine(0.4f, "40%").apply {
                                        lineColor = android.graphics.Color.parseColor("#E67E22")
                                        lineWidth = 1f
                                        textColor = android.graphics.Color.parseColor("#E67E22")
                                        textSize = 9f
                                        labelPosition = com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_BOTTOM
                                    }
                                )
                            }
                        }
                    },
                    update = { chart ->
                        val rawEntries = batters.mapIndexed { i, b ->
                            com.github.mikephil.charting.data.Entry(i.toFloat(), b.strikePercent)
                        }
                        val rawSet = com.github.mikephil.charting.data.LineDataSet(rawEntries, "Strike%").apply {
                            color = android.graphics.Color.parseColor("#1A5FA8")
                            setCircleColor(android.graphics.Color.parseColor("#1A5FA8"))
                            circleRadius = 4f
                            lineWidth = 2f
                            setDrawValues(false)
                            mode = com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER
                        }

                        val rollingEntries = rolling.mapIndexed { i, v ->
                            com.github.mikephil.charting.data.Entry(i.toFloat(), v)
                        }
                        val rollingSet = com.github.mikephil.charting.data.LineDataSet(rollingEntries, "Trend").apply {
                            color = android.graphics.Color.parseColor("#888888")
                            lineWidth = 1.5f
                            setDrawCircles(false)
                            setDrawValues(false)
                            enableDashedLine(10f, 5f, 0f)
                        }

                        chart.data = com.github.mikephil.charting.data.LineData(rawSet, rollingSet)
                        chart.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val (bgColor, emoji, message) = when (trendLevel) {
                    TrendLevel.GOOD   -> Triple(colorResource(R.color.color_green_bright), "🟢", stringResource(R.string.trend_good))
                    TrendLevel.WATCH  -> Triple(colorResource(R.color.color_orange_bright), "🟡", stringResource(R.string.trend_watch))
                    TrendLevel.CHANGE -> Triple(colorResource(R.color.color_strike), "🔴", stringResource(R.string.trend_change))
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = bgColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = message,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = bgColor
                            )
                            if (batters.size >= 2) {
                                val last = batters.last().strikePercent
                                val prev = batters[batters.size - 2].strikePercent
                                val diff = last - prev
                                val arrow = when {
                                    diff > 0.05f -> "↑"
                                    diff < -0.05f -> "↓"
                                    else -> "→"
                                }
                                Text(
                                    text = stringResource(R.string.trend_last_bf, (last * 100).toInt(), arrow),
                                    fontSize = 12.sp,
                                    color = colorResource(R.color.color_text_tertiary)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.trend_per_batter),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.color_text_subtle),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                batters.forEach { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.trend_batter_nr, b.batterNr),
                            fontSize = 12.sp,
                            color = colorResource(R.color.color_text_tertiary)
                        )
                        Text(
                            text = "${b.balls}B ${b.strikes}S ${b.fouls}F",
                            fontSize = 12.sp,
                            color = colorResource(R.color.color_text_tertiary)
                        )
                        Text(
                            text = "${(b.strikePercent * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                b.strikePercent >= 0.6f -> colorResource(R.color.color_green_bright)
                                b.strikePercent >= 0.4f -> colorResource(R.color.color_orange_bright)
                                else -> colorResource(R.color.color_strike)
                            }
                        )
                    }
                }
            }
        }
    }
}
