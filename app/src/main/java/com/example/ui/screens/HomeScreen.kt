package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Movie
import com.example.ui.theme.*
import com.example.ui.viewmodel.CineStreamUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: CineStreamUiState,
    onMovieClick: (Movie) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onGenreSelect: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenServerConfig: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit = {},
    onLiveCategorySelect: (String) -> Unit = {}
) {
    var showSearchBar by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
            ) {
                // Barra Superior Principal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo CineStream
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = NetflixRed,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "C",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CINESTREAM",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                            color = NetflixRed
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRefresh, modifier = Modifier.testTag("refresh_catalog_button")) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar catálogo", tint = TextPrimary)
                        }
                        IconButton(
                            onClick = { showSearchBar = !showSearchBar },
                            modifier = Modifier.testTag("toggle_search_button")
                        ) {
                            Icon(
                                imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = TextPrimary
                            )
                        }
                    }
                }
                Text(
                    text = "Películas completas de canales de YouTube",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 56.dp, bottom = 2.dp)
                )
                Text(
                    text = "Catálogo: ${uiState.totalMovieCount} películas",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .padding(start = 56.dp, bottom = 6.dp)
                        .testTag("catalog_movie_count")
                )

                // Selector de Pestaña: Inicio vs Películas vs Series vs TV en Vivo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("Inicio", "Películas")
                    tabs.forEach { tab ->
                        val isSelected = uiState.activeTab == tab
                        Surface(
                            onClick = { onSelectTab(tab) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) NetflixRed else DarkSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("tab_${tab.replace(" ", "_")}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when (tab) {
                                    "Inicio" -> Icons.Default.Home
                                    "Películas" -> Icons.Default.Movie
                                    "Series" -> Icons.Default.Tv
                                    else -> Icons.Default.LiveTv
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tab,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Barra de Búsqueda Desplegable
                AnimatedVisibility(visible = showSearchBar) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Buscar películas de YouTube...", color = TextMuted) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = NetflixRed,
                                unfocusedBorderColor = AccentBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_field")
                        )
                    }
                }

                // Chips de Categorías (adaptativos según pestaña activa)
                if (uiState.activeTab == "TV en Vivo") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.availableLiveCategories) { cat ->
                            val isSelected = uiState.selectedLiveCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { onLiveCategorySelect(cat) },
                                label = {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NetflixRed,
                                    containerColor = DarkSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) NetflixRed else AccentBorder
                                ),
                                modifier = Modifier.testTag("live_cat_chip_$cat")
                            )
                        }
                    }
                } else if (uiState.activeTab == "Películas" || uiState.activeTab == "Series") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.availableGenres) { genre ->
                            val isSelected = uiState.selectedGenre == genre
                            FilterChip(
                                selected = isSelected,
                                onClick = { onGenreSelect(genre) },
                                label = {
                                    Text(
                                        text = genre,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NetflixRed,
                                    containerColor = DarkSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) NetflixRed else AccentBorder
                                ),
                                modifier = Modifier.testTag("genre_chip_$genre")
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.activeTab == "TV en Vivo") {
            // VISTA: CANALES DE TV EN VIVO DE YOUTUBE ORGANIZADOS POR CATEGORÍA
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("live_tv_channels_list"),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Banner Informativo TV en Vivo
                item {
                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFFD32F2F),
                                shape = CircleShape,
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TELEVISIÓN EN VIVO • PLUTO TV & YOUTUBE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                                Text(
                                    text = if (uiState.selectedLiveCategory == "Todos")
                                        "Explora canales y señales en vivo las 24 horas organizadas por categoría"
                                    else
                                        "Mostrando canales en vivo para '${uiState.selectedLiveCategory}'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                if (uiState.selectedLiveCategory != "Todos") {
                    val filtered = uiState.liveChannels
                    if (filtered.isNotEmpty()) {
                        item {
                            MovieRowSection(
                                title = "Canales en Vivo: ${uiState.selectedLiveCategory}",
                                badge = "EN DIRECTO",
                                movies = filtered,
                                onMovieClick = onMovieClick
                            )
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Buscando transmisiones en vivo de ${uiState.selectedLiveCategory}...",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    // Vista General: Organizado por Categorías principales
                    val categories = listOf("Noticias", "Deportes", "Películas", "Entretenimiento", "Música", "Infantil", "Ciencia", "Gaming")
                    
                    categories.forEach { categoryName ->
                        val channelsInCat = uiState.liveChannelsByCategory[categoryName] 
                            ?: uiState.liveChannels.filter { it.genre.equals(categoryName, ignoreCase = true) }

                        if (channelsInCat.isNotEmpty()) {
                            item {
                                MovieRowSection(
                                    title = "Canales de $categoryName en Vivo",
                                    badge = "24/7",
                                    movies = channelsInCat,
                                    onMovieClick = onMovieClick
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    // Otros Canales en Vivo o Fallbacks
                    val otherChannels = uiState.liveChannels.filter { movie ->
                        categories.none { cat -> cat.equals(movie.genre, ignoreCase = true) }
                    }
                    if (otherChannels.isNotEmpty()) {
                        item {
                            MovieRowSection(
                                title = "Otras Señales y Canales en Vivo",
                                badge = "LIVE",
                                movies = otherChannels,
                                onMovieClick = onMovieClick
                            )
                        }
                    }
                }
            }
        } else if (uiState.activeTab == "Películas") {
            // VISTA: PELÍCULAS (EXCLUSIVO CINE)
            val moviesOnly = uiState.movies.filter { !it.isSeries && !it.isLive }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("movies_catalog_list"),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Película Destacada en Portada
                val featuredMovie = moviesOnly.firstOrNull { it.isFeatured } ?: moviesOnly.firstOrNull()
                featuredMovie?.let { featured ->
                    item {
                        HeroBillboard(
                            movie = featured,
                            onPlayClick = {
                                onPlayMovie(featured)
                            },
                            onInfoClick = { onMovieClick(featured) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Películas por Género
                val genres = moviesOnly.map { it.genre }.distinct().sorted()
                genres.forEach { genreName ->
                    val filteredInGenre = moviesOnly.filter { it.genre.equals(genreName, ignoreCase = true) }
                    if (filteredInGenre.isNotEmpty() && (uiState.selectedGenre == "Todos" || uiState.selectedGenre.equals(genreName, ignoreCase = true))) {
                        item {
                            MovieRowSection(
                                title = "Películas de $genreName",
                                movies = filteredInGenre,
                                onMovieClick = onMovieClick
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }

                val remainingMovies = if (uiState.selectedGenre == "Todos") moviesOnly else moviesOnly.filter { it.genre.equals(uiState.selectedGenre, ignoreCase = true) }
                if (remainingMovies.isNotEmpty() && uiState.selectedGenre != "Todos") {
                    item {
                        MovieRowSection(
                            title = "Películas Encontradas: ${uiState.selectedGenre}",
                            movies = remainingMovies,
                            onMovieClick = onMovieClick
                        )
                    }
                }
            }
        } else if (uiState.activeTab == "Series") {
            // VISTA: SERIES DE TELEVISIÓN
            val seriesOnly = uiState.movies.filter { it.isSeries && !it.isLive }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("series_catalog_list"),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Serie Destacada en Portada
                val featuredSeries = seriesOnly.firstOrNull { it.isFeatured } ?: seriesOnly.firstOrNull()
                featuredSeries?.let { featured ->
                    item {
                        HeroBillboard(
                            movie = featured,
                            onPlayClick = {
                                onPlayMovie(featured)
                            },
                            onInfoClick = { onMovieClick(featured) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Series por Género
                val genres = listOf("Ciencia Ficción", "Acción", "Drama")
                genres.forEach { genreName ->
                    val filteredInGenre = seriesOnly.filter { it.genre.equals(genreName, ignoreCase = true) }
                    if (filteredInGenre.isNotEmpty() && (uiState.selectedGenre == "Todos" || uiState.selectedGenre.equals(genreName, ignoreCase = true))) {
                        item {
                            MovieRowSection(
                                title = "Series de $genreName",
                                badge = "SERIE",
                                movies = filteredInGenre,
                                onMovieClick = onMovieClick
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }

                val remainingSeries = if (uiState.selectedGenre == "Todos") seriesOnly else seriesOnly.filter { it.genre.equals(uiState.selectedGenre, ignoreCase = true) }
                if (remainingSeries.isNotEmpty() && uiState.selectedGenre != "Todos") {
                    item {
                        MovieRowSection(
                            title = "Series Encontradas: ${uiState.selectedGenre}",
                            badge = "SERIE",
                            movies = remainingSeries,
                            onMovieClick = onMovieClick
                        )
                    }
                }
            }
        } else {
            // VISTA: INICIO (HOME PORTAL INTEGRADO)
            val allMoviesOnly = uiState.movies.filter { !it.isSeries && !it.isLive }
            val allSeriesOnly = uiState.movies.filter { it.isSeries && !it.isLive }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("movies_catalog_list"),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = "${uiState.movies.size} títulos · Se actualizan desde listas de canales de YouTube",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                // 1. Hero Featured Movie Billboard (unificado)
                val globalFeatured = uiState.featuredMovie ?: uiState.movies.firstOrNull()
                globalFeatured?.let { featured ->
                    item {
                        HeroBillboard(
                            movie = featured,
                            onPlayClick = {
                                onPlayMovie(featured)
                            },
                            onInfoClick = { onMovieClick(featured) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Lazy rows keep the full catalog responsive.
                if (allMoviesOnly.isNotEmpty()) {
                    item {
                        MovieRowSection(
                            title = if (uiState.searchQuery.isBlank()) "Películas para descubrir" else "Resultados: ${allMoviesOnly.size}",
                            badge = "YOUTUBE",
                            movies = if (uiState.searchQuery.isBlank()) allMoviesOnly.take(18) else allMoviesOnly,
                            onMovieClick = onMovieClick
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    if (uiState.searchQuery.isBlank()) {
                        allMoviesOnly.groupBy { it.genre }.toSortedMap().forEach { (genreName, titles) ->
                            item {
                                MovieRowSection(
                                    title = genreName,
                                    movies = titles,
                                    onMovieClick = onMovieClick
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                } else if (!uiState.isLoading) {
                    item {
                        Text(
                            text = uiState.errorMessage ?: "No encontré títulos con esa búsqueda.",
                            color = TextSecondary,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                if (uiState.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = NetflixRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Cargando catálogo…", color = TextSecondary)
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun HeroBillboard(
    movie: Movie,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clickable { onInfoClick() }
            .testTag("hero_billboard")
    ) {
        // Imagen de Fondo del Hero
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(movie.backdropUrl.ifEmpty { movie.posterUrl })
                .crossfade(true)
                .build(),
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Degradado Cinematográfico hacia negro profundo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            DarkBackground.copy(alpha = 0.5f),
                            DarkBackground.copy(alpha = 0.85f),
                            DarkBackground
                        ),
                        startY = 100f
                    )
                )
        )

        // Información y Botones del Hero
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = NetflixRed,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (movie.streamUrl.contains("youtube.com/embed/")) "YOUTUBE" else "DESTACADO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (movie.streamUrl.contains("youtube.com/embed/")) "PELÍCULA COMPLETA" else movie.quality,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BadgeTeal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (movie.rating >= 0.0) {
                    Text("★ ${movie.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RatingGold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Título
            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Sinopsis breve
            Text(
                text = movie.synopsis,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Botones de Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("hero_play_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reproducir",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                OutlinedButton(
                    onClick = onInfoClick,
                    shape = RoundedCornerShape(8.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Color.White, Color.Gray))),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceElevated.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("hero_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Más Info",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieRowSection(
    title: String,
    badge: String? = null,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Título de la fila
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            badge?.let {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = CrimsonDark,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Carrusel Horizontal de Películas
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(movies) { movie ->
                MovieCard(movie = movie, onClick = { onMovieClick(movie) })
            }
        }
    }
}

@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(135.dp)
            .height(205.dp)
            .clickable { onClick() }
            .testTag("movie_card_${movie.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(movie.posterUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Badge de Calificación o LIVE en la esquina
            Surface(
                color = if (movie.isLive) Color(0xFFD32F2F) else Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(bottomStart = 6.dp),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (movie.isLive) {
                        Text(
                            text = "● EN VIVO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    } else if (movie.streamUrl.contains("youtube.com/embed/")) {
                        Text("YOUTUBE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else if (movie.rating >= 0.0) {
                        Text("★ ${movie.rating}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RatingGold)
                    }
                }
            }

            // Sombra y Título inferior
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = movie.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
