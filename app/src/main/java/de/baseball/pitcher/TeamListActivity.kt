package de.baseball.pitcher

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class TeamListActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recycler: RecyclerView

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@registerForActivityResult
            showImportConfirmDialog(json)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_file_read_error), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_team_list)
        db = DatabaseHelper(this)
        supportActionBar?.title = getString(R.string.teams_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recyclerTeams)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fabAddTeam)
            .setOnClickListener { showAddTeamDialog() }

        loadTeams()
    }

    override fun onResume() {
        super.onResume()
        loadTeams()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, getString(R.string.menu_import_team)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> { importLauncher.launch(arrayOf("application/json", "*/*")); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadTeams() {
        val teams = db.getAllTeams()
        recycler.adapter = TeamAdapter(
            teams,
            onClick = { team ->
                val intent = Intent(this, TeamDetailActivity::class.java)
                intent.putExtra("teamId", team.id)
                startActivity(intent)
            },
            onDelete = { team ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_delete_team_title))
                    .setMessage(getString(R.string.dialog_delete_team_message, team.name))
                    .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                        db.deleteTeam(team.id)
                        loadTeams()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
        )
    }

    private fun showAddTeamDialog() {
        val et = EditText(this).apply {
            hint = getString(R.string.hint_team_name)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_team_title))
            .setView(et)
            .setPositiveButton(getString(R.string.btn_create)) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    db.insertTeam(name)
                    loadTeams()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showImportConfirmDialog(json: String) {
        val root = try { JSONObject(json) } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_invalid_json), Toast.LENGTH_LONG).show()
            return
        }
        if (!root.has("name")) {
            Toast.makeText(this, getString(R.string.toast_invalid_team_format), Toast.LENGTH_LONG).show()
            return
        }

        val teamName = root.getString("name")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_import_team_title))
            .setMessage(getString(R.string.dialog_import_team_message, teamName))
            .setPositiveButton(getString(R.string.btn_import)) { _, _ -> importTeam(root) }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun importTeam(root: JSONObject) {
        try {
            val teamId = db.insertTeam(root.getString("name"))

            // Standardpositionen von insertTeam() durch exportierte ersetzen
            db.getEnabledPositions(teamId).forEach { db.setPositionEnabled(teamId, it, false) }
            val posArray = root.optJSONArray("positions")
            if (posArray != null) {
                for (p in 0 until posArray.length()) {
                    db.setPositionEnabled(teamId, posArray.getInt(p), true)
                }
            }

            val playersArray = root.optJSONArray("players")
            if (playersArray != null) {
                for (p in 0 until playersArray.length()) {
                    val pl = playersArray.getJSONObject(p)
                    db.insertPlayer(
                        teamId,
                        pl.getString("name"),
                        pl.optString("number", ""),
                        pl.optInt("primary_position", 0),
                        pl.optInt("secondary_position", 0),
                        pl.optBoolean("is_pitcher", false),
                        pl.optInt("birth_year", 0)
                    )
                }
            }

            loadTeams()
            Toast.makeText(this, getString(R.string.toast_team_imported, root.getString("name")), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_import_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }
}

class TeamAdapter(
    private val teams: List<Team>,
    private val onClick: (Team) -> Unit,
    private val onDelete: (Team) -> Unit
) : RecyclerView.Adapter<TeamAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTeamName)
        val tvSub: TextView = view.findViewById(R.id.tvTeamSub)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteTeam)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_team, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val team = teams[position]
        holder.tvName.text = team.name
        holder.tvSub.text = holder.itemView.context.getString(R.string.team_tap_to_edit)
        holder.itemView.setOnClickListener { onClick(team) }
        holder.btnDelete.setOnClickListener { onDelete(team) }
    }

    override fun getItemCount() = teams.size
}
