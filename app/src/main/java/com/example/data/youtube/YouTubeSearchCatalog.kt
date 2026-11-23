package com.example.data.youtube

import android.util.Log
import com.example.data.model.Movie
import com.example.data.repository.YouTubeMoviesCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/** Uses CineStream's bundled NewPipe/La Clave extractor and discards anything not clearly a full Spanish film. */
internal class YouTubeSearchCatalog {
    companion object {
        private const val TAG = "YouTubeSearchCatalog"
        // YouTube search is paginated; broader genre queries find many more full films than a short fixed seed.
        private const val MAX_PAGES_PER_QUERY = 4
        private val queries = listOf(
            "película completa en español",
            "película completa español latino",
            "películas completas español latino",
            "película completa castellano",
            "película completa doblada al español",
            "película completa subtitulada en español",
            "largometraje completo en español",
            "full movie Spanish",
            "full movie Latin Spanish",
            "película completa acción español",
            "película completa aventura español",
            "película completa comedia español",
            "película completa drama español",
            "película completa romántica español",
            "película completa terror español",
            "película completa suspenso español",
            "película completa ciencia ficción español",
            "película completa western español",
            "película completa vaqueros español",
            "película completa crimen español",
            "película completa policiaca español",
            "película completa bélica español",
            "película completa guerra español",
            "película completa familiar español",
            "película completa animación español",
            "película completa mexicana español",
            "película completa argentina español",
            "película completa española castellano",
            "película completa latinoamericana español",
            "película completa cine clásico español",
            "película completa documental español",
            "película completa subtítulos español latino",
            "película completa doblaje español latino",
            "película de acción full movie español",
            "película de terror full movie español",
            "película de ciencia ficción full movie español"
        )
        private val fullLengthPattern = Regex(
            "(?iu)(pel[ií]cula(s)?\\s+(completa(s)?|entera(s)?|\\bfull\\b)|full\\s+(length\\s+)?movie|full\\s+film|feature\\s+film|largometraje|filme\\s+completo)"
        )
        private val spanishPattern = Regex(
            "(?iu)(espa[nñ]ol|spanish|castellano|latino|latina|doblad[oa]s?|subt[ií]tulos?\\s+en\\s+espa[nñ]ol|subtitles?\\s+in\\s+spanish)"
        )
        private val rejectPattern = Regex(
            "(?iu)(\\btrailer\\b|\\bteaser\\b|\\bshorts?\\b|\\bclip(s)?\\b|behind the scenes|\\binterview\\b|\\brecap\\b|\\bepisode\\b|\\bepisodio\\b|making of)"
        )

        internal fun isFullSpanishMovie(title: String, description: String, durationSeconds: Long): Boolean {
            // Do not impose a runtime cutoff: the extractor's movie/title signals decide inclusion.
            val text = "$title $description"
            return fullLengthPattern.containsMatchIn(text) &&
                spanishPattern.containsMatchIn(text) &&
                !rejectPattern.containsMatchIn(title)
        }

        private fun StreamInfoItem.toMovie(): Movie? {
            val title = name?.trim().orEmpty()
            val durationSeconds = duration
            val videoId = Regex("(?:v=|youtu\\.be/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1) ?: return null
            if (!isFullSpanishMovie(title, shortDescription.orEmpty(), durationSeconds)) return null
            val channel = uploaderName.orEmpty().ifBlank { "Canal de YouTube" }
            val image = thumbnails.maxByOrNull { it.height }?.url.orEmpty()
            val synopsis = listOfNotNull(
                "Canal: $channel",
                "Duración informada por el motor: ${durationSeconds / 60} min",
                shortDescription?.takeIf(String::isNotBlank)?.take(500)
            ).joinToString("\n\n")
            return Movie(
                id = videoId.hashCode(),
                title = title,
                synopsis = synopsis,
                posterUrl = image,
                backdropUrl = image,
                genre = YouTubeMoviesCatalog.genreFor(title, shortDescription.orEmpty()),
                durationMin = durationSeconds.toInt() / 60,
                releaseYear = 0,
                rating = -1.0,
                quality = "YouTube · $channel",
                sizeBytes = 0,
                isFeatured = false,
                streamUrl = "https://www.youtube-nocookie.com/embed/$videoId",
                archiveIdentifier = "",
                creator = channel,
                sourceUrl = "https://youtube.com/watch?v=$videoId"
            )
        }
    }

    suspend fun searchMovies(): List<Movie> = withContext(Dispatchers.IO) {
        val youtube = NewPipe.getService(ServiceList.YouTube.serviceId)
        val byVideoId = LinkedHashMap<String, Movie>()
        for (query in queries) {
            try {
                var page: Page? = null
                var pageNumber = 0
                do {
                    val handler = youtube.searchQHFactory.fromQuery(query, emptyList(), null)
                    val results = if (page == null) {
                        val info = SearchInfo.getInfo(youtube, handler)
                        page = info.nextPage
                        info.relatedItems
                    } else {
                        val info = SearchInfo.getMoreItems(youtube, handler, page!!)
                        page = info.nextPage
                        info.items
                    }
                    results.filterIsInstance<StreamInfoItem>().forEach { item ->
                        val movie = item.toMovie() ?: return@forEach
                        val id = movie.streamUrl.substringAfterLast('/').substringBefore('?')
                        byVideoId.putIfAbsent(id, movie)
                    }
                    pageNumber++
                    if (page != null && pageNumber < MAX_PAGES_PER_QUERY) delay(125)
                } while (page != null && pageNumber < MAX_PAGES_PER_QUERY)
            } catch (error: Exception) {
                Log.w(TAG, "Search query did not complete: $query", error)
            }
        }
        byVideoId.values.sortedWith(compareBy<Movie> { it.genre.lowercase(Locale.ROOT) }.thenBy { it.title.lowercase(Locale.ROOT) })
    }
}
