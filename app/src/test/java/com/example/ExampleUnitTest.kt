package com.example

import com.example.data.plutotv.PlutoTVCrawler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
    @Test
    fun testPlutoTVCrawler() = runBlocking {
        println("--- Iniciando prueba de PlutoTVCrawler con Robolectric ---")
        val channels = PlutoTVCrawler.fetchPlutoChannels("Todos")
        println("Canales obtenidos totales: ${channels.size}")
        
        for (i in 0 until minOf(15, channels.size)) {
            val ch = channels[i]
            println("Canal $i: id=${ch.id}, title=${ch.title}, genre=${ch.genre}, url=${ch.streamUrl}")
        }
        
        assertTrue("Debería retornar muchos canales parseados dinámicamente de las 3 listas M3U", channels.size > 15)
    }
}
