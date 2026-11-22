package com.example.data.youtube

import android.util.Log
import com.example.data.model.Movie
import com.example.data.repository.YouTubeMoviesCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/** Discovers additional full-length Spanish-language films beyond the small playlist RSS snapshots. */
internal class YouTubeSearchCatalog {
    companion object {
        private const val TAG = "YouTubeSearchCatalog"
        private const val MAX_PAGES_PER_QUERY = 3
        private val queries = listOf(
            "película completa en español",
            "película completa español latino",
            "película completa castellano",
            "películas completas dobladas al español",
            "largometraje completo en español",
            "película completa subtitulada en español",
            "full movie Spanish",
            "película completa Spanish dub"
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
            if (durationSeconds < 45 * 60) return false
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
                shortDescription?.takeIf(String::isNotBlank)?.take(500)
            ).joinToString("\n\n")
            return Movie(
                id = videoId.hashCode(),
                title = title,
                synopsis = synopsis,
                posterUrl = image,
                backdropUrl = image,
                genre = YouTubeMoviesCatalog.genreFor(title, shortDescription.orEmpty()),
                durationMin = durationSeconds.toInt().div(60),
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
                } while (page != null && pageNumber < MAX_PAGES_PER_QUERY)
            } catch (error: Exception) {
                Log.w(TAG, "Search query did not complete: $query", error)
            }
        }
        byVideoId.values.sortedWith(compareBy<Movie> { it.genre.lowercase(Locale.ROOT) }.thenBy { it.title.lowercase(Locale.ROOT) })
    }
}
