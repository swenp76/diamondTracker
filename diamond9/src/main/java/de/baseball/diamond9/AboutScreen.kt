package de.baseball.diamond9

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onMenuClick: () -> Unit,
    onOpenGithub: () -> Unit
) {
    var showLibraries by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (showLibraries) stringResource(R.string.about_libraries_button) else stringResource(R.string.about_title)) 
                },
                navigationIcon = {
                    if (showLibraries) {
                        IconButton(onClick = { showLibraries = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        if (showLibraries) {
            LibrariesContainer(
                Modifier.fillMaxSize().padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.icon),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.size(24.dp))

                Text(
                    text = stringResource(R.string.about_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.size(16.dp))

                Text(
                    text = stringResource(R.string.about_legal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.about_gpl_license),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.size(24.dp))

                Button(
                    onClick = onOpenGithub,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.about_github_button))
                }

                Spacer(modifier = Modifier.size(8.dp))

                OutlinedButton(
                    onClick = { showLibraries = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.about_libraries_button))
                }

                Spacer(modifier = Modifier.size(32.dp))

                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
