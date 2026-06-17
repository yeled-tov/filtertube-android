package com.filtertube.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
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

        val settings = SettingsStore(this)
        ThemeState.accent = Color(settings.accentColor)   // צבע ראשי שנבחר

        // קצב רענון גבוה (120 הרץ) למסכים שתומכים — תצוגה חלקה
        if (settings.highRefreshRate) applyHighRefreshRate()

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

    /** בוחר את מצב התצוגה עם קצב הרענון הגבוה ביותר באותה רזולוציה (אם נתמך). */
    @Suppress("DEPRECATION")
    private fun applyHighRefreshRate() {
        try {
            val disp = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay)
                ?: return
            val current = disp.mode ?: return
            val best = disp.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            if (best.modeId != current.modeId) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
            }
        } catch (_: Exception) { /* בלי קצב גבוה — לא נורא */ }
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
    var crashReport by remember { mutableStateOf(com.filtertube.app.data.CrashLog.lastCrash(context)) }

    val controller by com.filtertube.app.ui.rememberMediaController()
    val playerUi = com.filtertube.app.ui.rememberPlayerUiState(controller)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val mainRoutes = listOf("home", "shorts", "search", "library", "settings")
    val showBottomBar = currentRoute in mainRoutes

    fun openVideo(video: Video) {
        navController.navigate("player") { launchSingleTop = true }
        scope.launch {
            try {
                com.filtertube.app.playback.Playback.start(context, controller, video)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "שגיאה בניגון: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                navController.popBackStack("player", inclusive = true)
            }
        }
    }

    // החיפוש עבר לכפתור למעלה במסך הבית — לא בסרגל התחתון
    val navItems = buildList {
        add(NavItem("home", "בית", Icons.Default.Home))
        if (shortsEnabled) add(NavItem("shorts", "Shorts", Icons.Default.PlayArrow))
        add(NavItem("library", "ספריה", Icons.Default.LibraryMusic))
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
                                    selectedIconColor = ThemeState.accent,
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
            composable("home") { HomeScreen(onVideoClick = ::openVideo, onSearch = { navController.navigate("search") }) }
            composable("shorts") { ShortsScreen(onOpenShort = { navController.navigate("shortsPlayer") }, onSearch = { navController.navigate("search") }) }
            composable("shortsPlayer") { ShortsPlayerScreen(onBack = { navController.popBackStack() }) }
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
            composable("library") {
                LibraryScreen(
                    onOpenCollection = { type -> navController.navigate("collection/$type") },
                    onOpenSubscriptions = { navController.navigate("subscriptions") },
                    onOpenPlaylist = { name -> navController.navigate("playlist/${Uri.encode(name)}") },
                )
            }
            composable("collection/{type}") { entry ->
                CollectionScreen(
                    type = entry.arguments?.getString("type").orEmpty(),
                    onVideoClick = ::openVideo,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("subscriptions") {
                SubscriptionsScreen(
                    onOpenChannel = { id, name -> navController.navigate("channel/$id/${Uri.encode(name)}") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("channel/{id}/{name}") { entry ->
                ChannelVideosScreen(
                    channelId = entry.arguments?.getString("id").orEmpty(),
                    channelName = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                    onVideoClick = ::openVideo,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("playlist/{name}") { entry ->
                PlaylistScreen(
                    name = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                    onVideoClick = ::openVideo,
                    onBack = { navController.popBackStack() },
                )
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

    // דיווח קריסה — מציג את השגיאה האחרונה כדי שאפשר יהיה לאבחן ולשלוח אליי
    crashReport?.let { report ->
        CrashReportDialog(report) {
            com.filtertube.app.data.CrashLog.clear(context)
            crashReport = null
        }
    }
}

@Composable
private fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("דוח קריסה אחרון") },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(report, color = Color(0xFFCCCCCC), fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("crash", report))
                android.widget.Toast.makeText(context, "הועתק — שלח לי את זה", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("העתק") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("מחק וסגור") } },
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}

/** מצב ערכת-נושא גלובלי — הצבע הראשי. שינוי שלו מרענן את כל המסכים מיד. */
object ThemeState {
    var accent by mutableStateOf(Color(0xFFFF0000))
}

@Composable
fun FilterTubeTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = ThemeState.accent,
        background = Color(0xFF0F0F0F),
        surface = Color(0xFF1F1F1F),
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFAAAAAA),
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
