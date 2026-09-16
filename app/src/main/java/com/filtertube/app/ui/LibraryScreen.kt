package com.filtertube.app.ui
import com.filtertube.app.ThemeState
import com.filtertube.app.ui.theme.MosaicTile
import com.filtertube.app.ui.theme.Tint

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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tv
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
    onOpenMyRequests: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(context) }
    val accountStore = remember { AccountStore(context) }

    var version by remember { mutableStateOf(0) }
    val likes = remember(version) { store.likes() }
    // ── ההורדות של FilterTube בלבד ────────────────────────────────────────
    // מה שהורד מתוך FilterMusic נספר ומוצג שם, בספרייה של המוזיקה. שתי
    // הרשימות נשמרות יחד במכשיר, אבל המשתמש הוריד אותן משתי אפליקציות
    // שונות ומצפה למצוא כל אחת במקום שממנו הוריד אותה.
    val downloads = remember(version) { store.downloads().filter { !it.fromMusic } }
    val playlists = remember(version) { store.playlists() }
    var ytLikes by remember { mutableStateOf(store.youtubeLikes()) }
    var subs by remember { mutableStateOf(store.subscriptions()) }
    var history by remember { mutableStateOf(store.history()) }
    var recs by remember { mutableStateOf(store.recommendations()) }
    val localHist = remember(version) { store.localHistory() }   // היסטוריית צפייה מקומית
    var channelCount by remember { mutableStateOf(0) }
    // סמלי הערוצים המאושרים הראשונים — לפסיפס של הקובייה.
    var channelArt by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val cached = runCatching {
            com.filtertube.app.data.ChannelsRepository.getCachedChannelsFast(context)
        }.getOrDefault(emptyList())
        channelCount = cached.size
        channelArt = cached.take(8).mapNotNull {
            com.filtertube.app.data.ChannelAvatars.avatar(it.youtubeChannelId)
        }
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
                val approvedList = ChannelsRepository.getChannels(context)
                val approved = com.filtertube.app.data.ApprovedChannels(approvedList)

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
                    InnerTube.fillOwners(fromBoth) { !approved.approves(it) }
                }
                val musicLiked = musicRaw
                Diagnostics.log(
                    "SYNC גוגל · מיוזיק: ${musicRaw.size} התקבלו · " +
                        "${musicRaw.count { approved.approves(it) }} מאושרים",
                )
                if (!GoogleAuth.isSessionCurrent(context, googleSession)) return@launch
                if (musicLiked.isNotEmpty()) store.setMusicLikes(musicLiked)
                val musicIds = musicRaw.mapTo(HashSet()) { it.id }

                val likedRaw = syncStep("אהבתי") {
                    // אותה השלמה כמו בסנכרון הדפדפן: מזהה שאינו ברשימה הוא
                    // לרוב ערוץ האמן ולא המעלה, ובלי ההשלמה השיר מוצג אפור
                    // למרות שהערוץ שהעלה אותו מאושר.
                    InnerTube.fillOwners(
                        YouTubeAccountRepository.likedVideos(token),
                    ) { !approved.approves(it) }
                }
                val liked = likedRaw.filter { it.id !in musicIds }
                Diagnostics.log(
                    "SYNC גוגל · אהבתי: ${likedRaw.size} התקבלו · " +
                        "${liked.count { approved.approves(it) }} מאושרים",
                )
                if (!GoogleAuth.isSessionCurrent(context, googleSession)) return@launch
                if (liked.isNotEmpty()) { store.setYoutubeLikes(liked); ytLikes = liked }

                // ── המנויים נשמרים במלואם, כולל הלא-מאושרים ───────────────
                // קודם הם נזרקו כאן, והמשתמש ראה רשימה קטועה בלי שום רמז
                // שחסר בה משהו. עכשיו הרשימה מלאה, ומסך המנויים מציג את
                // הלא-מאושרים באפור ומאפשר לבקש להוסיף אותם.
                //
                // זה לא פותח שום פרצה: הרשימה הלבנה נאכפת בכניסה לערוץ
                // ובניגון, לא בשמירה. ערוץ לא מאושר אינו ניתן לפתיחה.
                val subList = syncStep("מנויים") {
                    YouTubeAccountRepository.subscriptions(token)
                }
                Diagnostics.log(
                    "SYNC גוגל · מנויים: ${subList.size} התקבלו · " +
                        "${subList.count { approved.approves(it.channelId, it.title) }} מאושרים",
                )
                if (!GoogleAuth.isSessionCurrent(context, googleSession)) return@launch
                if (subList.isNotEmpty()) { store.setSubscriptions(subList); subs = subList }

                // ── המסלול השלישי ─────────────────────────────────────────
                // אותו אסימון של החשבון שכבר במכשיר, מול השרת הפנימי של
                // יוטיוב — זה שכן מחזיק היסטוריה. אם הוא נענה, אפשר להביא
                // הכל בלי שהמשתמש יקליד סיסמה אף פעם.
                val oauthHistory = syncStep("היסטוריה") {
                    InnerTubeOAuth.history(token)
                }.filter { approved.approves(it) }
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
                val approvedList = ChannelsRepository.getChannels(context)
                val approved = com.filtertube.app.data.ApprovedChannels(approvedList)
                // ערוץ שלא זוהה נפסל. באפליקציית רשימה לבנה "לא ידוע" אינו
                // "מותר", ו-InnerTube מחזירה לא מעט פריטים שלא הצליחה לחלץ
                // להם מזהה ערוץ — כלומר זו הדרך העיקרית שבה תוכן לא מאושר
                // יכול היה להגיע לספרייה ומשם לנגן.
                val hist = InnerTube.history(accountStore.cookies).filter { approved.approves(it) }
                store.setHistory(hist); history = hist
                val rec = InnerTube.recommendations(accountStore.cookies).filter { approved.approves(it) }
                store.setRecommendations(rec); recs = rec

                // ── לייקים, מנויים ומוזיקה — אותן עוגיות, אותה פעולה ──────
                // כל מקור מדווח שלוש מספרים: כמה הגיעו, כמה נפלו כי לא זוהה
                // להם ערוץ, וכמה נשארו אחרי הרשימה הלבנה. "התחבר והכל נשאר
                // ריק" יכול לנבוע מכל אחד מהשלושה, ובלי הפירוט אי אפשר לדעת
                // מאיזה.
                fun report(name: String, items: List<Video>): List<Video> {
                    val noChannel = items.count { it.channelId.isBlank() }
                    val kept = items.filter { approved.approves(it) }
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
                ) { !approved.approves(it) }
                // ── נשמר הכל, כולל מערוצים שלא אושרו ──────────────────────
                // כמו במנויים: רשימה קטועה בלי שום רמז שחסר בה משהו היא
                // בלבול. מסך "אהבתי" מציג את הלא-מאושרים באפור ומאפשר לבקש
                // להוסיף את הערוץ שלהם. הרשימה הלבנה נאכפת בלחיצה — פריט
                // אפור לא מתנגן.
                report("מיוזיק", musicRaw)
                if (musicRaw.isNotEmpty()) store.setMusicLikes(musicRaw)
                val musicIds = musicRaw.mapTo(HashSet()) { it.id }

                // ── משלימים ערוץ לכל מה שלא נמצא ברשימה, לא רק לריקים ─────
                // פלייליסט "אהבתי" מחזיר לחלק מהפריטים את ערוץ ה-Topic של
                // האמן ולא את המעלה. הם קיבלו מזהה — ולכן לא נחשבו "ריקים" —
                // אבל המזהה לא היה ברשימה הלבנה, והשיר הוצג אפור למרות
                // שהערוץ שהעלה אותו מאושר לגמרי.
                val likedRaw = InnerTube.fillOwners(
                    InnerTube.likedVideos(accountStore.cookies),
                ) { !approved.approves(it) }
                val liked = likedRaw.filter { it.id !in musicIds }
                report("אהבתי", liked)
                if (liked.isNotEmpty()) { store.setYoutubeLikes(liked); ytLikes = liked }

                val allSubs = InnerTube.subscriptions(accountStore.cookies)
                val subsFromCookies = allSubs.map { (id, name) -> SubChannel(id, name) }
                Diagnostics.log(
                    "SYNC מנויים: ${allSubs.size} התקבלו · " +
                        "${allSubs.count { approved.approves(it.first, it.second) }} מאושרים",
                )
                if (subsFromCookies.isNotEmpty()) {
                    store.setSubscriptions(subsFromCookies); subs = subsFromCookies
                }

                status = "סונכרן ✓ ${hist.size} בהיסטוריה · ${liked.size} לייקים · " +
                    "${musicRaw.size} שירים ממיוזיק · ${subsFromCookies.size} מנויים · ${rec.size} המלצות"
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
            Text("ספריה", style = MaterialTheme.typography.displaySmall, color = ThemeState.text,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp))
            HorizontalDivider(color = ThemeState.divider)
        }

        // כרטיס חיבור Google
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)
                .clip(RoundedCornerShape(12.dp)).background(ThemeState.card).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountCircle, null, tint = Color(0xFFFF0000), modifier = Modifier.size(28.dp))
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
                    Icon(Icons.Rounded.Sync, null, tint = ThemeState.accent, modifier = Modifier.size(26.dp))
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
        // ── הכל קוביות, וכל קובייה בנויה ממה שיש בתוכה ────────────────────
        // קודם ישבו כאן שלוש קוביות ומתחתן שש שורות, וההבדל בגודל אמר "אלה
        // חשובים יותר". בפועל זו הייתה הבחנה שלי ולא של מי שמשתמש: גם
        // "ערוצים מאושרים" וגם "הבקשות שלי" הם יעדים שנכנסים אליהם.
        //
        // וגם: אייקון של לב אומר "אהבתי" אבל לא אומר *מה* אהבת. הכריכות של
        // הפריטים הראשונים עונות על זה במבט, והקובייה מפסיקה להיות תווית.
        item {
            val allLikes = remember(likes, ytLikes) { (likes + ytLikes).distinctBy { it.id } }
            val tiles = listOf(
                LibTileSpec(
                    "סרטונים שאהבתי", allLikes.size, Icons.Rounded.Favorite, Tint.red,
                    allLikes.map { it.thumbnailUrl },
                ) { onOpenCollection("likes") },
                LibTileSpec(
                    "ההורדות שלי", downloads.size, Icons.Rounded.Download, Tint.green,
                    downloads.map { it.thumbnailUrl },
                ) { onOpenCollection("downloads") },
                LibTileSpec(
                    "מנויים", subs.size, Icons.Rounded.Subscriptions, Tint.violet,
                    subs.map { it.thumbnailUrl },
                ) { onOpenSubscriptions() },
                LibTileSpec(
                    "ערוצים מאושרים", channelCount, Icons.Rounded.Tv, ThemeState.accent,
                    channelArt,
                ) { onOpenChannels() },
                // הבקשות יושבות ליד "ערוצים מאושרים" בכוונה: זו אותה שאלה
                // משני צדדיה — מה כבר מאושר, ומה ביקשתי שיאושר.
                LibTileSpec(
                    "הבקשות שלי", -1, Icons.Rounded.Inbox, Tint.amber, emptyList(),
                ) { onOpenMyRequests() },
                LibTileSpec(
                    "היסטוריית צפייה", localHist.size, Icons.Rounded.History, Tint.orange,
                    localHist.map { it.thumbnailUrl },
                ) { onOpenCollection("history") },
                LibTileSpec(
                    "מומלצים מיוטיוב", recs.size, Icons.Rounded.Recommend, Tint.teal,
                    recs.map { it.thumbnailUrl },
                ) { onOpenCollection("recs") },
                // FilterTube יודעת לנגן גם מה שכבר על הטלפון, לא רק מה שהורידה.
                LibTileSpec(
                    "במכשיר שלי", -1, Icons.Rounded.PhoneAndroid, Tint.blue, emptyList(),
                ) { onOpenDeviceMedia() },
            )
            // רשת ידנית ולא LazyVerticalGrid: אנחנו כבר בתוך LazyColumn,
            // ורשת עצלה מקוננת בתוך רשימה עצלה באותו כיוון גלילה אינה חוקית.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { spec ->
                            MosaicTile(
                                title = spec.title,
                                subtitle = if (spec.count >= 0) "${spec.count} פריטים" else "",
                                images = spec.images,
                                icon = spec.icon,
                                tint = spec.tint,
                                modifier = Modifier.weight(1f),
                                onClick = spec.onClick,
                            )
                        }
                        // תא ריק כדי שקובייה בודדת בשורה אחרונה לא תימתח
                        // לרוחב כפול ותיראה כמו פריט אחר לגמרי.
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // אלבומים
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = Tint.red, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("אלבומים", color = ThemeState.text, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { showCreate = true }) { Icon(Icons.Rounded.Add, "אלבום חדש", tint = ThemeState.text) }
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
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = ThemeState.subtext)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pl.name, color = ThemeState.text, style = MaterialTheme.typography.titleMedium)
                        Text("${pl.videos.size} סרטונים", color = ThemeState.subtext,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** תיאור קובייה אחת ברשת הספרייה. */
private data class LibTileSpec(
    val title: String,
    /** שלילי = לקובייה אין מונה (למשל "במכשיר שלי", שנספר רק אחרי סריקה). */
    val count: Int,
    val icon: ImageVector,
    val tint: Color,
    val images: List<String>,
    val onClick: () -> Unit,
)

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
