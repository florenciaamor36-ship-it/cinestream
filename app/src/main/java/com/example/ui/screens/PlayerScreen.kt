package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.Movie
import com.example.data.player.MediaCacheManager
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    movie: Movie,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Estado de controles y reproducción
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }

    val isYouTubeEmbed = movie.streamUrl.contains("youtube.com/embed")

    // Inicializar ExoPlayer con CacheDataSource para VOD o LiveDataSource para HLS (solo si no es YouTube embed)
    val exoPlayer = remember(movie.id, movie.streamUrl) {
        if (isYouTubeEmbed) return@remember null
        val dataSourceFactory = if (movie.isLive) {
            MediaCacheManager.createLiveDataSourceFactory(context)
        } else {
            MediaCacheManager.createCacheDataSourceFactory(context)
        }

        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10000L) // 10 segundos
            .setSeekForwardIncrementMs(10000L)
            .build().apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(movie.streamUrl))
                    .apply {
                        if (movie.isLive || movie.streamUrl.contains(".m3u8")) {
                            setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                        }
                    }
                    .build()

                val mediaSource = if (movie.isLive || movie.streamUrl.contains(".m3u8")) {
                    androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(mediaItem)
                } else {
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                }

                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
    }

    // Listener para actualizar estados de ExoPlayer
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) {
            isBuffering = false
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                    playbackError = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                val causeMsg = error.cause?.message ?: error.errorCodeName
                playbackError = "Error en el stream: ${error.message ?: causeMsg}"
                android.util.Log.e("CineStreamPlayer", "ExoPlayer error (${error.errorCode}): ${error.message}", error)
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Bucle para actualizar la barra de progreso y buffer continuamente
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        while (isActive) {
            currentPositionMs = exoPlayer.currentPosition
            bufferedPositionMs = exoPlayer.bufferedPosition
            val total = exoPlayer.duration
            if (total > 0 && total != C.TIME_UNSET) {
                durationMs = total
            }
            delay(300)
        }
    }

    // Ocultar controles automáticamente tras 4 segundos de inactividad
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !isBuffering) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
            .testTag("video_player_screen")
    ) {
        // Vista de Video: YouTube WebView nativo o Media3 / ExoPlayer
        if (isYouTubeEmbed) {
            val embedUrl = if (movie.streamUrl.contains("origin=")) {
                movie.streamUrl
            } else {
                "${movie.streamUrl}&origin=https://www.youtube.com"
            }

            val htmlData = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                    <style>
                        html, body {
                            margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden;
                        }
                        iframe {
                            width: 100%; height: 100%; border: none;
                        }
                    </style>
                </head>
                <body>
                    <iframe 
                        id="player"
                        src="$embedUrl" 
                        allow="autoplay; encrypted-media; picture-in-picture" 
                        referrerpolicy="no-referrer-when-downgrade"
                        allowfullscreen>
                    </iframe>
                </body>
                </html>
            """.trimIndent()

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Usamos nuestros controles modernos en Jetpack Compose
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay de Controles Modernos
        AnimatedVisibility(
            visible = showControls || isBuffering || playbackError != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                // Barra Superior de Controles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Salir",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (movie.isLive) {
                                Surface(
                                    color = Color(0xFFD32F2F),
                                    shape = RoundedCornerShape(3.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "EN VIVO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = "Transmisión en Vivo • HLS (.m3u8)",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            } else {
                                Surface(
                                    color = NetflixRed,
                                    shape = RoundedCornerShape(3.dp),
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "WORKER POOL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = "Stream HTTP Range • Caché SimpleCache Activa",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Botón Silenciar / Sonido
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            exoPlayer?.volume = if (isMuted) 0f else 1f
                        }
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volumen",
                            tint = Color.White
                        )
                    }
                }

                // Controles Centrales (Rebobinar 10s, Play/Pausa/Buffering, Adelantar 10s)
                if (!isYouTubeEmbed) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // -10 Segundos
                        IconButton(
                            onClick = {
                                exoPlayer?.let { p ->
                                    p.seekTo(maxOf(0L, p.currentPosition - 10000L))
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .testTag("player_seek_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Retroceder 10s",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Play / Pause o Spinner de Buffer
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = NetflixRed,
                                strokeWidth = 3.dp,
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("player_buffering_spinner")
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    exoPlayer?.let { p ->
                                        if (isPlaying) {
                                            p.pause()
                                        } else {
                                            p.play()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(68.dp)
                                    .background(NetflixRed, CircleShape)
                                    .testTag("player_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        // +10 Segundos
                        IconButton(
                            onClick = {
                                exoPlayer?.let { p ->
                                    p.seekTo(minOf(durationMs, p.currentPosition + 10000L))
                                }
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .testTag("player_seek_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Adelantar 10s",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Error en pantalla con botón de reintento
                playbackError?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = NetflixRed, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = err, color = Color.White, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    playbackError = null
                                    isBuffering = true
                                    val dataSourceFactory = if (movie.isLive) {
                                        MediaCacheManager.createLiveDataSourceFactory(context)
                                    } else {
                                        MediaCacheManager.createCacheDataSourceFactory(context)
                                    }
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(movie.streamUrl))
                                        .apply {
                                            if (movie.isLive || movie.streamUrl.contains(".m3u8")) {
                                                setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                                            }
                                        }
                                        .build()

                                    val mediaSource = if (movie.isLive || movie.streamUrl.contains(".m3u8")) {
                                        androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                                            .setAllowChunklessPreparation(true)
                                            .createMediaSource(mediaItem)
                                    } else {
                                        androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                                            .createMediaSource(mediaItem)
                                    }
                                    exoPlayer?.setMediaSource(mediaSource)
                                    exoPlayer?.prepare()
                                    exoPlayer?.play()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                            ) {
                                Text("Reintentar")
                            }
                        }
                    }
                }

                // Barra Inferior de Control de Progreso y Tiempo (oculta en streaming de YouTube Webview)
                if (!isYouTubeEmbed) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        // Barra Deslizante (Scrubber Slider)
                        val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                        val bufferProgress = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Barra de Buffer (Gris claro)
                            LinearProgressIndicator(
                                progress = { bufferProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .padding(horizontal = 6.dp),
                                color = Color.White.copy(alpha = 0.35f),
                                trackColor = Color.White.copy(alpha = 0.15f)
                            )

                            // Slider Interactivo de Media
                            Slider(
                                value = progress,
                                onValueChange = { newProgress ->
                                    val newPos = (newProgress * durationMs).toLong()
                                    currentPositionMs = newPos
                                    exoPlayer?.seekTo(newPos)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = NetflixRed,
                                    activeTrackColor = NetflixRed,
                                    inactiveTrackColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("player_progress_slider")
                            )
                        }

                        // Tiempos e Indicadores de Calidad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = movie.quality,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BadgeTeal,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (movie.isLive) "LIVE STREAM" else "CACHE OK",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (movie.isLive) Color(0xFFFF5252) else Color(0xFF81C784),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formatea milisegundos a formato HH:MM:SS o MM:SS
 */
private fun formatTime(timeMs: Long): String {
    if (timeMs <= 0) return "00:00"
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
