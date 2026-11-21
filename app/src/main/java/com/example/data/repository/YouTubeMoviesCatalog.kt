package com.example.data.repository

import com.example.data.model.Movie
import okhttp3.Request
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** A small, refreshable catalog of full movies published in YouTube playlists by their channels. */
internal class YouTubeMoviesCatalog {
    internal data class PlaylistSource(val name: String, val playlistId: String, val publisherMarker: String)

    companion object {
        // Curated playlists from film distributors / publishers. RSS contains only public YouTube video IDs;
        // playback always uses YouTube's official embedded player, never a scraped media URL.
        val sources = listOf(
            PlaylistSource("Film&Clips · Películas completas en español", "PL_ip4tli3pJXxQLtgyl5-yqoLLS1lvN51", "Film&Clips"),
            PlaylistSource("Film&Clips · Películas completas 4K", "PL01FPMJBuf6mOfnPadVdHmo5Qgyk-fKKJ", "Film&Clips"),
            PlaylistSource("Vision Video · Spanish Full Movies", "PL9cJRl8EYnLcnTz0_HRwRUmU5H3XMjwF0", "Vision Video"),
            PlaylistSource("Lifted · Películas en español", "PL5TwNUJx95KA4jPE9pijsucvCSPNcc7Aj", "Lifted"),
            PlaylistSource("Deep C Digital · Películas en español", "PLXNY08O_Iy2lG9TKvg8rIVsRupPbjc6se", "Deep C Digital"),
            PlaylistSource("Mosfilm · Largometrajes con subtítulos en español", "PL7EqAsBxqGgiclckrDzyS9hYtGpIKEngQ", "Mosfilm")
        )

        private const val YOUTUBE_EMBED = "https://www.youtube.com/embed/"
        private const val YOUTUBE_WATCH = "https://www.youtube.com/watch?v="
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
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
                runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            val entries = doc.getElementsByTagNameNS(ATOM_NS, "entry")
            buildList {
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
                    val genre = genreFor(title, summary)
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

        private fun genreFor(title: String, summary: String): String {
            val text = "$title $summary".lowercase(Locale.ROOT)
            return when {
                listOf("comedia", "comedy", "comédia").any(text::contains) -> "Comedia"
                listOf("terror", "horror", "thriller", "suspenso", "suspense").any(text::contains) -> "Suspenso y terror"
                listOf("acción", "accion", "action", "aventura", "adventure").any(text::contains) -> "Acción y aventura"
                listOf("romance", "romántica", "romantica", "romantic").any(text::contains) -> "Romance"
                listOf("documental", "documentary").any(text::contains) -> "Documental"
                else -> "Drama"
            }
        }
    }

    fun load(): List<Movie> {
        val client = com.example.data.network.NetworkClient.okHttpClient
        val movies = LinkedHashMap<String, Movie>()
        var lastError: Exception? = null
        for (source in sources) {
            try {
                val playlist = URLEncoder.encode(source.playlistId, "UTF-8")
                val request = Request.Builder()
                    .url("https://www.youtube.com/feeds/videos.xml?playlist_id=$playlist")
                    .header("User-Agent", "CineStream Android app")
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
            }
        }
        if (movies.isEmpty()) throw lastError ?: IllegalStateException("Las listas de películas de YouTube no devolvieron videos.")
        return movies.values.sortedBy { it.title.lowercase(Locale.ROOT) }
    }
}
