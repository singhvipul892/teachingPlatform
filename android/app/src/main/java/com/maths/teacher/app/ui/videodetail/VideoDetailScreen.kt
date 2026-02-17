package com.maths.teacher.app.ui.videodetail

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maths.teacher.app.R
import com.maths.teacher.app.domain.model.Video
import com.maths.teacher.app.ui.components.PdfDownloadSection

private const val LOREM_IPSUM =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit..."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailScreen(
    viewModel: VideoDetailViewModel,
    navController: NavController,
    sectionTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle(initialValue = null)
    val openPdfRequest by viewModel.openPdfAfterDownload.collectAsStateWithLifecycle(initialValue = null)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(openPdfRequest) {
        openPdfRequest?.let { (v, p) ->
            navController.navigate("pdf_viewer/$v/$p/${userId ?: 0}")
            viewModel.clearOpenPdfRequest()
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
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(if (isLandscape) PaddingValues(0.dp) else paddingValues)
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
                            text = uiState.errorMessage ?: "Something went wrong",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                uiState.video != null -> {
                    VideoDetailContent(
                        video = uiState.video!!,
                        userId = userId,
                        viewModel = viewModel,
                        downloadingPdfId = uiState.downloadingPdfId,
                        isLandscape = isLandscape,
                        onOpenPdf = { videoId, pdfId ->
                            navController.navigate("pdf_viewer/$videoId/$pdfId/${userId ?: 0}")
                        },
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
    userId: Long?,
    viewModel: VideoDetailViewModel,
    downloadingPdfId: Long?,
    isLandscape: Boolean,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(if (isLandscape) rememberScrollState() else scrollState)
            .padding(if (isLandscape) 0.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ✅ DUMMY VIDEO ID (Guaranteed Working)
        YouTubePlayer(
            videoId = "dQw4w9WgXcQ",
            modifier = if (isLandscape)
                Modifier.fillMaxSize()
            else
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
        )

        if (!isLandscape) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = LOREM_IPSUM,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (video.pdfs.isNotEmpty()) {
                PdfDownloadSection(
                    pdfs = video.pdfs,
                    videoId = video.id,
                    userId = userId,
                    onOpenPdf = onOpenPdf,
                    onDownloadPdf = { v, p, title ->
                        viewModel.downloadPdf(v, p, title)
                    },
                    downloadingPdfId = downloadingPdfId,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
