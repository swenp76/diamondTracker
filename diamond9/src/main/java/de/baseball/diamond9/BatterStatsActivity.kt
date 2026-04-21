package de.baseball.diamond9

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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

class BatterStatsActivity : ComponentActivity() {

    private var pendingSaveFile: java.io.File? = null

    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            pendingSaveFile?.let { file ->
                contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                Toast.makeText(this, R.string.toast_stats_saved, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val gameId       = intent.getLongExtra("gameId", -1L)
        val gameOpponent = intent.getStringExtra("gameOpponent") ?: ""
        val gameDate     = intent.getStringExtra("gameDate") ?: ""
        val db           = DatabaseHelper(this)

        setContent {
            BatterStatsScreen(
                gameId       = gameId,
                gameOpponent = gameOpponent,
                gameDate     = gameDate,
                db           = db,
                onBackClick  = { finish() },
                onExport     = { tab, format, action ->
                    val teamId = db.getGame(gameId)?.teamId ?: -1L
                    val players = if (teamId > 0) db.getPlayersForTeam(teamId).associateBy { it.id } else emptyMap()
                    val file = when (tab) {
                        0 -> StatsExporter.buildGameBatterTable(this, gameOpponent, gameDate, db.getGameBatterStats(gameId), players, format)
                        else -> StatsExporter.buildGamePitcherTable(this, gameOpponent, gameDate, db.getGamePitcherStats(gameId), players, format)
                    }
                    when (action) {
                        ExportAction.SHARE -> StatsExporter.shareFile(this, file, format)
                        ExportAction.SAVE  -> {
                            pendingSaveFile = file
                            saveLauncher.launch("${if (tab == 0) "batter" else "pitcher"}_stats.${format.extension()}")
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatterStatsScreen(
    gameId: Long,
    gameOpponent: String,
    gameDate: String,
    db: DatabaseHelper,
    onBackClick: () -> Unit,
    onExport: (tab: Int, format: ExportFormat, action: ExportAction) -> Unit
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
                        Text(gameOpponent)
                        if (gameDate.isNotBlank()) {
                            Text(gameDate, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
                0 -> GameBatterTab(gameId = gameId, db = db)
                1 -> GamePitcherTab(gameId = gameId, db = db)
            }
        }
    }

    if (showFormatDialog) {
        ExportFormatDialog(
            onDismiss = { showFormatDialog = false },
            onSelect = { format, action ->
                showFormatDialog = false
                onExport(selectedTab, format, action)
            }
        )
    }
}

@Composable
private fun GameBatterTab(gameId: Long, db: DatabaseHelper) {
    val rawRows = remember(gameId) { db.getGameBatterStats(gameId) }
    val players = remember(gameId) {
        val teamId = db.getGame(gameId)?.teamId ?: -1L
        if (teamId > 0) db.getPlayersForTeam(teamId).associateBy { it.id } else emptyMap()
    }

    var sortCol by remember { mutableStateOf(7) }  // default: AVG desc
    var sortAsc by remember { mutableStateOf(false) }

    fun avg(r: GameBatterStatsRow) = if (r.ab > 0) r.hits.toFloat() / r.ab else -1f
    fun obp(r: GameBatterStatsRow): Float {
        val d = r.ab + r.walks + r.hbp
        return if (d > 0) (r.hits + r.walks + r.hbp).toFloat() / d else -1f
    }
    fun slg(r: GameBatterStatsRow): Float {
        if (r.ab == 0) return -1f
        val s = r.hits - r.doubles - r.triples - r.homers
        return (s + 2 * r.doubles + 3 * r.triples + 4 * r.homers).toFloat() / r.ab
    }
    fun ops(r: GameBatterStatsRow): Float {
        val o = obp(r); val s = slg(r)
        return if (o < 0 && s < 0) -1f else maxOf(0f, o) + maxOf(0f, s)
    }
    fun name(r: GameBatterStatsRow) = players[r.playerId]?.name ?: ""

    val rows = remember(rawRows, sortCol, sortAsc) {
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
            Text(stringResource(R.string.batter_stats_empty), color = colorResource(R.color.color_text_secondary))
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
                val avgVal = avg(row)
                val avgStr = when {
                    row.ab == 0 -> "--"
                    avgVal >= 1f -> "1.000"
                    else -> ".%03d".format((avgVal * 1000).toInt())
                }
                val obpVal = obp(row)
                val obpDenom = row.ab + row.walks + row.hbp
                val obpStr = when {
                    obpDenom == 0 -> "--"
                    obpVal >= 1f  -> "1.000"
                    else -> ".%03d".format((obpVal * 1000).toInt())
                }
                val slgVal = slg(row)
                val slgStr = when {
                    row.ab == 0 -> "--"
                    slgVal >= 1f -> "%.3f".format(slgVal)
                    else -> ".%03d".format((slgVal * 1000).toInt())
                }
                val opsVal = ops(row)
                val opsStr = if (obpDenom == 0 && row.ab == 0) "--"
                             else "%.3f".format(maxOf(0f, opsVal)).trimStart('0').ifEmpty { ".000" }

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
private fun GamePitcherTab(gameId: Long, db: DatabaseHelper) {
    val rawRows = remember(gameId) { db.getGamePitcherStats(gameId) }
    val players = remember(gameId) {
        val teamId = db.getGame(gameId)?.teamId ?: -1L
        if (teamId > 0) db.getPlayersForTeam(teamId).associateBy { it.id } else emptyMap()
    }

    var sortCol by remember { mutableStateOf(3) }  // default: S% desc
    var sortAsc by remember { mutableStateOf(false) }

    fun spct(r: PitcherStats) =
        if (r.totalPitches > 0) (r.strikes).toFloat() / r.totalPitches else -1f
    fun name(r: PitcherStats) =
        players[r.pitcher.playerId]?.name ?: r.pitcher.name

    val rows = remember(rawRows, sortCol, sortAsc) {
        val sorted = when (sortCol) {
            0  -> rawRows.sortedBy { name(it) }
            1  -> rawRows.sortedBy { it.bf }
            2  -> rawRows.sortedBy { it.totalPitches }
            3  -> rawRows.sortedBy { spct(it) }
            4  -> rawRows.sortedBy { it.walks }
            5  -> rawRows.sortedBy { it.strikeouts }
            6  -> rawRows.sortedBy { it.hits }
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
        stringResource(R.string.season_stats_col_h)          to colStat
    )

    Column(modifier = Modifier.fillMaxSize()) {
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
                val name = players[row.pitcher.playerId]?.let { "#${it.number} ${it.name}" }
                    ?: row.pitcher.name
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
                        name                          to colName,
                        row.bf.toString()             to colStat,
                        row.totalPitches.toString()   to colStat,
                        strikePctStr                  to colPct,
                        row.walks.toString()          to colStat,
                        row.strikeouts.toString()     to colStat,
                        row.hits.toString()           to colStat
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
