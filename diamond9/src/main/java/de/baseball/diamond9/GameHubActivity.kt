package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class GameHubActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper
    private var gameId: Long = -1
    private var gameOpponent: String = ""
    private var gameDate: String = ""
    private var gameTime: String = ""
    private var teamName: String = ""
    private var teamId: Long = -1
    private var isHome: Int = 1

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(buildLineupJson().toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.toast_lineup_exported), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_lineup_import_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@registerForActivityResult
            val filled = importLineupJson(json)
            Toast.makeText(this, getString(R.string.toast_lineup_imported, filled), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_lineup_import_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        gameId = intent.getLongExtra("gameId", -1)
        gameOpponent = intent.getStringExtra("gameOpponent") ?: ""
        gameDate = intent.getStringExtra("gameDate") ?: ""
        gameTime = intent.getStringExtra("gameTime") ?: ""

        db = DatabaseHelper(this)
        val game = db.getGame(gameId)
        teamId = game?.teamId ?: -1
        teamName = game?.teamId?.let { db.getTeamById(it)?.name } ?: ""
        isHome = game?.isHome ?: 1

        setContent {
            GameHubScreen(
                gameId = gameId,
                gameOpponent = gameOpponent,
                gameDate = gameDate,
                gameTime = gameTime,
                teamName = teamName,
                isHome = isHome,
                db = db,
                onBackClick = { finish() },
                onOffenseClick = {
                    startActivity(Intent(this, BattingTrackActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    })
                },
                onDefenseClick = {
                    startActivity(Intent(this, PitcherListActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    })
                },
                onLineupClick = {
                    startActivity(Intent(this, OwnLineupActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    })
                },
                onOppoLineupClick = {
                    startActivity(Intent(this, OpponentLineupActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("opponentName", gameOpponent)
                    })
                },
                onBatterStatsClick = {
                    startActivity(Intent(this, BatterStatsActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    })
                },
                onExportClick = {
                    val safeName = gameOpponent.replace(Regex("[^A-Za-z0-9_\\- ]"), "_")
                    exportLauncher.launch("lineup_${safeName}_${gameDate}.json")
                },
                onImportClick = {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }
            )
        }
    }

    private fun buildLineupJson(): String {
        val root = JSONObject()
        root.put("exportType", "gameLineup")
        root.put("version", 1)
        root.put("gameDate", gameDate)
        root.put("gameTime", gameTime)
        root.put("opponent", gameOpponent)
        root.put("isHome", isHome)
        root.put("teamName", teamName)

        val ownLineup = db.getOwnLineup(gameId)
        val ownArr = JSONArray()
        for (slot in 1..20) {
            val player = ownLineup[slot] ?: continue
            ownArr.put(JSONObject().apply {
                put("slot", slot)
                put("playerNumber", player.number)
                put("playerName", player.name)
                put("primaryPosition", player.primaryPosition)
            })
        }
        root.put("ownLineup", ownArr)

        val oppoArr = JSONArray()
        for (entry in db.getLineup(gameId)) {
            oppoArr.put(JSONObject().apply {
                put("battingOrder", entry.battingOrder)
                put("jerseyNumber", entry.jerseyNumber)
            })
        }
        root.put("opponentLineup", oppoArr)

        val benchArr = JSONArray()
        for (b in db.getBenchPlayers(gameId)) {
            benchArr.put(JSONObject().apply { put("jerseyNumber", b.jerseyNumber) })
        }
        root.put("opponentBench", benchArr)

        return root.toString(2)
    }

    /** Returns the number of own lineup slots successfully filled. */
    private fun importLineupJson(json: String): Int {
        val root = JSONObject(json)
        if (root.optString("exportType") != "gameLineup") {
            Toast.makeText(this, getString(R.string.toast_lineup_invalid_format), Toast.LENGTH_LONG).show()
            return 0
        }

        db.clearLineupForGame(gameId)

        var filled = 0

        // Own lineup – resolve by jersey number from current team's roster
        val players = if (teamId >= 0) db.getPlayersForTeam(teamId) else emptyList()
        val byNumber = players.groupBy { it.number }

        val ownArr = root.optJSONArray("ownLineup")
        if (ownArr != null) {
            for (i in 0 until ownArr.length()) {
                val obj = ownArr.getJSONObject(i)
                val slot = obj.getInt("slot")
                val number = obj.getString("playerNumber")
                val player = byNumber[number]?.firstOrNull() ?: continue
                db.setOwnLineupPlayer(gameId, slot, player.id)
                filled++
            }
        }

        // Opponent lineup
        val oppoArr = root.optJSONArray("opponentLineup")
        if (oppoArr != null) {
            for (i in 0 until oppoArr.length()) {
                val obj = oppoArr.getJSONObject(i)
                db.upsertLineupEntry(gameId, obj.getInt("battingOrder"), obj.getString("jerseyNumber"))
            }
        }

        // Opponent bench
        val benchArr = root.optJSONArray("opponentBench")
        if (benchArr != null) {
            for (i in 0 until benchArr.length()) {
                db.insertBenchPlayer(gameId, benchArr.getJSONObject(i).getString("jerseyNumber"))
            }
        }

        return filled
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHubScreen(
    gameId: Long,
    gameOpponent: String,
    gameDate: String,
    gameTime: String,
    teamName: String,
    isHome: Int,
    db: DatabaseHelper,
    onBackClick: () -> Unit,
    onOffenseClick: () -> Unit,
    onDefenseClick: () -> Unit,
    onLineupClick: () -> Unit,
    onOppoLineupClick: () -> Unit,
    onBatterStatsClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val dateLabel = if (gameTime.isNotEmpty()) "$gameDate  $gameTime" else gameDate
    var menuExpanded by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(gameOpponent, style = MaterialTheme.typography.titleLarge)
                        Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_lineup)) },
                                onClick = { menuExpanded = false; onExportClick() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_import_lineup)) },
                                onClick = { menuExpanded = false; onImportClick() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = colorResource(R.color.color_background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Scoreboard(
                    gameId = gameId,
                    ownTeam = teamName,
                    opponentTeam = gameOpponent,
                    ownIsHome = isHome,
                    db = db
                )
                GameTimer(gameId = gameId, db = db)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HubButton(
                        text = stringResource(R.string.gamehub_offense),
                        color = colorResource(R.color.color_primary),
                        onClick = onOffenseClick
                    )
                    HubButton(
                        text = stringResource(R.string.gamehub_defense),
                        color = colorResource(R.color.color_strike),
                        onClick = onDefenseClick
                    )
                    HubButton(
                        text = stringResource(R.string.gamehub_lineup),
                        color = colorResource(R.color.color_green),
                        onClick = onLineupClick
                    )
                    HubButton(
                        text = stringResource(R.string.gamehub_oppo_lineup),
                        color = colorResource(R.color.color_purple),
                        onClick = onOppoLineupClick
                    )
                    HubButton(
                        text = stringResource(R.string.gamehub_batter_stats),
                        color = colorResource(R.color.color_orange),
                        onClick = onBatterStatsClick
                    )
                }
            }
        }
    }
}

@Composable
private fun Scoreboard(
    gameId: Long,
    ownTeam: String,
    opponentTeam: String,
    ownIsHome: Int,   // 1 = own team is home, 0 = own team is away
    db: DatabaseHelper
) {
    // isHome=0 row = away team, isHome=1 row = home team
    // If ownIsHome==1: row0 (away) = opponent, row1 (home) = own team
    // If ownIsHome==0: row0 (away) = own team, row1 (home) = opponent
    val awayTeam = if (ownIsHome == 1) opponentTeam else ownTeam
    val homeTeam = if (ownIsHome == 1) ownTeam else opponentTeam

    var scoreboardRuns by remember { mutableStateOf(db.getScoreboard(gameId)) }
    var editTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (inning, isHome)
    var inputValue by remember { mutableStateOf("") }

    val totalInnings = 9
    val scrollState = rememberScrollState()

    fun runsFor(inning: Int, isHome: Int): Int =
        scoreboardRuns.find { it.inning == inning && it.isHome == isHome }?.runs ?: 0

    fun hasEntry(inning: Int, isHome: Int): Boolean =
        scoreboardRuns.any { it.inning == inning && it.isHome == isHome }

    fun totalFor(isHome: Int): Int =
        (1..totalInnings).sumOf { runsFor(it, isHome) }

    editTarget?.let { (inning, isHome) ->
        val teamLabel = if (isHome == 1) homeTeam else awayTeam
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(stringResource(R.string.scoreboard_dialog_title, inning, teamLabel)) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { v -> inputValue = v.filter { it.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.scoreboard_runs_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    db.upsertScoreboardRun(gameId, inning, isHome, inputValue.toIntOrNull() ?: 0)
                    scoreboardRuns = db.getScoreboard(gameId)
                    editTarget = null
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Fixed left column: team names
            Column {
                ScoreCell(text = "", width = 90.dp, isHeader = true)
                ScoreCell(
                    text = awayTeam.ifBlank { stringResource(R.string.scoreboard_away) },
                    width = 90.dp,
                    fontWeight = FontWeight.Bold
                )
                ScoreCell(
                    text = homeTeam.ifBlank { stringResource(R.string.scoreboard_home) },
                    width = 90.dp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Scrollable inning columns
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                for (inning in 1..totalInnings) {
                    Column {
                        ScoreCell(text = inning.toString(), isHeader = true)
                        ScoreCell(
                            text = if (hasEntry(inning, 0)) runsFor(inning, 0).toString() else "-",
                            onClick = {
                                inputValue = runsFor(inning, 0).let { if (it > 0) it.toString() else "" }
                                editTarget = Pair(inning, 0)
                            }
                        )
                        ScoreCell(
                            text = if (hasEntry(inning, 1)) runsFor(inning, 1).toString() else "-",
                            onClick = {
                                inputValue = runsFor(inning, 1).let { if (it > 0) it.toString() else "" }
                                editTarget = Pair(inning, 1)
                            }
                        )
                    }
                }
            }

            // Fixed right column: totals
            Column {
                ScoreCell(text = stringResource(R.string.scoreboard_total_label), isHeader = true, width = 36.dp, fontWeight = FontWeight.Bold)
                ScoreCell(text = totalFor(0).toString(), width = 36.dp, fontWeight = FontWeight.Bold, bgColor = colorResource(R.color.color_divider_light))
                ScoreCell(text = totalFor(1).toString(), width = 36.dp, fontWeight = FontWeight.Bold, bgColor = colorResource(R.color.color_divider_light))
            }
        }
    }
}

@Composable
private fun GameTimer(gameId: Long, db: DatabaseHelper) {
    var startTime by remember { mutableStateOf(db.getStartTime(gameId)) }
    var elapsedMs by remember { mutableStateOf(if (startTime > 0L) System.currentTimeMillis() - startTime else 0L) }

    LaunchedEffect(startTime) {
        if (startTime > 0L) {
            while (true) {
                elapsedMs = System.currentTimeMillis() - startTime
                delay(1000L)
            }
        }
    }

    val hours = elapsedMs / 3_600_000L
    val minutes = (elapsedMs % 3_600_000L) / 60_000L
    val seconds = (elapsedMs % 60_000L) / 1_000L
    val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = timeString,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (startTime > 0L) colorResource(R.color.color_primary) else colorResource(R.color.color_text_secondary)
            )
            if (startTime == 0L) {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        db.setStartTime(gameId, now)
                        startTime = now
                        elapsedMs = 0L
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_green)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.timer_btn_start), fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = stringResource(R.string.timer_label_running),
                    fontSize = 13.sp,
                    color = colorResource(R.color.color_text_secondary)
                )
            }
        }
    }
}

@Composable
private fun ScoreCell(
    text: String,
    width: Dp = 36.dp,
    isHeader: Boolean = false,
    fontWeight: FontWeight = FontWeight.Normal,
    bgColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null
) {
    val background = if (isHeader) colorResource(R.color.color_primary) else bgColor
    val textColor = if (isHeader) Color.White else colorResource(R.color.color_text_primary)

    Box(
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .background(background)
            .border(0.5.dp, colorResource(R.color.color_gray_medium))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun HubButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .padding(vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
