package com.filtertube.app.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * נגן וידאו מבוסס WebView עם YouTube IFrame API.
 *
 * - HTML מותאם אישית בנינו בעצמנו (loadDataWithBaseURL → אמין יותר מ-loadUrl)
 * - CSS מסתיר לוגו YouTube, "More videos", watermark, וכל ה-UI שלהם
 * - הסרטון ממלא את כל המסך
 * - controls=1 (נגן בסיסי) אבל ללא יחדים של YouTube ברורים
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    videoId: String,
    title: String,
    channelName: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "חזור",
                    tint = Color.White,
                )
            }
            Text(
                text = "צפייה",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Player area (16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            YouTubeWebView(videoId = videoId, modifier = Modifier.fillMaxSize())
        }

        // Title + channel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = channelName,
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebView(videoId: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                setBackgroundColor(android.graphics.Color.BLACK)

                // HTML מותאם שמכסה את כל המסך ומסתיר את ה-branding של YouTube
                val html = buildPlayerHtml(videoId)
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

/**
 * HTML עם YouTube IFrame API + CSS שמסתיר את כל ה-UI של YouTube.
 */
private fun buildPlayerHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1.0,user-scalable=no">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  html, body {
    width: 100%; height: 100%;
    background: #000;
    overflow: hidden;
    -webkit-tap-highlight-color: transparent;
  }
  #player {
    width: 100vw;
    height: 100vh;
    position: absolute;
    top: 0; left: 0;
  }
  iframe {
    width: 100%;
    height: 100%;
    border: 0;
  }
  /* כסה את הלוגו של YouTube בפינה */
  .ytp-watermark, .ytp-youtube-button, .ytp-show-cards-title,
  .ytp-pause-overlay, .ytp-endscreen-content, .ytp-ce-element {
    display: none !important;
    opacity: 0 !important;
  }
</style>
</head>
<body>
  <div id="player"></div>
  <script>
    // טען את ה-IFrame API של YouTube
    var tag = document.createElement('script');
    tag.src = "https://www.youtube.com/iframe_api";
    document.head.appendChild(tag);

    var player;
    function onYouTubeIframeAPIReady() {
      player = new YT.Player('player', {
        videoId: '$videoId',
        playerVars: {
          autoplay: 1,
          playsinline: 1,
          controls: 1,            // controls פנימיים של YouTube — בכל זאת צריך כדי לשלוט
          modestbranding: 1,      // לוגו מינימלי
          rel: 0,                 // ללא סרטונים קשורים
          showinfo: 0,
          iv_load_policy: 3,      // ללא annotations
          fs: 1,                  // אפשר fullscreen
          disablekb: 0,
          origin: 'https://www.youtube.com'
        },
        events: {
          'onReady': function(e) {
            e.target.playVideo();
          },
          'onError': function(e) {
            document.body.innerHTML =
              '<div style="color:#fff;text-align:center;padding-top:40%;font-family:sans-serif;">' +
              'הסרטון אינו זמין<br><small>שגיאה: ' + e.data + '</small></div>';
          }
        }
      });
    }
  </script>
</body>
</html>
""".trimIndent()
