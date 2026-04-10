package de.baseball.diamond9

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ManageOpponentsActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = DatabaseHelper(this)

        val teamId = intent.getLongExtra("teamId", 0L)

        setContent {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            AppDrawer(
                drawerState = drawerState,
                scope = scope,
                currentActivity = ManageOpponentsActivity::class.java,
                context = this,
                teamId = teamId
            ) {
                ManageOpponentsScreen(
                    db = db,
                    teamId = teamId,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageOpponentsScreen(
    db: DatabaseHelper,
    teamId: Long,
    onMenuClick: () -> Unit
) {
    var opponents by remember { mutableStateOf(emptyList<OpponentTeam>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var opponentToDelete by remember { mutableStateOf<OpponentTeam?>(null) }

    fun refresh() {
        opponents = db.getOpponentTeamsForTeam(teamId)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_opponents_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = colorResource(R.color.color_primary),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.fab_add_opponent)) }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = colorResource(R.color.color_background)
        ) {
            if (opponents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.empty_opponents),
                        fontSize = 15.sp,
                        color = colorResource(R.color.color_text_secondary),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(opponents) { opponent ->
                        OpponentItem(
                            opponent = opponent,
                            onDelete = { opponentToDelete = opponent }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.dialog_add_opponent_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; error = false },
                    label = { Text(stringResource(R.string.hint_opponent_team_name)) },
                    isError = error,
                    supportingText = if (error) { { Text(stringResource(R.string.error_required_field)) } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        db.insertOpponentTeamForTeam(newName.trim(), teamId)
                        refresh()
                        showAddDialog = false
                    } else {
                        error = true
                    }
                }) { Text(stringResource(R.string.btn_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    opponentToDelete?.let { opponent ->
        AlertDialog(
            onDismissRequest = { opponentToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_opponent_title)) },
            text = { Text(stringResource(R.string.dialog_delete_opponent_message, opponent.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        db.deleteOpponentTeam(opponent.id)
                        refresh()
                        opponentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike))
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { opponentToDelete = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
private fun OpponentItem(
    opponent: OpponentTeam,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = opponent.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.content_desc_delete), tint = colorResource(R.color.color_strike))
            }
        }
    }
}
