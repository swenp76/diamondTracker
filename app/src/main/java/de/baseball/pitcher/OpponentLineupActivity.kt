package de.baseball.pitcher

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

enum class OppSubStatus { AVAILABLE, ACTIVE, DONE }

data class OppSlotState(
    val slot: Int,
    val currentJersey: String,
    val originalJersey: String,
    val swapType: SwapType
)

class OpponentLineupActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var gameId: Long = -1
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_own_lineup)

        gameId = intent.getLongExtra("gameId", -1)
        val opponentName = intent.getStringExtra("opponentName") ?: "Gegner"
        supportActionBar?.title = opponentName
        supportActionBar?.subtitle = getString(R.string.oppo_lineup_subtitle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = DatabaseHelper(this)
        container = findViewById(R.id.lineupContainer)
        buildView()
    }

    override fun onResume() { super.onResume(); buildView() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── State ─────────────────────────────────────────────────────────────────

    private fun computeState(): Pair<List<OppSlotState>, Map<String, OppSubStatus>> {
        val lineup = db.getLineup(gameId)
        val subs   = db.getOpponentSubstitutionsForGame(gameId)

        val slotStates = (1..9).map { slot ->
            val originalJersey = lineup.firstOrNull { it.battingOrder == slot }?.jerseyNumber ?: ""
            val slotSubs = subs.filter { it.slot == slot }
            val currentJersey = slotSubs.lastOrNull()?.jerseyIn ?: originalJersey
            val swapType = when {
                originalJersey.isEmpty()                               -> SwapType.NONE
                slotSubs.isEmpty()                                     -> SwapType.SUB_IN
                slotSubs.size == 1 && currentJersey != originalJersey -> SwapType.RETURN_STARTER
                slotSubs.size >= 2 && currentJersey == originalJersey -> SwapType.INJURY_ONLY
                else                                                   -> SwapType.NONE
            }
            OppSlotState(slot, currentJersey, originalJersey, swapType)
        }

        // Bank players mapped to virtual slots 11–20 by insertion order
        val bench = db.getBenchPlayers(gameId)
        val subStatuses = mutableMapOf<String, OppSubStatus>()
        bench.forEach { bp ->
            val timesIn  = subs.count { it.jerseyIn  == bp.jerseyNumber }
            val timesOut = subs.count { it.jerseyOut == bp.jerseyNumber }
            subStatuses[bp.jerseyNumber] = when {
                timesIn == 0                  -> OppSubStatus.AVAILABLE
                timesIn == 1 && timesOut == 0 -> OppSubStatus.ACTIVE
                else                          -> OppSubStatus.DONE
            }
        }
        return Pair(slotStates, subStatuses)
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private fun buildView() {
        container.removeAllViews()
        val (slotStates, subStatuses) = computeState()
        val bench = db.getBenchPlayers(gameId)

        addSectionHeader(getString(R.string.oppo_section_batting_order))
        slotStates.forEach { addSlotRow(it) }

        addSectionHeader(getString(R.string.oppo_section_bench))
        for (bankSlot in 1..10) {
            val bp = bench.getOrNull(bankSlot - 1)
            val status = bp?.let { subStatuses[it.jerseyNumber] }
            addBankRow(bankSlot, bp, status)
        }

        addWechselSection(slotStates, subStatuses)
    }

    private fun addSectionHeader(title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#888888"))
            setPadding(8, 24, 8, 8)
        })
    }

    // ── Starter row – mirrors OwnLineupActivity.addStarterRow exactly ─────────

    private fun addSlotRow(state: OppSlotState) {
        val subs = db.getOpponentSubstitutionsForGame(gameId)
        val hasSubs = subs.any { it.slot == state.slot }

        val row = rowLayout()

        row.addView(slotNumberView(state.slot, "#1a5fa8"))

        row.addView(TextView(this).apply {
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val isSubstituted = state.currentJersey != state.originalJersey && state.originalJersey.isNotEmpty()
            text = if (state.currentJersey.isNotEmpty())
                "#${state.currentJersey}${if (isSubstituted) "  ↩" else ""}"
            else getString(R.string.lineup_slot_empty)
            setTextColor(when {
                state.currentJersey.isEmpty() -> Color.parseColor("#bbbbbb")
                isSubstituted                 -> Color.parseColor("#c0392b")
                else                          -> Color.parseColor("#222222")
            })
        })

        if (state.swapType != SwapType.NONE) {
            row.addView(swapButton(state.swapType) { onSwapPressed(state) })
        }

        val btnClear = clearButton {
            db.deleteLineupEntry(gameId, state.slot)
            buildView()
        }
        btnClear.visibility = if (state.currentJersey.isNotEmpty() && !hasSubs) View.VISIBLE else View.INVISIBLE
        row.addView(btnClear)

        row.setOnClickListener {
            if (!hasSubs) showJerseyInput("Slot ${state.slot}", state.currentJersey) { jersey ->
                if (jersey.isEmpty()) db.deleteLineupEntry(gameId, state.slot)
                else db.upsertLineupEntry(gameId, state.slot, jersey)
                buildView()
            }
        }
        container.addView(row)
    }

    // ── Bank row – mirrors OwnLineupActivity.addSubstituteRow exactly ─────────

    private fun addBankRow(bankSlot: Int, bp: BenchPlayer?, status: OppSubStatus?) {
        val row = rowLayout()
        row.addView(slotNumberView(bankSlot + 10, "#2c7a2c"))

        row.addView(TextView(this).apply {
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = if (bp != null) "#${bp.jerseyNumber}" else getString(R.string.lineup_slot_empty)
            setTextColor(when {
                bp == null                   -> Color.parseColor("#bbbbbb")
                status == OppSubStatus.DONE  -> Color.parseColor("#999999")
                else                         -> Color.parseColor("#222222")
            })
        })

        if (bp != null) {
            when (status) {
                OppSubStatus.AVAILABLE -> row.addView(statusIcon("✓", "#27ae60"))
                OppSubStatus.DONE      -> row.addView(statusIcon("✗", "#c0392b"))
                else                   -> {}
            }

            val subs = db.getOpponentSubstitutionsForGame(gameId)
            val isInvolved = subs.any { it.jerseyIn == bp.jerseyNumber || it.jerseyOut == bp.jerseyNumber }
            val btnClear = clearButton {
                db.deleteBenchPlayer(bp.id)
                buildView()
            }
            btnClear.visibility = if (!isInvolved) View.VISIBLE else View.INVISIBLE
            row.addView(btnClear)

            row.setOnClickListener {
                if (!isInvolved) showJerseyInput("Bank – Slot ${bankSlot + 10}", bp.jerseyNumber) { jersey ->
                    if (jersey.isEmpty()) db.deleteBenchPlayer(bp.id)
                    else {
                        db.deleteBenchPlayer(bp.id)
                        db.insertBenchPlayer(gameId, jersey)
                    }
                    buildView()
                }
            }
        } else {
            // Empty bank slot – tap to add
            val btnClear = clearButton {}
            btnClear.visibility = View.INVISIBLE
            row.addView(btnClear)

            row.setOnClickListener {
                showJerseyInput("Bank – Slot ${bankSlot + 10}", "") { jersey ->
                    if (jersey.isNotEmpty()) { db.insertBenchPlayer(gameId, jersey); buildView() }
                }
            }
        }

        container.addView(row)
    }

    // ── Wechsel-Übersicht ─────────────────────────────────────────────────────

    private fun addWechselSection(slotStates: List<OppSlotState>, subStatuses: Map<String, OppSubStatus>) {
        val subs = db.getOpponentSubstitutionsForGame(gameId)
        if (subs.isEmpty()) return

        addSectionHeader(getString(R.string.lineup_section_changes))
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { bottomMargin = 8 }
            setBackgroundColor(Color.parseColor("#dddddd"))
        })

        data class Entry(val jersey: String, val label: String, val color: String)
        val entries = mutableListOf<Entry>()
        val seen = mutableSetOf<String>()

        subs.forEach { sub ->
            if (seen.add(sub.jerseyOut)) {
                val timesIn  = subs.count { it.jerseyIn  == sub.jerseyOut }
                val isStarter = slotStates.any { it.originalJersey == sub.jerseyOut }
                val label = when {
                    isStarter && timesIn == 0 -> getString(R.string.status_out_can_return)
                    isStarter && timesIn >= 1 -> getString(R.string.status_returned)
                    else                      -> getString(R.string.status_done)
                }
                val color = when {
                    isStarter && timesIn == 0 -> "#e67e22"
                    isStarter && timesIn >= 1 -> "#27ae60"
                    else                      -> "#c0392b"
                }
                entries.add(Entry(sub.jerseyOut, label, color))
            }
            if (seen.add(sub.jerseyIn)) {
                val isOnField = slotStates.any { it.currentJersey == sub.jerseyIn }
                entries.add(Entry(sub.jerseyIn,
                    if (isOnField) getString(R.string.status_in_game) else getString(R.string.status_done),
                    if (isOnField) "#27ae60" else "#c0392b"))
            }
        }

        entries.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 10, 8, 10)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 2 }
            }
            row.addView(TextView(this).apply {
                text = "#${entry.jersey}"
                textSize = 15f
                setTextColor(Color.parseColor("#222222"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = entry.label
                textSize = 13f
                setTextColor(Color.parseColor(entry.color))
                gravity = Gravity.END
            })
            container.addView(row)
        }
    }

    // ── Swap logic ────────────────────────────────────────────────────────────

    private fun onSwapPressed(state: OppSlotState) {
        when (state.swapType) {
            SwapType.SUB_IN -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_sub_title, state.slot))
                .setItems(arrayOf(getString(R.string.dialog_sub_normal), getString(R.string.dialog_sub_injury))) { _, which ->
                    if (which == 0) showNormalSubDialog(state) else showInjurySubDialog(state)
                }
                .setNegativeButton(getString(R.string.btn_cancel), null).show()
            SwapType.RETURN_STARTER -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_return_title, state.slot))
                .setMessage(getString(R.string.dialog_return_message, "#${state.originalJersey}"))
                .setPositiveButton("Zurück") { _, _ ->
                    db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, state.originalJersey)
                    buildView()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null).show()
            SwapType.INJURY_ONLY -> showInjurySubDialog(state)
            SwapType.NONE -> {}
        }
    }

    private fun availableBench(): List<BenchPlayer> {
        val subs = db.getOpponentSubstitutionsForGame(gameId)
        val onField = computeState().first.map { it.currentJersey }.toSet()
        return db.getBenchPlayers(gameId).filter { bp ->
            subs.none { it.jerseyIn == bp.jerseyNumber } && bp.jerseyNumber !in onField
        }
    }

    private fun outStarters(): List<String> {
        val subs = db.getOpponentSubstitutionsForGame(gameId)
        val onField = computeState().first.map { it.currentJersey }.toSet()
        return db.getLineup(gameId).map { it.jerseyNumber }.filter { jersey ->
            subs.count { it.jerseyOut == jersey } == 1 &&
            subs.none  { it.jerseyIn  == jersey } &&
            jersey !in onField
        }
    }

    private fun showNormalSubDialog(state: OppSlotState) {
        val bench = availableBench()
        if (bench.isEmpty()) {
            showJerseyInput("Einwechslung – Slot ${state.slot}", "") { jersey ->
                if (jersey.isNotEmpty()) {
                    db.insertBenchPlayer(gameId, jersey)
                    db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, jersey)
                    buildView()
                }
            }
            return
        }
        val labels = (bench.map { "#${it.jerseyNumber}" } + getString(R.string.dialog_other_number)).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_sub_title, state.slot))
            .setItems(labels) { _, which ->
                if (which < bench.size) {
                    db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, bench[which].jerseyNumber)
                    buildView()
                } else {
                    showJerseyInput(getString(R.string.dialog_jersey_title), "") { jersey ->
                        if (jersey.isNotEmpty()) {
                            db.insertBenchPlayer(gameId, jersey)
                            db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, jersey)
                            buildView()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    private fun showInjurySubDialog(state: OppSlotState) {
        val candidates = (availableBench().map { it.jerseyNumber } + outStarters()).distinct()
        if (candidates.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_no_players_title))
                .setMessage(getString(R.string.dialog_no_players_oppo_message))
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_injury_title, state.slot))
            .setItems(candidates.map { "#$it" }.toTypedArray()) { _, which ->
                db.addOpponentSubstitution(gameId, state.slot, state.currentJersey, candidates[which])
                buildView()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
    }

    // ── Shared helpers – identical to OwnLineupActivity ───────────────────────

    private fun rowLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(8, 12, 8, 12)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 4 }
    }

    private fun slotNumberView(slot: Int, colorHex: String) = TextView(this).apply {
        text = "$slot"
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor(colorHex))
        width = 72
        gravity = Gravity.CENTER
    }

    private fun swapButton(swapType: SwapType, onClick: () -> Unit) = Button(this).apply {
        val isInjuryOnly = swapType == SwapType.INJURY_ONLY
        text = if (isInjuryOnly) "🩹" else "⇄"
        textSize = if (isInjuryOnly) 16f else 18f
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor(if (isInjuryOnly) "#e67e22" else "#1a5fa8")
        )
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 8 }
        setPadding(24, 4, 24, 4)
        setOnClickListener { onClick() }
    }

    private fun clearButton(onClick: () -> Unit) = ImageButton(this).apply {
        setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
            this@OpponentLineupActivity, android.R.drawable.ic_menu_close_clear_cancel))
        background = null
        setOnClickListener { onClick() }
    }

    private fun statusIcon(symbol: String, colorHex: String) = TextView(this).apply {
        text = symbol
        textSize = 20f
        setTextColor(Color.parseColor(colorHex))
        setPadding(8, 0, 8, 0)
    }

    private fun showJerseyInput(title: String, current: String, onConfirm: (String) -> Unit) {
        val et = EditText(this).apply {
            hint = getString(R.string.dialog_jersey_title)
            setText(current)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("OK") { _, _ -> onConfirm(et.text.toString().trim()) }
            .setNegativeButton(getString(R.string.btn_cancel), null).show()
        et.post { et.selectAll() }
    }
}
