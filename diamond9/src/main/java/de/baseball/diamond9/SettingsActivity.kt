package de.baseball.diamond9

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : ComponentActivity() {

    private lateinit var backupLauncher: ActivityResultLauncher<String>
    private lateinit var restoreLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val backupManager = BackupManager(this)

        backupLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri ?: return@registerForActivityResult
            try {
                val json = backupManager.exportToJson()
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toString(2).toByteArray())
                }
                Toast.makeText(this, getString(R.string.settings_backup_success), Toast.LENGTH_SHORT).show()
/*            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.settings_backup_failed, e.message), Toast.LENGTH_LONG).show()
            } */
            } catch (e: Exception) {
                android.util.Log.e("BackupManager", "Export failed", e)  // ← NEU
                Toast.makeText(this, getString(R.string.settings_backup_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }

        restoreLauncher = registerForActivityResult(
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
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: return@registerForActivityResult
                backupManager.restoreFromJson(JSONObject(text))
                Toast.makeText(this, getString(R.string.settings_restore_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.settings_restore_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            AppDrawer(
                drawerState = drawerState,
                scope = scope,
                currentActivity = SettingsActivity::class.java,
                context = this
            ) {
                SettingsScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onTeamsClick = { startActivity(Intent(this, TeamListActivity::class.java)) },
                    onOpponentsClick = { startActivity(Intent(this, ManageOpponentsActivity::class.java)) },
                    onBackupClick = {
                        val fileName = "diamond9_backup_${
                            SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        }.json"
                        backupLauncher.launch(fileName)
                    },
                    onRestoreClick = {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.settings_restore_confirm_title))
                            .setMessage(getString(R.string.settings_restore_confirm_message))
                            .setPositiveButton(getString(R.string.btn_confirm)) { _, _ ->
                                restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
                            .setNegativeButton(getString(R.string.btn_cancel), null)
                            .show()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onMenuClick: () -> Unit,
    onTeamsClick: () -> Unit,
    onOpponentsClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Scaffold(
        containerColor = colorResource(R.color.color_background),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = colorResource(R.color.color_background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_section_general),
                    fontSize = 11.sp,
                    color = colorResource(R.color.color_text_secondary),
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                SettingsCard(
                    title = stringResource(R.string.settings_teams_title),
                    subtitle = stringResource(R.string.settings_teams_subtitle),
                    icon = Icons.Default.Build,
                    iconColor = colorResource(R.color.color_primary),
                    onClick = onTeamsClick
                )

                Spacer(modifier = Modifier.size(8.dp))

                SettingsCard(
                    title = stringResource(R.string.settings_opponents_title),
                    subtitle = stringResource(R.string.settings_opponents_subtitle),
                    icon = Icons.Default.Group,
                    iconColor = colorResource(R.color.color_strike),
                    onClick = onOpponentsClick
                )

                Spacer(modifier = Modifier.size(8.dp))

                SettingsCard(
                    title = stringResource(R.string.settings_backup_title),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    icon = Icons.Default.CloudUpload,
                    iconColor = colorResource(R.color.color_primary),
                    onClick = onBackupClick
                )

                Spacer(modifier = Modifier.size(8.dp))

                SettingsCard(
                    title = stringResource(R.string.settings_restore_title),
                    subtitle = stringResource(R.string.settings_restore_subtitle),
                    icon = Icons.Default.CloudDownload,
                    iconColor = colorResource(R.color.color_green),
                    onClick = onRestoreClick
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = iconColor
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.color_text_primary)
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = colorResource(R.color.color_text_secondary)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colorResource(R.color.color_gray_medium)
            )
        }
    }
}
