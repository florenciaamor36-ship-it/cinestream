package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import com.example.ui.viewmodel.CineStreamViewModel
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.MainActivity
import com.example.data.repository.YouTubeMoviesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises app navigation and waits for YouTube's IFrame API to report actual PLAYING state. */
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
    fun appOpensCuratedMovieAndLoadsOfficialYoutubeEmbedPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val movie = YouTubeMoviesCatalog(context).loadBundled().single { it.id == 10 }
        // Drive the real Activity-scoped ViewModel directly so this player test is independent
        // of Home/search/hero semantics; the target is a specific curated full movie.
        composeRule.activity.runOnUiThread {
            ViewModelProvider(composeRule.activity)[CineStreamViewModel::class.java].navigateToPlayer(movie)
        }
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching { composeRule.onNodeWithTag("video_player_screen").fetchSemanticsNode(); true }.getOrDefault(false)
        }
        composeRule.waitUntil(timeoutMillis = 120_000) {
            val playing = runCatching { composeRule.onNodeWithTag("youtube_player_playing").fetchSemanticsNode(); true }.getOrDefault(false)
            val error = runCatching { composeRule.onNodeWithTag("youtube_player_error_message").fetchSemanticsNode(); true }.getOrDefault(false)
            playing || error
        }
        val errorNode = runCatching { composeRule.onNodeWithTag("youtube_player_error_message").fetchSemanticsNode() }.getOrNull()
        if (errorNode != null) {
            val message = errorNode.config[SemanticsProperties.Text].joinToString { it.text }
            throw AssertionError("YouTube player returned an error for movie ${movie.id}: $message")
        }
        composeRule.onNodeWithTag("youtube_player_playing").fetchSemanticsNode()
    }
}

