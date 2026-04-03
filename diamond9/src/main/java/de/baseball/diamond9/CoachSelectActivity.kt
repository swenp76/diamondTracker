package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CoachSelectActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recycler: RecyclerView
    private lateinit var tvNoTeams: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coach_select)

        // Statusleiste auf helle Symbole (für dunkle Texte) umstellen
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.hide()
        db = DatabaseHelper(this)
        recycler = findViewById(R.id.recyclerTeamSelect)
        tvNoTeams = findViewById(R.id.tvNoTeams)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadTeams()
    }

    private fun loadTeams() {
        val teams = db.getAllTeams()
        if (teams.isEmpty()) {
            recycler.visibility = View.GONE
            tvNoTeams.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            tvNoTeams.visibility = View.GONE
            recycler.adapter = TeamSelectAdapter(teams) { team ->
                val intent = Intent(this, GameListActivity::class.java)
                intent.putExtra("teamId", team.id)
                intent.putExtra("teamName", team.name)
                startActivity(intent)
            }
        }
    }

}

class TeamSelectAdapter(
    private val teams: List<Team>,
    private val onClick: (Team) -> Unit
) : RecyclerView.Adapter<TeamSelectAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTeamName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_team_select, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val team = teams[position]
        holder.tvName.text = team.name
        holder.itemView.setOnClickListener { onClick(team) }
    }

    override fun getItemCount() = teams.size
}
