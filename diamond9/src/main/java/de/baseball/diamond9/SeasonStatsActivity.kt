package de.baseball.diamond9

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
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

    private var pendingSaveFile: java.io.File? = null
    private var pendingSaveFormat: ExportFormat = ExportFormat.PDF

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

        val teamId = intent.getLongExtra("teamId", -1L)
        val teamName = intent.getStringExtra("teamName") ?: ""
        val db = DatabaseHelper(this)

        setContent {
            var startDate by remember { mutableStateOf("") }
            var endDate by remember { mutableStateOf("") }

            SeasonStatsScreen(
                teamId = teamId,
                teamName = teamName,
                db = db,
                onBackClick = { finish() },
                startDate = startDate,
                endDate = endDate,
                onDateChange = { s, e -> startDate = s; endDate = e },
                onExport = { tab, format, action ->
                    val players = db.getPlayersForTeam(teamId).associateBy { it.id }
                    val dateRange = if (startDate.isNotBlank() || endDate.isNotBlank()) " ($startDate - $endDate)" else ""
                    val file = when (tab) {
                        0 -> StatsExporter.buildBatterTable(this, teamName, dateRange, db.getSeasonBatterStats(teamId, startDate, endDate), players, format, getString(R.string.season_stats_tab_batter))
                        else -> StatsExporter.buildSeasonPitcherTable(this, teamName, dateRange, db.getSeasonPitcherStats(teamId, startDate, endDate), players, format, getString(R.string.season_stats_tab_pitcher))
                    }
                    when (action) {
                        ExportAction.SHARE -> StatsExporter.shareFile(this, file, format)
                        ExportAction.SAVE  -> {
                            pendingSaveFile = file
                            pendingSaveFormat = format
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
private fun SeasonStatsScreen(
    teamId: Long,
    teamName: String,
    db: DatabaseHelper,
    startDate: String,
    endDate: String,
    onDateChange: (String, String) -> Unit,
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
            DateFilterHeader(
                startDate = startDate,
                endDate = endDate,
                onDateChange = onDateChange
            )

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
                0 -> BatterStatsTab(teamId = teamId, db = db, startDate = startDate, endDate = endDate)
                1 -> PitcherStatsTab(teamId = teamId, db = db, startDate = startDate, endDate = endDate)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterHeader(
    startDate: String,
    endDate: String,
    onDateChange: (String, String) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Start Date
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.season_stats_from), style = MaterialTheme.typography.labelMedium, color = colorResource(R.color.color_text_secondary))
                OutlinedCard(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        startDate.ifEmpty { stringResource(R.string.hint_date_format) },
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // End Date
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.season_stats_to), style = MaterialTheme.typography.labelMedium, color = colorResource(R.color.color_text_secondary))
                OutlinedCard(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        endDate.ifEmpty { stringResource(R.string.hint_date_format) },
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Clear Button
            if (startDate.isNotBlank() || endDate.isNotBlank()) {
                IconButton(
                    onClick = { onDateChange("", "") },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.season_stats_clear_filter),
                        tint = colorResource(R.color.color_strike)
                    )
                }
            }
        }
    }

    if (showStartPicker) {
        DatePickerDialog(
            onDismiss = { showStartPicker = false },
            onDateSelected = { onDateChange(it, endDate); showStartPicker = false }
        )
    }
    if (showEndPicker) {
        DatePickerDialog(
            onDismiss = { showEndPicker = false },
            onDateSelected = { onDateChange(startDate, it); showEndPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val datePickerState = rememberDatePickerState()
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = it }
                    val dateStr = "%02d.%02d.%04d".format(
                        cal.get(java.util.Calendar.DAY_OF_MONTH),
                        cal.get(java.util.Calendar.MONTH) + 1,
                        cal.get(java.util.Calendar.YEAR)
                    )
                    onDateSelected(dateStr)
                }
            }) { Text(stringResource(R.string.btn_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    ) {
        androidx.compose.material3.DatePicker(state = datePickerState)
    }
}

@Composable
private fun BatterStatsTab(teamId: Long, db: DatabaseHelper, startDate: String, endDate: String) {
    val rawRows = remember(teamId, startDate, endDate) { db.getSeasonBatterStats(teamId, startDate, endDate) }
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
private fun PitcherStatsTab(teamId: Long, db: DatabaseHelper, startDate: String, endDate: String) {
    val rawRows = remember(teamId, startDate, endDate) { db.getSeasonPitcherStats(teamId, startDate, endDate) }
    val players = remember(teamId) { db.getPlayersForTeam(teamId).associateBy { it.id } }

    // Default: sort by S% descending (col index 3)
    var sortCol by remember { mutableStateOf(3) }
    var sortAsc by remember { mutableStateOf(false) }

    val rows = remember(rawRows, sortCol, sortAsc) {
        fun name(r: SeasonPitcherRow) = players[r.playerId]?.name ?: ""
        fun spct(r: SeasonPitcherRow) =
            if (r.totalPitches > 0) (r.strikes + r.fouls).toFloat() / r.totalPitches else -1f
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
                    "%.0f%%".format((row.strikes + row.fouls).toFloat() / row.totalPitches * 100)
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
