package com.example.data.repository

import android.content.Context
import com.example.data.model.Movie
import com.example.data.model.ServerStatusResponse
import com.example.data.network.CineStreamApi
import com.example.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class MovieRepository(context: Context) {

    private val archiveCatalog = InternetArchiveCatalog(context.applicationContext)
    @Volatile private var cachedArchiveMovies: List<Movie>? = null
    private var api: CineStreamApi = NetworkClient.getApi()

    fun updateServerUrl(url: String) {
        api = NetworkClient.getApi(url)
    }

    suspend fun getMovies(genre: String? = null, search: String? = null): Result<List<Movie>> = withContext(Dispatchers.IO) {
        runCatching {
            val all = cachedArchiveMovies ?: archiveCatalog.load().also { cachedArchiveMovies = it }
            all.asSequence()
                .filter { genre.isNullOrBlank() || genre.equals("Todos", true) || it.genre.equals(genre, true) }
                .filter { search.isNullOrBlank() || it.title.contains(search, true) || it.synopsis.contains(search, true) || it.subjects.any { s -> s.contains(search, true) } }
                .toList()
        }
    }

    suspend fun getArchiveGenres(): List<String> = withContext(Dispatchers.IO) {
        val all = cachedArchiveMovies ?: archiveCatalog.load().also { cachedArchiveMovies = it }
        all.map { it.genre }.distinct().sorted()
    }

    /** Resolve a declared Archive.org movie to an MP4 only when its metadata still declares the expected rights mark. */
    suspend fun resolveArchiveMovie(movie: Movie): Result<Movie> = withContext(Dispatchers.IO) {
        runCatching {
            require(movie.archiveIdentifier.isNotBlank()) { "Este título no tiene un identificador de Internet Archive." }
            val id = movie.archiveIdentifier
            val request = Request.Builder().url("https://archive.org/metadata/${encodePathSegment(id)}").get().build()
            val response = NetworkClient.okHttpClient.newCall(request).execute()
            response.use {
                check(it.isSuccessful) { "Internet Archive respondió ${it.code}. Probá de nuevo más tarde." }
                val body = it.body?.string().orEmpty()
                val metadata = JSONObject(body)
                val rights = metadata.optJSONObject("metadata")?.optString("licenseurl").orEmpty()
                check(rights == "http://creativecommons.org/publicdomain/mark/1.0/") {
                    "Esta ficha no declara la marca de dominio público que CineStream admite para reproducir dentro de la app. Podés abrir la página original y revisar los derechos y condiciones de uso."
                }
                val files = metadata.optJSONArray("files") ?: error("La ficha no contiene archivos.")
                val videoFiles = (0 until files.length()).mapNotNull { index -> files.optJSONObject(index) }
                    .filter { file ->
                        val name = file.optString("name").lowercase()
                        val format = file.optString("format").lowercase()
                        name.endsWith(".mp4") && (format.contains("mpeg4") || format.contains("h.264") || format.contains("mpeg-4")) && file.optString("source") != "metadata"
                    }
                    .sortedWith(compareBy<JSONObject>({ it.optString("source") != "derivative" }, { it.optLong("size", Long.MAX_VALUE) }))
                val file = videoFiles.firstOrNull() ?: error("No encontré un MP4 compatible para este título en Internet Archive.")
                val name = file.optString("name")
                val size = file.optString("size").toLongOrNull() ?: 0L
                movie.copy(
                    streamUrl = "https://archive.org/download/${encodePathSegment(id)}/${name.split('/').joinToString("/") { encodePathSegment(it) }}",
                    sizeBytes = size,
                    quality = "Reproducción desde Internet Archive",
                    licenseUrl = rights
                )
            }
        }
    }

    private fun encodePathSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

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
