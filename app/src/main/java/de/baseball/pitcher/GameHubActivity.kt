package de.baseball.pitcher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class GameHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_hub)

        val gameId = intent.getLongExtra("gameId", -1)
        val gameOpponent = intent.getStringExtra("gameOpponent") ?: ""
        val gameDate = intent.getStringExtra("gameDate") ?: ""

        supportActionBar?.title = gameOpponent
        supportActionBar?.subtitle = gameDate
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<Button>(R.id.btnOffense).setOnClickListener {
            Toast.makeText(this, "Offense – coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnDefense).setOnClickListener {
            val intent = Intent(this, PitcherListActivity::class.java)
            intent.putExtra("gameId", gameId)
            intent.putExtra("gameOpponent", gameOpponent)
            intent.putExtra("gameDate", gameDate)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnLineup).setOnClickListener {
            val intent = Intent(this, OwnLineupActivity::class.java)
            intent.putExtra("gameId", gameId)
            intent.putExtra("gameOpponent", gameOpponent)
            intent.putExtra("gameDate", gameDate)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnOppoLineup).setOnClickListener {
            val intent = Intent(this, OpponentLineupActivity::class.java)
            intent.putExtra("gameId", gameId)
            intent.putExtra("opponentName", gameOpponent)
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
