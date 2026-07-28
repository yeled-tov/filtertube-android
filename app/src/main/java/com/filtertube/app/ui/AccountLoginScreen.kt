package com.filtertube.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.filtertube.app.ThemeState
import com.filtertube.app.data.AccountDataGuard
import com.filtertube.app.data.AccountStore
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.atomic.AtomicBoolean

/**
 * התחברות מלאה ליוטיוב דרך WebView — לוכדת את ה-cookies של החשבון לשימוש InnerTube
 * (היסטוריה/המלצות/לייקים). User-Agent נקי (בלי "wv") כדי לעקוף את חסימת
 * "דפדפן לא מאובטח" של גוגל.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AccountLoginScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AccountStore(context) }
    val webViewRef = remember { arrayOfNulls<WebView>(1) }
    val completed = remember { AtomicBoolean(false) }
    val expectedUid = remember {
        FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.isEmailVerified }
            ?.uid
    }
    val generation = remember { AccountDataGuard.generation() }

    fun sessionCurrent(): Boolean {
        val current = FirebaseAuth.getInstance().currentUser
        return expectedUid != null &&
            current?.uid == expectedUid &&
            current.isEmailVerified &&
            AccountDataGuard.generation() == generation
    }

    BackHandler {
        val wv = webViewRef[0]
        if (wv != null && wv.canGoBack()) wv.goBack() else onDone()
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("התחברות ליוטיוב", onBack = onDone)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                val webView = WebView(ctx).apply {
                    webViewRef[0] = this
                    cm.setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // UA נקי (בלי "wv") כדי לעקוף את חסימת ההתחברות של גוגל ב-WebView
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/130.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (
                                !sessionCurrent() ||
                                view == null ||
                                view !== webViewRef[0]
                            ) {
                                AccountStore.clearBrowserSession()
                                return
                            }
                            val cookies = cm.getCookie("https://www.youtube.com") ?: ""
                            if (
                                (cookies.contains("SAPISID") ||
                                    cookies.contains("__Secure-3PAPISID")) &&
                                completed.compareAndSet(false, true)
                            ) {
                                cm.flush()
                                store.cookies = cookies
                                AccountStore.clearBrowserSession()
                                if (store.isLoggedIn && sessionCurrent()) {
                                    onDone()
                                } else {
                                    completed.set(false)
                                }
                            }
                        }
                    }
                }
                // Do not let a previous Firebase account's Google WebView
                // session silently sign the next account back in.
                store.logout {
                    webView.post {
                        if (sessionCurrent() && webViewRef[0] === webView) {
                            webView.loadUrl(
                                "https://accounts.google.com/ServiceLogin" +
                                    "?service=youtube&continue=" +
                                    "https%3A%2F%2Fwww.youtube.com%2F",
                            )
                        }
                    }
                }
                webView
            },
            onRelease = { webView ->
                if (webViewRef[0] === webView) webViewRef[0] = null
                webView.webViewClient = WebViewClient()
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
                AccountStore.clearBrowserSession()
            },
        )
    }
}
