package com.example.data.repository

import android.content.Context
import com.example.data.model.Movie
import org.json.JSONObject
import java.util.Locale

/** A bundled snapshot of Archive.org records marked with Public Domain Mark 1.0.
 * The upstream mark is a declaration in catalog metadata, not a legal verification.
 */
internal class InternetArchiveCatalog(private val context: Context) {
    fun load(): List<Movie> {
        val root = context.assets.open("ia_public_domain_candidates.json").bufferedReader().use { JSONObject(it.readText()) }
        val records = root.getJSONArray("items")
        return buildList(records.length()) {
            for (index in 0 until records.length()) {
                val item = records.getJSONObject(index)
                val identifier = item.optString("identifier").trim()
                if (identifier.isEmpty()) continue
                val title = item.optString("title", identifier).ifBlank { identifier }
                val description = item.optString("description").trim()
                val creator = item.optJSONArray("creator")?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.joinToString(", ")
                }.orEmpty()
                val subjects = item.optJSONArray("subjects")?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
                val restrictedTerms = listOf("pornography", "pornographic", "porn", "erotic", "sexploitation", "hardcore", "softcore", "nudity", "nude", "striptease", "adult film", "sex farce", "sex comedy")
                val rightsAndContentText = (title + " " + description + " " + subjects.joinToString(" ")).lowercase(Locale.ROOT)
                if (restrictedTerms.any(rightsAndContentText::contains)) continue
                val languages = item.optJSONArray("language")?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
                val year = item.optInt("year", 0).takeIf { it in 1880..2100 } ?: 0
                val runtime = item.optDouble("runtime", 0.0).takeIf { it > 0.0 }?.toInt() ?: 0
                val genre = genreFrom(subjects)
                val extra = buildList {
                    if (description.isNotBlank()) add(description)
                    if (creator.isNotBlank()) add("Realización / autoría declarada: $creator")
                    if (languages.isNotEmpty()) add("Idioma declarado: ${languages.joinToString()}")
                    if (subjects.isNotEmpty()) add("Temas del registro: ${subjects.take(8).joinToString()}")
                }.joinToString("\n\n")
                add(
                    Movie(
                        id = index + 1,
                        title = title,
                        synopsis = extra.ifBlank { "Ficha del contenido publicada en Internet Archive." },
                        posterUrl = item.optString("posterUrl"),
                        backdropUrl = item.optString("posterUrl"),
                        genre = genre,
                        durationMin = runtime,
                        releaseYear = year,
                        rating = -1.0,
                        quality = "Internet Archive",
                        sizeBytes = 0L,
                        isFeatured = false,
                        streamUrl = "",
                        archiveIdentifier = identifier,
                        licenseUrl = item.optString("licenseUrl"),
                        creator = creator,
                        subjects = subjects,
                        sourceUrl = item.optString("sourceUrl"),
                        rightsStatus = item.optString("rightsStatus")
                    )
                )
            }
        }
    }

    private fun genreFrom(subjects: List<String>): String {
        val joined = subjects.joinToString(" ").lowercase(Locale.ROOT)
        return when {
            listOf("science fiction", "sci-fi", "science-fiction", "ciencia ficción").any(joined::contains) -> "Ciencia Ficción"
            listOf("horror", "terror", "monster", "ghost").any(joined::contains) -> "Terror"
            listOf("western", "westerns", "cowboy").any(joined::contains) -> "Western"
            listOf("comedy", "comedies", "humor").any(joined::contains) -> "Comedia"
            listOf("romance", "romantic").any(joined::contains) -> "Romance"
            listOf("crime", "detective", "mystery", "film noir").any(joined::contains) -> "Crimen y misterio"
            listOf("animation", "animated").any(joined::contains) -> "Animación"
            listOf("documentary", "documental").any(joined::contains) -> "Documental"
            listOf("action", "adventure", "war").any(joined::contains) -> "Acción y aventura"
            listOf("drama", "melodrama").any(joined::contains) -> "Drama"
            else -> "Cine y archivo"
        }
    }
}
