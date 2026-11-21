package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                    onOpenServerConfig = {},
                    onRefresh = { viewModel.refreshCatalog(refreshOnline = true) },
                    onSelectTab = { tab -> viewModel.selectTab(tab) },
                    onLiveCategorySelect = { cat -> viewModel.onLiveCategorySelected(cat) }
                )
            }
            is AppScreen.Detail -> {
                LaunchedEffect(screen.movie.sourceId) { viewModel.loadEpg(screen.movie) }
                DetailScreen(
                    movie = screen.movie,
                    onBack = { viewModel.navigateBack() },
                    onPlay = { movie -> viewModel.navigateToPlayer(movie) },
                    epgPrograms = uiState.epgPrograms
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

    if (uiState.playbackLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparando película") },
            text = {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    androidx.compose.foundation.layout.Spacer(Modifier.width(16.dp))
                    Text("Abriendo reproductor oficial de YouTube…")
                }
            },
            confirmButton = {}
        )
    }
    uiState.playbackError?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPlaybackError() },
            title = { Text("No se pudo reproducir") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { viewModel.dismissPlaybackError() }) { Text("Entendido") } }
        )
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
