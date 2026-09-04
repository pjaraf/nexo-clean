package com.nexo.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Catálogo precargado al arrancar / tras login. */
object Catalog {
    @Volatile var movies: List<VodItem> = emptyList()
        private set
    @Volatile var series: List<SeriesItem> = emptyList()
        private set
    @Volatile var ready: Boolean = false
        private set

    suspend fun preload() = coroutineScope {
        val moviesJob = async { runCatching { XtreamClient.movies() }.getOrDefault(emptyList()) }
        val seriesJob = async { runCatching { XtreamClient.series() }.getOrDefault(emptyList()) }
        movies = moviesJob.await()
        series = seriesJob.await()
        ready = true
        android.util.Log.i("Catalog", "ready movies=${movies.size} series=${series.size}")
    }

    fun clear() {
        movies = emptyList()
        series = emptyList()
        ready = false
    }
}
