package com.nexo.tv.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class CategoryShelf(
    val id: String,
    val name: String,
    val posters: List<PosterRef>
)

data class PosterRef(
    val id: String,
    val title: String,
    val cover: String?
)

/** Catálogo precargado al arrancar / tras login. */
object Catalog {
    @Volatile var movies: List<VodItem> = emptyList()
        private set
    @Volatile var series: List<SeriesItem> = emptyList()
        private set
    @Volatile var movieCategories: List<LiveCategory> = emptyList()
        private set
    @Volatile var seriesCategories: List<LiveCategory> = emptyList()
        private set
    @Volatile var liveChannels: List<LiveChannel> = emptyList()
        private set
    @Volatile var liveCategories: List<LiveCategory> = emptyList()
        private set
    @Volatile var ready: Boolean = false
        private set

    val movieShelves: List<CategoryShelf>
        get() = buildShelves(
            categories = movieCategories,
            items = movies.map {
                Triple(it.categoryId.orEmpty(), PosterRef(it.id, it.displayName, it.streamIcon), it)
            }
        )

    val seriesShelves: List<CategoryShelf>
        get() = buildShelves(
            categories = seriesCategories,
            items = series.map {
                Triple(it.categoryId.orEmpty(), PosterRef(it.id, it.name, it.cover), it)
            }
        )

    suspend fun preload() = coroutineScope {
        val moviesJob = async { runCatching { XtreamClient.movies() }.getOrDefault(emptyList()) }
        val seriesJob = async { runCatching { XtreamClient.series() }.getOrDefault(emptyList()) }
        val movieCatsJob = async { runCatching { XtreamClient.vodCategories() }.getOrDefault(emptyList()) }
        val seriesCatsJob = async { runCatching { XtreamClient.seriesCategories() }.getOrDefault(emptyList()) }
        val liveJob = async { runCatching { XtreamClient.liveChannels() }.getOrDefault(emptyList()) }
        val liveCatsJob = async { runCatching { XtreamClient.liveCategories() }.getOrDefault(emptyList()) }
        movies = moviesJob.await()
        series = seriesJob.await()
        movieCategories = movieCatsJob.await()
        seriesCategories = seriesCatsJob.await()
        liveChannels = liveJob.await().filter { it.id.isNotBlank() }
        liveCategories = liveCatsJob.await().filter { it.categoryId.isNotBlank() }
        ready = true
        android.util.Log.i(
            "Catalog",
            "ready movies=${movies.size} series=${series.size} " +
                "movieCats=${movieCategories.size} seriesCats=${seriesCategories.size} " +
                "liveChannels=${liveChannels.size} liveCats=${liveCategories.size}"
        )
    }

    fun clear() {
        movies = emptyList()
        series = emptyList()
        movieCategories = emptyList()
        seriesCategories = emptyList()
        liveChannels = emptyList()
        liveCategories = emptyList()
        ready = false
    }

    private fun <T> buildShelves(
        categories: List<LiveCategory>,
        items: List<Triple<String, PosterRef, T>>
    ): List<CategoryShelf> {
        val byCat = items.groupBy { it.first }
        val shelves = mutableListOf<CategoryShelf>()
        val seen = mutableSetOf<String>()

        for (cat in categories) {
            val id = cat.categoryId
            if (id.isBlank()) continue
            val posters = byCat[id].orEmpty().map { it.second }
            if (posters.isEmpty()) continue
            shelves += CategoryShelf(id = id, name = cat.categoryName.ifBlank { "Sin nombre" }, posters = posters)
            seen += id
        }

        for ((catId, group) in byCat) {
            if (catId.isBlank() || catId in seen) continue
            shelves += CategoryShelf(
                id = catId,
                name = "Otras",
                posters = group.map { it.second }
            )
        }

        val noCat = byCat[""].orEmpty()
        if (noCat.isNotEmpty()) {
            shelves += CategoryShelf(
                id = "_none",
                name = "Sin categoría",
                posters = noCat.map { it.second }
            )
        }

        return shelves
    }
}
