package de.baseball.pitcher

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

enum class SwapType { SUB_IN, RETURN_STARTER, INJURY_ONLY, NONE }
enum class SubStatus { AVAILABLE, ACTIVE, DONE }

data class SlotState(
    val slot: Int,
    val currentPlayer: Player?,
    val originalPlayer: Player?,
    val swapType: SwapType
)

class OwnLineupActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var gameId: Long = -1
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_own_lineup)

        gameId = intent.getLongExtra("gameId", -1)
        supportActionBar?.title = intent.getStringExtra("gameOpponent") ?: "Lineup"
        supportActionBar?.subtitle = intent.getStringExtra("gameDate") ?: ""
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = DatabaseHelper(this)
        container = findViewById(R.id.lineupContainer)
        buildLineup()
    }

    override fun onResume() {
        super.onResume()
        buildLineup()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── State computation ──────────────────────────────────────────────────────

    private fun computeState(): Pair<List<SlotState>, Map<Long, SubStatus>> {
        val lineup = db.getOwnLineup(gameId)
        val subs = db.getSubstitutionsForGame(gameId)

        val slotStates = (1..10).map { slot ->
            val original = lineup[slot]
            if (original == null) {
                SlotState(slot, null, null, SwapType.NONE)
            } else {
                val slotSubs = subs.filter { it.slot == slot }
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

        val subStatuses = mutableMapOf<Long, SubStatus>()
        for (slot in 11..20) {
            val player = lineup[slot] ?: continue
            val timesIn  = subs.count { it.playerInId  == player.id }
            val timesOut = subs.count { it.playerOutId == player.id }
            subStatuses[player.id] = when {
                timesIn == 0               -> SubStatus.AVAILABLE
                timesIn == 1 && timesOut == 0 -> SubStatus.ACTIVE
                else                       -> SubStatus.DONE
            }
        }

        return Pair(slotStates, subStatuses)
    }

    // ── Build UI ───────────────────────────────────────────────────────────────

    private fun buildLineup() {
        container.removeAllViews()
        val lineup = db.getOwnLineup(gameId)
        val (slotStates, subStatuses) = computeState()

        addSectionHeader(getString(R.string.lineup_section_starters))
        for (state in slotStates) {
            addStarterRow(state, lineup)
        }

        addSectionHeader(getString(R.string.lineup_section_substitutes))
        for (slot in 11..20) {
            val player = lineup[slot]
            addSubstituteRow(slot, player, if (player != null) subStatuses[player.id] ?: SubStatus.AVAILABLE else null)
        }

        addWechselSection(lineup, slotStates, subStatuses)
    }
    private fun addSectionHeader(title: String) {
        val tv = TextView(this).apply {
            text = title
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#888888"))
            setPadding(8, 24, 8, 8)
        }
        container.addView(tv)
    }

    private fun addStarterRow(state: SlotState, lineup: Map<Int, Player>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 12, 8, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
        }

        // Slot number
        row.addView(slotNumberView(state.slot, "#1a5fa8"))

        // Player name
        val tvPlayer = TextView(this).apply {
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (state.currentPlayer != null) {
                val isSubstituted = state.currentPlayer.id != state.originalPlayer?.id
                text = "#${state.currentPlayer.number}  ${state.currentPlayer.name}" +
                        if (isSubstituted) "  ↩" else ""
                setTextColor(if (isSubstituted) Color.parseColor("#c0392b") else Color.parseColor("#222222"))
            } else {
                text = getString(R.string.lineup_slot_empty)
                setTextColor(Color.parseColor("#bbbbbb"))
            }
        }
        row.addView(tvPlayer)

        // Swap button
        if (state.swapType != SwapType.NONE && state.currentPlayer != null) {
            val isInjuryOnly = state.swapType == SwapType.INJURY_ONLY
            val btnSwap = Button(this).apply {
                text = if (isInjuryOnly) "🩹" else "⇄"
                textSize = if (isInjuryOnly) 16f else 18f
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor(if (isInjuryOnly) "#e67e22" else "#1a5fa8")
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 8 }
                setPadding(24, 4, 24, 4)
                setOnClickListener { onSwapPressed(state) }
            }
            row.addView(btnSwap)
        }

        // Clear button (X) – nur wenn kein Wechsel stattgefunden hat
        val hasSubs = db.getSubstitutionsForGame(gameId).any { it.slot == state.slot }
        val btnClear = ImageButton(this).apply {
            setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
                this@OwnLineupActivity, android.R.drawable.ic_menu_close_clear_cancel))
            background = null
            visibility = if (state.currentPlayer != null && !hasSubs) View.VISIBLE else View.INVISIBLE
            setOnClickListener { db.clearOwnLineupSlot(gameId, state.slot); buildLineup() }
        }
        row.addView(btnClear)

        row.setOnClickListener {
            if (!hasSubs) showPickPlayerDialog(state.slot)
        }
        container.addView(row)
    }

    private fun addSubstituteRow(slot: Int, player: Player?, status: SubStatus?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 12, 8, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
        }

        row.addView(slotNumberView(slot, "#2c7a2c"))

        val tvPlayer = TextView(this).apply {
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (player != null) {
                text = "#${player.number}  ${player.name}  (${BaseballPositions.shortLabel(player.primaryPosition)})"
                setTextColor(if (status == SubStatus.DONE) Color.parseColor("#999999") else Color.parseColor("#222222"))
            } else {
                text = getString(R.string.lineup_slot_empty)
                setTextColor(Color.parseColor("#bbbbbb"))
            }
        }
        row.addView(tvPlayer)

        // Status icon
        if (player != null) {
            when (status) {
                SubStatus.AVAILABLE -> {
                    val tv = TextView(this).apply {
                        text = "✓"
                        textSize = 20f
                        setTextColor(Color.parseColor("#27ae60"))
                        setPadding(8, 0, 8, 0)
                    }
                    row.addView(tv)
                }
                SubStatus.DONE -> {
                    val tv = TextView(this).apply {
                        text = "✗"
                        textSize = 20f
                        setTextColor(Color.parseColor("#c0392b"))
                        setPadding(8, 0, 8, 0)
                    }
                    row.addView(tv)
                }
                SubStatus.ACTIVE -> {
                    // Currently playing – shown in starter section, no icon needed
                }
                null -> {}
            }

            // Clear button (only if no substitution involved this player)
            val subs = db.getSubstitutionsForGame(gameId)
            val isInvolved = subs.any { it.playerInId == player.id || it.playerOutId == player.id }
            val btnClear = ImageButton(this).apply {
                setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
                    this@OwnLineupActivity, android.R.drawable.ic_menu_close_clear_cancel))
                background = null
                visibility = if (!isInvolved) View.VISIBLE else View.INVISIBLE
                setOnClickListener { db.clearOwnLineupSlot(gameId, slot); buildLineup() }
            }
            row.addView(btnClear)
        }

        if (player == null || (db.getSubstitutionsForGame(gameId).none { it.playerInId == player.id || it.playerOutId == player.id })) {
            row.setOnClickListener { showPickPlayerDialog(slot) }
        }

        container.addView(row)
    }

    private fun addWechselSection(lineup: Map<Int, Player>, slotStates: List<SlotState>, subStatuses: Map<Long, SubStatus>) {
        val subs = db.getSubstitutionsForGame(gameId)
        if (subs.isEmpty()) return

        addSectionHeader(getString(R.string.lineup_section_changes))

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { bottomMargin = 8 }
            setBackgroundColor(Color.parseColor("#dddddd"))
        }
        container.addView(divider)

        // Collect all involved players with their final status
        data class WechselEntry(val player: Player, val statusLabel: String, val statusColor: String)
        val entries = mutableListOf<WechselEntry>()
        val seen = mutableSetOf<Long>()

        // Go through substitutions in order to build a meaningful log
        subs.forEach { sub ->
            val playerOut = db.getPlayerById(sub.playerOutId) ?: return@forEach
            val playerIn  = db.getPlayerById(sub.playerInId)  ?: return@forEach

            // Player who came OUT
            if (seen.add(playerOut.id)) {
                val timesIn  = subs.count { it.playerInId  == playerOut.id }
                val timesOut = subs.count { it.playerOutId == playerOut.id }
                val isStarter = (1..10).any { lineup[it]?.id == playerOut.id }
                val label = when {
                    isStarter && timesIn == 0 -> getString(R.string.status_out_can_return)
                    isStarter && timesIn >= 1 -> getString(R.string.status_returned)
                    !isStarter && timesOut >= 1 -> getString(R.string.status_done)
                    else -> getString(R.string.status_out)
                }
                val color = when {
                    isStarter && timesIn == 0 -> "#e67e22"
                    isStarter && timesIn >= 1 -> "#27ae60"
                    else -> "#c0392b"
                }
                entries.add(WechselEntry(playerOut, label, color))
            }

            // Player who came IN (substitute or returning starter)
            if (seen.add(playerIn.id)) {
                val timesIn  = subs.count { it.playerInId  == playerIn.id }
                val timesOut = subs.count { it.playerOutId == playerIn.id }
                val isOnField = slotStates.any { it.currentPlayer?.id == playerIn.id }
                val label = when {
                    isOnField && timesOut == 0 -> getString(R.string.status_in_game)
                    !isOnField && timesOut >= 1 -> getString(R.string.status_done)
                    else -> getString(R.string.status_in_game)
                }
                val color = when {
                    isOnField -> "#27ae60"
                    else -> "#c0392b"
                }
                entries.add(WechselEntry(playerIn, label, color))
            }
        }

        entries.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 10, 8, 10)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 2 }
            }

            val tvName = TextView(this).apply {
                text = "#${entry.player.number}  ${entry.player.name}"
                textSize = 15f
                setTextColor(Color.parseColor("#222222"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvStatus = TextView(this).apply {
                text = entry.statusLabel
                textSize = 13f
                setTextColor(Color.parseColor(entry.statusColor))
                gravity = Gravity.END
            }

            row.addView(tvName)
            row.addView(tvStatus)
            container.addView(row)
        }
    }

    private fun slotNumberView(slot: Int, colorHex: String) = TextView(this).apply {
        text = "$slot"
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor(colorHex))
        width = 72
        gravity = Gravity.CENTER
    }

    // ── Swap logic ─────────────────────────────────────────────────────────────

    private fun onSwapPressed(state: SlotState) {
        when (state.swapType) {
            SwapType.SUB_IN -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_sub_title, state.slot))
                    .setItems(arrayOf(getString(R.string.dialog_sub_normal), getString(R.string.dialog_sub_injury))) { _, which ->
                        if (which == 0) showNormalSubDialog(state)
                        else showInjurySubDialog(state)
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null).show()
            }
            SwapType.RETURN_STARTER -> {
                val original = state.originalPlayer ?: return
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_return_title, state.slot))
                    .setMessage(getString(R.string.dialog_return_message, "#${original.number} ${original.name}"))
                    .setPositiveButton("Zurück") { _, _ ->
                        db.addSubstitution(gameId, state.slot, state.currentPlayer!!.id, original.id)
                        buildLineup()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null).show()
            }
            SwapType.INJURY_ONLY -> showInjurySubDialog(state)
            SwapType.NONE -> {}
        }
    }

    private fun availableSubstitutes(): List<Player> {
        val lineup = db.getOwnLineup(gameId)
        val subs = db.getSubstitutionsForGame(gameId)
        val currentlyOnField = computeState().first.mapNotNull { it.currentPlayer?.id }.toSet()
        return (11..20).mapNotNull { lineup[it] }.filter { player ->
            subs.none { it.playerInId == player.id } && player.id !in currentlyOnField
        }
    }

    // Starter die gerade OUT sind (einmal ausgewechselt, noch nicht zurückgekehrt)
    private fun outStarters(): List<Player> {
        val lineup = db.getOwnLineup(gameId)
        val subs = db.getSubstitutionsForGame(gameId)
        val currentlyOnField = computeState().first.mapNotNull { it.currentPlayer?.id }.toSet()
        return (1..10).mapNotNull { lineup[it] }.filter { player ->
            val timesOut = subs.count { it.playerOutId == player.id }
            val timesIn  = subs.count { it.playerInId  == player.id }
            timesOut == 1 && timesIn == 0 && player.id !in currentlyOnField
        }
    }

    private fun showNormalSubDialog(state: SlotState) {
        val candidates = availableSubstitutes()
        if (candidates.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_no_subs_title))
                .setMessage(getString(R.string.dialog_no_subs_message))
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = candidates.map { "#${it.number}  ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_sub_title, state.slot))
            .setItems(labels) { _, which ->
                db.addSubstitution(gameId, state.slot, state.currentPlayer!!.id, candidates[which].id)
                buildLineup()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private fun showInjurySubDialog(state: SlotState) {
        val candidates = (availableSubstitutes() + outStarters()).distinctBy { it.id }
        if (candidates.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_no_players_title))
                .setMessage(getString(R.string.dialog_no_players_own_message))
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = candidates.map { "#${it.number}  ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_injury_title, state.slot))
            .setItems(labels) { _, which ->
                db.addSubstitution(gameId, state.slot, state.currentPlayer!!.id, candidates[which].id)
                buildLineup()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    // ── Player picker (lineup editing) ─────────────────────────────────────────

    private fun showPickPlayerDialog(slot: Int) {
        val teamId = db.getGame(gameId)?.teamId ?: 0L
        val allPlayers = if (teamId > 0) db.getPlayersForTeam(teamId) else emptyList()
        val currentLineup = db.getOwnLineup(gameId)
        val assignedIds = currentLineup.entries.filter { it.key != slot }.map { it.value.id }.toSet()
        val unassigned = allPlayers.filter { it.id !in assignedIds }

        if (unassigned.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_pick_player_title, slot))
                .setMessage(getString(R.string.dialog_no_more_players))
                .setPositiveButton("OK", null).show()
            return
        }

        val labels = unassigned.map { "#${it.number}  ${it.name}  (${BaseballPositions.shortLabel(it.primaryPosition)})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_pick_player_title, slot))
            .setItems(labels) { _, which ->
                db.setOwnLineupPlayer(gameId, slot, unassigned[which].id)
                buildLineup()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }
}
