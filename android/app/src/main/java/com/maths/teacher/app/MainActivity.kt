package com.maths.teacher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maths.teacher.app.data.api.ApiClient
import com.maths.teacher.app.data.repository.DefaultVideoRepository
import com.maths.teacher.app.ui.home.HomeScreen
import com.maths.teacher.app.ui.home.HomeViewModel
import com.maths.teacher.app.ui.home.HomeViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val repository = DefaultVideoRepository(ApiClient.api)
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(repository)
            )
            HomeScreen(viewModel = homeViewModel)
        }
    }
}
