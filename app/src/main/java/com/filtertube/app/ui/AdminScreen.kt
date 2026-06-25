package com.filtertube.app.ui
import com.filtertube.app.ThemeState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelAdmin
import com.filtertube.app.data.ChannelRequests
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.categoryLabels
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }

    // ברירת מחדל: הטוקן שהוזרק מה-secret (BuildConfig) — כך אין צורך להזין ידנית
    var token by remember { mutableStateOf(settings.githubToken.ifBlank { com.filtertube.app.BuildConfig.BUG_REPORT_TOKEN }) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var sha by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var requests by remember { mutableStateOf<List<ChannelRequests.Req>>(emptyList()) }

    // שדות הוספה
    var newChannelInput by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("music") }
    var busy by remember { mutableStateOf(false) }

    fun loadChannels() {
        if (token.isBlank()) { status = "הזן GitHub token תחילה"; return }
        loading = true; status = ""
        scope.launch {
            try {
                val (list, currentSha) = ChannelAdmin.fetchCurrent(token)
                channels = list.sortedBy { it.name }
                sha = currentSha
                settings.githubToken = token
                requests = runCatching { ChannelRequests.list(token) }.getOrDefault(emptyList())
                status = "${list.size} ערוצים נטענו · ${requests.size} בקשות ממתינות"
            } catch (e: Exception) {
                status = "שגיאה: ${e.message}"
            } finally { loading = false }
        }
    }

    fun approveRequest(r: ChannelRequests.Req) {
        if (busy) return
        busy = true; status = "מאשר: ${r.name}..."
        scope.launch {
            try {
                val resolved = ChannelAdmin.resolveChannel(r.url)
                if (resolved == null) { status = "לא זוהה ערוץ מהקישור של ${r.name}"; busy = false; return@launch }
                val (cid, nm) = resolved
                if (channels.none { it.youtubeChannelId == cid }) {
                    val updated = (channels + Channel(cid, nm, r.category)).sortedBy { it.name }
                    val ok = ChannelAdmin.commit(token, updated, sha, "Add channel (request): $nm")
                    if (!ok) { status = "שגיאה בעדכון GitHub"; busy = false; return@launch }
                    val (list, newSha) = ChannelAdmin.fetchCurrent(token)
                    channels = list.sortedBy { it.name }; sha = newSha
                }
                ChannelRequests.delete(token, r.fileName, r.sha)
                requests = runCatching { ChannelRequests.list(token) }.getOrDefault(emptyList())
                status = "אושר: ${r.name} ✓"
            } catch (e: Exception) { status = "שגיאה: ${e.message}" } finally { busy = false }
        }
    }

    fun rejectRequest(r: ChannelRequests.Req) {
        if (busy) return
        busy = true; status = "דוחה בקשה..."
        scope.launch {
            try {
                ChannelRequests.delete(token, r.fileName, r.sha)
                requests = runCatching { ChannelRequests.list(token) }.getOrDefault(emptyList())
                status = "הבקשה נדחתה ✓"
            } catch (e: Exception) { status = "שגיאה: ${e.message}" } finally { busy = false }
        }
    }

    fun addChannel() {
        if (newChannelInput.isBlank() || busy) return
        busy = true; status = "מזהה ערוץ..."
        scope.launch {
            try {
                val resolved = ChannelAdmin.resolveChannel(newChannelInput.trim())
                if (resolved == null) { status = "ערוץ לא נמצא"; busy = false; return@launch }
                val (channelId, name) = resolved
                if (channels.any { it.youtubeChannelId == channelId }) {
                    status = "הערוץ כבר קיים"; busy = false; return@launch
                }
                val updated = (channels + Channel(channelId, name, newCategory)).sortedBy { it.name }
                status = "מעדכן ב-GitHub..."
                val ok = ChannelAdmin.commit(token, updated, sha, "Add channel: $name")
                if (ok) {
                    val (list, newSha) = ChannelAdmin.fetchCurrent(token)
                    channels = list.sortedBy { it.name }; sha = newSha
                    newChannelInput = ""
                    status = "נוסף: $name ✓"
                } else status = "שגיאה בעדכון GitHub"
            } catch (e: Exception) {
                status = "שגיאה: ${e.message}"
            } finally { busy = false }
        }
    }

    fun removeChannel(channel: Channel) {
        if (busy) return
        busy = true; status = "מסיר..."
        scope.launch {
            try {
                val updated = channels.filter { it.youtubeChannelId != channel.youtubeChannelId }
                val ok = ChannelAdmin.commit(token, updated, sha, "Remove channel: ${channel.name}")
                if (ok) {
                    val (list, newSha) = ChannelAdmin.fetchCurrent(token)
                    channels = list.sortedBy { it.name }; sha = newSha
                    status = "הוסר: ${channel.name} ✓"
                } else status = "שגיאה בעדכון"
            } catch (e: Exception) {
                status = "שגיאה: ${e.message}"
            } finally { busy = false }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = ThemeState.text) }
            Text("פאנל ניהול ערוצים", color = ThemeState.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = ThemeState.divider)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                // GitHub token
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Token", color = ThemeState.subtext) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ThemeState.text, unfocusedTextColor = ThemeState.text,
                        focusedBorderColor = Color(0xFFFF0000), unfocusedBorderColor = Color(0xFF333333),
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { loadChannels() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeState.divider)) {
                    Text(if (loading) "טוען..." else "טען ערוצים")
                }

                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = Color(0xFFFFAA00), fontSize = 12.sp)
                }

                if (requests.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("בקשות ממתינות (${requests.size})", color = Color(0xFFFFAA00), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    requests.forEach { r ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(ThemeState.card).padding(12.dp),
                        ) {
                            Text(r.name, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(categoryLabels[r.category] ?: r.category, color = Color(0xFFFF0000), fontSize = 11.sp)
                            if (r.url.isNotBlank()) {
                                Text(r.url, color = ThemeState.subtext, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (r.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(r.description, color = ThemeState.subtext2, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { approveRequest(r) }, enabled = !busy,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                ) { Text("אשר") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { rejectRequest(r) }, enabled = !busy) {
                                    Text("דחה", color = ThemeState.text)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (channels.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("הוסף ערוץ", color = Color(0xFFFF0000), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newChannelInput,
                        onValueChange = { newChannelInput = it },
                        label = { Text("קישור / @handle / UC...", color = ThemeState.subtext) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ThemeState.text, unfocusedTextColor = ThemeState.text,
                            focusedBorderColor = Color(0xFFFF0000), unfocusedBorderColor = Color(0xFF333333),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    // בחירת קטגוריה
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        categoryLabels.forEach { (key, label) ->
                            val selected = newCategory == key
                            Box(
                                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) Color(0xFFFF0000) else ThemeState.divider)
                                    .clickable { newCategory = key }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text(label, color = ThemeState.text, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { addChannel() }, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("הוסף ערוץ")
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("${channels.size} ערוצים מאושרים", color = ThemeState.subtext2, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
            }

            items(channels, key = { it.youtubeChannelId }) { channel ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(channel.name, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(categoryLabels[channel.category] ?: channel.category,
                            color = ThemeState.subtext, fontSize = 11.sp)
                    }
                    IconButton(onClick = { removeChannel(channel) }, enabled = !busy) {
                        Icon(Icons.Default.Delete, "הסר", tint = Color(0xFFFF0000))
                    }
                }
                HorizontalDivider(color = ThemeState.card)
            }
        }
    }
}
