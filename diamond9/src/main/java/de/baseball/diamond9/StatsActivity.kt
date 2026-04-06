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
fun PitchGrid(pitches: List<Pitch>) {
    // Show B, S, F, W, H, and HBP in the sequence they occurred
    val pitchesOnly = pitches.filter { it.type == "B" || it.type == "S" || it.type == "F" || it.type == "W" || it.type == "HBP" || it.type == "H" }
    val rows = 35
    val groups = 3

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (grp in 0 until groups) {
            val startIdx = grp * rows
            val startNum = startIdx + 1
            val endNum = startIdx + rows

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$startNum–$endNum",
                    fontSize = 10.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "B", modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color(0xFF1A5FA8), textAlign = TextAlign.Center)
                    Text(text = "S", modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color(0xFFC0392B), textAlign = TextAlign.Center)
                }

                for (i in 0 until rows) {
                    val pitchIdx = startIdx + i
                    val actual = if (pitchIdx < pitchesOnly.size) pitchesOnly[pitchIdx] else null
                    
                    if (actual != null && (actual.type == "W" || actual.type == "HBP" || actual.type == "H")) {
                        // Merged cell for Walk, HBP or Hit
                        val bgColor = when(actual.type) {
                            "W" -> Color(0xFFFFF7E6)
                            "HBP" -> Color(0xFFF3E5F5)
                            else -> Color(0xFFFFEBEE)
                        }
                        val textColor = when(actual.type) {
                            "W" -> Color(0xFFD35400)
                            "HBP" -> Color(0xFF8E44AD)
                            else -> Color(0xFFE74C3C)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .border(0.5.dp, Color(0xFFDDDDDD))
                                .background(bgColor)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = actual.type,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    } else {
                        // Normal two-column layout for B/S/F
                        Row(modifier = Modifier.fillMaxWidth()) {
                            PitchCell(actual, listOf("B"), Color(0xFF1A5FA8), Modifier.weight(1f))
                            PitchCell(actual, listOf("S", "F"), Color(0xFFC0392B), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PitchCell(pitch: Pitch?, types: List<String>, activeColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(24.dp)
            .border(0.5.dp, Color(0xFFDDDDDD))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        val isMatch = pitch != null && pitch.type in types
        val displayColor = when (pitch?.type) {
            "F" -> Color(0xFFF39C12) // Foul color
            else -> activeColor
        }
        
        Text(
            text = if (pitch == null) "" else if (isMatch) "✓" else "·",
            fontSize = 11.sp,
            color = if (isMatch) displayColor else if (pitch == null) Color(0xFFDDDDDD) else Color(0xFFCCCCCC)
        )
    }
}
