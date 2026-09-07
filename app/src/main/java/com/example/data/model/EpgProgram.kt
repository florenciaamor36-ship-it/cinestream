package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EpgProgram(
    @Json(name = "titulo") val title: String,
    @Json(name = "descripcion") val description: String = "",
    @Json(name = "inicio") val start: String,
    @Json(name = "fin") val end: String,
    @Json(name = "ahora") val now: Boolean = false
)
