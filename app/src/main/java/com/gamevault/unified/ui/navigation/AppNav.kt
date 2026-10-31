package com.gamevault.unified.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gamevault.unified.ui.screens.GameDetailsDialog
import com.gamevault.unified.ui.screens.LibraryScreen
import com.gamevault.unified.ui.screens.ServerScreen
import com.gamevault.unified.ui.screens.StatsScreen
import com.gamevault.unified.viewmodel.LibraryViewModel

object Routes {
    const val LIBRARY = "library"
    const val STATS = "stats"
    const val SERVER = "server"
}

@Composable
fun AppNav(vm: LibraryViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var bottomBarVisible by remember { mutableStateOf(true) }

    fun go(route: String) {
        bottomBarVisible = true
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                NavigationBar {
                NavigationBarItem(
                    selected = current == Routes.LIBRARY,
                    onClick = { go(Routes.LIBRARY) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Library") },
                    label = { Text("Library") }
                )
                NavigationBarItem(
                    selected = current == Routes.STATS,
                    onClick = { go(Routes.STATS) },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = "Stats") },
                    label = { Text("Stats") }
                )
                NavigationBarItem(
                    selected = current == Routes.SERVER,
                    onClick = { go(Routes.SERVER) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Server") },
                    label = { Text("Server") }
                )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    vm = vm,
                    onOpenServer = { go(Routes.SERVER) },
                    onOpenStats = { go(Routes.STATS) },
                    onScrollVisibility = { bottomBarVisible = it }
                )
            }
            composable(Routes.STATS) {
                StatsScreen(vm = vm)
            }
            composable(Routes.SERVER) {
                ServerScreen(vm = vm)
            }
        }
    }
    // Detail dialog overlays every tab (library list + stats recents).
    GameDetailsDialog(vm = vm)
}
