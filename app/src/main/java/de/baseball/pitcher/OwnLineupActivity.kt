package de.baseball.pitcher

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun buildLineup() {
        container.removeAllViews()
        val lineup = db.getOwnLineup(gameId)

        addSectionHeader("Starter (1 – 10)")
        for (slot in 1..10) addSlotRow(slot, lineup[slot])

        addSectionHeader("Substitutes (11 – 20)")
        for (slot in 11..20) addSlotRow(slot, lineup[slot])
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

    private fun addSlotRow(slot: Int, player: Player?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 12, 8, 12)
            background = androidx.core.content.ContextCompat.getDrawable(
                this@OwnLineupActivity, android.R.drawable.list_selector_background
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4 }
            layoutParams = params
        }

        // Slot-Nummer
        val tvSlot = TextView(this).apply {
            text = "$slot"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (slot <= 10) Color.parseColor("#1a5fa8") else Color.parseColor("#2c7a2c"))
            width = 72
            gravity = Gravity.CENTER
        }

        // Spieler-Info
        val tvPlayer = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (player != null) {
                text = "#${player.number}  ${player.name}  (${BaseballPositions.shortLabel(player.primaryPosition)})"
                setTextColor(Color.parseColor("#222222"))
            } else {
                text = "— leer —"
                setTextColor(Color.parseColor("#bbbbbb"))
            }
        }

        // Löschen-Button (nur wenn belegt)
        val btnClear = ImageButton(this).apply {
            setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
                this@OwnLineupActivity, android.R.drawable.ic_menu_close_clear_cancel
            ))
            background = null
            visibility = if (player != null) android.view.View.VISIBLE else android.view.View.INVISIBLE
            setOnClickListener {
                db.clearOwnLineupSlot(gameId, slot)
                buildLineup()
            }
        }

        row.addView(tvSlot)
        row.addView(tvPlayer)
        row.addView(btnClear)

        row.setOnClickListener { showPickPlayerDialog(slot) }

        container.addView(row)
    }

    private fun showPickPlayerDialog(slot: Int) {
        val teamId = db.getGame(gameId)?.teamId ?: 0L
        val allPlayers = if (teamId > 0) db.getPlayersForTeam(teamId) else emptyList()
        val currentLineup = db.getOwnLineup(gameId)
        val assignedIds = currentLineup.entries
            .filter { it.key != slot }
            .map { it.value.id }
            .toSet()
        val unassigned = allPlayers.filter { it.id !in assignedIds }

        if (unassigned.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Slot $slot")
                .setMessage("Keine weiteren Spieler verfügbar.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val labels = unassigned.map { "#${it.number}  ${it.name}  (${BaseballPositions.shortLabel(it.primaryPosition)})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Slot $slot – Spieler wählen")
            .setItems(labels) { _, which ->
                db.setOwnLineupPlayer(gameId, slot, unassigned[which].id)
                buildLineup()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
