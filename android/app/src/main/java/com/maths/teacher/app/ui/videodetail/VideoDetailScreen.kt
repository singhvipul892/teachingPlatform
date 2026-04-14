package com.maths.teacher.app.ui.videodetail

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // ✅ landscape — hide scaffold, show only fullscreen video
    // same YouTubeEmbedPlayer instance — only modifier changes
    if (isLandscape && uiState.video != null && !uiState.isLoading && uiState.errorMessage == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            YouTubeEmbedPlayer(
                videoId = uiState.video!!.videoId,
                onDismiss = { },
                modifier = Modifier.fillMaxSize(),
                showCloseButton = false,
                isFullscreen = true
            )
        }
        return
    }

    Scaffold(
        topBar = {
            // ✅ hide topbar in landscape
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
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
    api: TeacherApi,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            // ✅ no padding in landscape so video fills edge to edge
            .padding(if (isLandscape) PaddingValues(0.dp) else PaddingValues(16.dp)),
        verticalArrangement = if (isLandscape) Arrangement.Top
        else Arrangement.spacedBy(16.dp)
    ) {
        // ✅ ONE single YouTubeEmbedPlayer — modifier changes based on orientation
        // this prevents WebView from being destroyed and recreated on rotation
        YouTubeEmbedPlayer(
            videoId = video.videoId,
            onDismiss = { },
            modifier = if (isLandscape)
            // landscape — fill entire screen
                Modifier.fillMaxSize()
            else
            // portrait — 16:9 ratio
                Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            showCloseButton = false,
            isFullscreen = isLandscape
        )

        // ✅ hide title and pdfs in landscape — only show video
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