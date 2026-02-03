package com.maths.teacher.app.ui.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maths.teacher.app.data.prefs.SessionManager
import com.maths.teacher.app.ui.components.PdfDownloadSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcesScreen(
    viewModel: ResourcesViewModel,
    navController: NavController,
    sessionManager: SessionManager,
    api: com.maths.teacher.app.data.api.TeacherApi
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userId by sessionManager.userId.collectAsStateWithLifecycle(initialValue = null)
    var dropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resources") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when {
                uiState.isLoadingSections -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.errorMessage != null && uiState.sections.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Section",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.selectedSection ?: "Select section",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "Open dropdown",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            uiState.sections.forEach { section ->
                                DropdownMenuItem(
                                    text = { Text(section) },
                                    onClick = {
                                        viewModel.selectSection(section)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (!uiState.isLoadingSections && uiState.sections.isEmpty() && uiState.errorMessage == null) {
                        Text(
                            text = "No sections available. Check connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (uiState.selectedSection == null) {
                        Text(
                            text = "Select a section to see videos and PDFs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    } else {
                        when {
                            uiState.isLoadingVideos -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            uiState.errorMessage != null -> {
                                Text(
                                    text = uiState.errorMessage!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                            uiState.videos.isEmpty() -> {
                                Text(
                                    text = "No videos with PDFs in this section.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp)
                                )
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    items(uiState.videos) { video ->
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = video.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            PdfDownloadSection(
                                                pdfs = video.pdfs,
                                                videoId = video.id,
                                                userId = userId,
                                                onOpenPdf = { v, p ->
                                                    navController.navigate("pdf_viewer/$v/$p")
                                                },
                                                api = api,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
