package de.baseball.diamond9

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ManageOpponentsActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_opponents)
        db = DatabaseHelper(this)
        supportActionBar?.title = getString(R.string.settings_opponents_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recyclerOpponents)
        tvEmpty = findViewById(R.id.tvEmptyOpponents)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fabAddOpponent)
            .setOnClickListener { showAddDialog() }

        load()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        val opponents = db.getAllOpponentTeams()
        tvEmpty.visibility = if (opponents.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (opponents.isEmpty()) View.GONE else View.VISIBLE
        recycler.adapter = OpponentAdapter(opponents,
            onDelete = { opponent ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_delete_opponent_title))
                    .setMessage(getString(R.string.dialog_delete_opponent_message, opponent.name))
                    .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                        db.deleteOpponentTeam(opponent.id)
                        load()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
        )
    }

    private fun showAddDialog() {
        val et = EditText(this).apply {
            hint = getString(R.string.hint_opponent_team_name)
            inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(48, 24, 48, 24)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_opponent_title))
            .setView(et)
            .setPositiveButton(getString(R.string.btn_add), null)
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    db.insertOpponentTeamIfNew(name)
                    load()
                    dialog.dismiss()
                } else {
                    et.error = getString(R.string.error_required_field)
                }
            }
        }
        dialog.show()
    }
}

class OpponentAdapter(
    private val opponents: List<OpponentTeam>,
    private val onDelete: (OpponentTeam) -> Unit
) : RecyclerView.Adapter<OpponentAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvOpponentName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteOpponent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_opponent, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val o = opponents[position]
        holder.tvName.text = o.name
        holder.btnDelete.setOnClickListener { onDelete(o) }
    }

    override fun getItemCount() = opponents.size
}
