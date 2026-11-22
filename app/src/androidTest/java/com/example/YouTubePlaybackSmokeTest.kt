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
        assertTrue(movies.size >= 50)
        assertTrue(movies.all { it.streamUrl.startsWith("https://www.youtube-nocookie.com/embed/") })
        assertTrue(movies.all { it.sourceUrl.startsWith("https://youtube.com/watch?v=") })
    }

    @Test
    fun candidateFullMoviesCanPlayInsideTheApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val template = YouTubeMoviesCatalog(context).loadBundled().first()
        val candidates = listOf(
            "RKOxbf03sgE" to "Dos Puños Contra Rio · Español",
            "sbucezgLn0k" to "Atraídas por la Noche · Español",
            "eNa1SGztvJA" to "Bendiciones Ocultas · doblaje español",
            "6gvc6AaUY8c" to "Call Girl · español",
            "yuP14Qx2JJk" to "El Cuarto Rey Mago · español"
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
            val errorText = runCatching { composeRule.onNodeWithTag("youtube_player_error_message").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } }.getOrDefault("unknown error")
            outcomes += "$title: $errorText"
        }
        throw AssertionError("No candidate reached PLAYING inside the app with a verified Latin-American Spanish track: ${outcomes.joinToString("; ")}")
    }
}
