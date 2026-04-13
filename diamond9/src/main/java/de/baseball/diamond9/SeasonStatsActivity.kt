package de.baseball.diamond9

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    val rawRows = remember(teamId) { db.getSeasonBatterStats(teamId) }
    val players = remember(teamId) { db.getPlayersForTeam(teamId).associateBy { it.id } }

    // Default: sort by AVG descending (col index 4)
    var sortCol by remember { mutableStateOf(4) }
    var sortAsc by remember { mutableStateOf(false) }

    val rows = remember(rawRows, sortCol, sortAsc) {
        fun name(r: SeasonBatterRow) = players[r.playerId]?.name ?: ""
        fun avg(r: SeasonBatterRow) = if (r.ab > 0) r.hits.toFloat() / r.ab else -1f
        fun obp(r: SeasonBatterRow): Float {
            val d = r.ab + r.walks + r.hbp
            return if (d > 0) (r.hits + r.walks + r.hbp).toFloat() / d else -1f
        }
        val sorted = when (sortCol) {
            0 -> rawRows.sortedBy { name(it) }
            1 -> rawRows.sortedBy { it.pa }
            2 -> rawRows.sortedBy { it.ab }
            3 -> rawRows.sortedBy { it.hits }
            4 -> rawRows.sortedBy { avg(it) }
            5 -> rawRows.sortedBy { obp(it) }
            6 -> rawRows.sortedBy { it.walks }
            7 -> rawRows.sortedBy { it.strikeouts }
            else -> rawRows.sortedBy { avg(it) }
        }
        if (sortAsc) sorted else sorted.reversed()
    }

    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.season_stats_empty_batter), color = colorResource(R.color.color_text_secondary))
        }
        return
    }

    val columns = listOf(
        Pair(stringResource(R.string.season_stats_col_name), 3f),
        Pair(stringResource(R.string.season_stats_col_pa), 1f),
        Pair(stringResource(R.string.season_stats_col_ab), 1f),
        Pair(stringResource(R.string.season_stats_col_h), 1f),
        Pair(stringResource(R.string.season_stats_col_avg), 1.4f),
        Pair(stringResource(R.string.season_stats_col_obp), 1.4f),
        Pair(stringResource(R.string.season_stats_col_bb), 1f),
        Pair(stringResource(R.string.season_stats_col_k), 1f)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SortableHeaderRow(
            columns = columns,
            sortColIndex = sortCol,
            sortAsc = sortAsc,
            onColumnClick = { idx ->
                if (sortCol == idx) sortAsc = !sortAsc
                else { sortCol = idx; sortAsc = idx == 0 } // name sorts A→Z by default
            }
        )
        LazyColumn {
            itemsIndexed(rows) { index, row ->
                val name = players[row.playerId]?.let { "#${it.number} ${it.name}" }
                    ?: stringResource(R.string.season_stats_unknown_player)
                val avg = if (row.ab > 0) row.hits.toFloat() / row.ab else 0f
                val avgStr = when {
                    row.ab == 0 -> "--"
                    avg >= 1f   -> "1.000"
                    else        -> ".%03d".format((avg * 1000).toInt())
                }
                val obpDenom = row.ab + row.walks + row.hbp
                val obp = if (obpDenom > 0) (row.hits + row.walks + row.hbp).toFloat() / obpDenom else 0f
                val obpStr = when {
                    obpDenom == 0 -> "--"
                    obp >= 1f     -> "1.000"
                    else          -> ".%03d".format((obp * 1000).toInt())
                }
                StatsDataRow(
                    columns = listOf(
                        Pair(name, 3f),
                        Pair(row.pa.toString(), 1f),
                        Pair(row.ab.toString(), 1f),
                        Pair(row.hits.toString(), 1f),
                        Pair(avgStr, 1.4f),
                        Pair(obpStr, 1.4f),
                        Pair(row.walks.toString(), 1f),
                        Pair(row.strikeouts.toString(), 1f)
                    ),
                    isEven = index % 2 == 0
                )
            }
        }
    }
}

@Composable
private fun PitcherStatsTab(teamId: Long, db: DatabaseHelper) {
    val rawRows = remember(teamId) { db.getSeasonPitcherStats(teamId) }
    val players = remember(teamId) { db.getPlayersForTeam(teamId).associateBy { it.id } }

    // Default: sort by S% descending (col index 3)
    var sortCol by remember { mutableStateOf(3) }
    var sortAsc by remember { mutableStateOf(false) }

    val rows = remember(rawRows, sortCol, sortAsc) {
        fun name(r: SeasonPitcherRow) = players[r.playerId]?.name ?: ""
        fun spct(r: SeasonPitcherRow) =
            if (r.totalPitches > 0) (r.strikes + r.fouls).toFloat() / r.totalPitches else -1f
        val sorted = when (sortCol) {
            0 -> rawRows.sortedBy { name(it) }
            1 -> rawRows.sortedBy { it.bf }
            2 -> rawRows.sortedBy { it.totalPitches }
            3 -> rawRows.sortedBy { spct(it) }
            4 -> rawRows.sortedBy { it.walks }
            5 -> rawRows.sortedBy { it.ks }
            else -> rawRows.sortedBy { it.bf }
        }
        if (sortAsc) sorted else sorted.reversed()
    }

    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.season_stats_empty_pitcher), color = colorResource(R.color.color_text_secondary))
        }
        return
    }

    val columns = listOf(
        Pair(stringResource(R.string.season_stats_col_name), 3f),
        Pair(stringResource(R.string.season_stats_col_bf), 1f),
        Pair(stringResource(R.string.season_stats_col_p), 1f),
        Pair(stringResource(R.string.season_stats_col_strike_pct), 1.4f),
        Pair(stringResource(R.string.season_stats_col_bb), 1f),
        Pair(stringResource(R.string.season_stats_col_k), 1f)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SortableHeaderRow(
            columns = columns,
            sortColIndex = sortCol,
            sortAsc = sortAsc,
            onColumnClick = { idx ->
                if (sortCol == idx) sortAsc = !sortAsc
                else { sortCol = idx; sortAsc = idx == 0 }
            }
        )
        LazyColumn {
            itemsIndexed(rows) { index, row ->
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
                    isEven = index % 2 == 0
                )
            }
        }
    }
}

@Composable
private fun SortableHeaderRow(
    columns: List<Pair<String, Float>>,
    sortColIndex: Int,
    sortAsc: Boolean,
    onColumnClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.color_primary))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        columns.forEachIndexed { index, (label, weight) ->
            val isActive = index == sortColIndex
            val indicator = if (isActive) if (sortAsc) " ▲" else " ▼" else ""
            Box(
                modifier = Modifier
                    .weight(weight)
                    .clickable { onColumnClick(index) },
                contentAlignment = if (index == 0) Alignment.CenterStart else Alignment.Center
            ) {
                Text(
                    text = label + indicator,
                    color = if (isActive) Color.Yellow else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (index == 0) TextAlign.Start else TextAlign.Center
                )
            }
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
