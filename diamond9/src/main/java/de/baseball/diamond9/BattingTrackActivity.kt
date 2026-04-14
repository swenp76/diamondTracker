package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class BattingTrackActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper
    private var gameId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameId = intent.getLongExtra("gameId", -1)
        db = DatabaseHelper(this)

        setContent {
            BattingTrackScreen()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BattingTrackScreen() {
        var inning by remember { mutableStateOf(1) }
        var outs by remember { mutableStateOf(0) }
        var currentSlot by remember { mutableStateOf(1) }
        var currentAtBatId by remember { mutableStateOf<Long>(-1) }
        var pitches by remember { mutableStateOf(emptyList<Pitch>()) }
        var halfInningState by remember { mutableStateOf(db.getHalfInningState(gameId)) }
        var showHalfInningSheet by remember { mutableStateOf(false) }

        // Saved before the 3rd out is recorded; used by HalfInningChange undo
        var prevLeadoffForHalfInning by remember { mutableStateOf(1) }
        var prevInningForHalfInning by remember { mutableStateOf(1) }

        val actionStack = remember { GameActionStack() }
        val snackbarHostState = remember { SnackbarHostState() }

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

        var lineup by remember { mutableStateOf(emptyMap<Int, Player>()) }

        fun refreshLineup() { lineup = db.getEffectiveLineup(gameId) }

        fun startNewAtBat(slot: Int): Long {
            val player = lineup[slot]
            val newId = db.insertAtBat(gameId, player?.id ?: 0L, slot, inning)
            currentAtBatId = newId
            currentSlot = slot
            pitches = emptyList()
            return newId
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            refreshLineup()
            if (result.resultCode == RESULT_OK) {
                val jumpToSlot = result.data?.getIntExtra("jumpToSlot", -1) ?: -1
                if (jumpToSlot != -1) {
                    val currentAb = db.getAtBat(currentAtBatId)
                    if (currentAb != null && currentAb.result == null) {
                        val pitchesCount = db.getPitchesForAtBat(currentAtBatId).size
                        if (pitchesCount == 0) db.deleteAtBat(currentAtBatId)
                        else db.updateAtBatResult(currentAtBatId, "OUT")
                    }
                    startNewAtBat(jumpToSlot)
                }
            }
        }

        fun refreshAtBat(id: Long) {
            if (id != -1L) pitches = db.getPitchesForAtBat(id)
        }

        LaunchedEffect(gameId) {
            if (gameId != -1L) {
                refreshLineup()
                val (i, o) = db.getGameState(gameId)
                inning = i
                outs = o
                halfInningState = db.getHalfInningState(gameId)

                val existingAtBats = db.getAtBatsForGame(gameId)
                if (existingAtBats.isEmpty()) {
                    val leadoff = db.getLeadoffSlot(gameId)
                    currentSlot = leadoff
                    startNewAtBat(leadoff)
                } else {
                    val lastAb = existingAtBats.last()
                    if (lastAb.result == null) {
                        currentAtBatId = lastAb.id
                        currentSlot = lastAb.slot
                        refreshAtBat(lastAb.id)
                    } else {
                        val maxSlot = if (lineup.containsKey(10)) 10 else 9
                        val nextSlot = (lastAb.slot % maxSlot) + 1
                        startNewAtBat(nextSlot)
                    }
                }
            }
        }

        fun ensureAtBat(): Long {
            return if (currentAtBatId == -1L) {
                val existing = db.getAtBatsForGame(gameId)
                if (existing.isNotEmpty() && existing.last().result == null) {
                    currentAtBatId = existing.last().id
                    currentSlot = existing.last().slot
                    refreshAtBat(currentAtBatId)
                    currentAtBatId
                } else {
                    startNewAtBat(currentSlot)
                }
            } else currentAtBatId
        }

        fun nextBatter() {
            val maxSlot = if (lineup.containsKey(10)) 10 else 9
            startNewAtBat((currentSlot % maxSlot) + 1)
        }

        /** Called when the Out-button path records an out (batter out, advance batter). */
        fun recordBatterOut(result: String) {
            val abId = ensureAtBat()
            val slot = currentSlot
            val savedInning = inning
            val savedOuts = outs

            db.updateAtBatResult(abId, result)

            val newOuts = savedOuts + 1
            if (newOuts >= 3) {
                prevLeadoffForHalfInning = db.getLeadoffSlot(gameId)
                prevInningForHalfInning = savedInning
                val maxSlot = if (lineup.containsKey(10)) 10 else 9
                val nextLeadoff = (slot % maxSlot) + 1
                db.updateLeadoffSlot(gameId, nextLeadoff)
                inning++
                outs = 0
                showHalfInningSheet = true
            } else {
                outs = newOuts
            }
            db.updateGameState(gameId, inning, outs)

            actionStack.push(GameAction.BatterOut(
                completedAtBatId = abId,
                completedSlot = slot,
                prevInning = savedInning,
                prevOuts = savedOuts
            ))
            nextBatter()
        }

        /** Called when the outs-indicator is tapped (runner out, batter stays, count resets). */
        fun recordRunnerOut() {
            val savedAtBatId = currentAtBatId
            val savedSlot = currentSlot
            val savedInning = inning
            val savedOuts = outs

            // batter stays but count resets — start fresh at-bat for same slot
            val newAtBatId = startNewAtBat(currentSlot)

            val newOuts = savedOuts + 1
            if (newOuts >= 3) {
                prevLeadoffForHalfInning = db.getLeadoffSlot(gameId)
                prevInningForHalfInning = savedInning
                // leadoff = current batter (runner was out, not this batter)
                db.updateLeadoffSlot(gameId, savedSlot)
                inning++
                outs = 0
                showHalfInningSheet = true
            } else {
                outs = newOuts
            }
            db.updateGameState(gameId, inning, outs)

            actionStack.push(GameAction.RunnerOut(
                prevInning = savedInning,
                prevOuts = savedOuts,
                newAtBatId = newAtBatId,
                prevAtBatId = savedAtBatId,
                prevSlot = savedSlot
            ))
        }

        var showKSheet by remember { mutableStateOf(false) }
        var showBBSheet by remember { mutableStateOf(false) }

        // Half-inning suggestion sheet
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
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_primary))
                        ) { Text(stringResource(R.string.half_inning_confirm), fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { showHalfInningSheet = false },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.half_inning_keep_going)) }
                    }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.gamehub_offense))
                            Text(
                                text = halfInningState.shortLabel,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorResource(R.color.color_primary),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
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
                StatsBar(inning, outs, pitches, onRunnerOut = { recordRunnerOut() })
                BatterStrip(currentSlot, lineup[currentSlot], launcher)
                Box(modifier = Modifier.weight(1f)) {
                    PitchLog(pitches)
                }
                ActionButtons(
                    showKSheet = showKSheet,
                    onShowKSheet = { showKSheet = true },
                    onKSheetDismiss = { showKSheet = false },
                    showBBSheet = showBBSheet,
                    onShowBBSheet = { showBBSheet = true },
                    onBBSheetDismiss = { showBBSheet = false },
                    onBall = {
                        val abId = ensureAtBat()
                        db.insertPitchForAtBat(abId, "B", inning)
                        actionStack.push(GameAction.Pitch(abId))
                        val updatedPitches = db.getPitchesForAtBat(abId)
                        val (balls, _) = currentAtBatCount(updatedPitches)
                        if (balls >= 4) showBBSheet = true
                        else refreshAtBat(abId)
                    },
                    onStrike = {
                        val abId = ensureAtBat()
                        val currentPitches = db.getPitchesForAtBat(abId)
                        val (_, currentStrikes) = currentAtBatCount(currentPitches)
                        if (currentStrikes >= 2) {
                            showKSheet = true
                        } else {
                            db.insertPitchForAtBat(abId, "S", inning)
                            actionStack.push(GameAction.Pitch(abId))
                            refreshAtBat(abId)
                        }
                    },
                    onFoul = {
                        val abId = ensureAtBat()
                        db.insertPitchForAtBat(abId, "F", inning)
                        actionStack.push(GameAction.Pitch(abId))
                        refreshAtBat(abId)
                    },
                    onUndo = {
                        when (val action = actionStack.pop()) {
                            is GameAction.Pitch -> {
                                db.undoLastPitchForAtBat(action.atBatId)
                                currentAtBatId = action.atBatId
                                refreshAtBat(action.atBatId)
                            }
                            is GameAction.BatterOut -> {
                                // Delete the empty at-bat created by nextBatter()
                                if (currentAtBatId != -1L) db.deleteAtBat(currentAtBatId)
                                // Restore previous at-bat
                                db.updateAtBatResult(action.completedAtBatId, null)
                                currentAtBatId = action.completedAtBatId
                                currentSlot = action.completedSlot
                                inning = action.prevInning
                                outs = action.prevOuts
                                db.updateGameState(gameId, inning, outs)
                                refreshAtBat(currentAtBatId)
                            }
                            is GameAction.RunnerOut -> {
                                // Delete the reset at-bat, restore previous
                                if (action.newAtBatId != -1L) db.deleteAtBat(action.newAtBatId)
                                currentAtBatId = action.prevAtBatId
                                currentSlot = action.prevSlot
                                inning = action.prevInning
                                outs = action.prevOuts
                                db.updateGameState(gameId, inning, outs)
                                refreshAtBat(action.prevAtBatId)
                            }
                            is GameAction.HalfInningChange -> {
                                db.updateHalfInning(gameId, action.prevState.inning, action.prevState.isTopHalf)
                                db.updateLeadoffSlot(gameId, action.prevLeadoffSlot)
                                inning = action.prevInning
                                outs = 2  // 3rd out was what triggered the change
                                db.updateGameState(gameId, inning, outs)
                                halfInningState = action.prevState
                            }
                            null -> { /* nothing to undo */ }
                        }
                    },
                    onResult = { result ->
                        val abId = ensureAtBat()
                        when (result) {
                            "H", "1B", "2B", "3B", "HR" -> {
                                db.insertPitchForAtBat(abId, "H", inning)
                                actionStack.push(GameAction.Pitch(abId))
                            }
                            "HBP" -> {
                                db.insertPitchForAtBat(abId, "HBP", inning)
                                actionStack.push(GameAction.Pitch(abId))
                            }
                            "K"  -> {
                                db.insertPitchForAtBat(abId, "SO", inning)
                                actionStack.push(GameAction.Pitch(abId))
                            }
                            "KL" -> {
                                db.insertPitchForAtBat(abId, "S", inning)
                                actionStack.push(GameAction.Pitch(abId))
                            }
                        }
                        db.updateAtBatResult(abId, result)
                        if (isOutResult(result)) recordBatterOut(result)
                        else nextBatter()
                    }
                )
            }
        }
    }

    @Composable
    fun StatsBar(inning: Int, outs: Int, pitches: List<Pitch>, onRunnerOut: () -> Unit) {
        val (balls, strikes) = currentAtBatCount(pitches)
        val outsDesc = stringResource(R.string.content_desc_outs_indicator)

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
                StatItem(stringResource(R.string.stat_balls), balls.toString(), colorResource(R.color.color_primary))
                StatItem(stringResource(R.string.stat_strikes), strikes.toString(), colorResource(R.color.color_strike))
                StatItem(stringResource(R.string.stat_pitch), (pitches.count { it.type in listOf("B", "S", "F") } + 1).toString(), colorResource(R.color.color_text_primary))
                StatItem(stringResource(R.string.stat_count), "$balls-$strikes", colorResource(R.color.color_primary))
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
    fun BatterStrip(
        slot: Int,
        player: Player?,
        launcher: androidx.activity.result.ActivityResultLauncher<Intent>
    ) {
        val context = LocalContext.current
        val batterText = if (player != null)
            stringResource(R.string.label_batter_slot_with_jersey, "#${player.number} ${player.name}", slot)
        else
            stringResource(R.string.label_batter_slot, slot)

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
                        val i = Intent(context, OwnLineupActivity::class.java)
                        i.putExtra("gameId", gameId)
                        i.putExtra("isOffenseMode", true)
                        launcher.launch(i)
                    }
                    .padding(8.dp)
            )
        }
    }

    @Composable
    fun PitchLog(pitches: List<Pitch>) {
        val displayItems = pitches.reversed()
        var pitchNumber = pitches.count { it.type in listOf("B", "S", "F") }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayItems) { pitch ->
                when (pitch.type) {
                    "HBP" -> Text(stringResource(R.string.pitch_label_hbp), color = colorResource(R.color.color_hbp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "H" -> Text(stringResource(R.string.pitch_label_hit), color = colorResource(R.color.color_hit), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "B", "S", "F" -> {
                        val currentNum = pitchNumber
                        pitchNumber--
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$currentNum.", fontSize = 12.sp, color = colorResource(R.color.color_text_tertiary), modifier = Modifier.width(32.dp))
                            val (label, bgColor) = when (pitch.type) {
                                "B" -> stringResource(R.string.pitch_label_ball) to colorResource(R.color.color_primary)
                                "F" -> stringResource(R.string.pitch_label_foul) to colorResource(R.color.color_foul)
                                else -> stringResource(R.string.pitch_label_strike) to colorResource(R.color.color_strike)
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ActionButtons(
        showKSheet: Boolean,
        onShowKSheet: () -> Unit,
        onKSheetDismiss: () -> Unit,
        showBBSheet: Boolean,
        onShowBBSheet: () -> Unit,
        onBBSheetDismiss: () -> Unit,
        onBall: () -> Unit,
        onStrike: () -> Unit,
        onFoul: () -> Unit,
        onUndo: () -> Unit,
        onResult: (String) -> Unit
    ) {
        var outExpanded by remember { mutableStateOf(false) }
        var showMoreSheet by remember { mutableStateOf(false) }
        var showHSheet by remember { mutableStateOf(false) }

        if (showHSheet) {
            ModalBottomSheet(onDismissRequest = { showHSheet = false }) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                        Button(onClick = { onResult("1B"); showHSheet = false }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_green_bright)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_result_1b), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onResult("2B"); showHSheet = false }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_green)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_result_2b), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                        Button(onClick = { onResult("3B"); showHSheet = false }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_green_dark)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_result_3b), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onResult("HR"); showHSheet = false }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_hit_homer)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_result_hr), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        if (showBBSheet) {
            ModalBottomSheet(onDismissRequest = onBBSheetDismiss) {
                Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).fillMaxWidth().height(72.dp)) {
                    Button(onClick = { onResult("BB"); onBBSheetDismiss() }, modifier = Modifier.fillMaxWidth().fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_primary)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_walk_confirm), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (showKSheet) {
            ModalBottomSheet(onDismissRequest = onKSheetDismiss) {
                Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).fillMaxWidth().height(72.dp)) {
                    Button(onClick = { onResult("K"); onKSheetDismiss() }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_strikeout_swinging), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onResult("KL"); onKSheetDismiss() }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike_looking)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_strikeout_looking), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        if (showMoreSheet) {
            ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
                Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp).fillMaxWidth().height(60.dp)) {
                    listOf(
                        R.string.btn_result_hbp to colorResource(R.color.color_hbp),
                        R.string.btn_result_sac to colorResource(R.color.color_orange),
                        R.string.btn_result_fc  to colorResource(R.color.color_primary),
                        R.string.btn_result_e   to colorResource(R.color.color_foul)
                    ).forEachIndexed { i, (labelRes, color) ->
                        val label = stringResource(labelRes)
                        if (i > 0) Spacer(Modifier.width(8.dp))
                        Button(onClick = { onResult(label); showMoreSheet = false }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = color), shape = RoundedCornerShape(8.dp)) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    Button(onClick = onBall, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_primary)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_ball), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onStrike, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_strike), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Button(onClick = onFoul, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_foul)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_foul), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onUndo, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_text_secondary)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_undo), fontSize = 14.sp) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colorResource(R.color.color_divider_light))
                Row(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    listOf(R.string.btn_result_h to colorResource(R.color.color_green), R.string.btn_result_k to colorResource(R.color.color_strike), R.string.btn_result_bb to colorResource(R.color.color_primary))
                        .forEachIndexed { i, (labelRes, color) ->
                            val label = stringResource(labelRes)
                            if (i > 0) Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    when (labelRes) {
                                        R.string.btn_result_h  -> { showHSheet = true; outExpanded = false }
                                        R.string.btn_result_k  -> { onShowKSheet(); outExpanded = false }
                                        R.string.btn_result_bb -> { onShowBBSheet(); outExpanded = false }
                                        else -> { onResult(label); outExpanded = false }
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                colors = ButtonDefaults.buttonColors(containerColor = color),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        }
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { outExpanded = !outExpanded; showMoreSheet = false }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = if (outExpanded) colorResource(R.color.color_orange) else colorResource(R.color.color_orange_bright)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_result_out), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { showMoreSheet = true; outExpanded = false }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_text_secondary)), shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.btn_result_more), fontSize = 18.sp) }
                }
                if (outExpanded) {
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        listOf(R.string.btn_result_go, R.string.btn_result_fo, R.string.btn_result_lo)
                            .forEachIndexed { i, labelRes ->
                                val label = stringResource(labelRes)
                                if (i > 0) Spacer(Modifier.width(6.dp))
                                Button(onClick = { onResult(label); outExpanded = false }, modifier = Modifier.weight(1f).fillMaxHeight(), colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_orange)), shape = RoundedCornerShape(8.dp)) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                            }
                    }
                }
            }
        }
    }

    private fun currentAtBatCount(pitches: List<Pitch>): Pair<Int, Int> {
        var balls = 0
        var strikes = 0
        for (p in pitches) {
            when (p.type) {
                "B" -> balls++
                "S" -> strikes++
                "F" -> if (strikes < 2) strikes++
            }
        }
        return Pair(minOf(balls, 4), minOf(strikes, 3))
    }

    private fun isOutResult(result: String?) =
        result in setOf("K", "KL", "GO", "FO", "LO", "SAC", "DP", "OUT")
}
