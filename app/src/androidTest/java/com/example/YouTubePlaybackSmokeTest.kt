package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.MainActivity
import com.example.data.repository.YouTubeMoviesCatalog
import com.example.ui.viewmodel.CineStreamViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies official YouTube IFrame playback for curated full-movie candidates. */
class YouTubePlaybackSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bundledFallbackHasPlayableYoutubeMovieEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val movies = YouTubeMoviesCatalog(context).loadBundled()
        assertEquals(62, movies.size)
        assertTrue(movies.all { it.streamUrl.startsWith("https://www.youtube-nocookie.com/embed/") })
        assertTrue(movies.all { it.sourceUrl.startsWith("https://youtube.com/watch?v=") })
    }

    @Test
    fun curatedYoutubeMoviesPlayInsideTheOfficialEmbed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = YouTubeMoviesCatalog(context).loadBundled()
        // Sample across the distinct curated publishers; an individual rights-holder may disable embeds.
        val candidateIds = listOf(1, 2, 3, 4, 6, 8, 9, 13, 15, 18)
        val candidates = candidateIds.mapNotNull { id -> catalog.firstOrNull { it.id == id } }
        val viewModel = ViewModelProvider(composeRule.activity)[CineStreamViewModel::class.java]
        val outcomes = mutableListOf<String>()

        for (movie in candidates) {
            composeRule.activity.runOnUiThread { viewModel.navigateToPlayer(movie) }
            composeRule.waitUntil(timeoutMillis = 30_000) {
                runCatching { composeRule.onNodeWithTag("youtube_player_loading").fetchSemanticsNode(); true }.getOrDefault(false)
            }
            val eventReceived = runCatching {
                composeRule.waitUntil(timeoutMillis = 45_000) {
                    val playing = runCatching { composeRule.onNodeWithTag("youtube_player_playing").fetchSemanticsNode(); true }.getOrDefault(false)
                    val error = runCatching { composeRule.onNodeWithTag("youtube_player_error_state").fetchSemanticsNode(); true }.getOrDefault(false)
                    playing || error
                }
                true
            }.getOrDefault(false)

            if (!eventReceived) {
                outcomes += "${movie.id} (${movie.title}): IFrame no respondió"
                continue
            }
            if (runCatching { composeRule.onNodeWithTag("youtube_player_playing").fetchSemanticsNode(); true }.getOrDefault(false)) {
                // Success requires a real onStateChange(PLAYING), not just a loaded page.
                composeRule.onNodeWithTag("youtube_player_playing").fetchSemanticsNode()
                return
            }
            outcomes += "${movie.id} (${movie.title}): YouTube devolvió error de inserción"
        }
        throw AssertionError("Ninguna de las películas probadas informó PLAYING: ${outcomes.joinToString("; ")}")
    }
}
