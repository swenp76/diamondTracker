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
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorResource(R.color.color_primary))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    listOf(
                        stringResource(R.string.season_stats_col_name) to 3f,
                        stringResource(R.string.season_stats_col_pa)   to 1f,
                        stringResource(R.string.season_stats_col_ab)   to 1f,
                        stringResource(R.string.season_stats_col_h)    to 1f,
                        stringResource(R.string.season_stats_col_avg)  to 1.4f,
                        stringResource(R.string.season_stats_col_obp)  to 1.4f,
                        stringResource(R.string.season_stats_col_bb)   to 1f,
                        stringResource(R.string.season_stats_col_k)    to 1f
                    ).forEachIndexed { i, (label, weight) ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(weight),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.Center
                        )
                    }
                }

                LazyColumn {
                    items(rows.sortedByDescending { it.ab }) { row ->
                        val player = players[row.playerId]
                        val name = player?.let { "#${it.number} ${it.name}" }
                            ?: stringResource(R.string.season_stats_unknown_player)
                        val avg = if (row.ab > 0) row.hits.toFloat() / row.ab else 0f
                        val avgStr = when {
                            row.ab == 0 -> "--"
                            avg >= 1f -> "1.000"
                            else -> ".%03d".format((avg * 1000).toInt())
                        }
                        val obpDenom = row.ab + row.walks + row.hbp
                        val obp = if (obpDenom > 0) (row.hits + row.walks + row.hbp).toFloat() / obpDenom else 0f
                        val obpStr = when {
                            obpDenom == 0 -> "--"
                            obp >= 1f -> "1.000"
                            else -> ".%03d".format((obp * 1000).toInt())
                        }
                        val isEven = rows.sortedByDescending { it.ab }.indexOf(row) % 2 == 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isEven) Color.White else colorResource(R.color.color_background))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                name                  to 3f,
                                row.pa.toString()     to 1f,
                                row.ab.toString()     to 1f,
                                row.hits.toString()   to 1f,
                                avgStr                to 1.4f,
                                obpStr                to 1.4f,
                                row.walks.toString()  to 1f,
                                row.strikeouts.toString() to 1f
                            ).forEachIndexed { i, (text, weight) ->
                                Text(
                                    text = text,
                                    modifier = Modifier.weight(weight),
                                    fontSize = 13.sp,
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
