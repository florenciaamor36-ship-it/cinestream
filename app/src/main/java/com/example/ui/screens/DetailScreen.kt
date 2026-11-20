package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Movie
import com.example.data.model.EpgProgram
import com.example.data.repository.OnDeviceSpanishTranslator
import kotlinx.coroutines.launch
import com.example.ui.theme.*

@Composable
fun DetailScreen(
    movie: Movie,
    onBack: () -> Unit,
    onPlay: (Movie) -> Unit,
    epgPrograms: List<EpgProgram> = emptyList()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var translatedTitle by remember(movie.archiveIdentifier) { mutableStateOf<String?>(null) }
    var translatedSynopsis by remember(movie.archiveIdentifier) { mutableStateOf<String?>(null) }
    var isTranslating by remember(movie.archiveIdentifier) { mutableStateOf(false) }
    var translationError by remember(movie.archiveIdentifier) { mutableStateOf<String?>(null) }
    val canPlayInApp = movie.licenseUrl == "http://creativecommons.org/publicdomain/mark/1.0/"

    Scaffold(
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // 1. Cabecera con Backdrop y Poster
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(movie.backdropUrl.ifEmpty { movie.posterUrl })
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Scrim degradado inferior
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                        DarkBackground.copy(alpha = 0.8f),
                                        DarkBackground
                                    )
                                )
                            )
                    )
                }

                // 2. Contenido Informativo Principal
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    // Título de la Película
                    Text(
                        text = translatedTitle ?: movie.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.testTag("detail_movie_title")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (movie.releaseYear > 0) {
                            Text("Año indicado: ${movie.releaseYear}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        if (movie.durationMin > 0) {
                            Text("${movie.durationMin} min en ficha", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Surface(color = DarkSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = if (movie.archiveIdentifier.isNotBlank()) "INTERNET ARCHIVE" else movie.quality,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BadgeTeal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        if (movie.genre.isNotBlank()) {
                            Text(movie.genre, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. Botón Gigante de "Reproducir"
                    Button(
                        onClick = {
                            if (movie.archiveIdentifier.isNotBlank() && !canPlayInApp) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(movie.sourceUrl))) }
                            } else onPlay(movie)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("detail_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                movie.archiveIdentifier.isBlank() -> "Reproducir ahora"
                                canPlayInApp -> "Reproducir desde Internet Archive"
                                else -> "Abrir ficha original"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Sinopsis
                    Text(
                        text = "Sinopsis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = translatedSynopsis ?: movie.synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )

                    if (movie.archiveIdentifier.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "La primera vez descarga por Wi‑Fi el modelo del idioma; solo traduce texto de la ficha.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        TextButton(
                            enabled = !isTranslating,
                            onClick = {
                                if (translatedTitle != null) {
                                    translatedTitle = null
                                    translatedSynopsis = null
                                    translationError = null
                                } else {
                                    isTranslating = true
                                    translationError = null
                                    scope.launch {
                                        try {
                                            translatedTitle = OnDeviceSpanishTranslator.translate(movie.title, movie.sourceLanguage)
                                            translatedSynopsis = OnDeviceSpanishTranslator.translate(movie.synopsis, movie.sourceLanguage)
                                        } catch (e: Exception) {
                                            translationError = "No pude traducir esta ficha. Con conexión, volvé a intentarlo."
                                        } finally {
                                            isTranslating = false
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(when {
                                isTranslating -> "Descargando modelo o traduciendo…"
                                translatedTitle != null -> "Ver texto original"
                                else -> "Traducir ficha al español"
                            })
                        }
                        translationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Fuente y derechos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    if (canPlayInApp)
                                        "La ficha declara la marca Public Domain Mark 1.0. Es una declaración del registro, no una verificación legal independiente; revisá la página original antes de reutilizar o redistribuir el contenido."
                                    else if (movie.licenseUrl.isNotBlank())
                                        "La ficha declara una licencia, pero CineStream no la verifica ni la admite para reproducir dentro de la app. Revisá sus condiciones en la página original."
                                    else
                                        "Esta ficha no declara una licencia. Por eso CineStream no habilita la reproducción dentro de la app. Revisá los derechos y condiciones en la página original.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    lineHeight = 19.sp
                                )
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(movie.sourceUrl))) }
                                }) { Text("Abrir ficha original de Internet Archive") }
                            }
                        }
                    }

                    if (movie.isLive && epgPrograms.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("Programación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        epgPrograms.take(8).forEach { program ->
                            val accent = if (program.now) NetflixRed else DarkSurfaceVariant
                            Surface(color = accent.copy(alpha = if (program.now) 0.22f else 1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(program.title, color = TextPrimary, fontWeight = if (program.now) FontWeight.Bold else FontWeight.Medium)
                                    if (program.description.isNotBlank()) Text(program.description, color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                    Text(if (program.now) "Ahora" else "${program.start.replace("T", " ").take(16)} – ${program.end.replace("T", " ").take(16)}", color = if (program.now) NetflixRed else TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (movie.isSeries) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = NetflixRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NetflixRed.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = NetflixRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Serie de TV • Completa",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${movie.seasons} Temporadas • ${movie.episodesCount} Capítulos Disponibles",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Capítulos de la Temporada 1",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Lista de capítulos oficiales
                        for (i in 1..4) {
                            Surface(
                                onClick = { 
                                    onPlay(movie.copy(title = "${movie.title} - T1:E$i"))
                                },
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("episode_item_$i")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(DarkSurfaceVariant, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Capítulo $i",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "Episodio $i de la temporada inaugural",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${movie.durationMin} min",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Technical playback claims are shown only for legacy configured media; Archive records are verified on demand.
                    if (movie.archiveIdentifier.isBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = BadgeTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Información de Reproducción",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Audio: Español Latino / Castellano (Dolby Atmos)\n" +
                                        "• Subtítulos: Español / Inglés (Disponibles)\n" +
                                        "• Formato: Flujo Adaptativo HLS (Media3 ExoPlayer)\n" +
                                        "• Resolución: ${movie.quality} Inteligente\n" +
                                        "• Almacenamiento: Caché inteligente de alta velocidad activa",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Botón Flotante Atrás
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                    .testTag("detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
        }
    }
}
