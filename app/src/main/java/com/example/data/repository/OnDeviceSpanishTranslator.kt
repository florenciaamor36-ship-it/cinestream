package com.example.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** On-device translation for item metadata. Media tracks are never changed. */
object OnDeviceSpanishTranslator {
    suspend fun translate(text: String, languageHint: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text
        val source = sourceLanguage(languageHint) ?: runCatching {
            Tasks.await(LanguageIdentification.getClient().identifyLanguage(text.take(2000)))
                .takeIf { it != "und" }
        }.getOrNull()?.let(::sourceLanguage)
        if (source == null || source == TranslateLanguage.SPANISH) return@withContext text

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build()
        )
        try {
            // The user explicitly asked to translate this ficha; model download is on demand.
            Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()))
            Tasks.await(translator.translate(text))
        } finally {
            translator.close()
        }
    }

    private fun sourceLanguage(raw: String): String? {
        val tag = when (raw.trim().lowercase()) {
            "eng", "english" -> "en"
            "por", "portuguese", "português" -> "pt"
            "spa", "spanish", "español" -> "es"
            "fre", "fra", "french" -> "fr"
            "ger", "deu", "german" -> "de"
            "ita", "italian" -> "it"
            "jpn", "japanese" -> "ja"
            "rus", "russian" -> "ru"
            "zho", "chi", "chinese" -> "zh"
            "ara", "arabic" -> "ar"
            "hin", "hindi" -> "hi"
            "dut", "nld", "dutch" -> "nl"
            "kor", "korean" -> "ko"
            else -> raw.trim().lowercase().takeIf { it.length in 2..3 }
        }
        return runCatching { TranslateLanguage.fromLanguageTag(tag) }.getOrNull()
    }
}
