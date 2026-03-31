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
            Toast.makeText(this, "Datei konnte nicht gelesen werden", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_team_list)
        db = DatabaseHelper(this)
        supportActionBar?.title = "Teams"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recyclerTeams)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddTeam)
            .setOnClickListener { showAddTeamDialog() }

        loadTeams()
    }

    override fun onResume() {
        super.onResume()
        loadTeams()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Team importieren").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
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
                    .setTitle("Team löschen")
                    .setMessage("\"${team.name}\" und alle Spieler wirklich löschen?")
                    .setPositiveButton("Löschen") { _, _ ->
                        db.deleteTeam(team.id)
                        loadTeams()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        )
    }

    private fun showAddTeamDialog() {
        val et = EditText(this).apply {
            hint = "Teamname"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Neues Team")
            .setView(et)
            .setPositiveButton("Erstellen") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    db.insertTeam(name)
                    loadTeams()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showImportConfirmDialog(json: String) {
        val root = try { JSONObject(json) } catch (e: Exception) {
            Toast.makeText(this, "Ungültiges Format – keine gültige JSON-Datei", Toast.LENGTH_LONG).show()
            return
        }
        if (!root.has("name")) {
            Toast.makeText(this, "Ungültiges Format – kein Team gefunden", Toast.LENGTH_LONG).show()
            return
        }

        val teamName = root.getString("name")
        AlertDialog.Builder(this)
            .setTitle("Team importieren")
            .setMessage("\"$teamName\" importieren und als neues Team hinzufügen?")
            .setPositiveButton("Importieren") { _, _ -> importTeam(root) }
            .setNegativeButton("Abbrechen", null)
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
            Toast.makeText(this, "\"${root.getString("name")}\" importiert", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
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
        holder.tvSub.text = "Tippen zum Bearbeiten"
        holder.itemView.setOnClickListener { onClick(team) }
        holder.btnDelete.setOnClickListener { onDelete(team) }
    }

    override fun getItemCount() = teams.size
}
