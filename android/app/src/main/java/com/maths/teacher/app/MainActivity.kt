package com.maths.teacher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        setContent {
            AppTheme {
                var isSessionLoaded by remember { mutableStateOf(false) }
                var startDestination by remember { mutableStateOf("login") }

                LaunchedEffect(Unit) {
                    sessionManager.loadFromStore()
                    startDestination = if (!sessionManager.currentToken.isNullOrBlank()) "home" else "login"
                    isSessionLoaded = true
                }

                if (!isSessionLoaded) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
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
                                api = api
                            )
                        }
                        composable("pdf_viewer/{videoId}/{pdfId}") { backStackEntry ->
                            val videoId = backStackEntry.arguments?.getString("videoId")?.toLongOrNull() ?: 0L
                            val pdfId = backStackEntry.arguments?.getString("pdfId")?.toLongOrNull() ?: 0L
                            PdfViewerScreen(
                                videoId = videoId,
                                pdfId = pdfId,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }
}
