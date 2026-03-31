package de.baseball.pitcher

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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

    private fun showAddPitcherDialog() {
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
