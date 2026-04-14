package com.maths.teacher.app.ui.home

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onFullscreenToggle: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // TODO: remove hardcoded test video before release
    val cleanVideoId = remember(videoId) { "jNQXAC9IVRw" }

    // The HTML wraps the embed in an iframe.
    // loadDataWithBaseURL with "https://www.youtube.com" makes YouTube's player
    // see the parent document origin as youtube.com → bypasses the 150/152 restriction.
    val htmlContent = remember(cleanVideoId) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                iframe { width: 100%; height: 100%; border: none; display: block; }
            </style>
        </head>
        <body>
            <iframe
                src="https://www.youtube.com/embed/$cleanVideoId?autoplay=1&playsinline=1&rel=0&modestbranding=1&enablejsapi=1"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowfullscreen>
            </iframe>
        </body>
        </html>
        """.trimIndent()
    }

    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    webView = wv

                    // Required for video frame rendering in WebView
                    wv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                    WebView.setWebContentsDebuggingEnabled(true)

                    // YouTube needs cookies to initialise the player
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(wv, true)
                    }

                    wv.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        allowFileAccess = true
                        allowContentAccess = true
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        // Present as Chrome mobile — hides the WebView identity from YouTube
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    wv.webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            Log.e("YouTubePlayer", "Error: ${error?.description} | ${request?.url}")
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            return false
                        }
                    }

                    wv.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                            msg?.let {
                                Log.d("YouTubePlayer", "JS [${it.messageLevel()}]: ${it.message()}")
                            }
                            return true
                        }
                    }

                    Log.d("YouTubePlayer", "Loading video: $cleanVideoId")

                    // Base URL = youtube.com → player sees origin as youtube.com → no 150/152 error
                    wv.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        htmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Fullscreen toggle button — bottom-right corner
        IconButton(
            onClick = onFullscreenToggle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(40.dp)
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit
                              else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                tint = Color.White
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView?.onPause()
                    webView?.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webView?.onResume()
                    webView?.resumeTimers()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    webView?.destroy()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.onPause()
            webView?.pauseTimers()
        }
    }
}
