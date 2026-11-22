package com.example.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.Locale

/** Resolves a YouTube video to a video-only stream plus an explicitly Latin-American Spanish audio track. */
data class ResolvedYouTubeStream(
    val videoUrl: String,
    val audioUrl: String,
    val audioLanguage: String,
    val durationSeconds: Long
)

object YouTubeStreamResolver {
    private val videoIdPattern = Regex("(?:embed/|watch\\?v=|youtu\\.be/)([A-Za-z0-9_-]{11})")
    private val latinAmericanSpanishRegions = setOf(
        "419", "AR", "BO", "CL", "CO", "CR", "CU", "DO", "EC", "SV", "GT", "HN",
        "MX", "NI", "PA", "PE", "PR", "PY", "US", "UY", "VE"
    )

    suspend fun resolve(videoUrl: String): ResolvedYouTubeStream = withContext(Dispatchers.IO) {
        val id = videoIdPattern.find(videoUrl)?.groupValues?.get(1)
            ?: error("No pude identificar el video de YouTube.")
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$id")
        val spanishTracks = info.audioStreams.filter { stream -> isSpanish(stream.audioLocale?.toLanguageTag()) }
        val audio = spanishTracks
            .maxByOrNull { stream ->
                val locale = stream.audioLocale
                val latinBonus = if (locale != null && isLatinAmericanSpanish(locale.toLanguageTag())) 1_000_000 else 0
                latinBonus + stream.averageBitrate
            }
            ?: error("No encontré una pista de audio identificada como español para esta película.")
        val video = info.videoOnlyStreams
            .filter { it.height in 1..1080 && it.content.isNotBlank() }
            .maxByOrNull { it.height }
            ?: error("No encontré un flujo de video compatible para esta película.")

        ResolvedYouTubeStream(
            videoUrl = video.content,
            audioUrl = audio.content,
            audioLanguage = audio.audioLocale?.toLanguageTag().orEmpty(),
            durationSeconds = info.duration
        )
    }

    internal fun isSpanish(languageTag: String?): Boolean {
        if (languageTag.isNullOrBlank()) return false
        return Locale.forLanguageTag(languageTag).language.equals("es", ignoreCase = true)
    }

    internal fun isLatinAmericanSpanish(languageTag: String?): Boolean {
        if (!isSpanish(languageTag)) return false
        val locale = Locale.forLanguageTag(languageTag!!)
        return locale.country.uppercase(Locale.ROOT) in latinAmericanSpanishRegions
    }
}
