package com.maths.teacher.app

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maths.teacher.app.data.prefs.SessionManager
import com.maths.teacher.app.ui.auth.LoginScreen
import com.maths.teacher.app.ui.auth.LoginViewModel
import com.maths.teacher.app.ui.auth.SignupScreen
import com.maths.teacher.app.ui.auth.SignupViewModel
import com.maths.teacher.app.ui.home.HomeScreen
import com.maths.teacher.app.ui.home.HomeViewModel
import com.maths.teacher.app.ui.pdfviewer.PdfViewerScreen
import com.maths.teacher.app.ui.resources.ResourcesScreen
import com.maths.teacher.app.ui.resources.ResourcesViewModel
import com.maths.teacher.app.ui.videodetail.VideoDetailScreen
import com.maths.teacher.app.ui.videodetail.VideoDetailViewModel
import com.maths.teacher.app.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.window_background)))

        setContent {
            AppTheme(darkTheme = false) {
                var isSessionReady by remember { mutableStateOf(false) }
                var startDestination by remember { mutableStateOf("login") }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.Default) {
                        sessionManager.loadFromStore()
                    }
                    startDestination = if (!sessionManager.currentToken.isNullOrBlank()) "home" else "login"
                    isSessionReady = true
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (!isSessionReady) {
                        SplashContent()
                    } else {
                        val navController = rememberNavController()
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("login") {
                                val loginViewModel: LoginViewModel = hiltViewModel()
                                LoginScreen(
                                    viewModel = loginViewModel,
                                    navController = navController
                                )
                            }
                            composable("signup") {
                                val signupViewModel: SignupViewModel = hiltViewModel()
                                SignupScreen(
                                    viewModel = signupViewModel,
                                    navController = navController
                                )
                            }
                            composable("home") {
                                val homeViewModel: HomeViewModel = hiltViewModel()
                                HomeScreen(
                                    viewModel = homeViewModel,
                                    navController = navController
                                )
                            }
                            composable("resources") {
                                val resourcesViewModel: ResourcesViewModel = hiltViewModel()
                                ResourcesScreen(
                                    viewModel = resourcesViewModel,
                                    navController = navController
                                )
                            }
                            composable("video_detail/{videoId}/{sectionName}") {
                                val detailViewModel: VideoDetailViewModel = hiltViewModel()
                                val sectionTitle = it.arguments?.getString("sectionName")?.let { name ->
                                    java.net.URLDecoder.decode(name, "UTF-8")
                                }.takeIf { s -> !s.isNullOrBlank() }
                                VideoDetailScreen(
                                    viewModel = detailViewModel,
                                    navController = navController,
                                    sectionTitle = sectionTitle
                                )
                            }
                            composable("video_detail/{videoId}") {
                                val detailViewModel: VideoDetailViewModel = hiltViewModel()
                                VideoDetailScreen(
                                    viewModel = detailViewModel,
                                    navController = navController
                                )
                            }
                            composable("pdf_viewer/{videoId}/{pdfId}/{userId}") { backStackEntry ->
                                val videoId = backStackEntry.arguments?.getString("videoId")?.toLongOrNull() ?: 0L
                                val pdfId = backStackEntry.arguments?.getString("pdfId")?.toLongOrNull() ?: 0L
                                val userId = backStackEntry.arguments?.getString("userId")?.toLongOrNull()
                                PdfViewerScreen(
                                    videoId = videoId,
                                    pdfId = pdfId,
                                    userId = userId?.takeIf { it > 0 },
                                    navController = navController
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_loader")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val accentColor = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(280.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_loader),
                contentDescription = "Loading",
                modifier = Modifier
                    .size(48.dp)
                    .rotate(rotation),
                colorFilter = ColorFilter.tint(accentColor),
                contentScale = ContentScale.Fit
            )
        }
    }
}
