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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

        val snackbarHostState = remember { SnackbarHostState() }

        var lineup by remember { mutableStateOf(emptyMap<Int, Player>()) }

        fun refreshLineup() {
            lineup = db.getOwnLineup(gameId)
        }

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
                        if (pitchesCount == 0) {
                            db.deleteAtBat(currentAtBatId)
                        } else {
                            db.updateAtBatResult(currentAtBatId, "OUT") // Treat as out if skipped? Or just leave it?
                        }
                    }
                    startNewAtBat(jumpToSlot)
                }
            }
        }

        fun refreshAtBat(id: Long) {
            if (id != -1L) {
                pitches = db.getPitchesForAtBat(id)
            }
        }

        LaunchedEffect(gameId) {
            if (gameId != -1L) {
                refreshLineup()
                val (i, o) = db.getGameState(gameId)
                inning = i
                outs = o

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
                val existingAtBats = db.getAtBatsForGame(gameId)
                if (existingAtBats.isNotEmpty() && existingAtBats.last().result == null) {
                    currentAtBatId = existingAtBats.last().id
                    currentSlot = existingAtBats.last().slot
                    refreshAtBat(currentAtBatId)
                    currentAtBatId
                } else {
                    startNewAtBat(currentSlot)
                }
            } else {
                currentAtBatId
            }
        }

        fun nextBatter() {
            val maxSlot = if (lineup.containsKey(10)) 10 else 9
            val nextSlot = (currentSlot % maxSlot) + 1
            startNewAtBat(nextSlot)
        }

        fun incrementOuts() {
            val newOuts = outs + 1
            if (newOuts >= 3) {
                inning++
                outs = 0
            } else {
                outs = newOuts
            }
            db.updateGameState(gameId, inning, outs)
        }

        fun strikeOut(abId: Long) {
            incrementOuts()
            if (abId != -1L) {
                db.updateAtBatResult(abId, "K")
            }
            nextBatter()
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.gamehub_offense)) },
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
                StatsBar(inning, outs, pitches)
                BatterStrip(currentSlot, lineup[currentSlot], launcher)
                Box(modifier = Modifier.weight(1f)) {
                    PitchLog(pitches)
                }
                ActionButtons(
                    onBall = {
                        val abId = ensureAtBat()
                        db.insertPitchForAtBat(abId, "B", inning)
                        val updatedPitches = db.getPitchesForAtBat(abId)
                        val (balls, _) = currentAtBatCount(updatedPitches)
                        if (balls >= 4) {
                            db.updateAtBatResult(abId, "BB")
                            nextBatter()
                        } else {
                            refreshAtBat(abId)
                        }
                    },
                    onStrike = {
                        val abId = ensureAtBat()
                        db.insertPitchForAtBat(abId, "S", inning)
                        val updatedPitches = db.getPitchesForAtBat(abId)
                        val (_, strikes) = currentAtBatCount(updatedPitches)
                        if (strikes >= 3) {
                            strikeOut(abId)
                        } else {
                            refreshAtBat(abId)
                        }
                    },
                    onHit = {
                        val abId = ensureAtBat()
                        db.insertPitchForAtBat(abId, "H", inning)
                        db.updateAtBatResult(abId, "H")
                        nextBatter()
                    },
                    onFoul = {
                        val abId = ensureAtBat()
                        db.insertPitchForAtBat(abId, "F", inning)
                        refreshAtBat(abId)
                    },
                    onHbp = {
                        val abId = ensureAtBat()
                        db.insertPitchForAtBat(abId, "HBP", inning)
                        db.updateAtBatResult(abId, "HBP")
                        nextBatter()
                    },
                    onUndo = {
                        val abId = currentAtBatId
                        if (abId != -1L) {
                            val currentPitches = db.getPitchesForAtBat(abId)
                            if (currentPitches.isNotEmpty()) {
                                db.undoLastPitchForAtBat(abId)
                                refreshAtBat(abId)
                            } else {
                                val allAb = db.getAtBatsForGame(gameId)
                                if (allAb.size > 1) {
                                    val currentAbIndex = allAb.indexOfFirst { it.id == abId }
                                    if (currentAbIndex > 0) {
                                        val prevAb = allAb[currentAbIndex - 1]

                                        if (prevAb.result == "K" || prevAb.result == "OUT") {
                                            if (outs > 0) {
                                                outs--
                                            } else if (inning > 1) {
                                                inning--
                                                outs = 2
                                            }
                                            db.updateGameState(gameId, inning, outs)
                                        }

                                        db.deleteAtBat(abId)
                                        currentAtBatId = prevAb.id
                                        currentSlot = prevAb.slot
                                        db.updateAtBatResult(prevAb.id, null)
                                        refreshAtBat(prevAb.id)
                                    }
                                }
                            }
                        }
                    },
                    onOut = {
                        incrementOuts()
                    }
                )
            }
        }
    }

    @Composable
    fun StatsBar(inning: Int, outs: Int, pitches: List<Pitch>) {
        val (balls, strikes) = currentAtBatCount(pitches)

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
                Row(verticalAlignment = Alignment.CenterVertically) {
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

    @Composable
    fun ActionButtons(
        onBall: () -> Unit,
        onStrike: () -> Unit,
        onHit: () -> Unit,
        onFoul: () -> Unit,
        onHbp: () -> Unit,
        onUndo: () -> Unit,
        onOut: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.height(80.dp)) {
                    Button(
                        onClick = onBall,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_primary)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_ball), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onStrike,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_strike), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.height(64.dp)) {
                    Button(
                        onClick = onHit,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_hit)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_hit), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onHbp,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_hbp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_hbp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onFoul,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_foul)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_foul), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.height(56.dp)) {
                    Button(
                        onClick = onOut,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_out), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onUndo,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_text_secondary)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_undo), fontSize = 14.sp)
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
}
