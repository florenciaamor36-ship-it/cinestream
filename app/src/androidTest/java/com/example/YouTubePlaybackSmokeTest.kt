package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.MainActivity
import com.example.data.model.Movie
import com.example.data.repository.YouTubeMoviesCatalog
import com.example.ui.viewmodel.CineStreamViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Confirms real playback inside the app using candidate YouTube full movies. */
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
    fun candidateFullMoviesCanPlayInsideTheApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val template = YouTubeMoviesCatalog(context).loadBundled().first()
        val candidates = listOf(
            "rs-C58c7YNE" to "Peter Pan (1924)",
            "rUoiK53xGxg" to "Easy Street (1917)",
            "6JN6DyIZlJI" to "Laurel & Hardy public-domain film",
            "55X8VHpS4hM" to "Good Morning, Miss Dove"
        )
        val viewModel = ViewModelProvider(composeRule.activity)[CineStreamViewModel::class.java]
        val outcomes = mutableListOf<String>()

        for ((index, candidate) in candidates.withIndex()) {
            val (videoId, title) = candidate
            val movie = template.copy(
                id = 900 + index,
                title = title,
                streamUrl = "https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&rel=0&autoplay=1",
                sourceUrl = "https://youtube.com/watch?v=$videoId",
                creator = "YouTube full-movie candidate",
                isLive = false
            )
            composeRule.activity.runOnUiThread { viewModel.navigateToPlayer(movie) }
            composeRule.waitUntil(timeoutMillis = 30_000) {
                runCatching { composeRule.onNodeWithTag("video_player_screen").fetchSemanticsNode(); true }.getOrDefault(false)
            }
            val eventReceived = runCatching {
                composeRule.waitUntil(timeoutMillis = 30_000) {
                    val playing = runCatching { composeRule.onNodeWithTag("youtube_player_playing").fetchSemanticsNode(); true }.getOrDefault(false)
                    val error = runCatching { composeRule.onNodeWithTag("youtube_player_error_state").fetchSemanticsNode(); true }.getOrDefault(false)
                    playing || error
                }
                true
            }.getOrDefault(false)

            if (!eventReceived) {
                outcomes += "$title: no IFrame status"
                continue
            }
            if (runCatching { composeRule.onNodeWithTag("youtube_player_playing").fetchSemanticsNode(); true }.getOrDefault(false)) {
                composeRule.onNodeWithTag("youtube_player_playing").fetchSemanticsNode()
                return
            }
            outcomes += "$title: rejected for embedding"
        }
        throw AssertionError("No candidate reached PLAYING inside the app: ${outcomes.joinToString("; ")}")
    }
}
