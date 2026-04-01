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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Einstellungen")
            .setIcon(android.R.drawable.ic_menu_manage)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showAddGameDialog() {
        val teams = db.getAllTeams()
        if (teams.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Kein Team vorhanden")
                .setMessage("Bitte zuerst ein eigenes Team unter Einstellungen → Teams anlegen.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_add_game, null)
        val spinnerTeam = view.findViewById<Spinner>(R.id.spinnerTeam)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etOpponent = view.findViewById<EditText>(R.id.etOpponent)

        spinnerTeam.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, teams.map { it.name })

        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        etDate.setText(sdf.format(Date()))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Neues Spiel")
            .setView(view)
            .setPositiveButton("Erstellen", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val date = etDate.text.toString().trim()
                val opp = etOpponent.text.toString().trim()
                val selectedTeam = teams[spinnerTeam.selectedItemPosition]
                if (date.isNotEmpty() && opp.isNotEmpty()) {
                    val gameId = db.insertGame(date, opp, selectedTeam.id)
                    loadGames()
                    dialog.dismiss()
                    showAvailabilityDialog(gameId, selectedTeam.id)
                } else {
                    if (date.isEmpty()) etDate.error = "Pflichtfeld"
                    if (opp.isEmpty()) etOpponent.error = "Pflichtfeld"
                }
            }
        }
        dialog.show()
    }

    private fun showAvailabilityDialog(gameId: Long, teamId: Long) {
        val players = db.getPlayersForTeam(teamId)
        if (players.isEmpty()) return

        val labels = players.map { "#${it.number} ${it.name}" }.toTypedArray()
        val checked = BooleanArray(players.size) { true }

        AlertDialog.Builder(this)
            .setTitle("Anwesenheit")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Speichern") { _, _ ->
                val availableIds = players.filterIndexed { i, _ -> checked[i] }.map { it.id }.toSet()
                db.saveAvailability(gameId, availableIds)
            }
            .setNegativeButton("Überspringen", null)
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
