package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ServerConfigDialog
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.CineStreamTheme
import com.example.ui.theme.DarkBackground
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.CineStreamViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CineStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    CineStreamApp()
                }
            }
        }
    }
}

@Composable
fun CineStreamApp(
    viewModel: CineStreamViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Manejo de retroceso del sistema
    BackHandler(enabled = uiState.currentScreen != AppScreen.Home) {
        viewModel.navigateBack()
    }

    // Transiciones fluidas entre pantallas
    AnimatedContent(
        targetState = uiState.currentScreen,
        transitionSpec = {
            when {
                targetState is AppScreen.Player -> {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                }
                initialState is AppScreen.Player -> {
                    slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                }
                targetState is AppScreen.Detail -> {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                }
                else -> {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            }
        },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            is AppScreen.Home -> {
                HomeScreen(
                    uiState = uiState,
                    onMovieClick = { movie -> viewModel.navigateToDetail(movie) },
                    onPlayMovie = { movie -> viewModel.navigateToPlayer(movie) },
                    onGenreSelect = { genre -> viewModel.onGenreSelected(genre) },
                    onSearchChange = { query -> viewModel.onSearchQueryChanged(query) },
                    onOpenServerConfig = { viewModel.openServerConfig() },
                    onRefresh = { viewModel.refreshCatalog() },
                    onSelectTab = { tab -> viewModel.selectTab(tab) },
                    onLiveCategorySelect = { cat -> viewModel.onLiveCategorySelected(cat) }
                )
            }
            is AppScreen.Detail -> {
                DetailScreen(
                    movie = screen.movie,
                    onBack = { viewModel.navigateBack() },
                    onPlay = { movie -> viewModel.navigateToPlayer(movie) }
                )
            }
            is AppScreen.Player -> {
                PlayerScreen(
                    movie = screen.movie,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
    }

    // Diálogo de Configuración del Servidor y Granja de Bots
    if (uiState.isServerConfigOpen) {
        ServerConfigDialog(
            currentUrl = uiState.serverUrl,
            serverStatus = uiState.serverStatus,
            cacheSizeMb = uiState.localCacheSizeMb,
            onDismiss = { viewModel.closeServerConfig() },
            onSaveUrl = { newUrl -> viewModel.updateServerUrl(newUrl) },
            onRefreshStatus = { viewModel.checkServer() },
            onClearCache = { viewModel.clearVideoCache() }
        )
    }
}
