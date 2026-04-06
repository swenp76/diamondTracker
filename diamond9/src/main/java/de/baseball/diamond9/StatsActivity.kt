package de.baseball.diamond9

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class StatsActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)
        val pitcherId = intent.getLongExtra("pitcherId", -1)
        val stats = db.getStatsForPitcher(pitcherId)

        setContent {
            StatsScreen(
                stats = stats,
                onBackClick = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    stats: PitcherStats,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stats.pitcher.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(label = stringResource(R.string.stat_bf), value = stats.bf.toString(), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_hits), value = stats.hits.toString(), valueColor = Color(0xFFE74C3C), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_balls), value = stats.balls.toString(), valueColor = Color(0xFF1A5FA8), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_strikes), value = stats.strikes.toString(), valueColor = Color(0xFFC0392B), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(label = stringResource(R.string.stat_walks), value = stats.walks.toString(), valueColor = Color(0xFFD35400), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_hbp), value = stats.hbp.toString(), valueColor = Color(0xFF8E44AD), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_strikeouts), value = stats.strikeouts.toString(), valueColor = Color(0xFF27AE60), modifier = Modifier.weight(1f))
                StatCard(label = stringResource(R.string.stat_total_pitches), value = stats.totalPitches.toString(), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val strikePercent = if (stats.totalPitches > 0) (stats.strikes * 100.0 / stats.totalPitches).toInt() else 0
                StatCard(label = stringResource(R.string.stat_strike_percent), value = "$strikePercent%", valueColor = Color(0xFF3B6D11), modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(2f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.stat_pitch_log),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
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
            Text(text = label, fontSize = 11.sp, color = Color(0xFF888888))
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
            color = Color(0xFF888888),
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
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF1A5FA8), thickness = 1.dp)
            Text(
                text = "  Inning $inningNr  ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A5FA8)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF1A5FA8), thickness = 1.dp)
        }

        val chunked = inningGroups.chunked(3)

        // Spalten-Header (B / S) für jede Spalte
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(minOf(inningGroups.size, 3)) {
                Row(modifier = Modifier.weight(1f)) {
                    Text("B", modifier = Modifier.weight(1f), fontSize = 10.sp, color = Color(0xFF1A5FA8), textAlign = TextAlign.Center)
                    Text("S", modifier = Modifier.weight(1f), fontSize = 10.sp, color = Color(0xFFC0392B), textAlign = TextAlign.Center)
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
            .background(Color(0xFFE8EFF8))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = headerText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A5FA8),
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
                    "W"   -> Color(0xFFFFF7E6)
                    "HBP" -> Color(0xFFF3E5F5)
                    "SO"  -> Color(0xFFE8F5E9)
                    else  -> Color(0xFFFFEBEE)
                }
                val textColor = when (pitch.type) {
                    "W"   -> Color(0xFFD35400)
                    "HBP" -> Color(0xFF8E44AD)
                    "SO"  -> Color(0xFF27AE60)
                    else  -> Color(0xFFE74C3C)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .border(0.5.dp, Color(0xFFDDDDDD))
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = pitch.type, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
            }
            "B", "S", "F" -> {
                Row(modifier = Modifier.fillMaxWidth()) {
                    PitchCell(pitch, listOf("B"), Color(0xFF1A5FA8), Modifier.weight(1f))
                    PitchCell(pitch, listOf("S", "F"), Color(0xFFC0392B), Modifier.weight(1f))
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
            .border(0.5.dp, Color(0xFFDDDDDD))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        val isMatch = pitch != null && pitch.type in types
        val displayColor = when (pitch?.type) {
            "F" -> Color(0xFFF39C12)
            else -> activeColor
        }
        Text(
            text = if (pitch == null) "" else if (isMatch) "✓" else "·",
            fontSize = 11.sp,
            color = if (isMatch) displayColor else if (pitch == null) Color(0xFFDDDDDD) else Color(0xFFCCCCCC)
        )
    }
}
