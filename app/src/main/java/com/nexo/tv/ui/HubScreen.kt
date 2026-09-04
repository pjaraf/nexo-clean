package com.nexo.tv.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
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
import com.nexo.tv.data.SeriesEpisode
import com.nexo.tv.data.SeriesItem
import com.nexo.tv.data.VodItem
import com.nexo.tv.data.XtreamClient

private val Orange = Color(0xFFDE5B17)
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
        val title = "$seriesName · T${ep.season} ${ep.label}"
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

    Box(Modifier.fillMaxSize()) {
        LoginBackdrop()

        Box(Modifier.fillMaxSize()) {
            when {
                selectedSeries != null -> SeriesDetailPane(
                    series = selectedSeries!!,
                    onBack = { selectedSeries = null },
                    onPlayEpisode = { cover, ep ->
                        openEpisode(selectedSeries!!.name, cover ?: selectedSeries!!.cover, ep)
                    }
                )
                tab == Tab.HOME -> HomePane(
                    movies = movies2026,
                    onMovie = { openMovie(it) }
                )
                tab == Tab.SERIES -> Box(
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
                tab == Tab.MOVIES -> Box(
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
                tab == Tab.TV -> {}
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
            NavIcon(Icons.Filled.Home, tab == Tab.HOME && selectedSeries == null) {
                selectedSeries = null
                tab = Tab.HOME
            }
            NavIcon(Icons.Filled.LiveTv, tab == Tab.TV) {
                selectedSeries = null
                ctx.startActivity(
                    Intent(ctx, LiveActivity::class.java)
                        .putExtra(LiveActivity.EXTRA_USER, Session.username)
                        .putExtra(LiveActivity.EXTRA_PASS, Session.password)
                        .putExtra(LiveActivity.EXTRA_SERVER, Session.server)
                )
            }
            NavIcon(Icons.Filled.Tv, tab == Tab.SERIES && selectedSeries == null) {
                selectedSeries = null
                tab = Tab.SERIES
            }
            NavIcon(Icons.Filled.Movie, tab == Tab.MOVIES) {
                selectedSeries = null
                tab = Tab.MOVIES
            }
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
    var loading by remember(series.id) { mutableStateOf(true) }
    var cover by remember(series.id) { mutableStateOf(series.cover) }
    var seasons by remember(series.id) {
        mutableStateOf<Map<String, List<SeriesEpisode>>>(emptyMap())
    }
    var selectedSeason by remember(series.id) { mutableStateOf<String?>(null) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(series.id) {
        loading = true
        error = null
        val (infoCover, eps) = XtreamClient.seriesEpisodes(series.id)
        if (!infoCover.isNullOrBlank()) cover = infoCover
        seasons = eps
        selectedSeason = eps.keys.firstOrNull()
        if (eps.isEmpty()) error = "No se encontraron episodios"
        loading = false
    }

    val episodes = selectedSeason?.let { seasons[it] }.orEmpty()

    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 88.dp, top = 24.dp, end = 24.dp, bottom = 20.dp)
    ) {
        Column(
            Modifier.width(160.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = cover,
                contentDescription = series.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(140.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF222222))
            )
            Spacer(Modifier.height(12.dp))
            Text(
                series.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onBack)
                    .focusable()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Volver", color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.width(20.dp))

        Column(Modifier.fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Orange)
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                }
                else -> {
                    Text("Temporadas", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons.keys.toList(), key = { it }) { season ->
                            val selected = season == selectedSeason
                            Box(
                                Modifier
                                    .tvFocus(shape = RoundedCornerShape(20.dp), focusedScale = 1.04f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) Orange else Color.White.copy(alpha = 0.14f))
                                    .clickable { selectedSeason = season }
                                    .focusable()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("T$season", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Episodios",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(episodes, key = { it.id }) { ep ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.02f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .clickable { onPlayEpisode(cover, ep) }
                                    .focusable()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Orange,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    ep.label,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
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
