package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class PitchTrackActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper
    private var pitcherId: Long = -1
    private var gameId: Long = -1

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        fun refresh() {
            stats = db.getStatsForPitcher(pitcherId)
            scope.launch {
                if (stats.pitches.isNotEmpty()) {
                    listState.animateScrollToItem(stats.pitches.size - 1)
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(pitcherName) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
            ) {
                StatsBar(stats)
                BatterStrip(stats)
                Box(modifier = Modifier.weight(1f)) {
                    PitchLog(stats, listState)
                }
                ActionButtons(
                    onBall = {
                        db.insertPitch(pitcherId, "B")
                        val (balls, _) = currentAtBatCount(stats.pitches)
                        if (balls >= 4) {
                            db.insertPitch(pitcherId, "W")
                            db.insertPitch(pitcherId, "BF")
                        }
                        refresh()
                    },
                    onStrike = {
                        db.insertPitch(pitcherId, "S")
                        val (_, strikes) = currentAtBatCount(stats.pitches)
                        if (strikes >= 3) {
                            db.insertPitch(pitcherId, "SO")
                            db.insertPitch(pitcherId, "BF")
                        }
                        refresh()
                    },
                    onFoul = {
                        val (_, strikesBefore) = currentAtBatCount(stats.pitches)
                        db.insertPitch(pitcherId, "F")
                        if (strikesBefore < 2) {
                            val (_, strikesAfter) = currentAtBatCount(db.getPitchesForPitcher(pitcherId))
                            if (strikesAfter >= 3) {
                                db.insertPitch(pitcherId, "SO")
                                db.insertPitch(pitcherId, "BF")
                            }
                        }
                        refresh()
                    },
                    onHbp = {
                        db.insertPitch(pitcherId, "HBP")
                        db.insertPitch(pitcherId, "BF")
                        refresh()
                    },
                    onBf = {
                        db.insertPitch(pitcherId, "BF")
                        refresh()
                    },
                    onUndo = {
                        db.undoLastPitch(pitcherId)
                        refresh()
                    }
                )
            }
        }
    }

    @Composable
    fun StatsBar(stats: PitcherStats) {
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
                StatItem(stringResource(R.string.stat_bf), totalBF.toString(), Color(0xFF333333))
                StatItem(stringResource(R.string.stat_balls), stats.balls.toString(), Color(0xFF1A5FA8))
                StatItem(stringResource(R.string.stat_strikes), stats.strikes.toString(), Color(0xFFC0392B))
                StatItem(stringResource(R.string.stat_pitch), "#${stats.totalPitches + 1}", Color(0xFF333333))
                StatItem(stringResource(R.string.stat_count), "$atBatBalls-$atBatStrikes", Color(0xFF1A5FA8))
            }
        }
    }

    @Composable
    fun StatItem(label: String, value: String, color: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = Color(0xFF888888))
            Text(value, fontSize = if (label == stringResource(R.string.stat_pitch)) 18.sp else 28.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }

    @Composable
    fun BatterStrip(stats: PitcherStats) {
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
                .background(Color(0xFF1A5FA8))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.label_batter), fontSize = 13.sp, color = Color(0xFFAACCFF))
            Spacer(modifier = Modifier.width(8.dp))
            Text(batterText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.hint_lineup),
                fontSize = 12.sp,
                color = Color(0xFFAACCFF),
                modifier = Modifier
                    .clickable {
                        if (gameId != -1L) {
                            val i = Intent(context, OpponentLineupActivity::class.java)
                            i.putExtra("gameId", gameId)
                            i.putExtra("opponentName", stats.pitcher.name)
                            context.startActivity(i)
                        }
                    }
                    .padding(8.dp)
            )
        }
    }

    @Composable
    fun PitchLog(stats: PitcherStats, listState: androidx.compose.foundation.lazy.LazyListState) {
        val gameBF = if (gameId != -1L) db.getTotalBFForGame(gameId) else stats.bf
        val bfOffset = gameBF - stats.bf
        var bfCount = bfOffset
        var pitchNumber = 0

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stats.pitches) { pitch ->
                when (pitch.type) {
                    "BF" -> {
                        bfCount++
                        val nextBattingOrder = (bfCount % 9) + 1
                        val nextJersey = getBatterJersey(nextBattingOrder)
                        val batterLabel = if (nextJersey.isNotEmpty())
                            stringResource(R.string.pitch_label_slot_with_jersey, nextJersey, nextBattingOrder)
                        else
                            stringResource(R.string.pitch_label_slot, nextBattingOrder)
                        Text(batterLabel, color = Color(0xFF888888), fontSize = 11.sp)
                    }
                    "HBP" -> Text(stringResource(R.string.pitch_label_hbp), color = Color(0xFF8E44AD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "W" -> Text(stringResource(R.string.pitch_label_walk), color = Color(0xFFD35400), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "SO" -> Text(stringResource(R.string.pitch_label_strikeout), color = Color(0xFF27AE60), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "B", "S", "F" -> {
                        pitchNumber++
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$pitchNumber.", fontSize = 12.sp, color = Color(0xFF666666), modifier = Modifier.width(32.dp))
                            val label = when (pitch.type) {
                                "B" -> stringResource(R.string.pitch_label_ball)
                                "F" -> stringResource(R.string.pitch_label_foul)
                                else -> stringResource(R.string.pitch_label_strike)
                            }
                            val bgColor = when (pitch.type) {
                                "B" -> Color(0xFF1A5FA8)
                                "F" -> Color(0xFFF39C12)
                                else -> Color(0xFFC0392B)
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
        onFoul: () -> Unit,
        onHbp: () -> Unit,
        onBf: () -> Unit,
        onUndo: () -> Unit
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A5FA8)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_ball), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onStrike,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_strike), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.height(64.dp)) {
                    Button(
                        onClick = onHbp,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E44AD)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_hbp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onFoul,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_foul), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.height(56.dp)) {
                    Button(
                        onClick = onBf,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B6D11)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_add_batter), fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onUndo,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF888888)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_undo), fontSize = 14.sp)
                    }
                }
            }
        }
    }

    private fun currentAtBatCount(pitches: List<Pitch>): Pair<Int, Int> {
        val lastBf = pitches.indexOfLast { it.type == "BF" }
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
        return Pair(balls, strikes)
    }

    private fun getBatterJersey(battingOrder: Int): String {
        if (gameId == -1L) return ""
        return db.getJerseyAtBattingOrder(gameId, battingOrder)
    }
}
