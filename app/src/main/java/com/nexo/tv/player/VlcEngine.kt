package com.nexo.tv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Zapping más rápido:
 * - Debounce corto (coalesce teclas del mando; solo sintoniza el canal final)
 * - Soft-switch (cambia media sin stop) cuando hay un mínimo de margen
 * - stop inmediato si el cambio es muy seguido (evita SIGSEGV)
 * - Cache live muy bajo + prefetch de vecinos
 */
class VlcEngine(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val lib = createLib(context.applicationContext)
    val player: MediaPlayer = MediaPlayer(lib)

    var onPlaying: (() -> Unit)? = null
    var onError: (() -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null
    var onEnded: (() -> Unit)? = null

    /** Proveedor dinámico de URL en vivo (permite refrescar/re-envolver URL al reconectar). */
    var liveUrlProvider: (() -> String)? = null

    var isVod: Boolean = false
        private set

    var isPausedByUser: Boolean = false
        private set

    private var layout: VLCVideoLayout? = null
    private var pending: Runnable? = null
    private var openRunnable: Runnable? = null
    private var gen = 0
    private var released = false
    private var lastUrl: String? = null
    private var lastOpenAt = 0L
    private var endedFiredForUrl: String? = null

    @Volatile private var lastProgressUptime = 0L
    @Volatile private var lastProgressTimeValue = -1L
    @Volatile private var hasReceivedTime = false
    @Volatile private var reconnectCooldownUntil = 0L

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (released) return
            checkLiveStall()
            main.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    init {
        player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> {
                    val now = SystemClock.uptimeMillis()
                    lastProgressUptime = now
                    hasReceivedTime = true
                    main.post {
                        applyAspectMode()
                        onBuffering?.invoke(false)
                        onPlaying?.invoke()
                    }
                }
                MediaPlayer.Event.Vout -> main.post { applyAspectMode() }
                MediaPlayer.Event.TimeChanged -> {
                    val t = ev.timeChanged
                    if (t != lastProgressTimeValue) {
                        lastProgressTimeValue = t
                        lastProgressUptime = SystemClock.uptimeMillis()
                        hasReceivedTime = true
                    }
                }
                MediaPlayer.Event.Buffering -> {
                    val pct = ev.buffering
                    if (pct >= 95f) {
                        lastProgressUptime = SystemClock.uptimeMillis()
                    }
                    main.post { onBuffering?.invoke(pct < 92f) }
                }
                MediaPlayer.Event.EndReached -> main.post {
                    if (!isVod) {
                        // En TV en vivo, EndReached significa corte de conexión del stream.
                        // Reconectar de inmediato y de forma silenciosa ("sin que se note").
                        Log.i(TAG, "EndReached en canal en vivo -> reconectando inmediatamente...")
                        reconnectLive("end_reached", force = true)
                    } else {
                        val url = lastUrl
                        if (url != null && endedFiredForUrl != url) {
                            endedFiredForUrl = url
                            onEnded?.invoke()
                        }
                    }
                }
                MediaPlayer.Event.EncounteredError -> main.post {
                    if (!isVod) {
                        Log.w(TAG, "EncounteredError en canal en vivo -> reconectando inmediatamente...")
                        reconnectLive("encountered_error", force = true)
                    } else {
                        onError?.invoke()
                    }
                }
            }
        }
        main.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    fun attach(view: VLCVideoLayout) {
        if (released) return
        if (layout === view) return
        try {
            if (layout != null) player.detachViews()
        } catch (_: Throwable) {}
        layout = view
        player.attachViews(view, null, false, false)
        applyAspectMode()
    }

    /** @deprecated use applyAspectMode */
    private fun applyForce169() = applyAspectMode()

    fun playNow(url: String) = schedule(url, 0L, vod = false)

    fun playVod(url: String) = schedule(url, 0L, vod = true)

    fun playZap(url: String) = schedule(url, ZAP_DEBOUNCE_MS, vod = false)

    fun togglePause() {
        if (released) return
        try {
            if (player.isPlaying) {
                isPausedByUser = true
                player.pause()
            } else {
                isPausedByUser = false
                lastProgressUptime = SystemClock.uptimeMillis()
                player.play()
            }
        } catch (_: Throwable) {}
    }

    fun pause() {
        if (released) return
        isPausedByUser = true
        try { player.pause() } catch (_: Throwable) {}
    }

    fun resume() {
        if (released) return
        isPausedByUser = false
        val now = SystemClock.uptimeMillis()
        lastProgressUptime = now
        reconnectCooldownUntil = now + 1200L
        try { player.play() } catch (_: Throwable) {}
        if (!isVod) {
            main.postDelayed({
                if (!released && !isVod && !isPausedByUser && !isPlayingSafe()) {
                    reconnectLive("resume_not_playing", force = true)
                }
            }, 500L)
        }
    }

    val isPlaying: Boolean
        get() = try { !released && player.isPlaying } catch (_: Throwable) { false }

    /** Posición actual en ms */
    fun timeMs(): Long = try {
        if (released) 0L else player.time.coerceAtLeast(0L)
    } catch (_: Throwable) {
        0L
    }

    /** Duración en ms */
    fun lengthMs(): Long = try {
        if (released) 0L else player.length.coerceAtLeast(0L)
    } catch (_: Throwable) {
        0L
    }

    private var pendingSeekMs: Long? = null
    private var seekFlush: Runnable? = null

    fun seekBy(deltaMs: Long) {
        if (released) return
        try {
            val len = player.length.coerceAtLeast(0L)
            val cur = pendingSeekMs ?: player.time.coerceAtLeast(0L)
            val target = if (len > 0) {
                (cur + deltaMs).coerceIn(0L, len)
            } else {
                (cur + deltaMs).coerceAtLeast(0L)
            }
            pendingSeekMs = target
            // Debounce taps rápidos del mando; un solo seek real
            seekFlush?.let { main.removeCallbacks(it) }
            val flush = Runnable {
                val t = pendingSeekMs ?: return@Runnable
                pendingSeekMs = null
                applySeek(t)
            }
            seekFlush = flush
            main.postDelayed(flush, 180L)
        } catch (_: Throwable) {}
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        try {
            val len = player.length
            val target = if (len > 0) positionMs.coerceIn(0L, len) else positionMs.coerceAtLeast(0L)
            pendingSeekMs = null
            seekFlush?.let { main.removeCallbacks(it) }
            applySeek(target)
        } catch (_: Throwable) {}
    }

    private fun applySeek(positionMs: Long) {
        if (released) return
        try {
            val len = player.length
            if (len > 0L) {
                player.position = (positionMs.toFloat() / len.toFloat()).coerceIn(0f, 0.999f)
            } else {
                player.time = positionMs
            }
            // Tras seek, algunos builds dejan el pipeline quieto si no se fuerza play
            if (!player.isPlaying) player.play()
            main.postDelayed({
                if (released) return@postDelayed
                try {
                    if (!player.isPlaying) player.play()
                } catch (_: Throwable) {}
            }, 250L)
        } catch (e: Throwable) {
            Log.w(TAG, "seek failed", e)
        }
    }

    data class Track(val id: Int, val name: String)

    fun audioTracks(): List<Track> {
        if (released) return emptyList()
        return try {
            val tracks = player.audioTracks ?: return emptyList()
            tracks.mapNotNull { t ->
                val id = t.id
                if (id < 0) null else Track(id, t.name?.ifBlank { "Audio $id" } ?: "Audio $id")
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun currentAudioTrackId(): Int = try {
        if (released) -1 else player.audioTrack
    } catch (_: Throwable) {
        -1
    }

    fun setAudioTrack(id: Int) {
        if (released) return
        try { player.audioTrack = id } catch (_: Throwable) {}
    }

    fun cycleAudioTrack(): String? {
        val tracks = audioTracks()
        if (tracks.isEmpty()) return null
        val cur = currentAudioTrackId()
        val idx = tracks.indexOfFirst { it.id == cur }.let { if (it < 0) 0 else (it + 1) % tracks.size }
        setAudioTrack(tracks[idx].id)
        return tracks[idx].name
    }

    fun subtitleTracks(): List<Track> {
        if (released) return emptyList()
        return try {
            val tracks = player.spuTracks ?: return emptyList()
            buildList {
                add(Track(-1, "Sin subtítulos"))
                tracks.forEach { t ->
                    val id = t.id
                    if (id >= 0) add(Track(id, t.name?.ifBlank { "Sub $id" } ?: "Sub $id"))
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun currentSubtitleTrackId(): Int = try {
        if (released) -1 else player.spuTrack
    } catch (_: Throwable) {
        -1
    }

    fun setSubtitleTrack(id: Int) {
        if (released) return
        try { player.spuTrack = id } catch (_: Throwable) {}
    }

    fun cycleSubtitleTrack(): String? {
        val tracks = subtitleTracks()
        if (tracks.isEmpty()) return "Sin subtítulos"
        val cur = currentSubtitleTrackId()
        val idx = tracks.indexOfFirst { it.id == cur }.let { if (it < 0) 0 else (it + 1) % tracks.size }
        setSubtitleTrack(tracks[idx].id)
        return tracks[idx].name
    }

    /** Modos con cambio visible (llenar / zoom recorta barras / ratios / original). */
    enum class AspectMode { FILL, ZOOM, RATIO_16_9, RATIO_4_3, ORIGINAL }

    private var aspectMode = AspectMode.FILL

    fun cycleAspectMode(): String {
        aspectMode = when (aspectMode) {
            AspectMode.FILL -> AspectMode.ZOOM
            AspectMode.ZOOM -> AspectMode.RATIO_16_9
            AspectMode.RATIO_16_9 -> AspectMode.RATIO_4_3
            AspectMode.RATIO_4_3 -> AspectMode.ORIGINAL
            AspectMode.ORIGINAL -> AspectMode.FILL
        }
        applyAspectMode()
        // Vout a veces pisa el scale; reaplicar un tick después
        main.postDelayed({ applyAspectMode() }, 120L)
        return when (aspectMode) {
            AspectMode.FILL -> "Pantalla completa"
            AspectMode.ZOOM -> "Zoom"
            AspectMode.RATIO_16_9 -> "16:9"
            AspectMode.RATIO_4_3 -> "4:3"
            AspectMode.ORIGINAL -> "Original"
        }
    }

    private fun applyAspectMode() {
        if (released) return
        try {
            // Limpiar antes: mezclar aspect 16:9 + FILL anulaba el cambio en películas.
            player.setAspectRatio(null)
            player.setScale(0f)
            when (aspectMode) {
                AspectMode.FILL -> {
                    setScaleType(MediaPlayer.ScaleType.SURFACE_FILL)
                }
                AspectMode.ZOOM -> {
                    // Recorta franjas negras típicas de cine (2.35 dentro de 16:9)
                    setScaleType(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                    player.setScale(1.35f)
                }
                AspectMode.RATIO_16_9 -> {
                    setScaleType(MediaPlayer.ScaleType.SURFACE_16_9)
                }
                AspectMode.RATIO_4_3 -> {
                    setScaleType(MediaPlayer.ScaleType.SURFACE_4_3)
                }
                AspectMode.ORIGINAL -> {
                    setScaleType(MediaPlayer.ScaleType.SURFACE_ORIGINAL)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "applyAspectMode failed", e)
        }
    }

    private fun setScaleType(type: MediaPlayer.ScaleType) {
        try {
            player.videoScale = type
        } catch (e: Throwable) {
            Log.w(TAG, "videoScale=$type failed", e)
        }
    }

    private fun schedule(url: String, debounceMs: Long, vod: Boolean) {
        if (released || url.isBlank()) return
        if (url == lastUrl && isPlayingSafe() && !vod) return
        lastUrl = url
        isVod = vod
        isPausedByUser = false
        val now = SystemClock.uptimeMillis()
        lastProgressUptime = now
        lastProgressTimeValue = -1L
        hasReceivedTime = false
        reconnectCooldownUntil = now + (if (vod) 4000L else 2000L)
        endedFiredForUrl = null
        val myGen = ++gen
        pending?.let { main.removeCallbacks(it) }
        openRunnable?.let { main.removeCallbacks(it) }
        val r = Runnable {
            if (released || myGen != gen) return@Runnable
            prepareSwitch(url, myGen, vod)
        }
        pending = r
        if (debounceMs > 0L) main.postDelayed(r, debounceMs) else main.post(r)
    }

    private fun isPlayingSafe(): Boolean = try {
        !released && player.isPlaying
    } catch (_: Throwable) {
        false
    }

    private fun prepareSwitch(url: String, myGen: Int, vod: Boolean) {
        val now = SystemClock.uptimeMillis()
        val gap = now - lastOpenAt
        // Soft-switch: más rápido (sin stop). Solo hard-stop si el cambio es muy seguido.
        val useSoft = !vod && gap >= SOFT_MIN_GAP_MS
        if (!useSoft) {
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Throwable) {}
        }
        val open = Runnable {
            if (released || myGen != gen) return@Runnable
            openMedia(url, vod)
        }
        openRunnable = open
        // Tras stop no hace falta esperar; el soft va al instante.
        main.post(open)
    }

    private fun openMedia(url: String, vod: Boolean) {
        if (released) return
        lastOpenAt = SystemClock.uptimeMillis()
        val cache = if (vod) VOD_CACHE_MS else LIVE_CACHE_MS
        try {
            val media = Media(lib, Uri.parse(url)).apply {
                try { setHWDecoderEnabled(true, false) } catch (_: Throwable) {}
                addOption(":network-caching=$cache")
                addOption(":file-caching=$cache")
                addOption(":sout-mux-caching=$cache")
                if (!vod) {
                    addOption(":live-caching=$cache")
                    addOption(":clock-jitter=500")
                    addOption(":http-reconnect")
                    addOption(":http-continuous")
                } else {
                    addOption(":http-reconnect")
                }
                addOption(":no-audio-time-stretch")
            }
            player.media = media
            media.release()
            applyAspectMode()
            player.play()
            main.post { applyAspectMode() }
        } catch (e: Throwable) {
            Log.e(TAG, "play failed $url", e)
            if (!vod) {
                reconnectLive("play_failed", force = true)
            } else {
                onError?.invoke()
            }
        }
    }

    /**
     * Revisa si el canal en vivo se quedó congelado, pegado o sin reproducir cuadros.
     */
    private fun checkLiveStall() {
        if (released || isVod || isPausedByUser) return
        val url = lastUrl ?: return
        val now = SystemClock.uptimeMillis()

        // Si estamos dentro del período de enfriamiento tras zapping o reconexión, esperar
        if (now < reconnectCooldownUntil) return

        // Consultar el player por si hubo avance sin evento TimeChanged
        val curTime = try { player.time } catch (_: Throwable) { -1L }
        if (curTime > 0L && curTime != lastProgressTimeValue) {
            lastProgressTimeValue = curTime
            lastProgressUptime = now
            hasReceivedTime = true
            return
        }

        // Si el estado del reproductor cayó en Stopped, Ended o Error de forma imprevista
        val state = try { player.playerState } catch (_: Throwable) { -1 }
        if (state == 5 /* Stopped */ || state == 6 /* Ended */ || state == 7 /* Error */) {
            Log.w(TAG, "Player en estado anormal ($state) en canal live -> reconectando silenciosamente...")
            reconnectLive("state_$state", force = true)
            return
        }

        // Si aún no ha iniciado la reproducción tras abrir el stream
        if (!hasReceivedTime) {
            val waitTime = now - lastOpenAt
            if (waitTime >= STARTUP_TIMEOUT_MS) {
                Log.w(TAG, "Canal live no arrancó tras ${waitTime}ms -> reconectando silenciosamente...")
                reconnectLive("startup_timeout", force = false)
            }
            return
        }

        // Si ya estaba reproduciendo pero lleva más de STALL_TIMEOUT_MS sin avanzar
        val stalledDuration = now - lastProgressUptime
        if (stalledDuration >= STALL_TIMEOUT_MS) {
            Log.w(TAG, "Canal live congelado (${stalledDuration}ms sin cuadros) -> reconectando inmediatamente sin que se note...")
            reconnectLive("freeze_${stalledDuration}ms", force = false)
        }
    }

    /**
     * Reconecta de forma transparente el stream en vivo:
     * No llama a player.stop() para evitar pantallas negras ("sin que se note").
     * La superficie mantiene el último fotograma congelado hasta que el nuevo stream
     * decodifica y comienza a reproducir en el mismo surface de inmediato.
     */
    fun reconnectLive(reason: String = "", force: Boolean = false) {
        if (released || isVod) return
        val now = SystemClock.uptimeMillis()
        if (!force && now < reconnectCooldownUntil) return
        reconnectCooldownUntil = now + RECONNECT_COOLDOWN_MS

        val nextUrl = liveUrlProvider?.invoke() ?: lastUrl ?: return
        lastUrl = nextUrl
        lastOpenAt = now
        lastProgressUptime = now
        lastProgressTimeValue = -1L
        hasReceivedTime = false

        val myGen = ++gen
        pending?.let { main.removeCallbacks(it) }
        openRunnable?.let { main.removeCallbacks(it) }

        Log.i(TAG, "Reconectando canal en vivo ($reason): $nextUrl")
        val open = Runnable {
            if (released || myGen != gen) return@Runnable
            openMedia(nextUrl, vod = false)
        }
        openRunnable = open
        main.post(open)
    }

    fun release() {
        if (released) return
        released = true
        main.removeCallbacks(watchdogRunnable)
        pending?.let { main.removeCallbacks(it) }
        openRunnable?.let { main.removeCallbacks(it) }
        try { player.setEventListener(null) } catch (_: Throwable) {}
        try { if (layout != null) player.detachViews() } catch (_: Throwable) {}
        layout = null
        try { player.stop() } catch (_: Throwable) {}
        try { player.release() } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "VlcEngine"
        /** Coalesce teclas rápidas: solo sintoniza el canal donde te detienes. */
        private const val ZAP_DEBOUNCE_MS = 70L
        /** Soft-switch si el canal anterior ya abrió hace ≥ esto (evita SIGSEGV). */
        private const val SOFT_MIN_GAP_MS = 120L
        /** Cache live optimizado: 350ms absorbe jitter de red sin demorar el zapping. */
        private const val LIVE_CACHE_MS = 350
        private const val VOD_CACHE_MS = 1000

        /** Intervalo de chequeo del watchdog de congelamiento (ms). */
        private const val WATCHDOG_INTERVAL_MS = 600L
        /** Tiempo sin avance de frames para declarar congelado (ms). */
        private const val STALL_TIMEOUT_MS = 2500L
        /** Tiempo de gracia en arranque inicial antes de reintentar (ms). */
        private const val STARTUP_TIMEOUT_MS = 4000L
        /** Cooldown para evitar tormenta de reconexiones seguidas (ms). */
        private const val RECONNECT_COOLDOWN_MS = 2500L

        private fun createLib(ctx: Context): LibVLC {
            val tries = listOf(
                arrayListOf(
                    "--no-stats",
                    "--no-video-title-show",
                    "--network-caching=$LIVE_CACHE_MS",
                    "--live-caching=$LIVE_CACHE_MS",
                    "--drop-late-frames",
                    "--skip-frames",
                    "--http-reconnect"
                ),
                ArrayList<String>()
            )
            for (opts in tries) {
                try {
                    return LibVLC(ctx, opts)
                } catch (e: Throwable) {
                    Log.w(TAG, "LibVLC opts failed: ${e.message}")
                }
            }
            return LibVLC(ctx)
        }
    }
}
