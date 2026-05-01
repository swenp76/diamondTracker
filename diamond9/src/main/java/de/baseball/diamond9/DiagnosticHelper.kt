package de.baseball.diamond9

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Helper to generate and share a diagnostic debug report.
 */
object DiagnosticHelper {

    private const val FILE_NAME = "debug_report.txt"

    /**
     * Aggregates app metadata, device info, system state, and recent logs.
     * Returns the report as a String.
     */
    fun generateReport(context: Context): String {
        val sb = StringBuilder()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        sb.appendLine("=== Diamond9 Diagnostic Report ===")
        sb.appendLine("Report Generated: ${java.util.Date()}")
        sb.appendLine()

        // 1. App Metadata
        sb.appendLine("--- App Metadata ---")
        sb.appendLine("Version Name: ${packageInfo.versionName}")
        sb.appendLine("Version Code: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode}")
        sb.appendLine("Package: ${context.packageName}")
        sb.appendLine()

        // 2. Device Info
        sb.appendLine("--- Device Info ---")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Android OS: ${Build.VERSION.RELEASE}")
        sb.appendLine("SDK Level: ${Build.VERSION.SDK_INT}")
        sb.appendLine()

        // 3. System State
        sb.appendLine("--- System State ---")
        sb.appendLine("Timezone: ${TimeZone.getDefault().id}")
        sb.appendLine("Locale: ${Locale.getDefault()}")
        val adbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        sb.appendLine("USB Debugging (ADB): ${if (adbEnabled == 1) "Enabled" else "Disabled"}")
        sb.appendLine()

        // 4. Logcat Logs (Last 200 lines)
        sb.appendLine("--- Recent Logs (Logcat) ---")
        try {
            val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
            val reader = process.inputStream.bufferedReader()
            val logs = reader.readLines().takeLast(200)
            logs.forEach { sb.appendLine(it) }
        } catch (e: Exception) {
            sb.appendLine("Error capturing logs: ${e.message}")
        }

        return sb.toString()
    }

    /**
     * Saves the report to the cache directory and opens a share sheet.
     */
    fun createAndShareReport(context: Context) {
        try {
            val reportContent = generateReport(context)
            val file = File(context.cacheDir, FILE_NAME)
            file.writeText(reportContent)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Diamond9 Debug Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Share Diagnostic Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            // In a real app, you might want to show a Toast or log this error
            e.printStackTrace()
        }
    }
}
