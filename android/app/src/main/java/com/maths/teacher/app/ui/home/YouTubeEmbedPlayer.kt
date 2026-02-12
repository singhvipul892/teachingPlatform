package com.maths.teacher.app.ui.home

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.maths.teacher.app.config.AppConstants

@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = true,
    heightDp: Int? = null,
    isFullscreen: Boolean = false
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val embedUrl = remember(videoId) {
        try {
            val url = AppConstants.buildYouTubeEmbedUrl(videoId)
            Log.d("YouTubeEmbedPlayer", "Video ID: '$videoId' (length: ${videoId.length})")
            Log.d("YouTubeEmbedPlayer", "Generated Embed URL: $url")
            
            // Validate video ID format (YouTube IDs are typically 11 characters)
            if (videoId.trim().length != 11) {
                Log.w("YouTubeEmbedPlayer", "Warning: Video ID length is ${videoId.length}, expected 11 characters")
            }
            
            url
        } catch (e: Exception) {
            Log.e("YouTubeEmbedPlayer", "Error building embed URL: ${e.message}", e)
            // Fallback to basic URL
            "${AppConstants.YOUTUBE_EMBED_URL_PREFIX}${videoId}"
        }
    }

    val htmlContent = remember(embedUrl) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    background-color: #000;
                }
                iframe {
                    width: 100%;
                    height: 100%;
                    border: 0;
                }
            </style>
        </head>
        <body>
            <iframe 
                width="100%" 
                height="100%" 
                src="$embedUrl" 
                title="YouTube video player" 
                frameborder="0" 
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                referrerpolicy="strict-origin-when-cross-origin" 
                allowfullscreen>
            </iframe>
        </body>
        </html>
        """.trimIndent()
    }

    Column(modifier = modifier) {
        // Close button row (only show if enabled)
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

        // WebView: fixed height when heightDp set, fullscreen when isFullscreen, otherwise 16:9 aspect ratio
        var webView by remember { mutableStateOf<WebView?>(null) }

        Box(
            modifier = Modifier
                .then(
                    if (isFullscreen) Modifier.fillMaxSize()
                    else if (heightDp != null) Modifier.fillMaxWidth().height(heightDp.dp)
                    else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        
                        // Enable WebView debugging (only works in debug builds)
                        WebView.setWebContentsDebuggingEnabled(true)
                        
                        // Set Chrome desktop user agent to avoid YouTube blocking WebView
                        // YouTube often blocks WebView user agents, so we use a desktop Chrome UA
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            allowFileAccess = true
                            allowContentAccess = true
                        }
                        
                        // Custom WebViewClient to log errors
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                Log.e("YouTubeEmbedPlayer", "WebView Error: ${error?.description}")
                                Log.e("YouTubeEmbedPlayer", "Error Code: ${error?.errorCode}")
                                Log.e("YouTubeEmbedPlayer", "Failed URL: ${request?.url}")
                            }
                            
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                Log.d("YouTubeEmbedPlayer", "Page started loading: $url")
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d("YouTubeEmbedPlayer", "Page finished loading: $url")
                            }
                        }
                        
                        // Custom WebChromeClient to capture console logs
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    val message = "Console [${it.messageLevel()}]: ${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}"
                                    when (it.messageLevel()) {
                                        ConsoleMessage.MessageLevel.LOG -> Log.d("YouTubeEmbedPlayer", message)
                                        ConsoleMessage.MessageLevel.WARNING -> Log.w("YouTubeEmbedPlayer", message)
                                        ConsoleMessage.MessageLevel.ERROR -> Log.e("YouTubeEmbedPlayer", message)
                                        ConsoleMessage.MessageLevel.DEBUG -> Log.d("YouTubeEmbedPlayer", message)
                                        ConsoleMessage.MessageLevel.TIP -> Log.i("YouTubeEmbedPlayer", message)
                                        else -> Log.d("YouTubeEmbedPlayer", message)
                                    }
                                }
                                return true
                            }
                        }
                        
                        Log.d("YouTubeEmbedPlayer", "Loading HTML content with embed URL: $embedUrl")
                        // Use the nocookie domain as base URL for better compatibility
                        val baseUrl = if (embedUrl.contains("youtube-nocookie.com")) {
                            "https://www.youtube-nocookie.com"
                        } else {
                            "https://www.youtube.com"
                        }
                        loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                    }
                },
                modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
            )
        }

        // Handle WebView lifecycle - pause/resume based on lifecycle events
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
}
