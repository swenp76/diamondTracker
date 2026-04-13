package de.baseball.diamond9

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

class BatterStatsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                onBackClick  = { finish() }
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
    onBackClick: () -> Unit
) {
    val rows = remember(gameId) { db.getGameBatterStats(gameId) }
    val players = remember(gameId) {
        val teamId = db.getGame(gameId)?.teamId ?: -1L
        if (teamId > 0) db.getPlayersForTeam(teamId).associateBy { it.id } else emptyMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(gameOpponent)
                        if (gameDate.isNotBlank()) {
                            Text(
                                gameDate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back)
                        )
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
            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.batter_stats_empty),
                        color = colorResource(R.color.color_text_secondary)
                    )
                }
            } else {
                val hScroll = rememberScrollState()
                val colName = 100.dp
                val colStat = 36.dp
                val colDec  = 46.dp
                val sortedRows = rows.sortedByDescending { it.ab }

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorResource(R.color.color_primary))
                        .horizontalScroll(hScroll)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    listOf(
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
                    ).forEachIndexed { i, (label, width) ->
                        Text(
                            text = label,
                            modifier = Modifier.width(width),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.Center
                        )
                    }
                }

                LazyColumn {
                    items(sortedRows) { row ->
                        val player = players[row.playerId]
                        val name = player?.let { "#${it.number} ${it.name}" }
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
                        val isEven = sortedRows.indexOf(row) % 2 == 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isEven) Color.White else colorResource(R.color.color_background))
                                .horizontalScroll(hScroll)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                name                        to colName,
                                row.pa.toString()           to colStat,
                                row.ab.toString()           to colStat,
                                row.hits.toString()         to colStat,
                                row.doubles.toString()      to colStat,
                                row.triples.toString()      to colStat,
                                row.homers.toString()       to colStat,
                                avgStr                      to colDec,
                                obpStr                      to colDec,
                                slgStr                      to colDec,
                                opsStr                      to colDec,
                                row.walks.toString()        to colStat,
                                row.strikeouts.toString()   to colStat
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
    }
}
