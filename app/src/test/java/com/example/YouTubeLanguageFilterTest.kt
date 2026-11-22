package com.example

import com.example.data.youtube.YouTubeStreamResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLanguageFilterTest {
    @Test fun acceptsExplicitLatinAmericanSpanishAudioLocales() {
        listOf("es-419", "es-AR", "es-MX", "es-CL", "es-CO", "es-PE", "es-UY").forEach {
            assertTrue("Expected $it to be accepted", YouTubeStreamResolver.isLatinAmericanSpanish(it))
        }
    }

    @Test fun rejectsCastilianSpanishAndUnspecifiedAudio() {
        listOf(null, "", "es", "es-ES", "en-US", "pt-BR").forEach {
            assertFalse("Expected $it to be rejected", YouTubeStreamResolver.isLatinAmericanSpanish(it))
        }
    }
}
