package com.example

import com.example.data.youtube.YouTubeStreamResolver
import com.example.data.youtube.YouTubeSearchCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLanguageFilterTest {
    @Test fun acceptsSpanishAudioFromLatinAmericaAndSpain() {
        listOf("es", "es-419", "es-AR", "es-MX", "es-CL", "es-CO", "es-ES").forEach {
            assertTrue("Expected $it to be accepted", YouTubeStreamResolver.isSpanish(it))
        }
    }

    @Test fun acceptsGenericSpanishAndSpainSpanishTags() {
        assertTrue(YouTubeStreamResolver.isSpanish("es"))
        assertTrue(YouTubeStreamResolver.isSpanish("es-ES"))
    }

    @Test fun rejectsUnspecifiedAndNonSpanishAudio() {
        listOf(null, "", "en-US", "pt-BR", "fr-FR").forEach {
            assertFalse("Expected $it to be rejected", YouTubeStreamResolver.isSpanish(it))
        }
    }

    @Test fun prefersLatinTracksButDoesNotRequireThem() {
        assertTrue(YouTubeStreamResolver.isLatinAmericanSpanish("es-AR"))
        assertFalse(YouTubeStreamResolver.isLatinAmericanSpanish("es-ES"))
        assertFalse(YouTubeStreamResolver.isLatinAmericanSpanish("es"))
    }

    @Test fun searchFiltersRejectShortClipsAndRequireSpanishFullMovieClues() {
        assertTrue(YouTubeSearchCatalog.isFullSpanishMovie("Película completa en castellano", "", 7200))
        assertTrue(YouTubeSearchCatalog.isFullSpanishMovie("Full Movie Spanish Dub", "", 7200))
        assertFalse(YouTubeSearchCatalog.isFullSpanishMovie("Trailer película completa español", "", 120))
        assertFalse(YouTubeSearchCatalog.isFullSpanishMovie("Película completa", "", 7200))
        assertFalse(YouTubeSearchCatalog.isFullSpanishMovie("Película completa español", "", 500))
    }
}
