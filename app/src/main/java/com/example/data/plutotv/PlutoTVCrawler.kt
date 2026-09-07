package com.example.data.plutotv

import android.util.Log
import com.example.data.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

object PlutoTVCrawler {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Listas M3U/M3U8 de televisión en vivo (Pluto TV y canales reales en español/Latinoamérica)
    private val m3uSources = listOf(
        "https://raw.githubusercontent.com/BuddyChewChew/pluto/main/pluto_mx.m3u",
        "https://iptv-org.github.io/iptv/countries/mx.m3u",
        "https://iptv-org.github.io/iptv/languages/spa.m3u"
    )

    // Cache local de canales Pluto TV para evitar múltiples peticiones seguidas
    private var cachedChannels: List<Movie>? = null
    private var lastFetchTime: Long = 0L
    private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutos de caché

    // Catálogo fallback robusto de Pluto TV por si falla la conexión de red
    private val fallbackPlutoChannels: List<Movie> = listOf(
        Movie(
            id = "pluto_fallback_cine_estelar".hashCode(),
            title = "Pluto TV Cine Estelar",
            synopsis = "El canal ideal con las películas más grandes de Hollywood y el cine internacional. Acción, drama y suspense.",
            posterUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=1200&auto=format&fit=crop&q=80",
            genre = "Películas",
            durationMin = 0,
            releaseYear = 2026,
            rating = 8.8,
            quality = "FHD HLS",
            isFeatured = true,
            streamUrl = "http://cf-playlist-mediapackage-public-us-east-1.prod.gcp.pluto.tv/v1/channel/5fa45373a216cb0007e5cbfd/index.m3u8",
            isLive = true
        ),
        Movie(
            id = "pluto_fallback_cine_accion".hashCode(),
            title = "Pluto TV Cine Acción",
            synopsis = "Tensión, disparos, explosiones y persecuciones de coches. Los héroes de acción más duros están aquí.",
            posterUrl = "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=1200&auto=format&fit=crop&q=80",
            genre = "Películas",
            durationMin = 0,
            releaseYear = 2026,
            rating = 8.5,
            quality = "FHD HLS",
            isFeatured = false,
            streamUrl = "http://cf-playlist-mediapackage-public-us-east-1.prod.gcp.pluto.tv/v1/channel/5fa45437812bc8000787e6fa/index.m3u8",
            isLive = true
        ),
        Movie(
            id = "pluto_fallback_cine_terror".hashCode(),
            title = "Pluto TV Cine Terror",
            synopsis = "Sustos, apariciones espectrales y psicópatas despiadados. Solo apto para los más valientes.",
            posterUrl = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1509248961158-e54f6934749c?w=1200&auto=format&fit=crop&q=80",
            genre = "Películas",
            durationMin = 0,
            releaseYear = 2026,
            rating = 8.2,
            quality = "FHD HLS",
            isFeatured = false,
            streamUrl = "http://cf-playlist-mediapackage-public-us-east-1.prod.gcp.pluto.tv/v1/channel/5fa4549f315206000762cf3d/index.m3u8",
            isLive = true
        ),
        Movie(
            id = "pluto_fallback_anime".hashCode(),
            title = "Pluto TV Anime",
            synopsis = "Las mejores series de animación japonesa. Batallas épicas, romance y aventuras de fantasía.",
            posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200&auto=format&fit=crop&q=80",
            genre = "Infantil",
            durationMin = 0,
            releaseYear = 2026,
            rating = 9.0,
            quality = "FHD HLS",
            isFeatured = false,
            streamUrl = "http://cf-playlist-mediapackage-public-us-east-1.prod.gcp.pluto.tv/v1/channel/5fa45d6ee94ff80007db819b/index.m3u8",
            isLive = true
        ),
        Movie(
            id = "pluto_fallback_novelas".hashCode(),
            title = "Pluto TV Novelas",
            synopsis = "Historias llenas de pasión, traición y romance. Las mejores telenovelas latinas.",
            posterUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=1200&auto=format&fit=crop&q=80",
            genre = "Entretenimiento",
            durationMin = 0,
            releaseYear = 2026,
            rating = 8.1,
            quality = "FHD HLS",
            isFeatured = false,
            streamUrl = "http://cf-playlist-mediapackage-public-us-east-1.prod.gcp.pluto.tv/v1/channel/5fa45be2010886000760f38b/index.m3u8",
            isLive = true
        ),
        Movie(
            id = "pluto_fallback_cocina".hashCode(),
            title = "Pluto TV Cocina",
            synopsis = "Recetas deliciosas, competencias culinarias y los chefs más entretenidos del mundo.",
            posterUrl = "https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=1200&auto=format&fit=crop&q=80",
            genre = "Ciencia",
            durationMin = 0,
            releaseYear = 2026,
            rating = 8.4,
            quality = "FHD HLS",
            isFeatured = false,
            streamUrl = "http://cf-playlist-mediapackage-public-us-east-1.prod.gcp.pluto.tv/v1/channel/5fa4588e010886000760eed9/index.m3u8",
            isLive = true
        ),
        Movie(
            id = "pluto_fallback_nick_clasico".hashCode(),
            title = "Nick Clásico",
            synopsis = "Los mejores shows clásicos de Nickelodeon que marcaron tu infancia. ¡Diversión garantizada!",
            posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
            genre = "Infantil",
            durationMin = 0,
            releaseYear = 2026,
            rating = 9.2,
            quality = "FHD HLS",
            isFeatured = false,
            streamUrl = "http://cf-playlist-mediapackage-public-us-east-1.prod.gcp.pluto.tv/v1/channel/5fa4595e8414fb00079975f7/index.m3u8",
            isLive = true
        ),
        Movie(
            id = "pluto_fallback_investiga".hashCode(),
            title = "Pluto TV Investiga",
            synopsis = "Documentales y programas de misterio, crímenes reales, investigación forense y ciencia.",
            posterUrl = "https://images.unsplash.com/photo-1453733190148-c44698c26578?w=800&auto=format&fit=crop&q=80",
            backdropUrl = "https://images.unsplash.com/photo-1453733190148-c44698c26578?w=1200&auto=format&fit=crop&q=80",
            genre = "Ciencia",
            durationMin = 0,
            releaseYear = 2026,
            rating = 8.6,
            quality = "FHD HLS",
            isFeatured = false,
            streamUrl = "http://cf-playlist-mediapackage-public-us-east-1.prod.gcp.pluto.tv/v1/channel/5fa457788414fb0007997576/index.m3u8",
            isLive = true
        )
    )

    /**
     * Obtiene todos los canales de televisión en vivo agregados de múltiples listas M3U en paralelo.
     */
    suspend fun fetchPlutoChannels(category: String = "Todos"): List<Movie> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedChannels != null && now - lastFetchTime < CACHE_EXPIRY_MS) {
            return@withContext filterByCategory(cachedChannels ?: emptyList(), category)
        }

        val allParsedChannels = mutableListOf<Movie>()

        // Ejecutar las solicitudes HTTP de todas las fuentes M3U en paralelo
        val deferreds = m3uSources.map { url ->
            async {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val parsed = parseM3U(bodyStr)
                            Log.d("PlutoTVCrawler", "Cargados exitosamente ${parsed.size} canales desde $url")
                            parsed
                        } else {
                            Log.e("PlutoTVCrawler", "Respuesta no exitosa (${response.code}) para $url")
                            emptyList()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlutoTVCrawler", "Error descargando o parseando lista M3U de $url: ${e.message}")
                    emptyList()
                }
            }
        }

        // Esperar a que se completen todas las descargas paralelas
        val results = deferreds.map { it.await() }
        for (res in results) {
            allParsedChannels.addAll(res)
        }

        // Eliminar duplicados basados en streamUrl para evitar repeticiones visuales y de red
        val deduplicated = allParsedChannels.distinctBy { it.streamUrl }

        if (deduplicated.isNotEmpty()) {
            cachedChannels = deduplicated
            lastFetchTime = now
            return@withContext filterByCategory(deduplicated, category)
        }

        // Si la red falló completamente, usar fallback local
        filterByCategory(fallbackPlutoChannels, category)
    }

    private fun filterByCategory(channels: List<Movie>, category: String): List<Movie> {
        return if (category == "Todos" || category == "En Vivo") {
            channels
        } else {
            channels.filter { it.genre.equals(category, ignoreCase = true) }
        }
    }

    /**
     * Parsea un archivo de formato M3U8 y extrae los canales mapeados a nuestras categorías.
     */
    private fun parseM3U(m3uContent: String): List<Movie> {
        val list = mutableListOf<Movie>()
        try {
            val reader = BufferedReader(StringReader(m3uContent))
            var line: String? = reader.readLine()

            var currentMetadata: M3uMetadata? = null

            while (line != null) {
                line = line.trim()
                if (line.startsWith("#EXTINF:")) {
                    currentMetadata = parseExtinfLine(line)
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    if (currentMetadata != null) {
                        val streamUrl = line
                        val category = mapPlutoGroupToCategory(currentMetadata.groupTitle, currentMetadata.title)

                        // Construir el objeto Movie para nuestro reproductor de TV en vivo
                        list.add(
                            Movie(
                                id = streamUrl.hashCode(),
                                title = currentMetadata.title,
                                synopsis = "Señal en vivo oficial de Pluto TV • Categoría: $category. Disfruta de la mejor televisión gratis.",
                                posterUrl = currentMetadata.tvgLogo.ifEmpty { "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800&auto=format&fit=crop&q=80" },
                                backdropUrl = currentMetadata.tvgLogo.ifEmpty { "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=1200&auto=format&fit=crop&q=80" },
                                genre = category,
                                durationMin = 0,
                                releaseYear = 2026,
                                rating = 8.5,
                                quality = "FHD HLS",
                                isFeatured = list.size < 4, // Hacer destacados los primeros canales
                                streamUrl = streamUrl,
                                isLive = true
                            )
                        )
                        currentMetadata = null
                    }
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            Log.e("PlutoTVCrawler", "Error parseando M3U: ${e.message}")
        }
        return list
    }

    /**
     * Parsea los atributos de la línea #EXTINF.
     * Ejemplo: #EXTINF:-1 tvg-id="pluto-tv-classic-movies" tvg-logo="https://..." group-title="Películas",Pluto TV Cine Clásico
     */
    private fun parseExtinfLine(line: String): M3uMetadata {
        var tvgId = ""
        var tvgLogo = ""
        var groupTitle = ""

        // Extraer tvg-id
        val idMatcher = Regex("""tvg-id="([^"]*)"""").find(line)
        if (idMatcher != null) {
            tvgId = idMatcher.groupValues[1]
        }

        // Extraer tvg-logo
        val logoMatcher = Regex("""tvg-logo="([^"]*)"""").find(line)
        if (logoMatcher != null) {
            tvgLogo = logoMatcher.groupValues[1]
        }

        // Extraer group-title
        val groupMatcher = Regex("""group-title="([^"]*)"""").find(line)
        if (groupMatcher != null) {
            groupTitle = groupMatcher.groupValues[1]
        }

        // Extraer título del canal (lo que va después de la última coma)
        val title = line.substringAfterLast(",", "Canal Pluto TV").trim()

        if (tvgId.isEmpty()) {
            tvgId = title
        }

        return M3uMetadata(tvgId, tvgLogo, groupTitle, title)
    }

    /**
     * Mapea los grupos de Pluto TV a nuestras 8 categorías estandarizadas de CineStream:
     * "Noticias", "Deportes", "Películas", "Entretenimiento", "Música", "Infantil", "Ciencia", "Gaming"
     */
    private fun mapPlutoGroupToCategory(group: String, title: String): String {
        val groupLower = group.lowercase()
        val titleLower = title.lowercase()

        return when {
            // Películas / Cine
            groupLower.contains("películas") || groupLower.contains("cine") || groupLower.contains("movie") ||
            titleLower.contains("cine") || titleLower.contains("películas") -> "Películas"

            // Noticias / News
            groupLower.contains("noticias") || groupLower.contains("news") || groupLower.contains("periodismo") ||
            titleLower.contains("noticias") || titleLower.contains("news") || titleLower.contains("euronews") -> "Noticias"

            // Deportes
            groupLower.contains("deportes") || groupLower.contains("sports") || groupLower.contains("futbol") ||
            titleLower.contains("sports") || titleLower.contains("deportes") || titleLower.contains("marca") -> "Deportes"

            // Infantil / Kids / Anime
            groupLower.contains("infantil") || groupLower.contains("kids") || groupLower.contains("anime") || groupLower.contains("dibujos") ||
            titleLower.contains("kids") || titleLower.contains("nick") || titleLower.contains("baby") || titleLower.contains("anime") -> "Infantil"

            // Música / Music
            groupLower.contains("música") || groupLower.contains("music") || titleLower.contains("music") || titleLower.contains("lofi") || titleLower.contains("radio") -> "Música"

            // Ciencia / Documentales / Estilo de Vida
            groupLower.contains("ciencia") || groupLower.contains("documentales") || groupLower.contains("curiosidad") || groupLower.contains("naturaleza") ||
            titleLower.contains("ciencia") || titleLower.contains("nasa") || titleLower.contains("explore") || titleLower.contains("nature") -> "Ciencia"

            // Gaming / Tech
            groupLower.contains("gaming") || groupLower.contains("geek") || groupLower.contains("tecnología") || titleLower.contains("gaming") || titleLower.contains("esports") -> "Gaming"

            // Por defecto, Entretenimiento
            else -> "Entretenimiento"
        }
    }

    private data class M3uMetadata(
        val tvgId: String,
        val tvgLogo: String,
        val groupTitle: String,
        val title: String
    )
}
