package com.example.data.repository

import android.content.Context
import com.example.data.model.Movie
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.xml.parsers.DocumentBuilderFactory

/** A small, refreshable catalog of full movies published in YouTube playlists by their channels. */
internal class YouTubeMoviesCatalog(private val context: Context) {
    internal data class PlaylistSource(val name: String, val playlistId: String, val publisherMarker: String)

    companion object {
        // Curated playlists from film distributors / publishers. RSS contains only public YouTube video IDs;
        // This feed value is retained as a stable video-ID locator; playback is resolved by the GPL NewPipe engine.
        val sources = listOf(
            PlaylistSource("Film&Clips · Películas completas en español", "PL_ip4tli3pJXxQLtgyl5-yqoLLS1lvN51", "Film&Clips"),
            PlaylistSource("Film&Clips · Películas completas 4K", "PL01FPMJBuf6mOfnPadVdHmo5Qgyk-fKKJ", "Film&Clips"),
            PlaylistSource("Vision Video · Spanish Full Movies", "PL9cJRl8EYnLcnTz0_HRwRUmU5H3XMjwF0", "Vision Video"),
            PlaylistSource("Lifted · Películas en español", "PL5TwNUJx95KA4jPE9pijsucvCSPNcc7Aj", "Lifted"),
            PlaylistSource("Deep C Digital · Películas en español", "PLXNY08O_Iy2lG9TKvg8rIVsRupPbjc6se", "Deep C Digital"),
            PlaylistSource("Mosfilm · Largometrajes con subtítulos en español", "PL7EqAsBxqGgiclckrDzyS9hYtGpIKEngQ", "Mosfilm"),
            PlaylistSource("Film&Clips · Películas completas en español (otra colección)", "PL_ip4tli3pJUfvei1-CR8KDA_RVQHcRgg", "Film&Clips"),
            PlaylistSource("Movie Central · Películas completas en español", "PLE-F9hLrTNLaWg5TnusLPmljYyOXMKkV1", "Movie Central"),
            PlaylistSource("Movie Central · Películas de acción", "PLE-F9hLrTNLbFnjrPsx4AIdAlrr22sk1G", "Movie Central"),
            PlaylistSource("Movie Central · Películas familiares", "PLE-F9hLrTNLZPbvD_WoGjfgbpVMTUwK8s", "Movie Central"),
            PlaylistSource("Movie Central · Películas clásicas", "PLE-F9hLrTNLafdu0nUt3mudcamcpOtBDY", "Movie Central"),
            PlaylistSource("FilmIsNow Movies+ · Películas completas en español latino", "PL1VQG1QhVNxX78j3ubKzGywWtSel93OQk", "FilmIsNow Movies+"),
            PlaylistSource("Romance Channel · Películas completas en español", "PLf5AXxQiJym_LGZRO6iP380FsLEVdYpGV", "Romance Channel"),
            PlaylistSource("Filmelier TV LATAM · Películas completas", "PLmw5k159xVUji8xNLB4XhqRD8YBKcCarp", "Filmelier TV LATAM"),
            PlaylistSource("Boxoffice · Películas completas en español", "PLgALNYLXbHkvj3CNPzOz3Yg_1A6BOgLio", "Boxoffice")
        )

        private const val YOUTUBE_EMBED = "https://www.youtube-nocookie.com/embed/"
        private const val YOUTUBE_WATCH = "https://youtube.com/watch?v="
        private const val ATOM_NS = "http://www.w3.org/2005/Atom"
        private const val YT_NS = "http://www.youtube.com/xml/schemas/2015"
        private const val MEDIA_NS = "http://search.yahoo.com/mrss/"
        private val fullMoviePattern = Regex(
            "(?iu)(pel[ií]cula\\s+(completa|entera|b[eé]lica|dram[aá]tica)|pel[ií]culas\\s+completas|full\\s+(length\\s+)?movie|full\\s+film|full\\s+movies|feature\\s+film|pellicola\\s+completa|subt[ií]tulos?\\s+en\\s+espa[nñ]ol|subtitles?\\s+in\\s+spanish)"
        )
        private val rejectPattern = Regex("(?iu)(\\btrailer\\b|\\bteaser\\b|(?:^|[-|:–])\\s*clips?\\b|\\bshorts?\\b|behind the scenes|\\binterview\\b|\\brecap\\b|\\bepisode\\b|\\bepisodio\\b|making of)")

        internal fun parseFeed(xml: String, source: PlaylistSource): List<Movie> {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            val entries = doc.getElementsByTagNameNS(ATOM_NS, "entry")
            return buildList {
                for (i in 0 until entries.length) {
                    val entry = entries.item(i) as? Element ?: continue
                    val id = entry.firstText(YT_NS, "videoId").trim()
                    val title = entry.firstText(ATOM_NS, "title").trim()
                    if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) continue
                    val channel = entry.getElementsByTagNameNS(ATOM_NS, "author").item(0) as? Element
                    val channelName = channel?.firstText(ATOM_NS, "name")?.trim().orEmpty().ifBlank { source.name }
                    if (!channelName.contains(source.publisherMarker, ignoreCase = true)) continue
                    if (!fullMoviePattern.containsMatchIn(title) || rejectPattern.containsMatchIn(title)) continue
                    val group = entry.getElementsByTagNameNS(MEDIA_NS, "group").item(0) as? Element
                    val thumbnail = (group?.getElementsByTagNameNS(MEDIA_NS, "thumbnail")?.item(0) as? Element)
                        ?.getAttribute("url").orEmpty().ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" }
                    val summary = entry.firstText(ATOM_NS, "summary").trim()
                    val synopsis = listOf("Canal: $channelName", "Lista: ${source.name}")
                        .plus(summary.takeIf { it.isNotBlank() }?.let { "Descripción publicada en YouTube: $it" })
                        .joinToString("\n\n")
                    val genre = genreFor(title, "$summary ${source.name}")
                    add(
                        Movie(
                            id = id.hashCode(),
                            title = title,
                            synopsis = synopsis,
                            posterUrl = thumbnail,
                            backdropUrl = thumbnail,
                            genre = genre,
                            durationMin = 0,
                            releaseYear = 0,
                            rating = -1.0,
                            quality = "YouTube · $channelName",
                            sizeBytes = 0,
                            isFeatured = false,
                            streamUrl = YOUTUBE_EMBED + id + "?playsinline=1&rel=0&autoplay=1",
                            archiveIdentifier = "",
                            creator = channelName,
                            sourceUrl = YOUTUBE_WATCH + id
                        )
                    )
                }
            }
        }

        private fun Element.firstText(namespace: String, local: String): String =
            getElementsByTagNameNS(namespace, local).item(0)?.textContent.orEmpty()

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
                listOf("drama", "dramática", "dramatico", "dramática", "dramatic").any(text::contains) -> "Drama"
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

    suspend fun load(): List<Movie> = withContext(Dispatchers.IO) {
        val client = com.example.data.network.NetworkClient.okHttpClient
            .newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        val movies = LinkedHashMap<String, Movie>()
        var lastError: Exception? = null
        for (source in sources) {
            try {
                val playlist = URLEncoder.encode(source.playlistId, "UTF-8")
                val request = Request.Builder()
                    .url("https://youtube.com/feeds/videos.xml?playlist_id=$playlist")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "No pude leer la lista ${source.name} (HTTP ${response.code})" }
                    val xml = response.body?.string().orEmpty()
                    parseFeed(xml, source).forEach { movie ->
                        val videoId = movie.streamUrl.substringAfterLast("/embed/").substringBefore('?')
                        movies.putIfAbsent(videoId, movie)
                    }
                }
            } catch (error: Exception) {
                lastError = error
                android.util.Log.w("YouTubeMoviesCatalog", "Omití una lista temporalmente inaccesible: ${source.name}", error)
                if (error is IOException) break
            }
        }
        // NewPipe search supplements the rotating playlist RSS feeds with more channels/videos.
        try {
            com.example.data.youtube.YouTubeSearchCatalog().searchMovies().forEach { movie ->
                val videoId = movie.streamUrl.substringAfterLast('/').substringBefore('?')
                movies.putIfAbsent(videoId, movie)
            }
        } catch (error: Exception) {
            lastError = error
            android.util.Log.w("YouTubeMoviesCatalog", "YouTube search did not complete", error)
        }
        if (movies.isEmpty()) throw lastError ?: IllegalStateException("YouTube no devolvió películas completas en español.")
        movies.values.sortedWith(compareBy<Movie> { it.genre.lowercase(Locale.ROOT) }.thenBy { it.title.lowercase(Locale.ROOT) })
    }
}
