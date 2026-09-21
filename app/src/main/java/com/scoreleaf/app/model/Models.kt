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
    val displayMode: PageDisplayMode = PageDisplayMode.SINGLE,
    val fitMode: PageFitMode = PageFitMode.PAGE,
    val lastHalf: PageHalf = PageHalf.TOP
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("fileName", fileName)
        put("composer", composer); put("genre", genre); put("tags", JSONArray(tags.toList()))
        put("rating", rating); put("difficulty", difficulty); put("key", key)
        put("tempo", tempo); put("durationSeconds", durationSeconds)
        put("addedAt", addedAt); put("lastPage", lastPage)
        put("bookmarks", JSONArray(bookmarkedPages.toList()))
        put("displayMode", displayMode.name)
        put("fitMode", fitMode.name)
        put("lastHalf", lastHalf.name)
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
            }.getOrDefault(PageDisplayMode.SINGLE),
            fitMode = runCatching {
                PageFitMode.valueOf(json.optString("fitMode", PageFitMode.PAGE.name))
            }.getOrDefault(PageFitMode.PAGE),
            lastHalf = runCatching {
                PageHalf.valueOf(json.optString("lastHalf", PageHalf.TOP.name))
            }.getOrDefault(PageHalf.TOP)
        )
    }
}

enum class PageDisplayMode(val label: String) {
    SINGLE("Single page"),
    TWO_UP("Two pages"),
    HALF_PAGE("Half page"),
    VERTICAL_SCROLL("Vertical scroll")
}

enum class PageFitMode(val label: String) {
    PAGE("Fit page"),
    WIDTH("Fit width"),
    HEIGHT("Fit height")
}

enum class PageHalf { TOP, BOTTOM }

data class ReaderLocation(val page: Int, val half: PageHalf = PageHalf.TOP)

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
enum class AnnotationTool { PEN, HIGHLIGHTER, ERASER }

data class InkStroke(
    val color: Long,
    val width: Float,
    val points: List<InkPoint>,
    val tool: AnnotationTool = AnnotationTool.PEN,
    val layerId: String = "default"
)

data class AnnotationHistory(
    val strokes: List<InkStroke> = emptyList(),
    val redo: List<InkStroke> = emptyList()
)
