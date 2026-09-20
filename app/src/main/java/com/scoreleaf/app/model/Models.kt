package com.scoreleaf.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Score(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val fileName: String,
    val composer: String = "",
    val genre: String = "",
    val tags: Set<String> = emptySet(),
    val rating: Int = 0,
    val difficulty: Int = 0,
    val key: String = "",
    val tempo: Int = 0,
    val durationSeconds: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPage: Int = 0,
    val bookmarkedPages: Set<Int> = emptySet(),
    val displayMode: PageDisplayMode = PageDisplayMode.SINGLE
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("fileName", fileName)
        put("composer", composer); put("genre", genre); put("tags", JSONArray(tags.toList()))
        put("rating", rating); put("difficulty", difficulty); put("key", key)
        put("tempo", tempo); put("durationSeconds", durationSeconds)
        put("addedAt", addedAt); put("lastPage", lastPage)
        put("bookmarks", JSONArray(bookmarkedPages.toList()))
        put("displayMode", displayMode.name)
    }

    companion object {
        fun fromJson(json: JSONObject) = Score(
            id = json.getString("id"), title = json.getString("title"),
            fileName = json.getString("fileName"),
            composer = json.optString("composer"), genre = json.optString("genre"),
            tags = json.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            rating = json.optInt("rating"), difficulty = json.optInt("difficulty"),
            key = json.optString("key"), tempo = json.optInt("tempo"),
            durationSeconds = json.optInt("durationSeconds"), addedAt = json.optLong("addedAt"),
            lastPage = json.optInt("lastPage"),
            bookmarkedPages = json.optJSONArray("bookmarks")?.let { a ->
                (0 until a.length()).map { a.getInt(it) }.toSet()
            } ?: emptySet(),
            displayMode = runCatching {
                PageDisplayMode.valueOf(json.optString("displayMode", PageDisplayMode.SINGLE.name))
            }.getOrDefault(PageDisplayMode.SINGLE)
        )
    }
}

enum class PageDisplayMode(val label: String) {
    SINGLE("Single page"),
    TWO_UP("Two pages")
}

data class Setlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val scoreIds: List<String> = emptyList()
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("scoreIds", JSONArray(scoreIds))
    }

    companion object {
        fun fromJson(json: JSONObject) = Setlist(
            json.getString("id"), json.getString("name"),
            json.getJSONArray("scoreIds").let { a -> (0 until a.length()).map { a.getString(it) } }
        )
    }
}

data class InkPoint(val x: Float, val y: Float)
data class InkStroke(val color: Long, val width: Float, val points: List<InkPoint>)
