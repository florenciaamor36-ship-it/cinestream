package com.example.data.youtube

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeLiveItem(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "channel_name") val channelName: String,
    @Json(name = "category") val category: String, // "Noticias", "Deportes", "Música", "Gaming", "Tecnología", "Infantil"
    @Json(name = "thumbnail_url") val thumbnailUrl: String,
    @Json(name = "youtube_video_id") val youtubeVideoId: String? = null,
    @Json(name = "hls_stream_url") val hlsStreamUrl: String? = null,
    @Json(name = "viewers_count") val viewersCount: String = "LIVE",
    @Json(name = "country") val country: String = "ES"
)
