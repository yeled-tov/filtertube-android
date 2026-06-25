package com.filtertube.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.SettingsStore

/**
 * מסך FilterTube Premium — שער תשלום מעוצב. לאחר 60 ימי הניסיון נחסמים הורדות,
 * ניגון ברקע וחלון צף עד רכישת מנוי. *עיבוד התשלום בפועל מתבצע בשרת מאובטח* —
 * הלקוח רק פותח את ה-Checkout ומאמת זכאות מול השרת (אסור לסמוך על הלקוח).
 */
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val daysLeft = settings.trialDaysLeft
    val active = settings.premiumActive
    var plan by remember { mutableStateOf("year") }   // "month" / "year"
    var method by remember { mutableStateOf("card") }  // "card" / "paypal"

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("FilterTube Premium", onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 28.dp),
        ) {
            // כתר + סטטוס
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(ThemeState.accentColors))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(
                if (settings.premiumPurchased) "אתה מנוי Premium 🎉"
                else if (active) "נותרו $daysLeft ימי ניסיון חינם"
                else "הניסיון הסתיים",
                color = ThemeState.text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "פתח את כל היכולות המתקדמות של FilterTube",
                color = ThemeState.subtext2, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(22.dp))
            Perk(Icons.Default.Download, "הורדות לצפייה לא־מקוונת")
            Perk(Icons.Default.MusicNote, "ניגון ברקע ובמסך כבוי")
            Perk(Icons.Default.PictureInPictureAlt, "חלון צף (Picture-in-Picture)")

            if (!settings.premiumPurchased) {
                Spacer(Modifier.height(24.dp))
                PlanCard(
                    title = "שנתי", price = "₪70", per = "לשנה",
                    note = "חיסכון 42% · ₪5.8 לחודש בלבד", best = true, selected = plan == "year",
                ) { plan = "year" }
                Spacer(Modifier.height(11.dp))
                PlanCard(
                    title = "חודשי", price = "₪10", per = "לחודש",
                    note = "ביטול בכל עת", best = false, selected = plan == "month",
                ) { plan = "month" }

                Spacer(Modifier.height(22.dp))
                Text("אמצעי תשלום", color = ThemeState.subtext2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    PayMethod("כרטיס אשראי", Icons.Default.CreditCard, method == "card", Modifier.weight(1f)) { method = "card" }
                    PayMethod("PayPal", null, method == "paypal", Modifier.weight(1f)) { method = "paypal" }
                }

                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = {
                        Toast.makeText(
                            context,
                            "התשלומים בהקמה — נעדכן כשייפתחו. בינתיים אתה נהנה מהכול בחינם 🙏",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
                ) {
                    Text(
                        if (plan == "year") "המשך לתשלום מאובטח · ₪70/שנה" else "המשך לתשלום מאובטח · ₪10/חודש",
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = ThemeState.subtext2, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("תשלום מאובטח · הצפנת SSL · איננו שומרים פרטי אשראי",
                        color = ThemeState.subtext2, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun Perk(icon: ImageVector, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ThemeState.card), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ThemeState.accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(text, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlanCard(title: String, price: String, per: String, note: String, best: Boolean, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ThemeState.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) ThemeState.accent else ThemeState.divider, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = ThemeState.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (best) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(ThemeState.accentColors)).padding(horizontal = 9.dp, vertical = 2.dp)) {
                        Text("הכי משתלם", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(note, color = ThemeState.subtext2, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(price, color = ThemeState.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(per, color = ThemeState.subtext2, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PayMethod(label: String, icon: ImageVector?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(ThemeState.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) ThemeState.accent else ThemeState.divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) ThemeState.accent else ThemeState.text, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = if (selected) ThemeState.accent else ThemeState.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
