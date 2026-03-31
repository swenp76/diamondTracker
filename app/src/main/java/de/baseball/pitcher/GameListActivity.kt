package de.baseball.pitcher

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import java.text.SimpleDateFormat
import java.util.*

class GameListActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: GameAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_list)
        db = DatabaseHelper(this)

        recycler = findViewById(R.id.recyclerGames)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddGame)
            .setOnClickListener { showAddGameDialog() }

        loadGames()
    }

    override fun onResume() {
        super.onResume()
        loadGames()
    }

    private fun loadGames() {
        val games = db.getAllGames()
        adapter = GameAdapter(games,
            onClick = { game ->
                val intent = Intent(this, PitcherListActivity::class.java)
                intent.putExtra("gameId", game.id)
                intent.putExtra("gameTitle", "${game.date} – ${game.opponent}")
                startActivity(intent)
            },
            onDelete = { game ->
                AlertDialog.Builder(this)
                    .setTitle("Spiel löschen")
                    .setMessage("${game.date} vs ${game.opponent} wirklich löschen?")
                    .setPositiveButton("Löschen") { _, _ ->
                        db.deleteGame(game.id)
                        loadGames()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        )
        recycler.adapter = adapter
    }

    private fun showAddGameDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_game, null)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etOpponent = view.findViewById<EditText>(R.id.etOpponent)

        // Pre-fill today's date
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        etDate.setText(sdf.format(Date()))

        AlertDialog.Builder(this)
            .setTitle("Neues Spiel")
            .setView(view)
            .setPositiveButton("Erstellen") { _, _ ->
                val date = etDate.text.toString().trim()
                val opp = etOpponent.text.toString().trim()
                if (date.isNotEmpty() && opp.isNotEmpty()) {
                    db.insertGame(date, opp)
                    loadGames()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}

class GameAdapter(
    private val games: List<Game>,
    private val onClick: (Game) -> Unit,
    private val onDelete: (Game) -> Unit
) : RecyclerView.Adapter<GameAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvOpponent: TextView = view.findViewById(R.id.tvOpponent)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val game = games[position]
        holder.tvDate.text = game.date
        holder.tvOpponent.text = game.opponent
        holder.itemView.setOnClickListener { onClick(game) }
        holder.btnDelete.setOnClickListener { onDelete(game) }
    }

    override fun getItemCount() = games.size
}
