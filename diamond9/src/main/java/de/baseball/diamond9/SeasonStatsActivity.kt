package de.baseball.diamond9

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
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
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val teamId = intent.getLongExtra("teamId", -1L)
        val teamName = intent.getStringExtra("teamName") ?: ""
        val db = DatabaseHelper(this)

        setContent {
            SeasonStatsScreen(
                teamId = teamId,
                teamName = teamName,
                db = db,
                onBackClick = { finish() },
                onShareClick = { tab, format ->
                    val players = db.getPlayersForTeam(teamId).associateBy { it.id }
                    when (tab) {
                        0 -> StatsExporter.shareBatterTable(
                            this, teamName, "", db.getSeasonBatterStats(teamId), players, format
                        )
                        1 -> StatsExporter.shareSeasonPitcherTable(
                            this, teamName, "", db.getSeasonPitcherStats(teamId), players, format
                        )
                    }
                }
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
    onBackClick: () -> Unit,
    onShareClick: (tab: Int, format: ExportFormat) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showFormatDialog by remember { mutableStateOf(false) }
    val tabs = listOf(
        stringResource(R.string.season_stats_tab_batter),
        stringResource(R.string.season_stats_tab_pitcher)
    )

    Scaffold(
        containerColor = colorResource(R.color.color_background),
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
                actions = {
                    IconButton(onClick = { showFormatDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
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

    if (showFormatDialog) {
        ExportFormatDialog(
            onDismiss = { showFormatDialog = false },
            onSelect = { format ->
                showFormatDialog = false
                onShareClick(selectedTab, format)
            }
        )
    }
}

@Composable
private fun BatterStatsTab(teamId: Long, db: DatabaseHelper) {
    val rawRows = remember(teamId) { db.getSeasonBatterStats(teamId) }
    val players = remember(teamId) { db.getPlayersForTeam(teamId).associateBy { it.id } }

    // Default: sort by AVG descending (col index 7)
    var sortCol by remember { mutableStateOf(7) }
    var sortAsc by remember { mutableStateOf(false) }

    val rows = remember(rawRows, sortCol, sortAsc) {
        fun name(r: SeasonBatterRow) = players[r.playerId]?.name ?: ""
        fun avg(r: SeasonBatterRow) = if (r.ab > 0) r.hits.toFloat() / r.ab else -1f
        fun obp(r: SeasonBatterRow): Float {
            val d = r.ab + r.walks + r.hbp
            return if (d > 0) (r.hits + r.walks + r.hbp).toFloat() / d else -1f
        }
        fun slg(r: SeasonBatterRow): Float {
            if (r.ab == 0) return -1f
            val s = r.hits - r.doubles - r.triples - r.homers
            return (s + 2 * r.doubles + 3 * r.triples + 4 * r.homers).toFloat() / r.ab
        }
        fun ops(r: SeasonBatterRow): Float {
            val o = obp(r); val s = slg(r)
            return if (o < 0 && s < 0) -1f else maxOf(0f, o) + maxOf(0f, s)
        }
        val sorted = when (sortCol) {
            0  -> rawRows.sortedBy { name(it) }
            1  -> rawRows.sortedBy { it.pa }
            2  -> rawRows.sortedBy { it.ab }
            3  -> rawRows.sortedBy { it.hits }
            4  -> rawRows.sortedBy { it.doubles }
            5  -> rawRows.sortedBy { it.triples }
            6  -> rawRows.sortedBy { it.homers }
            7  -> rawRows.sortedBy { avg(it) }
            8  -> rawRows.sortedBy { obp(it) }
            9  -> rawRows.sortedBy { slg(it) }
            10 -> rawRows.sortedBy { ops(it) }
            11 -> rawRows.sortedBy { it.walks }
            12 -> rawRows.sortedBy { it.strikeouts }
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

    val hScroll = rememberScrollState()
    val colName = 100.dp
    val colStat = 36.dp
    val colDec  = 46.dp

    val colDefs = listOf(
        stringResource(R.string.season_stats_col_name) to colName,
        stringResource(R.string.season_stats_col_pa)   to colStat,
        stringResource(R.string.season_stats_col_ab)   to colStat,
        stringResource(R.string.season_stats_col_h)    to colStat,
        stringResource(R.string.season_stats_col_2b)   to colStat,
        stringResource(R.string.season_stats_col_3b)   to colStat,
        stringResource(R.string.season_stats_col_hr)   to colStat,
        stringResource(R.string.season_stats_col_avg)  to colDec,
        stringResource(R.string.season_stats_col_obp)  to colDec,
        stringResource(R.string.season_stats_col_slg)  to colDec,
        stringResource(R.string.season_stats_col_ops)  to colDec,
        stringResource(R.string.season_stats_col_bb)   to colStat,
        stringResource(R.string.season_stats_col_k)    to colStat
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Sortable header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.color_primary))
                .horizontalScroll(hScroll)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            colDefs.forEachIndexed { index, (label, width) ->
                val isActive = index == sortCol
                val indicator = if (isActive) if (sortAsc) " ▲" else " ▼" else ""
                Box(
                    modifier = Modifier
                        .width(width)
                        .clickable {
                            if (sortCol == index) sortAsc = !sortAsc
                            else { sortCol = index; sortAsc = index == 0 }
                        },
                    contentAlignment = if (index == 0) Alignment.CenterStart else Alignment.Center
                ) {
                    Text(
                        text = label + indicator,
                        color = if (isActive) Color.Yellow else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = if (index == 0) TextAlign.Start else TextAlign.Center
                    )
                }
            }
        }

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
                val singles = row.hits - row.doubles - row.triples - row.homers
                val slgNumer = singles + 2 * row.doubles + 3 * row.triples + 4 * row.homers
                val slg = if (row.ab > 0) slgNumer.toFloat() / row.ab else 0f
                val slgStr = when {
                    row.ab == 0 -> "--"
                    slg >= 1f   -> "%.3f".format(slg)
                    else        -> ".%03d".format((slg * 1000).toInt())
                }
                val opsVal = maxOf(0f, obp) + maxOf(0f, slg)
                val opsStr = if (obpDenom == 0 && row.ab == 0) "--"
                             else "%.3f".format(opsVal).trimStart('0').ifEmpty { ".000" }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) Color.White else colorResource(R.color.color_background))
                        .horizontalScroll(hScroll)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        name                      to colName,
                        row.pa.toString()         to colStat,
                        row.ab.toString()         to colStat,
                        row.hits.toString()       to colStat,
                        row.doubles.toString()    to colStat,
                        row.triples.toString()    to colStat,
                        row.homers.toString()     to colStat,
                        avgStr                    to colDec,
                        obpStr                    to colDec,
                        slgStr                    to colDec,
                        opsStr                    to colDec,
                        row.walks.toString()      to colStat,
                        row.strikeouts.toString() to colStat
                    ).forEachIndexed { i, (text, width) ->
                        Text(
                            text = text,
                            modifier = Modifier.width(width),
                            fontSize = 12.sp,
                            color = colorResource(R.color.color_text_primary),
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
            if (r.totalPitches > 0) r.strikes.toFloat() / r.totalPitches else -1f
        val sorted = when (sortCol) {
            0  -> rawRows.sortedBy { name(it) }
            1  -> rawRows.sortedBy { it.bf }
            2  -> rawRows.sortedBy { it.totalPitches }
            3  -> rawRows.sortedBy { spct(it) }
            4  -> rawRows.sortedBy { it.walks }
            5  -> rawRows.sortedBy { it.ks }
            6  -> rawRows.sortedBy { it.hits }
            7  -> rawRows.sortedBy { it.homers }
            8  -> rawRows.sortedBy { it.gos }
            9  -> rawRows.sortedBy { it.fos }
            else -> rawRows.sortedBy { spct(it) }
        }
        if (sortAsc) sorted else sorted.reversed()
    }

    if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.season_stats_empty_pitcher), color = colorResource(R.color.color_text_secondary))
        }
        return
    }

    val hScroll = rememberScrollState()
    val colName = 100.dp
    val colStat = 36.dp
    val colPct  = 46.dp

    val colDefs = listOf(
        stringResource(R.string.season_stats_col_name)       to colName,
        stringResource(R.string.season_stats_col_bf)         to colStat,
        stringResource(R.string.season_stats_col_p)          to colStat,
        stringResource(R.string.season_stats_col_strike_pct) to colPct,
        stringResource(R.string.season_stats_col_bb)         to colStat,
        stringResource(R.string.season_stats_col_k)          to colStat,
        stringResource(R.string.season_stats_col_h)          to colStat,
        stringResource(R.string.season_stats_col_hr)         to colStat,
        stringResource(R.string.season_stats_col_go)         to colStat,
        stringResource(R.string.season_stats_col_fo)         to colStat
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Sortable header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.color_primary))
                .horizontalScroll(hScroll)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            colDefs.forEachIndexed { index, (label, width) ->
                val isActive = index == sortCol
                val indicator = if (isActive) if (sortAsc) " ▲" else " ▼" else ""
                Box(
                    modifier = Modifier
                        .width(width)
                        .clickable {
                            if (sortCol == index) sortAsc = !sortAsc
                            else { sortCol = index; sortAsc = index == 0 }
                        },
                    contentAlignment = if (index == 0) Alignment.CenterStart else Alignment.Center
                ) {
                    Text(
                        text = label + indicator,
                        color = if (isActive) Color.Yellow else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = if (index == 0) TextAlign.Start else TextAlign.Center
                    )
                }
            }
        }

        LazyColumn {
            itemsIndexed(rows) { index, row ->
                val name = players[row.playerId]?.let { "#${it.number} ${it.name}" }
                    ?: stringResource(R.string.season_stats_unknown_player)
                val strikePctStr = if (row.totalPitches > 0)
                    "%.0f%%".format(row.strikes.toFloat() / row.totalPitches * 100)
                else "---"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index % 2 == 0) Color.White else colorResource(R.color.color_background))
                        .horizontalScroll(hScroll)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        name                       to colName,
                        row.bf.toString()          to colStat,
                        row.totalPitches.toString() to colStat,
                        strikePctStr               to colPct,
                        row.walks.toString()       to colStat,
                        row.ks.toString()          to colStat,
                        row.hits.toString()        to colStat,
                        row.homers.toString()      to colStat,
                        row.gos.toString()         to colStat,
                        row.fos.toString()         to colStat
                    ).forEachIndexed { i, (text, width) ->
                        Text(
                            text = text,
                            modifier = Modifier.width(width),
                            fontSize = 12.sp,
                            color = colorResource(R.color.color_text_primary),
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
