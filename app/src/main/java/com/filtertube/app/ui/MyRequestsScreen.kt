package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.ChannelRequests
import com.filtertube.app.data.categoryLabels

/**
 * "הבקשות שלי" — מה שלחתי, ומה קרה לזה.
 *
 * ## למה זה נדרש
 * הלקוח שלח בקשה, קיבל "הבקשה נשלחה", ומשם — כלום. הוא לא ידע אם היא
 * ממתינה, אושרה או נדחתה, ולכן הדרך היחידה לברר הייתה לשלוח שוב. זה גם מה
 * שהסתיר את הבאג האמיתי: בקשות *נדרסו* זו את זו בשרת, ובלי המסך הזה אי
 * אפשר היה לראות שהן נעלמות.
 */
@Composable
fun MyRequestsScreen(onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var requests by remember { mutableStateOf<List<ChannelRequests.MyReq>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        requests = ChannelRequests.listMine()
        loading = false
    }

    val pending = requests.count { it.status == "pending" }
    val approved = requests.count { it.status == "approved" }
    val rejected = requests.count { it.status == "rejected" }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("הבקשות שלי (${requests.size})", onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ThemeState.accent)
            }

            requests.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "עוד לא שלחת בקשות.\nערוץ שאינו מאושר מופיע באפור בספרייה — לחיצה עליו שולחת בקשה.",
                    color = ThemeState.subtext, fontSize = 14.sp, lineHeight = 21.sp,
                    modifier = Modifier.padding(32.dp),
                )
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryPill("ממתין", pending, ThemeState.subtext2, Modifier.weight(1f))
                    SummaryPill("אושר", approved, APPROVED_GREEN, Modifier.weight(1f))
                    SummaryPill("נדחה", rejected, REJECTED_RED, Modifier.weight(1f))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                ) {
                    items(requests, key = { it.id }) { req -> RequestRow(req) }
                }
            }
        }
    }
}

private val APPROVED_GREEN = Color(0xFF3FBF6F)
private val REJECTED_RED = Color(0xFFE05A5A)

@Composable
private fun SummaryPill(label: String, count: Int, tint: Color, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(ThemeState.card)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", color = tint, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ThemeState.subtext2, fontSize = 11.5.sp)
    }
}

@Composable
private fun RequestRow(req: ChannelRequests.MyReq) {
    val tint = when (req.status) {
        "approved" -> APPROVED_GREEN
        "rejected" -> REJECTED_RED
        else -> ThemeState.subtext2
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                req.name.ifBlank { req.url }, color = ThemeState.text, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val category = categoryLabels[req.category] ?: req.category
            val when_ = req.requestedAt.take(10)
            Text(
                listOf(category, when_).filter { it.isNotBlank() }.joinToString(" · "),
                color = ThemeState.subtext2, fontSize = 11.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(tint.copy(alpha = 0.16f))
                .padding(horizontal = 11.dp, vertical = 5.dp),
        ) {
            Text(req.statusHe, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
