package com.nexo.tv.ui

import android.content.Intent
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nexo.tv.LiveActivity
import com.nexo.tv.SeriesActivity
import com.nexo.tv.Session
import com.nexo.tv.VodActivity
import com.nexo.tv.data.Catalog
import com.nexo.tv.data.ContinueWatching
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    var continueItems by remember { mutableStateOf(ContinueWatching.list(ctx)) }
    val movies = Catalog.movies
    val series = Catalog.series
    val movies2026 = remember(movies) { movies.filter { it.matchesYear(2026) } }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                continueItems = ContinueWatching.list(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun openMovie(item: VodItem, resumeMs: Long = -1L) {
        ctx.startActivity(
            Intent(ctx, VodActivity::class.java)
                .putExtra(VodActivity.EXTRA_URL, XtreamClient.movieUrl(item.id, item.ext ?: "mp4"))
                .putExtra(VodActivity.EXTRA_TITLE, item.displayName)
                .putExtra(VodActivity.EXTRA_POSTER, item.streamIcon.orEmpty())
                .putExtra(VodActivity.EXTRA_ID, item.id)
                .putExtra(VodActivity.EXTRA_RESUME_MS, resumeMs)
        )
    }

    fun openSeries(
        item: SeriesItem,
        resumeEpisodeId: String = "",
        resumeMs: Long = -1L
    ) {
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

    fun openContinue(item: ContinueWatching.Item) {
        when (item.kind) {
            "movie" -> {
                val fromCatalog = movies.find { it.id == item.id }
                val url = item.url?.takeIf { it.isNotBlank() }
                    ?: fromCatalog?.let { XtreamClient.movieUrl(it.id, it.ext ?: "mp4") }
                    ?: return
                ctx.startActivity(
                    Intent(ctx, VodActivity::class.java)
                        .putExtra(VodActivity.EXTRA_URL, url)
                        .putExtra(VodActivity.EXTRA_TITLE, item.title)
                        .putExtra(VodActivity.EXTRA_POSTER, item.poster.orEmpty())
                        .putExtra(VodActivity.EXTRA_ID, item.id)
                        .putExtra(VodActivity.EXTRA_RESUME_MS, item.positionMs)
                )
            }
            "series" -> {
                ctx.startActivity(
                    Intent(ctx, SeriesActivity::class.java)
                        .putExtra(SeriesActivity.EXTRA_SERIES_ID, item.id)
                        .putExtra(SeriesActivity.EXTRA_SERIES_NAME, item.title)
                        .putExtra(SeriesActivity.EXTRA_SERIES_COVER, item.poster.orEmpty())
                        .putExtra(SeriesActivity.EXTRA_CATEGORY_ID, item.categoryId.orEmpty())
                        .putExtra(SeriesActivity.EXTRA_RESUME_EPISODE_ID, item.episodeId.orEmpty())
                        .putExtra(SeriesActivity.EXTRA_RESUME_MS, item.positionMs)
                        .putExtra(SeriesActivity.EXTRA_USER, Session.username)
                        .putExtra(SeriesActivity.EXTRA_PASS, Session.password)
                        .putExtra(SeriesActivity.EXTRA_SERVER, Session.server)
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LoginBackdrop()

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomePane(
                    movies = movies2026,
                    continueItems = continueItems,
                    onMovie = { openMovie(it) },
                    onContinue = { openContinue(it) }
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
                                series.find { it.id == id }?.let { openSeries(it) }
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
    continueItems: List<ContinueWatching.Item>,
    onMovie: (VodItem) -> Unit,
    onContinue: (ContinueWatching.Item) -> Unit
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
                        .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.04f)
                        .clickable { onMovie(movie) }
                        .focusable()
                )
            }
        }

        if (continueItems.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Seguir viendo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(continueItems, key = { "${it.kind}:${it.id}" }) { item ->
                    ContinuePoster(item = item, onClick = { onContinue(item) })
                }
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
private fun ContinuePoster(item: ContinueWatching.Item, onClick: () -> Unit) {
    Column(
        Modifier
            .width(PosterW)
            .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.04f)
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Box {
            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(PosterW)
                    .height(PosterH)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222222))
            )
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                color = Orange,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            item.subtitle,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            maxLines = 1
        )
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
