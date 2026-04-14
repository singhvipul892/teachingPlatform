package com.maths.teacher.app.ui.videodetail

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.maths.teacher.app.ui.home.YouTubeEmbedPlayer

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
    val activity = LocalContext.current as? Activity

    // Hide / restore system UI for true fullscreen in landscape
    LaunchedEffect(isLandscape) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isLandscape) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                            if (!sectionTitle.isNullOrBlank()) {
                                Text(
                                    text = sectionTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(45.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        containerColor = if (isLandscape) Color.Black else MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isLandscape) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Something went wrong.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                uiState.video != null -> {
                    VideoDetailContent(
                        video = uiState.video!!,
                        isLandscape = isLandscape,
                        userId = userId,
                        onOpenPdf = { videoId, pdfId ->
                            navController.navigate("pdf_viewer/$videoId/$pdfId")
                        },
                        onFullscreenToggle = {
                            activity?.requestedOrientation =
                                if (isLandscape)
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        api = api,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoDetailContent(
    video: Video,
    isLandscape: Boolean,
    userId: Long?,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit,
    onFullscreenToggle: () -> Unit,
    api: TeacherApi,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(if (isLandscape) PaddingValues(0.dp) else PaddingValues(16.dp)),
        verticalArrangement = if (isLandscape) Arrangement.Top
        else Arrangement.spacedBy(16.dp)
    ) {
        // Single YouTubeEmbedPlayer — modifier changes on rotation, player never recreates
        YouTubeEmbedPlayer(
            videoId = video.videoId,
            modifier = if (isLandscape)
                Modifier.fillMaxSize()
            else
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            isFullscreen = isLandscape,
            onFullscreenToggle = onFullscreenToggle
        )

        if (!isLandscape) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            if (video.pdfs.isNotEmpty()) {
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
