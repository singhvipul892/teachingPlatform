package com.maths.teacher.app.ui.home

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = true,
    isFullscreen: Boolean = false
) {
    val context = LocalContext.current

    // ✅ Remember WebView to prevent recreation (fix flicker)
    val webView = remember {
        WebView(context).apply {

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setBackgroundColor(Color.BLACK)
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Desktop User-Agent prevents blocking
                userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Safari/537.36"
            }

            // ✅ Fix YouTube error 153
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
    }

    // ✅ Proper YouTube embed URL (VERY IMPORTANT)
    val embedUrl = remember(videoId) {
        "https://www.youtube.com/embed/$videoId" +
                "?playsinline=1" +
                "&rel=0" +
                "&modestbranding=1" +
                "&fs=1" +
                "&enablejsapi=1" +
                "&origin=https://www.youtube.com"
    }

    // ✅ Load video
    LaunchedEffect(videoId) {
        webView.loadUrl(embedUrl)
    }

    // ✅ Proper cleanup
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.onPause()
            webView.destroy()
        }
    }

    Column(modifier = modifier) {

        if (showCloseButton) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Box(
            modifier = if (isFullscreen)
                Modifier.fillMaxSize()
            else
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
