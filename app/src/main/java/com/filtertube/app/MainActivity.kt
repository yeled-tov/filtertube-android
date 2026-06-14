package com.filtertube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // הרשאת התראות נדרשת באנדרואיד 13+ כדי להציג את חלונית הנגן
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            FilterTubeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var shortsEnabled by remember { mutableStateOf(settings.shortsEnabled) }
    var filterLevel by remember { mutableStateOf(settings.filterLevel) }

    val controller by com.filtertube.app.ui.rememberMediaController()
    val playerUi = com.filtertube.app.ui.rememberPlayerUiState(controller)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val mainRoutes = listOf("home", "shorts", "search", "settings")
    val showBottomBar = currentRoute in mainRoutes

    fun openVideo(video: Video) {
        scope.launch { com.filtertube.app.playback.Playback.start(context, controller, video) }
        navController.navigate("player") { launchSingleTop = true }
    }

    val navItems = buildList {
        add(NavItem("home", "בית", Icons.Default.Home))
        if (shortsEnabled) add(NavItem("shorts", "Shorts", Icons.Default.PlayArrow))
        add(NavItem("search", "חיפוש", Icons.Default.Search))
        add(NavItem("settings", "הגדרות", Icons.Default.Settings))
    }

    Scaffold(
        containerColor = Color(0xFF0F0F0F),
        bottomBar = {
            if (showBottomBar) {
                androidx.compose.foundation.layout.Column {
                    com.filtertube.app.ui.MiniPlayer(
                        controller = controller,
                        ui = playerUi,
                        onOpen = { navController.navigate("player") { launchSingleTop = true } },
                    )
                    NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFFFF0000),
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = Color(0xFF888888),
                                    unselectedTextColor = Color(0xFF888888),
                                    indicatorColor = Color(0xFF1F1F1F),
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") { HomeScreen(onVideoClick = ::openVideo) }
            composable("shorts") { ShortsScreen(onVideoClick = ::openVideo) }
            composable("search") { SearchScreen(onVideoClick = ::openVideo) }
            composable("settings") {
                SettingsScreen(
                    shortsEnabled = shortsEnabled,
                    onShortsToggle = { enabled ->
                        shortsEnabled = enabled
                        settings.shortsEnabled = enabled
                    },
                    filterLevel = filterLevel,
                    onFilterLevelChange = { level ->
                        filterLevel = level
                        settings.filterLevel = level
                    },
                    onOpenAdmin = { navController.navigate("admin") },
                )
            }
            composable("admin") {
                AdminScreen(onBack = { navController.popBackStack() })
            }
            composable("player") {
                PlayerScreen(
                    controller = controller,
                    ui = playerUi,
                    onCollapse = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
fun FilterTubeTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFFFF0000),
        background = Color(0xFF0F0F0F),
        surface = Color(0xFF1F1F1F),
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFAAAAAA),
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
