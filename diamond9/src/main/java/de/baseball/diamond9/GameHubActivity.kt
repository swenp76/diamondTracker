package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class GameHubActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val gameId = intent.getLongExtra("gameId", -1)
        val gameOpponent = intent.getStringExtra("gameOpponent") ?: ""
        val gameDate = intent.getStringExtra("gameDate") ?: ""

        setContent {
            GameHubScreen(
                gameId = gameId,
                gameOpponent = gameOpponent,
                gameDate = gameDate,
                onBackClick = { finish() },
                onOffenseClick = {
                    val intent = Intent(this, BattingTrackActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    }
                    startActivity(intent)
                },
                onDefenseClick = {
                    val intent = Intent(this, PitcherListActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    }
                    startActivity(intent)
                },
                onLineupClick = {
                    val intent = Intent(this, OwnLineupActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("gameOpponent", gameOpponent)
                        putExtra("gameDate", gameDate)
                    }
                    startActivity(intent)
                },
                onOppoLineupClick = {
                    val intent = Intent(this, OpponentLineupActivity::class.java).apply {
                        putExtra("gameId", gameId)
                        putExtra("opponentName", gameOpponent)
                    }
                    startActivity(intent)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameHubScreen(
    gameId: Long,
    gameOpponent: String,
    gameDate: String,
    onBackClick: () -> Unit,
    onOffenseClick: () -> Unit,
    onDefenseClick: () -> Unit,
    onLineupClick: () -> Unit,
    onOppoLineupClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(gameOpponent, style = MaterialTheme.typography.titleLarge)
                        Text(gameDate, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color(0xFFF5F5F5)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HubButton(
                    text = stringResource(R.string.gamehub_offense),
                    color = Color(0xFF1a5fa8),
                    onClick = onOffenseClick
                )

                HubButton(
                    text = stringResource(R.string.gamehub_defense),
                    color = Color(0xFFc0392b),
                    onClick = onDefenseClick
                )

                HubButton(
                    text = stringResource(R.string.gamehub_lineup),
                    color = Color(0xFF2c7a2c),
                    onClick = onLineupClick
                )

                HubButton(
                    text = stringResource(R.string.gamehub_oppo_lineup),
                    color = Color(0xFF7d3c98),
                    onClick = onOppoLineupClick
                )
            }
        }
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
            .height(104.dp) // Adjusted to include some padding/spacing internally
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
