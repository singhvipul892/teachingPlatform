package com.maths.teacher.app.ui.home

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

    // Root with WebView + fullscreen overlay (handles fullscreen button in video)
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val playerRoot = remember {
        FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)

            val fullscreenContainer = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
                visibility = View.GONE
            }

            val webView = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
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
                    userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/120.0.0.0 Safari/537.36"
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

                    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                        if (view != null && callback != null) {
                            customViewCallback = callback
                            fullscreenContainer.removeAllViews()
                            fullscreenContainer.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )
                            fullscreenContainer.visibility = View.VISIBLE
                        }
                    }

                    override fun onHideCustomView() {
                        fullscreenContainer.removeAllViews()
                        fullscreenContainer.visibility = View.GONE
                        customViewCallback?.onCustomViewHidden()
                        customViewCallback = null
                    }
                }
            }

            addView(webView)
            addView(fullscreenContainer)
            webViewRef.value = webView
        }
    }

    val embedUrl = remember(videoId) {
        com.maths.teacher.app.config.AppConstants.buildYouTubeEmbedUrl(
            videoId = videoId,
            useNoCookieDomain = true
        )
    }

    LaunchedEffect(videoId) {
        val headers = mapOf("Referer" to "https://www.youtube-nocookie.com/")
        webViewRef.value?.loadUrl(embedUrl, headers)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.apply {
                stopLoading()
                onPause()
                destroy()
            }
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
                factory = { playerRoot },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
