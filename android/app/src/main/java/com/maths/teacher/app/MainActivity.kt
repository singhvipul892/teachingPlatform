package com.maths.teacher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maths.teacher.app.data.api.ApiClient
import com.maths.teacher.app.data.repository.DefaultVideoRepository
import com.maths.teacher.app.ui.home.HomeScreen
import com.maths.teacher.app.ui.home.HomeViewModel
import com.maths.teacher.app.ui.home.HomeViewModelFactory
import com.maths.teacher.app.ui.pdfviewer.PdfViewerScreen
import com.maths.teacher.app.ui.resources.ResourcesScreen
import com.maths.teacher.app.ui.resources.ResourcesViewModel
import com.maths.teacher.app.ui.resources.ResourcesViewModelFactory
import com.maths.teacher.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val navController = rememberNavController()
                val repository = DefaultVideoRepository(ApiClient.api)

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        val homeViewModel: HomeViewModel = viewModel(
                            factory = HomeViewModelFactory(repository)
                        )
                        HomeScreen(
                            viewModel = homeViewModel,
                            navController = navController
                        )
                    }
                    composable("resources") {
                        val resourcesViewModel: ResourcesViewModel = viewModel(
                            factory = ResourcesViewModelFactory(repository)
                        )
                        ResourcesScreen(
                            viewModel = resourcesViewModel,
                            navController = navController
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
