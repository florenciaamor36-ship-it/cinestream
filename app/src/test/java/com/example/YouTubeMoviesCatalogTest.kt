package com.example

import com.example.data.repository.YouTubeMoviesCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeMoviesCatalogTest {
    @Test fun categoryUsesFilmCluesAndDoesNotInventUnknownGenres() {
        assertEquals("Acción y aventura", YouTubeMoviesCatalog.genreFor("Una historia", "Películas de acción"))
        assertEquals("Clásicos", YouTubeMoviesCatalog.genreFor("Una historia", "Películas clásicas"))
        assertEquals("Sin clasificar", YouTubeMoviesCatalog.genreFor("Una historia", "Película completa en español"))
    }
}
