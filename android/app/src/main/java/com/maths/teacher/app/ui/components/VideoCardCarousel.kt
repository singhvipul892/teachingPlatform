package com.maths.teacher.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maths.teacher.app.domain.model.Video

@Composable
fun VideoCardCarousel(
    videos: List<Video>,
    onVideoSelected: (Long) -> Unit,
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
                onVideoSelected = onVideoSelected
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    onVideoSelected: (Long) -> Unit
) {
    AppTooltip(text = "${video.title}\nDuration: ${video.duration}") {
        val cardWidth = 264.dp // 220.dp * 1.2 (20% increase)
        val cardBorderRadius = 25.dp
        val thumbnailBorderRadius = 20.dp
        val cardPadding = 12.dp
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
                
                // Video info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = cardPadding, vertical = 8.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
