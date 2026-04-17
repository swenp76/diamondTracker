package de.baseball.diamond9

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONObject

class TeamSettingsActivity : ComponentActivity() {

    private lateinit var db: DatabaseHelper
    private var teamId: Long = -1

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val settings = db.getLeagueSettings(teamId)
            val json = JSONObject().apply {
                put("type", "league_settings")
                put("innings", settings.innings)
                if (settings.timeLimitMinutes != null) put("time_limit_minutes", settings.timeLimitMinutes)
                else put("time_limit_minutes", JSONObject.NULL)
            }
            contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
            Toast.makeText(this, getString(R.string.teamsettings_league_exported), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.teamsettings_export_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
            val size = pfd?.statSize ?: 0L
            pfd?.close()
            if (size > BackupManager.MAX_IMPORT_BYTES) {
                Toast.makeText(this, getString(R.string.toast_import_file_too_large), Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@registerForActivityResult
            importLeagueJson(text)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_file_read_error), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        teamId = intent.getLongExtra("teamId", -1)
        val teamName = intent.getStringExtra("teamName") ?: ""
        db = DatabaseHelper(this)

        setContent {
            TeamSettingsScreen(
                teamName = teamName,
                leagueSettings = db.getLeagueSettings(teamId),
                onBack = { finish() },
                onImportLeague = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                onExportLeague = {
                    exportLauncher.launch("league_settings.json")
                },
                onShareLeague = {
                    val settings = db.getLeagueSettings(teamId)
                    val json = JSONObject().apply {
                        put("type", "league_settings")
                        put("innings", settings.innings)
                        if (settings.timeLimitMinutes != null) put("time_limit_minutes", settings.timeLimitMinutes)
                        else put("time_limit_minutes", JSONObject.NULL)
                    }
                    BackupManager.shareJson(this, "league_settings.json", json.toString(2))
                },
                onSaveLeague = { newSettings ->
                    db.saveLeagueSettings(newSettings)
                    Toast.makeText(this, getString(R.string.teamsettings_saved), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun importLeagueJson(text: String) {
        try {
            val json = JSONObject(text)
            val innings = json.optInt("innings", 9)
            val timeLimit = if (json.has("time_limit_minutes") && !json.isNull("time_limit_minutes"))
                json.getInt("time_limit_minutes") else null
            db.saveLeagueSettings(LeagueSettings(teamId = teamId, innings = innings, timeLimitMinutes = timeLimit))
            Toast.makeText(this, getString(R.string.teamsettings_league_imported), Toast.LENGTH_SHORT).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.teamsettings_import_error, e.message), Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSettingsScreen(
    teamName: String,
    leagueSettings: LeagueSettings,
    onBack: () -> Unit,
    onImportLeague: () -> Unit,
    onExportLeague: () -> Unit,
    onShareLeague: () -> Unit,
    onSaveLeague: (LeagueSettings) -> Unit
) {
    var innings by remember { mutableStateOf(leagueSettings.innings) }
    var timeLimitMins by remember { mutableStateOf(leagueSettings.timeLimitMinutes) }
    var showTimePicker by remember { mutableStateOf(false) }

    val timeLimitDisplay = timeLimitMins?.let { mins ->
        val h = mins / 60
        val m = mins % 60
        buildString {
            if (h > 0) { append("$h h"); if (m > 0) append(" ") }
            if (m > 0) append("$m min")
            if (h == 0 && m == 0) append("0 min")
        }
    }

    if (showTimePicker) {
        TimeLimitPickerDialog(
            initialMinutes = timeLimitMins,
            onConfirm = { newMins ->
                timeLimitMins = newMins
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    Scaffold(
        containerColor = colorResource(R.color.color_background),
        topBar = {
            TopAppBar(
                title = { Text("$teamName – ${stringResource(R.string.teamhub_settings)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_desc_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Liga-Settings ──────────────────────────────────────────────
            Text(
                text = stringResource(R.string.teamsettings_league_section),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.color_text_primary),
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Innings stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.teamsettings_innings_label),
                            fontSize = 14.sp,
                            color = colorResource(R.color.color_text_subtle)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (innings > 1) innings-- },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = null,
                                    tint = colorResource(R.color.color_primary)
                                )
                            }
                            Text(
                                text = "$innings",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorResource(R.color.color_primary),
                                modifier = Modifier.widthIn(min = 32.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = { innings++ },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = colorResource(R.color.color_primary)
                                )
                            }
                        }
                    }

                    // Time limit row — opens clock picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.teamsettings_time_limit_label),
                            fontSize = 14.sp,
                            color = colorResource(R.color.color_text_subtle)
                        )
                        TextButton(onClick = { showTimePicker = true }) {
                            Text(
                                text = timeLimitDisplay
                                    ?: stringResource(R.string.teamsettings_no_time_limit),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorResource(R.color.color_primary)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSaveLeague(leagueSettings.copy(innings = innings, timeLimitMinutes = timeLimitMins))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(R.color.color_primary),
                            contentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onImportLeague,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(R.color.color_text_subtle),
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.btn_import))
                        }
                        Button(
                            onClick = onExportLeague,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(R.color.color_text_subtle),
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.btn_export))
                        }
                        Button(
                            onClick = onShareLeague,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(R.color.color_text_subtle),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.btn_share))
                        }
                    }
                }
            }

            // ── Team-Settings (Placeholder) ────────────────────────────────
            Text(
                text = stringResource(R.string.teamsettings_team_section),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.color_text_primary),
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.teamsettings_team_coming_soon),
                        fontSize = 13.sp,
                        color = colorResource(R.color.color_text_secondary)
                    )
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = colorResource(R.color.color_gray_medium),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.teamsettings_import_team))
                    }
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = colorResource(R.color.color_gray_medium),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.teamsettings_export_team))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeLimitPickerDialog(
    initialMinutes: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val initHour = (initialMinutes ?: 90) / 60
    val initMinute = (initialMinutes ?: 90) % 60
    val state = rememberTimePickerState(
        initialHour = initHour.coerceIn(0, 23),
        initialMinute = initMinute.coerceIn(0, 59),
        is24Hour = true
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.teamsettings_set_time_limit),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(R.string.teamsettings_no_time_limit))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                        TextButton(onClick = {
                            val total = state.hour * 60 + state.minute
                            onConfirm(if (total == 0) null else total)
                        }) {
                            Text(stringResource(R.string.btn_save))
                        }
                    }
                }
            }
        }
    }
}
