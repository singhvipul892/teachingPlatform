package com.maths.teacher.app.ui.home

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    @Suppress("UNUSED_VARIABLE")
    val cleanVideoId = remember(videoId) { videoId.trim() }
    val hardcodedVideoId = "0rcMxUx4drQ"
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
                        Log.d("YTEmbed", "onShowCustomView: entering fullscreen")
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

                        // Inject persistent guard BEFORE handing control back to YouTube.
                        // Codec rebuild takes up to 45–50s after orientation change; guard
                        // must stay alive past that entire window.
                        Log.d("YTEmbed", "onHideCustomView: injecting EXIT_FULLSCREEN_GUARD_JS (65s window)")
                        wv.evaluateJavascript(EXIT_FULLSCREEN_GUARD_JS) { result ->
                            Log.d("YTEmbed", "EXIT_FULLSCREEN_GUARD_JS injected, result=$result")
                        }

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

                        // Retry for up to 65 s but stop early once playback is confirmed
                        // stable for 3 consecutive 500 ms ticks (avoids poking a playing video).
                        wv.requestFocus()
                        var attempt = 0
                        var stableCount = 0
                        val retry = object : Runnable {
                            override fun run() {
                                wv.evaluateJavascript(RESUME_JS_LOGGED) { result ->
                                    Log.d("YTEmbed", "RESUME attempt=$attempt result=$result")
                                    val playing = result != null &&
                                        result.contains("\"paused\":false") &&
                                        (result.contains("\"readyState\":3") || result.contains("\"readyState\":4"))
                                    if (playing) {
                                        stableCount++
                                        if (stableCount >= 3) {
                                            Log.d("YTEmbed", "RESUME: playback stable, stopping retry at attempt $attempt")
                                            return@evaluateJavascript
                                        }
                                    } else {
                                        stableCount = 0
                                    }
                                    if (++attempt < 130) handler.postDelayed(this, 500)
                                    else Log.d("YTEmbed", "RESUME_JS retry loop reached 65s limit")
                                }
                            }
                        }
                        handler.postDelayed(retry, 300)
                    }
                }

                wv.loadUrl("https://m.youtube.com/watch?v=$hardcodedVideoId")
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

// Returns a JSON object {found, paused, played} so Android logs can show exact video state.
private var RESUME_JS_LOGGED = """
(function(){
  var v=document.querySelector('video');
  if(!v) return JSON.stringify({found:false});
  var wasPaused=v.paused;
  if(wasPaused) v.play().catch(function(){});
  return JSON.stringify({found:true,paused:wasPaused,readyState:v.readyState,currentTime:v.currentTime});
})()
""".trimIndent()

// Injected at the start of onHideCustomView so it stays alive across the full
// codec-rebuild window. Observed rebuild times: up to 45–50 s, so we use a
// 65-second window with a generous margin.
private val EXIT_FULLSCREEN_GUARD_JS = """
(function(){
  var WINDOW_MS=65000;
  var deadline=Date.now()+WINDOW_MS;
  var attached=new WeakSet();
  var obs;
  function playIfPaused(v,label){
    if(!v.paused) return;
    console.log('[YTEmbed] '+label+': video paused, readyState='+v.readyState+', resuming');
    v.play().catch(function(e){console.log('[YTEmbed] play rejected ('+label+'): '+e);});
  }
  function attach(v){
    if(attached.has(v)) return;
    attached.add(v);
    v.addEventListener('pause',function handler(){
      if(Date.now()>deadline){ v.removeEventListener('pause',handler); return; }
      console.log('[YTEmbed] pause event, readyState='+v.readyState);
      setTimeout(function(){ playIfPaused(v,'pause-retry'); },150);
    });
  }
  function init(){
    var v=document.querySelector('video');
    if(v){ console.log('[YTEmbed] guard init: video found, paused='+v.paused+' readyState='+v.readyState); playIfPaused(v,'init'); attach(v); }
    else { console.log('[YTEmbed] guard init: no video element yet'); }
    obs=new MutationObserver(function(){
      var nv=document.querySelector('video');
      if(nv){ attach(nv); playIfPaused(nv,'mutation'); }
    });
    obs.observe(document.body,{childList:true,subtree:true});
    setTimeout(function(){ obs.disconnect(); console.log('[YTEmbed] guard expired after '+WINDOW_MS+'ms'); },WINDOW_MS);
  }
  if(document.body) init(); else document.addEventListener('DOMContentLoaded',init);
})()
""".trimIndent()

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
    .ytp-cc-button,
    .ytp-fullscreen-button,
    .ytp-size-button,
    button.ytp-fullscreen-button,
    .ytp-chrome-bottom .ytp-fullscreen-button { display: none !important; }
  `;
  document.head.appendChild(css);

  function hideFullscreenBtn() {
    var selectors = [
      '.ytp-fullscreen-button',
      '.ytp-size-button',
      'button[aria-label*="full" i]',
      'button[aria-label*="fullscreen" i]',
      'button[title*="full" i]',
      'button[title*="fullscreen" i]',
      'button[class*="fullscreen"]',
      'button[class*="FullScreen"]'
    ];
    selectors.forEach(function(sel) {
      document.querySelectorAll(sel).forEach(function(el) {
        el.style.setProperty('display', 'none', 'important');
        el.style.setProperty('visibility', 'hidden', 'important');
        el.style.setProperty('pointer-events', 'none', 'important');
      });
    });
  }

  hideFullscreenBtn();

  var obs = new MutationObserver(hideFullscreenBtn);
  obs.observe(document.documentElement, { childList: true, subtree: true });

  document.querySelectorAll('[class*="open-app"],[class*="openApp"],[data-redirect-app-store]')
    .forEach(function(el){ el.remove(); });
})();
""".trimIndent()
