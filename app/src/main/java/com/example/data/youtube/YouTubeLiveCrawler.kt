package com.example.data.youtube

import android.util.Log
import com.example.data.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object YouTubeLiveCrawler {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Catálogo masivo de canales de TV en vivo organizados por categorías
    val predefinedChannels: List<YouTubeLiveItem> = listOf(
        // ==================== NOTICIAS (24 Horas en Vivo) ====================
        YouTubeLiveItem(
            id = "yt_noticias_dw",
            title = "DW Español - Noticias en Vivo",
            channelName = "DW Español",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1495020689067-958852a7765e?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCZ10bYgSgID9g8scS66_5_A",
            viewersCount = "45K en vivo",
            country = "Alemania/Global"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_rtve",
            title = "RTVE 24 Horas En Directo",
            channelName = "RTVE Noticias",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1586339949916-3e9457bef6d3?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC7QZIf0dta-XPXsp9Hv4dTw",
            viewersCount = "33K en vivo",
            country = "España"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_france24",
            title = "France 24 en Español Directo",
            channelName = "France 24 Español",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCYfvS0S6_993mG3R7Z5nE4g",
            viewersCount = "29K en vivo",
            country = "Francia"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_tn",
            title = "Todo Noticias (TN) En Vivo 24h",
            channelName = "TN Argentina",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCj6PcyIvBYU152G9x8S839w",
            viewersCount = "89K en vivo",
            country = "Argentina"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_canal26",
            title = "Canal 26 Noticias en Vivo 24/7",
            channelName = "Canal 26",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1526470608268-f674ce90ebd4?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC2uXf8Z7S2_i_A_v1SntMlg",
            viewersCount = "52K en vivo",
            country = "Argentina"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_milenio",
            title = "Milenio Televisión en Directo",
            channelName = "Milenio TV",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC5O6b68t-hSOfZ8UfA1l92g",
            viewersCount = "41K en vivo",
            country = "México"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_ntn24",
            title = "NTN24 Noticias 24 Horas",
            channelName = "NTN24",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1495020689067-958852a7765e?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCG_T7VbE_v7WlZ_78M47Ssg",
            viewersCount = "24K en vivo",
            country = "Colombia/Latam"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_euronews",
            title = "Euronews Noticias en Directo",
            channelName = "Euronews Español",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC06Jg_vS6C_8Q6S7m3_vMhQ",
            viewersCount = "18K en vivo",
            country = "Europa"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_telesur",
            title = "teleSUR Noticias en Vivo",
            channelName = "teleSUR tv",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC_Q_xG87U_mS_Jz1h_p8v-g",
            viewersCount = "19K en vivo",
            country = "Latinoamérica"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_nmas",
            title = "N+ Media Noticias en Vivo",
            channelName = "N+ Noticias",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1586339949916-3e9457bef6d3?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC786H_XF7_fU9L2xLNoQvSg",
            viewersCount = "35K en vivo",
            country = "México"
        ),
        YouTubeLiveItem(
            id = "yt_noticias_cnn_es",
            title = "CNN en Español Directo",
            channelName = "CNN",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1526470608268-f674ce90ebd4?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC_Q8M1qR_v7Yn_vM-S_t-Yw",
            viewersCount = "55K en vivo",
            country = "EE.UU."
        ),
        YouTubeLiveItem(
            id = "yt_noticias_telemundo",
            title = "Telemundo Noticias Directo",
            channelName = "Telemundo",
            category = "Noticias",
            thumbnailUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UChU0kU8oM_YgY2L03k9uXgg",
            viewersCount = "40K en vivo",
            country = "EE.UU./Latam"
        ),

        // ==================== DEPORTES ====================
        YouTubeLiveItem(
            id = "yt_deportes_redbull",
            title = "Red Bull TV Live Extreme",
            channelName = "Red Bull TV",
            category = "Deportes",
            thumbnailUrl = "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCblfuW_4rakU2Y5zy8F96JA",
            viewersCount = "62K en vivo",
            country = "Mundo"
        ),
        YouTubeLiveItem(
            id = "yt_deportes_chiringuito",
            title = "El Chiringuito de Jugones En Vivo",
            channelName = "El Chiringuito",
            category = "Deportes",
            thumbnailUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCEt99_rI4zZf0m-E2hC_r7A",
            viewersCount = "80K en vivo",
            country = "España"
        ),
        YouTubeLiveItem(
            id = "yt_deportes_marca",
            title = "Radio MARCA TV en Vivo",
            channelName = "Radio MARCA",
            category = "Deportes",
            thumbnailUrl = "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCzH_qYl8-q4N-V0_SsgFpSg",
            viewersCount = "15K en vivo",
            country = "España"
        ),
        YouTubeLiveItem(
            id = "yt_deportes_tyc",
            title = "TyC Sports Directo Debate",
            channelName = "TyC Sports",
            category = "Deportes",
            thumbnailUrl = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC0_gE6_1v6S5oO9N_9tN-4w",
            viewersCount = "45K en vivo",
            country = "Argentina"
        ),
        YouTubeLiveItem(
            id = "yt_deportes_surf",
            title = "World Surf League - Live Oceans",
            channelName = "WSL Surf",
            category = "Deportes",
            thumbnailUrl = "https://images.unsplash.com/photo-1502680390469-be75c86b636f?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC8_p98F_G3z3H2Z7K-S-M2g",
            viewersCount = "31K en vivo",
            country = "Global"
        ),
        YouTubeLiveItem(
            id = "yt_deportes_motogp",
            title = "MotoGP Live Stream Hub",
            channelName = "MotoGP",
            category = "Deportes",
            thumbnailUrl = "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC69_uYg1oM2O_4Z_Yv3O8Mg",
            viewersCount = "25K en vivo",
            country = "Mundo"
        ),

        // ==================== PELÍCULAS & CINE ====================
        YouTubeLiveItem(
            id = "yt_cine_clasico",
            title = "Classic Movies Retro 24/7",
            channelName = "Film&Clips",
            category = "Películas",
            thumbnailUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCfW_b_R7S5oO8-S_O8t_Vmg",
            viewersCount = "21K en vivo",
            country = "Latam/España"
        ),
        YouTubeLiveItem(
            id = "yt_cine_runtime",
            title = "Runtime Cine & Series en Vivo",
            channelName = "Runtime Español",
            category = "Películas",
            thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC-V1a8_798S8M4Z_8_G_2OA",
            viewersCount = "34K en vivo",
            country = "Latam"
        ),
        YouTubeLiveItem(
            id = "yt_cine_butaca",
            title = "Butaca TV Cine Gratis",
            channelName = "Butaca TV",
            category = "Películas",
            thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCuXf_b-sX2_Yv_h8M9tO_8g",
            viewersCount = "27K en vivo",
            country = "Hollywood"
        ),

        // ==================== ENTRETENIMIENTO ====================
        YouTubeLiveItem(
            id = "yt_entretenimiento_azteca",
            title = "TV Azteca Entretenimiento",
            channelName = "Azteca Uno",
            category = "Entretenimiento",
            thumbnailUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCtO8_M_Y_Y7K8s_vV-O_Q2g",
            viewersCount = "58K en vivo",
            country = "México"
        ),
        YouTubeLiveItem(
            id = "yt_entretenimiento_failarmy",
            title = "FailArmy 24/7 Funniest Clips",
            channelName = "FailArmy",
            category = "Entretenimiento",
            thumbnailUrl = "https://images.unsplash.com/photo-1526470608268-f674ce90ebd4?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCuAc0K68t_VvV-8Z7_S_8gg",
            viewersCount = "38K en vivo",
            country = "EE.UU."
        ),
        YouTubeLiveItem(
            id = "yt_entretenimiento_petcollective",
            title = "The Pet Collective 24/7",
            channelName = "The Pet Collective",
            category = "Entretenimiento",
            thumbnailUrl = "https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC_Q_xG87U_mS_Jz1h_p8v-g",
            viewersCount = "42K en vivo",
            country = "Global"
        ),
        YouTubeLiveItem(
            id = "yt_entretenimiento_gags",
            title = "Just For Laughs Gags - Bromas 24/7",
            channelName = "Just For Laughs Gags",
            category = "Entretenimiento",
            thumbnailUrl = "https://images.unsplash.com/photo-1514306191717-452ec28c7814?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC8_P8U_N1W9R_Yv-V-O_M2g",
            viewersCount = "29K en vivo",
            country = "Canadá"
        ),

        // ==================== MÚSICA ====================
        YouTubeLiveItem(
            id = "yt_musica_lofi",
            title = "Lofi Hip Hop Radio - Beats to relax/study",
            channelName = "Lofi Girl",
            category = "Música",
            thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCSJ4gkVC6NrvII8umztf0Ow",
            viewersCount = "48K en vivo",
            country = "Global"
        ),
        YouTubeLiveItem(
            id = "yt_musica_chillhop",
            title = "Chillhop Radio - Jazzy Beats",
            channelName = "Chillhop Music",
            category = "Música",
            thumbnailUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCOhqE_E1v6S5oO9N_9tN-4w",
            viewersCount = "19K en vivo",
            country = "Global"
        ),
        YouTubeLiveItem(
            id = "yt_musica_tomorrowland",
            title = "Tomorrowland Live Radio",
            channelName = "Tomorrowland One World",
            category = "Música",
            thumbnailUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC_Q8M1qR_v7Yn_vM-S_t-Yw",
            viewersCount = "67K en vivo",
            country = "Bélgica"
        ),
        YouTubeLiveItem(
            id = "yt_musica_dance",
            title = "Monstercat Silk Melodic House",
            channelName = "Monstercat Silk",
            category = "Música",
            thumbnailUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC87_pWbZ_M3qO_N0wV9tO3g",
            viewersCount = "33K en vivo",
            country = "Mundo"
        ),

        // ==================== INFANTIL ====================
        YouTubeLiveItem(
            id = "yt_infantil_pocoyo",
            title = "Pocoyó en Español 24h",
            channelName = "Pocoyó",
            category = "Infantil",
            thumbnailUrl = "https://images.unsplash.com/photo-1566492031773-4f4e44671857?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC87_pWbZ_M3qO_N0wV9tO3g",
            viewersCount = "55K en vivo",
            country = "España"
        ),
        YouTubeLiveItem(
            id = "yt_infantil_babyshark",
            title = "Baby Shark & Pinkfong Kids Songs",
            channelName = "Pinkfong",
            category = "Infantil",
            thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC_Q_xG87U_mS_Jz1h_p8v-g",
            viewersCount = "72K en vivo",
            country = "Global"
        ),
        YouTubeLiveItem(
            id = "yt_infantil_masha",
            title = "Masha y el Oso Directo",
            channelName = "Masha y el Oso",
            category = "Infantil",
            thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCYfvS0S6_993mG3R7Z5nE4g",
            viewersCount = "36K en vivo",
            country = "Rusia/Global"
        ),
        YouTubeLiveItem(
            id = "yt_infantil_cocomelon",
            title = "Cocomelon Español Canciones Infantiles",
            channelName = "Cocomelon",
            category = "Infantil",
            thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCB2T01ZfO_C647D0v-A-W8g",
            viewersCount = "60K en vivo",
            country = "EE.UU./Global"
        ),

        // ==================== CIENCIA ====================
        YouTubeLiveItem(
            id = "yt_ciencia_nasa",
            title = "NASA TV Live Directo",
            channelName = "NASA TV",
            category = "Ciencia",
            thumbnailUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCOhqE_E1v6S5oO9N_9tN-4w",
            viewersCount = "120K en vivo",
            country = "NASA"
        ),
        YouTubeLiveItem(
            id = "yt_ciencia_oceans",
            title = "Live Undersea Coral Reef Cam",
            channelName = "Explore Oceans",
            category = "Ciencia",
            thumbnailUrl = "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC3I2QsFi86_VbF-2_f8tSsg",
            viewersCount = "14K en vivo",
            country = "Global"
        ),
        YouTubeLiveItem(
            id = "yt_ciencia_space",
            title = "Space Videos Earth Views Live",
            channelName = "Space Videos",
            category = "Ciencia",
            thumbnailUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC8_N8U_9O_9R_Yv-V-O_M2g",
            viewersCount = "25K en vivo",
            country = "Space"
        ),

        // ==================== GAMING ====================
        YouTubeLiveItem(
            id = "yt_gaming_lvp",
            title = "LVPes League of Legends Directo",
            channelName = "LVP España",
            category = "Gaming",
            thumbnailUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC3I2QsFi86_VbF-2_f8tSsg",
            viewersCount = "54K en vivo",
            country = "España"
        ),
        YouTubeLiveItem(
            id = "yt_gaming_esl",
            title = "ESL Counter-Strike Live TV",
            channelName = "ESL CS",
            category = "Gaming",
            thumbnailUrl = "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UC87_pWbZ_M3qO_N0wV9tO3g",
            viewersCount = "78K en vivo",
            country = "Global"
        ),
        YouTubeLiveItem(
            id = "yt_gaming_minecraft",
            title = "Minecraft Live Streams 24/7",
            channelName = "Minecraft Community",
            category = "Gaming",
            thumbnailUrl = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=800&auto=format&fit=crop&q=80",
            youtubeVideoId = "channel:UCuAc0K68t_VvV-8Z7_S_8gg",
            viewersCount = "31K en vivo",
            country = "Global"
        )
    )

    /**
     * Busca canales de televisión en vivo en YouTube y combina con el catálogo maestro.
     */
    suspend fun searchLiveChannelsOnYouTube(categoryQuery: String): List<YouTubeLiveItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<YouTubeLiveItem>()
        try {
            val queryParam = if (categoryQuery == "Todos" || categoryQuery == "En Vivo") {
                "noticias deportes tv en vivo 24/7"
            } else {
                "$categoryQuery tv en vivo directo"
            }
            val queryEncoded = URLEncoder.encode(queryParam, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$queryEncoded&sp=EgJAAQ%253D%253D"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val parsed = extractLiveVideosFromHtml(html, categoryQuery)
                results.addAll(parsed)
            }
        } catch (e: Exception) {
            Log.w("YouTubeLiveCrawler", "Error buscando en YouTube para $categoryQuery: ${e.message}")
        }

        // Obtener el catálogo predefinido
        val fallback = if (categoryQuery == "Todos" || categoryQuery == "En Vivo") {
            predefinedChannels
        } else {
            predefinedChannels.filter {
                it.category.equals(categoryQuery, ignoreCase = true) ||
                        it.title.contains(categoryQuery, ignoreCase = true)
            }
        }

        // Siempre unir resultados encontrados con el catálogo base completo
        (fallback + results).distinctBy { it.id }
    }

    private fun extractLiveVideosFromHtml(html: String, defaultCategory: String): List<YouTubeLiveItem> {
        val list = mutableListOf<YouTubeLiveItem>()
        try {
            val marker = "var ytInitialData = "
            val startIdx = html.indexOf(marker)
            if (startIdx != -1) {
                val jsonStart = startIdx + marker.length
                val jsonEnd = html.indexOf(";</script>", jsonStart)
                if (jsonEnd != -1) {
                    val jsonStr = html.substring(jsonStart, jsonEnd)
                    val json = JSONObject(jsonStr)

                    val contents = json.optJSONObject("contents")
                        ?.optJSONObject("twoColumnSearchResultsRenderer")
                        ?.optJSONObject("primaryContents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                    if (contents != null && contents.length() > 0) {
                        val itemSection = contents.getJSONObject(0)
                            .optJSONObject("itemSectionRenderer")
                            ?.optJSONArray("contents")

                        if (itemSection != null) {
                            for (i in 0 until itemSection.length()) {
                                val item = itemSection.getJSONObject(i)
                                val videoRenderer = item.optJSONObject("videoRenderer")
                                if (videoRenderer != null) {
                                    val videoId = videoRenderer.optString("videoId")
                                    val titleObj = videoRenderer.optJSONObject("title")
                                    val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                        ?: titleObj?.optString("simpleText") ?: "Canal en Vivo"

                                    val ownerObj = videoRenderer.optJSONObject("ownerText")
                                    val channelName = ownerObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube Live"

                                    val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                    val thumbUrl = if (thumbnails != null && thumbnails.length() > 0) {
                                        thumbnails.getJSONObject(thumbnails.length() - 1).optString("url")
                                    } else {
                                        "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                                    }

                                    val badges = videoRenderer.optJSONArray("badges")
                                    val isLiveBadge = badges?.toString()?.contains("LIVE") == true ||
                                            badges?.toString()?.contains("DIRECTO") == true ||
                                            videoRenderer.toString().contains("BADGE_STYLE_TYPE_LIVE_NOW")

                                    if (videoId.isNotEmpty() && (isLiveBadge || list.size < 12)) {
                                        list.add(
                                            YouTubeLiveItem(
                                                id = "yt_crawl_$videoId",
                                                title = title,
                                                channelName = channelName,
                                                category = if (defaultCategory == "Todos") "Noticias" else defaultCategory,
                                                thumbnailUrl = thumbUrl,
                                                youtubeVideoId = videoId,
                                                hlsStreamUrl = null,
                                                viewersCount = "EN VIVO",
                                                country = "Directo"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("YouTubeLiveCrawler", "Parse error: ${e.message}")
        }
        return list
    }

    /**
     * Convierte un YouTubeLiveItem en un Movie con metadata apta para el reproductor universal.
     * Si no tiene un stream HLS directo, usa la URL de embed formateada.
     */
    fun toMovie(item: YouTubeLiveItem): Movie {
        val streamUrl = if (!item.hlsStreamUrl.isNullOrEmpty()) {
            item.hlsStreamUrl
        } else if (!item.youtubeVideoId.isNullOrEmpty()) {
            if (item.youtubeVideoId.startsWith("channel:")) {
                val channelId = item.youtubeVideoId.substringAfter("channel:")
                "https://www.youtube.com/embed/live?channel=$channelId&autoplay=1&playsinline=1&enablejsapi=1&rel=0"
            } else {
                "https://www.youtube.com/embed/${item.youtubeVideoId}?autoplay=1&playsinline=1&enablejsapi=1&rel=0"
            }
        } else {
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        }

        return Movie(
            id = item.id.hashCode(),
            title = item.title,
            synopsis = "${item.channelName} • Transmisión en vivo oficial desde YouTube. Categoría: ${item.category}. ${item.viewersCount}",
            posterUrl = item.thumbnailUrl,
            backdropUrl = item.thumbnailUrl,
            genre = item.category,
            durationMin = 0,
            releaseYear = 2026,
            rating = 9.5,
            quality = "LIVE 1080p",
            isFeatured = false,
            streamUrl = streamUrl,
            isLive = true
        )
    }
}
