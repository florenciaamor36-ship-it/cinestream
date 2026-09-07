package com.example.data.repository

import com.example.data.model.Movie
import com.example.data.model.PoolTelemetry
import com.example.data.model.ServerStatusResponse
import com.example.data.model.WorkerStatus
import com.example.data.network.CineStreamApi
import com.example.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MovieRepository {

    private var api: CineStreamApi = NetworkClient.getApi()

    fun updateServerUrl(url: String) {
        api = NetworkClient.getApi(url)
    }

    suspend fun getMovies(genre: String? = null, search: String? = null): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMovies(
                genre = if (genre == "Todos") null else genre,
                search = search
            )
            if (response.isNotEmpty()) Result.success(response)
            else Result.failure(IllegalStateException("El backend no devolvió contenido"))
        } catch (e: Exception) {
            Result.failure(e)
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

    /**
     * Catálogo local de alta definición con URLs de streaming MP4 reales que soportan
     * HTTP Range Requests de forma nativa para probar la UI y el reproductor Media3.
     */
    private fun getFallbackMovies(genre: String? = null, search: String? = null): List<Movie> {
        val allMovies = listOf(
            Movie(
                id = 1,
                title = "Cyber Horizon: 2099",
                synopsis = "En una metrópolis dominada por megacorporaciones y redes cuánticas, un detective cibernético descubre una señal oculta que amenaza con desconectar la conciencia de la humanidad entera.",
                posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200&auto=format&fit=crop&q=80",
                genre = "Ciencia Ficción",
                durationMin = 138,
                releaseYear = 2025,
                rating = 8.9,
                quality = "4K Ultra HD",
                sizeBytes = 2_147_483_648L,
                isFeatured = true,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                isSeries = false
            ),
            Movie(
                id = 2,
                title = "Sombra en la Niebla",
                synopsis = "Un agente encubierto en el puerto de Róterdam debe infiltrarse en un sindicato internacional de contrabando sin ser detectado por los algoritmos de vigilancia biométrica.",
                posterUrl = "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
                genre = "Acción",
                durationMin = 115,
                releaseYear = 2024,
                rating = 8.3,
                quality = "1080p Full HD",
                sizeBytes = 1_400_000_000L,
                isFeatured = false,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                isSeries = false
            ),
            Movie(
                id = 3,
                title = "El Eco del Silencio",
                synopsis = "Tras un misterioso cataclismo acústico que destruyó las telecomunicaciones globales, dos supervivientes cruzan el macizo alpino guiados por frecuencias de radio analógicas.",
                posterUrl = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
                genre = "Drama",
                durationMin = 124,
                releaseYear = 2024,
                rating = 8.6,
                quality = "4K HDR",
                sizeBytes = 1_850_000_000L,
                isFeatured = false,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                isSeries = false
            ),
            Movie(
                id = 4,
                title = "Código Rojo: Vuelo 814",
                synopsis = "A 35,000 pies de altura, un grupo de piratas informáticos toma el control de los sistemas de navegación de un avión comercial transoceánico.",
                posterUrl = "https://images.unsplash.com/photo-1436491865332-7a61a109cc05?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1519074069444-1ba4ea16e6f6?w=1200&auto=format&fit=crop&q=80",
                genre = "Suspenso",
                durationMin = 108,
                releaseYear = 2025,
                rating = 7.9,
                quality = "1080p",
                sizeBytes = 1_200_000_000L,
                isFeatured = false,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                isSeries = false
            ),
            Movie(
                id = 5,
                title = "Reinos Perdidos: La Reliquia",
                synopsis = "Una expedición arqueológica en los desiertos del Kalahari descubre una bóveda prehistórica que contiene tecnología que desafía las leyes físicas contemporáneas.",
                posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=1200&auto=format&fit=crop&q=80",
                genre = "Aventura",
                durationMin = 142,
                releaseYear = 2023,
                rating = 8.1,
                quality = "4K Ultra HD",
                sizeBytes = 2_400_000_000L,
                isFeatured = false,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4",
                isSeries = false
            ),
            Movie(
                id = 101,
                title = "Chronicles of Nexa (Serie)",
                synopsis = "Serie exclusiva de Ciencia Ficción. En un futuro post-apocalíptico, clanes tecnificados luchan por el control de la última fuente de energía geotérmica del planeta.",
                posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1200&auto=format&fit=crop&q=80",
                genre = "Ciencia Ficción",
                durationMin = 45,
                releaseYear = 2026,
                rating = 9.2,
                quality = "4K Dolby Vision",
                sizeBytes = 850_000_000L,
                isFeatured = true,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                isSeries = true,
                seasons = 3,
                episodesCount = 24
            ),
            Movie(
                id = 102,
                title = "Operación Rescate (Serie)",
                synopsis = "Serie de Acción y Suspenso. Sigue a un grupo de operaciones especiales de élite en misiones de extracción de alto riesgo a lo largo de todo el mundo.",
                posterUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1200&auto=format&fit=crop&q=80",
                genre = "Acción",
                durationMin = 50,
                releaseYear = 2025,
                rating = 8.7,
                quality = "1080p Full HD",
                sizeBytes = 900_000_000L,
                isFeatured = false,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                isSeries = true,
                seasons = 2,
                episodesCount = 16
            ),
            Movie(
                id = 103,
                title = "La Última Frontera (Serie)",
                synopsis = "Serie de Drama y Supervivencia. Un grupo de colonos espaciales queda varado en un planeta helado y debe luchar contra las inclemencias del clima y sus propios conflictos internos.",
                posterUrl = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=600&auto=format&fit=crop&q=80",
                backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
                genre = "Drama",
                durationMin = 60,
                releaseYear = 2025,
                rating = 8.9,
                quality = "4K Ultra HD",
                sizeBytes = 1_100_000_000L,
                isFeatured = false,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                isSeries = true,
                seasons = 1,
                episodesCount = 8
            )
        )

        return allMovies.filter { movie ->
            val matchGenre = genre == null || genre == "Todos" || movie.genre.equals(genre, ignoreCase = true)
            val matchSearch = search.isNullOrBlank() ||
                    movie.title.contains(search, ignoreCase = true) ||
                    movie.synopsis.contains(search, ignoreCase = true)
            matchGenre && matchSearch
        }
    }
}
