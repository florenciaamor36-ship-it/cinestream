package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CineStream", appName)
  }

  @Test
  fun `live channel mapping converts to playable movie`() {
    val live = com.example.data.model.LiveChannel(
      id = "euronews-es",
      name = "Euronews en Español",
      category = "Noticias 24/7",
      logoUrl = "https://example.com/logo.png",
      youtubeUrl = "https://youtube.com/live/xyz",
      streamUrl = "https://example.com/master.m3u8",
      isLive = true
    )
    val movie = live.toMovie()
    assertEquals("Euronews en Español", movie.title)
    assertEquals("En Vivo", movie.genre)
    assertEquals(true, movie.isLive)
    assertEquals("https://example.com/master.m3u8", movie.streamUrl)
  }
}

