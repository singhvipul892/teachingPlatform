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

@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onFullscreenToggle: () -> Unit = {},
    // preparer receives a callback it must call after the guard JS has finished executing
    onExitFullscreenPreparer: (preparer: (onReady: () -> Unit) -> Unit) -> Unit = {}
) {
    val cleanVideoId = remember(videoId) { videoId.trim() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val activity = LocalContext.current as? Activity

    // Register with parent: inject GUARD_JS, then call onReady() so rotation starts
    // only after the JS is confirmed to have run in the renderer.
    LaunchedEffect(webView) {
        val wv = webView ?: return@LaunchedEffect
        onExitFullscreenPreparer { onReady ->
            wv.evaluateJavascript(GUARD_JS) { onReady() }
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
                        // Resume playback after any page reload caused by viewport change
                        val handler = Handler(Looper.getMainLooper())
                        var attempt = 0
                        val retry = object : Runnable {
                            override fun run() {
                                view.evaluateJavascript(RESUME_JS, null)
                                if (++attempt < 8) handler.postDelayed(this, 300)
                            }
                        }
                        handler.postDelayed(retry, 500)
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

private const val RESUME_JS =
    "(function(){var v=document.querySelector('video');if(v)v.play().catch(function(){});})()"

private val GUARD_JS = """
(function(){
  var v=document.querySelector('video');
  if(!v) return;
  var play=function(){v.play().catch(function(){});};
  play();
  var fn=function(){setTimeout(play,120);};
  v.addEventListener('pause',fn,{once:true});
  var obs=new MutationObserver(function(){
    var nv=document.querySelector('video');
    if(nv&&nv!==v){
      v.removeEventListener('pause',fn);
      v=nv;
      v.addEventListener('pause',fn,{once:true});
      play();
    }
  });
  obs.observe(document.body,{childList:true,subtree:true});
  setTimeout(function(){obs.disconnect();v.removeEventListener('pause',fn);},6000);
})()
""".trimIndent()

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
