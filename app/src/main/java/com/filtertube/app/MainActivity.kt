package com.filtertube.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.filtertube.app.ui.HomeScreen
import com.filtertube.app.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FilterTubeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreen(
                onVideoClick = { video ->
                    // ה-URI ב-Compose Navigation דורש URL-encoded params
                    val encodedTitle = Uri.encode(video.title)
                    val encodedChannel = Uri.encode(video.channelName)
                    navController.navigate(
                        "player/${video.id}/$encodedTitle/$encodedChannel"
                    )
                },
            )
        }

        composable(
            route = "player/{videoId}/{title}/{channel}",
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("channel") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            PlayerScreen(
                videoId = backStackEntry.arguments?.getString("videoId").orEmpty(),
                title = backStackEntry.arguments?.getString("title").orEmpty(),
                channelName = backStackEntry.arguments?.getString("channel").orEmpty(),
                onBack = { navController.popBackStack() },
            )
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
