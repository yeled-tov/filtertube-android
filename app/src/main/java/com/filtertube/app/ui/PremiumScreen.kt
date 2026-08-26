package com.filtertube.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.filtertube.app.ThemeState
import com.filtertube.app.data.FirebaseBilling
import com.filtertube.app.data.ManualPremiumRequests
import com.filtertube.app.data.SettingsStore
import kotlinx.coroutines.launch

/** Premium checkout backed by Firebase Functions and Creem Checkout. */
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf("year") }
    var paidActive by remember { mutableStateOf(settings.premiumServerActive) }
    var canManage by remember { mutableStateOf(settings.premiumCanManage) }
    var status by remember { mutableStateOf("בודק את מצב המנוי…") }
    var entitlementReady by remember { mutableStateOf(settings.premiumActive) }
    var loading by remember { mutableStateOf(false) }
    var showPaymentForm by remember { mutableStateOf(false) }

    fun refreshBilling() {
        scope.launch {
            val result = FirebaseBilling.refresh(settings)
            paidActive = settings.premiumServerActive
            canManage = settings.premiumCanManage
            status = result.message
            entitlementReady = result.ok
        }
    }

    LaunchedEffect(Unit) {
        refreshBilling()
    }
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshBilling()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("FilterTube Premium", onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 28.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(ThemeState.accentColors))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    !entitlementReady -> "מכין את הזכאות שלך…"
                    paidActive -> "אתה מנוי Premium 🎉"
                    settings.premiumActive -> "נותרו ${settings.trialDaysLeft} ימי ניסיון חינם"
                    else -> "FilterTube Premium"
                },
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
            Perk(Icons.Default.Download, "הורדות לצפייה ללא חיבור")
            Perk(Icons.Default.MusicNote, "ניגון ברקע ובמסך כבוי")
            Perk(Icons.Default.PictureInPictureAlt, "חלון צף (Picture-in-Picture)")

            if (!paidActive) {
                Spacer(Modifier.height(24.dp))
                PlanCard(
                    title = "שנתי", price = "$22.89", per = "לשנה",
                    note = "החיסכון הטוב ביותר", best = true, selected = plan == "year",
                ) { plan = "year" }
                Spacer(Modifier.height(11.dp))
                PlanCard(
                    title = "חודשי", price = "$3.27", per = "לחודש",
                    note = "ניתן לבטל בכל עת", best = false, selected = plan == "month",
                ) { plan = "month" }

                Spacer(Modifier.height(18.dp))
                Text(
                    "התשלום מוסדר ידנית מול צוות FilterTube. לאחר התשלום נפעיל את המנוי בחשבון שלך.",
                    color = ThemeState.subtext2, fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(status, color = if (paidActive) Color(0xFF22C55E) else ThemeState.subtext2, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showPaymentForm = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
                    enabled = !loading,
                ) {
                    Text(
                        if (plan == "year") "פנייה להסדרת תשלום · $22.89/שנה" else "פנייה להסדרת תשלום · $3.27/חודש",
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        loading = true
                        status = "בודק את מצב המנוי…"
                        scope.launch {
                            val result = FirebaseBilling.refresh(settings)
                            loading = false
                            status = result.message
                            paidActive = settings.premiumServerActive
                            canManage = settings.premiumCanManage
                            entitlementReady = result.ok
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = !loading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeState.accent),
                ) { Text("רענן מצב מנוי") }
                if (canManage) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            loading = true
                            scope.launch {
                                val result = FirebaseBilling.createCustomerPortal()
                                loading = false
                                status = result.message
                                result.url?.let { FirebaseBilling.openBrowser(context, it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        enabled = !loading,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeState.accent),
                    ) { Text("ניהול או ביטול המנוי") }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lock, null, tint = ThemeState.subtext2, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "לא שולחים פרטי אשראי במייל · התשלום מוסדר מולנו ישירות",
                        color = ThemeState.subtext2, fontSize = 11.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            if (paidActive) {
                Spacer(Modifier.height(22.dp))
                Text(status, color = Color(0xFF22C55E), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        loading = true
                        scope.launch {
                            val result = FirebaseBilling.refresh(settings)
                            loading = false
                            status = result.message
                            paidActive = settings.premiumServerActive
                            canManage = settings.premiumCanManage
                            entitlementReady = result.ok
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = !loading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeState.accent),
                ) { Text("רענן מצב מנוי") }
                if (canManage) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            loading = true
                            scope.launch {
                                val result = FirebaseBilling.createCustomerPortal()
                                loading = false
                                status = result.message
                                result.url?.let { FirebaseBilling.openBrowser(context, it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        enabled = !loading,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ThemeState.accent),
                    ) { Text("ניהול או ביטול המנוי") }
                }
            }
        }
    }

    if (showPaymentForm) {
        ManualPaymentDialog(
            plan = plan,
            loading = loading,
            onDismiss = { if (!loading) showPaymentForm = false },
            onSubmit = { name, phone, email ->
                loading = true
                scope.launch {
                    val result = ManualPremiumRequests.submit(name, phone, email, plan)
                    val price = if (plan == "year") "$22.89" else "$3.27"
                    val period = if (plan == "year") "שנתי" else "חודשי"
                    val subject = "בקשה להצטרפות ל-FilterTube Premium ($period)"
                    val body = """
                        שלום,

                        אני פונה בנושא FilterTube Premium במסלול $period ($price).
                        שם: ${name.trim()}
                        טלפון: ${phone.trim()}
                        מייל ליצירת קשר: ${email.trim()}
                        מייל החשבון באפליקציה: ${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email.orEmpty()}

                        אשמח להסדיר את התשלום ולקבל אישור Premium.
                    """.trimIndent()
                    val mailto = Uri.Builder().scheme("mailto").path(ManualPremiumRequests.SUPPORT_EMAIL)
                        .appendQueryParameter("subject", subject)
                        .appendQueryParameter("body", body)
                        .build()
                    try {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, mailto))
                        status = if (result.ok) "הבקשה נשמרה ונפתחה הודעת מייל מוכנה" else "נפתחה הודעת מייל; ${result.message}"
                    } catch (_: ActivityNotFoundException) {
                        status = "אין אפליקציית מייל מותקנת במכשיר"
                    }
                    loading = false
                    showPaymentForm = false
                }
            },
        )
    }
}

@Composable
private fun ManualPaymentDialog(
    plan: String,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember {
        mutableStateOf(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email.orEmpty())
    }
    val valid = name.trim().isNotEmpty() && phone.trim().isNotEmpty() &&
        android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פרטי פנייה לתשלום", color = ThemeState.text) },
        text = {
            Column {
                Text(
                    if (plan == "year") "מסלול שנתי · $22.89" else "מסלול חודשי · $3.27",
                    color = ThemeState.subtext2, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("שם מלא") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it }, label = { Text("מספר טלפון") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("מייל ליצירת קשר") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(name, phone, email) }, enabled = valid && !loading) {
                Text(if (loading) "שולח…" else "שלח פנייה")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("ביטול") } },
        containerColor = ThemeState.card,
    )
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
