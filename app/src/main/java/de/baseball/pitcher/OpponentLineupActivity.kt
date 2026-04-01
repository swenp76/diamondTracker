package de.baseball.pitcher

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class OpponentLineupActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var gameId: Long = -1

    private lateinit var recyclerLineup: RecyclerView
    private lateinit var recyclerBench: RecyclerView
    private lateinit var tvBenchEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opponent_lineup)

        gameId = intent.getLongExtra("gameId", -1)
        val opponentName = intent.getStringExtra("opponentName") ?: "Gegner"
        supportActionBar?.title = "Aufstellung – $opponentName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = DatabaseHelper(this)

        recyclerLineup = findViewById(R.id.recyclerLineup)
        recyclerLineup.layoutManager = LinearLayoutManager(this)
        recyclerLineup.isNestedScrollingEnabled = false

        recyclerBench = findViewById(R.id.recyclerBench)
        recyclerBench.layoutManager = LinearLayoutManager(this)
        recyclerBench.isNestedScrollingEnabled = false

        tvBenchEmpty = findViewById(R.id.tvBenchEmpty)

        findViewById<Button>(R.id.btnAddBench).setOnClickListener {
            showJerseyInputDialog("Auswechselspieler hinzufügen", "") { jersey ->
                if (jersey.isNotEmpty()) {
                    db.insertBenchPlayer(gameId, jersey)
                    refresh()
                }
            }
        }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val lineup = db.getLineup(gameId)
        val bench = db.getBenchPlayers(gameId)

        // Build slots 1-9, merging DB entries
        val slots = (1..9).map { order ->
            lineup.firstOrNull { it.battingOrder == order }
                ?: LineupEntry(gameId = gameId, battingOrder = order, jerseyNumber = "")
        }

        recyclerLineup.adapter = LineupAdapter(
            slots,
            onEdit = { entry -> showEditLineupSlot(entry) },
            onSub = { entry -> showSubstituteDialog(entry, bench) }
        )

        recyclerBench.adapter = BenchAdapter(bench) { benchPlayer ->
            AlertDialog.Builder(this)
                .setTitle("Auswechselspieler entfernen")
                .setMessage("Trikotnummer #${benchPlayer.jerseyNumber} von der Bank entfernen?")
                .setPositiveButton("Entfernen") { _, _ ->
                    db.deleteBenchPlayer(benchPlayer.id)
                    refresh()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        tvBenchEmpty.visibility = if (bench.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showEditLineupSlot(entry: LineupEntry) {
        showJerseyInputDialog(
            "Batting Order ${entry.battingOrder} – Trikotnummer",
            entry.jerseyNumber
        ) { jersey ->
            if (jersey.isEmpty()) {
                db.deleteLineupEntry(gameId, entry.battingOrder)
            } else {
                db.upsertLineupEntry(gameId, entry.battingOrder, jersey)
            }
            refresh()
        }
    }

    private fun showSubstituteDialog(entry: LineupEntry, bench: List<BenchPlayer>) {
        if (bench.isEmpty()) {
            showJerseyInputDialog(
                "Wechsel – Batting Order ${entry.battingOrder}",
                ""
            ) { jersey ->
                if (jersey.isNotEmpty()) {
                    val oldJersey = entry.jerseyNumber
                    db.substitutePlayer(gameId, entry.battingOrder, jersey)
                    if (oldJersey.isNotEmpty()) db.insertBenchPlayer(gameId, oldJersey)
                    refresh()
                }
            }
            return
        }

        // Show bench players + option to enter a new number
        val benchLabels = bench.map { "#${it.jerseyNumber}" }.toMutableList()
        benchLabels.add("Andere Nummer eingeben…")

        AlertDialog.Builder(this)
            .setTitle("Wechsel – Batting Order ${entry.battingOrder}\nAktuell: #${entry.jerseyNumber.ifEmpty { "–" }}")
            .setItems(benchLabels.toTypedArray()) { _, which ->
                if (which < bench.size) {
                    val sub = bench[which]
                    val oldJersey = entry.jerseyNumber
                    db.substitutePlayer(gameId, entry.battingOrder, sub.jerseyNumber)
                    db.deleteBenchPlayer(sub.id)
                    if (oldJersey.isNotEmpty()) db.insertBenchPlayer(gameId, oldJersey)
                    refresh()
                } else {
                    showJerseyInputDialog("Trikotnummer des Einwechselspielers", "") { jersey ->
                        if (jersey.isNotEmpty()) {
                            val oldJersey = entry.jerseyNumber
                            db.substitutePlayer(gameId, entry.battingOrder, jersey)
                            if (oldJersey.isNotEmpty()) db.insertBenchPlayer(gameId, oldJersey)
                            refresh()
                        }
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showJerseyInputDialog(title: String, currentValue: String, onConfirm: (String) -> Unit) {
        val et = EditText(this).apply {
            hint = "Trikotnummer"
            setText(currentValue)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("OK") { _, _ -> onConfirm(et.text.toString().trim()) }
            .setNegativeButton("Abbrechen", null)
            .show()
        et.post { et.selectAll() }
    }
}

class LineupAdapter(
    private val slots: List<LineupEntry>,
    private val onEdit: (LineupEntry) -> Unit,
    private val onSub: (LineupEntry) -> Unit
) : RecyclerView.Adapter<LineupAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvOrder: TextView = view.findViewById(R.id.tvBattingOrder)
        val tvJersey: TextView = view.findViewById(R.id.tvJerseyNumber)
        val btnEdit: Button = view.findViewById(R.id.btnEditSlot)
        val btnSub: Button = view.findViewById(R.id.btnSub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lineup_slot, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = slots[position]
        holder.tvOrder.text = entry.battingOrder.toString()
        holder.tvJersey.text = if (entry.jerseyNumber.isNotEmpty()) "#${entry.jerseyNumber}" else "–"
        holder.btnEdit.setOnClickListener { onEdit(entry) }
        holder.btnSub.setOnClickListener { onSub(entry) }
    }

    override fun getItemCount() = slots.size
}

class BenchAdapter(
    private val bench: List<BenchPlayer>,
    private val onRemove: (BenchPlayer) -> Unit
) : RecyclerView.Adapter<BenchAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvJersey: TextView = view.findViewById(R.id.tvBenchJersey)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveBench)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bench_player, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = bench[position]
        holder.tvJersey.text = "#${p.jerseyNumber}"
        holder.btnRemove.setOnClickListener { onRemove(p) }
    }

    override fun getItemCount() = bench.size
}
