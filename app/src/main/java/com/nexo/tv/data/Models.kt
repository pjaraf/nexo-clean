package com.nexo.tv.data

import com.google.gson.annotations.SerializedName

data class UserInfo(
    val username: String? = null,
    val status: String? = null,
    val auth: Any? = null
)

data class LoginResponse(
    @SerializedName("user_info") val userInfo: UserInfo? = null
)

data class LiveCategory(
    @SerializedName("category_id") val categoryId: String = "",
    @SerializedName("category_name") val categoryName: String = ""
)

data class LiveChannel(
    @SerializedName("stream_id") val streamId: Any? = null,
    val name: String = "",
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("category_id") val categoryId: String? = null
) {
    val id: String get() = streamId?.toString()?.substringBefore(".0").orEmpty()
}

data class VodItem(
    @SerializedName("stream_id") val streamId: Any? = null,
    val name: String = "",
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("container_extension") val ext: String? = "mp4",
    val year: String? = null,
    @SerializedName("releasedate") val releaseDate: String? = null,
    @SerializedName("added") val added: String? = null
) {
    val id: String get() = streamId?.toString()?.substringBefore(".0").orEmpty()

    fun matchesYear(target: Int): Boolean {
        val y = target.toString()
        if (year?.trim() == y) return true
        if (releaseDate?.contains(y) == true) return true
        // Títulos tipo "Película (2026)" o "Película 2026"
        if (Regex("""(?:^|[^\d])$y(?:[^\d]|$)""").containsMatchIn(name)) return true
        return false
    }
}

data class SeriesItem(
    @SerializedName("series_id") val seriesId: Any? = null,
    val name: String = "",
    val cover: String? = null
) {
    val id: String get() = seriesId?.toString()?.substringBefore(".0").orEmpty()
}
