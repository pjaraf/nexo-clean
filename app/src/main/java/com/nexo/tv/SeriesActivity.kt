package com.nexo.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.nexo.tv.data.SeriesDetailInfo
import com.nexo.tv.data.SeriesEpisode
import com.nexo.tv.data.SeriesItem
import com.nexo.tv.data.XtreamClient
import com.nexo.tv.player.StreamBridge
import com.nexo.tv.player.VlcEngine
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.roundToInt

/**
 * Detalle de serie + preview VLC. "Pantalla completa" expande el mismo motor
 * (sin segunda reproducción).
 */
class SeriesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepAwakeWhileVisible()
        intent.getStringExtra(EXTRA_USER)?.let { Session.username = it }
        intent.getStringExtra(EXTRA_PASS)?.let { Session.password = it }
        intent.getStringExtra(EXTRA_SERVER)?.let { if (it.isNotBlank()) Session.server = it }

        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID).orEmpty()
        val seriesName = intent.getStringExtra(EXTRA_SERIES_NAME).orEmpty()
        val seriesCoverExtra = intent.getStringExtra(EXTRA_SERIES_COVER).orEmpty()
        val categoryIdExtra = intent.getStringExtra(EXTRA_CATEGORY_ID).orEmpty()

        StreamBridge.start()
        val engine = VlcEngine(this)

        setContent {
            var loading by remember { mutableStateOf(true) }
            var info by remember { mutableStateOf<SeriesDetailInfo?>(null) }
            var seasons by remember { mutableStateOf<Map<String, List<SeriesEpisode>>>(emptyMap()) }
            var selectedSeason by remember { mutableStateOf<String?>(null) }
            var selectedEpisode by remember { mutableStateOf<SeriesEpisode?>(null) }
            var recommended by remember { mutableStateOf<List<SeriesItem>>(emptyList()) }
            var error by remember { mutableStateOf<String?>(null) }
            var fullScreen by remember { mutableStateOf(false) }
            var playing by remember { mutableStateOf(true) }
            var position by remember { mutableLongStateOf(0L) }
            var duration by remember { mutableLongStateOf(0L) }
            var toast by remember { mutableStateOf<String?>(null) }
            var hudVisible by remember { mutableStateOf(true) }
            var hudTick by remember { mutableIntStateOf(0) }
            var slotX by remember { mutableIntStateOf(0) }
            var slotY by remember { mutableIntStateOf(0) }
            var slotW by remember { mutableIntStateOf(0) }
            var slotH by remember { mutableIntStateOf(0) }
            val playFocus = remember { FocusRequester() }
            val density = LocalDensity.current

            fun bumpHud() {
                hudVisible = true
                hudTick++
            }

            fun playEpisode(ep: SeriesEpisode, expand: Boolean = false) {
                selectedEpisode = ep
                val url = StreamBridge.maybeWrap(XtreamClient.seriesUrl(ep.id, ep.ext))
                engine.playVod(url)
                playing = true
                if (expand) fullScreen = true
            }

            fun openRelated(item: SeriesItem) {
                // launchMode standard: nueva Activity con el contenido de esa serie
                startActivity(
                    Intent(this@SeriesActivity, SeriesActivity::class.java)
                        .putExtra(EXTRA_SERIES_ID, item.id)
                        .putExtra(EXTRA_SERIES_NAME, item.name)
                        .putExtra(EXTRA_SERIES_COVER, item.cover.orEmpty())
                        .putExtra(EXTRA_CATEGORY_ID, item.categoryId.orEmpty())
                        .putExtra(EXTRA_USER, Session.username)
                        .putExtra(EXTRA_PASS, Session.password)
                        .putExtra(EXTRA_SERVER, Session.server)
                )
                finish()
            }

            DisposableEffect(engine) {
                engine.onPlaying = {
                    playing = true
                    duration = engine.lengthMs()
                }
                onDispose { engine.release() }
            }

            LaunchedEffect(seriesId) {
                loading = true
                error = null
                val detail = XtreamClient.seriesDetail(seriesId)
                info = detail.info
                seasons = detail.episodes
                val first = detail.episodes.keys.firstOrNull()
                selectedSeason = first
                val firstEp = first?.let { detail.episodes[it]?.firstOrNull() }
                selectedEpisode = firstEp
                if (detail.episodes.isEmpty()) {
                    error = "No se encontraron episodios"
                } else if (firstEp != null) {
                    playEpisode(firstEp, expand = false)
                }

                val all = runCatching { XtreamClient.series() }.getOrDefault(emptyList())
                    .filter { it.id.isNotBlank() && it.id != seriesId }
                val genreTokens = detail.info?.genre.orEmpty()
                    .lowercase()
                    .split(',', '|', '/', ';')
                    .map { it.trim() }
                    .filter { it.length > 2 }
                val byCategory = if (categoryIdExtra.isNotBlank()) {
                    all.filter { it.categoryId == categoryIdExtra }
                } else emptyList()
                val byGenre = if (genreTokens.isNotEmpty()) {
                    all.filter { s ->
                        val g = (s.genre ?: "").lowercase()
                        val n = s.name.lowercase()
                        genreTokens.any { t -> g.contains(t) || n.contains(t) }
                    }
                } else emptyList()
                recommended = (byCategory + byGenre + all)
                    .distinctBy { it.id }
                    .take(14)

                loading = false
            }

            val episodes = selectedSeason?.let { seasons[it] }.orEmpty()
            LaunchedEffect(selectedSeason, episodes) {
                if (selectedEpisode == null || episodes.none { it.id == selectedEpisode?.id }) {
                    episodes.firstOrNull()?.let { playEpisode(it) }
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    position = engine.timeMs()
                    val len = engine.lengthMs()
                    if (len > 0) duration = len
                    playing = engine.isPlaying
                    delay(500)
                }
            }

            LaunchedEffect(hudTick, fullScreen) {
                if (!fullScreen || !hudVisible) return@LaunchedEffect
                delay(8000)
                hudVisible = false
            }

            LaunchedEffect(toast) {
                if (toast == null) return@LaunchedEffect
                delay(1800)
                toast = null
            }

            LaunchedEffect(fullScreen, hudVisible) {
                if (fullScreen && hudVisible) {
                    delay(80)
                    runCatching { playFocus.requestFocus() }
                }
            }

            BackHandler {
                when {
                    fullScreen -> fullScreen = false
                    else -> finish()
                }
            }

            val title = info?.displayTitle?.takeIf { it.isNotBlank() } ?: seriesName
            val cover = info?.cover?.takeIf { it.isNotBlank() } ?: seriesCoverExtra
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

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0E15))
                    .onPreviewKeyEvent { e ->
                        if (!fullScreen) return@onPreviewKeyEvent false
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (e.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                        when (e.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER,
                            AndroidKeyEvent.KEYCODE_DPAD_UP,
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                            AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                bumpHud(); true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                            AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                                engine.seekBy(-10_000); bumpHud(); true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                            AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                engine.seekBy(10_000); bumpHud(); true
                            }
                            else -> false
                        }
                    }
            ) {
                // Un solo surface VLC: miniatura o pantalla completa (misma instancia)
                AndroidView(
                    factory = { ctx ->
                        VLCVideoLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            keepScreenOn = true
                            isFocusable = false
                            isFocusableInTouchMode = false
                            engine.attach(this)
                        }
                    },
                    modifier = if (fullScreen) {
                        Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                    } else if (slotW > 0 && slotH > 0) {
                        Modifier
                            .zIndex(3f)
                            .offset { IntOffset(slotX, slotY) }
                            .width(with(density) { slotW.toDp() })
                            .height(with(density) { slotH.toDp() })
                            .clip(RoundedCornerShape(10.dp))
                    } else {
                        Modifier
                            .size(1.dp)
                            .zIndex(0f)
                    }
                )

                if (!fullScreen) {
                    AsyncImage(
                        model = backdrop ?: cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().zIndex(0f)
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(1f)
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
                            .zIndex(1f)
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
                        loading -> Box(
                            Modifier.fillMaxSize().zIndex(2f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = SeriesBlue)
                        }
                        error != null -> Box(
                            Modifier.fillMaxSize().zIndex(2f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(error!!, color = Color.White, fontSize = 16.sp)
                        }
                        else -> Column(
                            Modifier
                                .fillMaxSize()
                                .zIndex(2f)
                                .padding(horizontal = 22.dp, vertical = 10.dp)
                        ) {
                            // Carátula esquina izq. arriba | info | mini player 16:9
                            Row(
                                Modifier
                                    .weight(1.05f, fill = true)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(108.dp)
                                        .height(156.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF222222))
                                )

                                Column(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.Top
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            title,
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (rating.isNotBlank()) {
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(SeriesRating)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    rating,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        dateLine,
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (epTag.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            epTag,
                                            color = SeriesAmber,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row {
                                        Text(
                                            "Actores: ",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            castText,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Row {
                                        Text(
                                            "Sinopsis: ",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            plotText,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SeriesActionButton("Pantalla completa", Icons.Filled.Tv, true) {
                                            fullScreen = true
                                            bumpHud()
                                        }
                                        SeriesActionButton("Idioma y subtítulos", Icons.Filled.Subtitles, false) {
                                            toast = engine.cycleAudioTrack()
                                                ?: engine.cycleSubtitleTrack()
                                                ?: "Sin pistas"
                                        }
                                    }
                                }

                                // Mini player 16:9 (sin estirar)
                                Box(
                                    Modifier
                                        .width(360.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black)
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInRoot()
                                            slotX = pos.x.roundToInt()
                                            slotY = pos.y.roundToInt()
                                            slotW = coords.size.width
                                            slotH = coords.size.height
                                        }
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(seasons.keys.toList(), key = { it }) { season ->
                                        val selected = season == selectedSeason
                                        var focused by remember { mutableStateOf(false) }
                                        Box(
                                            Modifier
                                                .onFocusChanged { focused = it.isFocused }
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(
                                                    when {
                                                        focused || selected -> SeriesBlue
                                                        else -> Color.White.copy(alpha = 0.12f)
                                                    }
                                                )
                                                .border(
                                                    BorderStroke(
                                                        if (focused) 2.dp else 1.dp,
                                                        Color.White.copy(alpha = if (focused) 0.95f else 0.2f)
                                                    ),
                                                    RoundedCornerShape(18.dp)
                                                )
                                                .clickable { selectedSeason = season }
                                                .focusable()
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                "Temporada $season",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                if (epRange.isNotBlank()) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(epRange, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 2.dp)
                            ) {
                                items(episodes, key = { it.id }) { ep ->
                                    val selected = ep.id == selectedEpisode?.id
                                    var focused by remember { mutableStateOf(false) }
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .onFocusChanged { focused = it.isFocused }
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    focused || selected -> SeriesBlue
                                                    else -> Color.White.copy(alpha = 0.10f)
                                                }
                                            )
                                            .border(
                                                BorderStroke(
                                                    if (focused || selected) 2.dp else 1.dp,
                                                    Color.White.copy(alpha = if (focused || selected) 0.9f else 0.22f)
                                                ),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { playEpisode(ep) }
                                            .focusable(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.PlayArrow,
                                                    null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    "${ep.episodeNum}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        } else {
                                            Text(
                                                "${ep.episodeNum}",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (recommended.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Recomendadas",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 2.dp)
                                ) {
                                    items(recommended, key = { it.id }) { item ->
                                        var focused by remember { mutableStateOf(false) }
                                        AsyncImage(
                                            model = item.cover,
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(72.dp)
                                                .height(102.dp)
                                                .onFocusChanged { focused = it.isFocused }
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    BorderStroke(
                                                        if (focused) 2.dp else 0.dp,
                                                        if (focused) SeriesBlue else Color.Transparent
                                                    ),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .background(Color(0xFF222222))
                                                .clickable { openRelated(item) }
                                                .focusable()
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Controles de pantalla completa sobre el MISMO video
                    if (hudVisible) {
                        Row(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(2f)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xE6000000))
                                    )
                                )
                                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "$title · $epTag",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                val progress = if (duration > 0) {
                                    (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = Color(0xFFDE5B17),
                                    trackColor = Color.White.copy(alpha = 0.22f)
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(formatSeriesTime(position), color = Color.White, fontSize = 11.sp)
                                    Text(
                                        formatSeriesTime(duration),
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MiniHudBtn(Icons.Filled.Replay10, "−10s") {
                                        engine.seekBy(-10_000); bumpHud()
                                    }
                                    MiniHudBtn(
                                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        if (playing) "Pausa" else "Play",
                                        playFocus
                                    ) {
                                        engine.togglePause()
                                        playing = engine.isPlaying
                                        bumpHud()
                                    }
                                    MiniHudBtn(Icons.Filled.Forward10, "+10s") {
                                        engine.seekBy(10_000); bumpHud()
                                    }
                                    MiniHudBtn(Icons.Filled.Translate, "Audio") {
                                        toast = engine.cycleAudioTrack() ?: "Sin audio"
                                        bumpHud()
                                    }
                                    MiniHudBtn(Icons.Filled.Subtitles, "Subs") {
                                        toast = engine.cycleSubtitleTrack()
                                        bumpHud()
                                    }
                                    MiniHudBtn(Icons.Filled.AspectRatio, "Pantalla") {
                                        toast = engine.cycleAspectMode()
                                        bumpHud()
                                    }
                                }
                            }
                        }
                    }
                }

                toast?.let { msg ->
                    Text(
                        msg,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .zIndex(5f)
                            .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_SERIES_ID = "series_id"
        const val EXTRA_SERIES_NAME = "series_name"
        const val EXTRA_SERIES_COVER = "series_cover"
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"
        const val EXTRA_SERVER = "server"
    }
}

private val SeriesBlue = Color(0xFF007AFF)
private val SeriesAmber = Color(0xFFFFC107)
private val SeriesRating = Color(0xFF00B0FF)

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
private fun MiniHudBtn(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { isFocused = it.isFocused }
                .clip(CircleShape)
                .background(if (isFocused) Color(0xFFDE5B17) else Color.White.copy(alpha = 0.16f))
                .clickable(onClick = onClick)
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (isFocused) Color(0xFFFF6A1A) else Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatSeriesTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
