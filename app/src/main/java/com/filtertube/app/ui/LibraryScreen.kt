package com.filtertube.app.ui
import com.filtertube.app.ThemeState

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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tv
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
import com.filtertube.app.data.AccountStore
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.GoogleAuth
import com.google.firebase.auth.FirebaseAuth
import com.filtertube.app.data.InnerTube
import com.filtertube.app.data.InnerTubeOAuth
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SubChannel
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeAccountRepository
import com.filtertube.app.data.YouTubeMusicApi
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

/**
 * מריץ שלב סנכרון אחד ומחזיר רשימה ריקה אם הוא נפל.
 *
 * קודם כל שלבי הסנכרון ישבו בתוך try אחד: הראשון שנפל ביטל את כל מה
 * שאחריו, המסך הראה "שגיאה בסנכרון" בלי לומר באיזה שלב, והיומן לא רשם
 * דבר — ולכן גם שורות OAUTH לא הופיעו בו מעולם. כאן כל מקור נכשל לבד,
 * ואומר ביומן את שמו ואת סוג התקלה.
 */
private suspend fun <T> syncStep(name: String, block: suspend () -> List<T>): List<T> =
    try {
        block()
    } catch (e: Exception) {
        Diagnostics.log("SYNC גוגל · $name: נכשל — ${e.javaClass.simpleName}: ${e.message}")
        emptyList()
    }

@Composable
fun LibraryScreen(
    onOpenCollection: (String) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenDeviceMedia: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(context) }
    val accountStore = remember { AccountStore(context) }

    var version by remember { mutableStateOf(0) }
    val likes = remember(version) { store.likes() }
    val downloads = remember(version) { store.downloads() }
    val playlists = remember(version) { store.playlists() }
    var ytLikes by remember { mutableStateOf(store.youtubeLikes()) }
    var subs by remember { mutableStateOf(store.subscriptions()) }
    var history by remember { mutableStateOf(store.history()) }
    var recs by remember { mutableStateOf(store.recommendations()) }
    val localHist = remember(version) { store.localHistory() }   // היסטוריית צפייה מקומית
    var channelCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        channelCount = runCatching {
            com.filtertube.app.data.ChannelsRepository.getCachedChannelsFast(context).size
        }.getOrDefault(0)
    }
    val loggedIn = accountStore.isLoggedIn   // מחושב מחדש בכל composition (מתעדכן בחזרה מהתחברות)

    var account by remember { mutableStateOf<GoogleSignInAccount?>(GoogleAuth.lastAccount(context)) }
    var status by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    // מסנכרן גם לייקים וגם מנויים מחשבון הגוגל
    fun syncAccount(acct: GoogleSignInAccount) {
        val a = acct.account ?: return
        val googleSession = GoogleAuth.session(context, acct) ?: run {
            account = null
            status = "יש להתחבר מחדש לחשבון YouTube"
            return
        }
        syncing = true; status = "מסנכרן מיוטיוב..."
        scope.launch {
            try {
                val token = GoogleAuth.accessToken(context, a, googleSession)
                Diagnostics.log("SYNC גוגל: אסימון התקבל (${token.length} תווים)")
                // רק תוכן מהערוצים המאושרים — לייק/מנוי שלא ברשימה הלבנה לא נשמר ולא מוצג
                val approved = ChannelsRepository.getChannels(context).map { it.youtubeChannelId }.toHashSet()

                // ── מיוזיק קודם, מאותה סיבה כמו בסנכרון הדפדפן ────────────
                // "מוזיקה שאהבתי" היא תת-קבוצה של אותו פלייליסט LL. מי שמושך
                // את שתיהן ושומר כל אחת במלואה מקבל את השירים גם ב-FilterTube
                // וגם ב-FilterMusic. כאן המיוזיק קובעת מה שיר, ומה שנשאר
                // ב"אהבתי" אחרי החיסור הוא הווידאו.
                //
                // fillOwners משלים את הערוץ שבאמת העלה: ביוטיוב מיוזיק הקישור
                // שמתחת לשיר מצביע על האמן, ולכן ההשוואה לרשימה הלבנה נכשלה
                // על כל שיר.
                val musicRaw = syncStep("מיוזיק") {
                    val fromBoth = (
                        YouTubeMusicApi.likedSongsRaw(token) + InnerTubeOAuth.likedMusic(token)
                        ).distinctBy { it.id }
                    // distinctBy לפני ההשלמה ולא אחריה: שני המקורות מחזירים
                    // את אותם שירים, והשלמת ערוץ היא בקשה לכל פריט.
                    InnerTube.fillOwners(fromBoth) { it.channelId !in approved }
                }
                val musicLiked = musicRaw.filter { it.channelId in approved }
                Diagnostics.log("SYNC גוגל · מיוזיק: ${musicRaw.size} התקבלו · ${musicLiked.size} מאושרים")
                if (!GoogleAuth.isSessionCurrent(context, googleSession)) return@launch
                if (musicLiked.isNotEmpty()) store.setMusicLikes(musicLiked)
                val musicIds = musicRaw.mapTo(HashSet()) { it.id }

                val likedRaw = syncStep("אהבתי") {
                    YouTubeAccountRepository.likedVideos(token)
                }
                val liked = likedRaw.filter { it.id !in musicIds && it.channelId in approved }
                Diagnostics.log("SYNC גוגל · אהבתי: ${likedRaw.size} התקבלו · ${liked.size} מאושרים")
                if (!GoogleAuth.isSessionCurrent(context, googleSession)) return@launch
                if (liked.isNotEmpty()) { store.setYoutubeLikes(liked); ytLikes = liked }

                val subList = syncStep("מנויים") {
                    YouTubeAccountRepository.subscriptions(token)
                }.filter { it.channelId in approved }
                Diagnostics.log("SYNC גוגל · מנויים: ${subList.size} מאושרים")
                if (!GoogleAuth.isSessionCurrent(context, googleSession)) return@launch
                if (subList.isNotEmpty()) { store.setSubscriptions(subList); subs = subList }

                // ── המסלול השלישי ─────────────────────────────────────────
                // אותו אסימון של החשבון שכבר במכשיר, מול השרת הפנימי של
                // יוטיוב — זה שכן מחזיק היסטוריה. אם הוא נענה, אפשר להביא
                // הכל בלי שהמשתמש יקליד סיסמה אף פעם.
                val oauthHistory = syncStep("היסטוריה") {
                    InnerTubeOAuth.history(token)
                }.filter { it.channelId in approved }
                if (oauthHistory.isNotEmpty()) {
                    store.setHistory(oauthHistory); history = oauthHistory
                }

                status = "סונכרנו ${liked.size} לייקים · ${musicLiked.size} שירים ממיוזיק · " +
                    "${subList.size} מנויים · ${oauthHistory.size} בהיסטוריה ✓"
            } catch (e: Exception) {
                Diagnostics.log("SYNC גוגל: ${e.javaClass.simpleName}: ${e.message}")
                status = "שגיאה בסנכרון: ${e.message}"
            } finally { syncing = false }
        }
    }

    /**
     * סנכרון מלא דרך העוגיות של הדפדפן — **הכול בפעולה אחת**.
     *
     * ## למה זה המסלול הראשי ולא גיבוי
     * ההתחברות עם גוגל תלויה בהגדרת OAuth בצד גוגל (לקוח אנדרואיד עם
     * טביעת האצבע של האפליקציה). כשההגדרה חסרה, Play Services מחזיר
     * DEVELOPER_ERROR ואי אפשר לעשות דבר בקוד כדי לעקוף את זה.
     *
     * המסלול הזה לא דורש שום הגדרה: הזדהות SAPISIDHASH מהעוגיות שהמשתמש
     * כבר נתן בהתחברות בדפדפן. הוא מביא היסטוריה, המלצות, לייקים, מנויים
     * ומוזיקה שאהבת — כלומר את כל מה שההתחברות עם גוגל הייתה אמורה להביא,
     * ועוד היסטוריה שהיא ממילא לא יכולה.
     */
    fun syncInnerTube() {
        if (!accountStore.isLoggedIn) return
        syncing = true; status = "מסנכרן את החשבון שלך..."
        scope.launch {
            try {
                val approved = ChannelsRepository.getChannels(context).map { it.youtubeChannelId }.toHashSet()
                // ערוץ שלא זוהה נפסל. באפליקציית רשימה לבנה "לא ידוע" אינו
                // "מותר", ו-InnerTube מחזירה לא מעט פריטים שלא הצליחה לחלץ
                // להם מזהה ערוץ — כלומר זו הדרך העיקרית שבה תוכן לא מאושר
                // יכול היה להגיע לספרייה ומשם לנגן.
                val hist = InnerTube.history(accountStore.cookies).filter { it.channelId in approved }
                store.setHistory(hist); history = hist
                val rec = InnerTube.recommendations(accountStore.cookies).filter { it.channelId in approved }
                store.setRecommendations(rec); recs = rec

                // ── לייקים, מנויים ומוזיקה — אותן עוגיות, אותה פעולה ──────
                // כל מקור מדווח שלוש מספרים: כמה הגיעו, כמה נפלו כי לא זוהה
                // להם ערוץ, וכמה נשארו אחרי הרשימה הלבנה. "התחבר והכל נשאר
                // ריק" יכול לנבוע מכל אחד מהשלושה, ובלי הפירוט אי אפשר לדעת
                // מאיזה.
                fun report(name: String, items: List<Video>): List<Video> {
                    val noChannel = items.count { it.channelId.isBlank() }
                    val kept = items.filter { it.channelId in approved }
                    Diagnostics.log(
                        "SYNC $name: ${items.size} התקבלו · $noChannel בלי מזהה ערוץ · " +
                            "${kept.size} מאושרים",
                    )
                    return kept
                }

                // ── מוזיקה קודם, ובכוונה ──────────────────────────────────
                // "מוזיקה שאהבתי" אינה רשימה נפרדת אצל יוטיוב אלא *תת-קבוצה*
                // של אותו פלייליסט LL: שיר שסימנת במיוזיק מופיע גם ב"אהבתי"
                // הרגיל. לכן מי שמושך את שתיהן בנפרד מקבל את אותם שירים
                // פעמיים — וזה בדיוק הערבוב שנראה במכשיר.
                //
                // הפתרון: המיוזיק היא הקובעת מה שיר. מה שהיא החזירה הולך
                // ל-FilterMusic, ומה שנשאר ב"אהבתי" אחרי החיסור הוא הווידאו
                // האמיתי של FilterTube. החלוקה נעשית לפי הסיווג של יוטיוב
                // עצמה, ולא לפי ניחוש שלנו.
                val musicRaw = InnerTube.fillOwners(
                    InnerTube.likedMusic(accountStore.cookies),
                ) { it.channelId !in approved }
                val music = report("מיוזיק", musicRaw)
                if (music.isNotEmpty()) store.setMusicLikes(music)
                val musicIds = musicRaw.mapTo(HashSet()) { it.id }

                val likedRaw = InnerTube.fillOwners(
                    InnerTube.likedVideos(accountStore.cookies),
                ) { it.channelId.isBlank() }
                val liked = report("אהבתי", likedRaw.filter { it.id !in musicIds })
                if (liked.isNotEmpty()) { store.setYoutubeLikes(liked); ytLikes = liked }

                val allSubs = InnerTube.subscriptions(accountStore.cookies)
                val subsFromCookies = allSubs.filter { it.first in approved }
                    .map { (id, name) -> SubChannel(id, name) }
                Diagnostics.log(
                    "SYNC מנויים: ${allSubs.size} התקבלו · ${subsFromCookies.size} מאושרים",
                )
                if (subsFromCookies.isNotEmpty()) {
                    store.setSubscriptions(subsFromCookies); subs = subsFromCookies
                }

                status = "סונכרן ✓ ${hist.size} בהיסטוריה · ${liked.size} לייקים · " +
                    "${music.size} שירים ממיוזיק · ${subsFromCookies.size} מנויים · ${rec.size} המלצות"
            } catch (e: Exception) {
                status = "שגיאה בסנכרון מלא: ${e.message}"
            } finally { syncing = false }
        }
    }

    // סנכרון אוטומטי כשמתחברים (loggedIn עובר ל-true בחזרה ממסך ההתחברות)
    LaunchedEffect(loggedIn) {
        // בעבר הסנכרון רץ רק כשההיסטוריה הייתה ריקה, ולכן מי שכבר היה לו
        // משהו לא קיבל לעולם את הלייקים והמנויים. ההתחברות עצמה היא
        // האירוע שמצדיק משיכה — לא מצב הספרייה.
        if (loggedIn) syncInnerTube()
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val acct = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            // ── התפקיד היחיד של הכרטיס הזה: למשוך נתונים מיוטיוב ─────────
            // ניסיתי לתלות כאן גם יצירת חשבון דרך גוגל, וזה הפך כפתור עובד
            // לכפתור שנכשל תמיד: אותה זרימה דרשה idToken, ו-idToken דורש
            // לקוח OAuth מסוג Android שאינו קיים בפרויקט.
            //
            // יצירת חשבון דרך גוגל נמצאת במסך הפתיחה, בזרימה נפרדת. תקלה
            // בהגדרת OAuth פוגעת רק בה, ולא גוררת איתה תכונה שעובדת.
            if (GoogleAuth.bindToCurrentFirebaseAccount(context, acct) == null) {
                GoogleAuth.signOut(context)
                status = "יש להתחבר קודם לחשבון FilterTube"
            } else {
                account = acct
                syncAccount(acct)
            }
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

    LazyColumn(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        item {
            Text("ספריה", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ThemeState.text,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp))
            HorizontalDivider(color = ThemeState.divider)
        }

        // כרטיס חיבור Google
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(12.dp)).background(ThemeState.card).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, null, tint = Color(0xFFFF0000), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (account != null) account?.email ?: "מחובר" else "חיבור גוגל",
                            color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (account != null) "לייקים ומנויים מיוטיוב"
                            else "בחירת חשבון בלחיצה — מביא לייקים ומנויים",
                            color = ThemeState.subtext, fontSize = 12.sp,
                        )
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
                            GoogleAuth.signOut(context)
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

        // כרטיס סנכרון מלא (InnerTube — היסטוריה והמלצות)
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp)).background(ThemeState.card).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, null, tint = ThemeState.accent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (loggedIn) "חיבור דפדפן — פעיל" else "חיבור דפדפן",
                            color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        // מסביר למה קיימת התחברות *שנייה*, כי בלי זה זה נראה
                        // כמו כפילות מיותרת: החיבור עם גוגל מביא לייקים ומנויים
                        // דרך ה-API הרשמי, אבל היסטוריית צפייה והמלצות פשוט לא
                        // קיימות שם — הן דורשות התחברות מלאה בדפדפן.
                        Text(
                            "התחברות עם מייל וסיסמה בדפדפן. מביא היסטוריה, המלצות, " +
                                "לייקים, מנויים ומוזיקה שאהבת — כולל מה שחיבור גוגל לא יכול.",
                            color = ThemeState.subtext, fontSize = 11.5.sp, lineHeight = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(
                        onClick = { if (loggedIn) syncInnerTube() else onOpenLogin() },
                        enabled = !syncing,
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
                    ) { Text(if (loggedIn) "סנכרן עכשיו" else "התחבר (סנכרון מלא)") }
                    if (loggedIn) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            accountStore.logout()
                            history = emptyList(); recs = emptyList()
                            store.setHistory(emptyList()); store.setRecommendations(emptyList())
                            version++
                            status = "התנתקת מהסנכרון המלא"
                        }) { Text("התנתק") }
                    }
                }
            }
        }

        // ── שלושה אוספים, לא שישה ─────────────────────────────────────────
        // שש קוביות שוות במשקל הן שש החלטות, ושתיים מהן היו כפילויות מבלבלות:
        // "אהבתי" מול "אהבתי ביוטיוב" נראו כמו אותו דבר פעמיים. עכשיו זו קובייה
        // אחת שנפתחת עם מתג בין שני המקורות.
        //
        // "היסטוריה" ו"מומלצים" ירדו לשורות טקסט מתחת: הן שימושיות, אבל הן לא
        // אוסף שהמשתמש *בונה* — הן נוצרות מאליהן, ולכן לא צריכות את אותו משקל.
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibTile("אהבתי", likes.size + ytLikes.size, Icons.Default.Favorite, Color(0xFFFF0000)) { onOpenCollection("likes") }
                LibTile("הורדות", downloads.size, Icons.Default.Download, Color(0xFF10B981)) { onOpenCollection("downloads") }
                LibTile("מנויים", subs.size, Icons.Default.Subscriptions, Color(0xFFA855F7)) { onOpenSubscriptions() }
            }
            Spacer(Modifier.height(14.dp))
            // "ערוצים מאושרים" חי כאן ולא בתפריט צף שמסתתר מאחורי אווטאר במסך
            // הבית. הספרייה היא "התוכן שלי", ומעקב אחרי ערוץ הוא בדיוק זה —
            // ממש ליד "מנויים", שהוא אותו רעיון בצד של יוטיוב.
            LibRow("ערוצים מאושרים", channelCount, Icons.Default.Tv, ThemeState.accent) { onOpenChannels() }
            LibRow("היסטוריית צפייה", localHist.size, Icons.Default.History, Color(0xFFFF6D00)) { onOpenCollection("history") }
            LibRow("מומלצים מיוטיוב", recs.size, Icons.Default.Recommend, Color(0xFF00BFA5)) { onOpenCollection("recs") }
            // FilterTube יודעת לנגן גם מה שכבר על הטלפון, לא רק מה שהיא הורידה.
            LibRow("במכשיר שלי", -1, Icons.Default.PhoneAndroid, Color(0xFF3B82F6)) { onOpenDeviceMedia() }
        }

        // אלבומים
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color(0xFFFF0000), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("אלבומים", color = ThemeState.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "אלבום חדש", tint = ThemeState.text) }
            }
        }
        if (playlists.isEmpty()) {
            item { Text("עדיין אין אלבומים — צור אחד עם +", color = ThemeState.subtext, fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) }
        } else {
            items(playlists, key = { it.name }) { pl ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(pl.name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(ThemeState.divider),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = ThemeState.subtext)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pl.name, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${pl.videos.size} סרטונים", color = ThemeState.subtext, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * שורה, לא קובייה — לאוספים שנוצרים מאליהם ולא נבנים ע"י המשתמש.
 * ההבדל בגודל הוא ההבדל בחשיבות, וזה בדיוק מה שהיה חסר כששש קוביות
 * זהות התחרו על אותה תשומת לב.
 */
@Composable
private fun LibRow(title: String, count: Int, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp)).background(ThemeState.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f))
        // count שלילי = לשורה אין מונה (כמו "במכשיר שלי", שנספר רק אחרי סריקה).
        if (count >= 0) Text("$count", color = ThemeState.subtext, fontSize = 13.sp)
    }
}

@Composable
private fun RowScope.LibTile(title: String, count: Int, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).height(104.dp).clip(RoundedCornerShape(14.dp))
            .background(ThemeState.card).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count פריטים", color = ThemeState.subtext, fontSize = 11.sp)
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
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text,
        textContentColor = ThemeState.text,
    )
}
