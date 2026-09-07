package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Movie
import com.example.data.model.EpgProgram
import com.example.data.model.ServerStatusResponse
import com.example.data.network.NetworkClient
import com.example.data.player.MediaCacheManager
import com.example.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AppScreen {
    object Home : AppScreen
    data class Detail(val movie: Movie) : AppScreen
    data class Player(val movie: Movie) : AppScreen
}

data class CineStreamUiState(
    val currentScreen: AppScreen = AppScreen.Home,
    val movies: List<Movie> = emptyList(),
    val liveChannels: List<Movie> = emptyList(),
    val liveChannelsByCategory: Map<String, List<Movie>> = emptyMap(),
    val selectedLiveCategory: String = "Todos",
    val availableLiveCategories: List<String> = listOf("Todos", "Noticias", "Deportes", "Películas", "Entretenimiento", "Música", "Infantil", "Ciencia", "Gaming"),
    val activeTab: String = "Inicio", // "Inicio" o "TV en Vivo"
    val featuredMovie: Movie? = null,
    val selectedGenre: String = "Todos",
    val availableGenres: List<String> = listOf("Todos", "En Vivo", "Ciencia Ficción", "Acción", "Drama", "Suspenso", "Aventura"),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val serverStatus: ServerStatusResponse? = null,
    val serverUrl: String = "http://10.0.2.2:8000/",
    val isServerConfigOpen: Boolean = false,
    val localCacheSizeMb: Double = 0.0,
    val errorMessage: String? = null,
    val epgPrograms: List<EpgProgram> = emptyList(),
    val epgLoading: Boolean = false
)

class CineStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MovieRepository()

    private val _uiState = MutableStateFlow(
        CineStreamUiState(
            serverUrl = NetworkClient.getBaseUrl()
        )
    )
    val uiState: StateFlow<CineStreamUiState> = _uiState.asStateFlow()

    init {
        refreshCatalog()
        checkServer()
        updateCacheSize()
    }

    fun selectTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == "TV en Vivo" && _uiState.value.liveChannels.size <= 6) {
            refreshLiveChannels()
        }
    }

    fun onLiveCategorySelected(category: String) {
        _uiState.update { it.copy(selectedLiveCategory = category) }
        refreshLiveChannels(category)
    }

    fun refreshLiveChannels(category: String = _uiState.value.selectedLiveCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.getLiveChannels(category)
            val channels = result.getOrDefault(emptyList())

            // Agrupar por categoría
            val byCat = channels.groupBy { it.genre }

            _uiState.update {
                it.copy(
                    liveChannels = channels,
                    liveChannelsByCategory = byCat,
                    isLoading = false
                )
            }
        }
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val currentGenre = _uiState.value.selectedGenre
            val currentSearch = _uiState.value.searchQuery

            // Cargar canales en vivo destacados ligeros para el carrusel de Inicio
            val liveChannels = repository.getFeaturedLiveChannels()
            val byCat = liveChannels.groupBy { it.genre }

            val result = repository.getMovies(currentGenre, currentSearch)
            result.onSuccess { list ->
                val combinedList = if (currentGenre == "En Vivo") {
                    liveChannels
                } else if (currentGenre == "Todos") {
                    list
                } else {
                    list
                }

                val featured = combinedList.firstOrNull { it.isFeatured } ?: combinedList.firstOrNull()
                _uiState.update {
                    it.copy(
                        movies = combinedList,
                        liveChannels = if (it.liveChannels.size <= 6) liveChannels else it.liveChannels,
                        liveChannelsByCategory = if (it.liveChannels.size <= 6) byCat else it.liveChannelsByCategory,
                        featuredMovie = featured,
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        liveChannels = if (it.liveChannels.size <= 6) liveChannels else it.liveChannels,
                        liveChannelsByCategory = if (it.liveChannels.size <= 6) byCat else it.liveChannelsByCategory,
                        movies = if (currentGenre == "En Vivo") liveChannels else emptyList(),
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Error cargando catálogo"
                    )
                }
            }
        }
    }

    fun onGenreSelected(genre: String) {
        if (genre == "En Vivo") {
            // Transición inteligente: cambia a la pestaña "TV en Vivo" para cargar los más de 2400 canales completos
            _uiState.update { it.copy(activeTab = "TV en Vivo", selectedGenre = "Todos") }
            refreshLiveChannels("Todos")
        } else {
            _uiState.update { it.copy(selectedGenre = genre) }
            refreshCatalog()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshCatalog()
    }

    fun navigateToDetail(movie: Movie) {
        _uiState.update { it.copy(currentScreen = AppScreen.Detail(movie)) }
    }

    fun loadEpg(movie: Movie) {
        if (!movie.isLive || movie.sourceId <= 0) {
            _uiState.update { it.copy(epgPrograms = emptyList(), epgLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(epgLoading = true) }
            val result = repository.getChannelEpg(movie)
            _uiState.update { it.copy(epgPrograms = result.getOrDefault(emptyList()), epgLoading = false) }
        }
    }

    fun navigateToPlayer(movie: Movie) {
        _uiState.update { it.copy(currentScreen = AppScreen.Player(movie)) }
    }

    fun navigateBack() {
        _uiState.update {
            val nextScreen = when (it.currentScreen) {
                is AppScreen.Player -> {
                    // Si estaba en el reproductor, vuelve al detalle de esa película
                    AppScreen.Detail(it.currentScreen.movie)
                }
                is AppScreen.Detail -> AppScreen.Home
                else -> AppScreen.Home
            }
            it.copy(currentScreen = nextScreen)
        }
        updateCacheSize()
    }

    fun openServerConfig() {
        _uiState.update { it.copy(isServerConfigOpen = true) }
        checkServer()
    }

    fun closeServerConfig() {
        _uiState.update { it.copy(isServerConfigOpen = false) }
    }

    fun updateServerUrl(newUrl: String) {
        repository.updateServerUrl(newUrl)
        _uiState.update { it.copy(serverUrl = newUrl) }
        checkServer()
        refreshCatalog()
    }

    fun checkServer() {
        viewModelScope.launch {
            val res = repository.checkServerStatus()
            res.onSuccess { status ->
                _uiState.update { it.copy(serverStatus = status) }
            }
        }
    }

    fun clearVideoCache() {
        try {
            MediaCacheManager.clearCache(getApplication())
            updateCacheSize()
        } catch (e: Exception) {
            // No-op
        }
    }

    fun updateCacheSize() {
        try {
            val size = MediaCacheManager.getCacheSizeMb(getApplication())
            _uiState.update { it.copy(localCacheSizeMb = size) }
        } catch (e: Exception) {
            // No-op
        }
    }
}
