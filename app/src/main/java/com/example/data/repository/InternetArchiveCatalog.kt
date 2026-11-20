package com.example.data.repository

import android.content.Context
import java.util.zip.GZIPInputStream
import com.example.data.model.Movie
import org.json.JSONObject
import java.util.Locale

/** A bundled snapshot of Archive.org records marked with Public Domain Mark 1.0.
 * The upstream mark is a declaration in catalog metadata, not a legal verification.
 */
internal class InternetArchiveCatalog(private val context: Context) {
    fun load(): List<Movie> {
        val root = GZIPInputStream(context.assets.open("ia_public_domain_candidates.json.gz")).bufferedReader().use { JSONObject(it.readText()) }
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
                val formats = item.optJSONArray("formats")?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
                val rightsAndContentText = (title + " " + description + " " + subjects.joinToString(" ") + " " + formats.joinToString(" ")).lowercase(Locale.ROOT)
                val restrictedTerms = listOf("pornography", "pornographic", "porn", "sexploitation", "hardcore porn", "softcore porn", "adult porn film", "adult sex film", "xxx porn")
                if (restrictedTerms.any(rightsAndContentText::contains)) continue
                val languages = item.optJSONArray("language")?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
                val year = item.optInt("year", 0).takeIf { it in 1880..2100 } ?: 0
                val runtime = item.optInt("runtimeMinutes", 0).takeIf { it > 0 } ?: 0
                val sourceLanguage = languages.firstOrNull().orEmpty()
                val genre = genreFrom(subjects)
                val readableLanguages = languages.map { languageName(it) }.distinct()
                val extra = buildList {
                    if (description.isNotBlank()) add("Descripción de Internet Archive:\n$description")
                    if (creator.isNotBlank()) add("Autoría declarada en la ficha: $creator")
                    if (readableLanguages.isNotEmpty()) add("Idioma indicado en la ficha (no confirma audio ni subtítulos): ${readableLanguages.joinToString()}")
                    if (subjects.isNotEmpty()) add("Temas indicados en la ficha: ${subjects.take(8).joinToString()}")
                    if (formats.isNotEmpty()) add("Formatos indicados en la ficha: ${formats.take(8).joinToString()}")
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
                        rightsStatus = item.optString("rightsStatus"),
                        sourceLanguage = sourceLanguage,
                        formats = formats
                    )
                )
            }
        }
    }

    private fun languageName(code: String): String = when (code.lowercase(Locale.ROOT)) {
        "eng", "en", "english" -> "inglés"
        "por", "pt", "portuguese", "português" -> "portugués"
        "spa", "es", "spanish", "español" -> "español"
        "fre", "fra", "fr", "french" -> "francés"
        "ger", "deu", "de", "german" -> "alemán"
        "ita", "it", "italian" -> "italiano"
        "jpn", "ja", "japanese" -> "japonés"
        "rus", "ru", "russian" -> "ruso"
        "zho", "chi", "zh", "chinese" -> "chino"
        "ara", "ar", "arabic" -> "árabe"
        "hin", "hi", "hindi" -> "hindi"
        "dut", "nld", "nl", "dutch" -> "neerlandés"
        "swe", "sv", "swedish" -> "sueco"
        "dan", "da", "danish" -> "danés"
        "nor", "no", "norwegian" -> "noruego"
        "pol", "pl", "polish" -> "polaco"
        "tur", "tr", "turkish" -> "turco"
        "kor", "ko", "korean" -> "coreano"
        "heb", "he", "hebrew" -> "hebreo"
        "gre", "ell", "el", "greek" -> "griego"
        "tha", "th", "thai" -> "tailandés"
        else -> code
    }

    private fun genreFrom(subjects: List<String>): String {
        val joined = subjects.joinToString(" ").lowercase(Locale.ROOT)
        return when {
            listOf("science fiction", "sci-fi", "science-fiction", "ciencia ficción", "ficção científica", "ficcao cientifica").any(joined::contains) -> "Ciencia ficción"
            listOf("horror", "terror", "monster", "ghost", "horror films").any(joined::contains) -> "Terror"
            listOf("western", "westerns", "cowboy").any(joined::contains) -> "Western"
            listOf("comedy", "comedies", "humor", "comédia", "comedia").any(joined::contains) -> "Comedia"
            listOf("romance", "romantic", "romântico", "romantico").any(joined::contains) -> "Romance"
            listOf("crime", "detective", "mystery", "film noir", "crime films", "mistério", "misterio").any(joined::contains) -> "Crimen y misterio"
            listOf("animation", "animated").any(joined::contains) -> "Animación"
            listOf("documentary", "documental", "documentário", "documentario").any(joined::contains) -> "Documental"
            listOf("action", "adventure", "war", "ação", "acao", "aventura").any(joined::contains) -> "Acción y aventura"
            listOf("drama", "melodrama", "drama films").any(joined::contains) -> "Drama"
            else -> "Cine y archivo"
        }
    }
}
