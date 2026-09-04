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
 * - Debounce mínimo (solo si el mando dispara varias teclas)
 * - Soft-switch (cambia media sin stop) cuando hay margen
 * - stop + tick inmediato si el cambio es muy seguido (evita SIGSEGV)
 * - Cache live muy bajo
 */
class VlcEngine(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val lib = createLib(context.applicationContext)
    val player: MediaPlayer = MediaPlayer(lib)

    var onPlaying: (() -> Unit)? = null
    var onError: (() -> Unit)? = null
    var onBuffering: ((Boolean) -> Unit)? = null

    private var layout: VLCVideoLayout? = null
    private var pending: Runnable? = null
    private var openRunnable: Runnable? = null
    private var gen = 0
    private var released = false
    private var lastUrl: String? = null
    private var lastOpenAt = 0L

    init {
        player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> main.post {
                    applyForce169()
                    onBuffering?.invoke(false)
                    onPlaying?.invoke()
                }
                MediaPlayer.Event.Vout -> main.post { applyForce169() }
                MediaPlayer.Event.Buffering -> {
                    val pct = ev.buffering
                    main.post { onBuffering?.invoke(pct < 92f) }
                }
                MediaPlayer.Event.EncounteredError -> main.post { onError?.invoke() }
            }
        }
    }

    fun attach(view: VLCVideoLayout) {
        if (released) return
        if (layout === view) return
        try {
            if (layout != null) player.detachViews()
        } catch (_: Throwable) {}
        layout = view
        player.attachViews(view, null, false, false)
        applyForce169()
    }

    private fun applyForce169() {
        if (released) return
        try {
            player.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
        } catch (_: Throwable) {
            try {
                player.setAspectRatio("16:9")
                player.setScale(0f)
            } catch (_: Throwable) {}
        }
        try {
            player.setAspectRatio("16:9")
            player.setScale(0f)
        } catch (_: Throwable) {}
    }

    fun playNow(url: String) = schedule(url, 0L)

    fun playZap(url: String) = schedule(url, ZAP_DEBOUNCE_MS)

    private fun schedule(url: String, debounceMs: Long) {
        if (released || url.isBlank()) return
        if (url == lastUrl && isPlayingSafe()) return
        lastUrl = url
        val myGen = ++gen
        pending?.let { main.removeCallbacks(it) }
        openRunnable?.let { main.removeCallbacks(it) }
        val r = Runnable {
            if (released || myGen != gen) return@Runnable
            prepareSwitch(url, myGen)
        }
        pending = r
        if (debounceMs > 0L) main.postDelayed(r, debounceMs) else main.post(r)
    }

    private fun isPlayingSafe(): Boolean = try {
        !released && player.isPlaying
    } catch (_: Throwable) {
        false
    }

    private fun prepareSwitch(url: String, myGen: Int) {
        val now = SystemClock.uptimeMillis()
        val gap = now - lastOpenAt
        // Soft: sin stop si el último cambio no fue hace milisegundos (más rápido)
        val useSoft = gap >= SOFT_MIN_GAP_MS
        if (!useSoft) {
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Throwable) {}
        }
        val open = Runnable {
            if (released || myGen != gen) return@Runnable
            openMedia(url)
        }
        openRunnable = open
        if (useSoft) main.post(open) else main.postDelayed(open, STOP_SETTLE_MS)
    }

    private fun openMedia(url: String) {
        if (released) return
        lastOpenAt = SystemClock.uptimeMillis()
        try {
            val media = Media(lib, Uri.parse(url)).apply {
                try { setHWDecoderEnabled(true, false) } catch (_: Throwable) {}
                addOption(":network-caching=$LIVE_CACHE_MS")
                addOption(":live-caching=$LIVE_CACHE_MS")
                addOption(":file-caching=$LIVE_CACHE_MS")
                addOption(":sout-mux-caching=$LIVE_CACHE_MS")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
                addOption(":http-reconnect")
                addOption(":no-audio-time-stretch")
            }
            player.media = media
            media.release()
            applyForce169()
            player.play()
            main.post { applyForce169() }
        } catch (e: Throwable) {
            Log.e(TAG, "play failed $url", e)
            onError?.invoke()
        }
    }

    fun release() {
        if (released) return
        released = true
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
        private const val ZAP_DEBOUNCE_MS = 30L
        private const val STOP_SETTLE_MS = 20L
        private const val SOFT_MIN_GAP_MS = 350L
        private const val LIVE_CACHE_MS = 50

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
