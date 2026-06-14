package com.filtertube.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.data.GoogleAuth
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeAccountRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(onVideoClick: (Video) -> Unit, onOpenPlaylist: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(context) }

    var version by remember { mutableStateOf(0) }
    val likes = remember(version) { store.likes() }
    val downloads = remember(version) { store.downloads() }
    val playlists = remember(version) { store.playlists() }

    var account by remember { mutableStateOf<GoogleSignInAccount?>(GoogleAuth.lastAccount(context)) }
    var ytLikes by remember { mutableStateOf(store.youtubeLikes()) }
    var status by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    fun importLikes(acct: GoogleSignInAccount) {
        val a = acct.account ?: return
        status = "מייבא מיוטיוב..."
        scope.launch {
            try {
                val token = GoogleAuth.accessToken(context, a)
                val liked = YouTubeAccountRepository.likedVideos(token)
                store.setYoutubeLikes(liked)
                ytLikes = liked
                status = "יובאו ${liked.size} סרטונים ✓"
            } catch (e: Exception) {
                status = "שגיאה בייבוא: ${e.message}"
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val acct = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            account = acct
            importLikes(acct)
        } catch (e: ApiException) {
            status = "ההתחברות נכשלה (${e.statusCode})"
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onCreate = { name -> store.createPlaylist(name); version++; showCreate = false },
            onDismiss = { showCreate = false },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        item {
            Text("ספריה", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp))
            HorizontalDivider(color = Color(0xFF272727))
        }

        // חיבור Google
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, null, tint = Color(0xFFFF0000), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (account != null) "מחובר: ${account?.email ?: ""}" else "חיבור לחשבון יוטיוב",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("ייבוא הסרטונים שאהבת ביוטיוב", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(
                        onClick = {
                            val acct = account
                            if (acct != null) importLikes(acct) else signInLauncher.launch(GoogleAuth.client(context).signInIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    ) { Text(if (account != null) "רענן ייבוא" else "התחבר") }
                    if (account != null) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            GoogleAuth.client(context).signOut()
                            account = null; ytLikes = emptyList(); status = "התנתקת"
                        }) { Text("התנתק") }
                    }
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = Color(0xFFFFAA00), fontSize = 12.sp)
                }
            }
        }

        section("אהבתי", likes, Icons.Default.Favorite, onVideoClick)
        section("הורדות", downloads, Icons.Default.Download, onVideoClick)
        section("אהבתי ביוטיוב", ytLikes, Icons.Default.AccountCircle, onVideoClick)

        // אלבומים
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color(0xFFFF0000), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("אלבומים", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "אלבום חדש", tint = Color.White) }
            }
        }
        if (playlists.isEmpty()) {
            item { Text("עדיין אין אלבומים — צור אחד עם +", color = Color(0xFF888888), fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) }
        } else {
            items(playlists, key = { it.name }) { pl ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(pl.name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF272727)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color(0xFF888888))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pl.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${pl.videos.size} שירים", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    videos: List<Video>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onVideoClick: (Video) -> Unit,
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color(0xFFFF0000), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("$title (${videos.size})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (videos.isEmpty()) {
        item { Text("ריק", color = Color(0xFF888888), fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) }
    } else {
        items(videos.take(30), key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
    }
}

@Composable
private fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onCreate(name) }) { Text("צור") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("בטל") } },
        title = { Text("אלבום חדש") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("שם האלבום") })
        },
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White,
        textContentColor = Color.White,
    )
}
