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
import com.filtertube.app.data.AdminDashboard
import com.filtertube.app.data.ManualPremiumRequests
import com.filtertube.app.data.categoryLabels
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var requests by remember { mutableStateOf<List<ChannelRequests.Req>>(emptyList()) }
    var premiumRequests by remember { mutableStateOf<List<ManualPremiumRequests.RequestItem>>(emptyList()) }
    var premiumHistory by remember { mutableStateOf<List<ManualPremiumRequests.RequestItem>>(emptyList()) }
    var channelHistory by remember { mutableStateOf<List<ChannelRequests.Req>>(emptyList()) }
    var premiumLoading by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(false) }
    var dashboard by remember { mutableStateOf<AdminDashboard.Snapshot?>(null) }
    var dashboardLoading by remember { mutableStateOf(false) }

    // שדות הוספה
    var newChannelInput by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("music") }
    var newGender by remember { mutableStateOf("all") }
    var busy by remember { mutableStateOf(false) }

    fun loadChannels() {
        loading = true; status = ""
        scope.launch {
            try {
                channels = ChannelRequests.listApproved().map {
                    Channel(it.youtubeChannelId, it.name, it.category, it.gender)
                }.sortedBy { it.name }
                requests = ChannelRequests.list()
                status = "${channels.size} ערוצים נטענו · ${requests.size} בקשות ממתינות"
            } catch (e: Exception) {
                status = "שגיאה: ${e.message}"
            } finally { loading = false }
        }
    }

    fun loadPremiumRequests() {
        premiumLoading = true
        status = "טוען בקשות Premium..."
        scope.launch {
            try {
                premiumRequests = ManualPremiumRequests.list()
                if (premiumRequests.isNotEmpty()) postAdminNotification(context, premiumRequests.size)
                status = "${premiumRequests.size} בקשות Premium ממתינות"
            } catch (e: Exception) {
                status = "שגיאה בטעינת Premium: ${e.message}"
            } finally { premiumLoading = false }
        }
    }

    fun resolvePremiumRequest(request: ManualPremiumRequests.RequestItem, resolution: String) {
        if (busy) return
        busy = true
        status = if (resolution == "approved") "מאשר Premium..." else "דוחה בקשה..."
        scope.launch {
            try {
                if (ManualPremiumRequests.resolve(request.id, request.version, resolution)) {
                    premiumRequests = ManualPremiumRequests.list()
                    status = if (resolution == "approved") "Premium הופעל עבור ${request.accountEmail} ✓" else "הבקשה נדחתה ✓"
                } else {
                    status = "עדכון בקשת Premium נכשל"
                }
            } catch (e: Exception) {
                status = "שגיאה: ${e.message}"
            } finally { busy = false }
        }
    }

    fun loadHistory() {
        historyLoading = true
        status = "טוען היסטוריית החלטות..."
        scope.launch {
            try {
                premiumHistory = ManualPremiumRequests.list(history = true)
                    .filter { it.status != "pending" }
                channelHistory = ChannelRequests.list(history = true)
                    .filter { it.status != "pending" }
                status = "היסטוריה נטענה: ${premiumHistory.size + channelHistory.size} החלטות"
            } catch (e: Exception) {
                status = "שגיאה בטעינת היסטוריה: ${e.message}"
            } finally { historyLoading = false }
        }
    }

    fun loadDashboard() {
        dashboardLoading = true
        status = "טוען דשבורד לקוחות..."
        scope.launch {
            try {
                dashboard = AdminDashboard.load()
                status = "דשבורד נטען: ${dashboard?.summary?.totalAccounts ?: 0} חשבונות"
            } catch (e: Exception) {
                status = "שגיאה בדשבורד: ${e.message}"
            } finally { dashboardLoading = false }
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
                    val ok = ChannelRequests.upsertApproved(ChannelRequests.Approved(cid, nm, r.category, r.gender))
                    if (!ok) { status = "שגיאה בשמירת הערוץ"; busy = false; return@launch }
                    channels = (channels + Channel(cid, nm, r.category, r.gender)).sortedBy { it.name }
                }
                if (!ChannelRequests.resolve(r.id, r.version, "approved")) {
                    status = "הערוץ נוסף, אך סימון הבקשה כמאושרת נכשל"
                    busy = false
                    return@launch
                }
                requests = ChannelRequests.list()
                status = "אושר: ${r.name} ✓"
            } catch (e: Exception) { status = "שגיאה: ${e.message}" } finally { busy = false }
        }
    }

    fun rejectRequest(r: ChannelRequests.Req) {
        if (busy) return
        busy = true; status = "דוחה בקשה..."
        scope.launch {
            try {
                if (!ChannelRequests.resolve(r.id, r.version, "rejected")) {
                    status = "דחיית הבקשה נכשלה"
                    busy = false
                    return@launch
                }
                requests = ChannelRequests.list()
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
                status = "שומר את הערוץ..."
                val ok = ChannelRequests.upsertApproved(ChannelRequests.Approved(channelId, name, newCategory, newGender))
                if (ok) {
                    channels = (channels + Channel(channelId, name, newCategory, newGender)).sortedBy { it.name }
                    newChannelInput = ""
                    status = "נוסף: $name ✓"
                } else status = "שגיאה בשמירת הערוץ"
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
                val ok = ChannelRequests.removeApproved(channel.youtubeChannelId)
                if (ok) {
                    channels = channels.filter { it.youtubeChannelId != channel.youtubeChannelId }
                    status = "הוסר: ${channel.name} ✓"
                } else status = "שגיאה בהסרת הערוץ"
            } catch (e: Exception) {
                status = "שגיאה: ${e.message}"
            } finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        loadPremiumRequests()
        loadChannels()
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = ThemeState.text) }
            Text("פאנל ניהול", color = ThemeState.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = ThemeState.divider)

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadChannels() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeState.divider)) {
                    Text(if (loading) "טוען..." else "טען ערוצים")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { loadPremiumRequests() }, modifier = Modifier.fillMaxWidth(),
                    enabled = !premiumLoading && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                ) {
                    Text(if (premiumLoading) "טוען בקשות Premium..." else "טען בקשות Premium")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { loadHistory() }, modifier = Modifier.fillMaxWidth(),
                    enabled = !historyLoading && !busy,
                ) { Text(if (historyLoading) "טוען היסטוריה..." else "הצג היסטוריית אישורים ודחיות") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { loadDashboard() }, modifier = Modifier.fillMaxWidth(),
                    enabled = !dashboardLoading && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                ) { Text(if (dashboardLoading) "טוען לקוחות..." else "דשבורד לקוחות ומנויים") }

                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = Color(0xFFFFAA00), fontSize = 12.sp)
                }

                dashboard?.let { snapshot ->
                    Spacer(Modifier.height(16.dp))
                    Text("דשבורד לקוחות", color = Color(0xFF90CAF9), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val s = snapshot.summary
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DashboardStat("חשבונות", s.totalAccounts, Modifier.weight(1f))
                        DashboardStat("מאומתים", s.verifiedAccounts, Modifier.weight(1f))
                        DashboardStat("Premium", s.premiumAccounts, Modifier.weight(1f))
                        DashboardStat("ניסיון", s.trialAccounts, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("לקוחות (${snapshot.clients.size})", color = ThemeState.subtext2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    snapshot.clients.forEach { client ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(client.email.ifBlank { client.uid }, color = ThemeState.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(
                                    when {
                                        client.premium -> "Premium"
                                        client.trialActive -> "ניסיון"
                                        else -> "ללא מנוי"
                                    },
                                    color = if (client.premium) Color(0xFF81C784) else ThemeState.subtext,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                "${if (client.verified) "✓ מאומת" else "לא מאומת"} · ${if (client.lastSignInAt.isBlank()) "טרם התחבר" else "מחובר בעבר"}",
                                color = ThemeState.subtext2, fontSize = 10.sp,
                            )
                            if (client.premium || client.trialActive) {
                                Text(
                                    "מסלול: ${when (client.plan) { "year" -> "שנתי"; "month" -> "חודשי"; else -> if (client.trialActive) "ניסיון" else "לא ידוע" }}",
                                    color = ThemeState.subtext, fontSize = 10.sp,
                                )
                                Text(
                                    "התחלה: ${formatDashboardDate(client.subscriptionStartedAt)} · סיום: ${formatDashboardDate(if (client.premium) client.subscriptionEndsAt else client.subscriptionEndsAt)}",
                                    color = ThemeState.subtext, fontSize = 10.sp,
                                )
                            }
                        }
                        HorizontalDivider(color = ThemeState.card)
                    }
                }

                if (premiumRequests.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("בקשות Premium ממתינות (${premiumRequests.size})", color = Color(0xFFCE93D8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    premiumRequests.forEach { request ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(ThemeState.card).padding(12.dp),
                        ) {
                            Text(request.name, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("חשבון: ${request.accountEmail}", color = ThemeState.subtext, fontSize = 11.sp)
                            Text("יצירת קשר: ${request.contactEmail}", color = ThemeState.subtext, fontSize = 11.sp)
                            Text("טלפון: ${request.phone}", color = ThemeState.subtext, fontSize = 11.sp)
                            Text(
                                if (request.plan == "year") "מסלול שנתי · ${request.priceUsd}" else "מסלול חודשי · ${request.priceUsd}",
                                color = Color(0xFFCE93D8), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { resolvePremiumRequest(request, "approved") }, enabled = !busy,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                ) { Text("אשר Premium") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { resolvePremiumRequest(request, "rejected") }, enabled = !busy) {
                                    Text("דחה", color = ThemeState.text)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (premiumHistory.isNotEmpty() || channelHistory.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("היסטוריית החלטות", color = ThemeState.subtext2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    premiumHistory.forEach { item ->
                        Text(
                            "${if (item.status == "approved") "✓ אושר" else "✕ נדחה"} · Premium · ${item.accountEmail} · ${item.requestedAt}",
                            color = if (item.status == "approved") Color(0xFF66BB6A) else Color(0xFFEF5350), fontSize = 11.sp,
                        )
                    }
                    channelHistory.forEach { item ->
                        Text(
                            "${if (item.status == "approved") "✓ אושר" else "✕ נדחה"} · ערוץ ${item.name} · ${item.requestedAt}",
                            color = if (item.status == "approved") Color(0xFF66BB6A) else Color(0xFFEF5350), fontSize = 11.sp,
                        )
                    }
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
                            Text(
                                when (r.gender.lowercase()) {
                                    "male" -> "זכר"
                                    "female" -> "נקבה"
                                    else -> "הכל"
                                },
                                color = ThemeState.subtext, fontSize = 11.sp,
                            )
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
                    Text("קטגוריה", color = ThemeState.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
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
                    Text("מגדר", color = ThemeState.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf("all" to "הכל", "male" to "זכר", "female" to "נקבה").forEach { (key, label) ->
                            val selected = newGender == key
                            Box(
                                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) Color(0xFFFF0000) else ThemeState.divider)
                                    .clickable { newGender = key }
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
                        Text(
                            when (channel.gender.lowercase()) {
                                "male" -> "זכר"
                                "female" -> "נקבה"
                                else -> "הכל"
                            },
                            color = ThemeState.subtext2, fontSize = 11.sp,
                        )
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

private fun postAdminNotification(context: android.content.Context, count: Int) {
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                "admin_requests", "בקשות מנהל", android.app.NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
    val notification = if (android.os.Build.VERSION.SDK_INT >= 26) {
        android.app.Notification.Builder(context, "admin_requests")
    } else {
        @Suppress("DEPRECATION")
        android.app.Notification.Builder(context)
    }.setSmallIcon(com.filtertube.app.R.drawable.ic_launcher_foreground)
        .setContentTitle("בקשות חדשות ממתינות")
        .setContentText("$count בקשות Premium ממתינות לאישור")
        .setAutoCancel(true)
        .build()
    runCatching {
        context.getSystemService(android.app.NotificationManager::class.java).notify(7001, notification)
    }
}

@Composable
private fun DashboardStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(ThemeState.card).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value.toString(), color = ThemeState.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ThemeState.subtext, fontSize = 10.sp)
    }
}

private fun formatDashboardDate(value: String): String =
    value.takeIf { it.isNotBlank() }?.replace("T", " ")?.take(16) ?: "—"
