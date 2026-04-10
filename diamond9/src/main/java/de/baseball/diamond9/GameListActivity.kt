package de.baseball.diamond9

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GameListActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = DatabaseHelper(this)

        val teamId = intent.getLongExtra("teamId", 0L)
        val teamName = intent.getStringExtra("teamName") ?: ""

        setContent {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            AppDrawer(
                drawerState = drawerState,
                scope = scope,
                currentActivity = GameListActivity::class.java,
                context = this,
                teamId = teamId
            ) {
                GameListScreen(
                    teamId = teamId,
                    teamName = teamName,
                    db = db,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onGameClick = { game ->
                        startActivity(Intent(this, GameHubActivity::class.java).apply {
                            putExtra("gameId", game.id)
                            putExtra("gameOpponent", game.opponent)
                            putExtra("gameDate", game.date)
                            putExtra("gameTime", game.gameTime)
                        })
                    },
                    onSeasonStatsClick = {
                        startActivity(Intent(this, SeasonStatsActivity::class.java).apply {
                            putExtra("teamId", teamId)
                            putExtra("teamName", teamName)
                        })
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameListScreen(
    teamId: Long,
    teamName: String,
    db: DatabaseHelper,
    onMenuClick: () -> Unit,
    onGameClick: (Game) -> Unit,
    onSeasonStatsClick: () -> Unit = {}
) {
    var games by remember { mutableStateOf(emptyList<Game>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var gameToEdit by remember { mutableStateOf<Game?>(null) }
    var gameToDelete by remember { mutableStateOf<Game?>(null) }
    var gameToCopy by remember { mutableStateOf<Game?>(null) }

    fun refresh() {
        games = db.getGamesForTeam(teamId)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(teamName) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, null)
                    }
                },
                actions = {
                    IconButton(onClick = onSeasonStatsClick) {
                        Icon(Icons.Default.Leaderboard, contentDescription = stringResource(R.string.season_stats_btn))
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
                text = { Text(stringResource(R.string.fab_add_game)) }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = colorResource(R.color.color_background)
        ) {
            if (games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.empty_games),
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
                    items(games) { game ->
                        GameItem(
                            game = game,
                            onClick = { onGameClick(game) },
                            onEdit = { gameToEdit = game },
                            onCopy = { gameToCopy = game },
                            onDelete = { gameToDelete = game }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        GameDialog(
            title = stringResource(R.string.dialog_add_game_title),
            confirmLabel = stringResource(R.string.btn_create),
            opponents = db.getOpponentTeamsForTeam(teamId),
            onDismiss = { showAddDialog = false },
            onConfirm = { date, time, opponent ->
                db.insertOpponentTeamForTeam(opponent, teamId)
                db.insertGame(date, opponent, teamId, time)
                refresh()
                showAddDialog = false
            }
        )
    }

    gameToEdit?.let { game ->
        GameDialog(
            title = stringResource(R.string.dialog_edit_game_title),
            confirmLabel = stringResource(R.string.btn_save),
            initialDate = game.date,
            initialTime = game.gameTime,
            initialOpponent = game.opponent,
            opponents = db.getOpponentTeamsForTeam(teamId),
            onDismiss = { gameToEdit = null },
            onConfirm = { date, time, opponent ->
                db.insertOpponentTeamForTeam(opponent, teamId)
                db.updateGame(game.id, date, opponent, time)
                refresh()
                gameToEdit = null
            }
        )
    }

    gameToCopy?.let { game ->
        var newOpponent by remember { mutableStateOf(game.opponent) }
        var error by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { gameToCopy = null },
            title = { Text(stringResource(R.string.dialog_copy_game_title, game.date)) },
            text = {
                OutlinedTextField(
                    value = newOpponent,
                    onValueChange = { newOpponent = it; error = false },
                    label = { Text(stringResource(R.string.label_opponent)) },
                    isError = error,
                    supportingText = if (error) { { Text(stringResource(R.string.error_required_field)) } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newOpponent.isNotBlank()) {
                        db.copyGame(game.id, newOpponent.trim())
                        refresh()
                        gameToCopy = null
                    } else {
                        error = true
                    }
                }) { Text(stringResource(R.string.btn_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { gameToCopy = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    gameToDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_game_title)) },
            text = { Text(stringResource(R.string.dialog_delete_game_message, game.date, game.opponent)) },
            confirmButton = {
                Button(
                    onClick = {
                        db.deleteGame(game.id)
                        refresh()
                        gameToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_strike))
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { gameToDelete = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
private fun GameItem(
    game: Game,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.opponent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                val dateLabel = if (game.gameTime.isNotEmpty()) "${game.date}  ${game.gameTime}" else game.date
                Text(
                    text = dateLabel,
                    fontSize = 14.sp,
                    color = colorResource(R.color.color_text_secondary)
                )
            }

            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, null, tint = colorResource(R.color.color_primary))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, tint = colorResource(R.color.color_primary))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = colorResource(R.color.color_strike))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDialog(
    title: String,
    confirmLabel: String,
    initialDate: String = "",
    initialTime: String = "",
    initialOpponent: String = "",
    opponents: List<OpponentTeam>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit   // date, time, opponent
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    val calendar = remember {
        Calendar.getInstance().apply {
            if (initialDate.isNotEmpty()) {
                try {
                    val parts = initialDate.split(".")
                    if (parts.size == 3) {
                        set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                        set(Calendar.MONTH, parts[1].toInt() - 1)
                        set(Calendar.YEAR, parts[2].toInt())
                    }
                } catch (_: Exception) {}
            }
        }
    }

    var date by remember { mutableStateOf(if (initialDate.isNotEmpty()) initialDate else sdf.format(calendar.time)) }
    var time by remember { mutableStateOf(initialTime) }
    var opponentText by remember { mutableStateOf(if (initialOpponent.isNotEmpty() && opponents.none { it.name == initialOpponent }) initialOpponent else "") }
    var selectedOpponent by remember { mutableStateOf(opponents.find { it.name == initialOpponent }) }
    var dateError by remember { mutableStateOf(false) }
    var opponentError by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            date = sdf.format(calendar.time)
            dateError = false
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val timePickerDialog = remember {
        val initHour = if (time.length == 5) time.substring(0, 2).toIntOrNull() ?: 12 else 12
        val initMin  = if (time.length == 5) time.substring(3, 5).toIntOrNull() ?: 0  else 0
        TimePickerDialog(context, { _, h, m ->
            time = "%02d:%02d".format(h, m)
        }, initHour, initMin, true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { },
                    label = { Text(stringResource(R.string.label_date)) },
                    readOnly = true,
                    isError = dateError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = if (dateError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = time,
                    onValueChange = { },
                    label = { Text(stringResource(R.string.label_time)) },
                    placeholder = { Text(stringResource(R.string.hint_time)) },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { timePickerDialog.show() },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                if (opponents.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedOpponent?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.label_opponent)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            opponents.forEach { opp ->
                                DropdownMenuItem(
                                    text = { Text(opp.name) },
                                    onClick = {
                                        selectedOpponent = opp
                                        expanded = false
                                        opponentError = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.label_or_enter_new),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                OutlinedTextField(
                    value = opponentText,
                    onValueChange = {
                        opponentText = it
                        if (it.isNotEmpty()) selectedOpponent = null
                        opponentError = false
                    },
                    label = { Text(stringResource(R.string.hint_opponent)) },
                    isError = opponentError,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalOpponent = opponentText.trim().ifEmpty { selectedOpponent?.name ?: "" }
                if (date.isNotBlank() && finalOpponent.isNotBlank()) {
                    onConfirm(date, time, finalOpponent)
                } else {
                    if (date.isBlank()) dateError = true
                    if (finalOpponent.isBlank()) opponentError = true
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
