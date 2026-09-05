package com.nexo.tv.ui

import android.content.Context
import android.content.Intent
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nexo.tv.AppExit
import com.nexo.tv.CategorySidePanel
import com.nexo.tv.ChannelSideBanner
import com.nexo.tv.LiveActivity
import com.nexo.tv.MovieActivity
import com.nexo.tv.SeriesActivity
import com.nexo.tv.Session
import com.nexo.tv.data.Catalog
import com.nexo.tv.data.CategoryShelf
import com.nexo.tv.data.LiveCategory
import com.nexo.tv.data.LiveChannel
import com.nexo.tv.data.SeriesItem
import com.nexo.tv.data.VodItem
import com.nexo.tv.data.XtreamClient
import com.nexo.tv.player.StreamBridge
import com.nexo.tv.player.VlcEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.roundToInt

private val Orange = Color(0xFFDE5B17)
private val PosterW = 132.dp
private val PosterH = 188.dp

private const val PREFS = "nexo_live"
private const val KEY_CHANNEL = "channel_id"
private const val KEY_CATEGORY = "category_id"

private enum class Tab { HOME, TV, SERIES, MOVIES }

@Composable
fun HubScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val prefs = remember { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val engine = remember { VlcEngine(ctx) }

    var tab by remember { mutableStateOf(Tab.HOME) }
    val movies = Catalog.movies
    val series = Catalog.series
    val movies2026 = remember(movies) { movies.filter { it.matchesYear(2026) } }
    val liveFocus = remember { FocusRequester() }

    // Estado de canales en vivo para el mini reproductor y pantalla completa
    var allChannels by remember { mutableStateOf<List<LiveChannel>>(Catalog.liveChannels) }
    var categories by remember { mutableStateOf<List<LiveCategory>>(Catalog.liveCategories) }
    var selectedCategoryId by remember {
        mutableStateOf(prefs.getString(KEY_CATEGORY, null).orEmpty())
    }
    var index by remember { mutableIntStateOf(0) }
    var fullScreen by remember { mutableStateOf(false) }
    var showBanner by remember { mutableStateOf(false) }
    var bannerTick by remember { mutableIntStateOf(0) }
    var showCategories by remember { mutableStateOf(false) }

    // Medidas y posición de la ranura (slot) en HomePane
    var slotX by remember { mutableIntStateOf(0) }
    var slotY by remember { mutableIntStateOf(0) }
    var slotW by remember { mutableIntStateOf(0) }
    var slotH by remember { mutableIntStateOf(0) }

    val fullScreenFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val catListState = rememberLazyListState()

    val activeChannels = remember(allChannels, selectedCategoryId) {
        if (selectedCategoryId.isBlank()) allChannels
        else allChannels.filter { it.categoryId == selectedCategoryId }.ifEmpty { allChannels }
    }
    val selectedCategoryName = remember(categories, selectedCategoryId) {
        categories.firstOrNull { it.categoryId == selectedCategoryId }?.categoryName
            ?: if (selectedCategoryId.isBlank()) "Todas" else "Categoría"
    }
    val currentChannel = activeChannels.getOrNull(index) ?: allChannels.getOrNull(index)

    fun persistWatching(ch: LiveChannel) {
        val cat = ch.categoryId.orEmpty()
        prefs.edit()
            .putString(KEY_CHANNEL, ch.id)
            .putString(KEY_CATEGORY, cat)
            .apply()
    }

    fun revealBanner() {
        showBanner = true
        bannerTick++
    }

    fun playChannel(ch: LiveChannel, instant: Boolean) {
        persistWatching(ch)
        val remote = XtreamClient.liveUrl(ch.id)
        val toPlay = StreamBridge.maybeWrap(remote)
        revealBanner()
        if (instant) engine.playNow(toPlay) else engine.playZap(toPlay)
    }

    fun warmNeighbors(around: Int) {
        val list = activeChannels
        if (list.size < 2) return
        val n = list.size
        listOf(list[(around + 1) % n], list[(around - 1 + n) % n]).forEach { ch ->
            StreamBridge.warm(XtreamClient.liveUrl(ch.id))
        }
    }

    fun zap(delta: Int) {
        val list = activeChannels
        if (list.isEmpty()) return
        index = (index + delta + list.size) % list.size
        playChannel(list[index], instant = false)
        warmNeighbors(index)
    }

    fun selectCategory(catId: String) {
        selectedCategoryId = catId
        showCategories = false
        val list = if (catId.isBlank()) allChannels
        else allChannels.filter { it.categoryId == catId }.ifEmpty { allChannels }
        if (list.isEmpty()) {
            prefs.edit().putString(KEY_CATEGORY, catId).apply()
            return
        }
        val savedId = prefs.getString(KEY_CHANNEL, null).orEmpty()
        val resumeIdx = list.indexOfFirst { it.id == savedId }.takeIf { it >= 0 } ?: 0
        index = resumeIdx
        playChannel(list[resumeIdx], instant = true)
        if (catId.isBlank()) {
            prefs.edit().putString(KEY_CATEGORY, "").apply()
        }
        warmNeighbors(resumeIdx)
        runCatching { fullScreenFocus.requestFocus() }
    }

    // Auto-ocultar banner tras 3.5 segundos
    LaunchedEffect(bannerTick) {
        if (bannerTick == 0) return@LaunchedEffect
        delay(3500)
        showBanner = false
    }

    // Foco en pantalla completa cuando se expande
    LaunchedEffect(fullScreen) {
        if (fullScreen) {
            delay(80)
            runCatching { fullScreenFocus.requestFocus() }
            revealBanner()
        }
    }

    // Foco al abrir selector de categorías en pantalla completa
    LaunchedEffect(showCategories) {
        if (showCategories) {
            delay(80)
            runCatching { categoryFocus.requestFocus() }
            val idx = categories.indexOfFirst { it.categoryId == selectedCategoryId }.coerceAtLeast(0)
            if (categories.isNotEmpty()) {
                runCatching { catListState.scrollToItem(idx) }
            }
        } else if (fullScreen) {
            delay(40)
            runCatching { fullScreenFocus.requestFocus() }
        }
    }

    // Cargar canales y categorías de TV en vivo al iniciar
    LaunchedEffect(Unit) {
        var streams = Catalog.liveChannels
        var cats = Catalog.liveCategories
        if (streams.isEmpty()) {
            streams = runCatching { XtreamClient.liveChannels() }.getOrDefault(emptyList())
                .filter { it.id.isNotBlank() }
        }
        if (cats.isEmpty()) {
            cats = runCatching { XtreamClient.liveCategories() }.getOrDefault(emptyList())
                .filter { it.categoryId.isNotBlank() }
        }
        categories = buildList {
            add(LiveCategory(categoryId = "", categoryName = "Todas"))
            addAll(cats)
        }
        allChannels = streams

        val savedChannelId = prefs.getString(KEY_CHANNEL, null).orEmpty()
        val savedChannel = streams.firstOrNull { it.id == savedChannelId }

        val catId = when {
            savedChannel != null -> savedChannel.categoryId.orEmpty()
            else -> {
                val raw = prefs.getString(KEY_CATEGORY, null).orEmpty()
                if (raw.isNotBlank() && cats.any { it.categoryId == raw }) raw else ""
            }
        }
        selectedCategoryId = catId

        val list = if (catId.isBlank()) streams else streams.filter { it.categoryId == catId }.ifEmpty { streams }
        val playIdx = when {
            savedChannelId.isNotBlank() -> list.indexOfFirst { it.id == savedChannelId }.takeIf { it >= 0 } ?: 0
            else -> 0
        }
        val start = list.getOrNull(playIdx)
        if (start != null) {
            index = playIdx
            playChannel(start, instant = true)
            warmNeighbors(playIdx)
        }
    }

    // Control de ciclo de vida del motor VLC
    DisposableEffect(lifecycleOwner, engine) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (tab == Tab.HOME) engine.resume()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    engine.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            engine.release()
        }
    }

    // Pausar reproducción cuando no se está en HOME
    LaunchedEffect(tab) {
        if (tab == Tab.HOME) {
            engine.resume()
        } else {
            engine.pause()
        }
    }

    // Foco por defecto en TV en vivo
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
        engine.pause()
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
        engine.pause()
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

    // Atrás en pantalla completa: contrae sin reconexión al mini reproductor
    BackHandler(enabled = fullScreen) {
        if (showCategories) {
            showCategories = false
        } else {
            fullScreen = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        LoginBackdrop()

        // Un solo surface VLC: miniatura o pantalla completa (misma instancia, sin reconexión)
        AndroidView(
            factory = { c ->
                VLCVideoLayout(c).apply {
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
            update = { engine.attach(it) },
            modifier = if (fullScreen) {
                Modifier
                    .fillMaxSize()
                    .zIndex(10f)
            } else if (slotW > 0 && slotH > 0 && tab == Tab.HOME) {
                Modifier
                    .zIndex(3f)
                    .offset { IntOffset(slotX, slotY) }
                    .width(with(density) { slotW.toDp() })
                    .height(with(density) { slotH.toDp() })
                    .clip(RoundedCornerShape(12.dp))
            } else {
                Modifier
                    .size(1.dp)
                    .zIndex(0f)
            }
        )

        // Overlay interactivo sobre la miniatura para recibir foco y click en TV Box
        if (!fullScreen && slotW > 0 && slotH > 0 && tab == Tab.HOME) {
            var miniFocused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .zIndex(4f)
                    .offset { IntOffset(slotX, slotY) }
                    .width(with(density) { slotW.toDp() })
                    .height(with(density) { slotH.toDp() })
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        BorderStroke(
                            if (miniFocused) 3.dp else 1.dp,
                            if (miniFocused) Color.White else Color.White.copy(alpha = 0.2f)
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .tvFocus(shape = RoundedCornerShape(12.dp), focusedScale = 1.03f)
                    .onFocusChanged { miniFocused = it.isFocused }
                    .clickable { fullScreen = true }
                    .focusable()
            ) {
                // Indicador "EN VIVO"
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFF3D00))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "EN VIVO",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (currentChannel == null) {
                    CircularProgressIndicator(
                        color = Orange,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }

        // Controles superpuestos en pantalla completa
        if (fullScreen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(11f)
                    .focusRequester(fullScreenFocus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (showCategories) return@onPreviewKeyEvent false
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (e.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                        when (e.nativeKeyEvent.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                            AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                            AndroidKeyEvent.KEYCODE_PAGE_DOWN -> {
                                zap(1); true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_UP,
                            AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                            AndroidKeyEvent.KEYCODE_PAGE_UP -> {
                                zap(-1); true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                            AndroidKeyEvent.KEYCODE_MENU,
                            AndroidKeyEvent.KEYCODE_INFO -> {
                                showCategories = true
                                revealBanner()
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                            AndroidKeyEvent.KEYCODE_ENTER -> {
                                revealBanner(); true
                            }
                            AndroidKeyEvent.KEYCODE_BACK,
                            AndroidKeyEvent.KEYCODE_ESCAPE -> {
                                fullScreen = false; true
                            }
                            else -> false
                        }
                    }
            ) {
                if (showBanner && currentChannel != null && !showCategories) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                    ) {
                        ChannelSideBanner(
                            number = index + 1,
                            channel = currentChannel,
                            categoryName = selectedCategoryName
                        )
                    }
                }

                if (showCategories) {
                    Dialog(
                        onDismissRequest = { showCategories = false },
                        properties = DialogProperties(
                            dismissOnBackPress = true,
                            dismissOnClickOutside = false,
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { showCategories = false },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            CategorySidePanel(
                                categories = categories,
                                selectedCategoryId = selectedCategoryId,
                                listState = catListState,
                                firstFocus = categoryFocus,
                                onSelect = { selectCategory(it) }
                            )
                        }
                    }
                }
            }
        }

        // Interfaz principal (visible cuando no está en pantalla completa)
        if (!fullScreen) {
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.HOME -> HomePane(
                        movies = movies2026,
                        currentChannel = currentChannel,
                        channelNumber = if (activeChannels.isNotEmpty()) index + 1 else 0,
                        categoryName = selectedCategoryName,
                        onExpandLive = {
                            fullScreen = true
                        },
                        onPositionSlot = { x, y, w, h ->
                            slotX = x
                            slotY = y
                            slotW = w
                            slotH = h
                        },
                        onMovie = { openMovie(it) }
                    )
                    Tab.SERIES -> Box(
                        Modifier
                            .padding(start = 88.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                            .fillMaxSize()
                    ) {
                        val shelves = Catalog.seriesShelves
                        if (shelves.isEmpty()) {
                            Text(
                                "No hay series en el catálogo",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            CategoryBrowser(
                                shelves = shelves,
                                onPoster = { id -> series.find { it.id == id }?.let { openSeries(it) } }
                            )
                        }
                    }
                    Tab.MOVIES -> Box(
                        Modifier
                            .padding(start = 88.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                            .fillMaxSize()
                    ) {
                        val shelves = Catalog.movieShelves
                        if (shelves.isEmpty()) {
                            Text(
                                "No hay películas en el catálogo",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            CategoryBrowser(
                                shelves = shelves,
                                onPoster = { id -> movies.find { it.id == id }?.let { openMovie(it) } }
                            )
                        }
                    }
                    Tab.TV -> {}
                }
            }

            // Barra lateral de navegación
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
                NavIcon(Icons.Filled.Home, tab == Tab.HOME) {
                    tab = Tab.HOME
                }
                NavIcon(
                    icon = Icons.Filled.LiveTv,
                    selected = false,
                    focusRequester = liveFocus
                ) {
                    tab = Tab.HOME
                    fullScreen = true
                }
                NavIcon(Icons.Filled.Tv, tab == Tab.SERIES) {
                    tab = Tab.SERIES
                }
                NavIcon(Icons.Filled.Movie, tab == Tab.MOVIES) {
                    tab = Tab.MOVIES
                }
                Spacer(Modifier.weight(1f))
                NavIcon(Icons.Filled.Logout, false) {
                    engine.release()
                    Session.logout()
                    onLogout()
                }
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
    currentChannel: LiveChannel?,
    channelNumber: Int,
    categoryName: String,
    onExpandLive: () -> Unit,
    onPositionSlot: (x: Int, y: Int, w: Int, h: Int) -> Unit,
    onMovie: (VodItem) -> Unit
) {
    var featured by remember(movies) { mutableStateOf(movies.firstOrNull()) }
    val playerHeight = 188.dp
    val playerWidth = 334.dp // 16:9 con altura de 188.dp (188 * 16 / 9)

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 88.dp, end = 20.dp, top = 22.dp, bottom = 12.dp)
    ) {
        Text("NEXO", color = Orange, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(14.dp))

        // Fila Hero: Mini reproductor de TV en vivo + información y botón + Carátula al lado derecho
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ranura (Slot) del mini reproductor 16:9
            Box(
                Modifier
                    .width(playerWidth)
                    .height(playerHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141418))
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        onPositionSlot(
                            pos.x.roundToInt(),
                            pos.y.roundToInt(),
                            coords.size.width,
                            coords.size.height
                        )
                    }
            )

            Spacer(Modifier.width(18.dp))

            // Información del canal y botón TV en vivo (centro)
            Column(
                Modifier
                    .weight(1f)
                    .height(playerHeight),
                verticalArrangement = Arrangement.Center
            ) {
                if (categoryName.isNotBlank()) {
                    Text(
                        text = categoryName.uppercase(),
                        color = Orange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = currentChannel?.name ?: "Cargando TV en vivo…",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (channelNumber > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Canal %03d".format(channelNumber),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(14.dp))
                var btnFocused by remember { mutableStateOf(false) }
                Row(
                    Modifier
                        .onFocusChanged { btnFocused = it.isFocused }
                        .tvFocus(shape = RoundedCornerShape(12.dp), focusedScale = 1.05f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (btnFocused) Color.White else Orange)
                        .border(
                            BorderStroke(
                                if (btnFocused) 2.dp else 0.dp,
                                if (btnFocused) Color.White else Color.Transparent
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onExpandLive() }
                        .focusable()
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Tv,
                        contentDescription = null,
                        tint = if (btnFocused) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "TV en vivo",
                        color = if (btnFocused) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.width(18.dp))

            // Carátula al lado derecho como antes con el mismo alto que el reproductor
            // y el ancho para que se vea la carátula completa
            featured?.let { movie ->
                Box(
                    Modifier
                        .height(playerHeight)
                        .aspectRatio(2f / 3f)
                        .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.04f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1B1B22))
                        .clickable { onMovie(movie) }
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    PosterImage(
                        url = movie.streamIcon,
                        contentDescription = movie.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (movies.isEmpty()) {
            Text(
                "No hay películas de 2026 en el catálogo",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
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
private fun CategoryBrowser(
    shelves: List<CategoryShelf>,
    onPoster: (String) -> Unit
) {
    var expanded by remember { mutableStateOf<CategoryShelf?>(null) }

    val open = expanded
    if (open != null) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .padding(start = 8.dp, bottom = 8.dp)
                    .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.03f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Orange.copy(alpha = 0.92f))
                    .clickable { expanded = null }
                    .focusable()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Volver · ${open.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            PosterGrid(
                items = open.posters.map { it.id to (it.cover to it.title) },
                onClick = onPoster
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(shelves, key = { it.id }) { shelf ->
                CategoryShelfRow(
                    shelf = shelf,
                    onPoster = onPoster,
                    onSeeAll = { expanded = shelf }
                )
            }
        }
    }
}

@Composable
private fun CategoryShelfRow(
    shelf: CategoryShelf,
    onPoster: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    val previewCount = 7
    val preview = remember(shelf) { shelf.posters.take(previewCount) }
    val rowState = rememberLazyListState()
    var seeAllFocused by remember { mutableStateOf(false) }

    LaunchedEffect(seeAllFocused) {
        if (seeAllFocused) {
            rowState.animateScrollToItem(preview.size)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = shelf.name,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, end = 8.dp)
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val visibleSlots = 7.28f
            val posterW = (maxWidth - gap * 7) / visibleSlots
            val posterH = posterW * 3f / 2f

            LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(preview, key = { it.id }) { poster ->
                    Poster(
                        url = poster.cover,
                        title = poster.title,
                        modifier = Modifier
                            .width(posterW)
                            .height(posterH)
                            .tvFocus(shape = RoundedCornerShape(8.dp), focusedScale = 1.04f)
                            .clickable { onPoster(poster.id) }
                            .focusable()
                    )
                }
                item(key = "see-all-${shelf.id}") {
                    SeeAllCategoryCard(
                        backdrop = shelf.posters.getOrNull(previewCount)?.cover
                            ?: shelf.posters.firstOrNull()?.cover,
                        categoryName = shelf.name,
                        modifier = Modifier
                            .width(posterW)
                            .height(posterH),
                        onFocused = { seeAllFocused = it },
                        onClick = onSeeAll
                    )
                }
            }
        }
    }
}

@Composable
private fun SeeAllCategoryCard(
    backdrop: String?,
    categoryName: String,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .tvFocus(shape = RoundedCornerShape(10.dp), focusedScale = 1.05f)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { onFocused(it.isFocused) }
            .clickable(onClick = onClick)
            .focusable()
    ) {
        PosterImage(
            url = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF7A18).copy(alpha = 0.92f),
                            Color(0xFFDE5B17).copy(alpha = 0.95f),
                            Color(0xFF3A1208).copy(alpha = 0.98f)
                        )
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ver categoría",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "completa",
                color = Color.White.copy(alpha = 0.95f),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                categoryName,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PosterGrid(
    items: List<Pair<String, Pair<String?, String>>>,
    onClick: (String) -> Unit = {}
) {
    val cols = 7
    val rowsPerPage = 3
    val pageSize = cols * rowsPerPage
    val pages = remember(items) { items.chunked(pageSize) }
    val listState = rememberLazyListState()
    val snap = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()
    var focusedPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusedPage) {
        if (pages.isEmpty()) return@LaunchedEffect
        val target = focusedPage.coerceIn(0, pages.lastIndex)
        listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        flingBehavior = snap,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true
    ) {
        items(
            count = pages.size,
            key = { page -> "page-$page-${pages[page].firstOrNull()?.first}" }
        ) { pageIndex ->
            PosterPage(
                items = pages[pageIndex],
                cols = cols,
                rows = rowsPerPage,
                onClick = onClick,
                onFocusPage = {
                    if (focusedPage != pageIndex) {
                        focusedPage = pageIndex
                    } else {
                        scope.launch { listState.animateScrollToItem(pageIndex) }
                    }
                },
                modifier = Modifier.fillParentMaxSize()
            )
        }
    }
}

@Composable
private fun PosterPage(
    items: List<Pair<String, Pair<String?, String>>>,
    cols: Int,
    rows: Int,
    onClick: (String) -> Unit,
    onFocusPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hGap = 6.dp
    val vGap = 6.dp
    val pad = 4.dp
    val rowItems = remember(items, cols) { items.chunked(cols) }

    Column(
        modifier = modifier.padding(pad),
        verticalArrangement = Arrangement.spacedBy(vGap)
    ) {
        repeat(rows) { rowIndex ->
            val row = rowItems.getOrNull(rowIndex).orEmpty()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(hGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(cols) { colIndex ->
                    val item = row.getOrNull(colIndex)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item != null) {
                            Poster(
                                url = item.second.first,
                                title = item.second.second,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(2f / 3f)
                                    .tvFocus(shape = RoundedCornerShape(8.dp), focusedScale = 1.03f)
                                    .onFocusChanged { if (it.isFocused) onFocusPage() }
                                    .clickable { onClick(item.first) }
                                    .focusable()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(url: String?, title: String, modifier: Modifier = Modifier) {
    PosterImage(
        url = url,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(8.dp))
    )
}
