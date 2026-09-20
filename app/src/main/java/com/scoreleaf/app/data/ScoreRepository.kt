package com.scoreleaf.app.data

import android.content.Context
import android.net.Uri
import com.scoreleaf.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScoreRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("scoreleaf", Context.MODE_PRIVATE)
    private val scoreDir = File(context.filesDir, "scores").apply { mkdirs() }

    fun scores(): List<Score> = readArray("scores").map(Score::fromJson)
    fun setlists(): List<Setlist> = readArray("setlists").map(Setlist::fromJson)
    fun scoreFile(score: Score) = File(scoreDir, score.fileName)

    suspend fun importPdf(uri: Uri, displayName: String): Score = withContext(Dispatchers.IO) {
        val safeTitle = displayName.removeSuffix(".pdf").ifBlank { "Untitled score" }
        val score = Score(title = safeTitle, fileName = "${System.nanoTime()}.pdf")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open PDF" }
            scoreFile(score).outputStream().use(input::copyTo)
        }
        saveScores(scores() + score)
        score
    }

    fun updateScore(updated: Score) = saveScores(scores().map { if (it.id == updated.id) updated else it })

    fun deleteScore(score: Score) {
        scoreFile(score).delete()
        saveScores(scores().filterNot { it.id == score.id })
        saveSetlists(setlists().map { it.copy(scoreIds = it.scoreIds - score.id) })
        File(context.filesDir, "ink_${score.id}.json").delete()
    }

    fun createSetlist(name: String) {
        if (name.isNotBlank()) saveSetlists(setlists() + Setlist(name = name.trim()))
    }

    fun addToSetlist(setlistId: String, scoreId: String) = saveSetlists(setlists().map {
        if (it.id == setlistId && scoreId !in it.scoreIds) it.copy(scoreIds = it.scoreIds + scoreId) else it
    })

    fun strokes(scoreId: String, page: Int): List<InkStroke> {
        val root = inkRoot(scoreId)
        val array = root.optJSONArray(page.toString()) ?: return emptyList()
        return (0 until array.length()).map { i ->
            val s = array.getJSONObject(i)
            val pts = s.getJSONArray("points")
            InkStroke(s.getLong("color"), s.getDouble("width").toFloat(),
                (0 until pts.length()).map { p ->
                    val pair = pts.getJSONArray(p)
                    InkPoint(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat())
                })
        }
    }

    fun saveStrokes(scoreId: String, page: Int, strokes: List<InkStroke>) {
        val root = inkRoot(scoreId)
        root.put(page.toString(), JSONArray(strokes.map { stroke ->
            JSONObject().apply {
                put("color", stroke.color); put("width", stroke.width)
                put("points", JSONArray(stroke.points.map { JSONArray(listOf(it.x, it.y)) }))
            }
        }))
        File(context.filesDir, "ink_${scoreId}.json").writeText(root.toString())
    }

    private fun inkRoot(id: String): JSONObject = runCatching {
        JSONObject(File(context.filesDir, "ink_${id}.json").readText())
    }.getOrDefault(JSONObject())

    private fun readArray(key: String): List<JSONObject> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun saveScores(items: List<Score>) = prefs.edit().putString("scores", JSONArray(items.map { it.toJson() }).toString()).apply()
    private fun saveSetlists(items: List<Setlist>) = prefs.edit().putString("setlists", JSONArray(items.map { it.toJson() }).toString()).apply()
}
