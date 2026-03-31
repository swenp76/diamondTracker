package de.baseball.pitcher

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PitchTrackActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var pitcherId: Long = -1

    private lateinit var tvPitchCount: TextView
    private lateinit var tvBalls: TextView
    private lateinit var tvStrikes: TextView
    private lateinit var tvBF: TextView
    private lateinit var pitchLogLayout: LinearLayout
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pitch_track)

        pitcherId = intent.getLongExtra("pitcherId", -1)
        val pitcherName = intent.getStringExtra("pitcherName") ?: "Pitcher"
        supportActionBar?.title = pitcherName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = DatabaseHelper(this)

        tvPitchCount = findViewById(R.id.tvPitchCount)
        tvBalls = findViewById(R.id.tvBalls)
        tvStrikes = findViewById(R.id.tvStrikes)
        tvBF = findViewById(R.id.tvBF)
        pitchLogLayout = findViewById(R.id.pitchLogLayout)
        scrollView = findViewById(R.id.scrollPitchLog)

        findViewById<Button>(R.id.btnBall).setOnClickListener {
            db.insertPitch(pitcherId, "B")
            refresh()
        }
        findViewById<Button>(R.id.btnStrike).setOnClickListener {
            db.insertPitch(pitcherId, "S")
            refresh()
        }
        findViewById<Button>(R.id.btnHbp).setOnClickListener {
            db.insertPitch(pitcherId, "HBP")
            db.insertPitch(pitcherId, "BF")
            refresh()
        }
        findViewById<Button>(R.id.btnWalk).setOnClickListener {
            db.insertPitch(pitcherId, "W")
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

    private fun refresh() {
        val stats = db.getStatsForPitcher(pitcherId)
        tvBalls.text = stats.balls.toString()
        tvStrikes.text = stats.strikes.toString()
        tvBF.text = stats.bf.toString()
        tvPitchCount.text = "Pitch #${stats.totalPitches + 1}"

        // Rebuild pitch log
        pitchLogLayout.removeAllViews()
        var pitchNumber = 0
        stats.pitches.forEach { pitch ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            when (pitch.type) {
                "BF" -> {
                    val tv = TextView(this).apply {
                        text = "── Batter ──────────────────────"
                        setTextColor(Color.parseColor("#888888"))
                        textSize = 11f
                    }
                    row.addView(tv)
                }
                "HBP" -> {
                    val tv = TextView(this).apply {
                        text = "── Hit by Pitch ─────────────────"
                        setTextColor(Color.parseColor("#8e44ad"))
                        textSize = 11f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    row.addView(tv)
                }
                "W" -> {
                    val tv = TextView(this).apply {
                        text = "── Walk ─────────────────────────"
                        setTextColor(Color.parseColor("#d35400"))
                        textSize = 11f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    row.addView(tv)
                }
                "B", "S" -> {
                    pitchNumber++
                    val numTv = TextView(this).apply {
                        text = "$pitchNumber."
                        textSize = 12f
                        setTextColor(Color.parseColor("#666666"))
                        width = 80
                    }
                    val badge = TextView(this).apply {
                        text = if (pitch.type == "B") "Ball" else "Strike"
                        textSize = 14f
                        setPadding(24, 8, 24, 8)
                        setTextColor(Color.WHITE)
                        background = ContextCompat.getDrawable(
                            context,
                            if (pitch.type == "B") R.drawable.badge_ball else R.drawable.badge_strike
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
