package com.maths.teacher.app.ui.videodetail

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maths.teacher.app.R
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.prefs.SessionManager
import com.maths.teacher.app.domain.model.Video
import com.maths.teacher.app.ui.components.PdfDownloadSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel,
    navController: NavController,
    sessionManager: SessionManager,
    api: TeacherApi,
    sectionTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userId by sessionManager.userId.collectAsStateWithLifecycle(initialValue = null)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val activity = context as? Activity

    // Orientation policy for this screen:
    //   null  -> free rotation, respecting the device auto-rotate setting.
    //   true  -> pinned landscape, because the user tapped "fullscreen".
    //   false -> pinned portrait, because the user tapped "exit fullscreen" or
    //            pressed back while landscape.
    // A pin is held until the device is physically held that way (see below),
    // then released so plain rotation works again.
    var pinnedLandscape by remember { mutableStateOf<Boolean?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pinnedLandscape) {
        activity?.requestedOrientation = when (pinnedLandscape) {
            true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            false -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            null -> ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    // How the device is PHYSICALLY being held, independent of what is on screen
    // and of the auto-rotate setting. Null until the sensor reports something
    // unambiguous.
    var physicallyLandscape by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return
                physicallyLandscape = when {
                    degrees in 60..120 || degrees in 240..300 -> true
                    degrees < 30 || degrees > 330 || degrees in 150..210 -> false
                    else -> return // held at an ambiguous angle; ignore
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }

    // Release the pin only once the device is actually being held the way the
    // pin asked for. Releasing on a timer instead would let the sensor flip the
    // screen straight back: tapping "exit fullscreen" while still holding the
    // phone sideways would snap to landscape again a moment later.
    LaunchedEffect(pinnedLandscape, physicallyLandscape) {
        val want = pinnedLandscape ?: return@LaunchedEffect
        if (physicallyLandscape == want) {
            pinnedLandscape = null
        }
    }

    // Leave the activity as we found it: free orientation, system bars visible.
    // The old code hard-set PORTRAIT here, which silently portrait-locked every
    // other screen for the rest of the session.
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                WindowCompat.getInsetsController(win, win.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // In landscape, back exits fullscreen first (standard video-player
    // behaviour); a second back leaves the screen.
    BackHandler {
        if (isLandscape) {
            pinnedLandscape = false
        } else {
            navController.popBackStack()
        }
    }

    LaunchedEffect(isLandscape) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isLandscape) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        topBar = {
            if (!isLandscape) {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sectionTitle ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(45.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isLandscape) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.video != null -> {
                    VideoDetailContent(
                        video = uiState.video!!,
                        isLandscape = isLandscape,
                        userId = userId,
                        onToggleFullscreen = { pinnedLandscape = !isLandscape },
                        playbackError = playbackError,
                        onPlaybackError = { playbackError = it },
                        onOpenPdf = { videoId, pdfId -> navController.navigate("pdf_viewer/$videoId/$pdfId") },
                        api = api
                    )
                }
            }
        }
    }
}

// Ensure your ViewModel or Data layer provides ONLY the ID.
// Example: "dQw4w9WgXcQ" NOT "https://youtu.be/dQw4w9WgXcQ"
@Composable
private fun VideoDetailContent(
    video: Video,
    isLandscape: Boolean,
    userId: Long?,
    onToggleFullscreen: () -> Unit,
    playbackError: String?,
    onPlaybackError: (String) -> Unit,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit,
    api: TeacherApi,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // The player box keeps the SAME position in the composition tree in both
        // orientations -- only its modifier changes. That is what lets the
        // remembered YouTubePlayerView survive rotation without being recreated,
        // so playback continues rather than reloading from the start.
        Box(
            modifier = (
                if (isLandscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
            ).background(Color.Black)
        ) {
            YouTubePlayer(
                videoId = video.videoId,
                modifier = Modifier.fillMaxSize(),
                onError = onPlaybackError
            )

            // Without this an unplayable video is just a black rectangle with no
            // explanation. onError fires when embedding is disabled on the video
            // (150/152), which is the main risk of the IFrame player.
            if (playbackError != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = playbackError,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            // Landscape hides the app bar and the system bars, so without this
            // there is no visible way out. It drops to the portrait player rather
            // than leaving the screen -- exiting entirely is the portrait app
            // bar's job, matching how the hardware back key behaves here.
            if (isLandscape) {
                PlayerOverlayButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit fullscreen",
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
            }

            // Top-end, not bottom-end: YouTube's scrubber runs along the bottom
            // edge and must not be covered. The top-right corner is free because
            // the IFrame player is built with fullscreen(0) and rel(0).
            PlayerOverlayButton(
                icon = if (isLandscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (isLandscape) "Exit fullscreen" else "Enter fullscreen",
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
        }

        if (!isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // PdfDownloadSection renders nothing when the list is empty, so no
                // guard is needed here; the title still shows for PDF-less videos.
                PdfDownloadSection(
                    pdfs = video.pdfs,
                    videoId = video.id,
                    userId = userId,
                    onOpenPdf = onOpenPdf,
                    api = api,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PlayerOverlayButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}
