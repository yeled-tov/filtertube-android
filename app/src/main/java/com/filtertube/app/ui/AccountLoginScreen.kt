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
import com.filtertube.app.data.AccountStore

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
                WebView(ctx).apply {
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
                            val cookies = cm.getCookie("https://www.youtube.com") ?: ""
                            if (cookies.contains("SAPISID") || cookies.contains("__Secure-3PAPISID")) {
                                cm.flush()
                                store.cookies = cookies
                                onDone()
                            }
                        }
                    }
                    loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F")
                }
            },
        )
    }
}
