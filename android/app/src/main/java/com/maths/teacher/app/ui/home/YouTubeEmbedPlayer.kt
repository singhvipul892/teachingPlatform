package com.maths.teacher.app.ui.home

import android.util.Log
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
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onFullscreenToggle: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cleanVideoId = remember(videoId) { videoId.trim() }

    // Keep a reference to the player view so lifecycle can be managed
    var playerView by remember { mutableStateOf<YouTubePlayerView?>(null) }

    Box(
        modifier = modifier.background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                YouTubePlayerView(ctx).also { view ->
                    playerView = view
                    // Lifecycle-aware: handles pause/resume/destroy automatically
                    lifecycleOwner.lifecycle.addObserver(view)

                    view.enableAutomaticInitialization = false
                    view.initialize(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            Log.d("YouTubePlayer", "Player ready, loading: $cleanVideoId")
                            youTubePlayer.loadVideo(cleanVideoId, 0f)
                        }
                    })
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
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                tint = Color.White
            )
        }
    }

    // Release the player when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            playerView?.let { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                view.release()
            }
        }
    }
}
