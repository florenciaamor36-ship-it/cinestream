package com.github.libretube.api.obj

/** Only the metadata needed to construct the upstream SABR manifest. */
data class Streams(
    val duration: Long,
    val audioStreams: List<PipedStream>,
    val videoStreams: List<PipedStream>,
    val serverAbrStreamingUrl: String?,
    val videoPlaybackUstreamerConfig: String?
)
