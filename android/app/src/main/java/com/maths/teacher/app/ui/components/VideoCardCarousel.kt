package com.maths.teacher.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.domain.model.Video
import kotlinx.coroutines.launch

@Composable
fun VideoCardCarousel(
    videos: List<Video>,
    onVideoSelected: (Long) -> Unit,
    api: TeacherApi,
    userId: Long?,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)
    ) {
        items(videos) { video ->
            VideoCard(
                video = video,
                onVideoSelected = onVideoSelected,
                api = api,
                userId = userId,
                onOpenPdf = onOpenPdf
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    onVideoSelected: (Long) -> Unit,
    api: TeacherApi,
    userId: Long?,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit
) {
    AppTooltip(text = "${video.title}${video.duration?.let { "\nDuration: $it" } ?: ""}") {
        val cardWidth = 264.dp // 220.dp * 1.2 (20% increase)
        val cardBorderRadius = 25.dp
        val thumbnailBorderRadius = 20.dp
        val cardPadding = 12.dp

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var isDownloading by remember(video.id) { mutableStateOf(false) }
        val pdf = video.pdfs.firstOrNull()

        Card(
            modifier = Modifier
                .size(width = cardWidth, height = 220.dp)
                .shadow(
                    elevation = 9.dp,
                    shape = RoundedCornerShape(cardBorderRadius),
                    spotColor = Color.Black.copy(alpha = 0.49f),
                    ambientColor = Color.Black.copy(alpha = 0.49f),
                    clip = false
                )
                .clickable { onVideoSelected(video.id) },
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(cardBorderRadius),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.ui.graphics.Color.White
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Thumbnail with play overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(horizontal = cardPadding, vertical = cardPadding)
                ) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(thumbnailBorderRadius)),
                        contentScale = ContentScale.Crop
                    )
                    // Play button overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .size(58.dp)
                                .alpha(0.8f)
                        )
                    }
                }

                // Video info: title + PDF action (only when a PDF is available)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = cardPadding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )

                    if (pdf != null) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Download and open PDF",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(enabled = !isDownloading) {
                                        isDownloading = true
                                        scope.launch {
                                            try {
                                                ensurePdfDownloaded(
                                                    context = context,
                                                    api = api,
                                                    userId = userId,
                                                    videoId = video.id,
                                                    pdf = pdf
                                                )
                                                onOpenPdf(video.id, pdf.id)
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Failed to open PDF: ${e.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } finally {
                                                isDownloading = false
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
