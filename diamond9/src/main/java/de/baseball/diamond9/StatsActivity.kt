package de.baseball.diamond9

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class StatsActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        db = DatabaseHelper(this)
        val pitcherId = intent.getLongExtra("pitcherId", -1)
        val stats = db.getStatsForPitcher(pitcherId)

        setContent {
            stats?.let {
                StatsScreen(
                    stats = it,
                    onBackClick = { finish() },
                    onShareClick = { StatsPdfExporter.sharePitcherDetail(this, it) }
                )
            } ?: run {
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    stats: PitcherStats,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit = {}
) {
    Scaffold(
        containerColor = colorResource(R.color.color_background),
        topBar = {
            TopAppBar(
                title = { Text(stats.pitcher.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colorResource(R.color.color_background))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(label = stringResource(R.string.stat_bf), value = stats.bf.toString(), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_hits), value = stats.hits.toString(), valueColor = colorResource(R.color.color_hit), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_balls), value = stats.balls.toString(), valueColor = colorResource(R.color.color_primary), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_strikes), value = stats.strikes.toString(), valueColor = colorResource(R.color.color_strike), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(label = stringResource(R.string.stat_walks), value = stats.walks.toString(), valueColor = colorResource(R.color.color_walk), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_hbp), value = stats.hbp.toString(), valueColor = colorResource(R.color.color_hbp), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_strikeouts), value = stats.strikeouts.toString(), valueColor = colorResource(R.color.color_green_bright), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_total_pitches), value = stats.totalPitches.toString(), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val strikePercent = if (stats.totalPitches > 0) (stats.strikes * 100.0 / stats.totalPitches).toInt() else 0
                StatCard(label = stringResource(R.string.stat_strike_percent), value = "$strikePercent%", valueColor = colorResource(R.color.color_green_dark), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_ip), value = stats.ip, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(2f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.stat_pitch_log),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.color_text_primary),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            PitchGrid(stats.pitches)
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Black
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 11.sp, color = colorResource(R.color.color_text_secondary))
            Text(text = value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
fun PitchGrid(pitches: List<Pitch>, jerseyMap: Map<Int, String> = emptyMap()) {
    val groups = remember(pitches) {
        groupPitchesByBatter(pitches).map { g ->
            g.copy(jerseyNumber = jerseyMap[g.battingSlot] ?: "")
        }
    }

    if (groups.isEmpty()) {
        Text(
            text = "–",
            color = colorResource(R.color.color_text_secondary),
            fontSize = 13.sp,
            modifier = Modifier.padding(8.dp)
        )
        return
    }

    val byInning = groups.groupBy { it.inning }.toSortedMap()

    byInning.forEach { (inningNr, inningGroups) ->

        // Inning-Trennbalken
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = colorResource(R.color.color_primary), thickness = 1.dp)
            Text(
                text = "  Inning $inningNr  ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.color_primary)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = colorResource(R.color.color_primary), thickness = 1.dp)
        }

        val chunked = inningGroups.chunked(3)

        // Spalten-Header (B / S) für jede Spalte
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(minOf(inningGroups.size, 3)) {
                Row(modifier = Modifier.weight(1f)) {
                    Text("B", modifier = Modifier.weight(1f), fontSize = 10.sp, color = colorResource(R.color.color_primary), textAlign = TextAlign.Center)
                    Text("S", modifier = Modifier.weight(1f), fontSize = 10.sp, color = colorResource(R.color.color_strike), textAlign = TextAlign.Center)
                }
            }
            repeat(3 - minOf(inningGroups.size, 3)) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        chunked.forEach { rowGroups ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowGroups.forEach { group ->
                    Column(modifier = Modifier.weight(1f)) {
                        BatterBlock(group)
                    }
                }
                repeat(3 - rowGroups.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun BatterBlock(group: BatterGroup) {
    val headerText = if (group.jerseyNumber.isNotEmpty()) "#${group.jerseyNumber}" else "B${group.batterNr}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .background(colorResource(R.color.color_bg_primary_light))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = headerText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.color_primary),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }

    val relevantPitches = group.pitches.filter {
        it.type in listOf("B", "S", "F", "W", "HBP", "H", "SO")
    }

    relevantPitches.forEach { pitch ->
        when (pitch.type) {
            "W", "HBP", "H", "SO" -> {
                val bgColor = when (pitch.type) {
                    "W"   -> colorResource(R.color.color_bg_walk)
                    "HBP" -> colorResource(R.color.color_bg_hbp)
                    "SO"  -> colorResource(R.color.color_bg_strikeout)
                    else  -> colorResource(R.color.color_bg_out)
                }
                val textColor = when (pitch.type) {
                    "W"   -> colorResource(R.color.color_walk)
                    "HBP" -> colorResource(R.color.color_hbp)
                    "SO"  -> colorResource(R.color.color_green_bright)
                    else  -> colorResource(R.color.color_hit)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .border(0.5.dp, colorResource(R.color.color_divider))
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = pitch.type, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
            }
            "B", "S", "F" -> {
                Row(modifier = Modifier.fillMaxWidth()) {
                    PitchCell(pitch, listOf("B"), colorResource(R.color.color_primary), Modifier.weight(1f))
                    PitchCell(pitch, listOf("S", "F"), colorResource(R.color.color_strike), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PitchCell(pitch: Pitch?, types: List<String>, activeColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(18.dp)
            .border(0.5.dp, colorResource(R.color.color_divider))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        val isMatch = pitch != null && pitch.type in types
        val displayColor = when (pitch?.type) {
            "F" -> colorResource(R.color.color_foul)
            else -> activeColor
        }
        Text(
            text = if (pitch == null) "" else if (isMatch) "✓" else "·",
            fontSize = 11.sp,
            color = if (isMatch) displayColor else if (pitch == null) colorResource(R.color.color_divider) else colorResource(R.color.color_gray_medium)
        )
    }
}
