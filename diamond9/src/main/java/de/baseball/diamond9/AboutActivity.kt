package de.baseball.diamond9

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.core.view.WindowCompat
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        setContent {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            AppDrawer(
                drawerState = drawerState,
                scope = scope,
                currentActivity = AboutActivity::class.java,
                context = this
            ) {
                AboutScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onOpenGithub = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_github_url)))
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
