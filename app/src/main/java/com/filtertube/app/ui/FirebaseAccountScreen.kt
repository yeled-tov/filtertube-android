package com.filtertube.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.CloudSync
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.FirebaseAccount
import com.filtertube.app.data.GoogleAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.filtertube.app.data.SettingsStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AccountEntryMode {
    WELCOME,
    SIGN_IN,
    REGISTER,
    VERIFY,

    /**
     * הגדרת הקוד ההורי אחרי כניסה עם גוגל.
     *
     * גוגל מאמתת מי המשתמש, אבל לא מספקת קוד. הקוד כאן מגן על רמות הסינון
     * ועל Shorts — לא על החשבון — ולכן הוא נדרש גם כשהזהות כבר ודאית.
     */
    SET_CODE,
}

/**
 * The account gate is intentionally completed before profile onboarding.
 * Creating an account and signing in are explicit, separate operations.
 */
@Composable
fun FirebaseAccountScreen(onDone: (needsProfile: Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val pendingUser = auth.currentUser
    val requiresSessionCompletion =
        pendingUser != null && (!pendingUser.isEmailVerified || settings.cloudUid != pendingUser.uid)

    var mode by rememberSaveable {
        mutableStateOf(
            if (requiresSessionCompletion) AccountEntryMode.VERIFY
            else AccountEntryMode.WELCOME,
        )
    }
    var email by rememberSaveable {
        mutableStateOf(pendingUser?.email ?: settings.userEmail)
    }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var verificationEmail by rememberSaveable {
        mutableStateOf(pendingUser?.email.orEmpty())
    }
    var loading by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var messageSuccess by rememberSaveable { mutableStateOf(false) }

    fun clearMessage() {
        message = ""
        messageSuccess = false
    }

    fun openMode(next: AccountEntryMode) {
        clearMessage()
        password = ""
        confirmPassword = ""
        mode = next
    }

    suspend fun finishSuccessfulAuthentication(result: FirebaseAccount.Result) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified || settings.cloudUid != user.uid) {
            message = "החשבון אומת, אך החיבור לא הושלם. לחץ שוב כדי לנסות."
            messageSuccess = false
            return
        }

        if (!result.created) {
            message = "החשבון מחובר. משחזרים את הפרטים שלך…"
            messageSuccess = true
            val restored = try {
                CloudSync.pullCloudData(context, settings, user.uid)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (!restored) {
                message = "החשבון מחובר, אך שחזור הנתונים לא הושלם. בדוק את האינטרנט ונסה שוב."
                messageSuccess = false
                return
            }
        }

        onDone(result.created || !settings.onboardingDone)
    }

    // ── כניסה/הרשמה בלחיצה אחת דרך גוגל ──────────────────────────────────
    // אותה פעולה בדיוק לשני המצבים: אם אין עדיין חשבון FilterTube למייל
    // הזה, Firebase יוצר אותו; אם יש, הוא מתחבר אליו. המשתמש לא צריך לדעת
    // מראש באיזה מהמקרים הוא נמצא — וזו כל הנקודה.
    //
    // המייל מגיע מאומת מגוגל, ולכן אין כאן שלב אימות מייל בכלל.
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        scope.launch {
            loading = true
            try {
                Diagnostics.log("AUTH גוגל: חזרה מבחירת חשבון")
                val acct = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
                    .getResult(ApiException::class.java)
                Diagnostics.log(
                    "AUTH גוגל: חשבון ${acct.email.orEmpty().take(3)}… · " +
                        "idToken=${if (acct.idToken.isNullOrBlank()) "חסר" else "התקבל"}",
                )

                val signedIn = GoogleAuth.signInToFirebase(acct)
                if (signedIn.isFailure) {
                    GoogleAuth.signOut(context)
                    message = signedIn.exceptionOrNull()?.message ?: "ההתחברות דרך גוגל נכשלה"
                    messageSuccess = false
                    return@launch
                }
                val user = auth.currentUser
                if (user == null) {
                    Diagnostics.log("AUTH גוגל: signInWithCredential הצליח אבל אין currentUser")
                    message = "ההתחברות לא הושלמה. נסה שוב."
                    messageSuccess = false
                    return@launch
                }

                // מקשר את ההרשאה ליוטיוב לחשבון שזה עתה נוצר/אותר, כדי
                // שהלייקים, המנויים והמוזיקה יימשכו מיד ולא בהתחברות נפרדת.
                GoogleAuth.bindToCurrentFirebaseAccount(context, acct)
                settings.cloudUid = user.uid
                settings.cloudEmail = user.email.orEmpty()
                user.email?.takeIf { it.isNotBlank() }?.let { settings.userEmail = it }

                val restored = runCatching { CloudSync.pullCloudData(context, settings, user.uid) }
                    .getOrDefault(false)
                Diagnostics.log("AUTH גוגל: התחברות הושלמה · שחזור מהענן=$restored")

                // ── הקוד ההורי ────────────────────────────────────────────
                // המסלול של מייל וקוד מגדיר אותו תוך כדי. גוגל לא מספקת קוד,
                // ובלעדיו רמות הסינון ו-Shorts היו נשארים פתוחים לכל אחד
                // שמחזיק את המכשיר — כלומר החיבור המהיר היה מבטל בשקט את
                // ההגנה שהוא אמור לשמור עליה.
                //
                // אם החשבון כבר קיים והקוד שוחזר מהענן, אין מה לשאול.
                if (!settings.hasFilterPassword) {
                    Diagnostics.log("AUTH גוגל: אין קוד הורי — מבקשים להגדיר")
                    message = ""
                    mode = AccountEntryMode.SET_CODE
                    return@launch
                }

                message = if (restored) "מחובר. משחזרים את הפרטים שלך…" else "החשבון מחובר ✓"
                messageSuccess = true
                onDone(!settings.onboardingDone)
            } catch (error: CancellationException) {
                throw error
            } catch (e: ApiException) {
                // 10 = DEVELOPER_ERROR: טביעת האצבע של החתימה לא רשומה
                // ב-Firebase. זו השגיאה היחידה כאן שהמשתמש לא יכול לפתור,
                // ולכן היא מקבלת הסבר משלה במקום "נסה שוב".
                Diagnostics.log("AUTH גוגל: ApiException ${e.statusCode}")
                message = if (e.statusCode == 10) {
                    "ההתחברות עם גוגל לא מוגדרת במלואה בפרויקט (טביעת אצבע חסרה)."
                } else {
                    "ההתחברות דרך גוגל נכשלה (${e.statusCode})"
                }
                messageSuccess = false
            } catch (_: Exception) {
                message = "משהו השתבש בחיבור לגוגל. נסה שוב."
                messageSuccess = false
            } finally {
                loading = false
            }
        }
    }

    fun startGoogle() {
        if (loading) return
        clearMessage()
        if (!GoogleAuth.unifiedSignInAvailable(context)) {
            Diagnostics.log("AUTH גוגל: אין מזהה לקוח Web — החיבור המאוחד כבוי")
            message = "ההתחברות עם גוגל אינה מוגדרת בגרסה הזו."
            messageSuccess = false
            return
        }
        Diagnostics.log("AUTH גוגל: פותח בחירת חשבון")
        googleLauncher.launch(GoogleAuth.client(context).signInIntent)
    }

    fun submitAccount() {
        if (loading) return
        clearMessage()
        val normalizedEmail = email.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            message = "הזן כתובת אימייל תקינה."
            return
        }
        if (password.length < 4) {
            message = "הקוד חייב להכיל לפחות 4 תווים."
            return
        }
        if (mode == AccountEntryMode.REGISTER && password != confirmPassword) {
            message = "הקודים אינם תואמים."
            return
        }

        loading = true
        scope.launch {
            try {
                val result = when (mode) {
                    AccountEntryMode.SIGN_IN ->
                        FirebaseAccount.signIn(normalizedEmail, password, settings)
                    AccountEntryMode.REGISTER ->
                        FirebaseAccount.register(normalizedEmail, password, settings)
                    else -> return@launch
                }
                message = result.message
                messageSuccess = result.ok
                if (result.ok) {
                    finishSuccessfulAuthentication(result)
                } else if (result.verificationPending) {
                    verificationEmail = result.email ?: normalizedEmail
                    mode = AccountEntryMode.VERIFY
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "משהו השתבש בחיבור. הפרטים נשמרו — אפשר לנסות שוב."
                messageSuccess = false
            } finally {
                loading = false
            }
        }
    }

    if (mode == AccountEntryMode.VERIFY) {
        EmailVerificationScreen(
            email = verificationEmail.ifBlank { auth.currentUser?.email.orEmpty() },
            emailVerified = auth.currentUser?.isEmailVerified == true,
            message = message,
            messageSuccess = messageSuccess,
            busy = loading,
            passcode = password,
            onPasscodeChange = {
                password = it
                clearMessage()
            },
            onResend = {
                if (!loading) {
                    loading = true
                    scope.launch {
                        try {
                            val result = FirebaseAccount.resendVerificationEmail()
                            message = result.message
                            messageSuccess = result.ok
                        } finally {
                            loading = false
                        }
                    }
                }
            },
            onCheck = {
                if (password.length < 4) {
                    message = "הזן את הקוד לחשבון ולהורים, לפחות 4 תווים."
                    messageSuccess = false
                } else if (!loading) {
                    loading = true
                    scope.launch {
                        try {
                            val result = FirebaseAccount.checkEmailVerification(settings, password)
                            message = result.message
                            messageSuccess = result.ok
                            verificationEmail = result.email ?: verificationEmail
                            if (result.ok) finishSuccessfulAuthentication(result)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            message = "לא ניתן להשלים כרגע את החיבור. נסה שוב."
                            messageSuccess = false
                        } finally {
                            loading = false
                        }
                    }
                }
            },
            onChangeEmail = {
                FirebaseAccount.abandonPendingAuthentication()
                email = ""
                password = ""
                confirmPassword = ""
                verificationEmail = ""
                openMode(AccountEntryMode.WELCOME)
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeState.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .background(
                    Brush.linearGradient(ThemeState.accentColors),
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "FilterTube",
            color = ThemeState.accent,
            fontSize = 31.sp,
            fontWeight = FontWeight.ExtraBold,
        )

        when (mode) {
            AccountEntryMode.WELCOME -> {
                // ההודעה מוצגת גם כאן. קודם היא נכתבה רק בתוך ענף המייל/קוד,
                // ולכן כישלון בהתחברות עם גוגל — כולל "טביעת אצבע חסרה" —
                // נכתב למשתנה ולא הוצג לאף אחד. מבחינת המשתמש: בחרתי חשבון
                // ולא קרה כלום.
                AccountMessage(message, messageSuccess)
                if (message.isNotBlank()) Spacer(Modifier.height(10.dp))
                AccountWelcome(
                    onSignIn = { openMode(AccountEntryMode.SIGN_IN) },
                    onRegister = { openMode(AccountEntryMode.REGISTER) },
                    onGoogle = ::startGoogle,
                    googleAvailable = GoogleAuth.unifiedSignInAvailable(context),
                    loading = loading,
                )
            }
            AccountEntryMode.SIGN_IN,
            AccountEntryMode.REGISTER -> {
                val registering = mode == AccountEntryMode.REGISTER
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { openMode(AccountEntryMode.WELCOME) }, enabled = !loading) {
                        Icon(Icons.Default.ArrowBack, "חזרה", tint = ThemeState.text)
                    }
                    Text(
                        if (registering) "יצירת חשבון חדש" else "כניסה לחשבון",
                        color = ThemeState.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    if (registering) {
                        "החשבון ישמור את הפרופיל, ההיסטוריה, החיפושים, ההורדות וה־Premium שלך."
                    } else {
                        "התחבר עם האימייל והקוד שכבר הגדרת."
                    },
                    color = ThemeState.subtext2,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                AccountField(
                    value = email,
                    onValueChange = {
                        email = it
                        clearMessage()
                    },
                    label = "כתובת אימייל",
                    icon = { Icon(Icons.Default.Email, null) },
                    keyboardType = KeyboardType.Email,
                )
                Spacer(Modifier.height(12.dp))
                AccountField(
                    value = password,
                    onValueChange = {
                        password = it
                        clearMessage()
                    },
                    label = "קוד לחשבון ולהורים (4 תווים ומעלה)",
                    icon = { Icon(Icons.Default.Lock, null) },
                    keyboardType = KeyboardType.Password,
                    password = true,
                )
                if (registering) {
                    Spacer(Modifier.height(12.dp))
                    AccountField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            clearMessage()
                        },
                        label = "אישור הקוד",
                        icon = { Icon(Icons.Default.Lock, null) },
                        keyboardType = KeyboardType.Password,
                        password = true,
                    )
                    if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                        Spacer(Modifier.height(6.dp))
                        Text("הקודים אינם תואמים", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                } else {
                    TextButton(
                        onClick = {
                            if (loading) return@TextButton
                            loading = true
                            scope.launch {
                                try {
                                    val result = FirebaseAccount.sendPasswordReset(email)
                                    message = result.message
                                    messageSuccess = result.ok
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("שכחתי את הקוד", color = ThemeState.accent)
                    }
                }

                AccountMessage(message, messageSuccess)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = ::submitAccount,
                    enabled = !loading &&
                        email.isNotBlank() &&
                        password.length >= 4 &&
                        (!registering || (confirmPassword == password && confirmPassword.length >= 4)),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (registering) Icons.Default.PersonAdd else Icons.Default.Lock,
                            null,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (registering) "יצירת החשבון" else "כניסה",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            AccountEntryMode.SET_CODE -> {
                Spacer(Modifier.height(16.dp))
                Text(
                    "בחר קוד הורים",
                    color = ThemeState.text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "הקוד הזה מגן על רמת הסינון ועל Shorts — לא על החשבון. " +
                        "גוגל כבר אימתה מי אתה, אבל בלי קוד כל מי שמחזיק את " +
                        "המכשיר יכול לשנות את רמת הסינון.",
                    color = ThemeState.subtext2, fontSize = 13.sp, lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                AccountField(
                    value = password,
                    onValueChange = { password = it },
                    label = "קוד (4 תווים לפחות)",
                    icon = { Icon(Icons.Default.Lock, null, tint = ThemeState.subtext2) },
                    keyboardType = KeyboardType.Password,
                    password = true,
                )
                Spacer(Modifier.height(10.dp))
                AccountField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "אישור הקוד",
                    icon = { Icon(Icons.Default.Lock, null, tint = ThemeState.subtext2) },
                    keyboardType = KeyboardType.Password,
                    password = true,
                )
                AccountMessage(message, messageSuccess)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (password.length < 4) {
                            message = "הקוד חייב להכיל לפחות 4 תווים."
                            messageSuccess = false
                        } else if (password != confirmPassword) {
                            message = "הקודים אינם תואמים."
                            messageSuccess = false
                        } else {
                            settings.setFilterPassword(password)
                            Diagnostics.log("AUTH גוגל: קוד הורי נקבע")
                            onDone(!settings.onboardingDone)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
                ) { Text("שמור והמשך", fontWeight = FontWeight.Bold) }
            }
            AccountEntryMode.VERIFY -> Unit
        }
    }
}

@Composable
private fun AccountWelcome(
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onGoogle: () -> Unit = {},
    googleAvailable: Boolean = false,
    loading: Boolean = false,
) {
    Spacer(Modifier.height(14.dp))
    Text(
        "החשבון שלך. הסינון שלך. הכול נשמר.",
        color = ThemeState.text,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "כדי להתחיל, בחר אם יש לך כבר חשבון או שזו הפעם הראשונה שלך.",
        color = ThemeState.subtext2,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))

    // ── גוגל קודם ─────────────────────────────────────────────────────────
    // זו הדרך המהירה ביותר, והיחידה שמביאה איתה גם את הלייקים, המנויים
    // והמוזיקה שאהבת. היא לא מחליפה את המייל והקוד — הקוד ההורי נשאר בכל
    // מקרה — אלא חוסכת את שלב אימות המייל, כי גוגל כבר אימתה אותו.
    if (googleAvailable) {
        Button(
            onClick = onGoogle,
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF1F1F1F),
                disabledContainerColor = ThemeState.card,
            ),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = ThemeState.accent,
                )
            } else {
                Text("G", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4))
                Spacer(Modifier.width(10.dp))
                Text("המשך עם גוגל", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "נכנס לחשבון קיים או יוצר חדש — ומביא מיד את הלייקים, המנויים " +
                "והמוזיקה שאהבת ביוטיוב.",
            color = ThemeState.subtext,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(ThemeState.divider))
            Text("או", color = ThemeState.subtext, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(ThemeState.divider))
        }
        Spacer(Modifier.height(18.dp))
    }

    Button(
        onClick = onSignIn,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
    ) {
        Text("יש לי חשבון — כניסה", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onRegister,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ThemeState.accent),
    ) {
        Text("אין לי חשבון — יצירת חשבון", color = ThemeState.accent, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(22.dp))
    Text(
        "קוד אחד, של 4 תווים ומעלה, משמש לכניסה לחשבון וגם להגנת ההורים. הקוד עצמו אינו נשמר כטקסט במכשיר.",
        color = ThemeState.subtext2,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: @Composable () -> Unit,
    keyboardType: KeyboardType,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = icon,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation =
            if (password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ThemeState.accent,
            focusedLabelColor = ThemeState.accent,
            cursorColor = ThemeState.accent,
            focusedTextColor = ThemeState.text,
            unfocusedTextColor = ThemeState.text,
        ),
    )
}

@Composable
private fun AccountMessage(message: String, success: Boolean) {
    if (message.isBlank()) return
    Spacer(Modifier.height(10.dp))
    Text(
        message,
        color = if (success) Color(0xFF22C55E) else Color(0xFFEF4444),
        fontSize = 12.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun EmailVerificationScreen(
    email: String,
    emailVerified: Boolean = false,
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
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeState.bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    Brush.linearGradient(ThemeState.accentColors),
                    RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Email, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (emailVerified) "השלמת הכניסה" else "אימות כתובת המייל",
            color = ThemeState.text,
            fontSize = 23.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (emailVerified) "המייל אומת. נשאר רק לחבר את החשבון למכשיר."
            else "שלחנו קישור אימות לכתובת:",
            color = ThemeState.subtext2,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(email, color = ThemeState.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            if (emailVerified) {
                "הזן את הקוד שבחרת ולחץ על „המשך”."
            } else {
                "פתח את הקישור במייל, חזור לכאן והזן שוב את הקוד שבחרת. כדאי לבדוק גם בתיקיית הספאם."
            },
            color = ThemeState.subtext2,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        if (onPasscodeChange != null) {
            Spacer(Modifier.height(18.dp))
            AccountField(
                value = passcode.orEmpty(),
                onValueChange = onPasscodeChange,
                label = "קוד החשבון וההורים",
                icon = { Icon(Icons.Default.Lock, null) },
                keyboardType = KeyboardType.Password,
                password = true,
            )
        }
        AccountMessage(message, messageSuccess)
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onCheck,
            enabled = !busy && (onPasscodeChange == null || passcode.orEmpty().length >= 4),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    if (emailVerified) "המשך לאפליקציה" else "אימתתי את המייל — המשך",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (!emailVerified) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onResend,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("שליחת קישור חדש")
            }
        }
        TextButton(onClick = onChangeEmail, enabled = !busy) {
            Text("שימוש במייל אחר", color = ThemeState.subtext2)
        }
    }
}
