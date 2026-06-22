package com.filtertube.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
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
        val sysDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        ThemeState.dark = when (settings.themeMode) { 1 -> true; 2 -> false; else -> sysDark }

        // קצב רענון גבוה (120 הרץ) למסכים שתומכים — תצוגה חלקה
        if (settings.highRefreshRate) applyHighRefreshRate()

        // הרשאת התראות נדרשת באנדרואיד 13+ כדי להציג את חלונית הנגן
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        handleDeepLink(intent)
        setContent {
            FilterTubeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** קולט קישור יוטיוב שנפתח דרך האפליקציה — מחלץ את מזהה הסרטון ומפעיל אותו. */
    private fun handleDeepLink(intent: android.content.Intent?) {
        val url = intent?.data?.toString() ?: return
        val id = extractYouTubeId(url) ?: return
        DeepLink.pendingVideoId = id
    }

    private fun extractYouTubeId(url: String): String? {
        for (p in listOf(
            "[?&]v=([A-Za-z0-9_-]{11})",
            "youtu\\.be/([A-Za-z0-9_-]{11})",
            "/shorts/([A-Za-z0-9_-]{11})",
            "/live/([A-Za-z0-9_-]{11})",
            "/embed/([A-Za-z0-9_-]{11})",
        )) {
            Regex(p).find(url)?.let { return it.groupValues[1] }
        }
        return null
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

/** מזהה סרטון שהגיע מקישור יוטיוב חיצוני — נצרך פעם אחת ב-AppRoot. */
object DeepLink {
    var pendingVideoId by mutableStateOf<String?>(null)
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var shortsEnabled by remember { mutableStateOf(settings.shortsEnabled) }
    var filterLevel by remember { mutableStateOf(settings.filterLevel) }
    var crashReport by remember { mutableStateOf(com.filtertube.app.data.CrashLog.lastCrash(context)) }
    var pendingUpdate by remember { mutableStateOf<com.filtertube.app.data.UpdateChecker.Update?>(null) }
    LaunchedEffect(Unit) {
        val u = com.filtertube.app.data.UpdateChecker.check()
        if (u != null && u.isNewer) pendingUpdate = u
    }

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

    // קישור יוטיוב שנפתח דרך האפליקציה — מפעיל את הסרטון בנגן שלנו
    LaunchedEffect(DeepLink.pendingVideoId) {
        val id = DeepLink.pendingVideoId ?: return@LaunchedEffect
        DeepLink.pendingVideoId = null
        openVideo(Video(id, "", "", "", "https://i.ytimg.com/vi/$id/hqdefault.jpg", System.currentTimeMillis()))
    }

    // החיפוש עבר לכפתור למעלה במסך הבית — לא בסרגל התחתון
    val navItems = buildList {
        add(GlassNavItem("home", "בית", Icons.Default.Home))
        if (shortsEnabled) add(GlassNavItem("shorts", "Shorts", Icons.Default.PlayArrow))
        add(GlassNavItem("library", "ספריה", Icons.Default.LibraryMusic))
        add(GlassNavItem("settings", "הגדרות", Icons.Default.Settings))
    }

    fun navigateTab(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
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
                    onOpenLogin = { navController.navigate("ytlogin") },
                )
            }
            composable("ytlogin") { AccountLoginScreen(onDone = { navController.popBackStack() }) }
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

        // מיני-נגן + סרגל ניווט צף (זכוכית), מרחפים מעל התוכן
        if (showBottomBar) {
            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                com.filtertube.app.ui.MiniPlayer(
                    controller = controller,
                    ui = playerUi,
                    onOpen = { navController.navigate("player") { launchSingleTop = true } },
                )
                com.filtertube.app.ui.GlassNavBar(navItems, currentRoute) { navigateTab(it) }
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

    pendingUpdate?.let { u ->
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = { Text("עדכון זמין: ${u.name}") },
            text = {
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text("מה השתנה:", color = ThemeState.accent, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(u.changelog.ifEmpty { "—" }, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                }
            },
            confirmButton = {
                val apk = u.apkUrl
                if (apk != null) {
                    TextButton(onClick = { com.filtertube.app.data.UpdateChecker.downloadApk(context, apk); pendingUpdate = null }) {
                        Text("הורד והתקן")
                    }
                } else {
                    TextButton(onClick = { pendingUpdate = null }) { Text("סגור") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingUpdate = null }) { Text("אחר כך") } },
            containerColor = Color(0xFF1F1F1F),
            titleContentColor = Color.White,
            textContentColor = Color.White,
        )
    }
}

@Composable
private fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var sending by remember { mutableStateOf(false) }

    fun copy() {
        val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("crash", report))
        android.widget.Toast.makeText(context, "הועתק", android.widget.Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("נמצא באג") },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("אפשר לשלוח את הדוח ישירות אליי (דורש טוקן אדמין בהגדרות).",
                    color = ThemeState.subtext2, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text(report, color = Color(0xFF999999), fontSize = 11.sp)
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        val token = settings.githubToken
                        if (token.isBlank()) { copy(); return@TextButton }
                        sending = true
                        scope.launch {
                            val ok = com.filtertube.app.data.BugReport.submit(token, report)
                            sending = false
                            android.widget.Toast.makeText(
                                context, if (ok) "הדוח נשלח ✓" else "השליחה נכשלה",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            if (ok) onDismiss()
                        }
                    }
                ) { Text(if (sending) "שולח…" else "שלח דוח") }
                TextButton(onClick = { copy() }) { Text("העתק") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("מחק") } },
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}

/**
 * מצב ערכת-נושא גלובלי — צבע ראשי + כהה/בהיר וטוקני צבע נגזרים.
 * שינוי של accent או dark מרענן את כל המסכים מיד (mutableState).
 */
object ThemeState {
    var accent by mutableStateOf(Color(0xFFFF0000))
    var dark by mutableStateOf(true)

    val bg: Color get() = if (dark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
    val bg2: Color get() = if (dark) Color(0xFF0A0A0A) else Color(0xFFEDEDED)   // נאב/נגן כהה יותר
    val surface: Color get() = if (dark) Color(0xFF1F1F1F) else Color(0xFFFFFFFF)
    val card: Color get() = if (dark) Color(0xFF1A1A1A) else Color(0xFFF1F1F1)
    val divider: Color get() = if (dark) Color(0xFF272727) else Color(0xFFE2E2E2)
    val text: Color get() = if (dark) Color.White else Color(0xFF111111)
    val subtext: Color get() = if (dark) Color(0xFF888888) else Color(0xFF6B6B6B)
    val subtext2: Color get() = if (dark) Color(0xFFAAAAAA) else Color(0xFF555555)
}

@Composable
fun FilterTubeTheme(content: @Composable () -> Unit) {
    val base = if (ThemeState.dark) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        primary = ThemeState.accent,
        background = ThemeState.bg,
        surface = ThemeState.surface,
        onBackground = ThemeState.text,
        onSurface = ThemeState.text,
        onSurfaceVariant = ThemeState.subtext2,
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
