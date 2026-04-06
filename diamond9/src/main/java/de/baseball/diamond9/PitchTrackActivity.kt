package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        val listState = rememberLazyListState()
        var inning by remember { mutableStateOf(1) }
        var outs by remember { mutableStateOf(0) }
        var showInningSnackbar by remember { mutableStateOf(false) }
        var showTrendSheet by remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        fun refresh() {
            stats = db.getStatsForPitcher(pitcherId)
        }

        LaunchedEffect(Unit) {
            if (gameId != -1L) {
                val (i, o) = db.getGameState(gameId)
                inning = i
                outs = o
            }
        }

        fun addOut() {
            val newOuts = outs + 1
            if (newOuts >= 3) {
                if (gameId != -1L) {
                    val gameBF = db.getTotalBFForGame(gameId)
                    val currentSlot = (gameBF % 9) + 1
                    db.updateLeadoffSlot(gameId, currentSlot)
                }
                inning++
                outs = 0
                showInningSnackbar = true
            } else {
                outs = newOuts
            }
            if (gameId != -1L) db.updateGameState(gameId, inning, outs)
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                StatsBar(stats, inning, outs)
                BatterStrip(stats)
                Box(modifier = Modifier.weight(1f)) {
                    PitchLog(stats, listState)
                }
                ActionButtons(
                    onBall = {
                        val (ballsBefore, _) = currentAtBatCount(stats.pitches)
                        db.insertPitch(pitcherId, "B", inning)
                        if (ballsBefore >= 3) {
                            db.insertPitch(pitcherId, "W", inning)
                            db.insertPitch(pitcherId, "BF", inning)
                        }
                        refresh()
                    },
                    onStrike = {
                        val (_, strikesBefore) = currentAtBatCount(stats.pitches)
                        db.insertPitch(pitcherId, "S", inning)
                        if (strikesBefore >= 2) {
                            db.insertPitch(pitcherId, "SO", inning)
                            db.insertPitch(pitcherId, "BF", inning)
                            addOut()
                        }
                        refresh()
                    },
                    onHit = {
                        db.insertPitch(pitcherId, "H", inning)
                        db.insertPitch(pitcherId, "BF", inning)
                        refresh()
                    },
                    onFoul = {
                        db.insertPitch(pitcherId, "F", inning)
                        refresh()
                    },
                    onHbp = {
                        db.insertPitch(pitcherId, "HBP", inning)
                        db.insertPitch(pitcherId, "BF", inning)
                        refresh()
                    },
                    onBf = {
                        db.insertPitch(pitcherId, "BF", inning)
                        refresh()
                    },
                    onUndo = {
                        db.undoLastPitch(pitcherId)
                        refresh()
                    },
                    onOut = { addOut() },
                    onShowTrend = { showTrendSheet = true }
                )
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
    }

    @Composable
    fun StatsBar(stats: PitcherStats, inning: Int, outs: Int) {
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
                StatItem(stringResource(R.string.stat_walks), stats.walks.toString(), Color(0xFFD35400))
                StatItem(stringResource(R.string.stat_hbp), stats.hbp.toString(), Color(0xFF8E44AD))
                StatItem(stringResource(R.string.stat_pitch), (stats.totalPitches + 1).toString(), Color(0xFF333333))
                StatItem(stringResource(R.string.stat_count), "$atBatBalls-$atBatStrikes", Color(0xFF1A5FA8))
            }
            HorizontalDivider(color = Color(0xFFEEEEEE))
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
                    color = Color(0xFF333333)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.label_outs),
                        fontSize = 14.sp,
                        color = Color(0xFF888888)
                    )
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(14.dp)
                                .background(
                                    color = if (index < outs) Color(0xFFC0392B) else Color(0xFFDDDDDD),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun StatItem(label: String, value: String, color: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = Color(0xFF888888))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
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
                        
                        Text(batterLabel, color = Color(0xFF888888), fontSize = 11.sp)
                        bfCount--
                    }
                    "HBP" -> Text(stringResource(R.string.pitch_label_hbp), color = Color(0xFF8E44AD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "H" -> Text(stringResource(R.string.pitch_label_hit), color = Color(0xFFE74C3C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "W" -> Text(stringResource(R.string.pitch_label_walk), color = Color(0xFFD35400), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "SO" -> Text(stringResource(R.string.pitch_label_strikeout), color = Color(0xFF27AE60), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    "B", "S", "F" -> {
                        val currentNum = pitchNumber
                        pitchNumber--
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$currentNum.", fontSize = 12.sp, color = Color(0xFF666666), modifier = Modifier.width(32.dp))
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
        onHit: () -> Unit,
        onFoul: () -> Unit,
        onHbp: () -> Unit,
        onBf: () -> Unit,
        onUndo: () -> Unit,
        onOut: () -> Unit,
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
                            tint = Color(0xFF1A5FA8)
                        )
                    }
                }
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
                        onClick = onHit,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_hit), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
                        onClick = onOut,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_out), fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                color = Color(0xFF333333),
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
                        color = Color(0xFF888888),
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
                    TrendLevel.GOOD   -> Triple(Color(0xFF27AE60), "🟢", stringResource(R.string.trend_good))
                    TrendLevel.WATCH  -> Triple(Color(0xFFE67E22), "🟡", stringResource(R.string.trend_watch))
                    TrendLevel.CHANGE -> Triple(Color(0xFFC0392B), "🔴", stringResource(R.string.trend_change))
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
                                    color = Color(0xFF666666)
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
                    color = Color(0xFF555555),
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
                            color = Color(0xFF666666)
                        )
                        Text(
                            text = "${b.balls}B ${b.strikes}S ${b.fouls}F",
                            fontSize = 12.sp,
                            color = Color(0xFF666666)
                        )
                        Text(
                            text = "${(b.strikePercent * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                b.strikePercent >= 0.6f -> Color(0xFF27AE60)
                                b.strikePercent >= 0.4f -> Color(0xFFE67E22)
                                else -> Color(0xFFC0392B)
                            }
                        )
                    }
                }
            }
        }
    }
}
