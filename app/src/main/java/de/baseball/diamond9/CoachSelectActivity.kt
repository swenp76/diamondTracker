package de.baseball.diamond9

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CoachSelectActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recycler: RecyclerView
    private lateinit var tvNoTeams: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coach_select)

        supportActionBar?.hide()
        db = DatabaseHelper(this)
        recycler = findViewById(R.id.recyclerTeamSelect)
        tvNoTeams = findViewById(R.id.tvNoTeams)
        recycler.layoutManager = LinearLayoutManager(this)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, getString(R.string.menu_settings))
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
