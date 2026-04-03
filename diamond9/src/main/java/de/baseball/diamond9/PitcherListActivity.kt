package de.baseball.diamond9

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PitcherListActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: android.widget.TextView
    private var gameId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pitcher_list)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = DatabaseHelper(this)

        gameId = intent.getLongExtra("gameId", -1)
        supportActionBar?.title = intent.getStringExtra("gameOpponent") ?: ""
        supportActionBar?.subtitle = intent.getStringExtra("gameDate") ?: ""
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recyclerPitchers)
        tvEmpty = findViewById(R.id.tvEmptyPitchers)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fabAddPitcher)
            .setOnClickListener { showAddPitcherDialog() }

        loadPitchers()
    }

    override fun onResume() {
        super.onResume()
        loadPitchers()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadPitchers() {
        val pitchers = db.getPitchersForGame(gameId)
        tvEmpty.visibility = if (pitchers.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        recycler.visibility = if (pitchers.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        recycler.adapter = PitcherAdapter(pitchers,
            onTrack = { pitcher ->
                val intent = Intent(this, PitchTrackActivity::class.java)
                intent.putExtra("pitcherId", pitcher.id)
                intent.putExtra("pitcherName", pitcher.name)
                intent.putExtra("gameId", gameId)
                startActivity(intent)
            },
            onStats = { pitcher ->
                val intent = Intent(this, StatsActivity::class.java)
                intent.putExtra("pitcherId", pitcher.id)
                startActivity(intent)
            },
            onDelete = { pitcher ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_delete_pitcher_title))
                    .setMessage(getString(R.string.dialog_delete_pitcher_message, pitcher.name))
                    .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                        db.deletePitcher(pitcher.id)
                        loadPitchers()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
        )
    }

    private fun showAddPitcherDialog() {
        // Nur Spieler aus Lineup-Slots 1–9; Fallback: alle Team-Spieler
        val starters = db.getOwnLineupStarters(gameId)
        val teamId = db.getGame(gameId)?.teamId ?: 0L
        val players = if (starters.isNotEmpty()) starters
                      else if (teamId > 0) db.getPlayersForTeam(teamId)
                      else emptyList()

        if (players.isEmpty()) {
            val et = EditText(this).apply {
                hint = getString(R.string.hint_pitcher_name)
                setPadding(48, 24, 48, 24)
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_add_pitcher_title))
                .setView(et)
                .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                    val name = et.text.toString().trim()
                    if (name.isNotEmpty()) {
                        db.insertPitcher(gameId, name)
                        loadPitchers()
                    }
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
            return
        }

        val labels = players.map { "#${it.number}  ${it.name}${if (it.isPitcher) "  P" else ""}" }.toTypedArray()
        var selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_pitcher_title))
            .setSingleChoiceItems(labels, 0) { _, which -> selectedIndex = which }
            .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                val selected = players[selectedIndex]
                db.insertPitcher(gameId, selected.name, selected.id)
                loadPitchers()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}

class PitcherAdapter(
    private val pitchers: List<Pitcher>,
    private val onTrack: (Pitcher) -> Unit,
    private val onStats: (Pitcher) -> Unit,
    private val onDelete: (Pitcher) -> Unit
) : RecyclerView.Adapter<PitcherAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPitcherName)
        val btnTrack: Button = view.findViewById(R.id.btnTrack)
        val btnStats: Button = view.findViewById(R.id.btnStats)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePitcher)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pitcher, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = pitchers[position]
        val isActive = position == pitchers.lastIndex
        holder.tvName.text = p.name
        holder.btnTrack.isEnabled = isActive
        holder.btnTrack.alpha = if (isActive) 1f else 0.35f
        holder.btnTrack.setOnClickListener { if (isActive) onTrack(p) }
        holder.btnStats.setOnClickListener { onStats(p) }
        holder.btnDelete.setOnClickListener { onDelete(p) }
    }

    override fun getItemCount() = pitchers.size
}
