package com.example.data.repository

import com.example.data.model.Movie
import com.example.data.model.ServerStatusResponse
import com.example.data.network.CineStreamApi
import com.example.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MovieRepository {

    private val youtubeMoviesCatalog = YouTubeMoviesCatalog()
    @Volatile private var cachedYouTubeMovies: List<Movie>? = null
    @Volatile private var catalogFetchedAtMs: Long = 0L
    private val catalogLock = Mutex()
    private val catalogCacheTtlMs = 6 * 60 * 60 * 1000L
    private var api: CineStreamApi = NetworkClient.getApi()

    fun updateServerUrl(url: String) {
        api = NetworkClient.getApi(url)
    }

    suspend fun getMovies(genre: String? = null, search: String? = null): Result<List<Movie>> = withContext(Dispatchers.IO) {
        runCatching {
            val all = loadYouTubeMovies()
            all.asSequence()
                .filter { genre.isNullOrBlank() || genre.equals("Todos", true) || it.genre.equals(genre, true) }
                .filter {
                    search.isNullOrBlank() || it.title.contains(search, true) ||
                        it.synopsis.contains(search, true) || it.creator.contains(search, true)
                }
                .toList()
        }
    }

    suspend fun getYouTubeGenres(): List<String> = withContext(Dispatchers.IO) {
        loadYouTubeMovies().map { it.genre }.distinct().sorted()
    }

    private suspend fun loadYouTubeMovies(): List<Movie> = catalogLock.withLock {
        val cached = cachedYouTubeMovies
        val fresh = System.currentTimeMillis() - catalogFetchedAtMs < catalogCacheTtlMs
        if (cached != null && fresh) return@withLock cached
        try {
            val current = youtubeMoviesCatalog.load()
            check(current.isNotEmpty()) { "No encontré películas completas disponibles en las listas de YouTube." }
            cachedYouTubeMovies = current
            catalogFetchedAtMs = System.currentTimeMillis()
            current
        } catch (error: Exception) {
            if (cached != null) cached else throw error
        }
    }

    suspend fun getLiveChannels(category: String = "Todos"): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getIptvChannels(
                category = if (category == "Todos" || category == "En Vivo") null else category
            )
            val channels = response.map { it.toMovie() }
            if (channels.isNotEmpty()) Result.success(channels)
            else Result.failure(IllegalStateException("No hay canales IPTV importados"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFeaturedLiveChannels(): List<Movie> = withContext(Dispatchers.IO) {
        api.getIptvChannels().take(12).map { it.toMovie() }
    }

    suspend fun getChannelEpg(channel: Movie): Result<List<com.example.data.model.EpgProgram>> =
        withContext(Dispatchers.IO) {
            if (channel.sourceId <= 0) return@withContext Result.failure(IllegalArgumentException("Canal sin identificador IPTV"))
            runCatching { api.getChannelEpg(channel.sourceId) }
        }

    suspend fun checkServerStatus(): Result<ServerStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val status = api.getServerStatus()
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
