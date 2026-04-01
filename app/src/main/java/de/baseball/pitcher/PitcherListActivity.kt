package de.baseball.pitcher

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private const val MENU_LINEUP = 1
private const val MENU_AVAILABILITY = 2

class PitcherListActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recycler: RecyclerView
    private var gameId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pitcher_list)
        db = DatabaseHelper(this)

        gameId = intent.getLongExtra("gameId", -1)
        val gameTitle = intent.getStringExtra("gameTitle") ?: ""
        supportActionBar?.title = gameTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recyclerPitchers)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddPitcher)
            .setOnClickListener { showAddPitcherDialog() }

        loadPitchers()
    }

    override fun onResume() {
        super.onResume()
        loadPitchers()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_LINEUP, 0, "Gegner Aufstellung").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_AVAILABILITY, 0, "Anwesenheit")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_LINEUP -> {
                val intent = Intent(this, OpponentLineupActivity::class.java)
                intent.putExtra("gameId", gameId)
                intent.putExtra("opponentName", supportActionBar?.title?.toString() ?: "")
                startActivity(intent)
                true
            }
            MENU_AVAILABILITY -> {
                showAvailabilityDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadPitchers() {
        val pitchers = db.getPitchersForGame(gameId)
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
                    .setTitle("Pitcher löschen")
                    .setMessage("${pitcher.name} wirklich löschen?")
                    .setPositiveButton("Löschen") { _, _ ->
                        db.deletePitcher(pitcher.id)
                        loadPitchers()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        )
    }

    private fun showAvailabilityDialog() {
        val teamId = db.getGame(gameId)?.teamId ?: 0L
        val allPlayers = if (teamId > 0) db.getPlayersForTeam(teamId) else emptyList()
        if (allPlayers.isEmpty()) return

        val availableIds = db.getAvailablePlayerIds(gameId)
        val labels = allPlayers.map { "#${it.number} ${it.name}" }.toTypedArray()
        val checked = BooleanArray(allPlayers.size) { i ->
            availableIds.isEmpty() || allPlayers[i].id in availableIds
        }

        AlertDialog.Builder(this)
            .setTitle("Anwesenheit")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Speichern") { _, _ ->
                val availableSet = allPlayers.filterIndexed { i, _ -> checked[i] }.map { it.id }.toSet()
                db.saveAvailability(gameId, availableSet)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showAddPitcherDialog() {
        val players = db.getAvailablePlayers(gameId)

        if (players.isEmpty()) {
            // Kein Team oder keine Spieler → Freitexteingabe
            val et = EditText(this).apply {
                hint = "Name des Pitchers"
                setPadding(48, 24, 48, 24)
            }
            AlertDialog.Builder(this)
                .setTitle("Neuer Pitcher")
                .setView(et)
                .setPositiveButton("Hinzufügen") { _, _ ->
                    val name = et.text.toString().trim()
                    if (name.isNotEmpty()) {
                        db.insertPitcher(gameId, name)
                        loadPitchers()
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
            return
        }

        val labels = players.map { if (it.isPitcher) "${it.name}  P" else it.name }.toTypedArray()
        var selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle("Neuer Pitcher")
            .setSingleChoiceItems(labels, 0) { _, which -> selectedIndex = which }
            .setPositiveButton("Hinzufügen") { _, _ ->
                val selected = players[selectedIndex]
                db.insertPitcher(gameId, selected.name, selected.id)
                loadPitchers()
            }
            .setNegativeButton("Abbrechen", null)
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
        holder.tvName.text = p.name
        holder.btnTrack.setOnClickListener { onTrack(p) }
        holder.btnStats.setOnClickListener { onStats(p) }
        holder.btnDelete.setOnClickListener { onDelete(p) }
    }

    override fun getItemCount() = pitchers.size
}
