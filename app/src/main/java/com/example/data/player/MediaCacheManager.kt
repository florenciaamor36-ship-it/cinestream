package com.example.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.data.network.NetworkClient
import java.io.File

/**
 * Gestor centralizado de caché de segmentos de video Media3 / ExoPlayer.
 * Almacena localmente en disco los chunks HTTP descargados mediante SimpleCache
 * y LeastRecentlyUsedCacheEvictor (300 MB).
 * Reduce hasta un 50% las peticiones entrantes hacia el backend y los bots de Telegram.
 */
@OptIn(UnstableApi::class)
object MediaCacheManager {

    private const val CACHE_DIR_NAME = "cinestream_video_cache"
    private const val MAX_CACHE_SIZE_BYTES = 300L * 1024L * 1024L // 300 MB

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    @Synchronized
    fun getSimpleCache(context: Context): SimpleCache {
        if (simpleCacheInstance == null) {
            val cacheFolder = File(context.cacheDir, CACHE_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            simpleCacheInstance = SimpleCache(cacheFolder, evictor, databaseProvider)
        }
        return simpleCacheInstance!!
    }

    /**
     * Construye un DataSource.Factory que primero consulta la caché local
     * y, si el fragmento no está presente, lo descarga a través de OkHttp con Range Requests
     * hacia el servidor de streaming de CineStream.
     */
    fun createCacheDataSourceFactory(context: Context): DataSource.Factory {
        val cache = getSimpleCache(context)

        // Upstream factory usando DefaultHttpDataSource con followRedirects y UserAgent estándar
        val upstreamHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 CineStream/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamHttpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * DataSource directo para transmisiones en vivo (HLS / m3u8) que no deben ser cacheadas
     * en disco de la misma manera que los archivos estáticos VOD, y que requieren soporte completo
     * de redirecciones cross-protocol y headers de streaming.
     */
    fun createLiveDataSourceFactory(context: Context): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 CineStream/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)

        return androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
    }

    /**
     * Devuelve el espacio ocupado actualmente por la caché en MegaBytes.
     */
    fun getCacheSizeMb(context: Context): Double {
        val cache = getSimpleCache(context)
        return cache.cacheSpace.toDouble() / (1024.0 * 1024.0)
    }

    /**
     * Limpia la caché local para liberar almacenamiento del dispositivo.
     */
    fun clearCache(context: Context) {
        val cache = getSimpleCache(context)
        for (key in cache.keys) {
            cache.removeResource(key)
        }
    }
}
