package com.nexo.tv.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nexo.tv.AppExit
import com.nexo.tv.LiveActivity
import com.nexo.tv.MovieActivity
import com.nexo.tv.SeriesActivity
import com.nexo.tv.Session
import com.nexo.tv.data.Catalog
import com.nexo.tv.data.SeriesItem
import com.nexo.tv.data.VodItem
import kotlinx.coroutines.delay

private val Orange = Color(0xFFDE5B17)
private val PosterW = 132.dp
private val PosterH = 188.dp

private enum class Tab { HOME, TV, SERIES, MOVIES }

@Composable
fun HubScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    val movies = Catalog.movies
    val series = Catalog.series
    val movies2026 = remember(movies) { movies.filter { it.matchesYear(2026) } }
    val liveFocus = remember { FocusRequester() }

    // Foco por defecto en TV en vivo (OK abre canales).
    var hubResumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hubResumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(hubResumeTick) {
        delay(200)
        runCatching { liveFocus.requestFocus() }
    }

    fun openMovie(item: VodItem, resumeMs: Long = -1L) {
        AppExit.openChildActivity {
            ctx.startActivity(
                Intent(ctx, MovieActivity::class.java)
                    .putExtra(MovieActivity.EXTRA_MOVIE_ID, item.id)
                    .putExtra(MovieActivity.EXTRA_MOVIE_NAME, item.displayName)
                    .putExtra(MovieActivity.EXTRA_MOVIE_COVER, item.streamIcon.orEmpty())
                    .putExtra(MovieActivity.EXTRA_CATEGORY_ID, item.categoryId.orEmpty())
                    .putExtra(MovieActivity.EXTRA_EXT, item.ext ?: "mp4")
                    .putExtra(MovieActivity.EXTRA_RESUME_MS, resumeMs)
                    .putExtra(MovieActivity.EXTRA_USER, Session.username)
                    .putExtra(MovieActivity.EXTRA_PASS, Session.password)
                    .putExtra(MovieActivity.EXTRA_SERVER, Session.server)
            )
        }
    }

    fun openSeries(
        item: SeriesItem,
        resumeEpisodeId: String = "",
        resumeMs: Long = -1L
    ) {
        AppExit.openChildActivity {
            ctx.startActivity(
                Intent(ctx, SeriesActivity::class.java)
                    .putExtra(SeriesActivity.EXTRA_SERIES_ID, item.id)
                    .putExtra(SeriesActivity.EXTRA_SERIES_NAME, item.name)
                    .putExtra(SeriesActivity.EXTRA_SERIES_COVER, item.cover.orEmpty())
                    .putExtra(SeriesActivity.EXTRA_CATEGORY_ID, item.categoryId.orEmpty())
                    .putExtra(SeriesActivity.EXTRA_RESUME_EPISODE_ID, resumeEpisodeId)
                    .putExtra(SeriesActivity.EXTRA_RESUME_MS, resumeMs)
                    .putExtra(SeriesActivity.EXTRA_USER, Session.username)
                    .putExtra(SeriesActivity.EXTRA_PASS, Session.password)
                    .putExtra(SeriesActivity.EXTRA_SERVER, Session.server)
            )
        }
    }

    fun openLive() {
        AppExit.openChildActivity {
            ctx.startActivity(
                Intent(ctx, LiveActivity::class.java)
                    .putExtra(LiveActivity.EXTRA_USER, Session.username)
                    .putExtra(LiveActivity.EXTRA_PASS, Session.password)
                    .putExtra(LiveActivity.EXTRA_SERVER, Session.server)
            )
        }
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
                        .padding(start = 88.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
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
                                series.find { it.id == id }?.let { openSeries(it) }
                            }
                        )
                    }
                }
                Tab.MOVIES -> Box(
                    Modifier
                        .padding(start = 88.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
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
            NavIcon(
                icon = Icons.Filled.LiveTv,
                selected = tab == Tab.TV,
                focusRequester = liveFocus
            ) {
                openLive()
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
private fun NavIcon(
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(52.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(50), focusedScale = 1.08f)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    focused || selected -> Orange.copy(alpha = 0.92f)
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
        Spacer(Modifier.height(18.dp))

        featured?.let { movie ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = movie.displayName,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(14.dp))
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
                        .width(PosterW)
                        .height(PosterH)
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
                            .width(PosterW)
                            .height(PosterH)
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
    val cols = 6
    val rowsVisible = 2
    val hGap = 10.dp
    val vGap = 10.dp
    val edgePad = 8.dp
    // Margen para que el foco (scale) no recorte bordes.
    val focusPad = 8.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val usableW = maxWidth - edgePad * 2 - focusPad * 2
        val usableH = maxHeight - edgePad * 2 - focusPad * 2
        val cellW = (usableW - hGap * (cols - 1)) / cols
        val cellH = (usableH - vGap * (rowsVisible - 1)) / rowsVisible
        // Encaja 2:3 dentro de la celda para que quepan 6×2 enteras.
        val posterW = minOf(cellW, cellH * 2f / 3f)
        val posterH = posterW * 3f / 2f

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(edgePad + focusPad),
            horizontalArrangement = Arrangement.spacedBy(hGap),
            verticalArrangement = Arrangement.spacedBy(vGap)
        ) {
            items(items, key = { it.first }) { (id, pair) ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Poster(
                        url = pair.first,
                        title = pair.second,
                        modifier = Modifier
                            .width(posterW)
                            .height(posterH)
                            .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.04f)
                            .clickable { onClick(id) }
                            .focusable()
                    )
                }
            }
        }
    }
}

@Composable
private fun Poster(url: String?, title: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
    )
}
