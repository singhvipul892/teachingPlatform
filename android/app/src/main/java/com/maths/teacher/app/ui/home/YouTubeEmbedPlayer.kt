package com.maths.teacher.app.ui.home

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onFullscreenToggle: () -> Unit = {}
) {
    val cleanVideoId = remember(videoId) { videoId.trim() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val activity = LocalContext.current as? Activity

    // Handle physical rotation: landscape → portrait resumes video
    val wasFullscreen = remember { mutableStateOf(isFullscreen) }
    LaunchedEffect(isFullscreen) {
        val prev = wasFullscreen.value
        wasFullscreen.value = isFullscreen
        if (prev && !isFullscreen) {
            repeat(8) { attempt ->
                delay(200L + attempt * 300L)
                webView?.evaluateJavascript(RESUME_JS, null)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).also { wv ->
                webView = wv
                wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                wv.setBackgroundColor(android.graphics.Color.BLACK)
                wv.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(wv, true)
                }

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36"
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView, request: WebResourceRequest
                    ) = false

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(HIDE_UI_JS, null)
                    }
                }

                wv.webChromeClient = object : WebChromeClient() {
                    private var customView: View? = null
                    private var customViewCallback: CustomViewCallback? = null
                    private val handler = Handler(Looper.getMainLooper())

                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        if (customView != null) {
                            callback.onCustomViewHidden()
                            return
                        }
                        customView = view
                        customViewCallback = callback

                        val decor = activity?.window?.decorView as? FrameLayout ?: return
                        decor.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        activity.requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        activity.window?.let { win ->
                            val ctrl = WindowCompat.getInsetsController(win, win.decorView)
                            ctrl.hide(WindowInsetsCompat.Type.systemBars())
                            ctrl.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }

                    override fun onHideCustomView() {
                        val decor = activity?.window?.decorView as? FrameLayout ?: return
                        decor.removeView(customView)
                        customView = null
                        customViewCallback?.onCustomViewHidden()
                        customViewCallback = null

                        activity?.requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        activity?.window?.let { win ->
                            WindowCompat.getInsetsController(win, win.decorView)
                                .show(WindowInsetsCompat.Type.systemBars())
                        }

                        // Give WebView focus back then retry playing via Handler
                        // (Handler is more reliable here than coroutines)
                        wv.requestFocus()
                        var attempt = 0
                        val retry = object : Runnable {
                            override fun run() {
                                wv.evaluateJavascript(RESUME_JS, null)
                                if (++attempt < 10) handler.postDelayed(this, 250)
                            }
                        }
                        handler.postDelayed(retry, 200)
                    }
                }

                wv.loadUrl("https://m.youtube.com/watch?v=$cleanVideoId")
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }
}

/** Resumes the HTML5 video only if it is actually paused. */
private const val RESUME_JS =
    "(function(){var v=document.querySelector('video');if(v&&v.paused)v.play().catch(function(){});})()"

private val HIDE_UI_JS = """
(function() {
  var css = document.createElement('style');
  css.textContent = `
    ytm-mobile-topbar-renderer,
    ytm-pivot-bar-renderer,
    ytm-app-promo-banner-renderer,
    ytm-watch-metadata-app-promo-renderer,
    ytm-compact-autoplay-renderer,
    ytm-item-section-renderer,
    ytm-comments-entry-point-header-renderer,
    ytm-watch-metadata-renderer .bottom-content,
    [class*="open-app"], [class*="openApp"],
    [href*="youtube://"], [href*="vnd.youtube"],
    [data-redirect-app-store] { display: none !important; }
    ytm-watch { padding-top: 0 !important; margin-top: 0 !important; }
    ytm-app  { padding-top: 0 !important; }
    .ytp-settings-button,
    .ytp-subtitles-button,
    .ytp-cc-button { display: none !important; }
  `;
  document.head.appendChild(css);
  document.querySelectorAll('[class*="open-app"],[class*="openApp"],[data-redirect-app-store]')
    .forEach(function(el){ el.remove(); });
})();
""".trimIndent()
