package com.maths.teacher.app.ui.videodetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Log
import com.maths.teacher.app.BuildConfig
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

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

/**
 * A YouTube player backed by the IFrame Player API (androidyoutubeplayer library).
 *
 * This replaces the previous approach of loading m.youtube.com in a bare WebView and
 * stripping its UI with injected CSS. That approach had no supported control channel:
 * playback could only be driven by calling play() on the raw <video> element, which
 * YouTube's own state machine would revert within ~250ms, and there was no way to tell
 * "YouTube paused it" from "the user paused it". The IFrame API gives real play/pause/seek
 * plus state callbacks, so none of that guesswork is needed.
 *
 * The underlying [YouTubePlayerView] is created once and remembered, so it survives
 * recomposition and configuration changes. The host screen keeps the player at the same
 * position in the composition tree across orientations (only its modifier changes), so
 * rotation resizes this view rather than recreating it and playback simply continues.
 *
 * The library's own fullscreen button is disabled (`fullscreen(0)`); fullscreen is driven by the
 * host screen through device orientation instead.
 */
@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Survive process death as well as rotation, so returning to the screen resumes where
    // playback left off instead of restarting. Neither value is read from a composable, so
    // writing them on every tick does not trigger recomposition.
    var lastPositionSeconds by rememberSaveable { mutableStateOf(0f) }
    var wasPlaying by rememberSaveable { mutableStateOf(true) }

    val playerView = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
        }
    }

    DisposableEffect(playerView) {
        val options = IFramePlayerOptions.Builder()
            .controls(1)
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
            .origin("https://teacherplatform.duckdns.org")
            .build()

        val cleanId = extractVideoId(videoId)

        val listener = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                // loadVideo autoplays, cueVideo does not. Honour whichever state the user
                // was last in: restoring must never force playback on someone who
                // deliberately paused.
                if (wasPlaying) {
                    youTubePlayer.loadVideo(cleanId, lastPositionSeconds)
                } else {
                    youTubePlayer.cueVideo(cleanId, lastPositionSeconds)
                }
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                lastPositionSeconds = second
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                when (state) {
                    PlayerConstants.PlayerState.PLAYING -> wasPlaying = true
                    PlayerConstants.PlayerState.PAUSED -> wasPlaying = false
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

        playerView.initialize(listener, options)
        lifecycleOwner.lifecycle.addObserver(playerView)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier
    )
}
