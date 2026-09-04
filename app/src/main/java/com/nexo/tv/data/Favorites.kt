package com.nexo.tv.data

import android.content.Context

/** Favoritos locales de series (SharedPreferences). */
object Favorites {
    private const val PREFS = "nexo_favorites"
    private const val KEY_SERIES = "series_ids"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSeriesFavorite(ctx: Context, seriesId: String): Boolean {
        val id = seriesId.substringBefore(".0")
        return load(ctx).contains(id)
    }

    fun toggleSeries(ctx: Context, seriesId: String): Boolean {
        val id = seriesId.substringBefore(".0")
        val set = load(ctx).toMutableSet()
        val added = if (set.contains(id)) {
            set.remove(id)
            false
        } else {
            set.add(id)
            true
        }
        prefs(ctx).edit().putStringSet(KEY_SERIES, set).apply()
        return added
    }

    private fun load(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_SERIES, emptySet())?.toSet().orEmpty()
}
