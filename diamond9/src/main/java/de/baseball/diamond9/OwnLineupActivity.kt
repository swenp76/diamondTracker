package de.baseball.diamond9

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SwapType { SUB_IN, RETURN_STARTER, INJURY_ONLY, NONE }
enum class SubStatus { AVAILABLE, ACTIVE, DONE }

data class SlotState(
    val slot: Int,
    val currentPlayer: Player?,
    val originalPlayer: Player?,
    val swapType: SwapType
)

data class WechselEntry(
    val player: Player,
    val statusLabel: String,
    val statusColor: Color
)

class OwnLineupActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = DatabaseHelper(this)

        val gameId = intent.getLongExtra("gameId", -1)
        val gameOpponent = intent.getStringExtra("gameOpponent") ?: "Lineup"
        val gameDate = intent.getStringExtra("gameDate") ?: ""

        setContent {
            OwnLineupScreen(
                db = db,
                gameId = gameId,
                gameOpponent = gameOpponent,
                gameDate = gameDate,
                onBackClick = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnLineupScreen(
    db: DatabaseHelper,
    gameId: Long,
    gameOpponent: String,
    gameDate: String,
    onBackClick: () -> Unit
) {
    var slotStates by remember { mutableStateOf(emptyList<SlotState>()) }
    var subStatuses by remember { mutableStateOf(emptyMap<Long, SubStatus>()) }
    var substitutions by remember { mutableStateOf(emptyList<Substitution>()) }
    var lineup by remember { mutableStateOf(emptyMap<Int, Player>()) }
    var wechselEntries by remember { mutableStateOf(emptyList<WechselEntry>()) }

    var activeDialogSlot by remember { mutableStateOf<Int?>(null) }
    var swapDialogState by remember { mutableStateOf<SlotState?>(null) }
    var subSelectionState by remember { mutableStateOf<Pair<SlotState, Boolean>?>(null) } // state, isInjury
    var returnStarterDialogState by remember { mutableStateOf<SlotState?>(null) }

    val refresh = {
        val lineupData = db.getOwnLineup(gameId)
        lineup = lineupData
        substitutions = db.getSubstitutionsForGame(gameId)

        val computedSlots = (1..10).map { slot ->
            val original = lineupData[slot]
            if (original == null) {
                SlotState(slot, null, null, SwapType.NONE)
            } else {
                val slotSubs = substitutions.filter { it.slot == slot }
                val current: Player? = if (slotSubs.isEmpty()) original
                else db.getPlayerById(slotSubs.last().playerInId)
                val swapType = when {
                    slotSubs.isEmpty() -> SwapType.SUB_IN
                    slotSubs.size == 1 && current?.id != original.id -> SwapType.RETURN_STARTER
                    slotSubs.size >= 2 && current?.id == original.id -> SwapType.INJURY_ONLY
                    else -> SwapType.NONE
                }
                SlotState(slot, current, original, swapType)
            }
        }
        slotStates = computedSlots

        val computedSubStatuses = mutableMapOf<Long, SubStatus>()
        for (slot in 11..20) {
            val player = lineupData[slot] ?: continue
            val timesIn = substitutions.count { it.playerInId == player.id }
            val timesOut = substitutions.count { it.playerOutId == player.id }
            computedSubStatuses[player.id] = when {
                timesIn == 0 -> SubStatus.AVAILABLE
                timesIn == 1 && timesOut == 0 -> SubStatus.ACTIVE
                else -> SubStatus.DONE
            }
        }
        subStatuses = computedSubStatuses
    }

    val entries = remember(substitutions, slotStates) {
        val list = mutableListOf<WechselEntry>()
        val seen = mutableSetOf<Long>()
        substitutions.forEach { sub ->
            val playerOut = db.getPlayerById(sub.playerOutId) ?: return@forEach
            val playerIn = db.getPlayerById(sub.playerInId) ?: return@forEach

            if (seen.add(playerOut.id)) {
                val timesIn = substitutions.count { it.playerInId == playerOut.id }
                val isStarter = slotStates.any { it.originalPlayer?.id == playerOut.id }
                val (label, color) = when {
                    isStarter && timesIn == 0 -> "OUT_CAN_RETURN" to Color(0xFFe67e22)
                    isStarter && timesIn >= 1 -> "RETURNED" to Color(0xFF27ae60)
                    else -> "OUT" to Color(0xFFc0392b)
                }
                list.add(WechselEntry(playerOut, label, color))
            }

            if (seen.add(playerIn.id)) {
                val timesOut = substitutions.count { it.playerOutId == playerIn.id }
                val isOnField = slotStates.any { it.currentPlayer?.id == playerIn.id }
                val (label, color) = when {
                    isOnField && timesOut == 0 -> "IN_GAME" to Color(0xFF27ae60)
                    else -> "DONE" to Color(0xFFc0392b)
                }
                list.add(WechselEntry(playerIn, label, color))
            }
        }
        list
    }
    wechselEntries = entries

    LaunchedEffect(Unit) {
        refresh()
    }

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { SectionHeader(stringResource(R.string.lineup_section_starters)) }

                items(slotStates) { state ->
                    val hasSubs = substitutions.any { it.slot == state.slot }
                    StarterRow(
                        state = state,
                        hasSubs = hasSubs,
                        onRowClick = { if (!hasSubs) activeDialogSlot = state.slot },
                        onSwapClick = {
                            when (state.swapType) {
                                SwapType.SUB_IN -> swapDialogState = state
                                SwapType.RETURN_STARTER -> returnStarterDialogState = state
                                SwapType.INJURY_ONLY -> subSelectionState = state to true
                                SwapType.NONE -> {}
                            }
                        },
                        onClearClick = { db.clearOwnLineupSlot(gameId, state.slot); refresh() }
                    )
                }

                item { SectionHeader(stringResource(R.string.lineup_section_substitutes)) }

                items((11..20).toList()) { slot ->
                    val player = lineup[slot]
                    val status = player?.let { subStatuses[it.id] } ?: SubStatus.AVAILABLE
                    val isInvolved = player?.let { p -> substitutions.any { it.playerInId == p.id || it.playerOutId == p.id } } ?: false
                    SubstituteRow(
                        slot = slot,
                        player = player,
                        status = status,
                        isInvolved = isInvolved,
                        onRowClick = { if (!isInvolved) activeDialogSlot = slot },
                        onClearClick = { db.clearOwnLineupSlot(gameId, slot); refresh() }
                    )
                }

                if (wechselEntries.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.lineup_section_changes)) }
                    item { HorizontalDivider(color = Color(0xFFDDDDDD), thickness = 1.dp) }
                    items(wechselEntries) { entry ->
                        ChangeRow(entry)
                    }
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    // Player Picker Dialog
    activeDialogSlot?.let { slot ->
        val teamId = db.getGame(gameId)?.teamId ?: 0L
        val allPlayers = if (teamId > 0) db.getPlayersForTeam(teamId) else emptyList()
        val assignedIds = lineup.entries.filter { it.key != slot }.map { it.value.id }.toSet()
        val unassigned = allPlayers.filter { it.id !in assignedIds }

        if (unassigned.isEmpty()) {
            AlertDialog(
                onDismissRequest = { activeDialogSlot = null },
                title = { Text(stringResource(R.string.dialog_pick_player_title, slot)) },
                text = { Text(stringResource(R.string.dialog_no_more_players)) },
                confirmButton = { Button(onClick = { activeDialogSlot = null }) { Text("OK") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { activeDialogSlot = null },
                title = { Text(stringResource(R.string.dialog_pick_player_title, slot)) },
                text = {
                    LazyColumn {
                        items(unassigned) { player ->
                            Text(
                                text = "#${player.number}  ${player.name}  (${stringResource(BaseballPositions.shortLabelRes(player.primaryPosition))})",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        db.setOwnLineupPlayer(gameId, slot, player.id)
                                        refresh()
                                        activeDialogSlot = null
                                    }
                                    .padding(16.dp),
                                fontSize = 16.sp
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { activeDialogSlot = null }) { Text(stringResource(R.string.btn_cancel)) } }
            )
        }
    }

    // Swap Type Dialog (Normal vs Injury)
    swapDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { swapDialogState = null },
            title = { Text(stringResource(R.string.dialog_sub_title, state.slot)) },
            text = {
                Column {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { subSelectionState = state to false; swapDialogState = null }
                    ) {
                        Text(stringResource(R.string.dialog_sub_normal), fontSize = 18.sp)
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { subSelectionState = state to true; swapDialogState = null }
                    ) {
                        Text(stringResource(R.string.dialog_sub_injury), fontSize = 18.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { swapDialogState = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    // Player Selection for Substitution
    subSelectionState?.let { (state, isInjury) ->
        val currentlyOnField = slotStates.mapNotNull { it.currentPlayer?.id }.toSet()
        val availableSubs = (11..20).mapNotNull { lineup[it] }.filter { player ->
            substitutions.none { it.playerInId == player.id } && player.id !in currentlyOnField
        }

        val candidates = if (isInjury) {
            val outStarters = (1..10).mapNotNull { lineup[it] }.filter { player ->
                val timesOut = substitutions.count { it.playerOutId == player.id }
                val timesIn = substitutions.count { it.playerInId == player.id }
                timesOut == 1 && timesIn == 0 && player.id !in currentlyOnField
            }
            (availableSubs + outStarters).distinctBy { it.id }
        } else {
            availableSubs
        }

        if (candidates.isEmpty()) {
            val title = if (isInjury) R.string.dialog_no_players_title else R.string.dialog_no_subs_title
            val msg = if (isInjury) R.string.dialog_no_players_own_message else R.string.dialog_no_subs_message
            AlertDialog(
                onDismissRequest = { subSelectionState = null },
                title = { Text(stringResource(title)) },
                text = { Text(stringResource(msg)) },
                confirmButton = { Button(onClick = { subSelectionState = null }) { Text("OK") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { subSelectionState = null },
                title = { Text(stringResource(if (isInjury) R.string.dialog_injury_title else R.string.dialog_sub_title, state.slot)) },
                text = {
                    LazyColumn {
                        items(candidates) { player ->
                            Text(
                                text = "#${player.number}  ${player.name}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        db.addSubstitution(gameId, state.slot, state.currentPlayer!!.id, player.id)
                                        refresh()
                                        subSelectionState = null
                                    }
                                    .padding(16.dp),
                                fontSize = 16.sp
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { subSelectionState = null }) { Text(stringResource(R.string.btn_cancel)) } }
            )
        }
    }

    // Return Starter Dialog
    returnStarterDialogState?.let { state ->
        val original = state.originalPlayer ?: return@let
        AlertDialog(
            onDismissRequest = { returnStarterDialogState = null },
            title = { Text(stringResource(R.string.dialog_return_title, state.slot)) },
            text = { Text(stringResource(R.string.dialog_return_message, "#${original.number} ${original.name}")) },
            confirmButton = {
                Button(onClick = {
                    db.addSubstitution(gameId, state.slot, state.currentPlayer!!.id, original.id)
                    refresh()
                    returnStarterDialogState = null
                }) { Text(stringResource(R.string.btn_back_to_game)) }
            },
            dismissButton = { TextButton(onClick = { returnStarterDialogState = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF888888),
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
    )
}

@Composable
private fun SlotNumber(slot: Int, color: Color) {
    Box(
        modifier = Modifier.width(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = slot.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun StarterRow(
    state: SlotState,
    hasSubs: Boolean,
    onRowClick: () -> Unit,
    onSwapClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlotNumber(state.slot, Color(0xFF1a5fa8))

            Column(modifier = Modifier.weight(1f)) {
                if (state.currentPlayer != null) {
                    val isSubstituted = state.currentPlayer.id != state.originalPlayer?.id
                    Text(
                        text = "#${state.currentPlayer.number}  ${state.currentPlayer.name}${if (isSubstituted) "  ↩" else ""}",
                        fontSize = 16.sp,
                        color = if (isSubstituted) Color(0xFFc0392b) else Color(0xFF222222)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.lineup_slot_empty),
                        fontSize = 16.sp,
                        color = Color(0xFFBBBBBB)
                    )
                }
            }

            if (state.swapType != SwapType.NONE && state.currentPlayer != null) {
                val isInjuryOnly = state.swapType == SwapType.INJURY_ONLY
                Button(
                    onClick = onSwapClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInjuryOnly) Color(0xFFe67e22) else Color(0xFF1a5fa8)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (isInjuryOnly) "🩹" else "⇄", fontSize = if (isInjuryOnly) 14.sp else 16.sp, color = Color.White)
                }
            }

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (state.currentPlayer != null && !hasSubs) {
                    IconButton(onClick = onClearClick) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_clear), tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubstituteRow(
    slot: Int,
    player: Player?,
    status: SubStatus,
    isInvolved: Boolean,
    onRowClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlotNumber(slot, Color(0xFF2c7a2c))

            Column(modifier = Modifier.weight(1f)) {
                if (player != null) {
                    Text(
                        text = "#${player.number}  ${player.name}  (${stringResource(BaseballPositions.shortLabelRes(player.primaryPosition))})",
                        fontSize = 16.sp,
                        color = if (status == SubStatus.DONE) Color(0xFF999999) else Color(0xFF222222)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.lineup_slot_empty),
                        fontSize = 16.sp,
                        color = Color(0xFFBBBBBB)
                    )
                }
            }

            if (player != null) {
                when (status) {
                    SubStatus.AVAILABLE -> {
                        Text("✓", fontSize = 20.sp, color = Color(0xFF27ae60), modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    SubStatus.DONE -> {
                        Text("✗", fontSize = 20.sp, color = Color(0xFFc0392b), modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    SubStatus.ACTIVE -> {
                        Spacer(modifier = Modifier.width(32.dp))
                    }
                }
            }

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (player != null && !isInvolved) {
                    IconButton(onClick = onClearClick) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_desc_clear), tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(entry: WechselEntry) {
    val statusText = when (entry.statusLabel) {
        "OUT_CAN_RETURN" -> stringResource(R.string.status_out_can_return)
        "RETURNED" -> stringResource(R.string.status_returned)
        "OUT" -> stringResource(R.string.status_out)
        "IN_GAME" -> stringResource(R.string.status_in_game)
        "DONE" -> stringResource(R.string.status_done)
        else -> entry.statusLabel
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${entry.player.number}  ${entry.player.name}",
            fontSize = 15.sp,
            color = Color(0xFF222222),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = statusText,
            fontSize = 13.sp,
            color = entry.statusColor,
            textAlign = TextAlign.End
        )
    }
}
