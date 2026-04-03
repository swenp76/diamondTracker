package de.baseball.diamond9

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.OutlinedTextField
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

enum class OppSubStatus { AVAILABLE, ACTIVE, DONE }

data class OppSlotState(
    val slot: Int,
    val currentJersey: String,
    val originalJersey: String,
    val swapType: SwapType
)

data class OppWechselEntry(
    val jersey: String,
    val label: String,
    val color: Color
)

class OpponentLineupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gameId = intent.getLongExtra("gameId", -1)
        val opponentName = intent.getStringExtra("opponentName") ?: "Gegner"
        val db = DatabaseHelper(this)

        setContent {
            OpponentLineupScreen(
                gameId = gameId,
                opponentName = opponentName,
                db = db,
                onBackClick = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentLineupScreen(
    gameId: Long,
    opponentName: String,
    db: DatabaseHelper,
    onBackClick: () -> Unit
) {
    var slotStates by remember { mutableStateOf(emptyList<OppSlotState>()) }
    var subStatuses by remember { mutableStateOf(emptyMap<String, OppSubStatus>()) }
    var benchPlayers by remember { mutableStateOf(emptyList<BenchPlayer>()) }

    var jerseyInputDialogState by remember { mutableStateOf<Triple<String, String, (String) -> Unit>?>(null) }
    var swapTypeDialogState by remember { mutableStateOf<OppSlotState?>(null) }
    var subSelectionDialogState by remember { mutableStateOf<Pair<OppSlotState, Boolean>?>(null) } // state, isInjury
    var returnStarterDialogState by remember { mutableStateOf<OppSlotState?>(null) }
    var noPlayersDialogState by remember { mutableStateOf(false) }

    val refresh = {
        val (computedSlots, computedSubStatuses) = computeState(db, gameId)
        slotStates = computedSlots
        subStatuses = computedSubStatuses
        benchPlayers = db.getBenchPlayers(gameId)
    }

    val wechselEntries = remember(slotStates, subStatuses) {
        val subs = db.getOpponentSubstitutionsForGame(gameId)
        val entries = mutableListOf<OppWechselEntry>()
        val seen = mutableSetOf<String>()

        subs.forEach { sub ->
            if (seen.add(sub.jerseyOut)) {
                val timesIn = subs.count { it.jerseyIn == sub.jerseyOut }
                val isStarter = slotStates.any { it.originalJersey == sub.jerseyOut }
                val (label, color) = when {
                    isStarter && timesIn == 0 -> "Draußen – kann zurückkehren" to Color(0xFFe67e22)
                    isStarter && timesIn >= 1 -> "Zurückgekehrt" to Color(0xFF27ae60)
                    else -> "Draußen" to Color(0xFFc0392b)
                }
                entries.add(OppWechselEntry(sub.jerseyOut, label, color))
            }
            if (seen.add(sub.jerseyIn)) {
                val isOnField = slotStates.any { it.currentJersey == sub.jerseyIn }
                val (label, color) = if (isOnField) "Im Spiel" to Color(0xFF27ae60)
                else "Fertig (nicht mehr verfügbar)" to Color(0xFFc0392b)
                entries.add(OppWechselEntry(sub.jerseyIn, label, color))
            }
        }
        entries
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(opponentName, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.oppo_lineup_subtitle), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = Color(0xFFF5F5F5)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { SectionHeader(stringResource(R.string.oppo_section_batting_order)) }

                items(slotStates) { state ->
                    val subs = db.getOpponentSubstitutionsForGame(gameId)
                    val hasSubs = subs.any { it.slot == state.slot }
                    OpponentStarterRow(
                        state = state,
                        hasSubs = hasSubs,
                        onRowClick = {
                            if (!hasSubs) {
                                jerseyInputDialogState = Triple("Slot ${state.slot}", state.currentJersey) { jersey ->
                                    if (jersey.isEmpty()) db.deleteLineupEntry(gameId, state.slot)
                                    else db.upsertLineupEntry(gameId, state.slot, jersey)
                                    refresh()
                                }
                            }
                        },
                        onSwapClick = {
                            when (state.swapType) {
                                SwapType.SUB_IN -> swapTypeDialogState = state
                                SwapType.RETURN_STARTER -> returnStarterDialogState = state
                                SwapType.INJURY_ONLY -> subSelectionDialogState = state to true
                                SwapType.NONE -> {}
                            }
                        },
                        onClearClick = {
                            db.deleteLineupEntry(gameId, state.slot)
                            refresh()
                        }
                    )
                }

                item { SectionHeader(stringResource(R.string.oppo_section_bench)) }

                items((1..10).toList()) { bankSlot ->
                    val bp = benchPlayers.getOrNull(bankSlot - 1)
                    val status = bp?.let { subStatuses[it.jerseyNumber] }
                    val subs = db.getOpponentSubstitutionsForGame(gameId)
                    val isInvolved = bp?.let { p -> subs.any { it.jerseyIn == p.jerseyNumber || it.jerseyOut == p.jerseyNumber } } ?: false

                    OpponentSubstituteRow(
                        bankSlot = bankSlot,
                        bp = bp,
                        status = status,
                        isInvolved = isInvolved,
                        onRowClick = {
                            if (!isInvolved) {
                                jerseyInputDialogState = Triple("Bank – Slot ${bankSlot + 10}", bp?.jerseyNumber ?: "") { jersey ->
                                    if (jersey.isEmpty()) {
                                        bp?.let { db.deleteBenchPlayer(it.id) }
                                    } else {
                                        bp?.let { db.deleteBenchPlayer(it.id) }
                                        db.insertBenchPlayer(gameId, jersey)
                                    }
                                    refresh()
                                }
                            }
                        },
                        onClearClick = {
                            bp?.let { db.deleteBenchPlayer(it.id) }
                            refresh()
                        }
                    )
                }

                if (wechselEntries.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.lineup_section_changes)) }
                    item { HorizontalDivider(color = Color(0xFFDDDDDD), thickness = 1.dp) }
                    items(wechselEntries) { entry ->
                        OpponentChangeRow(entry)
                    }
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────

    jerseyInputDialogState?.let { (title, initialValue, onConfirm) ->
        var text by remember { mutableStateOf(initialValue) }
        AlertDialog(
            onDismissRequest = { jerseyInputDialogState = null },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.dialog_jersey_title)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = { onConfirm(text.trim()); jerseyInputDialogState = null }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { jerseyInputDialogState = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    swapTypeDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { swapTypeDialogState = null },
            title = { Text(stringResource(R.string.dialog_sub_title, state.slot)) },
            text = {
                Column {
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = { subSelectionDialogState = state to false; swapTypeDialogState = null }) {
                        Text(stringResource(R.string.dialog_sub_normal), fontSize = 18.sp)
                    }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = { subSelectionDialogState = state to true; swapTypeDialogState = null }) {
                        Text(stringResource(R.string.dialog_sub_injury), fontSize = 18.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { swapTypeDialogState = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    subSelectionDialogState?.let { (state, isInjury) ->
        val subs = db.getOpponentSubstitutionsForGame(gameId)
        val onField = slotStates.map { it.currentJersey }.toSet()
        val bench = db.getBenchPlayers(gameId)
        val availableBench = bench.filter { bp -> subs.none { it.jerseyIn == bp.jerseyNumber } && bp.jerseyNumber !in onField }

        val candidates = if (isInjury) {
            val outStarters = db.getLineup(gameId).map { it.jerseyNumber }.filter { jersey ->
                subs.count { it.jerseyOut == jersey } == 1 && subs.none { it.jerseyIn == jersey } && jersey !in onField
            }
            (availableBench.map { it.jerseyNumber } + outStarters).distinct()
        } else {
            availableBench.map { it.jerseyNumber }
        }

        if (candidates.isEmpty() && !isInjury) {
            // Normal sub but no bench players: show jersey input
            jerseyInputDialogState = Triple("Einwechslung – Slot ${state.slot}", "") { jersey ->
                if (jersey.isNotEmpty()) {
                    db.insertBenchPlayer(gameId, jersey)
                    db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, jersey)
                    refresh()
                }
            }
            subSelectionDialogState = null
        } else if (candidates.isEmpty() && isInjury) {
            noPlayersDialogState = true
            subSelectionDialogState = null
        } else {
            AlertDialog(
                onDismissRequest = { subSelectionDialogState = null },
                title = { Text(stringResource(if (isInjury) R.string.dialog_injury_title else R.string.dialog_sub_title, state.slot)) },
                text = {
                    LazyColumn {
                        items(candidates) { jersey ->
                            Text(
                                text = "#$jersey",
                                modifier = Modifier.fillMaxWidth().clickable {
                                    db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, jersey)
                                    refresh()
                                    subSelectionDialogState = null
                                }.padding(16.dp),
                                fontSize = 16.sp
                            )
                        }
                        if (!isInjury) {
                            item {
                                Text(
                                    text = stringResource(R.string.dialog_other_number),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        jerseyInputDialogState = Triple("Trikotnummer", "") { jersey ->
                                            if (jersey.isNotEmpty()) {
                                                db.insertBenchPlayer(gameId, jersey)
                                                db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, jersey)
                                                refresh()
                                            }
                                        }
                                        subSelectionDialogState = null
                                    }.padding(16.dp),
                                    fontSize = 16.sp,
                                    color = Color(0xFF1a5fa8)
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { subSelectionDialogState = null }) { Text(stringResource(R.string.btn_cancel)) } }
            )
        }
    }

    returnStarterDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { returnStarterDialogState = null },
            title = { Text(stringResource(R.string.dialog_return_title, state.slot)) },
            text = { Text(stringResource(R.string.dialog_return_message, "#${state.originalJersey}")) },
            confirmButton = {
                Button(onClick = {
                    db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, state.originalJersey)
                    refresh()
                    returnStarterDialogState = null
                }) { Text("Zurück") }
            },
            dismissButton = { TextButton(onClick = { returnStarterDialogState = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (noPlayersDialogState) {
        AlertDialog(
            onDismissRequest = { noPlayersDialogState = false },
            title = { Text(stringResource(R.string.dialog_no_players_title)) },
            text = { Text(stringResource(R.string.dialog_no_players_oppo_message)) },
            confirmButton = { Button(onClick = { noPlayersDialogState = false }) { Text("OK") } }
        )
    }
}

private fun computeState(db: DatabaseHelper, gameId: Long): Pair<List<OppSlotState>, Map<String, OppSubStatus>> {
    val lineup = db.getLineup(gameId)
    val subs = db.getOpponentSubstitutionsForGame(gameId)

    val slotStates = (1..9).map { slot ->
        val originalJersey = lineup.firstOrNull { it.battingOrder == slot }?.jerseyNumber ?: ""
        val slotSubs = subs.filter { it.slot == slot }
        val currentJersey = slotSubs.lastOrNull()?.jerseyIn ?: originalJersey
        val swapType = when {
            originalJersey.isEmpty() -> SwapType.NONE
            slotSubs.isEmpty() -> SwapType.SUB_IN
            slotSubs.size == 1 && currentJersey != originalJersey -> SwapType.RETURN_STARTER
            slotSubs.size >= 2 && currentJersey == originalJersey -> SwapType.INJURY_ONLY
            else -> SwapType.NONE
        }
        OppSlotState(slot, currentJersey, originalJersey, swapType)
    }

    val bench = db.getBenchPlayers(gameId)
    val subStatuses = mutableMapOf<String, OppSubStatus>()
    bench.forEach { bp ->
        val timesIn = subs.count { it.jerseyIn == bp.jerseyNumber }
        val timesOut = subs.count { it.jerseyOut == bp.jerseyNumber }
        subStatuses[bp.jerseyNumber] = when {
            timesIn == 0 -> OppSubStatus.AVAILABLE
            timesIn == 1 && timesOut == 0 -> OppSubStatus.ACTIVE
            else -> OppSubStatus.DONE
        }
    }
    return Pair(slotStates, subStatuses)
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
    Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.Center) {
        Text(text = slot.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun OpponentStarterRow(
    state: OppSlotState,
    hasSubs: Boolean,
    onRowClick: () -> Unit,
    onSwapClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRowClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlotNumber(state.slot, Color(0xFF1a5fa8))

            val isSubstituted = state.currentJersey != state.originalJersey && state.originalJersey.isNotEmpty()
            Text(
                text = if (state.currentJersey.isNotEmpty()) "#${state.currentJersey}${if (isSubstituted) "  ↩" else ""}"
                else stringResource(R.string.lineup_slot_empty),
                fontSize = 16.sp,
                color = when {
                    state.currentJersey.isEmpty() -> Color(0xFFBBBBBB)
                    isSubstituted -> Color(0xFFc0392b)
                    else -> Color(0xFF222222)
                },
                modifier = Modifier.weight(1f)
            )

            if (state.swapType != SwapType.NONE && state.currentJersey.isNotEmpty()) {
                val isInjuryOnly = state.swapType == SwapType.INJURY_ONLY
                Button(
                    onClick = onSwapClick,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isInjuryOnly) Color(0xFFe67e22) else Color(0xFF1a5fa8)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (isInjuryOnly) "🩹" else "⇄", fontSize = if (isInjuryOnly) 14.sp else 16.sp, color = Color.White)
                }
            }

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (state.currentJersey.isNotEmpty() && !hasSubs) {
                    IconButton(onClick = onClearClick) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun OpponentSubstituteRow(
    bankSlot: Int,
    bp: BenchPlayer?,
    status: OppSubStatus?,
    isInvolved: Boolean,
    onRowClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRowClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlotNumber(bankSlot + 10, Color(0xFF2c7a2c))

            Text(
                text = if (bp != null) "#${bp.jerseyNumber}" else stringResource(R.string.lineup_slot_empty),
                fontSize = 16.sp,
                color = when {
                    bp == null -> Color(0xFFBBBBBB)
                    status == OppSubStatus.DONE -> Color(0xFF999999)
                    else -> Color(0xFF222222)
                },
                modifier = Modifier.weight(1f)
            )

            if (bp != null) {
                when (status) {
                    OppSubStatus.AVAILABLE -> Text("✓", fontSize = 20.sp, color = Color(0xFF27ae60), modifier = Modifier.padding(horizontal = 8.dp))
                    OppSubStatus.DONE -> Text("✗", fontSize = 20.sp, color = Color(0xFFc0392b), modifier = Modifier.padding(horizontal = 8.dp))
                    else -> Spacer(modifier = Modifier.width(32.dp))
                }
            }

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (bp != null && !isInvolved) {
                    IconButton(onClick = onClearClick) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun OpponentChangeRow(entry: OppWechselEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${entry.jersey}",
            fontSize = 15.sp,
            color = Color(0xFF222222),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = entry.label,
            fontSize = 13.sp,
            color = entry.color,
            textAlign = TextAlign.End
        )
    }
}
