package com.github.libretube.api.obj

/** Minimal format metadata consumed by the upstream SABR/UMP player implementation. */
data class PipedStream(
    val mimeType: String? = null, val codec: String? = null, val bitrate: Int? = null,
    val width: Int? = null, val height: Int? = null, val fps: Int? = null,
    val durationMs: Long? = null, val audioTrackId: String? = null,
    val audioTrackType: String? = null, val audioTrackLocale: String? = null,
    val itag: Int? = null, val lastModified: Long? = null, val isDrc: Boolean? = null,
    val xtags: String? = null
)
