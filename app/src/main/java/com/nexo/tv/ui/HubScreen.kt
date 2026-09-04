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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val movies = Catalog.movies
    val series = Catalog.series

    Box(Modifier.fillMaxSize()) {
        // Fondo cinematográfico en todas las pestañas
        LoginBackdrop()

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomePane(
                    movies = movies.take(30),
                    onMovie = { item ->
                        ctx.startActivity(
                            Intent(ctx, VodActivity::class.java)
                                .putExtra(VodActivity.EXTRA_URL, XtreamClient.movieUrl(item.id, item.ext ?: "mp4"))
                                .putExtra(VodActivity.EXTRA_TITLE, item.name)
                        )
                    }
                )
                Tab.SERIES -> Box(
                    Modifier
                        .padding(start = 88.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
                        .fillMaxSize()
                ) {
                    PosterGrid(items = series.map { it.id to (it.cover to it.name) })
                }
                Tab.MOVIES -> Box(
                    Modifier
                        .padding(start = 88.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
                        .fillMaxSize()
                ) {
                    PosterGrid(
                        items = movies.map { it.id to (it.streamIcon to it.name) },
                        onClick = { id ->
                            val item = movies.find { it.id == id } ?: return@PosterGrid
                            ctx.startActivity(
                                Intent(ctx, VodActivity::class.java)
                                    .putExtra(VodActivity.EXTRA_URL, XtreamClient.movieUrl(item.id, item.ext ?: "mp4"))
                                    .putExtra(VodActivity.EXTRA_TITLE, item.name)
                            )
                        }
                    )
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
                // Misma proporción/tamaño que el resto de carátulas
                Poster(
                    url = movie.streamIcon,
                    title = movie.name,
                    modifier = Modifier
                        .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.04f)
                        .clickable { onMovie(movie) }
                        .focusable()
                )
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = movie.name,
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
            }
        }

        Spacer(Modifier.weight(1f))
        Text("Películas", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(movies, key = { it.id }) { m ->
                Poster(
                    url = m.streamIcon,
                    title = m.name,
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
    // Solo carátula — sin nombre debajo
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
