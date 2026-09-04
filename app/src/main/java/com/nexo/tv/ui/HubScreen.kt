package com.nexo.tv.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexo.tv.LiveActivity
import com.nexo.tv.Session
import com.nexo.tv.VodActivity
import com.nexo.tv.data.Catalog
import com.nexo.tv.data.Favorites
import com.nexo.tv.data.SeriesDetailInfo
import com.nexo.tv.data.SeriesEpisode
import com.nexo.tv.data.SeriesItem
import com.nexo.tv.data.VodItem
import com.nexo.tv.data.XtreamClient

private val Orange = Color(0xFFDE5B17)
private val SeriesBlue = Color(0xFF007AFF)
private val SeriesAmber = Color(0xFFFFC107)
private val SeriesRating = Color(0xFF00B0FF)
private val PosterW = 132.dp
private val PosterH = 188.dp

private enum class Tab { HOME, TV, SERIES, MOVIES }

@Composable
fun HubScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    var selectedSeries by remember { mutableStateOf<SeriesItem?>(null) }
    val movies = Catalog.movies
    val series = Catalog.series
    val movies2026 = remember(movies) { movies.filter { it.matchesYear(2026) } }

    fun openMovie(item: VodItem) {
        ctx.startActivity(
            Intent(ctx, VodActivity::class.java)
                .putExtra(VodActivity.EXTRA_URL, XtreamClient.movieUrl(item.id, item.ext ?: "mp4"))
                .putExtra(VodActivity.EXTRA_TITLE, item.displayName)
                .putExtra(VodActivity.EXTRA_POSTER, item.streamIcon.orEmpty())
        )
    }

    fun openEpisode(seriesName: String, cover: String?, ep: SeriesEpisode) {
        val title = "$seriesName · T${ep.season} E${ep.episodeNum}"
        ctx.startActivity(
            Intent(ctx, VodActivity::class.java)
                .putExtra(VodActivity.EXTRA_URL, XtreamClient.seriesUrl(ep.id, ep.ext))
                .putExtra(VodActivity.EXTRA_TITLE, title)
                .putExtra(VodActivity.EXTRA_POSTER, cover.orEmpty())
        )
    }

    BackHandler(enabled = selectedSeries != null) {
        selectedSeries = null
    }

    // Detalle de serie a pantalla completa (como la foto)
    if (selectedSeries != null) {
        SeriesDetailPane(
            series = selectedSeries!!,
            onBack = { selectedSeries = null },
            onPlayEpisode = { cover, ep ->
                openEpisode(selectedSeries!!.name, cover ?: selectedSeries!!.cover, ep)
            }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LoginBackdrop()

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomePane(
                    movies = movies2026,
                    onMovie = { openMovie(it) }
                )
                Tab.SERIES -> Box(
                    Modifier
                        .padding(start = 88.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
                        .fillMaxSize()
                ) {
                    if (series.isEmpty()) {
                        Text(
                            "No hay series en el catálogo",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        PosterGrid(
                            items = series.map { it.id to (it.cover to it.name) },
                            onClick = { id ->
                                selectedSeries = series.find { it.id == id }
                            }
                        )
                    }
                }
                Tab.MOVIES -> Box(
                    Modifier
                        .padding(start = 88.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
                        .fillMaxSize()
                ) {
                    if (movies2026.isEmpty()) {
                        Text(
                            "No hay películas de 2026",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        PosterGrid(
                            items = movies2026.map { it.id to (it.streamIcon to it.displayName) },
                            onClick = { id ->
                                movies2026.find { it.id == id }?.let { openMovie(it) }
                            }
                        )
                    }
                }
                Tab.TV -> {}
            }
        }

        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp, top = 20.dp, bottom = 20.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "N",
                color = Orange,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            NavIcon(Icons.Filled.Home, tab == Tab.HOME) { tab = Tab.HOME }
            NavIcon(Icons.Filled.LiveTv, tab == Tab.TV) {
                ctx.startActivity(
                    Intent(ctx, LiveActivity::class.java)
                        .putExtra(LiveActivity.EXTRA_USER, Session.username)
                        .putExtra(LiveActivity.EXTRA_PASS, Session.password)
                        .putExtra(LiveActivity.EXTRA_SERVER, Session.server)
                )
            }
            NavIcon(Icons.Filled.Tv, tab == Tab.SERIES) { tab = Tab.SERIES }
            NavIcon(Icons.Filled.Movie, tab == Tab.MOVIES) { tab = Tab.MOVIES }
            Spacer(Modifier.weight(1f))
            NavIcon(Icons.Filled.Logout, false) {
                Session.logout()
                onLogout()
            }
        }
    }
}

@Composable
private fun SeriesDetailPane(
    series: SeriesItem,
    onBack: () -> Unit,
    onPlayEpisode: (cover: String?, ep: SeriesEpisode) -> Unit
) {
    val ctx = LocalContext.current
    var loading by remember(series.id) { mutableStateOf(true) }
    var info by remember(series.id) { mutableStateOf<SeriesDetailInfo?>(null) }
    var seasons by remember(series.id) {
        mutableStateOf<Map<String, List<SeriesEpisode>>>(emptyMap())
    }
    var selectedSeason by remember(series.id) { mutableStateOf<String?>(null) }
    var selectedEpisode by remember(series.id) { mutableStateOf<SeriesEpisode?>(null) }
    var favorite by remember(series.id) {
        mutableStateOf(Favorites.isSeriesFavorite(ctx, series.id))
    }
    var error by remember(series.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(series.id) {
        loading = true
        error = null
        val detail = XtreamClient.seriesDetail(series.id)
        info = detail.info
        seasons = detail.episodes
        val first = detail.episodes.keys.firstOrNull()
        selectedSeason = first
        selectedEpisode = first?.let { detail.episodes[it]?.firstOrNull() }
        if (detail.episodes.isEmpty()) error = "No se encontraron episodios"
        loading = false
    }

    val episodes = selectedSeason?.let { seasons[it] }.orEmpty()
    LaunchedEffect(selectedSeason, episodes) {
        if (selectedEpisode == null || episodes.none { it.id == selectedEpisode?.id }) {
            selectedEpisode = episodes.firstOrNull()
        }
    }

    val title = info?.displayTitle?.takeIf { it.isNotBlank() } ?: series.name
    val cover = info?.cover?.takeIf { it.isNotBlank() } ?: series.cover
    val backdrop = info?.backdropUrl ?: selectedEpisode?.image ?: cover
    val castText = info?.cast?.takeIf { it.isNotBlank() } ?: "—"
    val plotText = info?.plot?.takeIf { it.isNotBlank() }
        ?: "Disfruta de todos los episodios en alta definición."
    val dateLine = buildString {
        val d = info?.displayDate.orEmpty()
        if (d.isNotBlank()) append(d).append(" | ")
        append(title)
    }
    val rating = info?.ratingBadge.orEmpty()
    val epTag = selectedEpisode?.let { "T${selectedSeason ?: it.season} - E${it.episodeNum}" } ?: ""
    val epRange = if (episodes.isNotEmpty()) {
        val nums = episodes.map { it.episodeNum }.filter { it > 0 }
        if (nums.isNotEmpty()) "${nums.min()}-${nums.max()}" else "1-${episodes.size}"
    } else ""

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0E15))) {
        AsyncImage(
            model = backdrop ?: cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.78f),
                            Color(0xFF0D0E15).copy(alpha = 0.88f),
                            Color(0xFF08090E).copy(alpha = 0.97f)
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.88f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SeriesBlue)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlineChip("Volver", Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                }
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 16.dp)
            ) {
                // Top bar
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlineChip("Volver", Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                    OutlineChip(
                        label = if (favorite) "En Favoritos" else "Añadir a Favoritos",
                        icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        onClick = {
                            favorite = Favorites.toggleSeries(ctx, series.id)
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Main row: info + media
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                title,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (rating.isNotBlank()) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SeriesRating)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(rating, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dateLine,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (epTag.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(epTag, color = SeriesAmber, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text("Actores: ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                castText,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("Sinopsis: ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                plotText,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SeriesActionButton(
                                label = "Pantalla completa",
                                icon = Icons.Filled.Tv,
                                primary = true,
                                onClick = {
                                    selectedEpisode?.let { onPlayEpisode(cover, it) }
                                }
                            )
                            SeriesActionButton(
                                label = "Idioma y subtítulos",
                                icon = Icons.Filled.Subtitles,
                                primary = false,
                                onClick = {
                                    // Mismo reproductor: ahí se cambian audio/subs
                                    selectedEpisode?.let { onPlayEpisode(cover, it) }
                                }
                            )
                        }
                    }

                    // Poster + preview
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = cover,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(110.dp)
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF222222))
                        )
                        AsyncImage(
                            model = backdrop ?: cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A1A1A))
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Seasons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons.keys.toList(), key = { it }) { season ->
                            val selected = season == selectedSeason
                            var focused by remember { mutableStateOf(false) }
                            Box(
                                Modifier
                                    .onFocusChanged { focused = it.isFocused }
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        when {
                                            focused -> SeriesBlue
                                            selected -> SeriesBlue.copy(alpha = 0.92f)
                                            else -> Color.White.copy(alpha = 0.12f)
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            if (focused) 2.dp else 1.dp,
                                            if (focused) Color.White else Color.White.copy(alpha = 0.2f)
                                        ),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { selectedSeason = season }
                                    .focusable()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Temporada $season",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    if (epRange.isNotBlank()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(epRange, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Episode number grid
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(episodes, key = { it.id }) { ep ->
                        val selected = ep.id == selectedEpisode?.id
                        var focused by remember { mutableStateOf(false) }
                        Box(
                            Modifier
                                .size(48.dp)
                                .onFocusChanged { focused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        focused -> SeriesBlue
                                        selected -> SeriesBlue
                                        else -> Color.White.copy(alpha = 0.10f)
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        if (focused || selected) 2.dp else 1.dp,
                                        if (focused || selected) Color.White.copy(alpha = 0.9f)
                                        else Color.White.copy(alpha = 0.22f)
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedEpisode = ep
                                    onPlayEpisode(cover, ep)
                                }
                                .focusable(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "${ep.episodeNum}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Text(
                                    "${ep.episodeNum}",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) SeriesBlue else Color.White.copy(alpha = 0.10f))
            .border(
                BorderStroke(if (focused) 2.dp else 1.dp, Color.White.copy(alpha = if (focused) 0.95f else 0.35f)),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SeriesActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> SeriesBlue
        primary -> SeriesAmber
        else -> Color.White.copy(alpha = 0.12f)
    }
    val fg = if (!focused && primary) Color.Black else Color.White
    Row(
        Modifier
            .height(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                BorderStroke(
                    if (focused) 2.dp else 1.dp,
                    if (focused) Color.White else Color.White.copy(alpha = 0.28f)
                ),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NavIcon(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .tvFocus(shape = RoundedCornerShape(50), focusedScale = 1.08f)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    selected -> Orange.copy(alpha = 0.92f)
                    else -> Color.Black.copy(alpha = 0.28f)
                }
            )
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun HomePane(
    movies: List<VodItem>,
    onMovie: (VodItem) -> Unit
) {
    var featured by remember(movies) { mutableStateOf(movies.firstOrNull()) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 88.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
    ) {
        Text("NEXO", color = Orange, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(22.dp))

        featured?.let { movie ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = movie.displayName,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .tvFocus(shape = RoundedCornerShape(12.dp), focusedScale = 1.04f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Orange)
                            .clickable { onMovie(movie) }
                            .focusable()
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reproducir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.width(22.dp))
                Poster(
                    url = movie.streamIcon,
                    title = movie.displayName,
                    modifier = Modifier
                        .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.04f)
                        .clickable { onMovie(movie) }
                        .focusable()
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Text("Películas 2026", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (movies.isEmpty()) {
            Text(
                "No hay películas de 2026 en el catálogo",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(movies, key = { it.id }) { m ->
                    Poster(
                        url = m.streamIcon,
                        title = m.displayName,
                        modifier = Modifier
                            .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.04f)
                            .onFocusChanged { if (it.isFocused) featured = m }
                            .clickable { onMovie(m) }
                            .focusable()
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(
    items: List<Pair<String, Pair<String?, String>>>,
    onClick: (String) -> Unit = {}
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items, key = { it.first }) { (id, pair) ->
            Poster(
                url = pair.first,
                title = pair.second,
                modifier = Modifier
                    .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.04f)
                    .clickable { onClick(id) }
                    .focusable()
            )
        }
    }
}

@Composable
private fun Poster(url: String?, title: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .width(PosterW)
            .height(PosterH)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
    )
}
