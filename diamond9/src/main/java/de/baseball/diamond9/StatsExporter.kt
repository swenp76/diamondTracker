package de.baseball.diamond9

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

enum class ExportFormat { PDF, JPG, CSV }

object StatsExporter {

    // A4 landscape
    private const val PAGE_W = 842
    private const val PAGE_H = 595
    private const val MARGIN = 20f
    private const val ROW_H = 18f
    private const val HEADER_H = 22f
    private const val TITLE_AREA = 34f

    private val COLOR_PRIMARY = Color.parseColor("#1a5fa8")
    private val COLOR_ROW_ALT = Color.parseColor("#EEF4FF")

    // ── Public API ────────────────────────────────────────────────────────────────

    fun shareBatterTable(
        context: Context,
        title: String,
        subtitle: String,
        rows: List<SeasonBatterRow>,
        players: Map<Long, Player>,
        format: ExportFormat
    ) {
        val headers = listOf("Name", "PA", "AB", "H", "2B", "3B", "HR", "AVG", "OBP", "SLG", "OPS", "BB", "K")
        val colWidths = listOf(110f, 36f, 36f, 36f, 36f, 36f, 36f, 44f, 44f, 44f, 44f, 36f, 36f)
        val tableRows = rows.map { row ->
            val name = players[row.playerId]?.let { "#${it.number} ${it.name}" } ?: "-"
            listOf(
                name, row.pa.toString(), row.ab.toString(), row.hits.toString(),
                row.doubles.toString(), row.triples.toString(), row.homers.toString(),
                formatAvg(row.hits, row.ab),
                formatObp(row.hits, row.walks, row.hbp, row.ab),
                formatSlg(row.hits, row.doubles, row.triples, row.homers, row.ab),
                formatOps(row.hits, row.doubles, row.triples, row.homers, row.walks, row.hbp, row.ab),
                row.walks.toString(), row.strikeouts.toString()
            )
        }
        shareTable(context, "batter_stats", title, subtitle, headers, colWidths, tableRows, format)
    }

    fun shareSeasonPitcherTable(
        context: Context,
        title: String,
        subtitle: String,
        rows: List<SeasonPitcherRow>,
        players: Map<Long, Player>,
        format: ExportFormat
    ) {
        val headers = listOf("Name", "BF", "P", "S%", "BB", "K", "H", "HR", "GO", "FO")
        val colWidths = listOf(110f, 36f, 36f, 44f, 36f, 36f, 36f, 36f, 36f, 36f)
        val tableRows = rows.map { row ->
            val name = players[row.playerId]?.let { "#${it.number} ${it.name}" } ?: "-"
            val spct = if (row.totalPitches > 0) "%.0f%%".format(row.strikes.toFloat() / row.totalPitches * 100) else "---"
            listOf(
                name, row.bf.toString(), row.totalPitches.toString(), spct,
                row.walks.toString(), row.ks.toString(), row.hits.toString(),
                row.homers.toString(), row.gos.toString(), row.fos.toString()
            )
        }
        shareTable(context, "pitcher_season", title, subtitle, headers, colWidths, tableRows, format)
    }

    fun shareGameBatterTable(
        context: Context,
        title: String,
        subtitle: String,
        rows: List<GameBatterStatsRow>,
        players: Map<Long, Player>,
        format: ExportFormat
    ) {
        val headers = listOf("Name", "PA", "AB", "H", "2B", "3B", "HR", "AVG", "OBP", "SLG", "OPS", "BB", "K")
        val colWidths = listOf(110f, 36f, 36f, 36f, 36f, 36f, 36f, 44f, 44f, 44f, 44f, 36f, 36f)
        val tableRows = rows.map { row ->
            val name = players[row.playerId]?.let { "#${it.number} ${it.name}" } ?: "-"
            listOf(
                name, row.pa.toString(), row.ab.toString(), row.hits.toString(),
                row.doubles.toString(), row.triples.toString(), row.homers.toString(),
                formatAvg(row.hits, row.ab),
                formatObp(row.hits, row.walks, row.hbp, row.ab),
                formatSlg(row.hits, row.doubles, row.triples, row.homers, row.ab),
                formatOps(row.hits, row.doubles, row.triples, row.homers, row.walks, row.hbp, row.ab),
                row.walks.toString(), row.strikeouts.toString()
            )
        }
        shareTable(context, "game_batter_stats", title, subtitle, headers, colWidths, tableRows, format)
    }

    fun shareGamePitcherTable(
        context: Context,
        title: String,
        subtitle: String,
        rows: List<PitcherStats>,
        players: Map<Long, Player>,
        format: ExportFormat
    ) {
        val headers = listOf("Name", "BF", "P", "S%", "BB", "K", "H")
        val colWidths = listOf(110f, 36f, 36f, 44f, 36f, 36f, 36f)
        val tableRows = rows.map { row ->
            val name = players[row.pitcher.playerId]?.let { "#${it.number} ${it.name}" } ?: row.pitcher.name
            val spct = if (row.totalPitches > 0) "%.0f%%".format(row.strikes.toFloat() / row.totalPitches * 100) else "---"
            listOf(
                name, row.bf.toString(), row.totalPitches.toString(), spct,
                row.walks.toString(), row.strikeouts.toString(), row.hits.toString()
            )
        }
        shareTable(context, "game_pitcher_stats", title, subtitle, headers, colWidths, tableRows, format)
    }

    fun sharePitcherDetail(context: Context, stats: PitcherStats, format: ExportFormat) {
        when (format) {
            ExportFormat.PDF -> {
                val doc = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = doc.startPage(pageInfo)
                drawPitcherDetail(page.canvas, stats, 595f)
                doc.finishPage(page)
                val file = File(context.cacheDir, "pitcher_stats.pdf")
                file.outputStream().use { doc.writeTo(it) }
                doc.close()
                shareFile(context, file, "application/pdf")
            }
            ExportFormat.JPG -> {
                val w = 595
                val h = 320
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                drawPitcherDetail(canvas, stats, w.toFloat())
                val file = File(context.cacheDir, "pitcher_stats.jpg")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
                shareFile(context, file, "image/jpeg")
            }
            ExportFormat.CSV -> {
                val strikePercent = if (stats.totalPitches > 0) "${stats.strikes * 100 / stats.totalPitches}%" else "0%"
                val csv = buildString {
                    appendLine("Label,Value")
                    appendLine("BF,${stats.bf}")
                    appendLine("Hits,${stats.hits}")
                    appendLine("Balls,${stats.balls}")
                    appendLine("Strikes,${stats.strikes}")
                    appendLine("Walks,${stats.walks}")
                    appendLine("HBP,${stats.hbp}")
                    appendLine("Ks,${stats.strikeouts}")
                    appendLine("Pitches,${stats.totalPitches}")
                    appendLine("S%,$strikePercent")
                    appendLine("IP,${stats.ip}")
                }
                val file = File(context.cacheDir, "pitcher_stats.csv")
                file.writeText(csv, Charsets.UTF_8)
                shareFile(context, file, "text/csv")
            }
        }
    }

    // ── Format dispatch ───────────────────────────────────────────────────────────

    private fun shareTable(
        context: Context,
        baseName: String,
        title: String,
        subtitle: String,
        headers: List<String>,
        colWidths: List<Float>,
        rows: List<List<String>>,
        format: ExportFormat
    ) {
        when (format) {
            ExportFormat.PDF -> shareTablePdf(context, baseName, title, subtitle, headers, colWidths, rows)
            ExportFormat.JPG -> shareTableJpg(context, baseName, title, subtitle, headers, colWidths, rows)
            ExportFormat.CSV -> shareTableCsv(context, baseName, headers, rows)
        }
    }

    // ── PDF ───────────────────────────────────────────────────────────────────────

    private fun shareTablePdf(
        context: Context,
        baseName: String,
        title: String,
        subtitle: String,
        headers: List<String>,
        colWidths: List<Float>,
        rows: List<List<String>>
    ) {
        val rowsPerPage = ((PAGE_H - MARGIN * 2 - TITLE_AREA - HEADER_H) / ROW_H).toInt()
        val pages = if (rows.isEmpty()) listOf(emptyList()) else rows.chunked(rowsPerPage)
        val doc = PdfDocument()
        pages.forEachIndexed { idx, pageRows ->
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, idx + 1).create()
            val page = doc.startPage(info)
            drawTablePage(page.canvas, title, subtitle, headers, colWidths, pageRows, idx + 1, pages.size)
            doc.finishPage(page)
        }
        val file = File(context.cacheDir, "$baseName.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        shareFile(context, file, "application/pdf")
    }

    // ── JPG ───────────────────────────────────────────────────────────────────────

    private fun shareTableJpg(
        context: Context,
        baseName: String,
        title: String,
        subtitle: String,
        headers: List<String>,
        colWidths: List<Float>,
        rows: List<List<String>>
    ) {
        val imgH = (MARGIN * 2 + TITLE_AREA + HEADER_H + rows.size * ROW_H + MARGIN).toInt().coerceAtLeast(200)
        val bmp = Bitmap.createBitmap(PAGE_W, imgH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        drawTablePage(canvas, title, subtitle, headers, colWidths, rows, 1, 1)
        val file = File(context.cacheDir, "$baseName.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        shareFile(context, file, "image/jpeg")
    }

    // ── CSV ───────────────────────────────────────────────────────────────────────

    private fun shareTableCsv(
        context: Context,
        baseName: String,
        headers: List<String>,
        rows: List<List<String>>
    ) {
        val csv = buildString {
            appendLine(headers.joinToString(",") { csvEscape(it) })
            rows.forEach { row -> appendLine(row.joinToString(",") { csvEscape(it) }) }
        }
        val file = File(context.cacheDir, "$baseName.csv")
        file.writeText(csv, Charsets.UTF_8)
        shareFile(context, file, "text/csv")
    }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n'))
            "\"${value.replace("\"", "\"\"")}\""
        else value

    // ── Canvas drawing ────────────────────────────────────────────────────────────

    private fun drawTablePage(
        canvas: Canvas,
        title: String,
        subtitle: String,
        headers: List<String>,
        colWidths: List<Float>,
        rows: List<List<String>>,
        pageNum: Int,
        totalPages: Int
    ) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY; textSize = 10f
        }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = 10f
        }
        val bgPaint = Paint()
        val dividerPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

        var y = MARGIN + 14f
        canvas.drawText(title, MARGIN, y, titlePaint)
        if (subtitle.isNotBlank()) {
            y += 13f
            canvas.drawText(subtitle, MARGIN, y, subtitlePaint)
        }
        if (totalPages > 1) {
            val pageStr = "$pageNum / $totalPages"
            canvas.drawText(pageStr, PAGE_W - MARGIN - subtitlePaint.measureText(pageStr), MARGIN + 14f, subtitlePaint)
        }
        y += 10f

        bgPaint.color = COLOR_PRIMARY
        canvas.drawRect(MARGIN, y, PAGE_W.toFloat() - MARGIN, y + HEADER_H, bgPaint)
        var x = MARGIN + 4f
        headers.zip(colWidths).forEachIndexed { i, (header, width) ->
            if (i == 0) {
                canvas.drawText(header, x, y + HEADER_H - 6f, headerTextPaint)
            } else {
                val tw = headerTextPaint.measureText(header)
                canvas.drawText(header, x + (width - tw) / 2f, y + HEADER_H - 6f, headerTextPaint)
            }
            x += width
        }
        y += HEADER_H

        rows.forEachIndexed { idx, row ->
            bgPaint.color = if (idx % 2 == 0) Color.WHITE else COLOR_ROW_ALT
            canvas.drawRect(MARGIN, y, PAGE_W.toFloat() - MARGIN, y + ROW_H, bgPaint)
            x = MARGIN + 4f
            row.zip(colWidths).forEachIndexed { i, (cell, width) ->
                if (i == 0) {
                    canvas.drawText(cell.take(20), x, y + ROW_H - 5f, cellPaint)
                } else {
                    val tw = cellPaint.measureText(cell)
                    canvas.drawText(cell, x + (width - tw) / 2f, y + ROW_H - 5f, cellPaint)
                }
                x += width
            }
            canvas.drawLine(MARGIN, y + ROW_H, PAGE_W.toFloat() - MARGIN, y + ROW_H, dividerPaint)
            y += ROW_H
        }
    }

    private fun drawPitcherDetail(canvas: Canvas, stats: PitcherStats, width: Float) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_PRIMARY; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY; textSize = 10f; textAlign = Paint.Align.CENTER
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F0F6FF") }

        var y = MARGIN + 18f
        canvas.drawText(stats.pitcher.name, MARGIN, y, titlePaint)
        y += 28f

        val strikePercent = if (stats.totalPitches > 0) "${stats.strikes * 100 / stats.totalPitches}%" else "0%"
        val statCards = listOf(
            "BF" to stats.bf.toString(),
            "Hits" to stats.hits.toString(),
            "Balls" to stats.balls.toString(),
            "Strikes" to stats.strikes.toString(),
            "Walks" to stats.walks.toString(),
            "HBP" to stats.hbp.toString(),
            "Ks" to stats.strikeouts.toString(),
            "Pitches" to stats.totalPitches.toString(),
            "S%" to strikePercent,
            "IP" to stats.ip
        )

        val cols = 4
        val gap = 6f
        val cardW = (width - 2 * MARGIN - (cols - 1) * gap) / cols
        val cardH = 52f

        statCards.chunked(cols).forEach { chunk ->
            chunk.forEachIndexed { i, (label, value) ->
                val cardLeft = MARGIN + i * (cardW + gap)
                val cx = cardLeft + cardW / 2f
                canvas.drawRoundRect(RectF(cardLeft, y, cardLeft + cardW, y + cardH), 4f, 4f, cardBgPaint)
                canvas.drawText(label, cx, y + 16f, labelPaint)
                canvas.drawText(value, cx, y + 42f, valuePaint)
            }
            y += cardH + 8f
        }
    }

    // ── Stat formatting ───────────────────────────────────────────────────────────

    private fun formatAvg(hits: Int, ab: Int): String {
        if (ab == 0) return "--"
        val v = hits.toFloat() / ab
        return if (v >= 1f) "1.000" else ".%03d".format((v * 1000).toInt())
    }

    private fun formatObp(hits: Int, walks: Int, hbp: Int, ab: Int): String {
        val d = ab + walks + hbp
        if (d == 0) return "--"
        val v = (hits + walks + hbp).toFloat() / d
        return if (v >= 1f) "1.000" else ".%03d".format((v * 1000).toInt())
    }

    private fun formatSlg(hits: Int, doubles: Int, triples: Int, homers: Int, ab: Int): String {
        if (ab == 0) return "--"
        val singles = hits - doubles - triples - homers
        val v = (singles + 2 * doubles + 3 * triples + 4 * homers).toFloat() / ab
        return if (v >= 1f) "%.3f".format(v) else ".%03d".format((v * 1000).toInt())
    }

    private fun formatOps(hits: Int, doubles: Int, triples: Int, homers: Int, walks: Int, hbp: Int, ab: Int): String {
        val obpD = ab + walks + hbp
        if (obpD == 0 && ab == 0) return "--"
        val obp = if (obpD > 0) (hits + walks + hbp).toFloat() / obpD else 0f
        val singles = hits - doubles - triples - homers
        val slg = if (ab > 0) (singles + 2 * doubles + 3 * triples + 4 * homers).toFloat() / ab else 0f
        return "%.3f".format(maxOf(0f, obp) + maxOf(0f, slg)).trimStart('0').ifEmpty { ".000" }
    }

    // ── File sharing ──────────────────────────────────────────────────────────────

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
