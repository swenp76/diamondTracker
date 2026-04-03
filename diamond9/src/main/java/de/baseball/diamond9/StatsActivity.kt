package de.baseball.diamond9

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class StatsActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        db = DatabaseHelper(this)
        val pitcherId = intent.getLongExtra("pitcherId", -1)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val stats = db.getStatsForPitcher(pitcherId)
        supportActionBar?.title = stats.pitcher.name

        // Summary cards
        findViewById<TextView>(R.id.statBF).text = stats.bf.toString()
        findViewById<TextView>(R.id.statBalls).text = stats.balls.toString()
        findViewById<TextView>(R.id.statStrikes).text = stats.strikes.toString()
        findViewById<TextView>(R.id.statTotal).text = stats.totalPitches.toString()

        // Strike percentage
        val strikePercent = if (stats.totalPitches > 0)
            (stats.strikes * 100.0 / stats.totalPitches).toInt() else 0
        findViewById<TextView>(R.id.statStrikePercent).text = "$strikePercent%"

        // Pitch grid: 3 groups of B/S columns
        val gridLayout = findViewById<LinearLayout>(R.id.pitchGrid)
        buildPitchGrid(gridLayout, stats)
    }

    private fun buildPitchGrid(container: LinearLayout, stats: PitcherStats) {
        container.removeAllViews()
        val pitchesOnly = stats.pitches.filter { it.type == "B" || it.type == "S" }
        val rows = 35
        val groups = 3

        val horizontal = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (grp in 0 until groups) {
            val startIdx = grp * rows
            val endIdx = minOf(startIdx + rows, pitchesOnly.size)
            val startNum = startIdx + 1
            val endNum = startIdx + rows

            val groupLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (grp < groups - 1) 8 else 0
                }
            }

            // Group header
            val header = TextView(this).apply {
                text = "$startNum–$endNum"
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#888888"))
            }
            groupLayout.addView(header)

            // B / S column headers
            val colHeaders = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            listOf("B" to "#1a5fa8", "S" to "#c0392b").forEach { (label, color) ->
                val tv = TextView(this).apply {
                    text = label
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(color))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                colHeaders.addView(tv)
            }
            groupLayout.addView(colHeaders)

            // Rows
            for (i in 0 until rows) {
                val pitchNum = startIdx + i
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val actual = if (pitchNum < pitchesOnly.size) pitchesOnly[pitchNum] else null

                for (col in 0..1) {
                    val expectedType = if (col == 0) "B" else "S"
                    val tv = TextView(this).apply {
                        textSize = 11f
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, 44, 1f)
                        text = when {
                            actual == null -> ""
                            actual.type == expectedType -> "✓"
                            else -> "·"
                        }
                        setTextColor(when {
                            actual == null -> Color.parseColor("#dddddd")
                            actual.type == expectedType && col == 0 -> Color.parseColor("#1a5fa8")
                            actual.type == expectedType && col == 1 -> Color.parseColor("#c0392b")
                            else -> Color.parseColor("#cccccc")
                        })
                        background = ContextCompat.getDrawable(this@StatsActivity, R.drawable.cell_border)
                    }
                    row.addView(tv)
                }

                groupLayout.addView(row)
            }

            horizontal.addView(groupLayout)
        }

        container.addView(horizontal)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
