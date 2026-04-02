package de.baseball.pitcher

import android.app.AlertDialog
import android.app.DatePickerDialog
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

    private var teamId: Long = 0L
    private var teamName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_list)
        db = DatabaseHelper(this)

        teamId = intent.getLongExtra("teamId", 0L)
        teamName = intent.getStringExtra("teamName") ?: ""
        title = teamName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recyclerGames)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fabAddGame)
            .setOnClickListener { showAddGameDialog() }

        loadGames()
    }

    override fun onResume() {
        super.onResume()
        loadGames()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, getString(R.string.menu_settings))
            .setIcon(android.R.drawable.ic_menu_manage)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            1 -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadGames() {
        val games = db.getGamesForTeam(teamId)
        adapter = GameAdapter(games,
            onClick = { game ->
                val intent = Intent(this, GameHubActivity::class.java)
                intent.putExtra("gameId", game.id)
                intent.putExtra("gameOpponent", game.opponent)
                intent.putExtra("gameDate", game.date)
                startActivity(intent)
            },
            onCopy = { game -> showCopyGameDialog(game) },
            onEdit = { game -> showEditGameDialog(game) },
            onDelete = { game ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_delete_game_title))
                    .setMessage(getString(R.string.dialog_delete_game_message, game.date, game.opponent))
                    .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                        db.deleteGame(game.id)
                        loadGames()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
        )
        recycler.adapter = adapter
    }

    private fun showAddGameDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_game, null)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etOpponent = view.findViewById<EditText>(R.id.etOpponent)

        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val cal = Calendar.getInstance()
        etDate.setText(sdf.format(cal.time))
        etDate.isFocusable = false
        etDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                cal.set(year, month, day)
                etDate.setText(sdf.format(cal.time))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_game_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_create), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val date = etDate.text.toString().trim()
                val opp = etOpponent.text.toString().trim()
                if (date.isNotEmpty() && opp.isNotEmpty()) {
                    db.insertGame(date, opp, teamId)
                    loadGames()
                    dialog.dismiss()
                } else {
                    if (date.isEmpty()) etDate.error = getString(R.string.error_required_field)
                    if (opp.isEmpty()) etOpponent.error = getString(R.string.error_required_field)
                }
            }
        }
        dialog.show()
    }

    private fun showCopyGameDialog(game: Game) {
        val et = EditText(this).apply {
            setText(game.opponent)
            hint = getString(R.string.hint_opponent)
            inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(48, 24, 48, 24)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_copy_game_title, game.date))
            .setView(et)
            .setPositiveButton(getString(R.string.btn_copy), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val opp = et.text.toString().trim()
                if (opp.isNotEmpty()) {
                    db.copyGame(game.id, opp)
                    loadGames()
                    dialog.dismiss()
                } else {
                    et.error = getString(R.string.error_required_field)
                }
            }
        }
        dialog.show()
    }

    private fun showEditGameDialog(game: Game) {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val cal = Calendar.getInstance()
        try { cal.time = sdf.parse(game.date)!! } catch (_: Exception) {}

        val view = layoutInflater.inflate(R.layout.dialog_edit_game, null)
        val etDate = view.findViewById<EditText>(R.id.etEditDate)
        val etOpponent = view.findViewById<EditText>(R.id.etEditOpponent)

        etDate.setText(game.date)
        etOpponent.setText(game.opponent)
        etDate.isFocusable = false
        etDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                cal.set(year, month, day)
                etDate.setText(sdf.format(cal.time))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_game_title))
            .setView(view)
            .setPositiveButton(getString(R.string.btn_save), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val date = etDate.text.toString().trim()
                val opp = etOpponent.text.toString().trim()
                if (date.isNotEmpty() && opp.isNotEmpty()) {
                    db.updateGame(game.id, date, opp)
                    loadGames()
                    dialog.dismiss()
                } else {
                    if (date.isEmpty()) etDate.error = getString(R.string.error_required_field)
                    if (opp.isEmpty()) etOpponent.error = getString(R.string.error_required_field)
                }
            }
        }
        dialog.show()
    }

}

class GameAdapter(
    private val games: List<Game>,
    private val onClick: (Game) -> Unit,
    private val onCopy: (Game) -> Unit,
    private val onEdit: (Game) -> Unit,
    private val onDelete: (Game) -> Unit
) : RecyclerView.Adapter<GameAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvOpponent: TextView = view.findViewById(R.id.tvOpponent)
        val btnCopy: ImageButton = view.findViewById(R.id.btnCopy)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
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
        holder.btnCopy.setOnClickListener { onCopy(game) }
        holder.btnEdit.setOnClickListener { onEdit(game) }
        holder.btnDelete.setOnClickListener { onDelete(game) }
    }

    override fun getItemCount() = games.size
}
