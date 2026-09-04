package com.nexo.tv.player

import android.util.Log
import com.nexo.tv.data.Http
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * VLC en esta TV Box crashea (SIGSEGV/nettle) con HTTPS.
 * Este puente baja el stream con OkHttp (TLS de Java) y se lo da a VLC por HTTP local.
 */
object StreamBridge {
    private const val TAG = "StreamBridge"
    private val running = AtomicBoolean(false)
    private val seq = AtomicInteger(0)
    private val targets = ConcurrentHashMap<String, String>()
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var port: Int = 0
    private var server: ServerSocket? = null

    @Synchronized
    fun start() {
        if (running.get()) return
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        server = ss
        port = ss.localPort
        running.set(true)
        Thread({
            while (running.get()) {
                try {
                    val sock = ss.accept()
                    pool.execute { handle(sock) }
                } catch (_: Throwable) {
                    if (!running.get()) break
                }
            }
        }, "nexo-bridge").apply { isDaemon = true }.start()
        Log.i(TAG, "listening on 127.0.0.1:$port")
    }

    fun wrap(remoteUrl: String): String {
        start()
        val id = seq.incrementAndGet().toString()
        targets[id] = remoteUrl
        return "http://127.0.0.1:$port/$id"
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 25000
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val path = parts[1].substringBefore("?").trimStart('/')
            val id = path.substringBefore("/")
            val remote = targets[id]
            val out = socket.getOutputStream()
            if (remote.isNullOrBlank()) {
                writeHead(out, 404, "text/plain", 0)
                return
            }
            val req = Request.Builder()
                .url(remote)
                .header("User-Agent", "NexoPlayer/2.0")
                .header("Accept", "*/*")
                .build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    writeHead(out, resp.code, "text/plain", 0)
                    return
                }
                val body = resp.body ?: run {
                    writeHead(out, 502, "text/plain", 0)
                    return
                }
                val finalUrl = resp.request.url.toString()
                val ctype = (resp.header("Content-Type") ?: body.contentType()?.toString().orEmpty()).lowercase()
                val isPlaylist = ctype.contains("mpegurl") ||
                    ctype.contains("x-mpegurl") ||
                    ctype.contains("vnd.apple") ||
                    (finalUrl.contains(".m3u8", true) && !ctype.contains("video/") && !ctype.contains("mp2t"))
                if (isPlaylist) {
                    val text = body.string()
                    val rewritten = rewritePlaylist(text, finalUrl)
                    val bytes = rewritten.toByteArray(Charsets.UTF_8)
                    writeHead(out, 200, "application/vnd.apple.mpegurl", bytes.size)
                    out.write(bytes)
                } else {
                    val len = body.contentLength()
                    writeHead(
                        out,
                        200,
                        ctype.ifBlank { "application/octet-stream" },
                        if (len > 0) len.toInt() else -1
                    )
                    body.byteStream().copyTo(out)
                }
                out.flush()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "proxy error: ${e.message}", e)
        } finally {
            try { socket.close() } catch (_: Throwable) {}
        }
    }

    private fun rewritePlaylist(body: String, playlistUrl: String): String {
        val base = playlistUrl.toHttpUrlOrNull()
        return body.lineSequence().joinToString("\n") { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> raw
                line.startsWith("#") -> raw.replace(Regex("""URI="([^"]+)"""")) { m ->
                    val abs = resolve(base, m.groupValues[1])
                    """URI="${wrap(abs)}""""
                }
                else -> wrap(resolve(base, line))
            }
        }
    }

    private fun resolve(base: okhttp3.HttpUrl?, ref: String): String {
        if (ref.startsWith("http://", true) || ref.startsWith("https://", true)) return ref
        return base?.resolve(ref)?.toString() ?: ref
    }

    private fun writeHead(out: java.io.OutputStream, code: Int, type: String, length: Int) {
        val extra = if (length >= 0) "Content-Length: $length\r\n" else ""
        val head = "HTTP/1.1 $code OK\r\n" +
            "Content-Type: $type\r\n" +
            extra +
            "Connection: close\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
    }
}
