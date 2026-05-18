package com.filtertube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.filtertube.app.data.Channel
import com.filtertube.app.data.SupabaseClient
import com.filtertube.app.data.categoryLabels
import kotlinx.coroutines.launch

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
                    ChannelListScreen()
                }
            }
        }
    }
}

/**
 * שלוש מצבים אפשריים לטעינת הנתונים מ-Supabase
 */
sealed class ChannelsState {
    object Loading : ChannelsState()
    data class Success(val channels: List<Channel>) : ChannelsState()
    data class Error(val message: String) : ChannelsState()
}

@Composable
fun ChannelListScreen() {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ChannelsState>(ChannelsState.Loading) }

    LaunchedEffect(Unit) {
        scope.launch {
            state = try {
                ChannelsState.Success(SupabaseClient.fetchChannels())
            } catch (e: Exception) {
                ChannelsState.Error(e.message ?: "שגיאה לא ידועה")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header עם לוגו ושם
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(top = 24.dp),  // עבור status bar
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFF0000), shape = MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text("FT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "FilterTube",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        HorizontalDivider(color = Color(0xFF272727))

        // תוכן עיקרי לפי מצב
        when (val s = state) {
            is ChannelsState.Loading -> LoadingView()
            is ChannelsState.Error -> ErrorView(s.message) {
                state = ChannelsState.Loading
                scope.launch {
                    state = try {
                        ChannelsState.Success(SupabaseClient.fetchChannels())
                    } catch (e: Exception) {
                        ChannelsState.Error(e.message ?: "שגיאה")
                    }
                }
            }
            is ChannelsState.Success -> ChannelsList(s.channels)
        }
    }
}

@Composable
fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFFF0000))
            Spacer(Modifier.height(16.dp))
            Text("טוען ערוצים...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF0000),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "שגיאה בטעינה",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
            ) {
                Text("נסה שוב")
            }
        }
    }
}

@Composable
fun ChannelsList(channels: List<Channel>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${channels.size} ערוצים מאושרים",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(channels) { channel ->
                ChannelRow(channel)
                HorizontalDivider(color = Color(0xFF1F1F1F))
            }
        }
    }
}

@Composable
fun ChannelRow(channel: Channel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // אווטר עם האות הראשונה
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = colorForChannel(channel.name),
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = categoryLabels[channel.category] ?: channel.category,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// צבע אווטר לפי שם הערוץ (consistent hash)
private fun colorForChannel(name: String): Color {
    val colors = listOf(
        Color(0xFFFF0000),
        Color(0xFF3B82F6),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFFA855F7),
        Color(0xFFEC4899),
        Color(0xFF14B8A6),
    )
    val hash = name.fold(0) { acc, c -> (acc * 31 + c.code) }
    return colors[Math.floorMod(hash, colors.size)]
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
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
