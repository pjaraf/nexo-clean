package com.nexo.tv.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Progreso de películas y series para “Seguir viendo”. */
object ContinueWatching {
    private const val PREFS = "nexo_continue"
    private const val KEY = "items"
    private const val MAX = 20
    private const val MIN_POS_MS = 20_000L
    private const val DONE_RATIO = 0.92f

    private val gson = Gson()

    data class Item(
        val kind: String, // "movie" | "series"
        val id: String,
        val title: String,
        val poster: String? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val updatedAt: Long = 0L,
        val url: String? = null,
        val episodeId: String? = null,
        val episodeExt: String? = null,
        val season: String? = null,
        val episodeNum: Int? = null,
        val categoryId: String? = null
    ) {
        val progress: Float
            get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

        val subtitle: String
            get() = when (kind) {
                "series" -> {
                    val s = season?.takeIf { it.isNotBlank() } ?: "?"
                    val e = episodeNum?.takeIf { it > 0 } ?: "?"
                    "T$s · E$e"
                }
                else -> "Película"
            }
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Item> {
        val json = prefs(ctx).getString(KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<Item>>() {}.type
        return runCatching { gson.fromJson<List<Item>>(json, type) }
            .getOrNull()
            .orEmpty()
            .sortedByDescending { it.updatedAt }
    }

    fun get(ctx: Context, kind: String, id: String): Item? =
        list(ctx).firstOrNull { it.kind == kind && it.id == id }

    fun save(ctx: Context, item: Item) {
        if (item.id.isBlank()) return
        val pos = item.positionMs.coerceAtLeast(0L)
        val dur = item.durationMs.coerceAtLeast(0L)
        // Muy al inicio: no guardar. Casi terminado: quitar.
        if (dur > 0 && pos.toFloat() / dur >= DONE_RATIO) {
            remove(ctx, item.kind, item.id)
            return
        }
        if (pos < MIN_POS_MS && dur <= 0) return
        if (pos < MIN_POS_MS) return

        val now = System.currentTimeMillis()
        val rest = list(ctx).filterNot { it.kind == item.kind && it.id == item.id }
        val updated = (listOf(item.copy(updatedAt = now, positionMs = pos, durationMs = dur)) + rest)
            .take(MAX)
        prefs(ctx).edit().putString(KEY, gson.toJson(updated)).apply()
    }

    fun remove(ctx: Context, kind: String, id: String) {
        val rest = list(ctx).filterNot { it.kind == kind && it.id == id }
        prefs(ctx).edit().putString(KEY, gson.toJson(rest)).apply()
    }
}
