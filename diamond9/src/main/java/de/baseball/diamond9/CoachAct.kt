package de.baseball.diamond9

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import org.json.JSONObject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

private val Primary = Color(0xFF1a5fa8)

class CoachAct : ComponentActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = DatabaseHelper(this)
        setContent {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            AppDrawer(
                drawerState = drawerState,
                scope = scope,
                currentActivity = CoachAct::class.java,
                context = this
            ) {
                CoachSelectScreen(
                    loadTeams = { db.getAllTeams() },
                    onTeamClick = { team ->
                        startActivity(
                            Intent(this, GameListActivity::class.java).apply {
                                putExtra("teamId", team.id)
                                putExtra("teamName", team.name)
                            }
                        )
                    },
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onLoadDemoTeam = { showLoadDemoTeamDialog() }
                )
            }
        }
    }

    private fun showLoadDemoTeamDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_load_demo_title))
            .setMessage(getString(R.string.dialog_load_demo_message))
            .setPositiveButton(getString(R.string.btn_import)) { _, _ -> importDemoTeam() }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun importDemoTeam() {
        try {
            val json = resources.openRawResource(R.raw.demo_team).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val root = JSONObject(json)
            val teamId = db.insertTeam(root.getString("name"))

            db.getEnabledPositions(teamId).forEach { db.setPositionEnabled(teamId, it, false) }
            val posArray = root.optJSONArray("positions")
            if (posArray != null) {
                for (p in 0 until posArray.length()) db.setPositionEnabled(teamId, posArray.getInt(p), true)
            }

            val playersArray = root.optJSONArray("players")
            if (playersArray != null) {
                for (p in 0 until playersArray.length()) {
                    val pl = playersArray.getJSONObject(p)
                    db.insertPlayer(
                        teamId,
                        pl.getString("name"),
                        pl.optString("number", ""),
                        pl.optInt("primary_position", 0),
                        pl.optInt("secondary_position", 0),
                        pl.optBoolean("is_pitcher", false),
                        pl.optInt("birth_year", 0)
                    )
                }
            }

            Toast.makeText(this, getString(R.string.toast_team_imported, root.getString("name")), Toast.LENGTH_SHORT).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_import_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun CoachSelectScreen(
    loadTeams: () -> List<Team>,
    onTeamClick: (Team) -> Unit,
    onMenuClick: () -> Unit,
    onLoadDemoTeam: () -> Unit
) {
    var teams by remember { mutableStateOf(loadTeams()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) teams = loadTeams()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF5F5F5)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.coach_select_title),
                    fontSize = 16.sp,
                    color = Color(0xFF555555),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                )
                if (teams.isEmpty()) {
                    Text(
                        text = stringResource(R.string.coach_select_no_teams),
                        fontSize = 15.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                    Button(
                        onClick = onLoadDemoTeam,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_load_demo_team),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(teams) { team ->
                            TeamSelectCard(team = team, onClick = { onTeamClick(team) })
                        }
                    }
                }
            }

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = Primary
                )
            }
        }
    }
}

@Composable
private fun TeamSelectCard(team: Team, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = team.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1a1a1a)
            )
            Text(
                text = "›",
                fontSize = 24.sp,
                color = Primary
            )
        }
    }
}
