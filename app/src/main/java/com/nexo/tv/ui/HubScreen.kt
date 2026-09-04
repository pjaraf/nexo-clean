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
import com.nexo.tv.data.VodItem
import com.nexo.tv.data.XtreamClient

private val Orange = Color(0xFFDE5B17)
private val Bg = Color(0xFF0D0D0D)
private val Side = Color(0xFF141414)

private enum class Tab { HOME, TV, SERIES, MOVIES }

@Composable
fun HubScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    val movies = Catalog.movies
    val series = Catalog.series

    Row(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .width(100.dp)
                .fillMaxHeight()
                .background(Side)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("N", color = Orange, fontSize = 28.sp, fontWeight = FontWeight.Black)
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
                Tab.SERIES -> Box(Modifier.padding(24.dp).fillMaxSize()) {
                    PosterGrid(items = series.map { it.id to (it.cover to it.name) })
                }
                Tab.MOVIES -> Box(Modifier.padding(24.dp).fillMaxSize()) {
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
    }
}

@Composable
private fun NavIcon(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .tvFocus(shape = RoundedCornerShape(14.dp), focusedScale = 1.06f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Orange.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Orange else Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun HomePane(
    movies: List<VodItem>,
    onMovie: (VodItem) -> Unit
) {
    var featured by remember(movies) { mutableStateOf(movies.firstOrNull()) }
    val cover = featured?.streamIcon

    Box(Modifier.fillMaxSize().background(Color(0xFF070707))) {
        // Carátula grande a la derecha, bien visible (sin estirar mal)
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.58f)
        )
        // Degradado suave solo a la izquierda para leer el texto; la carátula queda clara
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xFF070707),
                        0.28f to Color(0xE6070707),
                        0.52f to Color(0x66070707),
                        0.72f to Color(0x14000000),
                        1.0f to Color.Transparent
                    )
                )
        )
        // Degradado abajo para la fila de películas
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color(0xCC070707),
                        1f to Color(0xF2070707)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 20.dp, top = 26.dp, bottom = 14.dp)
        ) {
            Text("NEXO", color = Orange, fontSize = 32.sp, fontWeight = FontWeight.Black)

            featured?.let { movie ->
                Column(
                    Modifier
                        .padding(top = 28.dp)
                        .fillMaxWidth(0.42f)
                        .weight(1f)
                ) {
                    Text(
                        text = movie.name,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(20.dp))
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
            } ?: Spacer(Modifier.weight(1f))

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
    Column(modifier.width(132.dp)) {
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(188.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222222))
        )
        Text(
            title,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
