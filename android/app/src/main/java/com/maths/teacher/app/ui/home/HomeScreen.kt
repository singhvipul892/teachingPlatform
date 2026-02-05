package com.maths.teacher.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.prefs.SessionManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maths.teacher.app.ui.components.AppFooter
import com.maths.teacher.app.ui.components.AppHeader
import com.maths.teacher.app.ui.components.AppNavigationDrawer
import com.maths.teacher.app.ui.components.FooterLink
import com.maths.teacher.app.ui.components.NavigationItem
import com.maths.teacher.app.ui.components.VideoCardCarousel
import com.maths.teacher.app.R
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    navController: NavController,
    sessionManager: SessionManager,
    api: TeacherApi
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayName by sessionManager.displayName.collectAsStateWithLifecycle(initialValue = null)
    val userId by sessionManager.userId.collectAsStateWithLifecycle(initialValue = null)
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navigationItems = listOf(
        NavigationItem(
            title = "Home",
            icon = Icons.Default.Home,
            onClick = {
                scope.launch { drawerState.close() }
            }
        ),
        NavigationItem(
            title = "Resources",
            icon = Icons.Default.Folder,
            onClick = {
                scope.launch { drawerState.close() }
                navController.navigate("resources")
            }
        ),
        NavigationItem(
            title = "Courses",
            icon = Icons.Default.MenuBook,
            onClick = {
                scope.launch { drawerState.close() }
                // TODO: Navigate to courses
            }
        ),
        NavigationItem(
            title = "About",
            icon = Icons.Default.Info,
            onClick = {
                scope.launch { drawerState.close() }
                // TODO: Navigate to about
            }
        ),
        NavigationItem(
            title = "Contact",
            icon = Icons.Default.Mail,
            onClick = {
                scope.launch { drawerState.close() }
                // TODO: Navigate to contact
            }
        ),
        NavigationItem(
            title = "Settings",
            icon = Icons.Default.Settings,
            onClick = {
                scope.launch { drawerState.close() }
                // TODO: Navigate to settings
            }
        )
    )

    val footerLinks = listOf(
        FooterLink(text = "Privacy Policy") {
            // TODO: Handle privacy policy click
        },
        FooterLink(text = "Terms of Service") {
            // TODO: Handle terms click
        },
        FooterLink(text = "Contact Us") {
            // TODO: Handle contact click
        }
    )

    AppNavigationDrawer(
        drawerState = drawerState,
        navigationItems = navigationItems,
        onItemClick = { item -> item.onClick() },
        displayName = displayName,
        onLogout = {
            scope.launch {
                drawerState.close()
                sessionManager.clearSession()
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                AppHeader(
                    onNavigationClick = {
                        scope.launch {
                            drawerState.open()
                        }
                    }
                )
            },
            bottomBar = {
                AppFooter(links = footerLinks)
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when {
                    uiState.isLoading -> {
                        LoadingState()
                    }
                    uiState.errorMessage != null -> {
                        ErrorState(uiState.errorMessage ?: "Something went wrong.")
                    }
                    uiState.sections.isEmpty() -> {
                        EmptyState()
                    }
                    else -> {
                        HomeContent(
                            sections = uiState.sections,
                            onVideoSelected = { id, sectionName -> navController.navigate("video_detail/$id/${java.net.URLEncoder.encode(sectionName, "UTF-8")}") },
                            displayName = displayName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = "No content available yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun HomeContent(
    sections: List<com.maths.teacher.app.domain.model.SectionWithVideos>,
    onVideoSelected: (Long, String) -> Unit,
    displayName: String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome text with avatar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.profile),
                    contentDescription = "User Avatar",
                    modifier = Modifier
                        .size(34.dp)
                        .border(2.dp, com.maths.teacher.app.ui.theme.PrimaryBlueLight.copy(alpha = 0.8f), CircleShape)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = "Namaste, ${displayName?.split(" ")?.joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } } ?: "User"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = com.maths.teacher.app.ui.theme.AccentSaffron,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        itemsIndexed(sections) { index, section ->
            SectionBlock(section, onVideoSelected = { id -> onVideoSelected(id, section.name) })
            if (index < sections.size - 1) {
                // Divider with 20% left and right spacing (60% width in center)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.weight(0.2f))
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .height(1.dp)
                            .background(androidx.compose.ui.graphics.Color(0xFFD6DDE7))
                    )
                    Spacer(modifier = Modifier.weight(0.2f))
                }
            }
        }
    }
}

@Composable
private fun SectionBlock(
    section: com.maths.teacher.app.domain.model.SectionWithVideos,
    onVideoSelected: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = section.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 0.dp)
        )
        VideoCardCarousel(
            videos = section.videos,
            onVideoSelected = onVideoSelected
        )
    }
}
