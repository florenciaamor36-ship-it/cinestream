package com.example.data.repository

import android.content.Context
import com.example.data.model.Movie
import com.example.data.youtube.YouTubeSearchCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/** CineStream film catalog. Live discovery is delegated to the bundled NewPipe/La Clave extractor. */
internal class YouTubeMoviesCatalog(private val context: Context) {
    companion object {
        private const val YOUTUBE_EMBED = "https://www.youtube-nocookie.com/embed/"
        private const val YOUTUBE_WATCH = "https://youtube.com/watch?v="

        internal fun genreFor(title: String, summary: String): String {
            val text = "$title $summary".lowercase(Locale.ROOT)
            return when {
                listOf("western", "vaquero", "cowboy", "oeste").any(text::contains) -> "Western"
                listOf("terror", "horror", "thriller", "suspenso", "suspense").any(text::contains) -> "Suspenso y terror"
                listOf("ciencia ficción", "ciencia ficcion", "sci-fi", "science fiction").any(text::contains) -> "Ciencia ficción"
                listOf("acción", "accion", "action", "aventura", "adventure").any(text::contains) -> "Acción y aventura"
                listOf("comedia", "comedy", "comédia").any(text::contains) -> "Comedia"
                listOf("romance", "romántica", "romantica", "romantic").any(text::contains) -> "Romance"
                listOf("documental", "documentary").any(text::contains) -> "Documental"
                listOf("bélica", "belica", "guerra", "war film", "wwii", "war movie").any(text::contains) -> "Bélica"
                listOf("crimen", "criminal", "policíaca", "policiaca", "detective", "crime").any(text::contains) -> "Crimen"
                listOf("animación", "animacion", "animated", "animation", "infantil", "para toda la familia", "family").any(text::contains) -> "Familiar y animación"
                listOf("clásico", "clasico", "classic").any(text::contains) -> "Clásicos"
                listOf("drama", "dramática", "dramatico", "dramatic").any(text::contains) -> "Drama"
                else -> "Sin clasificar"
            }
        }
    }

    fun loadBundled(): List<Movie> {
        val root = context.assets.open("youtube_movies_seed.json").bufferedReader().use { JSONObject(it.readText()) }
        val items = root.getJSONArray("items")
        return buildList(items.length()) {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    Movie(
                        id = item.optInt("id", index + 1),
                        title = item.optString("title"),
                        synopsis = item.optString("synopsis"),
                        posterUrl = item.optString("posterUrl"),
                        backdropUrl = item.optString("backdropUrl"),
                        genre = item.optString("genre", "Drama"),
                        durationMin = item.optInt("durationMin", 0),
                        releaseYear = item.optInt("releaseYear", 0),
                        rating = item.optDouble("rating", -1.0),
                        quality = item.optString("quality", "YouTube"),
                        sizeBytes = item.optLong("sizeBytes", 0L),
                        isFeatured = item.optBoolean("isFeatured", false),
                        streamUrl = item.optString("streamUrl"),
                        isLive = item.optBoolean("isLive", false),
                        isSeries = item.optBoolean("isSeries", false),
                        seasons = item.optInt("seasons", 1),
                        episodesCount = item.optInt("episodesCount", 10),
                        sourceId = item.optInt("sourceId", 0),
                        creator = item.optString("creator"),
                        sourceUrl = item.optString("sourceUrl")
                    )
                )
            }
        }
    }

    /** Keeps curated offline films and adds only full-length Spanish movie results returned by the engine. */
    suspend fun load(): List<Movie> = withContext(Dispatchers.IO) {
        val byVideoId = LinkedHashMap<String, Movie>()
        loadBundled().forEach { movie ->
            val videoId = movie.streamUrl.substringAfterLast("/embed/").substringBefore('?')
            if (videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) byVideoId.putIfAbsent(videoId, movie)
        }
        YouTubeSearchCatalog().searchMovies().forEach { movie ->
            val videoId = movie.streamUrl.substringAfterLast("/embed/").substringBefore('?')
            if (videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) byVideoId.putIfAbsent(videoId, movie)
        }
        check(byVideoId.isNotEmpty()) { "El motor no devolvió películas completas en español." }
        byVideoId.values.sortedWith(compareBy<Movie> { it.genre.lowercase(Locale.ROOT) }.thenBy { it.title.lowercase(Locale.ROOT) })
    }
}
