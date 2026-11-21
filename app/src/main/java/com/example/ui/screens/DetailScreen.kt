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
                        text = movie.title,
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
                                text = if (movie.streamUrl.contains("youtube.com/embed/")) "YOUTUBE" else movie.quality,
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
                            onPlay(movie)
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
                            text = "Reproducir película en YouTube",
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
                        text = movie.synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )

                    if (movie.streamUrl.contains("youtube.com/embed/")) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Canal en YouTube", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Publicado por ${movie.creator.ifBlank { "el canal de YouTube" }}. La película se reproduce con el reproductor oficial de YouTube; puede no estar disponible en todas las regiones o si el canal la retira.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    lineHeight = 19.sp
                                )
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(movie.sourceUrl))) }
                                }) { Text("Abrir en YouTube") }
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

                    if (movie.streamUrl.contains("youtube.com/embed/")) {
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
                                text = "La disponibilidad, el idioma de audio y los subtítulos dependen de cada video de YouTube. CineStream no los da por verificados.",
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
