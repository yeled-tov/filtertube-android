package com.filtertube.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.GoogleAuth
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.YouTubeAccountRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onOpenCollection: (String) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(context) }

    var version by remember { mutableStateOf(0) }
    val likes = remember(version) { store.likes() }
    val downloads = remember(version) { store.downloads() }
    val playlists = remember(version) { store.playlists() }
    var ytLikes by remember { mutableStateOf(store.youtubeLikes()) }
    var subs by remember { mutableStateOf(store.subscriptions()) }

    var account by remember { mutableStateOf<GoogleSignInAccount?>(GoogleAuth.lastAccount(context)) }
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    // מסנכרן גם לייקים וגם מנויים מחשבון הגוגל
    fun syncAccount(acct: GoogleSignInAccount) {
        val a = acct.account ?: return
        syncing = true; status = "מסנכרן מיוטיוב..."
        scope.launch {
            try {
                val token = GoogleAuth.accessToken(context, a)
                // רק תוכן מהערוצים המאושרים — לייק/מנוי שלא ברשימה הלבנה לא נשמר ולא מוצג
                val approved = ChannelsRepository.getChannels(context).map { it.youtubeChannelId }.toHashSet()
                val liked = YouTubeAccountRepository.likedVideos(token).filter { it.channelId in approved }
                store.setYoutubeLikes(liked); ytLikes = liked
                val subList = YouTubeAccountRepository.subscriptions(token).filter { it.channelId in approved }
                store.setSubscriptions(subList); subs = subList
                status = "סונכרנו ${liked.size} לייקים ו-${subList.size} מנויים (מאושרים בלבד) ✓"
            } catch (e: Exception) {
                status = "שגיאה בסנכרון: ${e.message}"
            } finally { syncing = false }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val acct = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            account = acct
            syncAccount(acct)
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

        // כרטיס חיבור Google
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, null, tint = Color(0xFFFF0000), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (account != null) account?.email ?: "מחובר" else "חיבור לחשבון יוטיוב",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("מושך את הלייקים והמנויים שלך", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    if (syncing) CircularProgressIndicator(color = Color(0xFFFF0000), strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(
                        onClick = {
                            val acct = account
                            if (acct != null) syncAccount(acct) else signInLauncher.launch(GoogleAuth.client(context).signInIntent)
                        },
                        enabled = !syncing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                    ) { Text(if (account != null) "סנכרן מחדש" else "התחבר") }
                    if (account != null) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            GoogleAuth.client(context).signOut()
                            account = null; ytLikes = emptyList(); subs = emptyList()
                            store.setYoutubeLikes(emptyList()); store.setSubscriptions(emptyList())
                            status = "התנתקת"
                        }) { Text("התנתק") }
                    }
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = Color(0xFFFFAA00), fontSize = 12.sp)
                }
            }
        }

        // קוביות אוספים — לחיצה פותחת את התוכן
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibTile("אהבתי", likes.size, Icons.Default.Favorite, Color(0xFFFF0000)) { onOpenCollection("likes") }
                LibTile("אהבתי ביוטיוב", ytLikes.size, Icons.Default.ThumbUp, Color(0xFF3B82F6)) { onOpenCollection("ytlikes") }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibTile("המנויים שלי", subs.size, Icons.Default.Subscriptions, Color(0xFFA855F7)) { onOpenSubscriptions() }
                LibTile("הורדות", downloads.size, Icons.Default.Download, Color(0xFF10B981)) { onOpenCollection("downloads") }
            }
        }

        // אלבומים
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 4.dp),
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

@Composable
private fun RowScope.LibTile(title: String, count: Int, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).height(104.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1A)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count פריטים", color = Color(0xFF888888), fontSize = 11.sp)
        }
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
