package com.maths.teacher.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maths.teacher.app.data.api.TeacherApi
import com.maths.teacher.app.data.prefs.SessionManager
import com.maths.teacher.app.domain.model.CourseWithVideos
import com.maths.teacher.app.ui.components.AppFooter
import com.maths.teacher.app.ui.components.AppHeader
import com.maths.teacher.app.ui.components.AppNavigationDrawer
import com.maths.teacher.app.ui.components.FooterLink
import com.maths.teacher.app.ui.components.NavigationItem
import com.maths.teacher.app.ui.components.SearchField
import com.maths.teacher.app.ui.components.VideoCardCarousel
import com.maths.teacher.app.util.EXPIRY_WARNING_DAYS
import com.maths.teacher.app.util.formatExpiryDate
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
                    .background(androidx.compose.ui.graphics.Color(0xFFF6F9FD))
            ) {
                when {
                    uiState.isLoading -> {
                        LoadingState()
                    }
                    uiState.errorMessage != null -> {
                        ErrorState(uiState.errorMessage ?: "Something went wrong.")
                    }
                    uiState.courses.isEmpty() -> {
                        EmptyState()
                    }
                    else -> {
                        // The search field sits outside the LazyColumn so it stays reachable no
                        // matter how far down the student has scrolled.
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                SearchField(
                                    query = uiState.searchQuery,
                                    onQueryChange = viewModel::onSearchQueryChange
                                )
                            }
                            HomeContent(
                                courses = uiState.courses,
                                searchQuery = uiState.searchQuery,
                                searchResults = uiState.searchResults,
                                onVideoSelected = { id, courseName ->
                                    navController.navigate("video_detail/$id/${java.net.URLEncoder.encode(courseName, "UTF-8")}")
                                },
                                displayName = displayName,
                                api = api,
                                userId = userId,
                                onOpenPdf = { v, p ->
                                    navController.navigate("pdf_viewer/$v/$p")
                                },
                                onShowPdfList = { v ->
                                    navController.navigate("pdf_list/$v")
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
            text = "You haven't purchased any courses yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun HomeContent(
    courses: List<CourseWithVideos>,
    searchQuery: String,
    searchResults: List<CourseSearchResult>,
    onVideoSelected: (Long, String) -> Unit,
    displayName: String?,
    api: TeacherApi,
    userId: Long?,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit,
    onShowPdfList: (videoId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSearching = searchQuery.isNotBlank()
    val listState = rememberLazyListState()

    // Results are prepended, so without this a student searching from deep in the list would see
    // nothing change.
    LaunchedEffect(searchQuery) {
        listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isSearching) {
            item(key = "results-header") {
                Text(
                    text = "RESULTS FOR \"${searchQuery.trim()}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (searchResults.isEmpty()) {
                item(key = "results-empty") {
                    Text(
                        text = "No course or class matches \"${searchQuery.trim()}\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(searchResults, key = { "result-${it.course.courseId}" }) { result ->
                    SectionBlock(
                        course = result.course,
                        subtitle = if (result.matchedByName) {
                            null
                        } else {
                            "${result.course.videos.size} of ${result.totalVideos} classes"
                        },
                        onVideoSelected = { id -> onVideoSelected(id, result.course.name) },
                        api = api,
                        userId = userId,
                        onOpenPdf = onOpenPdf,
                        onShowPdfList = onShowPdfList
                    )
                }
            }

            item(key = "results-divider") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionDivider(modifier = Modifier.padding(top = 16.dp))
                    Text(
                        text = "ALL COURSES",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Welcome text with avatar - scrolls with content. Hidden while searching so results sit
        // right under the search field.
        if (!isSearching) item(key = "greeting") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(androidx.compose.ui.graphics.Color(0xFFECF3FF), CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.profile),
                            contentDescription = "User Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = "Namaste, ${displayName?.split(" ")?.joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } } ?: "User"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = com.maths.teacher.app.ui.theme.AccentSaffron,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Keys are prefixed because a course can appear in both the results region and here.
        itemsIndexed(courses, key = { _, course -> "all-${course.courseId}" }) { index, course ->
            SectionBlock(
                course = course,
                onVideoSelected = { id -> onVideoSelected(id, course.name) },
                api = api,
                userId = userId,
                onOpenPdf = onOpenPdf,
                onShowPdfList = onShowPdfList
            )
            if (index < courses.size - 1) {
                SectionDivider(modifier = Modifier.padding(top = 32.dp))
            }
        }
    }
}

@Composable
private fun SectionDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

@Composable
private fun SectionBlock(
    course: CourseWithVideos,
    onVideoSelected: (Long) -> Unit,
    api: TeacherApi,
    userId: Long?,
    onOpenPdf: (videoId: Long, pdfId: Long) -> Unit,
    onShowPdfList: (videoId: Long) -> Unit,
    subtitle: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = course.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 0.dp)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ValidityLabel(course)
        if (course.expired) {
            Text(
                text = "Your access to this course has ended. Purchase it again on the website to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            VideoCardCarousel(
                videos = course.videos,
                onVideoSelected = onVideoSelected,
                api = api,
                userId = userId,
                onOpenPdf = onOpenPdf,
                onShowPdfList = onShowPdfList
            )
        }
    }
}

/**
 * States when access ends, and turns into a warning as the date approaches.
 * Lifetime courses say nothing at all — there is no date worth the student's attention.
 */
@Composable
private fun ValidityLabel(course: CourseWithVideos) {
    val expiryDate = course.expiryDate ?: return
    val daysLeft = course.daysRemaining

    val (text, isWarning) = when {
        course.expired -> "Expired on ${formatExpiryDate(expiryDate)}" to true
        daysLeft == null -> "Valid till ${formatExpiryDate(expiryDate)}" to false
        daysLeft == 0 -> "Expires today" to true
        daysLeft == 1 -> "Expires tomorrow" to true
        daysLeft <= EXPIRY_WARNING_DAYS -> "Expires in $daysLeft days" to true
        else -> "Valid till ${formatExpiryDate(expiryDate)}" to false
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (isWarning) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
