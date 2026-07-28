package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.FirebaseAccount
import com.filtertube.app.data.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun FirebaseAccountScreen(onDone: (needsProfile: Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(settings.userEmail) }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var messageSuccess by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val pendingUser = remember { FirebaseAuth.getInstance().currentUser }
    var verificationEmail by remember {
        mutableStateOf(
            pendingUser
                ?.takeIf { !it.isEmailVerified || settings.cloudUid != it.uid }
                ?.email,
        )
    }

    if (verificationEmail != null) {
        EmailVerificationScreen(
            email = verificationEmail.orEmpty(),
            message = message,
            messageSuccess = messageSuccess,
            busy = loading,
            passcode = password,
            onPasscodeChange = {
                password = it
                message = ""
                messageSuccess = false
            },
            onResend = {
                loading = true
                scope.launch {
                    val result = FirebaseAccount.resendVerificationEmail()
                    loading = false
                    message = result.message
                    messageSuccess = result.ok
                }
            },
            onCheck = {
                if (password.length < 4) {
                    message = "הזן את הקוד לחשבון ולהורים (לפחות 4 תווים)."
                    messageSuccess = false
                } else {
                    loading = true
                    scope.launch {
                        val result = FirebaseAccount.checkEmailVerification(settings, password)
                        loading = false
                        message = result.message
                        messageSuccess = result.ok
                        if (result.ok) {
                            onDone(result.created)
                        } else {
                            verificationEmail = result.email ?: verificationEmail
                        }
                    }
                }
            },
            onChangeEmail = {
                FirebaseAccount.abandonPendingAuthentication()
                verificationEmail = null
                password = ""
                message = ""
                messageSuccess = false
            },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ThemeState.bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("FilterTube", color = ThemeState.accent, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Text("התחברות או יצירת חשבון", color = ThemeState.text, fontSize = 21.sp)
        Spacer(Modifier.height(8.dp))
        Text("הנתונים והזכאות ל־Premium נשמרים בחשבון המאובטח שלך.", color = ThemeState.subtext2, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "כאן מגדירים קוד אחד לחשבון ולהורים, של לפחות 4 תווים. אותו קוד יגן גם על ההגדרות הרגישות במכשיר וגם על החשבון שלך.",
            color = ThemeState.subtext2,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("אימייל") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("קוד החשבון וההורים (לפחות 4 תווים)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = {
                loading = true
                scope.launch {
                    val result = FirebaseAccount.sendPasswordReset(email)
                    loading = false
                    message = result.message
                    messageSuccess = result.ok
                }
            },
            enabled = !loading,
        ) { Text("שכחתי קוד חשבון", color = ThemeState.accent) }
        if (message.isNotBlank()) {
            Text(
                message,
                color = if (messageSuccess) Color(0xFF22C55E) else Color(0xFFEF4444),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                loading = true
                message = ""
                scope.launch {
                    val result = FirebaseAccount.signInOrRegister(email, password, settings)
                    loading = false
                    message = result.message
                    messageSuccess = result.ok
                    if (result.ok) onDone(result.created)
                    else if (result.verificationPending) {
                        verificationEmail = result.email ?: email.trim()
                    }
                }
            },
            enabled = !loading && email.isNotBlank() && password.length >= 4,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
        ) { Text(if (loading) "מתחבר…" else "התחבר / צור חשבון") }
    }
}

@Composable
fun EmailVerificationScreen(
    email: String,
    message: String,
    messageSuccess: Boolean,
    busy: Boolean,
    passcode: String? = null,
    onPasscodeChange: ((String) -> Unit)? = null,
    onResend: () -> Unit,
    onCheck: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(ThemeState.bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("אימות כתובת המייל", color = ThemeState.text, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "השלמת אימות החשבון עבור:",
            color = ThemeState.subtext2,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(email, color = ThemeState.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "אם עדיין לא אימתת, פתח את הקישור שנשלח למייל. אם כבר אימתת, לחץ על הכפתור למטה. רק לאחר האימות יהיה אפשר להיכנס ולסנכרן.",
            color = ThemeState.subtext2,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "החשבון כבר נוצר, גם אם הודעת האימות עדיין לא הגיעה. אין צורך ליצור חשבון נוסף.",
            color = ThemeState.subtext2,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        if (onPasscodeChange != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                "כדי לסיים את האימות, הזן שוב את הקוד האחיד לחשבון ולהורים.",
                color = ThemeState.subtext2,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = passcode.orEmpty(),
                onValueChange = onPasscodeChange,
                label = { Text("קוד החשבון וההורים (לפחות 4 תווים)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                message,
                color = if (messageSuccess) Color(0xFF22C55E) else Color(0xFFEF4444),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onCheck,
            enabled = !busy && (onPasscodeChange == null || passcode.orEmpty().length >= 4),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
        ) {
            Text(if (busy) "בודק…" else "בדקתי ואפשר להמשיך")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onResend,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("שלח שוב") }
        TextButton(onClick = onChangeEmail, enabled = !busy) {
            Text("יציאה / החלפת מייל", color = ThemeState.subtext2)
        }
    }
}
