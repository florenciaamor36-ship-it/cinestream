package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Movie(
    @Json(name = "id_pelicula") val id: Int,
    @Json(name = "titulo") val title: String,
    @Json(name = "sinopsis") val synopsis: String = "",
    @Json(name = "poster_url") val posterUrl: String = "",
    @Json(name = "backdrop_url") val backdropUrl: String = "",
    @Json(name = "genero") val genre: String = "Acción",
    @Json(name = "duracion_min") val durationMin: Int = 120,
    @Json(name = "anio_estreno") val releaseYear: Int = 2024,
    @Json(name = "calificacion") val rating: Double = 8.5,
    @Json(name = "calidad_video") val quality: String = "4K Ultra HD",
    @Json(name = "tamano_bytes") val sizeBytes: Long = 1073741824L,
    @Json(name = "es_destacada") val isFeatured: Boolean = false,
    @Json(name = "stream_url") val streamUrl: String = "",
    @Json(name = "es_en_vivo") val isLive: Boolean = false,
    @Json(name = "es_serie") val isSeries: Boolean = false,
    @Json(name = "temporadas") val seasons: Int = 1,
    @Json(name = "capitulos") val episodesCount: Int = 10
)

@JsonClass(generateAdapter = true)
data class IptvChannel(
    @Json(name = "id") val id: Int,
    @Json(name = "nombre") val name: String,
    @Json(name = "url") val url: String,
    @Json(name = "grupo") val group: String = "Sin categoría",
    @Json(name = "logo_url") val logoUrl: String = "",
    @Json(name = "tvg_id") val tvgId: String = ""
) {
    fun toMovie(): Movie = Movie(
        id = -id,
        title = name,
        synopsis = "Canal IPTV en vivo. Grupo: $group",
        posterUrl = logoUrl,
        backdropUrl = logoUrl,
        genre = group,
        durationMin = 0,
        releaseYear = 0,
        rating = 0.0,
        quality = "LIVE",
        sizeBytes = 0,
        streamUrl = url,
        isLive = true
    )
}

@JsonClass(generateAdapter = true)
data class LiveChannel(
    @Json(name = "id") val id: String,
    @Json(name = "nombre") val name: String,
    @Json(name = "categoria") val category: String = "En Vivo",
    @Json(name = "logo_url") val logoUrl: String = "",
    @Json(name = "youtube_url") val youtubeUrl: String = "",
    @Json(name = "stream_url") val streamUrl: String = "",
    @Json(name = "es_en_vivo") val isLive: Boolean = true
) {
    fun toMovie(): Movie {
        return Movie(
            id = id.hashCode(),
            title = name,
            synopsis = "Transmisión en vivo desde YouTube vía HLS (.m3u8). Categoría: $category",
            posterUrl = logoUrl,
            backdropUrl = logoUrl,
            genre = "En Vivo",
            durationMin = 0,
            releaseYear = 2025,
            rating = 9.0,
            quality = "LIVE 1080p",
            isFeatured = false,
            streamUrl = streamUrl,
            isLive = true
        )
    }
}


@JsonClass(generateAdapter = true)
data class WorkerStatus(
    @Json(name = "bot_id") val botId: Int,
    @Json(name = "status") val status: String,
    @Json(name = "is_available") val isAvailable: Boolean,
    @Json(name = "active_streams") val activeStreams: Int,
    @Json(name = "mb_served") val mbServed: Double,
    @Json(name = "pause_seconds_left") val pauseSecondsLeft: Int,
    @Json(name = "errors") val errors: Int
)

@JsonClass(generateAdapter = true)
data class PoolTelemetry(
    @Json(name = "total_workers") val totalWorkers: Int = 20,
    @Json(name = "active_workers") val activeWorkers: Int = 20,
    @Json(name = "paused_workers") val pausedWorkers: Int = 0,
    @Json(name = "current_active_streams") val currentActiveStreams: Int = 0,
    @Json(name = "total_data_served_mb") val totalDataServedMb: Double = 0.0,
    @Json(name = "workers") val workers: List<WorkerStatus> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ServerStatusResponse(
    @Json(name = "status") val status: String = "online",
    @Json(name = "service") val service: String = "",
    @Json(name = "telegram_worker_pool") val workerPool: PoolTelemetry = PoolTelemetry()
)
