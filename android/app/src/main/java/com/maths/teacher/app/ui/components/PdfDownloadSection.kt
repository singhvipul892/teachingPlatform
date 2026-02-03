package com.maths.teacher.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.prefs.getPdfPath
import com.maths.teacher.app.data.prefs.savePdfPath
import com.maths.teacher.app.domain.model.Pdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@Composable
fun PdfDownloadSection(
    pdfs: List<Pdf>,
    videoId: Long,
    userId: Long?,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit,
    api: TeacherApi,
    modifier: Modifier = Modifier
) {
    if (pdfs.isEmpty()) return
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Download PDFs",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        pdfs.forEach { pdf ->
            PdfDownloadCard(
                pdf = pdf,
                videoId = videoId,
                userId = userId,
                onOpenPdf = onOpenPdf,
                api = api,
                scope = scope
            )
        }
    }
}

@Composable
private fun PdfDownloadCard(
    pdf: Pdf,
    videoId: Long,
    userId: Long?,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit,
    api: TeacherApi,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    var isDownloading by remember(pdf.id) { mutableStateOf(false) }
    // Add refresh trigger to force recomposition after download completes
    var refreshTrigger by remember(pdf.id) { mutableStateOf(0) }

    // Read refreshTrigger so Compose subscribes and recomposes when it changes
    val refreshTriggerRead = refreshTrigger
    val path = getPdfPath(context, userId, videoId, pdf.id)
    val isDownloaded = path != null && File(path).exists() && refreshTriggerRead >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdf.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pdf.pdfType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isDownloaded) {
                TextButton(onClick = { onOpenPdf(videoId, pdf.id) }) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Open")
                }
            } else {
                TextButton(
                    onClick = {
                        if (isDownloading) return@TextButton
                        isDownloading = true
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    val resp = api.downloadPdf(videoId, pdf.id)
                                    val url = URL(resp.url)
                                    val conn = url.openConnection()
                                    conn.connect()
                                    val ins = conn.getInputStream()
                                    val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                        ?: context.filesDir
                                    val safeName = pdf.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
                                    val f = File(dir, "$safeName.pdf")
                                    FileOutputStream(f).use { out -> ins.copyTo(out) }
                                    f
                                }
                                savePdfPath(context, userId, videoId, pdf.id, file.absolutePath)
                                refreshTrigger++
                                Toast.makeText(context, "Downloaded: ${file.name}", Toast.LENGTH_LONG).show()
                                onOpenPdf(videoId, pdf.id)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isDownloading = false
                            }
                        }
                    },
                    enabled = !isDownloading
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (isDownloading) "Downloading…" else "Download")
                }
            }
        }
    }
}
