package de.baseball.diamond9

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


class TeamDetailActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper
    private var teamId: Long = -1

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(BackupManager(this).exportTeam(teamId).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.toast_team_exported), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_export_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(this)
        teamId = intent.getLongExtra("teamId", -1)

        setContent {
            TeamDetailScreen(
                teamId = teamId,
                db = db,
                onBack = { finish() },
                onExport = {
                    val teamName = db.getAllTeams().firstOrNull { it.id == teamId }?.name ?: "team"
                    val safeName = teamName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                    exportLauncher.launch("$safeName.json")
                },
                onShare = {
                    val teamName = db.getAllTeams().firstOrNull { it.id == teamId }?.name ?: "team"
                    val safeName = teamName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                    BackupManager.shareJson(this, "$safeName.json", BackupManager(this).exportTeam(teamId))
                }
            )
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailScreen(
    teamId: Long,
    db: DatabaseHelper,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit
) {
    var teamName by remember { mutableStateOf("") }
    var players by remember { mutableStateOf(emptyList<Player>()) }
    var showPositionsDialog by remember { mutableStateOf(false) }
    var playerToEdit by remember { mutableStateOf<Player?>(null) }
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var playerToDelete by remember { mutableStateOf<Player?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    fun refresh() {
        teamName = db.getAllTeams().firstOrNull { it.id == teamId }?.name ?: ""
        players = db.getPlayersForTeam(teamId)
    }

    LaunchedEffect(teamId) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.team_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                                text = { Text(stringResource(R.string.menu_active_positions)) },
                                onClick = {
                                    menuExpanded = false
                                    showPositionsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_team)) },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_team)) },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddPlayerDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_add_player)) },
                containerColor = colorResource(R.color.color_primary),
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colorResource(R.color.color_background))
                .padding(16.dp)
        ) {
            // Team Name Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.label_team_name_header).uppercase(),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        letterSpacing = 0.1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var tempName by remember(teamName) { mutableStateOf(teamName) }
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { if (it.length <= 50) tempName = it },
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                        )
                        Button(
                            onClick = {
                                if (tempName.trim().isNotEmpty()) {
                                    db.updateTeamName(teamId, tempName.trim())
                                    teamName = tempName.trim()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.color_primary))
                        ) {
                            Text(stringResource(R.string.btn_save))
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.label_roster).uppercase(),
                fontSize = 11.sp,
                color = Color.Gray,
                letterSpacing = 0.1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (players.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.empty_players),
                        fontSize = 15.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(players) { player ->
                        PlayerItem(
                            player = player,
                            onEdit = { playerToEdit = it },
                            onDelete = { playerToDelete = it }
                        )
                    }
                }
            }
        }
    }

    if (showPositionsDialog) {
        ActivePositionsDialog(
            teamId = teamId,
            db = db,
            onDismiss = { showPositionsDialog = false }
        )
    }

    if (showAddPlayerDialog) {
        PlayerEditDialog(
            teamId = teamId,
            db = db,
            player = null,
            onDismiss = { showAddPlayerDialog = false },
            onSave = { refresh() }
        )
    }

    playerToEdit?.let { player ->
        PlayerEditDialog(
            teamId = teamId,
            db = db,
            player = player,
            onDismiss = { playerToEdit = null },
            onSave = { refresh() }
        )
    }

    playerToDelete?.let { player ->
        AlertDialog(
            onDismissRequest = { playerToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_player_title)) },
            text = { Text(stringResource(R.string.dialog_delete_player_message, player.name)) },
            confirmButton = {
                TextButton(onClick = {
                    db.deletePlayer(player.id)
                    refresh()
                    playerToDelete = null
                }) {
                    Text(stringResource(R.string.btn_delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { playerToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun PlayerItem(
    player: Player,
    onEdit: (Player) -> Unit,
    onDelete: (Player) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(player) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Jersey Number
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, colorResource(R.color.color_primary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (player.number.isNotEmpty()) "#${player.number}" else "–",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.color_primary)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = player.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.color_text_primary),
                modifier = Modifier.weight(1f)
            )

            if (player.isPitcher) {
                Surface(
                    color = colorResource(R.color.color_primary),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.badge_pitcher),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Primary Position
            PositionBadge(
                pos = player.primaryPosition,
                textColor = colorResource(R.color.color_strike)
            )

            if (player.secondaryPosition > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                PositionBadge(
                    pos = player.secondaryPosition,
                    textColor = colorResource(R.color.color_text_secondary)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { onDelete(player) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.content_desc_delete),
                    tint = colorResource(R.color.color_strike)
                )
            }
        }
    }
}

@Composable
fun PositionBadge(pos: Int, textColor: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(1.dp, colorResource(R.color.color_divider), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (pos > 0) stringResource(BaseballPositions.shortLabelRes(pos)) else "–",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun ActivePositionsDialog(
    teamId: Long,
    db: DatabaseHelper,
    onDismiss: () -> Unit
) {
    val enabledPositions = remember { mutableStateListOf<Int>().apply { addAll(db.getEnabledPositions(teamId)) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_active_positions_title)) },
        text = {
            LazyColumn {
                items(BaseballPositions.ALL) { (pos, labelRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val isChecked = pos !in enabledPositions
                            if (isChecked) enabledPositions.add(pos) else enabledPositions.remove(pos)
                            db.setPositionEnabled(teamId, pos, isChecked)
                        }.padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = pos in enabledPositions,
                            onCheckedChange = { isChecked ->
                                if (isChecked) enabledPositions.add(pos) else enabledPositions.remove(pos)
                                db.setPositionEnabled(teamId, pos, isChecked)
                            }
                        )
                        Text(text = stringResource(labelRes), fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_done)) }
        }
    )
}

@Composable
fun PlayerEditDialog(
    teamId: Long,
    db: DatabaseHelper,
    player: Player?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var number by remember { mutableStateOf(player?.number ?: "") }
    var primaryPos by remember { mutableStateOf(player?.primaryPosition ?: 0) }
    var secondaryPos by remember { mutableStateOf(player?.secondaryPosition ?: 0) }
    var isPitcher by remember { mutableStateOf(player?.isPitcher ?: false) }
    var birthYear by remember { mutableStateOf(if (player != null && player.birthYear > 0) player.birthYear.toString() else "") }

    val enabledPositions = remember { db.getEnabledPositions(teamId).sorted() }
    val positionItems = remember {
        listOf(0 to R.string.spinner_no_position) + BaseballPositions.ALL.filter { it.first in enabledPositions }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (player == null) R.string.dialog_add_player_title else R.string.dialog_edit_player_title)) },
        text = {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                DialogTextField(label = stringResource(R.string.label_name), value = name, onValueChange = { name = it }, hint = stringResource(R.string.hint_full_name), maxLength = 50)
                DialogTextField(label = stringResource(R.string.label_jersey_number), value = number, onValueChange = { number = it }, hint = stringResource(R.string.hint_jersey_number), keyboardType = KeyboardType.Number, maxLength = 3)

                Text(stringResource(R.string.label_primary_position), fontSize = 12.sp, color = Color.Gray)
                PositionSpinner(
                    selectedPos = primaryPos,
                    items = positionItems,
                    onSelected = {
                        primaryPos = it
                        if (secondaryPos == it) secondaryPos = 0
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.label_secondary_position), fontSize = 12.sp, color = Color.Gray)
                PositionSpinner(
                    selectedPos = secondaryPos,
                    items = positionItems,
                    disabledPos = primaryPos,
                    onSelected = { secondaryPos = it }
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isPitcher = !isPitcher }.padding(vertical = 8.dp)) {
                    Checkbox(checked = isPitcher, onCheckedChange = { isPitcher = it })
                    Text(stringResource(R.string.label_is_pitcher), fontSize = 15.sp)
                }

                DialogTextField(label = stringResource(R.string.label_birth_year), value = birthYear, onValueChange = { birthYear = it }, hint = stringResource(R.string.hint_birth_year), keyboardType = KeyboardType.Number, maxLength = 4)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().isNotEmpty()) {
                    val bYear = birthYear.trim().toIntOrNull() ?: 0
                    if (player == null) {
                        db.insertPlayer(teamId, name.trim(), number.trim(), primaryPos, secondaryPos, isPitcher, bYear)
                    } else {
                        db.updatePlayer(player.copy(name = name.trim(), number = number.trim(), primaryPosition = primaryPos, secondaryPosition = secondaryPos, isPitcher = isPitcher, birthYear = bYear))
                    }
                    onSave()
                    onDismiss()
                }
            }) {
                Text(stringResource(if (player == null) R.string.btn_add else R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun DialogTextField(label: String, value: String, onValueChange: (String) -> Unit, hint: String, keyboardType: KeyboardType = KeyboardType.Text, maxLength: Int = Int.MAX_VALUE) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            placeholder = { Text(hint) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Words else KeyboardCapitalization.None
            ),
            singleLine = true
        )
    }
}

@Composable
fun PositionSpinner(
    selectedPos: Int,
    items: List<Pair<Int, Int>>,
    disabledPos: Int = -1,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.first == selectedPos }?.second?.let { stringResource(it) } ?: stringResource(R.string.spinner_no_position)

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(selectedLabel, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, color = Color.Black)
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (pos, labelRes) ->
                val label = stringResource(labelRes)
                DropdownMenuItem(
                    text = { Text(label, color = if (pos != 0 && pos == disabledPos) Color.Gray else Color.Black) },
                    onClick = {
                        if (pos == 0 || pos != disabledPos) {
                            onSelected(pos)
                            expanded = false
                        }
                    },
                    enabled = (pos == 0 || pos != disabledPos)
                )
            }
        }
    }
}
