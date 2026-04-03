package de.baseball.diamond9

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import android.widget.CheckBox
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

class TeamDetailActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var teamId: Long = -1
    private lateinit var recyclerPlayers: RecyclerView
    private lateinit var tvEmptyPlayers: android.widget.TextView
    private lateinit var etTeamName: EditText

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(buildTeamJson(teamId).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.toast_team_exported), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_export_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_team_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        db = DatabaseHelper(this)
        teamId = intent.getLongExtra("teamId", -1)
        supportActionBar?.title = getString(R.string.team_detail_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etTeamName = findViewById(R.id.etTeamName)
        recyclerPlayers = findViewById(R.id.recyclerPlayers)
        tvEmptyPlayers = findViewById(R.id.tvEmptyPlayers)
        recyclerPlayers.layoutManager = LinearLayoutManager(this)

        loadTeamName()
        loadPlayers()

        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fabAddPlayer)
            .setOnClickListener { showAddPlayerDialog() }

        findViewById<android.widget.Button>(R.id.btnSaveTeamName).setOnClickListener {
            val name = etTeamName.text.toString().trim()
            if (name.isNotEmpty()) {
                db.updateTeamName(teamId, name)
                supportActionBar?.title = name
                Toast.makeText(this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPlayers()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, getString(R.string.menu_active_positions)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 2, 0, getString(R.string.menu_export_team)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> { showPositionsDialog(); true }
            2 -> {
                val teamName = db.getAllTeams().firstOrNull { it.id == teamId }?.name ?: "team"
                val safeName = teamName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                exportLauncher.launch("$safeName.json")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadTeamName() {
        val team = db.getAllTeams().firstOrNull { it.id == teamId } ?: return
        etTeamName.setText(team.name)
    }

    private fun showPositionsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val enabledPositions = db.getEnabledPositions(teamId)

        BaseballPositions.ALL.forEach { (pos, label) ->
            val cb = CheckBox(this).apply {
                text = label
                isChecked = pos in enabledPositions
                textSize = 15f
                setPadding(0, 8, 0, 8)
                setOnCheckedChangeListener { _, checked ->
                    db.setPositionEnabled(teamId, pos, checked)
                }
            }
            container.addView(cb)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_active_positions_title))
            .setView(container)
            .setPositiveButton(getString(R.string.btn_done), null)
            .show()
    }

    private fun loadPlayers() {
        val players = db.getPlayersForTeam(teamId)
        tvEmptyPlayers.visibility = if (players.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        recyclerPlayers.visibility = if (players.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        recyclerPlayers.adapter = PlayerAdapter(
            players,
            onEdit = { player -> showEditPlayerDialog(player) },
            onDelete = { player ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_delete_player_title))
                    .setMessage(getString(R.string.dialog_delete_player_message, player.name))
                    .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                        db.deletePlayer(player.id)
                        loadPlayers()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
        )
    }

    private fun showAddPlayerDialog() {
        showPlayerDialog(null)
    }

    private fun showEditPlayerDialog(player: Player) {
        showPlayerDialog(player)
    }

    private fun showPlayerDialog(existing: Player?) {
        val view = layoutInflater.inflate(R.layout.dialog_add_player, null)
        val etName = view.findViewById<EditText>(R.id.etPlayerName)
        val etNumber = view.findViewById<EditText>(R.id.etPlayerNumber)
        val spinnerPos = view.findViewById<Spinner>(R.id.spinnerPosition)
        val spinnerSecondaryPos = view.findViewById<Spinner>(R.id.spinnerSecondaryPosition)
        val cbIsPitcher = view.findViewById<CheckBox>(R.id.cbIsPitcher)
        val etBirthYear = view.findViewById<EditText>(R.id.etBirthYear)

        val enabledPositions = db.getEnabledPositions(teamId).sorted()
        val positionItems = listOf(0 to getString(R.string.spinner_no_position)) +
                BaseballPositions.ALL.filter { it.first in enabledPositions }
        val spinnerLabels = positionItems.map { it.second }

        val primaryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spinnerLabels)
        spinnerPos.adapter = primaryAdapter

        val secondaryAdapter = PositionAdapter(this, spinnerLabels)
        spinnerSecondaryPos.adapter = secondaryAdapter

        // Beim Ändern der 1. Position: gewählte Position in 2. Spinner ausgrauen
        spinnerPos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                secondaryAdapter.setDisabledIndex(pos)
                // Falls 2. Spinner die gleiche Position hat, auf "Keine" zurücksetzen
                if (spinnerSecondaryPos.selectedItemPosition == pos) {
                    spinnerSecondaryPos.setSelection(0)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        existing?.let {
            etName.setText(it.name)
            etNumber.setText(it.number)
            val idx1 = positionItems.indexOfFirst { p -> p.first == it.primaryPosition }
            if (idx1 >= 0) spinnerPos.setSelection(idx1)
            val idx2 = positionItems.indexOfFirst { p -> p.first == it.secondaryPosition }
            if (idx2 >= 0) spinnerSecondaryPos.setSelection(idx2)
            cbIsPitcher.isChecked = it.isPitcher
            if (it.birthYear > 0) etBirthYear.setText(it.birthYear.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) getString(R.string.dialog_add_player_title) else getString(R.string.dialog_edit_player_title))
            .setView(view)
            .setPositiveButton(if (existing == null) getString(R.string.btn_add) else getString(R.string.btn_save)) { _, _ ->
                val name = etName.text.toString().trim()
                val number = etNumber.text.toString().trim()
                val selectedPos = positionItems[spinnerPos.selectedItemPosition].first
                val selectedSecondaryPos = positionItems[spinnerSecondaryPos.selectedItemPosition].first
                val isPitcher = cbIsPitcher.isChecked
                val birthYear = etBirthYear.text.toString().trim().toIntOrNull() ?: 0
                if (name.isNotEmpty()) {
                    if (existing == null) {
                        db.insertPlayer(teamId, name, number, selectedPos, selectedSecondaryPos, isPitcher, birthYear)
                    } else {
                        db.updatePlayer(existing.copy(name = name, number = number, primaryPosition = selectedPos, secondaryPosition = selectedSecondaryPos, isPitcher = isPitcher, birthYear = birthYear))
                    }
                    loadPlayers()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun buildTeamJson(teamId: Long): String {
        val team = db.getAllTeams().first { it.id == teamId }
        val posArray = JSONArray()
        db.getEnabledPositions(teamId).sorted().forEach { posArray.put(it) }
        val playersArray = JSONArray()
        db.getPlayersForTeam(teamId).forEach { player ->
            playersArray.put(JSONObject().apply {
                put("name", player.name)
                put("number", player.number)
                put("primary_position", player.primaryPosition)
                put("secondary_position", player.secondaryPosition)
                put("is_pitcher", player.isPitcher)
                put("birth_year", player.birthYear)
            })
        }
        return JSONObject().apply {
            put("version", 1)
            put("name", team.name)
            put("positions", posArray)
            put("players", playersArray)
        }.toString(2)
    }
}

class PlayerAdapter(
    private val players: List<Player>,
    private val onEdit: (Player) -> Unit,
    private val onDelete: (Player) -> Unit
) : RecyclerView.Adapter<PlayerAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvPlayerNumber)
        val tvName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvPosition: TextView = view.findViewById(R.id.tvPlayerPosition)
        val tvSecondaryPosition: TextView = view.findViewById(R.id.tvPlayerSecondaryPosition)
        val tvPitcherBadge: TextView = view.findViewById(R.id.tvPitcherBadge)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePlayer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_player, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = players[position]
        holder.tvNumber.text = if (p.number.isNotEmpty()) "#${p.number}" else "–"
        holder.tvName.text = p.name
        holder.tvPosition.text = if (p.primaryPosition > 0)
            BaseballPositions.shortLabel(p.primaryPosition) else "–"
        if (p.secondaryPosition > 0) {
            holder.tvSecondaryPosition.text = BaseballPositions.shortLabel(p.secondaryPosition)
            holder.tvSecondaryPosition.visibility = android.view.View.VISIBLE
        } else {
            holder.tvSecondaryPosition.visibility = android.view.View.GONE
        }
        holder.tvPitcherBadge.visibility = if (p.isPitcher) android.view.View.VISIBLE else android.view.View.GONE
        holder.itemView.setOnClickListener { onEdit(p) }
        holder.btnDelete.setOnClickListener { onDelete(p) }
    }

    override fun getItemCount() = players.size
}

class PositionAdapter(context: android.content.Context, items: List<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {

    private var disabledIndex: Int = -1

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    fun setDisabledIndex(index: Int) {
        disabledIndex = index
        notifyDataSetChanged()
    }

    override fun isEnabled(position: Int) = position != disabledIndex

    override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        view.setTextColor(if (position == disabledIndex) Color.LTGRAY else Color.BLACK)
        return view
    }
}
