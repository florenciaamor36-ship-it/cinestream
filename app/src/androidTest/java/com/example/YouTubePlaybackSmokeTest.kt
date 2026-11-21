package com.example

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntil
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
    fun appOpensMovieAndLoadsOfficialYoutubeEmbedPage() {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            runCatching {
                composeRule.onNodeWithText("Ana Karénina. La historia de Vrónski", substring = true).assertExists()
                composeRule.onNodeWithTag("hero_play_button").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("hero_play_button").performClick()
        composeRule.waitUntil(timeoutMillis = 120_000) {
            runCatching {
                composeRule.onNodeWithTag("youtube_player_playing").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("youtube_player_playing").assertExists()
    }
}
