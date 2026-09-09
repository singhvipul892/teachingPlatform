package com.maths.teacher.app.ui.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.doOnAttach
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import coil.compose.AsyncImage
import com.maths.teacher.app.BuildConfig
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.delay

/**
 * The origin the embedded player claims to be served from. Two things must agree on it and they
 * sit far apart in this file, so it lives here exactly once:
 *
 *  - the IFramePlayerOptions origin, which decides whether YouTube accepts the embed at all
 *    (claiming to be youtube.com gets error 152 -- see FLOW.md).
 *  - [isAllowedPlayerNavigation], which treats this host as "our own page".
 *
 * If the app's domain ever changes, this is the only line to change.
 */
private const val PLAYER_ORIGIN = "https://teacherplatform.duckdns.org"

private val PLAYER_ORIGIN_HOST: String = Uri.parse(PLAYER_ORIGIN).host.orEmpty()

/** Hosts that can legitimately serve the embedded player. */
private val EMBED_HOSTS = setOf(
    "www.youtube.com",
    "youtube.com",
    "www.youtube-nocookie.com",
    "youtube-nocookie.com",
)

/** How long the transport bar stays up after a tap before fading out during playback. */
private const val CONTROLS_AUTO_HIDE_MS = 3_000L

/**
 * Extracts the 11-character YouTube video ID from whatever the backend stored — a bare ID,
 * a full watch URL, a youtu.be short link, or an embed URL. The IFrame player's `loadVideo`
 * requires the bare ID; handing it a URL causes a "video unavailable" (error 150/152) failure.
 */
private fun extractVideoId(raw: String): String {
    val input = raw.trim()
    // Already a bare 11-char ID (letters, digits, - and _).
    if (input.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return input
    // Any of the common URL shapes: watch?v=, youtu.be/, /embed/, /shorts/.
    val match = Regex("(?:v=|/embed/|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{11})").find(input)
    return match?.groupValues?.get(1) ?: input
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

/**
 * The library builds YouTubePlayerView -> LegacyYouTubePlayerView -> WebViewYouTubePlayer
 * (which *is* an [android.webkit.WebView]) inside its constructor chain, so the WebView exists
 * as soon as the constructor returns. Its accessor
 * (LegacyYouTubePlayerView.getWebViewYouTubePlayer, Kotlin-`internal` and so name-mangled on the
 * JVM) is not callable from here without reflection; walking the children for the first WebView
 * is the same lookup without the reflection.
 */
private fun View.findWebViewDescendant(): WebView? {
    if (this is WebView) return this
    if (this !is ViewGroup) return null
    for (i in 0 until childCount) {
        getChildAt(i).findWebViewDescendant()?.let { return it }
    }
    return null
}

/**
 * True for the handful of navigations the embedded player legitimately makes; false for
 * everything else, including every URL that would take the user to YouTube.
 *
 * The decision is made on the URL, **never** on WebResourceRequest.isForMainFrame. The player
 * runs in a cross-origin iframe, so the navigation we must allow and the link taps we must block
 * arrive on the *same* frame: multiple-window support is off, so target="_blank" folds into a
 * same-frame navigation. (And a user-activated iframe may navigate the top frame, so escapes
 * exist on both sides of that boolean.)
 *
 * Sub-resources -- scripts, images, media segments, XHR to ytimg/gstatic/googlevideo -- never
 * reach this callback at all; they go through shouldInterceptRequest, which is not overridden.
 * So they need no allowlisting.
 */
private fun isAllowedPlayerNavigation(uri: Uri): Boolean {
    when (uri.scheme?.lowercase()) {
        // Cannot leave the app, and blocking about:blank risks breaking the iframe the player
        // lives in for no gain.
        "about", "data", "blob", "javascript" -> return true
        "http", "https" -> Unit
        // vnd.youtube:, intent://, market://, android-app://, mailto:, tel: -- schemes whose only
        // purpose is handing the URL to another app. This branch is the actual fix.
        else -> return false
    }

    val host = uri.host?.lowercase() ?: return false

    // Our own base URL. loadDataWithBaseURL is app-initiated so it is never reported here, but if
    // some WebView build does report it, blocking would leave a permanently blank player -- and
    // loading it cannot escape to YouTube. Pure downside protection.
    if (host == PLAYER_ORIGIN_HOST) return true

    // Google's EEA/UK consent interstitial can front an embed. It is a dead end here: any link
    // out of it is judged again by this same predicate.
    if (host == "consent.youtube.com" || host == "consent.google.com") return true

    // youtu.be, m./music./studio.youtube.com, accounts.google.com and www.google.com/url?q=
    // (YouTube's link redirector) all fall out here.
    if (host !in EMBED_HOSTS) return false

    // On an allowed host, only the embedded player itself. /watch, /channel/, /@handle, /shorts
    // and /results are where the logo, the title and the end-screen cards point.
    val path = uri.path.orEmpty()
    return path == "/embed" || path.startsWith("/embed/")
}

/**
 * Stops the embedded player being a doorway out of the app, and stops YouTube's own chrome from
 * ever responding to a touch.
 *
 * *Navigation.* The library never calls setWebViewClient (verified: zero call sites in the AAR),
 * and a WebView with no client falls back to the platform default -- a private NullWebViewClient
 * whose shouldOverrideUrlLoading builds an ACTION_VIEW Intent and starts it. That is why tapping
 * the YouTube logo, the video title, "Watch on YouTube" or an end-screen card used to launch the
 * YouTube app and leave us. Installing any client removes that fallback; this one additionally
 * refuses to navigate anywhere but the embedded player.
 *
 * *Touch.* The embed is driven entirely through the JS bridge (play/pause/seek), so it never
 * needs a touch. Swallowing every touch means YouTube's chrome -- the share button, the channel
 * name, the title -- can neither be tapped nor summoned by tapping. Combined with `controls(0)`
 * and the poster scrim in [YouTubePlayer], none of it is ever reachable or visible.
 *
 * Returns false if the WebView could not be found, so the caller can retry on attach.
 */
private fun installPlayerLockdown(
    playerView: YouTubePlayerView,
    onBlocked: (Uri) -> Unit,
): Boolean {
    val webView = playerView.findWebViewDescendant() ?: return false

    // Already the defaults, set explicitly because the guard depends on them: with multiple
    // windows unsupported, target="_blank" becomes a same-frame navigation and therefore reaches
    // shouldOverrideUrlLoading, instead of WebChromeClient.onCreateWindow -- which we must NOT
    // override, since the library owns the chrome client for its fullscreen path.
    webView.settings.setSupportMultipleWindows(false)
    webView.settings.javaScriptCanOpenWindowsAutomatically = false

    webView.webViewClient = object : WebViewClient() {
        // minSdk is 24 and targetSdk is 36, so this is always the overload the framework calls;
        // the deprecated String overload is unreachable and is deliberately not implemented.
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            if (isAllowedPlayerNavigation(uri)) return false
            onBlocked(uri)
            return true // swallow it: the app never hands a URL to YouTube
        }
    }

    // Consume every touch before WebView.onTouchEvent sees it. Playback is driven from our own
    // controls through the JS bridge, so nothing is lost -- and YouTube's chrome cannot be
    // tapped or summoned.
    @Suppress("ClickableViewAccessibility")
    webView.setOnTouchListener { _, _ -> true }

    // Long-pressing text in the player starts Chromium's selection ActionMode, whose "Web search"
    // and process-text items launch a browser or another app.
    webView.isLongClickable = false
    webView.setOnLongClickListener { true }

    return true
}

/**
 * A YouTube player backed by the IFrame Player API (androidyoutubeplayer library), with YouTube's
 * own controls switched off and replaced by this app's.
 *
 * **Why custom controls.** The course videos are paid content: the embed must not advertise the
 * channel or hand out a link to the video. YouTube offers no parameter for that — `showinfo` was
 * removed in 2018 and `modestbranding` became a no-op in 2023, so the stock chrome always carries
 * the channel avatar, the channel name, the video title and a share button, each of which is a
 * route to youtube.com. Three things together remove it:
 *
 *  1. `controls(0)` drops YouTube's transport bar (share, captions, settings, the YouTube button).
 *  2. [installPlayerLockdown] swallows every touch, so the remaining chrome can never be tapped,
 *     and never appears in the first place because appearing is a response to a tap.
 *  3. Whenever playback is not running — the cued state, a pause, the end of the video — YouTube
 *     draws that chrome unprompted, so the poster scrim below covers the player completely at
 *     exactly those moments.
 *
 * The player is otherwise unchanged: the underlying [YouTubePlayerView] is created once and
 * remembered, so it survives recomposition and configuration changes. The host screen keeps it at
 * the same position in the composition tree across orientations (only its modifier changes), so
 * rotation resizes this view rather than recreating it and playback simply continues.
 */
@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cleanId = remember(videoId) { extractVideoId(videoId) }

    // Survive process death as well as rotation, so returning to the screen resumes where
    // playback left off instead of restarting.
    var positionSeconds by rememberSaveable { mutableStateOf(0f) }
    var wasPlaying by rememberSaveable { mutableStateOf(true) }

    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var durationSeconds by remember { mutableStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    // Distinguishes "not started yet" from "stalled mid-playback": a network stall must not slam
    // the poster back over a video the user is watching.
    var hasStarted by rememberSaveable { mutableStateOf(false) }
    // Non-null only while the user is dragging the scrubber, so onCurrentSecond does not yank the
    // thumb back under their finger.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }

    val playerView = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
        }
    }

    DisposableEffect(playerView) {
        val options = IFramePlayerOptions.Builder()
            // 0, not 1: YouTube's transport bar carries a share button and the YouTube button,
            // both of which lead out of the app. This app draws its own controls instead.
            .controls(0)
            .fullscreen(0)
            .rel(0)
            // The library defaults origin to "https://www.youtube.com" and loads the
            // player via loadDataWithBaseURL(origin, ...), so the page hosting the embed
            // claims to BE youtube.com. YouTube rejects that with error 152, which the
            // library does not map (it only knows 101 and 150), so it reached us as
            // UNKNOWN and looked like a video problem.
            //
            // Verified with a controlled comparison: from a youtube.com origin both a
            // known-good control video and ours return 152; from an ordinary
            // third-party origin both load fine. So the origin is the fault, not the
            // video. Claim the domain this app actually belongs to.
            .origin(PLAYER_ORIGIN)
            .build()

        val listener = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                player = youTubePlayer
                // loadVideo autoplays, cueVideo does not. Honour whichever state the user
                // was last in: restoring must never force playback on someone who
                // deliberately paused.
                if (wasPlaying) {
                    youTubePlayer.loadVideo(cleanId, positionSeconds)
                } else {
                    youTubePlayer.cueVideo(cleanId, positionSeconds)
                }
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                positionSeconds = second
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                durationSeconds = duration
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                when (state) {
                    PlayerConstants.PlayerState.PLAYING -> {
                        wasPlaying = true
                        isPlaying = true
                        hasStarted = true
                    }
                    PlayerConstants.PlayerState.PAUSED -> {
                        wasPlaying = false
                        isPlaying = false
                    }
                    PlayerConstants.PlayerState.ENDED -> {
                        // Navigation out of the player is blocked, so YouTube's end screen would
                        // be a grid of dead cards (rel=0 only limits them to this channel, it
                        // does not remove them). Re-cueing returns to our own poster, which is
                        // the affordance a finished video should have anyway -- and it means
                        // coming back to this screen starts from the beginning rather than
                        // autoplaying.
                        wasPlaying = false
                        isPlaying = false
                        hasStarted = false
                        positionSeconds = 0f
                        youTubePlayer.cueVideo(cleanId, 0f)
                    }
                    // BUFFERING is deliberately not handled: mid-playback stalls must leave the
                    // poster hidden and the controls as they were.
                    else -> Unit
                }
            }

            override fun onError(
                youTubePlayer: YouTubePlayer,
                error: PlayerConstants.PlayerError
            ) {
                // VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER / HTML_5_PLAYER etc. surface here.
                // If this fires with a clean id, embedding is disabled on the video itself
                // (fixable only by the channel owner in YouTube Studio). Report it rather
                // than leaving the user looking at a black rectangle.
                Log.e("YouTubePlayer", "Playback error for id='$cleanId' (raw='$videoId'): $error")
                onError(
                    buildString {
                        append(
                            when (error) {
                                PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
                                    "This video can't be embedded. Turn on \"Allow embedding\" for it in YouTube Studio."
                                PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
                                    "This video is no longer available."
                                PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST ->
                                    "This video's ID is not valid."
                                PlayerConstants.PlayerError.HTML_5_PLAYER ->
                                    "The video player failed to start."
                                // The library maps only 2/5/100/101/150. YouTube also returns
                                // 152 and 153 for embedding and referrer restrictions, and both
                                // fall through to UNKNOWN -- so this is very often still an
                                // embedding problem rather than a network one.
                                PlayerConstants.PlayerError.UNKNOWN ->
                                    "Couldn't play this video. It may not be embeddable, or the connection dropped."
                            }
                        )
                        // Debug builds show the technical detail so a screenshot is enough to
                        // diagnose; release builds show only the sentence above.
                        if (BuildConfig.DEBUG) {
                            append("\n\n[$error | id=$cleanId]")
                        }
                    }
                )
            }
        }

        // Install before initialize(): initialize() is what calls loadDataWithBaseURL, so there
        // is never a moment where the player page is live and unguarded. The WebView is built in
        // YouTubePlayerView's constructor chain and so already exists; doOnAttach is a cheap
        // retry in case a library upgrade ever defers that construction.
        val onBlocked: (Uri) -> Unit = { uri ->
            if (BuildConfig.DEBUG) {
                Log.i("YouTubePlayer", "Blocked navigation out of the player: $uri")
            }
        }
        if (!installPlayerLockdown(playerView, onBlocked)) {
            Log.w("YouTubePlayer", "WebView not found yet; deferring player lockdown to attach")
            playerView.doOnAttach {
                if (!installPlayerLockdown(playerView, onBlocked)) {
                    Log.e("YouTubePlayer", "Player lockdown could not be installed")
                }
            }
        }

        playerView.initialize(listener, options)
        lifecycleOwner.lifecycle.addObserver(playerView)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            player = null
            playerView.release()
        }
    }

    // Fade the transport bar out again so it does not sit over the video for the whole lesson.
    LaunchedEffect(controlsVisible, isPlaying, scrubPosition) {
        if (controlsVisible && isPlaying && scrubPosition == null) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // YouTube draws its channel/title/share chrome whenever playback is not running, so cover the
    // player completely at exactly those moments.
    val showPoster = !hasStarted || !isPlaying
    val showControls = showPoster || controlsVisible

    Box(modifier = modifier) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )

        // Taps toggle the transport bar. The WebView below swallows touches anyway; this is what
        // gives them a purpose.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { controlsVisible = !controlsVisible }
        )

        if (showPoster) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { player?.play() },
                contentAlignment = Alignment.Center
            ) {
                // hqdefault, not maxresdefault: the latter 404s for plenty of videos. This is an
                // image load, not a navigation, so it never reaches the navigation guard.
                AsyncImage(
                    model = "https://img.youtube.com/vi/$cleanId/hqdefault.jpg",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(10.dp)
                )
            }
        }

        if (showControls) {
            PlayerTransportBar(
                isPlaying = isPlaying,
                positionSeconds = scrubPosition ?: positionSeconds,
                durationSeconds = durationSeconds,
                onPlayPause = { if (isPlaying) player?.pause() else player?.play() },
                onScrub = { scrubPosition = it },
                onScrubFinished = {
                    scrubPosition?.let { target ->
                        positionSeconds = target
                        player?.seekTo(target)
                    }
                    scrubPosition = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
    }
}

/**
 * The app's own transport bar: play/pause, elapsed and total time, and a scrubber. It replaces
 * YouTube's, which cannot be used because it carries a share button and a link to the channel.
 */
@Composable
private fun PlayerTransportBar(
    isPlaying: Boolean,
    positionSeconds: Float,
    durationSeconds: Float,
    onPlayPause: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                )
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Text(
            text = formatTime(positionSeconds),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )

        Slider(
            value = positionSeconds.coerceIn(0f, durationSeconds.coerceAtLeast(0f)),
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished,
            // A zero-length range would make the thumb jump around before onVideoDuration lands.
            valueRange = 0f..durationSeconds.coerceAtLeast(1f),
            enabled = durationSeconds > 0f,
            colors = SliderDefaults.colors(
                thumbColor = Color.Red,
                activeTrackColor = Color.Red,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
            ),
            modifier = Modifier.weight(1f)
        )

        Text(
            text = formatTime(durationSeconds),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
