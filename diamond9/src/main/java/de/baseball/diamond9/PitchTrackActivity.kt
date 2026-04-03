package de.baseball.diamond9

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PitchTrackActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var pitcherId: Long = -1
    private var gameId: Long = -1

    private lateinit var tvPitchCount: TextView
    private lateinit var tvBalls: TextView
    private lateinit var tvStrikes: TextView
    private lateinit var tvBF: TextView
    private lateinit var tvAtBatCount: TextView
    private lateinit var tvCurrentBatter: TextView
    private lateinit var pitchLogLayout: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pitch_track)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        pitcherId = intent.getLongExtra("pitcherId", -1)
        gameId = intent.getLongExtra("gameId", -1)
        val pitcherName = intent.getStringExtra("pitcherName") ?: getString(R.string.pitcher_default_name)
        supportActionBar?.title = pitcherName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = DatabaseHelper(this)

        // Pitcherwechsel: offenen At-Bat vom Vorgänger übernehmen
        if (gameId != -1L && db.getPitchesForPitcher(pitcherId).isEmpty()) {
            db.getIncompleteAtBatBeforePitcher(gameId, pitcherId).forEach { type ->
                db.insertPitch(pitcherId, type)
            }
        }

        tvPitchCount = findViewById(R.id.tvPitchCount)
        tvBalls = findViewById(R.id.tvBalls)
        tvStrikes = findViewById(R.id.tvStrikes)
        tvBF = findViewById(R.id.tvBF)
        tvAtBatCount = findViewById(R.id.tvAtBatCount)
        tvCurrentBatter = findViewById(R.id.tvCurrentBatter)
        pitchLogLayout = findViewById(R.id.pitchLogLayout)
        scrollView = findViewById(R.id.scrollPitchLog)

        // Tap on "Aufstellung" hint opens opponent lineup
        findViewById<TextView>(R.id.tvOpponentLineupHint).setOnClickListener {
            if (gameId != -1L) {
                val i = Intent(this, OpponentLineupActivity::class.java)
                i.putExtra("gameId", gameId)
                i.putExtra("opponentName", pitcherName)
                startActivity(i)
            }
        }

        findViewById<Button>(R.id.btnBall).setOnClickListener {
            db.insertPitch(pitcherId, "B")
            val (balls, _) = currentAtBatCount()
            if (balls >= 4) {
                db.insertPitch(pitcherId, "W")
                db.insertPitch(pitcherId, "BF")
            }
            refresh()
        }
        findViewById<Button>(R.id.btnStrike).setOnClickListener {
            db.insertPitch(pitcherId, "S")
            val (_, strikes) = currentAtBatCount()
            if (strikes >= 3) {
                db.insertPitch(pitcherId, "SO")
                db.insertPitch(pitcherId, "BF")
            }
            refresh()
        }
        findViewById<Button>(R.id.btnFoulBall).setOnClickListener {
            val (_, strikesBefore) = currentAtBatCount()
            db.insertPitch(pitcherId, "F")
            // Foul bei 2 Strikes zählt nicht als Strike – kein Auto-Out möglich
            if (strikesBefore < 2) {
                val (_, strikesAfter) = currentAtBatCount()
                if (strikesAfter >= 3) {
                    db.insertPitch(pitcherId, "SO")
                    db.insertPitch(pitcherId, "BF")
                }
            }
            refresh()
        }
        findViewById<Button>(R.id.btnHbp).setOnClickListener {
            db.insertPitch(pitcherId, "HBP")
            db.insertPitch(pitcherId, "BF")
            refresh()
        }
        findViewById<Button>(R.id.btnBatterFaced).setOnClickListener {
            db.insertPitch(pitcherId, "BF")
            refresh()
        }
        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            db.undoLastPitch(pitcherId)
            refresh()
        }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Balls und Strikes des aktuellen Batters (seit letztem BF-Marker). */
    private fun currentAtBatCount(): Pair<Int, Int> {
        val pitches = db.getStatsForPitcher(pitcherId).pitches
        val lastBf = pitches.indexOfLast { it.type == "BF" }
        val current = if (lastBf == -1) pitches else pitches.drop(lastBf + 1)
        var balls = 0; var strikes = 0
        for (p in current) {
            when (p.type) {
                "B" -> balls++
                "S" -> strikes++
                "F" -> if (strikes < 2) strikes++
            }
        }
        return Pair(balls, strikes)
    }

    private fun getBatterJersey(battingOrder: Int): String {
        if (gameId == -1L) return ""
        val jersey = db.getJerseyAtBattingOrder(gameId, battingOrder)
        return if (jersey.isNotEmpty()) "#$jersey" else ""
    }

    private fun refresh() {
        val stats = db.getStatsForPitcher(pitcherId)
        tvBalls.text = stats.balls.toString()
        tvStrikes.text = stats.strikes.toString()
        tvPitchCount.text = "#${stats.totalPitches + 1}"

        // BF: Tagesgesamt anzeigen wenn Spieler aus Roster (player_id > 0)
        val playerId = stats.pitcher.playerId
        val date = if (gameId != -1L) db.getGame(gameId)?.date ?: "" else ""
        val totalBF = if (playerId > 0 && date.isNotEmpty())
            db.getTotalBFForPlayerOnDate(playerId, date)
        else
            stats.bf
        tvBF.text = totalBF.toString()

        // Current at-bat count
        val (balls, strikes) = currentAtBatCount()
        tvAtBatCount.text = "$balls-$strikes"

        // Current batter: spielübergreifende BF-Summe für korrekten Batting-Order-Slot
        val gameBF = if (gameId != -1L) db.getTotalBFForGame(gameId) else stats.bf
        val currentBattingOrder = (gameBF % 9) + 1
        val jerseyDisplay = getBatterJersey(currentBattingOrder)
        tvCurrentBatter.text = if (jerseyDisplay.isNotEmpty())
            getString(R.string.label_batter_slot_with_jersey, jerseyDisplay, currentBattingOrder)
        else
            getString(R.string.label_batter_slot, currentBattingOrder)

        // Rebuild pitch log
        pitchLogLayout.removeAllViews()
        var pitchNumber = 0
        // BF-Offset: wieviele Batters haben Pitcher vor diesem schon beendet?
        val bfOffset = gameBF - stats.bf
        var bfCount = bfOffset
        stats.pitches.forEach { pitch ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            when (pitch.type) {
                "BF" -> {
                    bfCount++
                    val nextBattingOrder = (bfCount % 9) + 1
                    val nextJersey = getBatterJersey(nextBattingOrder)
                    val batterLabel = if (nextJersey.isNotEmpty())
                        getString(R.string.pitch_label_slot_with_jersey, nextJersey, nextBattingOrder)
                    else
                        getString(R.string.pitch_label_slot, nextBattingOrder)
                    val tv = TextView(this).apply {
                        text = batterLabel
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 11f
                    }
                    row.addView(tv)
                }
                "HBP" -> {
                    val tv = TextView(this).apply {
                        text = getString(R.string.pitch_label_hbp)
                        setTextColor(Color.parseColor("#8e44ad"))
                        textSize = 11f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    row.addView(tv)
                }
                "W" -> {
                    val tv = TextView(this).apply {
                        text = getString(R.string.pitch_label_walk)
                        setTextColor(Color.parseColor("#d35400"))
                        textSize = 11f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    row.addView(tv)
                }
                "SO" -> {
                    val tv = TextView(this).apply {
                        text = getString(R.string.pitch_label_strikeout)
                        setTextColor(Color.parseColor("#27ae60"))
                        textSize = 11f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    row.addView(tv)
                }
                "B", "S", "F" -> {
                    pitchNumber++
                    val numTv = TextView(this).apply {
                        text = "$pitchNumber."
                        textSize = 12f
                        setTextColor(Color.parseColor("#666666"))
                        width = 80
                    }
                    val badge = TextView(this).apply {
                        text = when (pitch.type) { "B" -> getString(R.string.pitch_label_ball); "F" -> getString(R.string.pitch_label_foul); else -> getString(R.string.pitch_label_strike) }
                        textSize = 14f
                        setPadding(24, 8, 24, 8)
                        setTextColor(Color.WHITE)
                        background = ContextCompat.getDrawable(
                            context,
                            when (pitch.type) { "B" -> R.drawable.badge_ball; "F" -> R.drawable.badge_foul; else -> R.drawable.badge_strike }
                        )
                    }
                    row.addView(numTv)
                    row.addView(badge)
                }
            }
            pitchLogLayout.addView(row)
        }

        // Scroll to bottom
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
