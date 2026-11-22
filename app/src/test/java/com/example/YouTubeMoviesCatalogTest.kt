package com.example

import com.example.data.repository.YouTubeMoviesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeMoviesCatalogTest {
    @Test fun playlistFeedKeepsFullMovieCandidatesAndYouTubeIds() {
        val xml = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/">
          <entry><id>yt:video:AbCdEf12345</id><yt:videoId>AbCdEf12345</yt:videoId><title>Película Completa en Español</title><author><name>Canal de Cine</name></author><media:group><media:thumbnail url="https://i.ytimg.com/vi/AbCdEf12345/hqdefault.jpg"/></media:group></entry>
          <entry><yt:videoId>12345678901</yt:videoId><title>Trailer de Película Completa</title><author><name>Canal de Cine</name></author></entry>
          <entry><yt:videoId>AbCdEf1234</yt:videoId><title>Película Completa con ID inválido</title></entry>
          <entry><yt:videoId>ZyXwVu98765</yt:videoId><title>Full Movie in English</title><author><name>Other Channel</name></author></entry>
        </feed>"""
        val source = YouTubeMoviesCatalog.PlaylistSource("Lista prueba", "PL123", "Canal de Cine")
        val result = YouTubeMoviesCatalog.parseFeed(xml, source)
        assertEquals(1, result.size)
        assertTrue(result.all { it.streamUrl.startsWith("https://www.youtube-nocookie.com/embed/") })
        assertTrue(result.all { it.sourceUrl.startsWith("https://youtube.com/watch?v=") })
        assertTrue(result.any { it.creator == "Canal de Cine" })
        assertTrue(result.none { it.title.contains("Trailer", ignoreCase = true) })
        assertTrue(result.all { it.rating < 0.0 && it.releaseYear == 0 && it.durationMin == 0 })
    }
}

