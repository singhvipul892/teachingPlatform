package com.maths.teacher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maths.teacher.app.data.api.ApiClient
import com.maths.teacher.app.data.prefs.SessionManager
import com.maths.teacher.app.data.repository.DefaultVideoRepository
import com.maths.teacher.app.ui.auth.LoginScreen
import com.maths.teacher.app.ui.auth.LoginViewModel
import com.maths.teacher.app.ui.auth.LoginViewModelFactory
import com.maths.teacher.app.ui.auth.SignupScreen
import com.maths.teacher.app.ui.auth.SignupViewModel
import com.maths.teacher.app.ui.auth.SignupViewModelFactory
import com.maths.teacher.app.ui.home.HomeScreen
import com.maths.teacher.app.ui.home.HomeViewModel
import com.maths.teacher.app.ui.home.HomeViewModelFactory
import com.maths.teacher.app.ui.pdfviewer.PdfViewerScreen
import com.maths.teacher.app.ui.resources.ResourcesScreen
import com.maths.teacher.app.ui.resources.ResourcesViewModel
import com.maths.teacher.app.ui.resources.ResourcesViewModelFactory
import com.maths.teacher.app.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(applicationContext)
        runBlocking { sessionManager.loadFromStore() }
        val api = ApiClient.createApi(sessionManager)
        val repository = DefaultVideoRepository(api)

        // Single NavHost from first frame avoids root composition swap, reducing window
        // lifecycle churn. DeadObjectException in WindowManager (EXITING) is a known
        // system-server race when the window is destroyed; it cannot be fixed in app code.
        setContent {
            AppTheme {
                val navController = rememberNavController()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.fillMaxSize()
                    ) {
                    composable("splash") {
                        SplashContent(
                            onSplashComplete = { destination ->
                                navController.navigate(destination) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            },
                            sessionManager = sessionManager
                        )
                    }
                    composable("login") {
                        val loginViewModel: LoginViewModel = viewModel(
                            factory = LoginViewModelFactory(api, sessionManager)
                        )
                        LoginScreen(
                            viewModel = loginViewModel,
                            navController = navController
                        )
                    }
                    composable("signup") {
                        val signupViewModel: SignupViewModel = viewModel(
                            factory = SignupViewModelFactory(api)
                        )
                        SignupScreen(
                            viewModel = signupViewModel,
                            navController = navController
                        )
                    }
                    composable("home") {
                        val homeViewModel: HomeViewModel = viewModel(
                            factory = HomeViewModelFactory(repository)
                        )
                        HomeScreen(
                            viewModel = homeViewModel,
                            navController = navController,
                            sessionManager = sessionManager,
                            api = api
                        )
                    }
                    composable("resources") {
                        val resourcesViewModel: ResourcesViewModel = viewModel(
                            factory = ResourcesViewModelFactory(repository)
                        )
                        ResourcesScreen(
                            viewModel = resourcesViewModel,
                            navController = navController,
                            sessionManager = sessionManager,
                            api = api
                        )
                    }
                    composable("pdf_viewer/{videoId}/{pdfId}") { backStackEntry ->
                        val videoId = backStackEntry.arguments?.getString("videoId")?.toLongOrNull() ?: 0L
                        val pdfId = backStackEntry.arguments?.getString("pdfId")?.toLongOrNull() ?: 0L
                        val userId by sessionManager.userId.collectAsStateWithLifecycle(initialValue = null)
                        PdfViewerScreen(
                            videoId = videoId,
                            pdfId = pdfId,
                            userId = userId,
                            navController = navController
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun SplashContent(
    onSplashComplete: (String) -> Unit,
    sessionManager: SessionManager
) {
    LaunchedEffect(Unit) {
        coroutineScope {
            val dest = async(Dispatchers.Default) {
                sessionManager.loadFromStore()
                if (!sessionManager.currentToken.isNullOrBlank()) "home" else "login"
            }
            delay(3500)
            onSplashComplete(dest.await())
        }
    }
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
