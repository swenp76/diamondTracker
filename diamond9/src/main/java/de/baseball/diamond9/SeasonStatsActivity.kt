package de.baseball.diamond9

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SeasonStatsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val teamId = intent.getLongExtra("teamId", -1L)
        val teamName = intent.getStringExtra("teamName") ?: ""
        val db = DatabaseHelper(this)

        setContent {
            SeasonStatsScreen(
                teamId = teamId,
                teamName = teamName,
                db = db,
                onBackClick = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonStatsScreen(
    teamId: Long,
    teamName: String,
    db: DatabaseHelper,
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.season_stats_tab_batter),
        stringResource(R.string.season_stats_tab_pitcher)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.season_stats_title))
                        if (teamName.isNotBlank()) {
                            Text(teamName, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = colorResource(R.color.color_primary)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            when (selectedTab) {
                0 -> BatterStatsTab(teamId = teamId, db = db)
                1 -> PitcherStatsTab(teamId = teamId, db = db)
            }
        }
    }
}

@Composable
private fun BatterStatsTab(teamId: Long, db: DatabaseHelper) {
    val rows = remember(teamId) {
        db.getSeasonBatterStats(teamId)
            .sortedByDescending { if (it.ab > 0) it.hits.toFloat() / it.ab else 0f }
    }
    val players = remember(teamId) {
        db.getPlayersForTeam(teamId).associateBy { it.id }
    }

    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.season_stats_empty_batter), color = colorResource(R.color.color_text_secondary))
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        StatsHeaderRow(
            columns = listOf(
                Pair(stringResource(R.string.season_stats_col_name), 3f),
                Pair(stringResource(R.string.season_stats_col_ab), 1f),
                Pair(stringResource(R.string.season_stats_col_h), 1f),
                Pair(stringResource(R.string.season_stats_col_avg), 1.4f),
                Pair(stringResource(R.string.season_stats_col_bb), 1f),
                Pair(stringResource(R.string.season_stats_col_k), 1f)
            )
        )
        LazyColumn {
            items(rows) { row ->
                val name = players[row.playerId]?.let { "#${it.number} ${it.name}" }
                    ?: stringResource(R.string.season_stats_unknown_player)
                val avg = if (row.ab > 0) row.hits.toFloat() / row.ab else 0f
                val avgStr = when {
                    row.ab == 0 -> "---"
                    avg >= 1f -> "1.000"
                    else -> ".%03d".format((avg * 1000).toInt())
                }
                StatsDataRow(
                    columns = listOf(
                        Pair(name, 3f),
                        Pair(row.ab.toString(), 1f),
                        Pair(row.hits.toString(), 1f),
                        Pair(avgStr, 1.4f),
                        Pair(row.walks.toString(), 1f),
                        Pair(row.strikeouts.toString(), 1f)
                    ),
                    isEven = rows.indexOf(row) % 2 == 0
                )
            }
        }
    }
}

@Composable
private fun PitcherStatsTab(teamId: Long, db: DatabaseHelper) {
    val rows = remember(teamId) {
        db.getSeasonPitcherStats(teamId)
            .sortedByDescending { it.bf }
    }
    val players = remember(teamId) {
        db.getPlayersForTeam(teamId).associateBy { it.id }
    }

    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.season_stats_empty_pitcher), color = colorResource(R.color.color_text_secondary))
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatsHeaderRow(
            columns = listOf(
                Pair(stringResource(R.string.season_stats_col_name), 3f),
                Pair(stringResource(R.string.season_stats_col_bf), 1f),
                Pair(stringResource(R.string.season_stats_col_p), 1f),
                Pair(stringResource(R.string.season_stats_col_strike_pct), 1.4f),
                Pair(stringResource(R.string.season_stats_col_bb), 1f),
                Pair(stringResource(R.string.season_stats_col_k), 1f)
            )
        )
        LazyColumn {
            items(rows) { row ->
                val name = players[row.playerId]?.let { "#${it.number} ${it.name}" }
                    ?: stringResource(R.string.season_stats_unknown_player)
                val strikePct = if (row.totalPitches > 0)
                    (row.strikes + row.fouls).toFloat() / row.totalPitches * 100 else 0f
                val strikePctStr = if (row.totalPitches > 0) "%.0f%%".format(strikePct) else "---"
                StatsDataRow(
                    columns = listOf(
                        Pair(name, 3f),
                        Pair(row.bf.toString(), 1f),
                        Pair(row.totalPitches.toString(), 1f),
                        Pair(strikePctStr, 1.4f),
                        Pair(row.walks.toString(), 1f),
                        Pair(row.ks.toString(), 1f)
                    ),
                    isEven = rows.indexOf(row) % 2 == 0
                )
            }
        }
    }
}

@Composable
private fun StatsHeaderRow(columns: List<Pair<String, Float>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.color_primary))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        columns.forEach { (label, weight) ->
            Text(
                text = label,
                modifier = Modifier.weight(weight),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = if (weight == columns.first().second) TextAlign.Start else TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatsDataRow(columns: List<Pair<String, Float>>, isEven: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isEven) Color.White else colorResource(R.color.color_background))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.forEachIndexed { index, (text, weight) ->
            Text(
                text = text,
                modifier = Modifier.weight(weight),
                fontSize = 13.sp,
                color = colorResource(R.color.color_text_primary),
                textAlign = if (index == 0) TextAlign.Start else TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
