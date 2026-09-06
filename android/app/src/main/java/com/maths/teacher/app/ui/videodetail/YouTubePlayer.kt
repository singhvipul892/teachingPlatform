package com.maths.teacher.app.ui.videodetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Log
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
 * The underlying [YouTubePlayerView] is created once and remembered, so it survives
 * recomposition and configuration changes. Combine with `movableContentOf` at the call site
 * to move the SAME player instance between portrait and fullscreen layouts without reloading —
 * playback continues from the current position instead of restarting.
 *
 * The library's own fullscreen button is disabled (`fullscreen(0)`); fullscreen is driven by the
 * host screen through device orientation instead.
 */
@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
            .build()

        val cleanId = extractVideoId(videoId)

        val listener = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.loadVideo(cleanId, 0f)
            }

            override fun onError(
                youTubePlayer: YouTubePlayer,
                error: PlayerConstants.PlayerError
            ) {
                // VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER / HTML_5_PLAYER etc. surface here.
                // If this fires with a clean id, embedding is disabled on the video itself
                // (fixable only by the channel owner in YouTube Studio).
                Log.e("YouTubePlayer", "Playback error for id='$cleanId' (raw='$videoId'): $error")
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
