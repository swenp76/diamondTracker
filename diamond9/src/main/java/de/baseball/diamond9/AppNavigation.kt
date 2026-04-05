package de.baseball.diamond9

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class NavItem(val titleRes: Int, val icon: ImageVector, val activityClass: Class<*>) {
    object Home : NavItem(R.string.nav_home, Icons.Default.Home, CoachAct::class.java)
    object Teams : NavItem(R.string.nav_teams, Icons.Default.Group, TeamListActivity::class.java)
    object Opponents : NavItem(R.string.nav_opponents, Icons.Default.Group, ManageOpponentsActivity::class.java)
    object Settings : NavItem(R.string.nav_settings, Icons.Default.Settings, SettingsActivity::class.java)
}

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    scope: CoroutineScope,
    currentActivity: Class<*>,
    context: Context,
    content: @Composable () -> Unit
) {
    val items = listOf(NavItem.Home, NavItem.Teams, NavItem.Opponents, NavItem.Settings)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                items.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.titleRes)) },
                        selected = currentActivity == item.activityClass,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (currentActivity != item.activityClass) {
                                val intent = Intent(context, item.activityClass)
                                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        },
        content = content
    )
}
